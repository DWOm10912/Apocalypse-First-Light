# 商用卧式冷冻冰柜模型 V1

已完成可编辑模型、128×128 RGBA 贴图，以及四个独立滑盖 Position 动画。Runtime V1 已接入方块、动画、VoxelShape 与注册；商品库存和 worldgen 尚未实现。最新源模型已居中，并同步重新导出运行时 Geo；修正后仍需游戏内复测模型与选框重合。

## 文件

- 源文件：`src/main/blockbench/afl_chest_freezer.bbmodel`
- 贴图：`src/main/blockbench/textures/afl_chest_freezer.png`
- 预览：`src/main/blockbench/previews/afl_chest_freezer_{angle,front,side,top,left_open,right_open,interior}.png`

## 实测尺寸与结构

以 16 units = 1 block。最新保存的源模型包围盒为 X [-16,16]、Y [0,15.8]、Z [-8,8]，原点在占地中心，适配 2×1×1。正面为 -Z，控制面板位于该面。148 cubes、27 groups、1 张贴图；本次只改坐标与碰撞形状，没有改贴图。复杂度低于建议预算，保留稀疏篮框而不堆网格。

内胆侧壁间 X [-14,14]、Z [-6,6]，净宽 28、净深 12。连续内胆底面 Y=3.6；底部圆角收边到 Y=3.85。下轨盖框底 Y=14.3，因此保守净高 10.7；玻璃下表面 Y=14.38。篮框为独立可调整组，包含外围框、角杆、每篮一根底部横杆及对应立杆；物品摆放应避开实际杆件，不能将整个内胆包围盒视为完全无障碍空间。

根组 `chest_freezer_root` 下含 cabinet_body、bottom_base、top_rim、interior_shell、basket_frame、left_lid、right_lid、control_panel、vent_detail、interaction_layout。两片 lid 各包含 frame、glass、handle 子组，闭合状态为源文件默认姿态。

## 滑动接口

- left_lid：闭合源坐标 X [-14.25,0.1]，沿 +X 平移 14.15 units 至右半。
- right_lid：闭合源坐标 X [-0.1,14.25]，沿 -X 平移 14.15 units 至左半。
- 两轨盖框底 Y=14.3 / 14.9，高差 0.6；框厚 0.3，握边最大高度 0.5。叠放时框间垂直间隙 0.3，包含握边的最小间隙 0.1。
- 每次单片滑动后可露出约 13.9×12 units 的内胆顶部开口；双盖不能同时让整个顶部无覆盖。未来逻辑需要处理移动过程及实际开口。
- 对左右各 101 个平移采样位置检查两盖所有 cube 对，未发现体积相交。此为建模几何检查，并非运行时动画或交互验证。
- Blockbench 玻璃 cube 使用持久化 `render_order=in_front`，图集玻璃 alpha 约 16%，边缘高光约 30%。未来 Renderer 应单独处理不透明壳体与透明玻璃绘制顺序。

interaction_layout 下 slot_ref_01..08 是空参考组，中心化后的 X=-10.8/-3.6/3.6/10.8，Z=-3/3，Y=3.85。它们不代表已确定的库存数量，实际物品大小与射线选择需后续实现。

## 验收边界

### 独立滑盖动画 V1

源文件包含且仅包含 `left_lid_open`、`left_lid_close`、`right_lid_open`、`right_lid_close`，各 0.35 秒。每个动画仅给同名 lid 写 15 个 Position 关键帧，间隔 0.025 秒。采用 smoothstep 采样后线性插值，缓起缓停、单调、无过冲。无 Rotation、Scale、Camera 或其他骨骼关键帧。动画设置 hold，结束保持终点。

实际保存的 X 位移：left open 0→+14.15，left close +14.15→0；right open 0→−14.15，right close −14.15→0。MCP 创建接口会反转传入 X 值，已修正并通过 Blockbench 实际 mesh 位移验证方向。两侧的起点、中点及终点均检查，未驱动侧始终为零，两次关闭终点均精确为零。源几何、group origin、内部和贴图与动画前快照一致，保留原 0.6 units 双轨高差及 0.1 最小握边间隙。

稳定状态仅 CLOSED、LEFT_OPEN、RIGHT_OPEN；跨侧切换须先关闭当前侧，再打开另一侧。建模阶段仅准备动画；下文的 Runtime V1 已实现状态机。动画预览位于 `src/main/blockbench/previews/afl_chest_freezer_anim_{left_lid_open,left_lid_close,right_lid_open,right_lid_close}.png`。已在 Blockbench 检查四个动画终态及中间位移；游戏画面验证仍待实机。

已检查斜角、正面、侧面、顶视、双盖分别移开及隐藏双盖后的深腔预览。源文件保存为完整可见、双盖闭合。PNG 与嵌入贴图同时交付，无品牌、文字、随机噪点或食品占位 cube。源模型预览不等于游戏内视觉验收。

## Chest Freezer Runtime V1

- Registry ID：`apocalypse_firstlight:chest_freezer`；2×1×1；中文 `冷冻冰柜`、英文 `Chest Freezer`。
- `LEFT` 是视觉左半且为唯一 Master/BlockEntity/Renderer；`RIGHT` 是视觉右半 Slave。源模型正面 -Z；NORTH 朝向时 Master 在东侧、Slave 在西侧。原始 Bedrock Geo 的 X 在 GeckoLib 烘焙时镜像，因此 Geo X=-16..0 渲染在 Master 一侧、X=0..16 渲染在 Slave 一侧。`ChestFreezerBlock.localHit` 仍用 Master 为 0..16 的逻辑坐标，形状使用相同世界朝向映射。
- 运行时 GeckoLib 会镜像 Bedrock Geo 的 X：源模型 `right_lid` 骨骼落在视觉左半 / Master，`left_lid` 骨骼落在视觉右半 / Slave。`LEFT_OPEN` 现在驱动 `right_lid` 的开关动画与稳定姿态，`RIGHT_OPEN` 驱动 `left_lid`；点击判定与 VoxelShape 不互换。这样打开哪半，哪半的渲染盖板和碰撞都同时移开，避免人物穿进视觉上仍关闭的盖板。源 bbmodel 与贴图未改。
- 稳定状态保存在两格的 `lid` BlockState：`closed`、`left_open`、`right_open`。不存在 BOTH_OPEN。七 tick（0.35s）后提交状态，动画中维持上一稳定状态的碰撞与选取形状；重复点击由 Master BE 的 pending transition 拒绝。跨侧切换 V1 需先点击叠放盖关闭，再点另一半打开，无自动串行动画。
- 闭合 Shape 包含底、四壁、上沿、中轨及两片薄盖；盖框/握边选取范围依据最新源模型调整为各半 X [0,14.25] / [1.75,16]、Z [1.55,14.45]，闭合 Y [14.3,14.8] / [14.9,15.4]。左开时左半顶开口不被选取/碰撞覆盖，右半为 Y [14.3,15.4] 的叠放盖；右开相反。形状以 NORTH 为母版，旋转到四个 FACING。打开的一半底部仍可被准星命中，但本轮无商品交互。
- Runtime Geo：`src/main/resources/assets/apocalypse_firstlight/geo/chest_freezer.geo.json`，由最新居中 bbmodel 的 Bedrock 导出生成，Geo 包围盒 X [-16,16]、Z [-8,8]，不再额外平移 Geo；旧版 Z [0,16] 与形状有半格偏差。`ChestFreezerRenderer` 在 GeckoLib 默认的方块中心位移后只沿 X 平移 -0.5 格，使 Master 拥有正 X 半边。动画：`src/main/resources/assets/apocalypse_firstlight/animations/chest_freezer.animation.json`，含四个原动画及四个稳定 Pose。Runtime 贴图：`src/main/resources/assets/apocalypse_firstlight/textures/entity/chest_freezer.png`。不透明框架与内胆使用 cutout；`*_lid_glass` 骨骼使用 translucent。渲染只由 Master BE 执行，AABB 覆盖整台并留余量。
- 铁镐及以上：`minecraft:mineable/pickaxe`、`minecraft:needs_iron_tool`、`requiresCorrectToolForDrops()`。任一半破坏会清除另一半，正常生存掉落应为一件；创造模式不掉落。爆炸受 loot table 的 survives_explosion 条件控制。
- Display Storage V1、8 槽 ItemStack、商品拿放、GUI、电池、能源、制冷、worldgen：**NOT IMPLEMENTED**。下一阶段：`Chest Freezer Display Storage V1`。
- 居中 Geo / VoxelShape 和本轮左右动画骨骼绑定调整后，`processResources compileJava build` 与隔离 GameTest 均通过。GameTest 验证四朝向、稳定状态、开口 Collision/Selection、七 tick 后提交、工具与生存掉落、两半联动、爆炸和放置失败。修复结果仍需实机验收：四朝向左右盖板与开口碰撞一致、滑盖动画、玻璃排序、开口射线、F3+B、真实手感、退出重进恢复与 Slave 侧视角裁剪。构建及无界面 GameTest 不替代画面验收。

```text
CHEST_FREEZER_MODEL = COMPLETE
MODEL_SIZE_X = 32
MODEL_SIZE_Y = 15.8
MODEL_SIZE_Z = 16
MODEL_FRONT = -Z
TOTAL_CUBES = 148
INTERIOR_CLEAR_WIDTH = 28
INTERIOR_CLEAR_DEPTH = 12
INTERIOR_CLEAR_HEIGHT = 10.7 (liner envelope; avoid sparse basket rods)
INTERIOR_FLOOR_Y = 3.6
LID_TRACK_Y = 14.3 / 14.9
LEFT_LID_GROUP = left_lid
RIGHT_LID_GROUP = right_lid
LEFT_LID_INDEPENDENT = YES
RIGHT_LID_INDEPENDENT = YES
SLIDING_OVERLAP_CLEARANCE = 0.1 minimum including grip
BOTTOM_SPACE_READY_FOR_3D_ITEMS = YES
INTERACTION_LAYOUT_RESERVED = YES
TEXTURE_SIZE = 128x128
LOW_NOISE_STYLE = YES
JAVA_MODIFIED = YES (Runtime V1)
READY_FOR_LID_ANIMATION = YES
READY_FOR_RUNTIME_INTERACTION = YES (model structure only; implementation pending)
```
