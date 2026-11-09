/*
 * Copyright (c) 2026 Yukina_V
 * SPDX-License-Identifier: MIT
 */
package com.taczbotai.entity;

import com.taczbotai.config.BotConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 测试人员：无攻击性、无寻路 AI 的静止靶子。
 *
 *  - 继承了 {@link Mob} 但不注册任何 goal，且构造时 {@code setNoAi(true)}，因此不会移动；
 *  - 击退 / 推挤都被屏蔽，保证它永远站在生成点；
 *  - 永不自然消失（persistenceRequired）；
 *  - 名字与血量来自配置 {@code [dummy]}，默认名字「测试人员」。
 */
public class TestSubjectEntity extends Mob {

    public TestSubjectEntity(EntityType<? extends TestSubjectEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setCustomNameVisible(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    /** 没有任何 AI 行为。 */
    @Override
    protected void registerGoals() {
    }

    /** 应用配置里的名字、血量、无敌开关。 */
    public void applyConfig() {
        String name = BotConfig.guiValue(BotConfig.DUMMY_NAME);
        if (name != null && !name.isEmpty()) {
            this.setCustomName(Component.literal(name));
        }
        this.setCustomNameVisible(true);

        double health = BotConfig.guiValue(BotConfig.DUMMY_HEALTH);
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            this.setHealth((float) health);
        }
        this.setInvulnerable(BotConfig.guiValue(BotConfig.DUMMY_INVULNERABLE));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        this.applyConfig();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 靶子不移动
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
    }
}
