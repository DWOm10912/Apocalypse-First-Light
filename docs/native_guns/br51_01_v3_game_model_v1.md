# BR-51 V3 游戏模型（M14 机构，低模 + 烘焙贴图 + LabPBR）V1

## 状态（2026-10-21）

**用户 2026-10-22 实机 PASS**（第四轮之后："可PASS，提交这些内容"），提交到 master。旧存档里 35 发弹匣换成 30 发没在旧存档里试过。下面各节写的"没进游戏看过"指当时的状态。

**已接入运行时，编译通过。** 用户 2026-10-21 第一次进游戏看过，提了 7 个问题，已一次改完（第 9.1 节）；第二轮提了拿弹匣卡和手臂位置，第三轮又提了右手穿模，都已改完（第 9.2 节），右手最后一版没进游戏看过。 `apocalypse_firstlight:br51_01` 的 Profile、第三人称持枪（`GunStance`）、物品模型、物品栏图标都已指向 `br51_01_v3_native`。弹匣改走弹匣系统（[magazine_system_v1.md](magazine_system_v1.md) 第 10 节）：20 发、30 发、50 发弹鼓三种弹匣物品，换弹换整个弹匣，散装子弹手动压进弹匣。V2 的资源（`br51_01` 的 geo、网格、贴图、动画、`br51_01_in_hand.json`）还在仓库里，只是不再被物品引用，和 P9 留着 v2 一样。

用户原话（2026-10-21）：
- 「从模型开始到贴图/音效/动作都重新设计」「后续每把枪都需要做到P9现在的这个质量」
- 「原版外观 + M14 正确机构」：外形是我们自己的，机构和操作件按 M14 来（[br51_concept_v1.md](br51_concept_v1.md)）
- 「新的模型能用之前那份动画吗，之前那份动画看起来像是现代枪械」→「把骨骼按 M14 搭好，动画也重做」
- 「换弹6个声音得独立生成」「检视不能复用换弹里面的声音吗，否则音效会不一样」「弹匣出和入应该一起」
- 「先不做花式换弹，先做普通的和空仓 你看看现在p9是怎么做的 然后一次性接入游戏，包括我们之前做的第一人称+第三人称，弹匣的问题，压弹等等，不要漏东西」

## 1. 文件

| 用途 | 路径 |
|---|---|
| Blender 工程（概念高模、游戏低模、烘焙件） | `design/items/br51_m14/source/blender/br51_concept.blend` |
| 生成和烘焙脚本 | `design/items/br51_m14/source/blender/`：`game_build.py`、`game_bones.py`、`game_cull.py`、`game_uv.py`、`game_bake.py`、`game_save_bakes.py`、`game_compose.py`、`game_export.py`（外形 `receiver.py`、`barrel.py`、`stock.py`、`mags.py`，胡桃木 `walnut_tex.py`） |
| 低模导出（Blockbench 像素坐标） | `design/items/br51_m14/source/br51_01_v3_game_lowpoly_gun.json`（枪）、`br51_01_v3_game_lowpoly_mags.json`（30 发、弹鼓） |
| Blockbench 源（动画由动画脚本 `--write-source` 写入） | `src/main/blockbench/br51_01_v3_native.bbmodel` |
| 源贴图（2048，LabPBR） | `src/main/blockbench/textures/br51_01_v3_native{,_s,_n}.png`、`br51_01_v3_mags{,_s,_n}.png` |
| 构建 / 运行时导出 | `tools/build-br51-01-v3-native.mjs`、`tools/export-br51-01-v3-native.mjs`（`--check` 只核对） |
| 动画 | `tools/author-br51-01-v3-native-animations.mjs`（`--write-source`、`--write-runtime`） |
| 弹匣物品 | `tools/build-br51-01-magazines.mjs`（`--check`；也写 30 发和弹鼓的 Blockbench 源 `src/main/blockbench/br51_01_v3_mags.bbmodel`） |
| 压弹（第三人称） | `tools/build-br51-01-mag-load.mjs`（`--check`） |
| 物品栏图标 / wiki 图 | `tools/render-br51-01-inventory-icon.mjs`（`--check`）、`tools/render-br51-01-wiki.mjs` |
| 声音 | `tools/build-br51-01-sounds.mjs [sourceDir]`（默认 `E:/Download`，要 ffmpeg） |
| 运行时 | `geo/br51_01_v3_native.geo.json`（32 根骨骼）、`meshes/br51_01_v3_native.aflmesh.json`、`textures/item/br51_01_v3_native{,_s,_n}.png`、`textures/item/br51_01_v3_mags{,_s,_n}.png`、`models/item/br51_01_v3_native_in_hand.json`、`animations/br51_01_v3_native.animation.json` |
| 弹匣 | `geo/` + `meshes/` + `models/item/`：`br51_01_magazine`、`br51_01_extended_magazine`、`br51_drum_magazine_50` |
| 压弹 | `player_props/br51_01_magazine.json`、`player_props/762x51mm_round.json`、`player_actions/gun/br51_01_mag_load.json` |
| 概念图 | `design/items/br51_m14/sheets/` |

重建顺序：Blender 里 `game_build.py` → `game_bones.py` → `game_cull.py` → `game_uv.py` → `game_bake.py`（每次一个 pass）→ `game_save_bakes.py` → `game_compose.py` → `game_export.py`；然后 `node tools/build-br51-01-v3-native.mjs` → `node tools/export-br51-01-v3-native.mjs` → `node tools/author-br51-01-v3-native-animations.mjs --write-source --write-runtime` → `node tools/build-br51-01-magazines.mjs` → `node tools/build-br51-01-mag-load.mjs` → `node tools/render-br51-01-inventory-icon.mjs`。

## 2. 网格和骨骼

- 坐标沿用旧 BR-51 的：枪膛轴线 y 11.4375，枪口 z −28（朝 −Z），+X 是右侧。所以手持 `display` 和第三人称的身体缩放不用重算。
- 低模 `LO_Q` 0.38，55 个 part，三角形当量 15033（上限 16384；2026-10-21 照门旋钮内移后，原 15053）：`gun_body` 11105，弹匣三份各 934（`magazine`、`reload_magazine`、`empty_old_mag`），`trigger` 286，`bolt` 272，`oprod` 258，`selector` 110，`safety` 92，`bolt_lock` 68，`mag_latch` 40。
- 动的零件按 M14：
  - `oprod`：机匣右侧的拉机柄（操作杆）；
  - `bolt`：枪机，先绕枪膛轴转开锁，再往后走；
  - `bolt_lock`：左侧空仓挂机；
  - `safety`：扳机护圈前面的保险；
  - `mag_latch`：弹匣后面的卡榫；
  - `selector`：机匣右后侧快慢机；
  - `magazine`：绕前钩（枢轴 `MP` [0, 10.637, 1.549]）摇进摇出。
- 其他锚点：握把 `HP` [0, 8.3, 12.8]，照门和瞄准点 [0, 12.748, 9.31]，`muzzle_anchor`、`ejection_anchor`、`right_hand_anchor`、`left_hand_anchor`、`maintenance_anchor`、`camera`。

## 3. 动画（第一人称）

`tools/author-br51-01-v3-native-animations.mjs` 生成，用的是 P9 V3 脚本的求解、烘焙和动作语言，按抵肩步枪重新标定。这一轮只做普通档：**普通换弹和空仓换弹，没有熟练档（花式换弹）**，P9 的 Lv0/4/7 分档 BR-51 暂时不做。

| 片段 | 时长 s | 声音（秒） |
|---|---|---|
| `static_idle` / `empty_idle` | 0.25 | |
| `shoot` | 0.25 | |
| `draw` | 0.6 | 0 掏枪 |
| `first_draw` | 1.4 | 0 掏枪、0.57 拉机柄、0.77 放机柄 |
| `put_away` | 0.55 | 0 收枪 |
| `reload_tactical` | 2.4 | 0.40 按卡榫、0.45 弹匣出、1.42 弹匣入（1.70 s 卡到位） |
| `reload_empty` | 2.9 | 同上，加 2.04 拉机柄、2.29 放机柄 |
| `reload_tactical_no_mag` | 1.75 | 0.77 弹匣入（1.05 s 卡到位）：枪里没有弹匣时，见下文 |
| `reload_empty_no_mag` | 2.25 | 同上，加 1.39 拉机柄、1.64 放机柄 |
| `inspect` / `inspect_empty` | 7.2 | 3.60 按卡榫、3.66 弹匣出、5.84 弹匣入 |
| `inspect_no_mag` / `inspect_empty_no_mag` | 5.0 | |
| `unload` / `unload_empty` | 1.45 | 0.32 按卡榫、0.38 弹匣出，左手带到胸挂、0.9 收纳、0.95–1.3 回护木，枪 0.62–1.0 回待机（2026-10-24 第二版，[magazine_system_v1.md](magazine_system_v1.md) 第 3.1 节；旧版 1.3 s 0.62 收手） |

- 换弹按 M14：左手拇指往前推卡榫，弹匣前端一翻取下；新弹匣先把前钩挂进去，再往后一扳卡住。空仓换弹最后右手拉一下拉机柄，枪机从挂机位置放回去推弹上膛。
- 弹匣在 34 tick 换（数据 `presentation.mag_in_tick` / `empty_mag_in_tick`），空仓换弹在片段结束时上膛。
- 枪里没有弹匣时（取下弹匣以后再按换弹）播 `reload_*_no_mag`：原换弹去掉前 0.65 s（按卡榫、拔旧弹匣），左手从护木直接去腰间拿新弹匣，之后和原换弹逐帧相同，弹匣在 21 tick 换（[magazine_system_v1.md](magazine_system_v1.md) 第 3 节）。
- 检视用换弹里的同一组声音（用户要求），所以检视里弹匣出、入的声音和换弹完全一样。

## 4. 第三人称

- 不需要专门的第三人称换弹动作：`SkeletonGunStance` 播第一人称的片段，手用 IK 跟着 `right_hand_anchor` / `left_hand_anchor` 走。
- **低位预备**（2026-10-22，`gun/br51_01_idle`，[姿势循环](../dev/p9_stance_loops_v1.md) 4.7）：平时枪托在肩窝、枪口往下 30°；瞄准、开枪后 1.5 s、换弹、检视时举到肩上。第一人称低头 25–45° 时视图模型滑出、身体接过枪（[第一人称身体](../rendering/first_person_body_v1.md) 3.5.1），和 P9 一样。编译通过，离线核对过，没进游戏看过。
- 身上背着时（`GunStance.Carried`）：步枪类，实际大小 0.307（真枪 1.118 m 对枪架 1.05 m），身体缩放 1.25，枪托点 [0, 7, 26.7]，护木支点沿用。
- **压弹**：手里拿着 BR-51 的弹匣、身上有 7.62×51 mm 散装子弹时，按住使用键一发一发压。第三人称动作 `gun/br51_01_mag_load` 由 `tools/build-br51-01-mag-load.mjs` 从 P9 的 `gun/p9_01_mag_load` 换算（时间、手、胸前位置照搬，道具换成 BR-51 的弹匣和子弹，子弹关键帧平移到 BR-51 的供弹口）。细节见 [magazine_system_v1.md](magazine_system_v1.md) 第 10 节。

## 5. 弹匣

| 物品 | 发数 | 形状 | 贴图 | 抬高 px | 质量 kg | 格子 |
|---|---|---|---|---|---|---|
| `br51_01_magazine` | 20 | 钢制直弹匣（枪自己的 `magazine` 骨骼） | `br51_01_v3_native` | 3.11883 | 0.2 | 1×1 |
| `br51_01_extended_magazine` | 30 | 弧形 | `br51_01_v3_mags` | 4.82568 | 0.3 | 1×2 |
| `br51_drum_magazine_50` | 50 | 弹鼓，侧面有观察窗 | `br51_01_v3_mags` | 4.14022 | 0.8 | 2×2 |

- 质量是估计值。三个弹匣都在创造栏「配件」里，空和满各一个；没有配方（配方重做前不加）。
- 枪的容量 = 弹匣 + 膛内 1 发：21、31、51。旧的 35 发扩容弹匣 `br51_extended_magazine_35` 在旧存档里换成 30 发弹匣。
- 名字：BR-51 20 发弹匣 / BR-51 20-Round Magazine，BR-51 30 发扩容弹匣 / BR-51 30-Round Extended Magazine，BR-51 50 发弹鼓 / BR-51 50-Round Drum。

## 6. 声音

用户的 Firefly 录音（2026-10-21），一类声音一条录音，换弹和检视用同一组剪辑（`tools/build-br51-01-sounds.mjs` 的头注释有剪切点和响度）：

- `br51_01_fire`、`br51_01_fire_near`（开火）
- `br51_01_magazine_release`、`br51_01_magazine_out`、`br51_01_magazine_in`（从同一条「弹匣出 + 入」录音里切）
- `br51_01_charging_back`、`br51_01_charging_release`（「拉机柄拉 + 放」）
- `br51_01_draw`、`br51_01_put_away`
- `br51_01_mag_load`、`br51_01_mag_load_last`（压弹，P9 的录音降调）
- 消音 `br51_01_suppressed` 沿用。

旧的 `reload_tactical_1..3`、`reload_empty_1..4`、`inspect_slide_back/release` 删了，旧存档里的这些声音 ID 直接忽略（`NativeWeaponLegacyMappings`）。

## 7. 物品栏图标

`tools/render-br51-01-inventory-icon.mjs` 用游戏低模和它自己的 Base Color 贴图离线渲染：从右侧看，俯 14°、画面里转 −32°（枪口朝右下），256 px 透明底，最长边占 0.88，4×4 超采样。写到 `textures/item/br51_01_inventory.png` 和 `src/main/blockbench/inventory_icons/br51_01_inventory.png`。

Wiki 的图（Blockbench 这轮连不上，和 T15 一样先用离线渲染，同一套游戏网格和贴图）：`node tools/render-br51-01-wiki.mjs` 写枪的头图 `design/wiki/gun_renders/raw/br51_01.png`（右前上方 3/4 视角，再用 `wiki/scripts/frame-renders.mjs` 裁成统一画框）和三个弹匣的图标 `design/wiki/icons/raw/<id>.png`（512 px）。`wiki/icons.json` 给三个弹匣指了 Blockbench 源：20 发用枪的 `br51_01_v3_native.bbmodel`（只留 `magazine` 组），30 发和弹鼓用 `src/main/blockbench/br51_01_v3_mags.bbmodel`（`tools/build-br51-01-magazines.mjs` 生成，`mag30` / `mag50` 两组，内嵌贴图）。Main 以后用 Blockbench 渲染时会替换这几张离线图（现在 `icons.mjs` 报「render is stale」是这个原因）。

**HUD 武器列表的剪影**（2026-10-22，用户：「需要你再去blockbench里面截图一张2D向左的原厂枪械剪影」）：`node tools/render-br51-01-hud.mjs` 写 `textures/gui/gun/br51_01_hud.png`（1184×221）。原厂状态（标配 20 发弹匣，没有配件）的游戏低模，从左侧正交看，枪口朝左，画法和别的枪的剪影一样（平涂灰色、左上打光、轮廓暗一圈、2×2 超采样，同 `tools/build-equipment-meshes-v1.mjs` 的 `drawHud`）。Blockbench 这轮还是连不上，所以用离线渲染，模型和视角相同。枪数据 `presentation.hud` 从 60×12 改成 59×11，和新图的宽高比（5.36）一致（`LoadoutHud` 按这个比例画）。`--check` 只比对，不写文件。

## 8. 删掉的文件

- 工具：`build-br51-drum-magazine-50.mjs`、`build-br51-extended-magazine-35-v2.mjs`、`verify-br51-drum-magazine-50.mjs`、`verify-br51-extended-magazine-35.mjs`、`apply-br51_01-world-icons.mjs`。
- 35 发扩容弹匣的 geo、网格、物品模型、贴图；旧弹鼓贴图 `br51_drum_magazine_50{,_n,_s}.png`；`native_attachments/br51_drum_magazine_50.json`（弹鼓专用的空仓换弹 `reload_empty_drum`）。
- Blockbench 源：`br51_extended_magazine_35.bbmodel`、`br51_extended_magazine_35_mesh.bbmodel`、`br51_drum_magazine_50_mesh.bbmodel`、`extract_br51_magazine_35.cjs`。
- 旧文档 [br51_01_v2_pure_mesh_pbr.md](br51_01_v2_pure_mesh_pbr.md) 等 V1/V2 的记录只作历史。

## 9. 测试

- 离线：上面所有 `--check` 脚本通过；`sounds.json` 引用的文件都在；lang 1725/1725 键，行对齐；`./gradlew compileJava` 通过。
- 开发测试改成弹匣规则（`BR5101CombatGameTests`、`NativeInspectGameTests`、`MagazineSystemGameTests`、`MaintenanceAttachmentTests`、`Br51MagazineProbe`、`EquipmentTooltipProbe`、`MeshMigrationCapture`），**没有跑**：`compileDevJava` 现在有一个和本轮无关的错误（`ReactorIntegrationGameTests.java:96` 还引用 Jade 的 `IServerDataProvider`，Jade 在 b1275384 移出了开发环境）。

## 9.1 第一轮实机反馈的修改（2026-10-21，编译通过，没进游戏看过）

用户第一次进游戏后提的问题，一次改完：

- **第一人称左手**（「左手摆的位置太诡异了」：前臂竖直吊在枪下面正中）：
  - 改动：握点 `LG_POINT` [-0.35, 9.35 + 1 − ARM_HALF, −7.0]，前臂方向 `levelArm([-0.88, -0.5, 0.5], [0.3, 1, 0])`，肘往左下后方伸。腰射时前臂从手伸到画面左下边，屏幕 x 从 0.18 到 −0.27，和真人托护木一样。
  - 握点比第一版往后挪了 1.4 个单位，因为第三人称在换弹开头和结尾用这个握点时，左臂伸展度原来要到 1.04，挪后回到 0.98。离弹匣前端还有 8.45 个单位。
- **检视节奏**（「不要晃那么快，一点节奏感都没有」）：
  - 改动：检视加长到 7.2 s（无弹匣检视 5.0 s 不变）。弹匣拿出来以后先停稳，再慢慢转一次看侧面：`MAG_TURN` [8, −16, 10]，从 4.75 s 转到 5.15 s。之后在 5.35 s 送回去，整段只叠一层很慢的漂移（周期 1.4 s）。原来是连续晃动。
  - 声音：3.60 按卡榫、3.66 弹匣出、5.84 弹匣入。
- **无弹匣换弹**（「没有弹匣，结果播放了取弹匣的动画，没有音效」）：见第 3 节和 [magazine_system_v1.md](magazine_system_v1.md) 第 3 节。
- **照门旋钮和护耳的缝**（「模型箭头那边有缝隙，太大了很明显」）：
  - 原因：原来旋钮内侧离护耳 3.6 mm，轴（hub）也没伸到护耳，中间是空的。
  - 改动（`design/items/br51_m14/source/blender/receiver.py`）：
    - 高低旋钮移到 x −19.0…−11.6，方向旋钮移到 11.6…17.6，离下面的裙边 0.6 mm（离护耳 1.6 mm）。没有贴到 x ±11 的裙边平面上，避免共面闪烁。
    - 两根轴从 ±9.6 起，插进护耳。
    - 方向旋钮的螺母和螺钉跟着内移。
  - 重建：高模和低模重建，只换枪身贴图那部分低模（`LO_Q` 0.38，弹匣贴图那部分不动）。枪身贴图重新烘焙，生成链和图标、wiki 图也都重跑了。
- **第三人称拧手**（「头不能扭那么远……手都拧成麻花了」）：长枪的侧身角从 45° 改成 30°（`GunStance.Kind.RIFLE.chestTwist`，见 [gun_handling_reference_v1.md](../dev/gun_handling_reference_v1.md) 4.5）。头相对胸口最多偏 30°，身体跟着视线转。
- **地上的弹匣卡**（30 发 4390、弹鼓 6941 个三角形）：开光影时平滑部件的三角形现在也进区块（[render_performance_v1.md](../dev/render_performance_v1.md) 2026-10-21 一节）。

## 9.2 第二轮实机反馈的修改（2026-10-21，编译通过，没进游戏看过）

- **拿着弹匣卡**（「手持BR51弹鼓会降低60多帧」「BR51的所有弹匣拿出来都会卡，原装的也是」）：弹匣、弹匣顶上的子弹、静态网格物品原来在 CPU 上每帧逐顶点画，现在走 Vextris 显存缓存（`client/MeshItemCache`，[render_performance_v1.md](../dev/render_performance_v1.md)「手里的弹匣、子弹和网格物品走显存缓存」）。装在枪上的 30 发和弹鼓也跟着走缓存。
- **手臂再收一轮**（截图标注：右手往前到扳机，左手往里收）：
  - 右手：握点 `RH_POINT` 的 z 从 11.35 改到 9.6，手往前挪到扳机（扳机 z 8.3）后面。**这一版进游戏后穿模严重**（枪托握把和枪托后段穿过手和前臂），第三轮重做，见下。
  - 左手：握点不动，前臂横向分量从 −0.88 改到 −0.6（`levelArm([-0.6, -0.5, 0.5], [0.3, 1, 0])`），前臂往枪这边收。
  - 骨骼离线检查：站立空仓换弹拉机柄那段（原来左手 1.04）现在通过。剩下的失败项都是冲刺、检视里拿着弹匣看的那段和开镜时手臂皮肤离眼睛太近，以前就有。
- **右手第三轮**（「右手的位置还是不对，现在穿模太严重了」「往左下，让右手前面卡在扳机上，要自然」）：
  - 握点 `RH_POINT` [-0.13, 6.99, 7.72]（上一版 [0.5, 8.45, 9.6]，往左 0.63、往下 1.46），前臂 `levelArm([0.2, -0.7, 1])`（上一版 [0.22, -0.42, 1]），往下斜得更多，从枪托下面过去。
  - 离线拟合（游戏低模的枪托和扳机护圈顶点对手的方块）：手的前端面贴着扳机下端（扳机尖 y 7.83、z 8.35，离前端面 0.05）；握把下沿压进手顶 0.1–0.4；枪托不再穿手和前臂；扳机护圈后半段伸进手的前上角（最深 2.0），相当于手指伸进护圈。
  - 骨骼离线检查 14 项不通过（上一版 22 项）：冲刺时掏枪、待机、开火、卸弹匣现在通过。剩下的是检视里拿着弹匣看的那段、冲刺换弹，以及开镜时手臂皮肤离眼睛近（0.054 格，上一版 0.040），以前就有。

## 10. 已知问题 / 没核对的

- **没进游戏看过**：机构转向（枪机绕轴 +25°、开锁抬 +15°、卡榫 −12°）、手的姿势、弹匣的手持 `display` 都是算出来的。
- 骨骼离线检查（`./gradlew skeletonCheck --offline`，侧身 30°）：
  - 空仓换弹拉机柄那段（2.05–2.50 s，无弹匣版 1.40–1.85 s）第一轮第三人称左手伸展度 1.04；第二轮改了手臂以后通过。
  - 检视里拿着弹匣看的那段（2.45–3.40 s）左手离枪，伸展度到 1.28。这一项以前就不通过。
  - 冲刺时的各个片段也不通过，原因同上，以前就有。
- 红点和消音器的安装位置、`ads_center` 还是旧模型的。M14 没有导轨，红点会和照门撞，配件这一轮不管（用户 2026-10-21「暂时不需要管动画和其他通用配件」）。
- ~~HUD 剪影还是旧枪的~~：2026-10-22 换成 V3 的离线渲染（第 7 节），没进游戏看过。
- 30 发和弹鼓压弹时用 20 发的道具点，供弹口离子弹差 0.65 / 0.40 px。
- 压弹动作没有 AFL Rig 源文件。
- 没有熟练档换弹。
