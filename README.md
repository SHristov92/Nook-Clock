# Nook Clock

Turns a rooted **Nook Simple Touch** (NST, Android 2.1, 600×800 e-ink) into a
wall clock with current weather and a 4-day forecast. Weather comes from
[Open-Meteo](https://open-meteo.com), which is free and needs no API key.

![landscape](build/landscape.png)

There are two versions with the same layout:

| | **Android app** (`android/`) | **Native framebuffer** (`src/`) |
|---|---|---|
| how it runs | normal app, registered as a home screen | static ARM binary, Android UI stopped |
| switching | **n** button → picker: Clock / Home / ReLaunch | `adb` only (`nookclock.sh stop`) or reboot |
| e-ink updates | Android's partial updates + full flash every 30 min via `su` | direct fb0 writes + `epd_refresh` |
| needs | Android 2.1 (API 7), Superuser approval | root over adb |

The **Android app is the recommended version**. The native one is a minimal
"kiosk" mode that frees all resources.

---

## Android app

### Install and register as the n-button home

1. On the Nook, turn on **wireless debugging** (ADB over Wi-Fi, port 5555).
2. Run:
   ```sh
   android/install.sh 192.168.100.6          # build if needed, install, start, open chooser
   ```
3. On the Nook's chooser pick **Clock** and tick **"Use by default for this action"**.
4. Verify:
   ```sh
   android/install.sh 192.168.100.6 check
   ```
5. The first full refresh asks for root: choose **Allow** and **Remember** in Superuser.

`android/install.sh <ip> remove` uninstalls the app, and **n** goes back to B&N Home / ReLaunch.

### Using it

* **n from any app** → the clock.
* **n while the clock is showing** → picker of every home app (Clock, B&N
  Home, ReLaunch, Android launcher).
* **Tap the clock** → full (flashing) refresh, plus a weather update if the
  data is more than a minute old.
* The screen stays on while the clock is in front, so keep the Nook plugged in.

### Settings

Settings are constants at the top of `android/src/dev/nookclock/ClockActivity.java`:
`LAT`, `LON`, `PLACE`, `IMPERIAL`, the weather interval and the full-refresh
interval. Orientation is set in `AndroidManifest.xml` (`screenOrientation="landscape"`).

### NST quirks the app handles

* The **n** button sends B&N's `com.bn.intent.action.home`, not Android's
  MAIN/HOME, so the app registers both. The picker launches each home app
  through whichever of the two it answers.
* The Nook's timezone defaults to **Europe/London**. The clock uses the
  forecast location's timezone from Open-Meteo instead.
* The Nook's TLS can't reach modern servers, so weather is fetched over plain `http://`.

### Build

No Gradle. `android/build.sh` uses bare SDK tools from `toolchain/`, which is gitignored:

```sh
cd toolchain
curl -fLo jdk.tgz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir jdk && tar xzf jdk.tgz -C jdk --strip-components=1
curl -fLO https://dl.google.com/android/repository/build-tools_r34-linux.zip && unzip build-tools_r34-linux.zip && mv android-14 build-tools
curl -fLO https://dl.google.com/android/repository/android-2.1_r03.zip && unzip android-2.1_r03.zip && mv android-2.1_r03-linux android-7
cd .. && android/build.sh          # -> android/build/nookclock.apk
```

* The build uses **legacy `aapt`**, not aapt2. Android 2.1 can only read
  UTF-16 resource string pools, and an aapt2-built APK fails to install with
  `INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION` ("Bad string block").
* `javac` compiles against the API 7 `android.jar`, so newer APIs fail at
  build time, not on the device.
* `d8 --min-api 7` produces dex 035. The APK is v1-signed (SHA-1) with
  `~/.android/debug.keystore`, which is created if missing.

---

## Native framebuffer version

A static musl ARMv7 binary that draws straight into `/dev/graphics/fb0`. It
does its own DNS, because Android 2.1 has no `resolv.conf`, and has fonts and
icons baked in. `nookclock.sh start` stops the Android UI (zygote) and holds a
wakelock. Wi-Fi keeps working because `wpa_supplicant` and `dhcpcd` are
started by init.

```sh
make                 # build/nookclock (ARM, needs toolchain/arm-linux-musleabi-cross from musl.cc)
make preview         # PC previews from test/sample.json
./deploy.sh 192.168.100.6            # push + start (B&N UI is stopped)
./deploy.sh 192.168.100.6 status     # running? + log
./deploy.sh 192.168.100.6 stop       # B&N UI back (a reboot also restores it)
```

Settings live in `device/nookclock.conf`: location, units, `rotate`, refresh intervals.

On the device: the quick update (a zero-length write to fb0) refreshes
without flashing, `epd_refresh` does the full flash, and the clock is
corrected from the HTTP `Date` header.

---

## Tools

| | |
|---|---|
| `tools/genassets.py` | renders weather icons (and fonts for the native build). `--android <dir>` writes the app's PNGs, `--preview <png>` a contact sheet |
| `tools/nookshot.py` | screenshot of the e-ink framebuffer over adb (`python3 tools/nookshot.py shot.png`) |

Use the system `/usr/bin/python3` with `python3-pil`.

## Repository layout

```
android/            Android app: manifest, Java sources, icons, build.sh, install.sh
src/nookclock.c     native version (single file)
device/             native version's on-device script + config
tools/              asset generator, screenshot tool
test/sample.json    saved Open-Meteo response for offline previews
```

## References

* [badd10de: Nook Simple Touch notes](https://badd10de.dev/notes/nook-simple-touch.html):
  fb0 format, update via a zero-length write, `epd_refresh`, stopping Dalvik.
* [Terence Eden: old Nook as an information screen](https://shkspr.mobi/blog/2020/02/turn-an-old-ereader-into-an-information-screen-nook-str):
  the plain-HTTP limitation, and the Electric Sign approach.
* [Terence Eden: rooting the Nook](https://shkspr.mobi/blog/2013/05/rooting-the-nook/): NookManager.
* [nookDevs: Nook Simple Touch](https://nookdevs.com/Nook_Simple_Touch).
* [Open-Meteo forecast API](https://open-meteo.com/en/docs).
