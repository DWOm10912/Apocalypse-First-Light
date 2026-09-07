# AFL vs TaCZ Player Arm Presentation 专项审计

日期：2026-09-07。只读审计，当前工作树 master / 7685f61；不是新视觉修复版本。

## 1. 结论、范围与证据边界

**同样的 Vanilla 手臂几何不等于同样的最终显示尺寸。当前 AFL 手臂继承 .41 的共同 Display；
本地 TaCZ Glock 17 的手部父动画包含 [1,1.5,1] scale，且 Reload 的深度与前臂朝向明显不同。**
这是本轮“细、短、轻”差异的主要解释。PlayerRenderer hand helper 本身没有把几何变粗、变长。

本轮纠正三个过时前提：

- 当前 NativePlayerArmRenderer.PLAYER_ARM_SCALE=1F；canonicalPose 不再正交化/剥离父缩放。
  本模型当前实际 basis 为 [.41,.41,.41]，不是 .246。
- 当前 gun basis 同为 .41；旧 gun-only .8 已烘焙进资产，不能再按旧 .328 计算。
- 新 Ready Slim 右臂采样为 86/363 被枪遮挡，不是历史 240/242。
  新分母不同是朝向变化使三个面而非两个面朝向相机，不能把旧比例当作当前原因。

用户所给图片表明有真实的表现差异，但不同截图的尺寸、裁切、动画时刻、视线和 FOV 不完全一致。
以下数字是**当前磁盘资源及已检查调用链的确定性参考场景估算**，不是截图像素测量、GPU深度读回或新实机验收。
没有启动客户端、Gradle、截图、preview，没有修改生产/DEV代码、模型、动画或Display。
现有 dirty 工作树属于用户，未回退；下面列出的文件变化仅限本审计。

## 2. Sources inspected / 审计来源

### 本地第一权威

- 读取既有四份文档：native-gun-tacz-first-person-hand-audit.md、
  native-gun-first-person-runtime-path-audit.md、native-gun-hand-locator-authoring-standard.md、
  native-afl-gun-framework-v0.md。历史 V0.4.x 描述不覆盖当前 V0.5 源码。
- C = src/main/java/com/antaurora/apofirstlight/weapon/client/：
  ServicePistolFirstPerson、ServicePistolRenderer、ServicePistolHandLayer、NativePlayerArmRenderer、
  NativeHandBinding、ServicePistolPresentation、ServicePistolRenderMatrices。
- src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel；
  Runtime：src/main/resources/assets/apocalypse_firstlight/geo/service_pistol.geo.json、
  animations/service_pistol.animation.json、models/item/service_pistol_in_hand.json。
- 本地 TaCZ 1.1.8-hotfix 原始依赖JAR与 mapped JAR。坐标 curse.maven:timeless-and-classics-zero-1028108:8141310。
  原JAR SHA256：9ed8ada1283ed7a793a70cc1b51c4a340f367ce84707e1a7b8cf21ee3d288d77。
- JAR 内置 Glock17 geo / animation / display 在内存中读取；
  与 run/tacz/tacz_default_gun/assets/tacz 下对应三个文件逐字节一致。
  没有抽取或复制 TaCZ 资产到 AFL。
- javap 检查 TaCZ BedrockGunModel、RightHandRender / LeftHandRender、动画Listener、
  MathUtil、Bedrock模型转换与PlayerModelMixin相关实现。
  内嵌 SimpleBedrockModel 2.2.2 的 FirstPersonRenderHandler.class 在内存读取：
  RenderHandEvent入口/对应lambda调用 renderFirstPerson，未增加 PoseStack scale。
- 本地 Forge 47.4.22 sources JAR：PlayerModel、HumanoidModel、PlayerRenderer。
  原版 arm/sleeve、setupAnim pivot、renderHand 与 Forge hand hook 为本轮比较依据。

### 公开主来源交叉核对

TaCZ固定版本提交 b43eb84c38e9768d8e73c8b14f0b845669704b38：

- [RightHandRender][right]：固定Z180与延迟手部绘制。
- [BedrockModel][model]、[BedrockPart][part]：pivot、动画偏移、静态旋转和父scale顺序。
- [GunItemRendererWrapper][wrapper]：FP独立路径、位移/坐标转换、非FP display分支。
- [FirstPersonRenderGunEvent][event]：idle_view逆变换与瞄准取景。
- [RenderHelper][helper]：调用实际玩家的PlayerRenderer。
- [官方 hand_pos 规范][wiki]：Classic/Slim预览规格与runtime替换。
- SBM固定提交的在线原文件本轮获取失败；入口依据本地内嵌类检查，不把网络失败说成已读源码。

## 3. 方法、坐标与可复跑性

只读脚本：tools/audit-player-arm-presentation.mjs。

运行：`node tools/audit-player-arm-presentation.mjs --timeline`。
输出仅stdout JSON；脚本不写文件、不解压JAR到磁盘、不运行游戏。
使用节点/立方体变换与相机到表面点的OBB射线求交；矩阵含非均匀缩放，并有逆矩阵自检。

统一假设：

- 右惯用手、站立、equip=0、ADS=0，垂直FOV=70°、16:9、1080px高、近裁面 .05。
  本机 options 的 fov=0 对应默认70；不能据此保证截图瞬时投影完全一致。
- Ready用静止基线；Reload取原始clip .50s，并复核 .30/.80s。
  AFL总长1.30s，TaCZ reload_tactical总长1.9333s；同秒不是同归一化进度，也不是同一reload语义。
- 不模拟动态camera轨道、bob/sway/hurt、ADS、状态混合、第三方RenderArmEvent替换。
  TaCZ确有camera动画，真实画面会因此偏移；本轮静态相机结果不可当作逐帧游戏结果。
- 只建模枪的opaque OBB；TaCZ采用有弹标准弹匣、无配件，排除扩容弹匣互斥分支、
  手部预览、特效等。未计算纹理alpha、双手互遮、袖子遮皮肤、HUD遮挡。
- camera X向右、Y向上、Z负方向为前方，单位为render unit（模型单位/16）。
- arm cap=几何手端中心；wrist proxy=距手端4模型单位，midpoint=6，proximal end=12。
  Vanilla是完整刚性方柱，没有真实掌骨/腕关节；wrist不能冒充解剖学精确标记。
- 文中的“可见末端”是从手端沿臂轴最后一个可见采样截面中心，不是肩骨。
- 可见比例主指标：整条12单位臂分400截面，存在至少一个朝镜头、在视口内、
  且未被枪遮挡的表面点即算可见截面。它是**纵向覆盖率**，不是可见皮肤像素面积。
- projected/visible length：以截面中心线的投影步长加权；存在任一可见表面就记该段可见。
  因此“部分表面被枪遮挡”与“整段轴线不可见”是两项不同指标。
- 面遮挡：6面各11×11点，保留朝向相机的面，含重复边点；分母可能242或363。
  统计在视口裁切前进行，比例不是最终屏幕面积。历史242规则不强行套到新姿势。

## 4. AFL arm pipeline / 当前实际链

RenderHandEvent → ServicePistolFirstPerson → applyEquip →
ItemRenderer / FP Display → ServicePistolRenderer / Gecko动画 →
ServicePistolHandLayer的animated anchor pivot → canonicalPose完整矩阵副本 →
B_skin → 实际玩家PlayerModel完整arm与可选sleeve。

- Presentation只剩 T(0,-.6×equip,0)，没有旧Java静态BASE或Reload侧开曲线。
- Display：T=(1.0014804164,-7.2445006303,-11.6862374563)/16，
  Rxyz=(-4.0097357701,-3.9902403982,-.2794778767)°，S=.41。
  ItemRenderer/Gecko居中合并后剩T(0,.01,0)，脚本包括它。
- root→fp_root→weapon_root 下，gun与双手motion/anchor分支并列；
  手继承共同Display及动画父变换，不继承gun子树的几何尺寸。
- HandLayer读取已求值骨骼矩阵；不只读取静态anchor。当前两anchor和reference等价迁移已写入源/geo。
- B_skin=T(-centreX,-10,0)/16；Classic右/左centreX=-1/+1，Slim=-.5/+.5。
  part归零后，范围变成[-width/2,-12,-2]..[width/2,0,2]，cap正好是anchor原点。
- canonicalPose仅检查finite/determinant并复制，不重建rotation/scale basis。
  NativeHandBinding旧Javadoc仍说“scale-free / never inherits display”，**该注释已过时**；本轮只指出，不改生产文件。

## 5. TaCZ arm pipeline / 当前Glock17链

SBM RenderHandEvent → GunItemRendererWrapper第一人称路线 →
共同视图/idle_view逆变换 → Bedrock动画父节点 → righthand_pos / lefthand_pos →
Right/LeftHandRender固定Z180 → 延迟矩阵快照 → RenderHelper →
当前PlayerRenderer.renderRightHand/LeftHand → 原版arm/sleeve。

- Functional hand节点以玩家手替换authoring预览，而不是把预览cube作为皮肤手绘制。
- BedrockPart保留父缩放；手部延迟任务复制pose/normal，**不等于**移除缩放或重建单位basis。
- Wrapper中的display scale thirdperson=.6、ground=.6、fixed=1.2属于非FP分支，
  不能拿它们乘到第一人称手臂。
- 额外FP校正包括入口Y/Z坐标转换、idle_view基准、动作/移动/瞄准和camera相关处理；
  不存在一个通用“.246手臂缩放”。hand helper内部也没有专门宽度放大。

## 6. Scale、geometry、Classic/Slim、sleeve

| 项目 | AFL当前 | 本地TaCZ Glock17 |
| --- | --- | --- |
| Classic原始geometry | 4×12×4 | 4×12×4 |
| Slim原始geometry | 3×12×4 | 3×12×4 |
| 完整12高度 | 是，没有裁短 | 是，没有裁短 |
| sleeve inflate | .25/面 | .25/面 |
| Classic sleeve几何包围盒 | 4.5×12.5×4.5 | 4.5×12.5×4.5 |
| Slim sleeve几何包围盒 | 3.5×12.5×4.5 | 3.5×12.5×4.5 |
| Ready /所采Reload最终轴长 | (.41,.41,.41) | (1,1.5,1) |
| Classic变换后宽长深，render units | .1025×.3075×.1025 | .25×1.125×.25 |
| Slim变换后宽长深，render units | .076875×.3075×.1025 | .1875×1.125×.25 |

上表尺寸顺序始终是 **X宽×Y长×Z深**。同一裸臂宽/深，TaCZ:AFL约2.439倍；
长度约3.659倍，投影前已显著不同。袖子只增加每边.25原始单位，并不能解释这个倍率。

**关键额外scale来自Glock资源，不来自PlayerRenderer：**
static_idle与reload_tactical的左右hand控制父节点都设置scale=[1,1.5,1]。
这是沿臂长方向的资产变换，不是另一套模型geometry，也不是所有TaCZ枪都必然如此。
不要把“TaCZ用完整Vanilla arm”误读为“TaCZ保证最终各轴scale都等于1”。

### .246 追溯

历史文档记录旧共同.82×.30=.246，V0.4.9将其冻结为framework visual style，
canonicalPose剥掉父scale后重建.246轴长，目的是当时解耦枪与手的显示。
V0.5已移除此策略，改为保留完整authoring矩阵。
因此分类为 HISTORICAL_VISUAL_COMPENSATION，**不是**16模型单位到1render unit的必需换算。
未提交历史的确切引入时刻不能由git HEAD证明；版本追溯依据仓库历史说明与当前源码。
当前预览/运行时同源设计应由源Display和canonical arm共同体现，不能将旧普通BB预览视作实现.246的证据。

## 7. Binding origin 与 helper 差异

| 步骤 | AFL | TaCZ/Vanilla helper |
| --- | --- | --- |
| setupAnim | 不调用；每个part临时loadPose ZERO | PlayerRenderer设置可见性/attack/swim并setupAnim |
| pivot | part=0；B_skin将手端移到anchor | HumanoidModel站立setupAnim设肩pivot右(-5,2,0)、左(5,2,0) |
| rotation | arm/sleeve临时三轴归零 | helper归零xRot；TaCZ/SBM PlayerModelMixin配合清理手臂旋转 |
| Slim细节 | centreX改变.5单位，完整3宽geometry | 烘焙初始肩Y=2.5，但站立setupAnim重置为2；此路线不调用translateToHand的Slim±.5补偿 |
| sleeve | 同一原始sleeve；玩家开关；独立绘制和finally恢复 | 原版helper及其sleeve路线，旋转/可见性由helper与Mixin处理 |
| Forge hooks | 直接ModelPart，不调用helper的RenderArmEvent hook | helper经过Forge arm hook，其他mod可取消或替换 |
| scale | part自身scale临时1，pose仍保留.41父scale | helper不新乘.246或宽度因子；pose保留hand父scale |
| render origin | distal cap=anchor | hand_pos不是distal cap，需要Z180与肩pivot/几何偏移 |
| 厚/长主因 | 不在ModelPart.render本身 | 不在renderRightHand方法名称或helper本身 |

TaCZ右手从hand_pos到cap，还需Z180后T((-5+centreX),12,0)/16；
左手为T((5+centreX),12,0)/16。其hand_pos可能在camera后方，而真实手端仍在前方。
直接比较TaCZ hand_pos.Z与AFL anchor.Z会得出错误深度结论。

两条路线的原点/reset/hook确实不同；**不能直接交换矩阵**。
但在各自固定转换都正确应用后，没有helper固有的2.4倍厚度或3.7倍长度增益。
修改AFL改调renderRightHand本身不会自动解决当前差异。

## 8. Camera-space depth / 相机空间

以下Slim、无sleeve，XYZ均为camera render units。

| 场景 | hand cap | wrist proxy（4单位） | midpoint（6单位） | 最后可见截面中心 |
| --- | --- | --- | --- | --- |
| AFL Ready 右 | (0.0646, -0.2765, -0.6300) | (0.0664, -0.2872, -0.5281) | (0.0673, -0.2925, -0.4771) | (0.0693, -0.3042, -0.3659) |
| AFL Ready 左 | (0.0462, -0.2803, -0.6585) | (0.0053, -0.2942, -0.5655) | (-0.0152, -0.3011, -0.5191) | (-0.0766, -0.3218, -0.3801) |
| AFL Reload .50s 右 | (0.1271, 0.0826, -0.9814) | (0.1618, 0.0370, -0.8963) | (0.1791, 0.0142, -0.8538) | (0.2310, -0.0540, -0.7266) |
| AFL Reload .50s 左 | (-0.0189, -0.1926, -0.8717) | (-0.0202, -0.2176, -0.7723) | (-0.0209, -0.2301, -0.7226) | (-0.0229, -0.2676, -0.5739) |
| TaCZ Ready 右 | (0.0587, -0.4029, -0.8954) | (0.1020, -0.4435, -0.5251) | (0.1236, -0.4637, -0.3400) | (0.1092, -0.4502, -0.4636) |
| TaCZ Ready 左 | (-0.0003, -0.4487, -0.9221) | (-0.0745, -0.5303, -0.5637) | (-0.1117, -0.5712, -0.3845) | (-0.0702, -0.5255, -0.5848) |
| TaCZ Reload .50s 右 | (0.1931, -0.1159, -0.7778) | (0.4252, -0.3632, -0.6177) | (0.5412, -0.4869, -0.5376) | (0.5194, -0.4637, -0.5526) |
| TaCZ Reload .50s 左 | (0.0994, -0.2609, -0.5905) | (0.1333, -0.4035, -0.2453) | (0.1502, -0.4749, -0.0727) | (0.1209, -0.3513, -0.3717) |

右手gun frame用AFL weapon_root pivot、TaCZ g17 pivot；两者不是同一解剖/设计点，
只用于各自链内相对深度核验，不能逐值对齐它们来重摆握持。

| 场景 | gun frame XYZ | cap − gun frame XYZ |
| --- | --- | --- |
| AFL Ready 右 | (0.0529, -0.2335, -0.5919) | (0.0117, -0.0430, -0.0381) |
| AFL Reload .50s 右 | (0.1585, 0.1095, -0.9398) | (-0.0314, -0.0269, -0.0416) |
| TaCZ Ready 右 | (0.0543, -0.3125, -0.7500) | (0.0044, -0.0904, -0.1454) |
| TaCZ Reload .50s 右 | (0.3341, -0.0768, -0.6547) | (-0.1410, -0.0391, -0.1231) |

- AFL右cap深度从.630变.981，Reload更远约56%，不是靠近镜头。
- TaCZ右cap从.895变.778，Reload更近约13%。Ready时其cap虽然更远，
  但midpoint深度.340比AFL的.477更近；不能用单个locator深度代表整臂。
- TaCZ右hand_pos：Ready约(-.1526,-.5444,+.2531)，Reload(.8464,-.7017,+.0058)；
  正Z不代表实际手臂消失，是binding origin不同。
- TaCZ Ready右proximal end约(.1885,-.5245,+.2155)，整臂跨过相机/近裁面。
  不能给其“完整未裁切投影长度”一个正常有限px值。

## 9. Forearm axis、掌面、宽窄面

臂轴=cap沿臂向身体延伸的单位向量；camera forward=(0,0,-1)。
下表normal为相同定义的局部+Z前/后长面法线，沿用AFL canonical文档的“palm normal”术语。
**它不是从皮肤像素识别的解剖学掌心。** TaCZ Wiki的“掌心朝内”涉及手臂内侧面，
所以另外列出局部右+X/左-X面的法线，不混同两套语义。

| 场景 | forearm axis | +Z plane normal | dot(forward) | Slim主导长面 |
| --- | --- | --- | --- | --- |
| AFL Ready 右 | (0.0178, -0.1041, 0.9944) | (-0.0073, 0.9945, 0.1043) | -0.9944 | 3px-front/back |
| AFL Ready 左 | (-0.3999, -0.1351, 0.9065) | (0.1597, 0.9637, 0.2141) | -0.9065 | 3px-front/back |
| AFL Reload .50s 右 | (0.3381, -0.4446, 0.8295) | (0.6121, 0.7734, 0.1650) | -0.8295 | 4px-side |
| AFL Reload .50s 左 | (-0.0132, -0.2442, 0.9696) | (0.7564, 0.6318, 0.1694) | -0.9696 | 3px-front/back |
| TaCZ Ready 右 | (0.1153, -0.1081, 0.9874) | (0.0453, -0.9925, -0.1139) | -0.9874 | 3px-front/back |
| TaCZ Ready 左 | (-0.1980, -0.2177, 0.9557) | (0.0327, -0.9759, -0.2156) | -0.9557 | 3px-front/back |
| TaCZ Reload .50s 右 | (0.6187, -0.6595, 0.4270) | (-0.7757, -0.5990, 0.1989) | -0.4270 | 4px-side |
| TaCZ Reload .50s 左 | (0.0905, -0.3804, 0.9204) | (0.0366, -0.9223, -0.3848) | -0.9204 | 3px-front/back |

| 右手场景 | inward skin side normal（局部+X） |
| --- | --- |
| AFL Ready 右 | (0.9998, 0.0091, -0.0170) |
| AFL Reload .50s 右 | (0.7149, -0.4519, -0.5336) |
| TaCZ Ready 右 | (-0.9923, -0.0579, 0.1096) |
| TaCZ Reload .50s 右 | (-0.1246, 0.4543, 0.8821) |

- Ready两者主要看3px前/后面；Reload .50s两者主要看4px侧面。
  因而“TaCZ总看4px而AFL总看3px”不能解释这些当前样本。
- Ready右面余弦：AFL侧面.129、前/后面.605；TaCZ侧面.226、前/后面.858。
  Reload右：AFL .662/.023，TaCZ .843/.260。数值使用臂中点朝眼睛射线，不仅是Z轴。
- Reload右轴Z分量AFL .829、TaCZ .427；以相机平面粗估轴向展开系数
  sqrt(1-z²)分别.559、.904。AFL仍明显朝深度方向，纵向投影更容易缩短。
- 实际屏幕厚度还受端面、透视、袖子及双手重叠影响，不能把这些cos直接当作像素面积。

## 10. 可见长度与遮挡拆分

右手Slim裸臂，1080px高参考投影，中心线截面覆盖法：

| 场景 | 总投影px | 可见px | 全段被枪遮挡px | 视口裁切px | 可见纵向比例0..12 |
| --- | --- | --- | --- | --- | --- |
| AFL Ready 右 | 405.0 | 310.7 | 0.0 | 94.2 | 0.8650 |
| AFL Reload .50s 右 | 190.1 | 172.7 | 17.4 | 0.0 | 0.8975 |
| TaCZ Ready 右 | 未定义（跨近裁面） | 425.4 | 0.0 | 7065.5 | 0.3900 |
| TaCZ Reload .50s 右 | 2986.2 | 663.1 | 93.2 | 2230.0 | 0.4025 |

TaCZ Ready跨近裁面，总投影未定义；表中的7065.5px裁切仅为近裁面前的
离散截断中心线量，不可解释为一条7490.9px长的实机手臂。
为了避免透视奇点误导，严格状态中的visible ratio采用物理截面覆盖率，
而不是visible/这个截断总投影。

AFL Ready→Reload可见长度311→173px，约减少44%；TaCZ约425→663px，增加56%。
TaCZ可见纵向比例.39/.4025反而低于AFL .865/.8975，**但绝对屏幕长度更长**。
所以“TaCZ保留更多整条臂的比例”不是本测量结论；准确说法是更大的臂和展开姿势留下更长的屏幕段。

如果仅看人为定义的wrist proxy至proximal end（4..12），右手可见比为
AFL Ready .795 / Reload .915，TaCZ Ready .085 / Reload .2025。
这说明Vanilla方柱没有真正腕关节，选不同“前臂”起点会改变比例，必须声明定义。

同场景 .30/.50/.80s交叉采样（不是全动画证明）：

| 系统 | Reload秒 | 可见px | 可见纵向比例 |
| --- | --- | --- | --- |
| AFL | 0.50 | 172.7 | 0.8975 |
| TaCZ | 0.50 | 663.1 | 0.4025 |
| AFL | 0.30 | 172.7 | 0.8975 |
| AFL | 0.80 | 173.0 | 0.8975 |
| TaCZ | 0.30 | 666.2 | 0.4000 |
| TaCZ | 0.80 | 659.9 | 0.3975 |

## 11. Gun occlusion 与体型/袖子

| 右手场景 | 几何 | 朝镜头表面被枪遮挡 | 可见轴向px | 可见纵向比例 |
| --- | --- | --- | --- | --- |
| AFL Ready 右 | Slim | 86/363 = 23.69% | 310.7 | 0.8650 |
| AFL Ready 右 | Classic | 28/363 = 7.71% | 310.7 | 0.8650 |
| AFL Ready 右 | Slim sleeve | 41/363 = 11.29% | 324.9 | 0.8875 |
| AFL Reload .50s 右 | Slim | 34/242 = 14.05% | 172.7 | 0.8975 |
| AFL Reload .50s 右 | Classic | 0/242 = 0.00% | 190.1 | 1.0000 |
| AFL Reload .50s 右 | Slim sleeve | 13/242 = 5.37% | 190.1 | 1.0000 |
| TaCZ Ready 右 | Slim | 16/242 = 6.61% | 425.4 | 0.3900 |
| TaCZ Ready 右 | Classic | 12/242 = 4.96% | 449.7 | 0.4000 |
| TaCZ Ready 右 | Slim sleeve | 15/242 = 6.20% | 464.0 | 0.4075 |
| TaCZ Reload .50s 右 | Slim | 37/242 = 15.29% | 663.1 | 0.4025 |
| TaCZ Reload .50s 右 | Classic | 41/242 = 16.94% | 632.5 | 0.4025 |
| TaCZ Reload .50s 右 | Slim sleeve | 41/242 = 16.94% | 647.8 | 0.3975 |

- AFL新Ready Slim：86/363=23.69%，其中frame84、slide2；Classic28/363。
  不是旧99.17%遮挡。Slim更易被窄握把区域覆盖，但尚有可见表面。
- TaCZ Ready Slim：16/242=6.61%；局部手端遮挡合理，不是零重叠。
- Reload右：AFL34/242=14.05%，TaCZ37/242=15.29%。
  此样本“枪遮挡比例更高”不是AFL显短的主因，scale/depth/axis影响更大。
- 面被部分遮挡但同一截面仍有侧面可见时，表面遮挡比例非零而整段枪遮挡长度可为0。
  这不是两个算法互相矛盾。
- 袖子在外层可减少枪对外轮廓的覆盖，也可能增加越出视口的部分；
  其表格是独立外壳采样，不代表合成最终皮肤可见面积。
- 用户图中的浅色大袖端不能据此归因于贴图缺失；同一个skin和geometry可以因截面朝向不同显得像短块。
  本轮没有采集失败帧矩阵，具体每张图的穿插位置仍不能由表格逐像素认定。

## 12. 总对照表

| Concern | AFL | TaCZ Glock17 | Visual Effect | Likely Importance |
| --- | --- | --- | --- | --- |
| arm geometry | 完整Vanilla | 完整Vanilla | 非裁短几何差异 | 排除项 |
| slim/classic | 3/4宽，cap居中 | 3/4宽，helper肩原点 | Slim更易局部被枪挡 | P2 |
| sleeve | .25 inflate、皮肤开关 | .25 inflate、皮肤开关 | 外轮廓稍粗，不是大倍率主因 | P2 |
| final scale | .41各轴 | 1/1.5/1 | 相同裸臂显示尺寸显著不同 | P0 |
| locator origin | distal cap | hand_pos+肩/几何偏移 | 直接比locator位置会误判 | P1（诊断） |
| forearm axis | Reload Z=.829 | Reload Z=.427 | AFL纵向透视压缩更强 | P1 |
| palm normal | canonical+Z | Wiki掌面语义不同 | 须统一面定义，不能盲搬角度 | P2 |
| camera depth | Reload右cap=.981 | .778 | AFL换弹手更远 | P1 |
| gun occlusion | 新Ready23.69% | Ready6.61% | AFL局部接触更遮手，不是全臂失效 | P2 |
| visible length | Reload173px估算 | 663px估算 | 大尺寸+展开姿势留下长前臂 | P0/P1 |
| PlayerRenderer helper | direct part+刚性B_skin | helper+Z180/肩pivot | 有原点/hook差异，无内置放大 | 非scale主因 |
| parent scale inheritance | 保留共同.41 | 保留hand父[1,1.5,1] | 非单位轴保证；取决资产/显示 | P0 |
| display transform | 导出FP Display直接参与 | 这里列出的display scale只管非FP | 不能对两套display字段机械相乘 | P0 |
| reload behavior | source fp_root远移+原动作 | 资产hand controller、取景/动作 | 不能只换helper让姿态自动相同 | P1 |

## 13. Root cause ranking

**P0 — 最终手臂显示尺寸契约不同。**
AFL保留共同Display .41；Glock保持更大的手部basis并有1.5臂长scale。
这不是相同geometry的矛盾，也不是当前残留.246。单个PLAYER_ARM_SCALE=1不代表最终世界basis=1。

**P1 — Reload深度和方向组合不同。**
AFL fp_root把右手端送得更远，臂轴仍主要朝相机深度；TaCZ样本更靠近、向右下展开。
AFL .30~.80的约173px结果稳定，不是只挑一帧的结论，但动态camera、其它帧仍需实机验证。

**P2 — 局部手端遮挡、Slim横截面与面朝向/袖子构图。**
新Ready仍有较多frame覆盖Slim手部；但旧“几乎全遮挡”已不成立，Reload的遮挡比也不支持它为首因。
不是必须改gun设计才能解释，更不是短掌/薄片方案应回归的依据。

## 14. 最小下一轮建议（本轮未实施）

### A. Framework-level：建议先做统一player-arm presentation校准

先保留枪geometry、机械动画、音效、第三人称，只设计全Native Guns一致、
跨Ready/Fire/Reload固定的手部presentation尺寸契约与预览验证。
如果选择独立手部视觉尺寸，必须同步authoring参考臂/预览与runtime乘法位置，
保留已求值locator translation/rotation与B_skin含义，不用按状态scale，不用每枪专用renderer。

当前V0.5继承.41是明确的共享单位契约，并非漏掉某个Vanilla单位转换。
改变它属于**另行授权的契约修订**，不能悄悄恢复正交化、.246或写死TaCZ的[1,1.5,1]。
也不建议直接把共同Display .41改1：那会连枪和接触点一起变大，不能隔离问题。
先以Classic/Slim、同FOV/skin、同一已保存姿势检验框架层固定尺寸方案，
避免一开始重做枪模。没有证据要求复制TaCZ renderer或引入新运行时依赖。

### B. Per-gun authoring：Reload构图仍有必须处理的数据项

若目标包括TaCZ式可见长段Reload，统一尺寸校准后还需处理本枪fp_root /
hand_motion的深度与展开方向；目前右cap远移、Z方向压缩是实际资产数据。
不是通用helper能自动修好的。该项属于后续authoring取景/动作校准，
**本轮没有修改用户刚摆好的anchor/reference姿势**。
Ready局部接触也应在统一尺寸下再确认，不能现在直接补偏移。
两阶段分别验收，任何数值PASS都不等于视觉PASS。

禁止方案：per-state arm scale、per-gun PlayerArmRenderer、short hand、
thin grip surface、Reload-only mesh、照搬TaCZ位置/关键帧/UV/贴图/代码。

## 15. 状态、未做项与文件

本轮仅新增本审计文档、只读审计脚本，并在框架文档末尾追加摘要。
没有导出资源、构建或客户端运行；没有改变最终live行为。
脚本估算不是新实机验证，也不宣称现有握持已通过用户验收。

最终只读检查：src全部文件与build.gradle共1084个文件的SHA256相对审计开始完全不变，
没有新增src文件。框架文档修改前99530字节前缀的SHA256仍完全一致，确认只在末尾追加。
node --check、脚本含矩阵自检的24组场景运行、git diff --check通过；
Git仅提示已有文件的LF/CRLF转换警告，未做换行格式化或Gradle构建。

严格状态的向量为Slim Ready右手；palm字段采用上文+Z plane proxy。
helper_difference_is_visually_significant专指“helper自身足以解释当前厚度/长度差异”，不是说错误原点无影响。

```ini
AUDITED = YES
AFL_USES_FULL_VANILLA_ARM = YES
TACZ_USES_FULL_VANILLA_ARM = YES
AFL_CLASSIC_ARM_GEOMETRY = 4x12x4
AFL_SLIM_ARM_GEOMETRY = 3x12x4
TACZ_CLASSIC_ARM_GEOMETRY = 4x12x4
TACZ_SLIM_ARM_GEOMETRY = 3x12x4
AFL_PLAYER_ARM_FINAL_AXIS_SCALE = [0.41,0.41,0.41]
TACZ_PLAYER_ARM_FINAL_AXIS_SCALE = [1,1.5,1] (Glock17 sampled clips; inherited)
AFL_0_246_CLASSIFICATION = HISTORICAL_VISUAL_COMPENSATION
AFL_RIGHT_FOREARM_AXIS = [0.0178,-0.1041,0.9944]
TACZ_RIGHT_FOREARM_AXIS = [0.1153,-0.1081,0.9874]
AFL_RIGHT_PALM_NORMAL = [-0.0073,0.9945,0.1043] (local +Z proxy)
TACZ_RIGHT_PALM_NORMAL = [0.0453,-0.9925,-0.1139] (local +Z proxy)
AFL_READY_RIGHT_ARM_VISIBLE_FACE_WIDTH = 3px
TACZ_READY_RIGHT_ARM_VISIBLE_FACE_WIDTH = 3px
AFL_RELOAD_RIGHT_ARM_VISIBLE_FACE_WIDTH = 4px
TACZ_RELOAD_RIGHT_ARM_VISIBLE_FACE_WIDTH = 4px
AFL_VISIBLE_FOREARM_RATIO_READY = 0.8650
AFL_VISIBLE_FOREARM_RATIO_RELOAD = 0.8975
TACZ_VISIBLE_FOREARM_RATIO_READY = 0.3900
TACZ_VISIBLE_FOREARM_RATIO_RELOAD = 0.4025
AFL_READY_GUN_OCCLUSION_RATIO = 0.2369
TACZ_READY_GUN_OCCLUSION_RATIO = 0.0661
PLAYER_RENDERER_HELPER_TRANSFORM_DIFFERENCE_FOUND = YES
PLAYER_RENDERER_HELPER_DIFFERENCE_IS_VISUALLY_SIGNIFICANT = NO
TOP_1_ROOT_CAUSE = final basis scale and Glock hand-parent length scale
TOP_2_ROOT_CAUSE = reload camera depth and forearm foreshortening
TOP_3_ROOT_CAUSE = local grip occlusion and Slim face/sleeve composition
FRAMEWORK_LEVEL_FIX_RECOMMENDED = YES
PER_GUN_AUTHORING_FIX_REQUIRED = YES (reload depth/direction target)
PRODUCTION_CODE_CHANGED = NO
DEV_CODE_CHANGED = NO
BBMODEL_CHANGED = NO
GEO_CHANGED = NO
ANIMATION_CHANGED = NO
DISPLAY_CHANGED = NO
TACZ_CODE_COPIED = NO
TACZ_ASSETS_COPIED = NO
TACZ_RUNTIME_DEPENDENCY_ADDED = NO
SCREENSHOTS_CREATED = NO
PREVIEW_FILES_CREATED = NO
AUDIT_DOC_CREATED = YES
DOCS_UPDATED = YES
BUILD_RUN = NO
GRAPHICAL_CLIENT_LAUNCHED = NO
COMMIT = NO
PUSH = NO
```

[right]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/functional/RightHandRender.java
[model]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/bedrock/BedrockModel.java
[part]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/model/bedrock/BedrockPart.java
[wrapper]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java
[event]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java
[helper]: https://raw.githubusercontent.com/MCModderAnchor/TACZ/b43eb84c38e9768d8e73c8b14f0b845669704b38/src/main/java/com/tacz/guns/util/RenderHelper.java
[wiki]: https://tacwiki.mcma.club/zh/gunpack/gun/04_hand_pos.html

