package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.screen.hud.CSGameOverlay;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import com.mojang.datafixers.util.Pair;
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
     * 控制玩家名称的显示/隐藏
     * 在非游戏地图中显示所有名称
     * 在游戏地图中隐藏敌方玩家名称
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
        return DISABLED_MAP_NAME.equals(ClientData.currentMap);
    }

    /**
     * 判断目标玩家是否为敌方玩家
     * 
     * @param targetPlayer 目标玩家
     * @return 是否为敌方玩家
     */
    @Unique
    private boolean isOpponentPlayer(Player targetPlayer) {
        String currentTeam = ClientData.getCurrentTeam();
        String targetTeam = ClientData.getTeamByUUID(targetPlayer.getUUID());

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
     * 颜色优先级：
     * 1. 队伍中的位置颜色
     * 2. 默认队伍颜色
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
        String playerTeam = ClientData.getTeamByUUID(player.getUUID());
        if (playerTeam == null) {
            return -1;
        }

        // 1. 尝试根据玩家在队伍中的位置获取颜色
        int playerPositionColor = getPlayerPositionColor(player, playerTeam);
        if (playerPositionColor != -1) {
            return playerPositionColor;
        }

        // 2. 使用默认队伍颜色
        return getDefaultTeamColor(playerTeam);
    }

    /**
     * 根据玩家在队伍中的位置获取颜色
     * 
     * @param player 目标玩家
     * @param team 玩家所在队伍
     * @return 颜色值，-1表示未找到对应位置颜色
     */
    @Unique
    private int getPlayerPositionColor(Player player, String team) {
        UUID playerUUID = player.getUUID();
        List<Pair<UUID, TabData>> teamPlayers = ClientData.getTeamPlayers(team);
        
        // 查找玩家在队伍中的索引
        for (int i = 0; i < teamPlayers.size(); i++) {
            if (teamPlayers.get(i).getFirst().equals(playerUUID)) {
                // 如果索引在边框颜色数组范围内，返回对应颜色
                if (i < CSGameOverlay.BORDER_COLORS.length) {
                    return CSGameOverlay.BORDER_COLORS[i];
                }
                break;
            }
        }
        
        return -1;  // 未找到对应位置颜色
    }

    /**
     * 获取默认队伍颜色
     * 
     * @param team 队伍名称
     * @return 队伍对应的颜色
     */
    @Unique
    private int getDefaultTeamColor(String team) {
        return "ct".equals(team) ? 
            CSGameOverlay.textCTWinnerRoundsColor : 
            CSGameOverlay.textTWinnerRoundsColor;
    }
} 