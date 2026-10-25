package com.taczbotai.client;

import com.taczbotai.TaczBotAi;
import com.taczbotai.entity.TaczBotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;

/**
 * 史蒂夫骨架渲染器。
 *
 * 用原版 {@link PlayerModel}（经典 64x64 皮肤 UV）作为骨架，因此：
 *  - 皮肤直接用标准 Minecraft 皮肤图，资源包可覆盖 jar 内贴图；
 *  - TACZ 的 HumanoidModelMixin 会自动给这条骨架套上持枪/瞄准手臂姿势；
 *  - ItemInHandLayer 会走 TACZ 注册的物品渲染器画出枪械模型与枪口火光。
 *
 * shouldShowName 被覆盖：原版在实体属于「NameTagVisibility = ALWAYS」的计分板队伍时
 * 会强制显示名字标签，忽略 setCustomNameVisible(false)。这里改为按同步过来的模式判断：
 *  0 = 总是显示，1 = 仅同队可见，2 = 完全隐藏。
 */
public class TaczBotRenderer extends LivingEntityRenderer<TaczBotEntity, PlayerModel<TaczBotEntity>> {
    public TaczBotRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    protected boolean shouldShowName(TaczBotEntity entity) {
        int mode = entity.getNameTagMode();
        if (mode == TaczBotEntity.NAME_MODE_HIDDEN) {
            return false;
        }
        if (mode == TaczBotEntity.NAME_MODE_TEAM) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return false;
            }
            Team myTeam = player.getTeam();
            String myTeamName = myTeam == null ? "" : myTeam.getName();
            String botTeamName = entity.getSyncedTeamName();
            if (myTeamName.isEmpty() || botTeamName.isEmpty()
                    || !myTeamName.equalsIgnoreCase(botTeamName)) {
                return false;
            }
        }
        return super.shouldShowName(entity);
    }

    @Override
    public ResourceLocation getTextureLocation(TaczBotEntity entity) {
        int skin = entity.getSkin();
        if (skin <= 0) {
            return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
        }
        ResourceLocation candidate = new ResourceLocation(TaczBotAi.MODID, "textures/entity/tba_" + skin + ".png");
        // 资源包优先、jar 内兜底；两者都缺失时回退原版史蒂夫。
        if (Minecraft.getInstance().getResourceManager().getResource(candidate).isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
        }
        return candidate;
    }
}
