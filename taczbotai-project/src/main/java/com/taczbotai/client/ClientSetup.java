package com.taczbotai.client;

import com.taczbotai.TaczBotAi;
import com.taczbotai.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/** 客户端注册：实体渲染器 + 模组列表里的配置界面。 */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init() {
        // 模组列表 -> 配置 按钮直接打开我们的 GUI（Forge 自带扩展点）
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new BotConfigScreen(parent)));
    }

    @Mod.EventBusSubscriber(modid = TaczBotAi.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.TACZ_BOT.get(), TaczBotRenderer::new);
        }
    }
}
