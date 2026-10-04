package net.ptcrys.fpsmatch.common.capability.team;

import net.ptcrys.fpsmatch.common.event.FPSMapEvent;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

/**
 * {@link ShopCapability} 的**静态**事件订阅点。
 * <p>
 * 单独成类的原因：NeoForge 1.21.1 的 {@code @EventBusSubscriber} 走静态注入路径，
 * 要求被标注类的每个 {@code @SubscribeEvent} 方法都是 {@code static}；而 {@code ShopCapability}
 * 另有 2 个依赖实例状态的实例方法订阅（onJoin/onLeave，由
 * {@code CapabilityMap} 的 {@code NeoForge.EVENT_BUS.register(capability)} 逐实例注册）。
 * 两者不可共存于同一个类，故把静态部分拆出来。
 * <p>
 * 注意不能把这两个方法塞回 ShopCapability 的父类 {@code TeamCapability}——
 * 那会撞上规则 3（监听器的父类不允许带 @SubscribeEvent）。
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class ShopCapabilityEvents {

    private ShopCapabilityEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerPickupItem(FPSMapEvent.PlayerEvent.PickupItemEvent event) {
        ShopCapability.onPlayerPickupItem(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDropItem(ItemTossEvent event) {
        ShopCapability.onPlayerDropItem(event);
    }
}
