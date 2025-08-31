package com.phasetranscrystal.fpsmatch.core.data;

import java.util.List;
import java.util.UUID;

/**
 * 回合数据结构类
 * 用于记录每个回合的完整信息，包括地图、队伍、玩家等数据
 */
public class RoundData {
    private String mapName;           // 地图名称
    private int roundNumber;          // 回合编号
    private String winnerTeam;        // 获胜队伍 ("ct" 或 "t")
    private String winnerReason;      // 胜利原因 ("TIME_OUT", "ACED", "DEFUSE_BOMB", "DETONATE_BOMB")
    private long roundDuration;       // 回合持续时间(毫秒)
    private long timestamp;           // 时间戳
    private String mvpPlayer;         // MVP玩家名称 (可选)
    private String mvpReason;         // MVP原因 (可选)
    private int ctScore;              // CT队伍得分
    private int tScore;               // T队伍得分
    private boolean bombPlanted;      // 是否放置炸弹
    private boolean bombExploded;     // 炸弹是否爆炸
    private boolean bombDefused;      // 炸弹是否被拆除
    private long matchId;           // 比赛ID
    private List<PlayerRoundData> playerData; // 玩家数据数组

    public RoundData() {}

    public RoundData(String mapName, int roundNumber, String winnerTeam, String winnerReason,
                     long roundDuration, long timestamp, String mvpPlayer, String mvpReason,
                     int ctScore, int tScore, boolean bombPlanted, boolean bombExploded,
                     boolean bombDefused, long matchId, List<PlayerRoundData> playerData) {
        this.mapName = mapName;
        this.roundNumber = roundNumber;
        this.winnerTeam = winnerTeam;
        this.winnerReason = winnerReason;
        this.roundDuration = roundDuration;
        this.timestamp = timestamp;
        this.mvpPlayer = mvpPlayer;
        this.mvpReason = mvpReason;
        this.ctScore = ctScore;
        this.tScore = tScore;
        this.bombPlanted = bombPlanted;
        this.bombExploded = bombExploded;
        this.bombDefused = bombDefused;
        this.matchId = matchId;
        this.playerData = playerData;
    }

    // Getters and Setters
    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(String winnerTeam) {
        this.winnerTeam = winnerTeam;
    }

    public String getWinnerReason() {
        return winnerReason;
    }

    public void setWinnerReason(String winnerReason) {
        this.winnerReason = winnerReason;
    }

    public long getRoundDuration() {
        return roundDuration;
    }

    public void setRoundDuration(long roundDuration) {
        this.roundDuration = roundDuration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMvpPlayer() {
        return mvpPlayer;
    }

    public void setMvpPlayer(String mvpPlayer) {
        this.mvpPlayer = mvpPlayer;
    }

    public String getMvpReason() {
        return mvpReason;
    }

    public void setMvpReason(String mvpReason) {
        this.mvpReason = mvpReason;
    }

    public int getCtScore() {
        return ctScore;
    }

    public void setCtScore(int ctScore) {
        this.ctScore = ctScore;
    }

    public int getTScore() {
        return tScore;
    }

    public void setTScore(int tScore) {
        this.tScore = tScore;
    }

    public boolean isBombPlanted() {
        return bombPlanted;
    }

    public void setBombPlanted(boolean bombPlanted) {
        this.bombPlanted = bombPlanted;
    }

    public boolean isBombExploded() {
        return bombExploded;
    }

    public void setBombExploded(boolean bombExploded) {
        this.bombExploded = bombExploded;
    }

    public boolean isBombDefused() {
        return bombDefused;
    }

    public void setBombDefused(boolean bombDefused) {
        this.bombDefused = bombDefused;
    }

    public List<PlayerRoundData> getPlayerData() {
        return playerData;
    }

    public void setPlayerData(List<PlayerRoundData> playerData) {
        this.playerData = playerData;
    }

    public long getMatchId() {
        return matchId;
    }

    public void setMatchId(long matchId) {
        this.matchId = matchId;
    }

    /**
     * 玩家回合数据内部类
     */
    public static class PlayerRoundData {
        private String uuid;          // 玩家UUID
        private String playerName;   // 玩家名称
        private String team;          // 所属队伍 ("ct" 或 "t")
        private int kills;            // 本回合击杀数
        private int deaths;           // 本回合死亡数
        private int assists;          // 本回合助攻数
        private int damage;           // 本回合伤害值
        private boolean isAlive;      // 回合结束时是否存活
        private int money;            // 当前金钱数量
        private long matchId;          // 比赛ID

        public PlayerRoundData() {}

        public PlayerRoundData(String uuid, String playerName, String team, int kills,
                               int deaths, int assists, int damage, boolean isAlive, int money, long matchId) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.team = team;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
            this.damage = damage;
            this.isAlive = isAlive;
            this.money = money;
            this.matchId = matchId;
        }

        // Getters and Setters
        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public String getTeam() {
            return team;
        }

        public void setTeam(String team) {
            this.team = team;
        }

        public int getKills() {
            return kills;
        }

        public void setKills(int kills) {
            this.kills = kills;
        }

        public int getDeaths() {
            return deaths;
        }

        public void setDeaths(int deaths) {
            this.deaths = deaths;
        }

        public int getAssists() {
            return assists;
        }

        public void setAssists(int assists) {
            this.assists = assists;
        }

        public int getDamage() {
            return damage;
        }

        public void setDamage(int damage) {
            this.damage = damage;
        }

        public boolean isAlive() {
            return isAlive;
        }

        public void setAlive(boolean alive) {
            isAlive = alive;
        }

        public int getMoney() {
            return money;
        }

        public void setMoney(int money) {
            this.money = money;
        }

        public long getMatchId() {
            return matchId;
        }

        public void setMatchId(long matchId) {
            this.matchId = matchId;
        }
    }
}