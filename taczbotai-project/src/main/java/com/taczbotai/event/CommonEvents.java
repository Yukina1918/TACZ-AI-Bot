package com.taczbotai.event;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.taczbotai.TaczBotAi;
import com.taczbotai.command.BotCommand;
import com.taczbotai.config.BotConfig;
import com.taczbotai.entity.TaczBotEntity;
import com.taczbotai.path.PathManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** 全局事件：弹药 capability、友伤开关、锁头秒杀、击杀播报、指令注册、路径录制采样。 */
public final class CommonEvents {
    private static final TagKey<EntityType<?>> FORGE_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    private static final ResourceKey<DamageType> BOT_HEADSHOT =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(TaczBotAi.MODID, "bot_headshot"));

    private CommonEvents() {
    }

    // ------------------------------------------------------------------
    // 弹药 capability
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof TaczBotEntity bot) {
            // 关键：这个事件在 Entity 父类构造器内部就会被触发，此时子类字段初始化尚未执行，
            // 所以必须走惰性 getter（按需 new），直接读字段会拿到 null 并让 capability 表炸掉。
            var handler = bot.getAmmoHandler();
            event.addCapability(new ResourceLocation(TaczBotAi.MODID, "ammo"), handler);
            event.addListener(handler::invalidate);
        }
    }

    // ------------------------------------------------------------------
    // 同队误伤
    // ------------------------------------------------------------------

    /**
     * 第一道闸：TACZ 的命中前事件（可取消，取消后整次命中连击退/点燃都不发生）。
     * 这比只在 LivingHurtEvent 拦更彻底。
     */
    @SubscribeEvent
    public static void onGunHurtPre(EntityHurtByGunEvent.Pre event) {
        if (BotConfig.friendlyFire()) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity victim)) {
            return;
        }
        TaczBotEntity bot = resolveBotAttacker(event.getAttacker(), event.getBullet());
        if (bot == null || bot == victim) {
            return;
        }
        if (bot.isSameTeam(victim)) {
            event.setCanceled(true);
        }
    }

    /** 第二道闸：兜底拦截所有其它来源（我们的锁头伤害、其它枪械伤害路径等）。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (BotConfig.friendlyFire()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity cause = source.getEntity();
        if (cause == null) {
            cause = source.getDirectEntity();
        }
        TaczBotEntity bot = resolveBotAttacker(cause instanceof LivingEntity living ? living : null, cause);
        if (bot != null && bot != victim && bot.isSameTeam(victim)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // 档位 5：锁头秒杀
    // ------------------------------------------------------------------

    /**
     * 锁头走「TACZ 弹道命中后干预」而不是绕过弹道：
     * 子弹命中并结算伤害后（Post），按概率再补一份 = 目标最大生命值的伤害。
     * 补的伤害使用自定义伤害类型 taczbotai:bot_headshot（带 bypasses_armor 标签），
     * 因此不是 /kill、会走正常死亡逻辑与死亡信息。
     */
    @SubscribeEvent
    public static void onGunHurt(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        TaczBotEntity bot = resolveBotAttacker(event.getAttacker(), event.getBullet());
        if (bot == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity victim) || !victim.isAlive()) {
            return;
        }
        if (!BotConfig.INSTANT_KILL_ENABLED.get() || BotConfig.tier() < 5) {
            return;
        }
        // 打死同队的一律不走秒杀（友伤关闭时更不该发生）
        if (!BotConfig.friendlyFire() && bot.isSameTeam(victim)) {
            return;
        }
        if (isInstantKillImmune(victim)) {
            return;
        }
        int chance = BotConfig.INSTANT_KILL_CHANCE.get();
        if (chance <= 0 || bot.getRandom().nextInt(100) >= chance) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Registry<DamageType> damageTypes = serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        DamageSource source = new DamageSource(damageTypes.getHolderOrThrow(BOT_HEADSHOT), bot);
        victim.hurt(source, Math.max(1.0F, victim.getMaxHealth()));
    }

    /** 从事件里的攻击者 / 子弹实体反推是哪个 bot 开的枪（兼容自定义伤害来源把 owner 写在子弹上的情况）。 */
    @javax.annotation.Nullable
    private static TaczBotEntity resolveBotAttacker(@javax.annotation.Nullable LivingEntity attacker,
                                                    @javax.annotation.Nullable Entity bullet) {
        if (attacker instanceof TaczBotEntity bot) {
            return bot;
        }
        if (bullet instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof TaczBotEntity bot) {
            return bot;
        }
        return null;
    }

    private static boolean isInstantKillImmune(LivingEntity victim) {
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        if (victim.getType().is(FORGE_BOSSES)) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (id == null) {
            return false;
        }
        List<? extends String> blacklist = BotConfig.BOSS_BLACKLIST.get();
        return blacklist.contains(id.toString());
    }

    // ------------------------------------------------------------------
    // 击杀播报：只播报 taczbotai 生物 -> taczbotai 生物
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!BotConfig.KILL_MESSAGE.get()) {
            return;
        }
        if (!(event.getEntity() instanceof TaczBotEntity victim)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof TaczBotEntity attacker) || attacker == victim) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        Component message = Component.translatable("taczbotai.message.kill", victim.getDisplayName(), attacker.getDisplayName());
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    // ------------------------------------------------------------------
    // 指令 / tick / 存档生命周期
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BotCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (event.getServer().getTickCount() % 5 == 0) {
                com.taczbotai.match.MatchManager.tick(event.getServer());
            }
            BotCommand.tickRecording(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PathManager.invalidateAll();
        com.taczbotai.match.MatchManager.reset();
    }
}
