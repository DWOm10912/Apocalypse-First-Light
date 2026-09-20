# 简易隔音耳罩 V1 / Simple Hearing Protection

状态：已接入代码与资源；用户已验收 HEAD 装备/渲染及世界声音衰减。本轮 Tooltip 与耳鸣强度调整尚未实机验证；不运行客户端、GameTest 或截图。

## 物品与装备

- Registry ID：`apocalypse_firstlight:simple_hearing_protection`。
- `item/SimpleHearingProtectionItem.java` 是 `Item implements Equipable, HearingProtection`，不是 `ArmorItem`。
- 原版 `EquipmentSlot.HEAD`，堆叠 1，无耐久、护甲、韧性、击退抗性。与头盔互斥，不使用 Curios。
- 右键调用 1.20.1 `Equipable.swapWithEquipmentSlot`，遵循原版交换与绑定诅咒规则；可从头部装备栏取下。装备音为 `minecraft:item.armor.equip_leather`。
- Tooltip 使用现有灰色、非斜体介绍风格，显示短介绍及“降低外界声音 / 减轻耳鸣”两行功能；不公开精确百分比，不更改武器属性颜色。

## 已验收资产与运行时模型

- 可编辑源：`src/main/blockbench/simple_hearing_protection.bbmodel`，本轮未改。
- 导出输入：`E:/Download/simple_hearing_protection.json` 和同名 `.png`，本轮未改。
- Runtime：`src/main/resources/assets/apocalypse_firstlight/models/item/simple_hearing_protection.json`。
- Texture：`src/main/resources/assets/apocalypse_firstlight/textures/item/simple_hearing_protection.png`，64×64，与验收 PNG 字节一致。
- Texture 引用：`apocalypse_firstlight:item/simple_hearing_protection`。
- 126 个元素、坐标、旋转、UV 和全部导出 display 数值原样保留。`display.head.translation = [0, 8.5, 0]`；不额外缩放或量化角度。
- Forge loader `apocalypse_firstlight:hearing_protection` 在 client-only `client/model/HearingProtectionModelLoader.java` 注册。Runtime 将原 `elements` 放入 `afl_elements`，避免 Forge 调用自定义 loader **之前**的 Vanilla element 角度白名单校验。
- Loader 同时读 `angle/axis` 和 Blockbench 单轴 `x/y/z` 旋转；复用原版坐标/UV 校验，再将原始角度放回 `BlockElementRotation`。由 Forge `ElementsModel` → `FaceBakery` 烘焙普通 baked quads，完整保留 ±32.27564°、±62.44719°、±90°。不采用 22.5°/45°近似。当前只支持此资产使用的单轴、无 rescale 元素，多轴/rescale 明确报错而非错误显示。
- `CustomHeadLayer` → head pose → `ItemDisplayContext.HEAD` → 标准 ItemRenderer/baked model。没有 BEWLR、自定义 Player RenderLayer 或第一人称佩戴 overlay。
- Loader 仅由 `Dist.CLIENT` MOD subscriber 注册，common Item/manager 不引用客户端类。

## 听觉防护入口

`equipment/HearingProtection.java` 分离两种倍率，`HearingProtectionManager` 只读 HEAD stack：

| 状态 | worldSoundMultiplier | impulseProtectionMultiplier |
| --- | --- | --- |
| 未佩戴（或无玩家） | 1.00 | 1.00 |
| 简易隔音耳罩 | 0.50 | 0.50 |

以后其他 HEAD 防护物品可实现同一接口，独立配置世界声音与冲击防护；本轮没有军用电子耳罩。

### 本地世界声音

Forge `PlaySoundEvent` 只能替换播放实例，不能统一覆盖最终音量 clamp、持续声音更新和装备变化；因此使用一个 client-only `HearingProtectionSoundEngineMixin`，列在 mixin JSON 的 `client` 数组。

- `play` 的 float/category 音量调用重定向到 per-SoundInstance overload；该 overload 的 RETURN 是唯一乘倍率位置，位于原版音量 clamp **之后**。
- Tickable、初次播放、音量分类刷新使用相同计算，不包装/修改 SoundInstance，不改变原始音量、衰减距离或声音事件。
- `tickNonPaused` 检测 HEAD 防护倍率变化时，重新计算现有 world channels 的音量，使非 ticking/streaming 长声音也在戴上/摘下后恢复。不会基于已减半 gain 再乘；不修改 Options/sliders。
- BLOCKS、HOSTILE、NEUTRAL、PLAYERS、WEATHER、AMBIENT、走 Minecraft SoundEngine 的空间 VOICE，以及空间 MASTER 声音均适用。
- MUSIC、RECORDS、`minecraft:ui.*`、relative + attenuation NONE 的 UI/local feedback 不适用。**AMBIENT 是 relative/NONE 的例外**：原版 `forLocalAmbience` 同样使用它，属于世界环境听觉。
- `ExplosionTinnitusSound` 及 `apocalypse_firstlight:explosion_tinnitus` 显式排除，避免内部耳鸣再次减半。
- 非空间 VOICE 或第三方绕过 Minecraft SoundEngine 的语音不承诺覆盖；不拦截第三方独立音频引擎。

只改变当前客户端听者的最终音量。不修改 SoundEvent、服务端广播、其他玩家音量、AI hearing、Noise System 或枪声传播半径。

### 既有听觉冲击

当前枪声与爆炸是两个既有服务端输入，尚无统一 exposure accumulator；但最终都调用 `AflNetwork.sendTinnitusImpulse`。该发送入口调用一次 `HearingProtectionManager.effectiveTinnitusSeverity`，把发送给客户端的最终 severity 乘 HEAD 防护倍率（简易耳罩为 0.50）：

- `GunshotExposureTracker.accumulateListeners`：每发 exposure 保持原值进入 `GunshotExposureAccumulator.recordShot`，积累/阈值行为不受耳罩改变；原来不参与枪声耳鸣的枪仍不参与。输出 raw severity 后才在共用发送入口乘 0.50。
- `ExplosionTinnitusEvents.onDetonate`：既有 `n²` raw severity 保持原值用于触发判定，再由共用发送入口乘 0.50；没有新增爆炸检测或伤害机制。
- 客户端 episode/envelope 从 packet 中的 effective severity 派生耳鸣音量、时长和已有视觉反馈，不再次乘倍率。由于初始音量常数项、持续时间公式及客户端阈值仍存在，最终音量/时长不等同于恒定减半；防护针对的是 severity 标量。
- `ExplosionTinnitusOverlay` 属于耳鸣状态包驱动的听觉冲击视觉反馈（枪声也使用该包），不是独立的物理震荡系统，因此跟随 effective severity。既有视觉强度为 `sqrt(0.50 × n²)`；未改变爆炸物理伤害或击退。
- 未来 HEAD 防护物品可使用更低的 `impulseProtectionMultiplier`（如军用电子耳罩的设计目标 0.05–0.10），本轮未实现该物品；已移除旧 exposure 侧倍率，避免重复削弱。

## 配方、创造页、本地化

`data/apocalypse_firstlight/recipes/simple_hearing_protection.json`：

```text
 I 
L L
W W
```

I = `minecraft:iron_ingot`，L = `minecraft:leather`，W = `minecraft:wool` item tag（任意颜色）；产出 1。

独立创造页 Registry `apocalypse_firstlight:equipment`，中文精确为 **黎明启示录 装备**，英文 `Apocalypse: First Light Equipment`。图标和当前唯一条目均为耳罩。通过 Forge `withTabsBefore(WEAPONS_AND_AMMUNITION.getId())` 让武器页位于装备页之前；不移动、重排武器页条目。

`en_us.json` / `zh_cn.json` 包含 item、itemGroup 和三条 `tooltip.apocalypse_firstlight.simple_hearing_protection.*` 键；本轮仅调整三条 Tooltip 文本。

## 验证边界

上一轮 `compileJava`、`processResources` 均通过；本轮 Tooltip/耳鸣改动的编译与资源处理结果见本轮执行报告。

静态核对通过：126 个元素及全部 UV/display 与源 JSON 数据相等；PNG SHA-256 为 `A8F923B47CC56E4564F106A7B7FBA27E521DFB20887F1AB568D8D95F872C9247`，与验收 PNG 一致；源 bbmodel SHA-256 为 `8FFD908704E4BF78FCC3C072A82ABDE6D2036301C31FC89FAFA99A7EB8893626`，本轮未改。en_us/zh_cn JSON 可解析，diff whitespace 检查通过。common Item/manager 无 client 引用，模型注册及 Mixin 均仅客户端。
本轮不执行 runClient、clean、GameTest、截图、展示渲染、commit/push；新的耳鸣强度/Tooltip 效果仍需用户实机验收。
