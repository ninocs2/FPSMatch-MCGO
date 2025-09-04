package com.phasetranscrystal.blockoffensive.sound;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.blockoffensive.BlockOffensive;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMvpMusicPlayS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMusicStopS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@SuppressWarnings("all")
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MVPMusicManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MVPMusicManager.class);
    
    public static final Codec<MVPMusicManager> CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).xmap(MVPMusicManager::new,
            (manager)-> manager.mvpMusicMap);
    private final Map<String, ResourceLocation> mvpMusicMap;
    private static MVPMusicManager INSTANCE = new MVPMusicManager();
    
    // 网络数据包分发器，用于发送数据包到客户端
    // 注意：这里需要根据实际的网络系统进行调整

    public static MVPMusicManager getInstance(){
        return INSTANCE;
    }

    public MVPMusicManager(){
        mvpMusicMap = Maps.newHashMap();
    }

    public MVPMusicManager(Map<String, ResourceLocation> mvpMusicMap){
        this.mvpMusicMap = Maps.newHashMap();
        this.mvpMusicMap.putAll(mvpMusicMap);
    }

    public void addMvpMusic(String uuid, ResourceLocation music){
        mvpMusicMap.put(uuid, music);
    }

    public ResourceLocation getMvpMusic(String uuid){
        return this.mvpMusicMap.getOrDefault(uuid, new ResourceLocation("fpsmatch:empty"));
    }

    public boolean playerHasMvpMusic(String uuid){
        return this.mvpMusicMap.containsKey(uuid);
    }
    
    /**
     * 发送MVP音乐播放指令到客户端
     * @param mvpPlayerName MVP玩家名称
     * @param targetPlayers 目标玩家列表（接收音乐播放指令的玩家）
     */
    public void sendPlayMvpMusicToClients(String mvpPlayerName, ServerPlayer... targetPlayers) {
        try {
            FPSMvpMusicPlayS2CPacket packet = new FPSMvpMusicPlayS2CPacket(mvpPlayerName);
            
            if (targetPlayers.length == 0) {
                // 如果没有指定目标玩家，发送给所有玩家
                LOGGER.info("发送MVP音乐播放指令到所有客户端: {}", mvpPlayerName);
                NetworkPacketRegister.getChannelFromCache(packet.getClass()).send(PacketDistributor.ALL.noArg(), packet);
            } else {
                // 发送给指定的玩家
                for (ServerPlayer player : targetPlayers) {
                    LOGGER.info("发送MVP音乐播放指令到客户端 {}: {}", player.getName().getString(), mvpPlayerName);
                    NetworkPacketRegister.getChannelFromCache(packet.getClass()).send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        } catch (Exception e) {
            LOGGER.error("发送MVP音乐播放指令时发生异常: {}", mvpPlayerName, e);
        }
    }
    
    /**
     * 发送MVP音乐播放指令到所有客户端
     * @param mvpPlayerName MVP玩家名称
     */
    public void sendPlayMvpMusicToAllClients(String mvpPlayerName) {
        sendPlayMvpMusicToClients(mvpPlayerName);
    }
    
    /**
     * 发送停止MVP音乐指令到客户端
     * @param targetPlayers 目标玩家列表（接收停止指令的玩家）
     */
    public void sendStopMvpMusicToClients(ServerPlayer... targetPlayers) {
        try {
            FPSMusicStopS2CPacket packet = new FPSMusicStopS2CPacket();
            
            if (targetPlayers.length == 0) {
                // 如果没有指定目标玩家，发送给所有玩家
                LOGGER.info("发送停止MVP音乐指令到所有客户端");
                NetworkPacketRegister.getChannelFromCache(packet.getClass()).send(PacketDistributor.ALL.noArg(), packet);
            } else {
                // 发送给指定的玩家
                for (ServerPlayer player : targetPlayers) {
                    LOGGER.info("发送停止MVP音乐指令到客户端: {}", player.getName().getString());
                    NetworkPacketRegister.getChannelFromCache(packet.getClass()).send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        } catch (Exception e) {
            LOGGER.error("发送停止MVP音乐指令时发生异常", e);
        }
    }
    
    /**
     * 发送停止MVP音乐指令到所有客户端
     */
    public void sendStopMvpMusicToAllClients() {
        sendStopMvpMusicToClients();
    }

    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event){
        event.registerData(MVPMusicManager.class,"MvpMusicData",
                new SaveHolder.Builder<>(CODEC)
                        .withReadHandler(MVPMusicManager::read)
                        .withWriteHandler(MVPMusicManager::write)
                        .withMergeHandler(MVPMusicManager::merge)
                        .isGlobal(true)
                        .build()
        );
    }

    private void read() {
        INSTANCE = this;
    }

    private void read(MVPMusicManager mvpMusicManager) {
        this.mvpMusicMap.putAll(mvpMusicManager.mvpMusicMap);
    }

    public static void write(FPSMDataManager manager){
        manager.saveData(INSTANCE,"data");
    }

    public static MVPMusicManager merge(@Nullable MVPMusicManager old, MVPMusicManager newer){
        if(old == null){
            return newer;
        }else{
            old.read(newer);
            return old;
        }
    }
}
