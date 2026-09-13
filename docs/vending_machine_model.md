# 自动售货机静态模型 V1

已完成 intact / broken 两份可编辑 Blockbench 模型、共用贴图与正面/斜角预览，并实现 Runtime Integration V1：同一方块双状态、12 槽展示、撬棍破玻璃、配件风格提示与音效。运行时说明与验证边界见 [vending_machine_runtime.md](vending_machine_runtime.md)。开门、售卖、GUI 和 worldgen 尚未实现。

## 资产路径

- `src/main/blockbench/afl_vending_machine_intact.bbmodel`
- `src/main/blockbench/afl_vending_machine_broken.bbmodel`
- `src/main/blockbench/textures/afl_vending_machine.png`
- `src/main/blockbench/previews/afl_vending_machine_intact_front.png`
- `src/main/blockbench/previews/afl_vending_machine_intact_angle.png`
- `src/main/blockbench/previews/afl_vending_machine_broken_front.png`
- `src/main/blockbench/previews/afl_vending_machine_broken_angle.png`
- `src/main/blockbench/previews/afl_vending_machine_comparison.png`

预览来自真实 Blockbench viewport，置于中性灰背景；对照图保持原始宽高比。同一视角下两版使用相同相机参数。现有预览拍摄于本轮背板 UV 加深之前，仍展示原背板颜色；最新 UV 以 `.bbmodel` 和运行时资源为准。

## 尺寸、材质与状态差异

16 units = 1 block。两版包围盒完全一致：X [-7.82,7.82]，Y [0,32]，Z [-7.84,7.84]。占地中心为 X/Z=0，落地面 Y=0，适配 1×1×2；根组 origin [0,0,0]，旋转为零。正面朝 -Z / NORTH；从正面看支付面板在右侧，即源坐标负 X 一侧。

intact 为 270 cubes；broken 为 291 cubes。两版均为 Free Model 格式、纯 cube、56 个唯一命名分组。269 个非玻璃 cube 的坐标、尺寸、UV 与分组关系相同，机壳、灯箱、货道、支付区和取货区保持同一设计。

intact 使用一片完整透明玻璃；broken 使用 22 块边缘残片，中央主展示区为真实几何开口，残片贴图进一步表现锯齿断口和局部裂纹。机器本体与关闭的门框完整保留，没有地面散落碎片或悬挂门。

共用 128×128 RGBA 图集：深灰机壳、略亮边框、中灰层板、深色取货仓、冷蓝灰玻璃和小面积支付区点缀。主体用低幅度明暗分层，无随机颗粒、现实品牌、说明海报或食品占位模型。灯箱为素色乳白面；本轮未实现游戏发光效果。两份 bbmodel 都内嵌同一张 PNG，并保留 `textures/afl_vending_machine.png` 相对路径。

## 分组与交互预留

```text
machine_root
├─ body_shell
│  ├─ base_assembly
│  └─ ventilation
├─ top_sign_box
├─ interior_shell
├─ interior_rows
│  └─ interior_row_01..04
│     └─ lane_r1_c1..lane_r4_c3
│        └─ delivery_loop_r1_c1..delivery_loop_r4_c3
├─ display_layout
│  └─ display_r1_c1..display_r4_c3
├─ glass_door
│  ├─ front_frame
│  └─ glass_panel (intact) / broken_glass_parts (broken)
├─ control_panel
│  └─ keypad
└─ pickup_bin
   ├─ pickup_surround
   └─ pickup_hatch
```

四层、每层三个陈列货道，带托盘、导轨和简化方形送货环；不含真实机械传动模拟。12 个 `display_r*_c*` 空参考组：从正面左至右 X=4.83/1.23/-2.37，底至顶 Y=8.0/12.7/17.4/22.1，Z=-3.6。运行时 V1 对应三个可用货道，共 12 槽；旧 8 槽库存迁移到两侧货道。

`glass_door` 门轴 origin=[7.03,17.6,-7.35]，位于正面左边铰链侧。前框、玻璃和门锁属于该组；支付面板与机壳独立。`pickup_hatch` origin=[1.28,5.3,-5.85]，位于内凹取货挡板上边缘。两者只有轴点与分组，没有动画或运行时骨骼导出。

## 检查与后续接入边界

- 已检查两版正面及 3/4 视角，支付面板同向，外壳和门框重合。
- 已检查两版共用非玻璃几何、分组 origin、层级、包围盒及根原点一致。
- 所有 cube 完成逐面 UV，绑定唯一有效贴图；贴图索引无缺失，UV 在 128×128 范围内。两份源文件内嵌贴图与外部 PNG 的 SHA-256 一致。
- 玻璃使用双面显示，玻璃 cube 的 `render_order=in_front` 仅为 Blockbench 预览配置；未来接入需选择适当的透明渲染方式。
- Runtime V1 已创建 Java / BlockState / 静态 baked-model 资源，不使用 Geo 或动画资源。构建和服务端测试与图形客户端验收分开，详见运行时文档；原有截图仅证明 Blockbench 源资产，不代表游戏验证。

复现建模脚本：`scripts/build_vending_machine_blockbench.js`，在新建空 Free Model 工程中运行，并设置 `globalThis.aflVendingVariant` 为 `intact` 或 `broken`。该脚本重建新项目，不能对含用户改动的工程直接运行。预览导出脚本为 `scripts/capture_vending_machine_previews.js`。
