package com.phasetranscrystal.fpsmatch.core.item;

import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface IThrowEntityAble {

    default void shoot(Player pPlayer, Level pLevel, InteractionHand pHand, float velocity, float inaccuracy){
            ItemStack itemstack = pPlayer.getItemInHand(pHand);
            if(pPlayer.getCooldowns().isOnCooldown((Item) this)){
                return;
            }
            pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), this.getThrowVoice(), SoundSource.PLAYERS, 0.5F, 1);
            pPlayer.getCooldowns().addCooldown((Item) this, 20);
            if (!pLevel.isClientSide) {
                BaseProjectileEntity shell = this.getEntity(pPlayer, pLevel);
                shell.setItem(itemstack);
                shell.shootFromRotation(pPlayer, pPlayer.getXRot(), pPlayer.getYRot(), 0.0F, velocity, inaccuracy);
                Vec3 playerVelocity = pPlayer.getDeltaMovement();
                Vec3 adjustedMovement = new Vec3(
                        playerVelocity.x,
                        Math.max(playerVelocity.y, 0),
                        playerVelocity.z
                );
                shell.setDeltaMovement(shell.getDeltaMovement().add(adjustedMovement));
                pLevel.addFreshEntity(shell);
            }

            pPlayer.awardStat(Stats.ITEM_USED.get((Item) this));
            if (!pPlayer.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
    }
     BaseProjectileEntity getEntity(Player pPlayer, Level pLevel);

     default SoundEvent getThrowVoice(){
         return SoundEvents.SNOWBALL_THROW;
     }
}
