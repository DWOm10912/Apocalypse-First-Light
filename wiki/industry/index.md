# 工业制造

AFL 的电能以 FE（Forge Energy）计量。发电机把柴油、煤或岩浆转化为电能，配电盘把电分配给建筑里的灯和插座，机器用电加工矿石和材料

<StatusTag s="creative" /> 机器、电气部件和木材目前都没有合成配方，只能在创造模式获取；矿石也还不会自然生成

## 分区

| 页面 | 内容 |
|---|---|
| [发电机](./generators) | 柴油发电机组、便携式柴油发电机、热力发电机 |
| [建筑供电](./power) | 配电盘、暗线布线、插座和电线、灯 |
| [材料与加工](./materials) | 矿石、加工流程、机器功耗、木材 |

## 工作台

<StatusTag s="test" /> <ItemIcon id="wooden_workbench" /> 木制工作台，2 格长、1 格宽、2 格高：左边一台台钳，右边桌面下两个抽屉，背后一块工具挂板，顶上一层搁板

- 对着抽屉面右键，抽屉平滑地拉出来；再右键推回去。拉出来以后对着抽屉里面右键打开，每个抽屉 <V j="WorkbenchBlockEntity.DRAWER_SLOTS" /> 格，两个抽屉分开放东西
- 挂板上有 <V j="Workbench.PEGS" /> 对木钉，每对挂一件工具：拿着工具对着木钉右键挂上，空手右键取下，和木质工具架一样。伐木斧太长，挂不上
- <StatusTag s="test" /> 撬棍太长，挂在工作台上时钩子挂一根钉，铲口拄在桌面上斜靠着
- 挂着的工具和工作台一起画进区块，不会每帧重画
- 拆掉工作台时，抽屉里的东西和挂着的工具都会掉出来；用斧子拆得快
- 抽屉暂时用木柜门的声音
- 目前只能从创造模式物品栏获得
- <StatusTag s="test" /> 制作界面：右键桌面打开。顶上是"背包 / 制作"两个标签，按背包键切换；左边按分类列出已学会的配方，中间是物品和说明，右边是材料、工具、数量和制作按钮（点一下开始，等圆圈走满；再点一下停止）。按 F 把配方加入待做，屏幕右上角会显示还缺的材料
- <StatusTag s="test" /> 配方写在数据文件里，每个分类一个文件夹，每条写材料、时间和怎么学会。制作页只列出当前幸存者会的配方；学会的配方跟着这个幸存者，换人要重新学
- <StatusTag s="test" /> 现在只有一条测试配方：白栎原木 × 1 加身上的 <ItemIcon id="hand_saw" /> 手锯，做成 <ItemIcon id="white_oak_beam" /> 白栎横梁 × 1，在"建造"分类里。为了测试书，现在要先读 <ItemIcon id="field_survival_guide" /> [野外求生指南](../arms/equipment#野外求生指南)的弓钻那一节才会（生存模式）
- <StatusTag s="test" /> 横梁在桌面上做：站在工作台正面点一下"制作"，界面关掉，人走到桌前，用手锯把桌面上的原木顺着纹理锯开，锯缝一点点往里走，锯末往下掉。快捷栏上方显示正在做的物品、第几件和进度。做好的直接放进身上；数量调多了会一件接一件锯完。走动、跳、左键或打开任何界面就停下。站得太远或在工作台后面时，会提示站到工作台前面
- <StatusTag s="test" /> 锯木头的声音一推一拉，跟着手上的动作走，<V j="WorkbenchCrafting.SAW_RADIUS" /> 格外就听不见了。这个范围内的感染者也听得到
- 材料现在只从身上扣，抽屉里的不算；离工作台太远、没带工具、材料不够时做不了，屏幕下方会写原因
- 做好的横梁用 [建筑锤](../arms/equipment#建筑锤) 装到房子上
- 其他分类还是灰的；未学会的配方不会显示
- <StatusTag s="planned" /> 配方：从生活经验、动手摸索、书籍和图纸学会，书籍以后在城市里能搜到；制作时从工作台抽屉、身上和附近的箱子里扣材料

### 木家具材料

<StatusTag s="test" /> 第一批木家具要用的材料已经有了，都只在创造模式"材料"栏里。家具本身的配方还没有：做一件家具要 20 到 60 秒，这段时间在台前做什么还在设计。

| 物品 | 怎么来 | 质量 |
|---|---|---|
| <ItemIcon id="white_oak_board" /> 板材（6 个树种：白栎、白松、北方红栎、裂皮山核桃、红枫、加拿大铁杉） | 工作台上锯原木 | 白栎 <V d="item_mass/core_v1#items.apocalypse_firstlight:white_oak_board.unit_mass_kg" u="kg" /> |
| <ItemIcon id="wood_finish_can" /> 胡桃木色木器漆 | 城镇里找 | <V d="item_mass/core_v1#items.apocalypse_firstlight:wood_finish_can.unit_mass_kg" u="kg" /> |
| <ItemIcon id="nail_box" /> 圆钉 | 城镇里找 | <V d="item_mass/core_v1#items.apocalypse_firstlight:nail_box.unit_mass_kg" u="kg" /> |
| <ItemIcon id="cabinet_hinge" /> 合页 | 城镇里找；家具配件盒 | <V d="item_mass/core_v1#items.apocalypse_firstlight:cabinet_hinge.unit_mass_kg" u="kg" /> |
| <ItemIcon id="iron_knob" /> 铁拉手 | 城镇里找；家具配件盒 | <V d="item_mass/core_v1#items.apocalypse_firstlight:iron_knob.unit_mass_kg" u="kg" /> |
| <ItemIcon id="wooden_knob" /> 木拉手 | 工作台上用板材做 | <V d="item_mass/core_v1#items.apocalypse_firstlight:wooden_knob.unit_mass_kg" u="kg" /> |
| <ItemIcon id="keyhole_escutcheon" /> 锁眼盖 | 城镇里找；家具配件盒 | <V d="item_mass/core_v1#items.apocalypse_firstlight:keyhole_escutcheon.unit_mass_kg" u="kg" /> |
| <ItemIcon id="furniture_hardware_box" /> 家具配件盒 | 城镇里找 | <V d="item_mass/core_v1#items.apocalypse_firstlight:furniture_hardware_box.unit_mass_kg" u="kg" /> |

- <StatusTag s="test" /> 板材是 1.2 米长的长条板，不是木板方块。在工作台上用手锯锯原木：同树种原木 × <V d="workbench_recipes/woodwork/white_oak_boards#materials.0.count" /> 出 <V d="workbench_recipes/woodwork/white_oak_boards#result.count" /> 块，每次 <V d="workbench_recipes/woodwork/white_oak_boards#seconds" u="秒" />，和横梁一样在桌面上锯，桌上摆的是你锯的那种原木。林区住户一开始就会
- <StatusTag s="test" /> 木拉手：任意树种的板材 × <V d="workbench_recipes/woodwork/wooden_knob#materials.0.count" /> 加手锯，做出 <V d="workbench_recipes/woodwork/wooden_knob#result.count" /> 个，<V d="workbench_recipes/woodwork/wooden_knob#seconds" u="秒" />，现在只有进度条。家具上的拉手用铁拉手、木拉手都行，做出来的家具看起来一样
- 合页和锁眼盖做不出来，只能找到，以后拆旧家具也能拆出来
- 木器漆一罐 1 升，圆钉一盒 180 根，用多少扣多少，剩下的写在物品提示里，不满时物品格下面有一条进度条。这两样不叠放
- <StatusTag s="test" /> 家具配件盒：拿在手里按使用键打开，随机出 <V d="loot_tables/gameplay/furniture_hardware_box#pools.0.rolls.min" /> 到 <V d="loot_tables/gameplay/furniture_hardware_box#pools.0.rolls.max" /> 件合页、铁拉手和锁眼盖，盒子用掉。开盒声暂时用原版的
- <StatusTag s="planned" /> 家具配方（用量已定，见开发文档 `docs/gameplay/furniture_recipes_v1.md`）、制作时台前的动作、拆旧家具

## 机器一览

| 机器 | 储能 | 工作功耗 |
|---|---|---|
| <ItemIcon id="crusher" /> 粉碎机 | <V d="machine_balance/crusher#capacity_fe" u="FE" /> | <V d="machine_balance/crusher#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="industrial_furnace" /> 工业熔炉（3 条通道） | <V d="machine_balance/industrial_furnace#capacity_fe" u="FE" /> | 每条通道 <V d="machine_balance/industrial_furnace#work_fe_per_tick_per_lane" u="FE/t" /> |
| <ItemIcon id="alloy_furnace" /> 合金炉 | <V d="machine_balance/alloy_furnace#capacity_fe" u="FE" /> | <V d="machine_balance/alloy_furnace#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="chemical_reactor" /> 化学反应器 | <V d="machine_balance/chemical_reactor#capacity_fe" u="FE" /> | <V d="machine_balance/chemical_reactor#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="compressor" /> 压缩机 | <V d="machine_balance/compressor#capacity_fe" u="FE" /> | <V d="machine_balance/compressor#work_fe_per_tick" u="FE/t" />（尚无配方） |
| <ItemIcon id="intake_pump" /> 取液泵 | <V d="machine_balance/intake_pump#capacity_fe" u="FE" /> | <V d="machine_balance/intake_pump#work_fe_per_tick" u="FE/t" />，每 tick 抽取 <V d="machine_balance/intake_pump#pump_mb_per_tick" u="L" /> |
| <ItemIcon id="charging_station" /> 充电站 | <V d="machine_balance/charging_station#capacity_fe" u="FE" /> | 充电 <V d="machine_balance/charging_station#charge_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_battery" /> 能量电池 | <V d="machine_balance/energy_battery#capacity_fe" u="FE" /> | 充放电 <V d="machine_balance/energy_battery#max_extract_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_cell" /> 能量单元 | <V d="machine_balance/energy_cell#capacity_fe" u="FE" /> | 充放电 <V d="machine_balance/energy_cell#max_extract_fe_per_tick" u="FE/t" /> |

<ItemIcon id="precision_fabrication_station" />精密制造台目前只作装饰
