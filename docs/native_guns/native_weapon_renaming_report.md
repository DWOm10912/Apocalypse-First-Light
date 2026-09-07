# Native 枪械正式型号迁移

> 后续弹药更新已完成：P9-01 → `9x19mm_round`，BR51-01 → `762x51mm_round`；对应弹壳已独立注册/接入。旧 `9mm_round` 仅保留存档 missing-mapping 迁移。本文下列“9mm占位/不新增7.62”描述仅指当时改名轮次，并非当前状态；当前资源与验证见 `native_ammo_assets_v1.md`。

## 正式命名

| 旧称/ID | 正式中文 | 正式英文 | 新ID |
|---|---|---|---|
| Service Pistol / service_pistol | P9-01 制式手枪 | P9-01 Service Pistol | apocalypse_firstlight:p9_01 |
| M14原型 / m14 | BR51-01 战斗步枪 | BR51-01 Battle Rifle | apocalypse_firstlight:br51_01 |

BR51-01当前美术原型参考M14。正式设定7.62×51mm，当前实现仍复用9mm技术占位，后续需替换。
9mm_round注册ID和行为未改，显示名更新为9×19mm手枪弹 / 9×19mm Pistol Round。

## 文件与资源

完整机械迁移清单见 `native_weapon_rename_manifest.json`（from/to逐文件，共112项，包含仅内容修改项）。
另新增NativeWeaponLegacyMappings.java、verify-native-model-names.mjs、本报告；迁移入口修复见migrate-br51_01.mjs。

两枪资源统一使用p9_01 / br51_01前缀：

- models/item/{id}.json及{id}_in_hand.json：GUI/手持分离保留。
- geo/{id}.geo.json、animations/{id}.animation.json、textures/item/{id}.png。
- textures/item/{id}_inventory.png及textures/gui/gun/{id}_hud.png。
- p9_01额外icon及muzzle_flash资源同步改名；不修改二进制图片/音频内容。
- 声音event：service_pistol_* → p9_01_*，m14_* → br51_01_*，保留原下划线风格。
- 声音目录：sounds/weapons/p9_01/、sounds/br51_01/；sounds.json与Java、动画marker一致迁移。
- 本地化：item.apocalypse_firstlight.p9_01 / br51_01，tooltip/subtitle同步。
- 源：src/main/blockbench/p9_01_v03_8_fire_slide_cleanup.bbmodel、br51_01.bbmodel。
- P901* Java类替代ServicePistol*；注册/definition常量为P9_01、BR51_01；BR5101CombatGameTests同步。
- 通用NativeGun*类保留；P901Actions等历史公共入口仅改名，不重写业务。
- tools专属导出/校准/白版/图标脚本和docs专属文件同步命名及链接。

外部原始目录E:/Download/合集/M14不修改；migrate-br51_01.mjs显式读取历史文件名，加载时转换模型命名。
源内嵌data:图片跳过文本替换，防止损坏base64。

## 兼容

NativeWeaponLegacyMappings监听Forge MissingMappingsEvent（Forge事件总线）：
旧物品service_pistol→p9_01、m14→br51_01；旧专属声音前缀映射到存在的新声音event。
这是存档注册表迁移，不是旧ID别名注册；新世界/新命令应使用：

`/give @s apocalypse_firstlight:p9_01`

`/give @s apocalypse_firstlight:br51_01`

旧/give、外部资源包、外部数据包不保证兼容，需更新引用。未注册第二套旧物品。
既有AflGunAmmo弹药NBT语义未改；未对用户存档做直接文件修改。
旧存档实际加载迁移尚未验收，OLD_ID_COMPAT=PARTIAL。

## 保留行为

只做标识/引用替换：原动画数值、节拍、材质、伤害、弹量和渲染参数不变。
BR51-01仍8条正式动画、标准匣与empty临时副本；扩容/瞄具/激光/握把不导出。
不新增配件系统、ADS或7.62弹药。

## 验证边界

verify-native-model-names.mjs检查两枪模型/geo/动画/纹理存在、声音marker对应事件及本地OGG存在、
BR51-01恰好8条动画和白版剔除状态、两语言正式条目存在：PASS。
普通构建通过后追加clean构建，防止旧class/资源残留。日志native-rename-clean-build.log。
clean compileJava/processResources/build通过（1分19秒）；JAR扫描未发现旧资源路径或ServicePistol类文件，git diff --check通过。
视觉/听感、旧世界映射以及完整实机开火/换弹回归不能以编译替代；未验证项不得标PASS。
无图形runGameTestServer（独立build/br51_01-combat-gametest目录、-PaflWithoutTacz）58/58通过，
包括P9-01既有弹药/战斗测试及BR5101CombatGameTests三项；证据native-rename-gametest.log。
因此两枪自动化回归PASS；图形模型/动画/听感及旧存档迁移仍NOT TESTED，不代表全部实机验收完成。
本轮没有commit/push。
