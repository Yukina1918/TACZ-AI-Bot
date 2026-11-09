/*
 * Copyright (c) 2026 Yukina_V
 * SPDX-License-Identifier: MIT
 */
package com.taczbotai.client;

import com.taczbotai.config.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * 游戏内配置界面（模组列表 → 配置）。
 *
 * 布局：左侧分类列表（宽 25%，上下选择），右侧对应内容（宽 75%），
 * 内容超出高度时可用滚轮滚动，底部固定「保存并关闭」。
 *
 * 分类：战斗 / 数值 / 装备 / 队伍 / 路径 / 结算 / 测试人员 / 操作
 */
public class BotConfigScreen extends Screen {
    private enum Section {
        COMBAT, VALUES, EQUIP, TEAM, PATH, MATCH, DUMMY, ACTIONS
    }

    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFF;
    private static final int CATEGORY_ACTIVE = 0xFFD37F;
    private static final double LEFT_RATIO = 2.5D / 10.0D;

    private final Screen parent;

    private Section section = Section.COMBAT;
    private boolean loaded;
    /** 右侧内容的滚轮偏移。 */
    private int scroll;
    /** 当前分类内容最低点，用于计算最大滚动量。 */
    private int contentBottom = 200;

    // 战斗
    private int tier;
    private boolean friendlyFire;
    private boolean instantKill;
    private boolean killMessage;
    private boolean hostileMobs;

    // 数值
    private double rangeClose;
    private double rangeMid;
    private double rangeFar;
    private double reloadPistol;
    private double reloadRifle;
    private double reloadMarksman;
    private double reloadSniper;
    private double reloadMg;
    private double reloadHeavy;
    private double reloadOther;

    // 装备 / 队伍 / 路径
    private String gunId;
    private String attachments;
    private String gunNbt = "";
    private String names = "";
    private String nameMode = "team";
    private String teamName;
    private String pathName = "";
    private String pathMode = "loop";

    // 结算
    private boolean matchTeleport;
    private boolean matchLeaveTeam;
    private boolean matchClearInv;
    private double endX;
    private double endY;
    private double endZ;

    // 测试人员
    private String dummyName = "测试人员";
    private double dummyHealth = 20.0D;
    private boolean dummyInvulnerable;
    private boolean dummyAi;
    private double dummySpeed = 0.1D;

    // 控件引用（切分类 / 保存前回读）
    private EditBox rangeCloseBox;
    private EditBox rangeMidBox;
    private EditBox rangeFarBox;
    private EditBox reloadPistolBox;
    private EditBox reloadRifleBox;
    private EditBox reloadMarksmanBox;
    private EditBox reloadSniperBox;
    private EditBox reloadMgBox;
    private EditBox reloadHeavyBox;
    private EditBox reloadOtherBox;
    private EditBox gunIdBox;
    private EditBox attachmentsBox;
    private EditBox gunNbtBox;
    private EditBox namesBox;
    private EditBox teamBox;
    private EditBox pathBox;
    private EditBox endXBox;
    private EditBox endYBox;
    private EditBox endZBox;
    private EditBox dummyNameBox;
    private EditBox dummyHealthBox;
    private EditBox dummySpeedBox;

    public BotConfigScreen(Screen parent) {
        super(Component.translatable("taczbotai.gui.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    private int leftX() {
        return 16;
    }

    private int leftWidth() {
        return Math.max(88, (int) Math.round((this.width - 32) * LEFT_RATIO));
    }

    private int rightX() {
        return this.leftX() + this.leftWidth() + 12;
    }

    private int rightWidth() {
        return this.width - this.rightX() - 16;
    }

    @Override
    protected void init() {
        if (!this.loaded) {
            this.loadFromConfig();
            this.loaded = true;
        }
        this.rangeCloseBox = null;
        this.rangeMidBox = null;
        this.rangeFarBox = null;
        this.reloadPistolBox = null;
        this.reloadRifleBox = null;
        this.reloadMarksmanBox = null;
        this.reloadSniperBox = null;
        this.reloadMgBox = null;
        this.reloadHeavyBox = null;
        this.reloadOtherBox = null;
        this.gunIdBox = null;
        this.attachmentsBox = null;
        this.gunNbtBox = null;
        this.namesBox = null;
        this.teamBox = null;
        this.pathBox = null;
        this.endXBox = null;
        this.endYBox = null;
        this.endZBox = null;
        this.dummyNameBox = null;
        this.dummyHealthBox = null;
        this.dummySpeedBox = null;

        switch (this.section) {
            case COMBAT -> this.buildCombat();
            case VALUES -> this.buildValues();
            case EQUIP -> this.buildEquip();
            case TEAM -> this.buildTeam();
            case PATH -> this.buildPath();
            case MATCH -> this.buildMatch();
            case DUMMY -> this.buildDummy();
            case ACTIONS -> this.buildActions();
        }

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.apply"), b -> {
            this.capture();
            this.applyConfig();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 - 100, this.height - 26, 200, 18).build());

        this.buildCategories();
    }

    /** 左侧分类按钮（最后添加，渲染在最上层）。 */
    private void buildCategories() {
        int x = this.leftX();
        int w = this.leftWidth();
        int y = 32;
        for (Section target : Section.values()) {
            boolean active = target == this.section;
            Button button = Button.builder(categoryLabel(target, active), b -> this.switchSection(target))
                    .bounds(x, y, w, 18).build();
            this.addRenderableWidget(button);
            y += 21;
        }
    }

    private Component categoryLabel(Section target, boolean active) {
        String text = Component.translatable(categoryKey(target)).getString();
        return Component.literal(active ? "» " + text : text)
                .withStyle(active ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.GRAY);
    }

    private static String categoryKey(Section target) {
        return switch (target) {
            case COMBAT -> "taczbotai.gui.page.combat";
            case VALUES -> "taczbotai.gui.page.values";
            case EQUIP -> "taczbotai.gui.page.equip";
            case TEAM -> "taczbotai.gui.page.team";
            case PATH -> "taczbotai.gui.page.path";
            case MATCH -> "taczbotai.gui.page.match";
            case DUMMY -> "taczbotai.gui.page.dummy";
            case ACTIONS -> "taczbotai.gui.page.actions";
        };
    }

    private void switchSection(Section target) {
        if (target == this.section) {
            return;
        }
        this.capture();
        this.section = target;
        this.scroll = 0;
        this.clearWidgets();
        this.init();
    }

    // ------------------------------------------------------------------
    // 滚动
    // ------------------------------------------------------------------

    private int maxScroll() {
        return Math.max(0, this.contentBottom - (this.height - 40));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = this.maxScroll();
        if (max <= 0 || mouseX < this.rightX()) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int before = this.scroll;
        this.scroll = Mth.clamp(this.scroll - (int) Math.round(delta * 24.0D), 0, max);
        if (this.scroll != before) {
            this.capture();
            this.clearWidgets();
            this.init();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 读取 / 回写
    // ------------------------------------------------------------------

    private void loadFromConfig() {
        this.tier = BotConfig.guiValue(BotConfig.ATTACK_TIER);
        this.friendlyFire = BotConfig.guiValue(BotConfig.FRIENDLY_FIRE);
        this.instantKill = BotConfig.guiValue(BotConfig.INSTANT_KILL_ENABLED);
        this.killMessage = BotConfig.guiValue(BotConfig.KILL_MESSAGE);
        this.hostileMobs = BotConfig.guiValue(BotConfig.ATTACK_HOSTILE_MOBS);
        this.rangeClose = BotConfig.guiValue(BotConfig.RANGE_CLOSE);
        this.rangeMid = BotConfig.guiValue(BotConfig.RANGE_MID);
        this.rangeFar = BotConfig.guiValue(BotConfig.RANGE_FAR);
        this.reloadPistol = BotConfig.guiValue(BotConfig.RELOAD_PISTOL);
        this.reloadRifle = BotConfig.guiValue(BotConfig.RELOAD_RIFLE);
        this.reloadMarksman = BotConfig.guiValue(BotConfig.RELOAD_MARKSMAN);
        this.reloadSniper = BotConfig.guiValue(BotConfig.RELOAD_SNIPER);
        this.reloadMg = BotConfig.guiValue(BotConfig.RELOAD_MG);
        this.reloadHeavy = BotConfig.guiValue(BotConfig.RELOAD_HEAVY);
        this.reloadOther = BotConfig.guiValue(BotConfig.RELOAD_OTHER);
        this.gunId = BotConfig.guiValue(BotConfig.GUN_ID);
        this.gunNbt = BotConfig.guiValue(BotConfig.GUN_NBT);
        this.attachments = String.join(",", BotConfig.guiValue(BotConfig.ATTACHMENTS));
        this.teamName = BotConfig.guiValue(BotConfig.DEFAULT_TEAM);
        this.pathMode = BotConfig.guiValue(BotConfig.PATH_MODE);
        this.nameMode = BotConfig.guiValue(BotConfig.NAME_TAG_MODE);
        this.names = String.join(",", BotConfig.guiValue(BotConfig.NAMES));
        this.matchTeleport = BotConfig.guiValue(BotConfig.MATCH_END_TELEPORT);
        this.matchLeaveTeam = BotConfig.guiValue(BotConfig.MATCH_END_LEAVE_TEAM);
        this.matchClearInv = BotConfig.guiValue(BotConfig.MATCH_END_CLEAR_INVENTORY);
        this.endX = BotConfig.guiValue(BotConfig.MATCH_END_X);
        this.endY = BotConfig.guiValue(BotConfig.MATCH_END_Y);
        this.endZ = BotConfig.guiValue(BotConfig.MATCH_END_Z);
        this.dummyName = BotConfig.guiValue(BotConfig.DUMMY_NAME);
        this.dummyHealth = BotConfig.guiValue(BotConfig.DUMMY_HEALTH);
        this.dummyInvulnerable = BotConfig.guiValue(BotConfig.DUMMY_INVULNERABLE);
        this.dummyAi = BotConfig.guiValue(BotConfig.DUMMY_AI_ENABLED);
        this.dummySpeed = BotConfig.guiValue(BotConfig.DUMMY_AI_SPEED);
    }

    private void capture() {
        this.rangeClose = read(this.rangeCloseBox, this.rangeClose);
        this.rangeMid = read(this.rangeMidBox, this.rangeMid);
        this.rangeFar = read(this.rangeFarBox, this.rangeFar);
        this.reloadPistol = read(this.reloadPistolBox, this.reloadPistol);
        this.reloadRifle = read(this.reloadRifleBox, this.reloadRifle);
        this.reloadMarksman = read(this.reloadMarksmanBox, this.reloadMarksman);
        this.reloadSniper = read(this.reloadSniperBox, this.reloadSniper);
        this.reloadMg = read(this.reloadMgBox, this.reloadMg);
        this.reloadHeavy = read(this.reloadHeavyBox, this.reloadHeavy);
        this.reloadOther = read(this.reloadOtherBox, this.reloadOther);
        if (this.gunIdBox != null) {
            this.gunId = this.gunIdBox.getValue().trim();
        }
        if (this.attachmentsBox != null) {
            this.attachments = this.attachmentsBox.getValue();
        }
        if (this.gunNbtBox != null) {
            this.gunNbt = this.gunNbtBox.getValue();
        }
        if (this.namesBox != null) {
            this.names = this.namesBox.getValue();
        }
        if (this.teamBox != null) {
            this.teamName = this.teamBox.getValue().trim();
        }
        if (this.pathBox != null) {
            this.pathName = this.pathBox.getValue().trim();
        }
        this.endX = read(this.endXBox, this.endX);
        this.endY = read(this.endYBox, this.endY);
        this.endZ = read(this.endZBox, this.endZ);
        if (this.dummyNameBox != null) {
            this.dummyName = this.dummyNameBox.getValue();
        }
        this.dummyHealth = read(this.dummyHealthBox, this.dummyHealth);
        this.dummySpeed = read(this.dummySpeedBox, this.dummySpeed);
    }

    private static double read(EditBox box, double fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // 战斗
    // ------------------------------------------------------------------

    private void buildCombat() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.toggle(x, row, w, Component.translatable("taczbotai.gui.tier", this.tier),
                () -> Component.translatable("taczbotai.gui.tier", this.tier),
                () -> this.tier = this.tier >= 5 ? 1 : this.tier + 1);
        row += 22;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.friendly_fire", onOff(this.friendlyFire)),
                () -> Component.translatable("taczbotai.gui.friendly_fire", onOff(this.friendlyFire)),
                () -> this.friendlyFire = !this.friendlyFire);
        row += 22;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.instant_kill", onOff(this.instantKill)),
                () -> Component.translatable("taczbotai.gui.instant_kill", onOff(this.instantKill)),
                () -> this.instantKill = !this.instantKill);
        row += 22;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.kill_message", onOff(this.killMessage)),
                () -> Component.translatable("taczbotai.gui.kill_message", onOff(this.killMessage)),
                () -> this.killMessage = !this.killMessage);
        row += 22;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.attack_hostile", onOff(this.hostileMobs)),
                () -> Component.translatable("taczbotai.gui.attack_hostile", onOff(this.hostileMobs)),
                () -> this.hostileMobs = !this.hostileMobs);
        row += 22;
        this.contentBottom = row + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 数值
    // ------------------------------------------------------------------

    private void buildValues() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;
        int half = Math.max(120, (w - 12) / 2);
        int col2 = x + half + 12;

        this.rangeCloseBox = this.labeledNumber(x, row, half, "taczbotai.gui.range_close", this.rangeClose);
        this.rangeMidBox = this.labeledNumber(x, row + 22, half, "taczbotai.gui.range_mid", this.rangeMid);
        this.rangeFarBox = this.labeledNumber(x, row + 44, half, "taczbotai.gui.range_far", this.rangeFar);

        this.reloadPistolBox = this.labeledNumber(col2, row, half, "taczbotai.gui.reload_pistol", this.reloadPistol);
        this.reloadRifleBox = this.labeledNumber(col2, row + 22, half, "taczbotai.gui.reload_rifle", this.reloadRifle);
        this.reloadMarksmanBox = this.labeledNumber(col2, row + 44, half, "taczbotai.gui.reload_marksman", this.reloadMarksman);
        this.reloadSniperBox = this.labeledNumber(col2, row + 66, half, "taczbotai.gui.reload_sniper", this.reloadSniper);
        this.reloadMgBox = this.labeledNumber(col2, row + 88, half, "taczbotai.gui.reload_mg", this.reloadMg);
        this.reloadHeavyBox = this.labeledNumber(col2, row + 110, half, "taczbotai.gui.reload_heavy", this.reloadHeavy);
        this.reloadOtherBox = this.labeledNumber(x, row + 66, half, "taczbotai.gui.reload_other", this.reloadOther);

        this.contentBottom = row + 110 + 22 + this.scroll;
    }

    // ------------------------------------------------------------------
    // 装备
    // ------------------------------------------------------------------

    private void buildEquip() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.gunIdBox = this.labeledText(x, row, w, "taczbotai.gui.gun_id", this.gunId, 4096);
        this.gunIdBox.setSuggestion(this.gunId == null || this.gunId.isEmpty() ? "tacz:m1911" : "");
        row += 22;
        this.attachmentsBox = this.labeledText(x, row, w, "taczbotai.gui.attachments", this.attachments, 4096);
        this.attachmentsBox.setSuggestion(this.attachments == null || this.attachments.isBlank()
                ? "SCOPE=tacz:scope_acog_ta31,GRIP=tacz:grip_vertical_talon" : "");
        row += 22;
        this.gunNbtBox = this.labeledText(x, row, w - 96, "taczbotai.gui.gun_nbt", this.gunNbt, 65536);
        this.gunNbtBox.setEditable(false);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.gun_nbt.read"), b ->
                        this.readHeldGunNbt())
                .bounds(x + w - 92, row, 44, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.gun_nbt.clear"), b -> {
            this.gunNbt = "";
            if (this.gunNbtBox != null) {
                this.gunNbtBox.setValue("");
            }
        }).bounds(x + w - 44, row, 44, 16).build());
        row += 26;
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.equip"), b ->
                        this.sendCommand("taczbotai equip"))
                .bounds(x, row, (w - 8) / 2, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.gun_nbt.copy"), b -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.keyboardHandler.setClipboard(this.gunNbt == null ? "" : this.gunNbt);
                minecraft.player.displayClientMessage(Component.translatable(
                        "taczbotai.gui.gun_nbt.copied", this.gunNbt == null ? 0 : this.gunNbt.length()), false);
            }
        }).bounds(x + (w + 8) / 2, row, (w - 8) / 2, 18).build());

        this.contentBottom = row + 18 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 队伍
    // ------------------------------------------------------------------

    private void buildTeam() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.teamBox = this.labeledText(x, row, w - 100, "taczbotai.gui.team", this.teamName, 64);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.set_team"), b ->
                        this.sendCommand("taczbotai team set " + this.teamBox.getValue().trim()))
                .bounds(x + w - 96, row, 46, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.clear_team"), b ->
                        this.sendCommand("taczbotai team clear"))
                .bounds(x + w - 46, row, 46, 16).build());
        row += 24;

        this.namesBox = this.labeledText(x, row, w, "taczbotai.gui.names", this.names, 4096);
        row += 24;

        Component[] modeText = new Component[1];
        modeText[0] = Component.translatable("taczbotai.gui.name_mode",
                Component.translatable(nameModeKey(this.nameMode)));
        this.addRenderableWidget(Button.builder(modeText[0], b -> {
            this.nameMode = nextNameMode(this.nameMode);
            b.setMessage(Component.translatable("taczbotai.gui.name_mode",
                    Component.translatable(nameModeKey(this.nameMode))));
            this.sendCommand("taczbotai team namemode " + this.nameMode + " 64");
        }).bounds(x, row, w, 18).build());
        row += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.show_name_on"), b ->
                        this.sendCommand("taczbotai team showname true 64"))
                .bounds(x, row, (w - 8) / 2, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.show_name_off"), b ->
                        this.sendCommand("taczbotai team showname false 64"))
                .bounds(x + (w + 8) / 2, row, (w - 8) / 2, 18).build());

        this.contentBottom = row + 18 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 路径
    // ------------------------------------------------------------------

    private void buildPath() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.pathBox = this.labeledText(x, row, w - 190, "taczbotai.gui.path_short", this.pathName, 128);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.assign_path"), b ->
                        this.sendCommand("taczbotai path assign " + this.pathBox.getValue().trim()))
                .bounds(x + w - 186, row, 86, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.list_paths"), b ->
                        this.sendCommand("taczbotai path list"))
                .bounds(x + w - 96, row, 96, 16).build());
        row += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.path_mode",
                Component.translatable(pathModeKey(this.pathMode))), b -> {
            this.pathMode = nextPathMode(this.pathMode);
            b.setMessage(Component.translatable("taczbotai.gui.path_mode",
                    Component.translatable(pathModeKey(this.pathMode))));
        }).bounds(x, row, w, 18).build());
        row += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.clear_path"), b ->
                        this.sendCommand("taczbotai path clear"))
                .bounds(x, row, (w - 8) / 2, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.path_hint"), b -> {
        }).bounds(x + (w + 8) / 2, row, (w - 8) / 2, 18).build()).active = false;

        this.contentBottom = row + 18 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 结算
    // ------------------------------------------------------------------

    private void buildMatch() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.toggle(x, row, w, Component.translatable("taczbotai.gui.match_teleport", onOff(this.matchTeleport)),
                () -> Component.translatable("taczbotai.gui.match_teleport", onOff(this.matchTeleport)),
                () -> this.matchTeleport = !this.matchTeleport);
        row += 22;

        int third = Math.max(70, (w - 24) / 3);
        this.endXBox = this.labeledTiny(x, row, third, "taczbotai.gui.end_x", this.endX);
        this.endYBox = this.labeledTiny(x + third + 12, row, third, "taczbotai.gui.end_y", this.endY);
        this.endZBox = this.labeledTiny(x + (third + 12) * 2, row, third, "taczbotai.gui.end_z", this.endZ);
        row += 24;

        this.toggle(x, row, w, Component.translatable("taczbotai.gui.match_leave_team", onOff(this.matchLeaveTeam)),
                () -> Component.translatable("taczbotai.gui.match_leave_team", onOff(this.matchLeaveTeam)),
                () -> this.matchLeaveTeam = !this.matchLeaveTeam);
        row += 22;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.match_clear_inv", onOff(this.matchClearInv)),
                () -> Component.translatable("taczbotai.gui.match_clear_inv", onOff(this.matchClearInv)),
                () -> this.matchClearInv = !this.matchClearInv);

        this.contentBottom = row + 22 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 测试人员
    // ------------------------------------------------------------------

    private void buildDummy() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.dummyNameBox = this.labeledText(x, row, w, "taczbotai.gui.dummy_name", this.dummyName, 128);
        row += 22;
        this.dummyHealthBox = this.labeledNumber(x, row, Math.max(120, w / 2), "taczbotai.gui.dummy_health", this.dummyHealth);
        row += 24;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.dummy_ai", onOff(this.dummyAi)),
                () -> Component.translatable("taczbotai.gui.dummy_ai", onOff(this.dummyAi)),
                () -> this.dummyAi = !this.dummyAi);
        row += 24;
        this.dummySpeedBox = this.labeledNumber(x, row, Math.max(120, w / 2), "taczbotai.gui.dummy_speed", this.dummySpeed);
        row += 24;
        this.toggle(x, row, w, Component.translatable("taczbotai.gui.dummy_invulnerable", onOff(this.dummyInvulnerable)),
                () -> Component.translatable("taczbotai.gui.dummy_invulnerable", onOff(this.dummyInvulnerable)),
                () -> this.dummyInvulnerable = !this.dummyInvulnerable);
        row += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.testsub"), b ->
                        this.sendCommand("taczbotai testsub 1"))
                .bounds(x, row, (w - 8) / 2, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.spawn"), b ->
                        this.sendCommand("taczbotai spawn 1"))
                .bounds(x + (w + 8) / 2, row, (w - 8) / 2, 18).build());

        this.contentBottom = row + 18 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void buildActions() {
        int x = this.rightX();
        int w = this.rightWidth();
        int row = 32 - this.scroll;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.equip"), b ->
                        this.sendCommand("taczbotai equip"))
                .bounds(x, row, w, 20).build());
        row += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.spawn"), b ->
                        this.sendCommand("taczbotai spawn 1"))
                .bounds(x, row, w, 20).build());
        row += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.testsub"), b ->
                        this.sendCommand("taczbotai testsub 1"))
                .bounds(x, row, w, 20).build());
        row += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.info"), b ->
                        this.sendCommand("taczbotai info"))
                .bounds(x, row, w, 20).build());

        this.contentBottom = row + 20 + this.scroll + 16;
    }

    // ------------------------------------------------------------------
    // 控件工厂
    // ------------------------------------------------------------------

    private void toggle(int x, int y, int width, Component initial, java.util.function.Supplier<Component> text,
                        Runnable action) {
        this.addRenderableWidget(Button.builder(initial, b -> {
            action.run();
            b.setMessage(text.get());
        }).bounds(x, y, width, 18).build());
    }

    private EditBox labeledText(int x, int y, int width, String labelKey, String value, int maxLength) {
        return this.labeledText(x, y, width, labelKey, value, maxLength, 120);
    }

    /** 窄标签行（X/Y/Z 这类单字符标签用它，避免占位过宽把输入框挤出屏幕）。 */
    private EditBox labeledTiny(int x, int y, int width, String labelKey, double value) {
        return this.labeledText(x, y, width, labelKey, format(value), 16, 16);
    }

    private EditBox labeledText(int x, int y, int width, String labelKey, String value,
                                int maxLength, int maxLabelWidth) {
        String label = Component.translatable(labelKey).getString();
        int labelWidth = Math.min(maxLabelWidth, Math.max(14, this.font.width(label) + 6));
        this.addRenderableWidget(new StringWidget(x, y + 4, labelWidth, 12, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, x + labelWidth + 4, y,
                Math.max(40, width - labelWidth - 4), 16, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        this.addRenderableWidget(box);
        return box;
    }

    private EditBox labeledNumber(int x, int y, int width, String labelKey, double value) {
        return this.labeledText(x, y, width, labelKey, format(value), 16);
    }

    // ------------------------------------------------------------------
    // 文本 / 保存
    // ------------------------------------------------------------------

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "taczbotai.gui.on" : "taczbotai.gui.off");
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String nameModeKey(String mode) {
        if (mode != null && mode.equalsIgnoreCase("hidden")) {
            return "taczbotai.name_mode.hidden";
        }
        if (mode != null && mode.equalsIgnoreCase("always")) {
            return "taczbotai.name_mode.always";
        }
        return "taczbotai.name_mode.team";
    }

    private static String nextNameMode(String current) {
        if (current == null || current.equalsIgnoreCase("team")) {
            return "always";
        }
        if (current.equalsIgnoreCase("always")) {
            return "hidden";
        }
        return "team";
    }

    private static String pathModeKey(String mode) {
        if (mode != null && mode.equalsIgnoreCase("once")) {
            return "taczbotai.gui.pathmode.once";
        }
        if (mode != null && mode.equalsIgnoreCase("pingpong")) {
            return "taczbotai.gui.pathmode.pingpong";
        }
        return "taczbotai.gui.pathmode.loop";
    }

    private static String nextPathMode(String current) {
        if (current == null || current.equalsIgnoreCase("loop")) {
            return "pingpong";
        }
        if (current.equalsIgnoreCase("pingpong")) {
            return "once";
        }
        return "loop";
    }

    private void applyConfig() {
        this.capture();
        sendConfig("attack.attackTier", String.valueOf(this.tier));
        sendConfig("team.friendlyFire", String.valueOf(this.friendlyFire));
        sendConfig("attack.instantKillEnabled", String.valueOf(this.instantKill));
        sendConfig("message.killMessageEnabled", String.valueOf(this.killMessage));
        sendConfig("attack.attackHostileMobs", String.valueOf(this.hostileMobs));
        sendConfig("attack.range.close", format(clamp(this.rangeClose, 2.0D, 128.0D)));
        sendConfig("attack.range.mid", format(clamp(this.rangeMid, 2.0D, 256.0D)));
        sendConfig("attack.range.far", format(clamp(this.rangeFar, 2.0D, 512.0D)));
        sendConfig("reload.pistolSeconds", format(clamp(this.reloadPistol, 0.1D, 60.0D)));
        sendConfig("reload.rifleSeconds", format(clamp(this.reloadRifle, 0.1D, 60.0D)));
        sendConfig("reload.marksmanSeconds", format(clamp(this.reloadMarksman, 0.1D, 60.0D)));
        sendConfig("reload.sniperSeconds", format(clamp(this.reloadSniper, 0.1D, 60.0D)));
        sendConfig("reload.mgSeconds", format(clamp(this.reloadMg, 0.1D, 60.0D)));
        sendConfig("reload.shotgunSeconds", format(clamp(this.reloadHeavy, 0.1D, 60.0D)));
        sendConfig("reload.heavySeconds", format(clamp(this.reloadHeavy, 0.1D, 60.0D)));
        sendConfig("reload.otherSeconds", format(clamp(this.reloadOther, 0.1D, 60.0D)));
        sendConfig("bot.gunId", this.gunId == null ? "" : this.gunId);
        sendConfig("bot.attachments", this.attachments == null ? "" : this.attachments);
        sendConfig("bot.names", this.names == null ? "" : this.names);
        sendConfig("bot.nameTagMode", this.nameMode == null ? "team" : this.nameMode);
        sendConfig("team.defaultTeam", this.teamName == null ? "" : this.teamName);
        sendConfig("path.pathMode", this.pathMode == null ? "loop" : this.pathMode);
        sendConfig("match.endTeleport", String.valueOf(this.matchTeleport));
        sendConfig("match.endX", format(this.endX));
        sendConfig("match.endY", format(this.endY));
        sendConfig("match.endZ", format(this.endZ));
        sendConfig("match.endLeaveTeam", String.valueOf(this.matchLeaveTeam));
        sendConfig("match.endClearInventory", String.valueOf(this.matchClearInv));
        sendConfig("dummy.name", this.dummyName == null ? "" : this.dummyName);
        sendConfig("dummy.health", format(clamp(this.dummyHealth, 1.0D, 10240.0D)));
        sendConfig("dummy.invulnerable", String.valueOf(this.dummyInvulnerable));
        sendConfig("dummy.aiEnabled", String.valueOf(this.dummyAi));
        sendConfig("dummy.aiSpeed", format(clamp(this.dummySpeed, 0.0D, 1.0D)));
    }

    private void sendConfig(String key, String value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String cleaned = value == null ? "" : value.replace("\n", " ").trim();
        minecraft.player.connection.sendCommand("taczbotai config set " + key + " " + cleaned);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    private void readHeldGunNbt() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = minecraft.player.getMainHandItem();
        if (com.tacz.guns.api.item.IGun.getIGunOrNull(stack) == null) {
            minecraft.player.displayClientMessage(Component.translatable("taczbotai.gui.gun_nbt.not_gun"), false);
            return;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        String snbt = tag == null ? "{}" : tag.toString();
        this.gunNbt = snbt;
        if (this.gunNbtBox != null) {
            this.gunNbtBox.setValue(snbt);
        }
        minecraft.keyboardHandler.setClipboard(snbt);
        minecraft.player.displayClientMessage(
                Component.translatable("taczbotai.gui.gun_nbt.copied", snbt.length()), false);
        minecraft.player.connection.sendCommand("taczbotai gun");
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, VALUE_COLOR);

        int x = this.leftX();
        int y = 32;
        int w = this.leftWidth();
        int listBottom = y + Section.values().length * 21 + 6;
        graphics.fill(x - 4, 24, x + w + 4, listBottom, 0x30FFFFFF);
        graphics.fill(this.rightX() - 6, 24, this.width - 12, this.height - 32, 0x18FFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int top = 30;
        int bottom = this.height - 32;
        int track = bottom - top;
        int x = this.width - 10;
        graphics.fill(x, top, x + 4, bottom, 0x40FFFFFF);
        int thumb = Math.max(16, track * track / (track + max));
        int thumbTop = top + (int) ((track - thumb) * ((double) this.scroll / max));
        graphics.fill(x, thumbTop, x + 4, thumbTop + thumb, 0xC0FFFFFF);
    }

    @Override
    public void onClose() {
        this.applyConfig();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
