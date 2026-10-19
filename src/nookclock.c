/*
 * nookclock - clock + weather display for a rooted Nook Simple Touch.
 *
 * Static binary (musl), draws straight into the e-ink framebuffer
 * (/dev/graphics/fb0), fetches weather from Open-Meteo over plain HTTP
 * (the Nook's TLS stack cannot talk to modern servers) and does its own
 * DNS, because Android 2.1 has no /etc/resolv.conf for musl to read.
 *
 * Usage:
 *   nookclock [-c config] [-d] [-p pidfile] [--preview out.pgm] [--json file] [--once]
 *
 *   -d   detach from the terminal (for starting from `adb shell`)
 *   --status / --stop   query / terminate the instance named in the pidfile
 *   NOOKCLOCK_DNS env var: nameserver(s) to try when `dns` is not configured
 */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <glob.h>
#include <linux/fb.h>
#include <linux/input.h>
#include <netinet/in.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include "assets.h"

/* ------------------------------------------------------------ config -- */

static struct {
	char lat[32], lon[32], place[64];
	int imperial, clock24, rotate;
	int weather_every;   /* minutes */
	int full_every;      /* minutes between flashing full refreshes */
	int sync_time;       /* set system clock from HTTP Date header */
	int utc_offset;      /* minutes, used until the first weather fetch */
	char dns[128];       /* comma separated nameserver IPs */
	char fbdev[64], epd_refresh[96];
} cfg = {
	"42.70", "23.32", "Sofia", 0, 1, 0, 15, 30, 1, 180,
	"", "/dev/graphics/fb0", "/sys/class/graphics/fb0/epd_refresh",
};

static void logmsg(const char *fmt, ...)
{
	char ts[32];
	time_t t = time(NULL);
	va_list ap;
	strftime(ts, sizeof ts, "%Y-%m-%d %H:%M:%S", gmtime(&t));
	fprintf(stderr, "[%sZ] ", ts);
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fputc('\n', stderr);
}

static void load_config(const char *path)
{
	char line[256];
	FILE *f = fopen(path, "r");
	if (!f) {
		logmsg("config %s not found, using defaults", path);
		return;
	}
	while (fgets(line, sizeof line, f)) {
		char *k = line, *v = strchr(line, '='), *e;
		if (*k == '#' || !v)
			continue;
		*v++ = 0;
		while (*k == ' ') k++;
		for (e = k + strlen(k); e > k && e[-1] == ' '; ) *--e = 0;
		while (*v == ' ') v++;
		for (e = v + strlen(v); e > v && strchr(" \r\n", e[-1]); ) *--e = 0;
#define S(name) if (!strcmp(k, #name)) snprintf(cfg.name, sizeof cfg.name, "%s", v); else
#define I(name) if (!strcmp(k, #name)) cfg.name = atoi(v); else
		S(lat) S(lon) S(place) S(dns) S(fbdev) S(epd_refresh)
		I(clock24) I(rotate) I(weather_every) I(full_every) I(sync_time) I(utc_offset)
		if (!strcmp(k, "units")) cfg.imperial = !strcmp(v, "imperial");
		else logmsg("unknown config key '%s'", k);
#undef S
#undef I
	}
	fclose(f);
}

/* ------------------------------------------------------------ canvas -- */

static int W, H;                /* logical (rotated) canvas size */
static uint8_t *canvas;         /* 8-bit gray, 255 = white paper */

#define GRAY 90                 /* secondary text */

static void clear(void) { memset(canvas, 255, (size_t)W * H); }

static void blend(int x, int y, int a, int col)
{
	uint8_t *p;
	if (x < 0 || y < 0 || x >= W || y >= H || !a)
		return;
	p = &canvas[y * W + x];
	*p = (uint8_t)(*p - (a * (*p - col)) / 255);
}

static void fill_rect(int x, int y, int w, int h, int col)
{
	for (int j = y; j < y + h; j++)
		for (int i = x; i < x + w; i++)
			blend(i, j, 255, col);
}

static const glyph_t *glyph(const font_t *f, unsigned char c)
{
	if (c < 32 || c > 176)
		c = '?';
	return &f->g[c - 32];
}

static int text_width(const font_t *f, const char *s)
{
	int w = 0;
	for (; *s; s++)
		w += glyph(f, (unsigned char)*s)->adv;
	return w;
}

enum { LEFT, CENTER, RIGHT };

/* Draws s with its baseline at y; returns the x just past the text. */
static int text(const font_t *f, int x, int y, int align, int col, const char *s)
{
	if (align == CENTER) x -= text_width(f, s) / 2;
	if (align == RIGHT)  x -= text_width(f, s);
	for (; *s; s++) {
		const glyph_t *g = glyph(f, (unsigned char)*s);
		const uint8_t *b = f->bmp + g->off;
		for (int j = 0; j < g->h; j++)
			for (int i = 0; i < g->w; i++)
				blend(x + g->xoff + i, y + g->yoff + j, b[j * g->w + i], col);
		x += g->adv;
	}
	return x;
}

static int textf(const font_t *f, int x, int y, int align, int col, const char *fmt, ...)
{
	char buf[256];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof buf, fmt, ap);
	va_end(ap);
	return text(f, x, y, align, col, buf);
}

static void icon(const uint8_t *set, int size, int idx, int x, int y)
{
	const uint8_t *b = set + (size_t)idx * size * size;
	for (int j = 0; j < size; j++)
		for (int i = 0; i < size; i++)
			blend(x + i, y + j, b[j * size + i], 0);
}

/* ------------------------------------------------------- framebuffer -- */

static struct {
	int fd;
	uint8_t *mem;
	size_t len;
	struct fb_var_screeninfo var;
	struct fb_fix_screeninfo fix;
} fb = { .fd = -1 };

static int fb_open(void)
{
	fb.fd = open(cfg.fbdev, O_RDWR);
	if (fb.fd < 0 ||
	    ioctl(fb.fd, FBIOGET_VSCREENINFO, &fb.var) < 0 ||
	    ioctl(fb.fd, FBIOGET_FSCREENINFO, &fb.fix) < 0) {
		logmsg("cannot open framebuffer %s: %s", cfg.fbdev, strerror(errno));
		return -1;
	}
	fb.len = fb.fix.smem_len;
	fb.mem = mmap(NULL, fb.len, PROT_READ | PROT_WRITE, MAP_SHARED, fb.fd, 0);
	if (fb.mem == MAP_FAILED) {
		logmsg("mmap framebuffer: %s", strerror(errno));
		return -1;
	}
	logmsg("fb %ux%u %ubpp line=%u yoff=%u", fb.var.xres, fb.var.yres,
	       fb.var.bits_per_pixel, fb.fix.line_length, fb.var.yoffset);
	return 0;
}

/* Copy canvas into the framebuffer (rotating), then ask the EPD to update. */
static void fb_show(int full)
{
	int fw = fb.var.xres, fh = fb.var.yres, bpp = fb.var.bits_per_pixel;
	uint8_t *base = fb.mem + (size_t)fb.var.yoffset * fb.fix.line_length;

	for (int y = 0; y < fh; y++) {
		uint8_t *row = base + (size_t)y * fb.fix.line_length;
		for (int x = 0; x < fw; x++) {
			int lx, ly, g;
			switch (cfg.rotate) {
			case 90:  lx = y;          ly = fw - 1 - x; break;
			case 270: lx = fh - 1 - y; ly = x;          break;
			case 180: lx = fw - 1 - x; ly = fh - 1 - y; break;
			default:  lx = x;          ly = y;          break;
			}
			g = (lx < W && ly < H) ? canvas[ly * W + lx] : 255;
			g = (g * 15 + 127) / 255 * 17;  /* 16 e-ink gray levels */
			if (bpp == 16)
				((uint16_t *)row)[x] = (uint16_t)((g >> 3) << 11 | (g >> 2) << 5 | g >> 3);
			else if (bpp == 32)
				((uint32_t *)row)[x] = 0xff000000u | g << 16 | g << 8 | g;
			else
				row[x] = (uint8_t)g;
		}
	}
	/* On the NST a zero-length write to fb0 triggers a (non-flashing) update. */
	if (write(fb.fd, "", 0) < 0)
		logmsg("fb update write: %s", strerror(errno));
	if (full) {
		int fd = open(cfg.epd_refresh, O_WRONLY);
		if (fd >= 0) {
			if (write(fd, "1", 1) < 0)
				logmsg("epd_refresh: %s", strerror(errno));
			close(fd);
		}
	}
}

static void write_pgm(const char *path)
{
	FILE *f = fopen(path, "wb");
	if (!f) {
		logmsg("cannot write %s", path);
		return;
	}
	fprintf(f, "P5\n%d %d\n255\n", W, H);
	fwrite(canvas, 1, (size_t)W * H, f);
	fclose(f);
}

/* ----------------------------------------------------------- network -- */

static void sock_timeouts(int s, int sec)
{
	struct timeval tv = { sec, 0 };
	setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof tv);
	setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof tv);
}

/* Minimal DNS A-record lookup over UDP. Returns 0 on success. */
static int dns_query(const char *server, const char *name, struct in_addr *out)
{
	uint8_t q[512], r[1024];
	int n = 12, s, len, pos;
	struct sockaddr_in sa = { .sin_family = AF_INET, .sin_port = htons(53) };
	uint16_t id = (uint16_t)(rand() & 0xffff);

	if (!inet_aton(server, &sa.sin_addr))
		return -1;
	memset(q, 0, 12);
	q[0] = id >> 8; q[1] = id & 0xff;
	q[2] = 0x01;                     /* recursion desired */
	q[5] = 1;                        /* one question */
	for (const char *p = name; *p; ) {
		const char *dot = strchr(p, '.');
		int l = dot ? (int)(dot - p) : (int)strlen(p);
		if (l == 0 || l > 63 || n + l + 6 > (int)sizeof q)
			return -1;
		q[n++] = (uint8_t)l;
		memcpy(q + n, p, l);
		n += l;
		p += l + (dot ? 1 : 0);
	}
	q[n++] = 0;
	q[n++] = 0; q[n++] = 1;          /* type A */
	q[n++] = 0; q[n++] = 1;          /* class IN */

	if ((s = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
		return -1;
	sock_timeouts(s, 3);
	if (sendto(s, q, n, 0, (struct sockaddr *)&sa, sizeof sa) != n ||
	    (len = (int)recv(s, r, sizeof r, 0)) < 12) {
		close(s);
		return -1;
	}
	close(s);
	if (r[0] != q[0] || r[1] != q[1] || (r[3] & 0x0f))
		return -1;

	pos = n;                         /* answers follow the echoed question */
	for (int an = r[6] << 8 | r[7]; an > 0 && pos < len; an--) {
		int type, rdlen;
		if ((r[pos] & 0xc0) == 0xc0)
			pos += 2;
		else {
			while (pos < len && r[pos]) pos += r[pos] + 1;
			pos++;
		}
		if (pos + 10 > len)
			break;
		type = r[pos] << 8 | r[pos + 1];
		rdlen = r[pos + 8] << 8 | r[pos + 9];
		pos += 10;
		if (type == 1 && rdlen == 4 && pos + 4 <= len) {
			memcpy(out, r + pos, 4);
			return 0;
		}
		pos += rdlen;
	}
	return -1;
}

static int resolve(const char *host, struct in_addr *out)
{
	char list[160], *save = NULL;
	if (inet_aton(host, out))
		return 0;
	snprintf(list, sizeof list, "%s%s8.8.8.8,1.1.1.1", cfg.dns, cfg.dns[0] ? "," : "");
	for (char *s = strtok_r(list, ", ", &save); s; s = strtok_r(NULL, ", ", &save))
		if (dns_query(s, host, out) == 0)
			return 0;
	logmsg("DNS lookup for %s failed", host);
	return -1;
}

/* HTTP/1.0 GET (no chunked encoding). Returns malloc'd body or NULL. */
static char *http_get(const char *host, const char *path, time_t *date)
{
	struct sockaddr_in sa = { .sin_family = AF_INET, .sin_port = htons(80) };
	char req[1024], *buf, *body, *d;
	size_t cap = 256 * 1024, len = 0;
	ssize_t n;
	int s;

	if (resolve(host, &sa.sin_addr))
		return NULL;
	if ((s = socket(AF_INET, SOCK_STREAM, 0)) < 0)
		return NULL;
	sock_timeouts(s, 15);
	if (connect(s, (struct sockaddr *)&sa, sizeof sa) < 0) {
		logmsg("connect %s: %s", host, strerror(errno));
		close(s);
		return NULL;
	}
	n = snprintf(req, sizeof req, "GET %s HTTP/1.0\r\nHost: %s\r\n"
		     "User-Agent: nookclock/1.0\r\nConnection: close\r\n\r\n", path, host);
	if (write(s, req, n) != n) {
		close(s);
		return NULL;
	}
	buf = malloc(cap + 1);
	while (buf && len < cap && (n = read(s, buf + len, cap - len)) > 0)
		len += n;
	close(s);
	if (!buf)
		return NULL;
	buf[len] = 0;

	if (strncmp(buf, "HTTP/1.", 7) || atoi(buf + 9) != 200 ||
	    !(body = strstr(buf, "\r\n\r\n"))) {
		logmsg("bad HTTP response: %.40s", buf);
		free(buf);
		return NULL;
	}
	*body = 0;
	if (date && (d = strcasestr(buf, "\r\nDate:"))) {
		struct tm tm = { 0 };
		if (strptime(d + 7, " %a, %d %b %Y %H:%M:%S GMT", &tm))
			*date = timegm(&tm);
	}
	memmove(buf, body + 4, len - (size_t)(body + 4 - buf) + 1);
	return buf;
}

/* ----------------------------------------------------------- weather -- */

#define NDAYS 5

static struct {
	int valid, utc_off;              /* utc_off in seconds */
	time_t fetched;
	double temp, feels, wind;
	int humid, code, is_day;
	struct { int y, m, d, code, pop; double tmax, tmin; } day[NDAYS];
} wx;

/* Copy the JSON object that follows `key` (e.g. "current") into out. */
static int json_obj(const char *js, const char *key, char *out, size_t cap)
{
	char pat[64];
	const char *p, *e;
	snprintf(pat, sizeof pat, "\"%s\":{", key);
	if (!(p = strstr(js, pat)) || !(e = strchr(p, '}')))
		return -1;
	p += strlen(pat);
	if ((size_t)(e - p) >= cap)
		return -1;
	memcpy(out, p, e - p);
	out[e - p] = 0;
	return 0;
}

static double json_num(const char *obj, const char *key, double def)
{
	char pat[64];
	const char *p;
	snprintf(pat, sizeof pat, "\"%s\":", key);
	if (!(p = strstr(obj, pat)) || p[strlen(pat)] == 'n')
		return def;
	return strtod(p + strlen(pat), NULL);
}

/* Element i of an array value: numbers into *num, strings into str. */
static int json_arr(const char *obj, const char *key, int i, double *num, char *str, size_t cap)
{
	char pat[64];
	const char *p, *end;
	snprintf(pat, sizeof pat, "\"%s\":[", key);
	if (!(p = strstr(obj, pat)))
		return -1;
	p += strlen(pat);
	end = strchr(p, ']');
	for (; i > 0; i--)
		if (!(p = strchr(p, ',')) || !end || p > end)
			return -1;
		else
			p++;
	if (*p == 'n' || *p == ']')
		return -1;
	if (*p == '"' && str) {
		size_t n = strcspn(p + 1, "\"");
		if (n >= cap) n = cap - 1;
		memcpy(str, p + 1, n);
		str[n] = 0;
	} else if (num)
		*num = strtod(p, NULL);
	return 0;
}

static int parse_weather(const char *js)
{
	static char cur[2048], daily[4096];
	char date[16];
	double v;

	if (json_obj(js, "current", cur, sizeof cur) ||
	    json_obj(js, "daily", daily, sizeof daily)) {
		logmsg("weather JSON missing current/daily");
		return -1;
	}
	wx.utc_off = (int)json_num(js, "utc_offset_seconds", cfg.utc_offset * 60);
	wx.temp = json_num(cur, "temperature_2m", 0);
	wx.feels = json_num(cur, "apparent_temperature", wx.temp);
	wx.wind = json_num(cur, "wind_speed_10m", 0);
	wx.humid = (int)json_num(cur, "relative_humidity_2m", -1);
	wx.code = (int)json_num(cur, "weather_code", -1);
	wx.is_day = (int)json_num(cur, "is_day", 1);
	for (int i = 0; i < NDAYS; i++) {
		memset(&wx.day[i], 0, sizeof wx.day[i]);
		if (json_arr(daily, "time", i, NULL, date, sizeof date) == 0)
			sscanf(date, "%d-%d-%d", &wx.day[i].y, &wx.day[i].m, &wx.day[i].d);
		wx.day[i].code = json_arr(daily, "weather_code", i, &v, NULL, 0) ? -1 : (int)v;
		wx.day[i].pop = json_arr(daily, "precipitation_probability_max", i, &v, NULL, 0) ? -1 : (int)v;
		if (json_arr(daily, "temperature_2m_max", i, &v, NULL, 0) == 0) wx.day[i].tmax = v;
		if (json_arr(daily, "temperature_2m_min", i, &v, NULL, 0) == 0) wx.day[i].tmin = v;
	}
	wx.valid = 1;
	return 0;
}

static int fetch_weather(void)
{
	char path[600];
	time_t date = 0;
	char *js;

	snprintf(path, sizeof path, "/v1/forecast?latitude=%s&longitude=%s"
		 "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
		 "is_day,weather_code,wind_speed_10m"
		 "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
		 "precipitation_probability_max&timezone=auto&forecast_days=%d%s",
		 cfg.lat, cfg.lon, NDAYS,
		 cfg.imperial ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
	if (!(js = http_get("api.open-meteo.com", path, &date)))
		return -1;
	if (parse_weather(js)) {
		free(js);
		return -1;
	}
	free(js);
	wx.fetched = time(NULL);
	if (cfg.sync_time && date && labs((long)(date - wx.fetched)) > 30) {
		struct timeval tv = { date, 0 };
		if (settimeofday(&tv, NULL) == 0) {
			logmsg("clock corrected by %+lds", (long)(date - wx.fetched));
			wx.fetched = date;
		}
	}
	logmsg("weather: %.1f code %d", wx.temp, wx.code);
	return 0;
}

static const char *wmo_text(int c)
{
	switch (c) {
	case 0: return "Clear";
	case 1: return "Mostly clear";
	case 2: return "Partly cloudy";
	case 3: return "Overcast";
	case 45: case 48: return "Fog";
	case 51: case 53: case 55: return "Drizzle";
	case 56: case 57: return "Freezing drizzle";
	case 61: return "Light rain";
	case 63: return "Rain";
	case 65: return "Heavy rain";
	case 66: case 67: return "Freezing rain";
	case 71: return "Light snow";
	case 73: return "Snow";
	case 75: return "Heavy snow";
	case 77: return "Snow grains";
	case 80: case 81: return "Showers";
	case 82: return "Heavy showers";
	case 85: case 86: return "Snow showers";
	case 95: return "Thunderstorm";
	case 96: case 99: return "Thunder & hail";
	default: return "Unknown";
	}
}

static int wmo_icon(int c, int day)
{
	if (c <= 1) return day ? IC_CLEAR_DAY : IC_CLEAR_NIGHT;
	if (c == 2) return day ? IC_PARTLY_DAY : IC_PARTLY_NIGHT;
	if (c == 45 || c == 48) return IC_FOG;
	if (c >= 51 && c <= 57) return IC_DRIZZLE;
	if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return IC_RAIN;
	if ((c >= 71 && c <= 77) || c == 85 || c == 86) return IC_SNOW;
	if (c >= 95) return IC_THUNDER;
	return IC_CLOUDY;
}

/* ------------------------------------------------------------ render -- */

static const char *WDAY[] = { "Sunday", "Monday", "Tuesday", "Wednesday",
			      "Thursday", "Friday", "Saturday" };
static const char *MONTH[] = { "January", "February", "March", "April", "May", "June", "July",
			       "August", "September", "October", "November", "December" };

static int weekday(int y, int m, int d)
{
	struct tm tm = { .tm_year = y - 1900, .tm_mon = m - 1, .tm_mday = d, .tm_hour = 12 };
	time_t t = timegm(&tm);
	return gmtime_r(&t, &tm)->tm_wday;
}

static int battery(int *charging)
{
	glob_t g;
	int pct = -1;
	*charging = 0;
	if (glob("/sys/class/power_supply/*/capacity", 0, NULL, &g) == 0) {
		FILE *f = fopen(g.gl_pathv[0], "r");
		if (f) {
			char st[300], buf[32] = "";
			if (fscanf(f, "%d", &pct) != 1) pct = -1;
			fclose(f);
			snprintf(st, sizeof st, "%.*s/status",
				 (int)(strrchr(g.gl_pathv[0], '/') - g.gl_pathv[0]), g.gl_pathv[0]);
			if ((f = fopen(st, "r"))) {
				if (fgets(buf, sizeof buf, f))
					*charging = !strncmp(buf, "Charging", 8) || !strncmp(buf, "Full", 4);
				fclose(f);
			}
		}
		globfree(&g);
	}
	return pct;
}

static int utc_offset(void) { return wx.valid ? wx.utc_off : cfg.utc_offset * 60; }

static void render(time_t now)
{
	time_t lt = now + utc_offset();
	struct tm tm;
	char clk[16], buf[96];
	int land = W > H, chg, bat, x, y, h12;
	const font_t *cf = land ? &font_clock_l : &font_clock_p;
	const char *deg = "\xb0";
	time_t age = now - wx.fetched;

	gmtime_r(&lt, &tm);
	clear();

	/* status line */
	if (wx.valid) {
		time_t ft = wx.fetched + utc_offset();
		struct tm ftm;
		gmtime_r(&ft, &ftm);
		textf(&font_small, 16, 26, LEFT, GRAY, "%s  updated %02d:%02d",
		      cfg.place, ftm.tm_hour, ftm.tm_min);
	} else
		text(&font_small, 16, 26, LEFT, GRAY, cfg.place);
	if ((bat = battery(&chg)) >= 0)
		textf(&font_small, W - 16, 26, RIGHT, GRAY, "%s%d%%", chg ? "charging " : "", bat);

	/* clock + date */
	h12 = tm.tm_hour % 12 ? tm.tm_hour % 12 : 12;
	if (cfg.clock24)
		snprintf(clk, sizeof clk, "%02d:%02d", tm.tm_hour, tm.tm_min);
	else
		snprintf(clk, sizeof clk, "%d:%02d", h12, tm.tm_min);
	y = land ? 215 : 205;
	x = text(cf, W / 2, y, CENTER, 0, clk);
	if (!cfg.clock24)
		text(&font_med_b, x + 8, y, LEFT, 0, tm.tm_hour < 12 ? "AM" : "PM");
	snprintf(buf, sizeof buf, "%s, %d %s", WDAY[tm.tm_wday], tm.tm_mday, MONTH[tm.tm_mon]);
	text(&font_big, W / 2, y + 52, CENTER, 0, buf);
	fill_rect(30, y + 72, W - 60, 2, GRAY);

	/* current conditions */
	y += 82;
	if (!wx.valid || age > 3 * 3600) {
		text(&font_big, W / 2, y + 110, CENTER, GRAY,
		     wx.valid ? "Weather data is stale" : "Waiting for weather...");
		return;
	}
	icon(icon_l_bmp, ICON_L_SIZE, wmo_icon(wx.code, wx.is_day), 24, y + 4);
	x = textf(&font_temp, 205, y + 122, LEFT, 0, "%.0f%s", wx.temp, deg);
	if (land) {
		int tx = x + 30 > 440 ? x + 30 : 440;
		text(&font_med_b, tx, y + 36, LEFT, 0, wmo_text(wx.code));
		textf(&font_med, tx, y + 70, LEFT, 0, "Feels like %.0f%s", wx.feels, deg);
		textf(&font_med, tx, y + 104, LEFT, 0, "High %.0f%s  Low %.0f%s",
		      wx.day[0].tmax, deg, wx.day[0].tmin, deg);
		textf(&font_med, tx, y + 138, LEFT, GRAY, "Humidity %d%%", wx.humid);
		textf(&font_med, tx, y + 172, LEFT, GRAY, "Wind %.0f %s",
		      wx.wind, cfg.imperial ? "mph" : "km/h");
		y += 185;
	} else {
		text(&font_med_b, 205, y + 162, LEFT, 0, wmo_text(wx.code));
		textf(&font_med, 30, y + 222, LEFT, 0, "Feels like %.0f%s   High %.0f%s  Low %.0f%s",
		      wx.feels, deg, wx.day[0].tmax, deg, wx.day[0].tmin, deg);
		textf(&font_med, 30, y + 260, LEFT, GRAY, "Humidity %d%%   Wind %.0f %s",
		      wx.humid, wx.wind, cfg.imperial ? "mph" : "km/h");
		y += 282;
	}
	fill_rect(30, y, W - 60, 2, GRAY);

	/* forecast: the next four days */
	for (int i = 1; i < NDAYS; i++) {
		int cw = W / (NDAYS - 1), cx = cw * (i - 1) + cw / 2;
		if (!wx.day[i].y)
			continue;
		snprintf(buf, sizeof buf, "%s", WDAY[weekday(wx.day[i].y, wx.day[i].m, wx.day[i].d)]);
		if (!land)
			buf[3] = 0;
		text(&font_med_b, cx, y + 36, CENTER, 0, buf);
		if (land) {
			icon(icon_s_bmp, ICON_S_SIZE, wmo_icon(wx.day[i].code, 1), cx - 92, y + 46);
			x = textf(&font_med_b, cx - 10, y + 82, LEFT, 0, "%.0f%s", wx.day[i].tmax, deg);
			textf(&font_med, x + 4, y + 82, LEFT, GRAY, "%.0f%s", wx.day[i].tmin, deg);
			if (wx.day[i].pop >= 0)
				textf(&font_small, cx - 10, y + 112, LEFT, GRAY, "rain %d%%", wx.day[i].pop);
		} else {
			icon(icon_s_bmp, ICON_S_SIZE, wmo_icon(wx.day[i].code, 1), cx - ICON_S_SIZE / 2, y + 46);
			snprintf(buf, sizeof buf, "%.0f%s / %.0f%s", wx.day[i].tmax, deg, wx.day[i].tmin, deg);
			text(&font_med, cx, y + 156, CENTER, 0, buf);
			if (wx.day[i].pop >= 0)
				textf(&font_small, cx, y + 188, CENTER, GRAY, "rain %d%%", wx.day[i].pop);
		}
	}
}

/* -------------------------------------------------------------- main -- */

static volatile sig_atomic_t quit;
static void on_signal(int s) { (void)s; quit = 1; }

static char *read_file(const char *path)
{
	FILE *f = fopen(path, "rb");
	char *b;
	long n;
	if (!f) return NULL;
	fseek(f, 0, SEEK_END);
	n = ftell(f);
	rewind(f);
	if ((b = malloc(n + 1)) && fread(b, 1, n, f) == (size_t)n)
		b[n] = 0;
	fclose(f);
	return b;
}

/* --status / --stop: exit 0 if an instance is (was) running. */
static int control(int what, const char *pidfile)
{
	FILE *f = pidfile ? fopen(pidfile, "r") : NULL;
	int pid = 0;
	if (f) {
		if (fscanf(f, "%d", &pid) != 1)
			pid = 0;
		fclose(f);
	}
	if (pid <= 0 || kill(pid, 0) < 0) {
		printf("nookclock is not running\n");
		return 1;
	}
	if (what == 's') {
		printf("nookclock is running (pid %d)\n", pid);
		return 0;
	}
	kill(pid, SIGTERM);
	for (int i = 0; i < 50 && kill(pid, 0) == 0; i++)
		usleep(100000);
	if (kill(pid, 0) == 0)
		kill(pid, SIGKILL);
	printf("nookclock stopped\n");
	return 0;
}

int main(int argc, char **argv)
{
	const char *conf = "/data/local/nookclock/nookclock.conf";
	const char *preview = NULL, *json = NULL, *pidfile = NULL;
	int once = 0, daemonize = 0, ctl = 0, nin = 0, last_min = -1, force = 0;
	struct pollfd in[4];
	time_t next_wx = 0, last_full = 0, last_key = 0;

	for (int i = 1; i < argc; i++) {
		if (!strcmp(argv[i], "-c") && i + 1 < argc) conf = argv[++i];
		else if (!strcmp(argv[i], "--preview") && i + 1 < argc) preview = argv[++i];
		else if (!strcmp(argv[i], "--json") && i + 1 < argc) json = argv[++i];
		else if (!strcmp(argv[i], "--once")) once = 1;
		else if (!strcmp(argv[i], "-d")) daemonize = 1;
		else if (!strcmp(argv[i], "--status")) ctl = 's';
		else if (!strcmp(argv[i], "--stop")) ctl = 'k';
		else if (!strcmp(argv[i], "-p") && i + 1 < argc) pidfile = argv[++i];
		else {
			fprintf(stderr, "usage: %s [-c config] [-d] [-p pidfile] [--preview out.pgm] [--json file] [--once]\n", argv[0]);
			return 2;
		}
	}
	if (ctl)
		return control(ctl, pidfile);
	load_config(conf);
	if (!cfg.dns[0] && getenv("NOOKCLOCK_DNS"))
		snprintf(cfg.dns, sizeof cfg.dns, "%s", getenv("NOOKCLOCK_DNS"));
	if (daemonize) {
		pid_t pid = fork();
		if (pid < 0)
			return 1;
		if (pid > 0)
			return 0;
		setsid();
		signal(SIGHUP, SIG_IGN);
	}
	if (pidfile) {
		FILE *f = fopen(pidfile, "w");
		if (f) {
			fprintf(f, "%d\n", (int)getpid());
			fclose(f);
		}
	}
	logmsg("nookclock starting, lat=%s lon=%s rotate=%d", cfg.lat, cfg.lon, cfg.rotate);
	srand((unsigned)time(NULL) ^ (unsigned)getpid());
	signal(SIGTERM, on_signal);
	signal(SIGINT, on_signal);
	signal(SIGPIPE, SIG_IGN);

	if (preview) {
		W = cfg.rotate % 180 ? 800 : 600;
		H = cfg.rotate % 180 ? 600 : 800;
	} else {
		if (fb_open())
			return 1;
		W = cfg.rotate % 180 ? (int)fb.var.yres : (int)fb.var.xres;
		H = cfg.rotate % 180 ? (int)fb.var.xres : (int)fb.var.yres;
		/* Side page-turn buttons and the home/power keys force a refresh. */
		for (int i = 0; i < 4; i++) {
			char dev[32];
			snprintf(dev, sizeof dev, "/dev/input/event%d", i);
			if ((in[nin].fd = open(dev, O_RDONLY | O_NONBLOCK)) >= 0)
				in[nin++].events = POLLIN;
		}
	}
	canvas = malloc((size_t)W * H);

	if (json) {
		char *js = read_file(json);
		if (!js || parse_weather(js))
			return 1;
		wx.fetched = time(NULL);
		next_wx = (time_t)1 << 40;   /* never fetch */
	}

	while (!quit) {
		time_t now = time(NULL);
		int full = 0;

		if (now >= next_wx) {
			int ok = fetch_weather() == 0;
			now = time(NULL);
			next_wx = now + (ok ? cfg.weather_every * 60 : 120);
		}
		if (now / 60 != last_min || force) {
			render(now);
			if (preview) {
				write_pgm(preview);
				break;
			}
			full = force || now - last_full >= cfg.full_every * 60;
			force = 0;
			fb_show(full);
			if (full)
				last_full = now;
			last_min = (int)(now / 60);
		}
		if (once)
			break;

		/* sleep until the next minute (or a key press) */
		struct timeval tv;
		gettimeofday(&tv, NULL);
		int ms = (int)((60 - tv.tv_sec % 60) * 1000 - tv.tv_usec / 1000) + 50;
		if (poll(in, nin, ms) > 0) {
			struct input_event ev;
			for (int i = 0; i < nin; i++)
				while (read(in[i].fd, &ev, sizeof ev) == sizeof ev)
					if (ev.type == EV_KEY && ev.value == 1 && time(NULL) - last_key > 5) {
						last_key = time(NULL);
						next_wx = 0;
						force = 1;
						logmsg("key %d: refresh", ev.code);
					}
		}
	}
	logmsg("exiting");
	if (pidfile)
		unlink(pidfile);
	return 0;
}
