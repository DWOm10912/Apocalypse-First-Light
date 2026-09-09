# 通用步枪红点瞄具 V1

## 配件与属性

| 项目 | 当前实现 |
| --- | --- |
| 名称 / ID | 步枪红点瞄具 / Rifle Red Dot Sight / `apocalypse_firstlight:rifle_red_dot_01` |
| 槽位 / 兼容 | `SIGHT`；BR51-01 为第一兼容枪，非 BR51 专属实现 |
| 来源 | BR51 源模型原生隐藏 `sight`，没有重新设计 |
| 安装 / 拆卸 / 更换 | 仅枪械维护台；不恢复 V 快捷入口 |
| ADS 对齐点 | 枪体模型坐标 `[0,14.8125,3.70313]` |
| ADS FOV / 进入时间 | 保持 BR51 原值 `0.89` / `0.2 s`；不增加高倍镜效果 |
| 数值属性 | 无额外伤害、后坐力、散布、射程、射速、换弹速度修改 |
| 槽位共存 | SIGHT 与 `rifle_suppressor_01` MUZZLE 独立保存和装拆 |

## 源资产审计与提取

`src/main/blockbench/br51_01.bbmodel` 中的 `sight` 原为 `export=false / visibility=false`。只提取它直接包含的 28 个 cube 和 `bone15_illuminated` 中的 1 个既有瞄准点。子组 `bone16` 是延伸导轨辅助几何，不复制；激光器、手电及其他子树不复制。

原资产没有单独 lens 面：保留开放镜窗，不新增实体黑玻璃或凭空设计半透明镜片。仅独立 `reticle` 子组 full-bright，外壳正常受环境光。原来所有方块的形状、旋转和 UV 均保留；只平移到独立 root。瞄准点保留原贴图取色，实际 ADS 截图显示为红色，未重新绘制。

| 资产 | 路径 / 接口 |
| --- | --- |
| 独立可编辑源 | `src/main/blockbench/rifle_red_dot_01.bbmodel` |
| 可重复提取脚本 | `src/main/blockbench/extract_rifle_red_dot.cjs` |
| 独立 runtime geo | `src/main/resources/assets/apocalypse_firstlight/geo/rifle_red_dot_01.geo.json` |
| 独立贴图 | `src/main/resources/assets/apocalypse_firstlight/textures/item/rifle_red_dot_01.png`；复制 BR51 原图，不重绘，保留 128×128 UV / 256×256 像素 |
| 独立物品模型 | `src/main/resources/assets/apocalypse_firstlight/models/item/rifle_red_dot_01.json` |
| Root / 发光子组 | `rifle_red_dot_root` / `reticle` |
| 安装中心 | BR51 新增空 `sight_anchor`，parent `positioning2`，位置 `[0,12.75,5.54688]`；原 `scope_pos` 不变 |

BR51 源模型中的隐藏参考红点仍保留且不导出；没有加入整枪带镜变体，也没有在 runtime 基础枪模中加入红点 cube。只新增空挂载锚点，枪身、手部、弹匣、消音器和动画不变。

## 通用接入

- `NativeSightItem` 可选择现有 baked-item 或独立 Geo 展示路径，P9 保留原路径。Geo 瞄具通过 `NativeSightRendering` 调用共用静态 Geo 绘制，不新建 BR51 专属渲染器或 ADS handler。
- BR51 `sight_slot` 声明 anchor、零 mount_offset、ads_center 和 accepts。后续步枪可提供自己的对应数据，复用同一配件资产。
- `NativeAdsProfile.forStack` 读取实际装备的兼容 SIGHT 并解算光学轴。无配件继续原机械瞄准；真弹道保持 camera/look vector，视觉点不控制命中方向。
- `AflAttachments.SIGHT` 保存真实附件 ItemStack；共用维护台 hotspot、Context HUD、完整 Inventory 候选投影、原版点击音与 2.480 秒操作声。51 tick 后服务端重新验证并提交，期间不提前出现/消失。
- 兼容判断、revision、stale rejection 和安全返还均沿用 `MaintenanceAttachmentTransaction`。协议仍为 22，旧 V 报文继续为空操作。
- 所有支持的枪械渲染环境读取同一个真实 ItemStack；无独立维护台 sight 状态。独立配件进入武器与弹药创造标签页，使用简洁中英文 Tooltip。

### 背包独立 3D 展示修正

`NativeSightItem.initializeClient` 必须无条件注册延迟创建的 `NativeMuzzleRendering.ItemRenderer`：Forge 从 `Item` 父类构造函数调用该方法，此时子类 `geoModel` 尚未赋值，不能据此提前返回。此前错误判断造成步枪红点有枪上模型、背包却为空白。

步枪红点通过 `models/item/rifle_red_dot_01.json` 的 `builtin/entity` 使用独立 Geo 模型和现有 GUI 变换；P9 红点仍用原 baked 模型，不触发该自定义绘制。此修正不改源几何、枪上挂载、ADS 或维护台限定规则。

修正后客户端验证：`build/rifle-sight-item-client.log` 中 `[RIFLE SIGHT ITEM] PASS` 确认渲染器类型；`build/thermal-fluid-client/screenshots/rifle_red_dot_inventory.png` 已人工复核，普通背包第一格显示完整独立 3D 瞄具，未越出槽位。重新编译记录：`build/rifle-sight-item-build.log`。本次未重跑全套枪械 GameTest。

## 验证边界

源码审计确认原红点 28+1 cube、原发光点与隐藏状态；独立贴图与 BR51 原贴图 SHA256 一致。Blockbench MCP 本次未连接，未完成实时 Blockbench 检查。

| 检查 | 本次结果 / 边界 |
| --- | --- |
| 编译 | `build/rifle-red-dot-build.log`；离线 build 通过 |
| GameTest | `build/rifle-red-dot-tests.log`，28/28 必需测试通过；包括 BR51 双槽独立性、安装/更换/拆卸、ItemStack/BE 序列化、stale 拒绝和 P9 附件回归 |
| 维护台客户端 | `build/rifle-red-dot-client-final.log`，实际候选页点击、双附件安装、两次操作声、延迟期间不提前提交、取回保留通过 |
| 视觉 | `build/thermal-fluid-client/screenshots/rifle_red_dot_{context,candidates,bench,fp,ads,tp}.png`；复核维护台、第一人称、第三人称和真正 ADS 画面；镜窗开放、点位居中；用户随后反馈“看起来没问题了” |
| ADS 测试有效性 | 最终 probe 显式断言 screen 为空、ADS progress > 0.99；早期右键误开维护台的截图不作为 ADS 通过证据 |
| 多人 | 服务端 FakePlayer stale/conservation 测试通过；未进行两个真实客户端的同步视觉验收 |
| 未单独验收 | 夜间画面、连续射击 recoil/sway、P9 图形 ADS 全流程、独立物品大图；普通背包图标已在后续修正中验收，见上节 |

V 快装保持禁用，旧报文仍为空操作；没有新增客户端快捷安装入口。真实弹道、BR51 数值和动画未修改。
