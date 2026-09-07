# Native Gun Data JSON V1

## 文件与真值源

- `src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json`：`apocalypse_firstlight:p9_01`
- `src/main/resources/data/apocalypse_firstlight/native_guns/br51_01.json`：`apocalypse_firstlight:br51_01`
- datapack 使用同样的 `data/<namespace>/native_guns/<id>.json` 路径覆盖；一枪一份完整定义，不做字段合并。
- Item 仍由 AflItems 注册，运行中的 Item 按 ID 查询 NativeGunData；NativeGunDefinition 的 DEV 默认常量也从打包 JSON 读取，不是运行时缓存入口。

## Schema

| 字段 | 语义 |
| --- | --- |
| ammo / casing | 已注册物品 ID；弹壳渲染使用对应 item model |
| magazine_capacity | 正整数容量 |
| fire.mode / interval_ticks | V1只支持 semi；正整数间隔 |
| damage.base | 非负身体伤害 |
| damage.falloff_start / effective_range / max_range | 格；标称射程仍不参与命中计算 |
| damage.min_damage_multiplier | 0–1 |
| accuracy.base_spread_degrees / profile | 0–45°锥形半角；default或battle_rifle公共姿态预设 |
| reload.tactical_seconds / empty_seconds | 非负秒，向上取整到服务端tick |
| noise.radius / tinnitus | 非负半径；是否参与现有耳鸣累积系统 |
| ads.time_seconds / fov_multiplier | 非负进入/退出时长；正数FOV倍率 |
| sight_slot（可选） | anchor 名称、mount_offset 局部 xyz、ads_center 模型 xyz、accepts 兼容 SIGHT 物品 ID 数组；没有此字段即不支持。详见 [手枪红点 V1](pistol_red_dot_v1.md) |
| recoil | NativeRecoilProfile同名数值字段，见下文 |

recoil：verticalMin/Max、horizontalMin（负的左侧最大幅度）/horizontalMax（右侧最大幅度）、horizontalLeftMin/RightMin（正数下限）、maxVertical/Horizontal；recoveryDelay、cameraRecoveryTime、modelRecoveryTime、horizontalRecoveryTime 为秒；modelPitch/Back/Yaw/Roll 为现有模型后坐参数，horizontalContinueChance 为0–1概率。上下限/有限数/恢复时间/cap均校验。

爆头倍率仍由 NativeHeadshots 全局配置控制，不在单枪JSON重复定义。两把枪的姿态倍率原本不同，JSON只选择公共预设，不复制倍率数组；基础散布独立可调。

## Java 边界

注册、HUD布局/图标路径约定、骨骼/定位、动画关键帧及声音marker、Hitscan/衰减/恢复算法、网络、NBT、耳鸣/Noise实现仍留Java。疾跑禁止ADS、平滑退出、停止疾跑按右键恢复，以及创造无限备弹均是公共规则。

## 重载与同步

使用 AddReloadListenerEvent / SimpleJsonResourceReloadListener 扫描全部命名空间；完整校验后原子替换快照。登录与 /reload 完成通过现有网络通道的服务端到客户端数据包同步；集成服务端与客户端分开保存，断开连接清空客户端副本。SIGHT V1 新增装拆请求后协议版本为16，双方需同版本模组。配件兼容声明同样随 gun data 同步，禁用兼容后已存配件停止显示，但仍能拆回副手。

后续射击、弹量读取、换弹、噪声/耳鸣、ADS时间/FOV、后坐与弹壳读取新数据。重载取消正在进行的枪械操作与旧射速冷却；现存弹量按新容量安全clamp，不补回被截断的弹药。动画资源/声音marker不随时长改写，因此大幅调整换弹时长需另外校准美术，不能把JSON时长误当成动画关键帧编辑。

非法字段记录 ID、字段/原因，整枪保留上次有效定义；首次失败的正式枪使用打包JSON默认值。不会安装半截对象。第三方新增Item应提供有效打包默认定义；单独添加JSON不注册新物品。零ADS时间立即到目标；零散布无随机偏转；零换弹时间于后续服务端结算。

## 主表同步

`node tools/sync-native-gun-data-doc.mjs` 生成极简枪械主表，`--check` 检查漂移。参数来自JSON，名称来自lang，爆头默认值及蹲姿倍率来自公共Java规则。主表不记录实现过程。

## 后续新增枪械

1. Java注册NativeGunItem并绑定definition ID，保留现有渲染/动画接入流程。
2. 添加同ID的完整JSON、合法弹药/弹壳物品及资源。
3. 按现有约定接入展示定位与名称，运行文档生成器、构建和重载回归。

## 验证边界

DEV测试创建临时datapack，调用与 /reload 相同的服务器资源重载入口，验证伤害18→10、噪声112→64、间隔3→5、非法容量-1拒绝后保留完整旧值，随后恢复18/112/3。图形客户端ADS/FOV、后坐手感及多人远端同步需另行实机验收，不以服务端测试替代。

验证结果：`native-json-final-test.log` 一轮65/65通过；最终快照重复运行 `native-json-verified.log` 为64/65，唯一失败是既有 `nativenoiseflatdistances` 的感染者调查路径断言。JSON重载、非法字段回退、两枪弹药/换弹/射击和新增平衡断言均通过。测试临时覆盖均已撤销，正式JSON保持18/112/3。构建单独复验记录 `native-json-final-build.log`，主表 `--check` 通过；未提交/推送。
