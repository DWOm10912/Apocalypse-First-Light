# BR51-01 战斗步枪 V1 白版枪资源整理

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## 当前选择（2026-09-08）

V1固定20发，只启用 `mag_standard`。没有配件Item、UI、NBT槽、动态容量或xmag选择逻辑。

| 几何 | V1 runtime | 源资产/未来用途 |
|---|---|---|
| mag_standard / hu2 | 保留 | STANDARD，20发 |
| mag_extended_1 / hu3 | 排除 | 未采用，备用 |
| mag_extended_2 / hu7 | 排除 | 未来EXTENDED，未实现 |
| mag_extended_3 | 排除 | 未来DRUM，未实现 |
| empty_old_mag_standard / empty_old_hu2 | 保留 | 空仓换弹临时标准匣，不是款式或Item |

所有被排除几何仍在bbmodel中，组和cube均visibility=false、export=false。
没有从源永久删除资产；没有改变贴图、原几何尺寸、Java、手臂参数或Display。

## 配件准确映射

- 红点：`sight`及`bone15_illuminated`、`bone16`。包含光学主体/亮点及其自身安装结构，排除整个子树。
- 激光：嵌套在sight下的`laser_lopro_mini`，包括`laser_illuminated`、`bone9`、`bone15`、`flashlight_illuminated`、`bone17/18/19`，一并排除。组合附件上的灯光几何不单独留下。
- 前握把：`grip_default`，排除。枪身`br51_01_default`及其全部子树、护木/导轨、默认枪托和枪口保留。
- `scope_pos`、`laser_pos`、`grip_pos`等空定位骨骼保留，不产生附件几何。
- 本次合计17组、279个cube成为source-only；runtime剩80个bone。
- 未对命名不明的枪身部件大规模删除。机械瞄具最终可用性尚未目视验证，不能以保留枪身子树代替验收。

## 换弹

任务模板再次称tactical有顶空气；用户此前明确确认问题为empty、tactical没有问题。
当前tactical原轨道也未发现magazine_bullet/magazine/mag_standard的隐藏scale。
因此没有虚构新的tactical缺失根因或修改其动作：本轮修正的是四种款式同时导出的互斥遗漏。

普通换弹使用 `mag_and_lefthand → magazine_bullet → magazine → mag_standard/hu2`：
0–0.5s保留装入姿态，0.5333s开始取出，0.7667–1.1333s位于下方，1.4s接近插入，1.6333s归位。
`additional_magazine`和临时标准匣在tactical中隐藏；不新增第二只战术换弹弹匣。

空仓换弹继续使用上述正式标准匣，以及
`additional_magazine → empty_old_mag_standard → empty_old_hu2`。
临时旧匣在0–0.8333s留在枪上，随后按原轨道甩出，1.4–1.5s父级缩小、1.5s子级隐藏。
新匣仍走原magazine_bullet/magazine轨道。详见 `br51_01_visual_animation_fix_report.md`。
临时副本保留现名，因为只用于empty，没有扩展为tactical用途。

## 导出与保护

`tools/prepare-br51_01-white-gun.mjs` 从当前bbmodel层级确定排除子树，递归设置源隐藏/不导出，并过滤runtime geo。
`tools/migrate-br51_01.mjs` 完整迁移末尾先补标准旧匣，再执行白版选择，防止重新迁移恢复外挂。
不要为了刷新用户编辑而运行完整外部迁移。
本轮动画JSON未修改，源animations对象断言完全相同；保持正式8条，不恢复任何old/xmag分支。
脚本验证保留标准匣、临时匣、bolt和左右anchor，无悬空父节点，排除对象无当前动画驱动轨道。

## 验证边界

构建证据：`build/br51_01-white-gun-build.log`。
compileJava/processResources/build离线通过（30秒）；git diff --check通过。
build/resources中geo与源runtime文件逐字一致；标准匣和旧匣24个cube及UV逐对象相同。
本轮未启动客户端；以下全部NOT TESTED：普通/空仓换弹接触与交接、机械瞄具可用性、
隐藏前握把后的左手接触、全部8状态实机回归。没有为补偿未验证握空问题擅改anchor。
第一人称定位、左臂完整性、后坐力、第三人称旧问题仍未在本轮解决。

人工清单：idle检查标准匣及白版外观；非空R、打空R各重复3次，观察取出/插入及结束无残留；
shoot/bolt_caught/inspect/draw/put_away确认无附件闪现；重点看idle左手是否接触护木，记录握空的动作与时点。

COMMIT = NO；PUSH = NO。
