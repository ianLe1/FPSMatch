package net.ptcrys.fpsmatch.compat.tacz;

import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.spectate.tacz.*;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * TACZ 兼容层注册入口。
 * 由 {@link net.ptcrys.fpsmatch.FPSMatch#registerCompat()} 在确认 TACZ 已加载后调用。
 * <p>
 * 调用方须在调用前通过 {@code ModList.get().isLoaded("tacz")} 判断，
 * 避免 JVM 在 TACZ 未加载时解析本类及其 TACZ 依赖，导致 NoClassDefFoundError。
 * </p>
 */
public final class TACZBootstrap {

    private TACZBootstrap() {}

    /**
     * 注册所有 TACZ 兼容层组件。
     * 调用方保证 TACZ 已加载。
     */
    public static void registerCompat() {
        // 注册 Provider（服务端安全）
        GunCompatManager.register(new TACZGunProvider());
        // 注册事件桥接器（服务端安全）
        NeoForge.EVENT_BUS.register(TACZGunEventBridge.class);

        // 客户端专用事件监听器：仅在物理端为客户端时注册
        // 这些类直接依赖 net.minecraft.client.*（LocalPlayer、Minecraft 等），
        // 在专用服务器（DedicatedServer）上不存在，必须通过 DistExecutor 隔离类加载
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(net.ptcrys.fpsmatch.compat.tacz.client.event.SpectatorEventHandler.class);
            NeoForge.EVENT_BUS.register(SpectatorGunMovementMirror.class);
            NeoForge.EVENT_BUS.register(SpectatorGunRecoil.class);
            NeoForge.EVENT_BUS.register(SpectatorGunFireMirror.class);
            NeoForge.EVENT_BUS.register(SpectatorGunItemMirrorTicker.class);
            NeoForge.EVENT_BUS.register(SpectatorGunInspect.class);
            NeoForge.EVENT_BUS.register(OtherPlayerReloadSound.class);
        }
    }
}
