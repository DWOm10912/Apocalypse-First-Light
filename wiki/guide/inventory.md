# 背包与负重

AFL 用格子背包取代了原版的物品栏：每件东西占一块大小，按 **E** 打开

## 格子背包

- **口袋**：身上自带 4 个小口袋
- **胸挂**：装备战术胸挂后多出几个弹匣袋，见 [装备](../arms/equipment)
- **背包**：帆布背包 5×6 格，登山包 6×10 格
- 拖动物品时按 **R** 旋转，按 **Q** 丢掉鼠标下的物品
- 地上的物品不会自动捡起：看着它按使用键，或者潜行 + 使用直接收进格子

## 武器栏和快捷键

| 栏位 | 放什么 | 需要 |
|---|---|---|
| 1 | 长枪 | 枪上装了枪背带 |
| 2 | 手枪 | 手枪枪套 |
| 3 | 近战武器 | — |
| 4–9 | 快捷绑定口袋或胸挂里的东西 | — |

AFL 没有副手，枪一律右手持握

## 负重

每样东西都有接近真实的重量；方块按“搬一块”的重量算，所以比真实的轻很多。查不到的物品按每个 <V d="item_mass/core_v1#policy.fallback_unit_mass_kg" u="kg" /> 算

舒适负重是 <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" />，超过以后：

| 负重 | 移动速度 | 跳跃 |
|---|---|---|
| ≤ <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" /> | 正常 | 正常 |
| <V d="item_mass/core_v1#policy.penalties.curve.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.jump" /> |

- 负重达到舒适负重的 <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="100" digits="0" u="%" />（约 <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="30" digits="0" u="kg" />）就不能跑
- 穿在身上的盔甲只算 <V d="item_mass/core_v1#policy.penalties.armor_load_factor" pct /> 的重量
- 背得越重，体力消耗也越多：

| 负重 | 走跑消耗 | 动作消耗 | 体力恢复 |
|---|---|---|---|
| <V d="stamina/stamina_v1#weight.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.1.move" /> | ×<V d="stamina/stamina_v1#weight.1.action" /> | ×<V d="stamina/stamina_v1#weight.1.regen" /> |
| <V d="stamina/stamina_v1#weight.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.2.move" /> | ×<V d="stamina/stamina_v1#weight.2.action" /> | ×<V d="stamina/stamina_v1#weight.2.regen" /> |
| <V d="stamina/stamina_v1#weight.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.3.move" /> | ×<V d="stamina/stamina_v1#weight.3.action" /> | ×<V d="stamina/stamina_v1#weight.3.regen" /> |
| <V d="stamina/stamina_v1#weight.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.4.move" /> | ×<V d="stamina/stamina_v1#weight.4.action" /> | ×<V d="stamina/stamina_v1#weight.4.regen" /> |

表里的公斤数按默认舒适负重 30 kg 换算

## 搜刮容器

世界里的柜子、货架、垃圾桶等容器一开始是“没搜过”的。打开以后会一格一格地翻出东西，关掉界面就停下，下次接着搜

<StatusTag s="new" /> 现在容器里翻出的大多是按店铺类型（杂货、五金、药店、学校、工业）分的“货物”，具体物品清单还在做
