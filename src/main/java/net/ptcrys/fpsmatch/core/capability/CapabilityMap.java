package net.ptcrys.fpsmatch.core.capability;

import net.ptcrys.fpsmatch.core.capability.map.MapCapability;
import net.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.persistence.DataPersistenceException;
import net.ptcrys.fpsmatch.core.team.BaseTeam;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class CapabilityMap<H, T extends FPSMCapability<H>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapabilityMap.class);

    // NeoForge 1.21.1 运行期规则（与「空 @EventBusSubscriber 类不可注册」是同一条件的动态形态）：
    //   EventBus.register(Object) 要求被注册对象**自己或它的父类链上至少有一个 @SubscribeEvent 方法**，
    //   否则抛 IllegalArgumentException:
    //     class X has no @SubscribeEvent methods, but register was called anyway.
    //   1.20.1 Forge 不做这个校验，所以上游 initializeAndPublish 里那句无条件的
    //   NeoForge.EVENT_BUS.register(capability) 一直没暴露问题。
    // 触发面：FPSMatch 的能力类里只有一部分带**实例**订阅（例如 ShopCapability 的 onJoin/onLeave）；
    //   其余分两种，都必须跳过实例注册：
    //     a) 完全没有订阅（SpawnPointCapability 等）—— 注册必抛
    //        "class X has no @SubscribeEvent methods, but register was called anyway"；
    //     b) 只有 static 订阅（TeamSwitchRestrictionCapability.onJoin）—— 那个由类级
    //        @EventBusSubscriber 走静态注入，按实例注册会抛
    //        "Expected @SubscribeEvent method ... to NOT be static because register() was called
    //         with an instance type"。
    //   而这条注册路径在**建图 / 读图**时必然走到（MapTeams.addTeam → ofTeamCapability），
    //   因此不修的话 cs 类型地图根本建不出来。
    // 门控语义（即 NeoForge 对「实例注册」的真实前置条件）：
    //   · 本类声明了**非 static** 的 @SubscribeEvent → 注册（原行为，如 ShopCapability）
    //   · 父类链上有 @SubscribeEvent              → 也注册，故意让 NeoForge 自己抛 checkSupertypes
    //                                               （规则 3 的违规场景，不能被这里静默吞掉）
    //   · 其余（无订阅 / 只有 static 订阅）        → 跳过实例注册
    private static final Map<Class<?>, Boolean> BUS_REGISTRATION_CACHE = new ConcurrentHashMap<>();

    private static boolean shouldRegisterOnGameBus(final Class<?> capabilityClass) {
        return BUS_REGISTRATION_CACHE.computeIfAbsent(capabilityClass, type -> {
            boolean ownInstanceSubscriber = false;
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(SubscribeEvent.class)) {
                        continue;
                    }
                    if (current != type) {
                        return true;
                    }
                    if (!Modifier.isStatic(method.getModifiers())) {
                        ownInstanceSubscriber = true;
                    }
                }
            }
            return ownInstanceSubscriber;
        });
    }

    public static <C extends MapCapability> Optional<C> getMapCapability(BaseMap map, final Class<C> capability) {
        return map.getCapabilityMap().get(capability);
    }

    public static <C extends TeamCapability> Optional<C> getTeamCapability(BaseTeam team, final Class<C> capability) {
        return team.getCapabilityMap().get(capability);
    }

    public static <C extends TeamCapability> Map<BaseTeam, Optional<C>> getTeamCapability(BaseMap map, final Class<C> capability) {
        return map.getMapTeams().getNormalTeams().stream()
                .collect(Collectors.toMap(Function.identity(), team -> getTeamCapability(team, capability)));
    }

    public static CapabilityMap<BaseMap, MapCapability> ofMapCapability(BaseMap map) {
        CapabilityMap<BaseMap, MapCapability> capMap = new CapabilityMap<>(map, MapCapability.class);
        for (Class<? extends MapCapability> cap : FPSMCapabilityManager.getOriginalMapCapabilities()) {
            capMap.add(cap);
        }
        return capMap;
    }

    public static CapabilityMap<BaseTeam, TeamCapability> ofTeamCapability(BaseTeam team) {
        CapabilityMap<BaseTeam, TeamCapability> capMap = new CapabilityMap<>(team, TeamCapability.class);

        if (team.isClientSide()) return capMap;

        for (Class<? extends TeamCapability> cap : FPSMCapabilityManager.getOriginalTeamCapabilities()) {
            capMap.add(cap);
        }
        return capMap;
    }

    private final Map<Class<? extends T>, T> capabilities = new ConcurrentHashMap<>();
    private final Map<Class<? extends T>, PendingAddition<T>> pendingAdditions = new ConcurrentHashMap<>();
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private final H holder;

    private final Class<T> capabilityType;

    private CapabilityMap(final H holder, final Class<T> capabilityType) {
        this.holder = holder;
        this.capabilityType = capabilityType;
    }

    public final void tick() {
        for (T cap : capabilities.values()) {
            try {
                cap.tick();
            } catch (Exception e) {
                LOGGER.error("Error ticking capability {} on holder {}", cap.getClass().getSimpleName(), this.holder, e);
            }
        }
    }

    /**
     * 添加能力到队伍（通过管理器创建实例）
     *
     * @param capabilityClass 能力类型
     * @return 是否添加成功
     */
    public final <C extends T> boolean add(Class<C> capabilityClass) {
        lockLifecycleInterruptibly();
        try {
            if (capabilities.get(capabilityClass) != null) {
                return FPSMCapabilityManager.getRawFactory(capabilityClass)
                        .map(FPSMCapability.Factory::isOriginal)
                        .orElse(false);
            }
            return addWithLifecycleLock(capabilityClass);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private <C extends T> boolean addWithLifecycleLock(Class<C> capabilityClass) {
        PendingAddition<T> pending = new PendingAddition<>();
        PendingAddition<T> active = pendingAdditions.putIfAbsent(capabilityClass, pending);
        if (active != null) {
            awaitPending(active);
            return false;
        }
        try {
            T published = capabilities.get(capabilityClass);
            if (published != null) {
                pending.completion.complete(Optional.of(published));
                return false;
            }
            if (isCancelled(pending)) {
                pending.completion.complete(Optional.empty());
                return false;
            }
            Optional<C> candidate = FPSMCapabilityManager.createInstance(
                    this.getHolder(), capabilityClass);
            if (candidate.isEmpty()) {
                pending.completion.complete(Optional.empty());
                return false;
            }
            return initializeAndPublish(capabilityClass, candidate.orElseThrow(), pending);
        } catch (RuntimeException | Error failure) {
            if (!pending.completion.isDone()) {
                pending.completion.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            pendingAdditions.remove(capabilityClass, pending);
        }
    }

    public final boolean add(T capability) {
        if (!FPSMCapabilityManager.isRegistered(capability.getClass())) return false;
        return this.addDirectly(capability);
    }

    /**
     * 批量添加多种能力。
     *
     * @param capabilityClasses 能力类型列表
     * @return 添加成功的能力类型列表
     */
    public final <C extends T> List<Class<C>> addAll(Collection<Class<C>> capabilityClasses) {
        List<Class<C>> added = new ArrayList<>();
        for (Class<C> clazz : capabilityClasses) {
            if (add(clazz)) {
                added.add(clazz);
            }
        }
        return added;
    }

    /**
     * 直接添加能力实例（用于网络同步）
     *
     * @param capability 能力实例
     */
    private boolean addDirectly(T capability) {
        Class<T> capabilityClass = (Class<T>) capability.getClass();
        lockLifecycleInterruptibly();
        try {
            return addDirectlyWithLifecycleLock(capabilityClass, capability);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private boolean addDirectlyWithLifecycleLock(
                                                 Class<T> capabilityClass,
                                                 T capability) {
        PendingAddition<T> pending = new PendingAddition<>();
        PendingAddition<T> active = pendingAdditions.putIfAbsent(capabilityClass, pending);
        if (active != null) {
            awaitPending(active);
            return false;
        }
        try {
            T published = capabilities.get(capabilityClass);
            if (published != null) {
                pending.completion.complete(Optional.of(published));
                return false;
            }
            return initializeAndPublish(capabilityClass, capability, pending);
        } catch (RuntimeException | Error failure) {
            if (!pending.completion.isDone()) {
                pending.completion.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            pendingAdditions.remove(capabilityClass, pending);
        }
    }

    private boolean initializeAndPublish(
                                         Class<? extends T> capabilityClass,
                                         T capability,
                                         PendingAddition<T> pending) {
        if (isCancelled(pending)) {
            pending.completion.complete(Optional.empty());
            return false;
        }

        boolean initializationStarted = false;
        boolean registrationAttempted = false;
        try {
            initializationStarted = true;
            capability.init();
            if (isCancelled(pending)) {
                initializationStarted = false;
                capability.destroy();
                pending.completion.complete(Optional.empty());
                return false;
            }
            // 只有真有事件订阅的能力才允许注册到游戏总线；见 shouldRegisterOnGameBus 的注释。
            if (shouldRegisterOnGameBus(capability.getClass())) {
                registrationAttempted = true;
                NeoForge.EVENT_BUS.register(capability);
            }
        } catch (RuntimeException | Error failure) {
            rollbackFailedAddition(
                    capability,
                    initializationStarted,
                    registrationAttempted,
                    failure);
            pending.completion.completeExceptionally(failure);
            throw failure;
        }

        T existing;
        synchronized (pending) {
            if (pending.cancelled) {
                existing = null;
            } else {
                existing = capabilities.putIfAbsent(capabilityClass, capability);
                if (existing == null) {
                    pending.completion.complete(Optional.of(capability));
                    return true;
                }
            }
        }

        try {
            discardInitializedAddition(capability);
        } catch (RuntimeException | Error failure) {
            pending.completion.completeExceptionally(failure);
            throw failure;
        }
        pending.completion.complete(Optional.ofNullable(existing));
        return false;
    }

    private void discardInitializedAddition(T capability) {
        Throwable cleanupFailure = null;
        try {
            NeoForge.EVENT_BUS.unregister(capability);
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        try {
            capability.destroy();
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) {
                cleanupFailure = failure;
            } else {
                cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
    }

    private boolean isCancelled(PendingAddition<T> pending) {
        synchronized (pending) {
            return pending.cancelled;
        }
    }

    private Optional<T> awaitPending(PendingAddition<T> pending) {
        if (pending.ownerThread == Thread.currentThread()) {
            throw new IllegalStateException(
                    "Capability factory/init cannot recursively create the same capability type");
        }
        try {
            return pending.completion.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for capability initialization",
                    interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Capability initialization failed", cause);
        }
    }

    private void lockLifecycleInterruptibly() {
        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for capability lifecycle",
                    interrupted);
        }
    }

    private void rollbackFailedAddition(
                                        T capability,
                                        boolean initializationStarted,
                                        boolean registrationAttempted,
                                        Throwable failure) {
        if (registrationAttempted) {
            try {
                NeoForge.EVENT_BUS.unregister(capability);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        if (initializationStarted) {
            try {
                capability.destroy();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    /**
     * 移除队伍的能力
     *
     * @param capabilityClass 能力类型
     */
    public final <C extends T> boolean remove(Class<C> capabilityClass) {
        PendingAddition<T> pending = pendingAdditions.get(capabilityClass);
        boolean cancelled = false;
        if (pending != null) {
            synchronized (pending) {
                if (!pending.completion.isDone()) {
                    pending.cancelled = true;
                    cancelled = true;
                }
            }
        }

        C capability = (C) capabilities.get(capabilityClass);
        if (capability == null) {
            return cancelled;
        }
        lifecycleLock.lock();
        try {
            capability = (C) capabilities.get(capabilityClass);
            if (capability == null) {
                return cancelled;
            }
            if (capability.isImmutable()) {
                return false;
            }
            if (!capabilities.remove(capabilityClass, capability)) {
                return cancelled;
            }
            destroyAndUnregister(capability);
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 强制将所有已注册到全局事件总线的能力实例反注册。
     * <p>
     * 用于队伍/地图彻底销毁(删除/服务器关停)时清理各能力维持的 {@code @SubscribeEvent}
     * 监听器，避免事件总线长期强引用对象造成监听器累积内存泄漏。不可变能力同样在此清理。
     */
    public void unregisterAllFromBus() {
        lifecycleLock.lock();
        try {
            for (T capability : capabilities.values()) {
                try {
                    NeoForge.EVENT_BUS.unregister(capability);
                } catch (RuntimeException | Error ignored) {
                    // 从未注册过或已反注册的对象直接忽略，safeUnregister 语义
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void destroyAndUnregister(T capability) {
        Throwable cleanupFailure = null;
        try {
            capability.destroy();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        try {
            NeoForge.EVENT_BUS.unregister(capability);
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) {
                cleanupFailure = failure;
            } else {
                cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
    }

    /**
     * 获取队伍的能力
     *
     * @param capabilityClass 能力类型
     * @return 能力实例（Optional）
     */
    public final <C extends T> Optional<C> get(Class<C> capabilityClass) {
        return Optional.ofNullable((C) capabilities.get(capabilityClass));
    }

    /**
     * 获取指定类型的能力，如果不存在，则尝试创建并添加它。
     *
     * @param capabilityClass 能力类型
     * @return 存在或新创建的能力实例 (Optional)
     */
    public final <C extends T> Optional<C> getOrCreate(Class<C> capabilityClass) {
        Optional<C> existing = get(capabilityClass);
        if (existing.isPresent()) {
            return existing;
        }

        add(capabilityClass);
        return get(capabilityClass);
    }

    /**
     * 如果指定类型的能力存在，则对其执行提供的操作。
     * 无论能力是否存在，此方法都会返回 CapabilityMap 本身，以支持链式调用。
     *
     * @param capabilityClass 要检查的能力类型
     * @param action          如果能力存在，则执行的操作
     * @return 调用此方法的 CapabilityMap 实例
     */
    public final <C extends T> CapabilityMap<H, T> ifPresent(Class<C> capabilityClass, Consumer<C> action) {
        this.get(capabilityClass).ifPresent(action);
        return this;
    }

    /**
     * 检查队伍是否拥有指定能力
     *
     * @param capabilityClass 能力类型
     * @return 是否拥有
     */
    public final <C extends T> boolean contains(Class<C> capabilityClass) {
        return capabilities.containsKey(capabilityClass);
    }

    /**
     * 重置所有能力状态
     */
    public final void resetAll() {
        capabilities.values().forEach(FPSMCapability::reset);
    }

    public final <C extends T> void reset(Class<C> capability) {
        get(capability).ifPresent(FPSMCapability::reset);
    }

    public final List<T> values() {
        return capabilities.values().stream().toList();
    }

    public final List<String> capabilitiesString() {
        return values().stream().map(FPSMCapability::getName).toList();
    }

    public final List<String> synchronizableCapabilitiesString() {
        return values().stream().filter(cap -> cap instanceof FPSMCapability.CapabilitySynchronizable).map(FPSMCapability::getName).collect(Collectors.toList());
    }

    /**
     * 序列化指定能力到网络缓冲区
     *
     * @param capabilityClass 要序列化的能力类
     * @param buf             网络缓冲区
     */
    public final <C extends FPSMCapability<H> & FPSMCapability.CapabilitySynchronizable> void serializeCapability(Class<C> capabilityClass, FriendlyByteBuf buf) {
        get((Class<T>) capabilityClass).ifPresent(capability -> {
            ((FPSMCapability.CapabilitySynchronizable) capability).writeToBuf(buf);
        });
    }

    /**
     * 从网络缓冲区反序列化能力数据
     *
     * @param buf 网络缓冲区
     */
    public final void deserializeCapability(String className, FriendlyByteBuf buf) {
        FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(className, getCapabilityType()).ifPresent(capabilityClass -> {
            get(capabilityClass).ifPresentOrElse(
                    capability -> {
                        if (capability instanceof FPSMCapability.CapabilitySynchronizable synced) {
                            synced.readFromBuf(buf);
                        }
                    },
                    () -> FPSMCapabilityManager.createFromNetwork(this, className, buf)
                            .ifPresent(capability -> {
                                if (getCapabilityType().isAssignableFrom(capability.getClass())) {
                                    this.addDirectly((T) capability);
                                }
                            }));
        });
    }

    /**
     * 获取所有能力的流(Stream)，便于进行过滤、映射等操作。
     *
     * @return 包含所有能力的 Stream
     */
    public final Stream<T> stream() {
        return capabilities.values().stream();
    }

    /**
     * 获取需要同步的能力列表
     *
     * @return 需要同步的能力列表
     */
    public final <C extends FPSMCapability<H> & FPSMCapability.CapabilitySynchronizable> List<Class<C>> getSynchronizableCapabilityClasses(boolean check) {
        return values().stream()
                .filter(capability -> capability instanceof FPSMCapability.CapabilitySynchronizable sync && (sync.isDirty() || !check))
                .map(cap -> (Class<C>) cap.getClass())
                .collect(Collectors.toList());
    }

    public H getHolder() {
        return holder;
    }

    public Class<T> getCapabilityType() {
        return capabilityType;
    }

    public void clear() {
        Set<Class<? extends T>> classes = new HashSet<>(pendingAdditions.keySet());
        classes.addAll(capabilities.keySet());
        classes.forEach(this::remove);
    }

    public void write(String className, JsonElement data) {
        lockLifecycleInterruptibly();
        try {
            FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(className, getCapabilityType())
                    .ifPresent(clazz -> {
                        this.get(clazz).ifPresentOrElse(capability -> {
                            decodePublishedCapability(clazz, data);
                        }, () -> restoreCapability(clazz, data));
                    });
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void restoreCapability(Class<? extends T> capabilityClass, JsonElement data) {
        PendingAddition<T> pending = new PendingAddition<>();
        PendingAddition<T> active = pendingAdditions.putIfAbsent(capabilityClass, pending);
        if (active != null) {
            awaitPending(active);
            decodePublishedCapability(capabilityClass, data);
            return;
        }
        try {
            T published = capabilities.get(capabilityClass);
            if (published != null) {
                decodePublishedCapability(capabilityClass, data);
                pending.completion.complete(Optional.ofNullable(
                        capabilities.get(capabilityClass)));
                return;
            }
            if (isCancelled(pending)) {
                pending.completion.complete(Optional.empty());
                return;
            }
            Optional<? extends T> candidate = FPSMCapabilityManager.createInstance(
                    this.getHolder(), capabilityClass);
            if (candidate.isEmpty()) {
                pending.completion.complete(Optional.empty());
                return;
            }
            T capability = candidate.orElseThrow();
            if (!decodeCapability(capability, data)) {
                pending.completion.complete(Optional.empty());
                return;
            }
            initializeAndPublish(capabilityClass, capability, pending);
        } catch (RuntimeException | Error failure) {
            if (!pending.completion.isDone()) {
                pending.completion.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            pendingAdditions.remove(capabilityClass, pending);
        }
    }

    private void decodePublishedCapability(
                                           Class<? extends T> capabilityClass,
                                           JsonElement data) {
        capabilities.computeIfPresent(capabilityClass, (ignored, capability) -> {
            decodeCapability(capability, data);
            return capability;
        });
    }

    private boolean decodeCapability(T capability, JsonElement data) {
        if (!(capability instanceof FPSMCapability.Savable<?> savable)) {
            return true;
        }
        if (data.isJsonNull() || (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString() && data.getAsString().isEmpty())) {
            return false;
        }
        try {
            savable.decode(data);
            return true;
        } catch (Exception failure) {
            LOGGER.error("Error while write capability", failure);
            return false;
        }
    }

    public <D, C extends FPSMCapability<H> & FPSMCapability.Savable<D>> void write(Class<C> clazz, D data) {
        lockLifecycleInterruptibly();
        try {
            get((Class<T>) clazz).ifPresent(cap -> {
                ((FPSMCapability.Savable<D>) cap).write(data);
            });
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void write(Wrapper wrapper) {
        this.write(wrapper.data());
    }

    public void write(Map<String, JsonElement> data) {
        data.forEach(this::write);
    }

    public Wrapper getData() {
        lockLifecycleInterruptibly();
        try {
            Map<String, JsonElement> map = new HashMap<>();

            for (Map.Entry<Class<? extends T>, T> entry : capabilities.entrySet()) {
                String key = entry.getKey().getSimpleName();
                T capability = entry.getValue();

                if (capability instanceof FPSMCapability.Savable<?> savable) {
                    try {
                        map.put(key, savable.toJson());
                    } catch (Exception e) {
                        LOGGER.error("Failed to serialize capability: {}", key, e);
                        map.put(key, new JsonPrimitive(""));
                    }
                } else {
                    map.put(key, new JsonPrimitive(""));
                }
            }

            return new Wrapper(map);
        } finally {
            lifecycleLock.unlock();
        }
    }

    public record Wrapper(Map<String, JsonElement> data) {

        private static final Codec<JsonElement> JSON_CODEC = Codec.PASSTHROUGH.xmap(
                dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
                value -> new Dynamic<>(JsonOps.INSTANCE, value));
        public static final Codec<Map<String, JsonElement>> DATA_CODEC = Codec.unboundedMap(Codec.STRING, JSON_CODEC);
        public static final Codec<Wrapper> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DATA_CODEC.fieldOf("capabilities").forGetter(Wrapper::data)).apply(instance, Wrapper::new));

        public JsonElement encode() {
            return CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow(e -> { throw new DataPersistenceException("Error while encode capability map to json."); });
        }

        public static class Builder<H> {

            private final Map<String, JsonElement> data = new HashMap<>();

            public <T extends FPSMCapability<H>> Builder<H> add(Class<? extends T> clazz, JsonElement value) {
                data.put(clazz.getSimpleName(), value);
                return this;
            }

            public Builder<H> add(String key, JsonElement value) {
                data.put(key, value);
                return this;
            }

            public JsonElement encode() {
                return DATA_CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(e -> { throw new DataPersistenceException("Error while encode capability map to json."); });
            }

            public Wrapper build() {
                return new Wrapper(data);
            }
        }
    }

    private static final class PendingAddition<C> {

        private final CompletableFuture<Optional<C>> completion = new CompletableFuture<>();
        private final Thread ownerThread = Thread.currentThread();
        private boolean cancelled;
    }
}
