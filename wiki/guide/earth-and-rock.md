# 挖土和凿石

- <StatusTag s="test" /> <StatusTag s="creative" /> 本页的工具都只在创造模式物品栏里，还在测试分支上

地面可以用铲子一铲一铲地挖开、填平。地表散落着半埋在土里的花岗岩巨石，铲子挖不动，要用鹤嘴锄凿开

## 工具

| 工具 | 用途 | 重量 |
|---|---|---|
| <ItemIcon id="round_point_shovel" /> 圆头铲 | 挖土、倒土、刮平 | <V d="item_mass/core_v1#items.apocalypse_firstlight:round_point_shovel.unit_mass_kg" u="kg" /> |
| <ItemIcon id="pick_mattock" /> 鹤嘴锄 | 凿巨石 | <V d="item_mass/core_v1#items.apocalypse_firstlight:pick_mattock.unit_mass_kg" u="kg" /> |

## 铲子

拿着圆头铲对准松软的地面，按一下使用键铲一下，按住不会连续铲

- 铲子是空的：挖起一铲土，扣 <V d="stamina/stamina_v1#costs.shovel_dig" /> 耐力
- 铲子装着土：把土倒在准星处，扣 <V d="stamina/stamina_v1#costs.shovel_pour" /> 耐力
- 潜行时：刮平地面，扣 <V d="stamina/stamina_v1#costs.shovel_scrape" /> 耐力

挖到石头时提示“石头上挖不动”：从巨石旁边往下挖，就能把它埋在土里的部分挖出来

## 巨石

巨石是半埋在地里的花岗岩，有圆的漂砾、平板石、碎石块、石堆等十几种样子。空手、铲子和别的工具都打不动它，对着它会提示“要用镐”

用鹤嘴锄凿：

- 拿着鹤嘴锄对准巨石，按一下使用键凿一下，一下约 <V j="BoulderStrike.DURATION" x="0.05" u="秒" />
- 只能凿离脚不远的地方：从眼睛算起 <V j="BoulderStrike.REACH" u="格" /> 以内
- 每凿中一下扣 <V d="stamina/stamina_v1#costs.pick_strike" /> 耐力。耐力用光时凿不动
- 声音很响，附近的感染体能听到
- 大巨石凿够次数后先**裂开**成几块，每块再凿，凿够次数后**碎掉**，掉下<ItemIcon id="field_stone" />石块
- 一整块巨石从开始到全部凿碎，最多凿 50 下
- 巨石凿掉以后留下一个坑，坑边的土会塌下来

## 石块

<ItemIcon id="field_stone" />石块是凿碎巨石掉下来的花岗岩，<V d="item_mass/core_v1#items.apocalypse_firstlight:field_stone.unit_mass_kg" u="kg" /> 一块，最多叠 8 块。现在还没有用途

- <StatusTag s="planned" /> 地上能捡的散石和石子
- <StatusTag s="planned" /> 用鹤嘴锄开采天然石头
