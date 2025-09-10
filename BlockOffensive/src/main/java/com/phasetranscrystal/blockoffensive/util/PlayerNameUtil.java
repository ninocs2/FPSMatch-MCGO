package com.phasetranscrystal.blockoffensive.util;

import com.phasetranscrystal.fpsmatch.mcgo.api.queryUserXtnInfoApi;
import net.minecraft.network.chat.Component;

/**
 * 玩家名称工具类
 * 提供统一的玩家名称获取和显示逻辑
 */
public class PlayerNameUtil {
    private static final queryUserXtnInfoApi apiService = new queryUserXtnInfoApi();
    
    /**
     * 获取玩家的用户名（优先从缓存读取）
     * @param playerName 玩家名称
     * @return 用户名，如果不存在则返回null
     */
    public static String getUserName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试从文件加载玩家数据
            java.util.Optional<queryUserXtnInfoApi.PlayerInfoResponse> playerData = 
                apiService.loadPlayerDataFromFile(java.util.List.of(playerName));
            
            if (playerData.isPresent()) {
                queryUserXtnInfoApi.PlayerInfo playerInfo = playerData.get().getPlayerInfo(playerName);
                if (playerInfo != null) {
                    return playerInfo.getUserNm();
                }
            }
        } catch (Exception e) {
            // 静默处理异常，不影响游戏体验
        }
        
        return null;
    }
    
    /**
     * 获取用于显示的玩家名称
     * @param playerName 原始玩家名称
     * @return 格式化后的显示名称，格式为 userNm(playerNm) 或原名称
     */
    public static String getDisplayName(String playerName) {
        String userNm = getUserName(playerName);
        
        if (userNm != null && !userNm.trim().isEmpty() && !userNm.equals("Unknown Player")) {
            // 显示格式：userNm(playerNm)
            return userNm + "(" + playerName + ")";
        }
        
        // 如果没有自定义名称，使用原来的名称
        return playerName;
    }
    
    /**
     * 获取玩家的MVP音乐名称
     * @param playerName 玩家名称
     * @return MVP音乐名称，如果不存在则返回默认值
     */
    public static String getMvpMusicName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return "默认MVP音乐";
        }
        
        try {
            // 尝试从文件加载玩家数据
            java.util.Optional<queryUserXtnInfoApi.PlayerInfoResponse> playerData = 
                apiService.loadPlayerDataFromFile(java.util.List.of(playerName));
            
            if (playerData.isPresent()) {
                queryUserXtnInfoApi.PlayerInfo playerInfo = playerData.get().getPlayerInfo(playerName);
                if (playerInfo != null && playerInfo.getXtnInfo() != null) {
                    String mvpMusicNm = playerInfo.getXtnInfo().getMvpMusicNm();
                    if (mvpMusicNm != null && !mvpMusicNm.trim().isEmpty()) {
                        return mvpMusicNm;
                    }
                }
            }
        } catch (Exception e) {
            // 静默处理异常，不影响游戏体验
        }
        
        return "默认MVP音乐";
    }
    
    /**
     * 获取用于显示的玩家名称组件（适用于Component类型的原始名称）
     * @param originalName 原始名称组件
     * @return 格式化后的显示名称组件
     */
    public static Component getDisplayNameComponent(Component originalName) {
        String playerName = originalName.getString();
        String displayName = getDisplayName(playerName);
        
        if (!displayName.equals(playerName)) {
            return Component.literal(displayName);
        }
        
        return originalName;
    }
}