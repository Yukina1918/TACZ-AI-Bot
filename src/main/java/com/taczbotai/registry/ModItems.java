/*
 * Copyright (c) 2026 Yukina_V
 * SPDX-License-Identifier: MIT
 */
package com.taczbotai.registry;

import com.taczbotai.TaczBotAi;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TaczBotAi.MODID);

    public static final RegistryObject<Item> TACZ_BOT_SPAWN_EGG = ITEMS.register("tacz_bot_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TACZ_BOT, 0x3E5A33, 0xC8A165, new Item.Properties()));

    public static final RegistryObject<Item> TEST_SUBJECT_SPAWN_EGG = ITEMS.register("test_subject_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TEST_SUBJECT, 0x8A8A8A, 0xD32F2F, new Item.Properties()));

    private ModItems() {
    }
}
