package com.phasetranscrystal.fpsmatch.mcgo.api;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.MapTeams;
import com.phasetranscrystal.fpsmatch.mcgo.config.APIConfig;
import com.phasetranscrystal.fpsmatch.mcgo.util.SSLUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.concurrent.CompletableFuture;

public class CsGameMatchApi {
    private static final Gson gson = new Gson();
    
    /**
     * 获取API端点
     * @return API端点地址
     */
    private static String getApiEndpoint() {
        APIConfig config = APIConfig.getInstance();
        String baseUrl = config.getApiEndpoint();
        String saveMatchPath = config.getSaveMatch();
        
        if (baseUrl != null && saveMatchPath != null) {
            return baseUrl + "/" + saveMatchPath;
        }
        return null;
    }

    /**
     * 发送游戏结果数据到服务器
     * 作为GameDataApiUtils的功能实现
     *
     * @param winnerTeam 获胜队伍对象
     * @param loserTeam 失败队伍对象
     * @param mapName 地图名称
     * @param gameType 游戏类型
     * @param totalRounds 总回合数
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @param totalPlayers 总玩家数
     * @param mapTeams 地图团队管理器
     * @param matchId 比赛ID
     */
    public static void sendGameResult(BaseTeam winnerTeam, BaseTeam loserTeam, String mapName,
                                      String gameType, int totalRounds, long gameStartTime,
                                      long gameEndTime, int totalPlayers, MapTeams mapTeams,
                                      long matchId, int roundCount) {
        // 异步发送，不阻塞游戏逻辑
        CompletableFuture.runAsync(() -> {
            try {
                APIConfig config = APIConfig.getInstance();
                if (config == null || config.getApiEndpoint() == null || config.getSaveMatch() == null) {
                    FPSMatch.LOGGER.warn("API配置不完整，跳过游戏结果数据发送");
                    return;
                }

                // 构建游戏结果数据
                GameResultData gameResultData = new GameResultData();
                gameResultData.matchId = matchId;
                gameResultData.mapName = mapName;
                gameResultData.gameType = gameType;
                gameResultData.roundCount = roundCount;
                gameResultData.totalRounds = totalRounds;
                gameResultData.gameStartTime = gameStartTime;
                gameResultData.gameEndTime = gameEndTime;
                gameResultData.totalPlayers = totalPlayers;
                gameResultData.winnerTeamName = winnerTeam.name;
                gameResultData.loserTeamName = loserTeam.name;
                gameResultData.winnerTeamScore = winnerTeam.getScores();
                gameResultData.loserTeamScore = loserTeam.getScores();

                // 收集玩家统计数据
                gameResultData.playerStats = mapTeams.getAllPlayerStats(winnerTeam.name, roundCount);

                // 发送数据
                sendGameResultAsync(gameResultData).join();

            } catch (Exception e) {
                FPSMatch.LOGGER.error("发送游戏结果数据时发生错误: ", e);
            }
        });
    }

    /**
     * 异步发送游戏结果数据到后端
     *
     * @param gameResultData 游戏结果数据
     * @return CompletableFuture
     */
    private static CompletableFuture<Void> sendGameResultAsync(GameResultData gameResultData) {
        return CompletableFuture.runAsync(() -> {
            try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
                APIConfig config = APIConfig.getInstance();
                String url = getApiEndpoint();
                
                if (url == null) {
                    FPSMatch.LOGGER.warn("API端点未配置，跳过游戏结果数据发送");
                    return;
                }

                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader(config.getApiAuthHeader(), config.getApiAuthValue());

                String jsonData = gson.toJson(gameResultData);
                httpPost.setEntity(new StringEntity(jsonData, "UTF-8"));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode == 200) {
                        FPSMatch.LOGGER.info("游戏结果数据发送成功: {}", responseBody);
                    } else {
                        FPSMatch.LOGGER.error("游戏结果数据发送失败，状态码: {}, 响应: {}", statusCode, responseBody);
                    }
                }
            } catch (Exception e) {
                FPSMatch.LOGGER.error("发送游戏结果数据时发生网络错误: ", e);
            }
        });
    }



    /**
     * 游戏结果数据类
     */
    public static class GameResultData {
        // 比赛基本信息
        public long matchId;              // 比赛唯一标识ID
        public String mapName;            // 地图名称
        public String gameType;           // 游戏类型
        public Integer roundCount;        // 当前回合数
        public Integer totalRounds;       // 总回合数
        public Long gameStartTime;        // 游戏开始时间（毫秒时间戳）
        public Long gameEndTime;          // 游戏结束时间（毫秒时间戳）
        public Integer totalPlayers;      // 总玩家数
        
        // 队伍信息
        public String winnerTeamName;     // 获胜队伍名称
        public String loserTeamName;      // 失败队伍名称
        public Integer winnerTeamScore;   // 获胜队伍分数
        public Integer loserTeamScore;    // 失败队伍分数
        
        // 玩家统计数据
        public java.util.List<PlayerStatsDTO> playerStats;
    }

    /**
     * 玩家统计数据类
     */
    public static class PlayerStatsDTO {
        public String playerUuid;         // 玩家UUID
        public String playerName;         // 玩家名称
        public String teamName;           // 所属队伍名称
        public Integer kills;             // 击杀数
        public Integer deaths;            // 死亡数
        public Integer assists;           // 助攻数
        public Integer headshotKills;     // 爆头击杀数
        public Integer mvpCount;          // MVP次数
        public Integer scores;            // 得分
        public Double totalDamage;        // 总伤害
        public Boolean isWinner;          // 是否为获胜队伍成员
        public Integer roundCount;        // 回合数
    }
}
