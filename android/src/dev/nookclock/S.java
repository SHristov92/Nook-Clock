package dev.nookclock;

import java.util.HashMap;

/** UI strings in English and Bulgarian. {@link #lang} is set from {@link Prefs}. */
final class S {
    static String lang = "bg";

    static boolean bg() {
        return "bg".equals(lang);
    }

    private static final HashMap<String, String[]> T = new HashMap<String, String[]>();

    private static void t(String key, String en, String bg) {
        T.put(key, new String[] { en, bg });
    }

    static {
        t("app", "Clock", "Часовник");
        t("updated", "updated", "обновено");
        t("charging", "charging", "зарежда");
        t("waiting", "Waiting for weather...", "Изчакване на времето...");
        t("stale", "Weather data is stale", "Данните за времето са стари");
        t("feels", "Feels like", "Усеща се като");
        t("high", "High", "Макс.");
        t("low", "Low", "Мин.");
        t("humidity", "Humidity", "Влажност");
        t("wind", "Wind", "Вятър");
        t("rain", "rain", "валеж");
        t("kmh", "km/h", "км/ч");
        t("mph", "mph", "mph");
        t("mm", "mm", "мм");
        t("next24", "Next 24 hours", "Следващите 24 часа");
        t("rainchance", "Chance of rain", "Вероятност за валеж");
        t("air", "Air quality", "Качество на въздуха");
        t("aqi", "European AQI", "Европейски индекс (EAQI)");
        t("pollen", "Pollen", "Полени");
        t("nopollen", "No pollen in the air", "Няма полени във въздуха");
        t("aqi24", "Next 24 hours", "Следващите 24 часа");
        t("calendar", "Calendar", "Календар");
        t("namedays", "Name days", "Имен ден");
        t("holidays", "Holidays", "Празници");
        t("dayoff", "day off", "почивен ден");
        t("today", "Today", "Днес");
        t("upcoming", "Coming name days", "Предстоящи имени дни");
        t("night", "Night mode", "Нощен режим");
        t("nodata", "No data yet", "Все още няма данни");
        // settings
        t("settings", "Settings", "Настройки");
        t("language", "Language", "Език");
        t("location", "Location", "Местоположение");
        t("search", "Search", "Търси");
        t("searchhint", "City name", "Име на град");
        t("searching", "Searching...", "Търсене...");
        t("noresults", "Nothing found", "Няма резултати");
        t("units", "Units", "Мерни единици");
        t("metric", "Metric (°C, km/h)", "Метрични (°C, км/ч)");
        t("imperial", "Imperial (°F, mph)", "Английски (°F, mph)");
        t("clock24", "24-hour clock", "24-часов формат");
        t("screens", "Screens", "Екрани");
        t("autoreturn", "Return to clock after", "Връщане към часовника след");
        t("off", "Off", "Изключено");
        t("min", "min", "мин");
        t("nightmode", "Night mode", "Нощен режим");
        t("nightsum", "Big clock, updates every 10 min, no flashing, no weather downloads",
          "Голям часовник, обновяване на 10 мин, без премигване и сваляне на данни");
        t("nightfrom", "Night starts", "Начало на нощта");
        t("nightto", "Night ends", "Край на нощта");
        t("fullrefresh", "Full refresh (anti-ghosting) every", "Пълно опресняване на всеки");
        t("controls", "Page buttons or swipe: change screen. Tap: refresh. Long press: settings.",
          "Бутони или плъзгане: смяна на екрана. Докосване: опресняване. Задържане: настройки.");
        // screen names
        t("page_clock", "Clock and weather", "Часовник и време");
        t("page_hourly", "Hourly forecast", "Почасова прогноза");
        t("page_air", "Air quality", "Качество на въздуха");
        t("page_calendar", "Calendar", "Календар");
        t("page_bigclock", "Big clock", "Голям часовник");
    }

    static String get(String key) {
        String[] v = T.get(key);
        return v == null ? key : v[bg() ? 1 : 0];
    }

    static final String[] WDAY_EN = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
    static final String[] WDAY_BG = { "неделя", "понеделник", "вторник", "сряда", "четвъртък", "петък", "събота" };
    static final String[] WDAY_SHORT_EN = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    static final String[] WDAY_SHORT_BG = { "Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб" };
    static final String[] MONTH_EN = { "January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December" };
    static final String[] MONTH_BG = { "януари", "февруари", "март", "април", "май", "юни", "юли",
            "август", "септември", "октомври", "ноември", "декември" };

    /** dow: Calendar.DAY_OF_WEEK (1 = Sunday). */
    static String weekday(int dow) {
        return (bg() ? WDAY_BG : WDAY_EN)[dow - 1];
    }

    static String weekdayShort(int dow) {
        return (bg() ? WDAY_SHORT_BG : WDAY_SHORT_EN)[dow - 1];
    }

    static String month(int m0) {
        return (bg() ? MONTH_BG : MONTH_EN)[m0];
    }

    static String cap(String s) {
        return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "Tuesday, 20 October" / "Вторник, 20 октомври" */
    static String date(int dow, int day, int m0) {
        return cap(weekday(dow)) + ", " + day + " " + month(m0);
    }

    /** WMO weather code description. */
    static String weather(int c) {
        boolean b = bg();
        switch (c) {
        case 0: return b ? "Ясно" : "Clear";
        case 1: return b ? "Предимно ясно" : "Mostly clear";
        case 2: return b ? "Разкъсана облачност" : "Partly cloudy";
        case 3: return b ? "Облачно" : "Overcast";
        case 45: case 48: return b ? "Мъгла" : "Fog";
        case 51: case 53: case 55: return b ? "Ръмеж" : "Drizzle";
        case 56: case 57: return b ? "Леден ръмеж" : "Freezing drizzle";
        case 61: return b ? "Слаб дъжд" : "Light rain";
        case 63: return b ? "Дъжд" : "Rain";
        case 65: return b ? "Силен дъжд" : "Heavy rain";
        case 66: case 67: return b ? "Леден дъжд" : "Freezing rain";
        case 71: return b ? "Слаб сняг" : "Light snow";
        case 73: return b ? "Сняг" : "Snow";
        case 75: return b ? "Силен снеговалеж" : "Heavy snow";
        case 77: return b ? "Снежни зърна" : "Snow grains";
        case 80: case 81: return b ? "Превалявания" : "Showers";
        case 82: return b ? "Силни превалявания" : "Heavy showers";
        case 85: case 86: return b ? "Снежни превалявания" : "Snow showers";
        case 95: return b ? "Гръмотевична буря" : "Thunderstorm";
        case 96: case 99: return b ? "Буря с градушка" : "Thunder & hail";
        default: return b ? "Неизвестно" : "Unknown";
        }
    }

    /** European AQI band. */
    static String aqi(int v) {
        boolean b = bg();
        if (v < 20) return b ? "Добро" : "Good";
        if (v < 40) return b ? "Задоволително" : "Fair";
        if (v < 60) return b ? "Умерено" : "Moderate";
        if (v < 80) return b ? "Лошо" : "Poor";
        if (v < 100) return b ? "Много лошо" : "Very poor";
        return b ? "Изключително лошо" : "Extremely poor";
    }

    static String pollenLevel(double v) {
        boolean b = bg();
        if (v < 10) return b ? "ниско" : "low";
        if (v < 50) return b ? "умерено" : "moderate";
        if (v < 200) return b ? "високо" : "high";
        return b ? "много високо" : "very high";
    }

    static final String[][] POLLEN = {
            { "alder_pollen", "Alder", "Елша" }, { "birch_pollen", "Birch", "Бреза" },
            { "grass_pollen", "Grass", "Треви" }, { "mugwort_pollen", "Mugwort", "Пелин" },
            { "olive_pollen", "Olive", "Маслина" }, { "ragweed_pollen", "Ragweed", "Амброзия" } };

    /** Streamlined Bulgarian romanisation, for names in English mode. */
    static String latin(String s) {
        if (bg())
            return s;
        String[] map = { "а", "a", "б", "b", "в", "v", "г", "g", "д", "d", "е", "e", "ж", "zh", "з", "z",
                "и", "i", "й", "y", "к", "k", "л", "l", "м", "m", "н", "n", "о", "o", "п", "p", "р", "r",
                "с", "s", "т", "t", "у", "u", "ф", "f", "х", "h", "ц", "ts", "ч", "ch", "ш", "sh", "щ", "sht",
                "ъ", "a", "ь", "y", "ю", "yu", "я", "ya" };
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lc = Character.toLowerCase(c);
            String r = null;
            for (int k = 0; k < map.length; k += 2)
                if (map[k].charAt(0) == lc) {
                    r = map[k + 1];
                    break;
                }
            if (r == null)
                out.append(c);
            else if (c != lc)
                out.append(Character.toUpperCase(r.charAt(0))).append(r.substring(1));
            else
                out.append(r);
        }
        return out.toString();
    }
}
