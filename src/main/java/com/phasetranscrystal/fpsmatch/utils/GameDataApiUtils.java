package com.phasetranscrystal.fpsmatch.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.MapTeams;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.mcgo.config.APIConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import com.tacz.guns.api.item.IGun;
import org.apache.http.client.config.RequestConfig;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 游戏数据API工具类
 * 用于向后端发送游戏结果数据
 * 仅在专用服务器端加载和运行
 */
@OnlyIn(Dist.DEDICATED_SERVER)
public class GameDataApiUtils {
    private static APIConfig apiConfig;
    private static final Gson gson = new Gson();
    private static final String CONFIG_FOLDER = "MCGO";
    private static final String CONFIG_FILE = "api_config.json";
    // 添加API可用性标志
    private static boolean isApiAvailable = false;

    /**
     * 玩家商店配置缓存
     */
    private static final Map<String, ShopConfigResponse> PLAYER_SHOP_CONFIG_CACHE = new HashMap<>();

    static {
        // 初始化SSL配置
        SSLUtils.initGlobalSSL();
        // 加载API配置
        loadApiConfig();
        // 检测API可用性
        checkApiAvailability();

        // 添加初始化完成日志
        FPSMatch.LOGGER.info("游戏数据API初始化完成, API状态: {}", isApiAvailable ? "可用" : "不可用");
    }

    private static class ShopConfigRequest {
        String team;
        List<String> playerIds;

        ShopConfigRequest(String team, List<String> playerIds) {
            this.team = team;
            this.playerIds = playerIds;
        }
    }

    /**
     * 加载API配置
     * 如果配置文件不存在则创建默认配置
     */
    private static void loadApiConfig() {
        try {
            // 确保配置目录存在
            File configFolder = new File(CONFIG_FOLDER);
            if (!configFolder.exists()) {
                configFolder.mkdirs();
            }

            // 配置文件路径
            File configFile = new File(configFolder, CONFIG_FILE);

            // 如果配置文件不存在，创建默认配置
            if (!configFile.exists()) {
                APIConfig defaultConfig = new APIConfig();
                defaultConfig.setApiEndpoint("https://mygo.ninocs.com");
                defaultConfig.setSaveMatch("API地址");
                defaultConfig.setWeaponConfigure("API地址");
                defaultConfig.setApiAuthHeader("请求头"); // 必须配置认证头
                defaultConfig.setApiAuthValue("Token"); // 默认认证值，需要修改为正确的值

                // 写入默认配置
                try (FileWriter writer = new FileWriter(configFile)) {
                    gson.toJson(defaultConfig, writer);
                }

                FPSMatch.LOGGER.info("已创建默认API配置文件: {}", configFile.getAbsolutePath());
                FPSMatch.LOGGER.warn("请确保修改API配置文件中的认证头值，否则API请求将被拒绝(401)");
            }

            // 读取配置文件
            try (FileReader reader = new FileReader(configFile)) {
                apiConfig = gson.fromJson(reader, APIConfig.class);

                // 检查认证头配置
                if (apiConfig.getApiAuthHeader() == null || apiConfig.getApiAuthHeader().isEmpty() ||
                        apiConfig.getApiAuthValue() == null || apiConfig.getApiAuthValue().isEmpty()) {
                    FPSMatch.LOGGER.error("API认证头配置不完整，请检查配置文件。必须设置apiAuthHeader和apiAuthValue!否则API请求将被拒绝(401)");
                }
            /*
                else {
                    FPSMatch.LOGGER.info("已加载API配置，认证头: {}={}", apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
                }

                // 打印当前的URL配置
                FPSMatch.LOGGER.info("当前API配置: endpoint={}, saveMatch={}, weaponConfigure={}",
                        apiConfig.getApiEndpoint(), apiConfig.getSaveMatch(), apiConfig.getWeaponConfigure());

                // 打印构建的完整URL示例
                FPSMatch.LOGGER.info("游戏结果API URL示例: {}", buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getSaveMatch()));
                FPSMatch.LOGGER.info("商店配置API URL示例: {}", buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getWeaponConfigure()) + "?playerId=player&team=team");
            */
            }

        } catch (Exception e) {
            FPSMatch.LOGGER.error("加载API配置失败: ", e);
        }
    }

    /**
     * 检查API可用性
     */
    private static void checkApiAvailability() {
        if (apiConfig == null) {
            isApiAvailable = false;
            return;
        }

        try {
            // 使用简单的健康检查端点进行测试
            String healthEndpoint = apiConfig.getApiEndpoint() + "/health";
            // 或者尝试使用配置中的某个端点
            if (apiConfig.getSaveMatch() != null && !apiConfig.getSaveMatch().isEmpty()) {
                healthEndpoint = buildApiUrl(apiConfig.getApiEndpoint(), "health");
            }

            try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
                HttpGet request = new HttpGet(healthEndpoint);

                // 添加认证头
                if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                    request.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
                }

                // 设置请求超时（5秒）
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(5000)
                        .setSocketTimeout(5000)
                        .build();
                request.setConfig(requestConfig);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    // 2xx 状态码表示API可用
                    isApiAvailable = statusCode >= 200 && statusCode < 300;

                    FPSMatch.LOGGER.info("API可用性检查: 状态码={}, 结果={}",
                            statusCode, isApiAvailable ? "可用" : "不可用");

                    if (statusCode == 401) {
                        FPSMatch.LOGGER.warn("API认证失败(401)，请检查认证头配置");
                    } else if (statusCode == 404) {
                        FPSMatch.LOGGER.warn("API健康检查端点不存在(404)，但API可能仍然可用");
                        // 404可能意味着健康检查端点不存在，但API可能仍然可用
                        isApiAvailable = true;
                    }
                }
            }
        } catch (Exception e) {
            isApiAvailable = false;
            FPSMatch.LOGGER.error("API可用性检查失败: ", e);
        }
    }

    /**
     * 手动设置API可用性
     * @param available 是否可用
     */
    public static void setApiAvailability(boolean available) {
        isApiAvailable = available;
        FPSMatch.LOGGER.info("API可用性已手动设置为: {}", available ? "可用" : "不可用");
    }

    /**
     * 测试API连接
     * @return 测试结果消息
     */
    public static String testApiConnection() {
        if (apiConfig == null) {
            return "API配置未加载，请检查配置文件";
        }

        StringBuilder result = new StringBuilder();
        result.append("API配置信息：\n");
        result.append("端点: ").append(apiConfig.getApiEndpoint()).append("\n");
        result.append("保存比赛路径: ").append(apiConfig.getSaveMatch()).append("\n");
        result.append("武器配置路径: ").append(apiConfig.getWeaponConfigure()).append("\n");
        result.append("认证头: ").append(apiConfig.getApiAuthHeader()).append("=").append(maskToken(apiConfig.getApiAuthValue())).append("\n\n");

        try {
            // 测试健康检查端点
            String healthEndpoint = apiConfig.getApiEndpoint() + "/health";
            result.append("测试健康检查端点: ").append(healthEndpoint).append("\n");

            try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
                HttpGet request = new HttpGet(healthEndpoint);

                // 添加认证头
                if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                    request.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
                }

                // 设置请求超时
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(5000)
                        .setSocketTimeout(5000)
                        .build();
                request.setConfig(requestConfig);

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

                    result.append("健康检查结果: 状态码=").append(statusCode);
                    if (statusCode >= 200 && statusCode < 300) {
                        result.append(" (成功)");
                    } else if (statusCode == 401) {
                        result.append(" (认证失败)");
                    } else if (statusCode == 404) {
                        result.append(" (端点不存在)");
                    } else {
                        result.append(" (请求失败)");
                    }

                    result.append("\n响应内容: ").append(responseBody).append("\n\n");
                }
            }

            // 测试武器配置端点
            if (apiConfig.getWeaponConfigure() != null && !apiConfig.getWeaponConfigure().isEmpty()) {
                String configEndpoint = buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getWeaponConfigure()) + "?playerId=test&team=test";
                result.append("测试武器配置端点: ").append(configEndpoint).append("\n");

                try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
                    HttpGet request = new HttpGet(configEndpoint);

                    // 添加认证头
                    if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                        request.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
                    }

                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        int statusCode = response.getStatusLine().getStatusCode();

                        result.append("武器配置测试结果: 状态码=").append(statusCode);
                        if (statusCode >= 200 && statusCode < 300) {
                            result.append(" (成功)");
                        } else if (statusCode == 401) {
                            result.append(" (认证失败)");
                        } else if (statusCode == 404) {
                            result.append(" (端点不存在)");
                        } else {
                            result.append(" (请求失败)");
                        }

                        result.append("\n");
                    }
                }
            }

            // 根据测试结果更新API可用性
            checkApiAvailability();
            result.append("\nAPI可用性检查后的状态: ").append(isApiAvailable ? "可用" : "不可用");

            return result.toString();

        } catch (Exception e) {
            return "API测试失败: " + e.getMessage();
        }
    }

    /**
     * 隐藏认证令牌的大部分内容，只显示前4位和后4位
     */
    private static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "******";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * 发送游戏结果数据到后端
     * 如果API不可用则跳过发送
     */
    public static boolean sendGameResult(BaseTeam winnerTeam, BaseTeam loserTeam,
                                         String mapName, String gameType, int roundCount,
                                         long startTime, long endTime, int totalPlayers, MapTeams mapTeams, String matchId) {

        // 如果API不可用，记录数据但不发送
        if (!isApiAvailable) {
            logGameResultData(winnerTeam, loserTeam, mapName, gameType, roundCount,
                    startTime, endTime, totalPlayers, mapTeams, null, matchId);
            FPSMatch.LOGGER.debug("API不可用，跳过发送游戏结果数据");
            return false;
        }

        try {
            // 创建游戏结果数据对象
            GameResultData resultData = new GameResultData();
            // 设置基本信息
            resultData.mapName = mapName;
            resultData.gameType = gameType;
            resultData.roundCount = roundCount;
            resultData.matchId = matchId;
            // 设置队伍信息
            resultData.winnerTeamName = winnerTeam.getFixedName();
            resultData.loserTeamName = loserTeam.getFixedName();
            resultData.winnerScore = winnerTeam.getScores();
            resultData.loserScore = loserTeam.getScores();
            // 设置时间和玩家数信息
            resultData.gameStartTime = startTime;
            resultData.gameEndTime = endTime;
            resultData.totalPlayers = totalPlayers;
            resultData.totalRounds = roundCount;

            // 收集所有玩家的统计数据
            resultData.playerStats = new HashMap<>();
            collectPlayerStats(winnerTeam, resultData.playerStats, true, mapTeams);
            collectPlayerStats(loserTeam, resultData.playerStats, false, mapTeams);

            // 记录数据
            logGameResultData(winnerTeam, loserTeam, mapName, gameType, roundCount,
                    startTime, endTime, totalPlayers, mapTeams, resultData, matchId);

            return sendHttpsRequest(resultData);
        } catch (Exception e) {
            FPSMatch.LOGGER.error("发送游戏结果数据失败: ", e);
            return false;
        }
    }

    /**
     * 收集队伍中所有玩家的统计数据
     *
     * @param team 队伍对象
     * @param statsMap 统计数据Map
     * @param isWinner 是否是获胜队伍
     * @param mapTeams 地图团队管理器
     */
    private static void collectPlayerStats(BaseTeam team, Map<String, PlayerStats> statsMap, boolean isWinner, MapTeams mapTeams) {
        // 遍历队伍中的每个玩家
        for (Map.Entry<UUID, PlayerData> entry : team.getPlayers().entrySet()) {
            PlayerStats stats = new PlayerStats();
            UUID playerUUID = entry.getKey();
            PlayerData playerData = entry.getValue();

            // 设置玩家基本信息
            stats.teamName = team.getFixedName();
            stats.playerUUID = playerUUID.toString();
            stats.playerId = mapTeams.playerName.get(playerUUID).getString();

            // 设置战斗数据
            stats.kills = playerData.getKills();
            stats.deaths = playerData.getDeaths();
            stats.assists = playerData.getAssists();
            stats.headshotKills = playerData.getHeadshotKills();
            stats.mvpCount = playerData.getMvpCount();
            stats.totalDamage = playerData.getDamage();
            stats.isWinner = isWinner;

            // 将玩家数据添加到Map中
            statsMap.put(playerUUID.toString(), stats);
        }
    }

    /**
     * 发送HTTPS请求到后端API
     *
     * @param data 游戏结果数据对象
     * @return 是否发送成功
     */
    private static boolean sendHttpsRequest(GameResultData data) {
        try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
            // 构建完整的API请求URL，确保URL正确
            String url = buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getSaveMatch());

            // 创建POST请求
            HttpPost httpPost = new HttpPost(url);

            // 设置请求头
            httpPost.setHeader("Content-Type", "application/json");

            // 添加自定义验证请求头
            if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                httpPost.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
                //FPSMatch.LOGGER.info("游戏结果数据API请求已添加认证头: {}={}", apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
            } else {
                FPSMatch.LOGGER.error("API认证头未配置，游戏结果数据请求可能会被拒绝(401)");
            }

            // 将数据转换为JSON
            String jsonData = gson.toJson(data);
            StringEntity entity = new StringEntity(jsonData, "UTF-8");
            httpPost.setEntity(entity);

            //FPSMatch.LOGGER.info("正在发送游戏结果数据请求: {}", url);

            // 发送请求并处理响应
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                FPSMatch.LOGGER.info("游戏结果数据API响应: statusCode={}", statusCode);
                if (statusCode != 200) {
                    FPSMatch.LOGGER.error("游戏结果数据API请求失败: statusCode={}, response={}", statusCode, responseBody);
                    if (statusCode == 401) {
                        FPSMatch.LOGGER.error("API返回401错误，请检查认证头是否正确配置");
                    }
                }
                return statusCode == 200;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("HTTPS请求失败: ", e);
            return false;
        }
    }

    /**
     * 游戏结果数据结构
     * 用于序列化为JSON发送到后端
     */
    private static class GameResultData {
        String matchId;           // 比赛ID字段
        String mapName;           // 地图名称
        String gameType;          // 游戏类型
        int roundCount;           // 当前回合数
        String winnerTeamName;    // 获胜队伍名称
        String loserTeamName;     // 失败队伍名称
        int winnerScore;          // 获胜队伍得分
        int loserScore;          // 失败队伍得分
        long gameStartTime;       // 游戏开始时间戳
        long gameEndTime;         // 游戏结束时间戳
        int totalPlayers;         // 总玩家数
        int totalRounds;          // 总回合数
        Map<String, PlayerStats> playerStats;  // 玩家统计数据
    }

    /**
     * 玩家统计数据结构
     * 记录单个玩家的所有统计数据
     */
    private static class PlayerStats {
        String teamName;         // 队伍名称
        String playerId;         // 玩家游戏名称(修改为playerId，但意义不变)
        String playerUUID;       // 玩家UUID
        int kills;              // 击杀数
        int deaths;             // 死亡数
        int assists;            // 助攻数
        int headshotKills;      // 爆头击杀数
        int mvpCount;           // MVP次数
        float totalDamage;      // 总伤害输出
        boolean isWinner;       // 是否获胜
    }

    /**
     * 记录游戏结果数据到日志
     */
    private static void logGameResultData(BaseTeam winnerTeam, BaseTeam loserTeam,
                                          String mapName, String gameType, int roundCount,
                                          long startTime, long endTime, int totalPlayers,
                                          MapTeams mapTeams, GameResultData resultData,
                                          String matchId) {

        StringBuilder log = new StringBuilder();

        log.append("================================\n");
        log.append("比赛ID: ").append(matchId).append("\n");
        log.append("API状态: ").append(isApiAvailable ? "可用" : "不可用").append("\n");
        if (resultData != null) {
            log.append("JSON数据: ").append(gson.toJson(resultData)).append("\n");
        }
        log.append("================================\n");

        FPSMatch.LOGGER.info(log.toString());
    }

    /**
     * API响应数据结构
     */
    public static class ApiResponse<T> {
        public String code;
        public T data;
        public String message;
    }

    /**
     * 商店配置响应数据结构
     */
    public static class ShopConfigData {
        public String playerId;
        public Map<String, List<ShopItem>> shopData;
        public List<ItemStackData> startKits;
    }

    public static class ItemStackData {
        @SerializedName("id")
        public String id;
        @SerializedName("Count")
        public String count;
        @SerializedName("tag")
        public Map<String, Object> tag;  // 使用 Object 类型来处理复杂的 NBT 数据
    }

    public static class ShopItem {
        public String id;
        public String name;
        public String defaultCost;     // 改回 String 类型，因为从 API 返回的是字符串
        public String maxBuyCount;     // 改回 String 类型
        public String groupId;         // 改回 String 类型
        public List<String> listenerModule;
        @SerializedName("ItemStack")
        public ItemStackData itemStack;
    }

    public static class ShopConfigResponse {
        public Map<String, List<ShopItem>> shopData;
        public List<ItemStackData> startKits;
    }

    /**
     * 获取玩家商店配置 (支持批量)
     *
     * @param player      当前请求的玩家
     * @param teamPlayers 队伍中的所有玩家，用于批量请求
     * @param teamName    队伍名称
     * @return 商店配置
     */
    public static ShopConfigResponse getPlayerShopConfig(ServerPlayer player, String teamName, List<ServerPlayer> teamPlayers) {
        String cacheKey = player.getUUID().toString() + "_" + teamName;

        // 先从缓存获取
        if (PLAYER_SHOP_CONFIG_CACHE.containsKey(cacheKey)) {
            return PLAYER_SHOP_CONFIG_CACHE.get(cacheKey);
        }

        // 如果API不可用，直接返回null
        if (!isApiAvailable) {
            FPSMatch.LOGGER.debug("API不可用，跳过获取商店配置");
            return null;
        }

        try {
            // 如果缓存没有，就为整个队伍进行一次批量请求
            fetchTeamShopConfig(teamPlayers, teamName);
            // 再次尝试从缓存中获取
            if (PLAYER_SHOP_CONFIG_CACHE.containsKey(cacheKey)) {
                return PLAYER_SHOP_CONFIG_CACHE.get(cacheKey);
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("获取商店配置时发生错误: ", e);
        }

        // 如果批量请求后仍然失败，返回null
        return null;
    }

    /**
     * 为整个队伍批量获取商店配置并缓存
     *
     * @param teamPlayers 队伍中的玩家列表
     * @param teamName    队伍名称
     */
    public static void fetchTeamShopConfig(List<ServerPlayer> teamPlayers, String teamName) {
        if (!isApiAvailable) {
            FPSMatch.LOGGER.debug("API不可用，跳过获取商店配置");
            return;
        }

        if (teamPlayers == null || teamPlayers.isEmpty()) {
            FPSMatch.LOGGER.warn("尝试获取商店配置，但队伍玩家列表为空。");
            return;
        }

        try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
            String url = buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getWeaponConfigure());

            HttpPost httpPost = new HttpPost(url);

            if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                httpPost.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
            } else {
                FPSMatch.LOGGER.error("API认证头未配置，商店配置请求可能会被拒绝(401)");
            }

            // 构建请求体
            List<String> playerIds = teamPlayers.stream().map(p -> p.getName().getString()).collect(Collectors.toList());
            ShopConfigRequest requestBody = new ShopConfigRequest(teamName, playerIds);
            String jsonData = gson.toJson(requestBody);
            StringEntity entity = new StringEntity(jsonData, "UTF-8");
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-Type", "application/json");

            FPSMatch.LOGGER.info("正在批量请求商店配置: team={}, players={}", teamName, playerIds);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode == 200) {
                    try {
                        ApiResponse<Map<String, ShopConfigData>> responseData = gson.fromJson(responseBody,
                                new TypeToken<ApiResponse<Map<String, ShopConfigData>>>() {
                                }.getType());

                        if (responseData != null && responseData.data != null) {
                            // 遍历返回的所有玩家配置
                            for (Map.Entry<String, ShopConfigData> entry : responseData.data.entrySet()) {
                                String playerName = entry.getKey();
                                ShopConfigData playerConfigData = entry.getValue();

                                // 找到对应的ServerPlayer对象
                                ServerPlayer currentPlayer = teamPlayers.stream()
                                        .filter(p -> p.getName().getString().equals(playerName))
                                        .findFirst().orElse(null);

                                if (currentPlayer != null && playerConfigData != null) {
                                    // 转换为内部使用的格式
                                    ShopConfigResponse config = new ShopConfigResponse();
                                    config.shopData = playerConfigData.shopData; // 直接使用从API获取的数据
                                    config.startKits = playerConfigData.startKits;

                                    // 为每个玩家缓存配置
                                    String cacheKey = currentPlayer.getUUID().toString() + "_" + teamName;
                                    PLAYER_SHOP_CONFIG_CACHE.put(cacheKey, config);
                                    FPSMatch.LOGGER.info("[商店配置] 已缓存玩家 {} 的配置", playerName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        FPSMatch.LOGGER.error("[商店配置] 解析批量响应失败 - 队伍={}, 错误={}", teamName, e.getMessage(), e);
                    }
                } else {
                    FPSMatch.LOGGER.error("获取商店配置失败: statusCode={}, response={}", statusCode, responseBody);
                }
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("批量获取商店配置时发生错误: ", e);
        }
    }

    /**
     * 清除指定玩家的商店配置缓存
     * @param player 玩家
     * @param teamName 队伍名称
     */
    public static void clearPlayerShopConfigCache(ServerPlayer player, String teamName) {
        String cacheKey = player.getUUID().toString() + "_" + teamName;
        PLAYER_SHOP_CONFIG_CACHE.remove(cacheKey);
        FPSMatch.LOGGER.info("[商店配置] 已清除玩家 {} 队伍 {} 的配置缓存",
                player.getName().getString(), teamName);
    }

    /**
     * 清除所有玩家的商店配置缓存
     */
    public static void clearAllShopConfigCache() {
        PLAYER_SHOP_CONFIG_CACHE.clear();
        FPSMatch.LOGGER.info("[商店配置] 已清除所有玩家的配置缓存");
    }

    /**
     * 清除指定队伍的所有玩家商店配置缓存
     * @param teamName 队伍名称
     */
    public static void clearTeamShopConfigCache(String teamName) {
        PLAYER_SHOP_CONFIG_CACHE.entrySet().removeIf(entry ->
                entry.getKey().endsWith("_" + teamName));
        FPSMatch.LOGGER.info("[商店配置] 已清除队伍 {} 的所有玩家配置缓存", teamName);
    }

    /**
     * 转换商店配置
     */
    public static Map<ItemType, ArrayList<ShopSlot>> convertShopConfig(ShopConfigResponse config) {
        try {
            Map<ItemType, ArrayList<ShopSlot>> result = new HashMap<>();

            config.shopData.forEach((type, items) -> {
                ArrayList<ShopSlot> slots = new ArrayList<>();

                items.forEach(item -> {
                    // 处理物品堆
                    ItemStack itemStack;
                    if (item.itemStack.id.equals("minecraft:air")) {
                        // 空槽位特殊处理
                        itemStack = new ItemStack(Items.AIR, 0);
                    } else {
                        itemStack = new ItemStack(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.itemStack.id)),
                                safeParseInt(item.itemStack.count)
                        );

                        // 处理 NBT
                        if (item.itemStack.tag != null) {
                            CompoundTag nbt = new CompoundTag();
                            Map<String, Object> tag = item.itemStack.tag;

                            // 转换 NBT 标签大小写
                            if (itemStack.getItem() instanceof IGun) {
                                addTagData(nbt, tag);
                            } else {
                                // 其他物品的 NBT
                                addTagData(nbt, tag);
                            }
                            itemStack.setTag(nbt);
                        }
                    }

                    // 创建商店槽位，使用安全的数值转换
                    ShopSlot slot = new ShopSlot(
                            () -> itemStack.copy(),
                            safeParseInt(item.defaultCost),
                            safeParseInt(item.maxBuyCount),
                            safeParseInt(item.groupId),
                            stack -> ItemStack.isSameItemSameTags(stack, itemStack)
                    );

                    slots.add(slot);
                });

                result.put(ItemType.valueOf(type), slots);
            });

            // 确保每个物品类型都有5个槽位，不足则用空槽位填充
            for (ItemType type : ItemType.values()) {
                ArrayList<ShopSlot> slots = result.computeIfAbsent(type, k -> new ArrayList<>());
                while (slots.size() < 5) {
                    slots.add(new ShopSlot(ItemStack.EMPTY, 0));
                }
            }

            return result;

        } catch (Exception e) {
            FPSMatch.LOGGER.error("转换商店配置时发生错误: ", e);
            return null;
        }
    }

    /**
     * 安全地将字符串转换为整数
     * 处理浮点数字符串和其他特殊情况
     */
    private static int safeParseInt(String value) {
        try {
            // 如果是浮点数字符串，先转换为 double 再取整
            if (value.contains(".")) {
                return (int) Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            FPSMatch.LOGGER.warn("数值转换失败，使用默认值0: {}", value);
            return 0;
        }
    }

    /**
     * 根据物品数据创建ItemStack
     */
    public static ItemStack createItemStack(ItemStackData itemStackData) {
        try {
            // 创建物品
            ResourceLocation resourceLocation = new ResourceLocation(itemStackData.id);
            Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
            if (item == null) {
                FPSMatch.LOGGER.error("[物品创建] 找不到物品: {}", itemStackData.id);
                return null;
            }

            // 创建ItemStack
            ItemStack itemStack = new ItemStack(item, Integer.parseInt(itemStackData.count));

            // 如果有NBT标签，添加到物品上
            if (itemStackData.tag != null) {
                CompoundTag nbt = new CompoundTag();
                addTagData(nbt, itemStackData.tag);
                itemStack.setTag(nbt);
            }

            return itemStack;
        } catch (Exception e) {
            FPSMatch.LOGGER.error("[物品创建] 失败 - id={}, error={}",
                    itemStackData.id, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 递归添加NBT标签数据
     */
    private static void addTagData(CompoundTag nbt, Map<String, Object> tagData) {
        for (Map.Entry<String, Object> entry : tagData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                nbt.putString(key, (String) value);
            } else if (value instanceof Number num) {
                if (value instanceof Double || value instanceof Float) {
                    nbt.putDouble(key, num.doubleValue());
                } else {
                    nbt.putLong(key, num.longValue());
                }
            } else if (value instanceof Boolean) {
                nbt.putBoolean(key, (Boolean) value);
            } else if (value instanceof Map) {
                // 处理嵌套的NBT数据
                CompoundTag compound = new CompoundTag();
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                addTagData(compound, mapValue);
                nbt.put(key, compound);
            }
        }
    }

    /**
     * 构建正确的API URL
     * 确保API端点和路径之间只有一个斜杠
     */
    private static String buildApiUrl(String endpoint, String path) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "";
        }

        // 确保endpoint以斜杠结尾
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/";

        // 确保path不以斜杠开头
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        // 根据您提供的正确URL示例可能需要特殊处理，例如如果不需要apiEndpoint和path之间的斜杠
        // 直接使用完整的URL
        if (path.startsWith("http")) {
            return path;
        }

        // 返回修正后的URL
        return normalizedEndpoint + normalizedPath;
    }

    /**
     * 检测API连接状态并返回详细信息
     * 可以从游戏内命令调用
     * @return API连接状态信息
     */
    public static String checkApiConnection() {
        return testApiConnection();
    }

    /**
     * 重新加载API配置并测试连接
     * @return 重载结果
     */
    public static String reloadApiConfig() {
        try {
            loadApiConfig();
            checkApiAvailability();
            return "API配置已重新加载，当前状态: " + (isApiAvailable ? "可用" : "不可用");
        } catch (Exception e) {
            return "API配置重载失败: " + e.getMessage();
        }
    }

    /**
     * 获取API可用性状态
     * @return 当前API是否可用
     */
    public static boolean isApiAvailable() {
        return isApiAvailable;
    }
}