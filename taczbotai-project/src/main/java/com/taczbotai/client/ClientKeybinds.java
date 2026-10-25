package com.taczbotai.client;

import com.taczbotai.TaczBotAi;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 路径节点录制按键。
 *
 * 默认：
 *  - 小键盘 "+"（GLFW_KEY_KP_ADD）
 *  - 主键盘 "="（按住 Shift 打出来就是 "+"）
 * 两个都绑到同一个动作，玩家可以在「选项 -> 控制 -> 按键绑定」里改成任意键。
 * 按下后只是发一条 `/taczbotai record node` 给服务端（不新增网络包）。
 */
@Mod.EventBusSubscriber(modid = TaczBotAi.MODID, value = Dist.CLIENT)
public final class ClientKeybinds {
    public static final String CATEGORY = "key.categories.taczbotai";

    public static final KeyMapping ADD_NODE = new KeyMapping(
            "key.taczbotai.add_node",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            CATEGORY);

    public static final KeyMapping ADD_NODE_ALT = new KeyMapping(
            "key.taczbotai.add_node_alt",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            CATEGORY);

    public static final KeyMapping STOP_NODE = new KeyMapping(
            "key.taczbotai.stop_node",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_SUBTRACT,
            CATEGORY);

    public static final KeyMapping STOP_NODE_ALT = new KeyMapping(
            "key.taczbotai.stop_node_alt",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
            CATEGORY);
    private ClientKeybinds() {
    }

    @Mod.EventBusSubscriber(modid = TaczBotAi.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ADD_NODE);
            event.register(ADD_NODE_ALT);
            event.register(STOP_NODE);
            event.register(STOP_NODE_ALT);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean clicked = false;
        while (ADD_NODE.consumeClick()) {
            clicked = true;
        }
        while (ADD_NODE_ALT.consumeClick()) {
            clicked = true;
        }
        if (clicked && minecraft.player != null && minecraft.screen == null) {
            minecraft.player.connection.sendCommand("taczbotai record node");
        }
        boolean stopClicked = false;
        while (STOP_NODE.consumeClick()) {
            stopClicked = true;
        }
        while (STOP_NODE_ALT.consumeClick()) {
            stopClicked = true;
        }
        if (stopClicked && minecraft.player != null && minecraft.screen == null) {
            minecraft.setScreen(new StayNodeScreen(null));
        }
    }
}
