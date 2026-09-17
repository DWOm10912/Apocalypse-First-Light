# P9-01 Artist Asset Integration V1

状态：正式资源与代码已接入；手臂现按 BR51 TaCZ→AFL adapter 模式挂到作者 `*_pos` 下，`compileJava` 与 P9 静态结构检查通过。**本次变更尚未实机验收**，不将手臂显示、握持姿态或声音记为客户端 PASS。下文较早的测试仅属于当时的资产接入版本。

维护台回归修正：Artist V2 runtime Geo 的 identifier 已规范为 `geometry.p9_01`；P9 专用维护台中心按新版几何范围从旧资产坐标 `(-0.186, 0.416)` 重新标定为 `(-0.024, 0.160)`。维护台缩放 `0.55`、平移 `(0, 0.045, 0)`、旋转 `(0, -90, -90)` 与动态纵向居中保持不变；BR51 和其他枪械 profile 未改。代码与资源验证不等于客户端画面验收。

## 资产与渲染

- 本次核验的原始 Artist V2：`E:/Download/AFL/p9_01.bbmodel`（42 组、171 元素、9 条动画）；可编辑正式模型在 `src/main/blockbench/p9_01.bbmodel`。运行时使用 `geo/p9_01.geo.json`、`animations/p9_01.animation.json`、`textures/item/p9_01.png`。本次未更改贴图。
- 运行时 Geo 保留作者原有 42 个骨骼，另在 `righthand_pos` / `lefthand_pos` 下各增加一个无 cube 的 AFL hand anchor，共 44 骨骼。作者占位臂在 Blockbench 源中隐藏且不导出，运行时 Geo 中无其 cube；`camera` 可见 cube 亦不导出。九条 clip 的 `*_hand_1` 都有 `[1,1.6,1]` Y 缩放，对应 anchor 各有恒定 `[1,0.625,1]` 逆补偿。
- 已移除上一轮错误的 `gun → *_hand_motion → *_hand_anchor` 子链与九条额外重定向轨。新的 source-only Classic/Slim 参考臂挂在各自 anchor 下，保持 `export=false`；校准以作者 4×12×4 占位臂顶面中心（源模型右 `[-5.98125,20,0]`、左 `[6.01875,20,0]`）为接触点。Java 仍读取 `right_hand_anchor` / `left_hand_anchor`。P9 手臂层现直接调用与 BR51 相同的 `NativePlayerArmRenderer.render(...)`，完全恢复共享 X/Y/Z 尺寸 0.62/0.78/0.62；此前分别统一缩小截面或单独缩短前臂的方案均已弃用。P9 第一人称 Display 将枪和两只手所在整套 rig 相对镜头下移 2 模型单位、远离镜头 5 模型单位；保持原手部锚点和 Artist 动画不变。新的屏幕占比仍待实机验收。
- `P901Renderer` 继续绑定标准 AFL 两个 hand anchor；world compatibility 分支仍作用于完整 `g19_and_mag` 枪组件。旧 `P901Presentation`/`p9_01.equip.json` 已由服务端触发的作者 `draw`、`put_away` 正式动画取代。
- 由于骨架的 rear/front sight 坐标改变，仅重新标定 P9 视觉 HIP Display 与 Native ADS/瞄具挂点；ADS 时长 0.15 秒、FOV 倍率 0.95、eye relief 0.34、枪械战斗逻辑均未变。此几何标定为静态计算，仍须客户端检验准线与红点。

## 动作与状态

| 状态 | 作者 clip | 处理 |
| --- | --- | --- |
| 有弹待机 | `static_idle` | 按弹量选常态 |
| 空仓待机 | `empty_idle` | 最后一发的 `shoot` 结束后进入；0.04167 秒静态空仓姿态后保持末帧，不循环重启；套筒取 `inspect_empty` 第 0 帧 |
| 成功开火含最后一发 | `shoot` | 显式 one-shot；完整继承 `static_idle` 握持姿态，只在同一基姿态上叠加作者已有后坐/滑套抖动；枪声由服务端附件状态决定 |
| 战术换弹 | `reload_tactical` | 48 tick，弹药在结束时提交 |
| 空仓换弹 | `reload_empty` | 操作锁保持 63 tick；为避免 3.12 秒动画结束与第 63 tick 弹药同步之间短暂回到 `empty_idle`，P9 在结束前 2 tick 同步装填状态，动画结束后直接进入 `static_idle` |
| 切出 / 收起 | `draw` / `put_away` | 使用现有 Session/GeoItem 触发；收枪可能被 Vanilla 换槽可见时间截短 |
| 有弹 / 空仓检视 | `inspect` / `inspect_empty` | V 键现有 Inspect V1，主手弹量选分支；两条均 7.33 秒 |

P9 原 gameplay 换弹长度 `1.30 秒/26 tick`、`1.65 秒/33 tick` 调整为作者动画的 `2.38 秒/48 tick`、`3.12 秒/63 tick`。仅这两项 gameplay timing 改变；17 发容量、9×19mm、射速 3 tick、伤害、射程、散布、后坐、Noise、附件兼容和 ADS 时间/FOV 均未改变。战术换弹仍在动画完成处提交弹药；空仓换弹为消除动画结束后闪回后定姿态，在 63 tick 操作锁结束前 2 tick 同步弹药，期间操作锁仍阻止开火。声音仍在作者 marker 原时间点，不人为改关键帧。

Inspect 沿用现有优先级：draw/reload/fire 阶段不能抢占；fire/reload 可以取消较低优先级的 inspect 或 inspect_empty。换槽、死亡、维度变化、打开菜单仍取消当前 Session。服务端只对实际主手枪械选 `inspect_empty`，没有新检视网络协议。

`shoot` 原动画缺少两手握持基线；此前已将 `static_idle` 的手臂、弹匣/下机匣静态轨同步到源和运行时，仅保留作者开火后坐/滑套微动。本轮不再重定向 Artist 手臂；清除旧 `*_hand_motion` 轨，新增的 AFL anchor 只继承 Artist 父骨骼运动并施加恒定缩放。`empty_idle` 套筒角度/位置精确取 `inspect_empty` 第 0 帧，并由 P9 控制器 `thenPlayAndHold` 保持，不再对静态片段调用 `thenLoop`。其它作者动作关键帧及其时间不变。迁移脚本：`tools/migrate-p9_01-hand-adapter.mjs`、`tools/finalize-p9_01-artist-animations.mjs`；静态检查：`tools/verify-p9-01-artist-integration.mjs`。

## 音效

作者 OGG 未转码、混音或改写。文件统一在 `assets/apocalypse_firstlight/sounds/weapons/p9_01/`：`p9_01_fire.ogg`、`p9_01_suppressed.ogg`、`p9_01_magazine_in.ogg`（源名 `p9_01_mag_in.ogg`）、`p9_01_magazine_out.ogg`（源名 `p9_01_mag_out.ogg`）、`p9_01_slide_action.ogg`、`p9_01_inspect.ogg`。六个运行时文件各自与作者源 SHA-256 相同。

源模型和运行时 marker 已从旧 TaCZ/短名重映射到正式 `apocalypse_firstlight:p9_01_*` SoundEvent ID；`NativeGunAnimations.cues` 可直接读取正式 ID，同时保留旧短名兼容。战术换弹 marker 仍为 0.36/0.79 秒；空仓仍为 0.26/0.76/1.64 秒；检视仍为 0 秒。`shoot` 中的 `apocalypse_firstlight:p9_01_fire` marker 留在资源中，但生产 controller 无客户端 sound keyframe handler，服务端也不把 shoot cue 加入会话，所以不会重复播枪声；枪声继续由附件状态选择普通或 suppressed。

作者本批普通枪声、换弹和检视文件是双声道 OGG；suppressed 是单声道。文件按用户“不得编辑音频”原样接入。双声道在游戏内的空间衰减/定位效果尚未客户端验证，不在此声称声音已正常播放。

BR51 原 `sounds/br51_01/` 11 个文件移至 `sounds/weapons/br51_01/`；`sounds.json` 中每个 BR51 文件路径更新，公开 SoundEvent ID、音量/半径、marker 时间未改。新文件与迁移前 Git blob 哈希逐个相同；旧目录与旧运行时资源路径均不存在。

## Camera、兼容性与验证

`NativeCameraBoneConsumer` 复用 Gecko 同帧求值，P9 只对 `draw`、`put_away`、两种 reload 和两种 inspect 消费作者 `camera` rotation。`shoot` camera 不消费，仍由 Native recoil 负责开火镜头反馈，不叠加第二份后坐。六条动画轨道 `barrel`、`barrel2`、`bullet2`、`bullet_in_barrel`、`group2`、`slide_1` 在 Geo 中没有同名骨骼，记为 `KNOWN_ORPHAN_TRACKS`；`P901Model.crashIfBoneMissing=false`，不改作者时间轴。若实机出现明显缺失或高频错误再定位处理。

- `compileJava`：PASS。
- `check`：PASS；其中全项目历史检查不代表 P9 画面验收。
- `node tools/verify-p9-01-artist-integration.mjs`：PASS；9 动画、关键骨骼、proxy 移除、marker、P9/BR51 声音文件路径及旧目录。
- 隔离 `runGameTestServer -I src/dev/inspect-gametest.init.gradle`：3/3 PASS；含新 P9 有弹/空仓 Inspect 分支、优先级与取消。
- BR51 11 个 OGG 文件逐个与迁移前 blob 相同；P9 6 个 OGG 与作者源逐个相同。
- 本次 BR51 模式适配：`compileJava` PASS；`node tools/verify-p9-01-artist-integration.mjs` PASS（44 骨骼、9 条 clip、hand anchor 父子关系、缩放、空仓套筒姿态、marker/声音文件路径）。没有运行无关测试。此前客户端截图确认手臂缺失；**本次适配后的客户端画面尚未复验**。手部贴合、ADS/红点、开火、两种换弹/检视、Camera 和音频仍须人工 QA。

本次第一优先验收是重启客户端后的 P9 `static_idle`：左右玩家皮肤臂须同时出现、握持正常、无巨大方柱；若这里失败，即视为未修好，不继续讨论 `shoot`。随后依次检查 ADS、Shoot、Tactical Reload、Empty Reload、Inspect、Inspect Empty、Draw/Put Away。未获得用户实机确认前，状态仅为 `P9_ARM_RENDERING_CODE_SIDE = READY_FOR_QA`，不得声称手臂已修好。
