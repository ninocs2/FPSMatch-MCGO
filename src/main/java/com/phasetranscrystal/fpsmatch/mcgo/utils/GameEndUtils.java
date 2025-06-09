package com.phasetranscrystal.fpsmatch.mcgo.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 游戏结束工具类
 * 处理比赛胜利后的玩家踢出逻辑
 */
@Mod.EventBusSubscriber
public class GameEndUtils {

    private static final int TOTAL_TICKS = 200; // 10秒 = 600 ticks
    private static int remainingTicks = TOTAL_TICKS;
    private static boolean isCountingDown = false;
    private static MinecraftServer currentServer = null;
    private static boolean hasSentInitialMessage = false;

    /**
     * 处理比赛结束后的玩家踢出
     * @param server 服务器实例
     */
    public static void handleGameEnd(MinecraftServer server) {
        remainingTicks = TOTAL_TICKS;
        isCountingDown = true;
        currentServer = server;
        hasSentInitialMessage = false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!isCountingDown || currentServer == null) return;

        if (event.phase == TickEvent.Phase.END) {
            if (!hasSentInitialMessage) {
                sendCountdownMessage(currentServer, remainingTicks / 20);
                hasSentInitialMessage = true;
                return;
            }

            remainingTicks--;
            if (remainingTicks % 20 == 0) { // 每秒只发送一次消息
                int seconds = remainingTicks / 20;
                if (seconds > 0) {
                    sendCountdownMessage(currentServer, seconds);
                } else {
                    kickAllPlayers(currentServer);
                    isCountingDown = false;
                    currentServer = null;
                }
            }
        }
    }

    /**
     * 向所有玩家发送倒计时消息
     * @param server 服务器实例
     * @param seconds 剩余秒数
     */
    private static void sendCountdownMessage(MinecraftServer server, int seconds) {
        Component message = Component.literal("服务器将在 ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.valueOf(seconds))
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal(" 秒后关闭")
                        .withStyle(ChatFormatting.GREEN));

        server.getPlayerList().getPlayers().forEach(player -> {
            player.displayClientMessage(message, false);
        });
    }

    /**
     * 踢出所有玩家
     * @param server 服务器实例
     */
    private static void kickAllPlayers(MinecraftServer server) {
        Component kickMessage = Component.literal("比赛已结束，您已被踢出服务器")
                .withStyle(ChatFormatting.RED);
        server.getPlayerList().getPlayers().forEach(player ->
                player.connection.disconnect(kickMessage)
        );
    }
}