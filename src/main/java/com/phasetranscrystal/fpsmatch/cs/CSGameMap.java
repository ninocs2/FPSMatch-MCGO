package com.phasetranscrystal.fpsmatch.cs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.*;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.data.*;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.event.CSGameRoundEndEvent;
import com.phasetranscrystal.fpsmatch.core.event.GameWinnerEvent;
import com.phasetranscrystal.fpsmatch.core.event.PlayerGetMvpEvent;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.*;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopData;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.core.sound.MVPMusicManager;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import com.phasetranscrystal.fpsmatch.entity.drop.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.item.BombDisposalKit;
import com.phasetranscrystal.fpsmatch.item.CompositionC4;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.mcgo.utils.GameEndUtils;
import com.phasetranscrystal.fpsmatch.net.*;
import com.phasetranscrystal.fpsmatch.entity.drop.DropType;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 反恐精英（CS）模式地图核心逻辑类
 * 管理回合制战斗、炸弹逻辑、商店系统、队伍经济、玩家装备等核心机制
 * 继承自 BaseMap 并实现爆炸模式、商店、初始装备等接口
 */
@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CSGameMap extends BaseMap implements BlastModeMap<CSGameMap> , ShopMap<CSGameMap> , GiveStartKitsMap<CSGameMap>, IConfigureMap<CSGameMap> {
    private static final Map<String, BiConsumer<CSGameMap,ServerPlayer>> COMMANDS = registerCommands();
    private static final Map<String, Consumer<CSGameMap>> VOTE_ACTION = registerVoteAction();
    private final ArrayList<Setting<?>> settings = new ArrayList<>();
    private final Setting<Boolean> canAutoStart = this.addSetting("autoStart",true);
    private final Setting<Integer> autoStartTime = this.addSetting("autoStartTime",6000);
    private final Setting<Integer> winnerRound = this.addSetting("winnerRound",13); // 13回合
    private final Setting<Integer> pauseTime = this.addSetting("pauseTime",1200); // 60秒
    private final Setting<Integer> winnerWaitingTime = this.addSetting("winnerWaitingTime",160);
    private final Setting<Integer> warmUpTime = this.addSetting("warmUpTime",1200);
    private final Setting<Integer> waitingTime = this.addSetting("waitingTime",300);
    private final Setting<Integer> roundTimeLimit = this.addSetting("roundTimeLimit",2300);
    private final Setting<Integer> startMoney = this.addSetting("startMoney",800);
    private final Setting<Integer> closeShopTime = this.addSetting("closeShopTime",200);
    private int currentPauseTime = 0;
    private int currentRoundTime = 0;
    private boolean isError = false;
    private boolean isPause = false;
    private boolean isWaiting = false;
    private boolean isWarmTime = false;
    private boolean isWaitingWinner = false;
    private boolean isShopLocked = false;
    private int isBlasting = 0; // 是否放置炸弹 0 = 未放置 | 1 = 已放置 | 2 = 已拆除
    private boolean isExploded = false; // 炸弹是否爆炸
    private final List<AreaData> bombAreaData = new ArrayList<>();
    private String blastTeam;
    private final Map<String,FPSMShop> shop = new HashMap<>();
    private final Map<String,List<ItemStack>> startKits = new HashMap<>();
    private boolean isOvertime = false;
    private int overCount = 0;
    private boolean isWaitingOverTimeVote = false;
    private VoteObj voteObj = null;
    private SpawnPointData matchEndTeleportPoint = null;
    private int autoStartTimer = 0;
    private boolean autoStartFirstMessageFlag = false;
    private final BaseTeam ctTeam;
    private final BaseTeam tTeam;
    private UUID c4EntityUUID = null;
    // 格式化时间为 yyyyMMddHHmmss
    private static final DateTimeFormatter MATCH_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 构造函数：创建CS地图实例
     * @param serverLevel 服务器世界实例
     * @param mapName 地图名称
     * @param areaData 地图区域数据
     * @see #addTeam(String, int) 初始化时自动添加CT和T阵营
     */
    public CSGameMap(ServerLevel serverLevel,String mapName,AreaData areaData) {
        super(serverLevel,mapName,areaData);
        this.loadConfig();
        this.ctTeam = this.addTeam("ct",5);
        this.tTeam = this.addTeam("t",5);
        this.setBlastTeam(this.tTeam);
    }

    /**
     * 添加队伍并初始化商店系统
     * @param teamName 队伍名称（如"ct"/"t"）
     * @param playerLimit 队伍人数限制
     * @see FPSMShop 每个队伍拥有独立商店实例
     */
    @Override
    public BaseTeam addTeam(String teamName,int playerLimit){
        BaseTeam team = super.addTeam(teamName,playerLimit);
        PlayerTeam playerTeam = team.getPlayerTeam();
        playerTeam.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        playerTeam.setAllowFriendlyFire(false);
        playerTeam.setSeeFriendlyInvisibles(false);
        playerTeam.setDeathMessageVisibility(Team.Visibility.NEVER);
        this.shop.put(teamName, new FPSMShop(this.getMapName(), startMoney.get()));
        return team;
    }

    public void startVote(String title,Component message,int second,float playerPercent){
        if(this.voteObj == null){
            this.voteObj = new VoteObj(title,message,second,playerPercent);
            this.sendAllPlayerMessage(message,false);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.help").withStyle(ChatFormatting.GREEN),false);
        }
    }

    public void setCanAutoStart(boolean canAutoStart){
        this.canAutoStart.set(canAutoStart);
    }

    public boolean canStartAuto(){
        return this.canAutoStart.get();
    }

    /**
     * 获取队伍商店实例
     * @param shopName 队伍名称（ct/t）
     * @return 对应队伍的商店对象
     * @see FPSMShop 商店数据结构
     */
    @Override
    public Optional<FPSMShop> getShop(String shopName) {
        return Optional.ofNullable(this.shop.get(shopName));
    }

    @Override
    public Optional<FPSMShop> getShop(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        Optional<BaseTeam> team = this.getMapTeams().getTeamByPlayer(player);
        if (team.isPresent()) {
            return this.getShop(team.get().name);
        }

        return Optional.empty();
    }

    @Override
    public List<FPSMShop> getShops() {
        return this.shop.values().stream().toList();
    }

    @Override
    public List<String> getShopNames() {
        return this.shop.keySet().stream().toList();
    }

    public void syncShopInfo(boolean enable, int closeTime){
        for (BaseTeam team : this.getMapTeams().getTeams()){
            int next = this.getNextRoundMinMoney(team);
            var packet = new ShopStatesS2CPacket(enable,next,closeTime);
            this.sendPacketToTeamLivingPlayer(team,packet);
        }
    }

    public int getNextRoundMinMoney(BaseTeam team){
        int defaultEconomy = 1400;
        int compensation = 500;
        int compensationFactor = Math.min(4, team.getCompensationFactor() + 1);
        // 计算失败补偿
        return defaultEconomy + compensation * compensationFactor;
    }

    /**
     * 游戏主循环逻辑（每tick执行）
     * 管理暂停状态、回合时间、胜利条件检查等核心流程
     * @see #checkRoundVictory() 检查回合胜利条件
     * @see #checkBlastingVictory() 检查炸弹爆炸胜利
     * @see #startNewRound() 启动新回合
     */
    @Override
    public void tick() {
        if(isStart && !checkPauseTime()){
            // 暂停 / 热身 / 回合开始前的等待时间
            if (!checkWarmUpTime() & !checkWaitingTime()) {
                if(!isRoundTimeEnd()){
                    if(!this.isDebug()){
                        boolean flag = this.getMapTeams().getJoinedPlayers().size() != 1;
                        switch (this.isBlasting()){
                            case 1 : this.checkBlastingVictory(); break;
                            case 2 : if(!isWaitingWinner) this.roundVictory(this.getCTTeam(),WinnerReason.DEFUSE_BOMB); break;
                            default : if(flag) this.checkRoundVictory(); break;
                        }

                        // 回合结束等待时间
                        if(this.isWaitingWinner){
                            checkWinnerTime();

                            if(this.currentPauseTime >= winnerWaitingTime.get()){
                                this.startNewRound();
                            }
                        }
                    }
                }else{
                    if(!checkWinnerTime()){
                        this.roundVictory(this.getCTTeam(),WinnerReason.TIME_OUT);
                    }else if(this.currentPauseTime >= winnerWaitingTime.get()){
                        this.startNewRound();
                    }
                }
            }
        }
        this.checkErrorPlayerTeam();
        this.voteLogic();
        this.autoStartLogic();
    }

    private void autoStartLogic(){
        if (!canAutoStart.get()) return;

        if(isStart) {
            autoStartTimer = 0;
            autoStartFirstMessageFlag = false;
            return;
        }

        List<BaseTeam> teams = this.getMapTeams().getTeams();
        if(!teams.get(0).getPlayerList().isEmpty() && !teams.get(1).getPlayerList().isEmpty()){
            autoStartTimer++;
            if(!autoStartFirstMessageFlag){
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.auto.start.message", autoStartTime.get() / 20).withStyle(ChatFormatting.YELLOW),false);
                autoStartFirstMessageFlag = true;
            }
        } else {
            autoStartTimer = 0;
        }

        if(this.autoStartTimer > 0){
            if ((autoStartTimer >= 600 && autoStartTimer % 200 == 0) || (autoStartTimer >= 1000 && autoStartTimer <= 1180 && autoStartTimer % 20 == 0)) {
                this.getMapTeams().getJoinedPlayers().forEach((data -> {
                    data.getPlayer().ifPresent(serverPlayer -> {
                        serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.auto.start.title", (autoStartTime.get() - autoStartTimer) / 20).withStyle(ChatFormatting.YELLOW)));
                        serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("fpsm.map.cs.auto.start.subtitle").withStyle(ChatFormatting.YELLOW)));
                    });
                }));
            } else {
                if(autoStartTimer % 20 == 0){
                    if(this.voteObj == null) this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.auto.start.actionbar",(autoStartTime.get() - autoStartTimer) / 20).withStyle(ChatFormatting.YELLOW),true);
                }

                if(autoStartTimer >= 1200){
                    this.getMapTeams().getJoinedPlayers().forEach((data -> {
                        data.getPlayer().ifPresent(serverPlayer->{
                            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.auto.started").withStyle(ChatFormatting.YELLOW)));
                            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("")));
                        });
                    }));
                    this.autoStartTimer = 0;
                    this.startGame();
                }
            }
        }
    }

    private void setBystander(ServerPlayer player) {
        List<UUID> uuids = this.getMapTeams().getSameTeamPlayerUUIDs(player);
        Entity entity = null;
        if (uuids.size() > 1) {
            Random random = new Random();
            entity = this.getServerLevel().getEntity(uuids.get(random.nextInt(0, uuids.size())));
        } else if (!uuids.isEmpty()) {
            entity = this.getServerLevel().getEntity(uuids.get(0));
        }
        if (entity != null) {
            player.setCamera(entity);
        }
    }

    @Override
    public void join(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        if (player == null || teamName == null) {
            FPSMatch.LOGGER.error("加入队伍失败：玩家或队伍名称为空");
            return;
        }

        try {
            // 1. 基本加入逻辑
            FPSMCore.checkAndLeaveTeam(player);

            // 2. 让玩家加入队伍
            MapTeams mapTeams = this.getMapTeams();
            mapTeams.joinTeam(teamName, player);
            // 同步游戏类型和地图信息
            this.pullGameInfo(player);

            // 4. 如果不是观察者，处理玩家装备和商店配置
            if (!teamName.equals("spectator")) {
                // 使用新的商店配置管理方式
                this.getMapTeams().getTeamByPlayer(player).ifPresent(team -> {
                    this.getShop(team.name).ifPresent(teamShop -> {
                        List<ServerPlayer> teamPlayers = team.getPlayerList().stream()
                                .map(this::getPlayerByUUID)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toList());
                        teamShop.updatePlayerConfig(player, team.name, teamPlayers);
                    });
                });
            }

            // 5. 如果游戏已经开始，设置玩家为旁观者
            if (this.isStart) {
                player.setGameMode(GameType.SPECTATOR);
                mapTeams.getTeamByName(teamName)
                        .flatMap(team -> team.getPlayerData(player.getUUID()))
                        .ifPresent(data -> {
                            data.setLiving(false);
                        });
                setBystander(player);
            }

            FPSMatch.LOGGER.info("玩家 {} 成功加入 {} 队伍",
                    player.getName().getString(), teamName);

        } catch (Exception e) {
            FPSMatch.LOGGER.error("玩家 {} 加入 {} 队伍时发生错误: ",
                    player.getName().getString(), teamName, e);
            throw e;
        }
    }

    @Override
    public void leave(ServerPlayer player) {
        this.sendPacketToAllPlayer(new FPSMatchTabRemovalS2CPacket(player.getUUID()));
        this.getShop(player).ifPresent(shop -> shop.clearPlayerShopData(player.getUUID()));
        super.leave(player);
    }

    @Override
    public void pullGameInfo(ServerPlayer player){
        super.pullGameInfo(player);
        this.getShop(player).ifPresent(shop -> shop.syncShopData(player));
    }

    @Override
    public String getGameType() {
        return "cs";
    }

    private void voteLogic() {
        if(this.voteObj != null){
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.timer",(this.voteObj.getEndVoteTimer() - System.currentTimeMillis()) / 1000).withStyle(ChatFormatting.DARK_AQUA),true);
            int joinedPlayer = this.getMapTeams().getJoinedPlayers().size();
            AtomicInteger count = new AtomicInteger();
            this.voteObj.voteResult.values().forEach(aBoolean -> {
                if (aBoolean){
                    count.addAndGet(1);
                }
            });
            boolean accept = (float) count.get() / joinedPlayer >= this.voteObj.getPlayerPercent();
            if(this.voteObj.checkVoteIsOverTime() || this.voteObj.voteResult.size() == joinedPlayer || accept){
                Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
                if(accept){
                    if(VOTE_ACTION.containsKey(this.voteObj.getVoteTitle())){
                        this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.success",translation).withStyle(ChatFormatting.GREEN),false);
                        VOTE_ACTION.get(this.voteObj.getVoteTitle()).accept(this);
                    }
                }else{
                    this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.fail",translation).withStyle(ChatFormatting.RED),false);
                    for (PlayerData data : this.getMapTeams().getJoinedPlayers()) {
                        if(!this.voteObj.voteResult.containsKey(data.getOwner())){
                            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.disagree", data.name()).withStyle(ChatFormatting.RED), false);
                        }
                    }

                    if(this.voteObj.getVoteTitle().equals("overtime")){
                        this.isPause = false;
                        this.currentPauseTime = 0;
                        this.syncToClient();

                        // 计算当前总回合数
                        int totalRounds = this.getCTTeam().getScores() + this.getTTeam().getScores();

                        // 發送平局遊戲結果數據
                        this.sendGameResultData(
                                this.getCTTeam(),
                                this.getTTeam(),
                                totalRounds  // 使用当前回合数
                        );

                        this.resetGame();
                    }
                }
                this.voteObj = null;
            }
        }
    }

    private void checkErrorPlayerTeam() {
        this.getMapTeams().getTeams().forEach(team->{
            team.teamUnableToSwitch.forEach(uuid -> {
                this.getPlayerByUUID(uuid).ifPresent(player -> {
                    player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team.getPlayerTeam());
                    team.teamUnableToSwitch.remove(uuid);
                });
            });
        });
    }

    /**
     * 开始新游戏（初始化所有玩家状态）
     * @see #giveAllPlayersKits() 发放初始装备
     * @see #giveBlastTeamBomb() 给爆破方分配C4
     * @see #syncShopData() 同步商店数据
     */
    public void startGame(){
        GameDataApiUtils.clearAllShopConfigCache();
        // 更新所有玩家的商店配置
        this.getMapTeams().getTeams().forEach(team -> {
            List<ServerPlayer> teamPlayers = team.getPlayerList().stream()
                    .map(this::getPlayerByUUID)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            if (teamPlayers.isEmpty()) return;

            teamPlayers.forEach(player -> {
                this.getShop(team.name).ifPresent(teamShop -> {
                    teamShop.updatePlayerConfig(player, team.name, teamPlayers);
                });
            });
        });
        // 设置游戏开始时间和比赛ID
        this.setGameStartTime(System.currentTimeMillis());
        this.setMatchId(LocalDateTime.now().format(MATCH_ID_FORMATTER));
        // 使用GiveStartKitsMap接口的方法重置所有玩家状态和装备
        this.getMapTeams().resetLivingPlayers();
        this.giveAllPlayersKits();

        // 添加日志记录
        FPSMatch.LOGGER.info("FPSMatch-MCGO: 游戏开始, 比赛ID: {}, 开始时间: {}",
                this.getMatchId(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        this.getMapTeams().setTeamNameColor(this,"ct",ChatFormatting.BLUE);
        this.getMapTeams().setTeamNameColor(this,"t",ChatFormatting.YELLOW);
        if (this.isError) return;
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
        this.getServerLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,null);
        this.getServerLevel().getServer().setDifficulty(Difficulty.HARD,true);
        this.isOvertime = false;
        this.overCount = 0;
        this.isWaitingOverTimeVote = false;
        this.isStart = true;
        this.isWaiting = true;
        this.isWaitingWinner = false;
        this.setBlasting(null);
        this.setExploded(false);
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isShopLocked = false;
        boolean spawnCheck = this.getMapTeams().setTeamsSpawnPoints();
        if(!spawnCheck){
            this.resetGame();
            return;
        }
        this.getMapTeams().startNewRound();
        this.getMapTeams().resetLivingPlayers();
        this.getMapTeams().getJoinedPlayers().forEach((data -> {
            data.getPlayer().ifPresent(player->{
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION,-1,2,false,false,false));
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                this.clearPlayerInventory(player);
                this.teleportPlayerToReSpawnPoint(player);
            });
        }));
        syncShopInfo(true,getShopCloseTime());
        syncNormalRoundStartMessage();
        this.giveAllPlayersKits();
        this.giveBlastTeamBomb();
        this.syncShopData();
        this.getMapTeams().getJoinedPlayers().forEach((data -> this.setPlayerMoney(data.getOwner(),800)));
    }

    /**
     * 生成比赛ID
     * 格式: yyyyMMddHHmmss + 4位随机数
     * 例如: 202403191530451234
     * @return 生成的比赛ID
     */
    private String generateMatchId() {
        // 使用LocalDateTime生成时间戳
        String timestamp = LocalDateTime.now().format(MATCH_ID_FORMATTER);

        // 使用ThreadLocalRandom生成4位随机数
        int randomNum = ThreadLocalRandom.current().nextInt(10000);
        String formattedRandomNum = String.format("%04d", randomNum);

        // 直接拼接时间戳和随机数
        return timestamp + formattedRandomNum;
    }

    public boolean canRestTime(){
        return !this.isPause && !this.isWarmTime && !this.isWaiting && !this.isWaitingWinner;
    }

    public boolean checkPauseTime(){
        if(this.isPause && currentPauseTime < pauseTime.get()){
            this.currentPauseTime++;
        }else{
            if(this.isPause) {
                currentPauseTime = 0;
                if(this.voteObj != null && this.voteObj.getVoteTitle().equals("unpause")){
                    this.voteObj = null;
                }
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.done").withStyle(ChatFormatting.GOLD),false);
            }
            isPause = false;
        }
        return this.isPause;
    }

    public boolean checkWarmUpTime(){
        if(this.isWarmTime && currentPauseTime < warmUpTime.get()){
            this.currentPauseTime++;
        }else {
            if(this.canRestTime()) {
                currentPauseTime = 0;
            }
            isWarmTime = false;
        }
        return this.isWarmTime;
    }

    public boolean checkWaitingTime(){
        if(this.isWaiting && currentPauseTime < waitingTime.get()){
            this.currentPauseTime++;
            boolean b = false;
            Iterator<BaseTeam> teams = this.getMapTeams().getTeams().iterator();
            while (teams.hasNext()){
                BaseTeam baseTeam = teams.next();
                if(!b){
                    b = baseTeam.needPause();
                    if(b){
                        baseTeam.setNeedPause(false);
                    }
                }else{
                    baseTeam.resetPauseIfNeed();
                }
                teams.remove();
            }

            if(b){
                this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.now").withStyle(ChatFormatting.GOLD),false);
                this.isPause = true;
                this.currentPauseTime = 0;
                this.isWaiting = true;
            }
        }else {
            if(this.canRestTime()) currentPauseTime = 0;
            isWaiting = false;
        }
        return this.isWaiting;
    }

    public boolean checkWinnerTime(){
        if(this.isWaitingWinner && currentPauseTime < winnerWaitingTime.get()){
            this.currentPauseTime++;
        }else{
            if(this.canRestTime()) currentPauseTime = 0;
        }
        return this.isWaitingWinner;
    }

    public void checkRoundVictory(){
        if(isWaitingWinner) return;
        Map<BaseTeam, List<UUID>> teamsLiving = this.getMapTeams().getTeamsLiving();
        if(teamsLiving.size() == 1){
            BaseTeam winnerTeam = teamsLiving.keySet().stream().findFirst().get();
            this.roundVictory(winnerTeam, WinnerReason.ACED);
        }

        if(teamsLiving.isEmpty()){
            this.roundVictory(this.getCTTeam(),WinnerReason.ACED);
        }
    }

    public void checkBlastingVictory(){
        if(isWaitingWinner) return;
        if(this.isExploded()) {
            this.roundVictory(this.getTTeam(),WinnerReason.DETONATE_BOMB);
        }else {
            Map<BaseTeam, List<UUID>> teamsLiving = this.getMapTeams().getTeamsLiving();
            if(teamsLiving.size() == 1){
                BaseTeam winnerTeam = teamsLiving.keySet().stream().findFirst().get();
                boolean flag = this.checkCanPlacingBombs(winnerTeam.getFixedName());
                if(flag){
                    this.roundVictory(winnerTeam,WinnerReason.ACED);
                }
            }else if(teamsLiving.isEmpty()){
                this.roundVictory(this.getTTeam(),WinnerReason.ACED);
            }
        }
    }

    public boolean isRoundTimeEnd(){
        if(this.isBlasting() > 0){
            this.currentRoundTime = -1;
            return false;
        }
        if(this.currentRoundTime < this.roundTimeLimit.get()){
            this.currentRoundTime++;
        }
        if(this.isClosedShop()){
            this.isShopLocked = true;
            this.syncShopInfo(false,0);
        }
        return this.currentRoundTime >= this.roundTimeLimit.get();
    }

    public boolean isClosedShop(){
        return (this.currentRoundTime >= this.closeShopTime.get() || this.currentRoundTime == -1 ) && !this.isShopLocked;
    }

    public int getShopCloseTime(){
        int closeTime = (this.closeShopTime.get() - this.currentRoundTime);
        if (closeTime < 0) return 0;
        if(this.isWaiting){
            closeTime += this.waitingTime.get() - this.currentPauseTime;
        }
        return closeTime / 20;
    }

    /**
     * 向所有玩家发送标题消息
     * @param title 主标题内容
     * @param subtitle 副标题内容（可选）
     * @see ClientboundSetTitleTextPacket Mojang网络协议包
     */
    public void sendAllPlayerTitle(Component title,@Nullable Component subtitle){
        ServerLevel level = this.getServerLevel();
        this.getMapTeams().getJoinedPlayers().forEach((data -> {
            data.getPlayer().ifPresent(player -> {
                player.connection.send(new ClientboundSetTitleTextPacket(title));
                if(subtitle != null){
                    player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                }
            });

        }));
    }

    /**
     * 处理回合胜利逻辑
     * @param winnerTeam 获胜队伍
     * @param reason 胜利原因（如炸弹拆除/爆炸）
     * @see #checkLoseStreaks(BaseTeam) 计算经济奖励
     * @see #checkMatchPoint() 检查赛点状态
     * @see MVPMusicManager MVP音乐播放逻辑
     */
    private void roundVictory(@NotNull BaseTeam winnerTeam, @NotNull WinnerReason reason) {
        // 如果已经在等待胜利者，则直接返回
        if(isWaitingWinner) return;
        // 设置为等待胜利者状态
        this.isWaitingWinner = true;
        MapTeams.RawMVPData mvpData = this.getMapTeams().getRoundMvpPlayer(winnerTeam);
        MvpReason mvpReason = null;

        if(mvpData != null){
            Optional<ServerPlayer> player = this.getPlayerByUUID(mvpData.uuid());
            if (player.isPresent()) {
                PlayerGetMvpEvent event = new PlayerGetMvpEvent(player.get(),this,
                        new MvpReason.Builder(mvpData.uuid())
                                .setMvpReason(Component.literal(mvpData.reason()))
                                .setPlayerName(this.getMapTeams().playerName.get(mvpData.uuid()))
                                .setTeamName(Component.literal(winnerTeam.name.toUpperCase(Locale.ROOT))).build());
                MinecraftForge.EVENT_BUS.post(event);
                mvpReason = event.getReason();

                if(FPSMCore.getInstance().getMvpMusicManager().playerHasMvpMusic(mvpData.uuid().toString())){
                    ResourceLocation musicLocation = FPSMCore.getInstance().getMvpMusicManager().getMvpMusic(mvpData.uuid().toString());
                    this.sendPacketToAllPlayer(new FPSMusicPlayS2CPacket(musicLocation.toString()));
                }
            }
        }

        if(mvpReason == null){
            mvpReason = new MvpReason.Builder(UUID.randomUUID())
                    .setTeamName(Component.literal(winnerTeam.name.toUpperCase(Locale.ROOT))).build();
        }
        this.sendPacketToAllPlayer(new MvpMessageS2CPacket(mvpReason));

        MinecraftForge.EVENT_BUS.post(new CSGameRoundEndEvent(this,winnerTeam,reason));
        int currentScore = winnerTeam.getScores();
        int target = currentScore + 1;
        List<BaseTeam> baseTeams =this.getMapTeams().getTeams();
        if(target == 12 && baseTeams.remove(winnerTeam) && baseTeams.get(0).getScores() == 12 && !this.isOvertime){
            this.isWaitingOverTimeVote = true;
        }
        winnerTeam.setScores(target);

        // 获取胜利队伍和失败队伍列表
        List<BaseTeam> lostTeams = this.getMapTeams().getTeams();
        lostTeams.remove(winnerTeam);

        // 处理胜利经济奖励
        int reward = reason.winMoney;

        // 检查连败情况
        this.checkLoseStreaks(winnerTeam);
        // 遍历所有玩家，更新经济
        this.getMapTeams().getTeams().forEach(team -> {
            if(team.equals(winnerTeam)){
                team.getPlayerList().forEach(uuid -> {
                    this.getPlayerByUUID(uuid).ifPresentOrElse(player->{
                        this.addPlayerMoney(uuid, reward);
                        player.sendSystemMessage(Component.translatable("fpsm.map.cs.reward.money", reward, reason.name()));
                    },()->{
                        this.addPlayerMoney(uuid, reward);
                    });
                });
            }else{
                team.getPlayerList().forEach(uuid -> {
                    int defaultEconomy = 1400;
                    if(this.checkCanPlacingBombs(team.getFixedName()) && reason == WinnerReason.DEFUSE_BOMB){
                        defaultEconomy += 600;
                    }
                    int compensation = 500;
                    int compensationFactor = team.getCompensationFactor();
                    // 计算失败补偿
                    int loss = defaultEconomy + compensation * compensationFactor;

                    int finalDefaultEconomy = defaultEconomy;
                    this.getPlayerByUUID(uuid).ifPresentOrElse(player->{
                        // 如果玩家没有活着，则给予失败补偿
                        if(reason != WinnerReason.TIME_OUT){
                            this.addPlayerMoney(uuid, loss);
                            player.sendSystemMessage(Component.translatable("fpsm.map.cs.reward.money", loss, finalDefaultEconomy + " + " + compensation + " * " + compensationFactor));
                        }else{
                            team.getPlayerData(uuid).ifPresent(data->{
                                if(!data.isLiving()){
                                    this.addPlayerMoney(uuid, loss);
                                    player.sendSystemMessage(Component.translatable("fpsm.map.cs.reward.money", loss, finalDefaultEconomy + " + " + compensation + " * " + compensationFactor));
                                }else{
                                    player.sendSystemMessage(Component.translatable("fpsm.map.cs.reward.money", 0, "timeout living"));
                                }
                            });
                        }
                    },()->{
                        this.addPlayerMoney(uuid, loss);
                    });
                });
            }
        });
        // 同步商店金钱数据
        this.getShops().forEach(FPSMShop::syncShopMoneyData);
    }

    private void checkLoseStreaks(BaseTeam winnerTeam) {
        // 遍历所有队伍，检查连败情况
        this.getMapTeams().getTeams().forEach(team -> {
            int compensationFactor = team.getCompensationFactor();
            if (team.equals(winnerTeam)) {
                team.setCompensationFactor(Math.max(compensationFactor - 2, 0));
            } else {
                team.setCompensationFactor(Math.min(compensationFactor + 1, 4));
            }
        });
    }

    public void startNewRound() {
        // 更新所有玩家的商店配置
        this.getMapTeams().getTeams().forEach(team -> {
            List<ServerPlayer> teamPlayers = team.getPlayerList().stream()
                    .map(this::getPlayerByUUID)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            if (teamPlayers.isEmpty()) return;

            teamPlayers.forEach(player -> {
                this.getShop(team.name).ifPresent(teamShop -> {
                    teamShop.updatePlayerConfig(player, team.name, teamPlayers);
                });
            });
        });

        boolean check = this.getMapTeams().setTeamsSpawnPoints();
        if(!check){
            this.resetGame();
        }else{
            this.isStart = true;
            this.isWaiting = true;
            this.isWaitingWinner = false;
            this.cleanupMap();
            this.getMapTeams().startNewRound();
            this.getMapTeams().getJoinedPlayers().forEach((data -> {
                data.getPlayer().ifPresent(player->{
                    player.removeAllEffects();
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION,-1,2,false,false,false));
                    this.teleportPlayerToReSpawnPoint(player);
                });
            }));
            syncShopInfo(true,getShopCloseTime());
            syncNormalRoundStartMessage();
            this.giveBlastTeamBomb();
            this.getShops().forEach(FPSMShop::syncShopData);
            this.checkMatchPoint();
        }
    }

    public void checkMatchPoint(){
        int ctScore = this.getCTTeam().getScores();
        int tScore = this.getTTeam().getScores();
        if(this.isOvertime){
            int check = winnerRound.get() - 1 - 6 * this.overCount + 4;

            if(ctScore - check == 1 || tScore - check == 1){
                this.sendAllPlayerTitle(Component.translatable("fpsm.map.cs.match.point").withStyle(ChatFormatting.RED),null);
            }
        }else{
            if(ctScore == winnerRound.get() - 1 || tScore == winnerRound.get() - 1){
                this.sendAllPlayerTitle(Component.translatable("fpsm.map.cs.match.point").withStyle(ChatFormatting.RED),null);
            }
        }
    }

    private void syncNormalRoundStartMessage() {
        var mvpHUDClosePacket = new MvpHUDCloseS2CPacket();
        var fpsMusicStopPacket = new FPSMusicStopS2CPacket();
        var bombResetPacket = new BombDemolitionProgressS2CPacket(0);

        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> {
            this.getPlayerByUUID(uuid).ifPresent(player->{
                this.sendPacketToJoinedPlayer(player, mvpHUDClosePacket,true);
                this.sendPacketToJoinedPlayer(player, fpsMusicStopPacket,true);
                this.sendPacketToJoinedPlayer(player, bombResetPacket, true);
            });
        }));
    }

    @Override
    public void victory() {
        // 在游戏胜利时停止MVP音乐
        this.sendPacketToAllPlayer(new FPSMusicStopS2CPacket());

        this.getMapTeams().getJoinedPlayers().forEach((playerData -> {
            playerData.getPlayer().ifPresent(serverPlayer -> {
                this.sendPacketToJoinedPlayer(serverPlayer,new FPSMatchStatsResetS2CPacket(),true);
                serverPlayer.removeAllEffects();
            });
        }));
        resetGame();
        GameDataApiUtils.clearAllShopConfigCache();
        // 调用游戏结束处理
        GameEndUtils.handleGameEnd(this.getServerLevel().getServer());
    }

    @Override
    public boolean victoryGoal() {
        AtomicBoolean isVictory = new AtomicBoolean(false);
        if(this.isWaitingOverTimeVote){
            return false;
        }
        this.getMapTeams().getTeams().forEach((team) -> {
            if (team.getScores() >= (isOvertime ? winnerRound.get() - 1 + (this.overCount * 3) + 4 : winnerRound.get())) {
                isVictory.set(true);
                boolean flag = team.name.equals("t");

                // 获取胜负队伍
                BaseTeam winnerTeam = flag ? this.getTTeam() : this.getCTTeam();
                BaseTeam loserTeam = flag ? this.getCTTeam() : this.getTTeam();

                // 发送游戏结果数据
                this.sendGameResultData(
                        winnerTeam,
                        loserTeam,
                        this.isOvertime ? (12 + (this.overCount * 3) + 4) : winnerRound.get()
                );

                // 原有的事件触发逻辑
                MinecraftForge.EVENT_BUS.post(new GameWinnerEvent(this,
                        flag ? this.getTTeam(): this.getCTTeam(),
                        flag ? this.getCTTeam() : this.getTTeam(),
                        this.getServerLevel()));
                this.getMapTeams().getJoinedPlayers().forEach((data -> {
                    data.getPlayer().ifPresent(player->{
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.winner." + team.name + ".message").withStyle(team.name.equals("ct") ? ChatFormatting.DARK_AQUA : ChatFormatting.YELLOW)));
                    });
                }));
            }
        });
        return isVictory.get() && !this.isDebug();
    }

    public void startOvertimeVote() {
        Component translation = Component.translatable("fpsm.cs.overtime");
        this.startVote("overtime",Component.translatable("fpsm.map.vote.message","System",translation), 20, 0.5f);
    }

    public void startOvertime() {
        this.isOvertime = true;
        this.isWaitingOverTimeVote = false;
        this.isPause = false;
        this.currentPauseTime = 0;
        this.clearAndSyncShopData();
        this.getMapTeams().getTeams().forEach(team-> team.getPlayers().forEach((uuid, playerData)->{
            playerData.setLiving(false);
            this.setPlayerMoney(uuid, 10000);
        }));
        this.startNewRound();
    }

    // TODO 重要方法
    @Override
    public void cleanupMap() {
        super.cleanupMap();
        AreaData areaData = this.getMapArea();
        ServerLevel serverLevel = this.getServerLevel();

        serverLevel.getEntitiesOfClass(Entity.class,areaData.getAABB()).forEach(entity -> {
            if(entity instanceof ItemEntity itemEntity){
                itemEntity.discard();
            }
            if(entity instanceof CompositionC4Entity c4){
                c4.discard();
            }

            if(entity instanceof MatchDropEntity matchDropEntity){
                matchDropEntity.discard();
            }
        });
        AtomicInteger atomicInteger = new AtomicInteger(0);
        int ctScore = this.getCTTeam().getScores();
        int tScore = this.getTTeam().getScores();
        boolean switchFlag;
        if (!isOvertime) {
            // 发起加时赛投票
            if (ctScore == 12 && tScore == 12) {
                this.startOvertimeVote();
                this.setBlasting(null);
                this.setExploded(false);
                this.currentRoundTime = 0;
                this.isPause = true;
                this.currentPauseTime = pauseTime.get() - 500;
                return;
            }else{
                this.getMapTeams().getTeams().forEach((team)-> atomicInteger.addAndGet(team.getScores()));
                if(atomicInteger.get() == 12){
                    switchFlag = true;
                    MapTeams.switchAttackAndDefend(this,this.getCTTeam(),this.getTTeam());
                } else {
                    switchFlag = false;
                }
                this.currentPauseTime = 0;
            }
        }else{
            // 加时赛换边判断 打满3局换边
            int total = ctScore + tScore;
            int check = total - 24 - 6 * this.overCount;
            if(check % 3 == 0 && check > 0){
                switchFlag = true;
                MapTeams.switchAttackAndDefend(this,this.getCTTeam(),this.getTTeam());
                this.getMapTeams().getJoinedPlayers().forEach((data -> this.setPlayerMoney(data.getOwner(), 10000)));
                if (check == 6 && ctScore < 12 + 3 * this.overCount + 4 && tScore < 12 + 3 * this.overCount + 4 ) {
                    this.overCount++;
                }
            } else {
                switchFlag = false;
            }
            this.currentPauseTime = 0;
        }
        if (switchFlag) {
            // 更新所有玩家的商店配置
            this.getMapTeams().getTeams().forEach(team -> {
                List<ServerPlayer> teamPlayers = team.getPlayerList().stream()
                        .map(this::getPlayerByUUID)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
                if (teamPlayers.isEmpty()) return;

                teamPlayers.forEach(player -> {
                    this.getShop(team.name).ifPresent(teamShop -> {
                        teamShop.updatePlayerConfig(player, team.name, teamPlayers);
                    });
                });
            });
        }
        this.setBlasting(null);
        this.setExploded(false);
        this.currentRoundTime = 0;
        this.isShopLocked = false;
        this.getMapTeams().getJoinedPlayers().forEach((data -> {
            data.getPlayer().ifPresent(player->{
                player.heal(player.getMaxHealth());
                player.setGameMode(GameType.ADVENTURE);
                if(switchFlag){
                    this.clearPlayerInventory(player);
                    this.givePlayerKits(player);
                    this.sendPacketToJoinedPlayer(player,new ClientboundSetTitleTextPacket(Component.translatable("fpsm.map.cs.team.switch").withStyle(ChatFormatting.GREEN)),true);
                }else{
                    if(!data.isLiving()){
                        this.clearPlayerInventory(player);
                        this.givePlayerKits(player);
                    }else{
                        this.resetGunAmmon();
                    }
                    this.getShop(player).ifPresent(shop -> shop.getPlayerShopData(player.getUUID()).lockShopSlots(player));
                }
            });
        }));
        this.getShops().forEach(FPSMShop::syncShopData);
    }

    public void teleportPlayerToMatchEndPoint(){
        if (this.matchEndTeleportPoint == null ) return;
        SpawnPointData data = this.matchEndTeleportPoint;
        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> {
            this.getPlayerByUUID(uuid).ifPresent(player->{
                teleportToPoint(player, data);
                player.setGameMode(GameType.ADVENTURE);
            });
        }));
    }

    /**
     * 给爆破方随机玩家分配C4炸弹
     * @see CompositionC4 C4物品实体类
     * @see #cleanupMap() 回合结束清理残留C4
     */
    public void giveBlastTeamBomb(){
        BaseTeam team = this.getMapTeams().getTeamByComplexName(this.blastTeam);
        if(team != null){
            Random random = new Random();
            // 随机选择一个玩家作为炸弹携带者
            if(team.getPlayerList().isEmpty()) return;

            team.getPlayerList().forEach((uuid)-> clearPlayerInventory(uuid,(itemStack) -> itemStack.getItem() instanceof CompositionC4));

            UUID uuid = team.getPlayerList().get(random.nextInt(team.getPlayerList().size()));
            if(uuid!= null){
                this.getPlayerByUUID(uuid).ifPresent(player->{
                    player.getInventory().add(FPSMItemRegister.C4.get().getDefaultInstance());
                    player.inventoryMenu.broadcastChanges();
                    player.inventoryMenu.slotsChanged(player.getInventory());
                });
            }
        }
    }

    @Override
    public Map<String, List<ItemStack>> getStartKits() {
        return this.startKits;
    }

    public void setPauseState(ServerPlayer player){
        if(!this.isStart) return;
        this.getMapTeams().getTeamByPlayer(player).ifPresent(team->{
            if(team.canPause() && !this.isPause){
                team.addPause();
                if(!this.isWaiting){
                    this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.nextRound.success").withStyle(ChatFormatting.GOLD),false);
                }else{
                    this.sendAllPlayerMessage(Component.translatable("fpsm.map.cs.pause.success").withStyle(ChatFormatting.GOLD),false);
                }
            }else{
                player.displayClientMessage(Component.translatable("fpsm.map.cs.pause.fail").withStyle(ChatFormatting.RED),false);
            }
        });
    }

    public void setUnPauseState(){
        this.isPause = false;
        this.currentPauseTime = 0;
    }

    private void startUnpauseVote(ServerPlayer serverPlayer) {
        if(this.voteObj == null){
            Component translation = Component.translatable("fpsm.cs.unpause");
            this.startVote("unpause",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),translation),15,1f);
            this.voteObj.addAgree(serverPlayer);
        }else{
            Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
            serverPlayer.displayClientMessage(Component.translatable("fpsm.map.vote.fail.alreadyHasVote", translation).withStyle(ChatFormatting.RED),false);
        }
    }

    public void handleAgreeCommand(ServerPlayer serverPlayer){
        if(this.voteObj != null && !this.voteObj.voteResult.containsKey(serverPlayer.getUUID())){
            this.voteObj.addAgree(serverPlayer);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.agree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.GREEN),false);
        }
    }

    private void handleDisagreeCommand(ServerPlayer serverPlayer) {
        if(this.voteObj != null && !this.voteObj.voteResult.containsKey(serverPlayer.getUUID())){
            this.voteObj.addDisagree(serverPlayer);
            this.sendAllPlayerMessage(Component.translatable("fpsm.map.vote.disagree",serverPlayer.getDisplayName()).withStyle(ChatFormatting.RED),false);
        }
    }


    public void sendAllPlayerMessage(Component message,boolean actionBar){
        this.getMapTeams().getJoinedPlayers().forEach(data -> {
            data.getPlayer().ifPresent(player -> {
                player.displayClientMessage(message,actionBar);
            });
        });
    }

    public void resetGame() {
        // 在游戏重置时停止MVP音乐
        this.sendPacketToAllPlayer(new FPSMusicStopS2CPacket());

        this.getMapTeams().getTeams().forEach(baseTeam -> baseTeam.setScores(0));
        this.isOvertime = false;
        this.isWaitingOverTimeVote = false;
        this.overCount = 0;
        this.isShopLocked = false;
        this.cleanupMap();
        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> {
            this.getPlayerByUUID(uuid).ifPresent(player->{
                this.getServerLevel().getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
                player.getInventory().clearContent();
                player.removeAllEffects();
            });
        }));
        this.teleportPlayerToMatchEndPoint();
        this.sendPacketToAllPlayer(new FPSMatchStatsResetS2CPacket());
        this.isShopLocked = false;
        this.isError = false;
        this.isStart = false;
        this.isWaiting = false;
        this.isWaitingWinner = false;
        this.isWarmTime = false;
        this.currentRoundTime = 0;
        this.currentPauseTime = 0;
        this.isBlasting = 0;
        this.isExploded = false;
        this.getMapTeams().getJoinedPlayers().forEach(data->{
            data.getPlayer().ifPresent(this::resetPlayerClientData);
        });
        this.getShops().forEach(FPSMShop::resetPlayerData);
        this.getMapTeams().reset();
    }

    public final void setBlastTeam(BaseTeam team){
        this.blastTeam = team.getFixedName();
    }

    public boolean checkCanPlacingBombs(String team){
        if(this.blastTeam == null) return false;
        return this.blastTeam.equals(team);
    }

    /**
     * 检查玩家是否在炸弹安放区域
     * @param player 目标玩家
     * @return 是否在有效炸弹区域
     * @see AreaData 区域检测逻辑
     */
    public boolean checkPlayerIsInBombArea(Player player){
        AtomicBoolean a = new AtomicBoolean(false);
        this.bombAreaData.forEach(area->{
            if(!a.get()) a.set(area.isPlayerInArea(player));
        });
        return a.get();
    }
    @Override
    public ArrayList<ItemStack> getKits(BaseTeam team) {
        return (ArrayList<ItemStack>) this.startKits.getOrDefault(team.getFixedName(),new ArrayList<>());
    }

    @Override
    public void addKits(BaseTeam team, ItemStack itemStack) {
        Objects.requireNonNull(team, "Team cannot be null");
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        this.startKits.computeIfAbsent(team.getFixedName(), t -> new ArrayList<>()).add(itemStack);
    }

    @Override
    public void clearTeamKits(BaseTeam team){
        if(this.startKits.containsKey(team.getFixedName())){
            this.startKits.get(team.getFixedName()).clear();
        }
    }

    @Override
    public void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        kits.forEach((s, list) -> list.forEach((itemStack) -> {
            if(itemStack.getItem() instanceof IGun iGun){
                FPSMUtil.fixGunItem(itemStack, iGun);
            }
        }));

        this.startKits.clear();
        this.startKits.putAll(kits);
    }


    @Override
    public void setAllTeamKits(ItemStack itemStack) {
        this.startKits.values().forEach((v) -> v.add(itemStack));
    }
    public void addBombArea(AreaData area){
        this.bombAreaData.add(area);
    }
    public List<AreaData> getBombAreaData() {
        return bombAreaData;
    }
    public void setBlasting(CompositionC4Entity c4) {
        if(c4 == null) {
            isBlasting = 0;
            return;
        }
        if(c4.isDeleting()){
            isBlasting = 2;
            if (c4.getOwner() != null) {
                this.removePlayerMoney(c4.getOwner().getUUID(), 300);
            }
            if(c4.getDemolisher() != null){
                this.addPlayerMoney(c4.getDemolisher().getUUID(), 300);
            }
        }else{
            isBlasting = 1;
        }
    }
    public void setExploded(boolean exploded) {
        isExploded = exploded;
    }

    public int isBlasting() {
        return isBlasting;
    }

    public boolean isExploded() {
        return isExploded;
    }

    public int getClientTime(){
        int time;
        if(this.isPause){
            time = pauseTime.get() - this.currentPauseTime;
        }else {
            if (this.isWaiting) {
                time = waitingTime.get() - this.currentPauseTime;
            } else if (this.isWarmTime) {
                time = warmUpTime.get() - this.currentPauseTime;
            } else if (this.isWaitingWinner) {
                time = winnerWaitingTime.get() - this.currentPauseTime;
            } else if (this.isStart) {
                time = roundTimeLimit.get() - this.currentRoundTime;
            } else {
                time = 0;
            }
        }
        return time;
    }

    /**
     * 同步游戏设置到客户端（比分/时间等）
     * @see CSGameSettingsS2CPacket
     */
    public void syncToClient() {
        BaseTeam ct = this.getCTTeam();
        BaseTeam t = this.getTTeam();
        CSGameSettingsS2CPacket packet = new CSGameSettingsS2CPacket(
                ct.getScores(),t.getScores(),
                this.getClientTime(),
                this.isDebug(),
                this.isStart,
                this.isError,
                this.isPause,
                this.isWaiting,
                this.isWaitingWinner
        );
        this.getMapTeams().getJoinedPlayersWithSpec().forEach((uuid -> {
            this.getPlayerByUUID(uuid).ifPresent(player->{
                this.sendPacketToJoinedPlayer(player,packet,true);
                for (BaseTeam team : this.getMapTeams().getTeamsWithSpec()) {
                    for (UUID existingPlayerId : team.getPlayers().keySet()) {
                        team.getPlayerData(existingPlayerId).ifPresent(playerData -> {
                            var p1 = new CSGameTabStatsS2CPacket(existingPlayerId, playerData, team.name);
                            this.sendPacketToJoinedPlayer(player,p1,true);
                        });
                    }
                }
            });
        }));

        if(!isShopLocked){
            this.syncShopInfo(true,getShopCloseTime());
        }
    }

    public void resetPlayerClientData(ServerPlayer serverPlayer){
        FPSMatchStatsResetS2CPacket packet = new FPSMatchStatsResetS2CPacket();
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> serverPlayer), packet);
    }

    public void resetGunAmmon(){
        this.getMapTeams().getJoinedPlayers().forEach((data)->{
            data.getPlayer().ifPresent(FPSMUtil::resetAllGunAmmo);
        });
    }

    @Nullable
    public SpawnPointData getMatchEndTeleportPoint() {
        return matchEndTeleportPoint;
    }

    public void setMatchEndTeleportPoint(SpawnPointData matchEndTeleportPoint) {
        this.matchEndTeleportPoint = matchEndTeleportPoint;
    }


    @SubscribeEvent
    public static void onChat(ServerChatEvent event){
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if(map instanceof CSGameMap csGameMap){
            String[] m = event.getMessage().getString().split("\\.");
            if(m.length > 1){
                csGameMap.handleChatCommand(m[1],event.getPlayer());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map instanceof CSGameMap){
                dropC4(player);
                player.getInventory().clearContent();
            }
        }
    }

    private static void dropC4(ServerPlayer player) {
        int im = player.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof CompositionC4, -1, player.inventoryMenu.getCraftSlots());
        if (im > 0) {
            player.drop(new ItemStack(FPSMItemRegister.C4.get(), 1), false, false).setGlowingTag(true);
            player.getInventory().setChanged();
        }
    }

    @SubscribeEvent
    public static void onPlayerPickupItem(PlayerEvent.ItemPickupEvent event){
        if(event.getEntity().level().isClientSide) return;
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getEntity());
        if (map instanceof ShopMap<?> shopMap) {
            shopMap.getShop(event.getEntity()).ifPresent(shop -> {
                ShopData shopData = shop.getPlayerShopData(event.getEntity().getUUID());
                Pair<ItemType, ShopSlot> pair = shopData.checkItemStackIsInData(event.getStack());
                if(pair != null){
                    ShopSlot slot = pair.getSecond();
                    slot.lock(event.getStack().getCount());
                    shop.syncShopData((ServerPlayer) event.getEntity(),pair.getFirst(),slot);
                }});
        }

        if(map != null){
            FPSMUtil.sortPlayerInventory(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerDropItem(ItemTossEvent event){
        if(event.getEntity().level().isClientSide) return;
        ItemStack itemStack = event.getEntity().getItem();
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(event.getPlayer());
        if(itemStack.getItem() instanceof CompositionC4){
            event.getEntity().setGlowingTag(true);
        }

        if(itemStack.getItem() instanceof BombDisposalKit){
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("fpsm.item.bomb_disposal_kit.drop.message").withStyle(ChatFormatting.RED),true);
            event.getPlayer().getInventory().add(new ItemStack(FPSMItemRegister.BOMB_DISPOSAL_KIT.get(),1));
        }

        //商店逻辑
        if (map instanceof ShopMap<?> shopMap){
            shopMap.getShop(event.getPlayer()).ifPresent(shop -> {
                ShopData shopData = shop.getPlayerShopData(event.getEntity().getUUID());
                Pair<ItemType, ShopSlot> pair = shopData.checkItemStackIsInData(itemStack);
                if(pair != null){
                    ShopSlot slot = pair.getSecond();
                    if(pair.getFirst() != ItemType.THROWABLE){
                        slot.unlock(itemStack.getCount());
                        shop.syncShopData((ServerPlayer) event.getPlayer(),pair.getFirst(),slot);
                    }
                }
            });
        }

        DropType type = DropType.getItemDropType(itemStack);
        if(map instanceof CSGameMap && !event.isCanceled() && type != DropType.MISC){
            FPSMCore.playerDropMatchItem((ServerPlayer) event.getPlayer(),itemStack);
            event.setCanceled(true);
        }
    }


    @SubscribeEvent
    public static void onPlayerKilledByGun(EntityKillByGunEvent event){
        if(event.getLogicalSide() == LogicalSide.SERVER){
            if (event.getKilledEntity() instanceof ServerPlayer player) {
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map instanceof CSGameMap cs && map.checkGameHasPlayer(player)) {
                    if(event.getAttacker() instanceof ServerPlayer attacker){
                        BaseMap fromMap = FPSMCore.getInstance().getMapByPlayer(player);
                        if (fromMap instanceof CSGameMap csGameMap && csGameMap.equals(map)) {
                            BaseTeam killerTeam = map.getMapTeams().getTeamByPlayer(attacker).orElse(null);
                            BaseTeam deadTeam = map.getMapTeams().getTeamByPlayer(player).orElse(null);
                            if(killerTeam == null || deadTeam == null) return;
                            if (killerTeam.getFixedName().equals(deadTeam.getFixedName())){
                                cs.removePlayerMoney(attacker.getUUID(),300);
                                attacker.displayClientMessage(Component.translatable("fpsm.kill.message.teammate",300),false);
                            }else{
                                int reward = cs.gerRewardByGunId(event.getGunId());
                                cs.addPlayerMoney(attacker.getUUID(),reward);
                                attacker.displayClientMessage(Component.translatable("fpsm.kill.message.enemy",reward),false);
                            }

                            if(attacker.getMainHandItem().getItem() instanceof IGun) {
                                csGameMap.getMapTeams().getTeamByPlayer(attacker).ifPresent(team->{
                                    if(event.isHeadShot()){
                                        team.getPlayerData(attacker.getUUID()).ifPresent(PlayerData::addHeadshotKill);
                                    }
                                });

                                DeathMessage deathMessage = new DeathMessage.Builder(attacker, player, attacker.getMainHandItem()).setHeadShot(event.isHeadShot()).build();
                                DeathMessageS2CPacket killMessageS2CPacket = new DeathMessageS2CPacket(deathMessage);
                                csGameMap.sendPacketToAllPlayer(killMessageS2CPacket);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 玩家死亡事件处理
     * @see #handlePlayerDeath(ServerPlayer,Entity) 处理死亡逻辑
     */
    @SubscribeEvent
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map instanceof CSGameMap csGameMap) {
                csGameMap.handlePlayerDeath(player,event.getSource().getEntity());
                csGameMap.sendPacketToJoinedPlayer(player,new FPSMatchRespawnS2CPacket(),true);
                event.setCanceled(true);
            }
        }
    }

    public void handlePlayerDeath(ServerPlayer player, @Nullable Entity fromEntity) {
        ServerPlayer from;
        if (fromEntity instanceof ServerPlayer fromPlayer) {
            BaseMap fromMap = FPSMCore.getInstance().getMapByPlayer(fromPlayer);
            if (fromMap != null && fromMap.equals(this)) {
                from = fromPlayer;
                DeathMessage message;
                if(fromPlayer.getMainHandItem().isEmpty()){
                    message = new DeathMessage.Builder(player,fromPlayer, ItemStack.EMPTY).setArg("hand").build();
                    this.sendPacketToAllPlayer(new DeathMessageS2CPacket(message));
                }
            } else {
                from = null;
            }
        } else {
            from = null;
        }

        if(this.isStart) {
            MapTeams teams = this.getMapTeams();
            teams.getTeamByPlayer(player).ifPresent(deadPlayerTeam->{
                this.getShop(player).ifPresent(shop->{
                    shop.getDefaultAndPutData(player.getUUID());
                });

                this.sendPacketToJoinedPlayer(player,new ShopStatesS2CPacket(false,0,0),true);
                deadPlayerTeam.getPlayerData(player.getUUID()).ifPresent(data->{
                    data.addDeaths();
                    data.setLiving(false);
                    // 清除c4,并掉落c4
                    dropC4(player);
                    // 清除玩家所属子弹
                    this.getServerLevel().getEntitiesOfClass(EntityKineticBullet.class,mapArea.getAABB())
                            .stream()
                            .filter(entityKineticBullet -> entityKineticBullet.getOwner() != null && entityKineticBullet.getOwner().getUUID().equals(player.getUUID()))
                            .toList()
                            .forEach(Entity::discard);
                    // 清除拆弹工具,并掉落拆弹工具
                    int ik = player.getInventory().clearOrCountMatchingItems((i) -> i.getItem() instanceof BombDisposalKit, -1, player.inventoryMenu.getCraftSlots());
                    if (ik > 0) {
                        player.drop(new ItemStack(FPSMItemRegister.BOMB_DISPOSAL_KIT.get(), 1), false, false).setGlowingTag(true);
                        player.getInventory().setChanged();
                    }
                    FPSMCore.playerDeadDropWeapon(player);
                    player.getInventory().clearContent();
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.SPECTATOR);
                    player.setRespawnPosition(player.level().dimension(),player.getOnPos().above(),0,true,false);
                    this.setBystander(player);
                });
            });


            Map<UUID, Float> hurtDataMap = teams.getDamageMap().get(player.getUUID());
            if (hurtDataMap != null && !hurtDataMap.isEmpty()) {

                List<Map.Entry<UUID, Float>> sortedDamageEntries = hurtDataMap.entrySet().stream()
                        .filter(entry -> entry.getValue() > 4)
                        .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                        .limit(2)
                        .toList();

                for (Map.Entry<UUID, Float> sortedDamageEntry : sortedDamageEntries) {
                    UUID assistId = sortedDamageEntry.getKey();
                    teams.getTeamByPlayer(assistId)
                            .flatMap(assistPlayerTeam -> assistPlayerTeam.getPlayerData(assistId))
                            .ifPresent(assistData -> {
                                if (from != null && from.getUUID().equals(assistId)) return;
                                assistData.addAssist();
                            });
                }
            }

            if(from == null) return;
            teams.getTeamByPlayer(from)
                    .flatMap(killerPlayerTeam -> killerPlayerTeam.getPlayerData(from.getUUID()))
                    .ifPresent(data -> {
                        data.addKills();
                        MinecraftForge.EVENT_BUS.post(new PlayerKillOnMapEvent(this, player, from));
                    });
        }
    }

    public void handleChatCommand(String rawText,ServerPlayer player){
        COMMANDS.forEach((k,v)->{
            if (rawText.contains(k) && rawText.length() == k.length()){
                v.accept(this,player);
            }
        });
    }

    @Override
    public CSGameMap getMap() {
        return this;
    }

    /**
     * Codec序列化配置（用于地图数据保存/加载）
     * <p> 地图名称、区域数据、出生点、商店配置等全量数据
     */
    public static final Codec<CSGameMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // 基础地图数据
            Codec.STRING.fieldOf("mapName").forGetter(CSGameMap::getMapName),
            FPSMCodec.AREA_DATA_CODEC.fieldOf("mapArea").forGetter(CSGameMap::getMapArea),
            ResourceLocation.CODEC.fieldOf("serverLevel").forGetter(map -> map.getServerLevel().dimension().location()),

            // 队伍出生点数据
            FPSMCodec.SPAWN_POINT_DATA_MAP_LIST_CODEC.fieldOf("spawnpoints").forGetter(map->map.getMapTeams().getAllSpawnPoints()),

            // 商店数据 - 使用字符串到FPSMShop的映射
            Codec.unboundedMap(Codec.STRING, FPSMShop.CODEC).fieldOf("shops")
                    .forGetter(map -> map.shop),

            // 初始装备数据
            FPSMCodec.TEAM_ITEMS_KITS_CODEC.fieldOf("startKits")
                    .forGetter(map -> map.startKits),

            // 炸弹区域数据
            FPSMCodec.List_AREA_DATA_CODEC.fieldOf("bombAreas")
                    .forGetter(map -> map.bombAreaData),

            // 爆破队伍
            Codec.STRING.fieldOf("blastTeam")
                    .forGetter(map -> map.blastTeam),

            // 比赛结束传送点
            FPSMCodec.SPAWN_POINT_DATA_CODEC.optionalFieldOf("matchEndPoint")
                    .forGetter(map -> Optional.ofNullable(map.matchEndTeleportPoint))

    ).apply(instance, (mapName, mapArea, serverLevel, spawnPoints, shops, startKits, bombAreas, blastTeam, matchEndPoint) -> {
        // 创建新的CSGameMap实例
        CSGameMap gameMap = new CSGameMap(
                FPSMCore.getInstance().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,serverLevel)),
                mapName,
                mapArea
        );

        // 设置出生点数据
        gameMap.getMapTeams().putAllSpawnPoints(spawnPoints);

        // 设置商店数据
        gameMap.shop.clear();
        gameMap.shop.putAll(shops);

        // 设置初始装备
        Map<String, ArrayList<ItemStack>> data = new HashMap<>();
        startKits.forEach((t,l)->{
            ArrayList<ItemStack> list = new ArrayList<>(l);
            data.put(t,list);
        });
        gameMap.setStartKits(data);

        // 设置炸弹区域
        gameMap.bombAreaData.addAll(bombAreas);

        // 设置爆破队伍
        gameMap.blastTeam = blastTeam;

        // 设置比赛结束传送点
        matchEndPoint.ifPresent(point -> gameMap.matchEndTeleportPoint = point);

        return gameMap;
    }));

    public @NotNull BaseTeam getTTeam(){
        return this.tTeam;
    }
    public @NotNull BaseTeam getCTTeam(){
        return this.ctTeam;
    }

    public static Map<String, BiConsumer<CSGameMap,ServerPlayer>> registerCommands(){
        Map<String, BiConsumer<CSGameMap,ServerPlayer>> commands = new HashMap<>();
        commands.put("p", CSGameMap::setPauseState);
        commands.put("pause", CSGameMap::setPauseState);
        commands.put("unpause", CSGameMap::startUnpauseVote);
        commands.put("up", CSGameMap::startUnpauseVote);
        commands.put("agree",CSGameMap::handleAgreeCommand);
        commands.put("a",CSGameMap::handleAgreeCommand);
        commands.put("disagree",CSGameMap::handleDisagreeCommand);
        commands.put("da",CSGameMap::handleDisagreeCommand);
        return commands;
    }

    private void handleResetCommand(ServerPlayer serverPlayer) {
        if(this.voteObj == null && this.isStart){
            this.startVote("reset",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),Component.translatable("fpsm.cs.reset")),20,1f);
            this.voteObj.addAgree(serverPlayer);
        } else if (this.voteObj != null) {
            Component translation = Component.translatable("fpsm.cs." + this.voteObj.getVoteTitle());
            serverPlayer.displayClientMessage(Component.translatable("fpsm.map.vote.fail.alreadyHasVote", translation).withStyle(ChatFormatting.RED),false);
        }
    }

    private void handleLogCommand(ServerPlayer serverPlayer) {
        serverPlayer.displayClientMessage(Component.literal("-----------------INFO----------------").withStyle(ChatFormatting.GREEN), false);

        serverPlayer.displayClientMessage(Component.literal("| type ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.getGameType() + "]").withStyle(ChatFormatting.DARK_AQUA)), false);
        serverPlayer.displayClientMessage(Component.literal("| name ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.getMapName() + "]").withStyle(ChatFormatting.DARK_AQUA)), false);

        serverPlayer.displayClientMessage(Component.literal("| isStart ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isStart)), false);
        serverPlayer.displayClientMessage(Component.literal("| isPause ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isPause)), false);
        serverPlayer.displayClientMessage(Component.literal("| isWaiting ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isWaiting)), false);
        serverPlayer.displayClientMessage(Component.literal("| isWaitingWinner ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isWaitingWinner)), false);

        serverPlayer.displayClientMessage(Component.literal("| isBlasting ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.isBlasting + "]").withStyle(ChatFormatting.DARK_AQUA)), false);
        serverPlayer.displayClientMessage(Component.literal("| isExploded ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isExploded)), false);
        serverPlayer.displayClientMessage(Component.literal("| isOvertime ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isOvertime)), false);
        serverPlayer.displayClientMessage(Component.literal("| overCount ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.overCount + "]").withStyle(ChatFormatting.DARK_AQUA)), false);

        serverPlayer.displayClientMessage(Component.literal("| isWaitingOverTimeVote ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isWaitingOverTimeVote)), false);
        serverPlayer.displayClientMessage(Component.literal("| currentPauseTime ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.currentPauseTime + "]").withStyle(ChatFormatting.DARK_AQUA)), false);
        serverPlayer.displayClientMessage(Component.literal("| autoStartTimer ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.autoStartTimer + "]").withStyle(ChatFormatting.DARK_AQUA)), false);

        serverPlayer.displayClientMessage(Component.literal("| autoStartFirstMessageFlag ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.autoStartFirstMessageFlag)), false);
        serverPlayer.displayClientMessage(Component.literal("| waitingTime ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.waitingTime + "]").withStyle(ChatFormatting.DARK_AQUA)), false);
        serverPlayer.displayClientMessage(Component.literal("| currentRoundTime ").withStyle(ChatFormatting.GRAY).append(
                Component.literal("[" + this.currentRoundTime + "]").withStyle(ChatFormatting.DARK_AQUA)), false);

        serverPlayer.displayClientMessage(Component.literal("| isShopLocked ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isShopLocked)), false);
        serverPlayer.displayClientMessage(Component.literal("| isWarmTime ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isWarmTime)), false);
        serverPlayer.displayClientMessage(Component.literal("| isError ").withStyle(ChatFormatting.GRAY).append(
                RenderUtil.formatBoolean(this.isError)), false);

        for (BaseTeam team : this.getMapTeams().getTeams()) {
            serverPlayer.displayClientMessage(Component.literal("-----------------------------------").withStyle(ChatFormatting.GREEN), false);
            serverPlayer.displayClientMessage(Component.literal("info: team-").withStyle(ChatFormatting.GRAY).append(
                    Component.literal("[" + team.name + "]").withStyle(ChatFormatting.DARK_AQUA)).append(
                    Component.literal(" | player Count : ").withStyle(ChatFormatting.GRAY)).append(
                    Component.literal("[" + team.getPlayers().size() + "]").withStyle(ChatFormatting.DARK_AQUA)), false);
            for (PlayerData tabData : team.getPlayers().values()) {
                MutableComponent playerNameComponent = Component.literal("Player: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(this.getMapTeams().playerName.get(tabData.getOwner()).getString()).withStyle(ChatFormatting.DARK_GREEN));

                MutableComponent tabDataComponent = Component.literal(" | Tab Data: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("[" + tabData.getTabString() + "]").withStyle(ChatFormatting.DARK_AQUA));

                MutableComponent damagesComponent = Component.literal(" | damages : ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("[" + tabData.getDamage() + "]").withStyle(ChatFormatting.DARK_AQUA));

                MutableComponent isLivingComponent = Component.literal(" | isLiving :").withStyle(ChatFormatting.GRAY)
                        .append(RenderUtil.formatBoolean(tabData.isLiving()));

                serverPlayer.displayClientMessage(playerNameComponent.append(tabDataComponent).append(damagesComponent).append(isLivingComponent), false);
            }
            serverPlayer.displayClientMessage(Component.literal("-----------------------------------").withStyle(ChatFormatting.GREEN), false);
        }
    }

    private void handleStartCommand(ServerPlayer serverPlayer) {
        if((!this.isStart && this.voteObj == null) || (!this.isStart && !this.voteObj.getVoteTitle().equals("start"))){
            this.startVote("start",Component.translatable("fpsm.map.vote.message",serverPlayer.getDisplayName(),Component.translatable("fpsm.cs.start")),20,1f);
            this.voteObj.addAgree(serverPlayer);
        }
    }

    public static Map<String, Consumer<CSGameMap>> registerVoteAction(){
        Map<String, Consumer<CSGameMap>> commands = new HashMap<>();
        commands.put("overtime",CSGameMap::startOvertime);
        commands.put("unpause", CSGameMap::setUnPauseState);
        commands.put("reset", CSGameMap::resetGame);
        commands.put("start",CSGameMap::startGame);
        return commands;
    }

    @Override
    public Collection<Setting<?>> settings() {
        return settings;
    }

    @Override
    public <I> Setting<I> addSetting(Setting<I> setting) {
        settings.add(setting);
        return setting;
    }

    public void read() {
        FPSMCore.getInstance().registerMap(this.getGameType(),this);
    }

    public static void write(FPSMDataManager manager){
        FPSMCore.getInstance().getMapByClass(CSGameMap.class)
                .forEach((map -> {
                    map.saveConfig();
                    manager.saveData(map,map.getMapName());
                }));
    }

    public int gerRewardByGunId(ResourceLocation gunId){
        Optional<GunTabType> optional = FPSMUtil.getGunTypeByGunId(gunId);
        if(optional.isPresent()){
            switch(optional.get()){
                case SHOTGUN -> {
                    return 900;
                }
                case SMG -> {
                    return 600;
                }
                case SNIPER -> {
                    return 100;
                }
                default -> {
                    return 300;
                }
            }
        }else{
            return 300;
        }
    }

    public enum WinnerReason{
        TIME_OUT(3250),
        ACED(3250),
        DEFUSE_BOMB(3500),
        DETONATE_BOMB(3500);
        public final int winMoney;

        WinnerReason(int winMoney) {
            this.winMoney = winMoney;
        }
    }

}
