# 手枪消音器 / Pistol Suppressor — Model V1

Asset ID：`pistol_suppressor_01`。独立、可复用的视觉附件；首个尺寸校准对象为 `p9_01`。现已注册物品并接入 MUZZLE 安装，当前运行行为与验证边界见 [可装备 V1](pistol_suppressor_01_v1.md)。本文件仅说明未改动的源几何。

## 资源与安装坐标

- 可编辑源：`src/main/blockbench/pistol_suppressor_01.bbmodel`。
- GeckoLib runtime：`assets/apocalypse_firstlight/geo/pistol_suppressor_01.geo.json`，identifier `geometry.pistol_suppressor_01`。
- 独立贴图：`assets/apocalypse_firstlight/textures/item/pistol_suppressor_01.png`，64×128；沿用已有 `pistol_red_dot` 的 geo / textures/item 组织方式。
- `pistol_suppressor_root` 位于后端连接中心 `(0,0,0)`，无初始旋转；视觉前方为 local `-Z`。
- 子骨骼：`mount`、`body`、`front_cap`、`muzzle_exit_anchor`；出口 anchor 为 `(0,0,-9.1)`。
- P9 既有 `muzzle_anchor` 无需修改：Blockbench 源坐标 `(-2.98,10.35,-4.72)`，导出 Geo 坐标 `(2.98,10.35,-4.72)`。未来按完整锚点变换挂载独立附件，不重复叠加世界/枪根变换。

## 风格与比例

八边截面，57 个 cube，无高模网格；长度 9.1，为 P9 套筒主体长度14的65%。主体对边直径1.5，分段环最大1.58，低于套筒主体约1.6的高度。后座较窄，两道分段环，前盖中央真实凹孔约1.18深，不是表面黑点。

深灰枪黑、少量亮度分层，无品牌、文字、随机噪点或 PBR。主要面 UV 按约2 texels/模型单位，与 P9 主面 UV 一致；极小面最低0.25 texel。筒壁采用有厚度的旋转 cube；内部接缝覆盖，端板微小错层避免共面重叠闪烁。

## 预览与验证

- Blockbench 已检查独立正面、侧面、斜前、斜后；P9 裸枪与挂载 mock；第一人称瞄线/偏置构图；按现有维护垫12.8×7及枪比例0.55建立的静态摆放 mock。
- 预览位于 `build/pistol-suppressor-previews/`：`standalone_{front,side,front_oblique,rear_oblique}.png`、`p9_{bare,mounted}.png`、`first_person_mock.png`、`first_person_hip_mock.png`、`maintenance_static_mock.png`。
- Mock 场景使用自由格式以保留 P9、附件、垫子的各自贴图绑定；未保存到 P9 源或 runtime。仅基础构图验收，不是实际游戏 ADS / 手持 / 维护台 renderer 联调通过。
- 源与 runtime 各57 cubes，骨骼/UV边界检查通过；P9 源、Geo、贴图、动画 SHA-256 与任务开始一致。构建日志 `build/pistol-suppressor-model-build.log`。

MUZZLE 挂载与维护台现复用该独立 root；兼容规则、V 安装事务、声音、噪声与枪口视觉出口已接入。手臂安装动画和维护台附件热点尚未实现。`muzzle_exit_anchor` 只改变视觉定位，绝不改变 camera/look vector 弹道方向。
