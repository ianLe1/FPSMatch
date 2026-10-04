package net.ptcrys.fpsmatch.common.packet.register;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

/**
 * Forge {@code PayloadContext} 在 1.21.1 / NeoForge 上的等价垫片。
 * <p>
 * 上游 1.20.1 代码里 61 个包类通过 {@code Supplier<PayloadContext>} 取用上下文，
 * 实际只用到三个方法：{@code enqueueWork(Runnable)}、{@code setPacketHandled(boolean)}、
 * {@code getSender()}。NeoForge 1.21 取消了 {@code SimpleChannel} 与 {@code PayloadContext}，
 * 改用 {@code CustomPacketPayload} + {@code IPayloadContext}。
 * <p>
 * 这里保留旧调用面，让包类只需替换 import 即可，不必逐个重写编解码。
 */
public final class PayloadContext {

    private final IPayloadContext neoContext;

    public PayloadContext(IPayloadContext neoContext) {
        this.neoContext = neoContext;
    }

    /** 在主线程执行；语义与 Forge 的 {@code Context#enqueueWork} 一致。 */
    public void enqueueWork(Runnable work) {
        neoContext.enqueueWork(work);
    }

    /**
     * 兼容旧代码的空实现。
     * <p>
     * Forge 需要显式声明"已处理"以阻止主线程继续走默认逻辑；NeoForge 的载荷分发器
     * 自行管理完成状态，无需（也不支持）手动设置，因此这里是 no-op。
     */
    public void setPacketHandled(boolean handled) {
        // no-op：NeoForge 自动接管
    }

    /** C2S 包返回发送者；在客户端（S2C 包）上返回 {@code null}，与旧行为一致。 */
    public ServerPlayer getSender() {
        Player player = neoContext.player();
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }
}
