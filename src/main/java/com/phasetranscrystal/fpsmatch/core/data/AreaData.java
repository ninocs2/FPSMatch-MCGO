package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;

/**
 * AreaData 记录类用于定义和管理游戏中的区域
 * 包含两个坐标点来定义一个立方体区域
 */
public record AreaData(@Nonnull BlockPos pos1, @Nonnull BlockPos pos2) {

    /**
     * 检查玩家是否在区域内
     * @param player 要检查的玩家
     * @return 如果玩家在区域内返回true
     */
    public boolean isPlayerInArea(Player player) {
        return isInArea(new Vec3(player.getX(), player.getY(), player.getZ()));
    }

    /**
     * 检查方块坐标是否在区域内
     * @param blockPos 要检查的方块坐标
     * @return 如果方块在区域内返回true
     */
    public boolean isBlockPosInArea(BlockPos blockPos) {
        return isInArea(new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
    }

    /**
     * 检查实体是否在区域内
     * @param entity 要检查的实体
     * @return 如果实体在区域内返回true
     */
    public boolean isEntityInArea(Entity entity) {
        return isInArea(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
    }

    /**
     * 检查一个坐标点是否在区域内
     * @param pos 要检查的坐标点
     * @return 如果坐标点在区域内返回true
     */
    public boolean isInArea(Vec3 pos) {
        AABB area = new AABB(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
        return area.contains(pos);
    }

    /**
     * 获取区域的AABB（轴对齐包围盒）
     * @return 区域的AABB
     */
    public AABB getAABB() {
        return new AABB(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    /**
     * 渲染区域的边界
     * TODO: 完善渲染功能
     * @param multiBufferSource 渲染缓冲
     * @param poseStack 位姿矩阵
     */
    public void renderArea(MultiBufferSource multiBufferSource, PoseStack poseStack) {
        VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.lines());
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        LevelRenderer.renderLineBox(poseStack, vertexconsumer,
                minX, minY, minZ, // 最小角坐标
                maxX, maxY, maxZ, // 最大角坐标
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F
        );
    }
}