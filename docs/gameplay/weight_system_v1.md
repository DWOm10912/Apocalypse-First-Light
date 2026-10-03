# AFL Weight System V1 Core

状态：**Core 已实现，客户端启动、联机、实机计算与生命周期验收待用户测试**。唯一一次 `compileJava --offline` 的结果记录在本次交付报告；本文不把编译等同于实机通过。首批质量与容量均为测试估值，尚未完成平衡。

2026-10-03 补数据（用户要求）：
- 全部 135 个 AFL 物品都有显式质量；
- 补了常用原版 tag 和物品；
- 液体储罐、热能发电机、化学反应器带着的液体计重，标准容器 NBT 里的物品计重；
- `/aflweight coverage` 把所有缺显式质量的 AFL 物品都算缺失；
- 新增检查工具 `tools/check-item-mass.mjs`；
- 家具堆叠上限审计。

见下文“携带质量 V1”和“堆叠上限审计”。编译通过，**没有实机验证**。舒适负重（Base Comfort Capacity）保持 30 kg（用户确认）。创造 / 旁观模式只计算不受限（`penaltiesEnabled=false`，见下文）。

2026-10-03 同日加了**负重惩罚 V1**（数值由用户定）：
- 移速、跳跃、禁止疾跑；
- 按“负担”算：穿着的盔甲 ×0.65，大件家具和机器 ×1.10 / ×1.25。

见下文“负重惩罚 V1”。编译通过，**没有实机验证**。

## Source of Truth 与边界

服务端实际 ItemStack + 当前 immutable mass snapshot 是唯一质量真相。客户端接收本人负重结果和服务器预解析的质量表，不提交质量、不扫描 Inventory。物品 Tooltip 仅对当前悬停的已同步 ItemStack 使用同一计算器，展示单件/堆叠质量，不替代服务端玩家总负重。没有保存 currentWeight 的 NBT/capability，没有创建额外 Overflow inventory。

`PlayerStorageCapacity` 只管理插入容量，Weight 独立计算实际所有权。全部 Inventory.items 0–35 都计重，包括生存/冒险锁定的 9–35 格；主手已在其中，不重复加入。超出 comfort 不阻止拾取、插入或使用物品。

本轮不改变 Locked Inventory、伤害/RPM、换弹结算、附件交易、Dynamic Ammo、Mesh/动画/PBR、搜刮、Loot、Radiation、世界生成。武器侧仅在既有 `NativeGunActions.syncInventory` 与 `AttachmentInteractionCore.commit` 成功发布后 mark dirty。移动、疾跑、跳跃惩罚见“负重惩罚 V1”（2026-10-03）；没有 Stamina、ADS、Recoil、Sway 或正式 HUD 接入。

## 文件与 API

Java 路径统一前缀：`src/main/java/com/antaurora/apofirstlight/`。

| 文件 | 职责 |
|---|---|
| `weight/ItemMassData.java` | 数据加载、校验、tag 解析、原子 snapshot/revision |
| `weight/MassResult.java` | 整件质量、breakdown、issues/quality、饱和整数运算 |
| `weight/StackMassCalculator.java` | 普通物品、NativeGunItem、显式 contents provider、标准容器 NBT、液体 provider |
| `weight/AflCarriedContents.java` | 注册 AFL 带液体物品的液体 provider（common setup，`ApocalypseFirstLight#commonSetup`） |
| `weight/PlayerMassSources.java` | 玩家持有来源、extra equipment provider |
| `weight/EncumbranceState.java` | ratio、连续 severity、tier、适用性 |
| `weight/PlayerWeightRuntime.java` | 临时缓存、dirty、tick END、生命周期、同步节流 |
| `weight/WeightPackets.java` | Policy / State / Data 三种 S2C packet |
| `weight/ClientWeightState.java` | 客户端只读镜像、拒绝过时结果、断线清空 |
| `weight/ClientWeightTooltip.java` | 当前悬停物品的单件/堆叠质量展示 |
| `weight/WeightCommands.java` | `/aflweight` 运维/实机诊断 |
| `weight/WeightPenalties.java` | 服务端：移速属性修饰、疾跑标记；跳跃缩放的公共方法 |
| `weight/ClientWeightPenalties.java` | 客户端：本人跳跃、疾跑判断、抵消移速带来的视野缩小 |
| `mixin/client/LocalPlayerWeightSprintMixin.java` | 超重时禁止疾跑（和饿肚子同一个检查点） |
| `network/AflNetwork.java` | 原频道注册与发送，Core 协议32；Tooltip同步扩展后为33；惩罚 V1 的 State 加字段后为 34 |
| `weapon/NativeGunActions.java` | 既有射击/换弹库存同步处新增 dirty 通知 |
| `weapon/AttachmentInteractionCore.java` | 成功提交处新增 dirty 通知 |

数据（`src/main/resources/data/apocalypse_firstlight/item_mass/`）：
- `core_v1.json`：policy、枪、弹药、配件、材料（codex 首批）；
- `afl_content_v1.json`：其余 87 个 AFL 物品；
- `vanilla_common_v1.json`：原版物品和 tag 规则。

检查工具：`node tools/check-item-mass.mjs`（见“检查工具”）。Tooltip中英文语言键在 `src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json` 与 `en_us.json`，前缀 `tooltip.apocalypse_firstlight.weight.`。

`StackMassCalculator.mass(stack)` 返回**整个 stack**的 `MassResult`，调用方不得再次乘 count。`PlayerWeightRuntime.state(player)` 返回已合并计算的最新状态，初始化前可能为 null。`breakdown(player)` 返回对应来源明细。未来客户端消费者可读 `ClientWeightState.state()` / `policy()`，也必须处理尚未收到状态的 null。

## JSON、单位与重载

路径：`data/<namespace>/item_mass/*.json`。示例结构如下，实际完整定义见 core_v1.json：

```json
{
  "format_version": 1,
  "policy": {
    "fallback_unit_mass_kg": 0.25,
    "comfort_capacity_kg": 30,
    "severity_onset_ratio": 1.0,
    "severe_ratio": 2.0,
    "penalties": {
      "armor_load_factor": 0.65,
      "curve": [
        { "load_ratio": 1.0, "speed": 1.0, "jump": 1.0 },
        { "load_ratio": 2.0, "speed": 0.3, "jump": 0.91 }
      ],
      "sprint_block_ratio": 1.3333,
      "sprint_resume_ratio": 1.3
    }
  },
  "carry_factors": [
    { "tag": "apocalypse_firstlight:carry/oversized", "factor": 1.25 }
  ],
  "items": {
    "minecraft:apple": { "unit_mass_kg": 0.2, "estimated": true }
  },
  "native_guns": {
    "apocalypse_firstlight:br51_01": {
      "receiver_mass_kg": 3.2,
      "default_magazine_empty_mass_kg": 0.2,
      "estimated": true
    }
  },
  "tag_defaults": [
    { "tag": "minecraft:logs", "priority": 20, "unit_mass_kg": 2, "estimated": true }
  ]
}
```

外部 kg，内部 `long grams`。BigDecimal 精确换算，0.012 kg = 12 g；只接受可精确表示为整克的数值（小于 1g 的精度不接受，末尾零不影响）。拒绝负数、NaN/Infinity、超出 long 的单值。fallback 与 comfort 必须大于 0。加法/乘法溢出饱和到 Long.MAX_VALUE 并标为 ESTIMATED / arithmetic_saturated，不发生回绕负值。

普通 stack = unit grams × count；empty = 0。`EXACT` 仅表示没有估值、缺失或计算问题，不是对数据现实准确度的认证。首批所有显式质量和 tag 都设 estimated=true，所以持有这些物品通常为 ESTIMATED；issues 分别显示 `test_estimate:*`、`fallback:*`、`inactive_attachment:*` 或其它异常来源。未知物品统一 250g/件测试 fallback，不静默变成 0。默认弹匣等明确允许 0 的组件仍可定义 0。

优先级：explicit item > 最高 priority 的 tag > fallback。同最高优先级质量冲突时警告并使用带 tag_conflict 原因的 fallback；同值时按 tag ID 确定来源，只要有一个 estimated 就仍是估值。

tag 优先级分层（2026-10-03）：
- 5：形状大类（台阶、楼梯、门、床、船、`forge:storage_blocks`、`forge:ores` 等）；
- 10：`forge:ingots` 500 g（core_v1）；
- 12：`forge:dyes`，比宝石低，青金石按宝石算；
- 15：材料（石头、泥土、玻璃、羊毛、工具、盔甲等）；
- 20：`minecraft:logs` 2000 g（core_v1）；
- 25：具体子类（木台阶、木楼梯、运输箱船、各种储存方块）。

新规则要放进不会和别的规则同层冲突的位置；`tools/check-item-mass.mjs` 会查出同层冲突。

每次 datapack reload 从空规则重建，不做 last-good merge。每个文件原子校验；不合法文件整份拒绝并日志说明，其旧规则不会保留。注意：`items` 里只要有一个没注册的物品 ID（拼错、原版没有、mod 没装），**整个文件**都会被拒绝；改数据后先跑 `tools/check-item-mass.mjs`。同 ID 不同资源路径重复规则按文件 ID 排序，后续冲突文件拒绝；policy 也只接受一份。覆盖已有条目请使用高优先级 datapack 的**同一资源路径**覆盖文件，不能另写文件试图覆盖重复 ID。其它文件可增加新规则/tag。Native Gun 特殊结构使用 native_guns 的 definition ID；普通 item/tag 单质量不替代组装公式。

`OnDatapackSyncEvent` 在服务端 tag 绑定后预解析各 Item 的 tag 选择，发布不可变 snapshot 并增加 revision，再使玩家缓存失效。每件物品计算不读 JSON，也不重复遍历 tags。初始未发布时使用标为估值的统一 fallback。删除独有规则后应回到剩余 tag/fallback；禁用覆盖包会恢复下层原包规则，这与 last-good 残留不同。

## Native Gun 组装质量

适配 `NativeGunItem`，不限定 ConfiguredNativeGunItem，也不接 TaCZ。

```text
assembled = receiver
          + stored non-magazine attachments
          + effective magazine empty mass
          + actual loaded count × ammo unit mass
          + inactive stored magazine mass (only when present)
```

SIGHT/MUZZLE 按 stored ItemStack 实际持有质量；不兼容但仍存于枪内的附件继续计重，并输出 inactive_attachment。不会使无效附件获得容量或战斗效果。

有效 MAGAZINE 的物品质量只在 magazine 项计算一次，不进入普通附件项；没有有效 MAGAZINE 时取枪定义默认空匣质量。若有失效但仍 stored 的弹匣，它额外算作实际持有物，而有效配置仍使用隐式默认匣。

默认弹匣当前没有可交易 ItemStack。拆除扩容后恢复默认匣的质量按最终配置推导，**V1 不宣称默认弹匣实体质量严格守恒**，没有改附件交易来强行实体化默认匣。Silverwood 双管枪默认 magazine 组件设为 0，壳体整体记在 receiver。

枪内弹量只读 `NativeGunAmmo.read(stack, definition)`；缺失 NBT 按既有语义视为当前容量，已有零弹量保持零。没有额外 chamber +1。备用弹仅统计实际库存 Ammo ItemStack，一件 = 一发；不读取 reserve() 或 HUD ∞。Creative 射击/补弹如何改变枪内真值仍由现有 Runtime 决定。

## 首批数据覆盖（全部测试估值）

下表为 kg；AFL ID 省略 `apocalypse_firstlight:`。没有引用已退休 sheet/换色 ingot 等 Registry。

| Native Gun | receiver | default magazine |
|---|---:|---:|
| p9_01 | 0.600 | 0.080 |
| br51_01 | 3.200 | 0.200 |
| hr55 | 4.000 | 0.250 |
| silverwood_12 | 3.000 | 0 |
| blackridge_50 | 1.700 | 0.100 |
| cat | 2.000 | 0.150 |

| 弹药前缀 | `_round` | `_casing` |
|---|---:|---:|
| 9x19mm | 0.012 | 0.004 |
| 762x51mm | 0.024 | 0.012 |
| 12_7x55mm | 0.080 | 0.022 |
| 12_gauge | 0.045 | 0.010 |
| 50_ae | 0.035 | 0.012 |

| 附件 | kg |
|---|---:|
| pistol_red_dot / rifle_red_dot_01 | 0.100 / 0.250 |
| p9_01_extended_magazine | 0.120 |
| br51_extended_magazine_35 / br51_drum_magazine_50 | 0.300 / 0.800 |
| pistol_suppressor_01 / rifle_suppressor_01 / heavy_suppressor_01 | 0.250 / 0.450 / 0.700 |

21 种新材料：steel_billet 1；lead_brick 2；tungsten_filament 0.05；silver_scrap、cemented_carbide_blank、steel_scrap 各 0.25；plastic_scrap、plastic_pellets 各 0.1；bauxite、alumina、galena、sphalerite、cassiterite、pentlandite、electrolytic_nickel、wolframite、tungsten_oxide、tungsten_powder、spodumene_concentrate、lithium_carbonate、tungsten_carbide_powder 各 0.5。

其它 AFL 代表物品：simple_hearing_protection 0.3、crowbar 1.2、energy_battery 1。Vanilla 代表物品：minecraft:apple 0.2、iron_ingot 0.5、iron_chestplate 8、torch 0.1。

以上是 core_v1.json：6 组 gun 定义 + 46 个普通 item override（42 AFL、4 Vanilla）。其余 87 个 AFL 物品见下文“携带质量 V1”（2026-10-03 之前它们没有规则，全部是 250 g fallback）。完整 live Registry 的来源与缺失数量通过 coverage 查看，不把静态列表宣称为运行时覆盖结果。

BR51 无额外附件的满弹示例：默认20发 = 3200+200+20×24 = **3880g**；35发匣 = 3200+300+35×24 = **4340g**；50发鼓 = 3200+800+50×24 = **5200g**。每发消耗24g；同一玩家备用弹转入枪内，若没有掉落/获得物品且默认配置不变，总重应保持不变。

## 携带质量 V1（2026-10-03，测试估值）

数值由 Claude 按用户“按你觉得合理的来填”定，全部 `estimated: true`，没有实机平衡。

原则：
- 拿在手里的小东西接近真实质量。
- 整格方块和家具不按真实质量（一格混凝土真实约 2.4 吨），而是“搬一份”的游戏质量，按舒适负重 30 kg 压缩：
  - 搬一件大家具或机器（35–50 kg）就到 HEAVY；
  - 中型家具 10–20 kg；
  - 一组建材很重：石头类 2.5 kg/个，一组 64 个就是 160 kg。
- 台阶 = 母方块的一半，楼梯 = 3/4。
- 合成前后尽量不变重：
  - 铅屏蔽砖 8 kg = 4 块铅砖；
  - 钢筋混凝土 3 kg ≈ 粉碎平均产出（4 块碎混凝土 × 0.5 + 4 块废钢 × 0.25）；
  - 原版储存方块 = 9 个材料。
- 液体：一桶（1000 mB）按 10 L 搬，液体质量 = 满桶 − 空桶（见“携带容器内容”）。

AFL（`afl_content_v1.json`，kg，省略 `apocalypse_firstlight:`）：

| 类别 | 质量 |
|---|---|
| 机器（不含液体） | crusher、industrial_furnace 50；alloy_furnace、chemical_reactor 45；compressor、thermal_generator 40；energy_cell 35；fluid_tank 30；charging_station 20 |
| 大件家具 | vending_machine 45；commercial_dumpster（4 色）、commercial_glass_double_door、precision_fabrication_station 40；beverage_cooler、gun_maintenance_bench 35；chest_freezer、lead_chest、office_multifunction_printer 30；industrial_locker、tall_filing_cabinet、modern_office_desk 25 |
| 中型家具 | retail_shelf_single 20；commercial_flushometer_toilet 18；water_dispenser 15；industrial_electrical_box、modern_office_chair、low_filing_cabinet、restroom_partition、commercial_wall_mounted_sink 12；metal_trash_can、office_cubicle_partition、restroom_stall_door 10 |
| 小件 | cash_register 6；office_computer_station 5；modern_lcd_monitor 4；office_keyboard 0.8；office_mouse 0.1 |
| 钢结构 | steel_door 15；steel_block 8（台阶 4、楼梯 6）；steel_beam 6；steel_plate 4（台阶 2、楼梯 3）；steel_brace 4；steel_grate、steel_railing 3；steel_cable 1 |
| 建材与地形 | lead_shielding_bricks 8；reinforced_concrete 3（台阶 1.5、楼梯 2.25）；asphalt、fused_ground 2.5；fallout_soil、scorched_soil 1.5 |
| 矿石方块 | galena_ore、wolframite_ore 3；其余 5 种 2.5 |
| 杨木 | 原木、去皮原木、木头、去皮木头 2；木板 0.5；楼梯 0.375；台阶 0.25；门 1；活板门 1.5；树叶、树苗 0.1 |
| 管线、灯、路面 | fluid_pipe 2；industrial_utility_light 3；power_cable 0.5；三种路面标线 0.2 |
| 其它物品 | industrial_waste_bucket 13（空桶 1 + 废液 12）；concrete_rubble 0.5；geiger_counter 0.5 |

原版（`vanilla_common_v1.json`）：
- 92 条 tag 规则、342 个显式物品。
- 工具统计：1238 个原版物品中，显式 346 个（含 core_v1 的 4 个），tag 覆盖 722 个，仍是 250 g 的 170 个。
- 剩下的 170 个主要是刷怪蛋、命令方块等技术方块、末地物品、潜影盒（玩家去不了末地，用户确认不管）、珊瑚、幽匿方块。
- 典型值（kg）：
  - 桶：空桶 1，水桶、奶桶 11，岩浆桶 21；
  - 方块：石头类 2.5，泥土、沙、砾石 1.5，木板 0.5，玻璃 1；
  - 铁类储存方块 4.5；箱子、木桶 8；熔炉 20；
  - 工具：剑 1.5，镐 2.5；
  - 盔甲：头 2.5、胸 8、腿 6、脚 2；皮甲单独更轻。
- 杨木的物品 tag 原来缺失（只加了方块 tag），所以 core_v1 的原木规则对它无效。2026-10-03 在 `data/minecraft/tags/items/` 补了 `logs`、`logs_that_burn`、`leaves`、`saplings`，内容和方块 tag 一样。杨木现在都有显式质量，补 tag 主要让原版配方和其它 tag 规则认得它。

## 携带容器内容（2026-10-03）

一件物品的质量 = 外壳 + 下面这些内容（都受深度 8 / 512 件限制）：

1. **注册了 contents provider 的**：provider 给出的物品（原有接口，目前没有注册任何 provider）。
2. **没注册 provider、但 NBT 里有标准容器列表的**：
   - 读 `BlockEntityTag.Items`（方块物品）或 `Items`，按原版 `ContainerHelper` 格式逐个计重，breakdown 记为 `contents`；
   - 别的格式仍只算外壳，并标 `unsupported_contents`；
   - 目前没有 AFL 方块在拆掉后保留物品库存（拆掉时物品都掉出来），这条主要接住创造模式 Ctrl+中键复制的带内容箱子和别的 mod 的容器。潜影盒也会被算进去，但没有专门处理。
3. **注册了液体 provider 的**：加上液体质量，breakdown 记为 `fluid`。
   - 每 1000 mB 的质量 = 这种液体的满桶 − 空桶（`minecraft:bucket` 1 kg）。所以把几桶水倒进储罐再拆下来，总重不变。
   - 每 1000 mB：水 10 kg，岩浆 20 kg，工业废液 12 kg。
   - 没有桶、或桶没有定价的液体按每 1000 mB 10 kg 算，标 `fallback:fluid:<id>`。
   - 已注册的物品（`weight/AflCarriedContents`）：
     - fluid_tank：`BlockEntityTag.Fluid`，最多 20,000 mB（满水 200 kg）；
     - thermal_generator：`BlockEntityTag.LiquidTank`，最多 4,000 mB 岩浆（80 kg）；
     - chemical_reactor：`BlockEntityTag.InputTank` 和 `WasteTank`，各最多 8,000 mB。
   - 储存的能量不计重。

## 堆叠上限审计（2026-10-03）

规则：
- 多格，或 ≥ 20 kg 的家具：1；
- 单格 5–20 kg 的家具：4；
- 5 kg 以下的桌面小件：16；
- 建材、门、灯、线缆、管道：保持 64；
- 机器：原来就是 1。

| 堆叠 | 物品 |
|---|---|
| 1（本次改） | industrial_locker、retail_shelf_single、water_dispenser、commercial_dumpster ×4、commercial_glass_double_door、beverage_cooler、vending_machine、chest_freezer、modern_office_desk、commercial_wall_mounted_sink、tall_filing_cabinet、office_multifunction_printer、lead_chest、gun_maintenance_bench、precision_fabrication_station |
| 4（本次改） | industrial_electrical_box、cash_register、metal_trash_can、modern_office_chair、office_computer_station、office_cubicle_partition、restroom_partition、restroom_stall_door、commercial_flushometer_toilet、low_filing_cabinet |
| 16（本次改） | modern_lcd_monitor、office_keyboard、office_mouse |
| 1（原来就是） | 9 种机器（thermal_generator、energy_cell、charging_station、crusher、industrial_furnace、alloy_furnace、compressor、chemical_reactor、fluid_tank）；energy_battery、geiger_counter、industrial_waste_bucket；枪和配件 |
| 64（不变） | 建材、矿石、杨木全套、steel_door、industrial_utility_light、power_cable、fluid_pipe、路面标线、材料、弹药 |

改动在 `registry/AflItems.java` 的 `Item.Properties().stacksTo(n)`。旧存档里已经超过新上限的堆叠，按原版逻辑读档时不会被拆开，只是不能再往上叠（没有实测）。

## 负重惩罚 V1（2026-10-03）

数值由用户定，没有实机验证。曲线、盔甲系数和疾跑阈值写在 `core_v1.json` 的 `policy.penalties` 里，`/reload` 就能调。

### 负担（load）

惩罚不按物理质量，而按负担算：每一堆物品的负担 = 它的质量 × 放的位置的系数 × 物品自己的搬运系数。

| 位置 | 系数 |
|---|---|
| 背包 36 格、副手、鼠标上、合成格 | 1.00 |
| 穿在身上的盔甲（4 个盔甲槽） | 0.65（`policy.penalties.armor_load_factor`） |
| 以后的专业背包 | 约 0.90，注册时给（还没有背包） |
| 车辆货物 | 不算玩家负担（还没有车辆） |

物品搬运系数（`afl_content_v1.json` 的 `carry_factors`，按物品 tag，取最大的那个）：
- `apocalypse_firstlight:carry/oversized` ×1.25：多格或很重的家具和机器，共 26 种：堆叠改为 1 的 18 种大件家具（含 lead_chest），加上除 charging_station 以外的 8 种机器；
- `apocalypse_firstlight:carry/bulky` ×1.10：单格中型家具和 charging_station，共 9 种；
- 其它物品 ×1.00。

Tooltip 显示的是物理质量，不乘系数。`/aflweight` 同时给出质量（mass）和负担（load）；`breakdown` 列出每个来源的负担。

### 曲线

ratio = 负担 / 舒适负重。点之间线性，60 kg 以上不再加重：

| 负担（30 kg 舒适负重时） | ratio | 移速 | 跳跃速度 | 跳跃高度（约） | 疾跑 |
|---|---:|---:|---:|---:|---|
| ≤ 30 kg | 1.0 | 100% | 1.00 | 1.25 格 | 可以 |
| 35 kg | 1.1667 | 85% | 1.00 | 1.25 格 | 可以 |
| 40 kg | 1.3333 | 70% | 1.00 | 1.25 格 | **禁止** |
| 50 kg | 1.6667 | 50% | 0.94 | 1.13 格（90%） | 禁止 |
| ≥ 60 kg | 2.0 | 30% | 0.91 | 1.07 格（85%） | 禁止 |

- 跳跃按用户选的方案 B：任何负担下都还能跳上一格（原版起跳速度 0.42，每 tick 先减 0.08 再乘 0.98，按这个算出的最高点）。曲线里写的是起跳速度倍率，不是高度。
- 疾跑：负担到 40 kg 就禁止，降到 39 kg（ratio 1.3）以下才恢复，防止在 40 kg 附近捡一支箭就来回开关。
- 移速 30% 和原版潜行一样慢；潜行会再乘一次（大约 9%）。
- 骑乘时不禁疾跑，交给坐骑。

### 实现

- **移速**：服务端给 `MOVEMENT_SPEED` 加一个临时修饰（`WeightPenalties.SPEED_MODIFIER`，MULTIPLY_TOTAL，数值 = 移速倍率 − 1），随状态重算更新。它不写进存档，属性会自动同步给客户端。重生、换维度、登录时强制重算，会重新加上。
- **视野**：原版会按移速缩小视野（和缓慢药水一样）。`ClientWeightPenalties#fov` 把这个修饰带来的那部分除掉，所以负重不会让镜头拉近；飞行、疾跑、拉弓的视野变化照旧，用望远镜时不处理。
- **疾跑**：客户端 `LocalPlayerWeightSprintMixin` 让 `LocalPlayer#hasEnoughFoodToStartSprinting` 返回 false，和饿肚子禁疾跑走同一个检查点。原版在开始疾跑前和疾跑中每 tick 都会检查它，所以正在跑也会停下。服务端每 tick 如果还收到疾跑标记就清掉。
- **跳跃**：玩家移动在客户端算，所以本人的跳跃在客户端 `LivingJumpEvent` 里把向上速度乘倍率，用的是服务器发来的最新状态。
- 状态最多每 5 tick 同步一次，物品变化后大约 0.25 秒内生效。

### 验收（待用户执行）

用测试存档，Survival 模式，先清空库存：
1. 用铁锭（0.5 kg / 个）调负担，看 `/aflweight`。70 个是 35 kg，80 个 40 kg，100 个 50 kg，120 个 60 kg：
   - 35 kg 移速约 85%，40 kg 不能疾跑；
   - 50 kg 移速约 50%，还能跳上一格；
   - 60 kg 以上移速约 30%，跳上一格很勉强但能上去。
2. 视野不应随负重变窄。
3. 穿全套铁甲（18.5 kg），`/aflweight breakdown` 的 `load armor` 应为 12025 g（× 0.65）。
4. 拿一台售货机（45 kg），负担应为 56.25 kg（× 1.25），tier HAULING。
5. 负担在 40 kg 上下：到 40 kg 禁止疾跑，降到 39 kg 以下才恢复。
6. 切到 Creative / Spectator 后惩罚立刻消失，能疾跑；切回 Survival 惩罚恢复。
7. 死亡重生、换维度、退出重进后，惩罚按新状态，不残留旧的移速修饰。

## 玩家来源、Cursor 与 Crafting

来源分别输出 inventory / armor / offhand / cursor / crafting / equipment:<id>：

- Inventory.items 全36格、armor4格、offhand；不重复主手。
- 当前 containerMenu.getCarried() 鼠标 stack。
- inventoryMenu 的2×2实际输入槽1–4；当前 CraftingMenu 的3×3实际输入槽1–9。
- 未来注册的、与上述来源互斥的 extra equipment provider。

结果预览槽0不计重。没有遍历整个菜单 slot 列表累计，因此不重复菜单中的玩家背包镜像，也不计 Chest、机器、维护台、世界容器、他人库存、远端 Ender Chest 内容或掉落物。动画 Mesh、Dynamic Ammo 与抛壳特效不在计重来源里。

未知第三方 Menu 不猜测输入所有权；只保证它引用的玩家36格、盔甲、副手和当前cursor。当前只明确适配上述两种 Vanilla crafting 输入。

## 缓存、更新与生命周期

两层缓存：immutable mass data；每个 ServerPlayer 的临时重量缓存（latest state/breakdown、dirty、lastVerification、lastSent/lastSync、revision）。不持久化。

每服务器 tick 做轻量 Inventory.getTimesChanged、单个cursor拷贝比较、menu切换及模式/revision比较；菜单 Slot listener 只关注本玩家 Inventory 或已知 crafting 输入。点击、Shift、装备/副手、合成经这些通知失效；直接修改 stack NBT 等漏通知由**每10ticks**完整核对兜底。武器射击/换弹已存在的库存同步点与附件成功提交另有明确 dirty hook。

所有玩家重量重算合并在 ServerTick END（LOWEST），每玩家每tick最多一次，不因每个物品重复更新。Debug held 仅按需读一件物品，不改玩家缓存。没有复杂增量账本，没有每tick全量扫描。第三方不发通知的变化存在最多10ticks兜底延迟（以正常tick进度计）。

Login、Respawn、Clone、Dimension Change、Death 标记强制；clone清旧玩家缓存，按新玩家真实物品重算，不复制旧质量。模式变化每tick检查并强制重算/同步。死亡/keepInventory由原逻辑决定实际物品；最终清理完成后失效/兜底复核。Logout拆菜单listener并清缓存；ServerStopped清玩家缓存、sequence与mass data，下一次世界重开重新加载。没有修改辐射capability生命周期。

## EncumbranceState 与网络

comfort = 30000g，不是携带硬上限。2026-10-03 起 ratio = **负担（load）** / comfort，不再是物理质量 / comfort（见“负重惩罚 V1”）。severity = clamp((ratio−onset)/(severe−onset), 0, 1)，onset 1.0、severe 2.0 来自 policy。onset 原来是 0.75，校验原来要求 onset < 1；现在只要求 0 ≤ onset < severe。

Tier（2026-10-03 改为 5 档，按 onset→severe 三等分；30 kg 舒适负重时）：
- LIGHT：≤ 30 kg；
- BURDENED：30–40 kg；
- HEAVY：40–50 kg；
- HAULING：50–60 kg；
- EXTREME：≥ 60 kg。

原来的 LIGHT / APPROACHING_COMFORT / HEAVY / SEVERE 已删除。

`penaltiesEnabled = !Creative && !Spectator`：为 false 时质量和负担照算，但移速、跳跃倍率都是 1，也不禁止疾跑。

复用 AflNetwork `main`，当前协议33，PLAY_TO_CLIENT 的 Policy、State、Data。Policy同步 revision 与 fallback/comfort/onset/severe 元数据。Data在登录和datapack重载同步时发送不可变的已解析item质量表、Native Gun组件质量及相同policy/revision；tag选择已在服务器解析，不要求客户端自行加载datapack或重新判断tags。fallback物品不逐条展开。2026-10-03 补数据后，表里约 1,200 项（估算约 70 KB），离自定义包 1 MB 上限还很远。State包含 carriedMassGrams、loadGrams、comfortCapacityGrams、encumbranceRatio、severity、tier、penaltiesEnabled、speedMultiplier、jumpMultiplier、sprintBlocked、dataRevision、quality 和 server session sequence（2026-10-03 加了负担、两个倍率和 sprintBlocked，协议 34）。惩罚曲线和 carry 系数只在服务端，客户端只拿结果倍率。玩家State只发本人，不广播、不按每件物品发包。

通常变化每5ticks最多发一次，初始化/Login/模式变化/revision变化在下一次tick END强制发；respawn/换维度也强制。数据同步事件发送policy及Data，强制状态发送前再确保policy；仅这些生命周期点可能重复policy，不是每帧日志/包。Data不随每次重量变化重复发送。客户端拒绝旧revision/sequence，revision更新时清过时状态/质量表，断线清全部镜像。客户端Tooltip不会直接读取单机服务器的static质量snapshot，因此联机同样走服务器同步表。

## 物品重量 Tooltip

只显示一行（2026-10-03 按用户要求改，原来是“单件重量”和“堆叠总重量”两行）：
- 数量为 1：`重量：45 kg`；
- 数量大于 1：`重量：12 g × 64 = 768 g`，即单件 × 数量 = 这一堆的总重；
- Native Gun：`整枪重量（含附件及弹药）：3.88 kg`。

总重只指鼠标所指ItemStack，不是玩家全部携带重量。例如：64发9mm 是 `12 g × 64 = 768 g`；10个苹果是 `200 g × 10 = 2 kg`。低于1000g使用g，其余使用kg，保留整克精度并去掉末尾零。

不再追加“（估算）”：数据全部是测试估值，每个物品都带这个后缀，没有区分作用。估值、fallback 等数据质量信息只在 `/aflweight held` / `coverage` 里看。高级提示框（F3+H）下，只有用到 fallback 的物品多一行深灰色“没有质量数据，按默认值计算”。

语言键：`weight.single`、`weight.stack`、`weight.assembled`、`weight.fallback`、`weight.pending`，原来的 `weight.unit`、`weight.estimated` 已删除。

只计算当前悬停stack，不扫描客户端背包，不发送悬停请求。复用 `StackMassCalculator.mass(stack, ClientWeightState.data())`；枪械使用当前客户端已同步的附件/弹药NBT及NativeGun规则，随后随原库存同步更新。尚未收到服务器质量表时只提示“等待服务器数据”，不编造0重量或使用旧表。客户端没有世界时不显示该提示。没有新增正式Weight HUD。

Tooltip实机仍待验证：检查64发9mm、1/10个苹果、安装配件/射击前后枪重与`/aflweight held`一致；在远程服务器登录、`/reload`修改/删除质量规则后查看更新，重连另一世界应清旧表。确认原有弹药、辐射及Locked Overflow提示保持。

## Debug 与手动验收

需权限2；命令无重量写入功能：

```text
/aflweight
/aflweight breakdown
/aflweight held
/aflweight player <玩家名>
/aflweight player <玩家名> held
/aflweight coverage
/aflweight coverage <页码>
/aflweight coverage missing
/aflweight coverage missing <页码>
```

summary读服务器缓存，held即时只读主手整件组装明细（含 `contents` / `fluid` 项）。

coverage 每页20个AFL物品，报告来源：
- 2026-10-03 起，**每个 AFL 物品都必须有自己的规则**（`items` 或 `native_guns`）。只靠 tag 或 fallback 的都算缺失，行尾标 `MISSING_EXPLICIT`。
- 表头报告 `missing_explicit=<总数> (fallback=<n> tag_only=<n>)`，取代原来只算枪、配件、弹药、弹壳的 critical_missing_explicit。
- `coverage missing` 只列缺失项。

日志中 `[AFL WEIGHT]` 只记录重载错误/冲突，不做每tick spam。

### 检查工具

`node tools/check-item-mass.mjs` 只读，不写文件。不进游戏就能查出运行时会出的问题：
- 有没有 AFL 物品缺显式质量（从 `registry/AflItems.java` 读注册表，枪查 `native_guns`）；
- 哪个文件会被整份拒绝：物品 ID 不存在、不是整克、负数、和前面文件重复、第二个 policy；
- 原版物品上同层 tag 冲突（运行时会变成 fallback）、引用了不存在的 tag。

原版物品列表和原版 / Forge 的物品 tag 从 Gradle 缓存里的 `client-extra.jar` 和 Forge universal jar 读，版本取自 `gradle.properties`，也可以用 `--client-jar`、`--forge-jar` 指定。找不到 jar 时跳过原版检查并警告。

选项：
- `--list`：AFL 物品按质量从重到轻列出；
- `--vanilla-fallback`：列出仍是 fallback 的原版物品。

有错误时退出码为 1。2026-10-03 运行结果：AFL 135/135 显式，原版显式 346、tag 722、fallback 170，PASS。

以下是**用户待执行**步骤，尚未取得 PASS。建议测试存档、开启命令，先用空库存隔离差值；不要求在正式存档清空物品。拿在cursor/合成格时由服务器控制台或另一个OP执行 `aflweight player 玩家名`，避免关闭GUI导致物品返回库存。

1. 普通stack：`/give @s minecraft:apple 1` → +200g；再给9个 → 总+2000g。拆分/合并不改变总质量。`/give @s minecraft:feather 1` 应+10g（来源 `tag:forge:feathers`；2026-10-03 之前是 250 g fallback）；`/give @s minecraft:sculk 1` 应+250g并出现 fallback:minecraft:sculk。
2. Overflow：Creative把10个apple放到普通库存第10格，再切Survival；或管理员 `/item replace entity @s inventory.0 with minecraft:apple 10`（原版storage第一个格，即Inventory索引9）。2000g必须继续计入，格子仍沿用锁定规则，只能取出。
3. Armor/Offhand：`/item replace entity @s armor.chest with minecraft:iron_chestplate` → armor+8000g；`/item replace entity @s weapon.offhand with minecraft:torch 10` → offhand+1000g。主手与hotbar不重复。
4. Cursor：从背包拿起apple堆，inventory减少量等于cursor增加量；丢掉/放入箱子才减少玩家总重。打开箱子/维护台不应自动把其内容算给玩家；拾到cursor后则计入。
5. Crafting：将iron_ingot移入2×2或工作台3×3输入，总重应不变，crafting项增加；配方结果预览不可额外增加总重。取出成品后按实际新物品质量计算，不要求配方质量守恒。
6. Native Gun：`/give @s apocalypse_firstlight:br51_01`，`/aflweight held` 应receiver3200、magazine200、loaded_ammo480、total3880g（无其它配件）。P9默认满17发应884g；Blackridge默认满7发应2045g。已有NBT的空枪不得重新视为满弹。
7. 射击：Survival BR51每成功射击一次，枪内弹量与质量应分别−1 / −24g；不要把掉落弹壳特效加回玩家。Creative仍按真实gun ammo字段计量，不出现Integer.MAX_VALUE质量。
8. 换弹：`/give @s apocalypse_firstlight:762x51mm_round 64`。Survival换弹begin不提前改变质量，magIn真实transfer后枪增加量等于备用弹减少量，总重保持（无丢物/换配置）。Creative补弹可增加枪内真值质量，但没有虚构无限备用弹重量。
9. 匣/配件：给 br51_extended_magazine_35、br51_drum_magazine_50、rifle_red_dot_01、rifle_suppressor_01，用既有维护台/野外交互安装/拆除。无其它附件的20/35/50满弹枪应3880/4340/5200g；红点+250g，消音器+450g。magazine项仅一次；拆除回隐式默认匣的总量边界按上文，不误判为严格守恒。不要用动画中的辅助弹匣计量。
10. 模式：Creative → Survival的penaltiesEnabled下一tick变true，重量仍覆盖overflow；Spectator/Creative为false，这时没有任何移动惩罚。
11. 生命周期：分别用 keepInventory true/false测试死亡重生，比较实际保留/掉落物；跨维度、退出重进、重新开世界后重新推导，不出现旧缓存质量。测试联网本人同步不泄漏给其它玩家。
12. Reload：测试datapack覆盖同路径core_v1.json，将apple改0.3kg后`/reload`，revision增加且每个apple变300g。再从覆盖文件删除apple条目，若无其它匹配规则则250g，不保留旧300g。另测试相同最高priority的冲突tag，应警告并fallback；负数/小于克精度/溢出/非法policy文件应整份拒绝，而不是客户端崩溃。

2026-10-03 新增（同样待用户执行）：

13. 家具和堆叠：
    - `/give @s apocalypse_firstlight:vending_machine 2` 应得到两格各 1 台，每台 45 kg，质量约 90 kg，负担 112.5 kg（×1.25），tier EXTREME；
    - `office_mouse` 一格最多 16 个，`metal_trash_can` 最多 4 个；
    - 杨木原木应是 2 kg，来源 `item:`。
14. 液体：
    - 放一个 fluid_tank，倒进 3 桶水，拆下拿着；
    - `/aflweight held` 应为 shell 30000 + fluid 30000 = 60 kg；
    - 倒水前拿着 3 个水桶（33 kg）和倒完拿着 3 个空桶（3 kg），差值正好等于储罐里的 30 kg。
15. 容器内容：创造模式对装了东西的箱子按 Ctrl+中键，拿到的箱子物品应是 8 kg 外壳加里面物品的重量，breakdown 有 `contents`。
16. coverage：`/aflweight coverage` 表头应为 `missing_explicit=0`；`/aflweight coverage missing` 没有行。

## 未来扩展与未覆盖

`StackMassCalculator.registerContents(itemId, provider)` 只允许显式注册的、只读的单件外壳内容；外壳自重另计，depth最多8、elements最多512，达到限制标ESTIMATED。

当前没有内置 Backpack provider。没有 provider 时：
- 标准格式的 Items / BlockEntityTag.Items 按原版格式逐个计重（2026-10-03 起，见“携带容器内容”）；
- 其它格式标 unsupported_contents，只计外壳；
- 隐藏于第三方私有 capability 的内容无法自动识别，需要专门 provider。

`registerFluidContents(itemId, provider)` 是同样规则的液体版本。

`PlayerMassSources.registerExtraEquipment` 给未来独立装备槽使用，必须只返回与36格/盔甲/副手等不重复的真实stack；不能再次计量同一物品。provider异常标估算问题，不让未知物品使核心崩溃。当前不占胸甲槽、不提供背包或负重支撑效果。

2026-10-03 起可以带负担系数注册：`registerExtraEquipment(id, loadFactor, provider)`，比如以后的专业背包约 0.90。这表示负载分布更好，不是把质量变小：物理质量照算，只是负担按 0.90 算。原来不带系数的写法按 1.0 算。

未来Backpack可分别贡献 StorageCapacity（9/18/27/36为总格数）、自身质量、可选comfort/support modifier；这三项不是一个数字。Traits/Training将改变角色comfort/效率，不改钢坯/枪/弹的物理质量。Stamina仅消费EncumbranceState，Handling可同时读整枪mass与玩家state，均留后续独立任务。

当前未覆盖：
- 体力（Stamina）消耗、正式负重 HUD；
- 背包（只留了负担系数的接口）；
- 车辆：以后的车辆货物不算进玩家负担，只算车辆自己的质量（用户 2026-10-03 定）；
- 第三方菜单其它临时输入所有权；
- 非标准格式的嵌套容器内容；
- 通过 capability 装液体的第三方物品（只算显式注册的 3 种 AFL 物品，不读通用 fluid handler，避免和桶自身质量重复计）；
- 未知capability装备；
- 正式平衡、性能benchmark。

不可把ESTIMATED数值描述为已精确称重。
