# 办公电脑工位、键盘与鼠标

状态：静态装饰方块接入完成；构建校验通过，等待游戏内视觉、四向摆放和生存掉落验证。

| 项目 | 办公电脑工位 | 办公键盘 | 办公鼠标 |
|---|---|---|---|
| Registry ID | `apocalypse_firstlight:office_computer_station` | `apocalypse_firstlight:office_keyboard` | `apocalypse_firstlight:office_mouse` |
| 显示名 | 办公电脑 / Office Computer | 办公键盘 / Office Keyboard | 办公鼠标 / Office Mouse |
| 源模型 | 三个独立源的运行时组合 | `office_keyboard.bbmodel` | `office_mouse.bbmodel` |
| 运行时形式 | 显示器、键盘和鼠标合并为一个静态方块模型 | 独立静态方块模型 | 独立静态方块模型 |
| 方块状态 | `facing`、`lowered` | `facing`、`lowered` | `facing`、`lowered` |
| VoxelShape | 鼠标、键盘、显示器底座、支架与屏幕五个近似盒 | 单个低矮盒 | 单个低矮盒 |
| 物品栏 GUI | 组合模型保持现有展示 | `translation=[0,3.5,0]`，槽位居中 | `translation=[0,3.5,0]`，槽位居中 |
| 采掘 | 无工具要求；空手掉落显示器、键盘、鼠标各一个 | 无工具要求；空手掉落自身 | 无工具要求；空手掉落自身 |
| 功能 | 无 BlockEntity、GUI、电力、红石或动画 | 同左 | 同左 |

显示器、键盘和鼠标的可编辑源保持独立。`tools/export-office-props-runtime.mjs` 仅在运行时组合三套几何和各自贴图：显示器位于方块后部，键盘位于前部；鼠标位于键盘右侧。键盘仅做左右校正，空格键仍朝使用者，数字区位于使用者右侧；鼠标前端朝显示器。整个组合只占一个 BlockPos，建筑生成只需放置一个 `office_computer_station` 方块状态。

`office_keyboard` 和 `office_mouse` 仍注册为可单独摆放的回收组件；其独立模型位于各自方块格中央。NORTH、SOUTH、EAST、WEST 通过标准 `facing` 模型旋转与同一套 VoxelShape 旋转产生，不允许在世界生成中镜像源模型。

三个新装饰方块放在高度 13.5 model units 的现代办公桌上时，检测下方桌子并设置 `lowered=true`，模型和 VoxelShape 同步下移 2.5 units。该状态仅校正桌面高度，不会自动生成、绑定或移除任何桌面物件。

三个新装饰方块都没有加入 `mineable/*` 或 `needs_*_tool` 标签，也未启用 `requiresCorrectToolForDrops()`。正常拆除组合工位时不掉落组合方块自身，而是掉落 `modern_lcd_monitor`、`office_keyboard`、`office_mouse` 各一个。原有 `modern_lcd_monitor` 注册保留，避免破坏已有存档。
