package com.calendar.mod.data;

import com.calendar.mod.calendar.CalendarDate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 日历事件数据模型（日期/名称/描述/颜色/是否固定/标签），NBT 序列化。
 *
 * <p>26.2/Mojang 映射：NBT 类名使用 CompoundTag/ListTag/StringTag/Tag。
 */
public class CalendarEvent {
    private final CalendarDate date;
    private final String name;
    private final String description;
    private final int color;
    private final boolean isFixed;
    private final List<String> tags;

    public CalendarEvent(CalendarDate date, String name, String description, int color, boolean isFixed, List<String> tags) {
        this.date = date;
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.color = color;
        this.isFixed = isFixed;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public CalendarDate date() {
        return date;
    }

    /** date() 的 JavaBean 别名 */
    public CalendarDate getDate() {
        return date;
    }

    public String name() {
        return name;
    }

    /** name() 的 JavaBean 别名 */
    public String getName() {
        return name;
    }

    public String description() {
        return description;
    }

    /** description() 的 JavaBean 别名 */
    public String getDescription() {
        return description;
    }

    public int color() {
        return color;
    }

    /** color() 的 JavaBean 别名 */
    public int getColor() {
        return color;
    }

    public boolean isFixed() {
        return isFixed;
    }

    public List<String> tags() {
        return Collections.unmodifiableList(tags);
    }

    /** tags() 的 JavaBean 别名 */
    public List<String> getTags() {
        return tags();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        try {
            if (date != null) {
                tag.putInt("year", date.getYear());
                tag.putInt("month", date.getMonth());
                tag.putInt("day", date.getDay());
            }
            tag.putString("name", name);
            tag.putString("description", description);
            tag.putInt("color", color);
            tag.putBoolean("isFixed", isFixed);
            ListTag tagsList = new ListTag();
            for (String t : tags) {
                if (t != null) {
                    tagsList.add(StringTag.valueOf(t));
                }
            }
            tag.put("tags", tagsList);
        } catch (Exception e) {
            // 忽略序列化异常
        }
        return tag;
    }

    public static CalendarEvent load(CompoundTag tag) {
        try {
            int year = tag.getIntOr("year", 0);
            int month = tag.getIntOr("month", 0);
            int day = tag.getIntOr("day", 0);
            CalendarDate date = new CalendarDate(year, month, day);

            String name = "";
            if (tag.contains("name")) {
                name = tag.getStringOr("name", "");
            }

            String description = "";
            if (tag.contains("description")) {
                description = tag.getStringOr("description", "");
            }

            int color = tag.contains("color") ? tag.getIntOr("color", 0) : 0xFFFFFFFF;

            boolean isFixed = tag.contains("isFixed") && tag.getBooleanOr("isFixed", false);

            List<String> tags = new ArrayList<>();
            if (tag.contains("tags")) {
                ListTag tagsList = tag.getListOrEmpty("tags");
                for (int i = 0; i < tagsList.size(); i++) {
                    String t = tagsList.getStringOr(i, "");
                    if (t != null) {
                        tags.add(t);
                    }
                }
            }

            return new CalendarEvent(date, name, description, color, isFixed, tags);
        } catch (Exception e) {
            return new CalendarEvent(new CalendarDate(1, 1, 1), "", "", 0xFFFFFFFF, false, new ArrayList<>());
        }
    }
}
