# Fuel Island Kit V1（加油岛套件 V1）

状态（2026-10-04）：**已实现，未实机验证**。`compileJava --offline` PASS。外观只看过离线概念图；游戏里的模型、PBR、朝向、碰撞、生存挖掘掉落都还没看过。

## 定位

- 加油机站的那块抬高的混凝土岛的拼装件，截面和加油机自带的那段路缘完全一样：混凝土高 3 px（约 19 cm），两条长边包钢角铁。
- 拼法：`端头 + 直段 + 加油机（2 格）+ 直段 + 端头`，中间可以放多台加油机或更多直段；端头上面一格放防撞柱。也可以先用直段铺满整条岛，再对着路缘顶面放加油机：它会顶替下面两格路缘嵌进岛里，拆掉时把路缘还回去（见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md)）。
- 用户 2026-10-04 定：端头用半圆头；防撞柱用灰色钢柱加黄黑反光带；不做"端头自带防撞柱"的版本，防撞柱单独放（以后程序生成也是分开放）。

## 方块

| ID | 名称 | 堆叠 | 挖掘 | 说明 |
|---|---|---|---|---|
| `apocalypse_firstlight:fuel_island_curb` | 加油岛路缘 / Fuel Island Curb | 64 | 镐（任何等级），`requiresCorrectToolForDrops` | 1 格直段 |
| `apocalypse_firstlight:fuel_island_end` | 加油岛端头 / Fuel Island End | 64 | 同上 | 前半格直段，后半格半径 8 px 的半圆，钢角铁顺着弧绕过去 |
| `apocalypse_firstlight:fuel_island_bollard` | 防撞柱 / Bollard | 16 | 镐，钻石级（钢结构件，`needs_diamond_tool`） | 灰色镀锌钢管，直径约 18 cm、高约 1 m，圆顶，底部法兰盘和 4 颗螺栓，三道反光套环（黄、黑、黄，实体套环，不是画的） |

- 代码：`block/FuelIslandCurbBlock`、`block/FuelIslandEndBlock`、`block/FuelIslandBollardBlock`。
- 强度：路缘和端头 1.8 / 6.0、石头音效；防撞柱 5.0 / 6.0、金属音效。战利品表都带 `survives_explosion`。
- 质量（估计值）：路缘 1.2 kg，端头 1.0 kg，防撞柱 8 kg。
- 创造标签页："家具与设施"，排在加油机后面。

## 朝向与放置

- **路缘**：`facing` 朝向玩家；和加油机一样，加油岛沿 `facing` 的顺时针方向延伸。所以面对加油机正面放路缘，正好接在加油机两边。
- **端头**：`facing` 同上，接口在 `facing` 的逆时针一侧，半圆朝顺时针一侧。放下时如果旁边有加油岛部件（路缘、端头、加油机底层的两格），会自动转成接口对着它；没有就像路缘一样朝向玩家。
- **防撞柱**：没有朝向。属性 `on_curb`：下面一格是路缘或端头时为 true。
  - 这时防撞柱在路缘上面那一格，模型下沉 13 px，站在路缘顶面上（模型 `fuel_island/bollard_on_curb`）；
  - 放下时和下面一格变化时自动更新；
  - 下面既不是加油岛部件、也没有结实的中心支撑时，防撞柱会掉落。

## 碰撞与选择形状（px，朝北）

- 路缘：整格，高 3。
- 端头：前半格整宽；半圆分四级台阶近似（宽度取每级外缘处的半圆宽）。
- 防撞柱：
  - 在地上：柱身 6.55..9.45，高 16.58（超过一格，像栅栏），加底盘；
  - 在路缘上：碰撞只算这一格里露出的柱子（高 3.58 px）。玩家站在路缘上时身体和这段重叠，所以照样挡人。选择框是整根柱子（往下伸进路缘那一格）。

## 模型

- 生成器：`tools/build-fuel-island-v1.mjs`（`--check`、`--preview DIR`）。
- 一张 1024 图集（15.75 texel/px）加 `_s` / `_n`。材质：混凝土和钢包边与加油机相同；防撞柱是镀锌钢（LabPBR 铁）；反光带是黄 [196,160,52]、黑 [30,31,33] 的光滑面材。
- 三角面数：路缘 52，端头 424，防撞柱 520。共面重叠为 0。
- 2026-10-04 用户实机发现端头和直段接不上：端头的钢角铁沿路径扫出来，第一版路径方向反了，角铁里外颠倒，在接缝处和直段的角铁错开。已改正，接缝处两边截面的每个顶点都一致。
- 可编辑源：`src/main/blockbench/fuel_island_v1.bbmodel`（组：`straight`、`end`、`bollard`）和 `textures/fuel_island_v1{,_s,_n}.png`。
- 运行时：`models/block/fuel_island/{straight,end,bollard,bollard_on_curb}.obj/.mtl/.json`，贴图 `textures/block/fuel_island{,_s,_n}.png`，方块状态 `blockstates/fuel_island_{curb,end,bollard}.json`，物品模型 `models/item/fuel_island_{curb,end,bollard}.json`。

## 需要实机检查

- 和加油机拼起来是否严丝合缝（截面、高度、钢包边）；四个朝向；端头自动转向。
- 2026-10-04 起，加油站顶棚立柱可以嵌进直段路缘（占掉那一格，自己画那段路缘）；端头把它当成岛的一段来接（`isIslandPiece`），见 [fuel_canopy_kit_v1.md](fuel_canopy_kit_v1.md)。没有实机验证。
- 防撞柱在地上、在端头和路缘上的高度；拆掉下面的路缘后防撞柱掉落。
- 碰撞（能不能走上路缘、防撞柱挡不挡人）；生存挖掘和掉落（路缘任何镐，防撞柱钻石镐）。
- 外观和 PBR（光影下）。
