package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.utils.MvpMusicUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 客户端专用的MVP音乐处理类
 * 负责异步加载和播放MVP音乐
 */
@OnlyIn(Dist.CLIENT) // 确保此类只在客户端加载
public class MVPMusicHandler {
    // 创建单线程线程池用于异步操作
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    //private static final int MVP_MUSIC_TIMEOUT = 8000; // 15秒后自动停止
    
    /**
     * 异步加载并播放MVP音乐
     * 整个过程分三步:
     * 1. 获取音乐URL
     * 2. 下载/获取缓存的音乐文件
     * 3. 播放音乐
     * 
     * @param playerName MVP玩家名称
     */
    public static void handleMvpMusic(String playerName) {
        //FPSMatch.LOGGER.info("客户端：开始异步加载MVP音乐，玩家: {}", playerName);
        
        CompletableFuture.supplyAsync(() -> {
            // 在后台线程获取音乐URL
            return MvpMusicUtils.getMvpMusicUrl(playerName);
        }, executor).thenApplyAsync(musicUrl -> {
            // 在后台线程下载音乐文件
            if (musicUrl != null) {
                return MvpMusicUtils.downloadAndCacheMusic(musicUrl);
            }
            return null;
        }, executor).thenAcceptAsync(musicFile -> {
            // 在主线程播放音乐
            if (musicFile != null && musicFile.exists()) {
                //FPSMatch.LOGGER.info("客户端：MVP音乐加载完成，准备播放");
                FPSClientMusicManager.playLocalFile(musicFile);
                
                /*
                // 添加超时停止
                CompletableFuture.delayedExecutor(MVP_MUSIC_TIMEOUT, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        FPSClientMusicManager.stop();
                    }); */
            } else {
                FPSMatch.LOGGER.warn("客户端：无法加载MVP音乐");
            }
        });
    }

    /**
     * 关闭线程池，清理资源
     * 在游戏关闭时调用
     */
    public static void shutdown() {
        executor.shutdown();
    }
} 