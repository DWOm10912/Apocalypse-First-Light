# 城市黑色金属垃圾桶（建模资产）

盖沿对齐修正：原补缝使用固定坐标负向扩展，导致不同象限偏心。现由 `tools/city-trash-can-align-lid.blockbench.js` 统一为半径 5.71、22.5° 等间距的 16 段，局部尺寸 2.48 × 1；保持 204 cubes。已读取俯视截图检查对称轮廓，未做游戏内验证。`tools/export-city-trash-can.mjs --update-rim` 支持校验非盖沿元素不变、备份后更新源模型及贴图；无参数仍为不覆盖的首次导出。

状态：已作为纯静态装饰方块接入游戏，Registry ID 为 `apocalypse_firstlight:metal_trash_can`。不含 BlockEntity、GUI、容器、动画或功能性 BlockState。

| 项目 | 当前值 |
| --- | --- |
| 可编辑源 | `src/main/blockbench/city_trash_can.bbmodel` |
| 贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/city_trash_can.png` |
| 运行时模型 | `src/main/resources/assets/apocalypse_firstlight/models/block/metal_trash_can.json` |
| 方块实现 | `src/main/java/com/antaurora/apofirstlight/block/MetalTrashCanBlock.java` |
| 复杂度 | 204 cubes，19 groups |
| 贴图尺寸 | 128 × 128，低噪点炭黑喷涂金属 |
| 高度 | 顶部 Y=17.18 模型单位，约 1.07 方块；主体位于单格占地内 |
| 桶盖轴心 | `lid_assembly`: [8, 14.8, 8] |
| 盖提手轴心 | `lid_handle`: [8, 15.6, 8] |
| 左/右提手轴心 | [1.7, 12.1, 8] / [14.3, 12.1, 8] |

根组 `city_trash_can_root` 下分为 `trash_can_body`、`lid_assembly`、左右 `handle_assembly` 和 `optional_inner_bucket_or_misc`。桶盖、盖提手、侧把手环及固定座分别保留子组；源模型分组是后续动画基础，不代表 Java 方块 JSON 支持动画。

静态方块使用金属声音、硬度 3.0、爆炸抗性 5.0；碰撞由桶身、盖沿和顶盖提手的少量盒子近似，选中框为无内部连接线的单一外包络。破坏只通过标准方块 loot table 掉落自身 1 个。它位于 AFL 方块创造页，必须使用铁级或更高等级镐正确采集；石镐及更低等级工具不掉落。

桶身中空，16 段壳体；32 条连续底板替代粗分段内接底板，避免底座漏空。盖沿加厚并包住盖面分段边缘。未使用随机脏点、重锈蚀或文字标志。

验证：通过 MCP 检查闭盖及临时隐藏盖子的实际预览，恢复闭盖后保存；离线导出检查 204 cubes、贴图尺寸、每面绑定及合法 Java 旋转。`compileJava` 与 `processResources` 已通过。创造栏、放置、物品显示、碰撞和 Survival 掉落仍需 Minecraft 实机验证。

构建历史脚本：`tools/city-trash-can.blockbench.js` → `tools/city-trash-can-polish.blockbench.js` → `tools/city-trash-can-seal.blockbench.js` → `tools/city-trash-can-align-lid.blockbench.js`。这些脚本有现场状态保护，不应用于已有完成模型。`tools/export-city-trash-can-runtime.mjs` 从已保存 `.bbmodel` 同步并校验静态运行时模型；`tools/verify-metal-trash-can.mjs` 检查接入契约。
