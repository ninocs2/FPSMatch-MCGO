package com.phasetranscrystal.fpsmatch.client.data;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.client.screen.hud.CSGameHud;
import com.phasetranscrystal.fpsmatch.client.screen.hud.MVPHud;
import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客户端数据管理类
 * 存储和管理所有与游戏状态相关的客户端数据
 *
 * 主要功能：
 * 1. 游戏状态管理（回合、暂停、等待等）
 * 2. 玩家数据管理（金钱、队伍等）
 * 3. 计分板数据
 * 4. 商店系统数据
 *
 * 设计模式：
 * - 单例模式（静态类）
 * - 观察者模式（通过事件系统更新UI）
 */
public class ClientData {
    public static String currentMap = "fpsm_none";
    public static String currentGameType = "none";
    public static String currentTeam = "ct";
    public static boolean currentMapSupportShop = true;
    public static final Map<ItemType, List<ClientShopSlot>> clientShopData = getDefaultShopSlotData();
    public static final Map<UUID, Pair<String,TabData>> tabData = new HashMap<>();
    public static final Map<UUID,Integer> playerMoney = new HashMap<>();
    public static int cTWinnerRounds = 0;
    public static int tWinnerRounds = 0;
    public static int time = 0;
    public static boolean isDebug = false;
    public static boolean isStart = false;
    public static boolean isError = false;
    public static boolean isPause = false;
    public static boolean isWaiting = false;
    public static boolean isWarmTime = false;
    public static boolean isWaitingWinner = false;
    public static boolean canOpenShop = false;
    public static int shopCloseTime = 0;
    public static int nextRoundMoney = 0;
    public static float dismantleBombProgress = 0;
    public static boolean customTab = true;

    public static int getMoney(){
        return playerMoney.getOrDefault(Minecraft.getInstance().player.getUUID(),0);
    }
    public static Map<ItemType, List<ClientShopSlot>> getDefaultShopSlotData(){
        Map<ItemType, List<ClientShopSlot>> data = new HashMap<>();
        for (ItemType type : ItemType.values()) {
            List<ClientShopSlot> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(ClientShopSlot.getDefault());
            }
            data.put(type, list);
        }

        return data;
    }

    public static void resetShopData(){
        clientShopData.clear();
        clientShopData.putAll(getDefaultShopSlotData());
    }

    public static ClientShopSlot getSlotData(ItemType type,int index){
        return clientShopData.get(type).get(index);
    }

    public static void handleLoginMessage(){
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if(FPSMConfig.client.showLogin.get()){
                player.displayClientMessage(Component.translatable("fpsm.screen.scale.warm").withStyle(ChatFormatting.GRAY), false);
                player.displayClientMessage(Component.translatable("fpsm.screen.scale.warm.tips").withStyle(ChatFormatting.GRAY), false);
                player.displayClientMessage(Component.translatable("fpsm.login.message.closeable").withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    public static void removePlayerFromTab(UUID uuid){
        tabData.remove(uuid);
    }

    /**
     * 重置所有游戏数据到初始状态
     * 在游戏结束或切换地图时调用
     */
    public static void reset() {
        MVPHud.INSTANCE.resetAnimation();
        currentMap = "fpsm_none";
        currentGameType = "none";
        currentMapSupportShop = true;
        CSGameHud.INSTANCE.stopKillAnim();
        resetShopData();
        tabData.clear();
        playerMoney.clear();
        cTWinnerRounds = 0;
        tWinnerRounds = 0;
        time = 0;
        isDebug = false;
        isStart = false;
        isError = false;
        isPause = false;
        isWaiting = false;
        isWarmTime = false;
        isWaitingWinner = false;
        nextRoundMoney = 0;
        canOpenShop = false;
        dismantleBombProgress = 0;
    }

    public static int getNextRoundMinMoney() {
        return nextRoundMoney;
    }

    @Nullable
    public static TabData getTabDataByUUID(UUID uuid){
        if(ClientData.tabData.containsKey(uuid)){
            return ClientData.tabData.get(uuid).getSecond();
        }
        return null;
    }

    /**
     * 根据UUID获取玩家的队伍信息
     * @param uuid 玩家UUID
     * @return 玩家所在队伍，如果不存在返回null
     */
    @Nullable
    public static String getTeamByUUID(UUID uuid){
        if(ClientData.tabData.containsKey(uuid)){
            return ClientData.tabData.get(uuid).getFirst();
        }
        return null;
    }

    /**
     * 获取指定队伍的存活人数
     * @param team 队伍名称（"ct"或"t"）
     * @return 该队伍的存活人数
     */
    public static int getLivingWithTeam(String team){
        AtomicReference<Integer> living = new AtomicReference<>(0);
        ClientData.tabData.values().forEach((pair)->{
            if(pair.getFirst().equals(team) && pair.getSecond().isLiving()){
                living.getAndSet(living.get() + 1);
            }
        });
        return living.get();
    }

    /**
     * 获取指定队伍的所有玩家信息
     * @param team 队伍名称 ("ct" 或 "t")
     * @return 该队伍所有玩家的UUID和TabData对的列表
     */
    public static List<Pair<UUID, TabData>> getTeamPlayers(String team) {
        List<Pair<UUID, TabData>> teamPlayers = new ArrayList<>();

        tabData.forEach((uuid, pair) -> {
            if (pair.getFirst().equals(team)) {
                teamPlayers.add(Pair.of(uuid, pair.getSecond()));
            }
        });

        return teamPlayers;
    }

    // 获取当前玩家的队伍，确保不为null
    public static String getCurrentTeam() {
        return currentTeam != null ? currentTeam : "";
    }

    // 获取玩家的TabData
    public static TabData getPlayerTabData(UUID uuid) {
        for (Map.Entry<UUID, Pair<String, TabData>> entry : tabData.entrySet()) {
            if (entry.getKey().equals(uuid)) {
                return entry.getValue().getSecond();
            }
        }
        return null;
    }
}
