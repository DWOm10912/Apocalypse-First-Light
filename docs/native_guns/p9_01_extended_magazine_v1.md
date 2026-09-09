# P9-01 24-Round Extended Magazine V1

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

Status: implemented; user accepted mounted fit and requested ending the task. Verification limits remain below.

## Behavior

- Item `apocalypse_firstlight:p9_01_extended_magazine`, P9-01 24发扩容弹匣. P9-only MAGAZINE compatibility; BR51 is unchanged.
- Standard capacity 17, equipped capacity 24. Installing preserves existing rounds; reload/consumption/HUD use attachment-aware capacity.
- Removal retains at most 17 rounds and returns excess ammunition plus the removed attachment to inventory, dropping overflow when full. Transaction snapshots reject stale/repeated operations and roll back inventory if a required drop fails.
- Maintenance magazine hotspot supports installation/removal using existing timed operation and SFX. All player attachment changes require the maintenance bench; the V shortcut is removed.
- Unified equipment tooltip shows a non-italic description and Magazine 17 → 24 modifier; the gun's dynamic magazine stat follows its actual attachment. See [Equipment Tooltip V1](../ui/equipment_tooltip_v1.md). No new damage, recoil, range, audio or animations.
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

Implementation: `NativeMagazineItem`, `NativeMagazineMount`, `NativeMagazineRendering`, attachment-aware `NativeGunAmmo`, `P901Renderer`, `MaintenanceGunRendering`, `MaintenanceAttachmentTransaction`.

## Verification (historical integration results)

The V quick-exchange path in the results below has since been retired. These old probes do not validate current player installation; current player operations require the maintenance bench.

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
