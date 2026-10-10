# gas_station_01（Fuel Stop A1 正式资产）V1

状态（2026-10-10）：**NBT 已导出并导入项目，schema 1 元数据已写，两个 GameTest PASS**（四向放置都跑过，见"验证"）。还没进任何城市生成池，也没有在游戏里旋转放置后用眼睛看过。

**名字**：导出时的 ID 是 `gas_station_02`。2026-10-10 用户定为 `gas_station_01`："这个其实是第一栋加油站"；以前那个 `gas_station_01` 的旧加油站已经删掉了。项目里的 NBT 和元数据用新名字，`run/afl_authoring_exports/` 下的导出原件保留原名。

设计和施工见 [fuel_stop_a1_design_v1.md](fuel_stop_a1_design_v1.md)；城市规范见 [north_american_roads_and_lots_spec_v1.md](north_american_roads_and_lots_spec_v1.md) §9。

## 文件

| 什么 | 路径 |
|---|---|
| NBT | `src/main/resources/data/apocalypse_firstlight/structures/gas_station_01.nbt`（192 KB，SHA-256 前缀 `befb52f3ee10e9da`，和 `run/afl_authoring_exports/gas_station_02.nbt` 一样） |
| 正式元数据 | `src/main/resources/data/apocalypse_firstlight/afl_worldgen/structures/gas_station_01.json`（`StructureDefinition` schema 1） |
| 不用的 | 导出器写的 `run/afl_authoring_exports/gas_station_02.json`：旧格式，写着 `front=SOUTH`。没有导入 `small_city/buildings/`，免得出现两份互相矛盾的元数据（统一规范 §7） |

## 导出

- 2026-10-10 00:10，用户在开发存档"新的世界 (1)"里 `/afl_author resume gas_station_02 -113 -56 411 64 64 16 5` → `configure COMMERCIAL "COMMERCIAL,MIXED,EDGE" true true` → `validate` → `export`。
- 整片导出，不按 [城市接口](fuel_stop_a1_city_interface_v1.md) §5.2 拆模块：路面、标线、路缘、灯杆、地下管线电缆都是在世界里建的真方块。
- 导出时的状态处理（`authoring/ExportState`）：没电、油换成灌油标记、没有进行中的状态，插头保留。见 [small_city_building_authoring_v1.md](small_city_building_authoring_v1.md)"Export state"。

## 元数据

```json
{
  "schema_version": 1,
  "asset_revision": "2026-10-10-befb52f3ee10e9da",
  "structure_nbt": "apocalypse_firstlight:gas_station_01",
  "category": "apocalypse_firstlight:commercial",
  "front": "NORTH",
  "ground_anchor_offset_y": 5,
  "allowed_rotations": ["NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"],
  "sockets": [
    {"name": "main_entrance", "position": [51, 5, 0], "facing": "NORTH", "type": "DRIVEWAY"},
    {"name": "side_entrance", "position": [0, 5, 60], "facing": "WEST", "type": "DRIVEWAY"}
  ],
  "tags": ["apocalypse_firstlight:fuel_station", "apocalypse_firstlight:corner_lot"]
}
```

- **正面 NORTH**：世界里的 A1 是按图纸转 180° 建的（世界 x = −50 − u，z = 474 − v），所以模板坐标 x = 63 − u、z = 63 − v，正好是规范 §9 的 NORTH 正式坐标，不用另做归一化。主路在北（z = 0），支路在西（x = 0）。
- **地面锚点 5**：模板 y 5 是地面上第一层空气（世界 y −51，k 0）；y 0–4 是地下（k −5..−1）。放置时 `originY = 地面上第一层空气的 y − 5`。
- **接口**（入口第一格空气，在外边界上，朝外）：
  - `main_entrance`：S1，x 47..55、z 0，净宽 9，和规范一致；
  - `side_entrance`：E1，x 0、z 57..62，净宽 6，和规范一致；
  - 两个入口的出车道半边地面上有停车线（`pavement_bar`，占 y 5 那一格），所以接口取进车道半边的格子：S1 取 x 51（x 52..55 有停车线），E1 取 z 60（z 57..59 有停车线）。y 4 都是沥青。
- 类别和标签是我定的，城市配方还没有，以后按需要改。

## 实际范围

- 64 × 16 × 64（x × y × z），世界最小角 (−113, −56, 411)。
- 各层非空气方块数（y0 = k −5）：y0–y3 各 4096（地下：泥土、油罐、管道、电缆、混凝土），y4 4096（地面层 k −1：沥青、路面、草、路缘），y5 615、y6 260、y7 188、y8 218、y9 463、y10 541、y11 38、y12 16、y13 16，y14、y15 空。最高的是灯杆（k 8）。
- 地下部分要计入施工占地：模板最下面 5 层（k −5..−1，含地面层）整层写入，放下时会覆盖原地形；上面的空气层也照写，会清掉原地形。
- 边缘：路面、路缘、灯杆、南边界（z 63）上的一段电缆贴着边界，都在模板以内；读存档确认外面一圈没有被截断的东西。南边界那段电缆是以后接城市电网的位置。
- 方块实体：3 个地下油罐（6 个）、3 台潜油泵、8 台加油机和 16 个底槽、20 根顶棚立柱、16 个灯杆底座、价格牌、配电盘、电表箱、78 个可搜索容器、5 台插着的电器（3 台饮料冷柜、2 台冰柜）、便携发电机等。

## 放置

- 必须加结构处理器 `worldgen/structure/AflBlockEntityProcessor`（插头和电表箱的偏移跟着转）。
- 灌油：油罐和发电机第一次在服务端加载时按规则掷（`fluid/FuelFill`）。
- Mirror 固定 NONE（V1 规范）；另一手的转角要另做镜像资产。

## 验证（2026-10-10，GameTest，`-PaflWithoutShaders`：Oculus 不能在专用服务器上加载）

- `dev/GasStation01GameTests`（`src/dev/gas-station-01-gametest.init.gradle`）PASS：
  - 正式元数据对着 NBT 用 `StructureDefinitionLoader.validate` 校验通过（接口在边界、朝外、是空气、四个旋转都不出界）；
  - NBT 按四个旋转各放一次（带处理器），40 tick 后：5 台电器都还插在自己的插座上；3 个油罐完整、已掷灌油、在规则范围内；发电机已掷；两个入口是沥青上的空气；没有方块变成空气；所有方块状态都还是 NBT 状态转过去的样子；
  - 调色板里 512 种状态里的 AFL 方块，转 90° 后朝向、轴向、东西南北开关都跟着转。
  - 一共 91380 项检查。
- `dev/ExportStateGameTests`（`src/dev/export-state-gametest.init.gradle`）PASS：偏移转换 36 种情况、导出状态处理、油罐规则 400 次掷出 91 次空（22.75%，目标 20%）、放下的油罐 / 油桶 / 发电机按规则灌。
- **这次测试找到并修好的问题**：电缆（`PowerCableBlock`）和油管（`FluidPipeBlock`）继承原版 `PipeBlock`，没有旋转。转 90 / 180 / 270° 放置后，连接还指着原来的方向，方块更新再按同样错的邻居重算，沿转后方向的整段线全断开：第一次跑时约 70% 的电缆和油管变了状态（四个方向共 933 + 325）。新加 `block/PipeSides`，两种方块按旋转、镜像转换六个面的连接；修好后变化是 0，测试改成有变化就失败。
- 没做：游戏里旋转放置后用眼睛看（门、灯、车位线、立面）；放进真实城市道路旁接路。
