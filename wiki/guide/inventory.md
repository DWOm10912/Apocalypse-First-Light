# 背包与负重

AFL 用格子背包取代了原版物品栏：每件物品按尺寸占用一块格子，按 **E** 打开

## 格子背包

- **口袋**：身上自带 4 个小口袋
- **胸挂**：装备野战胸挂后增加几个弹匣袋，见 [装备](../arms/equipment)
- **背包**：帆布背包 5×6 格，登山背包 6×10 格
- **衣服** <StatusTag s="verified" d="2026-10-27" />：牛仔布衬衫放打底衣格，皮夹克放外套格，牛仔裤放腿部格，皮鞋放脚部格。右键或拖进格子穿上，身上会画出穿着的衣服。衣服有重量，也能保暖
  - 穿在身上的衣服贴着身体，弯腰、坐下、跑步时和身体一起弯。从里到外是牛仔裤、鞋、衬衫、夹克，一层比一层厚一点
  - 男女穿的是同一件衣服；女性穿上后照样能看出收腰和胸型，不会穿模
  - 衣服物品是叠好的样子：衬衫、夹克叠成方块，牛仔裤对折，鞋子一双并排。拿在手里、扔在地上、放进物品框都是这个样子
  - 全部脱掉后只剩内衣，光着脚
- 拖动物品时按 **R** 旋转，按 **Q** 丢弃鼠标下的物品
- 地面上的物品不会自动拾取：对准后按使用键捡起，或按潜行 + 使用直接收进格子

## 武器栏和快捷键

| 栏位 | 放什么 | 需要 |
|---|---|---|
| 1 | 长枪 | 长枪装有枪背带（[怎么装](../arms/attachments#枪背带)） |
| 2 | 手枪 | 手枪枪套 |
| 3 | 近战武器 | — |
| 4–9 | 快捷绑定口袋或胸挂里的物品 | — |

<StatusTag s="integrated" d="2026-10-25" /> 没穿手枪枪套时，空着的手枪栏是红色的，放不进去；身上没有装了枪背带的长枪时，空着的长枪栏也是红色的。鼠标指上去会写缺什么。滚轮切换会跳过红色的栏。手里拿着东西、身上又放不下时，滚轮不切换，屏幕上提示"放不下"

AFL 没有副手，枪一律右手持握

## 身体图

- <StatusTag s="verified" d="2026-10-20" /> 身体图

背包左栏中间是你自己皮肤的正面，压成了灰色。屏幕左边是身体的右边，脚下标着"右 / 左"，下面是生命值

受伤的部位会上色，并放一个和 HUD 一样的状态图标：红色要尽快处理（没清洁的感染、没固定的骨折），棕色需要处理（没处理的抓伤），琥珀色已经处理过、正在好转

鼠标停在一个部位上，会列出这里的每处伤和处理建议：身上有的最好的一样加框；更好但身上没有的变暗显示，提醒你该去找什么；什么都没有时给一句不靠物品的办法。"身上"包括手上、口袋、胸挂和背包，不包括地上和箱子。见 [生存状态](./survival#伤口)

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

## 检视物品

<StatusTag s="integrated" />

- 背包里鼠标指着一件物品按 **V**，或者手里拿着不是枪的物品按 **V**，打开检视界面。物品从格子里飞到屏幕中间放大，游戏不暂停。第一人称时物品和即时配件改装里的枪一样，按周围的光线照亮，背景不变暗
- 左键拖动旋转，中键拖动平移，滚轮缩放，**R** 重置视角；再按 **V**、**Esc** 或 **E** 关上
- 右上角是按键提示，下面是物品名字和一句说明（和鼠标悬停时提示框里名字下面那行一样）。打开时小地图、坐标、罗盘和盖革计数器窗口都隐藏
- 有的物品带交互点：转到那一面，鼠标指着时部件描一圈琥珀色边，点一下执行，动作做完才能点下一个。现在只有盖革计数器有：
  - 侧面的电源推钮：开机 / 关机
  - 背面的电池盖：打开电池盖；打开后右键电池仓合上
  - 装着的电池：取出电池
  - 空电池仓：装电池，从底部弹出的候选格里选一节身上的 9 V 电池（口袋、胸挂、背包、快捷栏）
- 取出的电池回到背包，放不下就掉在脚下。关上检视界面时电池盖开着，会先自动合上
- 受伤，或者物品不在原来那格了，检视界面自动关上
- 手里拿着枪按 **V** 照旧播检视动作；背包里的枪按 **V** 只能转着看

## 搜刮容器

世界中的柜子、货架、垃圾桶等容器一开始处于“未搜索”状态。打开后会逐格搜出物品，关闭界面即停止，下次打开时接着搜

<StatusTag s="integrated" /> 目前容器里搜出的大多是按店铺类型（杂货、五金、药店、学校、工业）区分的“货物”，具体物品清单尚未完成
