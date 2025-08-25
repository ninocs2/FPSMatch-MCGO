package com.phasetranscrystal.fpsmatch.common.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.music.OnlineMusic;
import com.phasetranscrystal.fpsmatch.mcgo.api.queryUserXtnInfoApi;
import com.phasetranscrystal.fpsmatch.mcgo.service.FileValidationService;
import com.phasetranscrystal.fpsmatch.mcgo.util.HashUtils;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 客户端音乐管理器，用于播放和停止音乐。
 * <p>
 * 该类通过 Minecraft 的音频系统播放音乐，并支持事件广播，允许其他模块在播放和停止音乐时进行干预。
 * 提供了播放音乐和停止当前音乐的功能。
 */
public class FPSClientMusicManager {
    static Minecraft mc = Minecraft.getInstance();
    private static final float FADE_DURATION_SECONDS = 1f; // 淡入淡出时长，单位秒
    private static Thread playThread;
    private static volatile boolean playing;
    
    // queryUserXtnInfoApi实例，用于获取玩家数据
    private static final queryUserXtnInfoApi playerDataApi = new queryUserXtnInfoApi();

    /**
     * 播放指定的音乐资源。
     * @param musicResource 音乐资源的 ResourceLocation
     */
    public static void play(ResourceLocation musicResource) {
        SoundManager soundManager = mc.getSoundManager();
        if (musicResource != null) {
            stop();
            SimpleSoundInstance instance = new SimpleSoundInstance(musicResource, SoundSource.VOICE, 1.0F, 1.0F, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, 0.0D, 0.0D, 0.0D, true);
            soundManager.play(instance);
            playing = true;
        } else {
            FPSMatch.LOGGER.error("failed to play music: music is null");
        }
    }

    /**
     * 播放指定的音乐事件。
     * <p>
     * 该方法会调用 {@link #play(ResourceLocation)}，并传入音乐事件的资源路径。
     *
     * @param musicResource 音乐事件
     */
    public static void play(SoundEvent musicResource) {
        play(musicResource.getLocation());
    }

    /**
     * 开始播放音频流
     * @param music 音频
     */
    public static void play(OnlineMusic music) {
        stop();

        playThread = new Thread(() -> {
            InputStream stream = music.stream();
            if(stream == null) return;
            try (BufferedInputStream bis = new BufferedInputStream(stream);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {

                AudioFormat format = ais.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(format);
                    float mcVolume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
                    float safeVolume = (mcVolume <= 0.0f) ? 0.0001f : mcVolume;

                    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = 20f * (float) Math.log10(safeVolume);
                        volumeControl.setValue(dB);
                    }
                    float sampleRate = format.getSampleRate();
                    int fadeInFrames  = (int)(FADE_DURATION_SECONDS * sampleRate);
                    int fadeOutFrames = (int)(FADE_DURATION_SECONDS * sampleRate);

                    long totalFrames = ais.getFrameLength();

                    int frameSize = format.getFrameSize();

                    line.start();
                    playing = true;

                    byte[] buffer = new byte[4096];
                    long currentFrame = 0;

                    int bytesRead;
                    while (playing && (bytesRead = ais.read(buffer, 0, buffer.length)) != -1) {
                        int framesInChunk = bytesRead / frameSize;
                        for (int f = 0; f < framesInChunk; f++) {
                            long frameIndex = currentFrame + f;
                            float fadeFactor = 1.0f;

                            if (totalFrames > 0) {
                                long fadeOutStart = totalFrames - fadeOutFrames;
                                // 淡入
                                if (frameIndex < fadeInFrames) {
                                    fadeFactor = frameIndex / (float) fadeInFrames; // 0 ~ 1 线性上升
                                }
                                else if (frameIndex > fadeOutStart) {
                                    long framesIntoFadeOut = frameIndex - fadeOutStart;
                                    float fraction = framesIntoFadeOut / (float) fadeOutFrames; // 0 ~ 1
                                    fadeFactor = 1.0f - fraction; // 1 ~ 0 线性下降
                                }
                            } else {
                                if (frameIndex < fadeInFrames) {
                                    fadeFactor = frameIndex / (float) fadeInFrames;
                                }
                            }
                            int frameOffsetBytes = f * frameSize;
                            for (int chByteOffset = 0; chByteOffset < frameSize; chByteOffset += 2) {
                                int sampleIndex = frameOffsetBytes + chByteOffset;
                                short sample = (short) ((buffer[sampleIndex + 1] << 8) | (buffer[sampleIndex] & 0xFF));
                                float processed = sample * fadeFactor;
                                short newSample = (short) Math.max(Short.MIN_VALUE,
                                        Math.min(Short.MAX_VALUE, (int) processed));
                                buffer[sampleIndex]     = (byte) (newSample & 0xFF);
                                buffer[sampleIndex + 1] = (byte) ((newSample >> 8) & 0xFF);
                            }
                        }
                        currentFrame += framesInChunk;
                        line.write(buffer, 0, bytesRead);
                    }

                    line.drain();
                    line.stop();
                } catch (LineUnavailableException e) {
                    e.fillInStackTrace();
                }

            } catch (UnsupportedAudioFileException | IOException e) {
                e.fillInStackTrace();
            }
        }, "AudioPlayer-Thread");

        playThread.start();
    }

    /**
     * 根据MVP玩家名称播放音乐
     * @param mvpPlayerName MVP玩家名称
     */
    public static void playMvpMusic(String mvpPlayerName) {
        try {
            // 从玩家数据API获取MVP音乐信息
            queryUserXtnInfoApi.XtnInfo mvpInfo = playerDataApi.getPlayerMvpInfo(mvpPlayerName);
            
            if (mvpInfo != null && mvpInfo.getMvpMusicUrl() != null && !mvpInfo.getMvpMusicUrl().trim().isEmpty()) {
                String musicUrl = mvpInfo.getMvpMusicUrl();
                String musicName = mvpInfo.getMvpMusicNm();
                
                FPSMatch.LOGGER.info("开始播放玩家 {} 的MVP音乐: {} ({})", mvpPlayerName, musicName, musicUrl);
                
                // 使用FileValidationService验证并获取本地文件
                FileValidationService validationService = FileValidationService.getInstance();
                FileValidationService.FileValidationResult result = validationService.validateMvpMusic(musicUrl);
                
                if (result.isValid()) {
                    // 文件验证成功，直接播放
                    playMvpMusicFromFile(result.getFile(), mvpPlayerName);
                    FPSMatch.LOGGER.info("成功播放玩家 {} 的MVP音乐: {}", mvpPlayerName, result.getFile().getAbsolutePath());
                } else {
                    FPSMatch.LOGGER.warn("MVP音乐文件验证失败: {} - {}", result.getStatus().getDescription(), result.getMessage());
                    if (result.getFile() != null) {
                        FPSMatch.LOGGER.warn("文件路径: {}", result.getFile().getAbsolutePath());
                    }
                    playDefaultMvpMusic();
                }
            } else {
                FPSMatch.LOGGER.info("玩家 {} 没有自定义MVP音乐，播放默认音乐", mvpPlayerName);
                playDefaultMvpMusic();
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("播放玩家 {} 的MVP音乐时发生异常", mvpPlayerName, e);
            playDefaultMvpMusic();
        }
    }
    
    /**
     * 播放默认MVP音乐
     */
    private static void playDefaultMvpMusic() {
        try {
            ResourceLocation defaultMusic = new ResourceLocation("fpsmatch:empty");
            play(defaultMusic);
            FPSMatch.LOGGER.info("播放默认MVP音乐");
        } catch (Exception e) {
            FPSMatch.LOGGER.error("播放默认MVP音乐时发生异常", e);
        }
    }
    
    /**
     * 从本地文件播放MVP音乐
     * @param musicFile 音乐文件
     * @param mvpPlayerName MVP玩家名称（用于重新请求）
     */
    private static void playMvpMusicFromFile(java.io.File musicFile, String mvpPlayerName) {
        // 验证文件SHA与文件名SHA是否一致
        String fileName = musicFile.getName();
        String fileNameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        
        // 计算文件的实际SHA256
        String actualSha = HashUtils.calculateFileSha256(musicFile);
        if (actualSha == null) {
            FPSMatch.LOGGER.error("无法计算文件SHA256: {}", musicFile.getAbsolutePath());
            playDefaultMvpMusic();
            return;
        }
        
        // 对比文件名SHA与实际文件SHA
        if (!HashUtils.isSameHash(fileNameWithoutExt, actualSha)) {
            FPSMatch.LOGGER.warn("文件SHA验证失败: 文件名SHA={}, 实际SHA={}, 文件={}", 
                fileNameWithoutExt, actualSha, musicFile.getAbsolutePath());
            
            // SHA验证失败，重新请求API获取玩家信息
            FPSMatch.LOGGER.info("SHA验证失败，重新请求玩家 {} 的信息", mvpPlayerName);
            try {
                // 强制从API重新获取数据
                queryUserXtnInfoApi.XtnInfo refreshedMvpInfo = playerDataApi.queryPlayerInfo(Arrays.asList(mvpPlayerName))
                    .map(response -> response.getPlayerInfo(mvpPlayerName))
                    .map(playerInfo -> playerInfo.getXtnInfo())
                    .orElse(null);
                
                if (refreshedMvpInfo != null && refreshedMvpInfo.getMvpMusicUrl() != null) {
                    String refreshedMusicUrl = refreshedMvpInfo.getMvpMusicUrl();
                    
                    // 验证刷新后的音乐文件
                    FileValidationService validationService = FileValidationService.getInstance();
                    FileValidationService.FileValidationResult refreshedResult = validationService.validateMvpMusic(refreshedMusicUrl);
                    
                    if (refreshedResult.isValid()) {
                         FPSMatch.LOGGER.info("重新请求后验证成功，等待下一次播放请求");
                         return;
                     }
                }
            } catch (Exception e) {
                FPSMatch.LOGGER.error("重新请求玩家信息失败", e);
            }
            
            // 重新请求失败或验证仍然失败，播放默认音乐
            FPSMatch.LOGGER.info("重新请求失败，播放默认MVP音乐");
            playDefaultMvpMusic();
            return;
        }
        
        FPSMatch.LOGGER.info("文件SHA验证成功: {}", musicFile.getAbsolutePath());
        
        stop();

        playThread = new Thread(() -> {
            // 直接从本地文件读取并播放
            try (BufferedInputStream bis = new BufferedInputStream(new java.io.FileInputStream(musicFile));
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {

                AudioFormat format = ais.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(format);
                    float mcVolume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
                    float safeVolume = (mcVolume <= 0.0f) ? 0.0001f : mcVolume;

                    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = 20f * (float) Math.log10(safeVolume);
                        volumeControl.setValue(dB);
                    }
                    float sampleRate = format.getSampleRate();
                    int fadeInFrames  = (int)(FADE_DURATION_SECONDS * sampleRate);
                    int fadeOutFrames = (int)(FADE_DURATION_SECONDS * sampleRate);

                    long totalFrames = ais.getFrameLength();

                    int frameSize = format.getFrameSize();

                    line.start();
                    playing = true;

                    byte[] buffer = new byte[4096];
                    long currentFrame = 0;

                    int bytesRead;
                    while (playing && (bytesRead = ais.read(buffer, 0, buffer.length)) != -1) {
                        int framesInChunk = bytesRead / frameSize;
                        for (int f = 0; f < framesInChunk; f++) {
                            long frameIndex = currentFrame + f;
                            float fadeFactor = 1.0f;

                            if (totalFrames > 0) {
                                long fadeOutStart = totalFrames - fadeOutFrames;
                                // 淡入
                                if (frameIndex < fadeInFrames) {
                                    fadeFactor = frameIndex / (float) fadeInFrames; // 0 ~ 1 线性上升
                                }
                                else if (frameIndex > fadeOutStart) {
                                    long framesIntoFadeOut = frameIndex - fadeOutStart;
                                    float fraction = framesIntoFadeOut / (float) fadeOutFrames; // 0 ~ 1
                                    fadeFactor = 1.0f - fraction; // 1 ~ 0 线性下降
                                }
                            } else {
                                if (frameIndex < fadeInFrames) {
                                    fadeFactor = frameIndex / (float) fadeInFrames;
                                }
                            }
                            int frameOffsetBytes = f * frameSize;
                            for (int chByteOffset = 0; chByteOffset < frameSize; chByteOffset += 2) {
                                int sampleIndex = frameOffsetBytes + chByteOffset;
                                short sample = (short) ((buffer[sampleIndex + 1] << 8) | (buffer[sampleIndex] & 0xFF));
                                float processed = sample * fadeFactor;
                                short newSample = (short) Math.max(Short.MIN_VALUE,
                                        Math.min(Short.MAX_VALUE, (int) processed));
                                buffer[sampleIndex]     = (byte) (newSample & 0xFF);
                                buffer[sampleIndex + 1] = (byte) ((newSample >> 8) & 0xFF);
                            }
                        }
                        currentFrame += framesInChunk;
                        line.write(buffer, 0, bytesRead);
                    }

                    line.drain();
                    line.stop();
                } catch (LineUnavailableException e) {
                    FPSMatch.LOGGER.error("音频线路不可用", e);
                }

            } catch (UnsupportedAudioFileException | IOException e) {
                FPSMatch.LOGGER.error("播放MVP音乐时发生异常", e);
            }
        }, "MVP-AudioPlayer-Thread");

        playThread.start();
    }
    
    /**
     * 停止当前播放的音乐。
     */
    public static void stop() {
        if (playing) {
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().stop();
        }

        playing = false;
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
        }
        playThread = null;
    }

}