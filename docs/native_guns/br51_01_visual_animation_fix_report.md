# BR51-01 战斗步枪 空仓换弹旧弹匣补齐（2026-09-08）

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## 本轮范围

以用户最新确认优先：问题是 `reload_empty`，不是 `reload_tactical`。
本轮只补齐缺失弹匣；第一人称定位、左臂、recoil、第三人称问题未在本轮修复，仍待处理。

## 根因与实现

`additional_magazine` 有完整的原空仓换弹运动轨道，但没有几何或子骨骼。
另一套正式弹匣在 `mag_and_lefthand → magazine_bullet → magazine` 下，跟随手部且前段隐藏，
所以原本应留在枪上供左手顶出的对象缺失。

复用现有 `mag_standard` 与其 `hu2` 子树（24个cube），复制到：

`additional_magazine → empty_old_mag_standard → empty_old_hu2`

不复制扩容弹匣变体；几何坐标、pivot、UV和材质沿用原标准弹匣，未改贴图。
源bbmodel及runtime geo同步。新增子骨骼scale轨道，不修改任何原骨骼关键帧、声音或时长。

## 显示时间线

- 空仓换弹0–0.8333s：旧弹匣保留于原additional_magazine静止位置。
- 0.8333–1.3333s：沿原驱动的position/rotation轨道甩出。
- 1.4–1.5s：原父骨骼scale从1衰减至0；新增子骨骼在1.5s归零，直到动作结束。
- 其他七条状态：新增子骨骼scale=0。普通换弹原轨道逐项保持不变。
- 新弹匣继续使用原magazine_bullet/magazine轨道，未改变其显示时间或插入动作。

## 导出与检查

`tools/restore-br51_01-empty-magazine.mjs` 读取当前工程源文件，幂等补齐；完整迁移末尾调用它。
脚本断言所有原source animator和runtime bone轨道保持不变。
构建日志：`build/br51_01-empty-magazine-build.log`。
compileJava/processResources/build离线通过（30秒）；git diff --check通过；脚本重复运行成功，未重复添加几何。
未启动客户端；运行时接触、过渡混合及连续换弹回归均为NOT TESTED，不能据资源检查标PASS。

人工验收：打空后R，检查起始旧匣存在、约0.83s顶出接触、甩出及新匣插入；
连续重复三次；随后检查普通换弹、待机、射击、inspect、draw/put_away没有额外弹匣残留。
若接触仍错位，应记录对应时刻，不删减原手部动作掩盖。
