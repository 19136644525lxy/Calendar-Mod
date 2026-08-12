package com.calendar.mod.network;

import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.calendar.CalendarSystem;
import com.calendar.mod.calendar.ConfigurableCalendar;
import com.calendar.mod.data.CalendarEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端缓存：持有服务端同步的日历数据快照。
 * 界面渲染时从此处读取，避免每帧访问服务端。
 * 线程安全：volatile 字段 + synchronized 写入。
 */
public class CalendarClientCache {
    public static final CalendarClientCache INSTANCE = new CalendarClientCache();

    private volatile CalendarConfig config = new CalendarConfig();
    private volatile CalendarDate dayZero = new CalendarDate(1, 1, 1);
    private volatile int yearOffset = 0;
    private volatile List<CalendarEvent> events = Collections.emptyList();
    private volatile long totalElapsedTicks = 0;

    private CalendarClientCache() {}

    /** 网络包调用：原子更新所有缓存数据 */
    public synchronized void update(CalendarConfig config, CalendarDate dayZero,
                                    int yearOffset, List<CalendarEvent> events,
                                    long totalElapsedTicks) {
        this.config = config;
        this.dayZero = dayZero;
        this.yearOffset = yearOffset;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.totalElapsedTicks = totalElapsedTicks;
    }

    /**
     * 基于实际累计游戏时间计算当前日期。
     * 优先使用独立追踪的 totalElapsedTicks，不受 /time 指令影响。
     *
     * @param fallbackWorldDay 当缓存未同步时使用的回退值（当前未使用）
     */
    public CalendarDate getCurrentDate(long fallbackWorldDay) {
        CalendarSystem sys = new ConfigurableCalendar(config);
        long worldDay = totalElapsedTicks / 24000L;
        CalendarDate base = sys.fromWorldDays(worldDay, dayZero);
        return new CalendarDate(base.getYear() + yearOffset, base.getMonth(), base.getDay());
    }

    /**
     * 基于实际累计游戏时间计算当前日期（推荐使用）。
     */
    public CalendarDate getCurrentDate() {
        return getCurrentDate(0L);
    }

    public CalendarSystem getSystem() { return new ConfigurableCalendar(config); }
    public CalendarConfig getConfig() { return config; }
    public CalendarDate getDayZero() { return dayZero; }
    public int getYearOffset() { return yearOffset; }
    public List<CalendarEvent> getEvents() { return events; }
    public long getTotalElapsedTicks() { return totalElapsedTicks; }

    /** 获取实际累计游戏天数 */
    public long getElapsedWorldDays() { return totalElapsedTicks / 24000L; }
}
