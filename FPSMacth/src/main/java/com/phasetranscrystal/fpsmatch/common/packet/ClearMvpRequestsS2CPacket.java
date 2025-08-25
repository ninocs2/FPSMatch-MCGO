package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端发送到客户端的包，用于清理MVP音乐请求记录
 * 在游戏结束或重置时使用
 */
public class ClearMvpRequestsS2CPacket {
    
    public ClearMvpRequestsS2CPacket() {
    }
    
    public static void encode(ClearMvpRequestsS2CPacket packet, FriendlyByteBuf buf) {
        // 无需编码任何数据
    }
    
    public static ClearMvpRequestsS2CPacket decode(FriendlyByteBuf buf) {
        return new ClearMvpRequestsS2CPacket();
    }
    
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // 在客户端清理MVP音乐请求记录
        ctx.get().enqueueWork(GameTabStatsS2CPacket::clearRequestedPlayers);
        ctx.get().setPacketHandled(true);
    }
}