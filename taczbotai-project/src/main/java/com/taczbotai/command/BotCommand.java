package com.taczbotai.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.taczbotai.config.BotConfig;
import com.taczbotai.entity.TaczBotEntity;
import com.taczbotai.path.BotPath;
import com.taczbotai.path.PathManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /taczbotai 指令：路径录制 / 管理、生成生物、队伍与难度档位。 */
public final class BotCommand {
    /** 正在录制的玩家 -> 轨迹。 */
    private static final Map<UUID, Recording> RECORDINGS = new HashMap<>();
    /** 指令不指定武器时使用的默认枪：无配件 1911。 */
    private static final String DEFAULT_SPAWN_WEAPON = "tacz:m1911";

    private BotCommand() {
    }

    private static final class Recording {
        final String name;
        final String dimension;
        final String mode;
        final List<com.taczbotai.path.PathPoint> points = new ArrayList<>();
        int ticks;

        Recording(String name, String dimension, String mode) {
            this.name = name;
            this.dimension = dimension;
            this.mode = mode;
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("taczbotai")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("record")
                        .then(Commands.literal("cs").then(recordModeTree("cs")))
                        .then(Commands.literal("bf").then(recordModeTree("bf")))
                        .then(Commands.literal("start")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> startRecording(ctx.getSource(), "cs",
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("node")
                                .executes(ctx -> addMoveNode(ctx.getSource()))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> addStopNode(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(Commands.literal("undo").executes(ctx -> undoNode(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> recordStatus(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> stopRecording(ctx.getSource()))))
                .then(Commands.literal("path")
                        .then(Commands.literal("list").executes(ctx -> listPaths(ctx.getSource())))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(BotCommand::suggestPathNames)
                                        .executes(ctx -> deletePath(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(BotCommand::suggestPathNames)
                                        .executes(ctx -> assignPath(ctx.getSource(), StringArgumentType.getString(ctx, "name"), 32.0D))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                                .suggests(BotCommand::suggestRadius)
                                                .executes(ctx -> assignPath(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("clear").executes(ctx -> clearPath(ctx.getSource()))))
                .then(Commands.literal("config")
                        .then(Commands.literal("list").executes(ctx -> listConfig(ctx.getSource())))
                        .then(Commands.literal("get")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(BotCommand::suggestConfigKeys)
                                        .executes(ctx -> getConfig(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(BotCommand::suggestConfigKeys)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .suggests(BotCommand::suggestConfigValue)
                                                .executes(ctx -> setConfig(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value")))))))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), 1, "", null))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(java.util.List.of("1", "2", "3", "5", "10"), b))
                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), "", null))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(BotCommand::suggestTeams)
                                        .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "team"), null))
                                        .then(Commands.argument("weapon", StringArgumentType.greedyString())
                                                .suggests(BotCommand::suggestGunIds)
                                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "team"),
                                                        StringArgumentType.getString(ctx, "weapon")))))))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("team")
                        .then(Commands.literal("set")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(BotCommand::suggestTeams)
                                        .executes(ctx -> setTeam(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), SCOPE_NEAREST))
                                        .then(Commands.literal("all").executes(ctx -> setTeam(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"), SCOPE_ALL)))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1000))
                                                .suggests(BotCommand::suggestRadius)
                                                .executes(ctx -> setTeam(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "team"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> setTeam(ctx.getSource(), "", SCOPE_NEAREST))
                                .then(Commands.literal("all").executes(ctx -> setTeam(ctx.getSource(), "", SCOPE_ALL)))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1000))
                                        .suggests(BotCommand::suggestRadius)
                                        .executes(ctx -> setTeam(ctx.getSource(), "",
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("friendlyfire")
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .suggests(BotCommand::suggestBooleans)
                                        .executes(ctx -> setFriendlyFire(ctx.getSource(),
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value")))))
                        .then(Commands.literal("showname")
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .suggests(BotCommand::suggestBooleans)
                                        .executes(ctx -> setShowName(ctx.getSource(),
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value"), SCOPE_NEAREST))
                                        .then(Commands.literal("all").executes(ctx -> setShowName(ctx.getSource(),
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value"), SCOPE_ALL)))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setShowName(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("namemode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggest(java.util.List.of("always", "team", "hidden"), b))
                                        .executes(ctx -> setShowMode(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mode"), SCOPE_NEAREST))
                                        .then(Commands.literal("all").executes(ctx -> setShowMode(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mode"), SCOPE_ALL)))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setShowMode(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "mode"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                .suggest(COLOR_PRESETS, builder))
                                        .executes(ctx -> setNameColor(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "color"), SCOPE_NEAREST))
                                        .then(Commands.literal("all").executes(ctx -> setNameColor(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "color"), SCOPE_ALL)))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setNameColor(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "color"),
                                                        IntegerArgumentType.getInteger(ctx, "radius")))))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(java.util.List.of("1", "2", "3", "4", "5"), b))
                                .executes(ctx -> setTier(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tier")))))
                .then(Commands.literal("skin")
                        .then(Commands.argument("skin", IntegerArgumentType.integer(0, 4))
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(java.util.List.of("0", "1", "2", "3", "4"), b))
                                .executes(ctx -> setSkin(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "skin")))))
                .then(Commands.literal("gun")
                        .executes(ctx -> saveHeldGunNbt(ctx.getSource()))
                        .then(Commands.literal("clear").executes(ctx -> clearGunNbt(ctx.getSource())))
                        .then(Commands.literal("show").executes(ctx -> showGunNbt(ctx.getSource()))))
                .then(Commands.literal("summon")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> summon(ctx.getSource(),
                                                net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(ctx, "pos"),
                                                IntegerArgumentType.getInteger(ctx, "count"), "", null))
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(BotCommand::suggestTeams)
                                                .executes(ctx -> summon(ctx.getSource(),
                                                        net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(ctx, "pos"),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "team"), null))
                                                .then(Commands.argument("weapon", StringArgumentType.greedyString())
                                                        .suggests(BotCommand::suggestGunIds)
                                                        .executes(ctx -> summon(ctx.getSource(),
                                                                net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(ctx, "pos"),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                StringArgumentType.getString(ctx, "team"),
                                                                StringArgumentType.getString(ctx, "weapon"))))))))
                .then(matchTree("none"))
                .then(matchTree("cs"))
                .then(matchTree("bf"))
                .then(Commands.literal("equip").executes(ctx -> reEquip(ctx.getSource()))));
    }

    // ------------------------------------------------------------------
    // 录制
    // ------------------------------------------------------------------

    private static int startRecording(CommandSourceStack source, String mode, String name) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = new Recording(name, player.level().dimension().location().toString(), mode);
        recording.points.add(com.taczbotai.path.PathPoint.move(player.position()));
        RECORDINGS.put(player.getUUID(), recording);
        boolean nodeMode = !"dense".equalsIgnoreCase(BotConfig.RECORD_MODE.get());
        source.sendSuccess(() -> Component.translatable(nodeMode ? "taczbotai.command.record.started_node"
                : "taczbotai.command.record.started", name), false);
        return 1;
    }

    /** 节点模式：在当前位置手动记一个节点（默认绑键 V，或直接敲指令）。 */
    private static int addMoveNode(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = RECORDINGS.get(player.getUUID());
        if (recording == null) {
            source.sendFailure(Component.translatable("taczbotai.command.record.none"));
            return 0;
        }
        recording.points.add(com.taczbotai.path.PathPoint.move(player.position()));
        final int count = recording.points.size();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.record.node", count), false);
        return 1;
    }

    private static int undoNode(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = RECORDINGS.get(player.getUUID());
        if (recording == null) {
            source.sendFailure(Component.translatable("taczbotai.command.record.none"));
            return 0;
        }
        if (recording.points.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.record.empty"));
            return 0;
        }
        recording.points.remove(recording.points.size() - 1);
        final int count = recording.points.size();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.record.undo", count), false);
        return 1;
    }

    private static int recordStatus(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = RECORDINGS.get(player.getUUID());
        if (recording == null) {
            source.sendFailure(Component.translatable("taczbotai.command.record.none"));
            return 0;
        }
        final int count = recording.points.size();
        final String name = recording.name;
        source.sendSuccess(() -> Component.translatable("taczbotai.command.record.status", name, count), false);
        return 1;
    }

    private static int stopRecording(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = RECORDINGS.remove(player.getUUID());
        if (recording == null) {
            source.sendFailure(Component.translatable("taczbotai.command.record.none"));
            return 0;
        }
        // 收尾：把当前位置作为终点（节点模式下这一步很关键）
        Vec3 now = player.position();
        Vec3 last = recording.points.isEmpty() ? null : recording.points.get(recording.points.size() - 1).pos();
        if (last == null || last.distanceToSqr(now) > 1.0D) {
            recording.points.add(com.taczbotai.path.PathPoint.move(now));
        }
        if (recording.points.size() < 2) {
            source.sendFailure(Component.translatable("taczbotai.command.record.too_short"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        PathManager.save(level, new BotPath(recording.name, recording.dimension, recording.mode, List.copyOf(recording.points)));
        source.sendSuccess(() -> Component.translatable("taczbotai.command.record.saved", recording.name,
                recording.points.size(), PathManager.folder(level.getServer()).toString()), false);
        return 1;
    }

    /**
     * 由服务端 tick 调用。
     * node 模式（默认）不再自动采样，只保留玩家手动丢的节点；
     * dense 模式才按 recordIntervalTicks 自动采样。
     */
    public static void tickRecording(net.minecraft.server.MinecraftServer server) {
        if (RECORDINGS.isEmpty()) {
            return;
        }
        boolean dense = "dense".equalsIgnoreCase(BotConfig.RECORD_MODE.get());
        int interval = Math.max(1, BotConfig.RECORD_INTERVAL.get());
        RECORDINGS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved()) {
                return true;
            }
            if (!dense) {
                return false;
            }
            Recording recording = entry.getValue();
            if (++recording.ticks >= interval) {
                recording.ticks = 0;
                Vec3 last = recording.points.isEmpty() ? null : recording.points.get(recording.points.size() - 1).pos();
                Vec3 now = player.position();
                if (last == null || last.distanceToSqr(now) > 0.04D) {
                    recording.points.add(com.taczbotai.path.PathPoint.move(now));
                    if (recording.points.size() > 20000) {
                        recording.points.remove(0);
                    }
                }
            }
            return false;
        });
    }

    public static boolean isRecording(UUID player) {
        return RECORDINGS.containsKey(player);
    }

    // ------------------------------------------------------------------
    // 路径管理
    // ------------------------------------------------------------------

    private static int listPaths(CommandSourceStack source) {
        var paths = PathManager.paths(source.getLevel());
        if (paths.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("taczbotai.command.path.empty",
                    PathManager.folder(source.getServer()).toString()), false);
            return 0;
        }
        StringBuilder builder = new StringBuilder();
        for (BotPath path : paths) {
            builder.append("\n - ").append(path.name()).append(" (").append(path.points().size()).append(" pts, ")
                    .append(path.dimension()).append(')');
        }
        String text = builder.toString();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.path.list", text), false);
        return paths.size();
    }

    private static int deletePath(CommandSourceStack source, String name) {
        boolean deleted = PathManager.delete(source.getLevel(), name);
        if (!deleted) {
            source.sendFailure(Component.translatable("taczbotai.command.path.missing", name));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("taczbotai.command.path.deleted", name), false);
        return 1;
    }

    /** 把路径分配给范围内的**所有**生物（原来是只给最近一只）。 */
    private static int assignPath(CommandSourceStack source, String name, double radius) {
        if (!PathManager.all(source.getLevel()).containsKey(name)) {
            source.sendFailure(Component.translatable("taczbotai.command.path.missing", name));
            return 0;
        }
        List<TaczBotEntity> bots = botsInRange(source, radius);
        if (bots.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        for (TaczBotEntity bot : bots) {
            bot.setPathName(name);
        }
        final int count = bots.size();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.path.assigned_many", name, count), false);
        return count;
    }

    private static int clearPath(CommandSourceStack source) {
        TaczBotEntity bot = nearestBot(source, 32.0D);
        if (bot == null) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        bot.setPathName("");
        source.sendSuccess(() -> Component.translatable("taczbotai.command.path.cleared"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // 生物
    // ------------------------------------------------------------------

    private static int spawn(CommandSourceStack source, int count, String team, @Nullable String weapon) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        int spawned = 0;
        String effectiveTeam = "";
        for (int i = 0; i < count; i++) {
            TaczBotEntity bot = com.taczbotai.registry.ModEntities.TACZ_BOT.get().create(level);
            if (bot == null) {
                continue;
            }
            double offsetX = (level.getRandom().nextDouble() - 0.5D) * 2.5D;
            double offsetZ = (level.getRandom().nextDouble() - 0.5D) * 2.5D;
            bot.moveTo(pos.x + offsetX, pos.y, pos.z + offsetZ, level.getRandom().nextFloat() * 360.0F, 0.0F);
            bot.finalizeSpawn(level, level.getCurrentDifficultyAt(bot.blockPosition()), MobSpawnType.COMMAND, null, null);
            if (!team.isEmpty()) {
                bot.setBotTeam(team);
            }
            effectiveTeam = bot.getBotTeamName();
            // 指令语义：不写武器参数 => 配置里有 gunNbt 就用它，否则用无配件 1911；
            // 写 id => 该枪无配件；写 SNBT => 完全按 NBT。
            String weaponSpec = (weapon == null || weapon.isBlank()) ? defaultSpawnSpec() : weapon;
            try {
                bot.equipGunFromCommand(weaponSpec);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                source.sendFailure(Component.translatable("taczbotai.command.weapon.bad"));
                return 0;
            }
            level.addFreshEntity(bot);
            spawned++;
        }
        final int result = spawned;
        final String teamMessage = effectiveTeam.isEmpty() ? "-" : effectiveTeam;
        source.sendSuccess(() -> Component.translatable("taczbotai.command.spawned_team", result, teamMessage), true);
        return spawned;
    }

    /** /taczbotai info：把最近生物的关键状态打出来，方便排查队伍/弹药问题。 */
    /** 指令不指定武器时的默认枪：优先用配置里的 gunNbt，否则用无配件 1911。 */
    private static String defaultSpawnSpec() {
        String nbt = BotConfig.GUN_NBT.get();
        return (nbt != null && !nbt.isBlank()) ? nbt : DEFAULT_SPAWN_WEAPON;
    }

    private static int info(CommandSourceStack source) {
        TaczBotEntity bot = nearestBot(source, 64.0D);
        if (bot == null) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        net.minecraft.world.item.ItemStack gun = bot.getMainHandItem();
        com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(gun);
        com.tacz.guns.api.entity.IGunOperator operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(bot);
        String gunId = iGun == null ? "-" : iGun.getGunId(gun).toString();
        String gunText = gunId;
        if (iGun != null) {
            String type = com.taczbotai.entity.BotGunProfile.typeOf(gun);
            gunText = gunId + " [" + (type == null ? "?" : type)
                    + " 射程" + (int) com.taczbotai.entity.BotGunProfile.fireRange(gun)
                    + " 换弹" + com.taczbotai.entity.BotGunProfile.reloadSeconds(gun) + "s]";
        }
        final String gunLabel = gunText;
        int magazine = iGun == null ? 0 : iGun.getCurrentAmmoCount(gun);
        Entity src = source.getEntity();
        String yourTeam = src != null && src.getTeam() != null ? src.getTeam().getName() : "-";
        boolean enemy = src instanceof net.minecraft.world.entity.LivingEntity living && bot.isValidTarget(living);
        boolean reloading = operator.getSynReloadState().getStateType().isReloading();
        String recordedTeam = bot.teamId().isEmpty() ? "-" : bot.teamId();
        String scoreboardTeam = bot.getTeam() == null ? "none" : bot.getTeam().getName();
        String teamText = recordedTeam + " (scoreboard:" + scoreboardTeam + ")";
        final String enemyText = String.valueOf(enemy);
        final boolean reloadingText = reloading;
        source.sendSuccess(() -> Component.translatable("taczbotai.command.info",
                gunLabel, magazine, bot.getAmmoReserve(), teamText,
                yourTeam, String.valueOf(BotConfig.friendlyFire()), String.valueOf(BotConfig.tier()),
                String.valueOf(reloadingText), enemyText), false);
        return 1;
    }

    /** /taczbotai team set <队伍> [半径|all] / team clear [半径|all] —— 设置队伍（支持作用范围）。 */
    private static int setTeam(CommandSourceStack source, String team, int scope) {
        List<TaczBotEntity> bots = botsInScope(source, scope);
        if (bots.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        for (TaczBotEntity bot : bots) {
            bot.setBotTeam(team);
        }
        final int count = bots.size();
        final Component scopeText = scopeText(scope);
        source.sendSuccess(() -> team.isEmpty()
                ? Component.translatable("taczbotai.command.team.cleared", count, scopeText)
                : Component.translatable("taczbotai.command.team.set", count, team, scopeText), false);
        return count;
    }

    /** /taczbotai team friendlyfire <true|false> —— 全局友伤开关。 */
    private static int setFriendlyFire(CommandSourceStack source, boolean value) {
        BotConfig.FRIENDLY_FIRE.set(value);
        BotConfig.saveIfLoaded();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.team.friendlyfire",
                Component.translatable(value ? "taczbotai.gui.on" : "taczbotai.gui.off")), false);
        return 1;
    }

    /**
     * 作用范围：
     *  - {@link #SCOPE_NEAREST} = 只作用于最近一只
     *  - {@link #SCOPE_ALL} = 当前维度**全部**生物（含远处）
     *  - 正数 = 以玩家/命令执行点为圆心的半径（格）
     */
    private static final int SCOPE_NEAREST = 0;
    private static final int SCOPE_ALL = -1;

    /** /taczbotai team showname <true|false> [半径|all] —— 头顶名字是否显示。 */
    private static int setShowName(CommandSourceStack source, boolean value, int scope) {
        List<TaczBotEntity> bots = botsInScope(source, scope);
        if (bots.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        for (TaczBotEntity bot : bots) {
            bot.setBotNameVisible(value);
        }
        final int count = bots.size();
        final Component scopeText = scopeText(scope);
        source.sendSuccess(() -> Component.translatable("taczbotai.command.team.showname", count,
                Component.translatable(value ? "taczbotai.gui.on" : "taczbotai.gui.off"), scopeText), false);
        return count;
    }

    /** /taczbotai team namemode <always|team|hidden> [半径|all] —— 头顶名字显示模式。 */
    private static int setShowMode(CommandSourceStack source, String modeName, int scope) {
        int mode = TaczBotEntity.parseNameMode(modeName);
        List<TaczBotEntity> bots = botsInScope(source, scope);
        if (bots.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        for (TaczBotEntity bot : bots) {
            bot.setBotNameMode(mode);
        }
        final int count = bots.size();
        final String modeKey = switch (mode) {
            case TaczBotEntity.NAME_MODE_ALWAYS -> "taczbotai.name_mode.always";
            case TaczBotEntity.NAME_MODE_HIDDEN -> "taczbotai.name_mode.hidden";
            default -> "taczbotai.name_mode.team";
        };
        final Component scopeText = scopeText(scope);
        source.sendSuccess(() -> Component.translatable("taczbotai.command.team.namemode", count,
                Component.translatable(modeKey), scopeText), false);
        return count;
    }

    private static Component scopeText(int scope) {
        if (scope == SCOPE_ALL) {
            return Component.translatable("taczbotai.command.team.scope_all");
        }
        if (scope > 0) {
            return Component.translatable("taczbotai.command.team.scope_radius", scope);
        }
        return Component.translatable("taczbotai.command.team.scope_nearest");
    }

    private static List<TaczBotEntity> botsInScope(CommandSourceStack source, int scope) {
        if (scope == SCOPE_NEAREST) {
            return single(source);
        }
        if (scope == SCOPE_ALL) {
            // 当前维度全部生物（含远处）：用超大 AABB 走分区块查询，比逐实体遍历稳
            net.minecraft.world.phys.AABB whole = new net.minecraft.world.phys.AABB(
                    -30000000.0D, -2048.0D, -30000000.0D,
                    30000000.0D, 2048.0D, 30000000.0D);
            return source.getLevel().getEntitiesOfClass(TaczBotEntity.class, whole);
        }
        return botsInRange(source, scope);
    }

    /** 原版 16 种聊天颜色，用于 /taczbotai team color 的补全与校验。 */
    private static final List<String> COLOR_PRESETS = List.of(
            "red", "dark_red", "gold", "yellow",
            "dark_green", "green", "aqua", "dark_aqua",
            "blue", "dark_blue", "light_purple", "dark_purple",
            "white", "gray", "dark_gray", "black");

    /** /taczbotai team color <颜色> [半径|all] —— 头顶名字颜色（只接受原版 16 色）。 */
    private static int setNameColor(CommandSourceStack source, String colorName, int scope) {
        net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.getByName(colorName);
        if (color == null || !color.isColor()) {
            source.sendFailure(Component.translatable("taczbotai.command.team.bad_color", colorName));
            return 0;
        }
        List<TaczBotEntity> bots = botsInScope(source, scope);
        if (bots.isEmpty()) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        for (TaczBotEntity bot : bots) {
            bot.setBotNameColor(color);
            bot.setBotNameVisible(true);
        }
        final int count = bots.size();
        final String colorText = color.getName();
        final Component scopeText = scopeText(scope);
        source.sendSuccess(() -> Component.translatable("taczbotai.command.team.color", count, colorText, scopeText), false);
        return count;
    }

    private static List<TaczBotEntity> single(CommandSourceStack source) {
        TaczBotEntity bot = nearestBot(source, 32.0D);
        return bot == null ? List.of() : List.of(bot);
    }

    private static List<TaczBotEntity> botsInRange(CommandSourceStack source, double radius) {
        Vec3 pos = source.getPosition();
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        return source.getLevel().getEntitiesOfClass(TaczBotEntity.class, area);
    }

    private static int setTier(CommandSourceStack source, int tier) {
        BotConfig.ATTACK_TIER.set(tier);
        BotConfig.saveIfLoaded();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.tier.set", tier), false);
        return 1;
    }

    private static int setSkin(CommandSourceStack source, int skin) {
        TaczBotEntity bot = nearestBot(source, 32.0D);
        if (bot == null) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        bot.setSkin(skin);
        source.sendSuccess(() -> Component.translatable("taczbotai.command.skin.set", skin), false);
        return 1;
    }

    private static int reEquip(CommandSourceStack source) {
        TaczBotEntity bot = nearestBot(source, 32.0D);
        if (bot == null) {
            source.sendFailure(Component.translatable("taczbotai.command.no_bot"));
            return 0;
        }
        bot.equipConfiguredGun();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.equip.done"), false);
        return 1;
    }

    /** /taczbotai gun：把你主手的 TACZ 枪械 NBT 存进配置，之后 equip / 生成蛋都用它。 */
    private static int saveHeldGunNbt(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        if (iGun == null) {
            source.sendFailure(Component.translatable("taczbotai.command.gun.not_gun"));
            return 0;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) {
            tag = new net.minecraft.nbt.CompoundTag();
        }
        String snbt = tag.toString();
        BotConfig.GUN_NBT.set(snbt);
        BotConfig.saveIfLoaded();
        final String gunId = iGun.getGunId(stack).toString();
        final int length = snbt.length();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.gun.saved", gunId, length), false);
        return 1;
    }

    private static int clearGunNbt(CommandSourceStack source) {
        BotConfig.GUN_NBT.set("");
        BotConfig.saveIfLoaded();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.gun.cleared"), false);
        return 1;
    }

    private static int showGunNbt(CommandSourceStack source) {
        String snbt = BotConfig.GUN_NBT.get();
        if (snbt == null || snbt.isBlank()) {
            source.sendSuccess(() -> Component.translatable("taczbotai.command.gun.empty"), false);
            return 0;
        }
        final int length = snbt.length();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.gun.show", length, snbt), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // 配置读写（SERVER 配置：只有房主 / 服务器管理员能改）
    // ------------------------------------------------------------------

    private static int listConfig(CommandSourceStack source) {
        java.util.Map<String, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>> values = BotConfig.allValues();
        StringBuilder sb = new StringBuilder();
        for (String key : values.keySet()) {
            sb.append("\n ").append(key).append(" = ").append(BotConfig.getByKey(key));
        }
        final String text = sb.toString();
        final int count = values.size();
        source.sendSuccess(() -> Component.translatable("taczbotai.command.config.list", count, text), false);
        return count;
    }

    private static int getConfig(CommandSourceStack source, String key) {
        String value = BotConfig.getByKey(key);
        if (value == null) {
            source.sendFailure(Component.translatable("taczbotai.command.config.missing", key));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("taczbotai.command.config.get", key, value), false);
        return 1;
    }

    private static int setConfig(CommandSourceStack source, String key, String value) {
        String error = BotConfig.setByKey(key, value);
        if (error == null) {
            source.sendSuccess(() -> Component.translatable("taczbotai.command.config.set", key, value), false);
            return 1;
        }
        source.sendFailure(switch (error) {
            case "missing" -> Component.translatable("taczbotai.command.config.missing", key);
            case "value" -> Component.translatable("taczbotai.command.config.value", key);
            case "type" -> Component.translatable("taczbotai.command.config.type", key);
            default -> Component.translatable("taczbotai.command.config.denied");
        });
        return 0;
    }

    // ------------------------------------------------------------------
    // 指令预设（Tab 补全）
    // ------------------------------------------------------------------

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTeams(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>(java.util.List.of("red", "blue", "green", "yellow"));
        try {
            names.addAll(ctx.getSource().getServer().getScoreboard().getTeamNames());
        } catch (Exception ignored) {
            // 控制台/客户端上下文拿不到记分板时只给默认队名
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(names, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPathNames(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        try {
            names.addAll(PathManager.all(ctx.getSource().getLevel()).keySet());
        } catch (Exception ignored) {
            // 忽略
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(names, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRadius(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider
                .suggest(java.util.List.of("16", "32", "64", "128", "256"), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBooleans(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("true", "false"), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestGunIds(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        try {
            com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().forEach(entry -> ids.add(entry.getKey().toString()));
        } catch (Exception ignored) {
            // TACZ 数据还没加载
        }
        if (ids.isEmpty()) {
            ids.add("tacz:m1911");
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(ids, builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestConfigKeys(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(BotConfig.allValues().keySet(), builder);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestConfigValue(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String key;
        try {
            key = StringArgumentType.getString(ctx, "key");
        } catch (Exception e) {
            return builder.buildFuture();
        }
        java.util.List<String> values = switch (key) {
            case "bot.skinMode" -> java.util.List.of("random", "1", "2", "3", "4", "vanilla");
            case "path.recordMode" -> java.util.List.of("node", "dense");
            case "path.pathMode" -> java.util.List.of("loop", "pingpong", "once");
            case "bot.nameTagMode" -> java.util.List.of("always", "team", "hidden");
            case "bot.nameTagColor" -> COLOR_PRESETS;
            case "attack.attackTier" -> java.util.List.of("1", "2", "3", "4", "5");
            default -> {
                net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> configValue = BotConfig.allValues().get(key);
                if (configValue != null && configValue.getDefault() instanceof Boolean) {
                    yield java.util.List.of("true", "false");
                }
                yield java.util.List.of();
            }
        };
        return net.minecraft.commands.SharedSuggestionProvider.suggest(values, builder);
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> recordModeTree(String mode) {
        return Commands.literal("start")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> startRecording(ctx.getSource(), mode, StringArgumentType.getString(ctx, "name"))))
                .then(Commands.literal("stop").executes(ctx -> stopRecording(ctx.getSource())));
    }

    /** 记录一个「停留节点」（由按键弹窗里的确认按钮通过指令调用）。 */
    private static int addStopNode(CommandSourceStack source, int seconds) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("taczbotai.command.need_player"));
            return 0;
        }
        Recording recording = RECORDINGS.get(player.getUUID());
        if (recording == null) {
            source.sendFailure(Component.translatable("taczbotai.command.record.none"));
            return 0;
        }
        recording.points.add(com.taczbotai.path.PathPoint.stay(player.position(), seconds));
        final int count = recording.points.size();
        final int stay = seconds;
        source.sendSuccess(() -> Component.translatable("taczbotai.command.record.node_stop", count, stay), false);
        return 1;
    }

    /** /taczbotai <none|cs|bf> start <分钟>|pause|end|status */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> matchTree(String mode) {
        return Commands.literal(mode)
                .then(Commands.literal("start")
                        .executes(ctx -> com.taczbotai.match.MatchManager.start(ctx.getSource().getLevel(), mode, 0))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 600))
                                .executes(ctx -> com.taczbotai.match.MatchManager.start(ctx.getSource().getLevel(), mode,
                                        IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("pause").executes(ctx ->
                        com.taczbotai.match.MatchManager.pause(ctx.getSource().getLevel())))
                .then(Commands.literal("end").executes(ctx ->
                        com.taczbotai.match.MatchManager.end(ctx.getSource().getLevel(), false)))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal(com.taczbotai.match.MatchManager.statusText()), false);
                    return 1;
                }));
    }

    private static int summon(CommandSourceStack source, Vec3 pos, int count, String team, @Nullable String weapon) {
        ServerLevel level = source.getLevel();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            TaczBotEntity bot = com.taczbotai.registry.ModEntities.TACZ_BOT.get().create(level);
            if (bot == null) {
                continue;
            }
            double offsetX = (level.getRandom().nextDouble() - 0.5D) * 2.5D;
            double offsetZ = (level.getRandom().nextDouble() - 0.5D) * 2.5D;
            bot.moveTo(pos.x + offsetX, pos.y, pos.z + offsetZ, level.getRandom().nextFloat() * 360.0F, 0.0F);
            bot.finalizeSpawn(level, level.getCurrentDifficultyAt(bot.blockPosition()), MobSpawnType.COMMAND, null, null);
            if (!team.isEmpty()) {
                bot.setBotTeam(team);
            }
            String weaponSpec = (weapon == null || weapon.isBlank()) ? defaultSpawnSpec() : weapon;
            try {
                bot.equipGunFromCommand(weaponSpec);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                source.sendFailure(Component.translatable("taczbotai.command.weapon.bad"));
                return 0;
            }
            level.addFreshEntity(bot);
            spawned++;
        }
        final int result = spawned;
        final String teamMessage = team.isEmpty() ? "-" : team;
        source.sendSuccess(() -> Component.translatable("taczbotai.command.spawned_team", result, teamMessage), true);
        return spawned;
    }
    @Nullable
    private static TaczBotEntity nearestBot(CommandSourceStack source, double radius) {
        Vec3 pos = source.getPosition();
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        List<TaczBotEntity> bots = source.getLevel().getEntitiesOfClass(TaczBotEntity.class, area);
        TaczBotEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TaczBotEntity bot : bots) {
            double distance = bot.position().distanceToSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bot;
            }
        }
        return best;
    }
}
