package com.calendar.mod.calendar;

public interface CalendarSystem {
    String getEraName();

    int getMonthsPerYear();

    int getDaysInMonth(int year, int month);

    String getMonthName(int month);

    CalendarDate fromWorldDays(long worldDays, CalendarDate dayZeroOffset);

    long toWorldDays(CalendarDate date, CalendarDate dayZeroOffset);
}
