package com.phasetranscrystal.blockoffensive.client.service;

import com.mojang.blaze3d.platform.NativeImage;
import com.phasetranscrystal.fpsmatch.mcgo.api.queryUserXtnInfoApi;
import com.phasetranscrystal.fpsmatch.mcgo.storage.MCGOStorageManager;
import com.phasetranscrystal.fpsmatch.mcgo.util.HashUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家头像服务类
 * 负责头像的获取、缓存管理、SHA256验证和渲染
 */
public class PlayerAvatarService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAvatarService.class);
    private static final PlayerAvatarService INSTANCE = new PlayerAvatarService();
    
    // 头像缓存：玩家名称 -> ResourceLocation
    private final Map<String, ResourceLocation> avatarCache = new ConcurrentHashMap<>();
    
    // 下载状态缓存：玩家名称 -> 是否正在下载
    private final Map<String, Boolean> downloadingStatus = new ConcurrentHashMap<>();
    
    // 默认头像URL（来自queryUserXtnInfoApi）
    private static final String DEFAULT_AVATAR_URL = "https://minio.mcgo.xin/pictures/default/21de85f762be90c962c560d5b9e533cde1fdffac2d616accf6b22d14274c978a.jpg";
    
    // queryUserXtnInfoApi实例
    private final queryUserXtnInfoApi apiService;
    
    private PlayerAvatarService() {
        this.apiService = new queryUserXtnInfoApi();
    }
    
    public static PlayerAvatarService getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取玩家头像ResourceLocation
     * @param playerName 玩家名称
     * @return 头像ResourceLocation，如果没有则返回null（使用原生皮肤）
     */
    public ResourceLocation getPlayerAvatar(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        
        // 检查内存缓存
        ResourceLocation cachedAvatar = avatarCache.get(playerName);
        if (cachedAvatar != null) {
            return cachedAvatar;
        }
        
        // 检查是否正在下载
        if (isDownloading(playerName)) {
            LOGGER.debug("玩家 {} 的头像正在下载中，使用原生皮肤", playerName);
            return null; // 返回null表示使用原生皮肤
        }
        
        // 尝试从本地文件加载
        Optional<ResourceLocation> localAvatar = loadAvatarFromLocal(playerName);
        if (localAvatar.isPresent()) {
            avatarCache.put(playerName, localAvatar.get());
            return localAvatar.get();
        }
        
        // 本地没有，尝试从API获取并下载
        requestPlayerAvatarAsync(playerName);
        
        return null; // 暂时返回null，等待下载完成
    }
    
    /**
     * 检查玩家头像是否正在下载
     * @param playerName 玩家名称
     * @return 是否正在下载
     */
    public boolean isDownloading(String playerName) {
        return downloadingStatus.getOrDefault(playerName, false);
    }
    
    /**
     * 从本地文件加载头像
     * @param playerName 玩家名称
     * @return 头像ResourceLocation
     */
    private Optional<ResourceLocation> loadAvatarFromLocal(String playerName) {
        try {
            // 从玩家数据文件获取头像URL
            Optional<queryUserXtnInfoApi.PlayerInfoResponse> playerData = 
                apiService.loadPlayerDataFromFile(List.of(playerName));
            
            if (!playerData.isPresent()) {
                LOGGER.debug("未找到玩家 {} 的本地数据文件", playerName);
                return Optional.empty();
            }
            
            queryUserXtnInfoApi.PlayerInfo playerInfo = playerData.get().getPlayerInfo(playerName);
            if (playerInfo == null) {
                LOGGER.debug("玩家 {} 的数据文件中没有头像信息", playerName);
                return Optional.empty();
            }
            
            String avatarUrl = playerInfo.getAvatar();
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                LOGGER.debug("玩家 {} 没有头像URL", playerName);
                return Optional.empty();
            }
            
            // 从URL提取SHA256
            String expectedSha256 = HashUtils.extractSha256FromUrl(avatarUrl);
            if (expectedSha256 == null) {
                LOGGER.warn("无法从头像URL中提取SHA256: {}", avatarUrl);
                return Optional.empty();
            }
            
            // 检查本地文件是否存在且SHA256匹配
            File avatarFile = MCGOStorageManager.getInstance().getCacheFile(expectedSha256, "avatar");
            if (!avatarFile.exists()) {
                LOGGER.debug("头像文件不存在: {}", avatarFile.getAbsolutePath());
                return Optional.empty();
            }
            
            // 验证SHA256
            if (!validateFileSha256(avatarFile, expectedSha256)) {
                LOGGER.warn("头像文件SHA256验证失败: {}", avatarFile.getAbsolutePath());
                // 删除损坏的文件
                try {
                    Files.delete(avatarFile.toPath());
                } catch (IOException e) {
                    LOGGER.error("删除损坏的头像文件失败", e);
                }
                return Optional.empty();
            }
            
            // 加载图片并创建ResourceLocation
            return loadImageAsResourceLocation(avatarFile, playerName);
            
        } catch (Exception e) {
            LOGGER.error("从本地加载玩家 {} 头像失败", playerName, e);
            return Optional.empty();
        }
    }
    
    /**
     * 验证文件的SHA256哈希值
     * @param file 文件
     * @param expectedSha256 期望的SHA256值
     * @return 是否匹配
     */
    private boolean validateFileSha256(File file, String expectedSha256) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return expectedSha256.equalsIgnoreCase(hexString.toString());
            
        } catch (Exception e) {
            LOGGER.error("验证文件SHA256失败: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 将图片文件加载为ResourceLocation
     * @param imageFile 图片文件
     * @param playerName 玩家名称
     * @return ResourceLocation
     */
    private Optional<ResourceLocation> loadImageAsResourceLocation(File imageFile, String playerName) {
        try {
            // 直接使用NativeImage加载图片文件
            NativeImage nativeImage = NativeImage.read(Files.newInputStream(imageFile.toPath()));
            
            // 调整图片大小为64x64（头像标准尺寸）
            if (nativeImage.getWidth() != 64 || nativeImage.getHeight() != 64) {
                NativeImage resizedImage = new NativeImage(64, 64, false);
                nativeImage.resizeSubRectTo(0, 0, nativeImage.getWidth(), nativeImage.getHeight(), resizedImage);
                nativeImage.close();
                nativeImage = resizedImage;
            }
            
            // 创建动态纹理
            DynamicTexture texture = new DynamicTexture(nativeImage);
            
            // 注册纹理并获取ResourceLocation
            ResourceLocation resourceLocation = new ResourceLocation("blockoffensive", "avatar/" + playerName.toLowerCase());
            Minecraft.getInstance().getTextureManager().register(resourceLocation, texture);
            
            LOGGER.info("成功加载玩家 {} 的头像: {}", playerName, imageFile.getAbsolutePath());
            return Optional.of(resourceLocation);
            
        } catch (Exception e) {
            LOGGER.error("加载图片文件失败: {}", imageFile.getAbsolutePath(), e);
            return Optional.empty();
        }
    }
    

    
    /**
     * 异步请求玩家头像
     * @param playerName 玩家名称
     */
    private void requestPlayerAvatarAsync(String playerName) {
        // 标记为正在下载
        downloadingStatus.put(playerName, true);
        
        // 在新线程中执行API请求
        new Thread(() -> {
            try {
                LOGGER.info("开始请求玩家 {} 的头像数据", playerName);
                
                // 调用API获取玩家信息
                Optional<queryUserXtnInfoApi.PlayerInfoResponse> response = 
                    apiService.queryPlayerInfo(List.of(playerName));
                
                if (response.isPresent()) {
                    LOGGER.info("成功获取玩家 {} 的头像数据，等待下载完成", playerName);
                    // API会自动触发下载，我们只需要等待一段时间后重新检查
                    Thread.sleep(2000); // 等待2秒让下载有时间完成
                    
                    // 重新尝试从本地加载
                    Optional<ResourceLocation> avatar = loadAvatarFromLocal(playerName);
                    if (avatar.isPresent()) {
                        avatarCache.put(playerName, avatar.get());
                        LOGGER.info("玩家 {} 的头像下载并加载成功", playerName);
                    }
                } else {
                    LOGGER.warn("无法获取玩家 {} 的头像数据", playerName);
                }
                
            } catch (Exception e) {
                LOGGER.error("请求玩家 {} 头像失败", playerName, e);
            } finally {
                // 移除下载状态
                downloadingStatus.remove(playerName);
            }
        }).start();
    }
    
    /**
     * 清除玩家头像缓存
     * @param playerName 玩家名称
     */
    public void clearPlayerAvatarCache(String playerName) {
        avatarCache.remove(playerName);
        downloadingStatus.remove(playerName);
    }
    
    /**
     * 清除所有头像缓存
     */
    public void clearAllAvatarCache() {
        avatarCache.clear();
        downloadingStatus.clear();
    }
}