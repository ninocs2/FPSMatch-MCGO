package com.phasetranscrystal.fpsmatch.mcgo.music;

import com.phasetranscrystal.fpsmatch.client.music.FPSClientMusicManager;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FPSClientSettings {
    // MVP音乐音量滑块
    public static final OptionInstance<Double> MVP_MUSIC_VOLUME = new OptionInstance<>(
            "options.fps.mvp_music_volume",
            OptionInstance.noTooltip(),
            (component, value) -> {
                // 将 0.25-1.0 的值转换为显示百分比
                double percentage = value * 100.0D;
                return Component.translatable("options.fps.mvp_music_volume")
                        .append(": ")
                        .append(String.format("%.0f%%", percentage));
            },
            // 使用基础的 double 类型，并在值改变时进行范围限制
            OptionInstance.UnitDouble.INSTANCE,
            1.0D, // 默认值
            value -> {
                // 限制最小值为 0.25 (25%)
                double limitedValue = Math.max(0.25D, Math.min(1.0D, value));
                // 当值改变时更新音量
                FPSClientMusicManager.setMvpVolume(limitedValue);
            }
    );
}