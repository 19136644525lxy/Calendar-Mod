package com.calendar.mod.calendar;

/**
 * 可配置历法实现。
 * 从 CalendarConfig 读取参数，动态计算日期转换。
 * 替代硬编码的 DefaultCalendar，支持玩家自定义历法规则。
 */
public class ConfigurableCalendar implements CalendarSystem {

    private final CalendarConfig config;

    public ConfigurableCalendar(CalendarConfig config) {
        this.config = config;
    }

    @Override
    public String getEraName() {
        return config.getEraName();
    }

    @Override
    public int getMonthsPerYear() {
        return config.getMonthsPerYear();
    }

    @Override
    public int getDaysInMonth(int year, int month) {
        return config.getDaysInMonth(month);
    }

    @Override
    public String getMonthName(int month) {
        return config.getMonthName(month);
    }

    @Override
    public CalendarDate fromWorldDays(long worldDays, CalendarDate dayZeroOffset) {
        long totalDays = toDaysFromZero(dayZeroOffset) + worldDays;
        int daysPerYear = config.getDaysPerYear();
        if (daysPerYear <= 0) daysPerYear = 360;

        long year = totalDays / daysPerYear;
        long remainder = totalDays % daysPerYear;
        if (remainder < 0) {
            year--;
            remainder += daysPerYear;
        }
        // 确保年份 >= 1
        if (year < 1) {
            remainder += (1 - year) * daysPerYear;
            year = 1;
        }

        // 逐月拆分
        int month = 1;
        int day = 1;
        long remaining = remainder;
        for (int m = 1; m <= config.getMonthsPerYear(); m++) {
            int dim = config.getDaysInMonth(m);
            if (remaining < dim) {
                month = m;
                day = (int) remaining + 1;
                break;
            }
            remaining -= dim;
        }
        return new CalendarDate((int) year, month, day);
    }

    @Override
    public long toWorldDays(CalendarDate date, CalendarDate dayZeroOffset) {
        return toDaysFromZero(date) - toDaysFromZero(dayZeroOffset);
    }

    /** 计算从纪元第 0 天开始的累计天数 */
    private long toDaysFromZero(CalendarDate date) {
        long days = 0;
        int daysPerYear = config.getDaysPerYear();
        if (daysPerYear <= 0) daysPerYear = 360;

        // 完整年份
        days += (long) date.getYear() * daysPerYear;
        // 当年已完成月份
        for (int m = 1; m < date.getMonth() && m <= config.getMonthsPerYear(); m++) {
            days += config.getDaysInMonth(m);
        }
        // 当月天数（day 是 1-based，所以 -1）
        days += date.getDay() - 1;
        return days;
    }

    public CalendarConfig getConfig() {
        return config;
    }
}
