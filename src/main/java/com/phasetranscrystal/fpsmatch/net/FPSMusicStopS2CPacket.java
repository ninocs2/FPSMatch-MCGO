package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.music.FPSClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMusicStopS2CPacket {
    // 服务端构造函数
    public FPSMusicStopS2CPacket() {}

    // 网络传输相关方法
    public static void encode(FPSMusicStopS2CPacket packet, FriendlyByteBuf buf) {}

    public static FPSMusicStopS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicStopS2CPacket();
    }

    // 客户端处理方法
    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // 确保在主线程执行
        context.enqueueWork(() -> {
            // 使用 DistExecutor 确保只在客户端执行
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient());
        });
        return true;
    }

    // 使用 @OnlyIn 确保此方法只在客户端存在
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient() {
        FPSClientMusicManager.stop();
    }
}
