package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 提供初始装备发放功能的地图接口。
 * <p>
 * 该接口扩展了基础地图接口（IMap），为地图提供了管理初始装备（Start Kits）的功能。
 * 支持为不同队伍配置不同的初始装备，并提供发放装备给玩家或整个队伍的方法。
 */
public interface GiveStartKitsMap<T extends BaseMap> extends IMap<T> {
    /**
     * 获取指定队伍的初始装备列表。
     *
     * @param team 队伍对象
     * @return 队伍的初始装备列表
     */
    ArrayList<ItemStack> getKits(BaseTeam team);

    /**
     * 为指定队伍添加初始装备。
     *
     * @param team 队伍对象
     * @param itemStack 要添加的装备
     */
    void addKits(BaseTeam team, ItemStack itemStack);

    /**
     * 设置指定队伍的初始装备列表。
     * <p>
     * 该方法会清除队伍当前的装备列表，并添加新的装备列表。
     *
     * @param team 队伍对象
     * @param itemStack 新的装备列表
     */
    default void setTeamKits(BaseTeam team, ArrayList<ItemStack> itemStack) {
        this.clearTeamKits(team);
        this.getKits(team).addAll(itemStack);
    }

    /**
     * 设置所有队伍的初始装备列表。
     *
     * @param kits 包含所有队伍初始装备的 Map（键为队伍名称，值为装备列表）
     */
    void setStartKits(Map<String, ArrayList<ItemStack>> kits);

    /**
     * 为所有队伍添加相同的初始装备。
     *
     * @param itemStack 要添加的装备
     */
    void setAllTeamKits(ItemStack itemStack);

    /**
     * 清空指定队伍的初始装备列表。
     *
     * @param team 队伍对象
     */
    default void clearTeamKits(BaseTeam team) {
        this.getKits(team).clear();
    }

    /**
     * 从指定队伍的初始装备中移除特定物品。
     *
     * @param team 队伍对象
     * @param itemStack 要移除的物品
     * @return 如果成功移除，返回 true；否则返回 false
     */
    default boolean removeItem(BaseTeam team, ItemStack itemStack) {
        AtomicBoolean flag = new AtomicBoolean(false);
        this.getKits(team).forEach(itemStack1 -> {
            if (itemStack1.is(itemStack.getItem())) {
                itemStack1.shrink(itemStack.getCount());
                flag.set(true);
            }
        });
        return flag.get();
    }

    /**
     * 清空所有队伍的初始装备。
     */
    default void clearAllTeamKits() {
        for (BaseTeam team : this.getMap().getMapTeams().getTeams()) {
            this.clearTeamKits(team);
        }
    }

    /**
     * 获取玩家的初始装备（优先使用API装备）
     *
     * @param player 玩家对象
     * @param team 队伍对象
     * @param teamPlayers 队伍中的所有玩家列表
     * @return 初始装备列表
     */
    default ArrayList<ItemStack> getPlayerStartKits(ServerPlayer player, BaseTeam team, List<ServerPlayer> teamPlayers) {
        // 优先使用API装备
        ArrayList<ItemStack> startKitItems = new ArrayList<>();
        var config = com.phasetranscrystal.fpsmatch.mcgo.api.shopDataApi.getPlayerShopConfig(player, team.name, teamPlayers);
        if (config != null && config.startKits != null && !config.startKits.isEmpty()) {
            for (com.phasetranscrystal.fpsmatch.mcgo.api.shopDataApi.ItemStackData itemData : config.startKits) {
                if (itemData == null) continue;
                ItemStack itemStack = com.phasetranscrystal.fpsmatch.mcgo.api.shopDataApi.createItemStack(itemData);
                if (itemStack != null) {
                    startKitItems.add(itemStack);
                }
            }
        }

        // 如果API装备为空，则使用默认装备
        if (startKitItems.isEmpty()) {
            startKitItems.addAll(this.getKits(team));
        }

        // 为所有武器设置正确的子弹数量
        startKitItems.forEach(itemStack -> {
            if (itemStack.getItem() instanceof IGun iGun) {
                FPSMUtil.fixGunItem(itemStack, iGun);
            }
        });

        return startKitItems;
    }

    /**
     * 为指定玩家发放初始装备。
     * <p>
     * 该方法会清空玩家当前的背包，并添加队伍的初始装备。
     *
     * @param player 玩家对象
     */
    default void givePlayerKits(ServerPlayer player) {
        BaseMap map = this.getMap();
        map.getMapTeams().getTeamByPlayer(player).ifPresentOrElse(team->{
                    // 获取队伍中的所有玩家，用于批量请求API配置
                    List<ServerPlayer> teamPlayers = new ArrayList<>();
                    for (UUID uuid : team.getPlayerList()) {
                        Player p = map.getServerLevel().getPlayerByUUID(uuid);
                        if (p instanceof ServerPlayer sp) {
                            teamPlayers.add(sp);
                        }
                    }

                    // 获取初始装备
                    ArrayList<ItemStack> startKitItems = getPlayerStartKits(player, team, teamPlayers);

                    // 清空玩家背包
                    player.getInventory().clearContent();

                    // 发放装备
                    startKitItems.forEach(itemStack -> {
                        ItemStack copy = itemStack.copy();
                        // 如果是武器，设置正确的子弹数量
                        if (copy.getItem() instanceof IGun iGun) {
                            FPSMUtil.fixGunItem(copy, iGun);
                        }
                        player.getInventory().add(copy);
                    });

                    // 更新物品栏
                    player.inventoryMenu.broadcastChanges();
                    player.inventoryMenu.slotsChanged(player.getInventory());
                    FPSMUtil.sortPlayerInventory(player);
                },()->
                        System.out.println("givePlayerKits: player not in team ->" + player.getDisplayName().getString())
        );
    }

    /**
     * 为指定队伍的所有玩家发放初始装备。
     *
     * @param team 队伍对象
     */
    default void giveTeamKits(@NotNull BaseTeam team) {
        BaseMap map = this.getMap();
        for (UUID uuid : team.getPlayerList()) {
            Player player = map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null) {
                ArrayList<ItemStack> items = this.getKits(team);
                player.getInventory().clearContent();
                items.forEach(itemStack -> {
                    ItemStack copy = itemStack.copy();
                    // 如果是武器，设置正确的子弹数量
                    if (copy.getItem() instanceof IGun iGun) {
                        FPSMUtil.fixGunItem(copy, iGun);
                    }
                    player.getInventory().add(copy);
                });
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.slotsChanged(player.getInventory());
            }
        }
    }

    /**
     * 为所有玩家发放初始装备。
     */
    default void giveAllPlayersKits() {
        BaseMap map = this.getMap();

        // 按队伍收集玩家，用于批量API请求
        Map<BaseTeam, List<ServerPlayer>> teamPlayersMap = new HashMap<>();
        for (PlayerData data : this.getMap().getMapTeams().getJoinedPlayers()) {
            data.getPlayer().ifPresent(player -> {
                map.getMapTeams().getTeamByPlayer((ServerPlayer) player).ifPresent(team -> {
                    teamPlayersMap.computeIfAbsent(team, k -> new ArrayList<>()).add((ServerPlayer) player);
                });
            });
        }

        // 为每个队伍的玩家发放装备
        for (Map.Entry<BaseTeam, List<ServerPlayer>> entry : teamPlayersMap.entrySet()) {
            BaseTeam team = entry.getKey();
            List<ServerPlayer> teamPlayers = entry.getValue();

            // 为每个玩家发放装备
            for (ServerPlayer player : teamPlayers) {
                // 获取初始装备
                ArrayList<ItemStack> startKitItems = getPlayerStartKits(player, team, teamPlayers);

                // 清空玩家背包
                player.getInventory().clearContent();

                // 发放装备
                startKitItems.forEach(itemStack -> {
                    ItemStack copy = itemStack.copy();
                    // 如果是武器，设置正确的子弹数量
                    if (copy.getItem() instanceof IGun iGun) {
                        FPSMUtil.fixGunItem(copy, iGun);
                    }
                    if(copy.getItem() instanceof ArmorItem armorItem){
                        player.setItemSlot(armorItem.getEquipmentSlot(), copy);
                    }else{
                        player.getInventory().add(copy);
                    }
                });

                // 更新物品栏并排序
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.slotsChanged(player.getInventory());
                FPSMUtil.sortPlayerInventory(player);
            }
        }
    }

    /**
     * 获取所有队伍的初始装备列表。
     *
     * @return 包含所有队伍初始装备的 Map（键为队伍名称，值为装备列表）
     */
    Map<String, List<ItemStack>> getStartKits();
}
