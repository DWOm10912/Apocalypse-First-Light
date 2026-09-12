# Gun Maintenance Attachment UI + SFX Polish V1

> 协议更新（Inspect V1）：当前共享通道为 **23**，新增轻量检视请求，客户端/服务端须匹配。下文关于协议 22 的描述属于历史版本；配件仍仅通过维护台安装，V 现用于检视。见 docs/native_guns/native_inspect_v1.md。

2026-09-09. Extends the accepted attachment interaction without changing hotspots, projection, HUD outward placement, compatibility, return-origin rules, gun rendering or atomic inventory exchange.

## UI

Context buttons use full, non-overlapping rectangles: Modify/Replace 62×20 GUI px at HUD+(6,38), Remove 60×20 at +(74,38). Dark gradient plates, one-pixel grey border, brighter hover/top edge and bright centered text. Press feedback is held for 80 ms before the UI action runs, without moving any inventory item. Candidate cells use the same idle/hover/pressed plate treatment. No hover sound, neon, container GUI or new layout.

Every accepted Modify/Replace/Remove/candidate/cancel click plays Vanilla `UI_BUTTON_CLICK`, pitch 1, volume 0.35. Disabled/repeated clicks do not play or submit. No custom button audio asset. During an operation buttons are disabled, Context target remains locked and `处理中...` / `Working...` is shown. Esc exits maintenance during a pending operation, cancelling on the server; right click is ignored while pending. Normal layered cancellation remains unchanged outside operations.

## Shared operation sound

- Source: `E:/Download/attachment_sound.ogg`.
- Runtime copy: `src/main/resources/assets/apocalypse_firstlight/sounds/attachment_sound.ogg`.
- Both SHA256: `c8d70857e470c733025282a6a24bb1b7ad9b5255de54b826c0cc7931c057b4a1`.
- Vorbis, stereo, 48000 Hz, **2.480000 seconds**, copied byte-for-byte; no re-synthesis, edits, separate install/remove files or pitch modification.
- Registry: `AflSounds.ATTACHMENT_OPERATION`, `apocalypse_firstlight:attachment_operation`; `sounds.json` preload and shared subtitle `subtitles.apocalypse_firstlight.attachment_operation`.
- Operator-local UI playback at pitch 1, volume 0.8, only after server approval. Install, remove and replace all reuse this single event. No second success sound at commit. Cancellation/exit stops the local instance; source never controls NBT.

## Authoritative action window

`MaintenanceAttachmentOperation` wraps the original `MaintenanceAttachmentTransaction`. `valid` is shared by Begin and final commit. Server keeps one pending action per player, a copied request, original menu identity and server game-time deadline. **51 ticks = ceil(2.480×20)+1**, nominal **2.55 s** at 20 TPS. Lower TPS delays commit rather than shortening the action. No client completion packet is trusted or required. Timing follows the specified fixed server duration; network/audio-device stalls are not an absolute audible-playback completion guarantee.

Begin validates and sends START; client plays the sound. Gun/attachments/inventory remain untouched during the window. Each server tick rejects invalid menu, player, root/world/distance, revision, full gun snapshot, compatibility or exact source. At deadline the existing atomic transaction revalidates and commits once. Logout and server shutdown clear pending state. Two players can begin against one revision; after the first commit the second becomes stale and cannot consume/return an item. Repeated Begin cannot reset the timer. No reservation, item preview, arm animation or local NBT mutation.

Network protocol **19**: MaintenanceResult phases 0 rejected/cancelled, 1 committed, 2 started. Matching client/server builds required. `MaintenanceActionState` remains a generic action marker for future animation integration, not a sound-specific workflow.

## Verification

ffprobe confirms format/duration; source, runtime and final JAR hashes prove the user asset is unchanged. Offline `build runGameTestServer` passed all 25 required tests (`build/maintenance-polish-tests.log`). Timed operation coverage includes install/remove/replace, unchanged early state, duplicate Begin, menu close, source loss and a two-FakePlayer stale-revision race. The fixture supplies a supporting floor so workstation survival does not invalidate the test.

The isolated graphical client passed actual MouseHandler input through installation of both attachments, removal, cancellation and origin return (`build/maintenance-polish-client.log`). It observed exactly three shared operation sound events and checked unchanged gun state during the early action window. Context and candidate screenshots under `build/thermal-fluid-client/screenshots/maintenance_attachments_*.png` were inspected. The isolated client exited successfully; the user's ordinary world was not used.

`build/libs/apocalypse_firstlight-1.0.0.jar` contains the sound registration, unchanged audio and operation class; development test classes are excluded. Tests are not a substitute for human loudness/comfort acceptance. True two-client multiplayer latency/audio synchronization was not tested; the server-side FakePlayer race does not establish that verification.
> Current channel protocol: **22**, adding P9 MAGAZINE support while retaining atomic shot confirmation. Earlier protocol references below are historical. Matching client/server required. See [24R magazine](../p9_01_extended_magazine_v1.md).
