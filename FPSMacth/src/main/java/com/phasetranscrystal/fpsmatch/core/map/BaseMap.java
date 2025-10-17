package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.GameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import com.phasetranscrystal.fpsmatch.mcgo.api.CsGameMatchApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

/**
 * BaseMap 抽象类，表示游戏中的基础地图。
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
    // 游戏开始时间
    protected long gameStartTime;
    // 游戏结束时间
    protected long gameEndTime;
    // 比赛ID
    protected long matchId;
    /**
     * BaseMap 类的构造函数。
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
     * 地图每个 tick 的操作
     */
    public final void mapTick() {
        checkForVictory();
        tick();
        syncToClient();
    }

    /**
     * 同步数据到客户端
     */
    public abstract void syncToClient();

    /**
     * 每个 tick 的操作
     */
    public void tick() {
    }

    /**
     * 检查胜利条件
     */
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    /**
     * 开始游戏
     */
    public abstract void startGame();

    /**
     * 检查玩家是否在游戏中
     *
     * @param player 玩家对象
     * @return 是否在游戏中
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
                .getJoinedUUID().contains(player);
    }

    public boolean checkSpecHasPlayer(Player player) {
        return this.getMapTeams().getSpecPlayers().contains(player.getUUID());
    }

    /**
     * 开始新一轮游戏
     */
    public void startNewRound() {
    }

    /**
     * 胜利操作
     */
    public abstract void victory();

    /**
     * 胜利条件
     *
     * @return 是否满足胜利条件
     */
    public abstract boolean victoryGoal();

    /**
     * 清理地图
     */
    public void cleanupMap() {
    }

    /**
     * 重置游戏
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
     * 加入团队
     * @param teamName 团队名称
     * @param player 玩家对象
     */
    public void join(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        this.pullGameInfo(player);
        this.getMapTeams().getTeamByName(teamName)
                .flatMap(team -> team.getPlayerData(player.getUUID()))
                .ifPresent(playerData -> this.sendPacketToAllPlayer(new GameTabStatsS2CPacket(player.getUUID(), playerData, teamName)));
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
     * @param packet 数据包对象
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
     * 发送数据包给加入游戏的玩家
     * @param player 玩家对象
     * @param packet 数据包对象
     * @param noCheck 是否跳过检查
     * @param <MSG> 数据包类型
     */
    public <MSG> void sendPacketToJoinedPlayer(@NotNull ServerPlayer player, MSG packet, boolean noCheck) {
        if (noCheck || this.checkGameHasPlayer(player)) {
            if (packet instanceof Packet<?> vanillaPacket) {
                player.connection.send(vanillaPacket);
            } else {
                NetworkPacketRegister.getChannelFromCache(packet.getClass()).send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        } else {
            FPSMatch.LOGGER.error("{} is not join {}:{}", player.getDisplayName().getString(), this.getGameType(), this.getMapName());
        }
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid){
        return FPSMCore.getInstance().getPlayerByUUID(uuid);
    }
    /**
     * 玩家登录事件处理
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map.isEmpty()) {
                if (!player.isCreative()) {
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
            }else{
                map.get().getMapTeams().getTeamByPlayer(player)
                        .flatMap(team -> team.getPlayerData(player.getUUID()))
                        .ifPresent(playerData -> {
                            playerData.setLiving(false);
                            player.setGameMode(GameType.SPECTATOR);
                        });
            }
        }
    }

    /**
     * 玩家登出事件处理
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FPSMCore.checkAndLeaveTeam(player);
        }
    }

    /**
     * 玩家受伤事件处理
     * @param event 玩家受伤事件
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event){
        if(event.getEntity() instanceof ServerPlayer hurt){
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(hurt);
            if(map.isPresent() && map.get().isStart) {
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
                    if(!attacker.getUUID().equals(hurt.getUUID())) map.get().getMapTeams().addHurtData(attacker, hurt.getUUID(), Math.min(hurt.getHealth(), event.getAmount()));
                }else{
                    if (attacker == null) return;
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map.isPresent()) {
                ServerPlayer attacker = null;
                if(event.getSource().getEntity() instanceof ServerPlayer sourcePlayer){
                    attacker = sourcePlayer;
                }else if(event.getSource().getDirectEntity() instanceof ServerPlayer sourcePlayer){
                    attacker = sourcePlayer;
                }

                if (attacker != null){
                    MinecraftForge.EVENT_BUS.post(new PlayerKillOnMapEvent(map.get(), player, attacker));
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
     * @param roundCount 当前回合数
     */
    public void sendGameResultData(BaseTeam winnerTeam, BaseTeam loserTeam, int totalRounds, int roundCount) {
        try {
            // 记录游戏结束时间
            this.gameEndTime = System.currentTimeMillis();

            // 调用API工具类发送游戏结果数据
            // 如果API不可用，CsGameMatchApi内部会自动跳过发送
            CsGameMatchApi.sendGameResult(
                    winnerTeam,          // 获胜队伍
                    loserTeam,           // 失败队伍
                    this.getMapName(),   // 地图名称
                    this.getGameType(),  // 游戏类型
                    totalRounds,         // 总回合数
                    this.gameStartTime,  // 游戏开始时间
                    this.gameEndTime,    // 游戏结束时间
                    this.getMapTeams().getJoinedPlayers().size(),  // 总玩家数
                    this.getMapTeams(),   // 地图团队管理器
                    this.matchId,        // 比赛ID
                    roundCount           // 当前回合数
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
        this.gameStartTime = startTime;
    }

    /**
     * 获取比赛ID
     * @return 比赛ID
     */
    public long getMatchId() {
        return matchId;
    }

    /**
     * 设置比赛ID
     * @param matchId 比赛ID
     */
    public void setMatchId(long matchId) {
        this.matchId = matchId;
    }

}