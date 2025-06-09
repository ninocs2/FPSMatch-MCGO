package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.net.CSGameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchStatsResetS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.Date;
import java.util.Objects;

/**
 * BaseMap 抽象类，作为所有游戏地图的基类
 * 提供了地图的基本功能和事件处理
 */
public abstract class BaseMap {
    // 地图名称
    public final String mapName;
    // 游戏是否开始
    public boolean isStart = false;
    // 是否处于调试模式
    private boolean isDebug = false;
    // 服务器世界
    private final ServerLevel serverLevel;
    // 地图团队
    private final MapTeams mapTeams;
    // 地图区域数据
    public final AreaData mapArea;
    // 添加时间记录字段
    private long gameStartTime;
    private long gameEndTime;
    // 添加比赛ID字段
    private String matchId;

    /**
     * BaseMap 类的构造函数
     * 初始化地图的基本属性和团队管理器
     *
     * @param serverLevel 地图所在世界。
     * @param mapName 地图名称。
     * @param areaData 地图的区域数据。
     */
    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.mapArea = areaData;
        this.mapTeams = new MapTeams(serverLevel, this);
    }

    /**
     * 添加团队
     * @param teamName 团队名称
     * @param playerLimit 玩家限制
     */
    public BaseTeam addTeam(String teamName, int playerLimit) {
        return this.mapTeams.addTeam(teamName, playerLimit);
    }

    public BaseTeam getSpectatorTeam(){
        return this.mapTeams.getSpectatorTeam();
    }

    /**
     * 地图的主要tick循环
     * 每个游戏tick都会执行一次
     * 检查胜利条件、执行地图特定的tick逻辑、同步数据到客户端
     */
    public final void mapTick() {
        checkForVictory();
        tick();
        syncToClient();
    }

    /**
     * 同步地图数据到客户端
     * 由子类实现具体的同步逻辑
     */
    public abstract void syncToClient();

    /**
     * 地图特定的tick逻辑
     * 由子类实现具体的tick操作
     */
    public void tick() {
    }

    /**
     * 检查胜利条件
     * 如果满足胜利条件则触发胜利逻辑
     */
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    /**
     * 开始游戏
     * 由子类实现具体的游戏开始逻辑
     */
    public abstract void startGame();

    /**
     * 检查指定玩家是否在当前游戏中
     * @param player 要检查的玩家
     * @return 如果玩家在游戏中返回true
     */
    public boolean checkGameHasPlayer(Player player) {
        return this.checkGameHasPlayer(player.getUUID());
    }

    /**
     * 检查玩家是否在游戏中
     *
     * @param player 玩家对象
     * @return 是否在游戏中
     */
    public boolean checkGameHasPlayer(UUID player) {
        return this.getMapTeams()
                .getJoinedPlayers()
                .stream()
                .anyMatch(playerData -> playerData.getOwner().equals(player));
    }

    public boolean checkSpecHasPlayer(Player player) {
        return this.getMapTeams().getSpecPlayers().contains(player.getUUID());
    }

    /**
     * 开始新一轮游戏
     * 可由子类重写以实现具体的新一轮逻辑
     */
    public void startNewRound() {
    }

    /**
     * 胜利处理逻辑
     * 由子类实现具体的胜利处理
     */
    public abstract void victory();

    /**
     * 检查胜利条件
     * 由子类实现具体的胜利条件判断
     * @return 如果满足胜利条件返回true
     */
    public abstract boolean victoryGoal();

    /**
     * 清理地图
     * 可由子类重写以实现具体的清理逻辑
     */
    public void cleanupMap() {
    }

    /**
     * 重置游戏
     * 由子类实现具体的重置逻辑
     */
    public abstract void resetGame();

    /**
     * 获取地图团队
     * @return 地图团队对象
     */
    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public void leave(ServerPlayer player) {
        this.sendPacketToJoinedPlayer(player,new FPSMatchStatsResetS2CPacket(),true);
        player.setGameMode(GameType.ADVENTURE);
        this.getMapTeams().leaveTeam(player);
    }


    public void join(ServerPlayer player) {
        MapTeams mapTeams = this.getMapTeams();
        List<BaseTeam> baseTeams = mapTeams.getTeams();
        if(baseTeams.isEmpty()) return;
        BaseTeam team = baseTeams.stream().min(Comparator.comparingInt(BaseTeam::getPlayerCount)).orElse(baseTeams.stream().toList().get(new Random().nextInt(0,baseTeams.size())));
        this.join(team.name, player);
    }

    /**
     * 玩家加入队伍的处理逻辑
     * 包括离开原有队伍、同步游戏类型、更新记分板等
     *
     * @param teamName 要加入的队伍名称
     * @param player 要加入的玩家
     */
    public void join(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        this.pullGameInfo(player);
        this.getMapTeams().getTeamByName(teamName)
                .flatMap(team -> team.getPlayerData(player.getUUID()))
                .ifPresent(playerData -> this.sendPacketToAllPlayer(new CSGameTabStatsS2CPacket(player.getUUID(), playerData, teamName)));
        this.getMapTeams().joinTeam(teamName, player);
        if (this instanceof ShopMap<?> shopMap && !teamName.equals("spectator")) {
            shopMap.getShop(player).ifPresent(shop -> shop.syncShopData(player));
        }
    }

    public void joinSpec(ServerPlayer player){
        FPSMCore.checkAndLeaveTeam(player);
        player.setGameMode(GameType.SPECTATOR);
        this.pullGameInfo(player);
        this.getMapTeams().getSpectatorTeam().join(player);
        this.getMapTeams().getSpectatorTeam().getSpawnPointsData().stream().findAny().ifPresent(data -> this.teleportToPoint(player, data));
    }


    public void teleportPlayerToReSpawnPoint(ServerPlayer player){
        this.getMapTeams().getTeamByPlayer(player)
                .flatMap(t -> t.getPlayerData(player.getUUID()))
                .ifPresent(playerData -> {
                    SpawnPointData data = playerData.getSpawnPointsData();
                    player.setRespawnPosition(data.getDimension(),data.getPosition(),0,true,false);
                    teleportToPoint(player, data);
        });

    }

    public void teleportToPoint(ServerPlayer player, SpawnPointData data) {
        BlockPos pos = data.getPosition();
        if(!Level.isInSpawnableBounds(pos)) return;
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        set.add(RelativeMovement.X_ROT);
        set.add(RelativeMovement.Y_ROT);
        if (player.teleportTo(Objects.requireNonNull(this.getServerLevel().getServer().getLevel(data.getDimension())), pos.getX(),pos.getY(),pos.getZ(), set, 0, 0)) {
            label23: {
                if (player.isFallFlying()) {
                    break label23;
                }

                player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                player.setOnGround(true);
            }
        }
    }

    public void clearPlayerInventory(UUID uuid, Predicate<ItemStack> inventoryPredicate){
        Player player = this.getServerLevel().getPlayerByUUID(uuid);
        if(player instanceof ServerPlayer serverPlayer){
            this.clearPlayerInventory(serverPlayer,inventoryPredicate);
        }
    }

    public void clearPlayerInventory(ServerPlayer player, Predicate<ItemStack> predicate){
        player.getInventory().clearOrCountMatchingItems(predicate, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void clearPlayerInventory(ServerPlayer player){
        player.getInventory().clearOrCountMatchingItems((p_180029_) -> true, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    /**
     * 获取服务器世界
     * @return 服务器世界对象
     */
    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    /**
     * 是否处于调试模式
     * @return 是否处于调试模式
     */
    public boolean isDebug() {
        return isDebug;
    }

    /**
     * 切换调试模式
     * @return 切换后的调试模式状态
     */
    public boolean switchDebugMode() {
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    /**
     * 获取地图名称
     * @return 地图名称
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * 获取游戏类型
     * @return 游戏类型
     */
    public abstract String getGameType();


    /**
     * 重新加载地图逻辑
     * */
    //TODO WIP
    public void reload(){}

    /**
     * 比较两张地图是否相等
     * @param object 比较对象
     * @return 是否相等
     */
    public boolean equals(Object object) {
        if (object instanceof BaseMap map) {
            return map.getMapName().equals(this.getMapName()) && map.getGameType().equals(this.getGameType());
        } else {
            return false;
        }
    }

    /**
     * 获取地图区域数据
     * @return 地图区域数据对象
     */
    public AreaData getMapArea() {
        return mapArea;
    }

    /**
     * 发送数据包给所有玩家
     * 遍历所有已加入游戏的玩家并发送数据包
     *
     * @param packet 要发送的数据包
     * @param <MSG> 数据包类型
     */
    public <MSG> void sendPacketToAllPlayer(MSG packet) {
        this.getMapTeams().getJoinedPlayersWithSpec().forEach(uuid ->
            this.getPlayerByUUID(uuid).ifPresent(player ->
                this.sendPacketToJoinedPlayer(player, packet, true)
            )
        );
    }

    public <MSG> void sendPacketToTeamPlayer(BaseTeam team ,MSG packet,boolean living){
        team.getPlayersData().forEach(data ->
            data.getPlayer().ifPresent(player->{
                if (data.isLiving() == living) {
                    this.sendPacketToJoinedPlayer(player, packet, true);
                }
            })
        );
    }

    public <MSG> void sendPacketToTeamLivingPlayer(BaseTeam team ,MSG packet){
        this.sendPacketToTeamPlayer(team,packet,true);
    }

    /**
     * 发送数据包给指定的已加入游戏的玩家
     *
     * @param player 目标玩家
     * @param packet 要发送的数据包
     * @param noCheck 是否跳过玩家在游戏中的检查
     * @param <MSG> 数据包类型
     */
    public <MSG> void sendPacketToJoinedPlayer(@NotNull ServerPlayer player, MSG packet, boolean noCheck) {
        if (noCheck || this.checkGameHasPlayer(player)) {
            if (packet instanceof Packet<?> vanillaPacket) {
                player.connection.send(vanillaPacket);
            } else {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        } else {
            FPSMatch.LOGGER.error("{} is not join {}:{}", player.getDisplayName().getString(), this.getGameType(), this.getMapName());
        }
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid){
        return FPSMCore.getInstance().getPlayerByUUID(uuid);
    }
    /**
     * 玩家登录事件处理器
     * 处理玩家重新连接游戏时的状态恢复
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map == null) {
                if (!player.isCreative()) {
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
            }else{
                map.getMapTeams().getTeamByPlayer(player)
                        .flatMap(team -> team.getPlayerData(player.getUUID()))
                        .ifPresent(playerData -> {
                            playerData.setLiving(false);
                            player.setGameMode(GameType.SPECTATOR);
                        });
            }
        }
    }

    /**
     * 玩家登出事件处理器
     * 处理玩家断开连接时的队伍清理
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FPSMCore.checkAndLeaveTeam(player);
        }
    }

    /**
     * 生物受伤事件处理器
     * 处理玩家之间的伤害数据记录
     *
     * @param event 生物受伤事件
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event){
        if(event.getEntity() instanceof ServerPlayer hurt){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(hurt);
            if(map != null && map.isStart) {
                DamageSource source = event.getSource();
                ServerPlayer attacker;
                if (source.getEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                } else if (source.getDirectEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                } else {
                    attacker = null;
                }

                boolean flag = attacker != null && !attacker.isDeadOrDying();

                if (flag) {
                    if(!attacker.getUUID().equals(hurt.getUUID())) map.getMapTeams().addHurtData(attacker, hurt.getUUID(), Math.min(hurt.getHealth(), event.getAmount()));
                }else{
                    if (attacker == null) return;
                    event.setCanceled(true);
                }
            }
        }
    }

    public void pullGameInfo(ServerPlayer player){
        this.sendPacketToJoinedPlayer(player,new FPSMatchGameTypeS2CPacket(this.getMapName(), this.getGameType()),true);
    }


    /**
     * 发送游戏结果数据到服务器
     * 仅在服务端调用，子类在victory()方法中调用
     *
     * @param winnerTeam 获胜队伍对象
     * @param loserTeam 失败队伍对象
     * @param totalRounds 总回合数
     */
    protected void sendGameResultData(BaseTeam winnerTeam, BaseTeam loserTeam, int totalRounds) {
        // 确保只在专用服务器上执行
        if (!this.serverLevel.getServer().isDedicatedServer()) {
            return;
        }

        try {
            // 记录游戏结束时间
            this.gameEndTime = System.currentTimeMillis();

            // 调用API工具类发送游戏结果数据
            // 如果API不可用，GameDataApiUtils内部会自动跳过发送
            GameDataApiUtils.sendGameResult(
                    winnerTeam,          // 获胜队伍
                    loserTeam,           // 失败队伍
                    this.getMapName(),   // 地图名称
                    this.getGameType(),  // 游戏类型
                    totalRounds,         // 总回合数
                    this.gameStartTime,  // 游戏开始时间
                    this.gameEndTime,    // 游戏结束时间
                    this.getMapTeams().getJoinedPlayers().size(),  // 总玩家数
                    this.getMapTeams(),   // 地图团队管理器
                    this.matchId   // 比赛ID
            );
        } catch (Exception e) {
            // 记录错误但不影响游戏逻辑
            FPSMatch.LOGGER.error("发送游戏结果数据时发生错误: ", e);
        }
    }

    /**
     * 设置游戏开始时间
     * 仅在专用服务器端调用
     * @param startTime 开始时间戳(毫秒)
     */
    protected void setGameStartTime(long startTime) {
        // 确保只在专用服务器上执行
        if (!this.serverLevel.getServer().isDedicatedServer()) {
            return;
        }
        this.gameStartTime = startTime;
    }

    /**
     * 获取游戏开始时间
     * @return 开始时间戳(毫秒)
     */
    protected long getGameStartTime() {
        return this.gameStartTime;
    }

    // 添加getter和setter
    protected void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    protected String getMatchId() {
        return this.matchId;
    }
}