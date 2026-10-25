package com.taczbotai.config;

import com.taczbotai.TaczBotAi;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 全部可通过游戏内 GUI（模组列表 -> 配置）或配置文件修改。
 * 使用 Forge 自带 ForgeConfigSpec，不额外引入 Cloth Config / YACL，零额外依赖、启动开销最小。
 */
public final class BotConfig {
    public static final ForgeConfigSpec SPEC;

    // ---- 攻击 ----
    public static final ForgeConfigSpec.IntValue ATTACK_TIER;
    public static final ForgeConfigSpec.BooleanValue INSTANT_KILL_ENABLED;
    public static final ForgeConfigSpec.IntValue INSTANT_KILL_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ATTACK_HOSTILE_MOBS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOSS_BLACKLIST;
    public static final ForgeConfigSpec.DoubleValue TARGET_RANGE;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_LINE_OF_SIGHT;
    public static final ForgeConfigSpec.DoubleValue LOST_SIGHT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHASE_GIVE_UP_SECONDS;
    public static final ForgeConfigSpec.IntValue REACTION_TICKS;
    public static final ForgeConfigSpec.DoubleValue FIRE_RANGE;
    public static final ForgeConfigSpec.DoubleValue RANGE_CLOSE;
    public static final ForgeConfigSpec.DoubleValue RANGE_MID;
    public static final ForgeConfigSpec.DoubleValue RANGE_FAR;

    // ---- 换弹 ----
    public static final ForgeConfigSpec.DoubleValue RELOAD_PISTOL;
    public static final ForgeConfigSpec.DoubleValue RELOAD_RIFLE;
    public static final ForgeConfigSpec.DoubleValue RELOAD_MARKSMAN;
    public static final ForgeConfigSpec.DoubleValue RELOAD_SNIPER;
    public static final ForgeConfigSpec.DoubleValue RELOAD_MG;
    public static final ForgeConfigSpec.DoubleValue RELOAD_SHOTGUN;
    public static final ForgeConfigSpec.DoubleValue RELOAD_HEAVY;
    public static final ForgeConfigSpec.DoubleValue RELOAD_OTHER;

    public static final ForgeConfigSpec.DoubleValue AIM_ERROR_T1;
    public static final ForgeConfigSpec.DoubleValue AIM_ERROR_T2;
    public static final ForgeConfigSpec.DoubleValue AIM_ERROR_T3;
    public static final ForgeConfigSpec.DoubleValue AIM_ERROR_T4;
    public static final ForgeConfigSpec.DoubleValue AIM_ERROR_T5;

    // ---- 队伍 ----
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_TEAM;

    // ---- 生物本体 ----
    public static final ForgeConfigSpec.DoubleValue HEALTH;
    public static final ForgeConfigSpec.DoubleValue SPEED;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_RANGE;
    public static final ForgeConfigSpec.ConfigValue<String> GUN_ID;
    public static final ForgeConfigSpec.ConfigValue<String> GUN_NBT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATTACHMENTS;
    public static final ForgeConfigSpec.IntValue MAGAZINE_AMMO;
    public static final ForgeConfigSpec.IntValue AMMO_RESERVE;
    public static final ForgeConfigSpec.BooleanValue INFINITE_AMMO;
    public static final ForgeConfigSpec.ConfigValue<String> SKIN_MODE;
    public static final ForgeConfigSpec.BooleanValue PERSISTENCE;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_TAG_MODE;
    public static final ForgeConfigSpec.ConfigValue<String> NAME_TAG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAMES;

    // ---- 路径 ----
    public static final ForgeConfigSpec.IntValue RECORD_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue PATROL_SPEED;
    public static final ForgeConfigSpec.ConfigValue<String> RECORD_MODE;
    public static final ForgeConfigSpec.ConfigValue<String> PATH_MODE;

    // ---- 播报 ----
    public static final ForgeConfigSpec.BooleanValue KILL_MESSAGE;

    // ---- 对局结束处理 ----
    public static final ForgeConfigSpec.BooleanValue MATCH_END_TELEPORT;
    public static final ForgeConfigSpec.DoubleValue MATCH_END_X;
    public static final ForgeConfigSpec.DoubleValue MATCH_END_Y;
    public static final ForgeConfigSpec.DoubleValue MATCH_END_Z;
    public static final ForgeConfigSpec.BooleanValue MATCH_END_LEAVE_TEAM;
    public static final ForgeConfigSpec.BooleanValue MATCH_END_CLEAR_INVENTORY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Attack settings").push("attack");
        ATTACK_TIER = b.comment("Attack accuracy tier, 1 (worst) .. 5 (best). Tier 5 also enables the instant-kill roll.")
                .defineInRange("attackTier", 3, 1, 5);
        INSTANT_KILL_ENABLED = b.comment("Enable the tier-5 instant-kill (headshot) roll.")
                .define("instantKillEnabled", true);
        INSTANT_KILL_CHANCE = b.comment("Percent chance per bullet hit (tier 5 only) to deal 100% of the target's max health.")
                .defineInRange("instantKillChance", 10, 0, 100);
        ATTACK_HOSTILE_MOBS = b.comment("Also target hostile monsters that are not in the bot's team.")
                .define("attackHostileMobs", true);
        BOSS_BLACKLIST = b.comment("Entity ids that can never be instantly killed (bosses).",
                        "Vanilla/Forge boss tag forge:bosses is always respected in addition to this list.")
                .defineList("bossBlacklist", List.of(
                                "minecraft:ender_dragon",
                                "minecraft:wither",
                                "minecraft:warden"),
                        o -> o instanceof String);
        TARGET_RANGE = b.comment("Range (blocks) used when searching for a target.")
                .defineInRange("targetRange", 48.0D, 4.0D, 256.0D);
        REQUIRE_LINE_OF_SIGHT = b.comment("Bots must actually SEE a target before acquiring it (no wall hack).",
                        "Once acquired, a bot keeps pursuing the last seen position even if the target hides.")
                .define("requireLineOfSight", true);
        LOST_SIGHT_SECONDS = b.comment("If a target stays out of sight for this many seconds, the bot gives up chasing it.")
                .defineInRange("lostSightSeconds", 15.0D, 1.0D, 300.0D);
        CHASE_GIVE_UP_SECONDS = b.comment("If the bot can SEE the target but cannot get into shooting position",
                        "for this many seconds, it also gives up (e.g. target across a chasm).")
                .defineInRange("chaseGiveUpSeconds", 20.0D, 1.0D, 300.0D);
        REACTION_TICKS = b.comment("Delay in ticks between spotting a target and opening fire.")
                .defineInRange("reactionTicks", 12, 0, 200);
        FIRE_RANGE = b.comment("Legacy global fire range. No longer used: fire range now follows the gun type.",
                        "See rangeClose / rangeMid / rangeFar below.")
                .defineInRange("fireRange", 32.0D, 2.0D, 256.0D);

        b.comment("Effective fire range by gun category (blocks).",
                        "close  = pistol / smg",
                        "mid    = machine gun / assault rifle / shotgun / other",
                        "far    = marksman rifle / sniper rifle")
                .push("range");
        RANGE_CLOSE = b.defineInRange("close", 12.0D, 2.0D, 128.0D);
        RANGE_MID = b.defineInRange("mid", 48.0D, 2.0D, 256.0D);
        RANGE_FAR = b.defineInRange("far", 72.0D, 2.0D, 512.0D);
        b.pop();

        b.comment("Aim error (degrees, standard deviation) for each tier.",
                        "Higher = wider spread = worse accuracy.")
                .push("aimError");
        AIM_ERROR_T1 = b.defineInRange("tier1", 7.0D, 0.0D, 45.0D);
        AIM_ERROR_T2 = b.defineInRange("tier2", 4.0D, 0.0D, 45.0D);
        AIM_ERROR_T3 = b.defineInRange("tier3", 2.0D, 0.0D, 45.0D);
        AIM_ERROR_T4 = b.defineInRange("tier4", 0.8D, 0.0D, 45.0D);
        AIM_ERROR_T5 = b.defineInRange("tier5", 0.15D, 0.0D, 45.0D);
        b.pop();
        b.pop();

        b.comment("Team settings, based on vanilla scoreboard teams (/team).").push("team");
        FRIENDLY_FIRE = b.comment("If false, bullets fired by a bot never damage entities of the same team.")
                .define("friendlyFire", false);
        DEFAULT_TEAM = b.comment("Scoreboard team assigned to newly spawned bots. Empty = no team (hostile to everyone).")
                .define("defaultTeam", "red");
        b.pop();

        b.comment("Bot body / equipment.").push("bot");
        HEALTH = b.comment("Max health.").defineInRange("health", 20.0D, 1.0D, 1024.0D);
        SPEED = b.comment("Movement speed attribute.").defineInRange("speed", 0.32D, 0.05D, 1.0D);
        FOLLOW_RANGE = b.comment("Follow range attribute.").defineInRange("followRange", 32.0D, 4.0D, 128.0D);
        GUN_ID = b.comment("Gun id given to the bot on spawn, e.g. tacz:m1911.")
                .define("gunId", "tacz:m1911");
        GUN_NBT = b.comment("Full gun item NBT (SNBT). When not empty it wins over gunId + attachments.",
                        "Fill it with /taczbotai gun (reads the gun in your main hand) or the GUI button")
                .define("gunNbt", "");
        ATTACHMENTS = b.comment("Attachments installed on the spawn gun, format TYPE=attachmentId.",
                        "TYPE is one of SCOPE, MUZZLE, STOCK, GRIP, LASER, EXTENDED_MAG.",
                        "Leave empty for an unmodified gun.")
                .defineList("attachments", List.of(), o -> o instanceof String);
        MAGAZINE_AMMO = b.comment("Ammo loaded into the magazine on spawn.").defineInRange("magazineAmmo", 0, 0, 9999);
        AMMO_RESERVE = b.comment("Reserve ammo the bot can reload from.").defineInRange("ammoReserve", 600, 0, 100000);
        INFINITE_AMMO = b.comment("If true, the bot never runs out of reserve ammo (it still reloads normally).")
                .define("infiniteAmmo", true);
        SKIN_MODE = b.comment("Skin selection: random | 1 | 2 | 3 | 4 | vanilla.")
                .define("skinMode", "random");
        PERSISTENCE = b.comment("Bots never despawn when the player walks away.")
                .define("persistenceRequired", true);
        NAME_TAG_MODE = b.comment("Nametag mode for newly spawned bots.",
                        "always = every player sees it",
                        "team   = only players in the same scoreboard team see it",
                        "hidden = nobody sees it")
                .define("nameTagMode", "team");
        NAME_TAG_COLOR = b.comment("Nametag color for newly spawned bots (MC color name, e.g. red / gold / aqua).",
                        "Empty = default white.")
                .define("nameTagColor", "");
        NAMES = b.comment("Bot names. Each new bot picks one of these at random.",
                        "Leave empty to use the default translated entity name.")
                .defineList("names", List.of(
                                "bot_Yukina_1918",
                                "bot_Xiexiaoai_74",
                                "bot_Wuliyincanaiyi",
                                "bot_Dafeiyu_2026"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Reload time (seconds) by gun type. The bot's magazine is topped up exactly when this timer ends.",
                        "pistol/smg = 1.0, rifle = 1.5, marksman = 2.5, sniper = 2.5, mg = 5.0",
                        "shotgun and heavy weapons (rpg / launcher / minigun / heavy) = 6.0")
                .push("reload");
        RELOAD_PISTOL = b.defineInRange("pistolSeconds", 1.0D, 0.1D, 60.0D);
        RELOAD_RIFLE = b.defineInRange("rifleSeconds", 1.5D, 0.1D, 60.0D);
        RELOAD_MARKSMAN = b.defineInRange("marksmanSeconds", 2.5D, 0.1D, 60.0D);
        RELOAD_SNIPER = b.defineInRange("sniperSeconds", 2.5D, 0.1D, 60.0D);
        RELOAD_MG = b.defineInRange("mgSeconds", 5.0D, 0.1D, 60.0D);
        RELOAD_SHOTGUN = b.defineInRange("shotgunSeconds", 6.0D, 0.1D, 60.0D);
        RELOAD_HEAVY = b.defineInRange("heavySeconds", 6.0D, 0.1D, 60.0D);
        RELOAD_OTHER = b.defineInRange("otherSeconds", 2.0D, 0.1D, 60.0D);
        b.pop();

        b.comment("Patrol path settings.").push("path");
        RECORD_INTERVAL = b.comment("Record one point every N ticks while recording (4 = 5 points per second).")
                .defineInRange("recordIntervalTicks", 4, 1, 40);
        PATROL_SPEED = b.comment("Path following speed multiplier.").defineInRange("patrolSpeed", 1.0D, 0.1D, 3.0D);
        RECORD_MODE = b.comment("Recording mode.",
                        "node  = only the points you drop with the hotkey (default V) plus start/end; small files, smooth patrol",
                        "dense = sample every tick-interval automatically (old behaviour)")
                .define("recordMode", "node");
        PATH_MODE = b.comment("How a bot walks an assigned path.",
                        "loop     = walk the points in order, then jump back to the first point and repeat",
                        "once     = walk to the last point once, then stand there and keep guarding",
                        "pingpong = walk forward to the end, then turn around and walk back (no map crossing)")
                .define("pathMode", "loop");
        b.pop();

        b.comment("Chat messages.").push("message");
        KILL_MESSAGE = b.comment("Broadcast a message when one taczbotai bot kills another taczbotai bot.")
                .define("killMessageEnabled", true);
        b.pop();

        b.comment("What happens when a match ends (timeout or early win).",
                        "Bots are always killed; players are teleported / optionally leave team & clear inventory.")
                .push("match");
        MATCH_END_TELEPORT = b.comment("Teleport all players to the point below when a match ends.")
                .define("endTeleport", true);
        MATCH_END_X = b.defineInRange("endX", 0.0D, -30000000.0D, 30000000.0D);
        MATCH_END_Y = b.defineInRange("endY", 64.0D, -2048.0D, 2048.0D);
        MATCH_END_Z = b.defineInRange("endZ", 0.0D, -30000000.0D, 30000000.0D);
        MATCH_END_LEAVE_TEAM = b.comment("Remove players from their scoreboard team when a match ends.")
                .define("endLeaveTeam", false);
        MATCH_END_CLEAR_INVENTORY = b.comment("Clear player inventories when a match ends.")
                .define("endClearInventory", false);
        b.pop();

        SPEC = b.build();
    }

    private BotConfig() {
    }

    /** 按档位返回瞄准角度误差（标准差，单位：度）。 */
    public static float aimErrorForTier(int tier) {
        return switch (tier) {
            case 1 -> AIM_ERROR_T1.get().floatValue();
            case 2 -> AIM_ERROR_T2.get().floatValue();
            case 3 -> AIM_ERROR_T3.get().floatValue();
            case 4 -> AIM_ERROR_T4.get().floatValue();
            default -> AIM_ERROR_T5.get().floatValue();
        };
    }

    public static int tier() {
        return ATTACK_TIER.get();
    }

    public static boolean friendlyFire() {
        return FRIENDLY_FIRE.get();
    }

    public static void saveIfLoaded() {
        try {
            SPEC.save();
        } catch (Exception e) {
            TaczBotAi.LOGGER.warn("[taczbotai] failed to save config: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 配置自省（供 /taczbotai config 指令与 GUI 使用）
    // ------------------------------------------------------------------

    /**
     * 客户端在读不到服务器配置时（比如在主菜单打开 GUI、还没连服务器）退回默认值，
     * 避免 "Cannot get config value before config is loaded" 抛出来。
     */
    @SuppressWarnings("unchecked")
    public static <T> T guiValue(ForgeConfigSpec.ConfigValue<T> value) {
        try {
            T current = value.get();
            return current != null ? current : value.getDefault();
        } catch (Exception e) {
            return value.getDefault();
        }
    }

    /** 扁平化所有配置键（用 "." 连接，如 attack.range.close）。 */
    public static java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> allValues() {
        java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> out = new java.util.LinkedHashMap<>();
        try {
            collect("", SPEC.getValues(), out);
        } catch (Exception e) {
            TaczBotAi.LOGGER.warn("[taczbotai] cannot enumerate config keys: {}", e.toString());
        }
        return out;
    }

    private static void collect(String prefix, com.electronwill.nightconfig.core.UnmodifiableConfig cfg,
                                java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> out) {
        for (java.util.Map.Entry<String, Object> entry : cfg.valueMap().entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                out.put(key, configValue);
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig child) {
                collect(key, child, out);
            }
        }
    }

    /**
     * 按扁平键写配置（仅服务端/房主可调用，因为 SERVER 配置在客户端是只读同步副本）。
     *
     * @return null 表示成功，否则返回错误类别（missing / value / type / denied）
     */
    @SuppressWarnings("unchecked")
    public static String setByKey(String key, String raw) {
        ForgeConfigSpec.ConfigValue<?> configValue = allValues().get(key);
        if (configValue == null) {
            return "missing";
        }
        Object defaultValue = configValue.getDefault();
        Object parsed;
        try {
            if (defaultValue instanceof Boolean) {
                parsed = Boolean.parseBoolean(raw.trim());
            } else if (defaultValue instanceof Integer) {
                parsed = Integer.parseInt(raw.trim());
            } else if (defaultValue instanceof Double) {
                parsed = Double.parseDouble(raw.trim());
            } else if (defaultValue instanceof String) {
                parsed = raw;
            } else if (defaultValue instanceof java.util.List<?>) {
                java.util.List<String> list = new java.util.ArrayList<>();
                for (String part : raw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        list.add(trimmed);
                    }
                }
                parsed = list;
            } else {
                return "type";
            }
            ((ForgeConfigSpec.ConfigValue<Object>) configValue).set(parsed);
            SPEC.save();
            return null;
        } catch (NumberFormatException e) {
            return "value";
        } catch (Exception e) {
            TaczBotAi.LOGGER.warn("[taczbotai] config set failed for {}: {}", key, e.toString());
            return "denied";
        }
    }

    public static String getByKey(String key) {
        ForgeConfigSpec.ConfigValue<?> configValue = allValues().get(key);
        if (configValue == null) {
            return null;
        }
        try {
            Object value = configValue.get();
            return value instanceof java.util.List<?> list ? String.join(",", (java.util.List<String>) list)
                    : String.valueOf(value);
        } catch (Exception e) {
            Object d = configValue.getDefault();
            return d instanceof java.util.List<?> list ? String.join(",", (java.util.List<String>) list)
                    : String.valueOf(d);
        }
    }
}
