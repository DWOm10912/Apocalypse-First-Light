# BR51-01 Native Rig V2（Phase 1）

2026-09-27，已实现，**未实机验证**。离线逐帧校验通过。

本轮只把 BR51 的作者原始 TaCZ 风格骨骼迁移到 AFL 当前 Native Rig 语义（与 P9 / Blackridge 同一世代）。枪械外形、比例、UV、贴图、战斗数据、ADS、后坐和动画风格全部冻结，几何仍是 Cube / GeckoLib。Pure Mesh 与 PBR 属于 Phase 2，本轮未开始。

## 工具与文件

- 迁移工具：`tools/migrate-br51-native-rig-v2.mjs`。
  - 在 TaCZ 世代文件上执行迁移并写出。
  - 在已迁移的文件上，从 Git 原件（`BR51_RIG_BASE`，缺省 `HEAD`）重新推导并逐字节比对，只校验、不写入。
- 主枪源：`src/main/blockbench/br51_01.bbmodel`（84 组 / 828 cube / 8 条动画）。
- 运行时：`geo/br51_01.geo.json`（68 骨骼 / 642 cube）、`animations/br51_01.animation.json`（8 条）。
- 扩容弹匣参考源（只做源资产剥离，未接 Runtime、未 Mesh 化）：
  - `src/main/blockbench/br51_extended_magazine.bbmodel`：原 `mag_extended_2` + `hu7`，24 cube，未来的 BR51 Extended Magazine。
  - `src/main/blockbench/br51_drum_magazine.bbmodel`：原 `mag_extended_3`，51 cube，未来的 BR51 Drum Magazine。
  - 两者都保留原 cube、UV、贴图引用（`br51_01.png`）、相对枢轴和朝向。坐标原点移到主枪 `mag_standard` 挂点 `[0, 9.41883, 4.52607]`，与 `br51_extended_magazine_35` 的附件约定相同。
  - `mag_extended_1` 不进入新资产路线，需要时从 Git 历史恢复。

## 新层级

```
camera
root
├─ handling                     原 gun_and_righthand（枪整体 + 右手，与 P9 handling 相同职责）
│  ├─ gun_body                  原 br51_01
│  │  ├─ magazine               枪内标准弹匣（原 magazine 组，移入枪体）
│  │  │  ├─ mag_standard → hu2
│  │  │  └─ bullet → 2 → 3, 7   顶部可见弹
│  │  ├─ empty_old_mag          原 additional_magazine（空仓换弹甩出的旧匣）
│  │  │  └─ empty_old_mag_standard → empty_old_hu2
│  │  ├─ bolt → bullet_in_barrel, octagon7, bone10
│  │  ├─ br51_01_default …      枪身几何（未改）
│  │  ├─ muzzle_default, sight（仅源）, grip_default（仅源）
│  │  └─ sight_anchor, muzzle_anchor, muzzle_pos, ejection_anchor
│  └─ righthand → righthand_pos → right_hand_anchor
├─ mag_out                      新增；复制原 mag_and_lefthand 的运动通道
│  └─ reload_magazine           原 magazine_bullet（换弹时左手拿的新匣）
│     ├─ reload_mag_standard → reload_hu2
│     └─ reload_bullet → reload_bullet_2 → reload_bullet_3, reload_bullet_7
└─ lefthand                     承接原 mag_and_lefthand 的枢轴与通道
   └─ lefthand_pos              承接原 lefthand 的枢轴与通道
      └─ left_hand_anchor
```

## 旧骨骼 → 新骨骼

| OLD_BONE | NEW_BONE | REASON |
|---|---|---|
| `gun_and_righthand` | `handling` | 职责本来就是“枪整体运动 + 右手”，与 P9 `handling` 一致；只改名，通道不变 |
| `br51_01` | `gun_body` | 枪体容器，AFL 标准名 |
| `mag_and_lefthand` | `mag_out` + `lefthand` | 弹匣与左手分离。原通道完整复制给两条独立链，两边的世界变换与原来逐帧一致 |
| `lefthand` | `lefthand_pos`（通道与枢轴） | 左手自身运动下移一级（P9 同构），`lefthand` 空出来承接载体运动 |
| `lefthand_pos`（旧，静态） | —（合并） | 无旋转、无动画的恒等层，由上一行的 `lefthand_pos` 取代 |
| `magazine_bullet` | `reload_magazine` | 换弹时左手拿的新匣；原通道（缩放出现、旋转）不变 |
| `magazine`（在 `magazine_bullet` 下） | `magazine`（在 `gun_body` 下） | 正常装在枪里的标准弹匣，跟随枪体 |
| `bullet` / `2` / `3` / `7` | 同名（在枪内 `magazine` 下） | 顶部可见弹随枪内弹匣；`static_bolt_caught` 的 `bullet` 隐藏通道不变 |
| — | `reload_mag_standard` / `reload_hu2` / `reload_bullet*` | 换弹新匣的标准几何副本（逐 cube 相同） |
| `additional_magazine` | `empty_old_mag` | 空仓换弹甩出的旧匣；通道不变 |
| `empty_old_mag_standard` | 同名（几何叶子） | 旧匣的标准几何与显隐通道；与 `mag_standard`、`reload_mag_standard` 对称，也是附件替换点 |
| `shell` | `ejection_anchor` | P9 标准抛壳锚点名；位置不变，Profile 同步 |
| `positioning2` | —（删除） | 锚点容器；锚点直接挂到 `gun_body` 下 |
| `muzzle_flash`, `scope_pos`, `laser_pos`, `grip_pos`, `stock_pos` | —（删除） | TaCZ 附件 / 特效定位点，无 Runtime、无动画引用 |
| `constraint` | —（删除） | TaCZ 约束辅助，空组，只有无意义的常量通道 |
| `positioning`（`thirdperson_hand`、`ground`、`fixed`） | —（删除） | TaCZ 展示定位，AFL 用物品模型 display |
| `view`（`idle_view`、`iron_view`、`refit_*_view`） | —（删除） | TaCZ 视角辅助，AFL 用 `camera` 与 ADS profile |
| `mag_extended_1/2/3`（+ `hu3`、`hu7`） | —（删除） | 2 / 3 先提取为独立源，1 弃用 |
| `bolt2`, `charger`（`shoot` 中的孤立通道） | —（删除） | 引用不存在的骨骼，原本就不起作用 |

未改：`root`、`camera`、`bolt`、`bullet_in_barrel`、`righthand`、`righthand_pos`、两侧 `*_hand_anchor` 及其仅源参考手臂、`sight_anchor`、`muzzle_anchor`、`muzzle_pos`、`br51_01_default` 及其全部几何子组、`muzzle_default`，以及仅源的 `sight`、`grip_default`。

## 动画迁移

8 条动画全部保留：`static_idle`、`reload_empty`、`reload_tactical`、`static_bolt_caught`、`inspect`、`shoot`、`put_away`、`draw`。时长、循环模式、声音事件、所有原有关键帧的时间和数值都不变。

- **通道只按上表搬家**。涉及的组都没有静态旋转，通道搬到枢轴相同、链上位置相同的骨骼，因此是精确的，不做任何重采样或烘焙。
- **三种弹匣状态靠常量缩放关键帧显隐**，沿用原 `empty_old_mag_standard` 在每条动画都写显隐键的做法，8 条动画都显式写入：

| 动画 | `magazine`（枪内） | `reload_magazine`（新匣） | `empty_old_mag_standard`（旧匣） |
|---|---|---|---|
| `reload_empty` | 0 | 原有键：0.2333 s 起出现 | 原有键：1.5 s 前显示 |
| `reload_tactical` | 0 | 1 | 0 |
| `inspect` | 0 | 1 | 0 |
| 其余 5 条 | 1 | 0 | 0 |

- **切换无跳变**：`mag_out` / `lefthand` 的运动在这些动画的开始和结束都回到零位，所以“枪内 ↔ 新匣”的切换发生在两者重合的位置。

## 附件与锚点

| 项 | 状态 | 说明 |
|---|---|---|
| SIGHT_ANCHOR | PASS | `sight_anchor` 名称与世界位置不变，父级由 `positioning2` 改为 `gun_body`；`rifle_red_dot_01` 与其 `reticle` 全亮组不受影响 |
| MUZZLE_ANCHOR | PASS | `muzzle_anchor`（附件安装面）不变 |
| 裸枪枪口特效 | LEGACY_COMPAT | `muzzle_pos` + Profile `barrelExitOffset` 4.8125，等于消焰器前端。它与安装面不是同一点，Phase 2 重建枪管时再定 AFL 名称 |
| CASING_ANCHOR | PASS | `shell` → `ejection_anchor`，位置不变；`AflItems` 中 BR51 Profile 同步 |
| 弹匣热点 | PASS | `magazine_slot.hotspot_anchor = mag_standard` 不变 |
| 35 发扩容匣 | PASS | 替换骨骼改为 `mag_standard`、`reload_mag_standard`、`empty_old_mag_standard`（`AflItems`），`subtree=true`；三者枢轴相同 |
| 手部 | PASS | `right_hand_anchor` / `left_hand_anchor` 世界位置逐帧一致 |

- 通用 Runtime 没有修改：第三人称冻结姿态与维护台本来就隐藏 `reload_mag*`、`empty_old*` 与左右手；`mag_out` 没有几何。
- 没有新增 BR51 专用 Runtime。Java 只改了 `AflItems` 中的两处注册数据。

## 验证（离线）

迁移工具内置五项校验，另有两项外部核对：

1. 源文件与运行时动画：581 个关键帧的时间、数值、通道一一对应（导出时旋转 X/Y、位置 X 取反）。
2. 运行时 geo 与源文件导出组：68 个骨骼的名字、父级、枢轴、旋转、cube 数一致；另用 Blockbench 自身的 Bedrock 导出器核对，零差异。
3. 8 条动画都在；所有被引用的骨骼都存在；旧名全部清除。
4. 视觉一致：8 条动画每 1/60 秒共 765 个采样时刻，新旧 rig 的全部可见 cube 世界包围盒（最多 594 个）完全相同；左右手、瞄具、枪口、裸枪枪口、抛壳、`camera` 锚点位置差小于 1e-6。
5. 几何与 UV：主枪 879 → 828 cube，即减去 99 个扩容匣 cube，加上 48 个换弹副本（与原件逐项相同）；贴图列表不变；两个参考源的 cube 与原件一致。
6. `verify-native-model-names.mjs` 的 BR51 部分通过。该工具的 P9 部分仍找旧 `geo/p9_01.geo.json`，是早已存在的过期问题，与本轮无关。
7. `compileJava --offline` 通过。

未实机验证：换弹、检视时三种弹匣的显隐与手部位置，第三人称，维护台，35 发扩容匣装在新 rig 上的效果。

## 仍存在的兼容与 Phase 2 前置

- **LEGACY_COMPAT**：`muzzle_pos`（裸枪枪口特效）；通用 Runtime 排除表中的 `additional_magazine`（BR51 已不再使用，HR55 等其他旧资产可能仍用，保留）。
- **Phase 2 前必须解决**：
  - 枪身几何组仍是作者原名（`br51_01_default`、`bone*`、`octagon*`、`qianguan`、`group*`），Pure Mesh 化时按部件重建，不在本轮改名。
  - 仅源的 `sight`（旧红点）与 `grip_default` 仍留在主枪源里，Phase 2 决定删除或独立。
  - 定义裸枪出口的 AFL 锚点，取代 `muzzle_pos`。
  - `empty_old_mag` 仍挂在枪体下（P9 在 root 下），这是为了保持动画精确而保留的差异，重做换弹动画时再统一。
  - 扩容匣与弹鼓的 Mesh 重建与接入是独立任务。
- **已可彻底删除（本轮已删）**：`constraint`、`positioning2`、`muzzle_flash`、`scope_pos`、`laser_pos`、`grip_pos`、`stock_pos`、`positioning` 及子组、`view` 及子组、`mag_and_lefthand`、`magazine_bullet`、`additional_magazine`、`gun_and_righthand`、`shell`、`mag_extended_1/2/3`、`hu3`、`hu7`，以及孤立通道 `bolt2`、`charger`。
- **历史工具**：`tools/prepare-br51_01-white-gun.mjs` 等旧 BR51 工具面向 TaCZ 世代层级，已不适用于 V2 源，只作历史保留。
