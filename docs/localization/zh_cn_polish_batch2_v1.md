# zh_cn 润色 · 批次 2（高频文本 48 条，已应用）

- **状态：** 用户 2026-10-18 整体通过，6 条按用户的写法改过（第 12 节），**已写进 master 的 zh_cn.json（48 个键）和 en_us.json（6 个键）**，没提交。格式、键、占位符检查通过；**没进游戏看**，等用户实机验收。
- **范围：** 玩家最常看到的文本：枪和配件的物品说明、弹药名称、枪械说明里的数值和单位、背包格子的提示、几个按钮和按键、Jade 状态词、枪声字幕，加上两条已确认的企业译名。
- **依据：** [zh_cn_style_guide_v1.md](zh_cn_style_guide_v1.md)（V1 已冻结）、企业译名见 [federation_companies_v1.md 第 6 节](../world/federation_companies_v1.md)。
- **机制核对：** 枪的射击模式、伤害、弹匣容量按 `src/main/resources/data/apocalypse_firstlight/native_guns/*.json` 核对过；背包锁定格按 `PlayerStorageCapacity` 核对过。都只是读数据和代码，**没有进游戏看**。

## 0. 这一批怎么写

用户 2026-10-18 的要求：**不机械执行禁用词表；不为了让中文更口语而牺牲专业性、语境或机制准确性。** 这一批按下面的先后顺序判断：

1. **机制准确。** 说明写错了机制，就是最严重的问题（MQM"致命"或"严重"）。射击模式、弹种、装弹方式要和数据一致。
2. **术语一致。** 同一个东西在名称、说明、按键、字幕、HUD 里叫法一样。
3. **专业。** 行业里有准确的词就用（制止力、运动霰弹枪、并排双管、12 号、枪声传播范围）。口语只用来替换翻译腔，不用来替换术语。
4. **格式。** 空格、单位、省略号、句末标点。

和规范第 4.3 节的旧建议不同的地方，**以本文为准**：

| 第 4.3 节 | 旧建议 | 为什么不用 |
|---|---|---|
| #19 红点瞄具 | 抬枪就能对上目标 | 夸大了效果。红点只是让瞄点更清楚，便于快速瞄准，不保证"对上" |
| #26 HR-55 | 近中距离一枪就能把人放倒 | **机制不准。** 基础伤害 26，有距离衰减，还要看护甲和部位，不能保证一枪。原文的"制止力"是真实的弹道术语，问题只是"拥有极强的"这个翻译腔 |
| #28 P9-01 | 顺手，近距离好用 | 太口语，丢了"操控灵活"这个准确的说法；"近距离交战"本来就是规范的军事用语，不用改 |
| #17、#22 扩容弹匣 | 说明里写"加长弹匣" | 物品名叫"扩容弹匣"，说明换一个词，玩家会以为是两样东西。名称和说明用同一个词 |
| #16 耳罩 | 拿手边材料凑的耳罩 | 意思没错，但比英文原文多了一层口气。这里不需要演 |

**理由分类：** 准确 = 和机制或数据不符；一致 = 同一个东西叫法不同；术语 = 行业说法；语域 = 翻译腔、广告腔、系统腔；格式 = 空格、单位、标点。

## 1. 企业译名（2 条）

用户 2026-10-18 确认的译名。世界里的字样不变：价格牌上仍然印 PRAIRIE，车尾仍然是 HALSTEAD。

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 1 | `block.*.price_sign` | PRAIRIE Price Sign | PRAIRIE 价格牌 | 普雷里价格牌 | 一致：方块名是中文界面，按规范第 5.9 节用中文译名 |
| 2 | `entity.*.pickup_t15` | Halstead Tamarack T15 | Halstead Tamarack T15 | 霍尔斯特德 Tamarack T15 | 一致：厂商用中文译名，车型名 Tamarack T15 不译（规范第 2.6 节） |

## 2. 枪的说明（5 条）

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 3 | `tooltip.*.hr55.description` | A heavy semi-automatic battle rifle chambered in 12.7×55mm, delivering formidable stopping power at close and medium range. | 一款使用12.7×55毫米弹药的重型半自动战斗步枪，在近中距离拥有极强的制止力 | 12.7×55mm 重型战斗步枪，可切换半自动和全自动，近中距离制止力出色 | **准确（严重）：** `hr55.json` 的射击模式是半自动 + 全自动，全自动有单独调过的射速和后坐力，不是误留，"半自动步枪"的说法不对。**术语：** 保留"制止力"（用户 2026-10-18 定），只删"拥有极强的"。**EN 要一起改**：A heavy select-fire battle rifle chambered in 12.7×55mm, with formidable stopping power at close and medium range. |
| 4 | `tooltip.*.silverwood_12.description` | A modern over-under sporting shotgun with a two-chamber break action and 12 Gauge buckshot. | 一款现代商用上下双管运动霰弹枪，采用双膛折枪式结构并发射12号鹿弹 | 民用并排双管运动霰弹枪，折开枪管装弹，发射 12 号鹿弹 | **准确（严重）：** 枪已经重做成左右并列双管（`docs/native_guns/silverwood_12_v3_sxs.md`），"上下双管"不对；"商用"是 sporting 的误译。**术语：** "运动霰弹枪""并排双管""折开枪管装弹"都是行业说法，比"双膛折枪式结构"清楚。**EN 要一起改**：A modern side-by-side sporting shotgun with a break action, firing 12 Gauge buckshot. |
| 5 | `tooltip.*.br51_01.description` | A select-fire battle rifle chambered in 7.62×51mm, offering strong power and range at the cost of heavy recoil that is hard to control on full auto. | 一款使用7.62×51毫米弹药的战斗步枪，威力与射程出色，但后坐力较大，全自动难以控制 | 7.62×51mm 战斗步枪，可切换半自动和全自动，威力强劲，射程出色，但后坐力较大，全自动时很难控制 | **准确：** 原文 select-fire 在中文里漏了。**语域：** 删"一款"。"难以控制"改成"很难控制"只是顺口，意思不变 |
| 6 | `tooltip.*.p9_01.description` | A compact and reliable 9mm service pistol, easy to handle and well suited for close-range combat. | 一款紧凑可靠的9毫米制式手枪，操控灵活，适合近距离交战 | 紧凑可靠的 9mm 制式手枪，可切换半自动和全自动，操控灵活，适合近距离交战 | **准确：** `p9_01.json` 有全自动（射速和后坐力单独调过），说明里没提。**格式：** 口径写法统一成 9mm（规范第 2.4 节）。其余保留：原文"操控灵活""近距离交战"准确，不改。**EN 建议一起补**：A compact and reliable select-fire 9mm service pistol… |
| 7 | `tooltip.*.cat.description` | Please do not ask how it works.... | 请不要询问它的工作原理... | 别问它是怎么工作的…… | **语域：** 彩蛋，规范第 2.11 节允许幽默；"请不要询问"太像告示，反而不好笑。**格式：** `...` 改 `……` |

## 3. 配件说明（8 条）

规则：先说是什么、给哪把枪用，再说效果。效果用准确的说法（弹匣容量、持续射击能力、枪声传播范围、枪口焰），不用"可显著 / 可大幅"这类营销词。

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 8 | `tooltip.*.br51_extended_magazine_35.description` | A 35-round extended magazine designed for the BR-51, significantly increasing sustained fire capability. | 一款为BR-51设计的35发扩容弹匣，可显著提升持续交火能力 | BR-51 专用 35 发扩容弹匣，增加弹匣容量，提升持续射击能力 | **语域：** 删"一款 / 可显著"。**准确：** 先说配件实际做了什么（弹匣容量变大），再说效果（用户 2026-10-18 定的写法）；"持续交火能力"是直译。**格式：** 空格 |
| 9 | `tooltip.*.br51_drum_magazine_50.description` | A 50-round twin-drum magazine designed for the BR-51, greatly extending sustained fire at the cost of a slower empty reload. | 一款为BR-51设计的50发双鼓弹匣，大幅提升持续火力，但空仓更换更费时 | BR-51 专用 50 发双弹鼓，增加弹匣容量，提升持续射击能力，但空仓换弹更慢 | **术语：** 双弹鼓是通行叫法，和物品名"BR-51 弹鼓"对得上。代价"空仓换弹更慢"保留，这是机制 |
| 10 | `tooltip.*.p9_01_extended_magazine.description` | A 28-round extended magazine built for the P9-01: a lengthened steel magazine with a grip-extension sleeve and a reinforced floorplate, greatly improving sustained fire. | 一款为P9-01专门设计的28发扩容弹匣，加长钢制弹匣配握把延长套和加固底板，可显著提升持续交火能力 | P9-01 专用 28 发扩容弹匣，钢制加长弹匣带握把延长套和加固底板，增加弹匣容量，提升持续射击能力 | 同 #8。结构说明（握把延长套、加固底板）是这个配件的特点，保留 |
| 11 | `tooltip.*.rifle_red_dot_01.description` | A universal rifle red dot sight that provides a clear aiming point for faster target acquisition. | 一款通用步枪红点瞄具，提供清晰瞄准点，便于快速捕捉目标 | 通用步枪红点瞄具，瞄点清晰，便于快速瞄准目标 | **语域：** 删"一款 / 提供"。"便于快速瞄准目标"是用户 2026-10-18 定的写法，比"目标捕获"好懂，意思一样 |
| 12 | `tooltip.*.pistol_red_dot.description` | A compact pistol optic with a lightly tinted lens. | 一款紧凑型手枪瞄具，配有淡色镀膜镜片 | 紧凑型手枪红点瞄具，镜片带淡色镀膜 | **一致：** 物品名是"手枪微型红点瞄具"，说明里只写"瞄具"太泛。删"一款 / 配有" |
| 13 | `tooltip.*.rifle_suppressor_01.description` | A quick-detach 7.62×51mm rifle suppressor that mounts over a compatible flash hider, greatly reducing gunshot noise propagation and muzzle flash. | 一款套装在兼容消焰器上的 7.62×51mm 快拆步枪消音器，可大幅降低枪声传播范围并削弱枪口焰 | 7.62×51mm 快拆步枪消音器，套在兼容的消焰器上。枪声传播范围明显缩小，枪口焰减弱 | **语域：** 长定语拆成两句。"枪声传播范围"保留：它对应游戏里的噪声半径机制，比"传得近"准确 |
| 14 | `tooltip.*.heavy_suppressor_01.description` | A large quick-detach 12.7×55mm suppressor that mounts over a compatible muzzle brake and sleeves back over the barrel, greatly reducing gunshot noise propagation and muzzle flash. | 一款 12.7×55mm 大型快拆消音器，套装在兼容的枪口制退器上并向后包住枪管，可大幅降低枪声传播范围并削弱枪口焰 | 12.7×55mm 大型快拆消音器，套在兼容的枪口制退器上，向后包住枪管。枪声传播范围明显缩小，枪口焰减弱 | 同 #13 |
| 15 | `tooltip.*.pistol_suppressor_01.description` | A 9mm pistol muzzle device that greatly reduces gunshot noise propagation and muzzle flash. | 一款9mm手枪枪口装置，可大幅降低枪声传播范围并削弱枪口焰 | 9mm 手枪消音器。枪声传播范围明显缩小，枪口焰减弱 | **一致：** 物品名叫"9mm 手枪消音器"，说明里叫"枪口装置"像另一样东西。EN 的 muzzle device 也建议改成 suppressor |

## 4. 弹药和配件名称（12 条）

规范第 2.4 节：口径是型号，写成 `9×19mm` `7.62×51mm` `12.7×55mm` `12 号`，后面加空格再接中文。配件名已经是这个写法（`7.62×51mm 步枪消音器`），弹药名还是"毫米"，同一把枪的说明里两种写法并存。

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 16 | `item.*.9x19mm_round` | 9×19mm Pistol Round | 9×19毫米手枪弹 | 9×19mm 手枪弹 | 一致 + 格式 |
| 17 | `item.*.762x51mm_round` | 7.62×51mm Rifle Round | 7.62×51毫米步枪弹 | 7.62×51mm 步枪弹 | 同上 |
| 18 | `item.*.12_7x55mm_round` | 12.7×55mm Heavy Round | 12.7×55毫米重型弹 | 12.7×55mm 重型弹 | 同上 |
| 19 | `item.*.9x19mm_casing` | 9×19mm Casing | 9×19毫米弹壳 | 9×19mm 弹壳 | 同上 |
| 20 | `item.*.762x51mm_casing` | 7.62×51mm Casing | 7.62×51毫米弹壳 | 7.62×51mm 弹壳 | 同上 |
| 21 | `item.*.12_7x55mm_casing` | 12.7×55mm Casing | 12.7×55毫米弹壳 | 12.7×55mm 弹壳 | 同上 |
| 22 | `item.*.9x19mm_round.caliber` | 9×19mm | 9×19毫米 | 9×19mm | 同上；这一条显示在枪的"弹药类型"一行 |
| 23 | `item.*.762x51mm_round.caliber` | 7.62×51mm | 7.62×51毫米 | 7.62×51mm | 同上 |
| 24 | `item.*.12_7x55mm_round.caliber` | 12.7×55mm | 12.7×55毫米 | 12.7×55mm | 同上 |
| 25 | `item.*.12_gauge_round` | 12 Gauge Shotgun Round | 12号口径霰弹 | 12 号霰弹 | **术语：** "号"就是 gauge 的口径单位，"号口径"重复。**格式：** 空格 |
| 26 | `item.*.12_gauge_casing` | 12 Gauge Casing | 12号口径霰弹空壳 | 12 号霰弹壳 | 同上；"霰弹壳"是通行叫法，"空壳"多余 |
| 27 | `item.*.p9_01_extended_magazine` | P9-01 28-Round Extended Magazine | P9-01 28发扩容弹匣 | P9-01 28 发扩容弹匣 | **格式：** 数字和量词之间加空格（规范第 2.4 节）。"扩容弹匣"保留，和 BR-51 的那一个同名 |

## 5. 枪械说明里的数值（6 条）

每次鼠标移到枪上都会看到。

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 28 | `tooltip.*.stat.damage` | Damage | 枪械伤害 | 伤害 | 语域：这一行本来就在枪的说明里，"枪械"多余 |
| 29 | `tooltip.*.stat.fire_mode` | Fire Mode | 开火方式 | 开火模式 | **一致：** 按键"切换开火模式"、消息"开火模式：%s"、HUD 都叫"开火模式"，只有这里叫"方式" |
| 30 | `tooltip.*.value.rounds` | %s Rounds | %s发 | %s 发 | 格式：`%s` 代入数字，两边加空格（规范第 2.4 节） |
| 31 | `tooltip.*.value.blocks` | %s blocks | %s格 | %s 格 | 格式；`value.ai_noise` 已经是"%s 格" |
| 32 | `tooltip.*.value.noise_radius` | %s blocks | %s格 | %s 格 | 同上 |
| 33 | `tooltip.*.weight.assembled` | Assembled weight (attachments and ammo): %s | 整枪重量（含附件及弹药）：%s | 整枪重量（含配件和弹药）：%s | **一致：** 创造模式标签页、维护台、按键都叫"配件"，只有这里叫"附件"。"及"改"和" |

## 6. 背包（4 条）

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 34 | `tooltip.*.inventory.locked` | Locked inventory slot | 未解锁的背包栏位 | 这一格不能用 | **准确（严重）：** 生存模式里这些是原版物品栏的格子，AFL 永远不用（`PlayerStorageCapacity`：只有创造模式能用）。"未解锁"让玩家以为以后能解锁。EN 的 Locked 也有同样的问题，建议改成 Unused slot |
| 35 | `tooltip.*.inventory.locked.hint` | Items go into the pocket, rig and backpack grids | 物品放进口袋、胸挂和背包的格子 | 请将物品放入口袋、胸挂或背包 | 语域："的格子"多余，口袋、胸挂、背包本身就是格子 |
| 36 | `tooltip.*.inventory.overflow.hint` | Items can be removed, but new items cannot be inserted | 可以取出物品，但无法放入新的物品 | 只能取出，不能放入 | 语域：系统腔，两个"物品"重复。机制不变 |
| 37 | `screen.*.inventory.slot.base` | Base layer | 内衬 | 打底衣 | **准确：** base layer 是贴身穿的打底衣物（户外分层穿衣的第一层），"内衬"是衣服的里子。三个字，和旁边的"外套""防弹衣"并列 |

## 7. 界面和按键（2 条）

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 38 | `survivor.*.death.title_screen` | Title screen | 返回标题画面 | 返回标题屏幕 | **一致：** 原版叫"标题屏幕"（规范第 2.5 节、第 4.6 节 #48） |
| 39 | `key.*.reload` | Reload native weapon | 武器换弹 | 换弹 | 语域："武器换弹"是名词 + 动词的直译语序；按键列表在"黎明启示录"分类下，旁边是"检视武器"，单写"换弹"就清楚 |

## 8. Jade 读数（4 条）

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 40 | `jade.*.fuel_hazard.burning` | On fire! | 着火了！ | 着火 | 语域：读数写状态，不喊话（规范第 2.2 节） |
| 41 | `jade.*.diesel_generator.running` | Running | 运行 | 运行中 | **一致：** 泵都是"运行中"，发电机一个"运行"一个"运行中"不统一；"停机""缺油停机"不变 |
| 42 | `jade.*.portable_diesel_generator.running` | Running | 运行 | 运行中 | 同上 |
| 43 | `tooltip.*.stored_fluid` | Fluid: %s | 流体：%s | 液体：%s | **一致：** 同一套机器的 `input_fluid` 叫"输入液体"。AFL 现在的流体（汽油、柴油、水、工业废液）全是液体。以后有气体再改回"流体" |

## 9. 枪声和配件字幕（5 条）

规范第 2.8 节：东西发出的声音写"东西：动作"。听到枪声的人分得出手枪和步枪，分不出型号，所以写枪种（原版的弩也是"弩：发射"）。

| # | 键 | EN | 现中文 | 建议 | 理由 |
|---|---|---|---|---|---|
| 44 | `subtitles.*.p9_01.fire` | P9-01 Service Pistol fires | 制式手枪开火 | 手枪：开火 | 字幕句式 |
| 45 | `subtitles.*.p9_01.suppressed` | Suppressed pistol fires | 消音手枪开火 | 消音手枪：开火 | 同上 |
| 46 | `subtitles.*.hr55.fire` | HR-55 Battle Rifle fires | HR-55 战斗步枪开火 | 步枪：开火 | 同上 |
| 47 | `subtitles.*.hr55.suppressed` | Suppressed HR55 Battle Rifle fires | 消音 HR55 战斗步枪开火 | 消音步枪：开火 | 同上；顺带去掉了少连字符的 HR55。**EN 的 HR55 也要改成 HR-55** |
| 48 | `subtitles.*.attachment_operation` | Gun attachment adjusted | 枪械附件调整 | 枪械：调整配件 | 字幕句式 + 一致（"配件"，同 #33） |

## 10. 看过、决定不改的

这些看上去碰到了规范里的"不要用"或者别的规则，但在原句里是准确的，不改：

| 键 | 现中文 | 为什么不改 |
|---|---|---|
| `death.attack.heatstroke` | %1$s 死于热射病 | 热射病是中暑里最重的一种，致死的就是它。状态名叫"中暑"、死因叫"热射病"是医学上准确的分法 |
| `screen.*.inventory.placeholder` | 暂无可穿戴物品 | 这个格子现在确实还没有能穿的东西，以后会有。"暂无"在这里准确，比"没有"多了"以后会有"的意思。**2026-10-18 合并 D 的幸存者 P1–P3 后，这个键已删除**（背包人物页重做，代码不再引用） |
| `tooltip.*.blackridge_50.description` | 使用 .50 AE 弹药的重型半自动手枪 | 已经没有"一款"，和数据一致（只有半自动） |
| `tooltip.*.clinical_thermometer.description` | 按住右键约 3 秒测量体温，读数在体温表盘中显示 10 秒 | "测量体温"本来就是规范说法；原第 4.3 节 #30 改成"量体温"收益太小 |
| `survivor.*.death.place` | 地点：%1$s %2$s, %3$s, %4$s | 后三个是坐标，坐标用半角逗号分隔是通行写法。自动校验里要给这条加豁免 |
| `jade.*.intake_pump.status.too_hot` | 液体温度过高：开泵会烧毁普通泵 | 只有冒号可以换成逗号，"温度过高""烧毁"都是准确的设备用语。留到 Jade 那一批再顺手改 |
| `item.*.br51_extended_magazine_35` 等 | 扩容弹匣 | 玩家熟悉的叫法，全表一致；不为了"加长弹匣"去改一串名字 |

## 11. 验收以后怎么改（2026-10-18 已按这个流程做完第 1–3 步）

1. 用户在本表里勾掉不要的条目，或者改写某一条。
2. 只改通过的键：zh_cn.json 逐条替换值，键、行号、占位符都不动；EN 要一起改的 5 条（#3、#4、#6、#15、#47，以及 #34 的建议）同一次改 en_us.json。
3. 改完跑语言文件检查（规划见 [lang_validation_plan_v1.md](lang_validation_plan_v1.md)；脚本还没写之前，用批次 1 用过的那套检查：JSON 解析、行对齐、占位符一致、CRLF）。
4. 进游戏看：枪和配件的说明有没有被截断、字幕位置、背包格子的提示、维护台。
5. D 分支（`feature/gun-proficiency`）也改了 zh_cn.json。master 改完以后通知 D，D 合并时以 master 的这几行为准。

## 12. 实施记录（2026-10-18）

**用户改了 6 条：**

| # | 原建议 | 用户定的写法 |
|---|---|---|
| 3 HR-55 | 停止作用很强 | 制止力出色 |
| 5 BR-51 | 威力大、射程远，但后坐力重 | 威力强劲，射程出色，但后坐力较大 |
| 8 扩容弹匣 | 持续火力更强 | 增加弹匣容量，提升持续射击能力 |
| 11 红点瞄具 | 目标捕获更快 | 便于快速瞄准目标 |
| 35 背包提示 | 东西放进口袋、胸挂和背包 | 请将物品放入口袋、胸挂或背包 |
| 48 配件字幕 | 配件：调整 | 枪械：调整配件 |

- **同类一起改：** #9（弹鼓）、#10（P9-01 扩容弹匣）原来也写"持续火力更强"，按 #8 的写法一起改成"增加弹匣容量，提升持续射击能力"。
- **全自动确认：** 用户要求先向枪械工作区确认 P9-01 和 HR-55 的全自动是最终设计。D 回复（2026-10-18）：两把都是正式设计。依据是用户的设计总览 `docs/项目内容/01 - 设计/武器/枪械.md`：
  - P9-01：半自动 / 全自动，默认半自动，B 切换；
  - HR-55：2026-09-29 开放全自动，定位是贴脸 / 压制用的备用模式。
  - 所以 #3、#6 的中英文说明按"可切换半自动和全自动 / select-fire"改了。
- **改了哪些文件：** 只改了值，没有加键、删键，行号没变，没动代码和机制。
  - zh_cn.json：本批 48 个键；
  - en_us.json：6 个键，即 #3、#4、#6、#15、#34、#47。#34 的英文改成 Unused slot。
- **检查**（脚本放在会话的临时目录里，是 `lang-check.cjs` 的手工版，正式脚本见 [lang_validation_plan_v1.md](lang_validation_plan_v1.md)）：全部通过。
  - 两份文件都能解析，没有重复键，没有空值；
  - 1276 个键一一对应，1320 行逐行对齐，分段空行也对齐；
  - 每个键的占位符（`%s`、`%n$s`、`%%`、`§`）中英一致；
  - 都是 CRLF，没有 BOM；
  - 和 HEAD 比，zh_cn 改了 79 个键（批次 1 的 31 个 + 本批 48 个），en_us 改了 6 个，**清单以外没有键被改动**。
- **代码里没有写死旧文字：** 搜过 `src/`，只在两处代码注释里出现英文 muzzle device。
- **同步的文档：**
  - `docs/gameplay/locked_inventory_slots_v1.md`：提示文字表；
  - `docs/ui/equipment_tooltip_v1.md`：标签"伤害""开火模式"。
- **通知了 D：** D 的 `feature/gun-proficiency` 也改了两个语言文件。合并时本批的键以 master 为准。

**实机验收要看的：**

1. 把鼠标移到 6 把枪上：说明有没有被截断；标签显示"伤害""开火模式"；射程和声音半径显示成"48 格"这种带空格的写法。
2. 配件（扩容弹匣、弹鼓、红点、3 个消音器）的说明长度。
3. 弹药和弹壳的名字（9×19mm、12 号霰弹），以及枪的说明里"弹药类型"那一行。
4. 背包：生存模式下灰色斜线格的提示；超出容量那一格的提示；"打底衣"格子的名字有没有挤到旁边。
5. 字幕：P9-01 和 HR-55 开枪、装消音器后开枪的字幕；在维护台装、拆配件时的字幕。
6. 按键设置里的"换弹"；死亡页的"返回标题屏幕"按钮。
7. Jade：柴油发电机、便携柴油发电机运行时显示"运行中"；燃油着火时显示"着火"。
8. 价格牌方块名"普雷里价格牌"（牌面上仍然是 PRAIRIE）；T15 的名字"霍尔斯特德 Tamarack T15"。

**实机验收第 1 轮（用户 2026-10-18）：** 枪的中文说明断行很怪：第一行只有"紧凑可靠的 9mm""7.62×51mm"，HR-55 最后一行只剩"色"。英文正常。

- 原因：原版自动换行只在空格处断，而且退回到这一行的最后一个空格。本批按规范在口径前后加了空格，这个空格就成了一行里唯一的断点。
- 修复：给原版拆行加上中文断点（`mixin/client/AflCjkLineBreakMixin`，规则在 `client/font/CjkLineBreak`；说明见 `docs/ui/afl_font_v1.md` 第 4 节第 6 项）。
  - 中文字后面都能断；
  - 标点不放在行首；
  - "12 号"这种数字加单位不拆开；
  - 英文不受影响。
- 文案不用改回"毫米"。编译通过，模拟检查通过，**等第 2 轮实机看**。
- 末行只剩一两个字（"出 / 色"）这种情况，这次的修复不管，取决于文字长度和窗口宽度。
