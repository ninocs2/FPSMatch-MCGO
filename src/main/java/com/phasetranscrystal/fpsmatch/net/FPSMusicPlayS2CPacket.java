package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.music.FPSClientMusicManager;
import com.phasetranscrystal.fpsmatch.utils.MvpMusicUtils;
import com.phasetranscrystal.fpsmatch.client.music.MVPMusicHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.File;
import java.util.function.Supplier;

public class FPSMusicPlayS2CPacket {
    private final String playerName;

    // 服务端构造函数
    public FPSMusicPlayS2CPacket(String playerName) {
        this.playerName = playerName;
    }

    // 网络传输相关方法
    public static void encode(FPSMusicPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerName);
    }

    public static FPSMusicPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicPlayS2CPacket(buf.readUtf());
    }

    // 客户端处理方法
    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 确保在主线程执行
        context.enqueueWork(() -> {
            // 使用 DistExecutor 确保只在客户端执行
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient(playerName));
        });
        return true;
    }

    // 使用 @OnlyIn 确保此方法只在客户端存在
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(String playerName) {
        FPSMatch.LOGGER.info("客户端收到MVP音乐播放请求，MVP玩家: {}", playerName);
        MVPMusicHandler.handleMvpMusic(playerName);
    }
}
