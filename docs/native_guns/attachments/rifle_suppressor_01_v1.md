# BR51 Generic Rifle Suppressor Integration V1

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](README.md)。下表为当前已实现效果。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | 步枪消音器 / `apocalypse_firstlight:rifle_suppressor_01` |
| 槽位 / 当前兼容 | `MUZZLE` / BR51-01；通用实现，非 BR51 专属 |
| 噪声半径倍率 | `0.05`；BR51 `112 → 6` 格 |
| 消音声 | 枪械定义 `apocalypse_firstlight:br51_01_suppressed` |
| 玩家音频距离 | 不乘降噪倍率 |
| 视觉出口 | 配件 `muzzle_exit_anchor`；抑制裸枪焰，使用消音出口视觉 |
| 伤害 / 射程 / 后坐力 / 射速 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |
| 维护台提交 | 共用操作音效，51 tick 后重新验证并提交 |

## 接入说明

`apocalypse_firstlight:rifle_suppressor_01` (步枪消音器 / Rifle Suppressor) is a real `NativeSuppressorItem`, slot `MUZZLE`. BR51 is its first compatible gun, not an exclusive item implementation. Future rifles opt in through their gun definition's `muzzle_slot.accepts` and supply their own mount anchor.

## Shared implementation

- BR51 data: `src/main/resources/data/apocalypse_firstlight/native_guns/br51_01.json`. No damage/range/recoil/reload/fire-rate changes.
- All player installs/removals/replacements use the maintenance bench. The V key mapping is removed and the legacy exchange packet is inert (wire ID retained). State lives in the real gun ItemStack; no rifle-specific NBT or packet.
- MaintenanceHotspots and MaintenanceAttachmentTransaction now use NativeAttachments.supportsSlot instead of P9 identity. Context HUD, candidate inventory projection, install/remove/replace, return handling and revision checks are unchanged shared paths. BR51 does not gain sight or magazine compatibility.
- Shared operation sound is 2.480 seconds. Server waits 51 ticks and revalidates before commit. UI uses the existing vanilla button click. No BR51-specific UI or sound-delay mechanism; network protocol remains 22.
- Simplified localized item tooltip describes a rifle muzzle device, without debug/key instructions.

## Rendering and sound

- Independent geo/texture: `assets/apocalypse_firstlight/geo/rifle_suppressor_01.geo.json` and `textures/item/rifle_suppressor_01.png`. Approved model and neutral BR51-derived palette are preserved.
- Editable sources remain under `src/main/blockbench/`. Production BR51 gains only an empty `muzzle_anchor`, parent `positioning2`, at `[0,11.4375,-26.2]`. Original cubes and animations stay unchanged. This seat sleeves over the original flash hider.
- Shared renderer mounts `rifle_suppressor_root`; accessory `muzzle_exit_anchor` is `[0,0,-11.55]`. Mounted exit is `[0,11.4375,-37.75]` before animation/render transforms.
- Equipped guns capture muzzle visuals at the definition's mounting anchor plus accessory exit. Bare BR51 still uses its original `muzzle_pos` and barrel offset. Existing shotId snapshot/tracer and suppressed FX consume the resolved exit, without changing server hit authority.
- Gun-defined `suppressed_fire_sound`: `apocalypse_firstlight:br51_01_suppressed`, mapped in sounds.json to `br51_01/suppressed.ogg`. Imported from user `E:/Download/br_51_suppressed.ogg`, converted from stereo to 48 kHz mono for positional playback; original untouched. Bare BR51 sound remains unchanged.
- Shared noise formula `max(1, round(base * 0.05))`: BR51 112 → 6; P9 64 → 3. Actual audio attenuation is not multiplied by 0.05. Sound belongs to the gun, not the generic accessory.

## Verification (historical integration results)

The V quick-exchange path in the results below has since been retired. These old probes do not validate current player installation; current player operations require the maintenance bench.

- Final full offline rebuild passed (all 11 tasks re-executed, 30 seconds), `build/rifle-suppressor-release.log`; compiled main class restored and `build/libs/apocalypse_firstlight-1.0.0.jar` regenerated.
- Server GameTests: 28/28 required tests passed (`build/rifle-suppressor-tests-final.log`). Includes BR51 quick-exchange, noise/sound selection, stack persistence, 20 stale maintenance install races with replacement/removal conservation, existing shared 51-tick operation tests and P9 sight/suppressor/24R regressions.
- Isolated graphical/network probe passed (`build/rifle-suppressor-client.log`): existing exchange packet/client sync, baked accessory exit exactly -11.55/16, bare and suppressed sound events, maintenance stack transfer. First-person, third-person and maintenance screenshots inspected under `build/thermal-fluid-client/screenshots/rifle_suppressor_*.png`.
- User visually accepted the result and requested completion. No real two-client multiplayer run; FakePlayer tests establish transaction safety, not two-client visual synchronization.
- The additional mouse-driven maintenance probe did not complete: its first launch encountered transient class-output deletion contention; the retry hit missing compiled main classes. This is not recorded as a passed UI/delay client test. Shared delay is covered by server tests; a full rebuild restores deliverable outputs. No further client testing after user acceptance.
- Accessory geo SHA-256 remained `AE4BACAE6273B88022A6C9DA0A72302D777F531D0FC8043A258F9840AB60EFE1`; production BR51 geometry change is only the empty mounting anchor. No balance or animation edits.
