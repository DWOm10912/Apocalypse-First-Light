# 镜头演出框架 V1 方案（开局动画前置）

状态（2026-10-16）：
- 方案写好后，用户同意第 6 节全部按建议，先做 D1、D2、D3。
- 之后用户又决定**开局动画先不做**：开局演出要按出生地设计，以后再定。第 2.9–2.11 节、第 5 节和第 6 节第 1–5 条留作以后的设计参考，没有实现。
- **D1、D2、D3 已实现**，离线检查通过，**还没进游戏验证**。实现记录见第 8 节。

最初的用户要求见 `player_actions_v1_plan.md` 第 9.8 节：会挪镜头的演出都不带人物影子；问这些是不是同一个框架，因为以后要做开局动画。

一句话：现在没有统一框架。本方案提出一个客户端的 `CameraDirector`（镜头导演），把镜头、晃动、身体偏移和影子、输入锁定、界面隐藏、跳过收到一处，再在它上面做开局动画。

相关文档：

- `player_skeleton_v1_design.md` 第 5 节（镜头是权威、镜头净空）、第 6 节（ActionState，还没做）；
- `player_actions_v1_plan.md` 第 9 节（S1 的走近、身体眼睛镜头、影子跟随）；
- `../gameplay/survivor_start_template_pool.md`（开局模板里的“开局演出 ID”）；
- `../gameplay/survivor_death_succession_v0.md`（继任流程，现在只发标题）。

## 1. 现状：每个系统各改各的

代码都在 `src/main/java/com/antaurora/apofirstlight/` 下。“时钟”指淡入淡出用什么计时：游戏刻会随单人暂停停住，系统时间不会。

### 1.1 改镜头位置 / 朝向的

| 系统 | 改什么 | 在哪改 | 进出过渡 | 时钟 | 输入锁定 | 界面 | 同步 | 和第一人称身体、骨架的关系 |
|---|---|---|---|---|---|---|---|---|
| 枪械检修台视角 `MaintenanceModeClientState` + `mixin/client/MaintenanceCameraMixin` | 镜头位置、朝向（固定点，看台面）、FOV（`MaintenanceGameRendererMixin#getFov`） | `Camera.setup` TAIL，按 blend 插值 | 进 0.22 s、出 0.16 s，smoothstep；朝向用 `rotLerp` + `lerp` | 系统时间（`System.nanoTime`） | 移动、跳、蹲全锁（`MovementInputUpdateEvent`）；鼠标给界面用 | 整个 HUD 取消（`RenderGuiEvent.Pre`）、手、方块轮廓、晃动（`bobView` / `bobHurt`）都关；本地玩家不画（`RenderPlayerEvent.Pre` 取消） | 纯客户端，跟着检修界面开关 | 镜头离眼远，第一人称身体按 `FirstPersonBody` 的距离判定不画 |
| 发电机拉绳 QTE `client/PortableGeneratorPull` + `mixin/client/PortableGeneratorCameraMixin` | 镜头弯到拉手上方（`BEND_OUT 0.55`、`BEND_UP 0.62`、最多挪 `BEND_MAX 1.4`），看着拉手；拉的一下往上顶 0.035 | `Camera.setup` TAIL | 进 10 刻、出 8 刻，smoothstep | 游戏刻 + partial | 移动全锁；蹲键改成“松手”；点击只认使用键；鼠标转不动（每帧 `RenderTickEvent` START 把玩家朝向写回锁定值） | 时间条画在准星层后面；手、方块轮廓关；其余 HUD 照常 | 握住、拉、着车由服务端方块实体同步 | 镜头离眼远，旧第一人称身体不画；骨架开时被下一行盖过 |
| 骨架走近的身体眼睛 `client/skeleton/SkeletonActions#camera` / `cameraRotation`（同一个 mixin 里、拉绳之后） | 镜头放到走近后身体的眼睛上；朝向 = 身体朝向 + 动作俯角，按动作权重渐入 | `Camera.setup` TAIL | 跟动作权重 | 跟动作（游戏刻） | 跟各自的动作系统 | 不改 | 不发包，读已同步的状态 | `FirstPersonBody.shown` 认这个镜头，照画身体；影子由 `EntityRenderDispatcherShadowMixin` 跟着身体挪 |

### 1.2 改玩家真实朝向的（不是只改镜头）

| 系统 | 改什么 | 进出 | 时钟 | 输入锁定 | 界面 | 同步 |
|---|---|---|---|---|---|---|
| 油桶倒油 `client/JerryCanPourView` | 把玩家朝向转到“油口出现在壶嘴左下方”，每帧写 `setYRot` / `setXRot` | 指数逼近，`TURN_RATE 5`/s，没有退出过渡 | 系统时间的帧间隔 | 强制蹲（`shiftKeyDown = true`，蹲姿照常发给服务端）、移动全锁；鼠标转不动 | 不改 | 倒油状态在物品 NBT |
| 加油枪插枪 `client/NozzleFillView` | 同上，转向油口 | 同上 | 同上 | 同上 | 手、方块轮廓关 | 插在哪由加油机方块实体同步 |

这两个改的是玩家真实朝向：服务端看到的视线、准星选中都跟着变。这是玩法需要，不是单纯演出。

### 1.3 只叠加角度的（晃动）

都在 `ViewportEvent.ComputeCameraAngles` 里往 pitch / yaw / roll 上加。

| 来源 | 内容 | 条件 |
|---|---|---|
| `PortableGeneratorPull#cameraShake` | 拉的一下、回弹、着车抖约 1 s，2–4° | 第一人称 |
| `CrowbarFirstPerson#camera` | 撬棍砸中后 3 刻的顿挫（pitch 0.8°、roll 0.25°） | 第一人称、砸玻璃动作中 |
| `JerryCanFirstPerson#camera` | 油桶视图模型的 `camera` 骨骼 | 第一人称、手里是油桶 |
| `weapon/client/NativeCameraBoneConsumer` | 枪动画的 `camera` 骨骼（换弹、检视、拔收枪） | 第一人称、没开界面、没用望远镜 |
| `temperature/ClientShiver` | 冷到第二阶段起的发抖，18 Hz | 第一人称、没用望远镜 |

### 1.4 只改 FOV 的

| 来源 | 内容 |
|---|---|
| `weapon/client/NativeGunAds#fov` | ADS 缩放（`ComputeFov`） |
| `weight/ClientWeightPenalties#fov` | 抵消 AFL 自己的减速带来的 FOV 收缩（`ComputeFovModifierEvent`） |
| `MaintenanceGameRendererMixin#getFov` | 检修台的固定 FOV |
| `client/ViewFov` | 只记录世界和手的 FOV，给“手上的点换到世界里”用，不改 |

### 1.5 看出来的问题

1. **两个 mixin 在同一个注入点**（`Camera.setup` TAIL）。谁先谁后靠 mixin 的应用顺序，没写在任何地方。今天两者不会同时生效，以后加开局动画就会撞。
2. **三种时钟**：系统时间、游戏刻、帧间隔。暂停和卡顿时手感不一样。
3. **四份输入锁定**，各写一遍 `MovementInputUpdateEvent`。鼠标锁靠每帧把玩家朝向写回去，副作用是服务端看到的朝向也被锁住。
4. **三种隐藏界面的写法**：取消整个 HUD、只关手和轮廓、各 HUD 自己查 `FieldAttachmentViewState`。
5. **身体偏移只有骨架走近一家**，影子 mixin 直接问 `SkeletonActions`。以后开局动画把身体放到别处，还得再接一次。
6. **镜头离眼时本地玩家整个不画**：原版只在 `Camera.isDetached()` 时画自己。拉绳 QTE 弯腰时看不到自己的身体就是这个原因。开局动画要从外面拍自己，必须处理。
7. **没有“跳过”、没有黑边、没有淡入黑场、没有服务端知道“正在演出”**。

## 2. 统一框架：`CameraDirector`

新包 `com.antaurora.apofirstlight.client.camera`（客户端），服务端部分放 `com.antaurora.apofirstlight.cutscene`。全部是 AFL 代码，不碰 Vextris。

### 2.1 一帧的顺序

1. `RenderTickEvent` START：推进时钟，算出这一帧所有请求的权重；处理鼠标锁。
2. `Camera.setup` TAIL（**只留一个 mixin**，`CameraDirectorMixin`）：从原版镜头出发，按优先级叠加各镜头源，得出位置、yaw、pitch，决定这一帧是否“分离”。
3. `ComputeCameraAngles`（一个订阅者）：叠加镜头源的 roll 和晃动层。
4. `ComputeFov`：先算玩法 FOV（ADS、负重抵消照旧），再按镜头源的 FOV 覆盖插值。
5. 画世界：身体偏移给骨架、影子 mixin、`FirstPersonBody`。
6. 画界面：按界面模式过滤 HUD；黑边、黑场、跳过提示在最后画，HUD 隐藏时也画。

### 2.2 镜头源（Shot）

每个镜头源每帧给出一个镜头姿势：位置、yaw、pitch、roll、FOV（NaN 表示不改）、是否分离。

| 镜头源 | 说明 | 现有对应 |
|---|---|---|
| `Vanilla` | 原版眼睛 | 栈底，永远在 |
| `Fixed` | 世界里的固定点，看向固定点或固定朝向 | 检修台 |
| `FollowAnchor` | 相对锚点（方块、实体、眼睛）的偏移，看向锚点；锚点每帧更新 | 拉绳 QTE 的弯腰视角 |
| `SkeletonEye` | 骨架摆好后的眼睛，朝向 = 身体朝向 + 动作俯角 | 走近动作 |
| `Track` | 关键帧轨道：相对一个锚点坐标系（位置 + yaw），按时间插值位置、朝向、roll、FOV | 开局动画（新） |

`Track` 的格式：

- 一个文件一条轨道：`assets/apocalypse_firstlight/cutscenes/<id>.json`。
- 键：时间（刻）、位置（锚点坐标系，格）、yaw、pitch、roll、FOV、缓动。
- 位置用 Catmull-Rom 曲线（经过每个键），角度逐键插值；可以“看向锚点上的一点”代替 yaw / pitch。
- 生成方式和动作片段一样，先用脚本生成（`tools/author-cutscenes.mjs`，带 `--check`），另给一个开发命令在游戏里记录键位（见第 6 节第 7 条）。

### 2.3 优先级和混合

每个请求有：所属系统、优先级、进出时长和缓动曲线（默认 smoothstep）、时钟。

导演从栈底（原版眼睛）开始，按优先级从低到高，用每个请求的权重 w 把当前姿势往它的姿势插值：

- 位置：线性插值；
- yaw / pitch：和现在一样用 `rotLerp` / `lerp`（保持已通过的手感，见第 4 节风险 2）；
- 只有带 roll 的轨道用四元数插值；
- FOV：线性插值。

建议的优先级（数字越大越在上）：

| 优先级 | 请求 |
|---|---|
| 0 | 原版眼睛 |
| 100 | 环绕镜头（2026-10-18，`client/camera/OrbitCamera`，替代原版 F5；见 `orbit_camera_v1.md`） |
| 200 | 机器 QTE（拉绳弯腰） |
| 300 | 骨架身体眼睛（走近）；坐车乘员（`SeatedRiders`）；身体动作的低头（2026-10-20，`client/skeleton/FirstPersonActionView`，id `skeleton_action_look`，见 `docs/rendering/first_person_body_v1.md` 3.5.2） |
| 400 | 界面视角（检修台） |
| 500 | 过场（开局动画，以后的剧情镜头） |
| 600 | 死亡镜头（布娃娃 V1 R-C，`client/survivor/DeathCamera`，2026-10-16 写好，未实机确认；见 `ragdoll_v1_plan.md` 第 4 节） |

例：拉绳时 QTE 弯腰（200）和身体眼睛（300）都在，结果和今天一样是身体眼睛盖住弯腰。

**分离**：结果离眼睛超过 `FirstPersonBody` 的距离（水平 0.3、竖直 0.6），并且最上面那个请求要求“拍到自己”时，`Camera.isDetached()` 返回真。原版就会把本地玩家整个画出来（含头），不画第一人称手。回到眼睛前，在权重 ≥ 0.98 且离眼足够近时切回第一人称身体，避免头突然消失时被看到。

### 2.4 时钟

两种：

- **游戏刻 + partial**：随单人暂停停住，默认；
- **系统时间**：给开着界面的视角用（检修台）。

迁移时每个系统先保留它原来的时钟，手感不变。

### 2.5 晃动层

- 晃动源只登记一个函数：输入当前时间，输出 pitch / yaw / roll（以后可以加位置）。
- 导演在 `ComputeCameraAngles` 里把所有源加起来。数值和条件原样搬过来，不改幅度。
- 统一的条件：第一人称、有过场时默认静音（过场可以自己带晃动轨道）、用望远镜时静音。
- 加一个总开关：给以后的“减弱镜头晃动”无障碍选项留位置。

### 2.6 身体演出偏移（接管影子接口）

- `BodyPresentation`：每个玩家实体一个“画的位置相对实体位置的偏移”和朝向，只影响画，不影响碰撞盒、准星选中和服务端。
- 现在的来源只有骨架走近（`SkeletonActions.stepped`）；以后加过场把身体放到别处。
- 影子 mixin 改成问导演：`CameraDirector.bodyOffset(entity)`，不再直接问 `SkeletonActions`。
- 同一个偏移还要给：名字牌、身上着火的火焰、拴绳的端点。今天走近只有约 0.7 格，这三样还没跟，先记成已知问题。

### 2.7 输入锁定

一个请求带一组标志，导演取并集，只有一个 `MovementInputUpdateEvent` 订阅者：

| 标志 | 作用 |
|---|---|
| `MOVE` | 前后左右、跳清零 |
| `SNEAK_FORCE_ON` / `SNEAK_FORCE_OFF` | 强制蹲（倒油、插枪）/ 不让蹲（拉绳，蹲键另作“松手”） |
| `LOOK` | 鼠标不转视角 |
| `ATTACK_USE` | 吞掉攻击和使用；可以放行使用键（拉绳靠它拉） |
| `HOTBAR` | 不切快捷栏 |

鼠标锁的做法要改：

- 只改镜头的请求（检修台、拉绳、过场）：在鼠标转动玩家那一步把转动量丢掉。玩家真实朝向不动，服务端也不受影响。
- 要改玩家真实朝向的（倒油、插枪）：做成单独的“朝向驱动”通道，保留今天写 `setYRot` 的语义和 `TURN_RATE`，只是统一到一处。

### 2.8 界面隐藏、黑边、黑场

每个请求带一个界面模式，导演取最严格的：

| 模式 | 效果 | 用在 |
|---|---|---|
| `NORMAL` | 不改 | 走近动作 |
| `NO_HANDS` | 关第一人称手和方块轮廓；其余 HUD、准星、QTE 自己的提示照画（和今天一样） | 拉绳、插枪 |
| `NO_HUD` | 整个界面关，含聊天（和今天的检修台一样） | 检修台 |
| `CINEMA` | 同 `NO_HUD`，再画黑边 | 过场 |

- 现在各 HUD 自己查 `FieldAttachmentViewState` 的地方，改成问导演一次。
- 黑边：上下两条，比例 2.39:1，进出 0.6 s 缓动，现代简洁风格，不画花边。
- 黑场：整屏淡入 / 淡出黑色，过场开头等地形、结尾交还时用。

### 2.9 跳过

- 只有过场能跳；QTE 和界面视角不算演出，用各自的退出键。
- 按住跳过键约 1 s，屏幕右下角出一个小进度环；开头 1 s 内不显示，防止误触。
- 跳过 = 直接跳到轨道结尾的状态，黑场淡出，交还镜头，并告诉服务端“已结束（跳过）”。
- 跳过不能改变任何已经决定的东西（模板池规则：跳过不重新抽取）。

### 2.10 多人同步

原则：**镜头只在本地，事实在服务端。**

| 东西 | 谁决定 | 同步方式 |
|---|---|---|
| 演出开始 | 服务端 | 新 S2C 包 `CutscenePlay`：轨道 ID、锚点位置和 yaw、随机种子、开始时的服务端刻 |
| 演出结束 / 跳过 | 客户端报告，服务端确认 | 新 C2S 包 `CutsceneDone`：是否跳过 |
| 演出中玩家状态 | 服务端 | 服务端记一个“演出中”标志，带超时（轨道长度 + 10 s），下线即清 |
| 别的玩家看到什么 | — | V1：只看到这个玩家站在原地（没有身体演出）。身体演出要等 ActionState（骨架设计第 6 节）再做 |
| 已有的玩法视角（拉绳、插枪、倒油） | 不变 | 照旧由方块实体 / 物品 NBT 同步 |

两个新包要把协议号 47 → 48。按多会话规则，先通知可能同时改协议号的会话再动。

### 2.11 开局动画怎么触发

- 服务端在幸存者“确定落地”之后触发：首位幸存者第一次进世界，和死亡继任重生接手。
- V0 现在在这个时刻发标题（`Survivors` 里的姓名标题）。有演出时，标题改成演出结尾由客户端显示，不再由服务端同时发，避免标题盖在黑场上。
- 客户端收到 `CutscenePlay` 后：
  1. 先黑场，等周围地形画好（最长 10 s，超时就照播）；
  2. 检查轨道每个键不在实心方块里，并且看得到锚点。不行就换成一条短的备用轨道（从身后 3 格推到眼睛）；
  3. 播放；
  4. 结尾回到眼睛，交还镜头，显示姓名标题，发 `CutsceneDone`。
- 重进世界不重播（`SurvivorRecord` 记一个“开局演出已播”）。
- 开发命令：`/aflc cutscene play <id>`（只在本地播，不发包），`/aflc cutscene stop`。

## 3. 迁移顺序

每一步单独实机检查。已经 PASS 的系统，数值和时钟原样搬过来。

| 步 | 内容 | 风险 | 怎么证明没变 |
|---|---|---|---|
| D1 | 导演核心：请求栈、混合、两种时钟、输入锁、界面模式、晃动层、身体偏移接口。**没有任何系统接进来** | 低 | 离线检查：混合、优先级、进出曲线 |
| D2 | 晃动源迁移：拉绳、撬棍、油桶、枪、发抖 | 低（纯叠加） | 离线比对：同一组输入，迁移前后每个源的角度逐帧相同 |
| D3 | 身体偏移 + 影子迁移：`SkeletonActions` 的走近改成登记到导演；影子 mixin 改问导演 | 低 | `ActionCheck` 照旧通过；实机看拉绳时影子 |
| D4 | 拉绳 QTE + 身体眼睛：同一个 mixin 里的两段拆成两个请求（200、300），输入锁和界面模式接导演 | 中：用户 PASS 的拉绳手感 | 离线比对镜头位置 / 朝向逐帧相同；用户实机复查 |
| D5 | 检修台：固定镜头 + 系统时钟 + FOV + `NO_HUD` + 不画本地玩家 | 中：检修台的选中依赖镜头位置 | 同上；检修台的点选要实机复查 |
| D6 | 倒油、插枪：朝向驱动通道 + 强制蹲 | 中 | 同上；和 S2 油桶迁移一起做 |
| D7 | 开局动画 V1（第 5 节） | 中 | 离线：轨道连续、结尾等于眼睛；GameTest：服务端标志和超时；用户实机 |

建议先做 **D1 → D2 → D3 → D7**：开局动画只依赖核心、晃动和身体偏移。D4–D6 等下次碰到那个系统时顺手迁移（D6 正好跟动作 S2 的油桶一起）。见第 6 节第 6 条。

## 4. 风险

1. **mixin 顺序**：合成一个 `CameraDirectorMixin` 后，顺序由优先级决定，不再靠 mixin 应用顺序。迁移期间旧 mixin 和新 mixin 并存，要保证同一个系统不会被两边各改一次。
2. **插值方式改变手感**：现有系统都是 yaw `rotLerp` + pitch `lerp`。四元数插值在大俯角时路径不同，所以没有 roll 的请求继续用欧拉插值。
3. **时钟改变手感**：检修台用系统时间，改成游戏刻会在卡顿时变慢。迁移时每个系统保留原时钟。
4. **分离镜头的副作用**：`isDetached()` 为真时原版不画第一人称手、会画自己；Oculus 的手部那一遍、声音监听位置（跟镜头走）、准星选中（跟玩家走，不跟镜头）都要实机看。
5. **第一人称身体切换**：从外面拍回到眼睛时，头从“画”到“不画”会跳一下。按第 2.3 节在足够近时切。
6. **地形没加载**：第一次进世界时镜头先在空中，远处区块可能没画好。黑场等地形 + 轨道限制在出生点约 24 格内。
7. **光影**：镜头离开玩家较远时，Sundial 体积云的残影（已知问题）可能更明显；阴影那一遍用镜头位置，应该没事，要实机看。
8. **协议号**：47 → 48 要先通知相关会话。
9. **已通过的东西**：拉绳（user PASS）、检修台、倒油、插枪都在迁移范围里。每一步都要离线逐帧比对，再请用户复查，不能只靠“代码看起来一样”。

## 5. 开局动画 V1 最小范围

做：

- 一条通用开局轨道 `afl:opening/generic`，约 8 s：
  - 从出生点外约 12 格、高约 6 格开始，看着出生点；
  - 慢慢推近，绕到人物背后肩上方；
  - 最后推进眼睛，FOV 从 60 回到玩家设置；
  - 结尾和第一人称完全重合，交还时看不出跳动。
- 人物站在出生点，骨架照常画待机姿势（看得到自己），不做身体演出。
- `CINEMA` 界面模式：HUD 和聊天关、黑边、开头和结尾黑场。
- 按住跳过。
- 服务端触发 + 两个新包 + “演出中”标志 + 超时 + 不重播。
- 首位幸存者和死亡继任共用这一条（模板池做好后，按模板换轨道）。
- 开发命令播放 / 停止。
- 离线检查 `CameraDirectorCheck`；GameTest 测服务端的标志、超时、下线。

不做：

- 身体演出（醒来、起身、看手表这类），要等身体片段和 ActionState；
- 按模板的专属轨道（溪谷老宅前廊等），等模板池；
- 别的玩家看到演出；
- 字幕、旁白、音乐（声音以后单独一轮）；
- 死亡镜头。

## 6. 需要你决定的

1. **开局 V1 演什么**：
   - A（建议）：只有镜头，从远处推到眼睛，结尾出姓名；
   - B：人物醒来、起身（要身体片段，别人看不到，工作量大很多）；
   - C：只黑场加文字卡片。
2. **第一次看能不能跳过**：建议一直能跳（按住 1 s）。
3. **演出中是否保护玩家**：建议服务端在演出期间让玩家不受伤、怪物不把他当目标，带超时。也可以完全不保护。
4. **黑边**：建议 2.39:1 细黑边加淡入淡出；或者不要黑边，只隐藏 HUD。
5. **死亡继任要不要也播**：建议播同一条，但短一些（约 4 s）；或者只在首次开局播。
6. **迁移范围**：建议先做 D1、D2、D3、D7，D4–D6 等以后碰到时再迁；或者开局动画前把全部系统迁完（更统一，但会动到你已经 PASS 的拉绳和检修台）。
7. **轨道怎么做**：建议脚本生成 + 一个游戏内记录命令（你在旁观模式飞到位置按键记一个键）；或者只用脚本。

## 7. 做完后要改的文档（开局动画那部分以后再改）

- 本文：加实现记录；
- `player_actions_v1_plan.md` 第 9 节：走近的影子改由导演接管；
- `player_skeleton_v1_design.md` 第 5 节：镜头权威的例外（演出镜头）；
- `../gameplay/survivor_start_template_pool.md`、`../gameplay/survivor_death_succession_v0.md`：开局演出 V1 的触发和标题时机；
- `../machines/portable_diesel_generator_v1.md`、`../models/fuel_dispenser_v1.md`：迁移后的镜头写法（数值不变）。

## 8. 实现记录：D1–D3（2026-10-16）

用户：「7 个问题都按方案里的建议，先做 D1、D2、D3，再做开局动画 V1」，随后「目前先不搞开局动画，这个是按照出生地去做的，后面我们再设计」。所以只做了 D1–D3。

### 8.1 文件

| 文件 | 内容 |
|---|---|
| `client/camera/CameraDirector.java` | 导演核心：请求栈（按优先级排，同 id 替换）、两种时钟（`Clock.GAME` / `REAL`）、合成（`place` → `compose`）、roll 和 FOV 缓入、晃动层、身体偏移、输入锁、界面模式；下线时清空 |
| `client/camera/CameraPose.java` | 镜头姿势和混合：位置、roll 线性，yaw `Mth.rotLerp`，pitch `Mth.lerp`（和现有视角一样） |
| `client/camera/CameraFade.java` | 请求的权重：进出时长、smoothstep，从当前权重开始淡出（同检修台） |
| `client/camera/CameraShake.java` | 晃动层的累加器和 `Source` 接口 |
| `client/camera/CameraSources.java` | 登记表：5 个晃动源、1 个身体偏移源 |
| `mixin/client/CameraDirectorMixin.java` | `Camera#setup` TAIL，优先级 1100（在两个旧镜头 mixin 之后执行）；没有打开的请求时什么都不做 |
| `mixin/client/MouseHandlerLookLockMixin.java` | `MouseHandler#turnPlayer` HEAD：锁视角时把这一帧攒下的鼠标移动清零，玩家真实朝向不动，解锁时也不会一下子转过去 |
| `apocalypse_firstlight.mixins.json` | 加上面两个 mixin |
| `build.gradle` | 新任务 `cameraCheck` |
| `src/test/.../client/camera/CameraDirectorCheck.java` | 离线检查（第 8.4 节） |

改动的现有文件：
- D2：`PortableGeneratorPull`、`CrowbarFirstPerson`、`JerryCanFirstPerson`、`weapon/client/NativeCameraBoneConsumer`、`temperature/ClientShiver` 去掉各自的 `ComputeCameraAngles` 订阅，改成 `cameraShake(Camera, partial, CameraShake)`。撬棍的公式拆成 `CrowbarFirstPerson#kick`，发抖的拆成 `ClientShiver#angles`，便于离线比对。
- D3：`SkeletonActions#shadowOffset` 改名 `bodyOffset`；`EntityRenderDispatcherShadowMixin` 改问 `CameraDirector#bodyOffset`。

### 8.2 现在的行为

- **镜头源**：框架有了，但还没有系统用它放镜头（D4–D6 以后迁移）。检修台、拉绳弯腰、身体眼睛、倒油、插枪都还是原来的代码。
- **晃动层**：5 个来源每帧都调用（枪的来源顺便准备手部那一遍要共用的动画采样），加起来乘 `CameraDirector.shakeScale`（现在固定 1，给以后的“减弱晃动”选项留位置），在 `ComputeCameraAngles` 里一次加到视角上。各来源的条件、数值不变。
- **身体偏移**：`CameraDirector#bodyOffset` 把所有来源加起来；现在只有骨架走近一个来源，影子照旧跟身体走。名字牌、火焰、拴绳还没跟（第 2.6 节）。
- **输入锁、界面模式**：代码在，没有请求用它们，所以现在不生效。界面模式只做了 `NORMAL` / `NO_HANDS` / `NO_HUD`；`CINEMA`、黑边、黑场、跳过、分离镜头都跟开局动画一起推迟。
- **FOV**：请求带 FOV 时在 `ComputeFov` 的 LOW 优先级里缓入（`ViewFov` 在 LOWEST 记录最终值）；现在没有请求带 FOV。
- 没有新网络包，协议号仍是 47。

- **2026-10-20 补充**：
  - 新的镜头源：第一人称身体动作的低头（`FirstPersonActionView`）。动作带 `first_person_pitch` 时，0.45 s 淡入把俯仰压到至少这个角度，停止时 0.45 s 淡出；只改镜头，不改玩家朝向。
  - 同 id 的请求在淡出时又被打开，新请求从旧请求当时的权重接着淡入（`CameraFade#open(now, weight)`，用 smoothstep 的反函数算起点），以前是从 0 重新淡入，画面会跳一下。对所有请求生效：环绕镜头、坐车、死亡镜头在淡出中重新打开时也不再跳。编译通过，没进游戏看过。

### 8.3 和方案不同的地方

1. 晃动层没有加统一的“用望远镜时静音”：拉绳、撬棍、油桶原来没有这个条件，统一加会改变已有行为。每个来源保留自己的条件。
2. 来源用一张固定的登记表（`CameraSources`），不在运行时注册：顺序确定，也不依赖类加载时机。
3. 拉绳弯腰视角以后迁移（D4）要注意：中途在淡入没完成时松手，旧代码是 `min(淡入, 1 − 淡出)`，导演的 `CameraFade` 是从当时的权重淡出。离线检查量到两者最大差 0.476（第 4 刻松手）。迁移时要么给 `CameraFade` 加旧的写法，要么请用户确认新手感。检修台的写法和 `CameraFade` 完全一样。

### 8.4 检查结果（2026-10-16）

- `./gradlew cameraCheck`：31 项全部通过。
  - 淡入淡出和检修台逐帧相同（3 个退出时刻，误差 0）；和拉绳在完整淡入后松手时相同。中途松手的差别见第 8.3 节第 3 条，只打印，不算失败。
  - 混合：yaw 走近路（170° → −170° 的中点是 180°），权重 0 / 1，FOV 按顺序缓入。
  - 栈：低优先级在前，高优先级在满权重时覆盖，同 id 替换，镜头源能拿到下面已经合成的姿势。
  - 输入锁取并集，开始淡出就解锁；界面模式取最严格的，淡出时保持。
  - 晃动：撬棍 201 帧、发抖 3 阵逐帧和旧公式按位相同；单个来源经晃动层加到视角上和旧代码按位相同（2000 个随机值）；几个来源同时生效时，最大差 < 1e-4°，只是浮点舍入。
- `./gradlew skeletonCheck`：动作部分全部通过；仍是原来 6 个暂停中的枪械检查失败，和改动前一样。
- 编译通过；两个新 mixin 在 refmap 里解析到 `Camera#m_90575_`（setup）和 `MouseHandler#m_91523_`（turnPlayer）。
- 没有跑 GameTest：只改了客户端代码，服务端不受影响。
- **没进游戏验证**。要请用户看的：
  1. 发电机拉绳的摇晃（拉一下、回弹、着车抖）和以前一样；
  2. 撬棍砸玻璃命中时的顿挫；
  3. 油桶倒油时镜头随动作的起伏；
  4. 枪换弹 / 检视时的镜头骨骼；
  5. 冷到第二阶段的发抖；
  6. 拉绳走近时影子跟着身体（和第 9.8 节修好的效果一样）。

### 8.5 以后

- D4–D6 等下次碰到那个系统时迁移（用户决定第 6 条）。
- 2026-10-18：环绕镜头（`orbit_camera_v1.md`）作为优先级 100 的镜头接入；`MouseHandlerLookLockMixin` 同时把鼠标移动交给它转镜头。
- 开局动画：按出生地重新设计后再做，第 2.9–2.11 节和第 5 节到时再改。
