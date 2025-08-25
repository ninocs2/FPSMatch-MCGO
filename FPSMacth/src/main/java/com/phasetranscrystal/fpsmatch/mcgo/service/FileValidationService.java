package com.phasetranscrystal.fpsmatch.mcgo.service;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.mcgo.storage.MCGOStorageManager;
import com.phasetranscrystal.fpsmatch.mcgo.util.HashUtils;

import java.io.File;
import java.util.Optional;

/**
 * 文件验证服务类
 * 用于处理MVP音乐和头像文件的SHA256验证机制
 */
public class FileValidationService {
    
    private static FileValidationService instance;
    private final MCGOStorageManager storageManager;
    
    private FileValidationService() {
        this.storageManager = MCGOStorageManager.getInstance();
    }
    
    public static FileValidationService getInstance() {
        if (instance == null) {
            instance = new FileValidationService();
        }
        return instance;
    }
    
    /**
     * 验证MVP音乐文件
     * @param musicUrl 音乐URL
     * @return 验证结果，包含文件路径和验证状态
     */
    public FileValidationResult validateMvpMusic(String musicUrl) {
        return validateFile(musicUrl, "music", ".wav");
    }
    
    /**
     * 验证头像文件
     * @param avatarUrl 头像URL
     * @return 验证结果，包含文件路径和验证状态
     */
    public FileValidationResult validateAvatar(String avatarUrl) {
        return validateFile(avatarUrl, "avatar", ".png");
    }
    
    /**
     * 通用文件验证方法
     * @param url 文件URL
     * @param cacheType 缓存类型
     * @param defaultExtension 默认文件扩展名
     * @return 验证结果
     */
    private FileValidationResult validateFile(String url, String cacheType, String defaultExtension) {
        if (url == null || url.isEmpty()) {
            return new FileValidationResult(null, ValidationStatus.INVALID_URL, "URL为空或无效");
        }
        
        // 从URL中提取SHA256哈希值
        String expectedHash = HashUtils.extractSha256FromUrl(url);
        if (expectedHash == null) {
            return new FileValidationResult(null, ValidationStatus.INVALID_HASH, "无法从URL中提取有效的SHA256哈希值");
        }
        
        // 构建本地文件路径
        // MCGOStorageManager会自动添加正确的扩展名，所以这里只传递哈希值
        File localFile = storageManager.getCacheFile(expectedHash, cacheType);
        
        // 检查文件是否存在
        if (!localFile.exists()) {
            return new FileValidationResult(localFile, ValidationStatus.FILE_NOT_FOUND, "本地文件不存在，需要下载");
        }
        
        // 验证文件哈希值
        String actualHash = HashUtils.calculateFileSha256(localFile);
        if (actualHash == null) {
            return new FileValidationResult(localFile, ValidationStatus.HASH_CALCULATION_FAILED, "无法计算文件哈希值");
        }
        
        if (HashUtils.isSameHash(expectedHash, actualHash)) {
            return new FileValidationResult(localFile, ValidationStatus.VALID, "文件验证成功");
        } else {
            FPSMatch.LOGGER.warn("文件哈希值不匹配: 期望={}, 实际={}, 文件={}", expectedHash, actualHash, localFile.getAbsolutePath());
            return new FileValidationResult(localFile, ValidationStatus.HASH_MISMATCH, "文件哈希值不匹配，需要重新下载");
        }
    }
    
    /**
     * 获取文件的本地缓存路径（不进行验证）
     * @param url 文件URL
     * @param cacheType 缓存类型
     * @param defaultExtension 默认文件扩展名
     * @return 本地文件路径，如果URL无效则返回空Optional
     */
    public Optional<File> getLocalFilePath(String url, String cacheType, String defaultExtension) {
        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }
        
        String expectedHash = HashUtils.extractSha256FromUrl(url);
        if (expectedHash == null) {
            return Optional.empty();
        }
        
        // MCGOStorageManager会自动添加正确的扩展名，所以这里只传递哈希值
        File localFile = storageManager.getCacheFile(expectedHash, cacheType);
        return Optional.of(localFile);
    }
    
    /**
     * 文件验证结果
     */
    public static class FileValidationResult {
        private final File file;
        private final ValidationStatus status;
        private final String message;
        
        public FileValidationResult(File file, ValidationStatus status, String message) {
            this.file = file;
            this.status = status;
            this.message = message;
        }
        
        public File getFile() { return file; }
        public ValidationStatus getStatus() { return status; }
        public String getMessage() { return message; }
        
        public boolean isValid() {
            return status == ValidationStatus.VALID;
        }
        
        public boolean needsDownload() {
            return status == ValidationStatus.FILE_NOT_FOUND || status == ValidationStatus.HASH_MISMATCH;
        }
    }
    
    /**
     * 验证状态枚举
     */
    public enum ValidationStatus {
        VALID("文件有效"),
        FILE_NOT_FOUND("文件不存在"),
        HASH_MISMATCH("哈希值不匹配"),
        HASH_CALCULATION_FAILED("哈希值计算失败"),
        INVALID_URL("无效的URL"),
        INVALID_HASH("无效的哈希值");
        
        private final String description;
        
        ValidationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}