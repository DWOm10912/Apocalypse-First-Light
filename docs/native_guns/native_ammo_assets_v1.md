# Native 正式弹药资产 V1

## 当前实现

| ID（均为 apocalypse_firstlight 命名空间） | 中文 | 英文 | 用途 |
| --- | --- | --- | --- |
| 9x19mm_round | 9×19毫米手枪弹 | 9×19mm Pistol Round | P9-01 |
| 762x51mm_round | 7.62×51毫米步枪弹 | 7.62×51mm Rifle Round | BR51-01 |
| 9x19mm_casing | 9×19毫米弹壳 | 9×19mm Casing | P9-01 抛壳及普通物品 |
| 762x51mm_casing | 7.62×51毫米弹壳 | 7.62×51mm Casing | BR51-01 抛壳及普通物品 |

四项均64堆叠，只有两种实弹进入 AFL 武器与弹药 标签，排序在枪械/撬棍之后。两种弹壳不加入创造标签，仅保留注册用于开发检查；不是面向玩家的获取内容。数字开头是合法 ResourceLocation 路径，无需前缀。弹壳不作为弹药，也没有回收配方；射击不会生成可拾取实体。

## 源资产与美术

已核对 `src/main/blockbench/br51_01.bbmodel` 的 `magazine_bullet/bullet/2/3` 和 `7`：各12 cubes，是错位排列的两颗完整弹药，不是两半。选择组3（UUID `335a03a2-aae3-f396-93b6-9a21cf307383`），移除弹匣父级放置旋转，统一放大4倍并居中；保留12个原方块、局部22.5°肩部旋转和原UV关系。补一个简化底火，未改枪械源文件。

7.62整弹为13 cubes；弹壳移除铜色弹头段，保留壳体、肩部、底缘与抽壳槽，补暗色凹口和底火，共9 cubes。独立256×256贴图是原BR51 atlas的逐字节副本，保留材质一致性，不改枪纹理。

9mm手工重做：32×32克制黄铜/铜材质，8条窄截面组成圆杆轮廓，短直壳、底缘、抽壳槽、底火与圆钝分段铜头。整弹65 cubes；弹壳去掉铜头加暗色壳口，共41 cubes。相比旧十字型宽台阶，截面更细、头壳对比更明确。9mm在相同模型单位下更粗短，7.62细长尖头；没有使用图像生成或照片贴图。

普通Java三维item渲染，无Gecko动画/独立geo需求。GUI统一斜置，9mm比例1.2，7.62为1.05；第一人称0.45、第三人称0.25、Ground0.22。Ground横放；具体游戏内可读性待目测，不把离线渲染当成实机通过。

## 文件与旧新映射

对上述四个ID，各有以下正式文件（共12项）：

- `src/main/blockbench/<ID>.bbmodel`
- `src/main/resources/assets/apocalypse_firstlight/models/item/<ID>.json`
- `src/main/resources/assets/apocalypse_firstlight/textures/item/<ID>.png`

旧 `9mm_round` 注册通过 `registry/NativeWeaponLegacyMappings.java` 的 MissingMappings 转为 `9x19mm_round`，不把旧手枪弹转换为步枪弹；旧存档加载兼容尚待实机确认。旧 `9mm_round` / `9mm_casing` 源模型、item和 `9mm_palette.png` 留作历史对照，不再是正式引用。旧导出器 `tools/export-9mm-assets.mjs` 不用于当前资产。

新导出：`node tools/export-native-ammo.mjs`；引用检查：`node tools/verify-native-ammo.mjs`。离线几何预览：`tools/preview-native-ammo.py` → `build/native-ammo-preview.png`，仅用于资产检查。

接入修改：`AflItems.java`、`AflCreativeTabs.java`、`NativeWeaponLegacyMappings.java`、`NativeGunDefinition.java`、`ConfiguredNativeGunItem.java`、中英文lang；客户端 `NativeGunFx.java`、`NativeGunFxModels.java`、`NativeAnimatedWeaponRenderer.java`；回归 `BR5101CombatGameTests.java`。

## 行为边界

P9-01只吃9x19mm_round，BR51-01只吃762x51mm_round，背包HUD统计沿用同一definition口径查询。已装枪内弹数NBT不重置。换弹时间、扣弹次数、dry-fire、伤害、精度、枪械模型、动画、Display均不变。

两把枪均已切换对应弹壳；Casing实例保存诞生时的模型，不会因切枪改变。沿用 .072 FX比例、局部抛出方向、重力、阻力、翻滚、碰撞、弹跳、音效、寿命和上限。枪焰不变。未来新枪默认9mm，若有新口径需显式增加资源选择，当前仅两种正式枪。

## 验证

- `compileJava processResources build --offline --stacktrace` 通过（36秒；首次测试API调用错误已修正）。
- GameTest：61项中60项通过，枪械/弹药测试通过，包括BR51拒收9mm、7.62普通/空仓结算、取消/不足补弹、射击/干击。整套未通过：`nativenoiseflatdistances` / `Hearing boundary 20`；不在本轮修改范围，未据此宣称全套通过。日志 `native-ammo-gametest.log`。
- 离线几何/贴图预览已查看；游戏内GUI、手持、掉落、抛壳美术与声音尚未完成验收。
- 当前无7.62弹药占位；未完成项是实机视觉验收和旧存档迁移验证，不是正式口径接入。

测试命令（对应四种物品）：

```text
/give @s apocalypse_firstlight:9x19mm_round 64
/give @s apocalypse_firstlight:762x51mm_round 64
/give @s apocalypse_firstlight:9x19mm_casing 1
/give @s apocalypse_firstlight:762x51mm_casing 1
```

尚不能标记 VISUAL_VALIDATION_DONE=YES。未提交、未推送。
