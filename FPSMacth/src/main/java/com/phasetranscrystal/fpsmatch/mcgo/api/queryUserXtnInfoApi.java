package com.phasetranscrystal.fpsmatch.mcgo.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.phasetranscrystal.fpsmatch.mcgo.service.FileValidationService;
import com.phasetranscrystal.fpsmatch.core.network.download.Downloader;
import com.phasetranscrystal.fpsmatch.core.network.download.DownloadHolder;
import com.phasetranscrystal.fpsmatch.mcgo.storage.MCGOStorageManager;
import com.phasetranscrystal.fpsmatch.mcgo.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class queryUserXtnInfoApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(queryUserXtnInfoApi.class);
    private static final Gson GSON = new Gson();
    
    // API地址配置，按优先级排序
    private static final String PRIMARY_API_URL = "https://mygo.ninocs.com/api/user/space/queryUserXtnInfo";
    private static final String SECONDARY_API_URL = "https://api.mcgo.ninocs.com:24264/user/space/queryUserXtnInfo";
    
    // 默认值配置
    private static final String DEFAULT_AVATAR = "https://minio.mcgo.xin/pictures/default/21de85f762be90c962c560d5b9e533cde1fdffac2d616accf6b22d14274c978a.jpg";
    private static final String DEFAULT_MVP_MUSIC_URL = "https://minio.mcgo.xin/music/default/b903ba7faa2f2c4638126f5d9de604bc0d828315812bd62788802cd5136e2288.wav";
    private static final String DEFAULT_MVP_MUSIC_NAME = "默认MVP音乐";
    
    private final HttpClient httpClient;
    
    // 玩家数据缓存，用于内存中快速访问
    private final Map<String, PlayerInfoResponse> playerDataCache = new ConcurrentHashMap<>();
    
    public queryUserXtnInfoApi() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * 批量查询玩家信息
     * @param playerIds 玩家ID列表
     * @return 查询结果，如果所有API都失败则返回空Optional
     */
    public Optional<PlayerInfoResponse> queryPlayerInfo(List<String> playerIds) {
        LOGGER.info("开始查询玩家扩展信息，玩家数量: {}, 玩家ID列表: {}", playerIds.size(), playerIds);
        
        // 首先尝试从文件加载数据
        Optional<PlayerInfoResponse> cachedResponse = loadPlayerDataFromFile(playerIds);
        if (cachedResponse.isPresent()) {
            LOGGER.info("从缓存文件加载玩家数据成功，玩家数量: {}", playerIds.size());
            return cachedResponse;
        }
        
        // 缓存中没有数据，从API请求
        LOGGER.info("缓存中无数据，开始从API请求玩家信息");
        
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.add("playerIds", GSON.toJsonTree(playerIds));
        String jsonBody = GSON.toJson(requestBody);
        
        // 尝试主API
        Optional<Map<String, Object>> result = makeRequest(PRIMARY_API_URL, jsonBody);
        if (result.isPresent()) {
            LOGGER.info("Successfully queried player info from primary API");
            Optional<PlayerInfoResponse> response = parseResponse(result.get());
            if (response.isPresent()) {
                // 保存到文件
                savePlayerDataToFile(playerIds, response.get());
            }
            return response;
        }
        
        // 主API失败，尝试备用API
        LOGGER.warn("Primary API failed, trying secondary API");
        result = makeRequest(SECONDARY_API_URL, jsonBody);
        if (result.isPresent()) {
            LOGGER.info("Successfully queried player info from secondary API");
            Optional<PlayerInfoResponse> response = parseResponse(result.get());
            if (response.isPresent()) {
                // 保存到文件
                savePlayerDataToFile(playerIds, response.get());
            }
            return response;
        }
        
        LOGGER.error("所有API都无法响应，查询玩家扩展信息失败");
        return Optional.empty();
    }
    
    /**
     * 解析API响应并处理默认值
     * @param response 原始响应数据
     * @return 解析后的玩家信息响应
     */
    private Optional<PlayerInfoResponse> parseResponse(Map<String, Object> response) {
        try {
            String code = (String) response.get("code");
            String message = (String) response.get("message");
            
            if (!"0".equals(code)) {
                LOGGER.warn("API returned error code: {}, message: {}", code, message);
                return Optional.empty();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                return Optional.empty();
            }
            
            Map<String, PlayerInfo> playerInfoMap = new HashMap<>();
            
            LOGGER.info("开始解析 {} 个玩家的信息", data.size());
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String playerId = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> playerData = (Map<String, Object>) entry.getValue();
                
                LOGGER.info("正在解析玩家 {} 的信息", playerId);
                PlayerInfo playerInfo = parsePlayerInfo(playerData);
                playerInfoMap.put(playerId, playerInfo);
            }
            
            LOGGER.info("成功解析完成 {} 个玩家的信息", playerInfoMap.size());
            return Optional.of(new PlayerInfoResponse(code, message, playerInfoMap));
            
        } catch (Exception e) {
            LOGGER.error("Error parsing response", e);
            return Optional.empty();
        }
    }
    
    /**
     * 解析单个玩家信息并设置默认值
     * @param playerData 玩家数据
     * @return 解析后的玩家信息
     */
    private PlayerInfo parsePlayerInfo(Map<String, Object> playerData) {
        String playerUUID = (String) playerData.get("playerUUID");
        String userNm = (String) playerData.getOrDefault("userNm", "Unknown Player");
        String avatar = (String) playerData.getOrDefault("avatar", DEFAULT_AVATAR);
        String loginIdNbr = (String) playerData.get("loginIdNbr");
        String playerId = (String) playerData.get("playerId");
        
        // 解析xtnInfo
        @SuppressWarnings("unchecked")
        Map<String, Object> xtnInfoData = (Map<String, Object>) playerData.get("xtnInfo");
        XtnInfo xtnInfo = parseXtnInfo(xtnInfoData);
        
        // 验证头像文件
        FileValidationService.FileValidationResult avatarValidation = 
            FileValidationService.getInstance().validateAvatar(avatar);
        logValidationResult("头像", playerId, avatar, avatarValidation);
        
        // 如果头像验证失败，自动下载
        if (avatarValidation.needsDownload()) {
            downloadAvatar(avatar);
        }
        
        return new PlayerInfo(playerUUID, userNm, avatar, loginIdNbr, playerId, xtnInfo);
    }
    
    /**
     * 解析扩展信息并设置默认值
     * @param xtnInfoData 扩展信息数据
     * @return 解析后的扩展信息
     */
    private XtnInfo parseXtnInfo(Map<String, Object> xtnInfoData) {
        if (xtnInfoData == null) {
            // 使用默认值并下载
            downloadMvpMusic(DEFAULT_MVP_MUSIC_URL);
            return new XtnInfo(DEFAULT_MVP_MUSIC_URL, DEFAULT_MVP_MUSIC_NAME);
        }
        
        // 后端逻辑保证：有mvpMusicUrl就一定有mvpMusicNm
        String mvpMusicUrl = (String) xtnInfoData.get("mvpMusicUrl");
        String mvpMusicNm = (String) xtnInfoData.get("mvpMusicNm");
        
        // 如果没有MVP音乐信息，使用默认值
        if (mvpMusicUrl == null || mvpMusicUrl.trim().isEmpty()) {
            mvpMusicUrl = DEFAULT_MVP_MUSIC_URL;
            mvpMusicNm = DEFAULT_MVP_MUSIC_NAME;
            // 使用默认值时也进行正常验证和下载
            downloadMvpMusic(mvpMusicUrl);
        } else {
            // 有自定义MVP音乐时进行正常验证和下载
            FileValidationService.FileValidationResult musicValidation = 
                FileValidationService.getInstance().validateMvpMusic(mvpMusicUrl);
            logValidationResult("MVP音乐", "unknown", mvpMusicUrl, musicValidation);
            
            // 如果MVP音乐验证失败，自动下载
            if (musicValidation.needsDownload()) {
                downloadMvpMusic(mvpMusicUrl);
            }
        }
        
        return new XtnInfo(mvpMusicUrl, mvpMusicNm);
    }
    
    /**
     * 执行HTTP POST请求
     * @param url API地址
     * @param jsonBody 请求体
     * @return 响应结果，如果请求失败或状态码不是200则返回空Optional
     */
    private Optional<Map<String, Object>> makeRequest(String url, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 检查HTTP状态码
            if (response.statusCode() != 200) {
                LOGGER.warn("API request failed with status code: {} for URL: {}", response.statusCode(), url);
                return Optional.empty();
            }
            
            // 解析响应体
            Map<String, Object> responseData = GSON.fromJson(response.body(), 
                    new TypeToken<Map<String, Object>>(){}.getType());
            
            return Optional.of(responseData);
            
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error making request to URL: {}", url, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during API request to URL: {}", url, e);
            return Optional.empty();
        }
    }
    
    /**
     * 记录文件验证结果
     * @param fileType 文件类型（头像/MVP音乐）
     * @param playerId 玩家ID
     * @param url 文件URL
     * @param validation 验证结果
     */
    private void logValidationResult(String fileType, String playerId, String url, 
                                   FileValidationService.FileValidationResult validation) {
        if (validation.isValid()) {
            LOGGER.info("{}文件验证成功 - 玩家: {}, URL: {}", fileType, playerId, url);
        } else if (validation.needsDownload()) {
            LOGGER.info("{}文件需要下载 - 玩家: {}, URL: {}, 原因: {}", 
                       fileType, playerId, url, validation.getMessage());
        } else {
            LOGGER.warn("{}文件验证失败 - 玩家: {}, URL: {}, 原因: {}", 
                       fileType, playerId, url, validation.getMessage());
        }
    }
    
    /**
     * 下载头像文件
     */
    private void downloadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            LOGGER.warn("头像URL为空，跳过下载");
            return;
        }
        
        // 跳过默认头像的下载
        if (DEFAULT_AVATAR.equals(avatarUrl)) {
            LOGGER.info("跳过默认头像下载: {}", avatarUrl);
            return;
        }
        
        try {
            // 从URL中提取SHA256哈希值
            String expectedSha256 = HashUtils.extractSha256FromUrl(avatarUrl);
            if (expectedSha256 == null) {
                LOGGER.warn("无法从头像URL中提取SHA256哈希值: {}", avatarUrl);
                return;
            }
            
            // 使用SHA256作为文件名
            File avatarFile = MCGOStorageManager.getInstance().getCacheFile(expectedSha256, "avatar");
            DownloadHolder downloadHolder = new DownloadHolder(avatarUrl, avatarFile);
            
            LOGGER.info("开始下载头像文件: {} -> {}", avatarUrl, avatarFile.getAbsolutePath());
            Downloader.Instance().download(downloadHolder);
            
        } catch (Exception e) {
            LOGGER.error("下载头像文件失败: {}", avatarUrl, e);
        }
    }
    
    /**
     * 下载MVP音乐文件
     * @param musicUrl 音乐URL
     */
    private void downloadMvpMusic(String musicUrl) {
        if (musicUrl == null || musicUrl.trim().isEmpty()) {
            LOGGER.warn("MVP音乐URL为空，跳过下载");
            return;
        }
        
        try {
            // 从URL中提取SHA256哈希值
            String expectedSha256 = HashUtils.extractSha256FromUrl(musicUrl);
            if (expectedSha256 == null) {
                LOGGER.warn("无法从MVP音乐URL中提取SHA256哈希值: {}", musicUrl);
                return;
            }
            
            // 使用SHA256作为文件名
            File musicFile = MCGOStorageManager.getInstance().getCacheFile(expectedSha256, "music");
            LOGGER.info("开始下载MVP音乐文件: {} -> {}", musicUrl, musicFile.getAbsolutePath());
            
            DownloadHolder downloadHolder = new DownloadHolder(musicUrl, musicFile);
            Downloader.Instance().download(downloadHolder);
            
        } catch (Exception e) {
            LOGGER.error("下载MVP音乐文件失败: {}", musicUrl, e);
        }
    }

    /**
     * 生成玩家ID列表的哈希值，用作文件名
     * @param playerIds 玩家ID列表
     * @return 哈希值字符串
     */
    private String generatePlayerListHash(List<String> playerIds) {
        try {
            // 对玩家ID列表进行排序，确保相同的玩家列表生成相同的哈希值
            List<String> sortedIds = new ArrayList<>(playerIds);
            sortedIds.sort(String::compareTo);
            
            String combined = String.join(",", sortedIds);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            LOGGER.error("生成玩家列表哈希值失败", e);
            return "fallback_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 保存玩家数据到文件（为每个玩家单独保存）
     * @param playerIds 玩家ID列表
     * @param response 玩家信息响应
     */
    private void savePlayerDataToFile(List<String> playerIds, PlayerInfoResponse response) {
        try {
            // 为每个玩家单独保存数据文件
            for (String playerId : playerIds) {
                PlayerInfo playerInfo = response.getPlayerInfo(playerId);
                if (playerInfo != null) {
                    // 使用玩家名称作为文件名
                    String playerName = playerInfo.getUserNm();
                    if (playerName == null || playerName.trim().isEmpty()) {
                        playerName = playerId; // 如果没有玩家名称，使用玩家ID
                    }
                    
                    File dataFile = MCGOStorageManager.getInstance().getPlayerDataFile(playerName);
                    
                    // 创建存储数据结构
                    Map<String, Object> storageData = new HashMap<>();
                    storageData.put("timestamp", System.currentTimeMillis());
                    storageData.put("playerId", playerId);
                    storageData.put("playerInfo", playerInfo);
                    
                    // 写入文件
                    try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                        GSON.toJson(storageData, writer);
                    }
                    
                    LOGGER.info("玩家数据已保存到文件: {} -> {}", playerName, dataFile.getAbsolutePath());
                }
            }
            
            // 保存到内存缓存（使用原有的哈希值作为key）
            String filename = generatePlayerListHash(playerIds);
            playerDataCache.put(filename, response);
            
        } catch (Exception e) {
            LOGGER.error("保存玩家数据到文件失败", e);
        }
    }
    
    /**
     * 从文件加载玩家数据
     * @param playerIds 玩家ID列表
     * @return 玩家信息响应，如果文件不存在或加载失败则返回空Optional
     */
    public Optional<PlayerInfoResponse> loadPlayerDataFromFile(List<String> playerIds) {
        try {
            String filename = generatePlayerListHash(playerIds);
            
            // 先检查内存缓存
            PlayerInfoResponse cachedResponse = playerDataCache.get(filename);
            if (cachedResponse != null) {
                LOGGER.debug("从内存缓存加载玩家数据: {}", filename);
                return Optional.of(cachedResponse);
            }
            
            // 尝试从单个玩家文件加载数据
            Map<String, PlayerInfo> playerInfoMap = new HashMap<>();
            boolean foundAnyData = false;
            
            for (String playerId : playerIds) {
                Optional<PlayerInfo> playerInfo = loadSinglePlayerDataFromFile(playerId);
                if (playerInfo.isPresent()) {
                    playerInfoMap.put(playerId, playerInfo.get());
                    foundAnyData = true;
                }
            }
            
            if (foundAnyData) {
                PlayerInfoResponse response = new PlayerInfoResponse("0", "success", playerInfoMap);
                // 保存到内存缓存
                playerDataCache.put(filename, response);
                LOGGER.info("从单个玩家文件加载数据成功，玩家数量: {}", playerInfoMap.size());
                return Optional.of(response);
            }
            
            LOGGER.debug("未找到任何玩家数据文件");
            return Optional.empty();
            
        } catch (Exception e) {
            LOGGER.error("从文件加载玩家数据失败", e);
            return Optional.empty();
        }
    }
    
    /**
     * 从文件加载单个玩家数据
     * @param playerId 玩家ID
     * @return 玩家信息，如果文件不存在或加载失败则返回空Optional
     */
    private Optional<PlayerInfo> loadSinglePlayerDataFromFile(String playerId) {
        try {
            // 首先尝试通过玩家ID查找对应的玩家名称文件
            File playerDataDir = MCGOStorageManager.getInstance().getTypeCacheDir(MCGOStorageManager.PLAYER_DATA_CACHE_TYPE);
            if (!playerDataDir.exists()) {
                return Optional.empty();
            }
            
            File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) {
                return Optional.empty();
            }
            
            // 遍历所有玩家数据文件，查找匹配的玩家ID
            for (File file : files) {
                try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                    Map<String, Object> storageData = GSON.fromJson(reader,
                            new TypeToken<Map<String, Object>>(){}.getType());
                    
                    if (storageData == null) {
                        continue;
                    }
                    
                    String storedPlayerId = (String) storageData.get("playerId");
                    if (playerId.equals(storedPlayerId)) {
                        // 检查数据时效性
                        Long timestamp = ((Number) storageData.get("timestamp")).longValue();
                        long currentTime = System.currentTimeMillis();
                        long maxAge = 24 * 60 * 60 * 1000; // 24小时
                        
                        if (currentTime - timestamp > maxAge) {
                            LOGGER.debug("玩家数据文件已过期: {}", file.getName());
                            continue;
                        }
                        
                        // 解析玩家信息
                        @SuppressWarnings("unchecked")
                        Map<String, Object> playerData = (Map<String, Object>) storageData.get("playerInfo");
                        if (playerData != null) {
                            PlayerInfo playerInfo = parsePlayerInfoFromStorage(playerData);
                            LOGGER.debug("从文件加载单个玩家数据成功: {} -> {}", playerId, file.getName());
                            return Optional.of(playerInfo);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("读取玩家数据文件失败: {}", file.getName(), e);
                }
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            LOGGER.error("从文件加载单个玩家数据失败: {}", playerId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 从存储数据中解析玩家信息
     * @param playerData 存储的玩家数据
     * @return 解析后的玩家信息
     */
    private PlayerInfo parsePlayerInfoFromStorage(Map<String, Object> playerData) {
        String playerUUID = (String) playerData.get("playerUUID");
        String userNm = (String) playerData.get("userNm");
        String avatar = (String) playerData.get("avatar");
        String loginIdNbr = (String) playerData.get("loginIdNbr");
        String playerId = (String) playerData.get("playerId");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> xtnInfoData = (Map<String, Object>) playerData.get("xtnInfo");
        XtnInfo xtnInfo = null;
        if (xtnInfoData != null) {
            String mvpMusicUrl = (String) xtnInfoData.get("mvpMusicUrl");
            String mvpMusicNm = (String) xtnInfoData.get("mvpMusicNm");
            xtnInfo = new XtnInfo(mvpMusicUrl, mvpMusicNm);
        }
        
        return new PlayerInfo(playerUUID, userNm, avatar, loginIdNbr, playerId, xtnInfo);
    }
    
    /**
     * 获取玩家MVP音乐信息（优先从缓存读取）
     * @param playerId 玩家ID
     * @return MVP音乐信息，如果不存在则返回null
     */
    public XtnInfo getPlayerMvpInfo(String playerId) {
        // 遍历内存缓存查找玩家信息
        for (PlayerInfoResponse response : playerDataCache.values()) {
            PlayerInfo playerInfo = response.getPlayerInfo(playerId);
            if (playerInfo != null) {
                return playerInfo.getXtnInfo();
            }
        }
        
        // 如果缓存中没有找到，尝试从文件加载或请求API
        LOGGER.debug("未在内存缓存中找到玩家MVP信息，尝试从文件或API获取: {}", playerId);
        
        try {
              // 先尝试从单个玩家文件加载
              Optional<PlayerInfo> playerInfo = loadSinglePlayerDataFromFile(playerId);
              if (playerInfo.isPresent()) {
                  PlayerInfo info = playerInfo.get();
                  XtnInfo xtnInfo = info.getXtnInfo();
                  if (xtnInfo != null) {
                      LOGGER.debug("从文件加载玩家MVP信息成功: {}", playerId);
                      return xtnInfo;
                  }
              }
              
              // 如果文件中也没有，则请求API
              LOGGER.debug("文件中也未找到玩家信息，请求API: {}", playerId);
              List<String> playerIdList = new ArrayList<>();
              playerIdList.add(playerId);
              Optional<PlayerInfoResponse> response = queryPlayerInfo(playerIdList);
              if (response.isPresent() && response.get().getData() != null) {
                  PlayerInfo info = response.get().getData().get(playerId);
                  if (info != null) {
                      XtnInfo xtnInfo = info.getXtnInfo();
                      if (xtnInfo != null) {
                          LOGGER.debug("从API获取玩家MVP信息成功: {}", playerId);
                          return xtnInfo;
                      }
                  }
              }
            
        } catch (Exception e) {
            LOGGER.error("获取玩家MVP信息时发生异常: {}", playerId, e);
        }
        
        LOGGER.debug("最终未找到玩家MVP信息: {}", playerId);
        return null;
    }
    
    /**
     * 获取玩家头像信息（优先从缓存读取）
     * @param playerId 玩家ID
     * @return 头像URL，如果不存在则返回null
     */
    public String getPlayerAvatar(String playerId) {
        // 遍历内存缓存查找玩家信息
        for (PlayerInfoResponse response : playerDataCache.values()) {
            PlayerInfo playerInfo = response.getPlayerInfo(playerId);
            if (playerInfo != null) {
                return playerInfo.getAvatar();
            }
        }
        
        LOGGER.debug("未在缓存中找到玩家头像信息: {}", playerId);
        return null;
    }

    // 数据类定义
    public static class PlayerInfoResponse {
        private final String code;
        private final String message;
        private final Map<String, PlayerInfo> data;
        
        public PlayerInfoResponse(String code, String message, Map<String, PlayerInfo> data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }
        
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public Map<String, PlayerInfo> getData() { return data; }
        public PlayerInfo getPlayerInfo(String playerId) { return data.get(playerId); }
    }
    
    public static class PlayerInfo {
        private final String playerUUID;
        private final String userNm;
        private final String avatar;
        private final String loginIdNbr;
        private final String playerId;
        private final XtnInfo xtnInfo;
        
        public PlayerInfo(String playerUUID, String userNm, String avatar, String loginIdNbr, String playerId, XtnInfo xtnInfo) {
            this.playerUUID = playerUUID;
            this.userNm = userNm;
            this.avatar = avatar;
            this.loginIdNbr = loginIdNbr;
            this.playerId = playerId;
            this.xtnInfo = xtnInfo;
        }
        
        public String getPlayerUUID() { return playerUUID; }
        public String getUserNm() { return userNm; }
        public String getAvatar() { return avatar; }
        public String getLoginIdNbr() { return loginIdNbr; }
        public String getPlayerId() { return playerId; }
        public XtnInfo getXtnInfo() { return xtnInfo; }
    }
    
    public static class XtnInfo {
        private final String mvpMusicUrl;
        private final String mvpMusicNm;
        
        public XtnInfo(String mvpMusicUrl, String mvpMusicNm) {
            this.mvpMusicUrl = mvpMusicUrl;
            this.mvpMusicNm = mvpMusicNm;
        }
        
        public String getMvpMusicUrl() { return mvpMusicUrl; }
        public String getMvpMusicNm() { return mvpMusicNm; }
    }
}
