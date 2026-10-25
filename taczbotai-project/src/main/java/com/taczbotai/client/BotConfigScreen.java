package com.taczbotai.client;

import com.taczbotai.config.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 游戏内配置界面（模组列表 → 配置）。
 *
 * 采用分页布局，避免字段太多时控件互相重叠：
 *  - 战斗：档位 / 友伤 / 锁头 / 击杀播报 / 是否打敌对生物
 *  - 数值：三档交战距离 + 七类换弹时间
 *  - 其它：枪械与配件、队伍、路径模式与分配、快捷操作
 */
public class BotConfigScreen extends Screen {
    private enum Page {
        COMBAT, VALUES, MISC, MATCH
    }

    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFF;

    private final Screen parent;

    private Page page = Page.COMBAT;
    private boolean loaded;
    /** 滚轮偏移（像素），内容超出可视区域时生效。 */
    private int scroll;

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

    // 其它
    private String gunId;
    private String attachments;
    private String teamName;
    private String pathName = "";
    private String pathMode = "loop";
    private String gunNbt = "";
    private String nameMode = "team";
    private String names = "";
    private boolean matchTeleport;
    private boolean matchLeaveTeam;
    private boolean matchClearInv;
    private double endX;
    private double endY;
    private double endZ;

    // 当前页控件（切页/保存前回读）
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
    private EditBox teamBox;
    private EditBox pathBox;
    private EditBox gunNbtBox;
    private EditBox namesBox;
    private EditBox endXBox;
    private EditBox endYBox;
    private EditBox endZBox;

    public BotConfigScreen(Screen parent) {
        super(Component.translatable("taczbotai.gui.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

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
        this.teamBox = null;
        this.pathBox = null;
        this.gunNbtBox = null;
        this.namesBox = null;
        this.endXBox = null;
        this.endYBox = null;
        this.endZBox = null;

        switch (this.page) {
            case COMBAT -> this.buildCombatPage();
            case VALUES -> this.buildValuesPage();
            case MISC -> this.buildMiscPage();
            case MATCH -> this.buildMatchPage();
        }
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.apply"), b -> {
            this.capture();
            this.applyConfig();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
        // 页签最后添加：渲染顺序靠后，内容滚到顶部时会被它遮住
        this.buildTabs();
    }

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
    }

    private void buildTabs() {
        int tabWidth = 92;
        int x = this.width / 2 - (tabWidth * 3 + 8) / 2;
        this.addRenderableWidget(Button.builder(tabLabel(Page.COMBAT), b -> this.switchPage(Page.COMBAT))
                .bounds(x, 20, tabWidth, 18).build());
        this.addRenderableWidget(Button.builder(tabLabel(Page.VALUES), b -> this.switchPage(Page.VALUES))
                .bounds(x + tabWidth + 4, 20, tabWidth, 18).build());
        this.addRenderableWidget(Button.builder(tabLabel(Page.MISC), b -> this.switchPage(Page.MISC))
                .bounds(x + (tabWidth + 4) * 2, 20, tabWidth, 18).build());
        this.addRenderableWidget(Button.builder(tabLabel(Page.MATCH), b -> this.switchPage(Page.MATCH))
                .bounds(x + (tabWidth + 4) * 3, 20, tabWidth, 18).build());
    }

    private Component tabLabel(Page target) {
        String key = switch (target) {
            case COMBAT -> "taczbotai.gui.page.combat";
            case VALUES -> "taczbotai.gui.page.values";
            case MISC -> "taczbotai.gui.page.misc";
            case MATCH -> "taczbotai.gui.page.match";
        };
        String text = Component.translatable(key).getString();
        return Component.literal(this.page == target ? "» " + text + " «" : text);
    }

    private void switchPage(Page target) {
        this.capture();
        this.page = target;
        this.scroll = 0;
        this.clearWidgets();
        this.init();
    }

    // ------------------------------------------------------------------
    // 滚轮滚动
    // ------------------------------------------------------------------

    /** 当前页内容最低点（含留白）。 */
    private int contentBottom() {
        return switch (this.page) {
            case COMBAT -> 176;
            case VALUES -> 212;
            case MISC -> 276;
            case MATCH -> 230;
        };
    }

    private int maxScroll() {
        return Math.max(0, this.contentBottom() - (this.height - 44));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = this.maxScroll();
        if (max <= 0) {
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

    /** 把当前页输入框里的值回读到字段（切页 / 保存前调用）。 */
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
        if (this.teamBox != null) {
            this.teamName = this.teamBox.getValue().trim();
        }
        if (this.pathBox != null) {
            this.pathName = this.pathBox.getValue().trim();
        }
        if (this.gunNbtBox != null) {
            this.gunNbt = this.gunNbtBox.getValue();
        }
        if (this.namesBox != null) {
            this.names = this.namesBox.getValue();
        }
        this.endX = readDouble(this.endXBox, this.endX);
        this.endY = readDouble(this.endYBox, this.endY);
        this.endZ = readDouble(this.endZBox, this.endZ);
    }

    private static double readDouble(EditBox box, double fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
    // 战斗页
    // ------------------------------------------------------------------

    private void buildCombatPage() {
        int left = this.width / 2 - 150;
        int row = 52 - this.scroll;
        int step = 24;

        this.addRenderableWidget(Button.builder(tierText(), b -> {
            this.tier = this.tier >= 5 ? 1 : this.tier + 1;
            b.setMessage(tierText());
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.addRenderableWidget(Button.builder(friendlyFireText(), b -> {
            this.friendlyFire = !this.friendlyFire;
            b.setMessage(friendlyFireText());
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.addRenderableWidget(Button.builder(instantKillText(), b -> {
            this.instantKill = !this.instantKill;
            b.setMessage(instantKillText());
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.addRenderableWidget(Button.builder(killMessageText(), b -> {
            this.killMessage = !this.killMessage;
            b.setMessage(killMessageText());
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.addRenderableWidget(Button.builder(hostileText(), b -> {
            this.hostileMobs = !this.hostileMobs;
            b.setMessage(hostileText());
        }).bounds(left, row, 300, 20).build());
    }

    // ------------------------------------------------------------------
    // 数值页
    // ------------------------------------------------------------------

    private void buildValuesPage() {
        int colLeft = this.width / 2 - 150;
        int colRight = this.width / 2 + 10;
        int row = 54 - this.scroll;
        int step = 22;

        this.rangeCloseBox = this.addLabeledNumber(colLeft, row, "taczbotai.gui.range.close", this.rangeClose);
        row += step;
        this.rangeMidBox = this.addLabeledNumber(colLeft, row, "taczbotai.gui.range.mid", this.rangeMid);
        row += step;
        this.rangeFarBox = this.addLabeledNumber(colLeft, row, "taczbotai.gui.range.far", this.rangeFar);

        int rightRow = 54 - this.scroll;
        this.reloadPistolBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.pistol", this.reloadPistol);
        rightRow += step;
        this.reloadRifleBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.rifle", this.reloadRifle);
        rightRow += step;
        this.reloadMarksmanBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.marksman", this.reloadMarksman);
        rightRow += step;
        this.reloadSniperBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.sniper", this.reloadSniper);
        rightRow += step;
        this.reloadMgBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.mg", this.reloadMg);
        rightRow += step;
        this.reloadHeavyBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.heavy", this.reloadHeavy);
        rightRow += step;
        this.reloadOtherBox = this.addLabeledNumber(colRight, rightRow, "taczbotai.gui.reload.other", this.reloadOther);
    }

    private EditBox addLabeledNumber(int x, int y, String labelKey, double value) {
        String text = Component.translatable(labelKey).getString();
        this.addRenderableWidget(new StringWidget(x, y + 4, 145, 12, Component.literal(text), this.font));
        EditBox box = new EditBox(this.font, x + 148, y, 46, 16, Component.literal(text));
        box.setMaxLength(8);
        box.setValue(format(value));
        this.addRenderableWidget(box);
        return box;
    }

    // ------------------------------------------------------------------
    // 装备 / 队伍 / 路径页
    // ------------------------------------------------------------------

    private void buildMiscPage() {
        int left = this.width / 2 - 150;
        int row = 54 - this.scroll;
        int step = 24;

        this.gunIdBox = this.addLabeledText(left, row, "taczbotai.gui.gun_id_short", this.gunId, 190);
        row += step;
        this.attachmentsBox = this.addLabeledText(left, row, "taczbotai.gui.attachments_short", this.attachments, 190);
        this.attachmentsBox.setMaxLength(4096);
        row += step;

        this.gunNbtBox = this.addLabeledText(left, row, "taczbotai.gui.gun_nbt_short", this.gunNbt, 76);
        // NBT 可能上千字符：EditBox 默认 512 会被截断，这里放宽到 64k；
        // 但配置是 SERVER 类型，NBT 只能由服务端写，所以这里只读显示。
        this.gunNbtBox.setMaxLength(65536);
        this.gunNbtBox.setEditable(false);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.read_gun_nbt"), b ->
                        this.readHeldGunNbt())
                .bounds(left + 152, row, 68, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.clear_nbt"), b -> {
            this.gunNbt = "";
            if (this.gunNbtBox != null) {
                this.gunNbtBox.setValue("");
            }
            this.sendCommand("taczbotai gun clear");
        }).bounds(left + 224, row, 68, 16).build());
        row += step;

        // 名字列表（逗号分隔）—— 存在服务器配置里，保存时通过 config set 下发
        this.namesBox = this.addLabeledText(left, row, "taczbotai.gui.names_short", this.names, 190);
        this.namesBox.setMaxLength(4096);
        row += step + 4;

        this.teamBox = this.addLabeledText(left, row, "taczbotai.gui.team_short", this.teamName, 70);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.set_team"), b ->
                        this.sendCommand("taczbotai team set " + this.teamBox.getValue().trim()))
                .bounds(left + 150, row, 70, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.clear_team"), b ->
                        this.sendCommand("taczbotai team clear"))
                .bounds(left + 224, row, 70, 16).build());
        row += step;

        this.pathBox = this.addLabeledText(left, row, "taczbotai.gui.path_short", this.pathName, 70);
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.assign_path"), b ->
                        this.sendCommand("taczbotai path assign " + this.pathBox.getValue().trim()))
                .bounds(left + 150, row, 70, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.list_paths"), b ->
                        this.sendCommand("taczbotai path list"))
                .bounds(left + 224, row, 70, 16).build());
        row += step + 4;

        this.addRenderableWidget(Button.builder(this.pathModeText(), b -> {
            this.pathMode = nextPathMode(this.pathMode);
            b.setMessage(this.pathModeText());
        }).bounds(left, row, 300, 20).build());
        row += step + 4;

        this.addRenderableWidget(Button.builder(this.nameModeText(), b -> {
            this.nameMode = nextNameMode(this.nameMode);
            b.setMessage(this.nameModeText());
            this.sendCommand("taczbotai team namemode " + this.nameMode + " 64");
        }).bounds(left, row, 300, 20).build());
        row += step + 4;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.equip"), b ->
                        this.sendCommand("taczbotai equip"))
                .bounds(left, row, 146, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.spawn"), b ->
                        this.sendCommand("taczbotai spawn 1"))
                .bounds(left + 154, row, 146, 20).build());
    }

    private Component pathModeText() {
        return Component.translatable("taczbotai.gui.path_mode", Component.translatable(pathModeKey(this.pathMode)));
    }

    private Component nameModeText() {
        return Component.translatable("taczbotai.gui.name_mode", Component.translatable(nameModeKey(this.nameMode)));
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

    // ------------------------------------------------------------------
    // 结算页（对局结束处理）
    // ------------------------------------------------------------------

    private void buildMatchPage() {
        int left = this.width / 2 - 150;
        int row = 52 - this.scroll;
        int step = 24;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.match_teleport",
                onOff(this.matchTeleport)), b -> {
            this.matchTeleport = !this.matchTeleport;
            b.setMessage(Component.translatable("taczbotai.gui.match_teleport", onOff(this.matchTeleport)));
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.endXBox = this.addLabeledText(left, row, "taczbotai.gui.end_x", String.format(Locale.ROOT, "%.1f", this.endX), 60);
        row += step;
        this.endYBox = this.addLabeledText(left, row, "taczbotai.gui.end_y", String.format(Locale.ROOT, "%.1f", this.endY), 60);
        row += step;
        this.endZBox = this.addLabeledText(left, row, "taczbotai.gui.end_z", String.format(Locale.ROOT, "%.1f", this.endZ), 60);
        row += step + 4;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.match_leave_team",
                onOff(this.matchLeaveTeam)), b -> {
            this.matchLeaveTeam = !this.matchLeaveTeam;
            b.setMessage(Component.translatable("taczbotai.gui.match_leave_team", onOff(this.matchLeaveTeam)));
        }).bounds(left, row, 300, 20).build());
        row += step;

        this.addRenderableWidget(Button.builder(Component.translatable("taczbotai.gui.match_clear_inv",
                onOff(this.matchClearInv)), b -> {
            this.matchClearInv = !this.matchClearInv;
            b.setMessage(Component.translatable("taczbotai.gui.match_clear_inv", onOff(this.matchClearInv)));
        }).bounds(left, row, 300, 20).build());
    }

    private EditBox addLabeledText(int x, int y, String labelKey, String value, int boxWidth) {
        String text = Component.translatable(labelKey).getString();
        this.addRenderableWidget(new StringWidget(x, y + 4, 70, 12, Component.literal(text), this.font));
        EditBox box = new EditBox(this.font, x + 72, y, boxWidth, 16, Component.literal(text));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        this.addRenderableWidget(box);
        return box;
    }

    // ------------------------------------------------------------------
    // 文本 / 保存 / 指令
    // ------------------------------------------------------------------

    private Component tierText() {
        return Component.translatable("taczbotai.gui.tier", this.tier);
    }

    private Component friendlyFireText() {
        return Component.translatable("taczbotai.gui.friendly_fire", onOff(this.friendlyFire));
    }

    private Component instantKillText() {
        return Component.translatable("taczbotai.gui.instant_kill", onOff(this.instantKill));
    }

    private Component killMessageText() {
        return Component.translatable("taczbotai.gui.kill_message", onOff(this.killMessage));
    }

    private Component hostileText() {
        return Component.translatable("taczbotai.gui.attack_hostile", onOff(this.hostileMobs));
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "taczbotai.gui.on" : "taczbotai.gui.off");
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * 保存：配置现在属于 SERVER 类型（客户端只有只读副本），所以不能本地写，
     * 统一发给服务端执行 `/taczbotai config set <key> <value>`。
     * 该指令要求权限等级 2 —— 也就是只有房主 / 服务器管理员能改，普通成员会被拒绝。
     */
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
        sendConfig("team.defaultTeam", this.teamName == null ? "" : this.teamName);
        sendConfig("bot.attachments", this.attachments == null ? "" : this.attachments);
        sendConfig("path.pathMode", this.pathMode == null ? "loop" : this.pathMode);
        sendConfig("bot.nameTagMode", this.nameMode == null ? "team" : this.nameMode);
        sendConfig("bot.names", this.names == null ? "" : this.names);
        sendConfig("match.endTeleport", String.valueOf(this.matchTeleport));
        sendConfig("match.endX", format(this.endX));
        sendConfig("match.endY", format(this.endY));
        sendConfig("match.endZ", format(this.endZ));
        sendConfig("match.endLeaveTeam", String.valueOf(this.matchLeaveTeam));
        sendConfig("match.endClearInventory", String.valueOf(this.matchClearInv));
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

    /**
     * 读取主手 TACZ 枪械 NBT：
     *  - 客户端拿 SNBT 复制到剪贴板（方便外部使用）；
     *  - 同时发 `/taczbotai gun`，由服务端把整段 NBT 写进配置（客户端无权写 SERVER 配置）。
     */
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
        // 交给服务端写入配置（需要房主/管理员权限）
        minecraft.player.connection.sendCommand("taczbotai gun");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, VALUE_COLOR);

        if (this.page == Page.VALUES) {
            graphics.drawString(this.font, Component.translatable("taczbotai.gui.section.range"),
                    this.width / 2 - 150, 40, LABEL_COLOR, false);
            graphics.drawString(this.font, Component.translatable("taczbotai.gui.section.reload"),
                    this.width / 2 + 10, 40, LABEL_COLOR, false);
        } else if (this.page == Page.MISC) {
            graphics.drawString(this.font, Component.translatable("taczbotai.gui.section.equip"),
                    this.width / 2 - 150, 40, LABEL_COLOR, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderScrollbar(graphics);
    }

    /** 右侧滚动条（内容超出时显示，纯指示用，拖动不处理）。 */
    private void renderScrollbar(GuiGraphics graphics) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int trackTop = 44;
        int trackBottom = this.height - 36;
        int trackHeight = trackBottom - trackTop;
        int x = this.width - 8;
        graphics.fill(x, trackTop, x + 4, trackBottom, 0x40FFFFFF);
        int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
        int thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * ((double) this.scroll / max));
        graphics.fill(x, thumbTop, x + 4, thumbTop + thumbHeight, 0xC0FFFFFF);
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
