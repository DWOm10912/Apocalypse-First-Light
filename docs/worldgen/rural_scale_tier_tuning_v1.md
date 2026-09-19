# Rural Scale / Tier Tuning V1

状态：自然生成档位参数与小范围搜索预算已实现；`compileJava` 离线通过。尚未运行世界生成、客户端、GameTest、接受率或视觉验收。

## 档位与有效规模

| Tier | 选择权重 | Reservation | 随机目标建筑数 | 有效最低建筑数 | 农田规则 |
| --- | ---: | ---: | ---: | ---: | --- |
| Isolated | 25 | 56×56 格 | 1 | 1 | 原规则 |
| Farmstead | 30 | 72×72 格 | 2–4 | 2 | 原规则 |
| Cluster | 30 | 96×96 格 | 6–8 | 4 | 原规则 |
| Full | 15 | 128×128 格 | 9–12 | 8 | 原规则 |

权重合计100。`RuralScaleTier.choose(seed, center)` 仍用原 seed/中心计算方式选档；`targetBuildings` 现在按独立的 `targetMinBuildings..maxBuildings` 取目标，`minBuildings` 只作为有效计划的最低规模。Cluster 可在只放下4–5栋时继续接受，但原农业/住宅要求和农田最低数仍会审查；Full 达到8栋即过数量门槛，即使随机目标为9–12且少一栋也不会因目标不足单独拒绝。Full 必须仍有非农舍住宅与谷仓，Farmstead 的谷仓、Cluster 的农业要求及通用住宅要求均未变。未增加 Full→Cluster 等档位回退。

这调整的是**候选被选中后的档位与目标**。StructureSet 仍为 spacing=40 chunks、separation=20 chunks、salt=1374512467、唯一结构权重1；未提高总体候选频率。由于不同档位的占地与门槛不同，25/30/30/15 是候选档位概率，**不是成功落地后实测比例**；实际成功率可能变化，尚待用户实机验收。

## Lot 搜索预算

`RuralNaturalGenerator.findLot` 仍使用 Building/Lot V2 的原 frontage、真实模板边界、4格建筑间距、access/道路/terrain/保留区检验。仅增加每个资产规格的尝试上限，避免先尝试的角色吃掉整村请求预算，并相应增加 Cluster/Full 全局上限：

| Tier | 每个规格的 frontage placement 请求上限 | 每村总请求上限 |
| --- | ---: | ---: |
| Isolated | 9 | 27 |
| Farmstead | 9 | 63 |
| Cluster | 18 | 198 |
| Full | 18 | 270 |

单次 frontage 可能提供深/浅两个实际位置；预算计数仍在已有候选矩形落入 reservation 之后，access/terrain 评估之前。原有角色槽、选择规则和 `maxBuildings * 3` 规格数量上限不变；没有增加道路、缩小建筑间距、允许重叠或降低 terrain 门槛。搜索上限不是成功数量保证，128×128 内是否能稳定容纳9–12栋仍需实机检查。

## 范围与验证

未改 56/72/96/128 reservation、道路几何、biome/terrain/水/坡度条件、Highway claim ±32 与 Rural buffer12、六资产池、农田布局/数量、怪物生成或开发命令。自然生成是本轮权威路径；`/afl rural` 继续旧建筑规划与旧目标校验，不能作为自然档位比例或V2密度的等价预览。旧已规划 Piece 保留已保存的 tier/target/lots，不会回溯重新选择。

`gradlew.bat --offline --gradle-user-home .gradle-user compileJava --console=plain`：BUILD SUCCESSFUL（现有弃用警告）。无 resource JSON 修改，未运行 `processResources`。未运行 `runClient`、`clean`、GameTest、大量测试、多 seed 回归或性能基准；未 commit/push，实机验收由用户完成。
