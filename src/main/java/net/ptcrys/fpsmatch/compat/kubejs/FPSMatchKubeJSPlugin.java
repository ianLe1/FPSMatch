package net.ptcrys.fpsmatch.compat.kubejs;

import net.ptcrys.fpsmatch.compat.kubejs.events.FPSMatchCommonEvents;
import net.ptcrys.fpsmatch.compat.kubejs.events.FPSMatchKubeJSEvents;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;

public class FPSMatchKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        try {
            FPSMatchCommonEvents.INSTANCE.init();
        } catch (Exception e) {
            // init() 失败不应阻止 FPSMatchEvents 事件组的注册
        }
        // 1.21.1 KubeJS：事件组交给 EventGroupRegistry 注册（EventGroup#register 已删）
        registry.register(FPSMatchKubeJSEvents.GROUP);
    }
}
