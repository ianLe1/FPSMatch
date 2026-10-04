package net.ptcrys.fpsmatch.common.event;

import net.neoforged.bus.api.ICancellableEvent;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.neoforged.bus.api.Event;

/**
 * FPSMatch重新加载事件
 */
public class FPSMReloadEvent extends Event {

    private final FPSMCore core;

    public FPSMReloadEvent(FPSMCore core) {
        this.core = core;
    }

    public FPSMCore getCore() {
        return core;
    }

}
