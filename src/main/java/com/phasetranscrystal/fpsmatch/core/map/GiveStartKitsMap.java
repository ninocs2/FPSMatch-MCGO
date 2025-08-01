package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
     * 为指定玩家发放初始装备。
     * <p>
     * 该方法会清空玩家当前的背包，并添加队伍的初始装备。
     *
     * @param player 玩家对象
     */
    default void givePlayerKits(ServerPlayer player) {
        BaseMap map = this.getMap();
        map.getMapTeams().getTeamByPlayer(player).ifPresentOrElse(team -> {
                    // 获取队伍所有玩家（用于API批量请求）
                    List<ServerPlayer> teamPlayers = team.getPlayerList().stream()
                            .map(uuid -> map.getServerLevel().getPlayerByUUID(uuid))
                            .filter(Objects::nonNull)
                            .map(p -> (ServerPlayer) p)
                            .collect(Collectors.toList());

                    // 优先用API装备
                    ArrayList<ItemStack> startKitItems = new ArrayList<>();
                    var config = com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils.getPlayerShopConfig(player, team.name, teamPlayers);
                    if (config != null && config.startKits != null && !config.startKits.isEmpty()) {
                        for (com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils.ItemStackData itemData : config.startKits) {
                            if (itemData == null) continue;
                            ItemStack itemStack = com.phasetranscrystal.fpsmatch.utils.GameDataApiUtils.createItemStack(itemData);
                            if (itemStack != null) {
                                startKitItems.add(itemStack);
                            }
                        }
                    } else {
                        // fallback到本地配置
                        startKitItems = this.getKits(team);
                    }

                    // 清空背包并发放
                    player.getInventory().clearContent();
                    for (ItemStack itemStack : startKitItems) {
                        player.getInventory().add(itemStack.copy());
                    }
                    player.inventoryMenu.broadcastChanges();
                    player.inventoryMenu.slotsChanged(player.getInventory());
                    com.phasetranscrystal.fpsmatch.util.FPSMUtil.sortPlayerInventory(player);

                    // 补充所有枪械弹药（修正调用方式）
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                            com.phasetranscrystal.fpsmatch.util.FPSMUtil.resetGunAmmo(stack, iGun);
                        }
                    }
                }, () ->
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
                items.forEach(itemStack ->
                        player.getInventory().add(itemStack.copy())
                );
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
        for (PlayerData data : this.getMap().getMapTeams().getJoinedPlayers()) {
            data.getPlayer().ifPresent(this::givePlayerKits);
        }
    }

    /**
     * 获取所有队伍的初始装备列表。
     *
     * @return 包含所有队伍初始装备的 Map（键为队伍名称，值为装备列表）
     */
    Map<String, List<ItemStack>> getStartKits();
}