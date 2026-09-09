# P9-01 24-Round Extended Magazine V1

Status: implemented; user accepted mounted fit and requested ending the task. Verification limits remain below.

## Behavior

- Item `apocalypse_firstlight:p9_01_extended_magazine`, P9-01 24发扩容弹匣. P9-only MAGAZINE compatibility; BR51 is unchanged.
- Standard capacity 17, equipped capacity 24. Installing preserves existing rounds; reload/consumption/HUD use attachment-aware capacity.
- Removal retains at most 17 rounds and returns excess ammunition plus the removed attachment to inventory, dropping overflow when full. Transaction snapshots reject stale/repeated operations and roll back inventory if a required drop fails.
- Maintenance magazine hotspot supports installation/removal using existing timed operation and SFX. V-key remains sight/muzzle only.
- Localized magazine tooltip includes 24 rounds; P9 static capacity specification follows its attachment. No new damage, recoil, range, audio or animations.
- Current network protocol 22; matching client/server builds required. Earlier protocol-21 shot behavior remains intact.

## Model

Independent 11-cube model keeps the standard insertion width/depth and pivot. Body extends downward by 2 model units, approximately one-third longer overall, with a subdued yellow floorplate. The insertion end stays fixed.

`NativeMagazineRendering` replaces original cubes on `magazine` and `empty_old_magazine` using their existing animated matrices. The current P9 `reload_magazine` is an empty helper, so no duplicate geometry is drawn there. Tactical and empty reload animations are reused unchanged; maintenance renders the replacement at the original bind pose.

Editable sources:

- `src/main/blockbench/p9_01_extended_magazine.bbmodel`
- `src/main/blockbench/p9_01_extended_magazine_fit_preview.bbmodel` (mounted fit reference only)

Runtime files under `src/main/resources/assets/apocalypse_firstlight/`:

- `geo/p9_01_extended_magazine.geo.json`
- `textures/item/p9_01_extended_magazine.png`
- `models/item/p9_01_extended_magazine.json`

Implementation: `NativeMagazineItem`, `NativeMagazineMount`, `NativeMagazineRendering`, attachment-aware `NativeGunAmmo`, `P901Renderer`, `MaintenanceGunRendering`, `MaintenanceAttachmentTransaction`.

## Verification

- Live Blockbench independent model and mounted P9 preview inspected: interface aligned, extension and yellow base visible.
- Offline build passed: `build/extended-magazine-build.log`.
- GameTests 26/26 passed: `build/extended-magazine-tests.log`. Capacity, reload, consumption, persistence, compatibility, repeated/stale transactions, two FakePlayer contenders and full-inventory item/ammo conservation covered. This is not a two-client test.
- Isolated graphical client passed capacity synchronization, actual fire, tactical reload to 24, empty reload and maintenance hotspot presence: `build/extended-magazine-client.log`. Client exited automatically.
- Inspected `build/thermal-fluid-client/screenshots/magazine_maintenance.png`: actual mounted P9 fit and size correct in this view. Independent inventory icon also inspected.
- Empty reload frame 16 visibly shows the yellow-bottom replacement. Other first-person/reload samples are partly hand-occluded: exhaustive no-clipping verification is not claimed.
- Third-person ran but hands obscure the magazine in its front-facing screenshot. Third-person close-up fit, dropped-gun appearance, graphical install/remove refresh and real multiplayer remain unverified. Initial client rendering fixture used injected attachment NBT; legitimate transactions were tested server-side.
- Client log also contains an unrelated pre-existing native-gun smoke failure from a source path relative to the isolated working directory. Magazine probe passed; not every unrelated startup probe passed.

No drum, second capacity, new reload animation or BR51 extension added.
