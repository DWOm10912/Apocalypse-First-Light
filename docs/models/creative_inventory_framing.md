# Creative inventory framing for recent custom blocks

Status: item GUI framing adjusted for the custom models visible in the creative Blocks tab. This affects the `gui` item display context (creative inventory, normal inventory and hotbar), not placed block geometry, VoxelShape, first/third-person holding, ground or item-frame transforms. No HUD overlay code is involved.

The user screenshot was measured against the center of each 18-pixel inventory slot at GUI scale 6. Corrections use the visible model bounding box, not the asymmetric pixel-mass centroid, so a chair or toilet can keep its intentional silhouette. Item GUI rotations and scales remain unchanged; only `display.gui.translation` changed. Values are in Minecraft item-model pixels:

| Registry ID suffix | GUI translation [X,Y,Z] |
| --- | --- |
| `retail_shelf_single` | `[-0.75,-2.9,0]` |
| `cash_register` | `[0,2.2,0]` |
| `commercial_glass_double_door` | `[0,-2.3,0]` |
| `beverage_cooler` | `[0,-2.1,0]` |
| `chest_freezer` | `[0,-0.1,0]` |
| `vending_machine` | `[0,-4,0]` |
| `modern_office_desk` | `[0,0,0]` |
| `modern_lcd_monitor` | `[0,0.7,0]` |
| `office_computer_station` | `[0,1,0]` |
| `office_keyboard` | `[0,6.2,0]` |
| `office_mouse` | `[0,12.4,0]` |
| `office_cubicle_partition` | `[0,-2.5,0]` |
| `restroom_partition` | `[0,-2.5,0]` |
| `restroom_stall_door` | `[0,-2.3,0]` |
| `commercial_flushometer_toilet` | `[0,-0.25,0]` |
| `commercial_wall_mounted_sink` | `[-1.3,-3.4,0]` |
| `low_filing_cabinet` | `[0,0.25,0]` |

The retail shelf item duplicates its parent's non-GUI display contexts while overriding GUI only, preventing partial display inheritance from changing held or ground appearance. The restroom stall door's item transform matches its commercial-door template so `tools/build-restroom-doorway-runtime.mjs` does not undo the adjustment. Generated office and cabinet item values live in `tools/export-office-props-runtime.mjs` and `tools/export-filing-cabinets-runtime.mjs`; re-exporting those assets preserves the GUI positions. `office_mouse.json` is a hand-authored item asset and is explicitly excluded from the office exporter so its existing geometry, rotation and other contexts are not overwritten.

The other custom icons in the screenshot were already visually centered to within about half a GUI pixel and were left untouched: industrial locker, lead chest, gun maintenance bench, precision fabrication station, water dispenser, metal trash can, commercial dumpster, modern office chair, tall filing cabinet and multifunction printer.

Verification boundary: source screenshot alignment and static resource/export checks confirm the intended offsets, but a restarted graphical client review of the post-change inventory and hotbar is still pending. Headless compilation alone does not prove pixel-perfect in-game rendering at every GUI scale.
