# 落地与摔倒 V1

**状态：已实现，用户 2026-10-20 实机 PASS（摔断两条腿后看状态面板，回复「PASS」；D 分支 `feature/survival-health-v1a`，已提交，未合并进 master）。** 用户 2026-10-20 批准概念动画：「概念通过，第 3 节按推荐，去 Blockbench 正式做并接上落地判定」。两个动作由作者脚本按概念写出，在 Blockbench 里用 AFL Rig 插件导入核对过（长度、骨头、`first_person_view` 都在）；落地分档、摔倒判定、撑地手的手臂骨折和摔倒声都已接上（第 6 节）。同日按用户的问题（「恢复中不会受到骨折的影响吗」）把状态面板的骨折说明改成"摔倒时撑地的那只手臂也可能骨折；骨折期间生命值无法恢复到满"（`hud.apocalypse_firstlight.status.fracture.detail`，中英文）：骨折本来就压低自然恢复的上限（腿未固定 85、固定 95，`HealthCap`），到上限前"恢复中"照常显示，坐着算休息恢复更快。用户看过后回复「PASS」（2026-10-20）。

接在 [fracture_splint_v1_plan.md](fracture_splint_v1_plan.md) 后面：骨折现在只由摔落造成，一次摔落里腿和手臂各自独立掷骰子，会出现"脚着地，手却断了"。

## 1. 用户的要求（2026-10-20）

- 「原版的直直落下有点假，正常来说，如果没站稳是会摔跤的」
- 「如果是脚着地，根据高度给骨折，如果没站稳滑倒了，就给手臂也增加骨折」
- 「不能说玩家脚着地了，手却骨折了」
- 骨裂音效：一次性播放；「速度有点慢，你可以看看加速」。摔倒着地 4 份「听起来都差不多」，挑一个或做变体都行。
- 「顺便一起做落地动画」

## 2. 落地分三档（推荐）

| 档 | 什么时候 | 动作 | 骨折 |
|---|---|---|---|
| 正常 | 摔落不掉血（3 格以内） | 不变 | 无 |
| 硬着陆 `land_hard` | 掉血，站稳了 | 0.6 s：屈膝屈髋往下一沉，两臂向外张开保持平衡，站起；镜头跟着眼睛下沉 | 只判腿（现在的腿概率不变） |
| 摔倒 `fall_over` | 没站稳 | 2.8 s：膝盖撑不住，向前扑，双手先伸出去，**左手先着地**（右手的版本镜像），跪趴着缓一口气，一只脚踩到前面，双手撑膝盖，站起；期间不能动、不能用物品 | 腿照常判定；**手臂只在摔倒时判定**，断的是先着地的那只手 |

## 3. 什么时候算没站稳（用户 2026-10-20 按推荐定）

- 摔倒概率随扣掉的生命值上升：`clamp((扣血 - 10) / 50, 0, 0.8)`（已乘过原版摔落 ×5，和骨折的判定用同一个值）。
- 更容易摔倒：腿本来就有骨折（×1.5）、负重超过轻载（+0.15）、落地时横向速度快（跑着跳下去，+0.15）、落在方块边缘或斜面上（脚下支撑不全，+0.15）。
- 落地时按住潜行（有准备地落地）：×0.5。
- **这一下摔断了腿，就一定摔倒**（刚断的腿站不住），所以不会出现摔断腿还站得稳稳的。
- 落进水里或摔落伤害为 0 的方块上，照旧什么都不发生；这一下摔死的也不播动作。

数值在 `health_v1.json` 的 `fracture.fall_over`（`HealthConfig.FallOver`）：`chance` [10, 50, 0.8]、`broken_leg` 1.5、`load` 0.15、`speed_from` 0.25（格/tick，约 5 m/s，疾跑跳下去会超过）、`speed` 0.15、`edge` 0.15、`sneak` 0.5；加完后封顶 1。

| 摔落（扣血） | 基础 | 疾跑跳下 | 疾跑跳下 + 负重 + 边缘 | 按住潜行 |
|---|---|---|---|---|
| 6 格（15 HP） | 10% | 25% | 55% | 5% |
| 8 格（25 HP） | 30% | 45% | 75% | 15% |
| 10 格（35 HP） | 50% | 65% | 95% | 25% |
| 12 格以上（45 HP 起） | 70%–80% | 85%–95% | 100% | 35%–40% |

（"腿本来有骨折"再把基础乘 1.5；这一下摔断了腿必摔倒，腿骨折概率见 [fracture_splint_v1_plan.md](fracture_splint_v1_plan.md) 第 3 节。）

## 4. 概念动画

在 Blockbench 里用 AFL Rig 核心解算：身体按关键姿势，腿用两骨 IK 落在地面，手用核心的手臂 IK 撑在地上，再烘焙成 FK；第一人称镜头放在摆好姿势的眼睛上，视线角度按关键帧变化（游戏里由动作的 `first_person_eye` 和导演镜头带着走）。文件在 `design/health/fall_landing_v1/`：

| 文件 | 内容 |
|---|---|
| `fall_over_concept_tp.gif` / `_fp.gif` / `_keys.png` | 摔倒：第三人称（侧面 + 左前方）、第一人称、关键帧拼图 |
| `land_hard_concept_tp.gif` / `_fp.gif` / `_keys.png` | 硬着陆：同上 |
| `src/concept_fall.cjs` | 关键姿势表（时间、身体、腿和手的目标点、视线），输出 `concept_fall.clip.json` |
| `src/make_gif.cjs` | 帧 → GIF（加中文分段字幕）和拼图 |
| `sound_preview/` | 骨裂声不同加速的试听：×1.0（原速）、×1.6、×2.0（游戏里用的）、×2.5 |

已知的取舍：Minecraft 的躯干和手臂一样长，跪趴时头伸在手的前面，第一人称看不到撑地的手；所以扑出去的那一段（0.2–0.45 s）让双手先向前伸，第一人称在这一段能看到手，趴稳后看地面。

## 5. 音效（用户 2026-10-20 的 Firefly 录音）

- `tools/build-fracture-sounds-v1.cjs`，输出 `assets/apocalypse_firstlight/sounds/health/`，单声道 Vorbis。
- **骨裂** `fracture_crack`：原录音是 0.12–0.82 s 一串断续的咔嚓声。截掉前后空白，用 rubberband 压缩到 ×2.0（音高不变，保住瞬态），约 0.4 s。在 `Fractures#breakLimb` 播放（新骨折和再次骨折都播，包括 `/afl health fracture <部位>`），只播一次，音高 ±5% 随机，传到 12 格，和最重的落地噪音同一个半径（`MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS`）。字幕"骨头：断裂"。**已接上，编译通过，没进游戏。**
- **摔倒着地** `body_fall`：四份听起来差不多，正适合做四个随机变体，每份截到 0.78–0.92 s（去掉尾巴）。字幕"玩家：摔倒"。在摔倒时手着地那一刻（0.45 s）由服务端播放（`FallLanding#handHits`），传 8 格；原版的落地声照旧。**已接上，编译通过，没进游戏。**

## 6. 实现（2026-10-20）

**服务端** `health/FallLanding`（`Fractures#onDamage` 在摔落扣血后调用）：

1. 摔死、骑乘中、在水里：什么都不做。
2. 先判腿：随机一侧，`leg_fall` 曲线（`Fractures#roll`，断了会播骨裂声）。
3. 再算第 3 节的摔倒概率，断了腿就是 1。
   - "腿本来有骨折"看的是这一下之前。
   - 负重看 `PlayerWeightRuntime.state` 的 Tier 是否 LIGHT。
   - 横向速度：玩家上一 tick 结束时的脚和现在的水平距离（服务端靠移动包移动玩家，没有可靠的速度）。
   - 边缘：脚下包围盒四个角往里 0.05 格、往下 0.1 格有没有碰撞箱。
   - 潜行：`isShiftKeyDown`。
4. 摔倒：随机哪只手先着地（右手就镜像），停下正在用的物品和疾跑；第 `HAND_HITS` = 9 tick（0.45 s）手着地：播 `body_fall`（传 8 格，和重落地噪音同半径），按 `arm_fall` 判撑地那只手的手臂；`FALL_TICKS` = 56 tick 内拒绝用物品、右键方块和实体、左键方块、攻击。
5. 没摔倒（扣了血）：硬着陆。
6. 发 `HealthPackets.Landing(实体 id, 档, 镜像)` 给本人和周围的玩家（包追加在健康包的最后，协议号合并时随 59→60 一起变）。

**客户端** `client/skeleton/FallLandingClient`：

- 记下每个玩家的落地（动作、开始时间、镜像），`SkeletonActions#live` 里排在调试播放之后、枪之前播放，长度过了就停（按动作的 `blend_out` 淡出）。
- 自己摔倒期间：移动输入清零（不能走、跳、潜行），取消攻击和使用键，不能疾跑；锁住落地时的朝向；第一人称时每帧（`RenderTickEvent` START，鼠标转完之后）把俯仰设成动作的 `first_person_view`，开头 0.1 s 从落地时的俯仰过渡进去，结尾曲线回到 0°（平视）。
- 镜头放在摆好姿势的眼睛上（两个动作都有 `first_person_eye`，和夹板动作一样走 `SkeletonActions#solved`）；过渡靠动作自己的 `blend_in` / `blend_out`（硬着陆 0.04 / 0.15 s，摔倒 0.06 / 0.3 s），没有另接导演镜头。
- 硬着陆不锁操作，第一人称保留手上的东西（`first_person: view_model`，只动腿），镜头跟着眼睛沉一下。

**动作**（`tools/medical-actions-v1a/author_fall_v1.cjs --write`，按批准的概念的关键姿势，脚用两骨 IK、手用核心 IK 解出后烘焙成 FK，IK 偏差 0–0.15 px）：

| 文件 | 内容 |
|---|---|
| `assets/apocalypse_firstlight/animations/player/fall.animation.json` | `fall.land_hard`（0.6 s）、`fall.fall_over`（2.8 s）；单独一份，免得夹板的作者脚本重写 `body.animation.json` 时冲掉 |
| `player_actions/body/land_hard.json` | 遮罩 `legs`，优先级 60，第一人称 `view_model`，`first_person_eye` |
| `player_actions/body/fall_over.json` | 遮罩 `full`，优先级 70，第一人称 `body`，`first_person_eye`、`hold_body`、`first_person_view`（俯仰 5° → 0.45 s 60° → 1.2 s 80° → 2.8 s 0°），`events` 里记着 0.45 s 的摔倒声（由服务端播） |

新字段 `first_person_view`：`PlayerAction#viewAt`（见 [player_actions_v1_plan.md](../dev/player_actions_v1_plan.md)）；AFL Rig 插件导入导出都保留，预览时视线跟着它走。

**其他文件**：`health/HealthConfig`（`FallOver`）、`health/HealthPackets`（`Landing`）、`health/Fractures`（`onDamage` 改调 `FallLanding.land`）、`client/skeleton/SkeletonActions`（来源）、`skeleton/action/PlayerAction`、`tools/blockbench/afl_rig.js`、`sounds.json`（`body_fall` 四个变体，传 8 格）。

## 7. 要用户实机看的

1. 摔倒概率（第 3 节的表）合不合适，疾跑跳下、边缘、负重加得够不够。
2. 两个动作在游戏里的样子，第三人称和别的玩家看到的；左右手镜像。
3. 第一人称：摔倒时视线低下去看地、能不能看到伸出去的手、站起来回到平视；硬着陆镜头沉一下。
4. 摔倒期间不能动、不能用东西，结束后能马上恢复。
5. 手着地那一刻的摔倒声和（断了时）骨裂声。
6. 拿着枪时摔倒（枪的姿势和全身动作叠在一起的样子）。

已知的取舍：摔倒中按住潜行不会蹲下（输入清零）；第三人称不改视线，只锁朝向。
