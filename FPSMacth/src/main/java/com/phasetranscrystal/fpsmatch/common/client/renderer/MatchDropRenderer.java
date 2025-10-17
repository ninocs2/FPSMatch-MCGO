package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.math.Axis;
import com.phasetranscrystal.fpsmatch.common.entity.drop.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.compat.LrtacticalCompat;
import com.phasetranscrystal.fpsmatch.impl.FPSMImpl;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class MatchDropRenderer implements EntityRendererProvider<MatchDropEntity> {
    public static final Random RANDOM = new Random();
    @Override
    public @NotNull EntityRenderer<MatchDropEntity> create(@NotNull Context context) {
        return new EntityRenderer<>(context) {
            private final ItemRenderer itemRenderer = context.getItemRenderer();
            private final float yRotation = RANDOM.nextFloat(0f, 360f);

            @Override
            public @NotNull ResourceLocation getTextureLocation(@NotNull MatchDropEntity entity) {
                return TextureAtlas.LOCATION_BLOCKS;
            }

            @Override
            public void render(@NotNull MatchDropEntity entity, float pEntityYaw, float pPartialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int pPackedLight) {
                poseStack.pushPose();
                ItemStack itemStack = entity.getItem();
                BakedModel bakedModel = itemRenderer.getModel(itemStack, entity.level(), null, entity.getId());
                boolean isSpecialItem = itemStack.getItem() instanceof IGun || (FPSMImpl.findEquipmentMod() && LrtacticalCompat.isKnife(itemStack));
                poseStack.translate(0, 0.25F, 0);
                if (isSpecialItem) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
                    poseStack.mulPose(Axis.YP.rotationDegrees(90));
                }
                itemRenderer.render(itemStack, ItemDisplayContext.GROUND, false, poseStack, bufferSource, pPackedLight, OverlayTexture.NO_OVERLAY, bakedModel);
                poseStack.popPose();
            }
        };
    }
}