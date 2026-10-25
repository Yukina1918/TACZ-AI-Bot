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

    private ModItems() {
    }
}
