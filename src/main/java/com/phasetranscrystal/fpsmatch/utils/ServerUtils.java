package com.phasetranscrystal.fpsmatch.utils;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务器工具类
 * 主要用于处理在线玩家列表的获取和SSL证书配置
 */
public class ServerUtils {
    // 创建类专用的日志记录器
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerUtils.class);

    /**
     * 玩家信息类
     * 用于存储玩家的基本信息
     */
    public static class PlayerInfo {
        private final String name;
        private final String uuid;

        public PlayerInfo(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }

        @Override
        public String toString() {
            return String.format("PlayerInfo{name='%s', uuid='%s'}", name, uuid);
        }
    }

    /**
     * 静态初始化块
     * 在类加载时执行一次，初始化SSL配置
     */
    static {
        // 初始化SSL配置，确保HTTPS请求能够正常工作
        SSLUtils.initGlobalSSL();
    }

    /**
     * 获取所有在线玩家的详细信息
     * 包含玩家名称和UUID
     * 
     * @return 包含所有在线玩家详细信息的Map，key为玩家名称，value为PlayerInfo对象
     */
    public static Map<String, PlayerInfo> getAllOnlinePlayersInfo() {
        try {
            Map<String, PlayerInfo> playersInfo = new HashMap<>();
            
            // 获取Minecraft客户端实例
            Minecraft mc = Minecraft.getInstance();
            // 检查客户端和网络连接是否可用
            if (mc != null && mc.getConnection() != null) {
                // 获取所有在线玩家的网络信息
                Collection<net.minecraft.client.multiplayer.PlayerInfo> playerInfos = mc.getConnection().getOnlinePlayers();
                // 遍历每个玩家信息
                for (net.minecraft.client.multiplayer.PlayerInfo info : playerInfos) {
                    String playerName = info.getProfile().getName();
                    String uuid = info.getProfile().getId().toString();
                    playersInfo.put(playerName, new PlayerInfo(playerName, uuid));
                    // 记录调试信息
                    LOGGER.debug("Found player: {} with UUID: {}", playerName, uuid);
                }
            }

            // 记录找到的玩家信息
            if (!playersInfo.isEmpty()) {
                LOGGER.info("Found {} online players with UUIDs", playersInfo.size());
            } else {
                LOGGER.info("No online players found");
            }

            return playersInfo;
        } catch (Exception e) {
            // 记录错误并返回空Map
            LOGGER.error("Error getting online players info", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取所有在线玩家名称
     * 为保持向后兼容性保留此方法
     * 
     * @return 包含所有在线玩家名称的集合
     */
    public static Collection<String> getAllOnlinePlayers() {
        return getAllOnlinePlayersInfo().keySet();
    }

    /**
     * 检查当前是否在客户端环境
     * 使用Forge的EffectiveSide工具类判断
     * 
     * @return true 如果在客户端环境，false 如果在服务端环境
     */
    public static boolean isClientSide() {
        return EffectiveSide.get() == LogicalSide.CLIENT;
    }

} 