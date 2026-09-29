# HR55 Native Rig V2（Phase 1）

2026-09-28，已实现，离线逐帧校验通过，`compileJava` 通过。**未实机验证。**

这一轮做两件事：
1. 把可编辑源同步成与运行时完全一致，源文件从此是唯一准绳；
2. 把枪械师的 TaCZ 风格骨骼迁移到 AFL 原生 Rig 语义，与 P9 V2、BR51 同一世代。

枪械外形、比例、cube、UV、贴图、战斗数据、ADS、后坐和全部动画都冻结，几何仍是 Cube / GeckoLib。Pure Mesh + PBR 是下一轮（Phase 2）。

## 工具与文件

- **迁移工具**：`tools/migrate-hr55-native-rig-v2.mjs`。
  - 在 TaCZ 世代的文件上：先同步、再迁移，然后写出。
  - 在已迁移的文件上：从 git 原件（`HR55_RIG_BASE`，缺省 `HEAD`）重新推导，再逐字节比对（忽略 CRLF / LF 差异），只校验、不写入。
- **主枪源**：`src/main/blockbench/hr55.bbmodel`。组数 55 → 40，cube 数 820 → 748，动画 9 条。
- **运行时**：`geo/hr55.geo.json`（骨骼 50 → 35，cube 742 个不变）、`animations/hr55.animation.json`（9 条）。两个文件改为标准两空格缩进的 JSON，内容等价。
- **扩容弹匣参考源**：`src/main/blockbench/hr55_extended_magazine.bbmodel`。
  - 原来是主源里不导出的 `mag_extended_1`，72 个 cube，cube、UV、贴图引用保持原样。
  - 坐标原点就是 `mag_standard` 的原点 (0, 0, 0)，作为以后 HR55 弹匣配件的安装基准。
  - 没有接入运行时。

## 1) 同步：以运行时为准修正源文件

| 项目 | 源文件（旧） | 运行时（现行，已写回源文件） |
| --- | --- | --- |
| `sight_anchor` 枢轴 | (0, 16.28035, −23.10661) | (0, 11.18035, −2.10661)，步枪红点的标定依据 |
| 音效节点 | 原始名（`reload_tactical`、`hr55_draw` 等）；`shoot` 带一个本地文件路径 `E:\Download\...\shoot.ogg`；`inspect` 在 0.025 秒 | 已注册的事件名 `apocalypse_firstlight:hr55_*`；`shoot` 没有节点（开火声由 `NativeGunActions` 播放 `hr55_fire`）；`inspect` 在 0.0417 秒 |
| `inspect` / `inspect_empty` | 时间在 1/40 秒网格，例如双手和枪体 189 个关键帧、左手锚点缩放 514 个关键帧 | 已对齐到 1/24 秒网格并精简：158 个和 9 个 |

- 一共同步了 1 个枢轴、7 条动画的音效节点、20 个动画通道。
- 同步后，源文件和运行时的 4113 个关键帧逐个一致（时间、数值；旋转 x / y 和位置 x 按导出约定取反）。
- 贴图：源文件内嵌图与 `textures/item/hr55.png` 的像素逐字节相同，无需同步。
- cube：源文件与运行时一致（只有小数第 4 位的导出舍入差异）。

## 2) 新层级

```
camera
afl_equip_motion
└─ root
   └─ handling                 原 root_ash12_1：整把枪 + 双手；每条动画都带同一个固定偏移（滚转 2°）
      ├─ righthand → righthand_pos → right_hand_anchor
      ├─ gun_body              原 root_ash12：枪体，换弹 / 检视时相对双手运动
      │  ├─ magazine           枪内弹匣；换弹时它就是被取下 / 甩出的旧匣
      │  │  ├─ bullet → bullet1, bullet2   顶部可见弹（cube，Phase 3 换动态弹药）
      │  │  └─ mag_standard
      │  ├─ reload_magazine    原 additional_magazine：换上的新匣，只在换弹中显示
      │  │  └─ reload_mag_standard
      │  ├─ muzzle_default, t（group, group2, group3, bone → octagon）, bolt → bone2, sight   枪身几何，未改
      │  └─ sight_anchor, muzzle_anchor, muzzle_pos, ejection_anchor
      └─ lefthand → lefthand_pos → left_hand_anchor
```

**与 BR51 的区别**：左手仍在 `handling` 下，没有提到 `root` 下面。原作者在 `handling` 上放了一个每条动画都相同的固定偏移（滚转 2° 加微小平移）。把左手移出去，就得把这个偏移折算进左手的旋转关键帧，而欧拉角插值在关键帧之间做不到精确，所以保留原层级。

## 旧骨骼 → 新骨骼

| 旧 | 新 | 说明 |
| --- | --- | --- |
| `root_ash12_1` | `handling` | 只改名，枢轴和通道不变 |
| `root_ash12` | `gun_body` | 只改名 |
| `additional_magazine` | `reload_magazine` | 只改名。仍被第三人称和维护台当作“仅第一人称 / 换弹用”隐藏（`reload_mag*` 前缀和名单） |
| `mag_1`、`ash12`、`mag_and_lefthand`、`positioning2` | —（去掉这一层） | 无旋转、无动画、不直接含 cube 的纯容器，子骨骼上移一层，世界位置不变 |
| `shell` | —（删除） | 抛壳锚点改用它下面的 `ejection_anchor`（同一枢轴）。`AflItems` 中 HR55 的抛壳锚点由 `shell` 改为 `ejection_anchor` |
| `scope_pos`、`muzzle_flash` | —（删除） | 下面的 `sight_anchor`、`muzzle_anchor` 先移到 `gun_body` 下 |
| `gun_and_righthand`、`constraint`、`laser_pos`、`grip_pos`、`positioning`（含 `thirdperson_hand`、`ground`、`fixed`） | —（删除） | TaCZ 辅助骨骼，都不含 cube；只有空骨骼 `constraint` 带动画通道，一并删除 |
| `mag_extended_1`（不导出） | 移到 `hr55_extended_magazine.bbmodel` | 见上 |

- 骨骼名 `bolt`、`bone2`、`magazine`、`mag_standard`、`bullet*`、`reload_mag_standard`、双手和锚点都不变。
- 数据 JSON（`sight_slot` / `muzzle_slot` 锚点）不需要改。

## 校验（全部由迁移工具执行）

| 编号 | 内容 | 结果 |
| --- | --- | --- |
| V1 | 源文件 ↔ 运行时：每个关键帧、音效节点、动画长度 | 4113 个关键帧一致 |
| V2 | 运行时 Geo ↔ 源文件导出组：名称、父级、枢轴、旋转、每根骨骼的 cube（按导出舍入容差逐个配对） | 35 根骨骼，742 个 cube |
| V3 | 动画集合、长度、循环模式、音效节点不变；动画用到的骨骼都存在；必需骨骼存在；已删除的骨骼不再出现；四个锚点都在 `gun_body` 下 | 9 条动画 |
| V4 | 每条动画每 1/60 秒一帧：所有可见 cube 的世界包围盒和 UV，以及双手锚点、`sight_anchor`、`muzzle_anchor`、`muzzle_pos`、抛壳锚点、`camera` 的世界位置，迁移前后完全相同 | 1385 帧，每帧最多 742 个可见 cube |
| V5 | 主源 cube（含 UV 与导出标记）与原件相同，只少了抽出的 72 个；参考源保存原始 cube；贴图不变 | 820 → 748 |

另外已重跑：
- `tools/verify-rifle-red-dot-01.mjs` 通过：HR55 的红点装配点 (0, 11.48438, −2.1138) 与导轨偏差 0，与迁移前一致；
- `tools/verify-rifle-suppressor-01.mjs` 通过。

`tools/verify-native-model-names.mjs` 失败是早就存在的问题：它要找已退役的 `geo/p9_01.geo.json`，与本轮无关。

## 未实机验证

- 所有动画（包括两条检视动画）在游戏里的观感：离线逐帧校验保证与迁移前一致。
- 第三人称、维护台、野外附件视图、抛壳位置（锚点改名，位置不变）。
