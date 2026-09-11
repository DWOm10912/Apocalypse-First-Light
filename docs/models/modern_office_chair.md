# Modern Office Chair — 建模资产

状态：Blockbench 模型、贴图与静态装饰方块接入完成；等待游戏内视觉与生存采掘验证。

| 项目 | 值 |
|---|---|
| 编辑源 | `src/main/blockbench/modern_office_chair.bbmodel` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/modern_office_chair.png` |
| Cube / Group | 202 / 31 |
| 贴图 | 128 × 128，低噪声炭灰材质，共享材质 UV |
| 实际宽 × 深 × 高 | 约 13.911 × 13.230 × 19.2 model units，即 0.87 × 0.83 × 1.2 blocks |
| 座面 / 扶手顶 | Y=8.7 / 10.8 |
| 办公桌匹配 | 桌板下沿 Y=11，前梁下沿 Y=9.8；扶手能低于桌板，但不能完全推进前梁下方 |
| 注册 ID | `apocalypse_firstlight:modern_office_chair` |
| 采掘 | 镐；铁镐或更高等级；错误工具不掉落 |
| VoxelShape | 简化为坐垫、靠背、左右扶手、中心柱与低矮十字底座，并随水平朝向旋转 |

`office_chair_root` 下有靠背、座面、左右扶手、气压杆和五爪底座独立总成；底座包含 `leg_01`–`leg_05`，各有 `wheel_01`–`wheel_05` 脚轮组。轮组采用双轮造型，总计五个脚轮组件。无头枕、无摆件、无动画。

座面及上部总成轴心设在 (8,7,8)，靠背子组设在 (8,8,12)，底座为 (8,2,8)，脚轮轴心在对应轮组中心附近。五爪使用 72° 径向间隔。运行时模型使用 `forge:composite`，将 0° / 72° / 144° / 216° / 288° 元素分别放入带根变换的子模型，保留编辑源中的五爪角度。

已检查 Blockbench 正面和背面实际截图；导出校验包含旋转后边界、202 个非退化 cube、五轮组、材质绑定、UV 范围、PNG 尺寸及文件回读一致性。方块已加入方块注册、物品注册、创造模式方块页、双语名称与战利品表；游戏内摆放与资源烘焙仍待实机验证。

模型仍保留完整五爪与五轮外观，但交互轮廓不再逐段包围每根爪和脚轮，以减少选中方块时的密集线框；细小底座零件允许玩家碰撞近似穿过。

工具：`tools/modern-office-chair.blockbench.js`、`tools/modern-office-chair-finish.blockbench.js`、`tools/modern-office-chair-view.blockbench.js`、`tools/export-modern-office-chair.mjs`、`tools/export-office-props-runtime.mjs`。
