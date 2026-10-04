package net.ptcrys.fpsmatch.common.packet.register;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 把上游那些"裸 POJO"包类（带静态 encode/decode、实例 handle）包装成
 * NeoForge 1.21 要求的 {@link CustomPacketPayload}。
 * <p>
 * 之所以能做到"零改包体"：上游的 {@code encode(T, FriendlyByteBuf)} /
 * {@code decode(FriendlyByteBuf)} 方法体在 1.21.1 上**原本就能编译通过**（实测无任何
 * 方法/字段不匹配），因此这里只在外面套一层壳，把字节流原样转发，不重新编解码。
 */
public final class ReflectivePayload implements CustomPacketPayload {

    private final Type<ReflectivePayload> type;
    private final Object body;

    public ReflectivePayload(Type<ReflectivePayload> type, Object body) {
        this.type = type;
        this.body = body;
    }

    /** 实际承载的上游包对象。 */
    public Object body() {
        return body;
    }

    @Override
    public Type<ReflectivePayload> type() {
        return type;
    }

    public static Type<ReflectivePayload> typeOf(String path) {
        return new Type<>(ResourceLocation.fromNamespaceAndPath("fpsmatch", path));
    }

    /**
     * 用上游的静态 encode/decode 方法构造载荷编解码器。
     * {@link RegistryFriendlyByteBuf} 是 {@code FriendlyByteBuf} 的子类，可直接透传。
     */
    public static StreamCodec<RegistryFriendlyByteBuf, ReflectivePayload> codec(
            Type<ReflectivePayload> type,
            java.util.function.BiConsumer<Object, net.minecraft.network.FriendlyByteBuf> encoder,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, Object> decoder) {
        return new StreamCodec<>() {
            @Override
            public ReflectivePayload decode(RegistryFriendlyByteBuf buffer) {
                return new ReflectivePayload(type, decoder.apply(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, ReflectivePayload value) {
                encoder.accept(value.body(), buffer);
            }
        };
    }
}
