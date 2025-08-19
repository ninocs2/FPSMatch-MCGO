package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.tab.TabManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay{
    @Inject(at = {@At("HEAD")}, method = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V", cancellable = true)
    public void fpsMatch$render$Custom(GuiGraphics guiGraphics, int windowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if(!FPSMGameHudManager.enable || FPSMClient.getGlobalData().equalsGame("none")) {
            return;
        }
        
        List<PlayerInfo> playerInfoList = this.getPlayerInfos();
        if(playerInfoList == null) return;

        TabManager.getInstance().render(guiGraphics, windowWidth, playerInfoList, scoreboard, objective);
        ci.cancel();
    }

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        return null;
    }


}
