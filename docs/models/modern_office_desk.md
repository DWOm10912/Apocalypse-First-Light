# Modern Office Desk — 建模资产

状态：模型、贴图与静态装饰方块接入完成；等待游戏内视觉、摆放和生存采掘验证。

| 项目 | 当前值 |
|---|---|
| 编辑源 | `src/main/blockbench/modern_office_desk.bbmodel` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/modern_office_desk.png` |
| 尺寸 X/Y/Z | 48 / 13.5 / 16 model units（3 × 0.84375 × 1 blocks） |
| 边界 | X -16–32；Y 0–13.5；Z 0–16 |
| Cube / Group | 145 / 11 |
| 贴图分辨率 | 128 × 128 |
| 风格 | 浅灰整面桌板、炭灰钢架、低频克制明暗，无随机噪声 |
| 注册 ID | `apocalypse_firstlight:modern_office_desk` |
| 方块结构 | 三格宽；`left / center / right` 联动放置与拆除，中心格负责掉落 |
| 采掘 | 镐；铁镐或更高等级；错误工具不掉落 |
| VoxelShape | 按三格分别覆盖抬高后的桌板、加长桌腿、横梁、挡板与走线槽，并随水平朝向旋转 |

根组 `modern_office_desk_root` 下分为 `tabletop_assembly`、左右 `frame_assembly`、`center_underframe`、`modesty_panel`、`cable_management`。四个桌腿另有子组。桌面完全留空，后侧设挡板和走线托槽；没有设备、抽屉或动画。

已通过 Blockbench MCP 实际预览检查，以及导出脚本的尺寸、非退化 cube、纹理绑定、UV 范围、PNG 分辨率和保存回读验证。桌板封边与顶面分开，避免外表面共面重叠。共享材质 UV 区域为有意复用。

桌面与上部框架由 12 units 提高至 13.5 units：桌板厚度不变，落地桌腿向上延长，上横梁、挡板和走线槽整体上移 1.5 units，近地横脚保持原高度。运行时资源由 `tools/export-office-props-runtime.mjs` 从编辑源生成，按 X=-16–0、0–16、16–32 切分为三格模型并保持 UV 连续。方块已加入方块注册、物品注册、创造模式方块页、双语名称与战利品表；没有加入城市生成池。游戏内最终比例与三格边界仍以实机验收为准。

生成脚本：`tools/modern-office-desk.blockbench.js`；完成修正：`tools/modern-office-desk-finish.blockbench.js`；高度迁移与校验：`tools/raise-modern-office-desk.mjs`；源资产校验：`tools/export-modern-office-desk.mjs`；运行时同步：`tools/export-office-props-runtime.mjs`。
