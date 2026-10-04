package net.ptcrys.fpsmatch.compat.spectate.net;

/**
 * 观众同步包（TACZ + LRTactical）。
 * <p>
 * 上游用独立的第二条 {@code SimpleChannel}；NeoForge 1.21 取消了 {@code SimpleChannel}，
 * 因此这里不再自建通道，4 个包统一并入 {@code FPSMatch} 的主 {@code NetworkPacketRegister}，
 * 由 {@code FPSMatch#onRegisterPackets} 在 {@code RegisterPayloadHandlersEvent} 里注册。
 */
public final class SpectatorSyncNetwork {

    private SpectatorSyncNetwork() {}
}
