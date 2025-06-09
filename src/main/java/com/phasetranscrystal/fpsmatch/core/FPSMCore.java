package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.sound.MVPMusicManager;
import com.phasetranscrystal.fpsmatch.entity.drop.MatchDropEntity;
import com.phasetranscrystal.fpsmatch.entity.drop.DropType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * FPSMCore 类是FPS游戏模式的核心管理类
 * 负责管理所有游戏地图、游戏类型的注册和玩家数据
 */
@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    // 单例实例
    private static FPSMCore INSTANCE;
    public final String archiveName;
    // 存储所有游戏地图，键为游戏类型，值为该类型的地图列表
    private final Map<String, List<BaseMap>> GAMES = new HashMap<>();
    // 存储游戏类型的构造器，用于创建新地图
    private final Map<String, Function3<ServerLevel,String,AreaData,BaseMap>> REGISTRY = new HashMap<>();
    private final FPSMDataManager fpsmDataManager;
    private final LMManager listenerModuleManager;
    private final MVPMusicManager mvpMusicManager;

    /**
     * 私有构造函数，确保单例模式
     * @param archiveName 存档名称
     */
    private FPSMCore(String archiveName) {
        this.archiveName = archiveName;
        this.mvpMusicManager = new MVPMusicManager();
        this.listenerModuleManager = new LMManager();
        this.fpsmDataManager = new FPSMDataManager(archiveName);
    }

    /**
     * 获取FPSMCore的单例实例
     * @return FPSMCore实例
     * @throws RuntimeException 如果实例未初始化
     */
    public static FPSMCore getInstance(){
        if(INSTANCE == null) throw new RuntimeException("error : fpsm not install.");
        return INSTANCE;
    }

    @Nullable public BaseMap getMapByPlayer(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> baseMapList.forEach((baseMap)->{
            if(baseMap.checkGameHasPlayer(player)) map.set(baseMap);
        })));
         return map.get();
    }

    @Nullable public BaseMap getMapByPlayerWithSpec(Player player){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.values().forEach((baseMapList -> baseMapList.forEach((baseMap)->{
            if(baseMap.checkGameHasPlayer(player)){
                map.set(baseMap);
            }else if (baseMap.checkSpecHasPlayer(player)){
                map.set(baseMap);
            }
        })));
        return map.get();
    }

    /**
     * 注册新的地图
     * @param type 游戏类型
     * @param map 要注册的地图实例
     * @return 注册成功返回地图实例，失败返回null
     */
    public void registerMap(String type, BaseMap map){
        if(REGISTRY.containsKey(type)) {
            if(getMapNames(type).contains(map.getMapName())){
                FPSMatch.LOGGER.error("error : has same map name -> {}", map.getMapName());
                return;
            }
            List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
            maps.add(map);
            GAMES.put(type,maps);
        }else{
            FPSMatch.LOGGER.error("error : unregister game type {}", type);
        }
    }

    /**
     * 根据地图名称获取地图实例
     * @param name 地图名称
     * @return 对应的地图实例，如果未找到则返回null
     */
    @Nullable
    public BaseMap getMapByName(String name){
        AtomicReference<BaseMap> map = new AtomicReference<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((baseMap)->{
            if(baseMap.getMapName().equals(name)) {
                map.set(baseMap);
            }
        }));
       return map.get();
    }

    /**
     * 获取指定类型的所有地图实例
     * @param clazz 地图类型的Class对象
     * @return 指定类型的地图列表
     */
    public <T> List<T> getMapByClass(Class<T> clazz){
        ArrayList<T> list = new ArrayList<>();
        for (List<BaseMap> maps : GAMES.values()) {
            for (BaseMap map : maps) {
                if (clazz.isInstance(map)){
                    list.add((T) map);
                }
            }
        }
        return list;
    }

    /**
     * 获取所有地图的名称列表
     * @return 所有地图的名称列表
     */
    public List<String> getMapNames(){
        List<String> names = new ArrayList<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((map-> names.add(map.getMapName()))));
        return names;
    }

    /**
     * 获取指定游戏类型的所有地图名称
     * @param type 游戏类型
     * @return 指定类型的地图名称列表
     */
    public List<String> getMapNames(String type){
        List<String> names = new ArrayList<>();
        List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
        maps.forEach((map-> names.add(map.getMapName())));
        return names;
    }

    /**
     * 检查指定的游戏类型是否已注册
     * @param mapType 游戏类型
     * @return 如果已注册返回true
     */
    public boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    /**
     * 获取指定游戏类型的地图构造器
     * @param mapType 游戏类型
     * @return 地图构造器，如果类型未注册则返回null
     */
    @Nullable
    public Function3<ServerLevel,String, AreaData,BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    /**
     * 注册新的游戏类型
     * @param typeName 游戏类型名称
     * @param map 地图构造器
     */
    public void registerGameType(String typeName, Function3<ServerLevel,String, AreaData,BaseMap> map){
        ResourceLocation.isValidResourceLocation(typeName);
        REGISTRY.put(typeName,map);
    }

    /**
     * 检查指定游戏类型是否启用商店功能
     * @param gameType 游戏类型
     * @return 如果启用商店返回true
     */
    public boolean checkGameIsEnableShop(String gameType){
        return GAMES.containsKey(gameType) && !GAMES.get(gameType).isEmpty() && GAMES.get(gameType).get(0) instanceof ShopMap<?>;
    }

    /**
     * 获取所有启用商店功能的游戏类型
     * @return 启用商店的游戏类型列表
     */
    public List<String> getEnableShopGames(){
        return GAMES.values().stream()
                .flatMap(List::stream)
                .filter(baseMap -> baseMap instanceof ShopMap<?>)
                .map(BaseMap::getMapName)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册的游戏类型
     * @return 游戏类型列表
     */
    public List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }

    /**
     * 获取所有已注册的地图
     * @return 包含所有地图的Map，键为游戏类型
     */
    public Map<String, List<BaseMap>> getAllMaps(){
        return GAMES;
    }

    /**
     * 处理服务器tick，更新所有地图
     */
    public void onServerTick(){
        this.GAMES.forEach((type,mapList) -> mapList.forEach((map)->{
            try{
                map.mapTick();
            }catch(Exception e){
                FPSMatch.LOGGER.error("{} map error: ", map.getMapName(), e);
                map.resetGame();
            }
        }));
    }

    /**
     * 清除所有游戏数据
     */
    protected void clearData(){
        GAMES.clear();
    }

    /**
     * 检查并让玩家离开当前队伍
     * @param player 要处理的玩家
     */
    public static void checkAndLeaveTeam(ServerPlayer player){
        BaseMap map = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
        if(map != null){
            map.leave(player);
        }
    }
    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event){
        FPSMCore.getInstance().fpsmDataManager.saveData();
    }

    /**
     * 服务器启动事件处理器
     * 初始化FPSMCore并触发地图注册事件
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public static void onServerStartedEvent(ServerStartedEvent event) {
        // 设置实例
        INSTANCE = new FPSMCore(event.getServer().getWorldData().getLevelName());
        // 注册地图
        MinecraftForge.EVENT_BUS.post(new RegisterFPSMapEvent(INSTANCE));
        // 注册数据
        MinecraftForge.EVENT_BUS.post(new RegisterFPSMSaveDataEvent(INSTANCE.fpsmDataManager));
        // 读取数据
        INSTANCE.fpsmDataManager.readData();
    }

    /**
     * 处理玩家掉落物品
     * @param player 玩家
     * @param itemStack 要掉落的物品
     */
    public static void playerDropMatchItem(ServerPlayer player, ItemStack itemStack){
        RandomSource random = player.getRandom();
        DropType type = DropType.getItemDropType(itemStack);
        MatchDropEntity dropEntity = new MatchDropEntity(player.level(),itemStack,type);
        double d0 = player.getEyeY() - (double)0.3F;
        Vec3 pos = new Vec3(player.getX(), d0, player.getZ());
        dropEntity.setPos(pos);
        float f8 = Mth.sin(player.getXRot() * ((float)Math.PI / 180F));
        float f2 = Mth.cos(player.getXRot() * ((float)Math.PI / 180F));
        float f3 = Mth.sin(player.getYRot() * ((float)Math.PI / 180F));
        float f4 = Mth.cos(player.getYRot() * ((float)Math.PI / 180F));
        float f5 = random.nextFloat() * ((float)Math.PI * 2F);
        float f6 = 0.02F * random.nextFloat();
        dropEntity.setDeltaMovement((double)(-f3 * f2 * 0.3F) + Math.cos(f5) * (double)f6, -f8 * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F, (double)(f4 * f2 * 0.3F) + Math.sin(f5) * (double)f6);
        player.level().addFreshEntity(dropEntity);
    }

    /**
     * 处理玩家死亡时的武器掉落
     * @param serverPlayer 死亡的玩家
     */
    public static void playerDeadDropWeapon(ServerPlayer serverPlayer){
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(serverPlayer);
        if(map != null){
            map.getMapTeams().getTeamByPlayer(serverPlayer).ifPresent(team->{
                ItemStack itemStack = ItemStack.EMPTY;
                for(DropType type : DropType.values()){
                    if(type == DropType.MISC){
                        break;
                    }
                    if(!itemStack.isEmpty()){
                        break;
                    }
                    Predicate<ItemStack> predicate = DropType.getPredicateByDropType(type);
                    Inventory inventory = serverPlayer.getInventory();
                    List<List<ItemStack>> itemStackList = new ArrayList<>();
                    itemStackList.add(inventory.items);
                    itemStackList.add(inventory.armor);
                    itemStackList.add(inventory.offhand);
                    for(List<ItemStack> itemStacks : itemStackList){
                        for(ItemStack stack : itemStacks){
                            if (predicate.test(stack)){
                                itemStack = stack;
                                break;
                            }
                        }
                    }
                }

                if(!itemStack.isEmpty()){
                    playerDropMatchItem(serverPlayer,itemStack);
                }
            });
        }
    }

    /**
     * 获取Minecraft服务器实例
     * @return 服务器实例
     */
    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid){
        return Optional.ofNullable(this.getServer().getPlayerList().getPlayer(uuid));
    }


    public FPSMDataManager getFPSMDataManager() {
        return fpsmDataManager;
    }

    public LMManager getListenerModuleManager(){
        return listenerModuleManager;
    }

    public MVPMusicManager getMvpMusicManager() {
        return mvpMusicManager;
    }
}
