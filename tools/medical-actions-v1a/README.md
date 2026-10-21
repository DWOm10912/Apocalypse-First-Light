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
| `roll_angle.cjs` | 绷带卷绕前臂的角度随时间的变化（`client/BandageWrapRenderer` 的 `START` / `TRAIL` 按它定） |

写出后在 Blockbench 里用 AFL Rig 插件打开 `src/main/blockbench/player_actions/medical_*.bbmodel`（或导入动作）看第一人称和第三人称，再跑 `node tools/check-medical-actions-v1a.cjs`。
