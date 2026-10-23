# 受击、重击倒地、重伤瘫倒 V1（含车辆撞击伤害）

**状态：第 1–8 节用户 2026-10-22 多人实机 PASS（专用服务器 + 两个客户端）。第 9 节活人布娃娃：探针在单人和专用服务器上验过，用户看画面说没问题，已提交 f7882710（D 分支 `feature/lightning-injury`）。第 9.7 节慢速轻碰（倒车撞人）：GameTest PASS，用户 2026-10-22 专用服务器 + 两个客户端实机 PASS。** 新同步包 `HealthPackets.Hit`、`HealthPackets.Ragdoll` 需要 Main 在合并时给协议号（分支仍为 68）。

接在 [fall_landing_v1_plan.md](fall_landing_v1_plan.md)、[lightning_injury_v1_plan.md](lightning_injury_v1_plan.md) 后面，复用它们的「摔倒 / 趴地 / 起身」框架（`health/FallLanding`、`client/skeleton/FallLandingClient`、`client/skeleton/ProneClient`）。

## 1. 用户的要求（2026-10-22）

- 「先做受伤倒地的骨骼动作」（Main 的通知原话是「受伤落地」）。用户选了三项全做：受击反应、重击倒地、重伤瘫倒；触发「按来源和伤害量」。
- 「现在车，没有撞击伤害，需要我们这边先做」：T15 撞人 / 撞生物的伤害由 D 来做，撞得重的进「重击倒地」。车辆归 Main，改动只在 `PickupTruckEntity` 里加一行调用，逻辑放在新类 `vehicle/VehicleImpact`（第 5 节）。

## 2. 受击反应（每次受伤）

- 有来源位置的伤害（被打、被枪打中、爆炸、车撞）在不致死时，服务端发 `HealthPackets.Hit(实体, 来源方向, 强度)` 给本人和看得到他的玩家。摔落、雷击没有来源方向，不触发。
- 强度 = 扣掉的生命值 / 最大生命值。
- 客户端 `client/skeleton/HitReactClient`：身体朝**远离来源**的方向晃一下。正面中弹往后仰，背后中弹往前扑，侧面中弹往另一侧歪。
- 晃动是阻尼振动：`e^(-ζωt)·sin(ω_d·t)`，ω = 14 rad/s，ζ = 0.5，约 0.09 s 到最大，0.5 s 内消失。
- 幅度 = 6° + 30° × 强度，最多 25°。几次受伤会叠加。
- 骨盆、胸、头分别吃 0.25、0.5、0.5（`HumanoidPoser.Input.flinchPitch / flinchRoll`）。
- 只动身体，不动镜头。

## 3. 重击倒地

### 3.1 什么时候倒

- **一定倒**（这一下不致死，且扣血至少 `always_min` = 8）：爆炸（`minecraft:explosion`、`minecraft:player_explosion`）、倒下的树（`apocalypse_firstlight:falling_tree`）、车辆撞击（`apocalypse_firstlight:vehicle`）。
- **其余有来源的伤害**：概率 `clamp((扣血 - 25) / 30, 0, 1)`。按 100 生命值算，扣 35 时约 0.33，扣 55 起一定倒。修正如下，最后封顶为 1：

  | 情况 | 概率 |
  |---|---|
  | 负重超过轻载 | +0.15 |
  | 腿有骨折 | ×1.5 |
  | 疾跑中 | +0.15 |
  | 潜行 | ×0.5 |

- 例子：
  - 感染者一爪 12，不会倒。
  - P9 一枪 30，约 17 %。
  - BR-51 一枪 62，一定倒。
- 没有来源位置的「一定倒」（倒下的树的伤害不带位置）：往前扑（`knock_front`），也没有受击晃动。
- 不触发重击倒地的情况：
  - 摔落（已有 `fall_over`）、雷击（已有自己的倒地）；
  - 在水里、坐着（乘客）；
  - 已经在摔倒或起身的动作里；
  - 已经趴在地上（雷击、瘫倒、钻低处）。
- 数值都在 `health_v1.json` 的 `knockdown` 段，可 `/reload`。

### 3.2 往哪边倒

按来源相对身体朝向分：

| 来源在 | 倒向 | 动作 |
|---|---|---|
| 前方 ±50° | 往后倒 | `body/knock_back` |
| 后方 ±50° | 往前扑 | `body/knock_front` |
| 右侧 | 往左倒 | `body/knock_side` |
| 左侧 | 往右倒 | `body/knock_side` 镜像 |

### 3.3 三个动作（作者脚本 `tools/medical-actions-v1a/author_knockdown_v1.cjs`）

每个动作是一整段：倒下、躺约 1 s、起身。期间不能移动、不能用物品；镜头在动作的眼睛上，俯仰跟 `first_person_view`。

- `knock_front`，4.5 s，0.8 s 着地：
  - 往前踉跄，双手先撑地，扑到地上，趴着；
  - 1.9 s 起接雷击起身的同一套动作（撑起、收膝、四肢着地、半跪、扶膝站起）。
- `knock_back`，3.8 s，0.62 s 着地：
  - 膝盖一软，往后坐倒，躺平仰面；
  - 坐起，双手撑在身后，两脚收到身下，蹲起，站直。
- `knock_side`，3.7 s，0.55 s 着地：
  - 往左倒，左手先撑，侧躺；
  - 翻成四肢着地（2.4 s），然后接雷击起身的后半段（右脚踩到前面，扶膝站起）。

离线检查（脚本自带，每 0.05 s 采样）：身体方块不低于地面，手肘、手掌、膝盖、脚踝不低于 23.75（地面 24，单位 Java px）。剩下的最深处：

| 动作 | 最低肢体点 | 说明 |
|---|---|---|
| `knock_front` | 膝盖 24.66 | 来自雷击起身（用户已通过）原样接上 |
| `knock_back` | 23.39 | 无 |
| `knock_side` | 脚踝 24.05、左膝 24.0 | 前者来自雷击起身；后者在侧躺翻成四肢着地的 0.1 s 里 |
| `collapse` | 膝盖 23.87 | 无 |

右手在侧倒的几帧（1.7–2.05 s）够不到地，手停在地面上方 2–4 px，看起来是伸手去撑。

### 3.4 其他效果

- 手里的东西有 30 % 的几率掉出去（和雷击同样的扔法）。
- 着地时有身体倒地声（和摔倒同一个声音，同样的噪声半径）。
- 车撞的另外判腿骨折（第 5 节）。
- 已知限制：倒地期间碰撞箱仍然是站立的高度，V1 不改。

## 4. 重伤瘫倒

- **什么时候**：一次受伤后剩余生命值 ≤ `collapse.at` = 15（满 100），且不致死。
  - 不触发：在水里、坐着、已经趴着。
  - 不算的伤害类型（慢性的，不是"一下子打垮"）：饥饿、脱水、失温、中暑、雷击（雷击有自己的昏迷和瘫痪）。
  - 瘫倒优先于重击倒地。
- **动作** `body/collapse`，2.0 s，1.35 s 胸口着地：
  - 膝盖一软跪下，上身往前一栽，双手没撑住，趴倒；
  - 最后正好是趴地爬行的姿势（`prone.idle`）。
  - 动作**一开始**服务端就把玩家放成趴姿（强制 `SWIMMING`，碰撞箱 0.6 高）。趴地站姿（`ProneClient`）的根偏移在 0.35 s 内渐入，动作脚本按这条渐入曲线反向补偿（`eased`），所以动作里的身体位置不跳；动作放完接趴地的站姿和爬行动作。
- **趴着时**：
  - 只能爬，速度是爬行的 `collapse.crawl` = 0.6 倍；
  - 不能跳，不能疾跑；
  - 可以用物品，比如自己包扎。
- **站起来**：至少趴 `collapse.min_seconds` = 12 s，然后满足下面任一条件就站起来（`FallLanding.getUp`，2.6 s 起身动作）：
  - 生命值回到 `collapse.up_at` = 20；
  - 趴满 `collapse.max_seconds` = 45 s（缓过来了）。

  头顶有东西挡着时继续爬，等能站了再站。
- **不存档**：下线再上线不会接着趴。重伤的人如果又被打到 ≤ 15，会再次瘫倒。
- **提示**：动作栏显示「站不住了」，站起时显示「缓过来了，能站起来了」。

## 5. 车辆撞击伤害（`vehicle/VehicleImpact`）

T15 以前撞到实体只会把它推开，不伤人。

- **判定**：服务端每 tick 在车移动之后、推开实体之前判定。
  - 对象：碰撞箱（放宽 0.15 + 车速 × 0.03 格）碰到的活体，包括玩家。
  - 不算：车上乘客、旁观者、创造模式玩家。
- **相对速度**：取车上接触点的速度（质心速度 + 角速度 × 力臂），减去对方的速度（非玩家），投到「车质心 → 对方」的水平方向上，得到闭合速度。
  - 擦边、并排时这个值很小。
  - 低于 0.8 m/s（约 3 km/h，`vehicle.min_speed`）只推不伤。
  - 0.8–1.5 m/s（`vehicle.knock_speed`，比如慢慢倒车碰到人）是轻碰：扣 0.5–1 血，玩家踉跄一下、被推开，不撞倒。2026-10-22 以前门槛是 1.5 m/s，慢慢倒车只会把人推开，见第 9.7 节。
  - 同一个实体 10 tick 内只算一次。
- **伤害**（按 100 生命值的人算，`health_v1.json` 的 `vehicle.damage` 曲线）：

  | 车速 | km/h | 扣血 |
  |---|---|---|
  | 0.8 m/s | 3 | 0.5 |
  | 1.5 m/s | 5 | 1 |
  | 3 m/s | 11 | 4 |
  | 5.6 m/s | 20 | 15 |
  | 8.3 m/s | 30 | 36 |
  | 11.1 m/s | 40 | 62 |
  | 13.9 m/s | 50 | 90 |
  | 16.7 m/s | 60 | 120 |

  - 对玩家，按原版尺度传 扣血 / 5，再由默认 ×5 换算回来，所以护甲照常起作用。
  - 对生物同样传 扣血 / 5。原版僵尸 20 血、2 点护甲，约 55 km/h 被撞死；50 km/h（扣 90，原版 18 点）撞不死。
- **撞飞**：对方沿撞击方向得到 `1.2 × 闭合速度 × 车重 / (车重 + 对方重量)` 的水平速度，外加 0.15 × 闭合速度的上抛，上抛最多 3 m/s。
  - 车重取 T15 的 1450 kg。
  - 对方重量按碰撞箱体积估算，人约 70 kg。
- **伤害类型** `apocalypse_firstlight:vehicle`：
  - 直接来源是车，凶手是司机，没人开时凶手是车；
  - 死亡信息「%1$s 被 %2$s 撞死了」；
  - 受击反应的来源方向就是车的位置。
- **骨折**：玩家被撞时按扣血判一条腿 `clamp((扣血 - 10) / 50, 0, 0.7)`（`vehicle.leg`）。
- **声音**：撞到时在对方位置播身体倒地声，噪声半径同重落地。
- **撞死时的布娃娃**：玩家被撞死时，尸体布娃娃（Ragdoll V1，`survivor/SurvivorRagdolls`）按撞飞速度 × 75 kg 的冲量，一半推骨盆、一半推胸口（`VehicleImpact.thrown`），顺着撞击方向飞出去。
- **已知限制**：
  - 车本身不减速：司机的客户端在跑车的物理，撞人对车没有反作用。
  - 活人被撞（没撞死、闭合速度不低于 1.5 m/s）变成活人布娃娃，见第 9 节。
  - 司机是玩家时，撞玩家受服务器 PvP 设置管：`pvp=false` 的服务器上车撞不伤玩家（原版 `ServerPlayer.hurt` 的规则）。单人和 AFL 的 `run-server` 都开着 PvP。
  - 服务端不知道玩家自己的速度（客户端在移动它），所以对玩家只算车的速度。
  - 撞到时车身没有凹痕或声音变化。

## 6. 实现（2026-10-22）

- **服务端** `health/Knockdown`：`onDamage(player, source, healthLost)` 由 `PlayerHealth.damage` 在 `LightningInjury.onDamage` 之后调用。
  - 依次判断受击包、瘫倒、重击倒地；
  - 瘫倒状态的 tick、移动修正、跳跃限制、站起都在这里。
- **`FallLanding`** 新增动作种类：

  | 种类 | 值 | 长度 |
  |---|---|---|
  | `KNOCK_FRONT` | 3 | `KNOCK_FRONT_TICKS` 90，第 16 tick 着地声 |
  | `KNOCK_BACK` | 4 | `KNOCK_BACK_TICKS` 76，第 12 tick |
  | `KNOCK_SIDE` | 5 | `KNOCK_SIDE_TICKS` 74，第 11 tick（镜像 = 往右倒） |
  | `COLLAPSE` | 6 | `COLLAPSE_TICKS` 40，第 27 tick |

  - 公开方法 `FallLanding.knock(player, kind, mirror)`，着地声由 `FallLanding.tick` 在着地那一 tick 播。
  - 摔倒期间禁止使用、攻击，这一套原样复用。
- **客户端** `FallLandingClient` 按种类放对应动作。收到本人的 `COLLAPSE` 时 `health/ClientKnockdown` 记下「瘫倒中」，用来本地禁止跳跃（`Knockdown.jump` 两端都拦）；本人起身（`GET_UP`）、死亡或离开世界时清掉。
- **和雷击伤的关系**：雷击趴着时不会瘫倒；瘫倒期间雷击的麻痹结束，不会把人放起来，由 `Knockdown` 负责站起。手里东西飞出去用雷击伤同一个方法（`LightningInjury.throwHeld`）。
- **受击**：`HealthPackets.Hit`（新包）→ `client/skeleton/HitReactClient` → `PlayerSkeleton.solve` 写入 `HumanoidPoser.Input.flinchPitch / flinchRoll`，`HumanoidPoser.solveBody` 在胸和头之后叠加。
- **车撞**：
  - `vehicle/VehicleImpact.tick(truck, physics)`，由 `PickupTruckEntity.tick` 在 `carry()` 之前调用（服务端）；
  - 伤害类型在 `data/apocalypse_firstlight/damage_type/vehicle.json`；
  - 死亡信息在中英文语言文件里。
- **数值**：`data/apocalypse_firstlight/health/health_v1.json` 的 `knockdown`、`collapse`、`vehicle` 三段，由 `HealthConfig` 读取并校验。
- **动画文件**：`animations/player/knock.animation.json`（`knock.front / back / side / collapse`），动作 `player_actions/body/knock_front.json`、`knock_back.json`、`knock_side.json`、`collapse.json`，都由 `tools/medical-actions-v1a/author_knockdown_v1.cjs --write` 生成。
- **受击晃动的符号**：`flinchPitch` 正 = 往前倾，`flinchRoll` 正 = 往左倾（模型空间前方 -z、右方 -x）。骨盆转动时大腿反向转回去，脚不动。
- **协议**：新增 `HealthPackets.Hit`，注册在 `Landing` 之后，后面所有包的编号顺移。分支协议号仍是 68，合并时 Main 要升号。

## 7. 要用户实机看的

1. 被感染者抓、被枪打中时，身体往远离来源的方向晃一下，镜头不动；第三人称和别的玩家看得到。
2. 吃一发 BR-51，或用 `/summon tnt` 在旁边炸：往远离来源的方向倒下，躺约 1 s 再起来；手里的东西可能掉。
3. 前、后、左、右四个方向各倒一次，看动作有没有穿地或抖动。
4. 生命值打到 ≤ 15：跪下栽倒，变成趴着爬；不能跳；生命值回到 20，或 45 s 后站起来。
5. 开 T15 以不同速度撞僵尸和另一个玩家：
   - 5 km/h 以下只推开；
   - 20 km/h 掉血、倒地；
   - 约 55 km/h 撞死僵尸（50 km/h 撞不死）；
   - 撞死玩家时尸体顺着撞的方向飞出去；
   - 死亡信息正确。

## 8. 撞人要不要布娃娃（用户 2026-10-22 问）

- 已有的布娃娃（Ragdoll V1）只用在玩家死后的尸体上。
- **撞死**：用现有的尸体布娃娃，把撞飞速度作为死亡冲量（第 5 节），尸体顺着撞的方向飞出去。
- **没撞死**：人按撞飞速度被抛出去，落地时放重击倒地动作，再起身。
- **活人布娃娃**：用户 2026-10-22 实机后要求现在做（「被撞那一下镜头没反应 现在先做活人布娃娃吧，碰撞照常」），见第 9 节。

## 9. 活人布娃娃（用户 2026-10-22）

用户实机反馈：被车撞的那一下第一人称镜头没反应。要求：没撞死的人被撞时也变成布娃娃，碰撞照常。

### 9.1 流程

1. **触发**（`health/Knockdown.onDamage`）：车辆撞击（`VehicleImpact.thrown` 不为空）、没在水里、没坐车、没睡觉、没被雷击放倒、没在摔倒动作里，同时在跑的活人布娃娃少于 8 个（`LiveRagdolls.MAX_ACTIVE`）。
   - 原来的重击倒地动作不放；
   - 已经瘫倒的人先解除瘫倒，再被撞飞；
   - 手里东西照样按 `knockdown.drop` 几率脱手。
2. **模拟**（服务端 `survivor/LiveRagdolls`）：
   - 用尸体布娃娃同一套 XPBD 身体（`SurvivorRagdolls.stance` 按玩家当前姿势建身体）；
   - 冲量和撞死时一样：撞飞速度 × 75 kg × 0.5，分别推骨盆和胸口；
   - 每 tick 发 `HealthPackets.Ragdoll`（FALLING）：各个盒子的位置朝向（`RagdollSnapshot`，相对骨盆）和锚点（骨盆正下方、身体最低点的高度）；
   - 飞行中再被撞，冲量直接加到身体上（`VehicleImpact.hit`），不再给玩家速度。
3. **碰撞照常**：玩家自己的碰撞箱不变形，客户端每 tick 把它往锚点移（`setDeltaMovement`，每 tick 最多 3 格），原版移动负责碰撞。
4. **停下**（身体静止）：
   - 胸口朝上用 `knock_back` 起身，朝下用 `knock_front` 起身；
   - 把动作的骨盆对到布娃娃的骨盆上、动作「骨盆 → 头」的方向对到布娃娃的上，得出起身动作的根位置和朝向（`LiveRagdolls.align`）；
   - 玩家传送到根位置（站立碰撞箱放不下时最多往上找 0.6 格，还不行就留在原地），朝向转过去；
   - 起身动作从躺着的那一段开始放：`knock_front` 从第 30 tick，`knock_back` 从第 28 tick（`LiveRagdolls.from`）；
   - 生命值 ≤ `collapse.at` 时不起身，原地瘫倒（`Knockdown.collapseWhereLying`，趴着爬，规则同第 4 节）。
5. **飞行中死了**：同一个布娃娃身体交给尸体（`SurvivorCorpses.create` → `LiveRagdolls.take`），不重新建；如果是车撞死的，那一下的冲量照第 5 节加到这个身体上。
6. **中途结束**：下线、换维度、坐上车、变旁观时丢掉布娃娃（`ENDED`），不放起身动作。

### 9.2 客户端（`client/survivor/LiveRagdollClient`）

- **姿态**：每 tick 取一个快照（最多排 3 个，防网络抖动），按 partial tick 在最近两个之间插值（`CorpsePoses.pose`），换到玩家自己的模型坐标，`PlayerSkeleton.solve` 直接用它代替求解。
- **第一人称镜头**：飞行中镜头在布娃娃的眼窝上，随头转（`CameraDirector`，优先级 550：高于过场，低于死亡镜头），淡入 0.12 s、淡出 0.35 s，鼠标、移动、攻击、使用都锁住；身体照常画（`FirstPersonBody.shown`），手是身体的手（`PlayerSkeleton.firstPersonHands`）。
- **起身衔接**：
  - 起身动作在服务端给的根位置和朝向上求解（`alignedYaw`、`afterSolve`），和实体自己画在哪、身体朝哪无关，所以别的玩家看到的位置插值不影响；
  - 画出来的姿势从布娃娃最后一帧过渡到动作，0.4 s（瘫倒 0.5 s）；
  - 动作结束、淡出之后才撤掉，这期间骨盆和胸口的朝向固定在躺的朝向，起来以后不会再转身。
- **本人**：飞行和起身期间不能移动、使用、疾跑（`FallLandingClient.fallingOver`）。

### 9.3 已知限制

- 瘫倒时布娃娃如果是仰面，会在 0.5 s 里过渡成趴着，看起来像翻身。
- 别的玩家的实体位置跟着本人客户端走，落后一点；布娃娃按服务端坐标画，不受影响。实体的视锥裁剪仍按碰撞箱，布娃娃飞出碰撞箱很远时边缘可能被裁掉。
- 只有车撞触发。爆炸、倒下的树还是原来的倒地动作。
- 协议：`HealthPackets.Ragdoll` 注册在 `Hit` 之后，后面所有包的编号顺移，合并时 Main 要升号。

### 9.4 要用户实机看的（专用服务器 + 两个客户端，一人开车撞另一人）

1. 20–40 km/h 撞人：被撞的人飞出去、落地、滚停，然后从躺着的姿势起来；起身前后不跳位置、不突然转身。
2. 被撞的人开第一人称：镜头跟着头飞出去、翻滚，落地后平滑接到起身动作的视角；身体和手看得到，不穿模。
3. 另一个客户端看：飞行轨迹顺、和本人看到的一致；起身的位置和朝向对。
4. 撞到墙边、坡上、树旁：碰撞箱不卡进方块，起身位置合理。
5. 生命值低时撞（≤ 15 剩余）：停下后原地瘫倒，趴着爬。
6. 50 km/h 以上撞死：尸体接着同一个飞行姿势，不跳一下，死亡镜头接管。

### 9.5 实机反馈修复（2026-10-22）

- 用户：「好像客户端的动画不同步」：别的客户端看到人趴着，本人客户端里却站着。
- 原因：重伤瘫倒（第 4 节）只在服务端设了强制姿势（`setForcedPose(SWIMMING)`）。本人客户端自己算姿势，没设，所以站着。雷击趴地那边客户端是自己设的（`LightningInjuryClient`），这里漏了。
- 修复：
  - `FallLandingClient.tick`：本人处于瘫倒（`ClientKnockdown`）时也强制趴着，起身后恢复；还在雷击趴地中时不恢复。
  - `LightningInjuryClient` 雷击结束时，人还瘫倒就不把姿势恢复成站立。
  - 本人重生或换维度（`ClientPlayerNetworkEvent.Clone`）时清掉客户端的瘫倒标记。
  - 服务端 `Knockdown.respawn`：死亡重生后清掉瘫倒记录，下一个幸存者不会带着瘫倒出生。

### 9.6 自动测试探针（2026-10-22）

用户反馈“被车撞后第一人称还是原版手、镜头不动”，写了探针自己开客户端复现。

- **单人**：`gradlew runClient --offline -I src/dev/live-ragdoll-probe-client.init.gradle`
  - 在 `build/live-ragdoll-probe` 里跑；
  - 用的是 `saves/live_ragdoll_probe`，从 `run/saves` 复制的存档。旧存档要在它的 `serverconfig` 里放行世界锁。
- **专用服务器**：
  - 先跑 `gradlew runServer --offline -PaflWithoutShaders -I src/dev/live-ragdoll-probe-server.init.gradle`（`build/live-ragdoll-server`，端口 25566，世界是 `run-server/world` 的副本）；
  - 服务器就绪后，用同一个 init 脚本跑 `runClient`，客户端会自动连进去。
- **流程**：
  - 服务端探针 `dev/LiveRagdollServerProbe` 在 y 200 搭石台，把玩家放上去（生存、满血、朝南）；
  - 在玩家身后放一辆 T15，通过 `VehicleImpact.hit` 撞人，速度 8.5 m/s；
  - 共撞三次：第一人称一次、第三人称一次、血量 50 时第一人称一次（撞后剩 12.1，停下瘫倒）。
- **客户端探针** `dev/LiveRagdollProbe`：
  - 看到血量下降，就开始逐 tick 记录：布娃娃状态、镜头请求、第一人称身体和手、原版手画了几帧、瘫倒和趴下；
  - 记录写进 `afl_live_ragdoll_probe_<sp|mp>.txt`，每 4 tick 截一张图，跑完自动退出；服务端等玩家下线后关服。
- **探针绕过了什么**：直接调用撞击函数，不经过车辆的碰撞检测（`VehicleImpact.tick`）；真开车撞人仍要实机看。
- **结果**：单人和专用服务器都正常。
  - 撞击当 tick 布娃娃启动，第一人称镜头请求打开，镜头跟头翻滚，飞行的 55 tick 里原版手 0 帧；
  - 停下后起身动作接上，约 6 秒后恢复；
  - 低血量的一次停下后瘫倒，状态是 `SWIMMING` 加 AFL 趴下，手由身体画；
  - 第三人称不开镜头请求。
  - 代码没改。用户看到的现象没能复现，可能是那次被直接撞死、由死亡镜头接管，也可能当时客户端还是旧版本。用户看了探针跑的画面后说“好像是没问题了”。

### 9.7 倒车撞不到人（用户 2026-10-22「车屁股倒车好像不能撞到人」）

用户是在活人布娃娃做出来之前、多人实机那一轮报的。

**先量**，按“先测再解释”：
- **倒车车速**（离线跑车辆物理）：挂 R 不踩油门不会动；全油门 1 m 后 2.5 m/s，2.7 m 后 4.2 m/s，3 s 后到顶 8.6 m/s。
- **服务端检测**：新 GameTest `dev/TruckImpactGameTests`（`gradlew runGameTestServer --offline -PaflWithoutShaders -I src/dev/truck-impact-gametest.init.gradle`）。
  - 驾驶座上坐一个模拟司机，服务器就不自己跑车的物理；
  - 每 tick 按司机同步包的方式喂状态（`physics.apply` + `place`，同 `acceptDrive`），让车朝离保险杠 1 m 的目标开过去；
  - 目标有两种：一只不动的僵尸，一个生存模式玩家（打开 PvP、跳过新角色设置）。
- **结果**：倒车和前进完全对称。
  - 僵尸：2.5 / 4 / 6 m/s 倒车，以及斜放 37° 倒车，都撞到了，伤害和前进一样；
  - 玩家：1.8 / 2.5 / 4 m/s 倒车都撞到并进入活人布娃娃。
- **测试里踩到的三个坑**（都不是游戏的问题）：
  - 刚登录的玩家有 60 tick 出生无敌；
  - 新角色在设置界面时伤害全被取消（`SurvivorSetupFlow`）；
  - GameTest 服务器默认关 PvP，玩家司机撞玩家会被原版规则挡掉。

**结论**：服务端检测、车尾碰撞箱、方向都没问题。真正漏掉的是慢速：低于 1.5 m/s 时车只把人推开（客户端 `pushAside`），没有伤害、没有受击反应，看起来就像没撞到。现实里车用步行速度倒车碰到人，人也会被撞得踉跄。

**改法**（`health_v1.json` 的 `vehicle`）：
- `min_speed` 1.5 → 0.8 m/s；
- 新增 `knock_speed` 1.5 m/s：低于它是轻碰，`VehicleImpact.bump` 为真时 `Knockdown` 不启动活人布娃娃，走普通受击（踉跄，`HealthPackets.Hit`），外加按撞击速度推开；
- 伤害曲线前面加两点：0.8 m/s 扣 0.5、1.5 m/s 扣 1（原来 1.5 m/s 扣 0）。

**GameTest 结果（改后）**：
- 倒车 1.0 m/s：僵尸 20 → 19.91，推开 0.92 m/s；玩家 100 → 99.49，没进布娃娃；
- 倒车 1.8 m/s：玩家 100 → 98.74，进布娃娃；
- 其余前进 / 倒车用例照旧撞到。

**实机**：用户 2026-10-22 在专用服务器 + 两个客户端上倒车撞人，PASS。真实网络下被撞的人在自己客户端里先被车推开（`pushAside`），服务端仍能看到重叠、照常算撞。
