package com.calendar.mod.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 历法配置数据模型（可序列化、可网络同步）。
 * 存储玩家自定义的历法参数：纪元名、月份数、每月天数、月份名、起始年份、星期名。
 */
public class CalendarConfig {

    /** 默认配置：12 月 × 30 天，星历纪元 */
    public static final CalendarConfig DEFAULT = createDefault();

    private String eraName;
    private int monthsPerYear;
    private int[] daysPerMonth;
    private List<String> monthNames;
    private int startYear;
    private List<String> weekdayNames;

    public CalendarConfig() {
        this.eraName = "星历";
        this.monthsPerYear = 12;
        this.daysPerMonth = new int[12];
        for (int i = 0; i < 12; i++) daysPerMonth[i] = 30;
        this.monthNames = new ArrayList<>();
        for (int i = 1; i <= 12; i++) monthNames.add("第" + i + "月");
        this.startYear = 1;
        this.weekdayNames = List.of("日", "一", "二", "三", "四", "五", "六");
    }

    private static CalendarConfig createDefault() {
        return new CalendarConfig();
    }

    /** 从 NBT 反序列化 */
    public static CalendarConfig load(CompoundTag tag) {
        CalendarConfig config = new CalendarConfig();
        if (tag.contains("eraName", Tag.TAG_STRING)) {
            config.eraName = tag.getString("eraName");
        }
        if (tag.contains("monthsPerYear", Tag.TAG_INT)) {
            config.monthsPerYear = Math.max(1, tag.getInt("monthsPerYear"));
        }
        // 每月天数数组
        if (tag.contains("daysPerMonth", Tag.TAG_LIST)) {
            ListTag list = tag.getList("daysPerMonth", Tag.TAG_INT);
            config.daysPerMonth = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                config.daysPerMonth[i] = Math.max(1, list.getInt(i));
            }
        } else {
            // 默认填充
            config.daysPerMonth = new int[config.monthsPerYear];
            for (int i = 0; i < config.monthsPerYear; i++) config.daysPerMonth[i] = 30;
        }
        // 月份名列表
        if (tag.contains("monthNames", Tag.TAG_LIST)) {
            ListTag list = tag.getList("monthNames", Tag.TAG_STRING);
            config.monthNames = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                config.monthNames.add(list.getString(i));
            }
        }
        if (tag.contains("startYear", Tag.TAG_INT)) {
            config.startYear = tag.getInt("startYear");
        }
        // 星期名列表
        if (tag.contains("weekdayNames", Tag.TAG_LIST)) {
            ListTag list = tag.getList("weekdayNames", Tag.TAG_STRING);
            config.weekdayNames = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                config.weekdayNames.add(list.getString(i));
            }
        }
        return config;
    }

    /** 序列化到 NBT */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("eraName", eraName);
        tag.putInt("monthsPerYear", monthsPerYear);
        ListTag daysList = new ListTag();
        for (int days : daysPerMonth) {
            daysList.add(net.minecraft.nbt.IntTag.valueOf(days));
        }
        tag.put("daysPerMonth", daysList);
        ListTag namesList = new ListTag();
        for (String name : monthNames) {
            namesList.add(StringTag.valueOf(name));
        }
        tag.put("monthNames", namesList);
        tag.putInt("startYear", startYear);
        ListTag weekdayList = new ListTag();
        for (String name : weekdayNames) {
            weekdayList.add(StringTag.valueOf(name));
        }
        tag.put("weekdayNames", weekdayList);
        return tag;
    }

    // ===== Getters =====

    public String getEraName() { return eraName; }
    public int getMonthsPerYear() { return monthsPerYear; }
    public int[] getDaysPerMonth() { return daysPerMonth; }
    public List<String> getMonthNames() { return Collections.unmodifiableList(monthNames); }
    public int getStartYear() { return startYear; }
    public List<String> getWeekdayNames() { return Collections.unmodifiableList(weekdayNames); }

    /** 获取指定月份的天数（安全访问，越界返回 30） */
    public int getDaysInMonth(int month) {
        if (month < 1 || month > daysPerMonth.length) return 30;
        return daysPerMonth[month - 1];
    }

    /** 获取月份名（安全访问，越界返回"第X月"） */
    public String getMonthName(int month) {
        if (month < 1 || month > monthNames.size()) return "第" + month + "月";
        return monthNames.get(month - 1);
    }

    /** 获取星期名（index 0=第一列） */
    public String getWeekdayName(int index) {
        if (index < 0 || index >= weekdayNames.size()) return "";
        return weekdayNames.get(index);
    }

    /** 星期数（一周几天） */
    public int getWeekdaysCount() {
        return weekdayNames.size();
    }

    /** 每年总天数 */
    public int getDaysPerYear() {
        int total = 0;
        for (int d : daysPerMonth) total += d;
        return total;
    }

    // ===== Setters（修改后需要重新同步） =====

    public void setEraName(String eraName) { this.eraName = eraName; }
    public void setMonthsPerYear(int months) { this.monthsPerYear = Math.max(1, months); }
    public void setDaysPerMonth(int[] days) { this.daysPerMonth = days; }
    public void setMonthNames(List<String> names) { this.monthNames = new ArrayList<>(names); }
    public void setStartYear(int year) { this.startYear = year; }
    public void setWeekdayNames(List<String> names) { this.weekdayNames = new ArrayList<>(names); }
}
