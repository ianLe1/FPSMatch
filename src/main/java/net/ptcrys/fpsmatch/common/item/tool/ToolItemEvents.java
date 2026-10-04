package net.ptcrys.fpsmatch.common.item.tool;

import net.ptcrys.fpsmatch.FPSMatch;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 工具栏物品的事件订阅点。
 * <p>
 * 单独成类的原因：NeoForge 1.21.1 的 {@code EventBus#checkSupertypes} 禁止「被注册为监听器的类，其父类带
 * {@code @SubscribeEvent} 方法」。原先该静态方法长在 {@link FPSMToolItem} 上，而 {@link EditToolItem}
 * 继承它并需要自己当监听器，实机启动即抛：
 * <pre>
 * IllegalArgumentException: Attempting to register a listener object of type ...EditToolItem,
 * however its supertype ...FPSMToolItem has a @SubscribeEvent method ...
 * This is not allowed! Only the listener object can have @SubscribeEvent methods.
 * </pre>
 */
@EventBusSubscriber(modid = FPSMatch.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ToolItemEvents {

    private ToolItemEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        FPSMToolItem.onLeftClickEmpty(event);
    }
}
