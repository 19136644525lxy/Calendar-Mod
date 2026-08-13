package com.calendar.mod.server;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 服务端 tick 监听器：检测日期变更时广播今日事件 + 发送同步包（26.2 Mojmap 版）。
 *
 * <p>与 1.20.1 Fabric 版差异：
 * <ul>
 *   <li>{@code ServerWorld} → {@link ServerLevel}</li>
 *   <li>{@code server.getOverworld()} → {@code server.overworld()}</li>
 *   <li>{@code getPlayerManager().broadcast} → {@code getPlayerList().broadcastSystemMessage}</li>
 *   <li>{@link net.minecraft.text.Text} → {@link Component}，{@code MutableText} → {@link MutableComponent}</li>
 *   <li>{@code ServerTickEvent} + {@code @SubscribeEvent} 仍由 Fabric API 的
 *       {@code ServerTickEvents.END_SERVER_TICK} 替代</li>
 *   <li>{@code CalendarMod.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt)} →
 *       {@link CalendarMod#broadcastSync}（遍历玩家逐个发送）</li>
 * </ul>
 */
public class AutoSyncScheduler {

    private static volatile boolean initialized = false;
    private static volatile long prevWorldDay = 0L;

    private AutoSyncScheduler() {}

    /** 由 CalendarMod.onInitialize 中 ServerTickEvents.END_SERVER_TICK 回调调用 */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        // 使用实际累计游戏时间计算天数（不受 /time 影响）
        CalendarSavedData savedData = CalendarSavedData.get(overworld);
        long currentWorldDay = savedData.getElapsedWorldDays();

        if (!initialized) {
            prevWorldDay = currentWorldDay;
            initialized = true;
            return;
        }

        if (currentWorldDay != prevWorldDay) {
            long dayDiff = currentWorldDay - prevWorldDay;
            if (dayDiff > 0) {
                for (long offset = 1; offset <= dayDiff; offset++) {
                    doDailyRollover(prevWorldDay + offset, server, overworld);
                }
            } else {
                doDailyRollover(currentWorldDay, server, overworld);
            }
            prevWorldDay = currentWorldDay;
        }
    }

    /** 日期变更时的处理：广播事件 + 同步数据 */
    private static void doDailyRollover(long worldDay, MinecraftServer server, ServerLevel overworld) {
        CalendarSavedData savedData = CalendarSavedData.get(overworld);
        CalendarDate currentDate = savedData.getCurrentDate();

        List<CalendarEvent> todayEvents = savedData.getEventsForDate(currentDate);
        for (CalendarEvent event : todayEvents) {
            MutableComponent eventComponent = Component.literal("[")
                    .append(Component.translatable("calendarmod.screen.title"))
                    .append(Component.literal("] "))
                    .append(Component.translatable("calendarmod.message.event_today", event.getName()));

            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                eventComponent = eventComponent.append(Component.literal(" - "))
                        .append(Component.translatable("calendarmod.message.event_description", event.getDescription()));
            }

            server.getPlayerList().broadcastSystemMessage(eventComponent, false);
        }

        CalendarMod.broadcastSync(savedData, server);
    }

    /** 重置初始化状态（服务器停止时调用） */
    public static void reset() {
        initialized = false;
        prevWorldDay = 0L;
    }
}
