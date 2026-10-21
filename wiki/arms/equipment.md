# 装备

身上有五个装备位：背包、胸挂、手表、耳部、枪套

## 携行

<StatusTag s="creative" />

| 装备 | 装备位 | 作用 |
|---|---|---|
| <ItemIcon id="field_chest_rig" /> 野战胸挂 | 胸挂 | 4 个弹匣袋：3 个 1×2、1 个 2×2 |
| <ItemIcon id="canvas_backpack" /> 帆布背包 | 背包 | 5×6 格 |
| <ItemIcon id="hiking_backpack" /> 登山背包 | 背包 | 6×10 格 |
| <ItemIcon id="pistol_holster" /> 手枪枪套 | 枪套 | 手枪可放入 2 号栏 |
| <ItemIcon id="gun_sling" /> 枪背带 | 装在长枪上 | 长枪可放入 1 号栏 |

见 [背包与负重](../guide/inventory)

## 工具

| 物品 | 作用 | 获取 |
|---|---|---|
| <ItemIcon id="simple_hearing_protection" /> 简易护耳罩 | 戴在耳部，减轻枪声引起的耳鸣，但也会让周围的声音变小 | 合成：铁锭、皮革、羊毛 |
| <ItemIcon id="wrist_thermometer" /> 腕式温度计 | 戴在手腕上，持续显示气温 | 合成：皮革、玻璃板、铜锭 |
| <ItemIcon id="clinical_thermometer" /> 体温计 | 按住右键约 3 秒测量体温 | 合成：玻璃板、红色染料 |
| <ItemIcon id="geiger_counter" /> 盖革计数器 | 显示当前辐射剂量率 | <StatusTag s="creative" /> |
| <ItemIcon id="crowbar" /> 撬棍 | 近战武器，也能拆下隧道里的部件 | <StatusTag s="creative" /> |
| <ItemIcon id="flip_lighter" /> 翻盖打火机 | 右键翻盖、打火，点着能照明，也能点燃柴炉、营火、蜡烛和 TNT | <StatusTag s="creative" /> |

## 翻盖打火机

<StatusTag s="test" /> 镀银的老式翻盖打火机，拿在主手里用

- 右键：盖子合着时翻开；开着时打火，偶尔打不着，再按一次
- 下蹲右键：合上盖子；点着时合上，火也随之熄灭
- 第一人称下翻盖、打火时，视线会短暂低头看向手里的打火机
- 点着后火苗会照亮周围；对着营火、蜡烛、TNT 或柴炉右键可以点燃，不能点燃其他方块
- 点着时会消耗燃油，物品栏里的燃油条显示剩余燃油；燃油用完或到了水下，火会熄灭
- 翻盖、打火的声音能被附近 <V j="FlipLighterItem.SOUND_RADIUS" /> 格内的感染者听到
- 目前只能从创造模式物品栏获得，音效是临时的

## 护甲

<StatusTag s="planned" /> AFL 还没有自己的护甲。计划做防弹衣、防暴盾、防护服和防辐射装备

## 原版装备

<StatusTag s="test" /> 原版剑、盾牌和盔甲在 AFL 中无法使用

- 无法合成，也不会出现在战利品、村民交易和生物的出生装备中
- 旧存档里已有的不会被删除，可以拿着或穿上，但没有任何效果：
  - 剑的伤害、攻击速度和挖掘速度与空手相同，没有横扫
  - 盾牌无法举起
  - 盔甲不提供护甲值、盔甲韧性、击退抗性和保暖
  - 附魔不生效；皮革靴无法在细雪上行走，皮革盔甲不再防冻；海龟壳不提供水下呼吸
- 附魔台、铁砧、酿造台、砂轮、锻造台和末影箱在生存模式下无法使用，也无法获得；附魔书和附魔之瓶无法获得
- 创造模式物品栏中不显示这些装备，仍可用 `/give` 获得；获得后在任何模式下都没有效果
- 开发用开关：`config/apocalypse_firstlight-common.toml` 中的 `vanilla_retirement.enabled`，设为 `false` 恢复原版规则
- 原版木头和木制品（含工作台、火把）也已无法获得，见 [森林](../world/forests#原版木头)
- 原版弓、弩、箭、三叉戟、不死图腾和全部工具也已无法获得，见 [原版内容](../world/#原版内容)
