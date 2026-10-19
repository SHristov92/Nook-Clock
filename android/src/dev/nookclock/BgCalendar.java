package dev.nookclock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Bulgarian public holidays and the major name days (имени дни), computed
 * offline. Movable feasts follow Orthodox Easter.
 */
final class BgCalendar {
    static final class Day {
        final int month, day;           // month 1-12
        final String bg, en;
        final boolean dayOff;
        Day(int month, int day, String bg, String en, boolean dayOff) {
            this.month = month; this.day = day; this.bg = bg; this.en = en; this.dayOff = dayOff;
        }
        String name() { return S.bg() ? bg : en; }
    }

    /** Orthodox Easter (Gregorian date) for 1900-2099: Meeus' Julian algorithm + 13 days. */
    static Calendar easter(int y) {
        int a = y % 4, b = y % 7, c = y % 19;
        int d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = (d + e + 114) % 31 + 1;
        Calendar cal = new GregorianCalendar(y, month - 1, day);
        cal.add(Calendar.DAY_OF_MONTH, 13);
        return cal;
    }

    private static Day rel(Calendar easter, int offset, String bg, String en, boolean off) {
        Calendar c = (Calendar) easter.clone();
        c.add(Calendar.DAY_OF_MONTH, offset);
        return new Day(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), bg, en, off);
    }

    private static final Object[][] FIXED = {
            { 1, 1, "Нова година", "New Year's Day" },
            { 3, 3, "Ден на Освобождението", "Liberation Day" },
            { 5, 1, "Ден на труда", "Labour Day" },
            { 5, 6, "Гергьовден", "St George's Day" },
            { 5, 24, "Ден на българската просвета и култура", "Culture and Literacy Day" },
            { 9, 6, "Ден на Съединението", "Unification Day" },
            { 9, 22, "Ден на Независимостта", "Independence Day" },
            { 12, 24, "Бъдни вечер", "Christmas Eve" },
            { 12, 25, "Коледа", "Christmas Day" },
            { 12, 26, "Коледа", "Christmas (2nd day)" },
    };

    /**
     * Official days off for a year, sorted. A fixed holiday falling on a
     * weekend moves the day off to the next working day (Labour Code art. 154).
     */
    static List<Day> holidays(int y) {
        List<Day> out = new ArrayList<Day>();
        Calendar e = easter(y);
        out.add(rel(e, -2, "Велики петък", "Good Friday", true));
        out.add(rel(e, -1, "Велика събота", "Holy Saturday", true));
        out.add(rel(e, 0, "Великден", "Easter", true));
        out.add(rel(e, 1, "Великден", "Easter Monday", true));
        for (Object[] f : FIXED)
            out.add(new Day((Integer) f[0], (Integer) f[1], (String) f[2], (String) f[3], true));
        List<Day> moved = new ArrayList<Day>();
        for (Object[] f : FIXED) {
            Calendar c = new GregorianCalendar(y, (Integer) f[0] - 1, (Integer) f[1]);
            if (!weekend(c))
                continue;
            do
                c.add(Calendar.DAY_OF_MONTH, 1);
            while (c.get(Calendar.YEAR) == y && (weekend(c) || find(out, c) != null || find(moved, c) != null));
            if (c.get(Calendar.YEAR) == y)
                moved.add(new Day(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                        "Почивен ден", "Day off", true));
        }
        out.addAll(moved);
        sort(out);
        return out;
    }

    static boolean weekend(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    static Day find(List<Day> list, Calendar c) {
        int m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        for (Day x : list)
            if (x.month == m && x.day == d)
                return x;
        return null;
    }

    private static void sort(List<Day> l) {
        java.util.Collections.sort(l, new java.util.Comparator<Day>() {
            public int compare(Day a, Day b) {
                return (a.month * 32 + a.day) - (b.month * 32 + b.day);
            }
        });
    }

    // Major name days (fixed dates, then relative to Orthodox Easter).
    private static final Object[][] NAMES = {
            { 1, 1, "Васил, Василка, Веселин, Веселина" },
            { 1, 6, "Йордан, Йорданка, Божидар, Божидара, Богдан, Богдана" },
            { 1, 7, "Иван, Иванка, Йоан, Йоана, Ивайло, Ваня" },
            { 1, 17, "Антон, Антония" },
            { 1, 18, "Атанас, Атанаска" },
            { 1, 20, "Евтим, Евтимия" },
            { 2, 1, "Трифон" },
            { 2, 10, "Харалампи" },
            { 3, 9, "Младен, Младена" },
            { 3, 25, "Благовест, Благовеста, Блага, Благой, Евангелина" },
            { 5, 6, "Георги, Гергана, Гергин" },
            { 5, 11, "Кирил, Методий" },
            { 5, 21, "Константин, Елена, Костадин, Костадинка" },
            { 6, 29, "Петър, Петя, Петко, Павел, Павлина" },
            { 7, 20, "Илия, Илиян, Илияна" },
            { 7, 27, "Пантелей" },
            { 8, 15, "Мария, Марияна, Марин, Марио" },
            { 9, 17, "София, Вяра, Надежда, Любов" },
            { 10, 14, "Петка, Параскева" },
            { 10, 26, "Димитър, Димитрина" },
            { 11, 8, "Михаил, Михаела, Гаврил, Ангел, Ангелина, Рафаил" },
            { 11, 30, "Андрей, Андреа" },
            { 12, 4, "Варвара" },
            { 12, 5, "Сава" },
            { 12, 6, "Никола, Николай, Николина" },
            { 12, 9, "Анна, Ана" },
            { 12, 20, "Игнат, Огнян, Огняна, Пламен, Пламена" },
            { 12, 22, "Анастасия" },
            { 12, 25, "Христо, Христина, Радослав, Радост" },
            { 12, 27, "Стефан, Стефка" },
    };
    private static final Object[][] NAMES_EASTER = {
            { -43, "Тодор, Теодора" },                                   // Тодоровден
            { -8, "Лазар, Лазарина" },                                   // Лазаровден
            { -7, "Цветан, Цветана, Цветелина, Лилия, Роза, Росица, Виолета, Маргарита, Явор, Калина" },
            { 39, "Спас, Спаска" },                                      // Спасовден
    };

    /** The next `count` dates after `from` (within ~4 months) that have name days. */
    static List<Calendar> upcomingNameDays(Calendar from, int count) {
        List<Calendar> out = new ArrayList<Calendar>();
        Calendar c = (Calendar) from.clone();
        for (int i = 0; i < 120 && out.size() < count; i++) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            if (nameDays(c) != null)
                out.add((Calendar) c.clone());
        }
        return out;
    }

    /** Name days celebrated on this date (Cyrillic, or romanised in English), or null. */
    static String nameDays(Calendar c) {
        int m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        for (Object[] n : NAMES)
            if ((Integer) n[0] == m && (Integer) n[1] == d)
                sb.append(n[2]);
        Calendar e = easter(c.get(Calendar.YEAR));
        for (Object[] n : NAMES_EASTER) {
            Calendar x = (Calendar) e.clone();
            x.add(Calendar.DAY_OF_MONTH, (Integer) n[0]);
            if (x.get(Calendar.MONTH) + 1 == m && x.get(Calendar.DAY_OF_MONTH) == d)
                sb.append(sb.length() > 0 ? ", " : "").append(n[1]);
        }
        return sb.length() == 0 ? null : S.latin(sb.toString());
    }
}
