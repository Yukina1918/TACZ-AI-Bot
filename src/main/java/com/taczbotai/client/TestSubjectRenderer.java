/*
 * Copyright (c) 2026 Yukina_V
 * SPDX-License-Identifier: MIT
 */
package com.taczbotai.client;

import com.taczbotai.entity.TestSubjectEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** 测试人员：同样使用史蒂夫骨架，贴图是灰底红色靶心。 */
public class TestSubjectRenderer
        extends LivingEntityRenderer<TestSubjectEntity, PlayerModel<TestSubjectEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("taczbotai", "textures/entity/test_subject.png");

    public TestSubjectRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(TestSubjectEntity entity) {
        return TEXTURE;
    }
}
