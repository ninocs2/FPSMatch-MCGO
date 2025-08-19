package com.phasetranscrystal.blockoffensive.mixin;

import com.phasetranscrystal.blockoffensive.item.BOItemRegister;
import com.phasetranscrystal.blockoffensive.item.BombDisposalKit;
import com.phasetranscrystal.blockoffensive.item.CompositionC4;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class C4KitsItemEntityMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(at = @At("HEAD"), method = "playerTouch", cancellable = true)
    public void fpsMatch$playerTouch$CustomC4(Player player, CallbackInfo ci) {
        if(!player.isCreative() && !player.level().isClientSide){
            if(this.getItem().getItem() instanceof CompositionC4){
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map == null) {
                    ci.cancel();
                    return;
                }
                BaseTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                if(team != null && map instanceof BlastModeMap<?> blastModeMap){
                    if(!blastModeMap.checkCanPlacingBombs(team.getFixedName())){
                        ci.cancel();
                    }
                }else{
                    ci.cancel();
                }
            }

            if(this.getItem().getItem() instanceof BombDisposalKit){
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map == null) {
                    ci.cancel();
                    return;
                }
                BaseTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                if(team != null && map instanceof BlastModeMap<?> blastModeMap){
                    if(!blastModeMap.checkCanPlacingBombs(team.getFixedName())){
                        int i = player.getInventory().countItem(BOItemRegister.BOMB_DISPOSAL_KIT.get());
                        if(i > 0){
                            ci.cancel();
                        }
                    }else{
                        ci.cancel();
                    }
                }else{
                    ci.cancel();
                }
            }
        }
    }
}
