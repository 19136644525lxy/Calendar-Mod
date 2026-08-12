package com.calendar.mod.server;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import com.calendar.mod.network.CalendarSyncPacket;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = CalendarMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoSyncScheduler {

    private static volatile boolean initialized = false;
    private static volatile long prevWorldDay = 0L;

    private AutoSyncScheduler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

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

        CalendarSyncPacket packet = new CalendarSyncPacket(
                savedData.getConfig(),
                savedData.getDayZero(),
                savedData.getYearOffset(),
                savedData.getEvents(),
                savedData.getTotalElapsedTicks()
        );
        CalendarMod.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
