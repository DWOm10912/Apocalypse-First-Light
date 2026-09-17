# AFL Native Weapon Integration Playbook V1

> 适用项目：Apocalypse: First Light / Minecraft 1.20.1 / Forge 47.4.22 / Java 17  
> Mod ID：`apocalypse_firstlight`；Java 包：`com.antaurora.apofirstlight`  
> 审查基线：2026-09-17 当前工作树。本文只描述当前代码与正式运行时资源；历史文档仅用于解释沿革，不覆盖代码事实。

## 1. Scope and Definitions

**AFL Native Gun** 是实现 `weapon/NativeGunItem.java`、由 `P901Actions` 执行服务端权威射击/换弹动作、由 `NativeGunData` 提供枪械定义，并使用 AFL 自有 GeckoLib 渲染、ADS、后坐、Noise、耳鸣、附件和维护台链路的物品。它不是 TaCZ 枪，也不是普通 `Item` 加一张模型。

“新增已存在类型武器”指：射击模式、供弹语义、动作状态和附件槽位均已由当前公共链支持，只新增一套 ID、资产、声音、数据和每枪视觉校准。例如下一把**弹匣供弹的半自动步枪**可复用 BR51-01 路线。

以下情况已经是新机制，不能机械复制本流程：全自动/点射（loader 当前只接受 `semi`）、栓动/泵动/逐发装填、独立膛内弹、霰弹多弹丸、充能/过热、双持、镜内 PIP、耐久/卡壳。它们必须先设计并扩展公共状态机、数据 schema 与测试。

状态术语：

- `KNOWN-GOOD`：当前正式代码与资源链完整，并有项目采用证据；不等于本轮重新做过图形实机验收。
- `PARTIAL`：实现已存在，但仍有明确人工 QA 或泛化缺口。
- `STALE`：旧文档陈述已被当前代码/资源取代。
- `UNKNOWN`：当前仓库没有足够证据作结论。

## 2. Known-Good Weapon Implementation Matrix

当前正式 Native Gun 只有两把；没有成熟 SMG、突击步枪、栓动步枪或霰弹枪模板。

| 项目 | `apocalypse_firstlight:p9_01` | `apocalypse_firstlight:br51_01` |
|---|---|---|
| 类型 | 半自动制式手枪 | 半自动战斗步枪 |
| 注册/生产 Item | YES；专用 `P901Item` | YES；通用 `ConfiguredNativeGunItem` |
| 服务端开火 | YES；`P901Input → AflNetwork → P901Actions → NativeGunShot` | YES；同一公共链 |
| Ammo/Magazine | 9×19mm，17 发；可装 24 发扩容匣 | 7.62×51mm，20 发；可装 35 发扩容匣 |
| Tactical / Empty reload | YES / YES；P9 空仓提交有 2 tick 专用提前量 | YES / YES；按动画长度锁定 |
| ADS | YES；专用 pistol profile；机械/红点 | YES；rifle profile；机械/红点 |
| Recoil / stance spread | YES；`default` 精度 profile | YES；`battle_rifle` 精度 profile |
| Noise | YES；裸枪 64 | YES；裸枪 112 |
| Tinnitus | 路径存在，但本枪 JSON 为 `false` | YES；本枪 JSON 为 `true` |
| Suppressor | 手枪消音器；声音与半径均接入 | 步枪消音器；声音与半径均接入 |
| Maintenance profile | 专用 `.55` profile | `DEFAULT` `.29` profile |
| Attachment slots | SIGHT / MUZZLE / MAGAZINE | SIGHT / MUZZLE / MAGAZINE |
| Animation set | 9 条：含双 inspect 与 empty idle | 8 条：含 bolt-caught idle |
| Sound set | 完整正式事件/OGG；部分立体声听感仍需人工 QA | 完整正式事件/OGG；inspect 复用既有事件 |
| 模板用途 | 手枪、专用作者 rig、P9 特例参考 | 新半自动步枪与通用配置枪首选 |
| 当前结论 | `KNOWN-GOOD / SPECIALIZED / VISUAL-QA-PARTIAL` | `KNOWN-GOOD / PRIMARY / VISUAL-QA-PARTIAL` |

```text
PRIMARY_KNOWN_GOOD_TEMPLATE = apocalypse_firstlight:br51_01
SEMI_AUTO_RIFLE_CLOSEST_TEMPLATE = apocalypse_firstlight:br51_01
OTHER_MATURE_TEMPLATE = apocalypse_firstlight:p9_01 (PISTOL, SPECIALIZED)
```

选择 BR51-01 为主模板的原因不是“步枪看起来接近”，而是它已使用 `ConfiguredNativeGunItem`、通用 `NativeAnimatedWeaponRenderer`、完整数据 JSON、两种换弹、附件三槽、维护台、Noise/耳鸣与正式声音 cue。P9-01 同样完整，但含 `P901Item`、`P901Renderer`、`P901FirstPerson`、专用空仓提交和手臂构图等特例，不应作为默认复制源。

## 3. Native Gun Architecture Map

```text
可编辑资产 src/main/blockbench/<weapon>.bbmodel
  → runtime geo/animation/texture/item model
  → registry/AflItems.java
  → NativeGunItem（优先 ConfiguredNativeGunItem）
  → data/<namespace>/native_guns/<weapon>.json
  → NativeGunData → NativeGunDefinition
  → NativeAnimatedWeaponRenderer / first-person event
  → P901Input → AflNetwork → P901Actions
  → NativeGunAmmo → NativeGunShot
  → NativeStanceAccuracy / NativeGunRecoil / NativeGunAds
  → AflSounds + NativeGunAnimations cues
  → NativeGunNoise → NoiseSystem → InfectedHearingSystem
  → GunshotExposureTracker（仅 definition 开启时）
  → NativeAttachments / MaintenanceAttachmentTransaction
  → AflEquipmentTooltip / AflCreativeTabs / lang
  → build、定向 GameTest、客户端人工验收
```

关键真值源：

- 注册：`src/main/java/com/antaurora/apofirstlight/registry/AflItems.java`
- 通用生产 Item：`weapon/ConfiguredNativeGunItem.java`；接口：`weapon/NativeGunItem.java`
- 战斗/动作锁：`weapon/P901Actions.java`（类名是历史名，实际服务所有 `NativeGunItem`）
- 输入：`weapon/client/P901Input.java`；V 当前是 Inspect，不是配件安装。
- 数据：`src/main/resources/data/apocalypse_firstlight/native_guns/<id>.json`；loader：`weapon/NativeGunData.java`
- 弹药：`weapon/NativeGunAmmo.java`；命中：`weapon/NativeGunShot.java`
- 渲染：`weapon/client/NativeAnimatedWeaponRenderer.java`；P9 特例为 `P901Renderer.java`
- ADS：`weapon/client/NativeGunAds.java`、`NativeAdsProfile.java`
- 后坐：`weapon/client/NativeGunRecoil.java`、`weapon/NativeRecoilState.java`
- 动画 cue：`weapon/NativeGunAnimations.java`
- Noise/耳鸣：`weapon/NativeGunNoise.java`、`noise/NoiseSystem.java`、`tinnitus/GunshotExposureTracker.java`
- 维护台：`client/MaintenanceGunRendering.java`、`MaintenanceViewProfile.java`、`MaintenanceHotspots.java`、`weapon/MaintenanceAttachmentTransaction.java`
- Tooltip：`tooltip/AflEquipmentTooltip.java`、`AflTooltipStatType.java`

## 4. Universal Native Gun Integration Checklist

### 4.1 Registry / Item

- [ ] 在 `AflItems.ITEMS` 注册固定 Registry ID。
- [ ] 默认采用 `ConfiguredNativeGunItem(definitionId, Profile)`，`Properties().stacksTo(1)` 已由类保证。
- [ ] `Profile` 明确 asset ID、idle、全部 clip、循环 clip、`right_hand_anchor`、`left_hand_anchor`、枪口/抛壳 anchor、barrel exit offset。
- [ ] 仅当公共 Item 无法表达真实状态/动画时才建专用类；P9 是特例，不是惯例。
- [ ] 加入 `AflCreativeTabs` 的现有“AFL 武器与弹药”页。

### 4.2 Localization / Tooltip

- [ ] `assets/apocalypse_firstlight/lang/{zh_cn,en_us}.json` 添加 `item.<modid>.<weapon>`。
- [ ] 添加 `tooltip.apocalypse_firstlight.<weapon>.description`。
- [ ] 新口径添加 `item.<modid>.<ammo>.caliber`；否则 Tooltip 回退到弹药物品名。
- [ ] `AflEquipmentTooltip.gunStats` 当前固定显示：基础伤害、弹药口径、**有效容量**、射击模式、`effective_range`、附件修正后的 AI Noise 半径。
- [ ] Tooltip 不显示实时装弹数，也不显示 recoil；颜色由 `AflTooltipStatType` 固定。

### 4.3 Model / Texture / Renderer

- [ ] 编辑源只放 `src/main/blockbench/`；`.bbmodel` 不参与运行时加载。
- [ ] runtime 至少有：`geo/<weapon>.geo.json`、`animations/<weapon>.animation.json`、`textures/item/<weapon>.png`、`models/item/<weapon>.json`、`models/item/<weapon>_in_hand.json`。
- [ ] GUI 推荐继续使用 `forge:separate_transforms`，平面 inventory 图与 `builtin/entity` 手持模型分离。
- [ ] `ConfiguredNativeGunItem` 自动创建 `NativeAnimatedWeaponRenderer(Profile)`；不要另写 renderer，除非有 P9 等已证明的专用需求。
- [ ] 第一/第三人称及左右手 Display 都要显式检查。右/左手 scale 不一致会直接破坏 ADS 对称假设。
- [ ] runtime geometry identifier 当前分别为 `geometry.p9_01`、`geometry.br51_01`。维护台按 ResourceLocation 读取 geo 文件，不按 identifier 查找，但 identifier 仍应与 `<weapon>` 规范一致，避免工具/导出/诊断漂移。
- [ ] `.bbmodel` metadata 不是运行时真值：当前 P9 `model_format=geckolib_model`，BR51 源为 `bedrock`，两者都以导出的 runtime geo/animation 为准。不能把某个 `model_format` 字符串当成通用硬要求。

### 4.4 Bone / Animation contract

通用配置枪必需或条件必需的语义骨骼：

- `right_hand_anchor`、`left_hand_anchor`：第一人称真实玩家手臂。
- Profile 指定的 muzzle/ejection anchors；BR51 为 `muzzle_pos` / `shell`。
- 有枪口附件时，JSON `muzzle_slot.anchor`（当前均为 `muzzle_anchor`）。
- 有瞄具时，JSON `sight_slot.anchor`（当前均为 `sight_anchor`）。
- 有可替换弹匣时，模型必须提供与 `NativeMagazineItem` replacement roots 匹配的子树。
- 建议 `camera`：只在 inspect/reload/draw/put-away 等 `NativeCameraBoneConsumer.supportedAction` 动作中消费旋转；`shoot` 不消费，避免与 Native recoil 双叠。

动画能力表：

| 动画 | 状态 | 说明 |
|---|---|---|
| `static_idle` | REQUIRED | 有弹循环基线 |
| `static_bolt_caught` 或枪型等价空仓基线 | REQUIRED（通用配置枪） | `ConfiguredNativeGunItem` 在 ammo=0 时使用 |
| `shoot` | REQUIRED | 通用配置枪固定名称；controller 强制 `PLAY_ONCE` |
| `reload_tactical` | REQUIRED | 非空未满换弹 |
| `reload_empty` | REQUIRED | 空仓换弹 |
| `draw` / `put_away` | REQUIRED | 切入锁定；切出可见时长受 Vanilla 换槽影响 |
| `inspect` | OPTIONAL | Profile 声明且资源存在才启用 |
| `inspect_empty` | P9-SPECIFIC | P9 按弹量选择；通用配置枪不会自动选择 |
| bolt/pump/action | NOT IMPLEMENTED | 不能只加动画名冒充机制 |

声音 marker 位于 animation JSON 的 `sound_effects`。`P901Actions` 只在 reload/operation 会话消费 cue；开火声由服务端成功射击路径单独播放，`shoot` marker 不会被消费，避免双播。

### 4.5 Combat checklist

- [ ] 左键输入由 `P901Input.attack` 拦截并以 `attackHeld` 做按下沿 gate；按住不会自动连发。
- [ ] `P901Actions` 在服务端校验主手、槽位、动作锁、射速与弹量，成功后先扣弹再执行 hitscan。
- [ ] `fire.interval_ticks` 最低间隔；理论 RPM=`1200 / interval_ticks`。P9 为 400 RPM，BR51 为 300 RPM。
- [ ] 当前只有 `semi`，不是类别 enum；`NativeGunData.parse` 会拒绝其他 mode。
- [ ] hitscan 从服务端眼位与 look vector 出发，使用 `NativeStanceAccuracy` 后的散布，射程为 `max_range`。
- [ ] `effective_range` 目前只用于 Tooltip；真实伤害衰减从 `falloff_start` 线性到 `max_range` 的 `min_damage_multiplier`。
- [ ] 爆头仅对全局配置 allow-list 精确实体 ID 生效；倍率来自 `NativeHeadshots` Forge config，默认 1.5，不在单枪 JSON。
- [ ] reload/fire/inspect 共用 Session 锁；fire/reload 可取消 inspect，但不能穿透 reload/draw 等动作。
- [ ] 当前代码不禁止 sprint 开火；sprint 只禁止 ADS，并通过 accuracy profile 大幅增加散布。

## 5. Semi-Auto Rifle Integration Profile

**TEMPLATE：`apocalypse_firstlight:br51_01`。**

可以直接复用：`ConfiguredNativeGunItem`、`P901Input/P901Actions` 半自动 gate、NBT 弹量、库存备弹、两种换弹、hitscan/falloff/headshot、成功射击回包、recoil、Noise、耳鸣接口、三种附件槽、维护台交易、Tooltip 与通用 renderer。

必须新增：Registry Item；完整 native gun JSON；源/runtime 资产；枪声事件与 OGG；中英名称/描述；GUI/HUD 图；Creative Tab；必要的 ammo/casing/attachment 物品；`NativeAdsProfile.forGun` 条目；`MaintenanceViewProfile.PROFILES` 条目；`AflItems` 中新的 `ConfiguredNativeGunItem.Profile`。

必须重新校准、禁止照抄：

- 第一/第三人称 Display、左右手 transform 与 scale。
- hand/muzzle/ejection/sight/magazine anchor 名和父级。
- `barrelExitOffset`、ADS `ax/ay/az`、HIP `hx/hy/hz`/rotation/scale、eye relief、composition、rootPitch。
- 维护台 scale/translation/rotation/center/click width。
- damage/range/spread/accuracy profile/recoil/fire interval/noise/tinnitus/ADS time/FOV/reload timing。
- 动画长度、cue 时间、声音映射、弹匣 replacement roots。

复用已有口径：JSON 指向已注册 ammo/casing；不重复注册物品。仍需验证容量、备弹消耗、抛壳 item model、Tooltip caliber。

使用新口径：在 `AflItems` 注册 round 与 casing；增加两者 item model/texture 与中英名称、round 的 `.caliber` key；加入武器页；JSON 分别填写 `ammo`/`casing`。当前两种口径没有 ammo tag 或配方引用，不能凭空宣称需要 tag/recipe；若设计要求可制造，再另行添加配方。

## 6. Weapon-Type Delta Profiles

### Pistol

- `TEMPLATE`：`p9_01`。
- `REQUIRED DIFFERENCES`：专用 P9 ADS/hand composition、`P901Item/P901Renderer/P901FirstPerson`、`empty_idle`、有弹/空仓 inspect 分支、P9 空仓换弹提前 2 tick 提交。
- `OPTIONAL DIFFERENCES`：手枪红点、手枪消音器、扩容匣。
- 不要把上述特例搬进新步枪。

### Semi-Auto Rifle / Battle Rifle

- `TEMPLATE`：`br51_01`。
- `REQUIRED DIFFERENCES`：通用 `ConfiguredNativeGunItem`、rifle ADS profile、`static_bolt_caught`、两种 reload、半自动按下沿、步枪级 accuracy/recoil/noise 的独立校准。
- `OPTIONAL DIFFERENCES`：步枪红点、消音器、扩容匣、是否启用 tinnitus。

其余枪型：`UNKNOWN / NOT IMPLEMENTED`。不得从名称或美术资产推导出成熟 Assault Rifle、Bolt-Action、Shotgun 或 SMG 模板。

## 7. Asset Contract

### 模型与贴图

- 源：`src/main/blockbench/<weapon>.bbmodel`；runtime：`assets/apocalypse_firstlight/{geo,animations,textures/item,models/item}/...`。
- runtime geo `description.identifier` 规范为 `geometry.<weapon>`；texture dimensions 必须与导出描述/PNG 一致。
- source texture namespace/path 应指向 `apocalypse_firstlight:item/<weapon>`；所有正式 cube/material face 必须绑定真实 gun texture，不能残留 reference texture UUID。
- 新枪至少提供 gun root/运动层、两手 anchor、muzzle、ejection、需要的 sight/magazine mount。具体父级按实际动画继承关系验证，不能只同名。
- P9 的当前 exporter/verify 脚本是 P9 专用契约；BR51 的迁移脚本也是一次性迁移工具。新枪应新增自己的验证，不应直接运行脚本覆盖资产。

### 动画与音效

- 配置枪正式名称：`static_idle`、`static_bolt_caught`、`shoot`、`reload_tactical`、`reload_empty`、`draw`、`put_away`；`inspect` 可选。
- idle 为 loop；所有 triggerable clip 在 Java controller 中强制 `PLAY_ONCE`。资源自己的错误 loop 值不应依赖，但应清理以免被其他消费者误用。
- reload cue 应拆成 mag-out、mag-in、action/bolt 等可定时事件；当前系统按 marker 逐条播放。不可拆分的大音频无法与弹药提交/中断可靠同步。
- OGG 放 `assets/apocalypse_firstlight/sounds/weapons/<weapon>/`，事件在 `AflSounds` 注册、文件映射在 `sounds.json`；事件 ID 应为 `apocalypse_firstlight:<weapon>_<action>`。

## 8. Gun Data Contract

路径：`src/main/resources/data/apocalypse_firstlight/native_guns/<weapon>.json`。Datapack 以同一路径完整覆盖；不是字段合并。`NativeGunData` 经 `SimpleJsonResourceReloadListener("native_guns")` 校验、原子替换，非法 reload 保留 last-good；首次失败回退打包定义。服务端 snapshot 同步客户端，Item 运行时按 ID 查询。

| 数据 | 分类 | 真值源/备注 |
|---|---|---|
| ammo, casing, capacity | DATA-DRIVEN | JSON；必须是已注册 Item |
| fire mode / interval | DATA-DRIVEN + HARDCODED LIMIT | JSON，但 mode 只允许 `semi` |
| damage/falloff/max/min multiplier | DATA-DRIVEN | JSON；effective range 仅显示 |
| base spread | DATA-DRIVEN | JSON |
| stance multipliers | PROFILE-DRIVEN | `NativeAccuracyProfile.DEFAULT/BATTLE_RIFLE`；JSON 只选名字 |
| recoil | DATA-DRIVEN | JSON → `NativeRecoilProfile` |
| reload/ADS/noise/tinnitus | DATA-DRIVEN | 秒换算为 ceil ticks |
| headshot multiplier/entity list | HARDCODED/CONFIG | `NativeHeadshots` Forge config；默认 1.5 |
| trail | HARDCODED | 当前所有枪均被 loader 指定 `NativeTrailProfile.SUBTLE_PISTOL` |
| HUD size / `magInTick` | HARDCODED SPECIAL CASE | loader 仅用 ID 是否等于 `br51_01` 判断 rifle/non-rifle |
| ADS visual alignment | PROFILE + ASSET | `NativeAdsProfile` 与 Display/几何 |
| animation timing/cues | ASSET-DRIVEN | runtime animation JSON；reload lock仍用 gun data 秒数 |
| mount compatibility | DATA-DRIVEN | JSON `accepts` + 注册附件类型校验 |

## 9. Ammo / Magazine / Reload Contract

```text
definition.ammoType
  → NativeGunAmmo 读取 AflGunAmmo/ammoInMagazine
  → 成功射击 consumeOne
  → reload 开始只校验，不预留弹药
  → mag-in/结束时重新扫描 0..inventory size，按精确 Item ID 消耗
  → 写回当前枪 stack
```

- 新枪 stack 未初始化时 `read` 视为满仓；服务端 `inventoryTick` 将该状态持久化。已有 0 不会补满。
- 没有独立 chamber/+1 状态；“空仓”就是 magazine count=0，并驱动空仓 baseline/empty reload。
- tactical：当前弹量 >0 且未满；empty：当前弹量=0。两者 clip、时长、声音 cue 分开。
- `NativeGunAmmo.transfer` 在提交点重新计算容量和库存；中断前不扣 reserve。
- Creative 的 reserve 为无限，reload 直接填满且不需要库存子弹。
- 空仓左键只播放全局 P9 dry-fire，6 tick 防刷；不触发 shoot、伤害、Noise 或抛壳。
- HUD 当前显示“当前装弹 | 备弹”；普通 Tooltip 仅显示容量。
- 弹匣附件通过 `NativeMagazineItem.capacity()` 改变有效容量；更换小容量弹匣时，维护台事务把超额弹药退回玩家库存/掉落，失败则回滚。

## 10. ADS Integration

入口是 `NativeGunAds.tick/apply/fov`；按住 Vanilla use 键。ADS 只改变客户端视觉矩阵和 FOV，不改变服务端瞄准方向、伤害或散布。

`NativeAdsProfile` 字段含 `anchor, ax/ay/az, eyeRelief, hx/hy/hz, rx/ry/rz, scale, compositionX/Y, rootPitch`：

- `hx..rz/scale` 必须与 `_in_hand.json` 实际 first-person Display 一致，用于构建 HIP 矩阵及其逆。
- `ax/ay/az` 是目标瞄轴坐标；ADS 矩阵为 `T(eyeRelief) × S × T(-anchor/16)`。
- `compositionX/Y` 与 `rootPitch` 是枪型特定构图补偿，最后微调，不是替代错误 anchor。
- **`anchor` 字符串本身没有在 `NativeGunAds.apply()` 中读取 Gecko bone。**机械瞄准使用人工标定的坐标。安装瞄具时字符串用于组成 `<sight_slot.anchor>/reticle_dot` 的语义描述/资源契约，实际 ADS 仍使用 JSON `ads_center` 数值；附件渲染和维护台定位才真正遍历 mount bone。
- `scale` 是 ADS 解算中的模型比例，必须等于实际 Display scale；不是缩放 UI。

禁止机械复制：全部 `a*`、`h*`、rotation、scale、eye relief、composition、rootPitch、sight `mount_offset/ads_center`。推荐顺序：HIP 正确 → 核对左右实际 Display scale → 新建 `forGun` profile → 机械瞄轴粗对齐 → 校准 anchor translation/eye relief → 最后 composition/rootPitch → 验证红点、sprint、recoil、reload/inspect。

Sprint 禁止进入 ADS，已 ADS 时平滑退出，停止 sprint 且仍按住 use 会恢复。draw/equip、reload request、reload/draw/put-away/inspect、使用物品、界面/失焦、第三人称均阻断。P9 左手 Display scale 当前为 .55、profile 为 .45，是已知不对称风险。

## 11. Recoil / Sprint / Handling Integration

- `NativeGunRecoil` 只在服务器确认成功射击后 kick；客户端请求前先发送最新 aim rotation，服务端仍自行取眼位/look/spread/hit。
- `NativeRecoilState` 同时驱动玩家 camera aim 与 viewmodel pitch/back/yaw/roll，按 JSON 时间恢复；ADS 没有另一套 recoil 数值，使用同一 profile。
- stance spread：蹲静/蹲移/站静/走/跑/空中乘以 `NativeAccuracyProfile`，停止移动后有 3/5 tick 恢复惩罚。
- sprint 开火当前允许，但不能 ADS，且 spread multiplier 增大；不要在文档中写成“sprint 禁止开火”。
- reload/draw 动作锁拒绝 fire；fire/reload 可取消 inspect。换槽、死亡、旁观、跨维度、主手 stack 改变会取消 Session。
- put-away 只在切出时触发，旧物品可能先停止渲染，不能保证整段可见。

## 12. Sound / Noise / Tinnitus Integration

### 12.1 Weapon Foley / SFX

- 注册：`registry/AflSounds.java`；映射：`assets/apocalypse_firstlight/sounds.json`；文件：`sounds/weapons/<weapon>/`。
- 成功开火由 `P901Actions` 服务端播放 `item.fireSound()` 或 suppressed event。通用配置枪默认查找 `<profile.id>_fire`。
- dry-fire 是当前公共 P9 事件；无每枪配置。
- reload/draw/put-away/inspect 的声音由 `NativeGunAnimations.cues` 从正式 animation JSON marker 转为服务端 tick cue。
- P9 marker 有兼容映射；其他资产 marker 必须是合法完整 ResourceLocation。

### 12.2 Noise System

`NativeGunShot.execute` 每次成功射击发布 `NoiseType.GUNSHOT`，source ID 为枪 ID。`NativeGunNoise.resolve` 读取有效 MUZZLE 附件：无附件用 JSON `noise.radius`；`NativeSuppressorItem` 倍率 `.05`，最终 `max(1, round(base×.05))`，同时选择 suppressed sound。`NoiseSystem` 转给 `InfectedHearingSystem`，因此“能播枪声”不等于“已发 Noise”。

### 12.3 Tinnitus

只有 JSON `noise.tinnitus=true` 才调用 `GunshotExposureTracker`。真消音状态直接返回 0。当前监听范围 16 格；声学半径 64..160 映射 loudness，单发 scale .55，半径 ≥128/160 分别乘 1.25/1.35，单发 exposure 上限 .75；累积阈值 .35/.60/.90，6 tick impulse 冷却。P9 false；BR51 true。耳鸣与感染者听觉共享“附件修正后的半径”，但使用不同算法。

## 13. Maintenance Table Integration

维护台不是 item Display 的复用。`MaintenanceGunRendering` 直接从 GeckoLib cache 读取 `geo/<id>.geo.json`，复制不可变 bind-pose bones，读取 runtime geo authored rotation，过滤手臂/camera/view/positioning 等骨骼，再以 `textures/item/<id>.png` 渲染。附件随后在相同 bone traversal 中绘制。

每把正式枪必须在 `MaintenanceViewProfile.PROFILES` 建立显式条目；否则落到 BR51 使用的 `DEFAULT`，这只是一项实现回退，不是新枪适配完成。需校准：scale、offset xyz、rotation xyz、center xyz、clickWidth。纵向中心会从 base gun bounds 动态求得，附件不会令枪跳动。

热点：SIGHT/MUZZLE 优先找 `maintenance_sight_anchor` / `maintenance_muzzle_anchor`，否则回退 gun JSON mount bone；MAGAZINE 使用 `magazine_slot.hotspot_anchor/hotspot_y`。`MaintenanceHotspots` 再按固定维护台相机投影。

通用回归规则：

- runtime geo hierarchy/坐标改变后必须重验 profile 与热点；手里正常不代表维护台正常。
- `.bbmodel` metadata 不直接参与维护台；维护台读取 runtime geo 与 texture。源改了但未导出等于维护台没改，反之亦然。
- bind pose 中关键骨骼必须存在且 parent transform 正确；只改名称不够。
- P9 因 artist geo 中心变化曾将 center 改为 `(-.024,.160,.161)`；这说明 center 必须按最终 runtime bounds 重标定。

## 14. Attachment Integration

当前正式槽位只有 `NativeAttachment.Slot.SIGHT/MUZZLE/MAGAZINE`；没有独立 rail accessory 槽。

- 兼容性真值在每枪 JSON 的 `sight_slot/muzzle_slot/magazine_slot.accepts`。
- Item 必须实现匹配的 `NativeAttachment`；loader 在数据校验时检查类型。
- 安装状态保存在枪 stack 的 `AflAttachments` NBT；`active` 会再次验证 slot 与 compatibility，数据 reload 移除兼容后附件停止生效。
- optic mount 由 `NativeSightMount` 的 bone、offset、ADS center 驱动；muzzle mount 由 anchor 驱动；magazine 同时依赖 compatible gun/capacity/replacement roots/hotspot。
- 消音器同时影响 fire sound 与 Noise/tinnitus，不只是模型。
- 唯一正式安装/拆卸流程是维护台 3D 交互：客户端只发 intent，`MaintenanceAttachmentOperation` 延迟 51 tick 后服务端再次校验并原子提交；Creative 也消耗真实附件。
- V 键当前是 Inspect。旧 SightExchange packet/fixture 是 inert/legacy，不得写入操作说明。

## 15. Tooltip / Localization / Creative Tab

- Item 名、描述、口径与通用值键都在 `assets/apocalypse_firstlight/lang/{zh_cn,en_us}.json`。
- `AflEquipmentTooltip` 直接读取 live `NativeGunDefinition` 与有效附件，避免手写重复数值；不要继续使用历史 `.spec` 文本作为主 Tooltip 真值。
- `AflCreativeTabs` 的武器页应包含枪、口径弹药、适用附件；是否放 casing 按当前页面规则核对，不要凭假设。
- HUD icon 路径由 loader 约定为 `textures/gui/gun/<id>_hud.png`；当前 loader 对 BR51 与非 BR51 还存在尺寸硬编码，新增枪前必须处理该 caveat，不能只放 PNG。

## 16. Common Failure Modes / Current Caveats

本次定向审查共记录 **10** 个 `STALE/PARTIAL/UNKNOWN` 项：

| ID / 状态 | Symptom | Root Cause | Check | Fix Location（只记录，不在本任务修改） |
|---|---|---|---|---|
| C01 PARTIAL | 代码/构建通过但 ADS、手臂或动作画面仍不可靠 | 历史报告多次明确未完成最终视觉 QA | 第一/三人称、双手、全动作实机矩阵 | 每枪 Display、ADS profile、runtime geo/animation |
| C02 PARTIAL | P9 左主手 ADS 比例/位置异常 | 左手 Display scale=.55，而 ADS profile scale=.45 | 对比 `_in_hand.json` 左右手 | P9 item model/Profile；不能影响 BR51 |
| C03 PARTIAL | 新枪有模型但 ADS 完全不工作 | `NativeAdsProfile.forGun` 仅列 P9/BR51，未知 ID 返回 null | 搜 `forGun` | `NativeAdsProfile.java` |
| C04 PARTIAL | 新步枪 HUD 尺寸、mag-in tick 或 trail 像手枪 | `NativeGunData` 以 ID==`br51_01` 硬编码 rifle presentation，trail 对所有枪固定 `SUBTLE_PISTOL` | 审查 parse 结果 | `NativeGunData.java`/schema 未来泛化 |
| C05 PARTIAL | 新枪 dry-fire 听起来像 P9；无动画枪默认开火声也是 P9 | 公共 fallback 硬编码 P9 sound | 空仓与 `NativeGunItem.fireSound` | `P901Actions` / `NativeGunItem` |
| C06 STALE | 旧文档写 protocol 22/23 或“V 快装配件” | 后续网络协议已到 26，V 已改 Inspect，旧 packet inert | 以 `AflNetwork` 与 `P901Input` 为准 | 旧专项文档，不作为新流程 |
| C07 STALE | 旧 BR51 文档写“无消音器配件”或 Creative 也消耗备弹 | 当前 JSON 已有 rifle suppressor；`NativeGunAmmo` Creative reserve 无限 | JSON/当前 Java | 旧 `br51_01_native_combat_completion.md` 段落 |
| C08 PARTIAL | 维护台显示比例错误或热点错位 | 未添加 profile 时静默回退 DEFAULT；骨架变更会令旧 center/anchor 失效 | 维护台四向/GUI scale/三槽 | `MaintenanceViewProfile`、runtime geo、mount JSON |
| C09 PARTIAL | 动画存在但循环/声音/状态不符 | asset loop、controller 强制 once、服务端 cue 消费是三套规则；shoot marker不消费 | 检查 controller + runtime animation + `sounds.json` | Profile、animation JSON、`NativeGunAnimations` |
| C10 UNKNOWN | 想直接复制栓动、泵动、全自动或霰弹模板 | 当前仓库没有成熟实现；loader 只接受 semi | Known-Good matrix | 先设计公共机制，不得伪装成数据项 |

额外高频检查：missing texture 往往是 runtime texture path/face UUID，而不是 bbmodel 能打开；maintenance 不显示通常是 runtime geo/cache/profile，不是 inventory item model；声音正常但感染者不响应要查 `NativeGunShot → NoiseSystem`；suppressed 音效正常但半径未变要查有效 MUZZLE NBT 与 JSON accepts；容量显示正确但不扣库存要查 `NativeGunAmmo.transfer`；两种 reload 都存在但不分支要查 ammo=0 与 `reloadClip`；附件模型存在但不生效要查注册 Item 类型、JSON accepts 和 mount bone。

## 17. Full Acceptance Test Matrix

### Registration

- [ ] Registry 正常；Creative Tab 正常
- [ ] `zh_cn` / `en_us` 名称、描述、口径正常
- [ ] Tooltip 的 damage/ammo/capacity/mode/range/noise 与 live data 一致

### Rendering

- [ ] 第一/第三人称；右/左主手
- [ ] GUI/ground/fixed；模型/贴图无 missing resource
- [ ] 两个 hand anchor 随动画正常，无巨大手臂/脱手
- [ ] muzzle/ejection/sight/magazine anchors 正确

### Animation

- [ ] idle / empty idle；draw / put-away；shoot；inspect
- [ ] tactical reload / empty reload
- [ ] action/bolt（仅机制真实存在时）
- [ ] 中断后 controller 回到正确 baseline，不循环卡死

### Combat

- [ ] fire mode 正确；半自动按住不自动连发
- [ ] shot interval/RPM；damage/headshot；spread/stance recovery
- [ ] falloff start/max/min multiplier；effective range Tooltip 语义
- [ ] recoil camera/model 与恢复；sprint 可开火但不能 ADS 的当前规则

### Ammo

- [ ] ammo/casing ID；基础/附件容量；reserve
- [ ] 每发只扣 1；空仓只 dry-fire
- [ ] tactical/empty reload 与提交时点
- [ ] 中断不扣/不复制；Creative 无限 reserve
- [ ] 小容量弹匣替换时超额弹药守恒

### ADS

- [ ] HIP 先正确；actual Display scale 与 profile 一致
- [ ] 机械瞄准、optic reticle 对准屏幕中心
- [ ] sprint 退出/恢复；ADS recoil；composition；FOV 恢复
- [ ] reload/inspect/draw/换枪不残留 ADS

### Sound

- [ ] fire / suppressed / dry-fire
- [ ] draw / put-away / mag-out / mag-in / action / inspect
- [ ] marker 只播一次；取消动作后未到 cue 不播放

### AFL Systems

- [ ] `NoiseType.GUNSHOT`；radius；suppressor reduction
- [ ] `InfectedHearingSystem` 接收、感染者吸引边界
- [ ] tinnitus 开关、距离/累积/消音器抑制

### Maintenance / Attachments

- [ ] 维护台 bind pose、scale、rotation、translation、center
- [ ] SIGHT/MUZZLE/MAGAZINE 热点和安装/替换/拆卸
- [ ] optic/suppressor/magazine 实际效果与渲染
- [ ] 满库存返还/掉落实体失败回滚；Creative 同样消耗附件

### Persistence / Edge Cases

- [ ] 保存重进世界；数据 `/reload`；断线重连
- [ ] 换枪、死亡、跨维度、开菜单、reload/inspect 中断
- [ ] 0/1/满仓、无备弹、部分备弹、满库存
- [ ] 不重复消耗/复制；附件 NBT 与 ammo NBT 同时保留

验证声明必须分开：`compileJava/processResources/build`、定向 GameTest、客户端启动、游戏内操作、视觉、听感、多人。前一项不能替代后一项。

## 18. How to Add the Next Semi-Auto Rifle

假设已有 `<weapon>.bbmodel`、`<weapon>.png`、动画和声音：

### Step 0 — Choose Known-Good Template `[COPY]`

复制结构而非数值：以 `br51_01` 的 `ConfiguredNativeGunItem + Profile + JSON + runtime resources` 为模板。不要复制 P9 专用 Item/renderer/空仓提前提交。

### Step 1 — Asset Validation `[VERIFY]`

检查 `src/main/blockbench/<weapon>.bbmodel` 的 texture 引用、geometry identifier、两手/muzzle/ejection/sight/magazine/camera bones、父级与 clip 清单。确认无 reference texture、orphan 必需轨、重复 bone。所有坐标和值都视为新枪数据。

### Step 2 — Runtime Geometry / Renderer `[NEW + CALIBRATE]`

导出 `geo/<weapon>.geo.json`、`animations/<weapon>.animation.json`、`textures/item/<weapon>.png`；创建 `<weapon>.json` separate transforms 与 `<weapon>_in_hand.json`。建立 Profile，填写真实 anchor 与 barrel exit offset。Display、左右手 scale、anchor、offset 不得照抄 BR51。

### Step 3 — Registry / Item / Localization `[NEW]`

在 `AflItems` 注册 `ConfiguredNativeGunItem`（stack=1）；在中英 lang 添加名称和 description。无充分机制理由不建专用 Java 类。

### Step 4 — Gun Data `[NEW + CALIBRATE]`

创建 `data/apocalypse_firstlight/native_guns/<weapon>.json`。填完整 ammo/casing、capacity、semi interval、damage/range/spread/profile、reload、noise/tinnitus、ADS、recoil、mounts/suppressed sound。绝不复制 balance、ADS、reload timing、noise 或 recoil 数值。

### Step 5 — Ammo / Magazine `[COPY or NEW]`

复用口径：引用现有 round/casing 并验证模型/口径键。新口径：注册 round/casing，补 item models/textures/lang/caliber、Creative Tab；当前系统不要求 ammo tag。需要扩容匣时新增 `NativeMagazineItem`、replacement roots、JSON accepts/hotspot。

### Step 6 — Fire / Semi-Auto `[VERIFY]`

不新增第二套射击代码。验证 `P901Input` 按下沿、`P901Actions` cooldown/扣弹、`NativeGunShot` 命中。确认 `fire.mode=semi`，按住不连发，interval 对应目标 RPM。

### Step 7 — Reload `[CALIBRATE + VERIFY]`

提供 `reload_tactical/reload_empty`，使 data 秒数、asset length、cue 与弹药提交一致。验证无备弹拒绝、中断不扣弹、Creative 填满。不要复制 P9 的 2 tick 特例。

### Step 8 — Animation `[NEW + VERIFY]`

Profile 列出全部 clip，仅 idle/empty baseline 在 loops 集合。检查 triggerable `PLAY_ONCE`、最后一发转空仓 baseline、draw lock、inspect 能力与 camera rotation。bolt/action 只有真实机制存在时才能加入。

### Step 9 — Sound `[NEW + VERIFY]`

在 `AflSounds` 注册 `<weapon>_fire`、suppressed 与每个 cue；`sounds.json` 指向 `sounds/weapons/<weapon>/*.ogg`；animation marker 使用正式 ID。开火 marker 不作为服务器枪声第二来源。

### Step 10 — ADS `[NEW + CALIBRATE]`

在 `NativeAdsProfile.forGun` 加 ID。先修 HIP，再量机械瞄轴 `ax/ay/az`、eye relief，最后 composition/rootPitch；填写 optic `mount_offset/ads_center`。验证右/左手、机械/红点、FOV、sprint 与 reload/recoil 交互。任何 BR51 ADS 数值都不可直接复制。

### Step 11 — Recoil / Handling `[CALIBRATE + VERIFY]`

按枪型填 recoil 与 accuracy profile，实测 camera/model 恢复、连续点射、走/跑/蹲/空中散布。当前 ADS 不提供独立 recoil；若需要就是新机制。

### Step 12 — Noise / Tinnitus `[CALIBRATE + VERIFY]`

填裸枪 radius 和 tinnitus bool；确认成功射击发布 GUNSHOT、感染者响应、消音器倍率与 suppressed sound 同时生效。能听到 OGG 不算本步通过。

### Step 13 — Maintenance Table `[NEW + CALIBRATE]`

在 `MaintenanceViewProfile.PROFILES` 添加 ID，校准 scale/offset/rotation/center/clickWidth；验证 runtime bind pose、纹理、三槽热点。不能接受静默 DEFAULT 作为完成。

### Step 14 — Attachments `[NEW/COPY + VERIFY]`

在 gun JSON 声明 accepts 和 mount；确认注册附件类型、模型 renderer、magazine roots。只通过维护台 Begin→51 tick→revalidate→commit；不添加 V 快装。

### Step 15 — Tooltip / Creative Tab `[NEW + VERIFY]`

加入武器页；验证 Tooltip 从 live definition 读取并随弹匣/消音器变化。新增 HUD 图后还必须处理 loader 中 BR51-only HUD 尺寸硬编码，不能只复制文件。

### Step 16 — Acceptance Tests `[VERIFY]`

按第 17 节逐项执行并分别记录构建、服务端、客户端、视觉、听感、多人边界。只有实现链与必要人工 QA 均有证据时，才将新枪加入 Known-Good matrix。

---

本文的当前事实优先级：**当前 Java/JSON/runtime resource > 当前专项文档 > 历史迁移报告 > 外部原资产说明**。发现冲突时先记录 `STALE/PARTIAL/UNKNOWN`，不得在文档审查任务中顺手修改实现。
