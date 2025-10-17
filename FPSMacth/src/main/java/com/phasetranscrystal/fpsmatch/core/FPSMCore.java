package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.event.FPSMReloadEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class FPSMCore {
    private static FPSMCore INSTANCE;
    public final String archiveName;
    private final Map<String, List<BaseMap>> GAMES = new HashMap<>();
    private final Map<String, Function3<ServerLevel,String,AreaData, BaseMap>> REGISTRY = new HashMap<>();
    private final FPSMDataManager fpsmDataManager;
    private final LMManager listenerModuleManager;

    private FPSMCore(String archiveName) {
        this.archiveName = archiveName;
        this.listenerModuleManager = new LMManager();
        this.fpsmDataManager = new FPSMDataManager(archiveName);
    }

    public static FPSMCore getInstance(){
        if(INSTANCE == null) throw new RuntimeException("fpsm not install.");
        return INSTANCE;
    }

    public static boolean initialized(){
        return INSTANCE != null;
    }

    public Optional<BaseMap> getMapByPlayer(Player player){
        for (List<BaseMap> list : GAMES.values()) {
            for (BaseMap map : list){
                if(map.checkGameHasPlayer(player)) return Optional.of(map);
            }
        }
        return Optional.empty();
    }

    public Optional<BaseMap> getMapByPlayerWithSpec(Player player){
        for (List<BaseMap> list : GAMES.values()) {
            for (BaseMap map : list){
                if(map.checkGameHasPlayer(player) || map.checkSpecHasPlayer(player)) return Optional.of(map);
            }
        }
        return Optional.empty();
    }

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

    public Optional<BaseMap> getMapByName(String name){
        for (List<BaseMap> list : GAMES.values()) {
            for (BaseMap map : list){
                if(map.getMapName().equals(name)) return Optional.of(map);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
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

    public List<String> getMapNames(){
        List<String> names = new ArrayList<>();
        GAMES.forEach((type,mapList)-> mapList.forEach((map-> names.add(map.getMapName()))));
        return names;
    }

    public List<String> getMapNames(String type){
        List<String> names = new ArrayList<>();
        List<BaseMap> maps = GAMES.getOrDefault(type,new ArrayList<>());
        maps.forEach((map-> names.add(map.getMapName())));
        return names;
    }

    public boolean checkGameType(String mapType){
       return REGISTRY.containsKey(mapType);
    }

    @Nullable public Function3<ServerLevel,String, AreaData, BaseMap> getPreBuildGame(String mapType){
         if(checkGameType(mapType)) return REGISTRY.get(mapType);
         return null;
    }

    public void registerGameType(String typeName, Function3<ServerLevel,String, AreaData, BaseMap> map){
        ResourceLocation.isValidResourceLocation(typeName);
        REGISTRY.put(typeName,map);
    }

    public boolean checkGameIsEnableShop(String gameType){
        return GAMES.containsKey(gameType) && !GAMES.get(gameType).isEmpty() && GAMES.get(gameType).get(0) instanceof ShopMap<?>;
    }

    public List<String> getEnableShopGames(String gameType){
        List<String> enableShopGames = new ArrayList<>();
        if (checkGameIsEnableShop(gameType)){
            GAMES.get(gameType).forEach((map)->{
                enableShopGames.add(map.getMapName());
            });
        }
        return enableShopGames;
    }

    public List<String> getGameTypes(){
        return REGISTRY.keySet().stream().toList();
    }


    public Map<String, List<BaseMap>> getAllMaps(){
        return GAMES;
    }

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

    protected void clearData(){
        GAMES.clear();
        REGISTRY.clear();
    }

    public static void checkAndLeaveTeam(ServerPlayer player){
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
        map.ifPresent(baseMap -> baseMap.leave(player));
    }
    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event){
        FPSMCore.getInstance().fpsmDataManager.saveData();
    }

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


    @SubscribeEvent
    public static void onReloadEvent(FPSMReloadEvent event) {
        for (List<BaseMap> maps : INSTANCE.GAMES.values()) {
            for (BaseMap map : maps) {
                try{
                    map.reload();
                }catch (Exception e){
                    FPSMatch.LOGGER.error("{} map reload error: ", map.getMapName(), e);
                }
            }
        }
    }

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

}
