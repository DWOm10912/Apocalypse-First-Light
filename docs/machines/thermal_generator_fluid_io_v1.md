# 热力发电机 Fluid IO V1

| 项目 | 当前行为 |
|---|---|
| 方块 | `apocalypse_firstlight:thermal_generator`；沿用原 BlockEntity、GUI、配方与静态模型 |
| Tank | 4000 mB；V1 仅 `minecraft:lava` |
| 输入 | 面向正面左侧，仅 fill，25 mB/t |
| 输出 | 面向正面右侧，仅 drain，25 mB/t；排出未消耗燃料，无副产物 |
| 其他面 | 正面、背面、顶底及 null 不暴露 fluid capability；背面仍输出 FE |
| 液体能量 | 1000 mB 熔岩 = 20000 FE；沿用 16 FE/t 转换上限 |
| 燃料选择 | 当前已扣除燃料结算完再选；空闲固体优先，不并行消耗 |
| 暂停 | 满电不扣新燃料、不消耗待转换能量；出现空间后继续 |
| 持久化 | Tank、液体分数 FE、待转换能量保存至 NBT；正确工具掉落后重放保留液体能量，不复制燃料槽物品 |
| 工具 | 原钻石级镐规则不变；不正确工具不掉机器，也不返还槽内液体 |
| Jade | 非空时显示名称、mB/容量及现有流体条；空槽完全隐藏流体信息，保留 FE 与燃料物品信息 |
| JEI | `apocalypse_firstlight:thermal_generation` 单一分类；催化剂为热力发电机；枚举当前四种物品燃料及 1000 mB 熔岩 |
| 桶 | 保留燃料槽熔岩桶及空桶返还；未新增手持桶灌注交互 |
| GUI 流体条 | 左侧与化学反应器同款 12×54 外框、8×48 填充；显示真实 4000 mB 槽，悬停显示液体燃料、名称及数量/容量；右侧 FE 条和槽位不变 |

## 四向映射

| FACING | 左输入 | 右输出 | FE 背面 |
|---|---|---|---|
| NORTH | EAST | WEST | SOUTH |
| SOUTH | WEST | EAST | NORTH |
| EAST | SOUTH | NORTH | WEST |
| WEST | NORTH | SOUTH | EAST |

## 数据与复用

- 管道通用视觉防抖：一次真实输送后保持流动贴图 10 tick（正常 TPS 下 0.5 秒），短暂停流不立即切静止。只有真实输送刷新保持时间，满槽重试不续期；持续停流后恢复静止。原路径显示的 20 tick 超时清除不变，流体或路径方向变化不继承旧流动状态。此规则仅修改 `FluidPipeVisualManager` 的视觉同步，不影响储量、速率、燃烧及满槽判断。

- GUI 共用 `client/MachineFluidBarRenderer.java`，由化学反应器原渲染直接抽取；保留 `MachineGuiRenderHelper.drawFluidFill` 的液体图集/颜色与底部向上填充，以及原刻度覆盖。`ThermalGeneratorMenu` 同步数量/容量，液体身份读取客户端 BE 快照；空槽只显示外框。布局位于 `machine_layout/thermal_generator.json`，位置与化学反应器左条一致。
- GUI 补充验证：`build/thermal-fluid-bar-tests.log` 构建与 12/12 服务端回归通过；本次新增 GUI 尚未进行图形客户端视觉验收，不复用上一轮 JEI 截图充当本次验证。

- 唯一能量来源：`src/main/resources/data/apocalypse_firstlight/machine_balance/thermal_generator.json`，由 `MachineBalanceManager` 加载。煤炭/木炭 500 FE，煤炭块 5000 FE，熔岩桶 20000 FE。
- `energy/ThermalFuelDefinitions.java` 将熔岩映射到已有熔岩桶定标；运行与 JEI 液体条目共用此定义。JEI 使用现有网络包同步的服务端燃料表，不另写客户端固定 FE 表；数据包同步后刷新展示。
- 化学反应室原有的单向槽适配器抽为 `fluid/SidedTankHandler.java`；共享 `FluidPortTransferBudget`、`FluidPipeTransfer`，两端各有独立 25 mB/t 预算。模拟操作不扣预算或改槽。
- 液体按 1 mB 扣除并保留尚未生成的 FE，整数除法余数以千分之一 FE 保存；同一转换预算不重复生成能量。
- `blockentity/ThermalGeneratorBlockEntity.java` 提供 `getLiquidFuel()`（副本）、`getLiquidAmount()`、`getTankCapacity()`、`getActiveFuelSource()`、`isRunning()`。变更快照通常按 10 tick 合并，燃料源/运行状态切换立即同步；不发送整个背包。
- 后续 [Dynamic Renderer V1](thermal_generator_dynamic_renderer_v1.md) 已接入动态液位/燃料、转子与状态灯；[Particles V1](thermal_generator_particles_v1.md) 已接入运行状态门控的固体燃烧、液体活性与持续排烟，声音与形状保留。

## 验证

- 管道视觉保持补充：`build/fluid-visual-hold-tests.log` 构建及 13/13 GameTest 通过，覆盖短暂停流保持、真实流动续期、持续堵塞超时、流体切换与原清除超时，并保留原 12 项回归；本次未进行图形客户端防抖效果目测。

- 服务端 GameTest 最终 12/12：四向管道实际灌入/排出及总量守恒，容量/白名单/单向/限速/模拟/cap 生命周期，1000 mB 精确发电，互斥与满电暂停，NBT/客户端快照，钻石/下界合金镐实际拆放保留 777 mB，Jade 条件数据与 JEI 燃料数据共源，原固体燃料、化学反应室、放置形状及工具等级回归。日志：`build/thermal-fluid-final.log`。
- `build` 通过，产物 `build/libs/apocalypse_firstlight-1.0.0.jar`；测试类不进入发布 jar。
- 隔离图形客户端通过真实 JEI runtime 检查：分类、催化剂、五种燃料、五种布局构建、热更新 FE 后无旧可见条目；恢复原值后打开 JEI 页面并实际绘制。截图：`build/thermal-fluid-client/screenshots/2026-09-08_12.08.21.png`。
- 客户端使用真实 Jade Tooltip 构建器检查空槽/非空槽：非空恰好增加名称/数量行与流体条，数量为 1000/4000。此项不是手动逐朝向悬停美术验收，液位动态显示仍未实现。
- 客户端日志：`build/thermal-fluid-client-final.log`，`AFL THERMAL CLIENT PROBE = PASS`。隔离运行目录使既有 Native Gun smoke 找不到其相对源码路径并报错；本轮机器 probe 独立通过，未据此宣称枪械回归通过，也未修改该无关测试。
- 可复跑：`gradlew.bat build runGameTestServer --offline -I src/dev/thermal-generator-model-gametest.init.gradle`；图形 probe 用 `src/dev/thermal-fluid-client.init.gradle`，需要 `build/thermal-fluid-client/saves/fluid_probe` 隔离测试存档，完成后自动关闭自己启动的客户端。
