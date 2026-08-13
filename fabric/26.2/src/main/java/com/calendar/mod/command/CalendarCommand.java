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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /calendar 命令注册（26.2 Mojmap 版）。
 *
 * <p>与 1.20.1 Fabric 版差异：
 * <ul>
 *   <li>{@code ServerCommandSource} → {@link CommandSourceStack}</li>
 *   <li>{@code CommandManager.literal/argument} → {@link Commands#literal}/{@link Commands#argument}</li>
 *   <li>{@code source.getWorld()} → {@code source.getLevel()}</li>
 *   <li>{@code sendFeedback/sendError} → {@code sendSuccess/sendFailure}</li>
 *   <li>{@code hasPermissionLevel(2)} → {@code Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)}</li>
 *   <li>{@code hasPermissionLevel(0)} 默认权限，移除 requires 子句</li>
 *   <li>{@link net.minecraft.text.Text} → {@link Component}</li>
 *   <li>{@code ServerWorld} → {@link ServerLevel}</li>
 *   <li>广播同步仍调用 {@link CalendarMod#broadcastSync}（替代 Forge 的 PacketDistributor）</li>
 * </ul>
 */
public class CalendarCommand {

    /** 注册命令（由 CalendarMod.onInitialize 调用 CommandRegistrationCallback 时触发） */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("calendar");

        root.then(literal("today").executes(CalendarCommand::today));

        root.then(literal("add")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("year", IntegerArgumentType.integer(1, 99999))
                .then(argument("month", IntegerArgumentType.integer(1, 12))
                .then(argument("day", IntegerArgumentType.integer(1, 31))
                .then(argument("name", StringArgumentType.greedyString())
                .executes(CalendarCommand::addEvent))))));

        root.then(literal("remove")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("index", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                .executes(CalendarCommand::removeEvent)));

        // list 为默认权限（原 hasPermissionLevel(0)），所有玩家可用
        root.then(literal("list")
                .executes(CalendarCommand::listEvents));

        root.then(literal("sync")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(CalendarCommand::syncAll));

        root.then(literal("set")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("yearOffset")
                        .then(argument("offset", IntegerArgumentType.integer(-99999, 99999))
                        .executes(CalendarCommand::setYearOffset)))
                .then(literal("eraName")
                        .then(argument("name", StringArgumentType.greedyString())
                        .executes(CalendarCommand::setEraName))));

        root.executes(CalendarCommand::help);

        dispatcher.register(root);
    }

    private static int today(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarDate date = data.getCurrentDate();
        CalendarConfig cfg = data.getConfig();
        src.sendSuccess(() -> Component.translatable("calendarmod.command.today_format",
                cfg.getEraName(), date.getYear(), cfg.getMonthName(date.getMonth()), date.getDay()), false);
        for (CalendarEvent ev : data.getEventsForDate(date)) {
            src.sendSuccess(() -> Component.literal("  ◆ " + ev.getName() + ": " + ev.getDescription()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int addEvent(CommandContext<CommandSourceStack> ctx) {
        int year = IntegerArgumentType.getInteger(ctx, "year");
        int month = IntegerArgumentType.getInteger(ctx, "month");
        int day = IntegerArgumentType.getInteger(ctx, "day");
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarEvent event = new CalendarEvent(
                new CalendarDate(year, month, day), name, "", 0xFFFFD700, true, List.of()
        );
        data.addEvent(event);
        src.sendSuccess(() -> Component.translatable("calendarmod.message.event_added"), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int removeEvent(CommandContext<CommandSourceStack> ctx) {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        if (index < 0 || index >= data.getEvents().size()) {
            src.sendFailure(Component.translatable("calendarmod.command.invalid_index"));
            return 0;
        }
        data.removeEvent(index);
        src.sendSuccess(() -> Component.translatable("calendarmod.message.event_removed"), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int listEvents(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        List<CalendarEvent> evts = data.getEvents();
        src.sendSuccess(() -> Component.translatable("calendarmod.command.list_total", evts.size()), false);
        for (int i = 0; i < evts.size(); i++) {
            CalendarEvent ev = evts.get(i);
            String line = String.format("[%d] %d/%d/%d %s%s: %s", i,
                    ev.getDate().getYear(), ev.getDate().getMonth(), ev.getDate().getDay(),
                    ev.isFixed() ? "[每年]" : "",
                    ev.getName(), ev.getDescription());
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int syncAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        CalendarMod.broadcastSync(data, src.getServer());
        src.sendSuccess(() -> Component.translatable("calendarmod.message.synced"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setYearOffset(CommandContext<CommandSourceStack> ctx) {
        int offset = IntegerArgumentType.getInteger(ctx, "offset");
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
        CalendarSavedData data = CalendarSavedData.get(world);
        data.setYearOffset(offset);
        src.sendSuccess(() -> Component.translatable("calendarmod.command.year_offset_set", offset), true);
        CalendarMod.broadcastSync(data, src.getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static int setEraName(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();
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
        src.sendSuccess(() -> Component.translatable("calendarmod.command.era_set", name), true);
        CalendarMod.broadcastSync(data, src.getServer());
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
}
