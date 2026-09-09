# Timed local first-person muzzle flash V1

## Current behavior

- Successful local unsuppressed shot with a valid snapshot: first flash frame stays at the frozen muzzle, matching the existing tracer visual origin before deferred recoil.
- `NativeFlashLifetime` begins a 50,000,000 ns (50 ms) monotonic timer at the first world draw submission. Lifetime is not a frame count and is not restarted on subsequent render calls.
- Following frames draw the fading tail in `NativeGunFx.anchor` under the current final muzzle pose, including recoil, ADS, sway and resolved attachment exit. Existing size, random roll/scale/alpha and fade curve retained. No detached multi-frame world core.
- The same shot cannot draw both snapshot and attached tail in the same render frame. Repeated hand passes are deduplicated by frame. Each confirmed shot owns an independent timer; existing 128-effect cap retained.
- Clear on world replacement, wrong held gun identity, non-first-person view, player death, menu/pause, or expiry. Effects waiting for first presentation retain the existing three-game-tick stale cutoff.
- Third-person flash timing is unchanged. Suppressed shots retain only their existing smoke. Casing, tracer coordinates/endpoints, shot IDs, snapshot validity checks, networking, recoil values/deferral and RenderType are unchanged.

## Scope / known limits

This fixes the one-render-frame visibility lifetime, not shader coordinates or every possible missing-shot visual. Invalid/stale/mismatched snapshots still skip local visuals under existing rules. No fabricated fallback flash was introduced. A draw submission is not proof that a pixel reached the screen. Camera movement/transition from the first frozen frame to the live tail requires visual validation. Shader compatibility remains unresolved and paused pending a stable Shader OFF baseline.

## Verification

- Standalone `src/dev/tests/NativeFlashLifetimeTest.java`: PASS at simulated 30/60/120/300/600/1000 FPS, with 2/3/6/15/30/50 visible submissions respectively in the same 50 ms interval. Also checks exact expiry, duplicate world/hand calls, no tail on first frame, no frozen replay, delayed frames and independent shots. This is lifecycle simulation, not graphical FPS testing.
- Build: PASS, 21 seconds (`build/flash-lifetime-build.log`, `gradlew.bat build --offline`).
- Deployment: client process absent; PCL `D:/Games/MC TEST/.minecraft/versions/1.20.1-Forge_47.4.22/mods/apocalypse_firstlight-1.0.0.jar` replaced and SHA256 matched build output: `4B072E24633607184E9F568C5C68EC662B9B338EE57AEABFA14E25F122234462`. Previous baseline jar backed up under `C:/Users/willi/AppData/Local/Temp/afl-flash-lifetime-backup-4eab68c3-318a-4bb0-9270-869cc33e6d91/`.
- Independent client: USER_VALIDATION_REQUIRED. Test Shader OFF first, P9/BR51 at 120 FPS and uncapped, HIP/ADS and sustained fire; confirm no floating core and unchanged tracer. No runtime PASS claimed.
