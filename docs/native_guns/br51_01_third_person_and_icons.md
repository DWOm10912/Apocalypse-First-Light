# BR51-01 战斗步枪 第三人称与图标（2026-09-08）

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## 第三人称适配

保留已有主手CROSSBOW_HOLD双臂姿势；此前Item Display额外的-90度pitch使长枪相对双臂竖直。
第三人称两手Display由R[-90,0,0] T[0,-4.24,1.82] S[.4,.4,.4]
改为R[0,0,0] T[0,-1.82,-4.24] S[.4,.4,.4]。
位置按源thirdperson_hand握持点[0,4.55,10.6]重新居中。不改玩家几何，不新增专用玩家姿势枚举。
这是静态方向适配；最终肩部接触、支撑手和运动/蹲伏仍待实机，不能宣称完整肩射姿势通过。
第一人称T[3.8,-7.2,-11.5] R[0,4,0] S[.45,.45,.45]逐对象检查不变。
原动画、手臂anchor、枪模及贴图、战斗逻辑不变。

## 用户图像

- E:/Download/br51_01.png → textures/item/br51_01_inventory.png（1165×574，原文件复制）
- E:/Download/br51_01_HUD.png → textures/gui/gun/br51_01_hud.png（1278×249，原文件复制）
- textures/item/br51_01.png仍为原枪模UV图，没有被图标覆盖。
- models/item/br51_01.json使用forge:separate_transforms；GUI使用forge:item_layers二维图，
  GUI scale为[.95,.95*574/1165,1]，保留原宽高比并留边。
- 其余场景通过br51_01_in_hand.json保留3D模型。源bbmodel只同步3D Display，不把包装JSON当作源几何。
- NativeGunDefinition增加每枪hudWidth/hudHeight。手枪仍36×22；BR51_01为60×12 GUI单位，
  近似原始5.13:1比例；底对齐原22高图标区域，不扩大原布局、不改弹量文字。
- 完整迁移末尾运行tools/apply-br51_01-world-icons.mjs；HIP/校准脚本识别分离模型，避免覆盖包装文件。

## 验证

构建日志build/br51_01-world-icons-build.log；源和runtime四个手持Display一致，第一人称未变。
compileJava/processResources/build离线通过（39秒），git diff --check通过。本轮未启动客户端。
视觉项均NOT TESTED：F5正面/背面/侧面、行走/蹲伏左右手接触、背包/快捷栏图标、HUD与空弹颜色。
测试时也检查P9-01 Service Pistol保持原图标尺寸。未修改其资源或姿势。

DOCUMENTATION: 此文件及br51_01_native_combat_completion.md同步当前状态。
