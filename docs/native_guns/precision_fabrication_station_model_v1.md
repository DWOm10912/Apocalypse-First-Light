# 精密制造台 V1 模型

状态：已注册静态四格方块与 BlockItem；无机器功能。模型为后续动态表现保留层级，V1 不包含动画文件或播放逻辑。方块行为及验证边界见 [入游戏说明](precision_fabrication_station_block_v1.md)。

| 项目 | 当前内容 |
| --- | --- |
| 名称 / Registry ID | 精密制造台 / Precision Fabrication Station；`apocalypse_firstlight:precision_fabrication_station` |
| 编辑源 | `src/main/blockbench/precision_fabrication_station.bbmodel` |
| 静态 Java 导出 | `src/main/resources/assets/apocalypse_firstlight/models/block/precision_fabrication_station.json` |
| 动态接入预留 Geo | `src/main/resources/assets/apocalypse_firstlight/geo/precision_fabrication_station.geo.json`；`geometry.precision_fabrication_station`，格式 `1.12.0` |
| 统一贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/block/precision_fabrication_station.png`，256×256，单图集，无 PBR / 发光通道 |
| 尺寸 | **宽 2 × 高 2 × 深 1 格**；约 32×32×16 模型单位 |
| 范围 | X −8～24；Y 0～32；Z 0.15～16；根枢轴 `[8,0,8]` |
| 正面 | NORTH / −Z；站在正面观察，左驱动、中央装配头及治具、右控制台 |
| 复杂度 | 253 cubes，21 groups，无网格面或动画 |
| 主体 | 独立钢框、钢制台面、导轨压装头、左右夹块、紧凑控制屏/三按钮、状态灯与嵌入灯条 |
| 下层 | 军绿零件箱、深灰双抽屉、黄铜色零件盒与通用零件，不绑定具体枪械 |
| 材质 | 深灰钢为主体；按用户反馈去除外壳密集噪点，仅保留低对比面差和边缘高光；少量暖色复合边条、橙色锁条/警示、绿色屏幕 |
| 背面与侧面 | 维护盖、双列散热口、固定框与紧凑侧检修板；不增加外接装饰电缆 |
| 标准电源接口 | 背面下方，外框 X12～20、Y3.9～11.9、Z15.4～16；`fe_power_port` 分组，朝 SOUTH；面板围绕接口中心布局 |
| 接口贴图复用 | 原接口 `[11,11,21,21]` 的 10×10 像素嵌入图集 `[235,235,245,245]`；接口世界位置不变，源坐标 X16/Y7.9，距格中心高度 0.00625 格。共享原图未修改 |
| 背面面板布局 | 左检修框收窄至 X−5.25～10；左右上下沿统一 Y3.9～11.9，间距 2 模型单位，统一 0.5 单位灰钢边框；仅调整背面装饰几何，碰撞与方块逻辑不变 |
| 背面排气孔 | 仅 rear_vent 区域的孔宽由 8 缩至 6 像素，四排两列保留，外框几何不变 |
| 灯与控制屏 | 只有可见材质，不实际发光，无状态切换或 GUI |
| 概念对应 | 高：保留四视图的宽高/进深、主框、左驱动、中央压装/夹具、右控制与三组下层收纳；按文字要求将大面积木台面改为钢面，并按用户要求替换背面接口图案 |

## 动画预留与后续渲染

- `assembly_head`：独立移动组件，枢轴 `[8,28.7,11.5]`；固定导轨及上支座属于 `frame/top_beam`，不随装配头整体移动。
- `central_fixture_left_clamp` / `central_fixture_right_clamp`：夹块分别可沿局部 X 开合，隶属 `central_fixture`。
- `work_light` / `status_light`：独立分组，仅预留后续材质或状态控制。
- V1 源采用 Java block 格式、保留完整分组与物品 Display；同源 Geo 骨骼为后续动态模型接入准备，**不等于动态渲染已实现**。
- 当前沿用枪械维护台的四格 baked model 路径，无 BER。后续若实现动画，可改为根方块 BER / Geo 层集中渲染，并保留四格真实占位；该动态方案尚未实施。
- FE 容量、输入、耗能、配方时长、电网、BlockEntity、GUI、制造等均未实现，不把概念参数记作当前游戏数据。

## 同步与校验

`tools/build-precision-fabrication-station.bb.js` 是仅用于**同名空项目**的初始 Blockbench 构建脚本；不可覆盖用户后续编辑。后续修改以 `.bbmodel` 为准。

在 Blockbench 中分别使用 `project` / `java_block` / `bedrock` 导出至上述路径，再运行：

```text
node tools/sync-precision-fabrication-station.mjs
```

该脚本检查源/Java/Geo 的逐部件坐标、UV、父子骨骼与枢轴，抽取源内嵌 PNG，验证标准接口区域逐像素一致、周围底板与机壳材质一致，规范化 Geo 并去除新 Bedrock Display 元数据。源 253 cubes 与两份导出一致；体积重叠 0，同向共面叠面 0；不使用偏移掩盖接缝。

已检查 Blockbench 正面、透视、背面与侧面轮廓，并在外壳平滑调整后复查窗口。游戏内显示验收状态见入游戏说明，不宣称动画已实现。

接口底板材质统一后已复查背面预览；`processResources --offline` 与 `git diff --check` 通过。仅资源处理验证，不代表完整构建或游戏内验收。
