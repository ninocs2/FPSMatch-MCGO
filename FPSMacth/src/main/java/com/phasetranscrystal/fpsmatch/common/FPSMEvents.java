package com.phasetranscrystal.fpsmatch.common;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.event.GameWinnerEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterListenerModuleEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ReturnGoodsModule;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMEvents {
    @SubscribeEvent
    public static void onServerTickEvent(TickEvent.ServerTickEvent event){
        if(event.phase == TickEvent.Phase.END){
            FPSMCore.getInstance().onServerTick();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new FPSMatchStatsResetS2CPacket());
        }
    }

    @SubscribeEvent
    public static void onRegisterListenerModuleEvent(RegisterListenerModuleEvent event){
        event.register(new ReturnGoodsModule());
        ChangeShopItemModule changeShopItemModule = new ChangeShopItemModule(new ItemStack(Items.APPLE), 50, new ItemStack(Items.GOLDEN_APPLE), 300);
        event.register(changeShopItemModule);
    }

    @SubscribeEvent
    public static void onGameWinnerEvent(GameWinnerEvent event) {
        BaseMap map = event.getMap();
        if (map != null) {
            // 获取总回合数（获胜队伍分数 + 失败队伍分数）
            int totalRounds = event.getWinner().getScores() + event.getLoser().getScores();
            // 当前回合数等于总回合数
            int roundCount = totalRounds;
            
            // 调用sendGameResultData发送游戏结果
            map.sendGameResultData(event.getWinner(), event.getLoser(), totalRounds, roundCount);
        }
    }
}
