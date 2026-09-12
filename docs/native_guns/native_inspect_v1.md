# Native Gun Inspect V1

状态：代码已实现，隔离服务端回归 3/3 通过；客户端体验验收进行中，未完成项目不记为实测 PASS。

## 输入与动作

- `weapon/client/P901Input.java` 注册标准 Forge IN_GAME KeyMapping：
  `key.apocalypse_firstlight.inspect`，默认 V，沿用 `key.categories.apocalypse_firstlight`。
  Controls 可改键；中文“检视武器”，英文“Inspect Weapon”。Screen、死亡、旁观、失焦、
  非 Native Gun 不处理；消费按键点击并用按住边沿保护，重复 V 不重启。
- `weapon/client/NativeGunInspect.java` 仅管理输入意图和 ADS 退出等待，不运行动画时钟。
- `NativeGunItem.inspectClip()` 为可选能力；默认 null。
  ConfiguredNativeGunItem 要求 profile.clips 声明 inspect 且正式资源存在；否则 NO_OP。
  BR51 正式动画名为 `inspect`，来源 `animations/br51_01.animation.json`。
  P9 目前不提供该能力；本轮未修改其源/geo/animation/equip，也未改 BR51 动画。
- 复用 `P901Actions.operation`、Session 动作锁、Gecko trigger/stop 同步及动画资源长度。
  现有第三人称同步随框架复用，不另建网络动画系统。

## 优先级与 ADS

已有 reload、fire、draw 或其他动作锁拒绝 Inspect；重复请求也拒绝。
Fire/Reload 请求仅可取消低优先级 inspect，再在同一次请求继续原战斗验证；
空枪/无备弹仍遵循原规则，不给额外弹药、不跳过 fire cooldown/draw gate。
换槽、切物品、死亡、维度变化沿用 Session 清理；打开输入界面发送仅针对 inspect 的取消请求。
检视按正式资源 animation_length 的 ceil(ticks) 完成，沿用原动作结束机制。

ADS 数学和 transform 不改，仅增加 inspect 输入阻断条件：沿用原平滑退 ADS，
前后插值端均为零才发起检视，因此不将 ADS transform 与 inspect camera 叠加。
检视结束保持 HIP；先前按住的瞄准键不能自动恢复，须松开并重新按下。
40 tick 等待仅用于触发回包丢失/拒绝的防重入超时，不代替正式动画长度。

## 网络与资源边界

共享 `AflNetwork` 协议从 22 升为 **23**，两端须匹配。
在末尾追加轻量 C2S `NativeInspectPacket(slot, id, cancel)`，保留旧包编号。
服务端核对槽位/GeoItem ID/主手/存活/动作锁/能力；取消只作用于相同 ID 的 inspect。
Inspect 本身不转移弹药、不改配件/耐久；仅使用已有表现动作 Session 和 GeoItem 同步身份。
音效仅复用正式动画 cue，不新增通用音效。

Camera Consumer 原样复用，识别 inspect 后读取已插值 camera.rotation；本轮未修改该类。
Recoil、Sway、弹道、ADS 对齐算法均未改。
旧 V 配件快速装拆不恢复；SightExchange 旧报文继续空操作，正式安装仍只经维护台。

## 验证

- `src/dev/java/com/antaurora/apofirstlight/dev/NativeInspectGameTests.java`：
  不重启/自然结束/无数据消耗、开火与换弹优先级、取消身份校验、切物品、P9 NO_OP。
- `src/dev/inspect-gametest.init.gradle`：隔离 build 测试世界，不触碰玩家存档。
- 最终 compileJava/processResources/full build 通过；隔离 GameTest 实际执行 3/3 通过。
  覆盖正式长度结束/无消耗/防重复、同一次 Fire 输入打断并射击、Reload 优先级及完成补弹、
  取消 ID 校验、切物品清锁、无检视能力 NO_OP。
  前几轮测试模板/命名空间配置失败或执行 0 项，不计为测试通过。
- 人工：V、连按 V、ADS 持续按住时 V、检视中射击/R、换弹中 V、换枪/普通物品、
  第三人称、Controls 改键、结束回 HIP 与 camera 回零。需重启最终构建后的客户端验证。
