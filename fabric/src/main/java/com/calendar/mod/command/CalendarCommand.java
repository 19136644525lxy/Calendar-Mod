package com.calendar.mod.command;

import com.calendar.mod.CalendarMod;
import com.calendar.mod.calendar.CalendarConfig;
import com.calendar.mod.calendar.CalendarDate;
import com.calendar.mod.data.CalendarEvent;
import com.calendar.mod.data.CalendarSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /calendar 命令注册（Fabric/Yarn 版）。
 *
 * <p>与 Forge 版差异：
 * <ul>
 *   <li>{@code RegisterCommandsEvent} + {@code @SubscribeEvent} →
 *       在 {@link CalendarMod#onInitialize()} 中调用 {@link CommandRegistrationCallback}</li>
 *   <li>{@code Commands.literal/argument} → {@code CommandManager.literal/argument}（静态导入）</li>
 *   <li>{@code CommandSourceStack} → {@link ServerCommandSource}</li>
 *   <li>{@code ctx.getSource().getLevel()} → {@code source.getWorld()}</li>
 *   <li>{@code sendSuccess/SendFailure} → {@code sendFeedback/sendError}</li>
 *   <li>{@code hasPermission(2)} → {@code hasPermissionLevel(2)}</li>
 *   <li>{@code Component.translatable/literal} → {@link Text#translatable}/{@link Text#literal}</li>
 *   <li>广播同步改为调用 {@link CalendarMod#broadcastSync}（替代 Forge 的 PacketDistributor）</li>
 * </ul>
 */
public class CalendarCommand {

    /** 注册命令（由 CalendarMod.onInitialize 调用 CommandRegistrationCallback 时触发） */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("calendar");

        root.then(literal("today").executes(CalendarCommand::today));

        root.then(literal("add")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("year", IntegerArgumentType.integer(1, 99999))
                .then(argument("month", IntegerArgumentType.integer(1, 12))
                .then(argument("day", IntegerArgumentType.integer(1, 31))
                .then(argument("name", StringArgumentType.greedyString())
                .executes(CalendarCommand::addEvent))))));

        root.then(literal("remove")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("index", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                .executes(CalendarCommand::removeEvent)));

        root.then(literal("list")
                .requires(s -> s.hasPermissionLevel(0))
                .executes(CalendarCommand::listEvents));

        root.then(literal("sync")
                .requires(s -> s.hasPermissionLevel(2))
                .executes(CalendarCommand::syncAll));

        root.then(literal("set")
                .requires(s -> s.hasPermissionLevel(2))
                .then(literal("yearOffset")
                        .then(argument("offset", IntegerArgumentType.integer(-99999, 99999))
                        .executes(CalendarCommand::setYearOffset)))
                .then(literal("eraName")
                        .then(argument("name", StringArgumentType.greedyString())
                        .executes(CalendarCommand::setEraName))));

        root.executes(CalendarCommand::help);

        dispatcher.register(root);
    }

    private static int today(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarDate date = data.getCurrentDate();
        CalendarConfig cfg = data.getConfig();
        src.sendFeedback(() -> Text.translatable("calendarmod.command.today_format",
                cfg.getEraName(), date.getYear(), cfg.getMonthName(date.getMonth()), date.getDay()), false);
        for (CalendarEvent ev : data.getEventsForDate(date)) {
            src.sendFeedback(() -> Text.literal("  ◆ " + ev.getName() + ": " + ev.getDescription()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int addEvent(CommandContext<ServerCommandSource> ctx) {
        int year = IntegerArgumentType.getInteger(ctx, "year");
        int month = IntegerArgumentType.getInteger(ctx, "month");
        int day = IntegerArgumentType.getInteger(ctx, "day");
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarEvent event = new CalendarEvent(
                new CalendarDate(year, month, day), name, "", 0xFFFFD700, true, List.of()
        );
        data.addEvent(event);
        src.sendFeedback(() -> Text.translatable("calendarmod.message.event_added"), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEvent(CommandContext<ServerCommandSource> ctx) {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        if (index < 0 || index >= data.getEvents().size()) {
            src.sendError(Text.translatable("calendarmod.command.invalid_index"));
            return 0;
        }
        data.removeEvent(index);
        src.sendFeedback(() -> Text.translatable("calendarmod.message.event_removed"), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int listEvents(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        List<CalendarEvent> evts = data.getEvents();
        src.sendFeedback(() -> Text.translatable("calendarmod.command.list_total", evts.size()), false);
        for (int i = 0; i < evts.size(); i++) {
            CalendarEvent ev = evts.get(i);
            String line = String.format("[%d] %d/%d/%d %s%s: %s", i,
                    ev.getDate().getYear(), ev.getDate().getMonth(), ev.getDate().getDay(),
                    ev.isFixed() ? "[每年]" : "",
                    ev.getName(), ev.getDescription());
            src.sendFeedback(() -> Text.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int syncAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarMod.broadcastSync(data, src.getServer());
        src.sendFeedback(() -> Text.translatable("calendarmod.message.synced"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setYearOffset(CommandContext<ServerCommandSource> ctx) {
        int offset = IntegerArgumentType.getInteger(ctx, "offset");
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        data.setYearOffset(offset);
        src.sendFeedback(() -> Text.translatable("calendarmod.command.year_offset_set", offset), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int setEraName(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarConfig oldCfg = data.getConfig();
        CalendarConfig cfg = new CalendarConfig();
        cfg.setEraName(name);
        cfg.setMonthsPerYear(oldCfg.getMonthsPerYear());
        cfg.setDaysPerMonth(oldCfg.getDaysPerMonth());
        cfg.setMonthNames(oldCfg.getMonthNames());
        cfg.setStartYear(oldCfg.getStartYear());
        cfg.setWeekdayNames(oldCfg.getWeekdayNames());
        data.setConfig(cfg);
        src.sendFeedback(() -> Text.translatable("calendarmod.command.era_set", name), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        String text = "Calendar 命令帮助:\n" +
                "/calendar today - 查看今日日期和事件\n" +
                "/calendar list - 列出所有事件\n" +
                "/calendar add <年> <月> <日> <名称> - 添加事件(OP)\n" +
                "/calendar remove <索引> - 删除事件(OP)\n" +
                "/calendar sync - 同步给所有玩家(OP)\n" +
                "/calendar set yearOffset <偏移> - 设置年份偏移(OP)\n" +
                "/calendar set eraName <名称> - 设置纪元名(OP)";
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return Command.SINGLE_SUCCESS;
    }
}
