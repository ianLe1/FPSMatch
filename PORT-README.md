# FPSMatch — 1.21.1 / NeoForge 移植

把 [FPSMatch](https://github.com/PhasetransCrystal/FPSMatch)（1.20.1 Forge）移植到
**Minecraft 1.21.1 + NeoForge 21.1.253**。分支 `1.21.1-neoforge-port`。

上游：`PhasetransCrystal/FPSMatch`，GPL-3.0。移植基于上游 `429cbd83`。

## 状态

| 项 | 结果 |
|---|---|
| 编译 | 错误 206 → 0（`./gradlew compileJava --offline`） |
| 产物 | `build/libs/fpsmatch-1.3.0-1.21.1-port.jar` |
| 服务端实机 | 与 BlockOffensive / codPattern 同服启动成功：`Done (1.351s)` |

## 构建

```bash
GRADLE_USER_HOME=$PWD/.gradle-home ./gradlew jar --offline
```

`libs/` 需要第三方 jar，见 [libs/README.md](libs/README.md)（不入库，请自行下载）。
**不要跑 `./gradlew clean`**：NeoForge 的中间工件不在 `.gradle-home` 的普通缓存里，离线环境下清掉后无法重新解析。

## 主要改动

- **网络层**：新增 `common/packet/register/PayloadContext.java` 与 `ReflectivePayload.java` 垫片，
  `NetworkPacketRegister` 改走 `PayloadRegistrar`；观众包重写；64 个文件的 import 改名。
- **NBT → DataComponents**：新增 `util/ItemNbt.java`，用 `DataComponents.CUSTOM_DATA` + `CustomData.getUnsafe()`
  保持 1.20.1「返回栈上同一实例、原地修改即生效」的旧语义（1.21.1 已删除 `getOrCreateTag/getTag/setTag`）。
- **事件**：删除全部 `isCancelable()`，改为 `implements ICancellableEvent`；
  `TickEvent` → `event.tick.{ServerTickEvent,PlayerTickEvent}.Post` / `client.event.{...}`；
  HUD 从 `IGuiOverlay` 迁到 `LayeredDraw.Layer`；`EventBusSubscriber.Bus.FORGE` → `Bus.GAME`；
  `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`。
- **注册 API**：`ForgeConfigSpec` → `ModConfigSpec`；`DeferredHolder` 泛型修正；`BuiltInRegistries` 包名。
- **运行期规则 1–7**：这些在编译期完全看不出来，只在 NeoForge 1.21.1 实机启动时才炸。
  完整说明见 [NEOFORGE-1.21.1-RUNTIME-RULES.md](NEOFORGE-1.21.1-RUNTIME-RULES.md)。
- **删除**：`bukkit/`（Spigot 端整套）与 `mixin/compat/grenades/FlashBangStatsMixin.java`（1.21.1 无对应 mod）。

## tools/ 静态扫描器

`tools/` 下都是幂等脚本，是这次移植的主要工作方式（一次改一批、编译再收敛）：

| 脚本 | 作用 |
|---|---|
| `scan_bus.py` | 规则 1：`@SubscribeEvent` 参数的 mod 总线 / game 总线归属（javap 递归判 `IModBusEvent`） |
| `scan_runtime_rules.py` | 规则 2 / 4：空订阅类、需 static 的订阅方法 |
| `scan_supertypes.py` | 规则 3：被注册类的祖先是否带 `@SubscribeEvent` |
| `scan_dist_cleaner.py` | 规则 6：`@EventBusSubscriber` 类声明方法的签名里是否含客户端类型 |
| `scan_mixin_targets.py` | 规则 5：`@At(target="...")` 串是否还能在目标 jar 里找到 |

## WarBorn Renewed 弹道防护兼容

新增 `compat/warborn/`（移植时不存在，本分支新写）。

**背景**：Warborn Renewed（`ru.liko.warbornrenewed` 0.6.1）定义了
`ModAttributes.BULLET_RESISTANCE` / `PROTECTION_CLASS`，并给出
`getBulletResistance` / `getProtectionClass` / `isPenetrated(protectionClass, kinetic)` /
`calculateDamage(base, bulletResistance, penetrated)` 四个 `public static` 方法，
但**在整个 mod 里零调用者**——它自带的 `compat/TACZIntegration` 同为死代码。
所以原版 Warborn 护甲对 TaCZ 枪械实际**没有任何防护效果**。这一层就是那个缺失的消费者。

- **挂点**：FPSMatch 的 `FPSMGunDamageEvent`（由 `compat/tacz/TACZGunEventBridge` 从 TaCZ 的
  `EntityHurtByGunEvent.Pre` 转发而来）。所有 FPSMatch 附属都走这个事件，
  BlockOffensive 等**自动获得同一保护**。
- **软依赖**：`WarbornCompat` 只用 `ModList.get().isLoaded("warbornrenewed")` 做布尔门控，
  不 import 任何 `ru.liko.*` 类型；真正引用 Warborn API 的是内层 `WarbornBallisticHandler`
  （`@SubscribeEvent(priority = LOW)`，排在 `GunDamageHandler` 之后）。
  未装 Warborn 时行为与移植前完全一致。
- **公式**：直接调原 mod 的方法（`isPenetrated` → 是否穿透；`calculateDamage` → 最终伤害），
  **不自造公式**。
- **动能估算（重要，是估算）**：TaCZ 的 `BulletData` **没有**弹头质量/动能字段，
  故按 ½mv² 估算，`v = |子弹速度| × 20`（格/tick → m/s）；拿不到子弹实体时用配置回退速度。
  质量默认 0.008 kg（≈9mm）。Warborn 的穿透阈值是
  `{0, 600, 800, 1000, 3500, 5000, 8000}`（焦耳），默认参数下回退动能 ≈ 640 J，
  低防护等级几乎必定穿透——**需要在实机里按第一条 INFO 日志里的实测动能标定**。
- **护甲磨损**：仅在未被穿透时，爆头磨头盔、躯干磨胸甲（各一件）。伤害已被上游清零
  （出生保护、队友免伤）时不参与计算也不磨损。
- **配置**：`config.warborn.*` → `compat.warborn.{enabled, bulletMassKg, fallbackSpeedMps,
  damageArmorDurability, armorDurabilityLossPerHit}`。

## 许可

上游 GPL-3.0，本移植同样以 GPL-3.0 发布，见 [LICENSE](LICENSE)。
依 GPLv3 §5(a) 声明：本分支相对上游做了修改，修改内容即上述移植改动。
