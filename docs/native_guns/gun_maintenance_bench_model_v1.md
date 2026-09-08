# 枪械维护台 V1 模型

状态：美术资产已获用户确认，现已接入 `apocalypse_firstlight:gun_maintenance_bench` 四格静态方块。方块行为与验证边界见 [入游戏说明](gun_maintenance_bench_block_v1.md)。本轮未改变已确认源几何或贴图。

| 项目 | 当前内容 |
| --- | --- |
| 名称 | 枪械维护台 / Gun Maintenance Bench |
| 编辑源 | `src/main/blockbench/gun_maintenance_bench.bbmodel` |
| Java 模型 | `src/main/resources/assets/apocalypse_firstlight/models/block/gun_maintenance_bench.json` |
| 可选 Geo | `src/main/resources/assets/apocalypse_firstlight/geo/gun_maintenance_bench.geo.json` |
| 唯一贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/gun_maintenance_bench.png` |
| 图集 | 256×256；共享材质分区、像素木纹、面差与克制磨损，无 PBR |
| 视觉尺寸 | **正面宽 2 × 深 1 × 高 2 格**；用户明确改用参考图比例，不采用原文字的宽 1、深 2 |
| 几何范围 | X：−8～24；Y：0～32；Z：0.05～16；16 模型单位为 1 格 |
| 组织 | 22 个分组，**449 cubes**；交接区域分件裁切，保留原材质 UV，不堆额外装饰 |
| 正面 / 原点 | 正面 NORTH（−Z），竖直 +Y；底部中心及组旋转枢轴 `[8,0,8]` |
| 材质 | 黑灰钢架/侧板、深灰挂板、棕木台面、蓝台钳、青绿维护垫、暗黄三格零件盒、军绿储物箱、灰工具箱与红盖清洁瓶 |
| 工具墙 | 钳子、螺丝刀、扳手、小锤、清洁刷；右侧小架放油壶与小罐 |
| 上半部侧板 | 两侧镜像；深度、折边、顶部包边与通风槽统一，留出工具墙可见空间 |
| 灯罩 | 最前部外壳沿 −Z 增加 1 单位出挑，灯条/灯槽同步前移；未增加宽度或厚度，未移动立柱 |
| 灯光 | 仅浅色灯条材质及灯槽，无动态照明、光源或动画 |

## 接缝与验证

- 按用户要求裁切相交部件，交接处 **0 间距**，没有用微小偏移遮掩共面闪烁；`__joint_*` 是可编辑裁切分件。
- `node tools/validate-gun-maintenance-bench.mjs` 检查源/运行时 cube 数、贴图绑定、PNG 与源内嵌贴图一致、UV/Java 模型范围、上半部结构镜像体积，以及共面多边形重叠。
- 最终检查：轴对齐体积重叠 0，共面同向叠面 0（含四个旋转工具部件）。旋转工具的嵌入式连接不宣称进行了完整实体布尔消交。
- 原生 Blockbench Java 导出；Geo 使用同源 Bedrock 几何，将格式规范化为 `1.12.0`，去除新版 Bedrock `item_display_transforms`。重新导出 Geo 后运行 `node tools/validate-gun-maintenance-bench.mjs --normalize-geo`。Java 模型保留独立 Display 与 particle 贴图。
- Blockbench 窗口造型确认已完成；方块入游戏后的独立验证状态见入游戏说明，不能用资源处理检查代替实机视觉验收。
- 最终源文件与当前窗口编译内容一致并已保存；资产校验、`processResources --offline` 和 `git diff --check` 通过。未 commit、未 push。

## 当前方块接入

采用 **2×1×2** 四格真实占位；放置几何先将源 X 整体偏移 +8 对齐格网，再按 X/Y 的 16 单位边界分片。`tools/export-gun-maintenance-bench-parts.mjs` 校验源与完整 Java 导出对应关系，保留裁切面的 UV，禁止为裁切面添加内部盖板。

正式渲染为普通 baked block model，不使用 BER、BlockEntity 或 GeckoLib 动画层。四片位于 `src/main/resources/assets/apocalypse_firstlight/models/block/gun_maintenance_bench/{base,side,upper,upper_side}.json`，由同名 blockstate 的 `facing` / `part` 选择。碰撞按结构分格，不按每个小工具细分。

`models/item/gun_maintenance_bench.json` 继续引用完整 `models/block/gun_maintenance_bench.json`，保留源内 GUI / 手持 / ground Display；可选 Geo 不参与本方块渲染。重新导出完整 Java 模型后执行分片脚本同步运行时。已注册 Block / BlockItem 与四格联动逻辑，钻石级及以上的镐采集掉落；没有 UI、配件/修理、配方、电池或照明功能。
