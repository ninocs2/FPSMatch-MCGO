package com.phasetranscrystal.fpsmatch.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import com.phasetranscrystal.fpsmatch.utils.HashUtils;

/**
 * MVP音乐系统工具类
 * 负责管理玩家的MVP音乐、头像等资源的获取、缓存和更新
 *
 * 主要功能：
 * 1. 从API获取玩家数据
 * 2. 管理三级缓存（内存、文件、API）
 * 3. 下载和缓存音乐、头像资源
 * 4. 定时更新和清理过期数据
 *
 * 缓存策略：
 * - 内存缓存：优先级最高，无需IO操作
 * - 文件缓存：次优先级，持久化存储
 * - API请求：最后选择，当缓存失效时使用
 *
 * 更新机制：
 * - 自动更新：每2分钟检查一次数据是否过期
 * - 强制更新：删除旧文件并重新下载资源
 * - 过期清理：每天清理超过14天的缓存文件
 */
public class MvpMusicUtils {
    // 用于JSON序列化和反序列化的Gson实例
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 将所有资源路径集中管理
    private static final class ResourcePaths {
        // 添加文件扩展名常量
        static final String JSON_EXTENSION = ".json";
        // 添加统一的基础路径
        static final String BASE_DIR = "MCGO";
        // 修改现有路径
        static final String CACHE_DIR = BASE_DIR + "/Player_Data";
        static final String IMAGE_CACHE_DIR = BASE_DIR + "/userImg";
        static final String MUSIC_CACHE_DIR = BASE_DIR + "/MvpMusic";

        // 添加文件名生成方法
        static String getPlayerDataFileName(String playerName) {
            return playerName + JSON_EXTENSION;
        }

        static String getResourceFileName(String url) {
            return url.substring(url.lastIndexOf('/') + 1);
        }
    }
    // 内存缓存，使用线程安全的ConcurrentHashMap
    private static final Map<String, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    // 缓存文件的最大保存时间（14天）
    /** 缓存文件的最大保存时间（14天） */
    private static final long CACHE_EXPIRY_TIME = 14 * 24 * 60 * 60 * 1000L;
    // 更新间隔（2分钟）
    /** 数据更新间隔（2分钟） */
    public static final long UPDATE_THRESHOLD = 2 * 60 * 1000L;

    // 添加默认音乐常量
    /** 默认CS2 MVP音乐URL */
    private static final String DEFAULT_MUSIC_URL = "https://minio.mcgo.xin/music/default/valve_cs2_Mvp.wav";  // 默认CS2 MVP音乐URL
    /** 默认音乐名称 */
    private static final String DEFAULT_MUSIC_NAME = "默认cs2音乐盒";  // 默认音乐名称
    /** 默认用户头像 */
    private static final String DEFAULT_IMAGE_URL = "https://minio.mcgo.xin/music/default/yuanshen.png";  // 默认用户头像

    // 添加更新计数器
    /** 更新计数器，用于统计和监控 */
    private static final AtomicInteger updateCounter = new AtomicInteger(0);

    // 添加统一的线程池管理
    /** 用于异步任务的线程池 */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newFixedThreadPool(3);
    /** 用于定时任务的调度器 */
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    /**
     * 玩家数据实体类
     * 用于存储玩家的MVP音乐、头像等信息
     */
    private static class PlayerData {
        String musicUrl;      // MVP音乐URL
        String imageUrl;      // 玩家头像URL
        String musicName;     // MVP音乐名称
        String userNm;        // 玩家用户名
        String uuid;          // 玩家UUID
        long timestamp;       // 数据获取时间戳

        // 添加资源哈希值记录
        String musicUrlHash;  // 音乐URL的SHA256哈希值
        String imageUrlHash;  // 头像URL的SHA256哈希值

        PlayerData(String musicUrl, String imageUrl, String musicName, String userNm, String uuid) {
            this.musicUrl = musicUrl;
            this.imageUrl = imageUrl;
            this.musicName = musicName;
            this.userNm = userNm;
            this.uuid = uuid;
            this.timestamp = System.currentTimeMillis();

            // 提取并保存哈希值 - 使用HashUtils
            this.musicUrlHash = HashUtils.extractSha256FromUrl(musicUrl);
            this.imageUrlHash = HashUtils.extractSha256FromUrl(imageUrl);
        }

        // 检查URL是否发生变化（基于哈希值）- 使用HashUtils
        public boolean hasMusicChanged(String newMusicUrl) {
            String newHash = HashUtils.extractSha256FromUrl(newMusicUrl);
            return !HashUtils.isSameHash(this.musicUrlHash, newHash);
        }

        public boolean hasImageChanged(String newImageUrl) {
            String newHash = HashUtils.extractSha256FromUrl(newImageUrl);
            return !HashUtils.isSameHash(this.imageUrlHash, newHash);
        }
    }

    /**
     * 资源更新决策类
     */
    private static class ResourceUpdateDecision {
        final boolean updateMusic;
        final boolean updateImage;
        final boolean updateData;

        ResourceUpdateDecision(boolean updateMusic, boolean updateImage) {
            this(updateMusic, updateImage, true);
        }

        ResourceUpdateDecision(boolean updateMusic, boolean updateImage, boolean updateData) {
            this.updateMusic = updateMusic;
            this.updateImage = updateImage;
            this.updateData = updateData;
        }

        boolean needsAnyUpdate() {
            return updateMusic || updateImage || updateData;
        }
    }

    /**
     * 检查玩家数据是否需要更新
     * 基于SHA256哈希值比较决定是否更新
     */
    private static ResourceUpdateDecision checkResourceUpdate(PlayerData currentData, PlayerData newData) {
        if (currentData == null || newData == null) {
            return new ResourceUpdateDecision(true, true);
        }

        boolean musicNeedsUpdate = false;
        boolean imageNeedsUpdate = false;
        boolean dataChanged = false;

        // 检查音乐URL是否变化
        if (newData.musicUrl != null && currentData.hasMusicChanged(newData.musicUrl)) {
            musicNeedsUpdate = true;
            dataChanged = true;
        }

        // 检查头像URL是否变化
        if (newData.imageUrl != null && currentData.hasImageChanged(newData.imageUrl)) {
            imageNeedsUpdate = true;
            dataChanged = true;
        }

        // 检查其他字段是否变化
        if (!Objects.equals(currentData.musicName, newData.musicName) ||
                !Objects.equals(currentData.userNm, newData.userNm) ||
                !Objects.equals(currentData.uuid, newData.uuid)) {
            dataChanged = true;
        }

        return new ResourceUpdateDecision(musicNeedsUpdate, imageNeedsUpdate, dataChanged);
    }

    /**
     * 更新玩家数据
     * 基于SHA256哈希值比较决定是否更新资源
     */
    public static void updatePlayerData(String playerName) {
        try {
            // 获取当前缓存的数据
            PlayerData currentData = playerDataCache.get(playerName);

            // 从API获取新数据
            PlayerData newData = getPlayerDataFromApi(playerName);
            if (newData == null) {
                FPSMatch.LOGGER.error("无法从API获取玩家 {} 的数据", playerName);
                return;
            }

            // 检查是否需要更新
            ResourceUpdateDecision decision = checkResourceUpdate(currentData, newData);

            if (!decision.needsAnyUpdate()) {
                FPSMatch.LOGGER.info("玩家 {} 的数据未变化，无需更新", playerName);
                return;
            }

            // 更新内存和文件缓存
            if (decision.updateData) {
                playerDataCache.put(playerName, newData);
                savePlayerDataToFile(playerName, newData);
                FPSMatch.LOGGER.info("已更新玩家 {} 的基本数据", playerName);
            }

            // 根据需要异步更新资源
            CompletableFuture.runAsync(() -> {
                try {
                    if (decision.updateMusic && newData.musicUrl != null) {
                        FPSMatch.LOGGER.info("玩家 {} 的音乐资源已变化，正在更新...", playerName);
                        downloadResource(newData.musicUrl, ResourcePaths.MUSIC_CACHE_DIR, "MVP音乐");
                    }

                    if (decision.updateImage && newData.imageUrl != null) {
                        FPSMatch.LOGGER.info("玩家 {} 的头像资源已变化，正在更新...", playerName);
                        downloadResource(newData.imageUrl, ResourcePaths.IMAGE_CACHE_DIR, "玩家头像");
                    }
                } catch (Exception e) {
                    FPSMatch.LOGGER.error("更新玩家 {} 资源时出错", playerName, e);
                }
            }, ASYNC_EXECUTOR);

            // 更新计数器增加
            updateCounter.incrementAndGet();
        } catch (Exception e) {
            FPSMatch.LOGGER.error("更新玩家 {} 数据时发生错误", playerName, e);
        }
    }

    /**
     * 获取并更新所有在线玩家的数据
     * 使用哈希比较而非时间戳决定是否更新
     */
    public static void updateAllOnlinePlayers() {
        try {
            Collection<String> players = ServerUtils.getAllOnlinePlayers();
            if (players.isEmpty()) {
                FPSMatch.LOGGER.info("当前没有在线玩家，跳过更新");
                return;
            }

            int updatedCount = 0;
            for (String playerName : players) {
                try {
                    // 获取当前缓存的数据
                    PlayerData currentData = playerDataCache.get(playerName);

                    // 从API获取新数据
                    PlayerData newData = getPlayerDataFromApi(playerName);
                    if (newData == null) continue;

                    // 检查是否需要更新
                    ResourceUpdateDecision decision = checkResourceUpdate(currentData, newData);

                    if (decision.needsAnyUpdate()) {
                        // 更新内存和文件缓存
                        if (decision.updateData) {
                            playerDataCache.put(playerName, newData);
                            savePlayerDataToFile(playerName, newData);
                        }

                        // 更新资源（异步）
                        final ResourceUpdateDecision finalDecision = decision;
                        final PlayerData finalNewData = newData;
                        ASYNC_EXECUTOR.submit(() -> {
                            try {
                                if (finalDecision.updateMusic && finalNewData.musicUrl != null) {
                                    downloadResource(finalNewData.musicUrl, ResourcePaths.MUSIC_CACHE_DIR, "MVP音乐");
                                }

                                if (finalDecision.updateImage && finalNewData.imageUrl != null) {
                                    downloadResource(finalNewData.imageUrl, ResourcePaths.IMAGE_CACHE_DIR, "玩家头像");
                                }
                            } catch (Exception e) {
                                FPSMatch.LOGGER.error("更新玩家 {} 资源时出错", playerName, e);
                            }
                        });

                        updatedCount++;
                    }
                } catch (Exception e) {
                    FPSMatch.LOGGER.error("更新玩家 {} 数据时发生错误", playerName, e);
                }
            }

            FPSMatch.LOGGER.info("已完成在线玩家数据更新，其中 {}/{} 名玩家数据有变化", updatedCount, players.size());
        } catch (Exception e) {
            FPSMatch.LOGGER.error("批量更新玩家数据时发生错误", e);
        }
    }

    private static void logError(String message, Object... args) {
        FPSMatch.LOGGER.error(message, args);
    }

    private static void logInfo(String message, Object... args) {
        FPSMatch.LOGGER.info(message, args);
    }

    /**
     * 从本地文件加载玩家数据
     * 简化版本 - 移除了完整性验证
     */
    private static PlayerData loadPlayerDataFromFile(String playerName) {
        File cacheDir = new File(ResourcePaths.CACHE_DIR);
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                FPSMatch.LOGGER.error("无法创建缓存目录: {}", ResourcePaths.CACHE_DIR);
                return null;
            }
        }

        File playerFile = new File(cacheDir, ResourcePaths.getPlayerDataFileName(playerName));
        if (!playerFile.exists()) {
            return null;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(playerFile), StandardCharsets.UTF_8)) {
            PlayerData data = GSON.fromJson(reader, PlayerData.class);
            return data;
        } catch (Exception e) {
            FPSMatch.LOGGER.error("读取玩家 {} 的缓存数据失败: {}", playerName, e.getMessage());
            return null;
        }
    }

    /**
     * 保存玩家数据到本地文件（UTF-8格式）
     */
    private static void savePlayerDataToFile(String playerName, PlayerData data) {
        try {
            // 确保目录存在
            File cacheDir = new File(ResourcePaths.CACHE_DIR);
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    FPSMatch.LOGGER.error("无法创建缓存目录: {}", ResourcePaths.CACHE_DIR);
                    return;
                }
                FPSMatch.LOGGER.info("已创建缓存目录: {}", ResourcePaths.CACHE_DIR);
            }

            // 创建文件并保存数据
            File playerFile = new File(cacheDir, ResourcePaths.getPlayerDataFileName(playerName));
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(playerFile), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
                FPSMatch.LOGGER.info("已保存玩家 {} 的数据到文件", playerName);
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("保存玩家 {} 的缓存数据失败: {}", playerName, e.getMessage());
        }
    }

    /**
     * 获取玩家数据的主方法
     * 优先级：内存缓存 > 文件缓存 > API请求
     */
    public static PlayerData getPlayerData(String playerName) {
        // 1. 检查内存缓存
        PlayerData data = playerDataCache.get(playerName);
        if (data != null) {
            return data;
        }

        // 2. 检查文件缓存
        data = loadPlayerDataFromFile(playerName);
        if (data != null) {
            playerDataCache.put(playerName, data);
            return data;
        }

        // 3. 从API获取新数据
        data = getPlayerDataFromApi(playerName);
        if (data != null) {
            // 保存到缓存
            playerDataCache.put(playerName, data);
            savePlayerDataToFile(playerName, data);
            // 异步下载资源
            final PlayerData finalData = data;
            ASYNC_EXECUTOR.submit(() -> downloadResources(finalData));
        }
        return data;
    }

    /**
     * 从API获取玩家数据
     * 如果API返回的音乐相关参数为空，将使用默认音乐
     */
    private static PlayerData getPlayerDataFromApi(String playerName) {
        try (CloseableHttpClient client = SSLUtils.createTrustAllHttpClient()) {
            HttpPost post = new HttpPost("https://mygo.ninocs.com/api/user/getUserMpvMusic");

            post.setHeader("Content-Type", "application/json");
            post.setHeader("x-mcgo-erna", "shenwei");

            // 获取玩家UUID
            String playerUuid = getPlayerUUID(playerName);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("playerId", playerName);
            requestBody.addProperty("userId", "");
            requestBody.addProperty("xtnNm", "");
            // 添加UUID到请求
            if (playerUuid != null) {
                requestBody.addProperty("uuid", playerUuid);
            }

            post.setEntity(new StringEntity(requestBody.toString(), "UTF-8"));

            try (CloseableHttpResponse response = client.execute(post)) {
                String responseJson = EntityUtils.toString(response.getEntity());
                JsonObject jsonResponse = GSON.fromJson(responseJson, JsonObject.class);

                if ("0".equals(jsonResponse.get("code").getAsString())) {
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    if (data == null) {
                        FPSMatch.LOGGER.warn("API返回的data字段为null: {}", playerName);
                        return createDefaultPlayerData(playerName);
                    }

                    // 获取必需参数,使用DEFAULT_IMAGE_URL作为默认值
                    String imageUrl = data.has("userImg") && !data.get("userImg").isJsonNull()
                            ? data.get("userImg").getAsString()
                            : DEFAULT_IMAGE_URL;
                    String userNm = data.get("userNm").getAsString();
                    String uuid = data.has("uuid") ? data.get("uuid").getAsString() : playerUuid;

                    // 初始化音乐参数为默认值
                    String musicUrl = DEFAULT_MUSIC_URL;
                    String musicName = DEFAULT_MUSIC_NAME;

                    // 如果API返回了音乐URL且不为空，则使用API返回的值
                    if (data.has("mvpMusicUrl") && !data.get("mvpMusicUrl").isJsonNull()) {
                        String apiMusicUrl = data.get("mvpMusicUrl").getAsString();
                        if (apiMusicUrl != null && !apiMusicUrl.trim().isEmpty()) {
                            musicUrl = apiMusicUrl;
                            // 记录SHA256文件名，用于调试 - 使用HashUtils
                            String sha256 = HashUtils.extractSha256FromUrl(apiMusicUrl);
                            if (sha256 != null) {
                                logInfo("玩家 {} 的音乐资源SHA256: {}", playerName, sha256);
                            }
                        }
                    }

                    // 如果API返回了音乐名称且不为空，则使用API返回的值
                    if (data.has("mvpMusicNm") && !data.get("mvpMusicNm").isJsonNull()) {
                        String apiMusicName = data.get("mvpMusicNm").getAsString();
                        if (apiMusicName != null && !apiMusicName.trim().isEmpty()) {
                            musicName = apiMusicName;
                        }
                    }

                    // 在getPlayerDataFromApi方法中添加以下代码，在提取音乐URL后
                    if (data.has("userImg") && !data.get("userImg").isJsonNull()) {
                        String apiImageUrl = data.get("userImg").getAsString();
                        if (apiImageUrl != null && !apiImageUrl.trim().isEmpty()) {
                            imageUrl = apiImageUrl;
                            // 记录头像SHA256文件名，用于调试 - 使用HashUtils
                            String imageHash = HashUtils.extractSha256FromUrl(apiImageUrl);
                            if (imageHash != null) {
                                logInfo("玩家 {} 的头像资源SHA256: {}", playerName, imageHash);
                            }
                        }
                    }

                    return new PlayerData(musicUrl, imageUrl, musicName, userNm, uuid);
                }
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("从API获取玩家 {} 数据时发生错误", playerName, e);
        }
        return createDefaultPlayerData(playerName);
    }

    /**
     * 获取玩家UUID
     * 使用Minecraft的玩家信息系统获取UUID
     *
     * @param playerName 玩家名称
     * @return 玩家UUID字符串，如果获取失败则返回null
     */
    private static String getPlayerUUID(String playerName) {
        try {
            // 从Minecraft客户端获取玩家信息
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                return mc.getConnection().getOnlinePlayers().stream()
                        .filter(info -> info.getProfile().getName().equals(playerName))
                        .findFirst()
                        .map(info -> info.getProfile().getId().toString())
                        .orElse(null);
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("获取玩家 {} 的UUID时发生错误", playerName, e);
        }
        return null;
    }

    /**
     * 创建默认的PlayerData对象
     */
    private static PlayerData createDefaultPlayerData(String playerName) {
        FPSMatch.LOGGER.info("为玩家 {} 创建默认数据", playerName);
        String uuid = getPlayerUUID(playerName);  // 尝试获取UUID
        return new PlayerData(DEFAULT_MUSIC_URL, DEFAULT_IMAGE_URL, DEFAULT_MUSIC_NAME, playerName, uuid);
    }

    // 新增：下载并缓存玩家头像
    public static File downloadAndCacheImage(String imageUrl) {
        return downloadAndCacheImage(imageUrl, false);
    }

    // 修改：带强制更新参数的下载方法，现在基于SHA256比较
    public static File downloadAndCacheImage(String imageUrl, boolean forceUpdate) {
        SSLUtils.initGlobalSSL();

        // 如果传入的URL为空，使用默认头像
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = DEFAULT_IMAGE_URL;
        }

        try {
            File cacheDir = new File(ResourcePaths.IMAGE_CACHE_DIR);
            if (!cacheDir.exists()) {
                if (cacheDir.mkdirs()) {
                    FPSMatch.LOGGER.info("创建头像缓存目录: {}", cacheDir.getAbsolutePath());
                } else {
                    FPSMatch.LOGGER.error("无法创建头像缓存目录: {}", cacheDir.getAbsolutePath());
                    return null;
                }
            }

            String fileName = ResourcePaths.getResourceFileName(imageUrl);
            File cacheFile = new File(cacheDir, fileName);

            // 提取URL中的SHA256哈希值 - 使用HashUtils
            String urlHash = HashUtils.extractSha256FromUrl(imageUrl);

            // 如果不是强制更新，且URL包含SHA256哈希值，且文件已存在，直接使用缓存
            if (!forceUpdate && urlHash != null && cacheFile.exists()) {
                return cacheFile;
            }

            // 如果不是强制更新且缓存存在（无SHA256或SHA256不匹配），则使用缓存
            if (!forceUpdate && cacheFile.exists() && urlHash == null) {
                return cacheFile;
            }

            // 强制更新或缓存不存在或SHA256不匹配时，下载新文件
            FPSMatch.LOGGER.info("开始下载新的玩家头像: {}", imageUrl);
            try (InputStream in = new URL(imageUrl).openStream();
                 OutputStream out = Files.newOutputStream(cacheFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                FPSMatch.LOGGER.info("已下载新的玩家头像: {}", cacheFile.getAbsolutePath());
                return cacheFile;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("下载玩家头像时发生错误", e);
            return null;
        }
    }

    /**
     * 获取玩家头像URL（带强制更新选项）
     * 修改为使用SHA256哈希比较
     */
    public static String getMvpImageUrl(String playerName, boolean forceUpdate) {
        if (forceUpdate) {
            // 直接从API获取新数据
            PlayerData newData = getPlayerDataFromApi(playerName);
            if (newData != null) {
                // 获取当前缓存的数据
                PlayerData currentData = playerDataCache.get(playerName);

                // 检查头像是否发生变化
                boolean imageChanged = (currentData == null || currentData.hasImageChanged(newData.imageUrl));

                // 如果头像已变化或没有当前数据，则更新缓存
                if (imageChanged || currentData == null) {
                    playerDataCache.put(playerName, newData);
                    savePlayerDataToFile(playerName, newData);

                    // 如果头像变化，异步下载新头像
                    if (imageChanged && newData.imageUrl != null) {
                        final String imageUrl = newData.imageUrl;
                        ASYNC_EXECUTOR.submit(() -> downloadAndCacheImage(imageUrl, true));
                    }
                }

                return newData.imageUrl != null ? newData.imageUrl : DEFAULT_IMAGE_URL;
            }
        }

        // 如果不是强制更新或API获取失败，走正常流程
        PlayerData data = getPlayerData(playerName);
        return (data != null && data.imageUrl != null) ? data.imageUrl : DEFAULT_IMAGE_URL;
    }

    // 保持原有方法向后兼容
    public static String getMvpImageUrl(String playerName) {
        return getMvpImageUrl(playerName, false);
    }

    /**
     * 下载并缓存音乐文件
     * 修改为使用SHA256哈希值比较
     */
    public static File downloadAndCacheMusic(String musicUrl) {
        SSLUtils.initGlobalSSL();

        // 如果URL为空，使用默认音乐
        if (musicUrl == null || musicUrl.trim().isEmpty()) {
            musicUrl = DEFAULT_MUSIC_URL;
        }

        try {
            File cacheDir = new File(ResourcePaths.MUSIC_CACHE_DIR);
            if (!cacheDir.exists()) {
                if (cacheDir.mkdirs()) {
                    FPSMatch.LOGGER.info("创建音乐缓存目录: {}", cacheDir.getAbsolutePath());
                } else {
                    FPSMatch.LOGGER.error("无法创建音乐缓存目录: {}", cacheDir.getAbsolutePath());
                    return null;
                }
            }

            String fileName = ResourcePaths.getResourceFileName(musicUrl);
            File cacheFile = new File(cacheDir, fileName);

            // 提取URL中的SHA256哈希值 - 使用HashUtils
            String urlHash = HashUtils.extractSha256FromUrl(musicUrl);

            // 如果URL包含SHA256哈希值且文件已存在，直接使用缓存
            if (urlHash != null && cacheFile.exists()) {
                return cacheFile;
            }

            // 如果缓存存在但没有SHA256哈希值，也使用缓存
            if (cacheFile.exists() && urlHash == null) {
                return cacheFile;
            }

            // 缓存不存在或SHA256不匹配时，下载新文件
            FPSMatch.LOGGER.info("开始下载新的MVP音乐文件: {}", musicUrl);
            try (InputStream in = new URL(musicUrl).openStream();
                 OutputStream out = Files.newOutputStream(cacheFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                FPSMatch.LOGGER.info("已下载新的MVP音乐文件: {}", cacheFile.getAbsolutePath());
                return cacheFile;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("下载MVP音乐时发生错误", e);
            return null;
        }
    }

    /**
     * 获取玩家的MVP音乐名称
     * 为了保持向后兼容，保留这个方法名
     */
    public static String getMvpMusicName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return DEFAULT_MUSIC_NAME;
        }
        String name = getMusicName(playerName);
        return name != null ? name : DEFAULT_MUSIC_NAME;
    }

    /**
     * 获取玩家的MVP音乐名称
     */
    public static String getMusicName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return DEFAULT_MUSIC_NAME;
        }
        PlayerData data = getPlayerData(playerName);
        if (data == null || data.musicName == null || data.musicName.isEmpty()) {
            return DEFAULT_MUSIC_NAME;
        }
        return data.musicName;
    }

    // 新增获取用户名的方法
    public static String getUserNm(String playerName) {
        PlayerData data = getPlayerData(playerName);
        return data != null ? data.userNm : null;
    }

    /**
     * 强制下载资源，不检查缓存
     */
    private static File forceDownloadResource(String url, String cacheDir, String resourceType) {
        // 确保SSL配置已初始化
        SSLUtils.initGlobalSSL();

        if (!ensureDirectory(cacheDir)) {
            return null;
        }

        String fileName = ResourcePaths.getResourceFileName(url);
        File cacheFile = new File(cacheDir, fileName);

        // 从URL中提取SHA256哈希值 - 使用HashUtils
        String urlHash = HashUtils.extractSha256FromUrl(url);

        // 如果URL包含SHA256哈希值且文件已存在，无需强制下载
        if (urlHash != null && cacheFile.exists()) {
            FPSMatch.LOGGER.info("资源 {} 的哈希值一致，无需强制下载", url);
            return cacheFile;
        }

        // 需要下载的情况：
        // 1. URL不包含SHA256哈希值
        // 2. 文件不存在
        // 3. SHA256哈希值已变化(通过文件名比较)
        try (InputStream in = new URL(url).openStream();
             OutputStream out = Files.newOutputStream(cacheFile.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            FPSMatch.LOGGER.info("已强制下载{}: {}", resourceType, cacheFile.getAbsolutePath());
            return cacheFile;
        } catch (Exception e) {
            FPSMatch.LOGGER.error("强制下载{}时发生错误: {}", resourceType, e.getMessage());
            return null;
        }
    }

    /**
     * 检查是否需要强制更新
     * 修改为基于哈希比较而非时间戳
     * 同时支持音乐和头像URL的检查
     */
    private static boolean needForceUpdate(PlayerData data, String newUrl) {
        // 如果数据为空或URL为空，肯定需要更新
        if (data == null || newUrl == null) {
            return true;
        }

        // 根据资源URL中的特征判断资源类型
        if (newUrl.contains("/music/") || newUrl.contains(".wav") || newUrl.contains(".mp3")) {
            // 音乐资源URL
            return data.hasMusicChanged(newUrl);
        } else if (newUrl.contains("/userImg/") || newUrl.endsWith(".png") || newUrl.endsWith(".jpg") || newUrl.endsWith(".jpeg")) {
            // 头像资源URL
            return data.hasImageChanged(newUrl);
        }

        // 如果无法确定资源类型，比较完整URL
        // URL完全不同时更新
        return !Objects.equals(data.musicUrl, newUrl) &&
                !Objects.equals(data.imageUrl, newUrl);
    }

    /**
     * 检查是否需要更新
     * 根据资源哈希值判断是否需要强制更新
     *
     * @param playerName 玩家名称
     * @return 是否需要更新
     */
    private static boolean shouldUpdate(String playerName) {
        PlayerData currentData = playerDataCache.get(playerName);
        if (currentData == null) {
            // 如果没有缓存数据，肯定需要更新
            return true;
        }

        // 检查是否超过更新阈值时间
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpdate = currentTime - currentData.timestamp;
        if (timeSinceLastUpdate < UPDATE_THRESHOLD) {
            logInfo("玩家 {} 的数据未到更新时间，跳过本次更新 (距离上次更新: {}秒)",
                    playerName, timeSinceLastUpdate / 1000);
            return false;
        }

        // 到达更新时间，需要检查数据
        return true;
    }

    private static void logUpdateSuccess(String playerName) {
        int count = updateCounter.incrementAndGet();
        logInfo("已强制更新玩家 {} 的所有数据和资源 (更新次数: {})", playerName, count);
    }

    /**
     * 获取玩家的MVP音乐URL
     * 为了保持向后兼容，保留这个方法名
     */
    public static String getMvpMusicUrl(String playerName) {
        return getMusicUrl(playerName);
    }

    /**
     * 获取玩家的MVP音乐URL,如果为空则使用默认音乐
     */
    public static String getMusicUrl(String playerName) {
        PlayerData data = getPlayerData(playerName);
        if (data == null || data.musicUrl == null || data.musicUrl.isEmpty()) {
            return DEFAULT_MUSIC_URL;
        }
        return data.musicUrl;
    }

    /**
     * 清理过期的缓存文件
     */
    public static void cleanExpiredCache() {
        File cacheDir = new File(ResourcePaths.CACHE_DIR);
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                long currentTime = System.currentTimeMillis();
                for (File file : files) {
                    try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                        PlayerData data = GSON.fromJson(reader, PlayerData.class);
                        if (data != null && currentTime - data.timestamp > CACHE_EXPIRY_TIME) {
                            if (file.delete()) {
                                FPSMatch.LOGGER.info("已删除过期缓存文件: {}", file.getName());
                            }
                        }
                    } catch (Exception e) {
                        // 如果文件损坏或无法读取，直接删除
                        if (file.delete()) {
                            FPSMatch.LOGGER.info("已删除损坏的缓存文件: {}", file.getName());
                        }
                    }
                }
            }
        }
    }

    /**
     * 修改定时更新任务，使用新的更新方法
     */
    public static void startForceUpdateTask() {
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                // 更新所有在线玩家的数据
                updateAllOnlinePlayers();
            } catch (Exception e) {
                FPSMatch.LOGGER.error("执行定时强制更新任务时发生错误", e);
            }
        }, UPDATE_THRESHOLD, UPDATE_THRESHOLD, TimeUnit.MILLISECONDS);
    }

    /**
     * 修改启动方法，同时启动清理任务和强制更新任务
     */
    public static void startTasks() {
        // 启动清理任务
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                cleanExpiredCache();
            } catch (Exception e) {
                FPSMatch.LOGGER.error("清理缓存时发生错误", e);
            }
        }, 1, 1, TimeUnit.DAYS);  // 每天执行一次清理

        // 启动强制更新任务
        startForceUpdateTask();

        FPSMatch.LOGGER.info("已启动缓存清理和强制更新任务");
    }

    private static volatile boolean isInitialized = false;

    /**
     * 初始化MVP音乐系统
     * 启动定时任务并确保只初始化一次
     *
     * 启动的任务：
     * 1. 缓存清理任务（每天执行）
     * 2. 强制更新任务（每分钟检查）
     */
    public static void initialize() {
        if (!isInitialized) {
            synchronized (MvpMusicUtils.class) {
                if (!isInitialized) {
                    startTasks();
                    isInitialized = true;
                    logInfo("MVP音乐系统已初始化");
                }
            }
        }
    }

    /**
     * 创建缓存目录，统一处理目录创建逻辑
     */
    private static boolean ensureDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                FPSMatch.LOGGER.error("无法创建目录: {}", path);
                return false;
            }
            FPSMatch.LOGGER.info("已创建目录: {}", path);
        }
        return true;
    }

    /**
     * 统一的资源下载方法
     */
    private static File downloadResource(String url, String cacheDir, String resourceType) {
        if (url == null || url.isEmpty()) {
            FPSMatch.LOGGER.error("无法下载资源，URL为空");
            return null;
        }

        SSLUtils.initGlobalSSL();

        try {
            // 确保缓存目录存在
            if (!ensureDirectory(cacheDir)) {
                return null;
            }

            // 从URL中提取文件名
            String fileName = ResourcePaths.getResourceFileName(url);
            File cacheFile = new File(cacheDir, fileName);

            // 检查是否已有缓存文件
            if (cacheFile.exists()) {
                // 从URL中提取SHA256哈希值 - 使用HashUtils
                String urlHash = HashUtils.extractSha256FromUrl(url);

                // 如果URL包含SHA256哈希值且文件已存在，无需重新下载
                if (urlHash != null) {
                    return cacheFile;
                }

                return cacheFile;
            }

            // 下载新文件
            FPSMatch.LOGGER.info("开始下载{}: {}", resourceType, url);
            try (InputStream in = new URL(url).openStream();
                 OutputStream out = Files.newOutputStream(cacheFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                FPSMatch.LOGGER.info("已下载{}: {}", resourceType, cacheFile.getAbsolutePath());
                return cacheFile;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("下载{}时发生错误: {}", resourceType, e.getMessage());
            return null;
        }
    }

    /**
     * 获取并更新所有在线玩家的数据
     * 使用哈希比较而非时间戳决定是否更新
     */
    private static void downloadResources(PlayerData data) {
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> {
                        // 确保使用有效的头像URL
                        String imageUrl = (data.imageUrl != null) ? data.imageUrl : DEFAULT_IMAGE_URL;
                        downloadResource(imageUrl, ResourcePaths.IMAGE_CACHE_DIR, "玩家头像");
                    }, ASYNC_EXECUTOR),
                    CompletableFuture.runAsync(() -> {
                        if (data.musicUrl != null) {
                            downloadResource(data.musicUrl, ResourcePaths.MUSIC_CACHE_DIR, "MVP音乐");
                        }
                    }, ASYNC_EXECUTOR)
            ).join();
        } catch (Exception e) {
            FPSMatch.LOGGER.error("下载资源时发生错误", e);
        }
    }

    /**
     * 更新资源文件，仅在哈希值有变化时下载
     */
    private static void updateResources(PlayerData newData) {
        if (newData == null) return;

        CompletableFuture.allOf(
                updateSingleResource(
                        newData.musicUrl,
                        ResourcePaths.MUSIC_CACHE_DIR,
                        "MVP音乐"
                ),
                updateSingleResource(
                        newData.imageUrl,
                        ResourcePaths.IMAGE_CACHE_DIR,
                        "玩家头像"
                )
        ).join();
    }

    private static CompletableFuture<Void> updateSingleResource(String url, String cacheDir, String resourceType) {
        // 如果是头像资源且URL为空，使用默认头像
        final String finalUrl = (url == null && ResourcePaths.IMAGE_CACHE_DIR.equals(cacheDir))
                ? DEFAULT_IMAGE_URL
                : url;

        if (finalUrl == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            // 提取URL中的SHA256哈希值 - 使用HashUtils
            String urlHash = HashUtils.extractSha256FromUrl(finalUrl);

            // 获取现有文件
            String fileName = ResourcePaths.getResourceFileName(finalUrl);
            File existingFile = new File(cacheDir, fileName);

            // 如果URL包含SHA256哈希值且文件已存在，无需更新
            if (urlHash != null && existingFile.exists()) {
                return;
            }

            // 文件不存在或需要更新时，重新下载
            downloadResource(finalUrl, cacheDir, resourceType);
        }, ASYNC_EXECUTOR);
    }

    /**
     * 异步下载玩家头像
     * 修改为使用SHA256哈希比较
     */
    public static CompletableFuture<File> downloadPlayerHeadAsync(String playerName, boolean forceUpdate) {
        return CompletableFuture.supplyAsync(() -> {
            // 先获取头像URL（可能从API获取）
            String imageUrl = getMvpImageUrl(playerName, forceUpdate);
            if (imageUrl != null) {
                // 下载头像，支持SHA256比较
                return downloadAndCacheImage(imageUrl, forceUpdate);
            }
            return null;
        }, ASYNC_EXECUTOR);
    }
}