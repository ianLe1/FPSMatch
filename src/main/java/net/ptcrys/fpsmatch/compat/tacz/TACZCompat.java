package net.ptcrys.fpsmatch.compat.tacz;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;

public class TACZCompat {

    /**
     * 注册TACZ观察者支持
     */
    @OnlyIn(Dist.CLIENT)
    public static void registerSpecClient(IEventBus bus) {}
}
