package com.phasetranscrystal.fpsmatch.mcgo.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * MCGO存储管理器，负责管理MCGO相关的文件存储路径
 * 将MVP音乐和玩家图片存储在mcgo主目录下
 */
public class MCGOStorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MCGOStorageManager.class);
    private static final Gson GSON = new Gson();
    
    // MCGO存储目录名称
    private static final String MCGO_DIR_NAME = "mcgo";
    private static final String CACHE_DIR_NAME = "cache";
    private static final String CONFIG_FILE_NAME = "mcgo_storage_config.json";
    
    // 缓存类型
    public static final String MUSIC_CACHE_TYPE = "music";
    public static final String AVATAR_CACHE_TYPE = "avatar";
    public static final String COVER_CACHE_TYPE = "cover";
    public static final String PLAYER_DATA_CACHE_TYPE = "player_data";
    
    private static MCGOStorageManager INSTANCE;
    private final File mcgoRootDir;
    private final File cacheDir;
    private final File configFile;
    
    private MCGOStorageManager() {
        // 获取游戏根目录
        File gameDir = FMLLoader.getGamePath().toFile();
        
        // 初始化MCGO目录结构
        this.mcgoRootDir = new File(gameDir, MCGO_DIR_NAME);
        this.cacheDir = new File(mcgoRootDir, CACHE_DIR_NAME);
        this.configFile = new File(mcgoRootDir, CONFIG_FILE_NAME);
        
        // 确保目录存在
        initializeDirectories();
        
        // 初始化配置文件
        initializeConfig();
    }
    
    /**
     * 获取单例实例
     */
    public static MCGOStorageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MCGOStorageManager();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化目录结构
     */
    private void initializeDirectories() {
        try {
            if (!mcgoRootDir.exists() && !mcgoRootDir.mkdirs()) {
                throw new RuntimeException("Failed to create MCGO root directory: " + mcgoRootDir);
            }
            
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new RuntimeException("Failed to create MCGO cache directory: " + cacheDir);
            }
            
            LOGGER.info("MCGO storage directories initialized at: {}", mcgoRootDir.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize MCGO directories", e);
            throw new RuntimeException("MCGO storage initialization failed", e);
        }
    }
    
    /**
     * 初始化配置文件
     */
    private void initializeConfig() {
        try {
            if (!configFile.exists()) {
                // 创建默认配置
                Map<String, Object> defaultConfig = new HashMap<>();
                defaultConfig.put("version", "1.0");
                defaultConfig.put("mcgoRootPath", mcgoRootDir.getCanonicalPath());
                defaultConfig.put("cachePath", cacheDir.getCanonicalPath());
                defaultConfig.put("supportedCacheTypes", new String[]{MUSIC_CACHE_TYPE, AVATAR_CACHE_TYPE, COVER_CACHE_TYPE, PLAYER_DATA_CACHE_TYPE});
                
                // 写入配置文件
                String configJson = GSON.toJson(defaultConfig);
                Files.write(configFile.toPath(), configJson.getBytes());
                
                LOGGER.info("Created MCGO storage config file: {}", configFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize MCGO config file", e);
        }
    }
    
    /**
     * 获取缓存文件路径
     * @param filename 文件名
     * @param type 缓存类型 (music, avatar, cover)
     * @return 缓存文件对象
     */
    public File getCacheFile(String filename, String type) {
        // 清理文件名
        String cleanFilename = fixFileName(filename);
        
        // 创建类型特定的缓存目录
        File typeCacheDir = new File(cacheDir, type);
        if (!typeCacheDir.exists() && !typeCacheDir.mkdirs()) {
            LOGGER.warn("Failed to create cache directory for type: {}", type);
        }
        
        // 添加文件扩展名
        String suffix = getFileExtensionForType(type);
        String finalFilename = cleanFilename + suffix;
        
        return new File(typeCacheDir, finalFilename);
    }
    
    /**
     * 获取MCGO根目录
     */
    public File getMCGORootDir() {
        return mcgoRootDir;
    }
    
    /**
     * 获取缓存根目录
     */
    public File getCacheDir() {
        return cacheDir;
    }
    
    /**
     * 获取特定类型的缓存目录
     */
    public File getTypeCacheDir(String type) {
        return new File(cacheDir, type);
    }
    
    /**
     * 清理文件名，移除不安全字符
     */
    private String fixFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unnamed_file";
        }
        
        // 定义不能包含的特殊字符
        String[] specialChars = new String[]{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};
        
        // 遍历特殊字符数组并替换
        for (String charToReplace : specialChars) {
            fileName = fileName.replace(charToReplace, "");
        }
        
        // 确保文件名不为空
        if (fileName.trim().isEmpty()) {
            fileName = "unnamed_file";
        }
        
        return fileName.trim();
    }
    
    /**
     * 根据缓存类型获取对应的文件扩展名
     */
    private String getFileExtensionForType(String type) {
        switch (type) {
            case MUSIC_CACHE_TYPE:
                return ".wav";
            case AVATAR_CACHE_TYPE:
                return ".png";
            case COVER_CACHE_TYPE:
                return ".jpg";
            case PLAYER_DATA_CACHE_TYPE:
                return ".json";
            default:
                return "." + type;
        }
    }
    
    /**
     * 检查缓存文件是否存在
     */
    public boolean cacheFileExists(String filename, String type) {
        return getCacheFile(filename, type).exists();
    }
    
    /**
     * 删除缓存文件
     */
    public boolean deleteCacheFile(String filename, String type) {
        File cacheFile = getCacheFile(filename, type);
        if (cacheFile.exists()) {
            boolean deleted = cacheFile.delete();
            if (deleted) {
                LOGGER.debug("Deleted cache file: {}", cacheFile.getAbsolutePath());
            } else {
                LOGGER.warn("Failed to delete cache file: {}", cacheFile.getAbsolutePath());
            }
            return deleted;
        }
        return true; // 文件不存在，视为删除成功
    }
    
    /**
     * 清理特定类型的所有缓存文件
     */
    public void clearCacheByType(String type) {
        File typeCacheDir = getTypeCacheDir(type);
        if (typeCacheDir.exists() && typeCacheDir.isDirectory()) {
            File[] files = typeCacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        LOGGER.debug("Deleted cache file: {}", file.getAbsolutePath());
                    }
                }
            }
        }
    }
    
    /**
     * 获取玩家数据文件
     * @param filename 文件名（通常是玩家ID列表的哈希值）
     * @return 玩家数据文件对象
     */
    public File getPlayerDataFile(String filename) {
        return getCacheFile(filename, PLAYER_DATA_CACHE_TYPE);
    }
    
    /**
     * 检查玩家数据文件是否存在
     * @param filename 文件名
     * @return 是否存在
     */
    public boolean playerDataFileExists(String filename) {
        return cacheFileExists(filename, PLAYER_DATA_CACHE_TYPE);
    }
    
    /**
     * 删除玩家数据文件
     * @param filename 文件名
     * @return 是否删除成功
     */
    public boolean deletePlayerDataFile(String filename) {
        return deleteCacheFile(filename, PLAYER_DATA_CACHE_TYPE);
    }
    
    /**
     * 清理所有玩家数据文件
     */
    public void clearAllPlayerData() {
        clearCacheByType(PLAYER_DATA_CACHE_TYPE);
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        long totalSize = 0;
        int totalFiles = 0;
        Map<String, Integer> filesByType = new HashMap<>();
        Map<String, Long> sizeByType = new HashMap<>();
        
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] typeDirectories = cacheDir.listFiles(File::isDirectory);
            if (typeDirectories != null) {
                for (File typeDir : typeDirectories) {
                    String type = typeDir.getName();
                    int typeFileCount = 0;
                    long typeSize = 0;
                    
                    File[] files = typeDir.listFiles(File::isFile);
                    if (files != null) {
                        for (File file : files) {
                            typeFileCount++;
                            typeSize += file.length();
                        }
                    }
                    
                    filesByType.put(type, typeFileCount);
                    sizeByType.put(type, typeSize);
                    totalFiles += typeFileCount;
                    totalSize += typeSize;
                }
            }
        }
        
        return new CacheStats(totalFiles, totalSize, filesByType, sizeByType);
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        private final int totalFiles;
        private final long totalSize;
        private final Map<String, Integer> filesByType;
        private final Map<String, Long> sizeByType;
        
        public CacheStats(int totalFiles, long totalSize, Map<String, Integer> filesByType, Map<String, Long> sizeByType) {
            this.totalFiles = totalFiles;
            this.totalSize = totalSize;
            this.filesByType = filesByType;
            this.sizeByType = sizeByType;
        }
        
        public int getTotalFiles() { return totalFiles; }
        public long getTotalSize() { return totalSize; }
        public Map<String, Integer> getFilesByType() { return filesByType; }
        public Map<String, Long> getSizeByType() { return sizeByType; }
    }
}