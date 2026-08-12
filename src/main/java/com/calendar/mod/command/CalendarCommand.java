package com.calendar.mod.command;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import com.calendar.mod.network.CalendarSyncPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = CalendarMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CalendarCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("calendar");

        root.then(Commands.literal("today").executes(CalendarCommand::today));

        root.then(Commands.literal("add")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("year", IntegerArgumentType.integer(1, 99999))
                .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                .then(Commands.argument("day", IntegerArgumentType.integer(1, 31))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(CalendarCommand::addEvent))))));

        root.then(Commands.literal("remove")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("index", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                .executes(CalendarCommand::removeEvent)));

        root.then(Commands.literal("list")
                .requires(s -> s.hasPermission(0))
                .executes(CalendarCommand::listEvents));

        root.then(Commands.literal("sync")
                .requires(s -> s.hasPermission(2))
                .executes(CalendarCommand::syncAll));

        root.then(Commands.literal("set")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("yearOffset")
                        .then(Commands.argument("offset", IntegerArgumentType.integer(-99999, 99999))
                        .executes(CalendarCommand::setYearOffset)))
                .then(Commands.literal("eraName")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(CalendarCommand::setEraName))));

        root.executes(CalendarCommand::help);

        dispatcher.register(root);
    }

    private static int today(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        CalendarDate date = data.getCurrentDate();
        CalendarConfig cfg = data.getConfig();
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.command.today_format",
                cfg.getEraName(), date.getYear(), cfg.getMonthName(date.getMonth()), date.getDay()), false);
        for (CalendarEvent ev : data.getEventsForDate(date)) {
            ctx.getSource().sendSuccess(() -> Component.literal("  ◆ " + ev.getName() + ": " + ev.getDescription()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int addEvent(CommandContext<CommandSourceStack> ctx) {
        int year = IntegerArgumentType.getInteger(ctx, "year");
        int month = IntegerArgumentType.getInteger(ctx, "month");
        int day = IntegerArgumentType.getInteger(ctx, "day");
        String name = StringArgumentType.getString(ctx, "name");
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        CalendarEvent event = new CalendarEvent(
                new CalendarDate(year, month, day), name, "", 0xFFFFD700, true, List.of()
        );
        data.addEvent(event);
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.message.event_added"), true);
        broadcastSync(data, ctx.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEvent(CommandContext<CommandSourceStack> ctx) {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        if (index < 0 || index >= data.getEvents().size()) {
            ctx.getSource().sendFailure(Component.translatable("calendarmod.command.invalid_index"));
            return 0;
        }
        data.removeEvent(index);
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.message.event_removed"), true);
        broadcastSync(data, ctx.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int listEvents(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        List<CalendarEvent> evts = data.getEvents();
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.command.list_total", evts.size()), false);
        for (int i = 0; i < evts.size(); i++) {
            CalendarEvent ev = evts.get(i);
            String line = String.format("[%d] %d/%d/%d %s%s: %s", i,
                    ev.getDate().getYear(), ev.getDate().getMonth(), ev.getDate().getDay(),
                    ev.isFixed() ? "[每年]" : "",
                    ev.getName(), ev.getDescription());
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int syncAll(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        broadcastSync(data, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.message.synced"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setYearOffset(CommandContext<CommandSourceStack> ctx) {
        int offset = IntegerArgumentType.getInteger(ctx, "offset");
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        data.setYearOffset(offset);
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.command.year_offset_set", offset), true);
        broadcastSync(data, ctx.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int setEraName(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerLevel level = ctx.getSource().getLevel();
        CalendarSavedData data = CalendarSavedData.get(level);
        CalendarConfig oldCfg = data.getConfig();
        CalendarConfig cfg = new CalendarConfig();
        cfg.setEraName(name);
        cfg.setMonthsPerYear(oldCfg.getMonthsPerYear());
        cfg.setDaysPerMonth(oldCfg.getDaysPerMonth());
        cfg.setMonthNames(oldCfg.getMonthNames());
        cfg.setStartYear(oldCfg.getStartYear());
        cfg.setWeekdayNames(oldCfg.getWeekdayNames());
        data.setConfig(cfg);
        ctx.getSource().sendSuccess(() -> Component.translatable("calendarmod.command.era_set", name), true);
        broadcastSync(data, ctx.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        String text = "Calendar 命令帮助:\n" +
                "/calendar today - 查看今日日期和事件\n" +
                "/calendar list - 列出所有事件\n" +
                "/calendar add <年> <月> <日> <名称> - 添加事件(OP)\n" +
                "/calendar remove <索引> - 删除事件(OP)\n" +
                "/calendar sync - 同步给所有玩家(OP)\n" +
                "/calendar set yearOffset <偏移> - 设置年份偏移(OP)\n" +
                "/calendar set eraName <名称> - 设置纪元名(OP)";
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void broadcastSync(CalendarSavedData data, MinecraftServer server) {
        CalendarSyncPacket pkt = new CalendarSyncPacket(
                data.getConfig(), data.getDayZero(), data.getYearOffset(),
                data.getEvents(), data.getTotalElapsedTicks()
        );
        CalendarMod.CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
    }
}
