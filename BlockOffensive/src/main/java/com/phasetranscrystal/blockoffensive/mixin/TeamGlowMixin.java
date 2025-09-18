package com.phasetranscrystal.blockoffensive.mixin;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 队友发光效果控制 Mixin
 * 目标：仅让队友在客户端渲染时具有发光描边，对手不显示发光。
 * 识别逻辑参考 EntityRendererMixin。
 *
 * 实现方案：
 * - 注入到 Entity#isCurrentlyGlowing()，在客户端渲染阶段根据队伍关系强制返回结果。
 * - 非游戏地图（fpsm_none）不干预，保持原行为。
 * - 队友：返回 true（显示发光）；对手：返回 false（不显示发光）。
 * - 非玩家实体不处理，走原有逻辑。
 */
@Mixin(Entity.class)
public abstract class TeamGlowMixin {

    @Unique
    private static final String DISABLED_MAP_NAME = "fpsm_none";

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, remap = true)
    private void blockoffensive$teamGlowControl(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        // 仅客户端侧渲染时生效，且仅处理玩家实体
        if (!self.level().isClientSide() || !(self instanceof Player targetPlayer)) {
            return;
        }

        // 非游戏地图：不干预
        if (DISABLED_MAP_NAME.equals(FPSMClient.getGlobalData().getCurrentMap())) {
            return;
        }

        // 获取当前玩家所属队伍与目标玩家队伍
        String currentTeam = FPSMClient.getGlobalData().getCurrentTeam();
        String targetTeam = FPSMClient.getGlobalData().getPlayerTeam(targetPlayer.getUUID()).orElse(null);

        // 队伍信息无效：不干预
        if (currentTeam == null || targetTeam == null || currentTeam.isEmpty() || targetTeam.isEmpty()) {
            return;
        }

        // 是自己：不干预（保持原逻辑）
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && targetPlayer.getUUID().equals(localPlayer.getUUID())) {
            return;
        }

        // 队友：发光，对手：不显示发光
        if (currentTeam.equals(targetTeam)) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}