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

## 许可

上游 GPL-3.0，本移植同样以 GPL-3.0 发布，见 [LICENSE](LICENSE)。
依 GPLv3 §5(a) 声明：本分支相对上游做了修改，修改内容即上述移植改动。
