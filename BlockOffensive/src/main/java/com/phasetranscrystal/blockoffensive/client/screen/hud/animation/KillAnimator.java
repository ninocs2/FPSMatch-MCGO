package com.phasetranscrystal.blockoffensive.client.screen.hud.animation;

import com.phasetranscrystal.blockoffensive.data.DeathMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public interface KillAnimator {
    void start(DeathMessage deathMessage); // 启动动画
    void reset(); // 强制停止
    void render(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int y); // 渲染逻辑
    boolean isActive(); // 是否在播放中
    void addKill(DeathMessage deathMessage); // 增加击杀
}