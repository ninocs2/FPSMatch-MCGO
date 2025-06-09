package com.phasetranscrystal.fpsmatch.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.phasetranscrystal.fpsmatch.utils.MvpMusicUtils;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.HashMap;
import com.phasetranscrystal.fpsmatch.utils.ServerUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ConcurrentHashMap;
import java.io.InputStream;
import java.util.Set;
import java.util.HashSet;

import static com.phasetranscrystal.fpsmatch.util.RenderUtil.color;

/**
 * CS风格游戏界面HUD渲染类
 * 主要负责渲染:
 * - 中央计分板和时间显示
 * - 两侧玩家头像
 * - 游戏状态信息
 */
public class CSGameOverlay implements IGuiOverlay {
    // GUI资源位置（用于进度条等元素）
    private static final ResourceLocation GUI_BARS_LOCATION = new ResourceLocation("textures/gui/bars.png");

    // 游戏时间常量（单位：秒）
    public static final int PAUSE_TIME = 60;        // 暂停时间
    public static final int WINNER_WAITING_TIME = 8; // 胜利等待时间
    public static final int WARM_UP_TIME = 60;      // 热身时间
    public static final int WAITING_TIME = 15;      // 等待时间
    public static final int ROUND_TIME_LIMIT = 115; // 回合时间限制

    // 颜色常量（ARGB格式）
    public static int textCTWinnerRoundsColor = color(182, 210, 240);  // CT方颜色
    public static int textTWinnerRoundsColor = color(253,217,141);     // T方颜色
    public static int noColor = color(0,0,0,0);                        // 透明色
    public static int textRoundTimeColor = color(255,255,255);         // 时间显示颜色

    // 拆弹密码
    public static final String code = "7355608";  // CS经典拆弹密码

    // 添加缓存头像的静态Map
    private static final Map<UUID, ResourceLocation> playerHeadTextures = new HashMap<>();
    private static final Map<UUID, Long> textureLastUpdateTime = new HashMap<>();
    // 添加纹理更新时间间隔常量 - 60秒
    private static final long TEXTURE_UPDATE_INTERVAL = 60000;

    // 添加玩家名称缓存
    private static final Map<UUID, String> playerNameCache = new HashMap<>();
    private static final Map<UUID, Long> nameLastUpdateTime = new HashMap<>();

    // 添加ServerUtils调用控制
    private static long lastServerUtilsCall = 0;
    private static final long SERVER_UTILS_CALL_INTERVAL = 300000; // 3分钟间隔

    // 将BORDER_COLORS改为public以供其他类使用
    public static final int[] BORDER_COLORS = new int[] {
            color(137, 206, 245),   // 蓝色 - 队伍中的第1名玩家
            color(0, 159, 129),     // 绿色 - 队伍中的第2名玩家
            color(190, 45, 151),    // 紫色 - 队伍中的第3名玩家
            color(241, 228, 66),    // 黄色 - 队伍中的第4名玩家
            color(230, 129, 43)     // 橙色 - 队伍中的第5名玩家
    };

    // 添加下载状态跟踪
    private static final Map<String, Boolean> downloadingTextures = new ConcurrentHashMap<>();

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        // 非游戏地图时不渲染
        if (ClientData.currentMap.equals("fpsm_none")) return;
        // 基础设置
        Font font = Minecraft.getInstance().font;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(mc.player == null) return;

        // UI缩放计算（基于855x480的基准分辨率）
        float scaleFactor = Math.min(screenWidth / 855.0f, screenHeight / 480.0f);

        // 布局参数计算
        int centerX = screenWidth / 2;  // 屏幕中心X坐标
        int startY = (int)(2 * scaleFactor);  // 顶部起始位置
        int backgroundHeight = (int)(35 * scaleFactor);  // 背景高度
        int timeBarHeight = (int)(15 * scaleFactor);  // 时间条高度
        int scoreBarHeight = (int)(30 * scaleFactor);  // 比分条高度
        int boxWidth = (int)(24 * scaleFactor);  // 头像区域宽度

        // 间距计算
        int gap = (int)(2 * scaleFactor);  // 通用间距
        int timeScoreGap = (int)(2 * scaleFactor);  // 时间和比分之间的间隙，设为2px
        int timeAreaWidth = (int)(20 * scaleFactor);  // 时间区域宽度
        int centerGap = (int)(12 * scaleFactor);  // 中心间隔
        int scoreGap = (int)(1 * scaleFactor);  // 比分之间的间隔，1px

        // 计算CT和T区域的X坐标
        int ctBoxX = centerX - timeAreaWidth - centerGap - boxWidth;  // CT区域X坐标
        int tBoxX = centerX + timeAreaWidth + centerGap;  // T区域X坐标

        // 渲染中央时间区域背景（带渐变效果）
        guiGraphics.fillGradient(
                centerX - timeAreaWidth,
                startY,
                centerX + timeAreaWidth,
                startY + timeBarHeight,
                -1072689136,  // 半透明黑色顶部
                -804253680    // 较浅的半透明黑色底部
        );

        // 渲染比分区域背景（带渐变消失效果）
        // CT比分区域（左侧）
        guiGraphics.fillGradient(
                centerX - timeAreaWidth,
                startY + timeBarHeight + (int)(2 * scaleFactor),  // 使用2px间隙
                centerX - (int)(2 * scaleFactor),  // 比分间隙
                startY + backgroundHeight + (int)(6 * scaleFactor),
                -1072689136,
                noColor
        );

        // T比分区域（右侧）
        guiGraphics.fillGradient(
                centerX + (int)(2 * scaleFactor),  // 比分间隙
                startY + timeBarHeight + (int)(2 * scaleFactor),  // 使用2px间隙
                centerX + timeAreaWidth,
                startY + backgroundHeight + (int)(6 * scaleFactor),
                -1072689136,
                noColor
        );

        // 渲染回合时间
        String roundTime = getRoundTimeString();  // 获取格式化的时间字符串
        float timeScale = scaleFactor * 1.3f;    // 时间显示的缩放比例

        // 使用矩阵变换来渲染时间
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                centerX,
                startY + timeBarHeight/2 - (int)(1 * scaleFactor),  // 稍微上移
                0
        );
        guiGraphics.pose().scale(timeScale, timeScale, 1.0f);
        guiGraphics.drawString(font, roundTime,
                -font.width(roundTime) / 2,
                -4,
                textRoundTimeColor,
                false
        );
        guiGraphics.pose().popPose();

        // 渲染头像
        renderTeamPlayerHeads("ct", ctBoxX, startY, boxWidth, backgroundHeight, scaleFactor, guiGraphics);
        renderTeamPlayerHeads("t", tBoxX, startY, boxWidth, backgroundHeight, scaleFactor, guiGraphics);

        // 渲染比分（进一步上移位置）
        float scoreScale = scaleFactor * 1.1f;  // 比分字体缩放比例

        // CT比分（左侧）
        String ctScore = String.valueOf(ClientData.cTWinnerRounds);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                centerX - timeAreaWidth/2 - scaleFactor,  // 水平位置：中心点左侧
                startY + timeBarHeight + (int)(2 * scaleFactor) + scoreBarHeight/2 - (int)(8 * scaleFactor),  // 垂直位置：再上移2px
                0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int ctScoreWidth = font.width(ctScore);
        guiGraphics.drawString(font, ctScore,
                -ctScoreWidth/2,  // 水平居中
                -font.lineHeight/2,  // 垂直居中
                textCTWinnerRoundsColor,
                false  // 不使用阴影
        );
        guiGraphics.pose().popPose();

        // T比分（右侧）
        String tScore = String.valueOf(ClientData.tWinnerRounds);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                centerX + timeAreaWidth/2 + scaleFactor,  // 水平位置：中心点右侧
                startY + timeBarHeight + (int)(2 * scaleFactor) + scoreBarHeight/2 - (int)(8 * scaleFactor),  // 垂直位置：与CT比分对齐
                0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int tScoreWidth = font.width(tScore);
        guiGraphics.drawString(font, tScore,
                -tScoreWidth/2,  // 水平居中
                -font.lineHeight/2,  // 垂直居中
                textTWinnerRoundsColor,
                false  // 不使用阴影
        );
        guiGraphics.pose().popPose();

        // 拆弹进度显示
        if(ClientData.dismantleBombProgress > 0) {
            renderDemolitionProgress(player, guiGraphics,screenWidth,screenHeight);
        }
        this.renderMoneyText(guiGraphics,screenWidth,screenHeight);
    }

    /**
     * 获取回合时间显示字符串
     * 根据不同的游戏状态返回不同的时间格式
     */
    private String getRoundTimeString() {
        if(ClientData.time == -1 && !ClientData.isWaitingWinner) {
            textRoundTimeColor = color(240,40,40);
            return "--:--";
        }
        return getCSGameTime();
    }

    /**
     * 渲染拆弹进度
     * 使用CS风格的数字密码显示，带有混淆效果
     */
    private void renderDemolitionProgress(LocalPlayer player, GuiGraphics guiGraphics,int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        MutableComponent component = Component.empty();
        for (int i = 1; i < 8; i++) {
            boolean flag = getDemolitionProgressTextStyle(i);
            int color = flag ? 5635925 : 16777215;
            component.append(Component.literal(String.valueOf(code.toCharArray()[i - 1]))
                    .withStyle(Style.EMPTY.withColor(color).withObfuscated(!flag)));
        }
        float xStart = screenWidth / 2F - ((font.width(component) * 1.5F) / 2F);
        float yStart = screenHeight / 2F + 65 + (font.lineHeight * 1.5F / 2F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xStart, yStart, 0);
        guiGraphics.pose().scale(1.5F,1.5F,0);
        guiGraphics.drawString(font, component, 0, 0, -1,true);
        guiGraphics.pose().popPose();
    }

    /**
     * 渲染玩家金钱
     * 在屏幕左下角显示当前玩家的金钱数量
     */
    private void renderMoneyText(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(5,screenHeight - 20,0 );
        guiGraphics.pose().scale(2,2,0);
        guiGraphics.drawString(font, "$ "+ClientData.getMoney(), 0,0, ClientData.currentTeam.equals("ct") ? textCTWinnerRoundsColor : textTWinnerRoundsColor);
        guiGraphics.pose().popPose();
    }

    public static boolean getDemolitionProgressTextStyle(int index){
        float i = (float) index / 7;
        return ClientData.dismantleBombProgress >= i;
    }

    private void drawBar(GuiGraphics pGuiGraphics, int pX, int pY, BossEvent pBossEvent, int pWidth, int p_281636_) {
        pGuiGraphics.blit(GUI_BARS_LOCATION, pX, pY, 0, BossEvent.BossBarColor.GREEN.ordinal() * 5 * 2 + p_281636_, pWidth, 5);
        if (pBossEvent.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
            RenderSystem.enableBlend();
            pGuiGraphics.blit(GUI_BARS_LOCATION, pX, pY, 0, 80 + (pBossEvent.getOverlay().ordinal() - 1) * 5 * 2 + p_281636_, pWidth, 5);
            RenderSystem.disableBlend();
        }
    }

    public static String getCSGameTime(){
        return formatTime(ClientData.time / 20);
    }

    /**
     * 将总秒数和过去秒数转换为分钟和秒的字符串表示。
     *
     * @param totalSeconds 总秒数
     * @return 格式化的时间字符串，如 "01:00"
     */
    public static String formatTime(int totalSeconds) {
        // 计算剩余的分钟和秒
        int remainingMinutes = totalSeconds / 60;
        int remainingSecondsPart = totalSeconds % 60;

        if(remainingMinutes == 0 && remainingSecondsPart <= 10){
            textRoundTimeColor = color(240,40,40);
        }else {
            textRoundTimeColor = color(255,255,255);
        }

        String minutesPart = String.format("%02d", remainingMinutes);
        String secondsPart = String.format("%02d", remainingSecondsPart);

        return minutesPart + ":" + secondsPart;
    }

    /**
     * 渲染队伍玩家头像
     */
    private void renderTeamPlayerHeads(String team, int x, int y, int width, int height, float scale, GuiGraphics guiGraphics) {
        List<Pair<UUID, TabData>> teamPlayers = ClientData.getTeamPlayers(team);
        if (teamPlayers.isEmpty()) return;

        // 调整头像尺寸和布局
        int headSize = (int)(28 * scale);
        int maxPerRow = 5;
        int spacing = (int)(7 * scale);

        // 计算头像位置 - 根据阵营调整绝对位置
        int baseY = y + (int)(3 * scale);
        // CT向右偏移，T向左偏移
        int baseX = team.equals("ct") ?
                x + (int)(4 * scale) :  // CT阵营
                x - (int)(8 * scale);   // T阵营

        // 传递scale参数
        renderPlayerHeads(teamPlayers, baseX, baseY, headSize, spacing, maxPerRow, guiGraphics, team, scale);
    }

    /**
     * 获取玩家名称（带缓存）
     */
    private String getPlayerName(UUID uuid) {
        long currentTime = System.currentTimeMillis();

        // 首先检查缓存
        if (playerNameCache.containsKey(uuid)) {
            long lastUpdate = nameLastUpdateTime.getOrDefault(uuid, 0L);
            if (currentTime - lastUpdate < 120000) { // 2分钟缓存时间
                return playerNameCache.get(uuid);
            }
        }

        // 尝试从在线玩家获取（这是最可靠的方式）
        String playerName = null;
        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (player != null) {
            playerName = player.getGameProfile().getName();
        }

        // 如果在线玩家获取失败，尝试从ServerUtils获取
        if (playerName == null && currentTime - lastServerUtilsCall > SERVER_UTILS_CALL_INTERVAL) {
            Map<String, ServerUtils.PlayerInfo> onlinePlayers = ServerUtils.getAllOnlinePlayersInfo();
            for (ServerUtils.PlayerInfo info : onlinePlayers.values()) {
                if (info.getUuid().equals(uuid.toString())) {
                    playerName = info.getName();
                    break;
                }
            }
            lastServerUtilsCall = currentTime;
        }

        // 更新缓存
        if (playerName != null) {
            playerNameCache.put(uuid, playerName);
            nameLastUpdateTime.put(uuid, currentTime);
        }

        return playerName;
    }

    /**
     * 渲染玩家头像列表
     */
    private void renderPlayerHeads(List<Pair<UUID, TabData>> players, int startX, int startY,
                                   int headSize, int spacing, int maxPerRow, GuiGraphics guiGraphics,
                                   String team, float scale) {
        int row = 0;
        int col = 0;
        long currentTime = System.currentTimeMillis();

        // 获取默认颜色（根据阵营）
        int defaultColor = team.equals("ct") ? textCTWinnerRoundsColor : textTWinnerRoundsColor;

        // 首先渲染所有已有纹理，并收集需要更新的UUID
        Set<UUID> missingTextures = new HashSet<>();

        for (int i = 0; i < players.size(); i++) {
            Pair<UUID, TabData> playerData = players.get(i);
            UUID uuid = playerData.getFirst();
            TabData tabData = playerData.getSecond();
            boolean isLiving = tabData.isLiving();

            // 计算位置 - 根据阵营决定递增方向
            int posX;
            if (team.equals("ct")) {
                // CT阵营向左递增
                posX = startX - col * (headSize + spacing);
            } else {
                // T阵营向右递增
                posX = startX + col * (headSize + spacing);
            }
            int posY = startY + row * (headSize + spacing);

            // 只为活着的玩家渲染描边
            if (isLiving) {
                int borderColor = i < 5 ? BORDER_COLORS[i] : defaultColor;
                int borderSize = 2;
                guiGraphics.fill(posX - borderSize, posY - borderSize,
                        posX + headSize + borderSize, posY + headSize + borderSize,
                        borderColor);
            }

            // 渲染头像
            ResourceLocation textureLocation = playerHeadTextures.get(uuid);
            if (textureLocation != null) {
                RenderSystem.setShaderTexture(0, textureLocation);
                if (!isLiving) {
                    RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f);
                }
                guiGraphics.blit(textureLocation, posX, posY, 0, 0, headSize, headSize, headSize, headSize);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                // 纹理不存在，收集需要更新的
                missingTextures.add(uuid);
            }

            // 更新位置计数器
            col++;
            if (col >= maxPerRow) {
                col = 0;
                row++;
            }
        }

        // 异步更新缺失的纹理，避免每帧都更新
        updateMissingTextures(missingTextures, players);
    }

    /**
     * 异步更新缺失的纹理
     * 只有在需要时才会请求下载
     */
    private void updateMissingTextures(Set<UUID> missingTextures, List<Pair<UUID, TabData>> players) {
        if (missingTextures.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        for (Pair<UUID, TabData> playerData : players) {
            UUID uuid = playerData.getFirst();

            // 检查是否需要更新纹理：
            // 1. 纹理不存在，或者
            // 2. 超过更新时间间隔
            if (missingTextures.contains(uuid) ||
                    (currentTime - textureLastUpdateTime.getOrDefault(uuid, 0L) > TEXTURE_UPDATE_INTERVAL)) {

                String playerName = getPlayerName(uuid);
                if (playerName != null) {
                    // 第一次加载时强制从API获取
                    boolean forceUpdate = !playerHeadTextures.containsKey(uuid);
                    updatePlayerHeadTexture(uuid, playerName, forceUpdate);
                }
            }
        }
    }

    /**
     * 更新玩家头像纹理
     */
    private void updatePlayerHeadTexture(UUID uuid, String playerName, boolean forceUpdate) {
        // 防止并发下载
        String lockKey = uuid.toString();
        if (downloadingTextures.putIfAbsent(lockKey, true) != null) {
            return; // 已经在下载中
        }

        // 检查是否需要更新：如果不是强制更新且上次更新时间未超过间隔，则跳过
        long currentTime = System.currentTimeMillis();
        if (!forceUpdate &&
                playerHeadTextures.containsKey(uuid) &&
                currentTime - textureLastUpdateTime.getOrDefault(uuid, 0L) < TEXTURE_UPDATE_INTERVAL) {
            downloadingTextures.remove(lockKey);
            return;
        }

        try {
            // 使用MvpMusicUtils的异步下载方法（现在已支持SHA256哈希比较）
            MvpMusicUtils.downloadPlayerHeadAsync(playerName, forceUpdate)
                    .thenAcceptAsync(imageFile -> {
                        try {
                            if (imageFile != null && imageFile.exists()) {
                                // 验证图片文件
                                try (InputStream is = Files.newInputStream(imageFile.toPath())) {
                                    // 尝试加载图片，如果失败则使用默认头像
                                    NativeImage image = NativeImage.read(is);

                                    // 在主线程中更新纹理
                                    Minecraft.getInstance().execute(() -> {
                                        try {
                                            // 如果已有纹理，先释放
                                            ResourceLocation oldTexture = playerHeadTextures.get(uuid);
                                            if (oldTexture != null) {
                                                Minecraft.getInstance().getTextureManager().release(oldTexture);
                                            }

                                            // 创建新纹理
                                            DynamicTexture texture = new DynamicTexture(image);
                                            ResourceLocation textureLocation = new ResourceLocation("fpsmatch", "textures/player_heads/" + uuid.toString());

                                            Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
                                            playerHeadTextures.put(uuid, textureLocation);
                                            textureLastUpdateTime.put(uuid, System.currentTimeMillis());
                                        } catch (Exception e) {
                                            FPSMatch.LOGGER.error("Error registering player head texture", e);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            FPSMatch.LOGGER.error("Error processing player head texture", e);
                            // 如果图片加载失败，删除损坏的文件
                            if (imageFile != null) {
                                imageFile.delete();
                            }
                        } finally {
                            // 释放下载锁
                            downloadingTextures.remove(lockKey);
                        }
                    }, ForkJoinPool.commonPool())
                    .exceptionally(throwable -> {
                        FPSMatch.LOGGER.error("Failed to update player head texture", throwable);
                        downloadingTextures.remove(lockKey);
                        return null;
                    });
        } catch (Exception e) {
            FPSMatch.LOGGER.error("Error initiating texture download", e);
            downloadingTextures.remove(lockKey);
        }
    }

    // 添加清理方法
    public static void clearHeadTextures() {
        playerHeadTextures.values().forEach(texture ->
                Minecraft.getInstance().getTextureManager().release(texture));
        playerHeadTextures.clear();
        textureLastUpdateTime.clear();
        playerNameCache.clear();
        nameLastUpdateTime.clear();
    }

    // 添加一个强制更新指定玩家头像的方法
    public void forceUpdatePlayerHead(UUID uuid) {
        String playerName = getPlayerName(uuid);
        if (playerName != null) {
            updatePlayerHeadTexture(uuid, playerName, true);
        }
    }

}
