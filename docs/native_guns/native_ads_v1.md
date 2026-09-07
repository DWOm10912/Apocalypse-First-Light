# Native ADS V1：机械瞄准（实现已接入，视觉验收未完成）

## 范围与输入

P9-01 / BR51-01 共用 `weapon/client/NativeGunAds.java`，按住 Vanilla `keyUse`（默认右键）请求 ADS，松开退出。不是 toggle，不新增网络消息、射击代码、散布奖励、配件或 TaCZ 依赖。

当前 `P901Input` 只拦截左键攻击，R为换弹；物品未覆写右键use。ADS不取消任何右键事件，门、按钮、实体与方块原交互仍可执行；菜单打开、持续使用物品时阻止ADS。瞬时交互（例如开门）不被ADS吞掉，也没有另写交互白名单。实际交互回归仍待完成。

## 进度与生命周期

`NativeAdsProgress` 每客户端END tick步进，渲染仅插值previous/current；不会按帧率累加。位置线性插值、旋转使用identity到补偿Quaternion的最短弧slerp，不跨±180°跳跃。整套枪和AFL-owned双手继承同一载体，不改任何原动画关键帧、HIP Display或手臂骨骼。

读取现有Gecko `action` 控制器的触发动作：reload/reload_empty/reload_tactical、draw、put_away、inspect禁止ADS；射击、空仓idle、干击不禁止。R请求先退出，等待服务器动画同步；请求被拒绝时仅3tick临时抑制。配置枪切入至少等待原draw时长，P9等待3tick equip。动画结束后仍按住右键可重新进入。

Sprint禁止进入ADS，已ADS时疾跑按原退出时长平滑退出；停止疾跑且仍按右键时重新进入。走路/蹲伏/空中允许，精度仍受原姿态系统约束。切物品/槽位/枪ID、死亡、重生、换世界重置两端进度；渲染即时验证身份，避免新物品继承一帧ADS。第三人称不应用ADS。切枪FOV立即恢复而非把旧枪缩放交给新枪。

## 锚点与参数

本轮已为BR51新增机瞄底座和前准星柱。BR51以实际后照门 `octagon9` 的中心X=0、Y=13.6875为瞄轴，沿用Z=15.46875作为眼距参考。旧 `iron_view` 的Y=14.8是辅助镜头定位，不是实际孔中心，首轮实机明显偏低，已停止使用该高度。前后瞄具是否完全共线尚待新版实机确认。P9复用 `gun/sight_anchor` 的瞄线高度/横向位置，后点取 `rear_sight` Z=9.04，前点Z=-3.36；模型空间瞄轴为-Z。核对当前Gecko加载器：pivot X反号，rotation X/Y反号。

集中配置：`weapon/client/NativeAdsProfile.java`。所有坐标均是模型单位/16，物理相机位置不动。

| 参数 | BR51-01 | P9-01 |
| --- | --- | --- |
| Anchor（Gecko空间、未除16） | [0,13.6875,15.46875] | [-2.98,11.59,9.04] |
| Eye relief | 0.16 | 0.34 |
| ADS scale | 0.45 | 0.41 |
| ADS rotation | [0,0,0] | [0,0,0] |
| ADS模型矩阵translation | [0,-0.3849609375,-0.59505859375] | [0.0763625,-0.29699375,-0.57165] |
| 进入/退出 | 4/4 tick（0.20/0.20s） | 3/3 tick（0.15/0.15s） |
| FOV multiplier | 0.89 | 0.95 |

矩阵求解：`ADS = T(0,0,-eyeRelief) × S × T(-anchor/16)`；`C = ADS × inverse(HIP)`。HIP包括现有Display、P9 composition、Geo/Vanilla净0.01Y偏移及P9 fp_root静态3°旋转。应用 `lerp(identity,C,progress)` 到原HIP载体外层。配置中保留当前HIP数值用于求逆，未写回原资源；以后更改HIP时需同步此配置。

BR51 HIP T=[3.8,-7.2,-11.5]/16 R=[0,4,0] S=.45；P9 HIP T=[1.00148,-7.2445,-11.68624]/16 R=[.54547,.19151,-.27948] S=.41，另有composition=[.10,.045,0]、fp_root X=3°。左主手镜像补偿；P9原左手Display略有差异，左手模式尚未精校，列为已知限制。

定位矩阵仍由本类维护；ADS时间/FOV已迁移到单枪native_guns JSON，经 /reload 同步客户端。BR51三点共线、后照门距离、P9手臂遮挡仍以实机为准，不得仅凭矩阵称为对齐通过。

## FOV、准星、后坐与弹道

Forge `ViewportEvent.ComputeFov` 在当前FOV上乘 `1+(multiplier-1)*progress`，不写options，也不固定70→50；世界与手部投影都应用，保持中心轴。原Sprint/potion modifier仍由原系统计算。身份失效返回progress=0，不残留缓存FOV。

Native准星仅在progress>=.999时隐藏中心点，退出立即恢复；命中反馈仍显示。没有重做HUD。

渲染顺序为 `recoil × ADS补偿 × 原HIP/动画`；后坐不被锚点求逆抵消，恢复时回ADS而不是HIP。枪焰、抛壳跟随现有真实渲染锚点。`NativeGunShot` 未修改，仍由服务端eye position/look vector加原散布计算hitscan；ADS没有参与真实弹道、伤害、扣弹或服务器状态。

调试默认关闭：JVM `-Dafl.nativeAdsDebug=true` 每秒记录gun/progress/blocked/HIP/ADS/correction/FOV，非正式HUD。

## 验证与已知边界

### BR51机瞄底座修正

用户确认原枪体遮挡瞄线并授权直接修改bbmodel。`tools/raise-br51-iron-sights.mjs` 对后孔8块和护翼/支撑10块整体上移0.875单位；新增后底座2块、前底座2块及细前准星柱1块，沿用原枪灰UV，不修改贴图文件。源文件 `src/main/blockbench/br51_01.bbmodel` 与 `geo/br51_01.geo.json` 同步；新组 `afl_iron_risers` 挂于 `br51_01_default`，随原枪体动画运动，无新增动画。

后孔中心与前柱顶均为X=0、Y=13.6875，前柱Z=-9.8；原顶部遮挡结构最高13.125，水平瞄线留出0.5625单位间隙。后底座连接原壳体，未抬动整个bone5（包含机匣），未改变枪体其它结构、HIP、手臂或原动画关键帧。ADS配置同步新高度。旧12.8125高度仅为修改前数据。

脚本为一次性防重复抬高工具；再次运行会拒绝。`compileJava processResources build --offline --stacktrace` 通过（42秒，`br51-iron-riser-build.log`），源动画/Display/贴图绑定与修改前一致检查通过，`git diff --check`通过。当前几何高度检查不等于游戏视野通过，新版实机仍待确认。

- `compileJava processResources --offline --stacktrace` 通过，32秒。
- `runGameTestServer --offline -PaflWithoutTacz -I src/dev/br51_01-combat-gametest.init.gradle`：62项中61项通过。新增 `NativeAdsGameTests.adsTickAndPartialRecovery` 检查4/3tick进度、partial、FPS无关采样、退出/反向/重置。唯一失败仍为 `nativenoiseflatdistances` 的 Hearing boundary20，不能称全套通过；本轮未修改该系统。
- `runClient --offline -PaflWithoutTacz` 成功进入test单人世界；已捕获BR51 HIP画面。
- Computer-use能够截图，但没有可靠持续按住鼠标API；已请用户配合长按右键。用户已提供首轮完整ADS截图，证明后照门低于中心，首轮对齐FAIL；已修正后照门高度，新版仍待重启验收。
- 新版BR51机械瞄线/开火/连续后坐、疾跑退出ADS及停止疾跑后重新进入、ADS→reload/switch、P9 ADS/回归、门按钮交互、FOV恢复：NOT_TESTED（代码已接入但实机验收未完成）。
- VISUAL_VALIDATION_DONE = NO；不能标记整项ADS最终完成。

后续optic_center可替换每枪anchor/eye relief，沿用同一载体与进度；不要让瞄具改变服务端弹道轴。PIP/倍率/配件均未实现。
