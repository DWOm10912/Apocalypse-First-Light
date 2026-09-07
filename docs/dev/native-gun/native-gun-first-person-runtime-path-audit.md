# Native Gun 第一人称运行时路径与无效代码审计

审计日期：2026-09-07。范围：Service Pistol 当前工作树；仅审计，不实施 V0.5。
结论来源：LIVE Java / JSON / bbmodel、全仓库引用搜索、本机依赖与编译产物 javap、现有客户端日志，以及独立只读几何采样。没有新启动客户端，没有截图或视觉通过声明。

## 1. Workspace state

- branch：`master`；HEAD：`7685f61`。
- 已执行 `git status --short`、`git branch --show-current`、`git rev-parse --short HEAD`；使用当前仓库专用 safe.directory。
- 环境：Minecraft 1.20.1 / Forge 47.4.22 / GeckoLib 4.7.4 / Java 17。
- 以下是审计开始前已有的全部脏文件。M / ?? 是 Git 状态，不是本轮新增修改；全部保留。
- 下文源码路径缩写：C = `src/main/java/com/antaurora/apofirstlight/weapon/client/`；W = `src/main/java/com/antaurora/apofirstlight/weapon/`；D = `src/dev/java/com/antaurora/apofirstlight/dev/`；A = `src/main/resources/assets/apocalypse_firstlight/`。行号指本次审计工作树。

```text
M  .obsidian/workspace.json
M  docs/01 - 系统设计/枪械/枪械系统.md
M  docs/03 - 制作清单.md
M  docs/dev/native-gun/afl_weapon_art_standard_v1.md
M  docs/dev/native-gun/native-afl-gun-framework-v0.md
M  src/dev/java/com/antaurora/apofirstlight/dev/NativeGunArmChecks.java
M  src/dev/java/com/antaurora/apofirstlight/dev/NativeGunArmClearance.java
M  src/dev/java/com/antaurora/apofirstlight/dev/NativeGunArmTrace.java
M  src/dev/java/com/antaurora/apofirstlight/dev/NativeGunMatrixChecks.java
M  src/dev/java/com/antaurora/apofirstlight/dev/NativeGunRuntimeSmokeCheck.java
M  src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel
M  src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolFirstPerson.java
M  src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolHandLayer.java
M  src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolPlayerPose.java
M  src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolPresentation.java
M  src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolRenderer.java
M  src/main/resources/assets/apocalypse_firstlight/animations/service_pistol.animation.json
M  src/main/resources/assets/apocalypse_firstlight/geo/service_pistol.geo.json
M  src/main/resources/assets/apocalypse_firstlight/models/item/service_pistol_in_hand.json
M  tools/verify-service-pistol.ps1
?? docs/dev/native-gun/native-gun-hand-locator-authoring-standard.md
?? docs/dev/native-gun/native-gun-tacz-first-person-hand-audit.md
?? src/dev/java/com/antaurora/apofirstlight/dev/LegacyHandMapping.java
?? src/dev/java/com/antaurora/apofirstlight/dev/NativeGunReloadGripChecks.java
?? src/dev/java/com/antaurora/apofirstlight/dev/NativeHandContractChecks.java
?? src/dev/java/com/antaurora/apofirstlight/dev/NativeHandVisualGate.java
?? src/dev/java/com/antaurora/apofirstlight/dev/NativePistolArmPoseChecks.java
?? src/main/blockbench/templates/afl_first_person_player_arm_rig.bbmodel
?? src/main/java/com/antaurora/apofirstlight/weapon/client/NativeGunRig.java
?? src/main/java/com/antaurora/apofirstlight/weapon/client/NativeHandBinding.java
?? src/main/java/com/antaurora/apofirstlight/weapon/client/NativePlayerArmRenderer.java
?? src/main/java/com/antaurora/apofirstlight/weapon/client/ServicePistolReloadGrip.java
```

本轮新增审计文档和 `tools/audit-native-gun-ready.mjs`，只向框架文档末尾追加摘要。没有运行 Gradle、修改生产/DEV Java、模型、动画、贴图、声音、配置或删除旧文件。

### 证据与复核方式

- `rg -n` 搜索上述类的 import、实例化、方法调用、字段和事件注解；对无普通调用者的事件订阅者检查 Forge 自动注册，不能据零引用直接判死。
- 读取 `build.gradle` 的 sourceSets、run mods source 和 jar exclude，以及 `apocalypse_firstlight.mixins.json`；AFL 当前没有 Native Gun hand / PlayerRenderer mixin。
- `javap -c -p` 读取 `build/classes/java/main` 内 Renderer、HandLayer、NativePlayerArmRenderer、SmokeCheck；确认实际编译调用目标与 LIVE 路径一致。历史私有方法虽然仍在 class 常量池，不代表 tick 调用了它们。
- GeckoLib bytecode：`.gradle-user/caches/forge_gradle/deobf_dependencies/software/bernie/geckolib/geckolib-forge-1.20.1/4.7.4_mapped_official_1.20.1/geckolib-forge-1.20.1-4.7.4_mapped_official_1.20.1.jar`。检查 GeoItemRenderer、GeoRenderer、GeoModel、RenderUtils、BakedModelFactory$Builtin。
- Forge/Minecraft bytecode：`build/fg_cache/net/minecraftforge/forge/1.20.1-47.4.22_mapped_official_1.20.1/forge-1.20.1-47.4.22_mapped_official_1.20.1-recomp.jar`。检查 ItemInHandRenderer、ItemRenderer、ItemTransform、ForgeHooksClient、SeparateTransformsModel$Baked。
- 已有 release JAR：`build/libs/apocalypse_firstlight-1.0.0.jar`，2173720 bytes，mtime 2026-09-07 04:42:26。实际 ZIP entries 中 `com/antaurora/apofirstlight/dev/**/*.class` 数量为 **0**；其中 FP right Display 也确为 T[-8.75,-0.25,0] / S[0.5,0.5,0.5]。
- 现有 `run/logs/latest.log`：04:45:59–04:47:11 这次运行，包含数值检查通过、Slim 玩家与第三人称完成事件、正常退出。是历史运行证据，不是本轮实机验收。
- 可重跑只读几何探针：`node tools/audit-native-gun-ready.mjs`。脚本不写文件、不连接客户端、不生成图片；对本次参数设防漂移断言，契约变更后必须重新审计。
- `tools/verify-service-pistol.ps1` 是外部资产检查工具，不是 runtime 自动导出器；有 PrepareAssets 写入开关。本轮不执行写入模式。Runtime 不直接加载 bbmodel。

## 2. Active runtime call chain

### 实际链路

```text
Minecraft ItemInHandRenderer.renderHandsWithItems
  → ForgeHooksClient.renderSpecificFirstPersonHand → RenderHandEvent
  → ServicePistolFirstPerson.renderHands [只接管主手为 ServicePistol 的两次 hand pass]
  → detachedCopy(camera/equip event PoseStack)
  → ServicePistolPresentation.applyBaseline
  → ItemRenderer.renderStatic → getModel / render
  → ForgeHooksClient.handleCameraTransforms
  → SeparateTransformsModel$Baked.applyTransform → in_hand BakedModel / ItemTransform.apply
  → ItemRenderer T(-.5,-.5,-.5)
  → IClientItemExtensions.getCustomRenderer
  → ServicePistolRenderer [GeoItemRenderer subclass]
  → GeoItemRenderer.renderByItem → GeoRenderer.defaultRender / preRender
  → GeoItemRenderer.actuallyRender → GeoModel.handleAnimations
      → per-stack AnimatableManager → AnimationProcessor → ServicePistolAnimationController.process
  → GeoRenderer.actuallyRender → 每个 top-level bone
  → ServicePistolRenderer.renderRecursively [root Reload offset / gun_model_root visual scale]
  → GeoItemRenderer.renderRecursively → GeoRenderer.renderRecursively
      → push / RenderUtils.prepMatrixForBone
      ├→ renderCubesOfBone → renderCube → createVerticesOfQuad → VertexConsumer [枪]
      ├→ applyRenderLayersForBone → ServicePistolHandLayer.renderForBone [anchor 骨骼]
      │    → detachedCopy + translateToPivotPoint
      │    → NativePlayerArmRenderer.render
      │    → canonicalPose + NativeHandBinding.apply
      │    → PlayerRenderer.getModel().right/leftArm.render [真实 arm ModelPart]
      │    → right/leftSleeve.render [开启对应 sleeve 时]
      └→ renderChildBones → 同一递归链 → pop
  → Minecraft MultiBufferSource/RenderType 最终批次提交 GPU
```

| 步骤、方法 | 实际 caller / 注册 | state ownership | matrix ownership | 状态 |
| --- | --- | --- | --- | --- |
| FirstPerson.renderHands，C:20–42 | Forge CLIENT @EventBusSubscriber / @SubscribeEvent | 当前主手 ItemStack、惯用手、equip；不是第二套枪械状态机 | event 栈只读，创建独立副本 | ACTIVE |
| Presentation.applyBaseline，C:23–27 | FirstPerson | Java 每武器固定 camera/equip 数值 | 修改该副本，在 Display 之前 | ACTIVE |
| ItemRenderer.render / ItemTransform.apply | renderStatic，Forge model route | DisplayContext 和 baked resource | push 后应用 Display、中心偏移 | ACTIVE |
| Item.initializeClient，W:59–74 | Forge item client extension 初始化 | 惰性缓存 ServicePistolRenderer | 不直接改矩阵 | ACTIVE |
| GeoItemRenderer.renderByItem / actuallyRender | ItemRenderer custom renderer 分支 | currentItemStack / renderPerspective / instance ID；GeoModel 根据 stack manager 评估动画 | Gecko 内部 scopes | ACTIVE |
| Renderer.renderRecursively，C:33–60 | GeoRenderer 树遍历 | root 额外动作、枪独立 scale 覆盖及 finally 恢复 | push/pop；不能把其变换误认为动画关键帧 | ACTIVE |
| HandLayer.renderForBone，C:23–38 | 构造器 addRenderLayer；GeoRenderer per-bone layer callback | 选择 right/left anchor，FP gate | 独立 locator 副本，补回当前 bone pivot | ACTIVE |
| NativePlayerArmRenderer.render，C:54–86 | HandLayer | 当前玩家、skin renderer、Slim/Classic、invisible、handFilter、sleeve 开关 | 新的 canonical PoseStack，原栈不回写；ModelPart 状态保存/恢复 | ACTIVE |
| NativeHandBinding.apply | NativePlayerArmRenderer | 右/左 + Slim/Classic，非动作状态 | canonicalPose 上的刚性 B_skin 平移 | ACTIVE |
| AnimationController.process | Gecko AnimationProcessor | 原 controller 的 seekTime / tickOffset 生成 reloadSeconds | 本身不绘制；super 驱动 bones | ACTIVE |

### 实际骨骼布局

```text
root
└─ weapon_root
   ├─ gun_model_root [FP runtime 覆盖为 0.8]
   │  └─ gun
   │     ├─ frame / slide / barrel / magazine / trigger / front_sight / rear_sight
   │     ├─ slide → ejection_anchor
   │     └─ muzzle_anchor / sight_anchor / reload_magazine [后者是无 cube guide]
   ├─ right_hand_motion → right_hand_anchor
   └─ left_hand_motion  → left_hand_anchor
```

Geo 为 19 bones / 77 gun cubes。reference groups/cubes 的 export=false，故不进入 geo；手臂不是枪贴图中的灰色 reference cube。

### 输入、动作与第三人称旁路

- AflItems 注册 ServicePistolItem；Item 构造创建 Gecko 缓存、注册 synced animatable 和 `afl_hold` easing。
- ServicePistolInput.attack / tick 经 Forge input/tick 事件，取消原版近战摆手、边沿检测左键/R；经 AflNetwork.ServicePistolC2SPacket 发给服务端。
- AflNetwork.handle 在 server work queue 调 ServicePistolActions.request；校验选中槽位、活体、主手类型与 busy session；给实际 stack 分配 GeckoLibID、同步物品，再 triggerAnim。
- ServicePistolActions 持有 WeakHashMap<ServerPlayer, Session>；Fire 锁 3 tick，Reload 锁 26 tick；声音 tick 8/19。切槽、死亡、维度变更停止具名动画。它不拥有 camera、hand scale 或 arm matrix。
- Item.registerControllers 注册单个 action controller，fire/reload 都是 thenPlay；动画长度分别 0.14s / 1.30s。controller 只是给 presentation 提供原动画时钟，没有第二个视觉 reload 计时器。
- 第三人称由 Item extension.getArmPose 返回 ServicePistolPlayerPose.PISTOL = **原版 CROSSBOW_HOLD**，再由 Vanilla PlayerRenderer/PlayerModel/HumanoidModel 应用；不是自定义 ArmPose enum，不经过 FP HandLayer。
- 非 FP 枪绘制仍走 ServicePistolRenderer，但 gun_model_root=1、gun 局部补偿 ×2。第三人称姿势、GUI 路由、声音本轮全未改。

## 3. Gun render ownership

**枪最终顶点由 GeckoLib GeoRenderer 提交，不是 NativePlayerArmRenderer。**

ServicePistolRenderer 是有生效策略的 wrapper：它注册 hand layer、覆盖 per-bone render 包装；没有自写枪 cube 顶点循环。实际方法为 GeoRenderer.renderCubesOfBone → renderCube → createVerticesOfQuad → VertexConsumer.vertex。GPU draw 批次由 Minecraft buffer/RenderType 提交，不能把 Java wrapper 直接等同 GL draw call。

JSON route 是 `A/models/item/service_pistol.json` 的 `forge:separate_transforms`：
GUI 选择静态 item_layers icon；第一人称使用 base → `service_pistol_in_hand.json` 的 builtin/entity，随后调用 item extension 的 custom renderer。SeparateTransformsModel 外壳的 isCustomRenderer=false 不意味着 pistol 不走 Gecko；applyTransform 返回的是选中子模型。

五层必须分开：

1. Minecraft camera/view/投影、bob/hurt/inertial hand event 上游矩阵；AFL 收到其结果，不在此实现新 FOV。
2. Java BASE translation / yaw / pitch / scale / equip（Presentation.applyBaseline）。
3. 已导出的 item Display：T / rotationXYZ / S；左手镜像 T.x、R.y、R.z。当前 rotation/rightRotation 都为缺省零。
4. ItemRenderer 的 T(-.5,-.5,-.5) + GeoItemRenderer.preRender 的 T(.5,.51,.5)，此路径等价于 Display 之后 T(0,.01,0)。GeoItemRenderer scaleWidth/scaleHeight 默认为1，AFL 未设置 withScale。
5. Renderer root Reload presentation，然后已评估的各 bone 动画和 gun_model_root scale。

**BLOCKBENCH_DISPLAY_ACTIVE_IN_RUNTIME = YES（仅指已导出 FP 数值）；不代表 bbmodel 整体是最终视觉唯一 authority。** 源 FP 两个 context 与资源及现有 JAR 一致；源中的 thirdperson/gui 与资源仍存在历史差异，且 GUI 使用独立静态图标。不能通过重导出全部 Display 误改它们。

## 4. Arm render ownership

**真正调用 arm/sleeve ModelPart.render 的是 NativePlayerArmRenderer.renderPart（C:72–86）。**

- HandLayer 不画手，只获取已动画化的 anchor 栈。
- NativePlayerArmRenderer 从当前 LocalPlayer 获取 PlayerRenderer；按 modelName=="slim" 选择 B_skin；真实 skin 来自 player.getSkinTextureLocation()。
- base arm 使用 entitySolid(skin)，sleeve 使用 entityTranslucent(skin)。袖子关闭只跳过袖子，不跳过 base arm。
- 使用 PlayerRenderer.getModel() 的完整 rightArm/leftArm/rightSleeve/leftSleeve，而非调用 PlayerRenderer.renderRightHand、裁切几何或短掌补丁。
- renderPart 暂存 PartPose、visible、skipDraw、XYZ scale，ZERO pose、scale=1、visible=true、skipDraw=false 后 render，并 finally 恢复。不会把 Vanilla 第三人称姿态带入 FP。
- canonicalPose 对 locator 的三个轴 Gram-Schmidt、保留反射 handedness，丢弃继承缩放/剪切，将轴长写为 .246；**locator 世界/相机空间 translation 原样保留**；normal = 新3×3矩阵 inverse-transpose。
- B_skin = T(-centreX/16,-10/16,0)，centreX：Classic右=-1、左=+1；Slim右=-.5、左=+.5。SCALE=1 是声明/检查值，apply 没有 scale 调用。
- 标准 geometry：Classic 4×12×4；Slim 3×12×4；sleeve 每面额外 .25 模型单位。骨骼 canonical 手端原点、前臂方向 -Y、掌面法线 +Z；runtime 没有另一个 forearm quaternion。
- READY/FIRE/RELOAD **同一条完整 arm 路线**。右手跟随 weapon_root + right_hand_motion + right_hand_anchor；左手动画目前在 left_hand_motion，不是在 left_hand_anchor 本身写局部 key。
- DEV gate 是额外可见性输入：release 默认 predicate=true 会尝试左右两手；dev 数值通过后默认只绘右手。这是 dev/release 行为差异，不应写成生产已经单右手。

## 5. Scale chain

| 来源 / 值 | 分类 | 影响范围、是否状态/武器相关 | V0.5 建议 |
| --- | --- | --- | --- |
| 源资产 gun 既有 ×0.5 物理迁移，围绕(0,8,6)；77 cubes、locators/路径已变 | ACTIVE 资产事实，不是额外 runtime scale call | gun 内容；手臂参考4×12×4未缩 | 保留来源，勿重复缩放 |
| FP Display S(.5,.5,.5)，左右相同 | ACTIVE | 每武器、全状态；枪尺寸与 locator 位置都继承 | 进入唯一 authoring 契约 |
| Java BASE_SCALE=.82 | ACTIVE | 每武器、全状态，Display 前；共同父级 | authoring 迁移后移除重复 authority，不能本轮直删 |
| gun_model_root/RIG=.8 | ACTIVE | 仅 gun 子树；FP每帧覆盖，非FP=1 | 统一由源或配置一方拥有 |
| fire/reload 的 gun_model_root 两个 constant .8 scale keys | ACTIVE 数据，runtime 被相同值覆盖 | 源预览基线；不能称 runtime 连乘 .8×.8 | 清除双处维护，需兼顾 READY无动画 |
| NativePlayerArmRenderer PLAYER_ARM_SCALE=.246 | ACTIVE | 全武器共享、全状态、独立完整手臂 | 不能未经方案直接改1；决定如何在 BB 真正表示它 |
| canonicalPose 正交化 / normalize / 去继承 scale | ACTIVE | 只改 arm basis，保留缩放后的接触点平移 | 明确导出与预览契约，不要在测试中偷偷再补偿 |
| NativeHandBinding.SCALE=1；ModelPart scale复位1 | ACTIVE 刚性绑定/状态复位 | Classic/Slim，全状态 | KEEP |
| Presentation.DISPLAY_SCALE=.3 | ACTIVE，但它是 Reload 平移的除数，不是 pose.scale | Reload+每武器；注释声称 display仍.3已过时 | 重构为明确空间数据，去硬编码旧除数 |
| magazine.scale=0/1 | ACTIVE animation visibility | Reload .52/.60 handoff，通过 afl_hold；不是 arm scale | 保留既有逻辑，下一轮另验 |
| reload_magazine | ACTIVE guide 无 cube | 没有自己的0/1 scale、没有新渲染弹匣类 | 勿误判为第二个绘制器 |
| Renderer.preserveNonFirstPersonSize ×2 | ACTIVE 非FP | 恢复旧TP/ground/fixed大小；手不走此支路 | 不能在FP cleanup里误删 |
| GeoItemRenderer scaleWidth/Height=1 | 默认中性 | 没有额外 withScale 调用 | 保持 |
| ServicePistolReloadGrip.applySurfacePose .30；3.40/2.55×3×.55薄块 | DEAD production / DEV opt-in测量 | 旧Reload特例，默认不会绘制 | 依赖清理后删除 |
| LegacyHandMapping.ARM_VISUAL_SCALE=.30，retainRigidContact | DEV_ONLY historical | 旧完整arm映射测试；默认不执行 | 连旧测试成组移除 |
| NativeGunArmChecks、MatrixChecks、ReloadGripChecks 的 .3、旧相机样本 | DEV_ONLY historical | 未被默认新契约调用的方法 | 删旧基线，保留当前顶点采样工具 |
| NativeHandContractChecks PREVIEW_ARM_STYLE=.246，PREVIEW_BINDING_SCALE=1，.6/.8/1等样本 | DEV_ONLY active tests | 正交化“预览”期望值、变形/反射/独立scale测试 | 保留测试价值，移除视觉门耦合并改成真实源契约 |

当前无 active per-state arm scale；短掌/薄面没有生产调用。

**GUN_ARM_SHARED_SCALE_BRANCH_EXISTS = YES，但需限定语义：**
gun 与 hand locator 仍共同经过 Java .82、Display .5、weapon_root；locator 的位置依赖这些缩放。最终 arm 几何 basis 在分支末端被固定为 .246，不再继承这些轴长；gun_model_root=.8 不在手臂祖先链上。因此“手臂大小完全随枪缩放”和“二者完全无共享变换”都不准确。

READY 当前 gun 轴长=.82×.5×.8=**.328**，locator inherited axis=.82×.5=**.41**，arm 最终轴长=**.246**。这解释了为何同步同一 Display 后，枪/手可见比例及遮挡仍改变。bbmodel 普通 Display 会把 reference 也按 .5 缩放，并不会自动运行 Java .246 正交化。

## 6. Offset / rotation chain

单位约定：bbmodel/geo pivot 与 animation position 是模型单位；提交 PoseStack 通常除16。以下 bone 数据使用 Gecko 已解码 / bbmodel方向（geo JSON 的 X pivot及Rx/Ry符号会转换），不要把 JSON 原符号直接用于 Java。

| 参数 / 来源 | ACTIVE 状态、作用 | per-state / per-weapon / legacy | V0.5 处理 |
| --- | --- | --- | --- |
| upstream camera/bob/hurt/hand inertia | RenderHandEvent incoming matrix；实际用户会变化，未捕获当前帧 | 状态相关，Minecraft authority | KEEP，不复制相机 |
| BASE T(±.51,-.44-.6×equip,-.70)，Ry(∓4°) Rx(-4°) | 全FP；没有额外roll | equip动态 / pistol / 旧presentation | 迁移authoring静态部分，保留明确equip策略 |
| FP右 Display T(-8.75,-.25,0)/16；左 T(-9.25,-.25,0)/16，渲染左context时X镜像 | 已导出且ACTIVE；R=0 | 全状态 / pistol / 用户新值 | KEEP唯一源 |
| ItemRenderer -(.5,.5,.5) / Gecko +(.5,.51,.5) | net T(0,.01,0) 在Display后 | 全状态 / 非武器特例 | 明确坐标转换，勿当握持修正 |
| root 与 weapon_root pivot (0,8,6) | 公共变换中心 | 全状态 / 资产 | KEEP |
| gun_model_root pivot (.1,7.75,9.2)；gun (0,8,6) | 枪独立scale中心 / gun内容 | 全状态 / 资产 | 单一authoring来源 |
| frame(0,8.5,6.7)；slide(0,11,7)；barrel(0,11,-7.9)；trigger(0,8.75,3) | 静态机械基点及其动画 | 资产 / 各状态 | KEEP |
| magazine与reload_magazine pivot(0,8,6.8)，Rx=-22° | 前者实际两cube弹匣，后者guide | Reload路径 / 资产 | KEEP；不要重造左手方案 |
| front_sight(0,12,-6.5)；rear_sight(0,12,9) | sight与slide等位移但为gun直接子级 | Fire / 资产 | KEEP |
| muzzle(0,11,-8.2)；ejection(1.425,11.45,2.9)，parent=slide；sight(0,12.55,-6.5) | 定位空骨骼；没有特效系统调用 | 资产 / muzzle和sight在gun下 | KEEP契约 |
| right_hand_motion pivot(4.265,9.1,6.3)，Rx=-22° | 右手父框架；无独立动作key | 全状态 / 资产 | KEEP可编辑 |
| right_hand_anchor解码pivot(.1,8.42206877,1.46570503)，Rx=-60.5° | 右手固定绑定；global cap≈(.1,6.66047434,2.07167726)，总Rx=-82.5° | 全状态 / 资产 | 需重新明确可见接触面，不是camera补丁 |
| left_hand_motion pivot(-4.995,8,4.9)，Rx=-22° | 真正承载左手Reload position | Reload / 资产 | 本轮不改 |
| left_hand_anchor解码pivot(-.24988004,9.99753260,-.71193481)，R(-70.75142,-42.38461,28.67797) | static local binding，global cap≈(-.24988004,7.74981219,-1.05158423) | 全状态 / 资产 | 本轮不改 |
| B_skin右Classic T(+1,-10,0)/16；右Slim T(+.5,-10,0)/16；左X反号 | 只把原版cube distal cap对齐canonical原点 | 无动作特判、非武器特例 | KEEP |
| Reload Java T(.05,.28,-.25)×w/.3；绕(0,8,6)/16执行Rz(-16w) Ry(+32w) Rx(+8w) | root额外side-open，原动画前叠加 | Reload / pistol / legacy | 收敛到源authoring，保留动作结果后才删除 |
| 非FP T(P) S2 T(-P)，P=(0,8,6)/16 | 物理缩枪迁移的兼容补偿 | context / pistol / compatibility | 禁止误删影响TP |
| LegacyHandMapping contact右(-1.55,.20,.75)/16、左(2.45,-.55,-1.65)/16 | DEV旧映射，没有active palmOffset | 历史右/左特例 | DELETE_LATER成组 |
| Legacy quaternion：-Y→normalize(.24,-.69,.69) / (-.24,-.87,.44)；palmY=8/9.5、centreX=±1/±.5 | DEV旧前臂朝向 | 历史映射 | 当前不要引用回生产 |
| ReloadGrip contact=Rx(+22°)(-4.265,-.1,1.3)+(-2.44,-2,.1)，再/16；Ry(-90°)、S.30 | DEAD生产，DEV旧surface probe仍引用 | Reload/右手/pistol/legacy | 成组清理 |

Java Reload w：seconds<0或>=1.18为0；0–.24为 smooth(s/.24)；.24–.85为1；.85–1.18为1-smooth((s-.85)/.33)，smooth(t)=t²(3−2t)。并不是动画时长被改成1.18；源动画继续到1.30。

关键动画空间（列出所有有动作的骨骼；不是本轮改值）：

| animation/bone | position / rotation / scale（资源向量） |
| --- | --- |
| Fire weapon_root | peak t=.04，position(0,.12,.38)，rotation(-3.2,0,0)；.14回0 |
| Fire slide / front_sight / rear_sight / sight_anchor | Z最大1.6 at .04，.105回0；继承gun scale，不是旧3.2 |
| Fire barrel | Y=-.185，Z最大.175；rotation X=-1.5°，.105回0 |
| Fire trigger | X rotation8° at .012–.07，.105为3°，.14回0 |
| Fire gun_model_root | 常量scale .8 |
| Reload weapon_root | position主要(-.3,-.35,.8)，R(-3,0,-14)；.93 Rx=-3.5；1.23回0 |
| Reload magazine / reload_magazine | 相同position轨道，.52为(1.5,-12.0533901094,4.8698857144)，.93回0；没有新增旋转轨道 |
| Reload magazine scale | 初始1、.52 key=0、.60 key=1、1.3=1，afl_hold处理段边界；guide无此隐藏scale |
| Reload left_hand_motion | .52为(-.9464431551,-18.7344804939,9.2744432129)，1.23回0；没有局部rotation key |
| Reload gun_model_root | 常量scale .8 |

源参考表示与runtime表示存在**有意折叠而非字面一致**：保存bbmodel的right anchor仍为(.1,6.76194,8.48311)、Rx=-43°；reference cube distal cap=(.1,12.76194,4.48311)、额外Rx=-17.5°。runtime把cap offset和旋转折入anchor，reference不导出。D/NativeHandContractChecks:68–91在内存做同样折叠后比较。它不保存bbmodel、不实时导出资源；用户只移动reference cube，普通geo导出忽略reference时不一定能传播到runtime。

## 7. READY arm invisible diagnosis

### 结论与证据等级

**最可能原因：新Display下枪的屏幕尺寸/深度与固定 .246 的右臂分离缩放，当前握把/枪身遮住了大部分Slim右臂；不是旧薄掌绘制器仍在生效。**
这是源码、实际Slim日志与静态几何采样共同支持的推断，尚未对用户消失那一帧做真实GPU/视觉确认。因此：
`READY_ARM_INVISIBLE_CAUSE_FOUND = NO`（未完成实际事件根因确认），而不是没有发现高优先级候选。

已有日志：

- 04:45:59.954：实际加载Display右T[-8.75,-.25,0]/S.5。
- 04:46:00.464：NativeHandContractChecks数值PASS；B1 RIGHT ONLY enabled。
- 04:46:00.498：baked geo/animation/anchors smoke PASS。
- 04:46:12.767：Vanilla pose 960次setupAnim PASS。
- 04:46:18.215/.533：实际PlayerRenderer Post记录skin=slim、mainArm=RIGHT。
- 日志没有用户出问题那帧的 handFilter / isInvisible / per-bone draw / GPU depth 记录；不能用上述PASS当视觉完成。

### 候选排查表

| 候选 | LIVE结果 / 证据 | 判断 |
| --- | --- | --- |
| DEV数值门永久关闭 | gate.begin清空两手；verify成功才passed；SmokeCheck checked在try前置true，失败不重试 | **确实存在风险**，但最新运行日志已PASS，不支持本次右手因此消失 |
| DEV right-only | passed后right=true放行；left始终false，除非命令both | 解释左手隐藏；不能解释右手消失；release默认双手放行 |
| anchor名字/导出丢失 | geo有19bones，right/left anchor命名匹配、motion parent匹配，smoke检查通过 | 未发现 |
| reference export=false | 有意的source-only参考；真实arm由layer绘制 | 正常，不能改成导出灰臂来“修” |
| hand layer没注册 | Renderer constructor:18 addRenderLayer，编译bytecode同样存在 | 未发现 |
| 注册被GUI route吞掉 | separate_transforms FP返回builtin/entity，Item extension创建Renderer | 枪能显示与该路径相符；未发现注册改坏 |
| 非FP / offhand / scope / spectator早退 | entry取消两hand，再只画主pass；layer再次检查FP | 这些入口早退通常连枪一起没了，不符“枪正常” |
| player.isInvisible() | NativePlayerArmRenderer:58直接隐藏双臂，但枪入口没有对应隐藏 | **确实可单独造成枪有手无**；本次日志未记录该状态，不能排除 |
| 皮肤renderer不是PlayerRenderer | C:59会早退；最新实际TP日志及vanilla skin路径支持正常PlayerRenderer | 低优先级，没捕获失败帧实例 |
| sleeve关掉 | options中right/left sleeve均true；即便false仍画base arm | 不能解释完整右臂消失 |
| arm ModelPart原visible=false | renderPart强制true/skipDraw=false/scale1，finally恢复 | 原版模型visible不是直接阻断 |
| 零/退化scale | FP .5 / .82 / .8均非0，canonical .246；右anchor无scale0轨道，magazine=0不是祖先 | READY未发现；通用canonical防退化仍存在 |
| 旧short/thin分支 | main零caller；DEV opt-in仅Bounds采样 | 不控制当前画面 |
| READY局部hand动画错误 | 右anchor/motion静态；通过父weapon_root继承动作 | READY没有额外旧状态分支 |
| 全臂掉出viewport | 静态70°/16:9/equip0模型采样全部位于frustum | 不作为首选结论；bob/equip/真实投影仍可能变化 |
| 枪体遮挡/尺寸关系 | 下表Slim arm与sleeve严重被frame遮挡；最终gun .328 vs arm .246 | **最高优先级候选** |

### 独立几何探针（不是截图、不是视觉PASS）

`tools/audit-native-gun-ready.mjs` 使用当前geo的全部77个cube，按GeoFactory解码X/旋转符号、逐父级pivot/Rz/Ry/Rx，加入已审计Java/Display/Gecko net中心平移。假设READY静止、equip=0、无bob/hurt、垂直FOV70°、aspect16:9。canonical geometry以B_skin后的中心化Classic/Slim上下界构造；sleeve每面inflate .25。相机向表面点发射ray，与变换后的枪cube OBB相交。

每个mesh六面各11×11采样，共726个，边缘重复；242个朝向相机的面采样。不把该比例解释为面积/像素比例。

| 右臂mesh | frustum内点 | 被枪遮挡的全部点 | 朝向相机且被枪遮挡 |
| --- | --- | --- | --- |
| Classic arm | 726/726 | 519/726 | 202/242 |
| Classic sleeve | 726/726 | 420/726 | 181/242 |
| Slim arm | 726/726 | 723/726（全部遮挡者为frame） | 240/242 |
| Slim sleeve | 726/726 | 669/726（全部遮挡者为frame） | 221/242 |

右手cap相机空间≈(0.062305,-0.274729,-0.689545)。Slim arm AABB：
X[.026322,.085442]、Y[-.316685,-.244037]、Z[-.693026,-.502356]。
这不支持静态READY“整条手臂已经跑出屏幕”的猜测，反而支持深度遮挡。

限制：把枪cube视作不透明体；没有纹理alpha、GPU raster、HUD遮挡、arm自身遮挡、当帧投影/状态和其他Mod渲染改写验证。脚本通过只证明可复现数值，不能代替用户视觉判断；本轮不启动客户端修复/试错。

### 为什么现有 numerical PASS 没发现

D/NativeHandContractChecks:246–288 比较的是“源hierarchy计算后**额外正交化并强制 .246**的期望mesh”与同规则实际arm顶点，不是Blockbench窗口实际投影。它没有执行目前default READY深度遮挡检查。relativeGrip（:464）是frame inverse×locator矩阵稳定性，也不证明皮肤在镜头前可见或握持形状成立。旧Clearance/SAT流程没有进入默认verify，而且其硬编码ancestor chain还漏了新gun_model_root，不能直接拿来补充视觉通过证据。

## 8. Production class classification

以下全部位于main，且上述现有release JAR中存在。除ReloadGrip外，不因“helper很多”判dead。

| Class | 当前职责 / caller → callee | Active / 重复职责 | V0.5建议 |
| --- | --- | --- | --- |
| ServicePistolFirstPerson | ForgeRenderHandEvent → Presentation、RenderMatrices、ItemRenderer | ACTIVE；单入口，没有第二个AFL hand入口 | KEEP入口；静态视觉参数后续移交源 |
| ServicePistolRenderer | Item extension → GeoItemRenderer、Model、HandLayer、Presentation、Rig、controller getter | ACTIVE；模型wrapper兼管legacy presentation/nonFP兼容 | REWRITE，分清数据authority，保留Gecko生命周期 |
| ServicePistolHandLayer | Renderer构造注册 → per-bone callback → RenderMatrices、RenderUtils、NativePlayerArmRenderer | ACTIVE；仅adapter，无第二套手绘制 | KEEP，可日后内聚进通用renderer |
| ServicePistolRenderMatrices | FirstPerson/HandLayer → PoseStack复制 | ACTIVE；和DEV copy工具概念重复但不是双绘 | KEEP，未来可通用改名/内聚 |
| ServicePistolPresentation | FirstPerson/Renderer → 基线与Reload额外变换 | ACTIVE；和BB Display/weapon_root承担叠加视觉职责 | REWRITE，逐步源authoritative迁移，不直接删除 |
| ServicePistolReloadGrip | **无main caller**；仅DEV ReloadGripChecks/ArmTrace → 旧surface顶点 | DEAD production；仍打包，但默认不加载绘制 | DEAD；DEV调用一起清理后DELETE_LATER |
| NativeGunRig | Renderer静态RIG实例 → GeoBone.updateScale | ACTIVE；Java .8和source .8重复维护 | KEEP metadata角色，scale authority需收敛 |
| NativeHandBinding | NativePlayerArmRenderer → canonical rigid T | ACTIVE；唯一当前B_skin | KEEP，Classic/Slim不可删 |
| NativePlayerArmRenderer | HandLayer → currentPlayerRenderer/ModelPart、Binding | ACTIVE；唯一真实skin arm/sleeve绘制；DEV可改filter | REWRITE契约/测试隔离；保留完整arm路线 |
| ServicePistolModel | Renderer → GeoModel三个ResourceLocation | ACTIVE；纯asset lookup，无视觉叠加 | KEEP，可并入Rig resource metadata |
| ServicePistolPlayerPose | Item extension引用PISTOL → Vanilla CROSSBOW_HOLD | ACTIVE第三人称；deprecated apply仅dead DEV方法引用 | KEEP字段/类；apply后续删，不删整个类 |
| ServicePistolInput（含Registration） | Forge输入/按键注册/tick → AflNetwork | ACTIVE，输入边沿状态，不改矩阵 | KEEP，本次不重构 |
| ServicePistolItem（含匿名extension） | 注册/物品实例 → Renderer、PlayerPose、AnimationController、Gecko缓存 | ACTIVE，renderer/client extension及动画入口 | KEEP |
| ServicePistolAnimationController | Item注册/Gecko process → super.process；Renderer读取reloadSeconds | ACTIVE，共享action时钟 | KEEP |
| ServicePistolActions（含Session） | packet/server events → synced trigger/stop、sounds | ACTIVE，服务端动作锁/音效；非手臂renderer | KEEP，不借本审计扩玩法 |
| AflNetwork.ServicePistolC2SPacket | Input请求→channel注册→Actions.request | ACTIVE基础设施；不是FP视觉类 | KEEP |
| AflItems.SERVICE_PISTOL | DeferredRegister→ServicePistolItem构造 | ACTIVE注册基础设施 | KEEP |
| AflSounds相关注册 | Actions声音引用 | ACTIVE音频基础设施；不拥有手视觉 | KEEP |

### 数量口径

- C目录共 **12个顶层类**：9个直接FP绘制/变换/asset lookup类 + Input + PlayerPose + dead-production ReloadGrip。
- 直接FP的9个：FirstPerson、Renderer、HandLayer、RenderMatrices、Presentation、Model、NativeGunRig、NativeHandBinding、NativePlayerArmRenderer。
- 加Item、AnimationController，完整AFL item/动画/FP链涉及 **11个顶层类**。外部Minecraft/Forge/Gecko不计入AFL类数。
- 整个W含C共有 **15个顶层类**（再计Actions、Input、PlayerPose、ReloadGrip）；现有JAR含其内部/匿名类共 **19个.class entries**。
- 11个Native Gun DEV顶层类另计，release JAR数量为0。不能把全部DEV探针数量冒充生产renderer数量。

## 9. DEV class classification

build.gradle明示：虽然目录是src/dev/java，仍通过 `sourceSets.main.java.srcDir 'src/dev/java'` 加入main ModFile，供Forge自动event subscriber扫描；jar任务显式exclude dev/**。因此“只在dev source set、不会自动执行”不成立；“发布JAR包含DEV”也不成立。

| Class（D目录） | Source set | In release JAR | Runs in dev client | Alters visual/runtime state | Keep? |
| --- | --- | --- | --- | --- | --- |
| NativeGunRuntimeSmokeCheck | dev目录+main编译 | NO | CLIENT tick自动once；有screen、无overlay时 | 调ContractChecks，间接关/开hand gate及执行缓存骨骼测试；checked失败不重试 | REWRITE：保留资产smoke，移走强制视觉gate；删未调用旧private方法 |
| NativeHandContractChecks | 同上 | NO | 由Smoke自动调用 | YES：begin/passed改生产static filter；临时动画processor更新共享baked bones，循环finally恢复initial；在前置检查失败时并非全函数都有统一restore | KEEP/REWRITE：数值验证与画面门分离；真实preview契约与遮挡分开验 |
| NativeHandVisualGate | 同上 | NO | begin/passed来自自动测试；CLIENT command事件注册right/both | **YES：直接setHandFilter**，默认release全放行→dev通过后right-only；测试失败可留全隐藏 | REWRITE：explicit debug opt-in，不支配默认生产可见性 |
| NativePistolArmPoseChecks | 同上 | NO | 有world/player时自动once；RenderPlayer.Post bounded日志 | 无持久玩家视觉修改；使用RemotePlayer/独立模型做960次检查 | KEEP，回归价值明确 |
| NativeGunArmChecks | 同上 | NO | require/vertices被新Contract调用；legacyMapping/checkGeometry/animatedNearZ默认不执行 | vertices暂改ModelPart后finally恢复，VertexConsumer只收集数据，不画GPU；旧方法不控制视觉 | KEEP当前工具，删除/迁移legacy部分 |
| NativeGunPresentationTrace | 同上 | NO | event已注册，但仅-Dafl.debug.nativeGunPresentation=true才记录 | NO，只读post render状态日志 | KEEP opt-in，当前无默认启用配置 |
| NativeGunArmTrace | 同上 | NO | CompileRenderLayers订阅；仅-Dafl.debug.legacyHandMappingReference=true安装Probe | 不画皮肤：旧surface.render接收的是Bounds而非GPUbuffer；有临时ModelPart采样/恢复 | DEPRECATE/DELETE_LATER，不能把它当新路径探针 |
| LegacyHandMapping | 同上 | NO | 被旧MatrixChecks/Smoke死方法/ArmChecks旧方法/opt-in ArmTrace调用 | 只改探针副本，不是生产mapping | DELETE_LATER成组，不可单删留下Java引用 |
| NativeGunReloadGripChecks | 同上 | NO | 无调用者、无subscriber；不自动运行 | 若手动接回会改共享bone后restore；默认无效 | DEAD，独立安全删除候选 |
| NativeGunMatrixChecks | 同上 | NO | 无调用者、无subscriber；不自动运行 | 若接回会改共享bone后restore；默认无效 | DEAD，旧.60/-.54/-.64及.3断言已不适用 |
| NativeGunArmClearance | 同上 | NO | 仅旧ArmChecks.animatedNearZ / ReloadGripChecks引用，默认都不执行 | 无绘制；SAT/occlusion纯计算，但路径硬编码已旧 | DEPRECATE；通用SAT算法可保留重用，旧wrapper成组清理 |

未发现除此之外的Native Gun/Hand Java DEV调用类（使用全D目录rg复核）。默认auto路径是Smoke→Contract→Gate+ArmChecks的当前工具，以及PistolArmPoseChecks；其他很多文件是历史测试而不是并行生产绘制器。

注意区别：
- DEV alter production visual state = **YES，在开发客户端**，setter位于main而调用者位于dev。
- DEV classes in release JAR = **0**，所以正式包不执行这个测试gate。
- 不应让“源码被测试通过”成为“玩家能看见手”的前置条件；测试工具读取repo bbmodel失败可以无意隐藏dev双手。
- Gate从未begin时release/default predicate=true；失败发生在begin之前与之后结果不同，不能笼统说任何smoke失败都隐藏两手。

## 10. Dead code candidates

经过类级引用、方法级调用与event入口交叉检查：

1. **ServicePistolReloadGrip**：main无任何调用；D/NativeGunReloadGripChecks引用、D/NativeGunArmTrace opt-in Bounds probe引用。生产路径dead，不是全仓库零引用。
2. **NativeGunMatrixChecks.verify / NativeGunReloadGripChecks.verify**：没有外部caller，也没有自动subscriber。可后续单独删这两份DEV文件，不会破坏当前main或默认DEV调用。
3. **NativeGunRuntimeSmokeCheck.checkPresentation / checkReloadController**：private且只声明，没有tick调用；其辅助projectedMagazineTop / checkReadyForearm只在该死方法群内使用。
4. **ServicePistolPlayerPose.apply**：仅上述checkPresentation历史私有方法调用；PISTOL字段实际生产仍用，不能整类删除。
5. **NativeGunArmChecks.checkGeometry**：无外部调用；legacyMapping/checkMapping/animatedNearZ属于旧调用群；require/vertices/Bounds必须保留当前Contract使用。
6. **NativeGunArmClearance**：只供非默认历史群；硬编码root→weapon_root→gun→part漏新gun_model_root，也没通用地遍历parent，旧数值不可拿来验当前姿态。
7. LegacyHandMapping / ArmTrace不是“绝对不可达”：有显式系统属性可开Probe；默认路径不执行，后续删除需要同时移除入口和引用。
8. 本次未发现Reflection/Class.forName或mixin用字符串重新加载ReloadGrip等旧类；构造器、lambda/method refs、event注解都计入检查。包里存在.class本身不能证明绘制生效。

## 11. Runtime overlap / duplicate responsibilities

真正问题不是有两套生产手顶点绘制器，而是**一个绘制器前面存在多个视觉authority**：

- BB Display + Java BASE共同控制camera-relative摆放；BB看到的源视图不是最终Java叠加后的视图。
- BB weapon_root Reload + Java root Reload offset共同控制side-open；硬编码旧.3除数与当前Display .5脱节。在同一方向before-base长度上，现有补偿被实际Display .5/.3=1.6667的比例影响，不是写注释所称“已抵消display”。
- Java RIG .8覆盖source动画constant .8；现在没有双乘，但有两份authoring配置，改一处可能检查失败或runtime覆盖掉新key。
- 手contact位置继承公共缩放，basis随后被固定.246；枪按.328绘制。**解耦尺寸不等于自动校准接触面，也不等于预览等价。**
- 测试侧也实现了一套正交化“preview”修正，PASS不能证明普通Blockbench Display和游戏一样；源cube局部offset折入anchor的规则还藏在DEV检查内，而非统一稳定的导出契约。
- DEV fail-closed gate改变生产Renderer默认filter，开发版本与发布版本可见手数不同。
- nativegun RenderMatrices.detachedCopy 与 DEV ArmChecks.copy 是相似的ownership工具，属于可整理的工具重复，并非画面被画两次。
- 旧surface/legacy手方向并不处于当前默认绘制链；删除它们会减文件量，但**不会单独修复当前遮挡或BB/runtime不一致**。

## 12. Minimum runtime class set

建议V0.5的最小是**5个职责**，不是生硬地要求仅5个Java文件：

1. FirstPersonEntry：只接管指定item与camera context，所有视觉数据来源明确。
2. NativeGunRenderer：Gecko模型/animation生命周期及同一evaluated骨骼空间；hand layer adapter可内聚。
3. NativePlayerArmRenderer：完整Vanilla arm/sleeve、skin、Classic/Slim、可见性与矩阵ownership。
4. NativeHandBinding：唯一canonical→Vanilla B_skin坐标契约。
5. GunDefinition/Rig metadata：资源路径、locator名称和明确单位；authoring应拥有武器视觉基线。

若保留Gecko的GeoModel和GeoRenderLayer独立类型，上述为**7个绘制相关顶层类**；矩阵复制可以作为renderer内部私有工具，或保留共享utility而多一文件。Item/AnimationController/Input/Actions/Network/Registry属于支持链，不是假dead，应另计。不为减少文件数把服务器动作锁与渲染塞进同类。

## 13. V0.5 cleanup recommendations

建议进入**另行授权**的V0.5实施，但先定契约再调姿态：

1. 明确Blockbench是否直接拥有最终gun/arm relative size与Display。若选择authoritative，必须让预览实际表现和runtime采用同一规则，不能继续靠测试重写preview预期值。
2. 明确唯一export步骤：reference cube局部编辑如何折叠成canonical anchor，Classic/Slim B_skin如何保留；不要让只保存bbmodel就被误认为runtime自动更新。
3. 保留单入口/Gecko动画链/完整skin arm/B_skin，集中源数据authority；将Java BASE与Reload offset迁移/合并时先做等价和视觉检查，避免直接删掉造成位移突变。
4. gun .8维持单一来源；公共scale、独立arm basis、contact位置要作为一起审查的契约。当前Slim被frame遮住先列为视觉P1，不预设新缩放值。
5. DEV检查改为不接管默认hand filter，测试失败记录错误不改变画面；release/dev同基线。需要right-only/both时作为明确opt-in工具，不静默启动。
6. 先验单右手READY：Classic和Slim分别可见、握持接触成立、不同Display下不被frame包住；数值等价与可见性各自报告。
7. 当前任务不改Fire/Reload/左手/第三人称；下一轮基线迁移必须回归这些分支，尤其非FP×2兼容与音效时间冻结。
8. 不复制TaCZ代码/资产，不新增TaCZ runtime引用。现有项目TaCZ依赖不是本次清理对象。

V0.5 = PLANNED / NOT IMPLEMENTED。没有发现需要本轮越界紧急修改的P0 crash/compile blocker；未执行新build，因此不声称重新编译通过。

## 14. Files safe to delete later

本轮一个都不删除。

**可独立删除的DEV零caller文件（下一轮再次rg确认）：**

- `src/dev/java/com/antaurora/apofirstlight/dev/NativeGunMatrixChecks.java`
- `src/dev/java/com/antaurora/apofirstlight/dev/NativeGunReloadGripChecks.java`

**可成组清理、不能逐文件盲删：**

- D/NativeGunArmTrace.java及旧debug property入口。
- D/LegacyHandMapping.java。
- D/NativeGunArmClearance.java（或只保留独立通用SAT算法，迁走旧硬编码wrapper）。
- C/ServicePistolReloadGrip.java。
- 同时移除D/NativeGunRuntimeSmokeCheck的旧private方法群、D/NativeGunArmChecks的旧legacy调用方法、C/ServicePistolPlayerPose.apply。
- 必須保留ArmChecks.require/vertices/Bounds或先迁移当前Contract的调用；清理后再build+DEV检查+release jar扫描。否则删除main ReloadGrip会让仍参与main编译的src/dev引用报错。

本轮新增只读audit脚本不参与Forge编译/打包/自动执行，也不拥有任何production状态。

## 15. Files that must not be deleted

- C/ServicePistolFirstPerson.java、ServicePistolRenderer.java、ServicePistolHandLayer.java：当前入口、枪/手接入桥。
- C/NativePlayerArmRenderer.java、NativeHandBinding.java、NativeGunRig.java：当前完整skin手臂与canonical/独立gun契约。
- C/ServicePistolModel.java、ServicePistolRenderMatrices.java：资源定位与独立矩阵所有权。
- C/ServicePistolPresentation.java：**目前仍active**；只能在等价迁移后替换，不是死类。
- C/ServicePistolPlayerPose.java中的PISTOL、W/Item/AnimationController/Actions、C/Input与Network/Registry：第三人称、动画与正常输入支持。
- D/NativeHandContractChecks.java、NativeGunRuntimeSmokeCheck.java、NativeHandVisualGate.java及ArmChecks当前工具：先解除依赖和视觉门职责再整理；不要直接删掉留下调用断裂。
- D/NativePistolArmPoseChecks.java与opt-in NativeGunPresentationTrace：仍有实际回归价值。
- 唯一编辑源 `src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`、arm模板、runtime geo/animation/display资源、纹理/声音/静态图标：不属dead Java清理范围。

本轮状态：审计完成、生产零改动；真实失败帧原因仍待后续实机确认。检查通过、启动成功、动作矩阵稳定、手臂可见/握持自然，是四种不同证据，不能互相替代。

本轮校验：只读脚本执行及Node语法检查成功；git diff --check无whitespace错误（已有LF/CRLF提示不属代码错误）。审计写入前后对src/main、src/dev、tools原有1093个文件逐文件SHA256再汇总，摘要均为zd3Ca9wO+bFmGPigLNaNcUP2eNvhRc4m1w8DaCUhjz4=；新增audit脚本不计入原集合。原生产/DEV源码、资产与旧工具没有被本轮改写。
