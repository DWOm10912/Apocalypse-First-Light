# AFL Weapon Art Standard V1 — V0.5.2 source aimline / entrance easing

> 弹药资产当前版本已由 `tools/export-native-ammo.mjs` 替代：四项正式 9x19mm / 762x51mm round/casing 均有独立源模型、3D item 与贴图。下文旧9mm资产及导出器仅为历史记录，不再用于游戏主引用。见 `docs/native_guns/native_ammo_assets_v1.md`；新资产尚待游戏内目测，旧版本视觉通过不继承。

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## V0.6.2 — 用户枪焰贴图 / 原创弹壳视觉消费者

原样接入用户128×128 RGBA枪焰图（透明角落、高亮不透明核心）：
assets/apocalypse_firstlight/textures/effects/p9_01_muzzle_flash.png。
不重新绘制，不用TaCZ素材。NativeGunFxLayer读取muzzle_anchor最终遍历矩阵，
以3个双面正交quad、emissive shader和SRC_ALPHA+ONE混合绘制1tick短闪；不修改枪械资产。
用户追加尺寸调整：FLASH_SCALE .34→.17（50%）；贴图/面片/寿命/混合及弹壳参数不变，新尺寸尚未实机验收。
现有9mm_casing.bbmodel及models/item/9mm_casing.json通过export-9mm-assets.mjs --check，
本轮只注册额外baked model供客户端瞬态FX绘制，不新增物品注册或服务端实体。
模型几何/黄铜色板/9mm Round/Display不变；V0.6.2.1 casingVisualScale=.072（原.24的30%）仅用于FX绘制。
同轮仅翻转抛壳局部横向-X→+X，保留其它分量/物理/音效/枪焰；2026-09-07用户确认尺寸、四向与F5回归没有问题。
落地音原样接入用户shell_casings_dropping.ogg；详见framework V0.6.2节。
构建与50/50服务端回归通过；第一/第三人称FX实机视觉仍待验收，不以数值代替。

## V0.6.1 — 最小空仓机械状态（当前）

P9-01 Service Pistol可编辑源仍为 src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel。
原fire/reload轨道、非animation源字段、Geo、Display、贴图及双手构图保持不变。
新增fire_last_round、empty_idle、reload_empty，由 tools/add-native-empty-states.mjs 派生；
tools/export-native-gun.mjs 将五个clip导出至
src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json。
empty_idle使用loop；仅slide/front_sight/rear_sight/sight_anchor保持后退1.28模型单位。
最后一发在0.04s后保持后锁；空仓换弹1.10–1.30s平滑前进，其余原换弹轨道逐值保留。
这不是独立完整Empty Reload，不新增释放滑套手势，不改0.40/0.95节点或1.30s时长。
派生器会重新生成这三个clip，手工修改派生clip前应明确源权威，不能误覆盖用户新动画。
2026-09-07：导出一致性/构建通过，实机截图已核对，用户确认本版测试没有问题。
服务端50/50测试与视觉验证边界详见 docs/dev/native-gun/native-afl-gun-framework-v0.md。

## 9mm Round / Casing V1 — V0.6已注册Round，Casing仅资产

2026-09-07：沿用已有储罐、铅箱源模型所在的 `src/main/blockbench/`，新增
`9mm_round.bbmodel` 和 `9mm_casing.bbmodel`。不修改 P9-01 Service Pistol。
原创 cube-only 普通 Java item 模型，纵轴 Y：Round X/Y/Z=4/12/4，
Casing=4/8.015/4（包含底火微小突出）。分别14/10个cuboid。
两者共用完全相同的弹壳局部几何；Round增加四段铜色弹头，
Casing保留开放四壁和暗色腔底。底缘、抽壳槽和中央底火独立表达。
共用原创16×16 `textures/item/9mm_palette.png` 色板，源模型嵌入同一PNG。

普通运行时资源为 `assets/apocalypse_firstlight/models/item/9mm_round.json`
和 `9mm_casing.json`，不使用GeckoLib。每个模型的唯一几何通过七项
Display适配GUI、左右第一/第三人称、Ground、Fixed。V0.6已注册9mm_round并接入17发弹匣；
Casing不注册，未实现抛壳。两份几何/色板不变。
用户授权HUD使用textures/gui/gun/p9_01_hud.png（925×574），无背景；
背包图使用textures/item/p9_01_inventory.png（767×524），原图完整复制，无重新绘制。
第一人称枪体UV贴图、源模型、Geo、动画和Display均不改。功能/验收详见Native框架V0.6节。

`node tools/export-9mm-assets.mjs` 重建上述生成资产；`--check` 只读检查一致性。
源文件可独立编辑，但再次执行生成器会覆盖手工修改，编辑后应同步生成器或另行导出。
结构/尺寸/源与导出一致性已检查；Blockbench实际打开及GUI/Hand/Ground
实机视觉尚未验证，不能将已配置Display视为视觉通过。
资产V1历史验证：`processResources build --offline` 通过（30s），`git diff --check`
及生成资产一致性、尺寸正值、UV范围、贴图引用检查通过；该资产任务未启动客户端。
V0.6新增弹药/HUD的构建、测试与客户端证据以framework文档V0.6节为准。

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



## Final Grip Composition — 当前候选，V1未冻结

实际最新源right_hand_anchor仅X -.70→-3.3064409003；按掌中心/握把中心的横向投影代理求解，
不是沿用旧候选。左anchor、右手静态旋转、Display、手臂比例、B_skin和Fire轨道不变。
Reload新增gun_model_root X=-3.2*w侧移（0–.30s进入、1.06–1.30s退出），允许有意枪手分离；
用户实机指出初版+3.2方向反了；已反转该方向与左手跟随，待复验，其余保持。
左motion X仅同步枪体操作位置。现有rotation、mag路径、.40/.95节拍及1.30s冻结，无Java修改。
源/geo/animation同步与build通过；正启动客户端等待实机验收，代理数值对齐不代表视觉通过。
详细参数见framework顶端；下方State-based Grip是已被用户判定仍需修正的历史候选。



## State-based Grip Separation — 当前资产候选（视觉待验收）

本轮right_hand_anchor X=.50→-.70，left_hand_anchor Y降低.35；其余静态旋转/坐标保持。
Reload只调整fp_root、left_hand_motion position，新增right_hand_anchor X18度腕部轨道；
fp_root主要滚转由约-80修至-50度，额外X+.50，平滑进入/退出，不改Java/镜头/缩放。
左右reference同步anchor，完整Classic/Slim与B_skin路线不变。
Fire、弹匣路径、1.30秒Reload与音效冻结。geo/animation已同步，未启动本轮客户端，视觉未通过。
完整曲线及验证边界见framework顶端；下方“最新用户源同步”是上一轮历史记录。


## 当前Ready手位 — 用户最新源等价同步（待目视）

2026-09-07以用户09:17:40保存源为权威。右anchor X=.50保留（本轮早先+.15），
不继续叠加偏移。用户左Classic reference局部(-4,0,-1)/旋转(-5,0,20)已等价迁入left_hand_anchor：
origin=(-4.3175392256,6.2607727898,2.1051145623)，
rotation=(-79.3660643678,2.7946316784,-9.6421944875)度。
参考子节点归零不是撤回用户姿势；Classic外观等价，Slim同步同一绑定。
源已有gun X -3同步到完整geo；animation同步源已有的gun零position轨道，其余轨道不变。
源枪几何/动画、Display、B_skin、通用手臂比例、Java、贴图和音效不改。
build通过；旧候选客户端曾因geo枪pivot过期而smoke FAIL。该客户端已退出，未自动重启，最新资源视觉仍待确认。
完整参数与验证边界见framework顶端；下节V2记录为历史。

## Reload Composition V2：Blockbench PASS / 已导出Runtime

2026-09-07用户明确通过V2正常速度Blockbench预览，源动画保持原样。
已只同步animation.json并build、启动测试客户端；geo/Display、Ready/Fire、尺寸、贴图、绑定和Java未改。
右手握把锚定旋转、左手主导取新匣/插入；1.30s与.40/.95声音节点冻结。
现有Gecko重复动作与手臂等价数值检查通过；Runtime遮挡、重量感、末段回位仍等待用户实机验收。
源设计与实机结果分开记录，不将Blockbench PASS直接写成Runtime VISUAL PASS。
参数、导出边界与测试证据见framework文档V2章节。

## 历史V0.5.2（Reload已被V2替代）

V0.5.2：hip枪线用muzzle/barrel -Z与sight方向投影辅助校准；只调整源FP Display旋转，
让枪和双手共同运动，不改anchor握持、枪尺寸或通用(.62,.78,.62)手臂尺寸。
Reload只在源fp_root的0–.24秒position/rotation烘焙缓入，每.005秒linear采样；
0.24秒后局部路径、弹匣/左右手动作、音效.40/.95秒不变。Java不负责reload presentation。
制作规则与数值见framework文档V0.5.2；本轮新视觉PENDING_USER，不沿用旧Ready验收。
导出key必须按秒递增，正整数秒用小数文本（如"1.0"）；JS整数键重排会让Gecko时段错误。
本轮修正这一导出缺陷，后段源路径未重画，但错误的旧Runtime时序不再保留。

当前实现/参数以 [Canonical Hand Locator 3.1](native-gun-hand-locator-authoring-standard.md)
为准；V0.4.x 的 .246 独立手臂缩放、Java gunVisualScale=.8、短掌/薄面、数值视觉门
全部废弃。P9-01 Service Pistol V0.5.1已获本轮Ready/Fire用户“目前没有问题”反馈；
Reload功能暂无回归，深度/展开方向视觉留V0.5.2，不宣称所有皮肤外观均已验证。

## Source ownership

唯一可编辑枪源：
`src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel`。
模型师在此完成枪的物理尺寸、双手 Ready、Fire、Reload 与整套 Display。
Runtime geo/item JSON 是导出产物，不代替可编辑源文件。
保留中性参考臂模板 `src/main/blockbench/templates/afl_first_person_player_arm_rig.bbmodel`；
旧 `afl_weapon_rig_template.bbmodel` 仅作历史参考。

## 比例与手部契约

- 玩家Classic 4×12×4、Slim 3×12×4，完整原版arm/sleeve和真实skin，不改baked geometry或UV。
  V0.5.1仅在FP矩阵施加全框架固定(.62,.78,.62)非均匀presentation；不是按武器/状态缩放。
- 枪适配标准手臂；gun-only物理调整须同时处理cube/bone pivot、inflate、机械平移和枪体locator。
- 当前P9-01 Service Pistol已包含历史 .5迁移及本轮原 .8视觉效果的实体烘焙；不得重复缩半。
  握把壳宽1.92，套筒主体宽2.24，slide峰值1.28模型单位。
- 所有参考臂group/cube export=false；Classic默认显示、Slim默认隐藏，可切换核查。
  手端原点、forearm=-Y、palm=+Z、lateral=+X；reference局部rotation=0。
- 编辑hand anchor/motion，不独立摆reference cube。source-only显示代理由通用helper按presentation/Display
  围绕cap更新bounds；规范几何、精确绑定与唯一B_skin见authoring standard。
- 右手主握grip/backstrap，左手偏低/前侧支撑，不镜像夹枪。接触重叠需要用户目视验收。
- 同一个locator/full-arm路线贯穿Ready/Fire/Reload，不因换弹换几何、scale或Java offset。

## Hierarchy / 动画

root→fp_root→weapon_root；gun_model_root/gun与两个hand_motion是共同carrier下的兄弟分支。
fp_root只承载第一人称侧开；weapon_root保留共同动作；枪体机械轨道独立。
gun_model_root是中性组织节点，无Java override、无恒定scale动画。
Fire .14s、Reload1.30s、声音时间不变。source中可见左右手随动、左手拿弹匣和回Ready。
77个gun cubes/20个runtime bones/183 keys；reference不导出、reload_magazine仍为空guide。

## Materials / 导出

枪使用原128×128 p9_01.png，参考臂使用原16×16中性纹理，不能互绑。
缩放几何前固定逐面UV，避免box UV根据新尺寸重新取样。贴图设计和皮肤UV保持。
FP Display仍需统一缩放；影响枪和接触位置，不决定最终arm/sleeve轴长。
先用`tools/native-arm-presentation.mjs --patch <source.bbmodel>`同步四个export=false代理并应用patch、核验`--check`。
保存后执行 `node tools/export-native-gun.mjs --write`，再 `--check` 和
`tools/verify-p9-01.ps1`。只同步两个FP Display，不覆盖无关TP/GUI。
本轮为文件导出，不声称使用了当前不可用的Blockbench MCP。

Runtime求值动画、固定绑定与通用presentation，保留皮肤、袖层、深度/矩阵隔离。新枪不新增PlayerArmRenderer，
不增加每枪B_skin或姿态硬编码。非FP历史尺寸兼容是P9-01 Service Pistol保旧外观的例外，
不是FP制作参数；若未来重制world资产应单独迁移，不让模型师用它校准握持。

## 验收

默认DEV与release均双手；数值FAIL不改画面。显式DEV命令
`/afl_nativegun_debug right|both`仅切测试视图。
源/导出数据、真实Vanilla顶点和重复动画数值等价是必要条件，不是用户视觉通过。
Ready双手、Fire、Reload、重复动作、F5、GUI以及Classic/Slim按序实机验收。
不自动截图，不生成preview图，不未经用户请求启动客户端。验证记录见framework文档。
