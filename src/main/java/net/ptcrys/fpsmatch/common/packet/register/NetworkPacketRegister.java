package net.ptcrys.fpsmatch.common.packet.register;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forge {@code SimpleChannel} 注册器在 1.21.1 / NeoForge 上的替代实现。
 * <p>
 * 上游用 {@code NetworkRegistry.newSimpleChannel(...)} + 反射 {@code encode/decode/handle}
 * 动态注册包类。NeoForge 1.21 删除了 {@code SimpleChannel}/{@code NetworkRegistry}，
 * 改为在 {@code RegisterPayloadHandlersEvent} 里用 {@link PayloadRegistrar} 声明式注册
 * {@code CustomPacketPayload} + {@code StreamCodec}。
 * <p>
 * 策略：保持 {@link #registerPacket(Class)} 的调用方式与包类反射协议完全不变，
 * 内部把旧协议包装成 NeoForge 新协议（见 {@link ReflectivePayload}）。
 */
public class NetworkPacketRegister {

    /** 包类 -> 已注册的载荷类型；发送时用来把裸包对象包装成 CustomPacketPayload。 */
    private static final Map<Class<?>, CustomPacketPayload.Type<ReflectivePayload>> TYPES = new ConcurrentHashMap<>();

    private final ResourceLocation name;

    /** 注册器由 {@code RegisterPayloadHandlersEvent} 提供，在事件回调期间绑定。 */
    private PayloadRegistrar registrar;

    public NetworkPacketRegister(ResourceLocation channel, String version) {
        this.name = channel;
    }

    public NetworkPacketRegister(ResourceLocation channel, Supplier<String> networkProtocolVersion,
                                 java.util.function.Predicate<String> clientAcceptedVersions,
                                 java.util.function.Predicate<String> serverAcceptedVersions) {
        // 版本协商改由 PayloadRegistrar 的 version 参数统一处理，因此这里只保留通道名。
        this.name = channel;
    }

    /** 在 {@code RegisterPayloadHandlersEvent} 回调里绑定注册器。 */
    public void bind(PayloadRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    public <T> void registerPacket(Class<T> packetClass) {
        registerPacketInternal(packetClass, inferDirection(packetClass));
    }

    public <T> void registerPacket(Class<T> packetClass, PayloadDirection direction) {
        registerPacketInternal(packetClass, Objects.requireNonNull(direction, "direction"));
    }

    /**
     * 依据包类名自动推导方向（C2S=PLAY_TO_SERVER、S2C=PLAY_TO_CLIENT），
     * 对齐上游显式声明 NetworkDirection 的规范；无法从命名判定的退回双向注册。
     */
    public static PayloadDirection inferDirection(Class<?> packetClass) {
        String simpleName = packetClass.getSimpleName();
        // 先看后缀（上游主流命名：XxxC2SPacket / XxxS2CPacket），
        // 再看前缀（观众同步包命名：C2SStartInspectPacket / S2CWatchedPlayerInspectPacket）。
        if (simpleName.endsWith("C2SPacket") || simpleName.endsWith("C2S")) {
            return PayloadDirection.TO_SERVER;
        }
        if (simpleName.endsWith("S2CPacket") || simpleName.endsWith("S2C")) {
            return PayloadDirection.TO_CLIENT;
        }
        boolean hasC2S = simpleName.contains("C2S");
        boolean hasS2C = simpleName.contains("S2C");
        if (hasC2S && !hasS2C) {
            return PayloadDirection.TO_SERVER;
        }
        if (hasS2C && !hasC2S) {
            return PayloadDirection.TO_CLIENT;
        }
        return PayloadDirection.BOTH;
    }

    private <T> void registerPacketInternal(Class<T> packetClass, PayloadDirection direction) {
        PayloadRegistrar reg = this.registrar;
        if (reg == null) {
            throw new IllegalStateException("NetworkPacketRegister.bind() must be called inside "
                    + "RegisterPayloadHandlersEvent before registering packets");
        }
        try {
            Method encode = packetClass.getMethod("encode", packetClass, FriendlyByteBuf.class);
            if (!Modifier.isStatic(encode.getModifiers())) {
                throw new IllegalArgumentException("encode() must be static in " + packetClass.getName());
            }
            Method decode = packetClass.getMethod("decode", FriendlyByteBuf.class);
            if (!Modifier.isStatic(decode.getModifiers())) {
                throw new IllegalArgumentException("decode() must be static in " + packetClass.getName());
            }
            if (!packetClass.isAssignableFrom(decode.getReturnType())) {
                throw new IllegalArgumentException("decode() must return " + packetClass.getName());
            }
            Method handle = packetClass.getMethod("handle", Supplier.class);

            CustomPacketPayload.Type<ReflectivePayload> type =
                    ReflectivePayload.typeOf(toSnakePath(packetClass));
            TYPES.put(packetClass, type);

            BiConsumer<Object, FriendlyByteBuf> encoder = (packet, buf) -> {
                try {
                    encode.invoke(null, packet, buf);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to encode packet " + packetClass.getName(), e);
                }
            };
            Function<FriendlyByteBuf, Object> decoder = buf -> {
                try {
                    return decode.invoke(null, buf);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to decode packet " + packetClass.getName(), e);
                }
            };
            IPayloadHandler<ReflectivePayload> handler = (payload, neoCtx) -> {
                try {
                    // 保持上游协议：把 Supplier<PayloadContext> 交给包类的 handle()
                    Supplier<PayloadContext> supplier = () -> new PayloadContext(neoCtx);
                    handle.invoke(payload.body(), supplier);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to handle packet " + packetClass.getName(), e);
                }
            };

            var codec = ReflectivePayload.codec(type, encoder, decoder);
            switch (direction) {
                case TO_SERVER -> reg.playToServer(type, codec, handler);
                case TO_CLIENT -> reg.playToClient(type, codec, handler);
                case BOTH -> reg.playBidirectional(type, codec, handler);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Packet class " + packetClass.getName()
                    + " is missing required methods (encode/decode/handle)", e);
        }
    }

    /** MapRoomToastS2CPacket -> map_room_toast_s2c_packet（ResourceLocation 路径只允许小写）。 */
    private static String toSnakePath(Class<?> packetClass) {
        String name = packetClass.getSimpleName();
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 发送侧

    private static CustomPacketPayload.Type<ReflectivePayload> requireType(Class<?> packetClass) {
        CustomPacketPayload.Type<ReflectivePayload> type = TYPES.get(packetClass);
        if (type == null) {
            throw new IllegalStateException("Packet class not registered: " + packetClass.getName());
        }
        return type;
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        PacketDistributor.sendToPlayer(player, new ReflectivePayload(requireType(message.getClass()), message));
    }

    public static void sendToServer(Object message) {
        PacketDistributor.sendToServer(new ReflectivePayload(requireType(message.getClass()), message));
    }

    public ResourceLocation getName() {
        return name;
    }

    /** 包的方向。取代已删除的 {@code NetworkDirection}。 */
    public enum PayloadDirection {
        TO_SERVER,
        TO_CLIENT,
        BOTH
    }
}
