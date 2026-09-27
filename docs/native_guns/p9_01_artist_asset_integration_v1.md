# P9-01 Artist Asset Integration V1

> **历史文档，非当前运行时合同。** 2026-09-27 起，正式物品 ID 仍是 `apocalypse_firstlight:p9_01`，但已原子切换为 `ConfiguredNativeGunItem` + `NativeAnimatedWeaponRenderer` + `NativePlayerArmRenderer`，读取 `p9_01_v2_native` Geo、动画、Pure Mesh sidecar、贴图与 Display。旧 P9 专用 Java 类及旧 Geo/动画/贴图/手持 Display 运行时资源已删除；旧 `.bbmodel` 仅作历史源保留。V2 两种换弹为 34/40 tick，均在 tick 18 结算；九条 clip 已启用，`first_draw` 资源存在但无运行时触发。手部、ADS、第三人称、维护台、现有附件与声音仍待客户端验收。当前资产与合同见 [P9-01 V2 Native Rig](p9_01_v2_native_rig_v1.md)。

> 以下记录的是 Artist V1/V2 与 2026-09-26 Pure Mesh 过渡阶段的实现和验证；其中旧源文件、P9 专用类、时长、ADS 数值和运行时资源路径均不再代表当前 V2。历史测试结果不能替代新资产的游戏内验收。

## 历史资产接入记录（已被 V2 Native 取代）

状态：正式资源与代码已接入；手臂现按 BR51 TaCZ→AFL adapter 模式挂到作者 `*_pos` 下，`compileJava` 与 P9 静态结构检查通过。**本次变更尚未实机验收**，不将手臂显示、握持姿态或声音记为客户端 PASS。下文较早的测试仅属于当时的资产接入版本。

维护台回归修正：Artist V2 runtime Geo 的 identifier 已规范为 `geometry.p9_01`；P9 专用维护台中心按新版几何范围从旧资产坐标 `(-0.186, 0.416)` 重新标定为 `(-0.024, 0.160)`。维护台缩放 `0.55`、平移 `(0, 0.045, 0)`、旋转 `(0, -90, -90)` 与动态纵向居中保持不变；BR51 和其他枪械 profile 未改。代码与资源验证不等于客户端画面验收。

## 资产与渲染

- 本次核验的原始 Artist V2：`E:/Download/AFL/p9_01.bbmodel`（42 组、171 元素、9 条动画）；可编辑正式模型在 `src/main/blockbench/p9_01.bbmodel`。运行时使用 `geo/p9_01.geo.json`、`animations/p9_01.animation.json`、`textures/item/p9_01.png`。本次未更改贴图。
- 运行时 Geo 保留作者原有 42 个骨骼，另在 `righthand_pos` / `lefthand_pos` 下各增加一个无 cube 的 AFL hand anchor；后续又加入零位 `handling` 父骨骼（包住 `g19_and_mag` 与 `righthand`，左手仍独立），当前共 45 根。作者占位臂在 Blockbench 源中隐藏且不导出，运行时 Geo 中无其 cube；`camera` 可见 cube 亦不导出。九条 clip 的 `*_hand_1` 都有 `[1,1.6,1]` Y 缩放，对应 anchor 各有恒定 `[1,0.625,1]` 逆补偿。
- 已移除上一轮错误的 `gun → *_hand_motion → *_hand_anchor` 子链与九条额外重定向轨。新的 source-only Classic/Slim 参考臂挂在各自 anchor 下，保持 `export=false`；校准以作者 4×12×4 占位臂顶面中心（源模型右 `[-5.98125,20,0]`、左 `[6.01875,20,0]`）为接触点。Java 仍读取 `right_hand_anchor` / `left_hand_anchor`。HIP 保持共享 X/Y/Z 尺寸 0.62/0.78/0.62；P9 ADS 为减少遮挡，围绕不变的握持端点平滑缩至横截面 90%、前臂长度 86%，不移动枪体或手部锚点。新的 ADS 手臂占比仍待实机验收。
- 迁移前 `P901Renderer` 按原名绑定两个 AFL hand anchor；旧资产的非第一人称 2.5 倍 `g19_and_mag` 枪组件大小由 P9 专用路径保持。旧 `P901Presentation`/`p9_01.equip.json` 后由服务端触发的作者 `draw`、`put_away` 动画取代。
- 由于骨架的 rear/front sight 坐标改变，仅重新标定 P9 视觉 HIP Display 与 Native ADS/瞄具挂点；ADS 时长 0.15 秒、FOV 倍率 0.95、eye relief 0.34、枪械战斗逻辑均未变。此几何标定为静态计算，仍须客户端检验准线与红点。

## 动作与状态

| 状态 | 作者 clip | 处理 |
| --- | --- | --- |
| 有弹待机 | `static_idle` | 按弹量选常态 |
| 空仓待机 | `empty_idle` | 最后一发的 `shoot` 结束后进入；0.04167 秒静态空仓姿态后保持末帧，不循环重启；套筒取 `inspect_empty` 第 0 帧 |
| 成功开火含最后一发 | `shoot` | 显式 one-shot；完整继承 `static_idle` 握持姿态，只在同一基姿态上叠加作者已有后坐/滑套抖动；枪声由服务端附件状态决定 |
| 战术换弹 | `reload_tactical` | 48 tick，弹药在结束时提交 |
| 空仓换弹 | `reload_empty` | 历史版本操作锁 63 tick，曾在 tick 61 提前结算；V2 已改为 40 tick / tick 18 |
| 切出 / 收起 | `draw` / `put_away` | 使用独立 `p9_01_draw` / `p9_01_put_away` 音效并在 0 秒触发；`draw` 延长为 0.59 秒，原 0–0.1667 秒动作关键帧不变，最终姿态保持至音效结束；`put_away` 保持原 1.30 秒时间轴，画面仍可能被 Vanilla 换槽可见时间截短 |

| 有弹 / 空仓检视 | `inspect` / `inspect_empty` | V 键现有 Inspect V1，主手弹量选分支；两条均 7.33 秒 |

旧 Artist 资产的空仓重新装备问题：作者 `draw` 把 `slide` 写成 loaded 位置，当时由 P9 专用 `empty_draw_slide` 控制器修正。`group2` 是旧资源孤立轨道，不参与覆盖。V2 已使用另一套动画和通用控制器；首帧与联机视觉仍待客户端复验。

历史 Artist 接入时，换弹长度由 `1.30 秒/26 tick`、`1.65 秒/33 tick` 调至 `2.38 秒/48 tick`、`3.12 秒/63 tick`，空仓版后来曾在 tick 61 结算。当前 V2 的时长、结算、音效 marker 和附件验证状态均以新资产与数据为准，不沿用这一段历史数值。

Inspect 沿用现有优先级：draw/reload/fire 阶段不能抢占；fire/reload 可以取消较低优先级的 inspect 或 inspect_empty。换槽、死亡、维度变化、打开菜单仍取消当前 Session。服务端只对实际主手枪械选 `inspect_empty`，没有新检视网络协议。

`shoot` 原动画缺少两手握持基线；此前已将 `static_idle` 的手臂、弹匣/下机匣静态轨同步到源和运行时，仅保留作者开火后坐/滑套微动。原 AFL anchor 只继承 Artist 父骨骼运动并施加恒定缩放。`empty_idle` 套筒角度/位置取 `inspect_empty` 第 0 帧，迁移后仍保持这一空仓姿态。其它作者动作关键帧及其时间不变。历史迁移脚本：`tools/migrate-p9_01-hand-adapter.mjs`、`tools/finalize-p9_01-artist-animations.mjs`；旧静态检查 `tools/verify-p9-01-artist-integration.mjs` 仍包含已退休类名，不能直接作为本轮迁移验证。

## 音效

作者 OGG 未转码、混音或改写。文件统一在 `assets/apocalypse_firstlight/sounds/weapons/p9_01/`：`p9_01_fire.ogg`、`p9_01_suppressed.ogg`、`p9_01_magazine_in.ogg`（源名 `p9_01_mag_in.ogg`）、`p9_01_magazine_out.ogg`（源名 `p9_01_mag_out.ogg`）、`p9_01_slide_action.ogg`、`p9_01_inspect.ogg`。六个运行时文件各自与作者源 SHA-256 相同。

源模型和运行时 marker 已从旧 TaCZ/短名重映射到正式 `apocalypse_firstlight:p9_01_*` SoundEvent ID；`NativeGunAnimations.cues` 可直接读取正式 ID，同时保留旧短名兼容。战术换弹 marker 仍为 0.36/0.79 秒；空仓仍为 0.26/0.76/1.64 秒；检视仍为 0 秒。`shoot` 中的 `apocalypse_firstlight:p9_01_fire` marker 留在资源中，但生产 controller 无客户端 sound keyframe handler，服务端也不把 shoot cue 加入会话，所以不会重复播枪声；枪声继续由附件状态选择普通或 suppressed。

作者本批普通枪声、换弹和检视文件是双声道 OGG；suppressed 是单声道。文件按用户“不得编辑音频”原样接入。双声道在游戏内的空间衰减/定位效果尚未客户端验证，不在此声称声音已正常播放。

BR51 原 `sounds/br51_01/` 11 个文件移至 `sounds/weapons/br51_01/`；`sounds.json` 中每个 BR51 文件路径更新，公开 SoundEvent ID、音量/半径、marker 时间未改。新文件与迁移前 Git blob 哈希逐个相同；旧目录与旧运行时资源路径均不存在。

## Camera、兼容性与验证

`NativeCameraBoneConsumer` 复用 Gecko 同帧求值，P9 只对 `draw`、`put_away`、两种 reload 和两种 inspect 消费作者 `camera` rotation。`shoot` camera 不消费，仍由 Native recoil 负责开火镜头反馈，不叠加第二份后坐。六条动画轨道 `barrel`、`barrel2`、`bullet2`、`bullet_in_barrel`、`group2`、`slide_1` 在 Geo 中没有同名骨骼，记为 `KNOWN_ORPHAN_TRACKS`；通用模型对这些旧资源孤立轨仍关闭 missing-bone crash，不改作者时间轴。若实机出现明显缺失或高频错误再定位处理。

- `compileJava`：PASS。
- `check`：PASS；其中全项目历史检查不代表 P9 画面验收。
- `node tools/verify-p9-01-artist-integration.mjs`：PASS；9 动画、关键骨骼、proxy 移除、marker、P9/BR51 声音文件路径及旧目录。
- 隔离 `runGameTestServer -I src/dev/inspect-gametest.init.gradle`：3/3 PASS；含新 P9 有弹/空仓 Inspect 分支、优先级与取消。
- BR51 11 个 OGG 文件逐个与迁移前 blob 相同；P9 6 个 OGG 与作者源逐个相同。
- 本次 BR51 模式适配：`compileJava` PASS；`node tools/verify-p9-01-artist-integration.mjs` PASS（44 骨骼、9 条 clip、hand anchor 父子关系、缩放、空仓套筒姿态、marker/声音文件路径）。没有运行无关测试。此前客户端截图确认手臂缺失；**本次适配后的客户端画面尚未复验**。手部贴合、ADS/红点、开火、两种换弹/检视、Camera 和音频仍须人工 QA。

本次第一优先验收是重启客户端后的 P9 `static_idle`：左右玩家皮肤臂须同时出现、握持正常、无巨大方柱；若这里失败，即视为未修好，不继续讨论 `shoot`。随后依次检查 ADS、Shoot、Tactical Reload、Empty Reload、Inspect、Inspect Empty、Draw/Put Away。未获得用户实机确认前，状态仅为 `P9_ARM_RENDERING_CODE_SIDE = READY_FOR_QA`，不得声称手臂已修好。
