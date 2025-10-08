package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.mcgo.api.queryUserXtnInfoApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class GameTabStatsS2CPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameTabStatsS2CPacket.class);
    private static final Set<String> requestedPlayers = new HashSet<>();
    
    /**
     * 清理已请求的玩家记录，用于游戏重新开始或玩家重新加入时
     */
    public static void clearRequestedPlayers() {
        synchronized (requestedPlayers) {
            requestedPlayers.clear();
            LOGGER.info("已清理玩家MVP音乐请求记录");
        }
    }
    
    private final UUID uuid;
    private final PlayerData data;
    private final String team;

    public GameTabStatsS2CPacket(UUID uuid, PlayerData data, String team) {
        this.uuid = uuid;
        this.data = data;
        this.team = team;
    }

    public GameTabStatsS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        Component name = buf.readComponent();
        int kills = buf.readInt();
        int _kills = buf.readInt();
        int deaths = buf.readInt();
        int _deaths = buf.readInt();
        int assists = buf.readInt();
        int _assists = buf.readInt();
        float damage = buf.readFloat();
        float _damage = buf.readFloat();
        int scores = buf.readInt();
        boolean isLiving = buf.readBoolean();
        int mvp = buf.readInt();
        float hp = buf.readFloat();
        PlayerData data = new PlayerData(this.uuid,name);
        data.setKills(kills);
        data.set_kills(_kills);
        data.setDeaths(deaths);
        data.set_deaths(_deaths);
        data.setAssists(assists);
        data.set_assists(_assists);
        data.setDamage(damage);
        data.set_damage(_damage);
        data.set_deaths(_deaths);
        data.set_assists(_assists);
        data.setScores(scores);
        data.setLiving(isLiving);
        data.setMvpCount(mvp);
        data.setHp(hp);
        this.data = data;
        this.team = buf.readUtf();
    }

    public static void encode(GameTabStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
        buf.writeComponent(packet.data.name());
        buf.writeInt(packet.data.getKills());
        buf.writeInt(packet.data._kills());
        buf.writeInt(packet.data.getDeaths());
        buf.writeInt(packet.data._deaths());
        buf.writeInt(packet.data.getAssists());
        buf.writeInt(packet.data._assists());
        buf.writeFloat(packet.data.getDamage());
        buf.writeFloat(packet.data._damage());
        buf.writeInt(packet.data.getScores());
        buf.writeBoolean(packet.data.isLiving());
        buf.writeInt(packet.data.getMvpCount());
        buf.writeFloat(packet.data.healthPercent());
        buf.writeUtf(packet.team);
    }

    public static GameTabStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new GameTabStatsS2CPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null && uuid.equals(Minecraft.getInstance().player.getUUID())) {
                if (!FPSMClient.getGlobalData().equalsTeam(team)) {
                    FPSMClient.getGlobalData().setCurrentTeam(team);
                }
            }
            FPSMClient.getGlobalData().setTabData(uuid,team,data);
            
            // 立即获取新加入玩家的MVP音乐信息
            requestPlayerMvpInfo();
        });
        ctx.get().setPacketHandled(true);
    }
    
    /**
     * 立即获取新加入玩家的MVP音乐信息
     */
    private void requestPlayerMvpInfo() {
        try {
            String playerName = data.name().getString();
            
            // 检查是否已经请求过该玩家的信息，避免重复请求
            synchronized (requestedPlayers) {
                if (requestedPlayers.contains(playerName)) {
                    return; // 已经请求过，跳过
                }
                requestedPlayers.add(playerName);
            }
            
            LOGGER.info("检测到新玩家加入: {}, 开始获取MVP音乐信息", playerName);
            
            // 异步请求单个玩家的数据
            new Thread(() -> {
                try {
                    queryUserXtnInfoApi api = new queryUserXtnInfoApi();
                    api.queryPlayerInfo(Collections.singletonList(playerName)).ifPresentOrElse(
                        response -> {
                            LOGGER.info("成功获取玩家 {} 的扩展信息，响应代码: {}", playerName, response.getCode());
                        },
                        () -> LOGGER.warn("获取玩家 {} 的扩展信息失败", playerName)
                    );
                } catch (Exception e) {
                    LOGGER.error("请求玩家 {} 的数据时发生异常", playerName, e);
                }
            }, "PlayerMvpInfoRequest-" + playerName).start();
            
        } catch (Exception e) {
            LOGGER.error("准备玩家MVP信息请求时发生异常", e);
        }
    }
}