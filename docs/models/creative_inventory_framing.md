# Creative inventory framing for recent custom blocks

Status: item GUI framing adjusted for the custom models visible in the creative Blocks tab. This affects the `gui` item display context (creative inventory, normal inventory and hotbar), not placed block geometry, VoxelShape, first/third-person holding, ground or item-frame transforms. No HUD overlay code is involved.

The user screenshot was measured against the center of each 18-pixel inventory slot at GUI scale 6. Corrections use the visible model bounding box, not the asymmetric pixel-mass centroid, so a chair or toilet can keep its intentional silhouette. Item GUI rotations and scales remain unchanged; only `display.gui.translation` changed. Values are in Minecraft item-model pixels:

| Registry ID suffix | GUI translation [X,Y,Z] |
| --- | --- |
| `retail_shelf_single` | `[-0.75,-2.9,0]` |
| `cash_register` | 2026-09-30 起由 `tools/build-cash-register-v2.mjs` 生成：rotation `[30,225,0]`、translation `[-0.038,1.482,0]`、scale 0.711（V2 Mesh，见 [cash_register_v2.md](cash_register_v2.md)） |
| `storage_rack` | 2026-10-04 起由 `tools/build-storage-rack-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中 `[0.99,-3.786,0]`，scale 0.5。物品模型是单独一个货架（中段加两端立柱），见 [storage_rack_v1.md](storage_rack_v1.md) |
| `fuel_dispenser` | 2026-10-04 起由 `tools/build-fuel-dispenser-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中 `[1.527,-2.177,0]`，scale 0.27。物品模型是整台加油机（含四把挂着的枪和路缘），见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md) |
| `underground_fuel_tank_gasoline`、`underground_fuel_tank_diesel` | 2026-10-04 起由 `tools/build-underground-fuel-tank-v1.mjs` 生成：父模型是对应的整罐方块模型，rotation `[30,225,0]`，scale 0.13，translation `[0,1.589,0]`（2026-10-05 加卸油口后重新居中），见 [underground_fuel_tank_v1.md](underground_fuel_tank_v1.md) |
| `fuel_dispenser_sump` | 2026-10-05 起由 `tools/build-fuel-station-sump-v1.mjs` 生成：父模型是整个底槽（2 × 2 × 1 格），rotation `[30,225,0]`，scale 0.3，translation `[1.7,-1.23,-2.67]`，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md) |
| `submersible_fuel_pump`、`pump_manhole_cover`、`fuel_fill_cover_gasoline`、`fuel_fill_cover_diesel` | 2026-10-05 起由 `tools/build-fuel-station-sump-v1.mjs` 生成：一格大小，rotation `[30,225,0]`，scale 0.625，不平移；井盖和卸油口盖的物品是底座加关着的盖子，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md) |
| `intake_pump` | 2026-10-05 起由 `tools/build-intake-pump-v1.mjs` 生成：父模型是 `intake_pump/item`（整台泵，开关关、灯灭），rotation `[30,225,0]`，按投影自动缩放到 15 px（scale 0.4，translation `[-2.008,3.197,0]`），见 [intake_pump_v1.md](intake_pump_v1.md) |
| `fuel_canopy_column`、`fuel_canopy_ceiling`、`fuel_canopy_light`、`fuel_canopy_fascia` | 2026-10-04 起由 `tools/build-fuel-canopy-v1.mjs` 生成：rotation `[30,225,0]`，scale 0.62，translation 按网格包围盒居中（立柱底座 `[0,0.866,0]`、吊顶和灯板 `[0,0,0]`、边檐直段 `[-0.077,0.146,0]`）；灯板的物品模型是灯板加未亮的灯面，见 [fuel_canopy_kit_v1.md](fuel_canopy_kit_v1.md) |
| `fuel_island_curb`、`fuel_island_end`、`fuel_island_bollard` | 2026-10-04 起由 `tools/build-fuel-island-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中（路缘 `[0,3.458,0]`、端头 `[-1.013,2.945,0]`、防撞柱 `[0,0.163,0]`），scale 0.62（防撞柱 0.55），见 [fuel_island_kit_v1.md](fuel_island_kit_v1.md) |
| `fuel_nozzle_gasoline`、`fuel_nozzle_diesel` | 同一生成器：油枪侧面朝外，rotation `[20,120,0]`，translation `[-0.166,-0.041,0]`，scale 1.55；手上的显示参数 2026-10-04 按原版手持变换离线拟合，见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md) |
| `checkout_counter`、`checkout_counter_display`、`checkout_counter_gate`、`back_bar_shelf` | 2026-10-04 起由 `tools/build-checkout-counter-v1.mjs` 生成：rotation `[30,225,0]`，translation 按网格包围盒居中（柜台 `[0.332,-0.376,0]`、带货架 `[0.132,-0.376,0]`、通道门 `[0.384,-0.644,0]`、背柜 `[1.131,-4.198,0]`；2026-10-04 台面加高到 16 px 后重新生成），scale 0.62（背柜 0.5）。物品模型是直段加两端封板（带货架款再加托盘），通道门是关着的静态模型 `checkout_counter/gate_item`，见 [checkout_counter_v1.md](checkout_counter_v1.md) |
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
| `commercial_flushometer_toilet`、`commercial_wall_mounted_sink`、`wall_mirror` | 2026-10-08 起由 `tools/build-restroom-fixtures-v2.mjs` 生成（真实尺寸的模型比一格小，所以按投影放大）：马桶 rotation `[30,225,0]`、translation `[2.369,-0.266,0]`、scale 1.25；洗手盆 rotation `[30,225,0]`、translation `[3.962,-7.115,0]`、scale 1.2；镜子正面朝外 rotation `[0,180,0]`、translation `[0,2.37,0]`、scale 1.25。见 [restroom_fixtures_v2.md](restroom_fixtures_v2.md)。V1 是 `[0,-0.25,0]` 和 `[-1.3,-3.4,0]` |
| `low_filing_cabinet` | `[0,0.25,0]` |

The retail shelf item duplicates its parent's non-GUI display contexts while overriding GUI only, preventing partial display inheritance from changing held or ground appearance. The restroom stall door's item transform matches its commercial-door template so `tools/build-restroom-doorway-runtime.mjs` does not undo the adjustment. Generated cabinet item values live in `tools/export-filing-cabinets-runtime.mjs`; re-exporting preserves the GUI positions. The office desk, chair, monitor, computer station, keyboard and mouse item models are generated by `tools/build-office-props-v2.mjs` since 2026-10-03 (V2 meshes): GUI rotation, scale and all non-GUI contexts keep their earlier values, the GUI translation is recomputed from the projected mesh bounds. The hand-authored `office_mouse.json` cube item model was replaced by a generated one (parent: the V2 block model); the old exporter `tools/export-office-props-runtime.mjs` was removed.

The other custom icons in the screenshot were already visually centered to within about half a GUI pixel and were left untouched: industrial locker, lead chest, gun maintenance bench, precision fabrication station, water dispenser, metal trash can and commercial dumpster (until their V2; now generated, see the table), modern office chair (until its V2; now generated, see the table), tall filing cabinet and multifunction printer.

Verification boundary: source screenshot alignment and static resource/export checks confirm the intended offsets, but a restarted graphical client review of the post-change inventory and hotbar is still pending. Headless compilation alone does not prove pixel-perfect in-game rendering at every GUI scale.
