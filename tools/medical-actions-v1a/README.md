# 医疗用品使用动作的关键帧脚本（健康 V1a）

`player_actions/item/bandage_wrap.json`、`alcohol_clean.json` 和片段 `animations/player/medical.animation.json` 的来源。用 AFL Rig 插件的核心（`tools/blockbench/afl_rig.js`）在 Node 里算几何：手掌对准道具点、手腕 / 前臂扭转、道具之间和道具与手臂的距离、第一人称画面里的位置。说明见 `docs/gameplay/survival_health_v1a.md` 第 7.3 节。

| 文件 | 作用 |
|---|---|
| `lib.cjs`、`alc_lib.cjs` | 加载插件核心、骨架、道具；酒精瓶和瓶盖的转向 |
| `author_bandage.cjs` | 绷带：关键帧表；不带参数只打印读数，`--write` 写出动作、片段和道具点 |
| `author_alcohol.cjs` | 医用酒精：同上（`HITS=1` 列出瓶子碰到手臂的时刻） |
| `roll_angle.cjs` | 绷带卷绕前臂的角度随时间的变化（`client/BandageWrapRenderer` 的 `START` / `TRAIL` 按它定） |

写出后在 Blockbench 里用 AFL Rig 插件打开 `src/main/blockbench/player_actions/medical_*.bbmodel`（或导入动作）看第一人称和第三人称，再跑 `node tools/check-medical-actions-v1a.cjs`。
