package com.taczbotai.entity;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.AttachmentDataUtils;
import com.taczbotai.config.BotConfig;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 按 TACZ 枪械的 {@code type} 字段（pistol / smg / rifle / mg / sniper / shotgun / rpg ...）
 * 决定这个 bot 的「有效交战距离」和「换弹耗时」。
 *
 * 距离三档（默认 12 / 48 / 72，可配置）：
 *  - 近距离：手枪、冲锋枪
 *  - 中距离：机枪、突击步枪（以及霰弹枪、火箭筒等未单列的）
 *  - 远距离：射手步枪、狙击步枪
 *
 * 换弹耗时（可配置）：手枪 1s、机枪 5s、突击步枪 1.5s、射手步枪 2.5s、狙击步枪 2.5s。
 * 冲锋枪归入手枪档；霰弹枪 2.5s；其余 2s。
 */
public final class BotGunProfile {
    public enum Category {
        CLOSE, MID, FAR
    }

    private BotGunProfile() {
    }

    @Nullable
    public static String typeOf(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return null;
        }
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(CommonGunIndex::getPojo)
                .map(pojo -> pojo.getType())
                .orElse(null);
    }

    public static Category category(ItemStack gun) {
        String type = typeOf(gun);
        if (type == null) {
            return Category.MID;
        }
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("pistol") || t.contains("smg") || t.contains("submachine")) {
            return Category.CLOSE;
        }
        if (t.contains("sniper") || t.contains("marksman") || t.contains("dmr")) {
            return Category.FAR;
        }
        return Category.MID;
    }

    /** 该枪的有效开火距离。 */
    public static double fireRange(ItemStack gun) {
        return switch (category(gun)) {
            case CLOSE -> BotConfig.RANGE_CLOSE.get();
            case FAR -> BotConfig.RANGE_FAR.get();
            default -> BotConfig.RANGE_MID.get();
        };
    }

    /** 该枪的换弹耗时（秒）。 */
    public static double reloadSeconds(ItemStack gun) {
        String type = typeOf(gun);
        if (type == null) {
            return BotConfig.RELOAD_OTHER.get();
        }
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("pistol") || t.contains("smg") || t.contains("submachine")) {
            return BotConfig.RELOAD_PISTOL.get();
        }
        if (t.equals("mg") || t.contains("machine") || t.contains("lmg") || t.contains("hmg")) {
            return BotConfig.RELOAD_MG.get();
        }
        if (t.contains("marksman") || t.contains("dmr")) {
            return BotConfig.RELOAD_MARKSMAN.get();
        }
        if (t.contains("sniper")) {
            return BotConfig.RELOAD_SNIPER.get();
        }
        // 霰弹枪与重武器（火箭筒/榴弹/转轮机枪等）统一 6 秒
        if (t.contains("shotgun") || t.contains("rpg") || t.contains("launcher")
                || t.contains("grenade") || t.contains("rocket") || t.contains("minigun")
                || t.contains("heavy")) {
            return BotConfig.RELOAD_HEAVY.get();
        }
        if (t.contains("rifle")) {
            return BotConfig.RELOAD_RIFLE.get();
        }
        return BotConfig.RELOAD_OTHER.get();
    }

    /** 弹匣容量（算上扩容弹匣配件）。 */
    public static int magazineSize(ItemStack gun, IGun iGun) {
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index == null) {
            return 8;
        }
        try {
            return Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData()));
        } catch (Throwable ignored) {
            return Math.max(1, index.getGunData().getAmmoAmount());
        }
    }
}
