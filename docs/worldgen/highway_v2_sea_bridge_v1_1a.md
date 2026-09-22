# Sea Bridge V1.1A — H 型双塔主跨实现与第一轮实机修正报告

参考图用于高耸塔腿、凹槽、高位横梁和双塔轮廓。保留现有道路；本轮不放斜拉索。以下为冻结实现，不是未来方案。

2026-09-21 第一轮实机截图确认了两类视觉问题：未来索锚使用的 `steel_block` 被错误地当成可见标记块放在桥面边缘和塔腿外侧；横梁中的横向细钢梁在真实模型截面下呈现为悬空短划线。本轮仅修正这两项，不改主跨、塔位、塔高、Pylon Zone、基础、普通桥墩抑制、道路、路由或 fallback。

## 1. 结构设计最终值

S/P 为既有 plan 的局部 station，L 为横向偏移，D 为塔 station 的原 roadY。plan 沿世界轴递增，不一定沿 mainland→satellite。世界位置统一通过 `LandmarkMainSpan.position` 计算。

| 参数 | 冻结值 |
| --- | --- |
| 最小桥长 | 576 格 |
| Preferred / minimum 主跨 | 256 / 256 格，不缩成小塔桥 |
| 两塔 station | A=floor((length−256)/2)，B=A+256 |
| 最小 approach | 扣除原 abutment 后，每侧到塔中心至少 144 格 |
| 632 格代表桥 | A=188，B=444；至两端均 188 格 |
| Pylon Zone | P−8…P+8，共 17 格 |
| 每腿 footing | L=12…14 或 −14…−12，S=P−5…P+5，3×11 格 |
| Footing Y | 每个 X/Z 列独立从合法 seabed 填至 D−4 |
| Pedestal | 每侧同三格宽，S=P−4…P+4，Y=D−3…D+5 |
| Tower shaft | 每侧同三格宽，S=P−2…P+2，Y=D+6…D+61 |
| 塔面混凝土竖肋 | S=P±3，L=±12/±14，Y=D+6…D+61 |
| 塔面凹槽桁架 | S=P±3，L=±13，Y=D+6…D+61，steel_brace AXIS=Y；未来索锚仅保留数据，不覆盖桁架 |
| Tower top | 每侧同三格宽，S=P−3…P+3，Y=D+62…D+64 |
| 横梁混凝土下弦 | L=−11…11，S=P−2…P+2，Y=D+46…D+47 |
| 横梁混凝土上弦 | L=−11…11，S=P−2…P+2，Y=D+51…D+52 |
| 横梁混凝土端框 | L=−11…−10 与 10…11，S=P−2…P+2，Y=D+48…D+50 |
| 横梁竖向钢梁 | S=P±2，L=−9,−3,3,9，Y=D+48…D+50，steel_beam AXIS=Y |
| 横梁竖向钢桁架 | S=P±2，L=−6,0,6，Y=D+48…D+50，steel_brace AXIS=Y |

64 格塔高配约 29 格外轮廓宽；高位横梁底部 D+46，塔头到 D+64。两塔主体相对各自 D 完全相同，基础随海床、平台随既有纵坡。未修改既有模型/贴图/UV。

### Pylon Zone 过渡

| 距塔中心 | 平台外缘 |
| --- | --- |
| 0…5 格 | abs(L)=14 |
| 6…7 格 | abs(L)=13 |
| 8 格 | abs(L)=12 |

平台按 roadY(S) 填 roadY−3…roadY；底半砖跟随最外列，塔基实体覆盖其占用位置。只过滤塔区原 |L|=12、Y=roadY+1 的半砖；|L|≤11 所有车道、路肩路缘、中央分隔、标线与纵坡连接不变。

### Future cable anchor 表

两塔、左右两侧、沿 station 正负两面统一复用下表。每塔 36 个 tower socket + 36 个 deck socket，合计 144 个 socket。socket 是纯数据坐标，本轮不放实体标记块，也不放钢缆。

| index | Tower socket 高度 | Deck station 距 P |
| --- | --- | --- |
| 0 | D+24 | 24 |
| 1 | D+28 | 36 |
| 2 | D+32 | 48 |
| 3 | D+36 | 60 |
| 4 | D+40 | 72 |
| 5 | D+44 | 84 |
| 6 | D+56 | 96 |
| 7 | D+59 | 108 |
| 8 | D+62 | 120 |

- Tower socket：L=±13，S=P±4；坐标保留在 `Anchor`，不再以 `steel_block` 覆盖塔面连续桁架。
- Deck socket：L=±13，S=P±(24+12×index)，Y=roadY(S)+1；坐标保留在 `Anchor`，该格不落方块。下方 roadY−3…roadY 仍为连接原 ±12 的混凝土 ledge。
- `Anchor.socket` 是方块位置；未来以 socket 中心定位，side 表示 L 正负，facingAlong 表示 station 正负。
- Tower anchors 避开横梁 D+46…52，socket 在横梁纵向外侧；不穿道路。
- 主跨/背跨均可接，主跨最远两组 deck socket 距中心各 8 格。
- 冻结的是 Semi-Fan 锚点，不承诺仅三种固定斜率就能连续拼接；下一轮需按端点解决细索连续几何。

## 2. 实现

- `LandmarkMainSpan`：确定性主跨、长度/approach guard、station 换算、普通 pier 实体相交判定。
- `PylonZoneGeometry`：只接管外侧护缘，生成渐变平台。
- `BridgePylonGeometry`：132 列 footing 验证、主塔/横梁去重 placements、纯数据 anchors；不再生成可见 `steel_block` socket。
- 未新增方块、物品、BE、模型、贴图或 renderer。

## 3. Sea Bridge 接入点

`SeaBridgeEngineering.build` 保留 endpointY 与原 1/8 grade guard。`plan` 建立原两端 profile，先完整验证 Landmark，再构建保留的普通 pier foundation map；abutment 原算法保留。

`render` 调用原 `HighwayRenderer.renderNatural`，仅用局部 deck adapter 过滤指定护缘。原 abutment 与 Landmark 经同一 finite/chunk writer 放置。

`NaturalHighwayGenerationAdapter`、缓存、RouteGraph、Bridgehead、Terrain 及通用 HighwayProfile/Renderer 未改动。

只对新生成区块生效，不自动升级旧桥。手动验证应使用相同 seed 新存档或尚未生成的桥区；旧 chunk 没有主塔不是当前计划未接入的证据。

## 4. Pier Suppression

仅 Landmark enabled 时，普通 pier 完整纵向 `[s−3,s+3]` 与 `[A−5,B+5]` 相交即排除。原 `profile.pierAllowed` 依据 map 自然跳过，无需改通用 renderer。

代表桥原计划 19 个 pier，抑制 9、保留 10。抑制全局 station：

`−7456, −7424, −7392, −7360, −7328, −7296, −7264, −7232, −7200`。

保留 station 数不等于基础验证后实际可放置数，仍遵循 V1 的普通基础规则。

## 5. Fallback

| reason | 条件 | 行为 |
| --- | --- | --- |
| BRIDGE_TOO_SHORT | length<576 | 原样普通 V1 |
| APPROACH_TOO_SHORT | 扣 abutment 后 approach<144 | 原样普通 V1 |
| BUILD_HEIGHT | 任一结构超出 min/max build height | 原样普通 V1 |
| ENVELOPE_EXCEEDED | 任一 footing/结构超出原 bounds | 原样普通 V1 |
| PYLON_FOUNDATION_FAILED | 任一 footing 列无基础或基础 Y 非法 | 原样普通 V1 |
| GRADE_INFEASIBLE | 原 endpoint grade>1/8 | 保持 V1 原有整桥不生成行为 |

四个 3×11 footing 共 132 列，各自调用原 immutable `pierFoundation`，最多向下 128 格，受 min build height 限制。任何一列失败即丢弃全部候选 placements、恢复全部普通 pier、保留原护缘；不生成半塔。原 V1 普通 pier/abutment 的 PARTIAL_FOUNDATION_FAILED 独立保留。

## 6. Chunk / Claim

所有落块 |L|≤14；原 29 格 HARD claim、[-64,320) reservation、有限 writer、chunk 查询全部复用。

同 crossing 全桥一次确定性规划；去重列表只提交属于目标 chunk 的方块。几何不依赖邻 chunk 已生成状态，不新增 ownership primitive。

## 7. Diagnose / Export

Sea Bridge diagnose 新增 landmarkEligible/enabled/status、mainSpanStart/end、global/local pylon stations、base/top Y、zone intervals、suppressed/retained stations、基础状态、fallback、future anchor 规则/数量和 ownedLandmarkCells。

wouldRender 同时计入 owned deck 和 owned Landmark。diagnose 仍为当前代码 dry replay，不证明历史区块放置。

离线 export 输出纯长度 eligibility 和候选 station/anchors，实际 enabled、高程、基础为 UNKNOWN/NOT_SAMPLED；长度不合时明确 DISABLED。

## 8. Tests

已执行并通过：

```text
gradlew.bat --offline compileJava processResources seaBridgeLandmarkTest seaBridgeV1Test -I scripts/highway-branch-tests.init.gradle
BUILD SUCCESSFUL
SeaBridgeLandmarkTest PASS checks=2065934
SeaBridgeV1Test PASS checks=14

gradlew.bat --offline build -x check
BUILD SUCCESSFUL
```

第一条 BUILD SUCCESSFUL 覆盖编译、资源处理和两组桥梁专项测试。第二条覆盖 `build -x check` 的打包生命周期，但明确跳过 `check`；不能把它当作完整测试集通过。

现有 reinforced_concrete、slab、steel_block、steel_beam、steel_brace 的 15 个 blockstate/block/item JSON 可解析，processResources 输出与源文件 hash 一致。`git diff --check` 通过。

初次 V1.1A 测试校正了 pier 数断言，并发现负方向锚座范围反序；已统一区间端点后重新运行完整专项验证通过。第一轮实机修正又加入零 `steel_block`、144 个 data-only anchor、混凝土端框、竖向 steel beam/brace、上下弦接触和四方向回放断言。数百万 checks 是逐格断言累计，不是数百万独立测试用例。

`SeaBridgeLandmarkTest` 使用真实 palette、真实 `SeaBridgeEngineering.plan/render` 与 finite/chunk writer，在内存 WorldGenLevel proxy 回放；不创建世界、生成 chunk 或启动客户端。

覆盖代表 seed graph、132 列基础、末列失败原子回退、短桥、高度、bounds、四方向、左右/双塔对称、竖向 beam/brace axis、data-only socket、普通 pier suppression、道路 ±11 在 r−3…r+6 与 V1 逐格对比、所有相关 chunk 正反顺序回放。

测试 grade=70/78、seabed=40 为合成条件，不能当作该 seed 实际 endpointY/海床通过。

## 9. 代表 Seed 测试坐标

Seed：`-4332662446239654818`。

| 位置 | X/Z | 推荐 TP |
| --- | --- | --- |
| Pylon A，local 188 | −2592 / −7452 | `/tp @s -2592 180 -7452` |
| Pylon B，local 444 | −2592 / −7196 | `/tp @s -2592 180 -7196` |
| 桥中心 | −2592 / −7324 | `/tp @s -2592 200 -7324` |
| 大陆端 | −2592 / −7011 | `/tp @s -2592 200 -7011` |
| 卫星岛端 | −2592 / −7638 | `/tp @s -2592 200 -7638` |
| A 侧面 | −2532 / −7492 | `/tp @s -2532 150 -7492` |
| 双塔侧面 | −2472 / −7324 | `/tp @s -2472 170 -7324` |

TP Y 为飞行观察高度。塔腿中心 X 为 −2605、−2579；实际 D 应看 diagnose。两塔在朝世界 Z 递增顺序命名，A 靠卫星岛、B 靠大陆。

## 10. Files Changed

- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/LandmarkMainSpan.java`（新增）
- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/PylonZoneGeometry.java`（新增）
- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/BridgePylonGeometry.java`（新增）
- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/SeaBridgeEngineering.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwayLiveGenerationDiagnostic.java`
- `src/dev/java/com/antaurora/apofirstlight/dev/HighwayNetworkExport.java`
- `src/test/java/com/antaurora/apofirstlight/worldgen/highway/SeaBridgeLandmarkTest.java`（新增）
- `src/test/java/com/antaurora/apofirstlight/worldgen/highway/SeaBridgeV1Test.java`（补充现有 steel palette 绑定）
- `scripts/highway-branch-tests.init.gradle`
- `docs/worldgen/highway_v2_sea_bridge_v1_1a.md`（本报告，新增）
- `docs/worldgen/highway_v2_sea_bridge_v1.md`
- `docs/authoring/afl_industrial_palette_v1.md`

`.obsidian/workspace.json` 为本轮开始前已有改动，不属于本次实现。

## 11. Validation

H-PYLON GENERATED=YES 指真实 production render 经 chunk-owned writer 在内存世界回放中产出完整双塔；没有启动客户端或在用户存档生成方块。用户提供的截图是修正前问题证据；修正后的塔腿与横梁观感仍待新世界/未生成区块实机验收。

```text
DESIGN FROZEN = YES
LANDMARK PLANNING = YES
PYLON ZONE = YES
H-PYLON GENERATED = YES (production writer in-memory replay; no live-world claim)
FOUNDATION VALIDATION = YES
PIER SUPPRESSION = YES
FALLBACK = YES
CHUNK-OWNED WRITING = YES
WITHIN ±14 ENVELOPE = YES
FUTURE CABLE ANCHORS = YES
STEEL CABLE SYSTEM = NOT IMPLEMENTED
COMPILEJAVA = PASS
PROCESSRESOURCES = PASS
BUILD = PASS (`build -x check`)
RUNCLIENT = NOT RUN
WORLD VISUALLY VERIFIED = PRE-FIX EVIDENCE ONLY; POST-FIX PENDING
COMMIT = NO
PUSH = NO
```

DOCUMENTATION:

DOCS UPDATED = YES

Updated:
- docs/worldgen/highway_v2_sea_bridge_v1_1a.md
- docs/worldgen/highway_v2_sea_bridge_v1.md
- docs/authoring/afl_industrial_palette_v1.md

## 12. H 型主塔第一轮实机修正结论

### STRUCTURE SCOPE

- 仅修改 `BridgePylonGeometry` 的可见锚点表现和高位横梁钢构排布，并补强 `SeaBridgeLandmarkTest`。
- 保留 `LandmarkMainSpan`、A/B 塔 station、64 格塔高、17 格 Pylon Zone、256 格主跨、footing、pier suppression、道路、桥台、路由、foundation/fallback/chunk-owned writing。
- 未新增 connector asset、模型、贴图、UV、方块、物品、BE、renderer、斜拉索或 slope 机制。

### VISIBLE STEEL_BLOCK MARKERS REMOVED

- `BridgePylonGeometry` 不再生成任何 `steel_block` placement；修正前 144 个可见 `steel_block` placement 降为 0。
- 塔腿锚点原先每点覆盖一格凹槽桁架并向外伸一格；现在完整保留连续 `steel_brace` 凹槽，外伸标记格不放置。
- 桥面锚点上方 `roadY+1` 的 `steel_block` 全部移除；下方混凝土 ledge 保留，因此不会改变道路、护缘或未来结构基座。

### ANCHOR DATA PRESERVED

- `Anchor` 记录数量仍为 144：72 个 tower socket + 72 个 deck socket。
- index、高度、station 偏移、side、facingAlong 和 socket 坐标公式全部未改。
- 专项测试逐个要求 anchor socket 不得出现在 Landmark placements 中，确保它们是纯数据预留而非可见标记。

### CROSSBEAM STRUCTURE FIXED

- 实机截图中的“横向黑色短划线”来自旧排布：横向 `steel_beam` 先铺满 L=−11…11，随后偶数 L 的竖向 `steel_brace` 覆盖交点，最终只剩交替的孤立 beam 段。
- 已删除该横向 steel beam 行；横梁两端新增 L=−11…−10 与 10…11 的三格高混凝土端框，直接连接上下混凝土弦与塔腿。
- `steel_beam` 改为 L=−9,−3,3,9 的 Y 轴竖向主构件；`steel_brace` 改为 L=−6,0,6 的 Y 轴竖向桁架。两类构件均从 D+48 连续到 D+50，下接 D+47 下弦、上接 D+51 上弦，并在两侧 S=P±2 对称生成。
- 横梁中不再存在需要解释的悬空横向钢构；钢构与端框之间保留空气开口，没有用整面混凝土封墙。

### REGRESSION STATUS

- `compileJava`：PASS。
- `processResources`：PASS。
- `seaBridgeLandmarkTest`：PASS，2,065,934 次逐格/契约断言。
- `seaBridgeV1Test`：PASS，14 checks。
- `build --offline -x check`：PASS；`check` 明确跳过。
- `git diff --check`：PASS，仅报告既有 Windows 换行提示，无 whitespace error。
- `runClient`：未运行；未修改用户存档，未生成新区块。
- 用户截图仅证明修正前问题；修正后视觉仍需用同 seed 的新世界或未生成桥区复验。

### SAME SEED / SAME COORDS

- Seed：`-4332662446239654818`
- Pylon A：`/tp @s -2592 180 -7452`
- Pylon B：`/tp @s -2592 180 -7196`
- 桥中心：`/tp @s -2592 200 -7324`
- A 侧面：`/tp @s -2532 150 -7492`
- 双塔侧面：`/tp @s -2472 170 -7324`

### FILES CHANGED

- `src/main/java/com/antaurora/apofirstlight/worldgen/highway/BridgePylonGeometry.java`
- `src/test/java/com/antaurora/apofirstlight/worldgen/highway/SeaBridgeLandmarkTest.java`
- `docs/worldgen/highway_v2_sea_bridge_v1_1a.md`

以上三项是本次第一轮实机修正的增量文件；本报告前面列出的其余文件属于尚未提交的 V1.1A 主实现。

### FINAL VALIDATION

```text
VISIBLE STEEL_BLOCK MARKERS REMOVED = YES
ANCHOR DATA PRESERVED = YES
CROSSBEAM STRUCTURE FIXED = YES
H-PYLON SILHOUETTE PRESERVED = YES
SEA BRIDGE SYSTEMS PRESERVED = YES
COMPILEJAVA = PASS
PROCESSRESOURCES = PASS
BUILD = PASS (`-x check`)
RUNCLIENT = NOT RUN
POST-FIX WORLD VISUAL VERIFICATION = PENDING
COMMIT = NO
PUSH = NO
```
