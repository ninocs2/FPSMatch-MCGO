package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.data.MvpReason;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

import java.io.File;

public class PlayerGetMvpEvent extends Event {
    private final Player player;
    private final BaseMap map;
    private final MvpReason reason;
    private File mvpMusicFile;  // 只在客户端使用

    public PlayerGetMvpEvent(Player player, BaseMap map, MvpReason reason) {
        this.player = player;
        this.map = map;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public MvpReason getReason() {
        return reason;
    }

    public BaseMap getMap() {
        return map;
    }

    // 只在客户端使用的方法
    public File getMvpMusicFile() {
        return mvpMusicFile;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
