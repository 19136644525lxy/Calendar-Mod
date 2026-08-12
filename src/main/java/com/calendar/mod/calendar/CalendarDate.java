package com.calendar.mod.calendar;

import java.util.Objects;

public final class CalendarDate implements Comparable<CalendarDate> {
    private final int year;
    private final int month;
    private final int day;

    public CalendarDate(int year, int month, int day) {
        if (year < 1) {
            throw new IllegalArgumentException("年份必须 >= 1");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("月份必须在 1-12 之间");
        }
        if (day < 1) {
            throw new IllegalArgumentException("日期必须大于 0");
        }
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalendarDate that)) return false;
        return year == that.year && month == that.month && day == that.day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }

    @Override
    public String toString() {
        return String.format("%d-%02d-%02d", year, month, day);
    }

    @Override
    public int compareTo(CalendarDate other) {
        int y = Integer.compare(this.year, other.year);
        if (y != 0) return y;
        int m = Integer.compare(this.month, other.month);
        if (m != 0) return m;
        return Integer.compare(this.day, other.day);
    }
}
