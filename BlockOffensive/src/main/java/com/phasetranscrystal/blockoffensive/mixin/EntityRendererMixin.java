package com.phasetranscrystal.blockoffensive.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 实体渲染器的Mixin类
 * 主要功能：
 * 1. 控制玩家名称的显示/隐藏（基于队伍关系）
 * 2. 设置玩家名称颜色与头像描边颜色同步
 * 
 * 实现原理：
 * - 通过Mixin注入到EntityRenderer类的renderNameTag方法
 * - 根据玩家队伍关系控制名称显示/隐藏
 * - 根据玩家在队伍中的位置或队伍类型设置名称颜色
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    
    /** 非游戏地图的标识符 */
    @Unique
    private static final String DISABLED_MAP_NAME = "fpsm_none";
    
    /**
     * 控制玩家名称的显示/隐藏和队友发光效果
     * 在非游戏地图中显示所有名称
     * 在游戏地图中隐藏敌方玩家名称，为队友添加发光效果
     * 
     * @param entity 目标实体
     * @param displayName 显示名称
     * @param matrixStack 渲染矩阵
     * @param buffer 渲染缓冲
     * @param packedLight 光照数据
     * @param ci 回调信息
     */
    @Inject(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "HEAD"),
        cancellable = true,
        remap = true
    )
    private void onRenderNameTag(Entity entity, Component displayName, PoseStack matrixStack,
                               MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        // 只处理玩家实体
        if (!(entity instanceof Player player)) {
            return;
        }

        // 如果应该隐藏名称标签，则取消渲染
        if (shouldHideNameTag(player)) {
            ci.cancel();
        }
    }

    /**
     * 修改玩家名称的显示颜色
     * 根据玩家在队伍中的位置或队伍类型设置颜色
     * 
     * @param originalDisplayName 原始显示名称
     * @param entity 目标实体
     * @return 修改后的显示名称
     */
    @ModifyVariable(
        method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "HEAD"),
        ordinal = 0,
        argsOnly = true,
        remap = true
    )
    private Component modifyDisplayName(Component originalDisplayName, Entity entity) {
        // 只处理玩家实体
        if (!(entity instanceof Player player)) {
            return originalDisplayName;
        }

        // 获取玩家的队伍颜色
        int teamColor = getTeamColor(player);
        if (teamColor != -1) {
            // 创建带颜色的名称组件
            return Component.literal(originalDisplayName.getString())
                .withStyle(style -> style.withColor(teamColor));
        }

        return originalDisplayName;
    }

    /**
     * 判断是否应该隐藏玩家名称标签
     * 综合考虑多个条件：
     * 1. 当前玩家是否有效
     * 2. 是否为自己
     * 3. 是否在非游戏地图中
     * 4. 是否为敌方玩家
     * 
     * @param targetPlayer 目标玩家
     * @return true表示应该隐藏名称
     */
    @Unique
    private boolean shouldHideNameTag(Player targetPlayer) {
        // 检查基本条件
        if (!isCurrentPlayerValid()) {
            return false;  // 当前玩家无效，显示所有名称
        }
        
        if (isCurrentPlayer(targetPlayer)) {
            return false;  // 是自己，显示名称
        }
        
        if (isInNonGameMap()) {
            return false;  // 非游戏地图，显示所有名称
        }
        
        // 在游戏地图中，隐藏敌方玩家名称
        return isOpponentPlayer(targetPlayer);
    }

    /**
     * 检查当前玩家是否有效
     * 
     * @return 当前玩家是否有效
     */
    @Unique
    private boolean isCurrentPlayerValid() {
        return Minecraft.getInstance().player != null;
    }

    /**
     * 检查目标玩家是否为当前玩家自己
     * 
     * @param targetPlayer 目标玩家
     * @return 是否为自己
     */
    @Unique
    private boolean isCurrentPlayer(Player targetPlayer) {
        Player currentPlayer = Minecraft.getInstance().player;
        return currentPlayer != null && 
               targetPlayer.getUUID().equals(currentPlayer.getUUID());
    }

    /**
     * 检查是否在非游戏地图中
     * 
     * @return 是否在非游戏地图中
     */
    @Unique
    private boolean isInNonGameMap() {
        return DISABLED_MAP_NAME.equals(FPSMClient.getGlobalData().getCurrentMap());
    }

    /**
     * 判断目标玩家是否为敌方玩家
     * 
     * @param targetPlayer 目标玩家
     * @return 是否为敌方玩家
     */
    @Unique
    private boolean isOpponentPlayer(Player targetPlayer) {
        String currentTeam = FPSMClient.getGlobalData().getCurrentTeam();
        String targetTeam = FPSMClient.getGlobalData().getPlayerTeam(targetPlayer.getUUID()).orElse(null);

        // 如果任一队伍信息无效，默认显示名称
        if (currentTeam == null || targetTeam == null || 
            currentTeam.isEmpty() || targetTeam.isEmpty()) {
            return false;
        }

        // 不同队伍则隐藏名称
        return !currentTeam.equals(targetTeam);
    }

    /**
     * 获取玩家的队伍颜色
     * 使用与头像背景色相同的颜色分配系统
     * 
     * @param player 目标玩家
     * @return 颜色值，-1表示使用默认颜色
     */
    @Unique
    private int getTeamColor(Player player) {
        // 非游戏地图使用默认颜色
        if (isInNonGameMap()) {
            return -1;
        }

        // 获取玩家队伍
        String playerTeam = FPSMClient.getGlobalData().getPlayerTeam(player.getUUID()).orElse(null);
        if (playerTeam == null) {
            return -1;
        }

        // 使用与头像背景色相同的颜色分配逻辑
        return getPlayerAvatarBackgroundColor(player.getUUID());
    }

    /** 头像背景颜色数组，与CSGameOverlay中的BG_COLORS保持一致 */
    @Unique
    private static final int[] BG_COLORS = new int[]{
            RenderUtil.color(216,130,44), // 橙色
            RenderUtil.color(238,228,75), // 黄色
            RenderUtil.color(66,185,131), // 紫色
            RenderUtil.color(7,156,130), // 绿色
            RenderUtil.color(145,203,234)  // 蓝色
    };

    /** 玩家颜色索引映射 */
    @Unique
    private static final java.util.Map<UUID, Integer> playerColorIndex = new java.util.HashMap<>();
    
    /** 下一个颜色索引 */
    @Unique
    private static int nextColorIndex = 0;

    /**
     * 获取玩家头像背景颜色
     * 使用与CSGameOverlay相同的颜色分配逻辑
     * 
     * @param uuid 玩家UUID
     * @return 颜色值
     */
    @Unique
    private int getPlayerAvatarBackgroundColor(UUID uuid) {
        // 获取玩家的颜色索引
        int colorIndex = getColorIndexForPlayer(uuid);
        return BG_COLORS[colorIndex];
    }

    /**
     * 为玩家分配颜色索引
     * 与CSGameOverlay中的getColorIndexForPlayer方法逻辑一致
     * 
     * @param uuid 玩家UUID
     * @return 颜色索引
     */
    @Unique
    private int getColorIndexForPlayer(UUID uuid) {
        if (!playerColorIndex.containsKey(uuid)) {
            playerColorIndex.put(uuid, nextColorIndex);
            nextColorIndex = (nextColorIndex + 1) % BG_COLORS.length;
        }
        return playerColorIndex.get(uuid);
    }

}