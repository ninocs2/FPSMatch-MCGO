package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * MVP音乐播放S2C数据包
 * 发送MVP玩家名称到客户端，让客户端查询本地数据并播放音乐
 */
public class FPSMvpMusicPlayS2CPacket {
    private final String mvpPlayerName;
    
    public FPSMvpMusicPlayS2CPacket(String mvpPlayerName) {
        this.mvpPlayerName = mvpPlayerName;
    }
    
    public static void encode(FPSMvpMusicPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.mvpPlayerName);
    }

    public static FPSMvpMusicPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMvpMusicPlayS2CPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 停止当前播放的音乐
            FPSClientMusicManager.stop();
            // 根据MVP玩家名称播放音乐
            FPSClientMusicManager.playMvpMusic(mvpPlayerName);
        });
        ctx.get().setPacketHandled(true);
    }
    
    public String getMvpPlayerName() {
        return mvpPlayerName;
    }
}