# Creative inventory framing for recent custom blocks

Status: item GUI framing adjusted for the custom models visible in the creative Blocks tab. This affects the `gui` item display context (creative inventory, normal inventory and hotbar), not placed block geometry, VoxelShape, first/third-person holding, ground or item-frame transforms. No HUD overlay code is involved.

The user screenshot was measured against the center of each 18-pixel inventory slot at GUI scale 6. Corrections use the visible model bounding box, not the asymmetric pixel-mass centroid, so a chair or toilet can keep its intentional silhouette. Item GUI rotations and scales remain unchanged; only `display.gui.translation` changed. Values are in Minecraft item-model pixels:

| Registry ID suffix | GUI translation [X,Y,Z] |
| --- | --- |
| `retail_shelf_single` | `[-0.75,-2.9,0]` |
| `cash_register` | 2026-09-30 起由 `tools/build-cash-register-v2.mjs` 生成：rotation `[30,225,0]`、translation `[-0.038,1.482,0]`、scale 0.711（V2 Mesh，见 [cash_register_v2.md](cash_register_v2.md)） |
| `storage_rack` | 2026-10-04 起由 `tools/build-storage-rack-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中，scale 0.5；2026-10-08 起 scale 0.41、translation `[0.812,-3.104,0]`（0.5 时图标高 18.1 px，超出 16 px 的格子，见下面"出格检查"）。物品模型是单独一个货架（中段加两端立柱），见 [storage_rack_v1.md](storage_rack_v1.md) |
| `fuel_dispenser` | 2026-10-04 起由 `tools/build-fuel-dispenser-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中 `[1.527,-2.177,0]`，scale 0.27。物品模型是整台加油机（含四把挂着的枪和路缘），见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md) |
| `underground_fuel_tank_gasoline`、`underground_fuel_tank_diesel` | 2026-10-04 起由 `tools/build-underground-fuel-tank-v1.mjs` 生成：父模型是对应的整罐方块模型，rotation `[30,225,0]`，scale 0.13，translation `[0,1.589,0]`（2026-10-05 加卸油口后重新居中），见 [underground_fuel_tank_v1.md](underground_fuel_tank_v1.md) |
| `fuel_dispenser_sump` | 2026-10-05 起由 `tools/build-fuel-station-sump-v1.mjs` 生成：父模型是整个底槽（2 × 2 × 1 格），rotation `[30,225,0]`，scale 0.3，translation `[1.7,-1.23,-2.67]`，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md) |
| `submersible_fuel_pump`、`pump_manhole_cover`、`fuel_fill_cover_gasoline`、`fuel_fill_cover_diesel` | 2026-10-05 起由 `tools/build-fuel-station-sump-v1.mjs` 生成：一格大小，rotation `[30,225,0]`，scale 0.625，不平移；井盖和卸油口盖的物品是底座加关着的盖子，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md) |
| `intake_pump` | 2026-10-05 起由 `tools/build-intake-pump-v1.mjs` 生成：父模型是 `intake_pump/item`（整台泵，开关关、灯灭），rotation `[30,225,0]`，按投影自动缩放到 15 px（scale 0.4，translation `[-2.008,3.197,0]`），见 [intake_pump_v1.md](intake_pump_v1.md) |
| `fuel_canopy_column`、`fuel_canopy_ceiling`、`fuel_canopy_light`、`fuel_canopy_fascia` | 2026-10-04 起由 `tools/build-fuel-canopy-v1.mjs` 生成：rotation `[30,225,0]`，scale 0.62，translation 按网格包围盒居中（立柱底座 `[0,0.866,0]`、吊顶和灯板 `[0,0,0]`、边檐直段 `[-0.077,0.146,0]`）；灯板的物品模型是灯板加未亮的灯面，见 [fuel_canopy_kit_v1.md](fuel_canopy_kit_v1.md) |
| `fuel_island_curb`、`fuel_island_end`、`fuel_island_bollard` | 2026-10-04 起由 `tools/build-fuel-island-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中（路缘 `[0,3.458,0]`、端头 `[-1.013,2.945,0]`、防撞柱 `[0,0.163,0]`），scale 0.62（防撞柱 0.55），见 [fuel_island_kit_v1.md](fuel_island_kit_v1.md) |
| `fuel_nozzle_gasoline`、`fuel_nozzle_diesel` | 同一生成器：油枪侧面朝外，rotation `[20,120,0]`，translation `[-0.166,-0.041,0]`，scale 1.55；手上的显示参数 2026-10-04 按原版手持变换离线拟合，见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md) |
| `checkout_counter`、`checkout_counter_display`、`checkout_counter_gate`、`back_bar_shelf` | 2026-10-04 起由 `tools/build-checkout-counter-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中（柜台 `[0.332,-0.376,0]`、带货架 `[0.132,-0.376,0]`、通道门 `[0.384,-0.644,0]`、背柜 `[1.131,-4.198,0]`，2026-10-08 起背柜 scale 0.41、translation `[0.928,-3.442,0]`（0.5 时高 18.0 px，出格）；2026-10-04 台面加高到 16 px 后重新生成），scale 0.62（背柜 0.5）。物品模型是直段加两端封板（带货架款再加托盘），通道门是关着的静态模型 `checkout_counter/gate_item`，见 [checkout_counter_v1.md](checkout_counter_v1.md) |
| `channel_letter`（26 个字母） | 2026-10-09 起由 `tools/build-prairie-signage-v1.mjs` 生成：每个字母一个物品模型 `channel_letter_<字母>`（父模型是那个字的灭灯 OBJ），`channel_letter.json` 按物品属性 `apocalypse_firstlight:letter` 切换；GUI rotation `[10,200,0]`，按投影自动缩放到 15.2 px（P：scale 1.213，translation `[2.876,-1.261,0]`）；手持、掉落、展示框用 `heldDisplay`。见 [fuel_stop_a1_details_v1.md](fuel_stop_a1_details_v1.md) |
| `price_sign` | 同一生成器：父模型 `price_sign/item`（整块牌子，不亮），rotation `[20,200,0]`，scale 0.224，translation `[1.684,-4.313,0]`；其余视角 `heldDisplay` |
| `roof_tpo`、`cmu_screen_wall` | 原版方块的显示参数（父模型是方块模型 / `block/block`）。围墙的物品模型是居中的一段带压顶的直墙（`cmu_screen_wall/item`，`tools/build-trash-enclosure-v1.mjs`） |
| `rooftop_unit` | `tools/build-store-roof-v1.mjs`：父模型 `rooftop_unit/unit`，rotation `[25,200,0]`，scale 0.473，translation `[3.238,-0.89,0]`；其余视角 `heldDisplay` |
| `enclosure_gate` | `tools/build-trash-enclosure-v1.mjs`：父模型 `enclosure_gate/item`（右合页、关着的整扇门，OBJ），rotation `[20,200,0]`，scale 0.45，translation `[4.469,-4.132,0]`；其余视角 `heldDisplay` |
| `commercial_glass_double_door` | `[0,-2.3,0]` |
| `beverage_cooler` | 2026-09-30 起由 `tools/build-beverage-cooler-v2.mjs` 生成：rotation `[25,225,0]`、translation `[-1.78,-3.254,0.613]`、scale 0.328（V2 Mesh，2026-10-01 修正为正面朝外，见 [beverage_cooler_v2.md](beverage_cooler_v2.md)） |
| `chest_freezer` | 2026-10-01 起由 `tools/build-chest-freezer-v2.mjs` 生成：rotation `[25,225,0]`、translation `[-2.5,-1.063,2.138]`、scale 0.442（V2 Mesh，正面朝外，见 [chest_freezer_v2.md](chest_freezer_v2.md)） |
| `commercial_dumpster`（及 `_blue`、`_brown`、`_gray`） | 2026-10-02 起由 `tools/build-commercial-dumpster-v2.mjs` 生成，四个颜色相同：rotation `[25,225,0]`、translation `[2.83,-0.187,-2.747]`、scale 0.477（V2 Mesh，见 [commercial_dumpster_v2.md](commercial_dumpster_v2.md)） |
| `metal_trash_can` | 2026-10-02 起由 `tools/build-metal-trash-can-v2.mjs` 生成：rotation `[30,225,0]`、translation `[0,0.348,-0.079]`、scale 0.759（V2 Mesh，见 [metal_trash_can_v2.md](metal_trash_can_v2.md)） |
| `water_dispenser` | 2026-10-01 起由 `tools/build-water-dispenser-v2.mjs` 生成：rotation `[25,225,0]`、translation `[0.801,-3.198,-0.172]`、scale 0.453（V2 Mesh，正面朝外，见 [water_dispenser_v2.md](water_dispenser_v2.md)） |
| `vending_machine` | 2026-10-01 起由 `tools/build-vending-machine-v2.mjs` 生成：rotation `[25,225,0]`、translation `[-0.014,-2.883,-1.412]`、scale 0.397（V2 Mesh，正面朝外，见 [vending_machine_v2.md](vending_machine_v2.md)） |
| `modern_office_desk` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[30,135,0]`、translation `[0,0.176,0]`、scale 0.28（V2 Mesh，平移按网格包围盒居中），见 [modern_office_desk.md](modern_office_desk.md) |
| `modern_office_chair` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[25,135,0]`、translation `[0.025,-0.814,0]`、scale 0.62（V2 Mesh，平移按网格包围盒居中；V1 为 `[0,-1,0]`），见 [modern_office_chair.md](modern_office_chair.md) |
| `modern_lcd_monitor` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[25,135,0]`、translation `[0.445,0.847,0]`、scale 0.72（V2 Mesh，平移按网格包围盒居中），见 [modern_lcd_monitor.md](modern_lcd_monitor.md) |
| `office_computer_station` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[25,135,0]`、translation `[0.111,1.096,0]`、scale 0.72（V2 Mesh，平移按网格包围盒居中），见 [office_computer_station.md](office_computer_station.md) |
| `office_keyboard` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[30,135,0]`、translation `[0.025,6.274,0]`、scale 0.95（V2 Mesh，平移按网格包围盒居中） |
| `office_mouse` | 2026-10-03 起由 `tools/build-office-props-v2.mjs` 生成：rotation `[25,152,0]`、translation `[-0.101,12.47,0]`、scale 1.8（V2 Mesh，平移按网格包围盒居中） |
| `office_cubicle_partition` | `[0,-2.5,0]` |
| `restroom_partition` | `[0,-2.5,0]` |
| `restroom_stall_door` | `[0,-2.3,0]` |
| `commercial_flushometer_toilet`、`commercial_wall_mounted_sink`、`wall_mirror` | 2026-10-08 起由 `tools/build-restroom-fixtures-v2.mjs` 生成（真实尺寸的模型比一格小，所以按投影放大）：马桶 rotation `[30,200,0]`、translation `[1.204,-0.44,0]`、scale 0.95（2026-10-08：原来 225° / scale 1.25 时高 19.1 px，出格；视角改成 200°，缩放改成 0.95，现在 14.5 px）；洗手盆 rotation `[30,225,0]`、translation `[3.962,-7.115,0]`、scale 1.2；镜子正面朝外 rotation `[0,180,0]`、translation `[0,2.37,0]`、scale 1.25。见 [restroom_fixtures_v2.md](restroom_fixtures_v2.md)。V1 是 `[0,-0.25,0]` 和 `[-1.3,-3.4,0]` |
| `low_filing_cabinet` | `[0,0.25,0]` |
| `light_pole_base`、`light_pole`、`area_light`、`wall_pack`、`canopy_downlight` | 2026-10-09 起由 `tools/build-site-lighting-v1.mjs` 生成（`guiFit`：按投影缩放到约 15.2 px，筒灯 13 px，上限 4，平移按包围盒居中）：底座 `[30,225,0]` / `[0,1.657,0]` / 0.675，灯杆 `[30,225,0]` / 不平移 / 1，灯头 `[30,135,0]` / `[6.035,7.421,0]` / 1.037，壁灯 `[20,205,0]` / `[6.79,-8.13,0]` / 2.449，筒灯 `[30,225,0]` / `[0,-27.019,0]` / 4（2026-10-09 修：原来 5.443，见下面"缩放上限"）。见 [site_lighting_v1.md](site_lighting_v1.md) |
| `curb_sidewalk`、`curb_grass` | 2026-10-09 起由 `tools/build-curbs-v1.mjs` 生成：方块模型本身（地面格 + 北边一条完整路牙），rotation `[30,225,0]`，scale 0.616（按投影算到高约 15.5 px），不平移，见 [curbs_v1.md](curbs_v1.md) |

The retail shelf item duplicates its parent's non-GUI display contexts while overriding GUI only, preventing partial display inheritance from changing held or ground appearance. The restroom stall door's item transform matches its commercial-door template so `tools/build-restroom-doorway-runtime.mjs` does not undo the adjustment. Generated cabinet item values live in `tools/export-filing-cabinets-runtime.mjs`; re-exporting preserves the GUI positions. The office desk, chair, monitor, computer station, keyboard and mouse item models are generated by `tools/build-office-props-v2.mjs` since 2026-10-03 (V2 meshes): GUI rotation, scale and all non-GUI contexts keep their earlier values, the GUI translation is recomputed from the projected mesh bounds. The hand-authored `office_mouse.json` cube item model was replaced by a generated one (parent: the V2 block model); the old exporter `tools/export-office-props-runtime.mjs` was removed.

The other custom icons in the screenshot were already visually centered to within about half a GUI pixel and were left untouched: industrial locker, lead chest, gun maintenance bench, precision fabrication station, water dispenser, metal trash can and commercial dumpster (until their V2; now generated, see the table), modern office chair (until its V2; now generated, see the table), tall filing cabinet and multifunction printer.

Verification boundary: source screenshot alignment and static resource/export checks confirm the intended offsets, but a restarted graphical client review of the post-change inventory and hotbar is still pending. Headless compilation alone does not prove pixel-perfect in-game rendering at every GUI scale.

## 出格检查（2026-10-08）

用户截图：马桶图标上下都超出格子。前一次只改了视角没改缩放，没修好，同一天第二次才改对。

做法：按 1.20.1 的 GUI 变换（translation → rotationXYZ → scale → 平移 −0.5）把物品模型的 OBJ 顶点或 JSON 方块元素投影到屏幕上，量图标的宽、高、中心。格子里面是 16 × 16 px，原版方块图标约 15.7 px 高。脚本在草稿目录里，不在仓库里。

- 出格的三个都已改好：马桶（19.1 → 14.5 px）、仓储货架（18.1 → 14.9 px）、收银背柜（18.0 → 14.8 px）。马桶用户 2026-10-08 PASS；两个货架没有单独确认。
- 其它 JSON / OBJ 物品图标都在格子里。
- 没量的：
  - 冷柜、垃圾箱、门这类代码画的网格物品（`builtin/entity`）：它们的网格原点约定我没有弄对，投影结果不可信；用户截图里都在格子里。
  - 2D 图标。

## 缩放上限 4（2026-10-09）

用户截图：雨篷筒灯的图标往下偏了半格，压到下一格。用户说"之前和你说过好多次这种问题了，马桶就是"。
- 原因：原版读物品模型的 display 时，把缩放截到 −4..4、平移截到 −80..80 px（`ItemTransform` 的反序列化）。筒灯模型很小，生成器算出缩放 5.443，平移按 5.443 算成 −36.8 px；游戏里按 4 画，平移不变，所以偏下约 10 px。
- 上一轮的出格检查也没有算这个截断，所以没查出来。量图标的脚本（草稿目录）已经补上截断，以后的检查按截断后的值算。
- 全部 129 个带 display 的模型查过，超过 4 的：
  - `canopy_downlight` gui 5.443：已修，`guiFit` 加上限 4；
  - `wall_outlet` gui 4.2，平移 `[11.401,4.487,0]`：实际偏约 0.5 px，`tools/build-power-outlets-v1.mjs` 改成 4，平移重算为 `[10.858,4.274,0]`；
  - `9x19mm_casing` gui 4.08、`pistol_red_dot` fixed 5：没有平移，截到 4 后还是居中，只是比写的小一点，没改。
- 规则：生成器算 GUI 缩放时先截到 4，再按截断后的缩放算平移；模型太小（4 倍还不到 15 px）就接受小图标，或者另做放大的物品模型。
- 修好后量过：灯杆底座、灯杆、灯头、壁灯、筒灯、墙上插座的图标中心都在 (0, 0)，都在格子里。还没在游戏里看（资源改动，F3+T 重载即可）。

## 手持、掉落、展示框（2026-10-09）

用户截图：新做的灯具拿在手里"都飞上天了"。用户说"你下次改一个bug能不能顺带找一下其他的"：前一轮只修了 GUI 图标，没看别的视角。

- 原因：各生成器给手持、掉落、展示框写的是固定平移（例如第一人称 `[0, 2.5, 0]`）。这只适合像方块一样占满格子的模型。挂在格子顶上、贴在格子一边、放在格子底上的模型，拿在手里就跟着偏出去。
- 量法：草稿目录的脚本（`held_audit.mjs`）按 1.20.1 的变换（含缩放和平移的截断）投影每个物品模型，和原版方块（`block/block.json` 的参数）在同一视角里比中心距离和大小，单位是"一个原版方块在那个视角里的大小"。
  - 第一次量时脚本读不到原版的 `block.json`（不在 mod 的资源里），参照被当成了无变换，数字不准；改成写死原版参数后重量。
- 修法：共用的 `tools/item-held-display.mjs`（`heldDisplay(points, {size, rotations})`）。
  - 每个视角保留旋转；缩放让模型的最大投影尺寸 = size × 原版方块在这个视角里的尺寸（上限 4）；平移让模型包围盒中心落在原版方块中心。
  - 左手视角按原版的镜像规则算（原版对左手把 y、z 旋转和 x 平移取反）。
  - GUI 不用它，各生成器原来的 GUI 居中照旧。
- 修了的（量出来偏移超过 0.3 个方块、而且不是故意摆的姿势），都按新算法生成，修后各视角中心偏移 0：

| 物品 | 修前第一人称 / 展示框偏移 | 生成器 | size |
| --- | --- | --- | --- |
| `canopy_downlight` | 1.13 / 0.97 | build-site-lighting-v1 | 0.6 |
| `wall_pack`、`area_light`、`light_pole_base`、`light_pole` | 最多 0.34 | build-site-lighting-v1 | 0.75 / 1 / 1 / 1 |
| `wall_outlet` | 1.11（第三人称 1.35）/ 0.53 | build-power-outlets-v1 | 0.5 |
| `power_strip_3`、`power_strip_6` | 0.73 / 1.14，0.59 / 0.98 | build-power-outlets-v1 | 0.6 / 0.7 |
| `office_mouse`、`office_keyboard` | 1.05 / 1.41，0.47 / 0.85 | build-office-props-v2 | 0.5 / 1 |
| `emergency_light` | 0.61（第三人称 0.66） | build-building-lights-v1 | 0.7 |
| `commercial_wall_mounted_sink`、`wall_mirror` | 0.53，0.39（第三人称 0.49） | build-restroom-fixtures-v2 | 0.6 / 0.6 |
| `fuel_dispenser` | 0.53 / 0.37 | build-fuel-dispenser-v1 | 1 |
| `back_bar_shelf`、`storage_rack` | 0.43，0.41 | build-checkout-counter-v1、build-storage-rack-v1 | 1 / 1 |

- 量了但没改的：
  - 故意摆的手持姿势：撬棍、加油枪（2026-10-04 按原版手持链离线拟合）、油桶、地面标线；
  - 原版本来就这样：路缘、雨篷、方形面板灯、线形灯这类扁平的东西在展示框里偏低，和原版地毯、台阶一样；钢梁、钢撑、钢索用原版方块的变换，模型比一格大；
  - 偏得不多、又是手写 JSON 或只有一个视角：隔断（第一人称 0.38）、打印机（第三人称 0.34）、取水泵（展示框 0.47）、地下油罐（展示框 0.24）、货架单元（展示框）。
  - 代码画的网格物品（冷柜、垃圾箱、门等，`builtin/entity`）：脚本不知道它们的网格原点，量不准。
- 改完都跑了各生成器的 `--check`，被跟踪的文件里只有这些物品模型变了。用户 2026-10-09 实机看过，PASS（以灯具为主，其余物品没有逐个确认）。
