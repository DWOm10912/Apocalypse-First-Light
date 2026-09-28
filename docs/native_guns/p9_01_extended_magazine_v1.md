# P9-01 28-Round Extended Magazine V2

## V2 现状（2026-09-27，已实现，未实机验收）

旧的 24 发 23-cube 扩容匣属于已退役 rig，已被本节的 Pure Mesh 重建版取代。下文"V1"各节只作历史记录，不再描述当前资产、容量或骨骼。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | P9-01 28发扩容弹匣 / `apocalypse_firstlight:p9_01_extended_magazine`（Registry ID 未变，旧存档物品直接沿用） |
| 容量 | 28 发 9×19mm；标准 17 → 28，+11（约 +65%） |
| 兼容 / 入口 | 仅 P9-01；维护台与 Z Field Attachment View；NBT 附件、服务端事务、创造标签不变 |
| 外观 | 加长冲压钢双排弹匣 + 短 polymer 握把延长套 + 加厚加固 polymer 底板，底板两侧各一块低饱和黄褐识别件 |
| 伤害 / 后坐 / 射速 / 换弹速度 | 无修改 |

### 结构与比例（标准弹匣坐标系，模型单位）

新弹匣与标准弹匣在同一个"弹匣局部坐标系"中生成（`MAG_PIVOT` [0, 3.5992, 1.647]，绕 X −22°）。

- **握把内部分与弹唇**：与标准 17 发弹匣逐环一致（离线比对偏差 0）。因此枪自己的 `follower` 骨骼、顶弹锚点和动态弹药都不用改。
- **总长**：6.99（y −3.47 … 3.52），标准为 4.51，约 1.55 倍。
- **握把底面以下外露 2.83**，自上而下：
  - polymer 延长套 0.845（占 30%）。外形沿握把轮廓（与握把 `gripPt` 同一超椭圆）直下，然后收窄贴到弹匣管上，底缘倒角。与握把底面留 0.015 的缝。
  - 冲压钢管 1.645。两侧各一条浅纵向加强筋，两端渐隐；背脊 3 个观察孔，约对应第 22 / 25 / 28 发。观察孔是画在贴图上的，没有数字。
  - 底板 0.325。标准为 0.27，加厚 +20%；比钢管宽 7%，下半段前后略外扩；上下倒角，一圈浅抓取槽，底面画一个拆卸孔。
- **识别件**：底板两侧各一块 0.8 × 0.09 的小矩形，凸出 0.016，带倒角。颜色为 muted tan（124, 106, 74）。
- **托弹面**：内壁止于 y 2.99，封成一个托弹板色的平面。
  - 装在枪上时，它位于枪自身 `follower`（顶面 3.065）之下，看到的仍是原托弹板。
  - 换弹新匣和甩出的旧匣没有独立托弹板骨骼，就由这个平面充当托弹板。

### 材质与 PBR（512 atlas，Base Color 与 `_s` / `_n` 同一次光栅化）

| 材质 | Base Color | 平滑度 | F0 | 备注 |
|---|---|---|---|---|
| 钢管（深色氮化钢） | 60,61,64 | 118 | 金属 | 弹唇 70,71,74 / 135；加强筋 66,67,70 / 124；内壁 30,30,32、AO 175 |
| 延长套（哑光 polymer） | 33,34,36 | 52 | 介电 | 倒角面亮度 ×1.22 |
| 底板（加固 polymer） | 29,30,32 | 74 | 介电 | 抓取槽 ×0.72、AO 185；顶面接触 AO 205 |
| 识别件（muted tan） | 124,106,74 | 62 | 介电 | 倒角 132,114,80 |
| 托弹面 | 96,78,52 | 70 | 介电 | |
| 观察孔 / 拆卸孔 | 13,13,14 | 22 | 介电 | AO 120，边缘一像素抗锯齿 |

- 每个面一种干净色调，加统一的顶光项。没有磨损、污点、划痕和假反射。
- `_n` 为平面法线，B 通道存 AO。
- 可见的顶弹仍由现有 9×19mm Pure Mesh 与 PBR 绘制。

### 网格与性能

- 共 5 个 part：`magazine_body` 352、`magazine_sleeve` 392、`magazine_floorplate` 288、`magazine_ribs` 36、`magazine_accent` 80（三角面当量）。
- 合计 1148 三角面：259 Quad + 630 Triangle，共 889 面，每次绘制提交 3556 个顶点。
- 替换掉的标准弹匣（主体 282 面 + 底板 173 面）为 1820 次顶点提交，装扩容匣后每次绘制净增 1736 次；托弹板仍由枪自身骨骼绘制。
- 贴图 512×512，约 45 texel/单位。不建弹簧、托弹板内部结构或底板内部几何。

### 运行时接入（通用路径，没有新 renderer）

- `NativeMagazineItem` 默认构造（P9）：容量 28，替换骨骼 `magazine`、`reload_magazine`（`mag_out` 的子骨骼）、`empty_old_mag`，`replacesSubtree=false`。
  - 旧 V1 替换的是不存在的 `empty_old_magazine`，已修正。
  - 三个骨骼在 V2 rig 中枢轴相同，均为 [0, 3.5992, 1.647]。资产原点就是这个枢轴，22° 倾角已烘进顶点，与标准弹匣相同。
  - 子骨骼照常遍历，所以 `follower` 和三个顶弹锚点（`magazine_round_anchor` / `mag_out_round_anchor` / `empty_old_mag_round_anchor`）保持有效。
  - 动态弹药继续用 `NativeMagazineRoundRendering` 和 P9 的 `magazine_round_visual`，不需要额外配置；它只画顶弹，与容量无关。
- 渲染：`NativeMagazineRendering` → `NativeMuzzleRendering.drawItem` → AFL Hybrid Mesh sidecar。
  - 第一人称、第三人称、换弹动画、维护台（`MaintenanceGunRendering` 已调用同一替换）与 Field 视图共用这条路径。
  - 换弹新匣与旧匣的显隐完全由现有动画骨骼缩放决定，没有改动画。
- 物品栏 / 掉落物 / 手持物品：`NativeMagazineRendering.ItemRenderer`，`itemLift` 3.58。
  - 新增 `NativeMagazineItem.itemTilt` = 22：绕 X +22° 撤销烘进资产的握把倾角，让独立物品竖直显示。
  - BR51 等其他弹匣走原 5 参构造，倾角为 0，行为不变。
  - 物品模型 `models/item/p9_01_extended_magazine.json`（`builtin/entity`）沿用。
- 维护台热点：`magazine_slot.hotspot_offset [0,−4.14,1.82]` 指向原装底板中心。装扩容匣后，这个点落在延长套 / 钢管上，仍在弹匣上。

### 文件

- 生成器：`tools/build-p9-01-extended-magazine.mjs`（加 `--check` 为只校验；附带贴合检查）。
- 编辑源：`src/main/blockbench/p9_01_extended_magazine_mesh.bbmodel`（Free Model，5 个 Mesh，内嵌 Base Color）。
  - 源贴图：`src/main/blockbench/textures/p9_01_extended_magazine{,_s,_n}.png`。
- 运行时：`geo/p9_01_extended_magazine.geo.json`（仅 `magazine_root` 骨骼）、`meshes/p9_01_extended_magazine.aflmesh.json`（V2）、`textures/item/p9_01_extended_magazine{,_s,_n}.png`。
- 旧源 `p9_01_extended_magazine.bbmodel`、`p9_01_extended_magazine_fit_preview.bbmodel`、`polish_p9_magazines.cjs` 只作历史保留，不再导出。

### 离线验证与未确认项

- 离线检查：
  - 生成器 `--check` 可重复。
  - 弹唇与标准弹匣偏差为 0。
  - 握把最低点 y −0.64，延长套顶 −0.655。
  - 延长套各环完全包住钢管，最小余量 0.196。
  - 底板比钢管宽。
  - 撤销倾角后，物品包围盒中心 y −3.574，与 `itemLift` 一致。
  - `verify-afl-mesh.mjs --java-loader` 用真实 Java 加载器通过全部生产 sidecar。
  - 离线软件渲染：装枪侧视和斜视比例正常，倾角与握把一致。
- 未实机确认：
  - 换弹时左手握新匣的位置正好在延长套 / 钢管上，是否有可见穿手。
  - 第一人称握把下方延长部分与右手模型的关系。
  - 光影下的 PBR 观感。
- 开发用 GameTest `ExtendedMagazineTests` 与客户端 probe 已同步为 28 发断言（17→28 补 11 发、溢出 11 发），本轮未运行。

---

# P9-01 24-Round Extended Magazine V1（历史：已退役 rig）

> 2026-09-27 P9 Native V2 cutover: the 24-round attachment item, capacity rule and installation data remain registered, but the mounted model and reload visuals below were built for the retired P9 rig. The new `p9_01_v2_native` rig uses `magazine` / `empty_old_mag`, so the old replacement-bone contract and mounted fit require a separate V2 pass. The historical visual/client acceptance below does not verify the new P9. A new 24-round magazine is deferred.

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **29**，客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](attachments/README.md)。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | P9-01 扩容弹匣 / `apocalypse_firstlight:p9_01_extended_magazine` |
| 槽位 / 当前兼容 | `MAGAZINE` / 仅 P9-01 |
| 容量 | 覆盖为 24 发；标准 17 → 24，差值 +7 |
| 安装弹药 | 保留原有余弹，不自动补满 |
| 拆卸弹药 | 枪内最多保留 17 发；多余弹药与配件安全返还，背包满时掉落 |
| 外观 | 独立扩容匣替换标准匣，保留黄色底板特征 |
| 伤害 / 后坐力 / 射程 / 换弹速度 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |

## 接入状态

Status: gameplay attachment remains implemented; V2 mounted appearance and reload integration are unverified. The earlier accepted fit applies to the retired P9 rig.

## Behavior

- Item `apocalypse_firstlight:p9_01_extended_magazine`, P9-01 24发扩容弹匣. P9-only MAGAZINE compatibility; BR51 is unchanged.
- Standard capacity 17, equipped capacity 24. Installing preserves existing rounds; reload/consumption/HUD use attachment-aware capacity.
- Removal retains at most 17 rounds and returns excess ammunition plus the removed attachment to inventory, dropping overflow when full. Transaction snapshots reject stale/repeated operations and roll back inventory if a required drop fails.
- Maintenance magazine hotspot supports installation/removal using existing timed operation and SFX. Player attachment changes use the maintenance bench or Z Field Attachment View; the V shortcut remains removed.
- The attachment tooltip retains its non-italic description but no longer shows the Magazine 17 → 24 modifier or separator lines. The gun's ordinary Tooltip V2 does not display capacity; actual attachment capacity and reload behavior remain unchanged. See [Equipment Tooltip / Gun V2](../ui/equipment_tooltip_v1.md).
- Current network protocol 22; matching client/server builds required. Earlier protocol-21 shot behavior remains intact.

## Model

The polished independent model has 23 cubes. It keeps the standard insertion width/depth and pivot. Body extends downward by 2 model units, approximately one-third longer overall, with a subdued yellow floorplate. Four solid top lips surround a recessed inner surface within the original top envelope. A basal shoulder, shallow side panels and transverse ribs add restrained depth. The standard P9 magazine and its empty-reload duplicate receive the same detailing (23 cubes each), retaining their dark base and original length. No pivot, animation, attachment or capacity changes.

`NativeMagazineRendering` replaces original cubes on `magazine` and `empty_old_magazine` using their existing animated matrices. The current P9 `reload_magazine` is an empty helper, so no duplicate geometry is drawn there. Tactical and empty reload animations are reused unchanged; maintenance renders the replacement at the original bind pose.

Editable sources:

- `src/main/blockbench/p9_01_extended_magazine.bbmodel`
- `src/main/blockbench/p9_01_extended_magazine_fit_preview.bbmodel` (mounted fit reference only)
- `src/main/blockbench/p9_01.bbmodel` (standard and empty-reload magazine detailing)
- `src/main/blockbench/polish_p9_magazines.cjs` records the one-time geometry generation from the 11-cube baseline; it intentionally rejects already-polished input.

Runtime files under `src/main/resources/assets/apocalypse_firstlight/`:

- `geo/p9_01_extended_magazine.geo.json`
- `textures/item/p9_01_extended_magazine.png`
- `models/item/p9_01_extended_magazine.json`
- `geo/p9_01.geo.json` (only standard magazine and empty-reload duplicate geometry changed)

Historical implementation: `NativeMagazineItem`, `NativeMagazineMount`, `NativeMagazineRendering`, attachment-aware `NativeGunAmmo`, retired `P901Renderer`, `MaintenanceGunRendering`, `MaintenanceAttachmentTransaction`. Current P9 uses `NativeAnimatedWeaponRenderer`; its V2 replacement fit has not been accepted.

## Verification (historical integration results)

The V quick-exchange path in the results below has since been retired. These old probes do not validate current player installation; current player operations use the maintenance bench or Z Field Attachment View; these historical tests do not verify the new Field entry.

Latest relief polish: live Blockbench independent and mounted extended-magazine previews inspected; all faces reference valid texture UUIDs. Offline rebuild passed in `build/p9-magazine-relief-build.log`, producing `build/libs/apocalypse_firstlight-1.0.0.jar`. This polish did not launch a graphical game client or rerun GameTests; the results below belong to the preceding V1 implementation, not the revised geometry.

- Live Blockbench independent model and mounted P9 preview inspected: interface aligned, extension and yellow base visible.
- Offline build passed: `build/extended-magazine-build.log`.
- GameTests 26/26 passed: `build/extended-magazine-tests.log`. Capacity, reload, consumption, persistence, compatibility, repeated/stale transactions, two FakePlayer contenders and full-inventory item/ammo conservation covered. This is not a two-client test.
- Isolated graphical client passed capacity synchronization, actual fire, tactical reload to 24, empty reload and maintenance hotspot presence: `build/extended-magazine-client.log`. Client exited automatically.
- Inspected `build/thermal-fluid-client/screenshots/magazine_maintenance.png`: actual mounted P9 fit and size correct in this view. Independent inventory icon also inspected.
- Empty reload frame 16 visibly shows the yellow-bottom replacement. Other first-person/reload samples are partly hand-occluded: exhaustive no-clipping verification is not claimed.
- Third-person ran but hands obscure the magazine in its front-facing screenshot. Third-person close-up fit, dropped-gun appearance, graphical install/remove refresh and real multiplayer remain unverified. Initial client rendering fixture used injected attachment NBT; legitimate transactions were tested server-side.
- Client log also contains an unrelated pre-existing native-gun smoke failure from a source path relative to the isolated working directory. Magazine probe passed; not every unrelated startup probe passed.

The original P9 task added no drum, second P9 capacity or new reload animation. BR51 now separately supports [its 35R magazine](attachments/br51_extended_magazine_35_v1.md); it does not share compatibility with P9. NativeMagazineItem now holds per-accessory configuration; the P9 default remains 24 rounds and its original rendering bones/placement.
