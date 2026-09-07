# BR51-01 战斗步枪 Blockbench 参考手臂（2026-09-08）

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

`src/main/blockbench/br51_01.bbmodel` 的左右 `hand_anchor` 下已补入 AFL 参考手臂。
模板来自 `p9_01_v03_8_fire_slide_cleanup.bbmodel`，保留UV与参考材质；
当前按 BR51_01 的显示比例换算参考尺寸，没有复制手枪的 anchor 姿势。

- `right_arm_reference` / `left_arm_reference`：默认可见。
- 对应 `_slim` 备选：默认隐藏，预览时与 Classic 二选一，避免重叠。
- 四组及四个 cube 均为 `export=false`，不会进入游戏几何。
- `afl_arm_reference_source_only.png` 内嵌在源文件；参考面的 texture index 指向该贴图，不指向枪体贴图。
- 当前预览尺寸 Classic 约5.51111×20.8×5.51111，Slim约4.13333×20.8×5.51111，
  由共享手臂呈现比例0.62/0.78/0.62除以BR51_01 Display比例0.45得到。
- 左右anchor握持端改至源手臂Y=20端，静态旋转归零；参考臂沿局部-Y延伸。
- 校正同时更新源、runtime geo和Display适配，但不改变原动画关键帧、枪体cube或贴图。
- 以上为静态绑定校正，尚未完成运行时逐帧视觉验收。

`tools/add-br51_01-reference-arms.mjs` 用于首次补齐，已有参考组时拒绝覆盖，保护用户摆位。
外部源完整重新迁移时 `tools/migrate-br51_01.mjs` 会调用该脚本，再调用
`tools/calibrate-br51_01-binding.mjs` 应用当前静态绑定及显示校准。
不要为了刷新编辑器而重新运行完整迁移；保存当前编辑内容后重开 `br51_01.bbmodel` 即可。

验证：JSON可解析；4个参考组/4个cube，Classic可见，Slim隐藏，全部不导出；
校准脚本断言源动画对象未改。compileJava/processResources/build通过（42秒）。
本次校准未启动客户端、未做Blockbench实时目视验收。

后续空仓换弹补齐：`tools/restore-br51_01-empty-magazine.mjs` 在源中新增旧弹匣子树，
不改这里的手臂绑定、预览规格或原手部动画。详见 `br51_01_visual_animation_fix_report.md`。
白版资源选择由 `tools/prepare-br51_01-white-gun.mjs` 在完整迁移最后执行；附件保留源中但隐藏、不导出。
手臂参考仍可见且不导出；去掉前握把后的接触尚未实机确认，本轮不改手臂关键帧。
HIP外层定位现由 `tools/apply-br51_01-hip-pose.mjs` 同步source/runtime Display；局部anchor、参考尺寸未改。
详见 `br51_01_first_person_hip_pose_fix.md`，不将整体位移视为手臂接触已通过。
