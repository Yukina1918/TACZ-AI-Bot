package com.taczbotai.match;

import com.taczbotai.entity.TaczBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对局状态机。
 *
 * 指令：/taczbotai <none|cs|bf> start <分钟> | pause | end | status
 *  - start：开始计时，记录参赛者（全部 bot + 全部玩家），minutes=0 表示不限时；
 *  - 超时：对方没死绝 -> 强制杀死全部 bot 与玩家（平局）；
 *  - 提前结束：一方（同队伍的 bot+玩家）全部阵亡 -> 立即结束，公告胜方；
 *  - pause：暂停倒计时（再执行一次恢复）；
 *  - end：直接结束，不杀人。
 *
 * 对局模式同时决定 bot 的巡逻行为（none=瞎逛，cs/bf 见 TaczBotEntity.followPath）。
 */
public final class MatchManager {
    private static final AABB WHOLE = new AABB(-30000000.0D, -2048.0D, -30000000.0D,
            30000000.0D, 2048.0D, 30000000.0D);
    private static final ResourceKey<DamageType> KILL_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("taczbotai", "bot_headshot"));

    private enum State {
        IDLE, RUNNING, PAUSED
    }

    private static State state = State.IDLE;
    private static String mode = "none";
    private static long endGameTime;
    private static long pauseStartedAt;
    private static long pausedTotal;
    private static final Set<String> sides = new HashSet<>();
    private static final List<UUID> participants = new ArrayList<>();

    private MatchManager() {
    }

    /** 对局进行中返回模式（none/cs/bf），未开局返回 null。 */
    @Nullable
    public static String activeMode(Level level) {
        return state == State.IDLE ? null : mode;
    }

    public static String statusText() {
        return "mode=" + mode + " state=" + state + " remaining=" + remainingSeconds() + "s sides=" + sides;
    }

    public static int start(ServerLevel level, String newMode, int minutes) {
        MinecraftServer server = level.getServer();
        state = State.RUNNING;
        mode = newMode;
        pausedTotal = 0L;
        pauseStartedAt = 0L;
        long now = level.getGameTime();
        endGameTime = minutes > 0 ? now + minutes * 60L * 20L : 0L;

        sides.clear();
        participants.clear();
        for (ServerLevel l : server.getAllLevels()) {
            for (TaczBotEntity bot : l.getEntitiesOfClass(TaczBotEntity.class, WHOLE)) {
                participants.add(bot.getUUID());
                String team = bot.getBotTeamName();
                if (!team.isEmpty()) {
                    sides.add(team);
                }
            }
        }
        // 阵营 = 有 bot 的队伍 ∪ 有玩家的队伍（这样 1 真人 vs N bot 的 PVE 也能判胜负）
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            participants.add(player.getUUID());
            if (player.getTeam() != null && !player.getTeam().getName().isEmpty()) {
                sides.add(player.getTeam().getName());
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.translatable("taczbotai.match.started", newMode,
                minutes > 0 ? minutes : 0).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    public static int pause(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (state == State.RUNNING) {
            state = State.PAUSED;
            pauseStartedAt = level.getGameTime();
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("taczbotai.match.paused"), false);
            return 1;
        }
        if (state == State.PAUSED) {
            long paused = level.getGameTime() - pauseStartedAt;
            pausedTotal += paused;
            if (endGameTime > 0L) {
                endGameTime += paused;
            }
            state = State.RUNNING;
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("taczbotai.match.resumed"), false);
            return 1;
        }
        return 0;
    }

    public static int end(ServerLevel level, boolean killEveryone) {
        MinecraftServer server = level.getServer();
        if (state == State.IDLE) {
            return 0;
        }
        finishMatch(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("taczbotai.match.ended").withStyle(ChatFormatting.GRAY), false);
        reset();
        return 1;
    }

    public static void reset() {
        state = State.IDLE;
        mode = "none";
        endGameTime = 0L;
        pauseStartedAt = 0L;
        pausedTotal = 0L;
        sides.clear();
        participants.clear();
    }

    private static long remainingSeconds() {
        if (state == State.IDLE || endGameTime <= 0L) {
            return -1L;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        long now = server == null ? 0L : server.overworld().getGameTime();
        return Math.max(0L, (endGameTime - now) / 20L);
    }

    /** 每个服务端 tick 调用：处理超时与“一方全灭”提前结束。 */
    public static void tick(MinecraftServer server) {
        if (state != State.RUNNING) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();

        if (endGameTime > 0L && now >= endGameTime) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("taczbotai.match.timeout").withStyle(ChatFormatting.RED), false);
            finishMatch(server);
            broadcast("taczbotai.match.draw");
            reset();
            return;
        }

        // 每个阵营的存活数 = 活着的 bot + 活着的玩家（旁观者不算）
        Map<String, Integer> aliveBySide = new LinkedHashMap<>();
        for (String side : sides) {
            aliveBySide.put(side, 0);
        }
        int totalAliveBots = 0;
        for (ServerLevel l : server.getAllLevels()) {
            for (TaczBotEntity bot : l.getEntitiesOfClass(TaczBotEntity.class, WHOLE)) {
                if (!bot.isAlive()) {
                    continue;
                }
                totalAliveBots++;
                String team = bot.getBotTeamName();
                if (aliveBySide.containsKey(team)) {
                    aliveBySide.merge(team, 1, Integer::sum);
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            String team = player.getTeam() == null ? "" : player.getTeam().getName();
            if (aliveBySide.containsKey(team)) {
                aliveBySide.merge(team, 1, Integer::sum);
            }
        }

        // 没有任何阵营可判（bot 与玩家都没队伍）：bot 清空即平局收场
        if (sides.isEmpty()) {
            if (totalAliveBots == 0) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("taczbotai.match.draw").withStyle(ChatFormatting.GRAY), false);
                reset();
            }
            return;
        }

        long survivingSides = aliveBySide.values().stream().filter(v -> v > 0).count();
        if (survivingSides > 1) {
            return;
        }
        finishMatch(server);
        String winner = aliveBySide.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey).findFirst().orElse("");
        server.getPlayerList().broadcastSystemMessage(
                winner.isEmpty()
                        ? Component.translatable("taczbotai.match.draw").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("taczbotai.match.winner", winner).withStyle(ChatFormatting.GOLD),
                false);
        reset();
    }

    private static void broadcast(String key) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(key), false);
        }
    }

    /** 对局结束处理：杀 bot、按配置传送玩家 / 退队 / 清背包。 */
    private static void finishMatch(MinecraftServer server) {
        killAllBots(server);
        boolean teleport = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_TELEPORT);
        boolean leaveTeam = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_LEAVE_TEAM);
        boolean clearInv = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_CLEAR_INVENTORY);
        double x = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_X);
        double y = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_Y);
        double z = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.MATCH_END_Z);
        ServerLevel overworld = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (leaveTeam && player.getTeam() instanceof net.minecraft.world.scores.PlayerTeam team) {
                server.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
            }
            if (clearInv) {
                player.getInventory().clearContent();
            }
            if (teleport) {
                player.teleportTo(overworld, x, y, z, player.getYRot(), player.getXRot());
            }
        }
    }

    private static void killAllBots(MinecraftServer server) {
        // 用原版 genericKill：死亡信息走 vanilla 键，不会出现 "%1$s 被 %2$s 一枪爆头" 这种缺参数的占位符
        DamageSource source = server.overworld().damageSources().genericKill();
        for (ServerLevel level : server.getAllLevels()) {
            for (TaczBotEntity bot : level.getEntitiesOfClass(TaczBotEntity.class, WHOLE)) {
                forceKill(bot, source);
            }
        }
    }

    private static void forceKill(LivingEntity entity, DamageSource source) {
        if (!entity.isAlive()) {
            return;
        }
        entity.hurt(source, 10000.0F);
        if (entity.isAlive()) {
            entity.kill();
        }
    }
}
