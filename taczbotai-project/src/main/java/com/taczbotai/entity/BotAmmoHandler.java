package com.taczbotai.entity;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * 给生物挂一个只读（可消耗）的弹药容器，让 TACZ 的换弹/库存弹药逻辑在非玩家生物身上正常运作。
 * TACZ 的 {@code canReload} 会扫描实体身上的 {@code ForgeCapabilities.ITEM_HANDLER}，
 * 生物没有原版物品栏，所以必须由我们自己提供。
 */
public class BotAmmoHandler implements net.minecraftforge.items.IItemHandler, ICapabilityProvider {
    private final LazyOptional<net.minecraftforge.items.IItemHandler> holder = LazyOptional.of(() -> this);
    private final TaczBotEntity bot;

    public BotAmmoHandler(TaczBotEntity bot) {
        this.bot = bot;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    /** 动态生成的弹药堆：永远跟随生物当前手持枪械的弹药类型。 */
    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack gun = this.bot.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation gunId = iGun.getGunId(gun);
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation ammoId = index.getGunData().getAmmoId();
        if (ammoId == null) {
            return ItemStack.EMPTY;
        }
        int stackSize = TimelessAPI.getCommonAmmoIndex(ammoId)
                .map(CommonAmmoIndex::getStackSize)
                .orElse(30);
        boolean infinite = com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.INFINITE_AMMO);
        if (!infinite) {
            int reserve = this.bot.getAmmoReserve();
            if (reserve <= 0) {
                return ItemStack.EMPTY;
            }
            stackSize = Math.min(stackSize, reserve);
        }
        int count = Math.min(stackSize, 64);
        return AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack ammo = this.getStackInSlot(slot);
        if (ammo.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ammo.copyWithCount(Math.min(amount, ammo.getCount()));
        if (!simulate && !com.taczbotai.config.BotConfig.guiValue(com.taczbotai.config.BotConfig.INFINITE_AMMO)) {
            this.bot.setAmmoReserve(this.bot.getAmmoReserve() - result.getCount());
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? this.holder.cast() : LazyOptional.empty();
    }

    public void invalidate() {
        this.holder.invalidate();
    }
}
