# Native AFL Gun Framework V0.4.6 — 第一人称手臂与换弹架构审计

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

研究日期：2026-09-07。范围：只读源码、二进制与资源结构审计；只写本文和框架文档研究摘要。**不是新的 runtime 版本，不代表视觉修复完成。**

## 结论先行

**STOP PATCHING CURRENT ARM GEOMETRY**

推荐 `REFACTOR_TO_LOCATOR_ARCHITECTURE`：建立 AFL 自己的、预览与运行时一致的 player-arm locator 约定，逐步替换 Reload 短块／薄掌面特例。不是复制 TaCZ，也不是重写整个枪械框架。

关键修正：AFL **已经**有 animated hand anchors、共同 weapon_root、真实玩家 skin 和独立矩阵。当前欠缺的是 **authoring preview → locator → Vanilla arm 的统一坐标、尺度和姿态契约**；不能把原因写成“GeckoLib 读不到动画 anchor”或“根本没有 locator”。

也不能把 TaCZ 描述成“右手永远冻结、完全没有程序变换”：官方 M4A1 的换弹右手局部轨道是常量，Glock 17 换弹和 M700 空仓换弹的右手却有变化轨道。共同父节点简化协调，但不自动保证所有枪、所有动作的相对矩阵恒定。动画作者仍负责握持关系。详见第 6 节“官方枪模层级与动画事实”。

上一轮 V0.4.5.1 背缘和侧面薄掌面都被用户否决，任务已终止。本轮保留那些未提交改动，不将历史数值 PASS 升格成视觉验收，也不继续调参。

## 1. Sources inspected 与版本边界

### 本地权威证据

- AFL live：`master`，HEAD `7685f61`。起始工作区已有 HandLayer、ReloadGrip、DEV 检查及四份文档的未提交修改；另有用户 `.obsidian/workspace.json` 改动。本文描述 **工作区**，不是仅描述 HEAD。
- 当前依赖：`build.gradle:194` 的 `curse.maven:timeless-and-classics-zero-1028108:8141310`。
- 原始 TaCZ JAR：`D:/Minecraft Modding/Apocalypse First Light/.gradle-user/caches/modules-2/files-2.1/curse.maven/timeless-and-classics-zero-1028108/8141310/bddafeea4c9c1132ed720c30fbaedfe5ab25e846/timeless-and-classics-zero-1028108-8141310.jar`。
- Manifest 实测 `Implementation-Version: 1.1.8-hotfix`；SHA-256：`9ED8ADA1283ED7A793A70CC1B51C4A340F367CE84707E1A7B8CF21EE3D288D77`。
- 本地 **没有找到 1.1.8-hotfix source JAR**。两个 Gradle cache 中找到的是旧 `6632240-sources-6633203` / 1.1.6 source，未把它冒充 hotfix。
- 使用本地 8141310 mapped JAR 的 `javap -c -p` 核对 LeftHandRender、RightHandRender、RenderHelper、FunctionalBedrockPart、BedrockGunModel、AnimateGeoItemRenderer 等关键方法；与下面固定 tag 的手臂替换、委托与接口路径一致。不是声称全仓库二进制逐字节对应源码。
- JAR 内嵌 `META-INF/jarjar/simplebedrockmodel-2.2.2-forge+mc1.20.1.jar`；内存读取其 mods.toml、类目录，确认版本和 FirstPersonRenderHandler / IFPGeoItemRenderer / PlayerModelMixin 的存在，未解压到项目。
- 官方三把枪的层级、动画通道是否变化、display 绑定来自 **JAR 内默认枪包**，不以可被用户修改的 `run/tacz/` 解包目录替代。
- Vanilla / Forge 实现来自本地 mapped 1.20.1-47.4.22 source JAR：`C:/Users/willi/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.22_mapped_official_1.20.1/forge-1.20.1-47.4.22_mapped_official_1.20.1-sources.jar`。

### 固定公开源码与官方文档

TaCZ GitHub tag `1.1.8-hotfix` 解析到 **`b43eb84c38e9768d8e73c8b14f0b845669704b38`**。不是滚动 `1.20.1` 分支，也没有使用搜索引擎的非官方源码总结。

| 标记 | 已读来源与证据位置 |
|---|---|
| W1 | [官方手臂定位组][W1]：两种预览尺寸、Position/Pivot 关系、运行时替换说明 |
| W2 | [官方 Animation Production Standards][W2]：Grouping、Pivot、static_idle、reload_tactical/empty、shoot |
| W3 | [官方动画与状态机配置][W3]：animation、state_machine、缺省动画 |
| W4 | [官方动画状态机教程][W4]：状态生命周期、轨道、基态、叠加和覆盖 |
| T1 | [历史 FirstPersonRenderEvent][T1]：23–24 行订阅注解被注释；66–68 行旧调用路径 |
| T2 | [BedrockGunModel][T2]：76–79 行手臂 functional renderer 注册；222–236 行额外弹匣机制 |
| T3 / T4 | [LeftHandRender][T3] / [RightHandRender][T4]：22–38 行坐标转换、矩阵快照、延后委托 |
| T5 | [RenderHelper][T5]：73–96 行选择 PlayerRenderer 并调用左右手 helper |
| T6 | [FunctionalBedrockPart][T6]：47–77 行节点变换与替代渲染分支 |
| T7 | [BedrockModel][T7]：280–335 行坐标转换；355–371 行本体提交、委托、清空队列 |
| T8 | [BedrockAnimatedModel][T8]：71–84 行清理动画值；95–103 行注册；146–169 行动画监听器 |
| T9 | [GunItemRendererWrapper][T9]：163–230 行第一人称完整生命周期 |
| T10 | [AnimateGeoItemRenderer][T10]：46–47 行实现 SBM 接口；325–399 行实例与副手阻止 |
| T11 | [FirstPersonRenderGunEvent][T11]：119–130 行整体处理；140–235 行视点逆变换；238–296 行程序 root 运动 |
| T12 | [TaCZ ItemInHandRendererMixin][T12]：34–37 行 BeforeRenderHandEvent；39–60 行旧 equip 修正已注释 |
| T13 | [CameraSetupEvent][T13]：78–91 行 hand camera 消费者；123–163 行物品 FOV |
| T14 | [TaCZ PlayerModelMixin][T14]：32–56 行清除默认旋转，不清除手臂位置 |
| T15 | [AnimationStateMachine][T15]：52–57 行更新；78–93 行状态转换 |
| T16 | [AnimationController][T16]：127–185 行按轨道更新与混合标记 |
| T17 | [LocalPlayerReload][T17]：53–117 行输入、检查、网络请求、reload/cancel 信号 |
| T18 | [GunAnimationStateContext][T18]：81–85、148–149、242–248 行供弹与状态查询 |
| S1 | [default_state_machine.lua][S1]：72–86 行 empty/tactical；110–113 行基态；318–324 行 shoot 叠加 |
| S2 / S3 | [Glock 状态机][S2] / [M4A1 状态机][S3]：各自扩容变体选择、继承默认状态 |
| S4 / S5 | [manual_action 状态机][S4]：16–25 行 bolt；[M870 状态机][S5]：83–88 行取消换弹进入 reload_end |
| B1 | [SBM FirstPersonRenderHandler][B1]：175–210 行真正 RenderHandEvent 入口；248–254 行接口识别 |
| B2 / B3 | [SBM ItemInHandRendererMixin][B2]：21–27 行切枪阶段锁原版；[SBM PlayerModelMixin][B3]：30–55 行默认旋转清理 |
| BB | [Blockbench 官方 Bedrock codec][BB]：886–899、965–971 行导出 X 轴转换。此补充来源是固定当前提交，不假称 TaCZ 随包的 Blockbench 版本 |

SBM 源码固定在 tag `2.2.2-forge-mc1.20.1` → `0a4a4084eca9e5f055d63fdd2fee37e284dcfb63`。Wiki 是未绑定 hotfix 的现行文档，只用于解释规范；有差异时以上述本地 JAR／固定源码为准。

许可边界：TaCZ 仓库 [README][LICENSE] 声明代码 GPL-3.0、资产 CC BY-NC-ND 4.0。本任务按用户更严格的“只研究、不复制实现或资产”执行；本文不是许可证兼容性法律结论。

## 2. CURRENT AFL HAND PIPELINE

```text
Forge RenderHandEvent
  → P901FirstPerson：只在主手 Native Pistol 时接管双方 hand pass
  → 独立 weapon PoseStack；base camera translation / equip 下沉
  → Vanilla ItemRenderer + exported first-person display
  → P901Renderer / GeoItemRenderer：求值 action 动画
  → root：叠加仅 Reload 的共同 presentation
  → animated weapon_root
       ├─ gun → frame / slide / barrel / magazine / reload_magazine ...
       ├─ right_hand_anchor → HandLayer
       └─ left_hand_anchor  → HandLayer
```

证据：[FirstPerson](../../../src/main/java/com/antaurora/apofirstlight/weapon/client/P901FirstPerson.java)、[Renderer](../../../src/main/java/com/antaurora/apofirstlight/weapon/client/P901Renderer.java)、[HandLayer](../../../src/main/java/com/antaurora/apofirstlight/weapon/client/P901HandLayer.java)、[Presentation](../../../src/main/java/com/antaurora/apofirstlight/weapon/client/P901Presentation.java)。这些链接相对本文位于 `docs/dev/native-gun/`，实际源码统一在仓库 `src/`。

### 累积的变换与分支

1. 整体 camera base 为 `(0.60,-0.54,-0.64)`；exported first-person display scale 为 `0.3`。这两项均未在本轮改变。
2. Reload 共同父补偿：相机等价平移 `(0.05,+0.28,-0.25)`、Z/Y/X 旋转 `(-16,+32,+8)` 度；0–0.24 秒进入，保持到 0.85，1.18 回零。另叠加源 `weapon_root` 动画，不是右手独立动作。
3. 正常手臂：先取得 evaluated anchor 的 pivot；加左右不同的 contact offset；`retainRigidContact` 去掉继承缩放但保留平移／正交基；追加硬编码左右 forearm quaternion；重设统一 arm scale `0.30`；再以 `palmY=8/9.5` 和 Classic/Slim 横向中心补偿。
4. 正常路线从当前 PlayerRenderer 取完整 arm/sleeve ModelPart，但用 `PartPose.ZERO` 去掉原始 part pose；随后恢复其状态。两种标准几何和真实玩家 skin 已存在，不是固定肤色。
5. Reload 右手改走 `P901ReloadGrip`：不绘制完整 arm/sleeve，改用固定 anchor-relative offset、额外 -90 度局部 Y 转向、薄表面和右手末端 UV；当前尺寸 Classic `3.40×3.00×0.55`、Slim `2.55×3.00×0.55`。此视觉方案已失败，保留在工作区只是因为用户终止而未回滚。

**不要把历史尝试写成当前三层同时渲染。** 4×4×4 短掌块已被薄表面替换；当前不是“全臂 + 短块 + 薄面同时画”。当前也没有随 READY/FIRE/RELOAD 变化的数值 arm-scale 曲线：`0.30` 是常量，发生变化的是右手几何和映射分支。

### 为什么不断需要补偿

源 `.bbmodel` 的两个 reference group 都在各自 hand anchor 下，但 reference group 自身还有独立 translation/rotation；4×12×4 cube 只是该子组中的标准长方体。它们和 cube 都为 `export=false`，运行时 geo 确实没有 reference bones。**被舍弃的子组变换没有被当作一个规范化 arm binding 导出**；Java 另行维护 contact／方向／尺度。Blockbench 通过验收，不能自动保证另一个映射结果一致。

当前 arm scale 的历史用途是消除对 display scale 的偶然依赖、保住用户认可的较小构图。它不是天然错误，也不能盲改成 1；但如果 authoring preview 不复现相同尺度处理，就不再所见即所得。palm offset 是对“anchor 是接触点、原版 part 不是接触点”的补偿；固定坐标适配本来必要，问题在于它逐渐承担了应由 rig 决定的握持姿态。

短块与薄面只绕过被遮挡／穿模／悬断的表象，没有解决同一 locator 在 READY 和 RELOAD 下对应不同手部表示的问题。

### 当前动画事实与保留价值

- runtime `fire` 轨道：slide、front_sight、rear_sight、sight_anchor、trigger、barrel、weapon_root。
- runtime `reload` 轨道：weapon_root、magazine、reload_magazine、left_hand_anchor；**没有独立 right_hand_anchor track**。
- gun 与两 anchor 同属 weapon_root；只要 gun 的局部姿态保持不变，右手相对握把本就可刚性跟随。左手已经由导出动画驱动，Java 没有额外左手路径插值器。
- `reload_magazine` 是存在于源与 runtime 的独立 bone，不能误报缺失，也不能因 TaCZ 有 additional_magazine 就复制其处理方式。
- 单个 GeckoLib `action` controller 接受服务器 fire/reload 触发；当前通用 Reload 为 26 tick／约 1.30 秒，无 tactical/empty 分支，无真实弹药。不是 TaCZ 式多轨叠加状态机。

来源：[bbmodel](../../../src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel)、[runtime geo](../../../src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json)、[runtime animation](../../../src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json)、[导出检查](../../../tools/export-p9-01.blockbench.js)、[Item](../../../src/main/java/com/antaurora/apofirstlight/weapon/P901Item.java)、[Actions](../../../src/main/java/com/antaurora/apofirstlight/weapon/P901Actions.java)。

## 3. TaCZ first-person render call chain：真正入口不是旧事件类

```text
Vanilla ItemInHandRenderer / Forge RenderHandEvent
  → 内嵌 SBM 2.2.2 FirstPersonRenderHandler.onRenderHand
  → 当前 IFPAnimationInstance 与 IClientItemExtensions custom renderer
  → IFPGeoItemRenderer.renderFirstPerson
  → TaCZ GunItemRendererWrapper
      更新 animation context / state machine → 将通道写入同一模型
      整体模型坐标转换 + camera locator / 程序 root 运动
      BedrockGunModel → 遍历父子节点
        hand-pos FunctionalBedrockPart
          → LeftHandRender / RightHandRender
          → 取得完整 evaluated node matrix，登记委托
      提交枪体 buffer
      执行手部委托 → RenderHelper → PlayerRenderer 左右手
      清空委托；清理模型临时动画值
```

[B1][B1] 根据 IFPGeoItemRenderer 接口识别物品；主手执行 custom render 后 cancel 原版 pass，副手是否 cancel 由 renderer 的 `blockOffhandRender()` 决定，TaCZ 返回 true。[T10][T10] 同时把 draw/put-away 生命周期桥接到 TaCZ 的动画系统。

[T1][T1] 的 `@SubscribeEvent` 被注释，所以不能拿其旧实现作为 1.1.8-hotfix 的活动入口。[T12][T12] 的旧 equip-progress 算法也被注释；现行换槽过渡锁有 [SBM 的对应 mixin][B2]。

这仍是一次枪与手共同的模型渲染生命周期，而非另起一套 Reload 手模型：只是在枪体 buffer 提交后，使用先前捕获的同帧 locator matrix 绘制真实手臂。[T7][T7]

### 相机层与 inverse matrix 的准确归属

- TaCZ 自己的 ItemInHandRendererMixin 在 `renderHandsWithItems` HEAD 发 BeforeRenderHandEvent；[CameraSetupEvent][T13] 接收并调用 `applyItemInHandCameraAnimation`。它是手部相机动画入口，不是替换手臂 cube 的入口。
- [FirstPersonRenderGunEvent][T11] 根据 idle_view、iron_view 等 camera-positioning 路径计算 **视点逆矩阵**，并处理整体程序 root 摆动、后坐和约束。
- [左右手 renderer][T3] 本身没有逆 gun matrix、逐状态 scale 或裁成短块的算法：它们继承节点矩阵，做固定坐标转换并保存快照。不要把 camera inverse 搬进 palm mapping。
- 因此不能宣称 TaCZ 的自然构图完全没有 runtime correction；可学习的是 **职责分离与明确的坐标契约**，不是“Java 里禁止出现任何变换”。

## 4. Hand-pos locator 与真实手臂

[BedrockGunModel][T2] 把两个保留节点注册为 functional renderer。[FunctionalBedrockPart][T6] 先应用自身 translation/rotation/scale，再调用非空 renderer；该分支不再 compile 原 cubes，也不遍历其 preview children。不是把参考皮肤贴到原枪包手臂上，而是替换绘制职责。节点仍保留在动画模型中。

`getRenderHand` 与 first-person context 控制是否画手；节点存在并不意味着第三人称也替换手臂。缺失定位节点不能靠“调用过注册函数”证明已挂进可遍历层级，AFL 应显式验证必需 locator 是否存在。

### LeftHandRender / RightHandRender

两者对称执行相同步骤，区别仅是 HumanoidArm：固定 Z 轴 180 度坐标转换；复制 pose 与 normal；登记延迟任务；任务内创建独立 PoseStack，恢复快照并调用 RenderHelper。这里复制矩阵值，是隔离枪体 buffer 与手部渲染状态，不是每帧重新猜接触位置。[T3][T3] [T4][T4]

RenderHelper 从当前玩家的 EntityRenderDispatcher 取得 PlayerRenderer，调用 `renderLeftHand` / `renderRightHand`；不是调用 ItemInHandRenderer 的摆手动画 helper。[T5][T5]

本地 Forge `PlayerRenderer.java:174–194` 可继续追到：

- 使用当前玩家 `getSkinTextureLocation()`；skin renderer 选择已包含 Classic/Slim 模型差异。
- base arm 使用 entitySolid，sleeve 使用 entityTranslucent，保留玩家外层可见设置和 Forge 专用手臂渲染 hook。
- 调用完整 PlayerModel arm，而不是重烘焙一个短掌段。
- Vanilla helper 会执行 setupAnim 并修改 PlayerModel 状态。TaCZ 和其内嵌 SBM 都有清除默认手臂旋转的 mixin，但它们 **只清 rotation，不把 arm pivot 清为零**。[T14][T14] [B3][B3]

这一点直接影响 AFL 方案：当前 AFL 的 `PartPose.ZERO` 不能与 TaCZ 的 PlayerRenderer helper 当成同一个坐标起点。下一轮也不能直接换成 PlayerRenderer helper，却漏掉默认姿态清理、状态恢复和 Forge hook 的决策。

## 5. Wiki 的预览偏移为什么成立

[W1][W1] 规定以下 **Blockbench 预览** 参数；下表不是 AFL 的新参数：

| 预览模型 | arm size | 相对 locator 的 cube Position |
|---|---|---|
| Steve left | 4×12×4 | (+4,0,-2) |
| Steve right | 4×12×4 | (-8,0,-2) |
| Alex left | 3×12×4 | (+4,0,-2) |
| Alex right | 3×12×4 | (-7,0,-2) |

推导依据是 **原版模型定义 + 坐标转换**，不是官方某把枪的几何坐标：

1. Vanilla Classic arm 的 part offset 为左右 `±5`、Y=2；局部 cube 的 X 起点分别为左 -1、右 -3。相加后是左 +4、右 -8。Slim 右 cube 起点变 -2，故右 -7；高度仍为 12，深度仍为 4。
2. Vanilla setupAnim 的非 crouch 路径将两 arm 的 Y 设回 2；因此局部 cube 的 Y=-2 与之抵消。不能仅看到 Slim mesh 初始 Y=2.5 就断言 Wiki 少了 0.5。
3. Blockbench Bedrock codec 将内部 cube 的 X 转成 `-(fromX + sizeX)`，并反转 bone pivot X；TaCZ loader 将 cube/节点转为 Java 的相对坐标及向下 Y；手臂 adapter 的固定 Z 轴半周转换再把原版手臂带到定位器约定方向。[BB][BB] [T7][T7] [T3][T3]
4. 例如 Classic 右手的原版 posed X 范围为 [-8,-4]、Y=[0,12]；经过 adapter 半周转换后为 X=[4,8]、Y=[-12,0]，对应上述 Blockbench 右预览经 Bedrock 导出／加载后的范围。

所以 locator pivot 表示 **带有固定 arm-local 约定的渲染参考坐标系**，不能直接等同为手掌中心。作者可以在其外侧再放一个以掌部为枢轴的 hand 控制组，驱动父组动画。[W2][W2] 的命名建议也作了这一区分。AFL 完全可以选择更直接的 palm-contact 坐标系，但必须自己实现并用同一套变换生成预览，不能拿 TaCZ 的四个数字去替换现有 contactOffset。

## 6. 官方枪模层级与动画事实

只记录父子关系与通道是否变化；**不记录武器尺寸、cube 坐标、pivot 值、UV、纹理或动画关键帧向量**。来源为上述 JAR 的 `assets/tacz/custom/tacz_default_gun/assets/tacz/`。

| 样本 | 右手与枪的关系 | 左手与弹匣的关系 | display 的状态机 |
|---|---|---|---|
| Glock 17 | root → right_and_gun；其下并列 righthand → righthand_pos 与 g17 | root → mag_and_left；其下并列 lefthand → lefthand_pos 与 mag_and_bullet → magazine | glock_17_state_machine |
| M4A1 | root → gun_and_righthand；其下并列 righthand → righthand_pos 与 m4a1 | root → mag_and_lefthand；其下并列 lefthand → lefthand_pos 与 mag_and_bullet → magazine | m4a1_state_machine |
| M700（栓动） | root → gun_and_righthand；其下并列 righthand → righthand_pos 与 defualt_gun（资源原拼写） | root → mag_and_lefthand；其下并列 lefthand → lefthand_pos 与 mag_and_bullet → magazine | manual_action_state_machine |

文件：`geo_models/gun/{glock_17,m4a1,m700}_geo.json`、`animations/{glock_17,m4a1,m700}.animation.json`、`display/guns/{glock_17,m4a1,m700}_display.json`。这些是 JAR 内证据位置，不是本轮新增的 AFL 资源。

对两个 Reload 的 righthand 通道做“不同向量数量”只读检查，结果：

- **M4A1**：rotation / position / scale 都为常量；共同 root 有动态轨道。符合“右手局部绑定保持、全枪带着手动”的例子。
- **M700**：tactical 右手为常量；empty 与 bolt 的 rotation / position 变化。操作枪机时不能假设右手永不离握把。
- **Glock 17**：两个 Reload 中 righthand 和 g17 的 position / rotation 都变化。其实现依靠作者协调动画，不能只由 shared-parent 推出它们的相对矩阵严格恒定。本研究未复算该枪所有关键帧的相对矩阵，不以“都变化”反过来断言它们必然脱手。
- 三者 lefthand 或其共同弹匣父组在 Reload 中变化；righthand_pos / lefthand_pos 本身不必直接有关键帧，**动画驱动祖先即可驱动 locator 的最终姿态**。
- 三者 shoot 未见单独的 hand 控制组通道，而是 root／枪机等部件通道；手通过层级继承共同运动。

这证明层级承担了“谁跟谁走”的大量工作，但层级不是自动 IK，也不会自动求碰撞或指尖包覆。

## 7. Reload 与状态机研究

### 资产、状态、渲染三个职责

[W3][W3] 区分 animation asset 与 state_machine 脚本；display 选择枪自己的资源，允许缺省动画。实际三把枪的 display 已验证绑定对应脚本，不以示例字段猜测。

状态机先更新状态，再让控制器按轨道求值；绑定到节点名的 translation/rotation/scale listener 将结果写入同一个模型，手和枪不是各跑一个 Java 时间轴。[T15][T15] [T16][T16] [T8][T8]

### static_idle、动作端点与 shoot overlay

官方规范要求持枪基态，动作衔接回同一基态，并将 shoot 作为混合动画。[W2][W2] 固定版本 [默认脚本][S1] 实际在基础轨道循环 static_idle；reload 在主轨道以覆盖方式一次播放；shoot 选择 gun-kick 轨道，以 additive 标记运行。不要在 additive shoot 中再写一遍绝对手部 Ready 姿态，否则会把偏移重复相加。

“返回 static_idle”是视觉基准／轨道回落语义，不要求所有动画 JSON 的末尾原始数值与静态片段字面相同；覆盖、叠加与 transition 也参与最终求值。AFL 的单 action controller 目前没有这种完整 overlay 系统，本轮不加入。

### tactical / empty 选择与 cancel

- [LocalPlayerReload][T17] 做状态／供弹检查、发出请求并向动画状态机输入 reload；它还暴露 cancel_reload 信号。动画不是弹药扣除的权威本身。
- 固定版本 [默认脚本][S1]、[Glock][S2] 和 [M4A1][S3] 使用“膛内没有弹且弹匣数量不大于零”区分 empty；后两者另选扩容变体。不能简化成“只看弹匣是否为零”。context 会处理开膛待击的膛内查询。[T18][T18]
- 音效侧 `LocalPlayerReload.doReload` 的 noAmmo 判断又按 OPEN_BOLT / 其他 bolt 分类；不要混淆它与 Lua 的动画选择条件。本轮不移植任一规则。
- `INPUT_CANCEL_RELOAD` 是协议／状态输入，不表示所有脚本都必然有完整取消动画。扫描本地脚本发现 M870、M1014、Kar98、SPAS-12 有显式 cancel → reload_end 处理；默认、Glock、M4A1 脚本没有该显式分支。切枪另经 put-away 生命周期，不应混写。[S5][S5] [T17][T17] [B1][B1]

未来 AFL Empty Reload 应是独立动作语义和独立片段，由自己的服务端供弹状态选择；手臂 renderer 不应知道“空仓要换一种几何”。本轮不实现真实 ammo、slide lock 或 empty reload。

## 8. H1–H6 verdict

| 假设 | 判断 | 证据与边界 |
|---|---|---|
| H1 hand-pos 是程序替换 locator | SUPPORTED | T2 的注册 + T6 的替代分支；节点仍参与 transform，不是额外可见 arm mesh |
| H2 标准 cube 主要供预览 | SUPPORTED | W1；T6 非空 functional renderer 不绘制原 cubes/children；T5 转到当前玩家 arm |
| H3 自然效果主要来自 rig / animated groups，而非不断调 scale | PARTIAL | T3/T4 无逐状态缩短／scale 算法；三种官方层级支持作者驱动。但 T11 有程序 root、camera、约束，且不能从代码量证明主观美感的唯一原因 |
| H4 右手稳定来自共同 parent / 不变局部姿态 | PARTIAL | M4A1 和 M700 tactical 支持；Glock Reload、M700 empty/bolt 有独立右手轨道；共享 parent 不等于强制相对矩阵恒定 |
| H5 左手换弹应由动画 locator 驱动 | SUPPORTED | 三个样本的 lefthand／弹匣共同父轨道 + T8/T15/T16。AFL 已在这样做，应保留 |
| H6 当前补丁意味着架构方向需要调整 | PARTIAL | 用户已否决短块／薄面，预览与 runtime contract 分裂值得重构；但 AFL 已有正确 anchors 与隔离矩阵，常量 scale／固定 basis adapter 本身并非错误，也没有当前 per-state scale 曲线可删除 |

## 9. TaCZ vs AFL

| Concern | TaCZ Approach | Current AFL | Problem | Recommended AFL Direction |
|---|---|---|---|---|
| first-person render ownership | SBM IFP 接口接管 | Native 专用 Forge hand event | 并非所有权缺失 | 保留 AFL 自己的单一入口，独立于 SBM/TaCZ |
| vanilla hand cancel | 主手 custom 后 cancel；接口控制副手 | Native 主手时 cancel 双 pass | 已存在，无需换一套全局 mixin | 保留局部范围，回归普通物品与 TaCZ 切换 |
| player skin | PlayerRenderer helper 取玩家皮肤 | 当前 skin + arm ModelPart；Reload patch 取局部 UV | 皮肤来源正确，Reload 表示不一致 | 完整 player arm 的统一 adapter |
| Classic / Slim | 实际 PlayerRenderer 选择 | 已区分 4/3 宽 | 仅改宽度不能保证接触一致 | 一份 canonical contact + 两种确定性绑定 |
| arm geometry | 完整 Vanilla 12 高 | 正常完整，Reload 右手薄面 | 状态切换更换表示 | 统一完整原版几何 |
| sleeve | Vanilla sleeve 外层 | 正常可见设置，Reload 右袖不画 | 截断并不能解决 rig | 与完整 arm 共用绑定，尊重外层设置 |
| hand locator | 固定 hand-pos 约定，外层控制组动画 | animated anchors 已用，reference 子组另有变换 | 缺少可验证的 preview/runtime 等价 | 保留名字，定义完整坐标契约 |
| bone hierarchy | 共同 gun/right、mag/left 控制组 | weapon_root 共同载体，左手/弹匣分别有轨道 | 可以成立，但多个轨道要协调 | 最小保留；确有需要再加 AFL-owned 控制组 |
| right-hand grip | 共同运动 + 作者协调局部动画 | 正常 arm 与薄面两套 binding | 不同状态换接触定义 | 单一不变 binding，当前通用 Reload 不加右手独立轨道 |
| left-hand reload | lefthand/祖先被动画驱动 | left_hand_anchor 已驱动 | 不是缺失路径 | 保留已做动作；校验同一 pose sample |
| gun root | 动画 root + 明确程序运动层 | weapon_root + Presentation | 数值叠加复杂，但现已冻结 | 本次研究不动；迁移先保留共同层 |
| reload animation | 同一模型/控制器求值 | 同一 action controller | 手部渲染特例打破表示一致性 | 动画决定姿态，renderer 不选手部形状 |
| tactical reload | 脚本动作选择 | 只有通用 reload | 原型缺项，不是本轮 bug | 后续供弹设计时再拆 |
| empty reload | 膛/弹匣状态 + 独立片段 | 未实现 | 不应混入手部修复 | 保留未来独立动作入口 |
| shoot overlay | base/main/kick 等轨道 | fire/reload 单 controller 互斥 | 当前原型不需要整套状态机 | 先保留，未来添加明确 mask/叠加规则 |
| matrix ownership | 保存 evaluated node，委托绘制并清理 | 已 detachedCopy + restore | 不应把失败都归为泄漏 | 保留隔离，测试 frame-to-frame 不漂移 |
| camera-space transform | camera locator inverse / FOV / root 层 | base/display/presentation 分层 | 不同取景下同一 rig 仍可能不好看 | 独立构图验收，禁止靠改 palm 去抵消 camera |
| state transition | 实例 draw/put-away + 多轨 entry/update/transition | server session + 一次动作与停止 | 不够完整但非本轮范围 | 动画状态决定 clip，不决定 arm mesh |

## 10. AFL First-Person Hand Architecture V1（建议，未实现）

### 10.1 一份绑定契约，而不是零变换

继续使用 `right_hand_anchor` / `left_hand_anchor`，把它们的语义确定为 **runtime player-arm locator**。推荐 AFL 采用“手端／掌面接触点为 locator 原点、局部某固定轴向前臂延伸”的 canonical convention；具体轴向由一份契约规定，不使用每把枪硬编码 forearm quaternion。

Runtime 只需要一个跨状态、跨武器稳定的 `B_skin`：从 Vanilla Classic/Slim arm 的 baked local 坐标映射到 AFL canonical hand frame。固定单位换算、轴变换和手端原点平移 **仍然需要**，但它们是适配器定义，不是每次视觉失败新增的补丁。不得直接把 TaCZ 的 Z180 和 Wiki 偏移套入 GeckoLib。

### 10.2 Preview 与 runtime 同源

- 在 `src/main/blockbench/` 保留完整 reference arms，Classic 4×12×4、Slim 3×12×4；仅用于预览，不占 runtime gun geometry/texture。
- reference group/cube 保持 `export=false`；locator 本身必须 export，动画也必须保留。导出器显式验证，而非仅靠命名习惯。
- 预览姿态由同一 canonical binding 生成或校验。现有 reference 子组的额外 pose 不能一边丢弃、一边在 Java 手填近似值。
- 必须先证明“静态 preview 的手端／角点”和“同一 bind pose 经 Vanilla adapter 的角点”一致；Classic/Slim 各自比较，不拿 Classic 预览证明 Slim。
- 若需要在新契约下重标定 source reference / anchor，则应下一轮明确授权并单独保留原 V0.3.8 源文件。不是在本轮暗改已认可枪模。

### 10.3 Runtime arm geometry 与 scale

推荐完整 Vanilla PlayerModel arm + sleeve，统一 READY/FIRE/RELOAD 路径，不再使用短块或 thin grip surface。可保留“实际皮肤 renderer 选型 + 隔离的 arm/sleeve ModelPart 渲染”的 AFL 自有适配方式，以避免为了清除姿态而复制 TaCZ 的全局 mixin。

若采用 PlayerRenderer 的公开左右手 helper，需要额外明确 Forge hand hook、默认 pose 清理与共享模型恢复；单纯换一行调用并不保证所见即所得。不要从 TaCZ helper 直接继承或反射。

尺度优先采用 **一个经过 authoring/runtime 同时验证的模型单位体系**。当前 0.30 不能立即改为 1；下一轮先分清 viewmodel 整体 scale、原版 model-unit/16 换算和手臂自身风格 scale。若产品确需独立 arm visual scale，可以有一个固定、预览可复现的参数；禁止再以 Reload 状态换 scale 或用去缩放+重缩放掩盖不一致。V1 目标是不需要逐状态尺度修正，单一常量是否保留由等价校验决定。

### 10.4 Matrix ownership

用列向量表示概念关系（不是 TaCZ 实现代码）：

```text
M_weapon = M_camera_presentation × M_common_carrier(t) × M_gun_bind
M_locator = M_camera_presentation × M_common_carrier(t) × M_locator_local(t)
M_arm = M_locator × B_skin
```

单位换算与确定的 viewmodel scale 只能在约定位置应用一次。B_skin 若含固定独立风格 scale，preview 必须相同；不能悄悄从 M_locator 删去父缩放，再由另一状态补回来。

枪和两只手共用同一求值时刻，但每个绘制分支持有自己的 PoseStack/normal 快照。状态结束从 bind/rest 重建结果，不累加上一帧。buffer 材质切换与冲刷由 AFL 自己负责，不为了显示掌面关闭正常深度测试。

### 10.5 READY / FIRE / RELOAD

- **READY**：两个完整手臂由 locator 决定位置与方向，主手主握、左手支撑的不对称性属于 rig。前臂自然延伸出视野是构图约束，不靠截成手块实现。完整几何不保证自动好看，仍需实机看接触和屏占比。
- **FIRE**：共同 weapon_root 后坐带着 gun 和两个 locator；slide/barrel 等局部轨道独立。V1 第一小步保留当前 fire clip 和动作互斥，不先移植 additive 状态机。
- **通用 RELOAD**：保持右 locator 相对 grip carrier 的 bind 不变，左 locator 由现有 Reload 动画驱动。只要 gun 局部 bind 不变，现有 weapon_root 下的兄弟层级就足够；**无需为了像 TaCZ 而重命名／重排整棵树**。
- 若后续 gun 自己也要相对共同 root 转动，右 locator 应随握把 carrier，或由作者同步局部轨道。以 `inverse(M_grip) × M_right` 在要求握持的区间恒定为验收约束，而不是依赖组名。
- 左手与 magazine / reload_magazine 在接取区间要同帧对齐。可先保留分开的轨道，不强制运行时 reparent；将来必要时用原创共同操作组减少重复关键帧，不能直接照搬 TaCZ hierarchy。
- 完成／取消动作只恢复 Ready pose，不再更换右手 geometry。`P901ReloadGrip` 应在替代路线通过验收后删除，不保留为另一套默认模式。

### 10.6 Future empty reload / overlay

未来以 AFL-owned `ReloadKind` 和服务端真实膛／弹匣状态选择独立 clip，预留 slide-lock/bolt 操作但不在本轮实现。稳定握持的约束只覆盖应握持的动作区间，不能阻止未来右手拉栓。

未来若加入 shoot overlay，明确 base/action/additive 职责和 bone mask；不要把绝对 Ready 手部 pose 叠加两遍。无需为了借鉴这些原则加入 TaCZ/SBM/Lua 依赖；现有 GeckoLib 能继续作为求值和渲染基础。

## 11. 最小迁移计划与类处置（下一轮才实施）

| 顺序 | 最小交付 / 验收门槛 |
|---|---|
| 0 | 用户确认新架构范围：允许替换失败的 Reload 右手表示，并允许最小 source reference/locator 契约修正；保留枪 geometry/texture、音效、玩法。不把本研究当作实施授权 |
| 1 | 明确 AFL canonical hand frame 与单位；只做 Classic/Slim preview/runtime 角点、手端和 sleeve 的数值等价测试。未等价不得调 camera |
| 2 | 统一完整 arm adapter，先验收 READY 单右手再双手；删除“为每把枪猜 forearm 朝向”的职责，保留需要的确定性 skin adapter |
| 3 | 同一 adapter 验收 FIRE 和现有通用 Reload：枪和右手共动、左手/弹匣同帧、结束回位；不重做供弹/音效。冻结的 gun presentation 若使完整手臂无法满足视野约束，报告并另求授权，不回到薄面补救 |
| 4 | 反复动作、切槽/丢枪、Classic/Slim、袖层开关、主要 FOV/窗口比例、普通物品、第三人称/GUI 回归；数值结果与用户视觉结论分开记录 |
| 5 | 只有替代方案通过后才移除旧 ReloadGrip 与相关专用测试；同步直接相关文档，再另开 ammo/empty reload/overlay 范围 |

类的建议处置：

- `P901ReloadGrip`：**计划废弃/删除**。短掌段也不恢复；本轮没有删除。
- `P901HandLayer`：重写为统一 locator → skin-arm adapter，去除 per-state geometry branch、武器专用朝向和重复姿态补偿。保留当前玩家/皮肤/context 范围判断与状态恢复经验。
- `P901RenderMatrices`：保留并扩充测试；已有隔离不是问题来源。
- `P901FirstPerson`：保留 Native 局部所有权；本轮不引入 SBM 的切枪管理器。
- `P901Renderer` / `P901Presentation`：最小迁移先保留共同求值与当前枪取景，不以修手为由改枪。以后是否把 presentation 收敛进 authoring view 要独立决策。
- `P901AnimationController` / `P901Actions` / `P901Item`：最小 V1 保留动作与服务端逻辑。未来才讨论 clips/overlay/empty 状态。
- source references、导出验证和 DEV hand tests：下一轮改测 preview/runtime 等价与握持约束；历史“薄表面射线可见”不能成为新架构的通过标准。

## 12. Explicitly NOT copied / verification boundary

- 未复制 TaCZ Java 实现、Lua 脚本、模型几何、UV、贴图、音效、动画关键帧或 pivot。本文只保留许可的类/节点语义、部分父子关系和公开文档坐标约定的解释。
- 公开源码仅在线／内存阅读，本地 JAR 只读；没有把 TaCZ/SBM 类 vendoring、extends、反射接入或新增 native imports。
- 本轮只新增本文并向 `native-afl-gun-framework-v0.md` 追加研究摘要。生产代码、DEV 代码、bbmodel、runtime JSON、音效和依赖均不修改；既存 dirty changes 不属于本轮新增。
- 不启动或操作图形客户端，不编译运行不相关任务，不截图、不生成 preview。研究结论不是实现验证。
- 目标是 Native 模块未来不依赖 TaCZ 也能工作；**当前整个 AFL 工程仍有历史 TaCZ 依赖和非 Native 集成**，本轮没有移除，也未执行移除 JAR 的启动验证。不能把“native 新增 imports=0”表述为整仓库已脱离 TaCZ。
- 收尾检查：`src/` 下 1,081 个文件的 SHA-256 与本轮起始快照全部一致，无新增或删除；`git diff --check` 通过。文档本地链接另行核对。不复用上一轮 build/客户端 PASS。

## 13. 研究状态

```text
AUDITED = YES
TACZ_LOCAL_1_1_8_HOTFIX_SOURCE_FOUND = NO
TACZ_LOCAL_1_1_8_HOTFIX_BINARY_INSPECTED = YES
TACZ_PUBLIC_GITHUB_SOURCE_INSPECTED = YES
TACZ_OFFICIAL_WIKI_INSPECTED = YES

FIRST_PERSON_RENDER_CALL_CHAIN_UNDERSTOOD = YES
LEFT_HAND_RENDER_IMPLEMENTATION_FOUND = YES
RIGHT_HAND_RENDER_IMPLEMENTATION_FOUND = YES
PLAYER_SKIN_PATH_UNDERSTOOD = YES
CLASSIC_SLIM_HANDLING_UNDERSTOOD = YES
HAND_POS_IS_RUNTIME_LOCATOR = YES
PREVIEW_ARM_IS_NON_RUNTIME_REFERENCE = YES
HAND_LOCATOR_IS_ANIMATED = YES
RIGHT_HAND_RIGID_FOLLOW_MECHANISM_UNDERSTOOD = YES
LEFT_HAND_RELOAD_MECHANISM_UNDERSTOOD = YES
TACTICAL_RELOAD_ARCHITECTURE_UNDERSTOOD = YES
EMPTY_RELOAD_ARCHITECTURE_UNDERSTOOD = YES

H1 = SUPPORTED
H2 = SUPPORTED
H3 = PARTIAL
H4 = PARTIAL
H5 = SUPPORTED
H6 = PARTIAL
RECOMMENDED_DIRECTION = REFACTOR_TO_LOCATOR_ARCHITECTURE
CURRENT_RELOAD_SHORT_HAND_SHOULD_SURVIVE = NO
CURRENT_THIN_GRIP_SURFACE_SHOULD_SURVIVE = NO
CURRENT_PER_STATE_ARM_SCALE_SHOULD_SURVIVE = NO

AFL_NATIVE_TAcz_IMPORTS_ADDED = 0
TACZ_CODE_COPIED = NO
TACZ_ASSETS_COPIED = NO
TACZ_RUNTIME_DEPENDENCY_ADDED = NO
PRODUCTION_CODE_CHANGED = NO
SCREENSHOTS_CREATED = NO
PREVIEW_FILES_CREATED = NO
DOCS_UPDATED = YES
COMMIT = NO
PUSH = NO
```

`HAND_LOCATOR_IS_ANIMATED` 包括父节点动画间接驱动；`RIGID_FOLLOW...UNDERSTOOD` 不表示官方所有动作都严格刚性；`PER_STATE_ARM_SCALE...NO` 是设计禁用项，当前 scale 已是常量，固定单一参数是否保留见 10.3。

[W1]: https://tacwiki.mcma.club/zh/gunpack/gun/04_hand_pos.html
[W2]: https://tacwiki.mcma.club/gunpack/animation/
[W3]: https://tacwiki.mcma.club/zh/gunpack/gun/display/02_animation.html
[W4]: https://tacwiki.mcma.club/zh/gunpack/gun/animation/state_machine_script
[LICENSE]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/readme.md
[T1]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/event/FirstPersonRenderEvent.java#L23
[T2]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/BedrockGunModel.java#L76
[T3]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/functional/LeftHandRender.java#L22
[T4]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/functional/RightHandRender.java#L22
[T5]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/util/RenderHelper.java#L73
[T6]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/FunctionalBedrockPart.java#L47
[T7]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/bedrock/BedrockModel.java#L280
[T8]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/BedrockAnimatedModel.java#L71
[T9]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java#L163
[T10]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java#L46
[T11]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java#L119
[T12]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/mixin/client/ItemInHandRendererMixin.java#L34
[T13]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/event/CameraSetupEvent.java#L78
[T14]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/mixin/client/PlayerModelMixin.java#L32
[T15]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/api/client/animation/statemachine/AnimationStateMachine.java#L52
[T16]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/api/client/animation/AnimationController.java#L127
[T17]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerReload.java#L53
[T18]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java#L81
[S1]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/default_state_machine.lua#L72
[S2]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/glock_17_state_machine.lua#L25
[S3]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/m4a1_state_machine.lua#L28
[S4]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/manual_action_state_machine.lua#L16
[S5]: https://github.com/MCModderAnchor/TACZ/blob/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/scripts/m870_state_machine.lua#L83
[B1]: https://github.com/MCModderAnchor/SimpleBedrockModel/blob/0a4a4084eca9e5f055d63fdd2fee37e284dcfb63/src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java#L175
[B2]: https://github.com/MCModderAnchor/SimpleBedrockModel/blob/0a4a4084eca9e5f055d63fdd2fee37e284dcfb63/src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/mixin/client/ItemInHandRendererMixin.java#L21
[B3]: https://github.com/MCModderAnchor/SimpleBedrockModel/blob/0a4a4084eca9e5f055d63fdd2fee37e284dcfb63/src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/mixin/client/PlayerModelMixin.java#L30
[BB]: https://github.com/JannisX11/blockbench/blob/47e633e4a1338f957ee7baa0acbcf54da11e77df/js/formats/bedrock/bedrock.js#L886
