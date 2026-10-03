# AFL Weight System V1 Core

状态：**Core 已实现，客户端启动、联机、实机计算与生命周期验收待用户测试**。唯一一次 `compileJava --offline` 的结果记录在本次交付报告；本文不把编译等同于实机通过。首批质量与容量均为测试估值，尚未完成平衡。

## Source of Truth 与边界

服务端实际 ItemStack + 当前 immutable mass snapshot 是唯一质量真相。客户端接收本人负重结果和服务器预解析的质量表，不提交质量、不扫描 Inventory。物品 Tooltip 仅对当前悬停的已同步 ItemStack 使用同一计算器，展示单件/堆叠质量，不替代服务端玩家总负重。没有保存 currentWeight 的 NBT/capability，没有创建额外 Overflow inventory。

`PlayerStorageCapacity` 只管理插入容量，Weight 独立计算实际所有权。全部 Inventory.items 0–35 都计重，包括生存/冒险锁定的 9–35 格；主手已在其中，不重复加入。超出 comfort 不阻止拾取、插入或使用物品。

本轮不改变 Locked Inventory、伤害/RPM、换弹结算、附件交易、Dynamic Ammo、Mesh/动画/PBR、搜刮、Loot、Radiation、世界生成。武器侧仅在既有 `NativeGunActions.syncInventory` 与 `AttachmentInteractionCore.commit` 成功发布后 mark dirty。没有移动/冲刺/跳跃、Stamina、ADS、Recoil、Sway 或正式 HUD 接入。

## 文件与 API

Java 路径统一前缀：`src/main/java/com/antaurora/apofirstlight/`。

| 文件 | 职责 |
|---|---|
| `weight/ItemMassData.java` | 数据加载、校验、tag 解析、原子 snapshot/revision |
| `weight/MassResult.java` | 整件质量、breakdown、issues/quality、饱和整数运算 |
| `weight/StackMassCalculator.java` | 普通物品、NativeGunItem、显式 contents provider |
| `weight/PlayerMassSources.java` | 玩家持有来源、extra equipment provider |
| `weight/EncumbranceState.java` | ratio、连续 severity、tier、适用性 |
| `weight/PlayerWeightRuntime.java` | 临时缓存、dirty、tick END、生命周期、同步节流 |
| `weight/WeightPackets.java` | Policy / State / Data 三种 S2C packet |
| `weight/ClientWeightState.java` | 客户端只读镜像、拒绝过时结果、断线清空 |
| `weight/ClientWeightTooltip.java` | 当前悬停物品的单件/堆叠质量展示 |
| `weight/WeightCommands.java` | `/aflweight` 运维/实机诊断 |
| `network/AflNetwork.java` | 原频道注册与发送，Core 协议32；Tooltip同步扩展后为33 |
| `weapon/NativeGunActions.java` | 既有射击/换弹库存同步处新增 dirty 通知 |
| `weapon/AttachmentInteractionCore.java` | 成功提交处新增 dirty 通知 |

数据：`src/main/resources/data/apocalypse_firstlight/item_mass/core_v1.json`。Tooltip中英文语言键在 `src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json` 与 `en_us.json`，前缀 `tooltip.apocalypse_firstlight.weight.`。

`StackMassCalculator.mass(stack)` 返回**整个 stack**的 `MassResult`，调用方不得再次乘 count。`PlayerWeightRuntime.state(player)` 返回已合并计算的最新状态，初始化前可能为 null。`breakdown(player)` 返回对应来源明细。未来客户端消费者可读 `ClientWeightState.state()` / `policy()`，也必须处理尚未收到状态的 null。

## JSON、单位与重载

路径：`data/<namespace>/item_mass/*.json`。示例结构如下，实际完整定义见 core_v1.json：

```json
{
  "format_version": 1,
  "policy": {
    "fallback_unit_mass_kg": 0.25,
    "comfort_capacity_kg": 30,
    "severity_onset_ratio": 0.75,
    "severe_ratio": 2.0
  },
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

优先级：explicit item > 最高 priority 的 tag > fallback。当前 tag 为 `minecraft:logs` priority20 / 2000g 与 `forge:ingots` priority10 / 500g。同最高优先级质量冲突时警告并使用带 tag_conflict 原因的 fallback；同值时按 tag ID 确定来源，只要有一个 estimated 就仍是估值。

每次 datapack reload 从空规则重建，不做 last-good merge。每个文件原子校验；不合法文件整份拒绝并日志说明，其旧规则不会保留。同 ID 不同资源路径重复规则按文件 ID 排序，后续冲突文件拒绝；policy 也只接受一份。覆盖已有条目请使用高优先级 datapack 的**同一资源路径**覆盖文件，不能另写文件试图覆盖重复 ID。其它文件可增加新规则/tag。Native Gun 特殊结构使用 native_guns 的 definition ID；普通 item/tag 单质量不替代组装公式。

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

合计 6 组 gun 定义 + 46 个普通 item override（42 AFL、4 Vanilla）。未在列表中的 AFL 建筑/机器/容器物品等没有显式质量，例如 industrial_locker、gun_maintenance_bench、crusher；若不匹配两个 tag 则使用 fallback。完整 live Registry 的来源、缺失关键条目与 fallback 数量通过 coverage 查看，不把静态列表宣称为运行时覆盖结果。

BR51 无额外附件的满弹示例：默认20发 = 3200+200+20×24 = **3880g**；35发匣 = 3200+300+35×24 = **4340g**；50发鼓 = 3200+800+50×24 = **5200g**。每发消耗24g；同一玩家备用弹转入枪内，若没有掉落/获得物品且默认配置不变，总重应保持不变。

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

测试 comfort = 30000g，不是携带硬上限。ratio = carried / comfort。severity = clamp((ratio−0.75)/(2−0.75), 0, 1)，onset/severe 来自 policy。

Tier：ratio <0.75 LIGHT；<1 APPROACHING_COMFORT；<2 HEAVY；其余 SEVERE。tier用于未来文案，severity用于未来连续曲线。`penaltiesEnabled = !Creative && !Spectator`，只表示未来效果的适用性，**即使 true 也没有实施任何惩罚**。Creative/Spectator质量照算。

复用 AflNetwork `main`，当前协议33，PLAY_TO_CLIENT 的 Policy、State、Data。Policy同步 revision 与 fallback/comfort/onset/severe 元数据。Data在登录和datapack重载同步时发送不可变的已解析item质量表、Native Gun组件质量及相同policy/revision；tag选择已在服务器解析，不要求客户端自行加载datapack或重新判断tags。fallback物品不逐条展开。State包含 carriedMassGrams、comfortCapacityGrams、encumbranceRatio、severity、tier、penaltiesEnabled、dataRevision、quality 和 server session sequence。玩家State只发本人，不广播、不按每件物品发包。

通常变化每5ticks最多发一次，初始化/Login/模式变化/revision变化在下一次tick END强制发；respawn/换维度也强制。数据同步事件发送policy及Data，强制状态发送前再确保policy；仅这些生命周期点可能重复policy，不是每帧日志/包。Data不随每次重量变化重复发送。客户端拒绝旧revision/sequence，revision更新时清过时状态/质量表，断线清全部镜像。客户端Tooltip不会直接读取单机服务器的static质量snapshot，因此联机同样走服务器同步表。

## 物品重量 Tooltip

当前已加入中英文两行提示：普通物品“单件重量”和“堆叠总重量”；Native Gun首行使用“整枪重量（含附件及弹药）”。即使数量为1也显示两行。“堆叠总重量”仅指鼠标所指ItemStack，不是玩家全部携带重量。

例如64发9mm：单件12 g，堆叠768 g；10个苹果：单件200 g，堆叠2 kg。低于1000g使用g，其余使用kg，保留整克精度并去掉末尾零。估值/fallback/未覆盖内容均追加“（估算）”；首批数据仍全部是测试估值。

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
```

summary读服务器缓存，held即时只读主手整件组装明细，coverage每页20个AFL物品，报告来源、fallback总数和 critical_missing_explicit。关键分类为 NativeGunItem / NativeAttachment / `_round` / `_casing`。查询全部缺失项时翻页；日志中 `[AFL WEIGHT]` 只记录重载错误/冲突，不做每tick spam。

以下是**用户待执行**步骤，尚未取得 PASS。建议测试存档、开启命令，先用空库存隔离差值；不要求在正式存档清空物品。拿在cursor/合成格时由服务器控制台或另一个OP执行 `aflweight player 玩家名`，避免关闭GUI导致物品返回库存。

1. 普通stack：`/give @s minecraft:apple 1` → +200g；再给9个 → 总+2000g。拆分/合并不改变总质量。`/give @s minecraft:feather 1` 应+250g并出现 fallback:minecraft:feather。
2. Overflow：Creative把10个apple放到普通库存第10格，再切Survival；或管理员 `/item replace entity @s inventory.0 with minecraft:apple 10`（原版storage第一个格，即Inventory索引9）。2000g必须继续计入，格子仍沿用锁定规则，只能取出。
3. Armor/Offhand：`/item replace entity @s armor.chest with minecraft:iron_chestplate` → armor+8000g；`/item replace entity @s weapon.offhand with minecraft:torch 10` → offhand+1000g。主手与hotbar不重复。
4. Cursor：从背包拿起apple堆，inventory减少量等于cursor增加量；丢掉/放入箱子才减少玩家总重。打开箱子/维护台不应自动把其内容算给玩家；拾到cursor后则计入。
5. Crafting：将iron_ingot移入2×2或工作台3×3输入，总重应不变，crafting项增加；配方结果预览不可额外增加总重。取出成品后按实际新物品质量计算，不要求配方质量守恒。
6. Native Gun：`/give @s apocalypse_firstlight:br51_01`，`/aflweight held` 应receiver3200、magazine200、loaded_ammo480、total3880g（无其它配件）。P9默认满17发应884g；Blackridge默认满7发应2045g。已有NBT的空枪不得重新视为满弹。
7. 射击：Survival BR51每成功射击一次，枪内弹量与质量应分别−1 / −24g；不要把掉落弹壳特效加回玩家。Creative仍按真实gun ammo字段计量，不出现Integer.MAX_VALUE质量。
8. 换弹：`/give @s apocalypse_firstlight:762x51mm_round 64`。Survival换弹begin不提前改变质量，magIn真实transfer后枪增加量等于备用弹减少量，总重保持（无丢物/换配置）。Creative补弹可增加枪内真值质量，但没有虚构无限备用弹重量。
9. 匣/配件：给 br51_extended_magazine_35、br51_drum_magazine_50、rifle_red_dot_01、rifle_suppressor_01，用既有维护台/野外交互安装/拆除。无其它附件的20/35/50满弹枪应3880/4340/5200g；红点+250g，消音器+450g。magazine项仅一次；拆除回隐式默认匣的总量边界按上文，不误判为严格守恒。不要用动画中的辅助弹匣计量。
10. 模式：Creative → Survival的penaltiesEnabled下一tick变true，重量仍覆盖overflow；Spectator/Creative为false。所有模式都无减速/体力处罚。
11. 生命周期：分别用 keepInventory true/false测试死亡重生，比较实际保留/掉落物；跨维度、退出重进、重新开世界后重新推导，不出现旧缓存质量。测试联网本人同步不泄漏给其它玩家。
12. Reload：测试datapack覆盖同路径core_v1.json，将apple改0.3kg后`/reload`，revision增加且每个apple变300g。再从覆盖文件删除apple条目，若无其它匹配规则则250g，不保留旧300g。另测试相同最高priority的冲突tag，应警告并fallback；负数/小于克精度/溢出/非法policy文件应整份拒绝，而不是客户端崩溃。

## 未来扩展与未覆盖

`StackMassCalculator.registerContents(itemId, provider)` 只允许显式注册的、只读的单件外壳内容；外壳自重另计，depth最多8、elements最多512，达到限制标ESTIMATED。当前**没有内置 Backpack/Shulker provider**，不递归任意NBT。可识别的 Items / BlockEntityTag.Items 但无provider时标unsupported_contents，只计外壳；隐藏于其它第三方私有capability的内容无法自动识别，需要专门provider。

`PlayerMassSources.registerExtraEquipment` 给未来独立装备槽使用，必须只返回与36格/盔甲/副手等不重复的真实stack；不能再次计量同一物品。provider异常标估算问题，不让未知物品使核心崩溃。当前不占胸甲槽、不提供背包或负重支撑效果。

未来Backpack可分别贡献 StorageCapacity（9/18/27/36为总格数）、自身质量、可选comfort/support modifier；这三项不是一个数字。Traits/Training将改变角色comfort/效率，不改钢坯/枪/弹的物理质量。Stamina仅消费EncumbranceState，Handling可同时读整枪mass与玩家state，均留后续独立任务。

当前未覆盖：第三方菜单其它临时输入所有权、通用嵌套容器内容、未知capability装备、正式平衡、性能benchmark、正式HUD、实际惩罚。不可把ESTIMATED数值描述为已精确称重。
