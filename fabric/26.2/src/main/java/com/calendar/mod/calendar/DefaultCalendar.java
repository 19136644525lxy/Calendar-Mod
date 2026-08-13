package com.calendar.mod.calendar;

public class DefaultCalendar implements CalendarSystem {
    private static final int MONTHS_PER_YEAR = 12;
    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_YEAR = MONTHS_PER_YEAR * DAYS_PER_MONTH;

    @Override
    public String getEraName() {
        return "星历";
    }

    @Override
    public int getMonthsPerYear() {
        return MONTHS_PER_YEAR;
    }

    @Override
    public int getDaysInMonth(int year, int month) {
        return DAYS_PER_MONTH;
    }

    @Override
    public String getMonthName(int month) {
        return "第" + month + "月";
    }

    @Override
    public CalendarDate fromWorldDays(long worldDays, CalendarDate dayZeroOffset) {
        long totalDays = toDaysFromZero(dayZeroOffset) + worldDays;
        long year = totalDays / DAYS_PER_YEAR;
        long remainder = totalDays % DAYS_PER_YEAR;
        if (remainder < 0) {
            year--;
            remainder += DAYS_PER_YEAR;
        }
        int month = (int) (remainder / DAYS_PER_MONTH) + 1;
        int day = (int) (remainder % DAYS_PER_MONTH) + 1;
        return new CalendarDate((int) year, month, day);
    }

    @Override
    public long toWorldDays(CalendarDate date, CalendarDate dayZeroOffset) {
        return toDaysFromZero(date) - toDaysFromZero(dayZeroOffset);
    }

    private long toDaysFromZero(CalendarDate date) {
        return (long) date.getYear() * DAYS_PER_YEAR
                + (long) (date.getMonth() - 1) * DAYS_PER_MONTH
                + (date.getDay() - 1);
    }
}
