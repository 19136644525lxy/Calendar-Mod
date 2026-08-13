package com.calendar.mod.data;

import com.calendar.mod.calendar.CalendarDate;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 日历事件数据模型（日期/名称/描述/颜色/是否固定/标签），NBT 序列化。
 *
 * <p>Fabric/Yarn 移植：NBT 类名替换
 * （CompoundTag→NbtCompound, ListTag→NbtList, StringTag→NbtString, Tag→NbtElement）。
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

    public NbtCompound save() {
        NbtCompound tag = new NbtCompound();
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
            NbtList tagsList = new NbtList();
            for (String t : tags) {
                if (t != null) {
                    tagsList.add(NbtString.of(t));
                }
            }
            tag.put("tags", tagsList);
        } catch (Exception e) {
            // 忽略序列化异常
        }
        return tag;
    }

    public static CalendarEvent load(NbtCompound tag) {
        try {
            int year = tag.getInt("year");
            int month = tag.getInt("month");
            int day = tag.getInt("day");
            CalendarDate date = new CalendarDate(year, month, day);

            String name = "";
            if (tag.contains("name", NbtElement.STRING_TYPE)) {
                name = tag.getString("name");
            }

            String description = "";
            if (tag.contains("description", NbtElement.STRING_TYPE)) {
                description = tag.getString("description");
            }

            int color = tag.contains("color", NbtElement.INT_TYPE) ? tag.getInt("color") : 0xFFFFFFFF;

            boolean isFixed = tag.contains("isFixed", NbtElement.BYTE_TYPE) && tag.getBoolean("isFixed");

            List<String> tags = new ArrayList<>();
            if (tag.contains("tags", NbtElement.LIST_TYPE)) {
                NbtList tagsList = tag.getList("tags", NbtElement.STRING_TYPE);
                for (int i = 0; i < tagsList.size(); i++) {
                    String t = tagsList.getString(i);
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
