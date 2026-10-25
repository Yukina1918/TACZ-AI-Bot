package com.taczbotai.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.taczbotai.TaczBotAi;
import com.taczbotai.config.BotConfig;
import com.taczbotai.path.PathManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * TACZ 枪械 AI 生物。
 *
 * 射击、换弹、持枪/瞄准动画全部由 TACZ 的原生逻辑完成：
 *  - TACZ 的 mixin 把 {@link IGunOperator} 注入到 {@link LivingEntity}，因此生物可以直接使用
 *    {@code IGunOperator.fromLivingEntity(this).shoot(pitch, yaw)} 走完整弹道（EntityKineticBullet）。
 *  - 第 3 人称的持枪/瞄准手臂姿势由 TACZ 的 HumanoidModelMixin 依据同步的 aimingProgress 自动摆好。
 * 本类只负责「看谁、瞄哪、打偏多少、何时开火/换弹/走位」。
 */
public class TaczBotEntity extends PathfinderMob {
    public static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(TaczBotEntity.class, EntityDataSerializers.INT);
    /** 头顶名字是否显示（同步到客户端，渲染器据此决定画不画名字标签）。 */
    public static final EntityDataAccessor<Boolean> DATA_NAME_VISIBLE =
            SynchedEntityData.defineId(TaczBotEntity.class, EntityDataSerializers.BOOLEAN);
    /** 头顶名字模式：0=总是显示 1=仅同队可见 2=完全隐藏。 */
    public static final EntityDataAccessor<Integer> DATA_NAME_MODE =
            SynchedEntityData.defineId(TaczBotEntity.class, EntityDataSerializers.INT);
    /** 队伍名同步到客户端，用于「仅同队可见」的判断。 */
    public static final EntityDataAccessor<String> DATA_TEAM_NAME =
            SynchedEntityData.defineId(TaczBotEntity.class, EntityDataSerializers.STRING);

    public static final int NAME_MODE_ALWAYS = 0;
    public static final int NAME_MODE_TEAM = 1;
    public static final int NAME_MODE_HIDDEN = 2;

    private static final String TAG_GUN = "BotGun";
    /** 到达判定：2 格以内就算到点（录制点很密，用不着精确逐点）。 */
    private static final double ARRIVE_SQ = 4.0D;
    private static final SimpleCommandExceptionType BAD_WEAPON =
            new SimpleCommandExceptionType(Component.translatable("taczbotai.command.weapon.bad"));
    private static final String TAG_PATH = "BotPath";
    private static final String TAG_TEAM = "BotTeam";
    private static final String TAG_RESERVE = "BotAmmoReserve";
    private static final String TAG_SKIN = "BotSkin";
    private static final String TAG_NAME_COLOR = "BotNameColor";
    private static final String TAG_NAME_MODE = "BotNameMode";

    /** 懒初始化：Entity 父类构造器里就会触发 AttachCapabilitiesEvent，那时字段初始化还没跑。 */
    private BotAmmoHandler ammoHandler;

    private int ammoReserve;
    private String pathName = "";
    private String teamName = "";
    private int waypointIndex;
    private boolean pathFinished;
    private int pathDirection = 1;
    /** 停留节点倒计时（gameTime 截止）与停留中心。 */
    private long stayUntil;
    @Nullable
    private Vec3 stayPoint;
    /** cs 模式：已到达终点，转入终点 5 格徘徊。 */
    private boolean hoverAtEnd;
    private int repathCooldown;
    private int wanderCooldown;
    private int scanCooldown;
    private int dryTicks;
    private int reactionTicks;

    /** 追敌时的重新寻路节流与放弃计时。 */
    private int chaseRepath;
    private int chaseTicks;
    @Nullable
    private UUID ignoredTarget;
    private int ignoreTicks;

    /** 丢失视野计时与最后看见的位置（看不到目标时朝这里追，而不是透视追踪实时坐标）。 */
    private int lostSightTicks;
    @Nullable
    private Vec3 lastSeenPos;

    /** 换弹锁定：>0 时禁止开火，归零时把弹匣补满（实现按枪种自定义的换弹时长）。 */
    private int reloadLockTicks;

    @Nullable
    private ChatFormatting nameColor;
    /** 头顶名字模式：0=总是显示 1=仅同队可见 2=完全隐藏（同步到客户端）。 */
    private int nameTagMode = NAME_MODE_ALWAYS;
    /** 随机名字（来自配置 [bot] names）；空 = 用默认实体名。 */
    private String botName = "";
    private static final String TAG_BOT_NAME = "BotName";

    @Nullable
    private LivingEntity target;
    @Nullable
    private ResourceLocation drawnGunId;

    public TaczBotEntity(EntityType<? extends TaczBotEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
        this.getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    /** 属性在实体创建时从配置读取（配置在 mod 加载期已就绪）。 */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        try {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BotConfig.HEALTH.get());
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BotConfig.SPEED.get());
            this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(BotConfig.FOLLOW_RANGE.get());
            if (this.getHealth() <= 0.0F || this.getHealth() > this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
            if (this.ammoReserve <= 0) {
                this.ammoReserve = BotConfig.AMMO_RESERVE.get();
            }
        } catch (Exception e) {
            TaczBotAi.LOGGER.warn("[taczbotai] config not ready while creating bot: {}", e.toString());
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN, 0);
        this.entityData.define(DATA_NAME_VISIBLE, true);
        this.entityData.define(DATA_NAME_MODE, NAME_MODE_ALWAYS);
        this.entityData.define(DATA_TEAM_NAME, "");
    }

    @Override
    protected void registerGoals() {
        // 全部移动/战斗逻辑由本类的 tick 驱动（巡逻、闲逛、交战），
        // 只保留原版的落水浮起，避免与自定义移动逻辑抢导航。
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        this.syncGunOperator();
        if (this.ignoreTicks > 0) {
            this.ignoreTicks--;
            if (this.ignoreTicks == 0) {
                this.ignoredTarget = null;
            }
        }

        ItemStack mainGun = this.getMainHandItem();
        IGun mainIGun = IGun.getIGunOrNull(mainGun);
        IGunOperator operator = IGunOperator.fromLivingEntity(this);
        if (mainIGun != null) {
            this.tickReloadLock(operator, mainIGun, mainGun);
            this.guardAmmo(operator, mainIGun, mainGun);
        }

        if (--this.scanCooldown <= 0) {
            this.scanCooldown = 8;
            this.refreshTarget();
        }
        if (this.target != null) {
            double acquire = this.acquireRange();
            boolean gone = !this.target.isAlive()
                    || this.target.level() != this.level()
                    || this.distanceToSqr(this.target) > acquire * acquire
                    || !this.isValidTarget(this.target);
            if (gone) {
                this.target = null;
                this.reactionTicks = 0;
                this.chaseTicks = 0;
                this.lostSightTicks = 0;
                this.lastSeenPos = null;
            }
        }

        // 目标丢失视野：先记住最后位置追过去；连续 lostSightSeconds 秒完全看不到就放弃
        if (this.target != null) {
            if (this.hasLineOfSight(this.target)) {
                this.lostSightTicks = 0;
                this.lastSeenPos = this.target.position();
            } else if (++this.lostSightTicks > (int) Math.round(BotConfig.LOST_SIGHT_SECONDS.get() * 20.0D)) {
                this.ignoredTarget = this.target.getUUID();
                this.ignoreTicks = 120;
                this.target = null;
                this.lostSightTicks = 0;
                this.lastSeenPos = null;
                this.chaseTicks = 0;
                this.getNavigation().stop();
            }
        }

        if (this.target != null) {
            this.engage(this.target);
        } else {
            this.relaxed();
        }
    }

    /** 交战时的索敌半径：取「枪械有效射程 + 余量」和配置索敌距离的较大值。 */
    private double acquireRange() {
        ItemStack gun = this.getMainHandItem();
        double byGun = gun.isEmpty() ? 0.0D : BotGunProfile.fireRange(gun) + 16.0D;
        return Math.max(BotConfig.TARGET_RANGE.get(), byGun);
    }

    /** 检测到主手枪械变化时通知 TACZ 重新「拔出」，否则 TACZ 不会允许射击。 */
    private void syncGunOperator() {
        ItemStack gun = this.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            this.drawnGunId = null;
            return;
        }
        ResourceLocation id = iGun.getGunId(gun);
        if (this.drawnGunId == null || !this.drawnGunId.equals(id)) {
            IGunOperator operator = IGunOperator.fromLivingEntity(this);
            operator.initialData();
            operator.draw(this::getMainHandItem);
            this.drawnGunId = id;
        }
    }

    /** 无事可做：收枪、检查弹药、按路径巡逻或随机闲逛。 */
    private void relaxed() {
        IGunOperator operator = IGunOperator.fromLivingEntity(this);
        operator.aim(false);

        ItemStack gun = this.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null && this.reloadLockTicks <= 0) {
            this.tryReload(operator, iGun, gun, false);
        }

        if (!this.pathName.isEmpty()) {
            this.followPath();
        } else {
            this.wander();
        }
    }

    /**
     * 交战流程：
     *  1. 有效射程内且能看见 -> 停下、瞄准、开火；
     *  2. 看不见或超出射程 -> 朝**最后看见的位置**绕路靠近（不是实时透视追踪）；
     *  3. 完全失联超过 lostSightSeconds（默认 15s）-> 放弃目标（在主循环里判定）；
     *  4. 能看见但 20 秒都追不到（例如隔了深沟）-> 也放弃，避免原地抽搐。
     */
    private void engage(LivingEntity enemy) {
        this.getLookControl().setLookAt(enemy, 60.0F, 60.0F);

        ItemStack gun = this.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            this.relaxed();
            return;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(this);

        // 换弹期间不移动、不开火（换弹时长按枪种配置）
        if (this.reloadLockTicks > 0) {
            this.getNavigation().stop();
            return;
        }
        if (this.reloadLockTicks <= 0 && this.tryReload(operator, iGun, gun, true)) {
            this.getNavigation().stop();
            return;
        }

        double distance = this.distanceTo(enemy);
        double fireRange = BotGunProfile.fireRange(gun);
        boolean hasSight = this.hasLineOfSight(enemy);
        boolean canShootNow = distance <= fireRange && hasSight;

        if (!canShootNow) {
            // 朝最后看见的位置追过去（看不到目标时不用它的实时坐标，避免"透视追踪"）
            operator.aim(false);
            Vec3 goal = hasSight ? enemy.position()
                    : (this.lastSeenPos != null ? this.lastSeenPos : enemy.position());
            if (--this.chaseRepath <= 0 || this.getNavigation().isDone() || this.getNavigation().isStuck()) {
                this.chaseRepath = 10;
                this.getNavigation().moveTo(goal.x, goal.y, goal.z, BotConfig.PATROL_SPEED.get());
            }
            if (++this.chaseTicks > (int) Math.round(BotConfig.CHASE_GIVE_UP_SECONDS.get() * 20.0D)) {
                this.ignoredTarget = enemy.getUUID();
                this.ignoreTicks = 200;
                this.target = null;
                this.chaseTicks = 0;
                this.lostSightTicks = 0;
                this.lastSeenPos = null;
                this.getNavigation().stop();
            }
            return;
        }

        this.chaseTicks = 0;
        this.getNavigation().stop();

        if (this.reactionTicks < BotConfig.REACTION_TICKS.get()) {
            this.reactionTicks++;
            operator.aim(true);
            return;
        }
        operator.aim(true);

        if (operator.getSynReloadState().getStateType().isReloading()) {
            return;
        }
        if (operator.getSynDrawCoolDown() > 0L || operator.getSynShootCoolDown() > 0L) {
            return;
        }

        this.fireAt(enemy, operator, iGun, gun);
    }

    /**
     * 换弹兜底：如果弹匣空、又长时间没进入换弹状态（某些枪包/Lua 换弹脚本在非玩家身上会失败），
     * 直接把弹匣补满，避免生物站在那儿打空枪。
     */
    private void guardAmmo(IGunOperator operator, IGun iGun, ItemStack gun) {
        if (this.reloadLockTicks > 0) {
            this.dryTicks = 0;
            return;
        }
        int magazine = iGun.getCurrentAmmoCount(gun);
        boolean reloading = operator.getSynReloadState().getStateType().isReloading();
        if (magazine > 0 || reloading) {
            this.dryTicks = 0;
            return;
        }
        if (++this.dryTicks < 80) {
            return;
        }
        this.dryTicks = 0;
        if (!BotConfig.INFINITE_AMMO.get() && this.ammoReserve <= 0) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(gun);
        int max = BotGunProfile.magazineSize(gun, iGun);
        iGun.setCurrentAmmoCount(gun, max);
        iGun.setBulletInBarrel(gun, true);
        TaczBotAi.LOGGER.debug("[taczbotai] reload fallback refilled {} to {}", gunId, max);
    }

    /**
     * 按档位给瞄准方向加角度误差，然后交给 TACZ 走原生弹道。
     * 档位 5 的误差极小（≈枪械自身散布），因此命中率接近正常水平。
     */
    private void fireAt(LivingEntity enemy, IGunOperator operator, IGun iGun, ItemStack gun) {
        Vec3 aim = this.resolveAimPoint(enemy);
        double dx = aim.x - this.getX();
        double dy = aim.y - this.getEyeY();
        double dz = aim.z - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float baseYaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;
        float basePitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        float error = BotConfig.aimErrorForTier(BotConfig.tier());
        float errYaw = (float) this.getRandom().nextGaussian() * error;
        float errPitch = (float) this.getRandom().nextGaussian() * error * 0.7F;

        float shotYaw = baseYaw + errYaw;
        float shotPitch = Mth.clamp(basePitch + errPitch, -89.0F, 89.0F);

        ShootResult result = operator.shoot(() -> shotPitch, () -> shotYaw);
        if (result == ShootResult.NEED_BOLT || (!iGun.hasBulletInBarrel(gun) && this.needsBolt(gun, iGun))) {
            operator.bolt();
        }
        if (result == ShootResult.NO_AMMO) {
            this.tryReload(operator, iGun, gun, true);
        }
    }

    /** 瞄准点：默认躯干中心；命中头部会额外吃到 TACZ 的爆头倍率。 */
    private Vec3 resolveAimPoint(LivingEntity enemy) {
        return new Vec3(enemy.getX(), enemy.getEyeY() - 0.35D, enemy.getZ());
    }

    private boolean needsBolt(ItemStack gun, IGun iGun) {
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index == null) {
            return false;
        }
        return index.getGunData().getBolt() == com.tacz.guns.resource.pojo.data.gun.Bolt.MANUAL_ACTION;
    }

    /**
     * 按枪种时长开始换弹（返回 true 表示当前处于换弹锁定中）。
     *
     * TACZ 自己的换弹动画时长由枪包数据决定，无法按实体改；所以这里用「锁定计时器」控制实际时长：
     *  - 开始时调用 TACZ 的 reload() 播放动画；
     *  - 计时归零时（= 配置的 1s / 1.5s / 2.5s / 5s）取消 TACZ 剩余动画并把弹匣补满。
     */
    private boolean tryReload(IGunOperator operator, IGun iGun, ItemStack gun, boolean inCombat) {
        if (this.reloadLockTicks > 0) {
            return true;
        }
        if (!BotConfig.INFINITE_AMMO.get() && this.ammoReserve <= 0) {
            return false;
        }
        int current = iGun.getCurrentAmmoCount(gun);
        int max = BotGunProfile.magazineSize(gun, iGun);
        boolean need = current <= 0 || (!inCombat && current < max);
        if (!need) {
            return false;
        }
        ReloadState reloadState = operator.getSynReloadState();
        if (reloadState.getStateType().isReloading()) {
            return true;
        }
        if (operator.getSynShootCoolDown() > 0L || operator.getSynDrawCoolDown() > 0L) {
            return true;
        }
        operator.reload();
        double seconds = BotGunProfile.reloadSeconds(gun);
        this.reloadLockTicks = Math.max(1, (int) Math.round(seconds * 20.0D));
        return true;
    }

    /** 换弹计时器：归零时结束换弹并把弹匣补满。 */
    private void tickReloadLock(IGunOperator operator, IGun iGun, ItemStack gun) {
        if (this.reloadLockTicks <= 0) {
            return;
        }
        if (--this.reloadLockTicks > 0) {
            return;
        }
        if (operator.getSynReloadState().getStateType().isReloading()) {
            operator.cancelReload();
        }
        iGun.setCurrentAmmoCount(gun, BotGunProfile.magazineSize(gun, iGun));
        iGun.setBulletInBarrel(gun, true);
        this.dryTicks = 0;
    }

    // ------------------------------------------------------------------
    // 索敌 / 队伍
    // ------------------------------------------------------------------

    private void refreshTarget() {
        if (this.getMainHandItem().isEmpty() || !(this.getMainHandItem().getItem() instanceof IGun)) {
            this.target = null;
            return;
        }
        double range = this.acquireRange();
        boolean needSight = BotConfig.REQUIRE_LINE_OF_SIGHT.get();
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(range), this::isValidTarget);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (this.ignoreTicks > 0 && this.ignoredTarget != null
                    && this.ignoredTarget.equals(candidate.getUUID())) {
                continue;
            }
            // 非透视索敌：必须先看见才能锁定
            if (needSight && !this.hasLineOfSight(candidate)) {
                continue;
            }
            double score = this.distanceToSqr(candidate);
            if (candidate instanceof Player) {
                score *= 0.75D; // 优先打玩家
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != this.target) {
            this.reactionTicks = 0;
            this.lostSightTicks = 0;
            this.lastSeenPos = best == null ? null : best.position();
        }
        this.target = best;
    }

    public boolean isValidTarget(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive() || candidate.isRemoved()) {
            return false;
        }
        if (candidate instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            return !this.isSameTeam(player);
        }
        if (candidate instanceof TaczBotEntity other) {
            return !this.isSameTeam(other);
        }
        if (candidate instanceof Enemy) {
            return BotConfig.ATTACK_HOSTILE_MOBS.get() && !this.isSameTeam(candidate);
        }
        return false;
    }

    /**
     * 本生物的队伍标识：优先用 NBT 里记录的名字（setBotTeam 写入），
     * 再退回计分板。这样即使生物没能成功写进计分板的 teamsByPlayer，
     * 队伍判定依然是稳定的。
     */
    public String teamId() {
        if (!this.teamName.isEmpty()) {
            return this.teamName;
        }
        Team team = this.getTeam();
        return team == null ? "" : team.getName();
    }

    /** 对方实体的队伍标识（生物用记录值，其它实体如玩家用计分板）。 */
    private static String teamIdOf(Entity other) {
        if (other instanceof TaczBotEntity bot) {
            String recorded = bot.teamId();
            if (!recorded.isEmpty()) {
                return recorded;
            }
        }
        Team team = other.getTeam();
        return team == null ? "" : team.getName();
    }

    /**
     * 队伍判定：两边都优先用「记录/计分板解析出的队伍标识」比较。
     * 早期版本只在双方都能查到计分板队伍时才认队友，导致两个都查不到队伍的 bot 互相开火。
     */
    public boolean isSameTeam(Entity other) {
        String mine = this.teamId();
        if (mine.isEmpty()) {
            return false;
        }
        return mine.equalsIgnoreCase(teamIdOf(other));
    }

    public String getBotTeamName() {
        return this.teamId();
    }

    public void setBotTeam(@Nullable String name) {
        this.teamName = name == null ? "" : name;
        // 同步到客户端，供「名字仅同队可见」模式判断
        this.entityData.set(DATA_TEAM_NAME, this.teamName);
        if (this.level().isClientSide()) {
            return;
        }
        Scoreboard scoreboard = this.level().getScoreboard();
        if (name == null || name.isEmpty()) {
            Team current = this.getTeam();
            if (current instanceof PlayerTeam playerTeam) {
                scoreboard.removePlayerFromTeam(this.getScoreboardName(), playerTeam);
            }
            return;
        }
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        scoreboard.addPlayerToTeam(this.getScoreboardName(), team);
        // 诊断：如果计分板反查不到（某些情况下实体 UUID 不会出现在 teamsByPlayer），
        // 记一条日志；队伍判定本身仍会依赖 teamName 记录值，功能不受影响。
        if (this.getTeam() == null) {
            TaczBotAi.LOGGER.info("[taczbotai] bot {}: scoreboard lookup for team '{}' failed, using recorded team name",
                    this.getUUID(), name);
        }
    }

    // ------------------------------------------------------------------
    // 路径巡逻 / 闲逛
    // ------------------------------------------------------------------

    public void followPath() {
        String behavior = this.pathBehavior();
        if ("none".equals(behavior)) {
            this.wander();
            return;
        }
        List<com.taczbotai.path.PathPoint> points = PathManager.getPoints(this.level(), this.pathName);
        if (points.isEmpty()) {
            this.wander();
            return;
        }
        long now = this.level().getGameTime();

        // 停留节点：在节点周围 4 格内慢速徘徊，时间到再继续
        if (now < this.stayUntil) {
            Vec3 center = this.stayPoint != null ? this.stayPoint : points.get(Math.min(this.waypointIndex, points.size() - 1)).pos();
            this.hoverAround(center, 4.0D);
            return;
        }

        // cs 模式：走到终点后围绕终点 5 格徘徊
        if (this.hoverAtEnd) {
            this.hoverAround(points.get(points.size() - 1).pos(), 5.0D);
            return;
        }

        if (this.waypointIndex >= points.size()) {
            this.waypointIndex = 0;
        }

        // 到达判定：2 格内算到达，一次跳过所有贴脸的连续点
        int guard = 0;
        while (guard++ < 32
                && this.position().distanceToSqr(points.get(this.waypointIndex).pos()) < ARRIVE_SQ) {
            com.taczbotai.path.PathPoint reached = points.get(this.waypointIndex);
            if (reached.stop() && reached.staySeconds() > 0) {
                this.stayUntil = now + reached.staySeconds() * 20L;
                this.stayPoint = reached.pos();
                this.advancePath(points.size(), behavior);
                this.getNavigation().stop();
                return;
            }
            if (!this.advancePath(points.size(), behavior)) {
                this.hoverAtEnd = true;
                this.getNavigation().stop();
                return;
            }
        }

        Vec3 point = points.get(this.waypointIndex).pos();
        double distanceSq = this.position().distanceToSqr(point);
        boolean needRepath = --this.repathCooldown <= 0
                || this.getNavigation().isDone()
                || this.getNavigation().isStuck();
        if (needRepath) {
            this.repathCooldown = 8;
            double speed = BotConfig.PATROL_SPEED.get() * (distanceSq > 36.0D ? 1.5D : 1.0D);
            int y = BlockPos.containing(point.x, point.y, point.z).getY();
            this.getNavigation().moveTo(point.x, y, point.z, speed);
        }
    }

    /** 当前该用哪种行为：对局模式优先，否则用路径录制时选的模式。 */
    private String pathBehavior() {
        String matchMode = com.taczbotai.match.MatchManager.activeMode(this.level());
        if (matchMode != null) {
            return matchMode;
        }
        return PathManager.modeOf(this.level(), this.pathName);
    }

    /**
     * 推进到下一个节点。
     *
     * @return false 表示这条路径已经走到尽头（仅 cs 模式会返回 false，转成终点徘徊）
     */
    private boolean advancePath(int size, String behavior) {
        if (size <= 1) {
            return !"cs".equals(behavior);
        }
        if ("bf".equals(behavior)) {
            int next = this.waypointIndex + this.pathDirection;
            if (next >= size) {
                this.pathDirection = -1;
                next = size - 2;
            } else if (next < 0) {
                this.pathDirection = 1;
                next = Math.min(1, size - 1);
            }
            this.waypointIndex = Math.max(0, Math.min(size - 1, next));
            return true;
        }
        // cs / none：单向走到最后
        if (this.waypointIndex + 1 >= size) {
            return false;
        }
        this.waypointIndex++;
        return true;
    }

    /** 在某个中心点周围随机小范围慢速移动（停留 / 终点徘徊共用）。 */
    private void hoverAround(Vec3 center, double radius) {
        if (this.getNavigation().isInProgress() && !this.getNavigation().isDone()) {
            return;
        }
        if (--this.wanderCooldown > 0) {
            return;
        }
        this.wanderCooldown = 20 + this.getRandom().nextInt(40);
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = radius * Math.sqrt(this.getRandom().nextDouble());
        double x = center.x + Math.cos(angle) * distance;
        double z = center.z + Math.sin(angle) * distance;
        int y = BlockPos.containing(x, center.y, z).getY();
        this.getNavigation().moveTo(x, y, z, 0.6D);
    }

    private void wander() {
        if (--this.wanderCooldown > 0 && !this.getNavigation().isDone()) {
            return;
        }
        this.wanderCooldown = 40 + this.getRandom().nextInt(80);
        if (this.getNavigation().isInProgress() && !this.getNavigation().isDone()) {
            return;
        }
        double radius = 12.0D;
        double x = this.getX() + (this.getRandom().nextDouble() - 0.5D) * 2.0D * radius;
        double z = this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 2.0D * radius;
        BlockPos target = BlockPos.containing(x, this.getY(), z);
        this.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
    }

    public String getPathName() {
        return this.pathName;
    }

    public void setPathName(String name) {
        this.pathName = name == null ? "" : name;
        this.waypointIndex = 0;
        this.repathCooldown = 0;
        this.pathFinished = false;
        this.pathDirection = 1;
        this.stayUntil = 0L;
        this.stayPoint = null;
        this.hoverAtEnd = false;
    }

    // ------------------------------------------------------------------
    // 装备
    // ------------------------------------------------------------------

    private static int maxAmmoFor(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> Math.max(1, index.getGunData().getAmmoAmount()))
                .orElse(8);
    }

    /**
     * 解析武器参数：
     *  - "tacz:m1911" 这种纯 id → 该枪、不带任何配件
     *  - "{GunId:\"tacz:m1911\",...}" → 直接应用这段 SNBT（可带任意配件/弹匣数据）
     */
    public static ItemStack parseGunSpec(@Nullable String spec) throws CommandSyntaxException {
        String trimmed = spec == null ? "" : spec.trim();
        if (trimmed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (trimmed.startsWith("{")) {
            CompoundTag tag = TagParser.parseTag(trimmed);
            ResourceLocation gunId = ResourceLocation.tryParse(tag.getString("GunId"));
            if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
                throw BAD_WEAPON.create();
            }
            ItemStack gun = baseGun(gunId);
            gun.getOrCreateTag().merge(tag);
            return gun;
        }
        ResourceLocation gunId = ResourceLocation.tryParse(trimmed);
        if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            throw BAD_WEAPON.create();
        }
        return baseGun(gunId);
    }

    private static ItemStack baseGun(ResourceLocation gunId) throws CommandSyntaxException {
        ItemStack gun = GunItemBuilder.create()
                .setId(gunId)
                .setFireMode(FireMode.AUTO)
                .setAmmoCount(maxAmmoFor(gunId))
                .setAmmoInBarrel(true)
                .build();
        if (gun.isEmpty()) {
            throw BAD_WEAPON.create();
        }
        return gun;
    }

    private void applyGun(ItemStack gun) {
        this.setItemSlot(EquipmentSlot.MAINHAND, gun);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.ammoReserve = BotConfig.AMMO_RESERVE.get();
        this.drawnGunId = null;
    }

    /** 用指令参数换枪（空 = 用配置）。 */
    public void equipGunFromCommand(@Nullable String spec) throws CommandSyntaxException {
        if (spec == null || spec.isBlank()) {
            this.equipConfiguredGun();
            return;
        }
        this.applyGun(parseGunSpec(spec));
    }

    /** 按配置生成一把枪并放到主手；配置里有 gunNbt 时优先用整段 NBT。 */
    public void equipConfiguredGun() {
        String gunNbt = BotConfig.GUN_NBT.get();
        if (gunNbt != null && !gunNbt.isBlank()) {
            try {
                this.applyGun(parseGunSpec(gunNbt));
                return;
            } catch (CommandSyntaxException e) {
                TaczBotAi.LOGGER.warn("[taczbotai] invalid gunNbt in config, falling back to gunId: {}", e.getMessage());
            }
        }
        ResourceLocation gunId = ResourceLocation.tryParse(BotConfig.GUN_ID.get());
        if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            TaczBotAi.LOGGER.warn("[taczbotai] unknown gun id in config: {}", BotConfig.GUN_ID.get());
            return;
        }
        GunItemBuilder builder = GunItemBuilder.create()
                .setId(gunId)
                .setFireMode(FireMode.AUTO)
                .setAmmoCount(BotConfig.MAGAZINE_AMMO.get() > 0
                        ? BotConfig.MAGAZINE_AMMO.get()
                        : maxAmmoFor(gunId))
                .setAmmoInBarrel(true);
        for (String entry : BotConfig.ATTACHMENTS.get()) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String typeName = entry.substring(0, split).trim().toUpperCase(java.util.Locale.ROOT);
            String attachmentId = entry.substring(split + 1).trim();
            try {
                AttachmentType type = AttachmentType.valueOf(typeName);
                ResourceLocation id = ResourceLocation.tryParse(attachmentId);
                if (id == null) {
                    continue;
                }
                if (TimelessAPI.getCommonAttachmentIndex(id).isEmpty()) {
                    TaczBotAi.LOGGER.warn("[taczbotai] unknown attachment id: {}", attachmentId);
                    continue;
                }
                builder.putAttachment(type, id);
            } catch (IllegalArgumentException ignored) {
                TaczBotAi.LOGGER.warn("[taczbotai] unknown attachment type: {}", typeName);
            }
        }
        ItemStack gun = builder.build();
        if (gun.isEmpty()) {
            TaczBotAi.LOGGER.warn("[taczbotai] failed to build gun {}", gunId);
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, gun);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.ammoReserve = BotConfig.AMMO_RESERVE.get();
        this.drawnGunId = null;
    }

    // ------------------------------------------------------------------
    // 存档 / 生成
    // ------------------------------------------------------------------

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        if (BotConfig.PERSISTENCE.get()) {
            this.setPersistenceRequired();
        }
        this.rollSkin();
        this.rollName();
        if (this.getMainHandItem().isEmpty()) {
            this.equipConfiguredGun();
        }
        if (this.ammoReserve <= 0) {
            this.ammoReserve = BotConfig.AMMO_RESERVE.get();
        }
        String team = BotConfig.DEFAULT_TEAM.get();
        if (!team.isEmpty() && this.getTeam() == null) {
            this.setBotTeam(team);
        }
        // 头顶名字默认值来自配置（之后可用 /taczbotai team showname|color 单独覆盖）
        this.nameTagMode = parseNameMode(BotConfig.NAME_TAG_MODE.get());
        String colorName = BotConfig.NAME_TAG_COLOR.get();
        this.nameColor = colorName == null || colorName.isBlank() ? null : ChatFormatting.getByName(colorName);
        this.applyDisplayName();
        return data;
    }

    /** 从配置 [bot] names 里随机挑一个名字。 */
    private void rollName() {
        if (!this.botName.isEmpty()) {
            return;
        }
        java.util.List<? extends String> names = BotConfig.NAMES.get();
        if (names == null || names.isEmpty()) {
            this.botName = "";
            return;
        }
        int index = this.getRandom().nextInt(names.size());
        String picked = names.get(index);
        this.botName = picked == null ? "" : picked;
    }

    public String getBotName() {
        return this.botName;
    }

    private void rollSkin() {
        String mode = BotConfig.SKIN_MODE.get();
        int skin;
        if ("random".equalsIgnoreCase(mode)) {
            skin = 1 + this.getRandom().nextInt(4);
        } else if ("vanilla".equalsIgnoreCase(mode)) {
            skin = 0;
        } else {
            try {
                skin = Mth.clamp(Integer.parseInt(mode.trim()), 0, 4);
            } catch (NumberFormatException e) {
                skin = 1 + this.getRandom().nextInt(4);
            }
        }
        this.entityData.set(DATA_SKIN, skin);
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkin(int skin) {
        this.entityData.set(DATA_SKIN, Mth.clamp(skin, 0, 4));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_PATH, this.pathName);
        tag.putBoolean("BotPathFinished", this.pathFinished);
        tag.putInt("BotPathDirection", this.pathDirection);
        tag.putString(TAG_TEAM, this.getBotTeamName());
        tag.putInt(TAG_RESERVE, this.ammoReserve);
        tag.putInt(TAG_SKIN, this.getSkin());
        tag.putInt(TAG_NAME_MODE, this.nameTagMode);
        if (!this.botName.isEmpty()) {
            tag.putString(TAG_BOT_NAME, this.botName);
        }
        if (this.nameColor != null) {
            tag.putString(TAG_NAME_COLOR, this.nameColor.getName());
        }
        if (!this.getMainHandItem().isEmpty()) {
            tag.put(TAG_GUN, this.getMainHandItem().save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.pathName = tag.getString(TAG_PATH);
        this.pathFinished = tag.getBoolean("BotPathFinished");
        this.pathDirection = tag.contains("BotPathDirection") ? tag.getInt("BotPathDirection") : 1;
        this.teamName = tag.getString(TAG_TEAM);
        this.ammoReserve = tag.getInt(TAG_RESERVE);
        if (tag.contains(TAG_SKIN)) {
            this.entityData.set(DATA_SKIN, tag.getInt(TAG_SKIN));
        }
        this.nameTagMode = tag.contains(TAG_NAME_MODE) ? tag.getInt(TAG_NAME_MODE) : NAME_MODE_ALWAYS;
        this.botName = tag.getString(TAG_BOT_NAME);
        if (tag.contains(TAG_NAME_COLOR)) {
            this.nameColor = ChatFormatting.getByName(tag.getString(TAG_NAME_COLOR));
        }
        this.applyDisplayName();
        if (this.getMainHandItem().isEmpty() && tag.contains(TAG_GUN)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.of(tag.getCompound(TAG_GUN)));
        }
        if (!this.level().isClientSide() && !this.teamName.isEmpty()) {
            this.setBotTeam(this.teamName);
        }
    }

    // ------------------------------------------------------------------
    // 头顶名字：颜色 / 是否显示
    // ------------------------------------------------------------------

    /** 设置头顶名字颜色（null = 默认白色，即不覆盖自定义名字）。 */
    public void setBotNameColor(@Nullable ChatFormatting color) {
        this.nameColor = color;
        this.applyDisplayName();
    }

    /** 兼容旧指令：true = 总是显示，false = 完全隐藏。 */
    public void setBotNameVisible(boolean visible) {
        this.setBotNameMode(visible ? NAME_MODE_ALWAYS : NAME_MODE_HIDDEN);
    }

    public void setBotNameMode(int mode) {
        this.nameTagMode = Mth.clamp(mode, NAME_MODE_ALWAYS, NAME_MODE_HIDDEN);
        this.applyDisplayName();
    }

    /** 客户端看到的同步值；渲染器用它决定画不画名字标签。 */
    public boolean isBotNameVisible() {
        return this.entityData.get(DATA_NAME_MODE) != NAME_MODE_HIDDEN;
    }

    /** 0=总是显示 1=仅同队可见 2=完全隐藏（同步值）。 */
    public int getNameTagMode() {
        return this.entityData.get(DATA_NAME_MODE);
    }

    /** 客户端用来判断「是否同队」的队伍名（同步值）。 */
    public String getSyncedTeamName() {
        return this.entityData.get(DATA_TEAM_NAME);
    }

    public static int parseNameMode(@Nullable String raw) {
        if (raw == null) {
            return NAME_MODE_TEAM;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "always", "on", "true", "all" -> NAME_MODE_ALWAYS;
            case "hidden", "off", "false", "none" -> NAME_MODE_HIDDEN;
            default -> NAME_MODE_TEAM;
        };
    }

    private void applyDisplayName() {
        if (!this.botName.isEmpty()) {
            Component base = Component.literal(this.botName);
            if (this.nameColor != null) {
                base = base.copy().withStyle(this.nameColor);
            }
            this.setCustomName(base);
        } else if (this.nameColor != null) {
            this.setCustomName(Component.translatable("entity.taczbotai.tacz_bot").withStyle(this.nameColor));
        }
        this.setCustomNameVisible(this.nameTagMode != NAME_MODE_HIDDEN);
        this.entityData.set(DATA_NAME_MODE, this.nameTagMode);
        this.entityData.set(DATA_NAME_VISIBLE, this.nameTagMode != NAME_MODE_HIDDEN);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !BotConfig.PERSISTENCE.get();
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public int getAmmoReserve() {
        return this.ammoReserve;
    }

    public void setAmmoReserve(int value) {
        this.ammoReserve = Math.max(0, value);
    }

    public BotAmmoHandler getAmmoHandler() {
        if (this.ammoHandler == null) {
            this.ammoHandler = new BotAmmoHandler(this);
        }
        return this.ammoHandler;
    }

}
