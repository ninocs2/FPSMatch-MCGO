package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraftforge.eventbus.api.Event;

/**
 * FPSMatch重新加载事件
 * */
public class FPSMReloadEvent extends Event {
    private final FPSMCore core;

    public FPSMReloadEvent(FPSMCore core) {
        this.core = core;
    }

    public FPSMCore getCore() {
        return core;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
