# AFL Canonical Hand Locator — Authoring Equivalence 3.1 / V0.5.2

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

## Reload Composition V2：Blockbench已通过，Runtime已同步

2026-09-07用户确认Blockbench Preview Gate=PASS。保留现有V2源及所有绑定，
只导出Runtime animation；基础anchor/B_skin/reference geometry、Display、Ready/Fire与Java不变。
V2握把支点与左手操作设计、完整参数见framework文档顶端，不再沿用V0.5.2 Reload。
geo不重导：现有format_version/一个size舍入差异与本轮无关，不能为全量export --check而改几何。
animation精确匹配源导出；compileJava/processResources/build通过，客户端已启动。
现有Gecko 40次动作/7,320帧及Classic/Slim等价检查PASS；完整Reload实机视觉仍PENDING_USER。
重点验收起手、.40抽匣、.95插入、1.10–1.30回位；不将Blockbench通过等同于Runtime目视通过。

## 以下为保留的V0.5.2绑定契约（Reload动作已由上方V2替换）

V0.5.2只校准FP Display枪线与fp_root早期缓入；V0.5.1手臂尺寸与所有基础手部绑定冻结。
新Ready/Reload起手用户视觉仍PENDING_USER，不能沿用上一轮验收。

当前V0.5.1通用第一人称手臂presentation已获用户本轮“目前没有问题”反馈；
Ready/Fire通过，Reload功能无已知回归，深度/展开方向视觉仍留V0.5.2。V0.4.x 的固定 .246、
正交化“预览”、Java gunVisualScale、DEV fail-closed gate 均不再是制作规范。

## 唯一源与坐标契约

Service Pistol 源：
`src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`。
Runtime 不直接读取 bbmodel；保存后必须显式导出资源，再构建。

```text
root
  fp_root                           仅第一人称侧开动画；非FP遍历时跳过此容器变换
    weapon_root                     共同 Fire / Reload 原动作
      gun_model_root                中性组织节点，无 scale track / Java override
        gun                         已烘焙物理尺寸的77个cube与机械定位点
      right_hand_motion
        right_hand_anchor
          right_arm_reference       Classic, source-only
          right_arm_reference_slim  Slim, source-only, 默认隐藏
      left_hand_motion
        left_hand_anchor
          left_arm_reference
          left_arm_reference_slim
```

所有 reference group / cube：export=false。显示 Classic 或 Slim 之一，不同时叠看。
编辑 anchor / motion，不手工移动reference cube。源参考cube是规范几何的显示代理，
由tools/native-arm-presentation.mjs围绕手端烘焙presentation尺寸；导出检查拒绝过期代理。
旧四臂模板仍是4×12×4/3×12×4的中性单位标尺，不是最终FP尺寸预览；它没有hand_anchor制作层级。
Service Pistol是当前presentation参考实现候选，尚非视觉批准的成品。

- origin = 手端 distal-cap 中心，前臂沿 -Y，掌面法线 +Z，横向 +X。
- 原始Classic局部bounds=(-2,-12,-2)..(2,0,2)，4×12×4。
- 原始Slim bounds=(-1.5,-12,-2)..(1.5,0,2)，3×12×4。
- V0.5.1源显示代理bounds=上述bounds逐轴乘(.62,.78,.62)/source FP Display uniform scale。
  当前.41下系数=(1.51219512195,1.90243902439,1.51219512195)。这仅改变export=false代理的from/to，
  不是改Vanilla baked geometry、UV、B_skin或locator。代理手端仍在anchor，rotation=0。
- reference origin 与 anchor 相同，reference group/cube rotation=0。
- 袖层每面额外 .25 模型单位，由真实玩家皮肤和 sleeve 开关决定显示。
- 16 模型单位 = 1 渲染单位；框架 canonical arm scale=1。
- B_skin=T(-centreX,-10,0)/16，Classic右/左centreX=-1/+1，Slim=-.5/+.5。
  该适配器不读武器名、动作状态、Display、camera或枪体尺寸。

## Runtime 职责

`M_arm = M_evaluated_contact × S(1/s) × S(.62,.78,.62) × B_skin`。

其中s为已求值矩阵内的统一缩放，当前.41；只抵消该标量，不归一化三个轴、
不执行Gram-Schmidt、不提取重建Quaternion、不修复shear。输入须为刚性变换×统一scale；
非有限、退化、非均匀/shear输入明确不支持并跳过当前绘制，DEV测试/导出契约检查负责报告。
新武器须遵守统一Display、hand祖先无scale轨道的既有契约；不能依赖拒绝路径制作隐藏动画。
S_presentation全Native共用、跨动作固定，禁止per-gun/per-state覆盖；枪仍继承原Display。
位置与旋转由animated locator决定；先把Vanilla cap通过B_skin归零，再施加presentation，
因此列向量乘法中S在B_skin左边。若机械照写B_skin×S会推走手端。
完整 PlayerModel arm/sleeve、实际 skin、Classic/Slim，
PartPose/visible/skipDraw/scale 在 finally 恢复，枪与手矩阵/材质 buffer 分离。

FirstPerson 仅接管当前 Native Service Pistol、保留 incoming camera 与 -0.6×equipProgress。
Presentation 仅剩 applyEquip。Renderer 不在第一人称添加任何美术 pose/scale。
保留的非FP历史尺寸兼容逆变换不作用于双手或FP：先撤销本次围绕(.1,7.75,9.2)
的 .8 烘焙，再撤销历史围绕(0,8,6)的 .5；第三人称 CROSSBOW_HOLD 不变。

## 当前源数据

枪原先已经物理缩小 .5；本次没有再次减半。仅把旧 gun_model_root .8 的效果烘焙到
gun 子树的 bounds、cube/bone pivot、inflate、65个机械 position keys。删除两个动画
中共4个恒定 .8 keys。贴图/UV/设计细节保持。握把壳宽2.4→1.92，套筒主体宽2.8→2.24，
slide后坐峰值1.6→1.28；枪口/抛壳/瞄点同步。reference 尺寸不变。

READY（2026-09-07用户已摆好reference pose的等价迁移）：
right anchor=(.35,6.4302294997,4.3735038416)，rotation=(-79.962575657,4.9238473409,.8703813957)；
left=(-.4404511189,6.3549129897,3.3069197667)，rotation=(-73.7006336975,-16.448834621,-15.1570841619)。
两个motion不改。上述值由旧anchor→reference group→用户reference cube完整矩阵合成得出，
不是重新设计姿势或直接相加欧拉角。Classic所有角点保持原位置；Slim沿用同一新手端坐标系，
只保持3单位宽度，reference group/cube均恢复canonical绑定。B_skin/Java未改。
这些是用户源姿势的等价表示，不是本轮游戏目视通过证明。

Fire .14s：原旋转、时点、共同后坐及声音不变；双手通过共同祖先随动。
Reload 1.30s：原 weapon_root 动作保留；新增 fp_root position峰值
(2.6666666667,14.9333333333,-13.3333333333)、rotation=(8,32,-16)，pivot=(0,8,6)。
V0.5.2起手替换旧smoothstep：u=(t/.24)^1.5、f=6u^5−15u^4+10u^3，
position/rotation同乘f，0–.24s每.005s烘焙linear keys；保持到.85s、
.85–1.18s每.03s采样回零，1.30s保持零；Runtime 不再计算曲线或旧 .3 除数。
左 motion .08–.22s进入弹匣操作接触偏移(.45,-2.7,1.8)，.22–.93跟随当前
magazine 的同一 position delta，.98–1.23回支撑位。guide与magazine仍共轨；
magazine .52隐藏/.60显示的 afl_hold、音效.40/.95s不变。
右手全程保持共同 carrier 下的固定主握绑定，不切换 geometry。

导出时间键必须保持严格递增：正整数秒写为"1.0"而非"1"，避免JS对象把整数键排到
小数键前面。Gecko4.7.4按JSON entry顺序计算时段；数值相同但顺序错误也会破坏动作。
V0.5.2修复fp_root这一既有问题，保留后段源keys但纠正Runtime求值，旧错误画面不视作冻结权威。
DEV须比较同秒source曲线与Gecko实际骨骼，不可只比较最终回位或JSON对象相等。

共20个导出bones、77个gun cubes；V0.5.2仅增加fp_root早期position/rotation采样，不新增动作。

## Display authority 与艺术工作流

原JavaBASE T(.51,-.44,-.70)、Ry(-4)、Rx(-4)、S(.82)已按矩阵等价合并到源 Display。
这是授权的authority迁移，不是重新优化用户取景：
右 T=(1.0014804164,-7.2445006303,-11.6862374563)；
左 T=(.5924791558,-7.2445006303,-11.7148376105)；
上述T为历史高精度迁移记录，当前保存源T已四舍五入到5位小数。
V0.5.2右R=(.54546703,.19150607,-.27948)、左R=(.54550276,.11247657,-.27948)°，
S=(.41,.41,.41)不变。依据muzzle/barrel -Z射线在camera Z=-20参考平面投影对准中心，
并用sight_anchor和前后准星轴交叉核对；不是ADS，不能以改变单手绑定代替整套rig旋转。
左惯用手仍使用 Vanilla Display镜像约定。只同步这两个FP context，其他Display冻结。

1. 在指定源文件中用标准参考臂审查物理比例与握持；用 anchor/motion 控制姿态。
2. 在动画模式编辑 Fire/Reload，所有侧开和手部动作必须在源中看得到。
3. 在Display中摆放rig，只允许统一scale；它控制枪与接触位置，不决定最终手臂厚长。
4. 保存；执行 `node tools/native-arm-presentation.mjs --patch src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel`，
   由Codex应用输出patch；该操作只更新四个source-only参考cube的from/to，不改枪/anchor/keys/UV。
   执行同命令的`--check`核验；重新打开磁盘源后，BB普通模型/Display视图可直接看代理尺寸。
   Display scale改变后必须重同步代理；不要用旧BB缓存覆盖磁盘文件。再执行`node tools/export-native-gun.mjs --write`。
5. 执行 `node tools/export-native-gun.mjs --check`、`tools/verify-service-pistol.ps1`，
   再 processResources/build。此为文件导出器，不声称调用 Blockbench MCP。
   V0.5.2只同步animation/FP Display并用`node tools/check-native-gun-aimline.mjs`检查；
   geo因冻结约束保留旧format/guard精度差异，全量export --check仍可能报告该既有差异。
6. 明确请求后再启动客户端，用户按 Ready双手→Fire→Reload→重复动作→F5→GUI 验收。
   完成测试后关闭测试客户端；不截图、不自动生成preview文件。

## 测试与可见性

默认 dev==release：双手。不需要数值PASS来开放手臂。
`/afl_nativegun_debug right|both`是明确opt-in的DEV视图选择，不记录视觉通过。
Smoke/Contract 的FAIL只记日志，所有临时骨骼状态恢复；发布JAR排除DEV classes。

Authoring equivalence 比较直接source hierarchy/presentation reference顶点与真正烘焙的
Vanilla Classic/Slim全臂/袖层顶点，包含手端、两侧全部角点、相对frame主握矩阵、
多次Fire/Reload回位；source与export的所有key、UV和尺寸另行精确检查。
同帧骨骼动画delta来自实际Gecko求值，不用第二个动作时钟；不能把它说成独立GPU观感测试。
袖层代理inflate按presentation/display逐轴换算；不向runtime导出参考臂或袖层。
另测incoming .30/.41/.50/.80/1.00的最终轴长及手端漂移。旧正交化和内存重摆姿势仍未恢复。

NUMERIC PASS ≠ USER VISUAL PASS。握持自然、遮挡、前臂出屏和比例须由用户目视判断。
当前用户对V0.5.1 Ready/Fire反馈暂无问题；这不代表本枪后续Reload构图或所有皮肤外观已全部验收。
