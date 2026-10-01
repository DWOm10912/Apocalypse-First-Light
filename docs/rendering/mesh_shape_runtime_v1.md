# AFL Mesh Shape Runtime / Asset Contract V1

状态（2026-09-30）：**坐标契约修复已实现，修复后实机验证待用户完成**。
- `compileJava --offline` 一次 PASS。
- 第一个正式接入是 `industrial_locker`（见 [industrial_locker_v2.md](../models/industrial_locker_v2.md)）。

## 五层彼此独立

一个复杂 Mesh 方块由五样东西组成，它们互不依赖：

| 层 | 作用 | 来源 |
|---|---|---|
| Visual Mesh | 画面 | Pure Mesh + Animated / Static Mesh 渲染，**本系统不碰** |
| Physical Shape | 碰撞、阻挡（`getCollisionShape`） | profile `physical`：少量 AABB |
| Selection Shape | 准星选中、方块轮廓（`getShape`） | profile `selection`：少量 AABB，省略时等于 physical |
| Interaction Shape | AFL 世界交互"瞄的是哪个区域" | profile `interaction`：命名区域，每个区域由若干 AABB 组成 |
| Interaction Anchor | 提示框显示的位置 | profile `anchors`：命名点 |

两条原则：
- **Interaction Shape ≠ Physical Shape**：交互区域可以覆盖整块门板、整个柜口，与碰撞无关。
- **Anchor ≠ Interaction Hit Area**：只要瞄中区域就算命中，提示框再画到锚点上（例如门锁旁），玩家不需要精确瞄准锚点。

原则是 **Mesh 精细，Shape 简化**：百叶、把手、铰链、螺丝不需要一一对应的碰撞盒，也不会把三角面自动转换成盒子。

## Profile 格式

文件位置：`src/main/resources/data/<ns>/mesh_shapes/<id>.json`，从 mod jar 读取（与 `native_guns` 相同），客户端和服务端完全一致。

```json
{
  "format_version": 1,
  "units": "px",
  "cells_y": 2,
  "states": {
    "closed": {
      "physical":  [[x0, y0, z0, x1, y1, z1], ...],
      "selection": [[...], ...],
      "interaction": {"door": {"boxes": [[...]], "anchor": "door_lock"}},
      "anchors": {"door_lock": [x, y, z]}
    },
    "open": { ... }
  }
}
```

**坐标**：与 Mesh 源文件相同的模型坐标，单位 px。原点在方块底面中心，y 向上，正面朝 -Z（即 facing=north）。只写一套坐标，不为四个朝向各写一份。

### 2026-09-30 坐标审计与修复

原实现生成缓存时按 NORTH/EAST/SOUTH/WEST 编号，读取却使用 `Direction.get2DDataValue()` 的 SOUTH/WEST/NORTH/EAST 编号，导致所有朝向取到了相差 180° 的形状，连带区域和提示锚点错位。不是模型镜像、门轴或门打开角度错误。

现在 `AflMeshShapeTransform` 统一缓存索引、点和盒子的变换。已有 `HorizontalShapeUtils` 是 block 包内的 VoxelShape 专用工具；本工具遵守它相同的旋转公式，并服务于盒子和锚点，不改变旧方块工具或冻结的渲染运行时。

唯一坐标链：源 Mesh / profile 的 px 坐标 → `((X+8)/16, Y/16-cell, (Z+8)/16)` → facing → 加当前格 BlockPos → 世界射线。X 正向东、Y 正向上、Z 正向南。`Block.box` 的 0..16 是角点原点，因此源 X/Z 必须先加 8；本实现转换到 block 单位后调用 `Shapes.box`。

| Facing | 方块局部 `(x,z)` | BER Y 角度 |
|---|---|---|
| NORTH | `(x,z)` | 0° |
| EAST | `(1-z,x)` | -90° |
| SOUTH | `(1-x,1-z)` | 180° |
| WEST | `(z,1-x)` | 90° |

绕 `(0.5,0,0.5)` 旋转，不镜像。AABB 变换两个对角点后重新规范化 min/max。下格截取源 Y `[0,16]`；上格截取 `[16,32]` 并减 16 px，加上上格世界位置后与源高度吻合。交互区域、锚点及交互遮挡用完整资产，不截断在中缝；按相同 cell 偏移使上下格返回相同世界结果。

本轮只做源代码和坐标静态核对；最后单次 `compileJava --offline` 的结果以任务交付报告为准。未启动客户端或执行游戏内验证。

**states**：有限个离散状态，由方块把 BlockState 映射到状态名。
- 静态道具只写一个 `"default"`。
- 查询未知状态时，使用第一个状态。

**cells_y**：多格高的资产使用。
- physical / selection 按每格的高度切开，分别给下半、上半。
- 水平方向伸出本格的部分（例如打开的门）保留。
- interaction 区域和 anchor 不切开，按世界坐标整体判断，上下两格得到的结果相同。

**interaction 区域**：
- 命名是 `名字 → {boxes, anchor}`，`anchor` 必须是本状态 `anchors` 里已有的名字。
- 没有 anchor 时，提示框画在命中点上。

**校验**：
- 每组最多 64 个盒子，最多 16 个区域。
- 坐标绝对值不超过 64 px，min 必须小于 max。
- 有未知字段就拒绝整个 profile。
- 缺失或无效时只记录一次错误，形状退化为完整方块，没有交互区域，不会崩溃。

## 运行时

代码在 `src/main/java/com/antaurora/apofirstlight/meshshape/`：

| 类 | 作用 |
|---|---|
| `AflMeshShapes` | 按 id 加载并永久缓存。形状在 Minecraft 建立 block state 缓存时就要用到，所以不能随资源包或数据包重载 |
| `AflMeshShapeProfile` | 加载时把 `状态 × 四个朝向 × 格` 全部预先算成 VoxelShape、方块局部坐标的区域盒子和锚点。之后查询只是查数组，不解析 JSON，不创建 VoxelShape，也不跟随动画逐帧变化 |
| `AflMeshShapeTransform` | NORTH 顺序的显式索引；源 px 到当前格局部坐标及 facing 的统一变换，盒子和锚点共用 |
| `AflMeshShapeBlock` | 由 **Block** 实现，不需要 BlockEntity：`meshShapeProfile()`，可选 `meshShapeState / meshShapeFacing / meshShapeCell`。提供 `meshPhysicalShape`、`meshSelectionShape`，以及 `meshInteraction(state, pos, player)` |

**Facing**：与 Animated Block Mesh Runtime 的朝向约定相同（NORTH 0、EAST -90、SOUTH 180、WEST 90，绕方块中心旋转），公式与它的 render bounds 一致，所以形状和画面始终对齐。默认读取 `HORIZONTAL_FACING`。

**交互判定**（`meshInteraction`）：
1. 用玩家自己的视线（眼睛 + 视线方向 × 方块触及距离）检测区域盒子。
2. 只接受位于完整资产 selection 表面之上或之前（误差 0.03 格）的区域。遮挡使用简化 selection，不是三角面；完整形状避免跨上下格的斜视线受半格裁切影响。
3. 客户端提示和服务端 `use()` 使用同一判定。

**伸出本格的选中**：
- 问题：原版只在视线穿过方块所在格时检测它。
- 处理：`AflMeshShapePicking`（通过 `mixin/client/AflMeshShapePickMixin`，注入 `GameRenderer.pick` 的 TAIL）额外检测视线经过格子的水平邻域，包含斜对角。半径来自已缓存 profile 的 selection 越界 bounds 向上取整；储物柜为 1，即最多 3×3 邻域，去重并排除原版已经遍历的格子。不读取未加载 chunk。只对 selection 越界的 Mesh Shape 方块做实际 shape.clip，不硬编码 locker。只有实际交点严格近于现有方块/实体交点时才替换；等距时保留原版结果。profile 在方块状态 shape 缓存初始化时加载。
- 服务端限制：原版会拒绝命中点离方块中心超过 1 格的使用请求，所以这类命中点会被夹回本格范围内；区域仍按视线判定，不受影响。

**提示框**：复用 `WorldInteractionHint` 和 `AttachmentHintStyle`。区域的锚点通过本帧的世界 view / projection 矩阵（`RenderLevelStageEvent` AFTER_SKY，包含视角晃动）投影到屏幕，提示框画在锚点的右上方。锚点在镜头后方时退回到准星位置。自动售货机仍以准星为锚点，行为不变。

## 接入步骤

**静态道具**：
1. 生成器输出 `mesh_shapes/<id>.json`，只写一个 `default` 状态。
2. Block 实现 `AflMeshShapeBlock`。
3. `getShape` 返回 `meshSelectionShape(state)`，`getCollisionShape` 返回 `meshPhysicalShape(state)`。

**动态资产**：
1. 为每个离散状态写一份 shape。
2. 覆盖 `meshShapeState`，多格资产再覆盖 `meshShapeCell`。
3. 视觉动画可以连续变化，形状只在状态之间切换，精度可以不同。例如门打开时只保留一个简化的门板盒子。

**交互**：
1. 方块在 `use()` 里调用 `meshInteraction`，根据"区域 + 自身状态"决定动作。
2. 客户端提示用同一个结果，在 `WorldInteractionHint` 里加一个分支（参考储物柜）。

**生成**：`.mjs` 生成器直接从 Mesh 的常量推导出这些盒子和锚点，写入 JSON。参考 `tools/build-industrial-locker-v2.mjs` 的 `SHAPES`：门打开时的盒子由门部件的全部顶点转到最终角度后取 AABB 得到。

## V1 不做

- 不做三角面精确碰撞。
- 不做逐帧动画碰撞。
- 不把 Mesh 自动拆成大量小盒子。
- 不提供编辑器。
- 不支持 UP/DOWN 朝向。
- 不支持资源包或数据包重载形状。
- 旧家具没有迁移。

## 2026-09-30 追加：通用交互提示接口与第二个接入

- 新增 `meshshape/AflMeshInteractionBlock`（继承 `AflMeshShapeBlock`）：方块实现 `interactionHintKey(level, state, pos, region)`，根据瞄中的区域和自身状态返回提示文字键。`WorldInteractionHint` 对所有实现者用同一分支显示提示，并画在该区域锚点的屏幕投影旁；方块自己的 `use()` 必须做同样的判定。
- `IndustrialLockerBlock` 改为实现这个接口，行为不变；`LeadChestBlock` 是第二个接入（`lid` / `interior` 区域，见 [lead_chest_v3.md](../models/lead_chest_v3.md)）；`IndustrialElectricalBoxBlock` 是第三个（`door` / `interior` 区域，见 [industrial_electrical_box_v2.md](../models/industrial_electrical_box_v2.md)）；`CashRegisterBlock` 是第四个（`drawer` / `interior` 区域，打开的钱箱水平伸出本格 0.41 格且没有碰撞，见 [cash_register_v2.md](../models/cash_register_v2.md)）。
- 铅箱开盖后竖起的盖子高出本格（y 最高约 22 px）。碰撞可以正常超出本格；但补充选中检测只查水平相邻格，所以盖子高出本格的那一段选不中，本格高度内的部分仍可以选中并用来关盖。
