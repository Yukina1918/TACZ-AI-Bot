/*
 * Copyright (c) 2026 Yukina_V
 * SPDX-License-Identifier: MIT
 */
package com.taczbotai.registry;

import com.taczbotai.TaczBotAi;
import com.taczbotai.entity.TaczBotEntity;
import com.taczbotai.entity.TestSubjectEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TaczBotAi.MODID);

    public static final RegistryObject<EntityType<TaczBotEntity>> TACZ_BOT = ENTITY_TYPES.register("tacz_bot",
            () -> EntityType.Builder.of(TaczBotEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(TaczBotAi.MODID + ":tacz_bot"));

    /** 测试人员：无攻击性、不会移动的静止靶子。 */
    public static final RegistryObject<EntityType<TestSubjectEntity>> TEST_SUBJECT = ENTITY_TYPES.register("test_subject",
            () -> EntityType.Builder.of(TestSubjectEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(TaczBotAi.MODID + ":test_subject"));

    private ModEntities() {
    }
}
