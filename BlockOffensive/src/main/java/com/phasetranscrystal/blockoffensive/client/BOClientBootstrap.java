package com.phasetranscrystal.blockoffensive.client;

import com.phasetranscrystal.blockoffensive.client.renderer.C4Renderer;
import com.phasetranscrystal.blockoffensive.client.screen.hud.*;
import com.phasetranscrystal.blockoffensive.entity.BOEntityRegister;
import com.phasetranscrystal.fpsmatch.common.client.FPSMGameHudManager;
import com.phasetranscrystal.fpsmatch.common.client.tab.TabManager;
import com.phasetranscrystal.blockoffensive.BlockOffensive;
import com.phasetranscrystal.blockoffensive.client.key.DismantleBombKey;
import com.phasetranscrystal.blockoffensive.client.key.OpenShopKey;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = BlockOffensive.MODID)
public class BOClientBootstrap {
    public static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((playerInfo) -> 0)
            .thenComparing((playerInfo) -> Optionull.mapOrDefault(playerInfo.getTeam(), PlayerTeam::getName, ""))
            .thenComparing((playerInfo) -> playerInfo.getProfile().getName(), String::compareToIgnoreCase);
    
    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.register(OpenShopKey.OPEN_SHOP_KEY);
        event.register(DismantleBombKey.DISMANTLE_BOMB_KEY);
        // cs: hud | overlay | tab
        TabManager.getInstance().registerRenderer(new CSGameTabRenderer());
        FPSMGameHudManager.INSTANCE.registerHud("cs", CSGameHud.getInstance());
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BOEntityRegister.C4.get(), new C4Renderer());
    }

    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }
}
