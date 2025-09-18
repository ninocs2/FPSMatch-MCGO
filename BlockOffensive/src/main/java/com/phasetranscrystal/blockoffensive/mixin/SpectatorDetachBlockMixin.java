package com.phasetranscrystal.blockoffensive.mixin;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * 在游戏地图中（非 fpsm_none）禁用旁观者模式下"离开附着玩家"的快捷键（含鼠标左/右键）。
 * 实现思路：1) 拦截 Minecraft#handleKeybinds，屏蔽会触发脱离的输入；
 *          2) 守底线：拦截 Minecraft#setCameraEntity 阻止从他人切回本地玩家（脱离）；
 *          3) 监听游戏模式变化：当从旁观者模式切换到其他模式时自动解除相机附着。
 */
@Mixin(Minecraft.class)
public abstract class SpectatorDetachBlockMixin {

    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Final public Options options;
    @Shadow @Final public LevelRenderer levelRenderer;
    @Shadow @Final public GameRenderer gameRenderer;
    @Shadow @Nullable public abstract Entity getCameraEntity();
    @Shadow @Final public Gui gui;
    @Shadow @Nullable public Screen screen;
    @Shadow protected abstract boolean isMultiplayerServer();
    @Shadow @Final private static Component SOCIAL_INTERACTIONS_NOT_AVAILABLE;
    @Shadow @Final private GameNarrator narrator;
    @Shadow @Nullable private TutorialToast socialInteractionsToast;
    @Shadow @Final private Tutorial tutorial;
    @Shadow public abstract void setScreen(@org.jetbrains.annotations.Nullable Screen pGuiScreen);
    @Shadow @Nullable public abstract ClientPacketListener getConnection();
    @Shadow protected abstract void openChatScreen(String pDefaultText);
    @Shadow @Nullable private Overlay overlay;
    @Shadow @Final public MouseHandler mouseHandler;
    @Shadow @Nullable public MultiPlayerGameMode gameMode;
    @Shadow public abstract void setCameraEntity(Entity pViewingEntity);

    private static final String DISABLED_MAP_NAME = "fpsm_none";
    private GameType lastGameType = null;

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void blockoffensive$disableSpectatorDetach(CallbackInfo ci) {
        // 地图判定：非游戏地图不干预
        if (DISABLED_MAP_NAME.equals(FPSMClient.getGlobalData().getCurrentMap())) {
            return;
        }
        if (this.player == null) {
            return;
        }
        
        // 检查游戏模式变化：如果从旁观者模式切换到其他模式，自动解除相机附着
        GameType currentGameType = this.player.isSpectator() ? GameType.SPECTATOR : 
                                  this.player.isCreative() ? GameType.CREATIVE :
                                  this.player.getAbilities().mayBuild ? GameType.ADVENTURE : GameType.SURVIVAL;
        
        if (lastGameType != null && lastGameType == GameType.SPECTATOR && currentGameType != GameType.SPECTATOR) {
            // 从旁观者模式切换到其他模式，自动解除相机附着
            Entity currentCamera = this.getCameraEntity();
            if (currentCamera != null && currentCamera != this.player) {
                this.setCameraEntity(this.player);
            }
        }
        lastGameType = currentGameType;
        
        // 仅旁观者模式时考虑
        if (!this.player.isSpectator()) {
            return;
        }
        // 仅当相机当前附着到"非本地玩家"的实体时拦截（即正在观战某实体）
        Entity cam = this.getCameraEntity();
        if (cam == null || cam == this.player) {
            return;
        }

        // 拦截原始按键处理，改为自定义的精简处理：
        // - 保留视角切换/平滑相机/聊天/菜单/旁观者热键菜单等
        // - 屏蔽 Attack/Use/持续攻击 等可能触发"离开附着"的输入
        ci.cancel();

        // 视角切换（F5）
        for (; this.options.keyTogglePerspective.consumeClick(); this.levelRenderer.needsUpdate()) {
            CameraType prev = this.options.getCameraType();
            this.options.setCameraType(this.options.getCameraType().cycle());
            if (prev.isFirstPerson() != this.options.getCameraType().isFirstPerson()) {
                this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
            }
        }

        // 平滑相机
        while (this.options.keySmoothCamera.consumeClick()) {
            this.options.smoothCamera = !this.options.smoothCamera;
        }

        // 快捷栏（旁观者菜单选择仍可使用）
        for (int i = 0; i < 9; ++i) {
            boolean save = this.options.keySaveHotbarActivator.isDown();
            boolean load = this.options.keyLoadHotbarActivator.isDown();
            if (this.options.keyHotbarSlots[i].consumeClick()) {
                if (this.player.isSpectator()) {
                    this.gui.getSpectatorGui().onHotbarSelected(i);
                } else if (!this.player.isCreative() || this.screen != null || !load && !save) {
                    this.player.getInventory().selected = i;
                } else {
                    CreativeModeInventoryScreen.handleHotbarLoadOrSave((Minecraft)(Object)this, i, load, save);
                }
            }
        }

        // 社交界面
        while (this.options.keySocialInteractions.consumeClick()) {
            if (!this.isMultiplayerServer()) {
                this.player.displayClientMessage(SOCIAL_INTERACTIONS_NOT_AVAILABLE, true);
                this.narrator.sayNow(SOCIAL_INTERACTIONS_NOT_AVAILABLE);
            } else {
                if (this.socialInteractionsToast != null) {
                    this.tutorial.removeTimedToast(this.socialInteractionsToast);
                    this.socialInteractionsToast = null;
                }
                this.setScreen(new SocialInteractionsScreen());
            }
        }

        // 背包
        while (this.options.keyInventory.consumeClick()) {
            if (this.gameMode != null && this.gameMode.isServerControlledInventory()) {
                this.player.sendOpenInventory();
            } else {
                this.tutorial.onOpenInventory();
                this.setScreen(new InventoryScreen(this.player));
            }
        }

        // 进度
        while (this.options.keyAdvancements.consumeClick()) {
            if (this.player != null && this.player.connection != null) {
                this.setScreen(new AdvancementsScreen(this.player.connection.getAdvancements()));
            }
        }

        // 交换主副手（旁观者一般无效，但保持一致行为）
        while (this.options.keySwapOffhand.consumeClick()) {
            if (!this.player.isSpectator()) {
                this.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            }
        }

        // 丢弃物品（旁观者无效，保持一致）
        while (this.options.keyDrop.consumeClick()) {
            if (!this.player.isSpectator() && this.player.drop(Screen.hasControlDown())) {
                this.player.swing(InteractionHand.MAIN_HAND);
            }
        }

        // 聊天
        while (this.options.keyChat.consumeClick()) {
            this.openChatScreen("");
        }

        // 命令
        if (this.screen == null && this.overlay == null && this.options.keyCommand.consumeClick()) {
            this.openChatScreen("/");
        }

        // 关键屏蔽点：不处理 Use/Attack/持续攻击，从而禁用可能触发离开的鼠标按键
        // 不调用：startUseItem()/continueAttack()/pickBlock()
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void blockoffensive$blockDetachOnSetCamera(Entity target, CallbackInfo ci) {
        // 地图判定：非游戏地图不干预
        if (DISABLED_MAP_NAME.equals(FPSMClient.getGlobalData().getCurrentMap())) {
            return;
        }
        if (this.player == null) {
            return;
        }
        // 仅旁观者模式时考虑
        if (!this.player.isSpectator()) {
            return;
        }
        Entity current = this.getCameraEntity();
        // 当当前相机不等于本地玩家（正在附着他人）且目标切回本地玩家时，认为是"脱离附着"，进行拦截
        if (current != null && current != this.player && target == this.player) {
            ci.cancel();
        }
    }
}