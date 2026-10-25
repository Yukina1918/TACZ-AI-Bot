package com.taczbotai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** bf 模式按 `-` 弹出的「停留节点时长」窗口：分 + 秒，确认后发给服务端记录节点。 */
public class StayNodeScreen extends Screen {
    private final Screen parent;
    private EditBox minutes;
    private EditBox seconds;

    public StayNodeScreen(Screen parent) {
        super(Component.translatable("taczbotai.gui.stay_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.minutes = new EditBox(this.font, cx - 70, 72, 60, 18,
                Component.translatable("taczbotai.gui.stay_minutes"));
        this.minutes.setMaxLength(3);
        this.minutes.setValue("1");
        this.seconds = new EditBox(this.font, cx + 10, 72, 60, 18,
                Component.translatable("taczbotai.gui.stay_seconds"));
        this.seconds.setMaxLength(2);
        this.seconds.setValue("0");
        this.addRenderableWidget(this.minutes);
        this.addRenderableWidget(this.seconds);

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.stay_confirm"), b -> {
            int total = parse(this.minutes.getValue(), 0) * 60 + parse(this.seconds.getValue(), 0);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand("taczbotai record node stop " + total);
            }
            this.onClose();
        }).bounds(cx - 100, 108, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.stay_cancel"), b -> this.onClose())
                .bounds(cx + 5, 108, 95, 20).build());
    }

    private static int parse(String raw, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 32, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("taczbotai.gui.stay_minutes"),
                this.width / 2 - 70, 58, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("taczbotai.gui.stay_seconds"),
                this.width / 2 + 10, 58, 0xA0A0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
