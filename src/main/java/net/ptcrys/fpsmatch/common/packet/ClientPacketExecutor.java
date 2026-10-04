package net.ptcrys.fpsmatch.common.packet;

import net.ptcrys.fpsmatch.common.packet.register.PayloadContext;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

/**
 * 所有 S2C 包的统一出口：在主线程上把包转交给客户端处理器表。
 * <p>
 * 上游用 {@code DistExecutor.unsafeRunWhenOn} + lambda 间接引用来避免专用服务器加载
 * 客户端类。NeoForge 1.21 删除了 {@code DistExecutor}；这里改读
 * {@code FMLEnvironment.dist} 这个普通静态字段（类加载安全，不牵入任何客户端类型），
 * 语义等价。
 */
public final class ClientPacketExecutor {

    private ClientPacketExecutor() {}

    public static void execute(Supplier<PayloadContext> ctxSupplier, Object packet) {
        PayloadContext context = ctxSupplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientPacketRegistry.handle(packet);
            }
        });
        context.setPacketHandled(true);
    }
}
