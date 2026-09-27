# Native Gun Inventory Presentation V1

状态（2026-09-27）：五把基准枪械的资源与生成器静态检查通过；Creative Tab 实机观感仍待用户验收。此规范只作用于背包/创造栏 GUI 图标，不改变手持、掉落、HUD、维护台、武器模型或玩法。

## 当前显示链

`models/item/{id}.json` 使用 `forge:separate_transforms`：`base` 仍指向既有 `{id}_in_hand` 3D 模型；`perspectives.gui` 使用 `minecraft:item/generated` + `forge:item_layers` 读取 `textures/item/{id}_inventory.png`。因此 GUI 实际显示预渲染 PNG，不使用枪械 Runtime 3D 绘制。

V1 前五张原图各自紧裁边界，画布尺寸分别为 P9 187×142、Blackridge 268×195、BR51 1165×574、HR55 1028×586、Silverwood 1225×606。Blackridge、BR51、HR55、Silverwood 的 GUI 子模型另有各自的非等比 `display.scale`，使图标占格受原图宽高和手动补偿共同影响。BR51 的 1165×574 也不满足 power-of-two。原 PNG 来源分散，没有统一生成器。HUD 使用 `NativeGunHudLayout.silhouette` 按图像宽高在独立区域中居中适配；本轮仅沿用该 `min(width ratio, height ratio)` 数学思路，没有更改 HUD 代码或尺寸。

## V1 生成流程

1. 在 Blockbench 打开已保存的正式 `.bbmodel`，运行 `tools/capture-native-gun-inventory-source.blockbench.js`。脚本使用共同的右侧三分之四正交视角，在实际渲染画面上测量 alpha 投影边界并自动居中与缩放；参考手臂方块只在截图期间于预览对象中隐藏，完成后恢复。用户视角在 `finally` 中恢复，模型不保存、不改几何。捕图源位于 `src/main/blockbench/inventory_icons/{id}_inventory.png`，1024×1024 透明画布。未来枪械可沿用此脚本；项目文件名与正式 ID 不同的枪可用 `globalThis.AFL_INVENTORY_ID` 指定 ID。
2. 运行 `tools/normalize-native-gun-inventory-icons.py`（需要 Pillow）。它从已渲染的 alpha 投影边界取可见区域，使用 `scale = min(targetWidth / projectedWidth, targetHeight / projectedHeight)`，再按可见中心放入固定 256×256 RGBA 画布。256 是 16×16 物品格和当前原图质量所需的 power-of-two 尺寸；没有先生成低分辨率截图再放大。`--check` 只读比较输出像素、检查物品模型路由和是否残留每枪 GUI 缩放。
3. GUI 子模型均不设置 per-weapon `display.scale`，故 256×256 PNG 本身决定实际格内占比；`*_in_hand.json` 完全不变。

| Class | 枪械 | 目标最长轴占格 |
| --- | --- | ---: |
| PISTOL | P9-01 | 82% |
| LARGE_PISTOL | Blackridge .50 | 88%（P9 的 1.073×） |
| LONG_GUN | BR51-01、HR55 | 88% |
| EXTRA_LONG_GUN | Silverwood 12 | 89% |

所有类共用自动投影居中，无每枪手动 offset 或 scale。细长枪只略提高目标最长轴占格，仍按投影宽高共同适配。新枪只需将正式 ID 与上述类别加入生成器映射并运行同一捕图/归一化流程。

## 静态验收

下表边界为最终 256×256 PNG 的非透明像素 `[minX,minY,maxX,maxY]`，坐标含末端像素。

| 枪械 | alpha bounds | fillX | fillY | longest | center offset (px) |
| --- | --- | ---: | ---: | ---: | --- |
| P9-01 | `[23,48,232,206]` | 82.03% | 62.11% | 82.03% | `(0,-0.5)` |
| Blackridge .50 | `[15,43,239,211]` | 87.89% | 66.02% | 87.89% | `(-0.5,-0.5)` |
| BR51-01 | `[15,72,239,182]` | 87.89% | 43.36% | 87.89% | `(-0.5,-0.5)` |
| HR55 | `[15,63,239,191]` | 87.89% | 50.39% | 87.89% | `(-0.5,0)` |
| Silverwood 12 | `[14,71,241,183]` | 89.06% | 44.14% | 89.06% | `(0,-0.5)` |

Blockbench 统一视角捕图已逐张检查，没有参考臂方块；Python 生成及 `--check` 一致性通过。BR51 GUI PNG 现为 256×256，原 1165×574 所致 mip 限制预计消失，仍需实机日志确认。没有运行 Java 编译（无 Java 改动），也没有进行游戏客户端/Creative Tab 验收。
