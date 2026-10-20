# 背包与负重

AFL 用格子背包取代了原版物品栏：每件物品按尺寸占用一块格子，按 **E** 打开

## 格子背包

- **口袋**：身上自带 4 个小口袋
- **胸挂**：装备野战胸挂后增加几个弹匣袋，见 [装备](../arms/equipment)
- **背包**：帆布背包 5×6 格，登山背包 6×10 格
- 拖动物品时按 **R** 旋转，按 **Q** 丢弃鼠标下的物品
- 地面上的物品不会自动拾取：对准后按使用键捡起，或按潜行 + 使用直接收进格子

## 武器栏和快捷键

| 栏位 | 放什么 | 需要 |
|---|---|---|
| 1 | 长枪 | 长枪装有枪背带 |
| 2 | 手枪 | 手枪枪套 |
| 3 | 近战武器 | — |
| 4–9 | 快捷绑定口袋或胸挂里的物品 | — |

AFL 没有副手，枪一律右手持握

## 负重

每件物品都有接近真实的重量；方块按“搬一块”的重量计算，所以比实际轻得多。缺少重量数据的物品按每个 <V d="item_mass/core_v1#policy.fallback_unit_mass_kg" u="kg" /> 计算

舒适负重为 <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" />，超过后：

| 负重 | 移动速度 | 跳跃 |
|---|---|---|
| ≤ <V d="item_mass/core_v1#policy.comfort_capacity_kg" u="kg" /> | 正常 | 正常 |
| <V d="item_mass/core_v1#policy.penalties.curve.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.1.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.2.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.3.jump" /> |
| <V d="item_mass/core_v1#policy.penalties.curve.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.speed" /> | ×<V d="item_mass/core_v1#policy.penalties.curve.4.jump" /> |

- 负重达到舒适负重的 <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="100" digits="0" u="%" />（约 <V d="item_mass/core_v1#policy.penalties.sprint_block_ratio" x="30" digits="0" u="kg" />）时无法疾跑
- 身上穿着的盔甲只计 <V d="item_mass/core_v1#policy.penalties.armor_load_factor" pct /> 的重量
- 负重越高，耐力消耗也越多：

| 负重 | 移动消耗 | 动作消耗 | 耐力恢复 |
|---|---|---|---|
| <V d="stamina/stamina_v1#weight.1.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.1.move" /> | ×<V d="stamina/stamina_v1#weight.1.action" /> | ×<V d="stamina/stamina_v1#weight.1.regen" /> |
| <V d="stamina/stamina_v1#weight.2.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.2.move" /> | ×<V d="stamina/stamina_v1#weight.2.action" /> | ×<V d="stamina/stamina_v1#weight.2.regen" /> |
| <V d="stamina/stamina_v1#weight.3.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.3.move" /> | ×<V d="stamina/stamina_v1#weight.3.action" /> | ×<V d="stamina/stamina_v1#weight.3.regen" /> |
| <V d="stamina/stamina_v1#weight.4.load_ratio" x="30" digits="0" u="kg" /> | ×<V d="stamina/stamina_v1#weight.4.move" /> | ×<V d="stamina/stamina_v1#weight.4.action" /> | ×<V d="stamina/stamina_v1#weight.4.regen" /> |

表中的公斤数按默认舒适负重 30 kg 换算

## 搜刮容器

世界中的柜子、货架、垃圾桶等容器一开始处于“未搜索”状态。打开后会逐格搜出物品，关闭界面即停止，下次打开时接着搜

<StatusTag s="integrated" /> 目前容器里搜出的大多是按店铺类型（杂货、五金、药店、学校、工业）区分的“货物”，具体物品清单尚未完成
