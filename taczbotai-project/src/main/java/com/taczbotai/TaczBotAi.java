package com.taczbotai;

import com.mojang.logging.LogUtils;
import com.taczbotai.config.BotConfig;
import com.taczbotai.entity.TaczBotEntity;
import com.taczbotai.registry.ModEntities;
import com.taczbotai.registry.ModItems;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * taczbotai - TACZ 枪械 AI 生物模组。
 *
 * 设计要点（详见 README_设计说明.md）：
 *  - 生物是原生 Forge 实体（PathfinderMob），不使用 CustomNPCs / 假玩家 / KubeJS / EntityJS。
 *  - 射击、持枪/瞄准动画全部交给 TACZ 自身的 API 与 mixin（IGunOperator 被 TACZ 注入到 LivingEntity），
 *    本模组只负责「瞄准谁、打偏多少、什么时候开火/换弹」。
 */
@Mod(TaczBotAi.MODID)
public class TaczBotAi {
    public static final String MODID = "taczbotai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczBotAi() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onBuildTabs);

        // SERVER 配置：只存在于服务端/房主侧（客户端拿到的是只读同步副本），
        // 因此只有房主或服务器管理员能改，普通成员无法修改规则。
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BotConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(com.taczbotai.event.CommonEvents.class);

        LOGGER.info("[taczbotai] initialized");
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TACZ_BOT.get(), TaczBotEntity.createAttributes().build());
    }

    private void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TACZ_BOT_SPAWN_EGG);
        }
    }

    /** 供 GM 用的简易属性构造器（避免外部直接依赖内部类）。 */
    public static AttributeSupplier.Builder botAttributes() {
        return TaczBotEntity.createAttributes();
    }

    /** 客户端专用的注册（渲染器 / 配置界面）都在这里被间接调用，保证专用服务器不会加载客户端类。 */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(com.taczbotai.client.ClientSetup::init);
        }
    }
}
