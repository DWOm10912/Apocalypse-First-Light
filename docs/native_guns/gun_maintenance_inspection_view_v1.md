# Gun Inspection View V1 — Field / Maintenance Scope Correction

2026-09-27：纠正上一轮错误入口。完整 Orbit 属于野外 Field 页面。维护台枪械本体和镜头位置/朝向固定；只允许滚轮 FOV Zoom 和 R Reset，禁止任何拖拽平移或旋转。旧版“维护台自由旋转、Field 不变”的说明作废。本次未启动客户端，实机视觉与输入验收由用户完成；最后一次 compileJava 的结果见交付报告。

## 上一轮接线审计

上一轮 Orbit 左拖、Zoom 滚轮、Pan 中拖、R Reset 都接在 GunMaintenanceScreen。MaintenanceInspectionController 持有 current/target 状态，MaintenanceModeEvents 在 render tick START 更新；GunMaintenanceBenchRenderer 在 benchTransform 之后、原 MaintenanceViewProfile 之前应用矩阵，MaintenanceHotspots / MaintenanceGunPicking 共用它。FieldAttachmentScreen 当时只有附件点击和候选列表滚轮，因此截图中的 Field 页面没有观察控制。

本次没有整轮回滚：旧控制器/边界类通用化为 GunInspectionController / GunInspectionGeometry。维护台模式禁用所有拖拽；中键也无平移。滚轮只修改目标缩放，FOV跟随currentZoom平滑变化。已从维护台 BER、热点与 picking 删除 inspection model transform；原 MaintenanceViewProfile 数据不变。

## 最终功能

| 页面 | Orbit | Zoom | Pan | Reset | 平滑 |
|---|---|---|---|---|---|
| 野外 FieldAttachmentScreen（默认 Z） | 非交互区左拖 | viewport 滚轮 | 中拖 | R | 开启 |
| GunMaintenanceScreen（维护台） | 禁止 | 滚轮FOV缩放 | 禁止 | R恢复默认FOV | 开启 |

两页共用 GunInspectionController，只有页面能力/坐标基不同，不区分具体枪型。透明 viewport 为 GUI-scaled 坐标 [12,width-12) × [12,height-64)，宽≤32或高≤88时停用。4 GUI px拖动门槛。Field 在按下时检查 viewport 与 HUD/hotspot 优先级；已捕获的左/中键拖拽跨过枪身、配件热点、Context 面板或 viewport 边缘仍连续跟随，不重新做 hover 阻挡检查。释放、resize、关闭、Reset、目标/资源变化以及进入候选选择或配件操作时取消拖拽。维护台仍禁止拖拽。

点击优先执行原附件 HUD/hotspot；候选选择、按钮等待和服务端操作pending时禁止inspection。Context面板/热点上的滚轮不缩放；底部候选列表区域滚轮翻页；其他viewport内才zoom。Esc/页面退出绑定优先于R。页面外R仍换弹，未改gameplay。

## Field 真实路径与变换

FieldAttachmentScreen → FieldAttachmentViewState（RenderTickEvent START更新一次）→ ConfiguredGunFirstPerson 原 RenderHandEvent → FieldAttachmentTransform → 原 ItemRenderer / NativeAnimatedWeaponRenderer → 枪械骨骼及既有 sight / muzzle / magazine 绘制层。

FieldAttachmentTransform 在原 HIP→Field correction 前应用 inspection root；新增 target(stack,right) 复用原Field profile计算。零delta完全保留现有Field构图，左主手沿用镜像。关闭时inspection delta随原退出进度淡出。相机、世界、太阳和Shader光照不旋转。

FieldAttachmentHotspots 已从原遍历中的最终 PoseStack 投影，自动包含新的根变换，无额外像素补偿。Pan读取实际第一人称 projection matrix 的m11，使用与热点一致的投影。枪与所有既有附件共用根；无anchor、安装offset、资源profile、animation修改。

维护台绘制始终为 bench基准 → 原profile → 原骨骼/附件，绝不移动、旋转或缩放枪。MaintenanceModeClientState.cameraPosition 恢复原固定位置；cameraFov统一提供缩放后的FOV，MaintenanceGameRendererMixin / hotspot projection / picking共用它。公式 FOV=2*atan(tan(75°/2)/zoom)，R恢复75°；不再dolly或pan。场景、镜头位置/朝向、枪和光源均不移动。

## 参数、缓存和安全

- current/target yaw、pitch、zoom、panX、panY均为Screen私有客户端状态，打开为(0,0,1,0,0)，关闭丢弃；不写NBT、不发inspection包。
- Field yaw连续累计，仅构建矩阵时按360度取余；pitch delta限±80度，灵敏度0.55度/GUI px。
- 旋转/平移95% step response=0.10秒，zoom=0.15秒；alpha=1-exp(-ln(20)*dt/response)，按render-frame实时时差计算。
- Reset为0.25秒easeOutCubic，yaw选择最近等价默认方向；新drag/wheel从当前状态打断reset。
- 请求zoom范围0.60–2.50，默认1.00。Field乘以原基础展示比例、相机固定；维护台仅改变FOV，镜头位置和枪械比例固定。
- GunInspectionGeometry在打开、枪/附件快照或资源generation变化时，做一次CPU-only静态坐标capture，包含枪与安装附件，没有GPU提交。Field用FieldTarget × inverse(MaintenanceBase)抵消采样入口的维护台profile；最终Field显示不使用维护台姿态。
- 缓存是保守bind-pose边界，不是每帧动画碰撞检测；Field额外扩大AABB 0.04格，给小幅idle root/follower偏移留余量。实际所有动画状态与Shader组合需实机确认。
- 日常帧只检查缓存AABB八角。Field最大zoom=min(2.50,(pivotDepth-0.10)/towardCameraExtent)，留0.10格枪体近裁面深度；维护台FOV缩放固定0.60–2.50，不改变原近裁面与模型的距离；current也限幅，覆盖旋转、复位及新附件。安全边界处可比普通平滑更快收缩。
- 0.60是输入下限；未来异常超大资源若仍不安全，安全约束优先，可能使用更小有效比例。不保证所有枪所有朝向都能2.50倍。
- 仅Field允许Pan，默认viewport宽±35%、高±25%，按固定枪体采样点投影进一步收紧，保留8 GUI px余量；current和target都受限。Field的枪体pan沿相机平面；维护台的current/target pan始终为0。极小窗口优先保证点在视口内。这不是工作台/墙体碰撞系统。
- 同一枪/资源generation装卸附件保留pivot，不因附件改变基础构图；附件尺寸变化会重算安全zoom。

## 维护台手部隐藏修正

原GameRenderer.renderItemInHand拦截只覆盖原版入口；本机Oculus HandRenderer直接调用ItemInHandRenderer.renderHandsWithItems，绕过该Mixin。维护期间新增MaintenanceModeEvents的HIGHEST优先级RenderHandEvent取消，作用于两只手、空手与持物，并早于普通自定义枪械/撬棍事件。Oculus和原版都经过该Forge hook，覆盖进入/打开/退出阶段；关闭后active=false自动恢复。既有Mixin及自身RenderPlayerEvent.Pre隐藏保留。这里只核对了本机字节码调用路径，尚未实机验收。Field行为不变。

## 范围与验证

P9、Blackridge、BR51、HR55、Silverwood均走configured Native Gun共同入口，无新增逐枪分支。未来走同一Field入口的Native Gun复用控制器；原Field可打开条件、配件槽/政策及服务端交易不变。

静态检查输入优先级、Maintenance旋转双重禁用、Field完整根变换、左右手镜像及热点链。上一轮数值检查不当作本轮Field实机证据。本轮最后仅执行一次compileJava --offline，不运行build/processResources/runClient/GameTest。

用户验收：Field P9+消音器旋转/最大zoom/pan/R；检查近裁面、屏幕边界、配件/热点跟随；BR51/HR55放大查看两端；左右主手、GUI scale、退出恢复；Sundial/Complementary固定世界光照检查PBR。维护台左/中拖均无效；滚轮仅缩放视野，R恢复75°；枪与桌面相对位置、方向、尺寸及镜头位置/朝向必须不变。检查普通渲染/Oculus下主副手、空手和持物手臂在进入/打开/退出阶段均不显示。

核心源码目录：src/main/java/com/antaurora/apofirstlight/。client/GunInspectionController.java、client/GunInspectionGeometry.java、client/FieldAttachmentScreen.java、client/GunMaintenanceScreen.java、client/MaintenanceModeClientState.java、weapon/client/FieldAttachmentViewState.java、weapon/client/FieldAttachmentTransform.java。原维护台渲染/热点仍接共用控制器。
