package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.event.PlayerGetMvpEvent;
import com.phasetranscrystal.fpsmatch.net.FPSMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.mcgo.music.FPSClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import java.lang.reflect.Field;

import java.io.File;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, value = Dist.CLIENT)
public class EventHandler {

    static {
        FPSMatch.LOGGER.info("MVP音乐播放 已初始化");
    }

    // 服务端事件处理
    @Mod.EventBusSubscriber(modid = FPSMatch.MODID)
    public static class ServerEvents {
        @SubscribeEvent
        public static void onPlayerGetMvp(PlayerGetMvpEvent event) {
            // 服务端只负责发送网络包通知客户端
            String playerName = event.getPlayer().getName().getString();
            FPSMatch.LOGGER.info("服务端：玩家 {} 获得MVP", playerName);

            // 发送网络包到所有客户端
            FPSMatch.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    new FPSMusicPlayS2CPacket(playerName)
            );
        }
    }

    // 客户端事件处理
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onPlayerGetMvp(PlayerGetMvpEvent event) {
        FPSMatch.LOGGER.info("客户端：收到MVP事件，玩家: {}", event.getPlayer().getName().getString());
        File mvpMusic = event.getMvpMusicFile();
        if (mvpMusic != null && mvpMusic.exists()) {
            FPSMatch.LOGGER.info("找到MVP音乐文件: {}", mvpMusic.getAbsolutePath());
            FPSClientMusicManager.playLocalFile(mvpMusic);
        } else {
            FPSMatch.LOGGER.warn("未找到MVP音乐文件");
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SoundOptionsScreen soundScreen) {
            try {
                // 获取选项列表
                OptionsList list = null;
                for (Field field : SoundOptionsScreen.class.getDeclaredFields()) {
                    if (field.getType() == OptionsList.class) {
                        field.setAccessible(true);
                        list = (OptionsList) field.get(soundScreen);
                        break;
                    }
                }

                if (list != null) {
                    // 在主音量选项后添加MVP音量选项
                    list.addBig(FPSClientSettings.MVP_MUSIC_VOLUME);
                }
            } catch (Exception e) {
                FPSMatch.LOGGER.error("添加MVP音量选项时发生错误", e);
            }
        }
    }
} 