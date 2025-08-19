package com.phasetranscrystal.fpsmatch.mcgo.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.phasetranscrystal.fpsmatch.FPSMatch;

import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.mcgo.config.APIConfig;
import com.phasetranscrystal.fpsmatch.mcgo.util.SSLUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏数据API工具类
 * 用于向后端发送游戏结果数据
 * 仅在专用服务器端加载和运行
 */
@OnlyIn(Dist.DEDICATED_SERVER)
public class shopDataApi {
    // API配置
    private static final APIConfig apiConfig;
    private static final Gson gson = new Gson();

    // 商店配置缓存
    private static final Map<String, ShopConfigResponse> PLAYER_SHOP_CONFIG_CACHE = new HashMap<>();

    static {
        // 初始化SSL配置
        SSLUtils.initGlobalSSL();
        // 加载API配置
        APIConfig.loadApiConfig();
        apiConfig = APIConfig.getInstance();
    }

    private static class ShopConfigRequest {
        String team;
        List<String> playerIds;

        ShopConfigRequest(String team, List<String> playerIds) {
            this.team = team;
            this.playerIds = playerIds;
        }
    }



    /**
     * API响应数据结构
     */
    public static class ApiResponse<T> {
        public String code;
        public T data;
        public String message;
    }

    /**
     * 商店配置响应数据结构
     */
    public static class ShopConfigData {
        public String playerId;
        public Map<String, List<ShopItem>> shopData;
        public List<ItemStackData> startKits;
    }

    public static class ItemStackData {
        @SerializedName("id")
        public String id;
        @SerializedName("Count")
        public String count;
        @SerializedName("tag")
        public Map<String, Object> tag;  // 使用 Object 类型来处理复杂的 NBT 数据
    }

    public static class ShopItem {
        public String id;
        public String name;
        public String defaultCost;     // 改回 String 类型，因为从 API 返回的是字符串
        public String maxBuyCount;     // 改回 String 类型
        public String groupId;         // 改回 String 类型
        public List<String> listenerModule;
        @SerializedName("ItemStack")
        public ItemStackData itemStack;
    }

    public static class ShopConfigResponse {
        public Map<String, List<ShopItem>> shopData;
        public List<ItemStackData> startKits;
    }

    /**
     * 获取玩家商店配置 (支持批量)
     *
     * @param player      当前请求的玩家
     * @param teamPlayers 队伍中的所有玩家，用于批量请求
     * @param teamName    队伍名称
     * @return 商店配置
     */
    public static ShopConfigResponse getPlayerShopConfig(ServerPlayer player, String teamName, List<ServerPlayer> teamPlayers) {
        String cacheKey = player.getUUID().toString() + "_" + teamName;

        // 先从缓存获取
        if (PLAYER_SHOP_CONFIG_CACHE.containsKey(cacheKey)) {
            ShopConfigResponse cachedConfig = PLAYER_SHOP_CONFIG_CACHE.get(cacheKey);
            if (cachedConfig != null) {
                FPSMatch.LOGGER.debug("[商店配置] 从缓存获取配置: player={}, team={}", player.getName().getString(), teamName);
                return cachedConfig;
            } else {
                FPSMatch.LOGGER.debug("[商店配置] 缓存中为null，将使用默认配置: player={}, team={}", player.getName().getString(), teamName);
                return null;
            }
        }

        try {
            // 为整个队伍进行批量请求
            FPSMatch.LOGGER.info("[商店配置] 开始批量请求: team={}, player={}", teamName, player.getName().getString());
            fetchTeamShopConfig(teamPlayers, teamName);

            // 从缓存中获取结果
            if (PLAYER_SHOP_CONFIG_CACHE.containsKey(cacheKey)) {
                ShopConfigResponse config = PLAYER_SHOP_CONFIG_CACHE.get(cacheKey);
                if (config != null) {
                    FPSMatch.LOGGER.info("[商店配置] 获取配置成功: player={}, team={}", player.getName().getString(), teamName);
                    return config;
                } else {
                    FPSMatch.LOGGER.info("[商店配置] 配置为null，使用默认配置: player={}, team={}", player.getName().getString(), teamName);
                    return null;
                }
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("[商店配置] 获取商店配置时发生错误: player={}, team={}, error={}",
                    player.getName().getString(), teamName, e.getMessage(), e);
        }

        // 如果获取失败，设置null缓存并返回null
        PLAYER_SHOP_CONFIG_CACHE.put(cacheKey, null);
        FPSMatch.LOGGER.warn("[商店配置] 获取失败，使用默认配置: player={}, team={}", player.getName().getString(), teamName);
        return null;
    }

    /**
     * 为整个队伍批量获取商店配置并缓存
     *
     * @param teamPlayers 队伍中的玩家列表
     * @param teamName    队伍名称
     */
    public static void fetchTeamShopConfig(List<ServerPlayer> teamPlayers, String teamName) {

        try (CloseableHttpClient httpClient = SSLUtils.createTrustAllHttpClient()) {
            String url = buildApiUrl(apiConfig.getApiEndpoint(), apiConfig.getWeaponConfigure());
            HttpPost httpPost = new HttpPost(url);

            // 添加认证头
            if (apiConfig.getApiAuthHeader() != null && !apiConfig.getApiAuthHeader().isEmpty()) {
                httpPost.setHeader(apiConfig.getApiAuthHeader(), apiConfig.getApiAuthValue());
            }

            // 构建请求体
            List<String> playerIds = teamPlayers.stream().map(p -> p.getName().getString()).collect(Collectors.toList());
            ShopConfigRequest requestBody = new ShopConfigRequest(teamName, playerIds);
            String jsonData = gson.toJson(requestBody);
            StringEntity entity = new StringEntity(jsonData, "UTF-8");
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-Type", "application/json");

            FPSMatch.LOGGER.info("正在批量请求商店配置: team={}, players={}", teamName, playerIds);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode == 200) {
                    try {
                        ApiResponse<Map<String, ShopConfigData>> responseData = gson.fromJson(responseBody,
                                new TypeToken<ApiResponse<Map<String, ShopConfigData>>>() {}.getType());

                        if (responseData != null && responseData.data != null) {
                            // 遍历返回的所有玩家配置
                            for (Map.Entry<String, ShopConfigData> entry : responseData.data.entrySet()) {
                                String playerName = entry.getKey();
                                ShopConfigData playerConfigData = entry.getValue();

                                // 找到对应的ServerPlayer对象
                                ServerPlayer currentPlayer = teamPlayers.stream()
                                        .filter(p -> p.getName().getString().equals(playerName))
                                        .findFirst().orElse(null);

                                if (currentPlayer != null && playerConfigData != null) {
                                    // 转换为内部使用的格式
                                    ShopConfigResponse config = new ShopConfigResponse();
                                    config.shopData = playerConfigData.shopData;
                                    config.startKits = playerConfigData.startKits;

                                    // 为每个玩家缓存配置
                                    String cacheKey = currentPlayer.getUUID() + "_" + teamName;
                                    PLAYER_SHOP_CONFIG_CACHE.put(cacheKey, config);
                                    FPSMatch.LOGGER.info("[商店配置] 已缓存玩家 {} 的配置", playerName);
                                }
                            }
                            FPSMatch.LOGGER.info("[商店配置] 队伍 {} 的配置获取成功，共 {} 个玩家", teamName, responseData.data.size());
                        } else {
                            FPSMatch.LOGGER.warn("[商店配置] API返回空数据 - 队伍={}", teamName);
                        }
                    } catch (Exception e) {
                        FPSMatch.LOGGER.error("[商店配置] 解析响应失败 - 队伍={}, 错误={}", teamName, e.getMessage(), e);
                    }
                } else {
                    FPSMatch.LOGGER.error("获取商店配置失败: statusCode={}, team={}", statusCode, teamName);
                }
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("批量获取商店配置时发生错误: team={}, error={}", teamName, e.getMessage(), e);
        }

        // 如果API请求失败，为所有玩家设置null配置
        for (ServerPlayer player : teamPlayers) {
            String cacheKey = player.getUUID() + "_" + teamName;
            if (!PLAYER_SHOP_CONFIG_CACHE.containsKey(cacheKey)) {
                PLAYER_SHOP_CONFIG_CACHE.put(cacheKey, null);
                FPSMatch.LOGGER.info("[商店配置] 为玩家 {} 设置null配置，将使用默认配置", player.getName().getString());
            }
        }
    }

    /**
     * 清除指定玩家的商店配置缓存
     * @param player 玩家
     * @param teamName 队伍名称
     */
    public static void clearPlayerShopConfigCache(ServerPlayer player, String teamName) {
        String cacheKey = player.getUUID().toString() + "_" + teamName;
        PLAYER_SHOP_CONFIG_CACHE.remove(cacheKey);
        FPSMatch.LOGGER.info("[商店配置] 已清除玩家 {} 队伍 {} 的配置缓存",
                player.getName().getString(), teamName);
    }

    /**
     * 清除所有玩家的商店配置缓存
     */
    public static void clearAllShopConfigCache() {
        PLAYER_SHOP_CONFIG_CACHE.clear();
        FPSMatch.LOGGER.info("[商店配置] 已清除所有玩家的配置缓存");
    }

    /**
     * 清除指定队伍的所有玩家商店配置缓存
     * @param teamName 队伍名称
     */
    public static void clearTeamShopConfigCache(String teamName) {
        PLAYER_SHOP_CONFIG_CACHE.entrySet().removeIf(entry ->
                entry.getKey().endsWith("_" + teamName));
        FPSMatch.LOGGER.info("[商店配置] 已清除队伍 {} 的所有玩家配置缓存", teamName);
    }

    /**
     * 转换商店配置
     */
    public static Map<String, ArrayList<ShopSlot>> convertShopConfig(ShopConfigResponse config) {
        try {
            Map<String, ArrayList<ShopSlot>> result = new HashMap<>();

            // 检查config和shopData是否为null
            if (config == null || config.shopData == null) {
                FPSMatch.LOGGER.error("商店配置或shopData为null");
                return createDefaultEmptyConfig();
            }

            // 记录API返回的类型
            //FPSMatch.LOGGER.info("[商店配置] API返回的物品类型: {}", config.shopData.keySet());

            config.shopData.forEach((typeString, items) -> {
                // 直接使用字符串作为键，不再转换为ItemType枚举
                String type = typeString.toUpperCase();

                ArrayList<ShopSlot> slots = new ArrayList<>();

                // 检查items是否为null
                if (items == null) {
                    FPSMatch.LOGGER.warn("物品类型 {} 的items为null", type);
                    // 添加5个空槽位
                    for (int i = 0; i < 5; i++) {
                        slots.add(new ShopSlot(ItemStack.EMPTY, 0));
                    }
                } else {
                    items.forEach(item -> {
                        // 检查item和itemStack是否为null
                        if (item == null || item.itemStack == null) {
                            FPSMatch.LOGGER.warn("物品或itemStack为null");
                            slots.add(new ShopSlot(ItemStack.EMPTY, 0));
                            return;
                        }

                        // 处理物品堆
                        final ItemStack itemStack;
                        if (item.itemStack.id == null || item.itemStack.id.equals("minecraft:air")) {
                            // 空槽位特殊处理
                            itemStack = new ItemStack(Items.AIR, 0);
                        } else {
                            ItemStack tempItemStack;
                            try {
                                tempItemStack = new ItemStack(
                                        Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.itemStack.id))),
                                        safeParseInt(item.itemStack.count)
                                );

                                // 处理 NBT
                                if (item.itemStack.tag != null) {
                                    CompoundTag nbt = new CompoundTag();
                                    Map<String, Object> tag = item.itemStack.tag;

                                    // 转换 NBT 标签大小写
                                    if (tempItemStack.getItem() instanceof IGun) {
                                        addTagData(nbt, tag);
                                    } else {
                                        // 其他物品的 NBT
                                        addTagData(nbt, tag);
                                    }
                                    tempItemStack.setTag(nbt);
                                }
                            } catch (Exception e) {
                                FPSMatch.LOGGER.error("创建物品堆时发生错误: {}", e.getMessage());
                                tempItemStack = new ItemStack(Items.AIR, 0);
                            }
                            itemStack = tempItemStack;
                        }

                        // 创建商店槽位，使用安全的数值转换
                        ShopSlot slot = new ShopSlot(
                                itemStack::copy,
                                safeParseInt(item.defaultCost),
                                safeParseInt(item.maxBuyCount),
                                safeParseInt(item.groupId),
                                stack -> ItemStack.isSameItemSameTags(stack, itemStack)
                        );

                        slots.add(slot);
                    });
                }

                // 确保当前类型有5个槽位，不足则用空槽位填充
                while (slots.size() < 5) {
                    slots.add(new ShopSlot(ItemStack.EMPTY, 0));
                }

                result.put(type, slots);
            });
            return result;

        } catch (Exception e) {
            FPSMatch.LOGGER.error("转换商店配置时发生错误: {}", e.getMessage(), e);
            return createDefaultEmptyConfig();
        }
    }

    /**
     * 创建默认的空配置
     * 确保每个物品类型都有5个空槽位
     */
    private static Map<String, ArrayList<ShopSlot>> createDefaultEmptyConfig() {
        Map<String, ArrayList<ShopSlot>> result = new HashMap<>();
        // 定义商店类型，不再依赖ItemType枚举
        String[] shopTypes = {"EQUIPMENT", "PISTOL", "MID_RANK", "RIFLE", "THROWABLE"};
        for (String type : shopTypes) {
            ArrayList<ShopSlot> slots = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                slots.add(new ShopSlot(ItemStack.EMPTY, 0));
            }
            result.put(type, slots);
        }
        return result;
    }

    /**
     * 安全地将字符串转换为整数
     * 处理浮点数字符串和其他特殊情况
     */
    private static int safeParseInt(String value) {
        try {
            // 如果是浮点数字符串，先转换为 double 再取整
            if (value.contains(".")) {
                return (int) Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            FPSMatch.LOGGER.warn("数值转换失败，使用默认值0: {}", value);
            return 0;
        }
    }

    /**
     * 根据物品数据创建ItemStack
     */
    public static ItemStack createItemStack(ItemStackData itemStackData) {
        try {
            // 创建物品
            ResourceLocation resourceLocation = new ResourceLocation(itemStackData.id);
            Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
            if (item == null) {
                FPSMatch.LOGGER.error("[物品创建] 找不到物品: {}", itemStackData.id);
                return null;
            }

            // 创建ItemStack
            ItemStack itemStack = new ItemStack(item, Integer.parseInt(itemStackData.count));

            // 如果有NBT标签，添加到物品上
            if (itemStackData.tag != null) {
                CompoundTag nbt = new CompoundTag();
                addTagData(nbt, itemStackData.tag);
                itemStack.setTag(nbt);
            }

            return itemStack;
        } catch (Exception e) {
            FPSMatch.LOGGER.error("[物品创建] 失败 - id={}, error={}",
                    itemStackData.id, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 递归添加NBT标签数据
     */
    private static void addTagData(CompoundTag nbt, Map<String, Object> tagData) {
        for (Map.Entry<String, Object> entry : tagData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                nbt.putString(key, (String) value);
            } else if (value instanceof Number num) {
                if (value instanceof Double || value instanceof Float) {
                    nbt.putDouble(key, num.doubleValue());
                } else {
                    nbt.putLong(key, num.longValue());
                }
            } else if (value instanceof Boolean) {
                nbt.putBoolean(key, (Boolean) value);
            } else if (value instanceof Map) {
                // 处理嵌套的NBT数据
                CompoundTag compound = new CompoundTag();
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                addTagData(compound, mapValue);
                nbt.put(key, compound);
            }
        }
    }

    /**
     * 构建正确的API URL
     * 确保API端点和路径之间只有一个斜杠
     */
    private static String buildApiUrl(String endpoint, String path) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "";
        }

        // 确保endpoint以斜杠结尾
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/";

        // 确保path不以斜杠开头
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        // 根据您提供的正确URL示例可能需要特殊处理，例如如果不需要apiEndpoint和path之间的斜杠
        // 直接使用完整的URL
        if (path.startsWith("http")) {
            return path;
        }

        // 返回修正后的URL
        return normalizedEndpoint + normalizedPath;
    }
}