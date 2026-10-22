# 医疗用品使用动作的关键帧脚本（健康 V1a）

`player_actions/item/bandage_wrap.json`、`alcohol_clean.json`、`splint_arm.json`、`splint_leg.json` 和片段 `animations/player/medical.animation.json` 的来源。用 AFL Rig 插件的核心（`tools/blockbench/afl_rig.js`）在 Node 里算几何：手掌对准道具点、手腕 / 前臂扭转、道具之间和道具与手臂的距离、第一人称画面里的位置。说明见 `docs/gameplay/survival_health_v1a.md` 第 7.3 节。

| 文件 | 作用 |
|---|---|
| `lib.cjs`、`alc_lib.cjs` | 加载插件核心、骨架、道具；酒精瓶和瓶盖的转向 |
| `author_bandage.cjs` | 绷带：关键帧表；不带参数只打印读数，`--write` 写出动作、片段和道具点 |
| `author_alcohol.cjs` | 医用酒精：同上（`HITS=1` 列出瓶子碰到手臂的时刻） |
| `author_splint.cjs` | 夹板（骨折与夹板 V1，`docs/gameplay/fracture_splint_v1_plan.md` 5.2）：`node author_splint.cjs arm [--write]`（腿换成 `leg`），写出动作、片段和道具 `player_props/splint.json`；`OV` 环境变量覆盖常量，`DBG=a,b` 打印那几秒的道具位置。**已被 V2 取代，不要再用 `--write`**（会覆盖 V2 的动作和片段） |
| `author_splint_v2.cjs` | 夹板 V2 和坐下键（`docs/gameplay/splint_animation_v2_plan.md`）：`node author_splint_v2.cjs [--write]`，按批准的概念的关键姿势（身体 FK、腿两骨 IK、手用核心的 IK 解出后烘焙成 FK）写出 `body.animation.json`（`body.sit_down` / `body.sit` / `body.stand_up`）、`medical.splint_leg` / `medical.splint_arm` 片段和五个动作 JSON；打印每个关键姿势的手 IK 偏差和坐姿眼睛高度 |
| `author_fall_v1.cjs` | 落地与摔倒 V1（`docs/gameplay/fall_landing_v1_plan.md`）：`node author_fall_v1.cjs [--write]`，按批准的概念（`design/health/fall_landing_v1/src/concept_fall.cjs`）的关键姿势写出 `fall.animation.json`（`fall.land_hard` 0.6 s、`fall.fall_over` 2.8 s）和 `body/land_hard`、`body/fall_over` 两个动作 JSON（含 `first_person_view`）；打印每个关键姿势的脚和手 IK 偏差。长度和手着地时刻要和 `health/FallLanding` 的 `HARD_TICKS` / `FALL_TICKS` / `HAND_HITS` 一致 |
| `author_lightning_getup_v1.cjs` | 雷击伤害 V1 的起身（`docs/gameplay/lightning_injury_v1_plan.md` 3.5）：`node author_lightning_getup_v1.cjs [--write]`，从趴在地上撑起上身、收腿成四肢着地，之后沿用摔倒的半跪、撑膝、站起，写出 `lightning.animation.json`（`lightning.prone_get_up` 2.6 s）和 `body/prone_get_up` 动作 JSON；打印手 IK 偏差和眼睛位置。长度要和 `health/FallLanding.GET_UP_TICKS`（52）一致 |
| `author_prone_v1.cjs` | 趴在地上（雷击伤害第二轮，`docs/gameplay/lightning_injury_v1_plan.md` 10）：`node author_prone_v1.cjs [--write] [--arm=drag\|crawl\|idle\|out]`，写出 `prone.animation.json`：`prone.idle`（醒着趴着不动）、`prone.drag`（双腿麻痹拖腿爬）、`prone.crawl`（匍匐，同侧膝盖蹬）、`prone.out`（昏迷，脸侧贴地）。爬的两个片段一循环拉两下，按 `skeleton/ProneCrawl` 同一条速度曲线算手掌：拉的那段手掌在世界里不动。打印手 IK 偏差、头 / 胸 / 骨盆 / 手肘 / 手掌离地、拉动时手掌的滑动；`--arm` 逐键列出那一段的手臂。`REACH`、`SLOW`、`CYCLE_BLOCKS` 要和 `ProneCrawl` 一致 |
| `pose_lib.cjs` | 全身动作共用：腿的两骨 IK、按 FK 摆身体、手用核心的 IK 解出后烘焙成 FK、关键姿势变片段、坐在地上的姿势（`SEAT` / `REST`，以及处理小腿时的 `SHIN_WORK`：身体前倾、左膝抬起、右腿向旁边伸开） |
| `author_dress_v1.cjs` | 按部位包扎（`docs/gameplay/medical_actions_by_part_v1.md`）：`node author_dress_v1.cjs [--write]`（`ONLY=head\|chest\|belly\|leg` 只算一个，`ROWS=1` 打印每 0.1 秒的手 IK），写出 `medical.bandage_{head,chest,belly,leg}` 片段和 `player_actions/item/bandage_<部位>.json`。绷带卷沿部位外一圈圆角路径走三圈，时间和 `client/BandageWrapRenderer` 的 `wrapFrom` / `wrapSeconds` 一致；两只手轮流在背后（小腿下面）接卷、在前面压住，手用 IK 解出后烘焙成 FK。打印手 IK 最大偏差、卷和手掌离身体最近的距离、卷离地高度、第一人称画面里的角度 |
| `author_alcohol_parts_v1.cjs` | 按部位倒酒精（同一份文档）：`node author_alcohol_parts_v1.cjs [--write]`（`ONLY`、`ROWS` 同上），写出 `medical.alcohol_{head,chest,belly,leg}` 片段（只有身体）和 `player_actions/item/alcohol_<部位>.json`。开盖、盖回的时间和 `alcohol_clean` 完全一样（声音 `MedicalItem.ALCOHOL_CUES` 和倒的时间窗 `client/MedicalPourJets` 2.1–2.9 秒对所有部位都成立），瓶子在 2.15–2.85 秒倒在部位上，左手捏着瓶盖放在一边；手在游戏里实时 IK。打印手 IK 偏差、瓶子和手掌离身体最近的距离、倒的时候瓶轴朝下多少、液流离伤口中心多远。`author_alcohol.cjs` 的前臂版本不受影响 |
| `roll_angle.cjs` | 绷带卷绕前臂的角度随时间的变化（`client/BandageWrapRenderer` 的 `START` / `TRAIL` 按它定） |

写出后在 Blockbench 里用 AFL Rig 插件打开 `src/main/blockbench/player_actions/medical_*.bbmodel`（或导入动作）看第一人称和第三人称，再跑 `node tools/check-medical-actions-v1a.cjs`。
