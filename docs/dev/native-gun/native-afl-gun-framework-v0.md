# Native AFL Gun Framework — Reload Composition V2 Runtime

> P9 当前动画已由 [P9 动画重做 V1](../../native_guns/p9_01_animation_rebuild_v1.md) 替代：新增 p9_handling、idle 与显示层 draw/put_away，重新创作 fire/reload/reload_empty；普通/空仓时长及装弹/声音时点不变。下文历史 fp_root 换弹轨道、旧左手路径和“无独立拔收枪”说明不再代表当前实现。

> P9 空仓动作又已更新为 [独立特殊换弹](../../native_guns/p9_01_empty_reload_special_rebuild.md)：右手侧转、短促左扫，在左向速度峰值附近释放旧匣，旧匣继承运动继续向左下飞离；左手另取新匣。此前右甩版因遮挡和力度不足被替代。临时旧匣只在第一人称空仓换弹显示，玩法时长及声音时点不变；最新观感待实机确认。

> 当前弹药更新：P9-01 使用 `9x19mm_round`，BR51-01 使用 `762x51mm_round`；各自抛出同口径 `_casing` 新模型。四项均64堆叠，仅两种实弹进入武器与弹药标签；弹壳不展示在创造标签，FX不产生可拾取实体。下文版本历史中的9mm占位、旧路径和“Casing未注册”均已被替代。详见 `docs/native_guns/native_ammo_assets_v1.md`。

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## BR51_01 第二把战斗枪接入（2026-09-08，实机验收待完成）

BR51_01 已从动画测试物品改为 `ConfiguredNativeGunItem implements NativeGunItem`。
共用服务端战斗入口按每枪definition和动画资源配置处理；P9-01 Service Pistol原时长、音效、资源与姿势不变。
BR51_01正式20发7.62×51mm、4tick半自动、18HP；普通/空仓换弹52/57tick结束补弹。
实现范围、切出动画限制与真实验证状态见 [BR51_01战斗补完报告](../../native_guns/br51_01_native_combat_completion.md)。

## Native Gun V0.6.2.1 — 弹壳尺寸 / 横向修正（用户实机验收通过）

用户确认V0.6.2枪焰、落地、弹跳与音效可保留；只修弹壳过大和向左抛出。
NativeGunFx.CASING_SCALE由.24改为.072，即原线性尺寸30%；不改9mm_casing.bbmodel。
direction(matrix,-1,0,0)改为direction(matrix,1,0,0)，仅翻转局部横向基向量。
up、forward随机、速度量级、重力/阻力/翻滚/碰撞/反弹/音效/寿命/上限及枪焰完全不变。
2026-09-07：用户对尺寸、北/南/东/西抛壳及F5回归验收问题回复“没有问题”；
据用户实机反馈记录通过，未扩展为多人双客户端或全部极端视角验收。
本轮compileJava/processResources/build通过（39s）；两个导出--check与git diff --check通过。
逐值比较确认NativeGunFx相对此轮开始仅改scale、lateral符号和对应注释；其它逻辑完全一致。
新版runClient已启动并记录实际射击截图；afl-v062-020-FIRST_PERSON-shot3-frame2.png
可见弹壳位于枪体右上方。截图证明本视角，四向/F5结论依据用户确认。
客户端日志12:55:56确认保存并停止；本轮结束，不改其它参数。

## Native Gun V0.6.2 — Custom Muzzle Flash / Client Casing（枪焰与落地获用户确认）

2026-09-07：clean compileJava通过；processResources/build通过；50/50服务端GameTest回归通过。
clean后的Forge元数据下载曾被sandbox网络阻止，授权重试后通过，未更改依赖。
runClient已启动；新FX第一/第三人称截图与多人观察者验收尚未完成。
旧DEV Ready muzzle ray calibration检查仍失败，不改冻结构图、不伪报全部旧烟测通过。

- V0.6.1成功射击入口不改；sendNativeShot额外发送NativeShotFxS2CPacket(shooterId,gunId)
  到TRACKING_ENTITY_AND_SELF，协议12。原HUD通知仍只发射手，不改HUD逻辑或弹量/伤害/Noise。
  Dry Fire不进入该入口，最后一发仍正常产生FX。
- NativeGunFxLayer复用P901Renderer实际bone遍历矩阵及pivot：
  muzzle_anchor驱动枪焰，ejection_anchor驱动弹壳。只接受手持FP/TP pass，排除GUI/掉落物。
  不另算枪械动画，不动源bbmodel、Geo、Display、Ready/Fire/Reload姿态及空仓状态。
- 枪焰原样使用用户E:/Download/muzzle_flash.png，保存为
  assets/apocalypse_firstlight/textures/effects/p9_01_muzzle_flash.png。
  3个正交双面quad；emissive shader、SRC_ALPHA+ONE加法混合，仅写颜色不写深度。
  lifetime1.0 tick，从首个可见anchor帧开始；alpha在0/.6/1tick为1/.75/0。
  preset尺寸.17（用户追加要求：原.34的50%），随机±10%，轴向roll±12°，alpha .95–1。
  此次仅缩小枪焰显示尺寸，其它FX参数不变；此前视觉通过不代表新尺寸已实机验收。
  RenderType负责恢复状态，不用原版火焰/烟粒子，不创建枪焰Entity。
- 复用src/main/blockbench/9mm_casing.bbmodel现有导出models/item/9mm_casing.json，
  RegisterAdditional加载baked model；不创建Item/Entity，不改变源几何或9mm Round。
  casingVisualScale=.072（V0.6.2.1，原.24），原模型4×8.015×4，显示包围尺寸约.018×.0360675×.018 block。
- NativeGunFx客户端队列最多64颗，满时移除最老，50tick后移除，断开/换世界清空。
  每个通知最多生成一颗；等待真实anchor最多3ticks，未渲染的远处/遮蔽持枪不使用伪造出生点。
  世界变换使用当帧view逆矩阵；第一人称还以hand/world投影转换保持脱离时屏幕位置，
  不用eye+常量。初速按枪械局部轴right .24–.30、up .12–.16、forward ±.04 block/tick，
  三轴独立有符号15–35°/tick；gravity .04、drag .98。
- 客户端block COLLIDER线段检查，撞击点法向外留.07block视觉间距；
  反弹系数.32后整体速度*.65，角速度*.5，地面最多两次反弹后静止。
  首次法向撞击速度>.04时播放shell_casings_dropping，音量.18、pitch .95–1.05，
  资源原样复用E:/Download/shell_casings_dropping.ogg；最多一次主要撞击声，衰减距离12。
- DEV截图入口：-I src/dev/native-gun-v062-validation.init.gradle，
  run/screenshots/afl-v062-*.png；不自动输入或修改存档。多人双客户端结果需单独确认，
  本节实现描述不是视觉通过声明。
- 未实现Camera Recoil、Viewmodel Recoil新系统、Impact FX、ADS或完整Empty Reload。
  下一阶段建议V0.6.3 Recoil / Gun Feel，不是当前功能。

## Native Gun V0.6.1 — 已实现 / 用户实机验收通过

2026-09-07 验证：clean compileJava、processResources、build、导出一致性与 git diff --check 通过；
隔离 GameTest Server 50/50 通过，覆盖服务端命中/遮挡/伤害、冷却、干击无伤害/Noise、
僵尸听觉、衰减与散布，并包含原有回归测试。
图形客户端已启动并记录 run/screenshots/afl-v061-*.png；目视检查了射击红闪、
空仓红色剪影/数字、后锁及空仓换弹状态。用户最终确认“这一版的测试没有问题”。
本轮正常交互实机验收通过；不等同于穷举多人、所有GUI Scale或所有取消/背包竞态。
旧 DEV Ready muzzle ray calibration at reference plane 检查仍报告失败，
属于冻结构图的既有校准检查，本轮没有改姿态或放宽断言；不记为全部旧烟测通过。

- 保留live开火间隔3 ticks（150ms），不是任务建议的4 ticks；半自动点击边沿、服务端弹量/冷却/换弹校验。
- Successful Shot唯一入口位于P901Actions的成功扣1发分支：同步stack、normal/last-round动画与枪声、
  NativeGunShot服务器hitscan/damage、AFL Noise、给射手的S2C HUD flash。0发只播放限流dry-fire，6 ticks。
- NativeGunDefinition实际参数：capacity17，damage7，headshotMultiplier3（预留，未启用头部判定），
  falloffStart24、effectiveRange48（描述性元数据）、maxRange64、minMultiplier.65、base spread1.2°半角、noise64。
  最终散布现按通用姿态倍率和3/5tick移动恢复计算，BR51-01基础0.30°；见docs/native_guns/native_stance_accuracy_v1.md。
  24–64格线性衰减；64格伤害4.55。球面圆锥内随机射线由服务器从眼睛/视线生成。
  方块碰撞体和实体AABB取最近交点，无穿透、反弹或弹丸；同载具实体过滤，PvP仍遵循原版判定。
- 自定义damage_type apocalypse_firstlight:native_bullet 使用projectile标签，保留护甲/盾牌/抗性结算，
  仅bypasses_cooldown，避免合法150ms连发被原版hurt免疫窗口吞掉。无armor penetration。
  通用Headshot未实现，所有命中使用body damage，未用错误的通用头部box冒充。
- 成功射击调用NoiseSystem.emit(GUNSHOT, radius64)，沿用既有遮蔽/僵尸听觉链；
  不调用枪声耳鸣入口（P9-01 Service Pistol tinnitus=false），爆炸耳鸣不改，生产TaCZ引用仍为0。
- HUD尺寸/位置不变：仅current成功射击后5 ticks红闪，ammo0时current和中性剪影持续红；
  reserve始终白色，非空剪影正常。S2C只传slot/render ID作展示计时，不复制弹药状态。
  Network协议10→11。换槽/不同gun ID/不同世界不会错用另一把枪的flash。
- 原fire(.14s)/reload(1.30s)数据不变；source新增fire_last_round、empty_idle、reload_empty。
  仅slide/front_sight/rear_sight/sight_anchor在后退1.28模型单位保持后锁；
  最后一发保留原fire其它轨道，.04s达后锁后不再前进；empty_idle循环后锁。
  从0开始reload选择专用派生clip，锁存于动作选择，不因.95s补弹而中途切状态；
  保留所有原reload轨道，1.10–1.30s仅机械骨骼smoothstep前进。未制作完整Empty Reload/释放滑套手势。
  trigger优先于empty-idle predicate，后者仅在0发且无进行中trigger时运行。
- 现有E:/Download/dry_fire.ogg原样复制为textures之外的sounds/dry_fire.ogg，
  注册p9_01_dry_fire；不复用正常fire声音，不产生hitscan/damage/Noise/HUD shot flash。
- 生成工具tools/add-native-empty-states.mjs只派生机械clip；tools/export-native-gun.mjs识别并导出新增clip。
  bbmodel其余字段、Geo、Display、原双手姿态/动画、贴图不变。
- DEV新增NativeGunShotGameTests；截图入口 -I src/dev/native-gun-v061-validation.init.gradle，
  最多100张run/screenshots/afl-v061-*.png，仅读取游戏渲染结果，不自动输入或修改背包/世界。
- 未做camera recoil、recoil调参、muzzle flash、casing、ADS、消音器、chamber。
  下一阶段建议V0.6.2 Recoil + Muzzle Flash + 9mm Casing Ejection；不是当前实现。


## Native Gun V0.6 — 9mm / 弹匣 / 功能HUD（已实现，部分验证边界见下）

`apocalypse_firstlight:9mm_round` 已注册为64堆叠普通Item，位于创造标签P9-01 Service Pistol之后，
中英文名9mm手枪弹/9mm Round，复用既有3D物品模型。Casing仍只保留资产，没有抛壳实现。
`NativeGunDefinition`/`NativeGunItem`提供id、ammoType、capacity、hudIcon、26tick时长、19tick装填点、3tick开火间隔。
P9-01 Service Pistol容量17，无chamber/17+1；每把枪NBT `AflGunAmmo.ammoInMagazine` 范围0..17。
无状态的新枪逻辑默认17，服务端首次物品tick/开火持久化；已有0绝不自动补满。
V0.6历史行为：服务端每次获准开火扣1，0发不播放正常动画/声音；当时无dry-fire，现已由V0.6.1补齐。
Reload满匣/零reserve拒绝，19tick重新统计背包并一次性扣弹补匣，26tick结束；8tick抽匣音不改。
当前枪对象/槽位/维度变化、死亡、旁观会取消；已提交的装弹不退回、不再提交。
背包所有原版槽位（含副手）按definition.ammoType计数；不读取其他容器或嵌套背包。
所有模式包括Creative均真实扣弹，不实现无限弹特例。状态通过原版ItemStack/Inventory同步，HUD无独立缓存。

用户明确取消背景框：HUD仅复用925×574剪影 `textures/gui/gun/p9_01_hud.png` 和突出current/次级reserve数字。
767×524渲染图另存 `textures/item/p9_01_inventory.png`，仅供背包图；不覆盖128×128枪体UV贴图。
HUD右下GUI坐标布局，当前主手实现NativeGunItem时显示，其他物品/隐藏HUD/旁观时不显示。
实机首轮发现剪影过大，已从98×60收至36×22 GUI单位；坐标(width-70,height-99)，
current字号1.25、reserve字号1，无背景，底部留空间避开默认盖革计/HUD。
P901Item通过Forge shouldCauseReequipAnimation忽略仅弹量NBT更新/首次GeckoLibID初始化；
换槽、不同枪身份及其它NBT变化仍触发装备动作，避免每次扣弹让整枪下沉再抬起。未改Fire动画。
手枪源、Geo、Fire/Reload动画、Display、臂渲染规则和音效资源均冻结；GUI物品图按用户新授权替换。
V0.6历史阶段尚无Hitscan/Damage/Native Noise；这些已由上方V0.6.1实现取代。
Recoil/MuzzleFlash/抛壳/ADS/消音器仍未实现；下一阶段建议V0.6.2，枪声耳鸣保持关闭。

DEV：`NativeGunAmmoGameTests`检查NBT、复制/容器/掉落实体序列化、部分装填、19tick单次提交及切槽取消。
`-I src/dev/native-gun-ammo-validation.init.gradle`只启用HUD实机截图，玩家进入世界后稳定显示1秒即保存
`run/screenshots/afl-v06-compact-hud-<current>-<reserve>-gui<scale>.png`；不自动操控输入或修改存档/背包。
这些DEV文件不进入发布JAR。旧文档中的“无限弹/尚无ammo或HUD”已被本节取代，视觉参数仍沿用冻结版本。

### V0.6 验证记录（2026-09-07）

- clean compileJava、processResources、build与git diff --check通过；无TaCZ GameTest 48/48通过。
  包含服务端5枪=12、同tick重复开火抑制、零弹/满匣/零reserve拒绝、部分装填、
  19tick单次提交与切槽取消、NBT复制/容器/掉落实体保存恢复，以及ammo同步不触发reequip的回归断言。
- 用户PNG与资源SHA256一致；发布JAR包含新弹药/HUD资源及生产类，不含dev测试/截图类。
- 首轮客户端已进世界并正常保存退出；实际查看17/0、0/64截图，背包图正确，
  但98×60剪影过大且用户报告开火跳动，因此首轮布局不作为通过基线。
- 紧凑布局与reequip修正版已构建、启动并进世界；已目视检查
  `run/screenshots/afl-v06-compact-hud-17-36-gui0.png`、`afl-v06-compact-hud-12-41-gui0.png`
  及`afl-v06-compact-hud-hidden-gui0.png`：无背景剪影/数字正确，默认盖革面板无重叠，
  非枪时隐藏。当前仅验证2560×1417、GUI Auto；其它GUI Scale/分辨率尚未实机覆盖。
  用户对修正版明确反馈“现在不跳了”，扣弹引发的reequip跳动实机确认修复；回归断言通过。
  不将48项测试替代视觉验收；切槽/掉落/箱子/退出重进的全部玩家操作用例尚未逐项实机完成。
- 启动仍记录既存Ready muzzle ray calibration at reference plane DEV断言失败，
  未修改冻结的姿势/动画或放宽该断言。本轮不宣称Native视觉烟测全部通过。



## 2026-09-07 TaCZ 解耦 — 当前生产边界

### 本轮验证结果

- `clean compileJava --offline --stacktrace`、`processResources --offline`、
  `build --offline --stacktrace` 和 `git diff --check` 通过。
- `dependencyInsight --configuration compileClasspath --dependency timeless-and-classics-zero --offline`
  无匹配依赖；src/main/java及src/dev/java的TaCZ API引用为0，644个编译class无`com/tacz`引用。
- 默认 `runClient --offline --stacktrace` 成功加载AFL与TaCZ；
  `runClient -PaflWithoutTacz --offline --stacktrace` 不加载TaCZ也成功启动、进入世界并保存退出。
  两个客户端均已退出。启动资源元数据下载曾受沙箱网络权限阻挡，获准重试后完成。
- 两种配置均有相同的既存 `Ready muzzle ray calibration at reference plane`
  DEV断言失败。此前的item注册、baked model/animation与anchor检查已走过；
  不能记作完整Native视觉/动作自检通过。本轮不修改该断言或手枪资源来掩盖问题。
- 无TaCZ隔离回归：`runGameTestServer -PaflWithoutTacz -I src/dev/noise-system-v2-gametest.init.gradle --offline --stacktrace`
  **44/44 required tests通过**，含内部Gunshot听觉、耳鸣累积/共享Episode、爆炸回归。
  相比旧49项，删除6项外部集成/审计测试，新增1项纯内部Noise测试；没有以删核心测试冒充回归。
- 日志：`build/decoupling-verification/with-tacz.log`、`without-tacz.log`，
  `build/noise-system-v2-gametest/logs/latest.log`（build目录会被clean清除）。
- 未实际逐发验证TaCZ开火或耳鸣试听；无外部枪声连接的结论来自源码、字节码与编译依赖扫描。

TaCZ 不再是 AFL 强制依赖，`mods.toml` 不再声明 TaCZ；Gradle 仅保留
`runtimeOnly fg.deobf("curse.maven:timeless-and-classics-zero-1028108:8141310")` 供开发对比。
`-PaflWithoutTacz` 可省略此开发依赖。Native Gun Framework 是后续枪械功能唯一生产路线。
已删除 `TaczNoiseEvents`、`GunshotNoiseResolver`、枪械ID/类别fallback、附件silence读取及旧专用审计器。
TaCZ 开火不再产生 AFL 枪声 Noise / Tinnitus；但任何模组走标准 Forge Detonate 的爆炸
仍按通用爆炸入口处理，不屏蔽或重复监听第三方弹丸。

保留 NoiseSystem、感染者听觉、Explosion Noise、Explosion Tinnitus、共享Episode、Overlay/音频、
GunshotExposureTracker/Profile/Accumulator 的通用参数与冷却。未来 successful shot 的接入位置：
`NoiseSystem.emit(NoiseEvent, ServerLevel)`（类型 `GUNSHOT`）及
`GunshotExposureTracker.onGunshot(level, shooter, sourcePosition, acousticRadius, false)`。
后者仅发耳鸣impulse、不代发Noise；布尔参数是保留的通用策略输入，不再读取外部附件。
当前 Native P9-01 Service Pistol 尚未调用这两个枪声入口；本轮未实现枪声pipeline或Native消音器。
P9-01 Service Pistol 视觉/动画/声音、9mm资产、HUD及弹药逻辑未改。

删除专用TaCZ开发测试；保留爆炸、耳鸣核心测试，并用显式数值输入测试内部Noise听觉入口。
旧TaCZ测试通过记录仅属历史，不能视为当前集成仍存在或本轮运行验证。


## Reload第0帧转静态基线 — 当前版本 / 待视觉确认

2026-09-07按用户明确要求，将最新Reload第0帧的双手姿势提升为静态anchor；
编辑模式/Ready/Fire共用该姿势，不再仅Reload生效。未重新设计手位。
右anchor origin=(-3.30644,6.28023,4.5735)，rotation=(-84.0494750851,9.5135423796,7.7351212345)。
左anchor origin=(-6.197441193,4.6161394648,6.818674883)，rotation=(-82.5194347864,6.8733485877,-18.0501308899)。
Classic/Slim reference跟随新静态origin，原通用尺寸保持。

方法：将anchor第0帧position/rotation分别加到静态origin/rotation；
Reload对应所有position/rotation关键帧减去同一第0帧值。
reference随pivot平移，因此完整手臂变换保持等价，不重复叠加。
两anchor的Reload第0帧position/rotation现在归零，Fire原关键帧完全不变。
1ms采样、左右第一人称、Classic/Slim两手八角点：
完整Reload最大变换误差4.06e-16 render units；
新Ready相对迁移前Reload第0帧最大误差2.24e-16。
没有重做Reload或改变弹匣路径/节拍；原有首尾细小差异如有仍保留。
源gun geometry、Display、Java、音效、arm scale均未改。

bbmodel/geo/animation保存导出；export --check及processResources/build通过（27s）。
未启动客户端或截图，尚未获得新视觉通过。Blockbench需重新打开磁盘源，避免保存旧内存项目覆盖。
下方“仅Reload reference迁移”是历史；当前新手位已是编辑模式/Ready/Fire共同基线。



## Latest User Reference Animation Migration — 当前源同步 / 待实机确认

2026-09-07用户要求用10:11:27保存的bbmodel重建，并明确同意将reference动画等价迁入anchor。
原导出被source-only reference轨道阻止；没有忽略或丢弃用户动作。
Reload右reference：rotation=(-9.9907,-.434,-2.4621)；
左reference：rotation=(-12.0869,3.2114,14.6598)，position=(-1,-5,0)，均为0秒恒定关键帧。

迁移使用R_anchor(t)*R_reference(t)组合，分解后减去anchor静态Euler作为新rotation轨道；
position为原anchor position加R_anchor(t)*reference position。两reference pivot与anchor相同。
每5ms烘焙到right_hand_anchor/left_hand_anchor，清空已迁移Classic reference轨道。
Classic/Slim共用新anchor；参考尺寸只修正Blockbench五位小数舍入到既有canonical尺寸。
每1ms抽样Classic角点，旋转/位移合成插值最大误差约.00001797模型单位；
不代表GPU视觉通过。没有重新设计姿势或改Java。

特别注意：用户reference关键帧只有0秒，故整个Reload都带该局部动作，包括末端。
本次原样保留，不擅自加回位帧；新Reload到Ready可能有姿势跳变，待用户验收。
上一节历史的Reload末端回Ready误差0不适用于本轮用户新动作。
Fire、源Display、枪几何及其它动画轨道保持本次用户源不变。
geo/animation/FP Display完整同步最新源；Display仅同步源保存精度，
例如右手rotation (.54546703,.19150607,-.27948)→(.54547,.19151,-.27948)，
不是新增镜头校准，scale仍.41。
export --check通过；processResources/build通过（30s，compileJava UP-TO-DATE）。
未启动新客户端，未截图、preview、commit/push；本轮Runtime/视觉未验证。
以下Micro Polish及更早章节为历史，不得覆盖最新用户动画。



## Ready / Fire Final Micro Polish — 当前候选 / 待目视

2026-09-07用户明确允许现有父骨骼约3度修正与Reload基线抵消。
基于实际最新源，fp_root静态rotation X=+3度（原0），Y/Z/pivot不变；
右hand anchor保持X=-3.3064409003，Y 6.43023→6.28023、Z 4.37350→4.57350，
rotation=(-74,12,8)不变。左anchor不另摆，仅随fp_root共同变化。
右Classic/Slim reference跟随新anchor，arm scale/B_skin/Java/Display、gun几何/UV/音效不改。

Fire所有keyframes逐值不变，继承新共同姿态。
Reload只增基线抵消：
w=smoothstep5(t/.30)*(1-smoothstep5((t-1.06)/.24))，输入夹紧0..1；
fp_root原rotation X减3*w；right_hand_motion新增position=(0,+.15*w,-.20*w)。
0–.30s过渡进旧Reload，.30–1.06s完全抵消新基线，1.06–1.30s连接新Ready。
因此不能说Reload文件字节未改；没有重设计旧主段。
gun_model_root -3.2侧移、所有magazine/reload_magazine轨道、左motion、右腕轨道、
weapon_root及其它原rotation/position、mag_out=.40、mag_in=.95、总长1.30均保留。
仅Reload fp_root/right_hand_motion两条轨道变化。

每5ms源数学核对：.30–1.06s枪/弹匣/双手定位点最大差2.31e-16 render units，
Classic/Slim右臂八角点最大差2.39e-16；Fire/Reload回新Ready端点误差0。
geo/animation一致性通过；processResources/build通过（30s）。
该验证不等于GPU视觉通过；新Ready露臂面积、顶面减少量与首尾衔接待用户F3+T后目视确认。
未新增客户端、截图、preview、Java、commit/push；V1未冻结。
下方Arm-only与Final Grip章节为历史，当前静态参数与Reload补偿以上述为准。



## Arm-only Asymmetric Pose Polish — 当前候选 / 待目视

2026-09-07只修改实际驱动Runtime的两条hand anchor，不改gun、Display、camera、arm scale或Java。
右anchor位置(-3.3064409003,6.43023,4.37350)完全保留；
rotation由(-79.96258,4.92385,.87038)改为(-74,12,8)度，绕手端倾斜前臂。
左anchor位置增量(+.25,-.25,+.20)，结果(-4.0675392256,5.6607727898,2.3051145623)；
rotation由(-79.3660643678,2.7946316784,-9.6421944875)改为(-70,-8,-20)度。
左手更靠近右手且更低；不对称角度用于减少正对镜头的大平面感，但投影宽度/遮挡仍需目视。
Classic/Slim source reference同步左anchor，标准几何与通用(.62,.78,.62)尺度不变。

仅bbmodel、geo及相关文档变化；animation.json和所有源关键帧完全冻结。
Reload既有-3.2侧移、弹匣节拍及路径不改；共享anchor新朝向会带入Fire/Reload，
不声称其它状态的手臂外观逐像素不变，仍须复查。
每5ms源采样：右手端最大变化1.25e-16、枪口变化0、动作末端回位误差0；
这些只证明变换与回位，不证明掌面穿插或视觉通过。
geo/animation导出一致，processResources/build通过（29s）；未新增客户端、截图或preview。
当前视觉PENDING_USER，V1未冻结。下方Final Grip参数为历史，手臂参数以上述为准。



## Final Grip Composition Pass — 当前候选 / 等待实机确认

2026-09-07读取本轮实际09:38:14保存源，不恢复旧坐标。用户已明确上一候选Ready未居中、
Reload与右臂重叠，本轮尚不能冻结V1。以下State-based Grip章节是上一轮候选历史。

Ready/Fire：仅right_hand_anchor X从实际-.70修至-3.3064409003307897。
Y/Z及旋转不变；左anchor保持原样。同步Classic/Slim右reference，不改geometry/arm scale/B_skin/Display。
X依据当前FP Display投影求解：右掌中心代理为手端往前臂方向2个标准模型单位；
握把上段中心代理为(-2.98,6.7873653095,8.0839109552)。
1080高/FOV70横向误差约98.83px→0；只表示代理横向对齐，非真实皮肤轮廓或GPU视觉通过。
允许握把部分嵌入右掌。Fire全部轨道不变，只继承新Ready anchor。

Reload：gun_model_root position=(-3.2*w,0,0)模型单位。
首次+3.2候选用户实机确认方向反了（跑到右手右侧）；现仅反转侧移与左手跟随方向，待复验。
w=smoothstep5(t/.30)*(1-smoothstep5((t-1.06)/.24))，输入夹紧0..1。
0–.30s展开，.30–1.06s保持，1.06–1.30s回零。局部Y/Z=0，旋转不改；
3.2模型单位经.41/16折算约.082渲染单位，最终屏幕方向受既有侧开旋转影响。
这是有意的gun-to-hand相对位移，不要求Reload握持漂移为零；边界/浮空必须实机判断。
左手motion X跟随相同枪位移，在.04–.32s进入跟随、1.06–1.27s退出，
完整操作段与magazine保持原局部接触关系，不重画mag manipulation。
仅修改Reload的gun_model_root与left_hand_motion position；
fp_root、weapon_root、右腕rotation、全部其它rotation、magazine/reload_magazine局部轨道逐值冻结。
枪体子树的位移也会作用于共用第三人称枪动画，但不新增第三人称补偿或修改Renderer。

source/geo/animation导出一致，Fire/Reload端点回Ready误差0；processResources/build通过（27s）。
Java、NativePlayerArmRenderer、NativeHandBinding、Display、镜头、geometry/UV、声音、HUD不改。
mag_out=.40s，mag_in=.95s，Reload=1.30s。未截图，未commit/push。
客户端PID40064已启动；启动smoke报Ready muzzle ray calibration断言失败，未记Runtime自检通过。
本轮未改Ready枪体/Display，不扩范围修枪口校准。用户已判定初版Reload方向错误；反向资源待重载验收。
Ready/Fire目视与是否浮空仍未确认，V1视觉冻结=NO。



## State-based Grip Separation — 资产候选 / 未获视觉通过

2026-09-07基于本轮最新保存源进行小范围状态区分；下方Ready Minor Polish为历史记录，
其坐标冻结/动画未改说明不再代表当前候选。没有回退枪体或用户左手旋转。

- Ready/Fire：right_hand_anchor X .50→-.70（-1.20模型单位），Y/Z与旋转不变；
  左anchor Y 6.2607727898→5.9107727898（-.35），X/Z与旋转不变。
  Classic/Slim reference同步真实anchor；不改arm/sleeve比例、B_skin、Display或枪几何。
- Reload：既有fp_root叠加源Z轴+30度的滚转修正（主要合成滚转约-80→-50度），
  source X +.50模型单位；按新右手接触点补偿旋转pivot，保留原右手接触轨迹残差。
  w=smoothstep5(t/.30)*(1-smoothstep5((t-1.06)/.24))，smoothstep5夹紧0..1。
  修正0–.30s平滑进入，.30–1.06s保持，1.06–1.30s退出；不是camera/Java补偿。
- 仅Reload新增right_hand_anchor rotation X=18*w度，绕手端旋转前臂；
  接触点不因该局部旋转移动。Ready/Fire没有该腕部旋转，从而改变枪与前臂的相对关系。
- 左手在.04–.32s逐步抵消Ready的-.35Y；.32–1.06s保持原有枪局部操作位置，
  1.06–1.27s返回新支撑位。只修left_hand_motion position，不改其旋转或弹匣路径。
- magazine/reload_magazine、weapon_root、Fire所有关键帧完全冻结；
  时长.14/1.30s及.40/.95声音节拍不变。仅三个Reload骨轨道变化：
  fp_root、left_hand_motion、right_hand_anchor。

已导出geo/animation；processResources/build --offline --stacktrace通过（27s）。
导出与build资源一致、Classic/Slim契约及端点回Ready检查通过；腕部局部旋转不移动手端。
本轮不改Java、Display、手臂缩放、贴图、声音、第三人称Renderer或HUD。
fp_root第一人称专属；手部anchor只驱动第一人称手层。
左右手回位与导出一致性属于数值检查，不能证明支撑、穿插或Reload可读性通过。
未启动本轮客户端；Ready/Fire主握与Reload前臂侧面构图均待用户视觉验收，
STATE_BASED_GRIP_SEPARATION_DONE / READY_VISUAL_PASS / RELOAD_VISUAL_PASS仍为NO。


## Ready Minor Hand Position Polish — 用户最新源同步 / 待目视

2026-09-07以用户09:17:40再次保存的bbmodel为权威，替代本轮先前的左手微调候选。
实际Runtime手臂由P901HandLayer读取animated right_hand_anchor/left_hand_anchor，
通过NativePlayerArmRenderer与B_skin渲染；仅移动reference cube不会驱动Runtime。

- 右anchor保留最新源坐标(.50,6.43023,4.37350)，旋转(-79.96258,4.92385,.87038)不变。
  本轮早先仅X .35→.50（+.15）；收到最新源后没有再次叠加右手偏移。
- 用户最新左Classic reference相对原anchor移动(-4,0,-1)，局部旋转(-5,0,20)度。
  将这一现成姿势等价迁入left_hand_anchor，而不是恢复旧支撑位：
  origin=(-4.317539225647375,6.260772789810336,2.105114562329118)，
  rotation=(-79.36606436776994,2.7946316784146132,-9.642194487526469)度。
  按Rz*Ry*Rx合成原anchor与reference旋转，位置使用原anchor旋转作用于局部位移。
  新角度只是等价分解，不是新设计。Classic/Slim参考子节点归零并跟随真实anchor。
- 最新Classic姿势迁移前后，Ready/Fire/Reload每5ms采样八角点最大误差
  4.464e-8 render units；这是姿势等价证据，不是穿插或视觉通过证据。

用户源gun子树此前已整体X -3，而旧geo未同步。本次保留该源摆位并完整导出geo，
包含gun及机械节点的位置；没有再次修改源gun几何、UV或设计。
Runtime animation.json也按最新源同步：相对旧资源仅多出用户源已有的gun零position轨道
（0与0.63333秒均为[0,0,0]），其余Runtime轨道完全相同。
源Fire/Reload关键帧、时长、Display、贴图、声音、所有Java及手臂比例均未修改。
基础手位与用户gun摆位会带入其它状态，不为此新增Reload补偿或第三人称特例。

processResources/build --offline --stacktrace通过（30s，compileJava UP-TO-DATE）。
本次已启动的客户端PID540最初加载旧候选，启动检查曾FAIL: source/runtime pivot gun；
原因是当时geo缺少用户gun移动。当前geo已重新同步，但尚未重新执行客户端一次性smoke检查，
不能把旧FAIL改记为Runtime PASS。最终检查时PID540已退出；未自动重启。
最新资源已构建，仍需下次客户端加载后由用户验收。
当前Ready穿插/支撑及其它状态回归均未获新视觉通过；无截图、preview、commit/push。

改动：源bbmodel、Runtime geo/animation及相关文档。以下V2导出记录为历史，不代表本轮导出范围。

## 上轮 Reload Composition Rework V2 — Blockbench PASS / Runtime待实机验收

2026-09-07：用户明确确认 **Blockbench Preview Gate = PASS**，保留当前V2源构图。
本轮未修改 `src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`，
已只同步Runtime `animations/p9_01.animation.json`，构建并启动客户端。
Ready/Fire、geo、Display、所有Java与声音文件未改；下方V0.5.2是历史Reload，不再代表当前运行时。

### 旧问题与新构图

视频中 AFL 仍把枪与双手一起抬高，右臂像跟着枪移动。A 的合成变换围绕
旧 carrier pivot=(0,8,6)，又叠加 P=(1.1,3.25,-3.8)，所以即使接触矩阵无漂移，
右手在屏幕里仍移动约151px。此前“降低抬升”不等于正确构图。
本轮没有读取/复制 TaCZ 资产或参数；只借鉴用户视频中的右手控枪、左手操作与紧凑构图。

V2 不再平移到换弹平台，而是以已有右手接触点
c=(0.35,6.4302294997,4.3735038416) 为旋转中心，固定它在 Ready 的原位置。
合成 source Rxyz 主姿势=(-35,-15,-80)°，顺序 Rz×Ry×Rx；
这是源坐标欧拉分解，不应理解为额外屏幕 yaw/roll，更不能再叠加到 Java。
角度选择同时检查握把稳定、弹匣井不被右臂参考体遮挡、右臂向画面右下展开和抽匣可读性。
右手不新增独立 key，也不重摆基础 anchor：右手/枪保持固定相对关系，左手承担主要运动。

设 d=c−carrierPivot，则：
`P_target=d−R_target×d`，这是换旋转支点所需的位移，不是搬动枪或镜头。
仍保留 weapon_root 原轨道，源 fp_root 烘焙：
`R_fp=R_target×inverse(R_weapon)`，
`P_fp=P_target−R_fp×P_weapon`。
因此第三人称 gun carrier 旧动作也未改；用户允许共享的新 magazine 轨道现已导出。
第三人称 Renderer/CROSSBOW_HOLD/Display 与全部 Java 均冻结。

### 节奏、接触和路径

- 总长1.30s，.00–.30五次 smootherstep 缓入；前一运动段近静止，不再抬起整个 rig。
- 左手 .04 开始离开 support，.16 绕行，.32到达底板，.32–.36抓稳停顿。
- .40抽匣声音位置不变；此时弹匣已沿自身-Y轴退出.24单位，.43为.34。
- .52轴向退出6.6，.52–.55保持分离节拍；完全退出后才转向画面左下离开。
- 最大轴向8.2；出屏附加路径为 source `inverse(R_main)×(-3,-5,0)`，
  .55开始、.64–.72保持、.82归零；这不是 Runtime camera offset。
- .64隐藏旧弹匣、.72复用同一几何显示新弹匣；隐藏/再现时整个弹匣包围盒在下边界外，
  不是在画面里 teleport。手臂始终不隐藏。旧匣完整退出阶段可见；取新匣过渡刻意出屏。
- .82轴向6.5且已同轴，.88为2、.92为.45、.95归零插入。mag_in仍.95，无音效改动。
- .95–1.06保持接触；.95–1.035微小确认脉冲至多+.35° pitch/+.15° roll，同样绕握把，
  无Y脉冲，无握把位置变化。1.06–1.30缓出，左手1.27回support，枪最后缓停。
- 抓匣左 motion R=(48,12,10)°，出屏转换(52,8,2)°，让手端平面靠近底板方向；
  基础 hand anchor/B_skin/reference geometry 未动。
- 左手 position 由 `target−motionPivot−R_motion×(anchor−motionPivot)` 求解，
  target跟随底板接触点；magazine和reload_magazine guide同帧一致，没有复制第二份几何。
- 路径仍以保形三次Hermite制作，.005s采样为linear源关键帧；未新增Runtime插值器。

### 数值辅助（不是视觉通过）

工具：`tools/author-p9-01-reload.mjs`，只读审计/候选stdout，不导出资源、不写图片。
投影参考1920×1080、竖直FOV70，原点准心，X向右、Y向下；不是实际游戏截图坐标。
共1041个时点、步长.00125s，包含关键帧之间的插值，不能只测烘焙点。

| 同口径指标 | A（未通过） | V2候选 |
| --- | ---: | ---: |
| grip相对Ready最大屏幕漂移 | 151.121px | 0.034px |
| gun中心上下行程 | 147.440px | 17.778px |
| gun中心相机深度行程 | .091273 render units | .005022 render units |
| .30–1.00右前臂裁切线段均长 | 402.84px | 484.63px |
| .30–1.00右前臂裁切线段最短 | 398.32px | 479.15px |
| magwell接触标记被右参考臂射线遮挡样本 | 561/561 | 0/561 |

gun中心是冻结的枪几何原始AABB中心的跟随标记，不是像素质心；
magwell使用静止底板接触标记，不是移动弹匣中心。
前臂代理是右手端到完整reference arm近身端的投影线，经近裁面/视口裁切；
**它不计算袖子、枪体遮挡后的真实可见面积**。射线只测右臂参考体，非完整GPU遮挡测试。
不能因此把 magwell readablity / 皮肤无穿模 / 用户视觉验收标成YES。

| t(s) | grip屏幕(x,y) | gun中心(x,y) | magwell(x,y) | 右臂线段px | 左手端(x,y) |
| --- | --- | --- | --- | ---: | --- |
| 0.00 | 91.0, 356.4 | 75.0, 323.8 | 82.8, 631.3 | 192.9 | 62.8, 343.2 |
| 0.10 | 91.0, 356.4 | 78.2, 328.5 | 32.0, 595.8 | 189.9 | 40.6, 339.3 |
| 0.20 | 91.0, 356.4 | 68.4, 341.3 | 37.2, 425.5 | 341.7 | 36.9, 312.4 |
| 0.30 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | 92.3, 364.8 |
| 0.40 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | 88.6, 382.6 |
| 0.52 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | -101.5, 497.8 |
| 0.65 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | -387.7, 889.6 |
| 0.80 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | -135.3, 540.5 |
| 0.95 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | 93.6, 379.6 |
| 1.06 | 91.0, 356.4 | 60.6, 340.7 | 93.6, 379.6 | 484.9 | 93.6, 379.6 |
| 1.18 | 91.0, 356.4 | 76.5, 336.4 | 4.2, 513.8 | 205.6 | 31.9, 331.7 |
| 1.30 | 91.0, 356.4 | 75.0, 323.8 | 82.8, 631.3 | 192.9 | 62.8, 343.2 |

.65为出屏换匣；.80左手端接近下边缘，之后插入和接触进入下部画面。Ready与收尾的magwell
下缘本来出屏，冻结Ready；主要操作阶段.30–1.00的标记保持准心下约380px。
.635旧匣与.72新匣未缩放包围盒最低屏幕Y分别626.1/623.5px（下边界540px），隐藏切换不在屏内。

源相对握持矩阵最大误差1.11e-15，左手接触设计点误差2.85e-12 render units，guide误差0。
上轮源制作阶段检查了开始/结束回Ready、原Fire与geometry/groups/Display冻结。
本轮导出阶段src哈希只有animation.json变化，bbmodel、Java、geo、Display与声音完全未变。
这些不是实机、声音实播或 Blockbench 正常速度循环播放的证据。

### Preview Gate通过后的Runtime交付与验证

用户本轮明确通过Blockbench Gate并授权导出/build/runClient；没有重设计或重存源。
仅使用现有编译器的animation输出，逐项检查Runtime animation等于保存的bbmodel导出，
Fire内容完全不变。未执行全量export --write：geo原先有format_version和一个size舍入差异
（bones[5].cubes[5].size[1]：.91238对编译结果.91237），与本轮动画无关，保持geo不变。
完整export --check仍会因该既有geo差异失败；本轮采用animation精确相等与Display冻结的定向校验。

执行 `gradlew.bat compileJava processResources build --offline --stacktrace`，
08:31:21构建成功（38s，compileJava UP-TO-DATE，无Java改动）。
动画已进入build/resources/main与build/libs/apocalypse_firstlight-1.0.0.jar。
随后执行 `runClient --offline --stacktrace`，08:32:16启动Forge，
08:32:45资源加载及现有DEV检查通过：
- 40次动作 / 7,320帧；
- Classic最大误差5.151687e-7，Slim 5.119259e-7；
- 右握持矩阵最大漂移5.066395e-7；
- Gecko烘焙模型/动画、hold easing、停止动作包校验PASS。
现有DEV日志标签仍写V052 ENTRANCE，但读取的是本轮V2源和新Runtime动画；未修改Java。

测试客户端PID41356（本轮runClient会话），启动前PID16200确认为Gradle daemon，未结束它。
测试窗口已交给用户。**本轮完整Reload实机目视仍PENDING_USER**，重点：
起手、.40s抽匣、.95s插入、1.10–1.30s回Ready及重复换弹。
数字检查不证明皮肤遮挡、第三人称弹匣观感或声音实播；第三人称专用实现仍冻结。
测试完成后保存退出，或退到标题后结束本次客户端；不要结束不明JDK进程。
未截图、未创建preview文件、未commit/push。

```ini
AUDITED = YES
SOURCE_RELOAD_REWORKED = YES
RELOAD_COMPOSITION_REDESIGNED = YES
GRIP_USED_AS_VISUAL_ANCHOR = YES
GUN_STAYS_CLOSE_TO_RIGHT_FOREARM = YES (rig relationship; visual pending)
GUN_ROTATION_DOMINATES_TRANSLATION = YES
GUN_VERTICAL_TRAVEL_REDUCED = YES (source projection)
GUN_DEPTH_TRAVEL_REDUCED = YES (source projection)
RIGHT_HAND_ROLE = PRIMARY_CONTROL
LEFT_HAND_ROLE = MAGAZINE_MANIPULATION
RIGHT_HAND_STAYS_ON_GRIP = YES (source)
RIGHT_HAND_GRIP_DRIFT_MAX = 1.11e-15 (relative matrix)
RIGHT_FOREARM_VISIBLE_LENGTH_IMPROVED = YES (clipped line proxy only)
MAGWELL_READABLE_THROUGH_MAIN_RELOAD = NO (not visually verified)
MAG_OUT_CONTACT_BEAT = YES
MAG_IN_CONTACT_BEAT = YES
RELOAD_FIRST_FRAME_SNAP = NO (source continuity)
RETURN_TO_READY_SNAP = NO (source continuity)
RELOAD_TOTAL_DURATION = 1.30
MAG_OUT_SOUND_TIMING_CHANGED = NO
MAG_IN_SOUND_TIMING_CHANGED = NO
AUDIO_FILES_CHANGED = NO
READY_POSE_CHANGED = NO
FIRE_ANIMATION_CHANGED = NO
PLAYER_ARM_PRESENTATION_SCALE_CHANGED = NO
RUNTIME_JAVA_CHANGED = NO
BLOCKBENCH_PREVIEW_GATE = PASS
RUNTIME_ASSETS_EXPORTED = YES (animation only)
BUILD_RUN = YES
BUILD_VERIFIED = YES
GRAPHICAL_CLIENT_LAUNCHED = YES
RELOAD_USER_VISUAL_PASS = PENDING_USER
TACZ_CODE_COPIED = NO
TACZ_ASSETS_COPIED = NO
SCREENSHOTS_CREATED = NO
PREVIEW_FILES_CREATED = NO
DOCS_UPDATED = YES
COMMIT = NO
PUSH = NO
```

## 历史 Runtime V0.5.2：待机枪线保留，Reload已由上方V2替代

2026-09-07，master / 7685f61，保留已有dirty工作树。仅修改源FP Display旋转、
Reload的fp_root前0.24秒position/rotation；没有生产Java美术补偿。
V0.5.1的(.62,.78,.62)通用arm presentation、完整Classic/Slim/袖层、双手绑定继续冻结。
本节覆盖下文历史Display和起手采样描述；不是重做Reload深度/方向或左手轨迹。

### Aimline：真实枪管轴，不是ADS

- source barrel、muzzle、前后照门定位明确local forward=-Z。旧Display pitch/yaw约-4°，
  相机中的枪管方向为(.0695865,-.0697565,-.9951340)，向右下偏离准心。
- 用muzzle发出的barrel轴射线与camera Z=-20 render units参考平面相交，
  再投影到FOV70°、高1080px，X向右/Y向下；这只是可复跑的hip视觉参考，不是实际弹道距离。
  source sight_anchor + front_sight−rear_sight方向交叉核对，未把前后准星叠成ADS。
- 右Display Rxyz从(-4.00974,-3.99024,-.27948)改为(.54546703,.19150607,-.27948)°；
  左Display为(.54550276,.11247657,-.27948)°，计算包含Vanilla左context镜像。
  两者原translation、uniform scale=.41不变。整套枪和左右locator一起运动，不改基础接触。
- 右射线准心偏差从(+54.3915,+59.1341)px到约(0,0)px；
  sight射线约(+.00594,-1.22528)px；枪口本身仍在(+58.16,+165.66)px，
  前后准星仍不重合。数学射线零误差不代表ADS、GPU或用户视觉已通过。
- Fire原.14s轨道、后坐/套筒/枪管/扳机全部冻结，只继承同一个新Display基线。

### Reload entrance：仅source fp_root重分配早期时间

审计发现两个问题。首先是导出时序错误：JS对象的整数键"1"被提到小数键之前，
JSON顺序变成0→1→.02…；本地GeckoLib4.7.4 BakedAnimationsAdapter按entrySet顺序构造
时段并相减，没有先排序。首轮实际Gecko在.24秒仅得到fp_root Y=2.036而非源14.933，
证明只比较源JSON数值或回位不能发现该错误。导出器现将正整数秒写为"1.0"，
并断言序列严格递增；DEV增加源早期keys与实际Gecko同秒位置/角度比较，失败即报FAIL。
该修复不改0.24秒后源路径值，但会纠正此前错误的Runtime求值，不能声称旧屏幕中后段逐帧不变。

其次，旧0帧本身是零，不是文件中直接从高位开始。旧source smoothstep每.02秒线性采样，
在.08秒已到25.93%、.12秒50%，叠加约14.93模型单位上抬，造成第一小段快速拉高的观感。
没有Java Reload presentation残留。weapon_root原轻微动作未改，避免改变第三人称动画。

新曲线仅制作时计算：u=(t/.24)^1.5，f=6u^5−15u^4+10u^3；
position与rotation同乘f，每.005秒采样成普通linear keys，Gecko不新增easing实现。
峰值source P=(2.6666666667,14.9333333333,-13.3333333333)，R=(8,32,-16)不变。

| 秒 | 旧进度 | 新进度 | 旧Y / 新Y（模型单位） |
| --- | --- | --- | --- |
| 0 | 0% | 0% | 0 / 0 |
| .04 | 7.41% | .28% | 1.10617 / .04237 |
| .08 | 25.93% | 5.23% | 3.87160 / .78080 |
| .12 | 50% | 24.07% | 7.46667 / 3.59464 |
| .16 | 74.07% | 58.27% | 11.06173 / 8.70145 |
| .20 | 92.59% | 90.75% | 13.82716 / 13.55158 |
| .24 | 100% | 100% | 14.93333 / 14.93333 |

源曲线fp_root首个线性段平移速度由19.8691降为.001093模型单位/秒（非旧错误Runtime实测）；
末段约1.15758单位/秒后接保持。线性采样仍有小斜率折点，不宣称严格C1连续。
0–.04秒平均速度37.4007→1.43259；为了固定.24秒终点，.12–.20秒会更快，
密集采样峰值约190.111单位/秒。若用户仍觉得后半起手急促，应记未通过，不偷偷延迟弹匣时序。

0.24秒及之后的fp_root keys、整段weapon_root、左右hand_motion/anchors、
magazine/reload_magazine全部保持原值；Reload仍1.30s，mag_out=.40s / mag_in=.95s不变。
中后段局部路径冻结，但其屏幕投影会继承共享Display的小幅朝向变化，不声称逐像素冻结。

### 文件、检查与验收边界

- 修改唯一源src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel；
  从保存源编译同步animations/p9_01.animation.json、models/item/p9_01_in_hand.json。
  geo字节冻结：既有geo格式版本/guard小数差异本轮不顺便重写，不能声称全geo精确导出一致。
- 新tools/check-native-gun-aimline.mjs：source矩阵、muzzle/sight投影、入口速度、
  资源同步、Fire/Reload回Ready；只读stdout，无图片，无TaCZ依赖或读取。
- tools/export-native-gun.mjs修复整数时间键的JSON枚举顺序，防止Gecko负时段/乱序求值。
- DEV NativeHandContractChecks增加真正Gecko骨骼的双context枪线与.00/.04/.08/.12/.16/.20/.24
  起手采样日志；保留40动作/7,320帧、Classic/Slim完整arm/sleeve对应和固定主握矩阵检查。
- 最终compileJava/processResources/build --offline分别32s/24s/26s通过，test NO-SOURCE；
  release apocalypse_firstlight-1.0.0.jar中DEV classes=0，git diff --check通过。
  增加DEV断言时曾有一处var复合声明编译错误，已拆开并完成上述最终重编译。
- 首个07:28客户端发现时序不一致后正常关闭PID41764；07:35最终客户端资源加载完成，
  真正Gecko求值与source早期position/rotation逐样本差异<.0005（模型单位/度），
  0/.04/.08/.12/.16/.20/.24及重复触发全部通过；.24秒Y现为14.9333，非旧错误2.0357。
  枪线右context误差(0,2.298355e-6)px，左(1.4364718e-7,-5.7458874e-7)px。
  40动作/7,320帧检查通过；Classic误差4.5613135e-7、Slim4.217318e-7，
  1,903,720对应点，右握相对矩阵漂移1.0430813e-6，小于1e-5；Smoke通过。
- 早期49时点/30,184枪cube角点参考投影：camera Z范围[-1.138,-.443]，无近裁面穿越；
  Y最大绝对NDC=1.226，枪下端仍有出屏部分。此检查不包含袖子互遮/纹理/实际GPU，
  不把无近裁面穿越说成无穿模或视觉通过；用户已收到最终客户端验收请求。
- Ready双手/Fire回位为数值回归通过，目视仍PENDING_USER；Reload中后段源轨道冻结，
  Runtime修正时间排序后的观感及音效配合仍PENDING_USER。最终PID22508（07:34:54启动），
  07:36:08已进入test世界；07:37:04正常退出并保存所有维度，进程已结束。未收到本轮视觉结论，
  不把用户进入/退出世界当成通过，也不继续留测试客户端或自动重启。
- 源geometry/UV/textures/groups/所有locator/Fire/非FP Display冻结检查已通过。
  不生成截图/preview，不commit/push。

### V0.5.2 严格状态（数值/实现与用户视觉分开）

FIRST_MOTION_IS_SMOOTH仅指fp_root首段速度接近0的数值检查，不声称线性折线严格C1或画面已通过；
weapon_root原轻微起手保留。MID/END的MINIMAL指修复JSON顺序后的Runtime求值及共享Display影响，
所有.24秒后源key值/时间未改。

```ini
AUDITED = YES
IMPLEMENTED = YES
BUILD_VERIFIED = YES
GRAPHICAL_CLIENT_LAUNCHED = YES
READY_AIMLINE_CALIBRATED = YES
READY_AIMLINE_USES_MUZZLE_OR_SIGHT_REFERENCE = YES
READY_AIMLINE_SCREEN_ERROR_X_BEFORE = 54.3915 px
READY_AIMLINE_SCREEN_ERROR_Y_BEFORE = 59.1341 px
READY_AIMLINE_SCREEN_ERROR_X_AFTER = 0 px
READY_AIMLINE_SCREEN_ERROR_Y_AFTER = 0.000002298355 px
READY_AIMLINE_USER_VISUAL_PASS = PENDING_USER
RELOAD_ENTRANCE_EASING_IMPLEMENTED = YES
RELOAD_ENTRANCE_FIRST_MOTION_IS_SMOOTH = YES
RELOAD_ENTRANCE_VELOCITY_DISCONTINUITY_BEFORE = wrong JSON time order; intended source first segment 19.8691 model_units/s
RELOAD_ENTRANCE_VELOCITY_DISCONTINUITY_AFTER = chronological export; source first segment 0.001093 model_units/s; piecewise linear
RELOAD_TOTAL_DURATION_CHANGED = NO
MAG_OUT_TIMING_CHANGED = NO
MAG_IN_TIMING_CHANGED = NO
AUDIO_TIMING_CHANGED = NO
RELOAD_MID_PATH_CHANGED = MINIMAL
RELOAD_END_PATH_CHANGED = MINIMAL
RELOAD_ENTRANCE_USER_VISUAL_PASS = PENDING_USER
PLAYER_ARM_PRESENTATION_SCALE_CHANGED = NO
RIGHT_HAND_LOCATOR_BASE_CHANGED = NO
LEFT_HAND_LOCATOR_BASE_CHANGED = NO
GUN_MODEL_GEOMETRY_CHANGED = NO
GUN_PHYSICAL_SCALE_CHANGED = NO
THIRD_PERSON_CHANGED = NO
HUD_CHANGED = NO
GAMEPLAY_CHANGED = NO
JAVA_RELOAD_PRESENTATION_REINTRODUCED = NO
PER_STATE_ARM_SCALE_USED = NO
SHORT_HAND_USED = NO
THIN_GRIP_SURFACE_USED = NO
TACZ_CODE_COPIED = NO
TACZ_ASSETS_COPIED = NO
TACZ_RUNTIME_DEPENDENCY_ADDED = NO
DEV_CLASSES_IN_RELEASE_JAR = 0
SCREENSHOTS_CREATED = NO
PREVIEW_FILES_CREATED = NO
DOCS_UPDATED = YES
COMMIT = NO
PUSH = NO
```

DOCUMENTATION: 本页、native-gun-hand-locator-authoring-standard.md、afl_weapon_art_standard_v1.md、
docs/03 - 制作清单.md同步V0.5.2；历史审计报告保留其原始版本证据。

## 历史 V0.5.1：通用第一人称玩家手臂表现尺寸（尺寸继续生效）

2026-09-07，master / 7685f61，基于用户已有dirty工作树；没有commit/push。
本节覆盖下文V0.5的“手臂完整继承Display尺寸”描述；历史审计保持原始证据，不当作新参数。

### Universal First-Person Player Arm Presentation Scale

- 唯一owner：NativePlayerArmRenderer.PRESENTATION_X/Y/Z=(.62,.78,.62)。所有Native武器、
  左右手、Classic/Slim、Ready/Fire/Reload共用，无武器名或动作条件。
- old：M_evaluated_contact × B_skin，实际basis=(.41,.41,.41)。
- new：M_evaluated_contact × S(1/s) × S(.62,.78,.62) × B_skin。
  s为输入刚性矩阵所含统一缩放；保留平移与旋转，只用一个逆标量抵消尺寸。
  不用Gram-Schmidt、不重建Quaternion，不恢复.246/.30、per-state/per-gun scale。
  输入非均匀父scale/shear不属于制作契约，校验拒绝而非偷偷修正。源Display须统一，hand祖先不放scale轨道。
- scale pivot=canonical distal cap；B_skin仍T(-centreX,-10,0)/16。
  scale在B_skin左侧，先将Vanilla手端映射至零点再缩放；直接反向相乘会造成接触漂移。
- Vanilla Classic4×12×4、Slim3×12×4、skin/UV、sleeve .25 inflate/玩家开关不变。
  base arm与sleeve走同一矩阵，全部六面保留，finally恢复共享ModelPart状态。
- 本轮冻结枪/locator/hand_motion/Display/Fire/Reload轨道/音效/第三人称/HUD/图标。
  Reload深度与展开方向仍属于V0.5.2，未实施。

### Blockbench preview 同步与 source-only 代理

仅修改原源文件四个export=false reference cube的from/to，将canonical几何显示变换
(.62,.78,.62)/.41烘焙到代理bounds；没有改变组/locator的origin、rotation、父子关系、动画或UV。
代理cap不移动；当前Classic代理尺寸约6.04878×22.82927×6.04878，
Slim约4.53659×22.82927×6.04878。这不是运行时Vanilla几何变更，也不导出为枪geometry。
更新源文件需在Blockbench重新读取磁盘；不能用打开中的旧缓存覆盖修改。

工具tools/native-arm-presentation.mjs从Java读取唯一framework常量；--patch输出仅参考bounds的patch，
由Codex应用；--check拒绝过期显示代理。tools/export-native-gun.mjs同步验证这个规则。
修改Display scale之后需重新同步代理。中性四臂模板仍保留原始模型单位标尺，不当作最终FP预览。
本轮没有运行资源写入导出：geo/animation/display应逐字节冻结；此前geo格式/guard小数精度、
source/runtime Display精度差异仍保留，不能用本次检查声称全资源精确导出一致。

### 投影与验证边界

tools/check-native-arm-presentation.mjs：70°垂直FOV/16:9/1080高、同一静态Ready，Slim右臂：

| 指标 | Before | After |
| --- | --- | --- |
| 可见轴段投影长度 | 310.75px | 378.68px |
| 手端截面水平投影宽度 | 94.73px | 143.88px |
| 朝镜头表面枪遮挡采样比例 | 23.69% | 3.58% |
| 手端XYZ漂移（JS参考场景） | — | 0 |

长度为纵向表面截面覆盖后的投影积分，宽度为cap截面角点水平跨度；不是截图测量。
枪遮挡减少不等于无穿插，放大后的双手/袖子互遮和握持自然度必须用户目视。
候选依据：X/Z较旧最终.41增加约51%，Y增加约90%；选择中等增幅，不照抄TaCZ[1,1.5,1]。

- compileJava --offline --stacktrace通过32s；processResources --offline通过24s；
  build --offline --stacktrace通过26s，test NO-SOURCE，存在既有弃用警告。
- 新DEV contract覆盖incoming .30/.41/.50/.80/1.00、左右手、Classic/Slim手端漂移与固定最终轴长；
  真正烘焙的完整arm/sleeve角点对照source代理，并保留40动作/7,320帧重复回位检查。
  07:08:51新客户端实际执行通过：maxScaleError=1.4600097e-7、cap contact drift=3.414285e-8 render units；
  Classic maxError=4.3443978e-7、Slim=4.3445573e-7，1,903,720对应点，40动作/7,320帧；
  相对握把矩阵漂移5.066395e-7，均小于1e-5。完整arm/sleeve、状态回位/动画和stop packet smoke通过。
- 用户关闭旧PID17660后，07:08:32启动本轮新版，日志确认OpenGL4.6/NVIDIA窗口和资源加载，
  07:09:02已进入世界。未结束旧进程或不明JDK进程；用户反馈后正常关闭本轮PID8584。
  07:11:51日志确认玩家与所有维度区块保存完成，进程已退出，runClient正常结束。
  用户在Ready/Fire/Reload功能和袖子验收询问后反馈“目前没有问题”：Ready/Fire本轮用户验收通过，
  Reload功能未反馈回归；Reload深度/展开方向视觉仍NOT_TESTED。Classic单独皮肤目视未逐项确认，
  不把bake两种几何的数值通过冒充所有皮肤外观均已实机覆盖。
- release JAR内DEV entries=0；git diff --check通过（仅既有换行提示）。
- 无截图/preview图片，无TaCZ代码/资产复制或新依赖。

### 本轮文件与严格状态（用户反馈暂无问题）

Production：NativePlayerArmRenderer.java（通用presentation）、NativeHandBinding.java（注释与矩阵顺序说明）。
DEV：NativeHandContractChecks.java（五种parent scale、手端、source代理/真实arm+sleeve、动作回归）。
源：p9_01_v03_8_fire_slide_cleanup.bbmodel（仅四个reference cube from/to）。
Tools：native-arm-presentation.mjs、check-native-arm-presentation.mjs（新增）；
export-native-gun.mjs（验证显示代理）、audit-player-arm-presentation.mjs（导出既有数学函数供Before估算）。
Docs：本页、native-gun-hand-locator-authoring-standard.md、afl_weapon_art_standard_v1.md、docs/03 - 制作清单.md。

```ini
AUDITED = YES
IMPLEMENTED = YES
BUILD_VERIFIED = YES
GRAPHICAL_CLIENT_LAUNCHED = YES
TEST_CLIENT_CLOSED = YES
UNIVERSAL_ARM_PRESENTATION_SCALE_IMPLEMENTED = YES
ARM_PRESENTATION_SCALE_X = 0.62
ARM_PRESENTATION_SCALE_Y = 0.78
ARM_PRESENTATION_SCALE_Z = 0.62
ARM_PRESENTATION_SCALE_IS_FRAMEWORK_LEVEL = YES
ARM_PRESENTATION_SCALE_IS_WEAPON_INDEPENDENT = YES
ARM_PRESENTATION_SCALE_IS_STATE_INDEPENDENT = YES
PLAYER_ARM_INHERITS_GUN_DISPLAY_SCALE = NO
PLAYER_ARM_INHERITS_GUN_VISUAL_SCALE = NO
HAND_CONTACT_POSITION_DRIFT = 3.414285e-8 render units
HAND_CONTACT_POSITION_PRESERVED = YES
CLASSIC_GEOMETRY_CHANGED = NO
SLIM_GEOMETRY_CHANGED = NO
PLAYER_SKIN_CHANGED = NO
SLEEVE_GEOMETRY_CHANGED = NO
BLOCKBENCH_REFERENCE_PREVIEW_MATCHES_RUNTIME_SCALE = YES (numeric; UI visual pending)
READY_RIGHT_ARM_PROJECTED_LENGTH_BEFORE = 310.75px
READY_RIGHT_ARM_PROJECTED_LENGTH_AFTER = 378.68px
READY_RIGHT_ARM_PROJECTED_WIDTH_BEFORE = 94.73px
READY_RIGHT_ARM_PROJECTED_WIDTH_AFTER = 143.88px
READY_GUN_OCCLUSION_BEFORE = 0.2369
READY_GUN_OCCLUSION_AFTER = 0.0358
READY_USER_VISUAL_PASS = YES
FIRE_USER_VISUAL_PASS = YES
RELOAD_VISUAL_PASS = NOT_TESTED
FIRE_REGRESSION = NO (numeric and user report)
RELOAD_FUNCTIONAL_REGRESSION = NO (numeric and user report)
RIGHT_HAND_LOCATOR_CHANGED = NO
LEFT_HAND_LOCATOR_CHANGED = NO
GUN_MODEL_CHANGED = NO
GUN_SCALE_CHANGED = NO
DISPLAY_CHANGED = NO
RELOAD_ANIMATION_CHANGED = NO
FIRE_ANIMATION_CHANGED = NO
LEGACY_0_246_RESTORED = NO
PER_GUN_ARM_SCALE_USED = NO
PER_STATE_ARM_SCALE_USED = NO
SHORT_HAND_USED = NO
THIN_GRIP_SURFACE_USED = NO
TACZ_CODE_COPIED = NO
TACZ_ASSETS_COPIED = NO
TACZ_RUNTIME_DEPENDENCY_ADDED = NO
DEV_CLASSES_IN_RELEASE_JAR = 0
SCREENSHOTS_CREATED = NO
PREVIEW_FILES_CREATED = NO
DOCS_UPDATED = YES
COMMIT = NO
PUSH = NO
```

## 历史 V0.5：运行时视觉权威收敛（尺寸契约已由V0.5.1覆盖）

### 最新：按用户原姿势等价迁移 reference arm → anchor

2026-09-07用户明确要求保留刚摆好的姿势，不重新设计。此前用户直接修改Classic参考cube，
而Runtime只读取anchor，导出器因此拒绝非canonical参考臂。现已仅折叠这次变换：

- 右：origin=(.35,6.4302294997,4.3735038416)，rotation=(-79.962575657,4.9238473409,.8703813957)。
- 左：origin=(-.4404511189,6.3549129897,3.3069197667)，rotation=(-73.7006336975,-16.448834621,-15.1570841619)。
- 合成旧anchor、reference group、cube的完整矩阵；新origin是变换后的手端中心，
  新rotation取组合旋转的ZYX欧拉表示，不直接相加角度。Classic角点不变；Slim使用同一新坐标系。
- 源只修改2个anchor、4个reference group及其4个cube。Runtime geo只改两个anchor的pivot/rotation。
  motion、枪体几何、Display、动画轨道、贴图、Java/B_skin、玩法、音效均未改。
- 已保存源与实际geo坐标约定核对：Ready及Fire/Reload密集采样共41,648角点，
  最大误差4.2613e-11模型单位，证明保留用户源姿势；非本轮Gecko图形/目视测试。
- `processResources build --offline --stacktrace`通过（28s，compileJava UP-TO-DATE，test NO-SOURCE）。
  本轮没有启动客户端；用户需要重新载入磁盘bbmodel，勿用旧编辑器缓存覆盖本次迁移。
- 完整`export-native-gun --check`仍有此前保存/手动geo导出的非手臂差异：geo format_version为
  1.21.110（工具目标1.12.0）、一处guard高度相差.00001、FP Display为保存前高精度而源已四舍五入。
  为遵守只迁移姿势，本轮保留这些差异；手臂canonical检查和对应geo绑定通过，未虚报全资源精确一致。
  用户此前“能用”属于旧候选，不能代替本次新绑定的实机目视验收。

2026-09-07；起始master / 7685f61，沿用当前未提交工作树。先完成git状态/分支/HEAD
与四份审计/规范读取，再移除DEV门控、复核引用并清理legacy，随后迁移资产/Runtime。
进入任务前的dirty清单见本页末尾所链接的Runtime Path Audit；此外该审计文档、
其只读audit工具及本页审计摘要也是本轮已有文件。没有重置或提交现有修改。

### Authority 与保留职责

- Blockbench源：`src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`。
  枪物理尺寸、Ready双手、Fire/Reload手部、fp_root侧开和FP Display在此制作。
- FirstPerson仅接管主手P9-01 Service Pistol并取消原版两次hand pass，保留camera/equip context；
  Presentation只剩`applyEquip`的-0.6×equipProgress，无静态艺术BASE/Reload曲线。
- Renderer保持Gecko生命周期、RIG节点元数据、手部layer；第一人称不重写bone scale或手部pose。
- HandLayer→NativePlayerArmRenderer→B_skin→当前PlayerModel完整arm/sleeve是唯一生产手部路径。
  真实skin/Classic/Slim/sleeve开关与隐身处理、独立矩阵/buffer、ModelPart finally恢复保留。
- NativeHandBinding仍为固定刚性T(-centreX,-10,0)/16，和武器/状态无关。
  NativePlayerArmRenderer的canonical scale=1；保留完整父矩阵，不正交化、不改成.246。
- Input、Item/controller、服务端Actions、网络、音效、无限弹药原型均未重构。
  非FP继续CROSSBOW_HOLD；其尺寸兼容分支在下文单独说明，不用于FP调姿。

### 清理结果与恢复

测试FAIL/PASS只记录结果，不再改变默认handFilter。默认dev==release：双手。
仅用户显式`/afl_nativegun_debug right|both`选择测试视图；与测试是否通过无关。
没有发布DEV gate，也没有测试失败时的几何fallback。

复核引用后删除：

- D/NativeGunMatrixChecks.java
- D/NativeGunReloadGripChecks.java
- D/LegacyHandMapping.java
- D/NativeGunArmTrace.java
- D/NativeGunArmClearance.java
- C/P901ReloadGrip.java

D=src/dev/java/com/antaurora/apofirstlight/dev/；
C=src/main/java/com/antaurora/apofirstlight/weapon/client/。
SmokeCheck的历史private presentation/reload群、ArmChecks的legacy方法、
PlayerPose.apply删除；有效Contract、Smoke、require/vertices/Bounds、PistolArmPoseChecks保留。
PresentationTrace改为读取source fp_root旋转，不再读取Java侧开权重。
原文件及源资源可从本地忽略归档
`.gradle-user/asset-backups/native-gun-v05-entry-20260907.zip`恢复；未删除TaCZ或其他模型。

### OLD / NEW scale chain

| 范围 | 旧V0.4.9 | 当前V0.5 |
| --- | --- | --- |
| gun物理几何 | 已有.5迁移，另外source动画/Java各维护gun .8 | 将已有.8效果烘焙到gun几何及机械空间；不重复.5 |
| FP整套构图 | Java BASE .82 × source Display .5 | source Display .41，Java静态BASE=NONE |
| gun运行时额外比例 | .8覆盖source scale，每帧保存/恢复 | 无FP额外scale，无gun scale轨道 |
| 完整手臂 | 位置继承.41，basis正交化后强制.246 | canonical单位1，完整矩阵继承同一Display .41 |
| B_skin | 刚性，scale1 | 不变 |
| Reload侧开 | Java curve/.3除数 + source weapon_root | source fp_root keys + 原weapon_root |

gun_model_root保留为中性组织节点。77个gun cube、所有逐面UV与贴图保持设计；
bounds/pivot/inflate和65个枪内position keys统一乘.8（围绕(.1,7.75,9.2)），
包括slide、barrel、magazine/guide、枪口/抛壳/瞄点。删除4个恒定.8 keys。
握把壳宽1.92，套筒主体宽2.24，slide最大后坐1.28。标准参考臂不缩放。

### Source Ready / Fire / Reload / Display

新hierarchy：root→fp_root→weapon_root；
其下并列gun_model_root/gun、right_hand_motion/anchor/reference、
left_hand_motion/anchor/reference。额外Slim参考臂默认隐藏、export=false。
源与runtime有20 bones /77 gun cubes/183 keys（Fire59、Reload124）。

- READY初始V0.5候选为右anchor=(.35,6.8,8.6)、R=(-50,0,0)；左=(-1.65,6.2,6.7)、R=(-48,-18,-18)。
  现行值已由本页顶部“按用户原姿势等价迁移”取代，以下仅描述初始迁移过程。
  motion静态旋转归零，reference严格固定Classic/Slim canonical bounds。
  右主握、左低/前/左支撑的候选在源中完成，不是Java offset。
- FIRE仍.14s：原动作角度、时点、音效不变，仅机械平移按物理比例同步；双手继承共同后坐。
- RELOAD仍1.30s：fp_root pivot=(0,8,6)，源rotation峰值=(8,32,-16)，position峰值=
  (2.6666666667,14.9333333333,-13.3333333333)模型单位；原Java smoothstep被采样为
  源linear keys：0–.24每.02s进入、保持到.85、.85–1.18每.03s回零，1.30保持零。
  Java的+32/-16/+8与DISPLAY_SCALE=.3硬编码已退出FP。
- 左motion .08–.22进入弹匣接触偏移(.45,-2.7,1.8)，.22–.93使用magazine同一平移delta，
  .98–1.23回支撑；右手始终刚性跟随共同carrier。guide仍为空bone，非新弹匣绘制器。
  magazine .52/.60的afl_hold可见性、音效.40/.95时点及音频内容不改。
- 原BASE T(.51,-.44,-.70)、Ry(-4)Rx(-4)、S(.82)合并到用户保存的FP Display：
  right T=(1.0014804164,-7.2445006303,-11.6862374563)，
  left T=(.5924791558,-7.2445006303,-11.7148376105)，
  R均=(-4.0097357701,-3.9902403982,-.2794778767)，S均=.41。
  静态矩阵误差2.1443e-12；不是另行优化camera。仅导出两个FP context，其他Display保留。

**非FP兼容的必要例外**：Renderer遍历时跳过fp_root容器变换，但继续绘制其子树；
gun分支先应用历史around(0,8,6)×2，再应用around(.1,7.75,9.2)×1.25。
在列向量作用顺序中先撤销本次.8，再撤销历史.5，从而保留TP/ground/fixed原尺寸和机械行程。
这仍是仅非FP的历史兼容Java变换，未宣称整个Renderer已完全零数值。
原CROSSBOW_HOLD及非FP Display保持；未来world资产迁移另行处理，不能拿此分支调FP。

### 艺术工作流 / 导出工具

保存指定bbmodel，执行`node tools/export-native-gun.mjs --write`，
再执行`node tools/export-native-gun.mjs --check`、`tools/verify-p9-01.ps1`。
文件导出器明确支持当前numeric position/rotation/scale、linear/step、cube/bone/逐面UV子集，
不支持的模式会报错；不会悄悄丢弃动画或参考臂额外变换。它不写源/贴图/声音，
仅生成geo、animation和两个FP Display。当前无可调用的Blockbench MCP，不虚报原生GUI导出。
普通编辑模式即显示正式枪体大小，不必播放常量scale动画才能看见比例。
完整艺术规范见 [Authoring Equivalence 3.0](native-gun-hand-locator-authoring-standard.md)。

### 验证记录与边界

- compileJava --offline --stacktrace PASS（32s）；processResources --offline PASS（26s）；
  build --offline --stacktrace PASS（36s）；现有弃用API警告，test NO-SOURCE。
  初次编译暴露DEV快照Map的CoreGeoBone/GeoBone类型不匹配，已修正后重跑成功。
- 精确导出校验PASS：77 cubes/20 bones/183 keys、Classic/Slim规范参考、贴图和OGG签名；
  不同步无关Display。Node JSON -0 与序列化0的检查差异已规范化后通过。
- 源几何迁移非FP回归：Fire/Reload共321,552个角点比较，最大误差1.2119e-10模型单位。
  静态BASE迁移等价误差2.1443e-12。它们不是深度/GPU画面通过。
- 发布JAR：DEV classes=0，已删legacy classes=0，assets/tacz entries=0。
  Native Java未新增TaCZ imports/dependency；git diff --check PASS。
- 本轮05:47:08实际启动Forge图形客户端，OpenGL4.6/OpenAL及资源加载完成。
  05:47:37：真实GeckoLib40动作/7,320帧、1,905,280对应点PASS；
  Classic maxError=4.2252086e-7、Slim=4.290396e-7、rightGripDrift=5.364418e-7（epsilon1e-5）。
  直接源reference角点与实际Vanilla arm/sleeve比较，无旧预览正交化或DEV内存折叠。
  测试前后恢复骨骼；日志明确No visibility changes。资源/hold/stop-packet smoke PASS。
- 数值动画delta取同帧Gecko求值；source/export key另行核对，不把这种数学一致性称为GPU视觉等价。
  新版完整手掌遮挡、双手角色、magwell可读性、F5/GUI实际按键回归仍待用户。
  用户实机反馈“能用，但是后面还需要改”；记录为基本可用，不等于各姿势视觉批准。
  READY/FIRE/RELOAD USER VISUAL=PENDING_USER；未将P9-01 Service Pistol标成正式视觉批准资产。
  用户已进入世界，本轮不再自行调参；05:54:36日志确认所有维度存档完成，05:54:37客户端Stopping。
- 不新增ADS/真实弹药/empty reload/slide lock/shell/damage/hitscan/Noise/Tinnitus/Suppressor。
  无截图/preview文件、commit、push。

### 本轮文件

编辑：上述bbmodel；A/geo/p9_01.geo.json；A/animations/p9_01.animation.json；
A/models/item/p9_01_in_hand.json（A=src/main/resources/assets/apocalypse_firstlight/）。
生产C：NativeGunRig、NativePlayerArmRenderer、P901FirstPerson、
P901Presentation、P901Renderer、P901PlayerPose。
DEV D：NativeHandVisualGate、NativeHandContractChecks、NativeGunRuntimeSmokeCheck、
NativeGunArmChecks、NativeGunPresentationTrace。六个删除文件见上。
工具：新增tools/export-native-gun.mjs，更新tools/verify-p9-01.ps1。
文档：本页、native-gun-hand-locator-authoring-standard.md、afl_weapon_art_standard_v1.md、
docs/01 - 系统设计/枪械/枪械系统.md、docs/03 - 制作清单.md。
HandLayer/Binding/RenderMatrices/Model/模板未在本轮另改；其已有dirty状态不冒认成本轮新增。

## 历史记录：以下V0.4.x参数与“当前”说明均已由上方V0.5取代

下方保留审计证据与失败尝试，不作为现行艺术参数/可见性规则；既有玩法和音效历史仍有参考价值。

## Current V0.4.9 — 枪体与通用玩家手臂缩放解耦（候选）

### 最新：再次同步用户 First-Person Display（2026-09-07）

- FP right translation=(-8.75,-.25,0)、left=(-9.25,-.25,0)，两者统一scale=(.5,.5,.5)，无rotation。
  仅更新p9_01_in_hand.json的两个FP项；bbmodel、geo、animation、生产Java及其他display不改。
- 相较上一轮：Y从.5降至-.25，非均匀scale替换为均匀.5。gunVisualScale=.8、
  framework arm style=.246仍冻结；源里第三人称新translation/scale不在本次导出范围。
- FP专项核对、构建输出参数与diff-check通过；processResources/build离线29s成功。
  04:43:52启动图形客户端，04:44:21日志确认加载新Display；40动作/7,320帧/
  2,006,420对应点PASS，Classic maxError=4.7782567e-7，Slim=3.6910464e-7，
  rightGripDrift=1.847744e-6（均<1e-5）；资源smoke PASS。未做游戏内视觉验收。
- 验证后按用户要求核对并终止本次客户端PID26428；runClient退出-1为主动停止。
  本次仅runtime display资源及直接相关文档变化，未修改测试代码或生产Java。

### 前次 First-Person Display 同步记录（非当前参数）

- 保存的bbmodel FP right translation=(-8.75,.5,0)、left=(-9.25,.5,0)，
  两者scale=(.76484,.3,.41133)，无rotation。仅同步到
  `src/main/resources/assets/apocalypse_firstlight/models/item/p9_01_in_hand.json`
  的这两个display项，未导入source新第三人称参数，GUI route/icon不改。
- 用户保存的source仍为reference cube带变换的authoring形式，与已转换geo的姿势等价。
  本次不覆盖bbmodel，不再转换geo/animation。DEV比较在内存中等价折算reference，
  再读取实际runtime display参数进行矩阵检查；非均匀display的独立source预览用
  叉积正交化，不能再沿用旧固定.3或仅按列归一化的测试。
- 生产camera、gunVisualScale=.8、framework arm style=.246、B_skin均不改；
  用户非均匀display会改变枪体轴向尺寸，但玩家arm/sleeve仍使用固定框架尺寸。
  Reload原补偿分母.3保持旧校准，不在本次额外重调Reload路径。
- `tools/verify-p9-01.ps1 -FirstPersonDisplayOnly`只检查两项FP同步；不将
  未同步的第三人称或未canonical化source误报为已完成全资源导出。完整旧验证仍严格。
- processResources/build离线执行41s成功，构建输出的两项FP参数逐项确认；diff-check PASS。
  本次runtime仅改in-hand JSON，DEV检查与FP专项验证脚本同步，其余生产代码/geo/animation/source不改。
- runClient于04:35:15启动，04:35:43日志确认读取新版非均匀Display。
  04:35:44：40动作/7,320帧/2,006,420对应点PASS；Classic maxError=3.1292439e-7，
  Slim=3.042048e-7，rightGripDrift=8.2701445e-7；.6/.8/1.0独立缩放及资源smoke PASS。
  仅资源加载/数值验证，不声称进入世界或完成视觉验收。
- 按用户要求，验证后核对窗口标题与启动时间，已Stop-Process终止本次客户端PID16588；
  runClient随后exit -1属于主动停止，不是前述build失败。以后不未经明确请求启动客户端。

### 以下为此前资源/架构历史（验证不自动覆盖最新Display）

### 最新：用户参考臂姿态同步（2026-09-07）

用户在同一bbmodel内摆好参考臂，左臂已自行恢复4×12×4。本次仅将reference cube的
位移/旋转等价折算到canonical hand_anchor，再将reference恢复identity，确保
用户编辑器姿势能够驱动真实玩家手臂，而不是导出一份忽略export=false参考臂的旧定位。
右anchor origin=(.1,8.42206877,1.46570503)、rotation=(-60.5,0,0)，继承原carrier后
READY手端=(.1,6.66047434,2.07167726)、总X=-82.5°。
左anchor origin=(-.24988004,9.99753260,-.71193481)，rotation保持
(-70.75142,-42.38461,28.67797)。左右reference均为4×12×4，rotation=0，origin与anchor一致。
逐角点检查：折算前后最大误差<4e-9 model units，保留用户姿势，没有另调比例或握持。
gun_model_root pivot=(.1,7.75,9.2)/scale=.8、固定arm style=.246、B_skin、动作和
音效/玩法不改；原136动画keys按最新source核对同步，77 runtime cubes不增加参考臂。
geo/animation为直接文件同步，不声称调用不可用的MCP导出器。DEV Ready期望姿态同步。
资源校验及git diff --check PASS；compileJava/processResources/build --offline --stacktrace
合并执行46s BUILD SUCCESSFUL（现有弃用API警告，test NO-SOURCE）。本次只请求导出编译，未启动新版客户端；以下04:08日志
属于此次用户姿态修改之前，不能当作新姿态的游戏内验证。用户认可的是BB姿势，实机仍待验。

### V0.4.9 架构与前一候选验证（姿态数值以下方历史记录为准）

- Live audit：此前枪体资产 .5 迁移并没有缩小 locator/reference；B_skin=1。
  残余耦合来自 P901HandLayer 直接继承共同 PoseStack 的 .82×.30=.246
  basis，camera/display 改小也会缩手；不是 locator 驱动失效。
- 新增 source/geo `weapon_root → gun_model_root → gun`，双手 motion/anchor
  仍为同级独立分支。77 cubes、UV、贴图、原组 pivot/rotation 与132原关键帧不改。
  新根 pivot=(.1,7.75,9.2)，绕 READY 手端缩枪；right anchor/carrier/ref 不再微调。
- `P901Renderer.RIG` = NativeGunRig(root、左右locator、gunVisualScale=.8)。
  Root scale 绘制时设置而非叠乘，finally 恢复；所有FP动作同值，非FP值1。
  原 non-FP gun 分支2倍逆物理补偿保留，不改 CROSSBOW_HOLD、GUI icon 或 HUD。
- 新 `NativePlayerArmRenderer` 不依赖任何具体枪械。输入动画后的 locator，保留其
  position/rotation，正交化 basis 去除 inherited scale/shear，固定框架 arm style=.246；
  B_skin仍仅T(-centreX,-10,0)/16。实际PlayerRenderer全长Classic/Slim arm+sleeve，
  skin/visibility/lighting保持，PartPose/scale临时归零/1后恢复。无新特殊手几何。
- 新链：gun=P×A_weapon×T(p)×S(.8)×T(-p)×A_gun_local；
  arm=T(animated locator position)×R(locator)×S(.246)×B_skin。
  屏幕投影仍受深度/FOV影响，但固定手臂模型尺寸不读取 per-weapon display/gun scale。
- Source Fire/Reload 各加2个恒定 gun_model_root scale=.8关键帧，共136 keys/19 bones。
  原 action .14s/1.30s、轨迹key值、rotation、音效时间均不改。
  slide位移1.6由父级自动成1.28；magazine/reload_magazine和枪体locators同样缩一次。
  weapon_root及hand translation不乘gunVisualScale。左手数据不改，缩枪后的左手与
  弹匣接触没有重新验收；本轮只开放DEV Right Only READY，Reload视觉 NOT_TESTED。
- 新source-only模板 `src/main/blockbench/templates/afl_first_person_player_arm_rig.bbmodel`：
  Classic左右4×12×4、Slim左右3×12×4，distal origin/-Y forearm/+Z palm/+X lateral。
  全部export=false，只复用旧中性reference纹理，runtime从不读取模板mesh。
- 在Blockbench选Fire/Reload的0秒查看.8视觉层；普通编辑模式仍是原始几何。
  没有可调用的Blockbench MCP，本轮直接同步文件，不声称执行GUI/MCP导出。
- 构建：compileJava单独42s PASS、processResources单独29s PASS、build单独31s PASS；
  增补DEV检查后的最终compileJava+build 46s PASS。资产验证为77 cubes /19 bones /
  136精确keys，diff-check PASS（仅原有CRLF提示）。
- 客户端于2026-09-07 04:08:19启动Forge图形目标，OpenGL4.6已初始化。
  04:08:53 ARM BASIS、.6/.8/1.0 GUN SCALE检查PASS（含完整Classic/Slim arm/sleeve
  顶点、normal、镜像、非均匀basis与退化保护）；77 cubes /1,848 UV corners PASS。
  04:08:54：40动作 /7,320帧 /2,006,420对应点PASS；Classic maxError=3.2132652e-7、
  Slim=3.0401657e-7、rightGripDrift=1.1920929e-6，均<1e-5。
  GeckoLib资源加载与Fire/Reload stop packet smoke PASS，DEV已开放Right Only。
- Fire/Reload回归结论只覆盖数值执行/回位/固定手臂尺寸，不等于玩家按键与动作观感验收。
  第三人称原逻辑保留、新visual root非FP归1的数值测试PASS；本轮F5实机仍待用户。
  离线认证/更新检测出现网络权限警告，但未阻止资源加载和上述检查；不修改网络/认证配置。
- **READY visual=PENDING_USER；尚不宣称真正握持/无穿模/比例通过。**
  No screenshot / preview image / gameplay / audio / TaCZ change / commit / push。

### V0.4.9 本轮文件清单（不含进入任务前已有的其他dirty文件）

- `src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`
- `src/main/blockbench/templates/afl_first_person_player_arm_rig.bbmodel`（新增）
- `src/main/resources/assets/apocalypse_firstlight/geo/p9_01.geo.json`
- `src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json`
- `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeGunRig.java`（新增）
- `src/main/java/com/antaurora/apofirstlight/weapon/client/NativePlayerArmRenderer.java`（新增）
- `src/main/java/com/antaurora/apofirstlight/weapon/client/P901Renderer.java`
- `src/main/java/com/antaurora/apofirstlight/weapon/client/P901HandLayer.java`
- `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeHandBinding.java`（仅注释）
- `src/main/java/com/antaurora/apofirstlight/weapon/client/P901Presentation.java`（仅注释）
- `src/dev/java/com/antaurora/apofirstlight/dev/NativeHandContractChecks.java`
- `src/dev/java/com/antaurora/apofirstlight/dev/NativeHandVisualGate.java`
- `tools/verify-p9-01.ps1`
- `docs/dev/native-gun/native-afl-gun-framework-v0.md`
- `docs/dev/native-gun/native-gun-hand-locator-authoring-standard.md`
- `docs/dev/native-gun/afl_weapon_art_standard_v1.md`
- `docs/01 - 系统设计/枪械/枪械系统.md`
- `docs/03 - 制作清单.md`

## 历史记录（以下版本不代表V0.4.9当前缩放契约）

## Previous — Right-Hand Ready Grip Recalibration (visual acceptance not established)

- Live audit: physical gun migration is already present (grip width 2.4 versus
  Classic arm width 4 / Slim 3). B_skin=1 and full arm/sleeve path are active;
  no 1.4 enlargement. Do not repeat the gun shrink or modify the camera to hide
  remaining grip/orientation issues. Source is authoritative, not an open stale tab.
- Only source right_hand_anchor/right_arm_reference origin and reference cube
  position change, plus the matching geo anchor and DEV expected-pose checks.
  Origin `(0,7.325285,9.357840)` → **(.1,6.761943,8.483114)**;
  anchor rotation `(-18,0,0)` → **(-43,0,0)**. Original right_hand_motion remains
  at -22° X, so composed READY cap changes `(0,8.6,9.8)` → **(.1,7.75,9.2)**,
  total X=-40° → **-65°**. Reference rotation=0, bounds remain 4x12x4.
- Forearm direction=(0,-.422618,.906308); palm normal=(0,.906308,.422618).
  Palm plane is 25° from model horizontal instead of 50°, without yaw/roll.
  This flattens the palm and directs the full forearm toward the player rather
  than laterally. Cap adjustment (+.1,-.85,-.6) approaches the smaller backstrap.
  Controlled grip overlap is intentional; no separate finger geometry is added.
- Gun geometry/UV, .5 physical scale, B_skin, camera, left-hand data/visibility,
  all Fire/Reload keys and timings, audio, HUD/icon, gameplay and third-person
  compensation are frozen this pass. A shared static right rest pose naturally
  propagates to Fire/Reload; no action-specific override. Those visuals are not
  this pass's acceptance target and cannot be declared unchanged by observation.
- No Blockbench MCP is callable in this session. Source edits and matching geo
  anchor synchronization are direct file changes, not a claimed MCP export or
  live editor update. Close/reopen an old BB tab without saving over newer disk data.
- Offline compileJava (43s), processResources (29s), build (32s), asset verifier
  and diff-check PASS. Entry src hashes show only source bbmodel, geo and DEV
  NativeHandContractChecks changed; no production Java or animation edits.
- Client launched 2026-09-07 03:30:37. At 03:31:08, 77 cubes / 1,848 UV corners
  passed; at 03:31:09, Classic maxError=3.4240261e-7, Slim=3.0135735e-7,
  rightGripDrift=9.536743e-7 (epsilon=1e-5), 40 actions / 7,320 frames passed.
  Only numerical compatibility is asserted, not other-action visual acceptance.
- Static source OBB audit for Classic/Slim: base arm overlaps grip/backstrap and
  seated magazine, not slide/barrel/trigger guard. The inflated sleeve envelope
  also intersects frame_primary at its back edge; actual skin transparency and
  visible clipping require user review. This candidate is NOT collision-free.
- **READY visual = NOT VERIFIED / NOT PASSED**.
  User review of a new in-game READY image is required; numerical equivalence
  alone does not prove true grip, thickness, or absence of visible clipping.

## Previous — Weapon-to-Hand physical recalibration (not visually accepted)

- User explicitly rejected V0.4.8.1 and its screenshot: the gun remained too
  large and hand read as a narrow post behind it. That implementation is FAILED,
  not merely awaiting approval. This pass changes the asset, not just its camera.
- In editable `src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`,
  only **gun + its 11 descendants / 77 cubes** receive `F(p)=P+0.5*(p-P)`,
  P=(0,8,6). Cube bounds/origins and bone pivots are baked at the new dimensions;
  no runtime first-person scale trick and no scaled weapon_root/hand hierarchy.
  Grip shell width 4.8→2.4, main frame length 33→16.5 model units. Standard
  Classic 4x12x4 / Slim 3x12x4 arms remain untouched.
- Remove the rejected 1.4 enlargement: B_skin returns to **T(-centreX,-10,0)/16**,
  scale=1, complete skin arm/sleeve. No short hand, thin palm, state-specific scale.
  First-person camera T=(.51,-.44,-.70), yaw/pitch=-4°, common .82 and display .30
  remain byte-for-byte unchanged this pass. Raw source references again match
  standard runtime arm size; camera view is still a separate common parent.
- Right hand is placed independently, not batch-scaled: source anchor/reference
  origin=(0,7.325285,9.357840), child rotation=(-18,0,0); original -22° carrier
  retained. READY distal cap=(0,8.6,9.8), total X=-40°, forearm=(0,-.766044,.642788).
  Palm volume is behind/overlapping the reduced upper backstrap. Fixed right
  locator/frame relationship applies to Ready/Fire/Reload; visual grip is unproven.
- **65 gun-subtree position keys** scaled by .5: slide, front/rear sights,
  sight_anchor, barrel, magazine and reload_magazine. Slide peak 3.2→1.6 at .04s;
  magazine/guide maximum path at .52s (-3,-24.1067802,9.7397714) becomes
  (-1.5,-12.0533901,4.8698857). Gun rotations, scale keys, interpolation, UUIDs,
  132-key count, Fire=.14s/Reload=1.30s and audio times remain unchanged.
- Muzzle/ejection/sight pivots are rescaled with gun geometry. weapon_root
  position/rotation keys are audited and retained: they are shared held-rig motion,
  not mechanical travel. Its pivot equals P. Runtime side-open presentation stays
  unchanged; it is camera composition, not magazine extraction distance.
- Left anchor/carrier/reference are not resized or repositioned. Existing 20
  left-hand position keys are remapped only for the resized magazine workspace:
  delta_new=(1-w)*delta_old+w*(.5*delta_old+.5*(P-A)), A=old left locator origin;
  w=0 through .08s, linear to 1 at .22, held through .98, linear to 0 at 1.23.
  Times/rotations unchanged; Ready endpoint unchanged. Left stays DEV-hidden;
  this preserves the path relationship, not a completed left-hand contact solution.
- 76 box-UV cubes become fixed per-face UV, retaining original face rectangles.
  Otherwise GeckoLib floors the new cube dimensions and would sample a different
  texture region. PNG/embedded texture and skin UVs are unchanged. One existing
  per-face cube remains unchanged. Runtime UV corner comparison checks the real
  GeckoLib cube baker, not just rectangle area. Exported assets are an audited
  mathematical update of the existing geo/animation, **not an unavailable MCP
  exporter invocation**. No third-party assets copied.
- Non-first-person gun branch alone applies F^-1 around P **after weapon_root**,
  before gun/children transforms. This compensates the shared asset migration for
  third-person/ground/fixed renders; it does not affect FP or hand branches.
  Uniform scaling commutes with retained rotations; half-size pivots/positions
  recover their previous values under F^-1. Third-person code path has a necessary
  compatibility transform; intended appearance/pose is unchanged, not reauthored.
  Static icon, model display JSON, third-person ArmPose, audio, HUD, gameplay and
  TaCZ files remain untouched.
- Validation: offline compileJava (34s), processResources (24s), build (26s),
  verifier (77 cubes/18 bones/132 exact keys) and diff-check PASS. Entry source
  hash comparison confirms only bbmodel/geo/animation, NativeHandBinding,
  P901Renderer and DEV NativeHandContractChecks changed. Camera,
  third-person ArmPose/display JSON, textures, sounds and gameplay are unchanged.
- Client launched 2026-09-07 03:14:25. At 03:14:48 the actual GeckoLib cube baker
  validated **77 cubes / 1,848 per-corner UV correspondences**, including the
  non-FP inverse-geometry transform. At 03:14:49, 40 actions / 7,320 frames passed:
  Classic maxError=3.4240261e-7, Slim=3.0538294e-7, rightGripDrift=8.6426735e-7,
  epsilon=1e-5. These prove numerical mapping/return, not contact or visual quality.
  03:14:57 world-entry detached-player checks passed 960 setupAnim calls; actual
  third-person visual comparison is still a user check.
- **Ready / Fire / Reload user visual PASS: NOT VERIFIED.** Current candidate
  cannot be marked complete or approved for left-hand polish without that review.
  The specified bbmodel was checked on disk (03:07:28 save): frame_primary length
  16.5, grip width 2.4, source reference 4x12x4. An already-open Blockbench tab can
  retain old in-memory data; reload from disk without overwriting it with that tab.

## Historical V0.4.8.1 — rejected single-right READY candidate

- V0.4.8 user's screenshot **failed**: lateral forearm, hand beside grip and too
  thin. Camera-only calibration did not solve holding. This pass targets READY
  only, not left-hand restoration or Fire/Reload visual redesign.
- Right source locator origin `(4.265,9.1,6.3)` → `(0,6.349441,12.307031)`;
  rotation `(-49.14366,22.47242,18.29368)` → `(-10,0,0)` degrees. The unchanged
  right_hand_motion parent contributes -22° X: resulting READY distal cap is
  **(0,8.8,12.9)**, total X=-32°, forearm **(0,-0.848048,0.529919)**. Centred on
  grip X behind the upper backstrap; arm extends down/back, not sideways. Grip
  shells use -22° about (0,9,7.6); extra 10° directs the forearm behind them.
  Distal volume deliberately overlaps the grip; full cuboid arms have no fingers.
- Generic fixed binding is **S(1.4) × T(-centreX,-10,0)/16**. CentreX unchanged:
  Classic right=-1/left=1, Slim right=-0.5/left=0.5. Hand cap remains at locator.
  Original 4x12x4 / 3x12x4 full arm geometry, UV and sleeve route are preserved.
  No weapon/state conditional. The same 1.4 applies to the hidden left arm too;
  left locator, animation, logic and DEV visibility are not changed.
- Common camera is **T(0.51,-0.44,-0.70) × Ry(-4°) × Rx(-4°) × S(0.82)**;
  mirror X/yaw for left dominance, display=.30 and equip lowering=.6 unchanged.
  Effective gun scale=.246, arm=.3444 (previously .276/.276): gun linear scale
  -10.87%, arm +24.78%, arm/gun relative scale +40%. These are transform ratios,
  NOT measured screen-area improvements. Projection also depends on new depth.
- Source right locator/reference origin and the matching runtime anchor are
  synchronized. All 77 gun cubes and 132 animation keys remain unchanged. No
  Blockbench MCP exporter was available/called: audited locator-only JSON sync,
  not a new gun geometry export. Right reference remains standard 4x12x4.
- Numerical preview explicitly adds S(1.4) about the source reference origin,
  with an independent expected constant, then compares real Classic/Slim emitted
  arm/sleeve vertices. **Stock Blockbench does not automatically apply Java
  binding scale or camera**; bare references are not a final runtime-size preview.
  Equivalence concerns this explicitly composed mathematical preview path.
- All actions inherit the static baseline; no state-specific workaround. Right
  rest pose changes therefore propagate into Fire/Reload, whose visual regression
  is deferred. Their keys/timelines and Reload side-open curve are unchanged.
  Third-person, sounds, icon, HUD, ammo and TaCZ remain untouched.
- Offline compileJava (36s), processResources (26s), build (26s), resource
  verifier and diff-check PASS. Entry SHA-256 comparison: only NativeHandBinding,
  P901Presentation, DEV NativeHandContractChecks, source bbmodel and
  runtime geo changed under src; no source deletions. Source semantic diff is
  limited to right locator/reference groups and the right reference cube.
- Client launched 2026-09-07 02:51:52; 02:52:14 numeric PASS: Classic
  maxError=4.256623e-7, Slim=3.9198835e-7, rightGripDrift=7.1525574e-7,
  epsilon=1e-5; 40 actions / 7,320 frames / 1,905,800 point comparisons.
  This is compatibility/equivalence evidence, NOT Fire/Reload visual acceptance.
  READY source OBB audit finds intended grip/backstrap overlap and intersection
  with the seated magazine inside the grip; it is **not** a collision-free claim.
  No slide/barrel/trigger cube intersection in that static Classic audit. Reload
  clearance is deferred. Full-arm geometry still requires actual screenshot review.
- **READY visual = NOT PASSED / PENDING_USER**.
  Supplied before screenshots establish failure; need a new same-view image to
  judge the candidate. No screenshot or preview file generated by the agent.

## Historical V0.4.8 — camera-only candidate, rejected by user

- Only the common first-person camera parent changes. Previously translation
  `(0.60,-0.54,-0.64)`, no extra rotation/scale. Now `T(0.55,-0.47,-0.66) ×
  Ry(-4°) × Rx(-4°) × S(0.92)` for right dominance; left dominance mirrors X
  translation and yaw. Equip lowering remains `-equipProgress * 0.6` on Y.
  `P901FirstPerson` calls `P901Presentation.applyBaseline` once,
  before item display and the animated hierarchy, with no action-state input.
- Source/item display is still 0.30; **effective common scale is 0.276**, for
  the gun and full arm/sleeve alike. Canonical locators, source reference arms,
  `NativeHandBinding` / B_skin=1 and HandLayer are unchanged. There is no separate
  right-hand enlargement or contact correction. Uniform scale preserves physical
  gun/hand size ratio; the calibration targets projection, distance and visible
  forearm through a slightly raised, rotated, more distant common view.
- Ready/Fire/Reload inherit this same parent. Existing Reload presentation
  offsets, +32° yaw / -16° roll / +8° pitch and 0–0.24 / 0.85 / 1.18s curve
  are untouched, but their final camera-space effect is composed with the new
  parent too. This is not a claim of identical old camera-space Reload motion.
  Gun/hand animation keys, magazine/left-hand logic, audio, third person, HUD,
  source geometry/textures and gameplay are frozen.
- Equivalence remains `P_common × M_locator × B_skin` versus
  `P_common × M_source_reference`. DEV checks now call the production common
  baseline for both paths, additionally testing handedness/equip/uniform scale;
  the existing 40-action animated comparisons use it too. This verifies geometry
  correspondence, not visual grip/contact or a separately running BB viewport.
  No bbmodel/display JSON changes, screenshots or preview artifacts.
- Verification: offline compileJava (36s), processResources (25s), build (27s),
  asset verifier (77 cubes/18 bones/132 exact keys) and git diff --check PASS.
  Entry SHA-256 comparison of all 1,086 src files shows only Presentation,
  FirstPerson and DEV NativeHandContractChecks changed; no assets/bindings/left
  logic/third-person changes or source removals. Release JAR has zero DEV entries.
- Graphical client launched 2026-09-07 02:37:27. At 02:37:51, numerical PASS:
  Classic maxError=4.3142657e-7, Slim=3.8346974e-7, epsilon=1e-5;
  1,905,800 point correspondences, 40 actions / 7,320 frames,
  rightGripDrift=8.940697e-7. Full arm/sleeve equivalence, stable relative grip
  and return-to-Ready checks passed using the new common parent.
  **Single-right-hand READY user acceptance first**, Fire/Reload afterwards;
  all proportion gates remain PENDING_USER. DEV left visibility is unchanged.
  No V0.4.4 or earlier visual sign-off is carried into this new baseline.

## V0.4.7.1 — third-person ArmPose crash fix

- Crash evidence: `run/crash-reports/crash-2026-09-07_02.14.19-client.txt`, local
  player rendering, `HumanoidModel.poseRightArm:234`, index 10 / array length 10.
  Root cause **CUSTOM_ARMPOSE_ENUM_SWITCH_OOB**: the lazily initialized
  `P901PlayerPose.PISTOL` created `AFL_P9_01` after the synthetic
  switch map was sized. Local bytecode sizes the array from `ArmPose.values()`
  once; it does not resize after enum extension. Forge's default applyTransform
  branch cannot run because the array lookup fails first. This is not proof that
  all properly initialized Forge extensible poses are unsupported.
- Before: main-hand `IClientItemExtensions.getArmPose` returned the custom pose
  (crashing ordinal 10). After: the same item-scoped route returns Vanilla
  **CROSSBOW_HOLD**, ordinal 6. Offhand still returns null for Vanilla fallback.
  No custom enum is created. Ordinary items do not use this extension.
- **User-authorized minimal alternative:** no new post-setupAnim AFL hook.
  Vanilla HumanoidModel/AnimationUtils applies the two-handed main/support pose
  and handedness mirror; PlayerModel propagates it to sleeves before held-item
  layers. The old exact custom angles are not retained as live behavior. The
  deprecated `apply` helper remains solely for historical DEV comparisons.
- No new mixin, renderer replacement, switch-table patch, reflection or TaCZ
  code/dependency. First-person V0.4.7 locator/B_skin/HandLayer, source assets,
  presentation/matrices and DEV B1 right-only gate remain untouched.
- `NativePistolArmPoseChecks` is DEV-only: on world entry, detached Classic/Slim
  models initialize Vanilla switch before querying the pistol extension, then
  exercise both dominant hands, crouching and pistol/stick/empty transitions.
  A bounded RenderPlayerEvent.Post observer records completed actual player
  rendering per camera mode; this is not a visual PASS or automated F5 press.
- Offline compileJava (37s), processResources (25s), build (28s), asset verifier
  (77 cubes/18 bones/132 keys) and git diff --check passed. Entry SHA-256 comparison
  shows only P901PlayerPose changed under existing src files, plus the
  new DEV NativePistolArmPoseChecks; all first-person/B1 files and assets are exact.
  Release JAR contains zero DEV entries. Client launched at 02:22:45; world entry
  at 02:23:20 ran **960 setupAnim calls PASS** on detached Classic/Slim models,
  both main arms, crouching and pistol/stick/empty transitions. Actual pose ordinal=6.
  At 02:23:26 RenderPlayerEvent.Post confirmed completed local-player pistol
  rendering in **THIRD_PERSON_BACK and THIRD_PERSON_FRONT**, actual Slim/RIGHT.
  No original AIOOBE occurred in those observed renders. Repeated F5, actual item
  switching/drop/pickup and visual skin/sleeve/main-support checks still require
  user confirmation; numerical detached-player transitions are not those tests.
  B1 READY single-right-hand visual acceptance remains pending. Earlier V0.4.7
  startup PASS did not catch the subsequent third-person world-render crash.

## Current V0.4.7 hand contract (supersedes historical hand mappings below)

Implemented, not yet visually accepted. Artist-facing contract:
[canonical locator authoring standard](native-gun-hand-locator-authoring-standard.md).

- `NativeHandBinding`: origin = distal hand-cap centre; forearm -Y, palm normal
  +Z, lateral +X. Fixed `B_skin = T(-centreX,-10,0)/16`; Classic centreX is
  right=-1/left=+1, Slim right=-0.5/left=+0.5. Binding scale=1; inherits the
  source 0.30 display scale once (V0.4.8 common parent adds 0.92). No orthonormalization, weapon quaternion,
  contact-offset guessing, per-state scale or geometry switch.
- `P901HandLayer` renders full current-player Vanilla arm/sleeve in
  Ready/Fire/Reload, restores ModelPart state, respects invisibility/sleeve
  settings and uses normal lighting/depth. Detached matrices remain unchanged.
- Original source anchors retain UUIDs as `right_hand_motion` / `left_hand_motion`.
  New child `*_hand_anchor` nodes define canonical frames; source references
  have identity rotation and standard 4x12x4 bounds at those frames. The old
  reference group's endpoint/rotation was factored into the new locator, not
  guessed in Java. The left track only changes its target name to motion;
  all 132 source/export key values, times, interpolation and durations stay intact.
- Source and runtime geo were minimally synchronized: 18 bones, **77 unchanged
  gun cubes**, no exported reference geometry. The runtime animation only renames
  the left target. No full native Blockbench MCP export was performed this turn;
  no unavailable exporter success is claimed. Existing native export script remains usable.
- `P901ReloadGrip` is deprecated and uncalled by the default renderer,
  retained until all visual gates pass. Legacy mapping/checks remain in DEV
  for reference, no longer startup acceptance checks. No automatic fallback.
- `NativeHandContractChecks` compares real baked Classic/Slim base/sleeve vertices
  with source reference bounds and hierarchy, at epsilon 1e-5; checks the actual
  GeckoLib controller over repeated Fire/Reload and records frame-relative grip
  drift and seven left/magazine timeline observations. Same-frame source animation
  deltas come from the evaluated controller; key fidelity is checked separately.
- DEV gate: numerical PASS enables right-only B1. `/afl_hand_gate right|both`
  selects visibility but is not a visual sign-off. DEV code is excluded from the
  release JAR; release uses the unified full-arm route for both hands.
- Gun source geometry/textures, Ready/Fire/Reload presentation, magazine motion,
  audio/timing, third-person, static icon and gameplay are frozen. Source/geo
  semantic comparisons and 132-key asset verification passed. Build, startup and
  numerical results are recorded below when run; all user visual gates are pending.

The V0.4.5.1 side-face patch was rejected and the task terminated; all later
"pending" or historical numerical PASS descriptions below are not acceptance.
The original source hashes below are historical, not hashes of the V0.4.7 locator revision.

### V0.4.7 verification — 2026-09-07, B1 checkpoint

- Offline `compileJava --offline --stacktrace` passed (31s), `processResources
  --offline` passed (25s), `build --offline --stacktrace` passed (27s).
  Initial compile errors were obsolete ReloadGrip references to removed helpers;
  the retained historical class is now self-contained, not called by HandLayer.
- `runClient` launched at 02:10:45, OpenGL 4.6 initialized on the RTX 3080 Ti,
  resource reload and OpenAL completed. At 02:11:09 the new actual-geometry
  test and resource/stop-packet smoke check passed. This is graphical startup
  plus numerical verification, **not user visual acceptance**.
- Classic maximum source/runtime point error **3.4240261e-7**, Slim
  **3.3394895e-7**, renderer-space epsilon **1e-5**; 1,904,240 point comparisons.
  Both base arms and inflated sleeves emit all 24 vertices; sizes remain standard.
- 40 Fire/Reload actions / 7,320 evaluated frames, with the existing natural
  GeckoLib settle period: both hands and gun return to their own Ready matrices.
  `inverse(M_frame) * M_right_locator` maximum matrix-element drift
  **7.4505806e-7**; no writes to gun matrices. These numeric PASS flags are
  contract/return tests, not zero-intersection or natural-grip claims.
- Seven same-frame hand/magazine observations are below (model units, rounded).
  `reload_magazine` guide matches the magazine pivot at these samples but has
  no visible cubes. Relative positions are recorded, **not interpreted as proven
  palm contact or user-visible magazine synchronization**. That remains pending
  after the Ready gates. The inherited 0.60s hold boundary can evaluate just before
  or after visibility switches due to floating-point clip time; no keys were changed.

| Phase/time | Left locator XYZ | Magazine / guide pivot XYZ |
|---|---|---|
| start 0.00 | -4.995, 8.000, 4.900 | 0, 8.000, 7.600 |
| mag out 0.40 | -11.980, -20.550, 18.430 | -3.746, -8.578, 14.130 |
| old clear 0.42 | -12.530, -22.750, 19.210 | -4.295, -10.780, 14.910 |
| new approach 0.60 | -16.360, -25.690, 20.510 | -8.121, -13.720, 16.210 |
| insertion 0.85 | -8.652, -7.967, 13.830 | -0.546, 4.119, 9.605 |
| seated 0.93 | -7.757, -4.462, 12.580 | 0.278, 7.685, 8.397 |
| return 1.30 | -4.995, 8.000, 4.900 | 0, 8.000, 7.600 |

- Current DEV view is **right-only READY (B1)**. User confirmation is required
  before B2 both-hands, Fire and Reload visual gates. All four are NOT_TESTED;
  Classic/Slim are numerically verified, neither is newly visually accepted.
- V0.4.7 source SHA-256:
  `55e583e8eeda6646428eeee64c56d38a158da1486258e092529f10561fac170d`.
  Semantic baseline comparison confirms all gun elements, textures, display,
  non-hand geo bones and animation keys unchanged (only left target renamed).
  All other existing `src/` files outside the listed hand/DEV/source/geo/animation
  edits retain their entry hashes, including audio, presentation and gameplay.
- Asset verifier passed 77 cubes / 18 bones / 132 exact keys, canonical references,
  source PNG, display and OGG; `git diff --check` passed (only line-ending warnings).
  Release JAR has NativeHandBinding, zero DEV entries and zero TaCZ asset entries;
  Native weapon source has zero TaCZ imports. No screenshots/previews, commit/push.
- Changed production: `NativeHandBinding` (new), `P901HandLayer`,
  `P901ReloadGrip` (historical self-containment only). Source `.bbmodel`,
  runtime geo and animation changed only as above. Tool: `verify-p9-01.ps1`.
  DEV: new `LegacyHandMapping`, `NativeHandContractChecks`, `NativeHandVisualGate`;
  redirected historical `NativeGunArmChecks`, `NativeGunArmTrace`,
  `NativeGunMatrixChecks`, `NativeGunReloadGripChecks`, `NativeGunRuntimeSmokeCheck`.
  Docs: this file, checklist, gun-system, art standard, new locator authoring standard.

## Scope and implementation

Minecraft 1.20.1 / Forge 47.4.22 / Java 17. Existing GeckoLib **4.7.4** and Maven
configuration are retained, not upgraded. In this historical implementation pass,
TaCZ remained installed with unchanged behavior. As of 2026-09-10 the default
development runtime omits TaCZ; `-PaflWithTacz` temporarily restores the comparison
runtime dependency. Native pistol code has no TaCZ imports.

Item `apocalypse_firstlight:p9_01` / P9-01 Service Pistol / 制式手枪 is registered
in `AflItems` and appended to the AFL **Items** creative tab. Its development
tooltip is Native weapon system prototype / 原生武器系统测试版.

This is an infinite-ammo animation/audio prototype. There is no ammo item/count,
inventory consumption, durability, damage, hitscan, projectile, dry-fire, shell
ejection, ADS, attachments, suppressor, Noise or Tinnitus.
GeckoLib writes only its standard `GeckoLibID` render identity to a stack; no
custom gameplay NBT is added. Source-only reference arms are not runtime geometry.

## Source and export gate

Accepted source: `src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`.
V0.4 source SHA-256: `b4d77e47a5e3b481c21e56064e9a10d44a771103bfbe66979f2ceea4c5195391`.
During V0.4.1 the user saved a GUI-display edit and requested a fresh geo export.
The saved/live native exporter agreed; the new geo and animation objects exactly
match the existing runtime assets. No geometry rewrite was necessary. The user's
new source SHA-256 is `cf5191d92bfaf73e2b1876fb407864a08a2452bca38354038b376b1e78da2835`.
Codex does not edit the source. Its new GUI settings remain in the source and
in-hand export; the runtime GUI is independently routed to a static icon.
The user subsequently changed the source GUI pose again (SHA-256
`2dfacbe292aae026013bbde318f4aaa06bc18b4767ab27bb966af4910f359bb0`).
Non-GUI display and animation verification still pass. Blockbench MCP was then
unavailable, so a second native export of this latest snapshot is not claimed.
The unused GUI display on the in-hand JSON is intentionally not synchronized;
the accepted static icon and GUI routing remain frozen.

Actual Blockbench GeckoLib plugin 4.2.5 model/display export actions and the
plugin-patched `Animator.buildFile` compiled the full model, not hand-authored
replacement geometry. Compatibility normalization is applied to the outputs:

- Native output uses Bedrock 1.21.110 metadata, which GeckoLib 4.7.4 does not
  enumerate. This model uses only the supported cube/bone/per-face-UV subset;
  output metadata is normalized to 1.12.0 without modifying geometry.
- The current animation exporter rounds millisecond source times to 120 Hz and
  omits `step`. Each exported track is matched by bone/channel/key order against
  the source; vector values and coordinate conversion must agree, then exact
  source times are restored. There are 132 keys, two animations, with no lost
  endpoint after animation_length.
- Source step interpolation is encoded as `easing: afl_hold` on the incoming
  segment. `P901Item` registers this easing through GeckoLibUtil: hold
  start value until GeckoLib's segment-end branch selects the end value. The
  built-in subdivided `step` easing is deliberately not used. This retains the
  magazine's exact 0.52/0.60-second hide/reappear boundaries without partial scale.
- Runtime has 77 cubes, 16 bones. `right_arm_reference` and `left_arm_reference`
  are excluded; hand, muzzle, ejection and sight anchors remain. All named action
  bones remain, including the empty `reload_magazine` guide.
- The embedded 128×128 `p9_01.png` is decoded byte-for-byte. No material
  or texture generation. Non-GUI runtime display parameters match the saved source.

Resources, relative to `src/main/resources/assets/apocalypse_firstlight/`:

- `geo/p9_01.geo.json`
- `animations/p9_01.animation.json`
- `textures/item/p9_01.png`
- `models/item/p9_01.json` (`forge:separate_transforms`, GUI routing)
- `models/item/p9_01_in_hand.json` (`builtin/entity`, exported display settings)
- `textures/item/p9_01_icon.png` (original 32x32 RGBA pixel artwork)
- `sounds/weapons/p9_01/p9_01_fire.ogg`
- `sounds/weapons/p9_01/p9_01_magazine_out.ogg`
- `sounds/weapons/p9_01/p9_01_magazine_in.ogg`

`tools/verify-p9-01.ps1` checks source/export key fidelity, duration,
cube/reference counts, anchors, display and texture bytes. Optional
`-PrepareAssets -SoundSourceDirectory E:/Download` decodes the source PNG and
copies the three supplied OGG files; no images are rendered or captured.

`tools/export-p9-01.blockbench.js` repeats the actual native export and
normalization from a matching saved/live V0.3.8 in Blockbench MCP. It returns
geo/animation/display objects for the above paths, refuses unsaved differences,
and never writes the source. Run the verifier after saving returned assets.
GeckoLib API reference: [GeckoLib4 triggerable animations](https://github.com/bernie-g/geckolib/wiki/Triggerable-Animations-%28Geckolib4%29);
the installed 4.7.4 source JAR was used to check the actual runtime API/parser.

## Input, state and synchronization

Implementation under `src/main/java/com/antaurora/apofirstlight/weapon/`:

- `P901Item.java`: GeoItem, synced instance, zero-transition triggerable
  controller `action`, `fire`/`reload`, prototype tooltip, no melee/block attack.
- `client/P901Model.java`, `client/P901Renderer.java`: GeoModel/
  GeoItemRenderer with the animated hand layer and V0.4.2 first-person-only
  presentation parent. Exported display JSON is not rewritten.
- `client/P901Input.java`: client-only `InteractionKeyMappingTriggered`
  cancels vanilla attack/swing for this item, sends once per press using an
  attack latch. R is a remappable IN_GAME KeyMapping, category AFL; consuming
  clicks plus held latch avoids OS key-repeat reload requests. GUI, focus,
  spectator and dead-player guards are applied.
- `P901Actions.java`: server-side ephemeral player session, 3 tick fire
  minimum and 26 tick reload lock. R during an active action is ignored, including
  the short fire lock; fire during reload is ignored. Held stack change, dimension
  change, death or logout clears/cancels active interaction. No persistent state
  machine or ammo state. Server melee/block-break guards additionally reject
  ordinary attacks made while holding this prototype.

`network/AflNetwork.java` protocol is **10** (was 9). One direction-restricted
C2S packet carries reload/fire choice plus selected slot. Server validates slot,
main hand, life/spectator state and action lock. Existing S2C messages are unchanged.
Stack render identity is synchronized before GeckoLib's trigger packet.
`triggerAnim` uses GeckoLib's TRACKING_ENTITY_AND_SELF distribution; nearby player
animation visibility is supported by the API path but multiplayer visual testing
is still required. No double client-predicted trigger or duplicate local sound.

## Audio and authored timing

Only three sounds are copied/registered in `AflSounds` and `sounds.json`:

| Source in E:/Download | AFL event | Time |
| --- | --- | --- |
| 9mm_fire.ogg | p9_01_fire | Immediately on accepted fire, once |
| 9mm_magazine_out.ogg | p9_01_magazine_out | Reload tick 8 (0.40s), source fully clear at 0.42s |
| 9mm_magazine_in.ogg | p9_01_magazine_in | Reload tick 19 (0.95s), source seated at 0.93s |

SoundSource.PLAYERS, volume/pitch 1.0, source at player position, server world
broadcast including shooter. Quantization error is 0.02s for each reload cue.
Each cue has an explicit once-per-session guard; changing held stack cancels
pending cues. Reload has no slide action, so no slide_action sound is played.
Suppressed/dry-fire/casing/slide files remain unused in the user's source folder.
Audible range is not infected Noise radius; no AFL Noise event is emitted here.

## V0.4 validation history

- Offline compileJava, processResources and build succeeded. Existing deprecation
  warnings remain; no dependency changes were necessary.
- Source/export verifier passed: 77 cubes, 16 bones, 132 exact keys, source PNG,
  display parameters, anchors and original OGG hashes.
- Repeating the native export script reproduced all three saved geo, animation
  and display objects exactly. The accepted V0.3.8 source SHA-256 is unchanged.
- Built `build/libs/apocalypse_firstlight-1.0.0.jar` contains the expected gun
  classes/resources and no DEV classes, copied `assets/tacz/`, or reference-arm
  geometry. Source bbmodel is separate from runtime assets.
- Graphical runClient started, OpenAL initialized, resource reload completed and
  the user entered the test world. The first manual pass crashed on cancellation:
  `run/crash-reports/crash-2026-09-06_14.29.13-server.txt`,
  StopTriggeredSingletonAnimPacket.encode -> FriendlyByteBuf.writeUtf(null).
  The new AFL cancellation path passed a null animation name; GeckoLib 4.7.4's
  packet encoder requires a non-null name despite the higher-level nullable API.
  This is an AFL integration defect, not a model or TaCZ defect.
- Fixed cancellation to use the shared explicit fire/reload name, and retained
  the item instance independently of the mutable stack so dropping its last item
  cannot cast AIR to P901Item. Server cancellation guards run at highest
  priority before other ordinary attack/break subscribers.
- DEV-only `NativeGunRuntimeSmokeCheck` checks the actual loaded GeckoLib model,
  animation cache, reference exclusion, anchors, registered hold easing and both
  stop-packet encodings after resource loading. It is excluded from the final JAR.
- The fixed graphical client logged `[AFL NATIVE GUN SMOKE] PASS` at 14:34:07
  on 2026-09-06. The final offline build succeeded in 26s; the final JAR was
  rechecked and contains zero DEV classes and zero `assets/tacz/` entries.
- After relaunch, the user answered "正常" to the explicit retest of firing or
  reloading while switching slots/dropping the pistol, GUI/first-/third-person
  rendering, gunshot/reload sounds and input locks. These user-tested items pass;
  the cancellation crash did not recur in that retest. No screenshots were taken.
- Creative-tab placement and semi-auto/vanilla-attack suppression are implemented
  and code-audited, but were not separately confirmed in that retest answer.
  Ground rendering, held-click non-repeat, ordinary melee/mining suppression,
  multiplayer visibility and TaCZ regression remain explicit manual checks.

## Source cleanup and recovery

Only the accepted V0.3.8 pistol remains as a loose source. Eleven obsolete pistol
bbmodels plus the pre-material-pass PNG were removed after every archived entry's
SHA-256 was checked. Recovery archive (local, ignored by Git):
`.gradle-user/asset-backups/p9-01-pre-v038-20260906.zip`.
The rig template `src/main/blockbench/templates/afl_weapon_rig_template.bbmodel`
and non-pistol models remain. Older review files are retained as explicitly
historical records, not deleted or presented as current runtime documentation.

## V0.4.1 presentation implementation and verification

Historical first pass: its hand calibration and camera placement below are
superseded by V0.4.2. The GUI and third-person implementation remain current.

- **Static GUI**: Forge `separate_transforms` selects an unanimated `item_layers`
  2D icon for GUI/Hotbar/Inventory. All other contexts use the exported
  `builtin/entity` model. No recursive custom-renderer GUI call, screenshot,
  resampling, TaCZ icon or third-party image is involved. Reproducible native-pixel
  drawing source: `tools/draw-p9-01-icon.ps1`.
- **First-person pass**: `P901FirstPerson` intercepts `RenderHandEvent`
  only while the main hand is this item, cancels both vanilla hand passes and
  renders the current main-hand stack once. It retains vanilla resting/equip
  translation and exported first-person transforms, but does not apply melee
  swing. This avoids rendering ItemInHandRenderer's stale pre-GeckoLibID stack
  during equip interpolation. Offhand items are visually suppressed while this
  two-handed gun is held; their inventory state and gameplay are not changed.
- **Skin arms**: `P901HandLayer` renders only the actual PlayerRenderer
  model's arm/sleeve parts using the current player's `getSkinTextureLocation()`.
  `getModelName()` selects Classic/Slim hand-center compensation; the actual
  PlayerRenderer supplies the corresponding 4px/3px arm mesh and vanilla skin UV.
  Sleeves honor player skin-part settings. Temporary part poses/visibility are
  restored in finally blocks. Invisible players do not render arms.
- **Anchor authority**: the per-bone layer inherits the full weapon_root and
  hand-anchor animated matrix, then translates to that bone's pivot. The accepted
  reference rig's local ZYX orientation calibrates the vanilla arm. Its hand-tip
  center lands exactly at the anchor for both Classic and Slim widths. No
  reference cube, reference texture, full player model copy or source offset is
  exported into gun geometry.
- **Third-person pose**: `P901PlayerPose` uses Forge's item-specific
  `IClientItemExtensions.getArmPose` / extensible two-handed ArmPose, inside normal
  HumanoidModel.setupAnim. Both arms raise and converge with head pitch/yaw;
  normal skin sleeves and held-item layers follow. Main hand and support roles
  mirror for left-dominant players. No mixin or world-player scan is needed.
  The third-person gun display transform is initially preserved pending visual
  alignment review. Multiplayer uses ordinary held-item/player-state visibility,
  but an observer-client test has not been performed.
- **Reload audit boundary**: exported 1.30s reload still contains weapon_root,
  magazine, reload_magazine and left_hand_anchor tracks. `reload_magazine` is an
  empty guide in the accepted source; the visible moving magazine is `magazine`.
  There is one triggerable action controller, with no idle loop overriding it.
  R/C2S/server trigger, 3/26-tick locks and all sound timing/volume/pitch/IDs/files
  remain unchanged. The stale-stack path was hardened; the later V0.4.2 audit
  identifies camera framing, not a lost animation, as the reload visibility cause.
- **Automated checks passed**: compileJava/processResources and offline build
  passed (final build 26s). The JAR's icon, routing model, in-hand model, geo and
  animation bytes match the source resources; no DEV classes or TaCZ assets are
  packaged. The 77-cube/16-bone/132-key/source-PNG/OGG verifier also passes.
  At 14:56:35 on 2026-09-06 the graphical client logged three smoke-test PASS
  results. The real reload controller reached 0.2443461 radians root rotation,
  24.106781 units magazine travel and 37.46896 units left-hand travel, and returned
  the magazine to ready. This rules out missing tracks and the default STOP
  predicate overriding a triggered reload. DEV-only tests
  check baked GUI-vs-3D routing, Classic/Slim vanilla geometry, anchor-tip mapping,
  raised-arm pose and real GeckoLib reload-controller evaluation. A bounded
  `NativeGunPresentationTrace` observes renderedId/heldId, controller and root/
  magazine/left-anchor transforms in the actual first-person pass. These classes
  are excluded from the release JAR. During the user's 14:59 in-world tests,
  first-person renderedId and heldId both remained 1. Repeated complete reloads
  showed root rotation around -0.2443 radians, magazine travel exceeding 22 units,
  left-anchor travel exceeding 35 units and subsequent zero return. The actual
  skin path was Slim. Static values after 15:00:02 followed a logged game pause,
  not a missing animation. Visual readability/skin contact/third-person alignment
  still require the user's feedback; this text trace is not visual acceptance.
  Manual in-world verification is pending;
  V0.4's prior broad acceptance is not reused as proof for this visual-fix pass.

## V0.4.2 first-person grip and reload presentation

The gun presentation is retained in V0.4.3; the arm-scale and local-direction
mapping below describes the historical V0.4.2 pass, superseded for arms only.

- **Cause**: V0.4.1 mapped both distal arm tips onto separated anchors with
  reference-rig orientations. Together with camera framing this exposed long,
  similar-looking arm columns rather than embedding the grip into the main palm.
  Animated anchor inheritance already worked; it was not a static-anchor bug.
- **Right hand**: unchanged animated anchor plus local pixel offset
  `(-1.55, +0.20, +0.75)`; vanilla arm palm center at local Y=8 (tip is Y=10).
  The two-pixel inset makes the grip intersect the palm region. Arm -Y aligns
  to normalized `(0.24, -0.69, 0.69)` within the anchor's animated frame.
- **Left hand**: local offset `(+2.45, -0.55, -1.65)`, palm Y=9.5;
  arm -Y aligns to normalized `(-0.24, -0.87, 0.44)`. This is a lower/front,
  shallower support contact, not a mirrored copy of the right-hand calibration.
  Classic/Slim center compensation remains; vanilla geometry/UV and source rig
  are unchanged. No geometry clipping or shortening is performed: orientation
  and camera composition move most of the forearm outside the lower viewport.
- **Ready**: camera-relative base translation changes from `(0.56,-0.52,-0.72)`
  to `(0.60,-0.54,-0.64)`; equip lowering remains `-0.6 * equipProgress`.
  The authored first-person translation/rotation and 0.3 scale remain unchanged.
- **Reload cause**: actual in-world controller and bone traces completed reload
  with matching stack IDs. At the old 70-degree held-camera framing, the entire
  magazine projected below the viewport during major extraction/insertion
  samples (top NDC Y=-1.202 at 0.32s, -2.320 at 0.48s). No renderer assignment
  was overwriting weapon_root; the motion existed outside the useful view.
- **Presentation**: after GeckoLib evaluates animation, the first-person root
  receives one extra parent transform, shared by gun and both animated hand
  layers. Weight uses smoothstep `t*t*(3-2*t)`: rise 0.00–0.24s, hold through
  0.85s, fall through 1.18s, then zero. At weight 1: camera translation
  `(+0.05,+0.28,-0.25)`, pivot `(0,8,6)/16`, rotations Z=-16°, Y=+32°, X=+8°
  in that order. Translation is divided by the existing display scale before
  applying. The authored root's roughly -14° roll combines to about -30°.
  Lift and pullback expose the long existing magazine travel; moving closer
  would worsen lower-screen clipping. No second magazine or hand timeline is
  authored. The empty reload_magazine guide remains empty.
- `P901AnimationController` reads clip time after the existing controller
  processing, using its tickOffset and speed; it adds no gameplay/network state.
  The hand layer continues reading each fully animated anchor transform.
  Fire has zero reload offset; all original animation bytes are retained.
- Third-person ArmPose/display and static icon/GUI routing are frozen. Audio
  files, 0.40/0.95s cues, volume and pitch are unchanged. No ADS or ammo system.
- Offline compileJava/processResources and build passed (build 31s).
  At 15:23:58 the graphical client passed Classic/Slim contact, actual reload
  controller, GUI routing and numeric projection tests (minimum magazine top
  NDC Y=-0.8021). Actual in-world reload traces showed the additional curve
  rising to 1 and returning to 0 while authored bone motion continued.
  **User rejected this first pass's direction: muzzle opened to the right.**
  The added yaw was corrected from -32 to +32 degrees: local barrel forward is
  -Z, so positive Y rotation turns its direction toward camera-left. All other
  Ready, hand calibration, reload translation/roll/pitch/timing are unchanged.
  A direction assertion was added. The corrected offline compileJava,
  processResources and build passed (39s), as did diff-check and packaged resource
  byte checks (DEV classes=0, TaCZ assets=0). The corrected graphical client
  started; at 15:28:35 direction, Classic/Slim contact, GUI routing and bounded
  curve checks passed, but the magazine extraction/insertion viewport assertion
  FAILED after the yaw reversal. That pass was not marked accepted at the time.
  The user's subsequent V0.4.3 brief explicitly accepts the gun Ready/Fire/Reload
  composition as basically usable and freezes it. The earlier -0.8021 result does
  not apply to +32° yaw: this known numeric frustum limitation remains recorded,
  now a diagnostic warning, not a reason to move the accepted gun in an arm task.

## V0.4.3 player arm scale and extension

The initial unit-scale attempt below was visually rejected. The explicitly
authorized independent-scale correction follows in the next subsection.

- **Scale cause confirmed**: the hand layer ran after the item JSON's 0.3 display
  scale. Vanilla arm geometry was correct but its full camera-space basis was
  still scaled: Classic effectively 1.2x3.6x1.2 pixels, Slim 0.9x3.6x1.2, instead
  of 4x12x4 and 3x12x4. No custom or shortened cuboid was used.
- **Old transform**: first-person base -> display translation/scale -> reload
  presentation -> animated root/hand anchor -> anchor pivot -> contact offset ->
  local arm rotation -> palm compensation -> vanilla arm/sleeve.
- **New transform**: same full hierarchy through contact offset to resolve the
  identical animated camera-space contact; in a pushed arm-only matrix, retain
  translation and orthonormalize the basis, preserving handedness. Then apply
  arm direction and palm compensation at unit scale. Lighting normals are updated
  to the rigid basis. No magic 3.33 multiplier or hard-coded inverse gun scale;
  no gun or anchor position is divided by scale. The shared gun pose is restored
  by popPose and no global camera/projection/depth setting changes.
- **Local directions**: right arm -Y aligns to normalized `(0.30,-0.85,-0.43)`,
  left to `(-0.35,-0.82,-0.42)` in the animated anchor frame. With the -22-degree
  bind rotation these point predominantly down/outward and slightly away from
  the camera. The old vectors pointed toward the camera and strongly foreshortened
  the already scaled arms. Complete emitted-vertex checks rule out actual near-
  plane truncation in the audited neutral-camera Ready/Fire/Reload poses: old
  right Ready sleeve closest Z=-0.31354; old full reload closest Z=-0.29643,
  both safely beyond Z=-0.05. The apparent short segment is consistent with
  shrunken geometry and camera-directed foreshortening, not missing faces.
  Looking-down/bob/hurt-camera visual behavior remains a manual check.
- Contact offsets remain right `(-1.55,+0.20,+0.75)`, left `(+2.45,-0.55,-1.65)`
  in authoring pixels. Palm Y remains 8/9.5 with Classic/Slim center compensation.
  Runtime rotation changes only arm extension, not the animated palm position.
- **Skin and sleeves**: actual PlayerRenderer rightArm/leftArm and rightSleeve/
  leftSleeve were already present; no missing sleeve was invented as a cause.
  Player skin ResourceLocation and sleeve-enable settings remain authoritative.
  Vanilla sleeves expand each face by 0.25 pixels. All six faces are rendered
  at standard depth, not always-on-top. Part pose, visibility, skipDraw and part
  scale are restored after rendering so the first-person pass cannot leak them
  into third person. No fixed Steve texture or reference-arm geometry.
- Frozen: gun base `(0.60,-0.54,-0.64)`, display scale 0.3, full +32-degree yaw
  reload curve, source/geo/animation/texture, third-person pose, icon/routes and
  all audio timing/files/volume/pitch. No gameplay scope expansion.
- DEV-only `NativeGunArmChecks` probes emitted vanilla base/sleeve vertices,
  dimensions, full faces, scale independence and unchanged contact positions.
  Reload/Fire controller sampling checks whole sleeves against the unchanged
  near plane. `NativeGunArmTrace` uses the actual per-bone first-person matrix
  and player skin, logging at most 120 samples per hand; it draws nothing and
  is excluded from the release JAR.
- **Verification, 2026-09-06**: offline compileJava, processResources and build
  passed (final build 39s); diff-check passed. All 14 frozen gun/source/audio
  files have unchanged SHA-256; release JAR contains the hand-layer update,
  zero DEV classes and zero TaCZ assets. Asset verifier passed 77 cubes, 16 bones,
  132 exact animation keys, source PNG and non-GUI display. Rechecking original
  E:/Download OGGs was unavailable because 9mm_magazine_out.ogg is no longer at
  that source path; project OGG signatures and pre-task hashes still match.
- At 15:44:45–46 the launched graphical client passed actual Classic/Slim base
  and sleeve geometry (24 emitted vertices each), unit basis for gun scales
  0.2/0.3/0.6/1.0, exact contact preservation, and complete sleeve near-plane
  checks. New right Ready closest Z=-0.35221; 301 reload samples closest
  Z=-0.23782; 41 fire samples closest Z=-0.33914; all are behind Z=-0.05.
  Fire recoil still peaks at 0.0558505 radians and returns to Ready.
  The frozen V0.4.2 magazine-top projection remains -1.0929165 and is explicitly
  logged as a known warning, not hidden or repaired by changing the gun.
  Manual arm thickness, perceived completeness, sleeves/skin appearance, grip
  and magazine non-occlusion await user review. Classic numerical geometry is
  verified, not yet a Classic-skin visual acceptance.
- The actual in-world V0.4.3 per-bone path subsequently logged Slim player skin
  `minecraft:textures/entity/player/slim/efe.png`, both sleeve settings enabled,
  24 base and 24 sleeve vertices per hand, and unit scale during Ready/Reload.
  Across the first 85 logged samples per hand, new closest Z was about -0.33
  (right) / -0.15 (left), with no near-plane intersection. This verifies the
  current Slim runtime route and skin selection, not a Classic visual result
  or a guarantee for every possible camera effect.
- **Manual acceptance FAILED**: the user supplied comparison images showing
  oversized AFL hands covering/intersecting the slide in Ready and obstructing
  the grip/magazine during Reload. Unit-scale and near-plane tests did not catch
  this contact-volume/occlusion failure and are not visual acceptance. Removing
  the inherited 0.3 scale increased the arm's linear size by about 3.33 while
  retaining palm-center mapping; preserving a mathematical contact point did not
  preserve a valid surface grip. The current arm pass must not be marked done.
  Further correction needs palm-surface clearance calibration. The user has now
  explicitly relaxed runtime scale=1.0 and authorized independent first-person
  arm visual scale while retaining vanilla geometry and frozen gun presentation.
  Gun transforms, all animations, source geometry, audio and TaCZ remain frozen.

### V0.4.3 correction: independent arm visual scale and palm-surface contact

Historical attempt, superseded by the V0.4.4 baseline restore below.

- User-authorized `ARM_VISUAL_SCALE = 0.55` is applied only after resolving the
  animated contact and removing inherited gun scale. It is a deliberate arm
  presentation parameter, not inverse compensation for the gun's 0.3 scale.
  Standard base geometry remains Classic 4x12x4 / Slim 3x12x4, with unchanged UVs
  and 0.25-pixel sleeve inflation. Visual dimensions are those values times 0.55;
  they are no longer reported as unit-scale runtime dimensions.
- Right palm contact offset becomes `(-1.55,-0.30,+1.10)` in anchor-local
  authoring pixels; left becomes `(+2.45,-0.70,-1.60)`. Right palm Y=9.65,
  left Y=9.85 (base distal tip Y=10), reducing the volume above the grip contact.
  Contact X is the vanilla arm center plus `-0.20 * width` for right and
  `+0.20 * width` for left, where width is 4/3 for Classic/Slim. The palm surface,
  not the cuboid center, meets the gun. Source anchors are not edited.
- Order: full animated gun hierarchy -> anchor/contact offset -> rigid contact
  basis -> retained arm local orientation -> arm visual scale -> palm-surface
  compensation -> original base/sleeve parts. Only the arm branch changes;
  contact positions still follow the actual animated anchors, not a fixed pose.
- DEV checks now assert 0.55 independent arm scale across incoming gun scales,
  exact calibrated contact mapping, standard unscaled geometry and complete
  faces. `NativeGunArmClearance` adds 15-axis OBB checks between complete sleeve
  geometry and animated slide/barrel/front/rear sights over Ready/Fire/Reload.
  This catches upper-gun intersections that the earlier near-plane-only tests
  missed; it does not prove visual magazine non-occlusion or natural gripping.
- Offline compileJava/processResources and build passed (final build 35s), and
  the corrected graphical client started. At 15:58:51 it passed independent
  0.55 scaling across gun scales 0.2/0.3/0.6/1.0, calibrated palm contact,
  standard Classic/Slim geometry and complete sleeves. Across 301 reload and
  41 fire samples, the OBB checks found no sleeve intersections with slide,
  barrel or sights. Closest sleeve Z=-0.33871 (reload), -0.41138 (fire), beyond
  the unchanged -0.05 near plane. Manual grip/magazine-occlusion acceptance
  remains pending; OBB clearance is not a claim of visual success.
- All 14 frozen file SHA-256 hashes are unchanged. Source/resource verifier
  passed, and release JAR contains no DEV classes or TaCZ assets. Whole-repo
  diff-check reports five Markdown trailing-space lines in the user's concurrently
  appended future-ammo/HUD checklist; those unrelated edits are preserved.
  Diff-check excluding that checklist passes; the arm changes add no whitespace
  errors. No screenshots/previews, source edits, commit or push.

## V0.4.4 first-person baseline restore and matrix ownership

- **Baseline provenance**: native pistol files remain uncommitted on `master`
  at `451f8bd`; there is no V0.4.2 commit to claim or reset to. Exact values are
  recoverable from the V0.4.2 implementation record above, the unchanged assets,
  and file hashes retained at the V0.4.3 entry audit. Before V0.4.4 edits, all
  14 audited frozen files still matched that snapshot. In particular:
  `P901Presentation.java` SHA-256
  `dc6a1c7499fb17b0adb5d9589881dc4bd015a9f5feae21e7c3d246457787e07a`,
  old `P901FirstPerson.java` SHA-256
  `186075f62c2ada7a627f5caabbf9d25f93a8c2864549a5d3e74f5553e6327f83`,
  and animation SHA-256 `ae04019755413def1170af9a42708f4bb52bb5c27d2b2ccfc0ee2e024cfcd30e`.
- **Current vs V0.4.2 READY**: camera translation `(0.60,-0.54,-0.64)`, no extra
  base rotation, display translation `(-6,1,0)/16`, display scale `(0.3,0.3,0.3)`;
  equip adds `-0.6*equipProgress` on Y. At rest weapon_root animation delta is zero.
  No difference in these values was found. The combined neutral pre-bone matrix
  has diagonal 0.3 and translation `(0.225,-0.4745,-0.64)` including GeckoLib's
  0.01 local Y offset. Existing camera bob/hurt transforms are inherited unchanged.
- **FIRE**: same base matrix, same 0.14s source; weapon_root pitch magnitude
  peaks at 3.2 degrees, slide reaches +3.2 authoring Z at 0.04s, then returns.
  No animation/source or recoil redesign. Reload presentation is zero on Fire.
- **Oversized-gun audit classification: OTHER**. There is no measured gun scale,
  camera-Z, root or double-transform change. Local PoseStack source confirms
  pushPose deep-copies pose/normal matrices; existing try/finally scopes balance.
  No arm-to-weapon_root assignment was found. The confirmed intervening change
  is hand scale/contact/direction around the gun, causing the reported hugging
  composition. This is not evidence of a gun matrix leak; perceived gun size
  still requires the user's visual comparison after restoring the reference hands.
- **Restore**: retain/enforce the already matching V0.4.2 gun matrix and restore
  its exact effective hand mapping: independent scale 0.30, palm Y 8/9.5,
  Classic/Slim hand-center compensation, offsets right `(-1.55,+0.20,+0.75)` /
  left `(+2.45,-0.55,-1.65)`, local -Y directions `(0.24,-0.69,0.69)` /
  `(-0.24,-0.87,0.44)`, normalized. This intentionally accepts slightly small
  reference arms; no new tuning or state-dependent hand scaling is introduced.
- **Reload preserved**: current +32° yaw, -16° roll, +8° pitch; camera delta
  `(+0.05,+0.28,-0.25)`; unchanged smoothstep envelope 0–0.24s, hold to 0.85s,
  return by 1.18s. Full 1.30s weapon_root/magazine/reload_magazine/left-anchor
  evaluation remains. Hand rendering uses the restored reference mapping in
  every state; gun side-open and hand-anchor motion are not rolled back.
- **Ownership isolation**: `P901RenderMatrices.detachedCopy` creates
  separate pose AND normal matrix objects. The weapon entry copies the caller's
  camera stack and owns its push/finally/pop scope. The hand layer only reads
  the animated per-bone pose, copies it, and calibrates/renders on its own scoped
  stack. Even a direct or unbalanced arm-only matrix edit cannot mutate weapon
  or camera matrices. Arm code does not write bones, roots, display or projection.
- `NativeGunMatrixChecks` adds one-shot DEV checks for baked display agreement,
  old push/pop isolation, detached matrix ownership, legacy-hand matrix equality
  and repeated Fire/Reload returns. Existing high-frequency DEV traces now require
  `-Dafl.debug.nativeGunPresentation=true`; default is off and release excludes
  all DEV classes. No release path logging was added.
- **Verification boundary**: offline compileJava/processResources/build passed;
  graphical clients were launched. The first restored-baseline run exposed a
  Classic right sleeve/upper-gun intersection under the stricter V0.4.3 OBB test.
  This is checked against the exact legacy mapping, not silently removed: the
  current and baseline intersection counts must agree at every sampled pose.
  Restoration is not a claim of zero arm/gun intersections; further palm
  calibration remains deferred. The known partial magazine frustum warning
  is also retained, not hidden.
- An initial repeated-action test checked Fire at 0.30s and found matrix element
  residual `0.00017054359`. GeckoLib's unchanged default bone-reset duration is
  5 ticks after the last sampled animation frame; the corrected test includes
  natural settling (Fire 0.45s / Reload 1.60s) without resetting bones between
  actions or modifying runtime/source timing. Final startup check at 16:24:40
  passed 40 actions / 7,320 samples on one controller manager, both hands and
  Classic/Slim: legacy/current hand matrices agree within 1e-5, each arm pass
  preserves the gun matrix/root, and every naturally settled action returns to
  the same READY matrix. This is numerical transition verification, not manual
  acceptance or a claim of bit-exact zero at every animation end timestamp.
- The final graphical client's resource/controller smoke checks passed. The
  initial Classic right sleeve intersection count is 1 in both legacy and current
  mappings; all 301 Reload + 41 Fire clearance samples match the old baseline.
  Reload sleeve nearest Z=-0.29642868, Fire Z=-0.30748424; both remain beyond
  the -0.05 near plane. Reload root rotation=0.2443461rad, magazine travel=
  24.106781, left-anchor travel=37.46896; Fire recoil=0.055850536rad.
- Final offline build passed (40s). Source/resource verifier passed 77 cubes,
  16 bones and 132 exact keys. Of 14 frozen-entry hashes, only the authorized
  FirstPerson stack-ownership refactor changed a file; presentation parameters,
  source/runtime assets, audio, icon, third-person and action logic remain exact.
  Release JAR includes the new matrix helper and contains zero DEV/TaCZ entries.
  Whole-repo `git diff --check` still reports five pre-existing trailing-space
  lines in the user's appended future-ammo/HUD checklist; they were preserved.
  The check excluding that checklist passes. No screenshots or previews were made.
- **V0.4.4 changed files**: production `weapon/client/P901FirstPerson.java`,
  `P901HandLayer.java`, new `P901RenderMatrices.java`; DEV
  `NativeGunMatrixChecks.java`, `NativeGunRuntimeSmokeCheck.java`,
  `NativeGunArmChecks.java`, `NativeGunArmClearance.java`,
  `NativeGunPresentationTrace.java`, `NativeGunArmTrace.java`. The four directly
  related framework/checklist/gun-system/art-standard documents were synchronized.
- Gun geometry, texture, source animations, audio, third-person, icon, input and
  prototype gameplay remain frozen.
- **User visual acceptance**: the user replied "ok" to the final V0.4.4 grouped
  in-client check covering acceptable READY size/distance/main-support hand
  relationship, normal FIRE, preserved current Reload side-open, and return to
  the same position after repeated Fire/Reload. These presentation checks are
  accepted by the user. This does not claim all skins/FOVs were visually tested
  or erase the known baseline intersection/frustum limits above. No further
  model, animation, audio or render-parameter changes accompany this sign-off.

## V0.4.5 reload right-hand grip presentation

Historical, unaccepted attempt; superseded by V0.4.5.1 below.

- **Live audit**: entry branch `master`, HEAD `7685f61`. The user has committed
  the native module since the V0.4.4 audit; the earlier uncommitted status above
  is historical. Existing documentation/sign-off and workspace edits are retained.
- **Reported broken-sleeve cause**: the old `P901HandLayer` called
  `renderPart` for the whole vanilla right arm AND enabled sleeve during every
  state, including Reload. The complete finite 12-pixel arm has no connected
  shoulder/body mesh in this first-person layer; side-opening exposes its end
  inside the view. This is not a missing right-anchor animation or a matrix leak.
  Earlier full-arm probes stayed beyond the camera near plane; no evidence
  justified moving the gun or changing near clipping to address the report.
- `right_hand_anchor` is a child of `weapon_root`, rest pivot
  `(-4.265,9.1,6.3)`, rotation `(22,0,0)` in exported coordinates. Reload has
  **no independent right-anchor track**; the left anchor has its existing
  magazine-handling track. No source/bone/animation edit is needed.
- **Reload-only geometry route**: `P901ReloadGrip.active` uses the
  existing renderer/controller clip time, right-hand-only, finite and >=0.
  The same evaluated anchor and unchanged `orientAtHandTip` matrix rigidly
  attach the compact hand to the grip. There is no new position, rotation,
  scale curve, action cache, root write or motion authored for the right hand.
- The cached compact geometry keeps vanilla arm-local Y=6..10, around the
  existing Y=8 palm contact. Base meshes are Classic 4x4x4 / Slim 3x4x4,
  using the unchanged independent 0.30 arm visual scale. Optional right-hand
  outer layer retains vanilla 0.25 inflation but is cut at the same wrist plane
  (Y=6..10.25). Neither a full forearm nor a full forearm sleeve is emitted
  in this branch. The original PlayerModel and Blockbench reference remain intact.
- **Player skin/UVs**: bind `player.getSkinTextureLocation()` every render.
  Bake UVs from vanilla right-arm layout `(40,16)` and outer layer `(40,32)`,
  respecting Classic/Slim widths; side UVs interpolate along the original edges
  so only distal hand rows are sampled, not the whole sleeve squeezed onto a
  short box. The fingertip UV is unchanged; a closed wrist cap reuses the same
  hand-end island rather than a shoulder-colored/open end. The user's right
  sleeve visibility setting still governs the compact outer layer. No fixed
  skin texture, skin color, glove color or generated texture is introduced.
- **Frozen paths**: Ready/Fire use the original full arm/sleeve render path;
  left arm uses it in every state. Gun Ready/Fire transform, current Reload
  side-open, weapon_root/magazine/reload_magazine/left-anchor animation, audio,
  third-person and icon remain unchanged. Normal completion/cancellation makes
  the clip time inactive and restores the original right-arm route on that frame;
  no shared ModelPart visibility or geometry is modified by the compact branch.
- **Isolation/performance**: reuse V0.4.4's detached arm matrix and finally/pop
  scope. Four immutable 24-vertex meshes are baked once, with no world scan,
  disk lookup, skin cache or new release logging in the per-frame branch.
- **Verification status**: compileJava (37s), processResources (26s) and build
  (40s) passed. The 00:53:46 graphical startup failed the new upper-gun
  intersection check, while V0.4.4 baseline/matrix checks passed. The user
  reported the hand was effectively invisible and supplied V0.4.5.1 instead.
  No later shrink/offset pass was applied before that request: live geometry
  was still the 4x4x4 / 3x4x4 segment. This attempt is not accepted/completed.

## V0.4.5.1 reload right grip surface

- **Audit/root cause: DEPTH_OCCLUSION**. Branch `master`, HEAD `7685f61`, with
  the unfinished V0.4.5 changes and existing user documentation/workspace edits.
  Live old geometry was still 4x4x4 / 3x4x4; no undocumented shrinking pass is
  assumed from the prompt. The old anchor-centered, forearm-oriented volume is
  on the occluded side of the gun at current +32-degree side-open. A geometry
  ray audit of that pose found 0/27 old sample points unblocked, with hits on
  frame shell/primary/backstrap. The first thin backstrap surface gave 9/9
  unblocked sample points, but the user rejected its in-game appearance: only
  a rear-edge sliver was visible, looking stuck through the gun. That placement
  was **WRONG_FACE / WRONG_OFFSET** despite passing its numerical tests. Neither
  those tests nor the old 01:14:48-49 startup constitutes visual acceptance.
- **Placement authority**: still the evaluated `right_hand_anchor` under
  animated `weapon_root`. Instead of applying the normal forearm orientation,
  this Reload-only branch uses a fixed anchor-to-visible-SIDE calibration.
  The audited grip side has render-space pivot `(0,9,7.6)`, X rotation -22 degrees
  and outer X=-2.4. The patch's inner center is `(-2.44,7.0,7.7)` before that
  grip rotation, with its face normal pointing outward along grip -X. Classic
  spans Y=5.5..8.5 and Z=6.0..9.4, above the raised side panel's Y=5.4 top and
  below the primary frame. Anchor-local translation is approximately
  `(-6.705,-2.579707,+1.267878)` model units plus fixed local Y rotation -90 degrees.
  These are derived surface placement coordinates, **not** an epsilon-sized
  anchor translation, camera-space motion or new time-dependent animation.
- **Thin surface**: Classic `3.40 x 3.00 x 0.55`, Slim `2.55 x 3.00 x 0.55`;
  two cached, closed 24-vertex meshes. The 0.04 model-unit epsilon is measured
  from the grip side's outer face along its outward normal. Render-space normal
  is `(-1,0,0)` before weapon_root/presentation rotation; thickness extends only
  outward, not into the grip. The rejected rear patch's 3.50 height is now 3.00
  to keep this side patch between the raised panel and the frame.
  Existing independent arm visual scale 0.30 is retained. No gun scale/Z/root,
  anchor or source geometry is modified.
- **Skin/depth**: use the current player's skin with the existing
  `RenderType.entitySolid`, normal depth test/write and ordinary lighting.
  Main face reads the vanilla right-arm SOUTH face's distal rows: Classic
  U=52..56, Slim U=51..54, V=28..32 on the 64x64 skin. Thin edges use boundary
  strips of that same hand region. No torso/leg UV, fixed color or fixed skin.
  **No right sleeve, including compact outer layer, is rendered during Reload**;
  no forearm, overlay-on-top pass or depth disabling is used.
- **Frozen paths/return**: Ready/Fire and all left-arm rendering keep the
  original V0.4.4 path/matrices; gun side-open, magazine, handoff, left anchor,
  all source animations, audio, third-person, icon and prototype logic are frozen.
  The surface relative transform is constant through Reload and reads no
  time-dependent right-hand offsets. Completion/cancellation immediately selects
  the normal right-arm/sleeve path; no persistent visibility, geometry or action
  state is mutated. Weapon and arm matrices remain detached.
- **Build/resource verification**: the side-face correction passed offline
  compileJava/processResources/build (41s) and `git diff --check`. The resource verifier passed 77 gun
  cubes, 16 bones and 132 exact animation keys. Among existing native production
  and gun asset files, Git diff against entry `7685f61` only changes HandLayer;
  the new ReloadGrip helper is additional. Frozen gun transforms, source/runtime
  model/animation/audio assets, left-anchor tracks, third-person and icon have
  no diff. Release JAR includes the helper and excludes all DEV and TaCZ entries.
- **Historical backstrap numerical verification** at 01:14:48–49: all smoke checks
  passed, but that version failed user visual acceptance. Eight consecutive reloads / 2,094 active timeline samples retained a
  constant grip-relative matrix and restored the normal Ready route. Classic/Slim
  UVs were compared against actual baked vanilla right-arm UVs. First-cycle
  upper-gun, trigger/guard, magwell and animated-magazine OBB checks passed;
  center ray checks throughout Reload and 3x3 surface ray samples through the
  main side-open interval were unoccluded under normal depth. Nearest surface
  Z=-0.36733216, beyond the -0.05 near plane. The prior V0.4.4 40-action/7,320-sample
  matrix baseline check also passed. These are numerical, not visual results.
- **Side-face correction numerical verification**, 01:24:47-48 graphical startup:
  `[AFL RELOAD GRIP V0451 SIDE] PASS`, 8 reloads / 2,094 active samples, both skin
  widths. Fixed grip-relative matrix, normal Ready route restored, no upper-gun,
  entire-frame, trigger or animated-magazine OBB intersections; clearance checks
  now include **all 28 frame cubes**, including grip shells, webs and panels,
  not only the guard/magwell subset. Surface-center rays and side-open 3x3 samples
  remain unoccluded by gun geometry; nearest Z=-0.43980306. Classic/Slim hand UVs
  passed against baked vanilla geometry. The 40-action / 7,320-sample frozen
  Ready/Fire/gun-matrix regression and the full startup smoke suite also passed.
  These checks still do not establish how natural the palm looks in-game.
- **New visual acceptance pending**: user must judge small palm-side visibility,
  floating/occlusion and unchanged Ready/Fire/left-hand Reload in-game. No
  screenshots/previews are used; numerical tests do not set visual PASS flags.
  Existing full-arm baseline intersection and partial magazine frustum warnings
  remain recorded separately; the former is not the new thin-surface geometry.
- **Changed files this pass**: production `weapon/client/P901HandLayer.java`
  and new `P901ReloadGrip.java`; DEV `NativeGunReloadGripChecks.java`,
  `NativeGunRuntimeSmokeCheck.java`, `NativeGunArmChecks.java`,
  `NativeGunArmClearance.java`, `NativeGunArmTrace.java`; the four directly related
  framework/checklist/gun-system/art-standard documents. Existing unrelated user
  edits were preserved. No commit/push or additional gameplay work was performed.

## V0.4.6 research — hand locator architecture (not implemented)

Research only; see [TaCZ first-person hand architecture audit](native-gun-tacz-first-person-hand-audit.md)
for version-pinned sources, the actual call chain, H1-H6 verdicts, comparison and migration gates.

- **Acceptance correction superseding the V0.4.5.1 pending notes above:** the user
  rejected the side-face thin-palm revision too and terminated that task. Neither
  the backstrap nor side-face patch is visually accepted. Existing uncommitted
  production/DEV/source changes were preserved, not rolled back or continued here.
- **STOP PATCHING CURRENT ARM GEOMETRY.** Recommend an AFL-owned locator/skin-arm
  contract, not additional short-hand or thin-surface corrections. The current
  AFL pipeline already evaluates animated anchors and isolates weapon/arm matrices;
  the missing contract is preview-to-runtime pose/scale/basis equivalence.
- The exact TaCZ 1.1.8-hotfix binary was inspected; no matching local source JAR
  was found. Public source was pinned to tag commit `b43eb84c38e9768d8e73c8b14f0b845669704b38`.
  Its active first-person entry is the embedded SBM 2.2.2 handler, not the old
  unsubscribed FirstPersonRenderEvent. Hand nodes substitute full player-skin arms
  through an evaluated locator matrix and a fixed coordinate adapter.
- Hierarchy is important but not a universal rigid lock: M4A1 reload keeps right-hand
  local tracks constant; Glock 17 reload and M700 empty/bolt contain independent
  right-hand tracks. Animation authors remain responsible for grip relationships.
- Next step, only after authorization: define a canonical AFL hand frame and prove
  Classic/Slim preview/runtime equivalence; use one full-arm adapter across Ready,
  Fire and Reload; retain existing action/audio/gun behavior in the first slice.
  Deprecate P901ReloadGrip only after its replacement is visually accepted.
  Do not blindly replace the current constant 0.30 scale with 1 or import TaCZ offsets.
- This pass changed documentation only. No runtime/DEV code, model, animation,
  audio or dependencies changed; no new client run, screenshots, previews, commit or push.
  TaCZ/SBM implementations and assets were read, not copied into AFL.

## Planned, not implemented

V0.4.3 unit/0.55 arm passes were not accepted as the final Ready baseline.
V0.4.4 restores the smaller reference mapping; further isolated Arm Calibration
is deferred. V0.4.4 READY/FIRE/Reload presentation is user-accepted. ADS is not implemented. V0.6+: real ammo,
damage/ballistics, Noise, Tinnitus, suppressor and shell systems. None is active
in this V0.4 prototype. Manual visual/audio testing does not imply those features.

## First-Person Runtime Path Audit summary

2026-09-07：只读审计当前工作树 master / 7685f61，详见
[第一人称运行时路径与无效代码审计](native-gun-first-person-runtime-path-audit.md)。
本段记录 LIVE 状态，优先于上文历史版本的当前路径描述；V0.5 尚未实施。

- 当前主链：RenderHandEvent → P901FirstPerson → Java baseline →
  ItemRenderer / exported Display → P901Renderer / GeckoLib animation
  evaluation → per-bone HandLayer → NativePlayerArmRenderer → B_skin → 完整玩家 arm/sleeve。
  枪顶点由 GeoRenderer 的 cube/quad 路线提交；手顶点由真实 PlayerModel ModelPart 提交。
- 直接 FP 绘制相关为9个 AFL 顶层类；加 Item/AnimationController 为11个。
  11个 Native Gun DEV 顶层类不进入已检查 release JAR（dev class entries=0），
  但 src/dev/java 在 runClient 同时参与main编译和事件扫描。
- 当前 FP Display S=.5，与 Java BASE_SCALE=.82及gun-only .8共同作用；
  gun最终轴长=.328，arm basis固定=.246，而locator位置仍继承公共父变换。
  Java BASE及Reload side-open仍active；旧Reload除数.3未跟Display变化。
  普通Blockbench显示不自动执行arm正交化，数值测试的“preview equivalence”不是窗口视觉等价。
- P901ReloadGrip薄掌面没有main调用，不影响默认画面；LegacyHandMapping等主要是旧DEV群。
  NativeGunMatrixChecks和NativeGunReloadGripChecks是零caller独立清理候选；
  其余旧类须连引用成组清理，本轮未删除。
- DEV NativeHandVisualGate确实改变main Renderer的handFilter：数值失败可能保持全隐藏，
  通过后默认right-only；release默认左右放行。最新既有日志已放行右手，且实际玩家为Slim。
- READY右臂消失的最高优先级候选是当前比例/接触下被frame深度遮挡：
  只读静态采样中Slim右臂朝镜头242点有240点被枪体遮挡，袖子为221/242；
  该假设下所有采样点仍在视野内。这不是像素面积或实机确认，真实失败帧原因仍未最终确认，
  player.isInvisible等早退条件也未在失败帧采集。详见审计限制与可重跑脚本。
- 本轮仅新增审计文档/只读脚本并追加本摘要；未改production/DEV Java、资产、配置，
  未运行Gradle或客户端、未截图/preview、未commit/push。建议另行授权V0.5契约重构，
  不把历史数值PASS冒充Ready视觉验收。

## AFL vs TaCZ Player Arm Presentation Audit（2026-09-07，当前 V0.5）

本节只追加审计结论，不实施新修复。优先于紧邻上节的旧V0.4.9 Runtime Path Audit：
当前V0.5已经实施；旧`.246/.328`及`240/242`数据不可继续当作LIVE。
详见[玩家手臂表现专项审计](native-gun-afl-vs-tacz-player-arm-presentation-audit.md)。

- 两者均使用完整Vanilla Classic 4×12×4 / Slim 3×12×4，以及每面inflate .25的sleeve。
  当前AFL canonicalPose保留完整父矩阵，PLAYER_ARM_SCALE=1、Display=.41，最终轴长为(.41,.41,.41)。
  `.246`是历史.82×.30及V0.4.9视觉补偿，不是模型单位换算；V0.5已移除该固定basis。
- 本地TaCZ 1.1.8-hotfix Glock17的static_idle/reload_tactical手部父节点scale=(1,1.5,1)，
  最终手臂basis继承它；helper不额外放大，也不剥掉父scale。
  相同原始手臂宽/深的变换后尺寸约为AFL的2.439倍，长度约3.659倍；这不是另一套geometry。
- 70°/16:9/1080高、静态相机、Slim右手的参考场景：Ready/Reload .50s可见轴段
  AFL约311/173px，TaCZ约425/663px；AFL换弹cap更远(.630→.981)，
  TaCZ更近(.895→.778)，且AFL前臂更朝深度方向，透视缩短更强。
  这些是当前资源的只读数学估算，不是用户截图像素测量或新客户端视觉验证。
- 新Ready Slim右臂枪遮挡86/363=23.69%，TaCZ16/242=6.61%；旧近全遮挡不再成立。
  可见纵向比例AFL .865/.8975、TaCZ .390/.4025，不等于像素面积；
  TaCZ虽然更多臂长在画面外，留在画面内的绝对长度仍更长。
- 排序：P0最终显示尺寸契约；P1 Reload深度/轴向；P2局部接触遮挡与Slim/袖子构图。
  建议后续先审定全Native共用、跨状态固定且preview/runtime同源的手部presentation尺寸方案，
  再处理本枪Reload的深度/展开方向。不是建议恢复.246、直接复制TaCZ的1.5、修改gun设计，
  或回到short hand/thin surface/per-state scale。当前NativeHandBinding的scale-free注释过时，
  但其实际B_skin仍是刚性平移，本轮未改生产注释或实现。
- 本轮只新增本专项审计文档与tools/audit-player-arm-presentation.mjs，并追加本摘要。
  未修改production/DEV Java、bbmodel、geo、动画、Display，未运行Gradle或客户端，
  未截图/preview、未复制TaCZ代码/资产、未新增依赖、未commit/push。
