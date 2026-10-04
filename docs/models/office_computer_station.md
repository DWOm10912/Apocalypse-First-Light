# 办公电脑工位、键盘与鼠标

状态：**V2（2026-10-03）已接入**。三件都换成 Pure Mesh + LabPBR 的静态 OBJ 方块模型，选中轮廓和碰撞同时改了。生成脚本 `--check` 通过，`compileJava --offline` 通过；**没有实机验证**。

以下都没变：
- 注册 ID、显示名、方块状态（`facing`、`lowered`）；
- 桌上下沉 2.5 px 的逻辑；
- 掉落和挖掘规则。

| 项目 | 办公电脑工位 | 办公键盘 | 办公鼠标 |
|---|---|---|---|
| Registry ID | `apocalypse_firstlight:office_computer_station` | `apocalypse_firstlight:office_keyboard` | `apocalypse_firstlight:office_mouse` |
| 显示名 | 办公电脑 / Office Computer | 办公键盘 / Office Keyboard | 办公鼠标 / Office Mouse |
| 网格 | 显示器、键盘、鼠标三件的同一套网格拼在一起 | 自己的网格 | 自己的网格 |
| 运行时模型 | `office_computer_station{,_lowered}.{json,obj,mtl}`，MTL 里三种材质，分别用三件的贴图 | `office_keyboard{,_lowered}.{json,obj,mtl}` | `office_mouse{,_lowered}.{json,obj,mtl}` |
| 贴图 | 用显示器、键盘、鼠标三张 | `textures/block/office_keyboard{,_s,_n}.png`，512 | `textures/block/office_mouse{,_s,_n}.png`，256 |
| 方块状态 | `facing`、`lowered` | `facing`、`lowered` | `facing`、`lowered` |
| 选中轮廓 | 一个盒子 `0,0,4.3 → 16,12.75,13.6` | 一个盒子 `1.8,0,6.2 → 14.2,0.95,9.75` | 一个盒子 `6.95,0,6.55 → 9.05,1,9.95` |
| 碰撞 | 前面键盘 + 鼠标一个矮盒 `0.25,0,4.3 → 15.4,0.95,8`，后面显示器一个盒子 `0,0,8.8 → 16,12.75,13.6` | 同轮廓 | 同轮廓 |
| 物品栏 GUI | rotation `[25,135,0]`、translation `[0.111,1.096,0]`、scale 0.72 | `[30,135,0]`、`[0.025,6.274,0]`、0.95 | `[25,152,0]`、`[-0.101,12.47,0]`、1.8 |
| 采掘 | 无工具要求；空手掉落显示器、键盘、鼠标各一个 | 无工具要求；空手掉落自身 | 无工具要求；空手掉落自身 |
| 功能 | 无 BlockEntity、GUI、电力、红石或动画 | 同左 | 同左 |

形状数值都是 facing=north、没下沉时的值，`lowered=true` 时整体下移 2.5 px，随朝向旋转。代码在 `block/OfficeDesktopDecorationBlock.java`，工位的碰撞由 `computerStationCollision()` 单独给出。旧形状由多个盒子拼成，工位的轮廓呈台阶状；2026-10-03 起轮廓都只有一个盒子。

## 网格（`tools/build-office-props-v2.mjs`）

- **键盘**（`keyboard`）：
  - 全尺寸布局：主键区、方向键区、数字键盘，约 104 个独立的收窄键帽（1 U = 0.5 px）；
  - 键帽放在一个低楔形底座上，打字斜面约 5.8°；
  - 不印字；
  - 底座 `[31,32,35]`，键帽 `[47,49,53]`；
  - 尺寸 12.35 × 0.95 × 3.525 px；
  - 空格键朝 -Z，数字键盘在 -X（面朝 +Z 的人的右手边）；
  - 1068 个三角面，约 31.5 texel/px。
- **鼠标**（`mouse`）：
  - 对称放样外壳，后部隆起，平底；
  - 左右键分割线是一条窄的深色条（几何，不是贴图）；
  - 橡胶滚轮；
  - 尺寸 2.1 × 0.98 × 3.4 px，前端朝 +Z（朝显示器）；
  - 708 个三角面，约 39.75 texel/px；
  - 外壳法线做了平滑。
- **显示器**：见 [modern_lcd_monitor.md](modern_lcd_monitor.md)。

## 三合一工位

为了建筑生成方便而保留：放一个方块状态就是一整套电脑。

- 用的是和单独的显示器、键盘、鼠标同一套网格，外观完全一致。
- 位置和 V1 相同（方块中心为原点，单位 px）：
  - 显示器向后 3.2；
  - 键盘 x +1.2、z -1.9，往使用者左手边偏；
  - 鼠标 x -6.7、z -2.0，在使用者右手边。
- 整个组合只占一个 BlockPos。四个朝向靠标准模型旋转，不镜像。
- 正常拆除时不掉落工位本身，而是掉落 `modern_lcd_monitor`、`office_keyboard`、`office_mouse` 各一个。三件都没加入 `mineable/*` 或 `needs_*_tool` 标签，也没启用 `requiresCorrectToolForDrops()`。这是现有规则，这次没改。

## 已知的现有不一致（未改）

单独的显示器需要铁镐才掉落，但拆电脑工位空手就能掉落一个显示器。

## 旧资源（已过时）

- **鼠标物品模型**：原来手写的 cube 几何物品模型（`models/item/office_mouse.json`）已被生成的物品模型替换，父模型是新的方块模型，各场合的变换数值保留。
- **V1 cube 源**：`office_keyboard.bbmodel`、`office_mouse.bbmodel` 保留作参考。
- **不要再运行** `tools/office-keyboard-delivery.mjs`、`tools/office-mouse-delivery.mjs`：它们会把 V1 贴图写回运行时贴图。
- **运行时导出脚本**：`tools/export-office-props-runtime.mjs` 已删除，方块和物品模型现在由 `tools/build-office-props-v2.mjs` 生成。方块状态文件没变，不再由脚本生成。

## 需要实机验证

- 三件和工位在桌上 / 地上的高度和朝向；
- 新的轮廓和碰撞；
- 物品栏图标居中；
- 键帽在远处是否闪烁；
- 生存掉落。
