package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.sound.sampled.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MVP音乐管理器
 * 负责处理MVP音乐的播放、停止等功能
 */
@OnlyIn(Dist.CLIENT)
public class FPSClientMusicManager {
    /** Minecraft 客户端实例 */
    static Minecraft mc = Minecraft.getInstance();
    /** 当前正在播放的音乐实例 */
    private static SoundInstance currentMusic;
    private static Clip currentClip;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** MVP音乐音量 */
    private static double mvpVolume = 1.0D;

    /**
     * 设置MVP音乐音量
     * @param volume 音量值 (0.0-1.0)
     */
    public static void setMvpVolume(double volume) {
        mvpVolume = volume;
        // 如果当前正在播放，更新音量
        if (currentClip != null) {
            try {
                FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                if (gainControl != null) {
                    // 将线性音量转换为分贝值
                    float dB = (float) (Math.log10(Math.max(0.0001, volume)) * 20.0);
                    gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                }
            } catch (Exception e) {
                FPSMatch.LOGGER.error("设置MVP音乐音量时发生错误", e);
            }
        }
    }

    /**
     * 播放本地音乐文件
     * @param file 要播放的音乐文件
     */
    public static void playLocalFile(File file) {
        //FPSMatch.LOGGER.info("准备播放MVP音乐: {}", file.getAbsolutePath());
        stop();

        AudioInputStream audioStream = null;
        try {
            // 直接使用 Java Sound API 播放音频文件
            audioStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                // 如果不支持原始格式，尝试转换
                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        format.getSampleRate(),
                        16,
                        format.getChannels(),
                        format.getChannels() * 2,
                        format.getSampleRate(),
                        false
                );
                audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            }

            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);

            // 设置初始音量
            setMvpVolume(mvpVolume);

            // 使用 final 变量在 lambda 中引用
            final AudioInputStream finalAudioStream = audioStream;

            // 添加播放完成监听器
            currentClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    currentClip.close();
                    try {
                        finalAudioStream.close();
                    } catch (Exception e) {
                        FPSMatch.LOGGER.error("关闭音频流时发生错误", e);
                    }
                }
            });

            // 开始播放
            currentClip.start();
            FPSMatch.LOGGER.info("MVP音乐开始播放");

        } catch (Exception e) {
            FPSMatch.LOGGER.error("播放MVP音乐时发生错误: {}", e.getMessage());
            e.printStackTrace();
            // 如果出错，确保关闭音频流
            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (Exception closeError) {
                    FPSMatch.LOGGER.error("关闭音频流时发生错误", closeError);
                }
            }
        }
    }

    /**
     * 播放指定资源位置的音乐
     * @param musicResource 音乐资源位置
     */
    public static void play(ResourceLocation musicResource) {
        SoundManager soundManager = mc.getSoundManager();
        stop();
        if (musicResource != null) {
            // 创建并配置音乐实例
            SimpleSoundInstance instance = new SimpleSoundInstance(
                    musicResource,
                    SoundSource.MUSIC,
                    1.0F,
                    1.0F,
                    SoundInstance.createUnseededRandom(),
                    false,
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0D, 0.0D, 0.0D,
                    true
            );
            soundManager.play(instance);
            currentMusic = instance;
        } else {
            FPSMatch.LOGGER.error("无法播放音乐：资源为空");
        }
    }

    /**
     * 播放指定音乐事件
     * @param musicResource 音乐事件
     */
    public static void play(SoundEvent musicResource) {
        if (musicResource != null) {
            play(musicResource.getLocation());
        } else {
            FPSMatch.LOGGER.error("无法播放音乐：音乐事件为空");
        }
    }

    /**
     * 停止当前正在播放的所有音乐
     * 使用淡出效果平滑过渡
     */
    public static void stop() {
        Clip clipToStop = currentClip; // 创建一个本地引用
        if (clipToStop != null) {
            // 先将 currentClip 设为 null，防止其他线程访问
            currentClip = null;

            // 异步处理音乐停止，避免主线程卡顿
            CompletableFuture.runAsync(() -> {
                try {
                    clipToStop.stop();
                    clipToStop.close();
                } catch (Exception e) {
                    FPSMatch.LOGGER.error("停止音乐时发生错误", e);
                }
            }, executor);
        }

        // 停止Minecraft原生音乐
        if (currentMusic != null) {
            mc.getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
        mc.getMusicManager().stopPlaying();
    }

    /**
     * 获取当前正在播放的音乐实例
     * @return 当前音乐实例，如果没有则返回null
     */
    public static SoundInstance getCurrentMusic() {
        return currentMusic;
    }
}