# Suppressor Muzzle Smoke V1

Status: Vanilla smoke restoration implemented in code. In-game visual acceptance is pending.

Each confirmed suppressed shot creates **one** `ParticleTypes.SMOKE` particle. The existing `NativeGunNoise.resolve` selects the suppressed branch, which still omits ordinary muzzle flash. Unsuppressed effects are unchanged. The previously introduced `apocalypse_firstlight:suppressor_muzzle_smoke` type, provider, and particle JSON have been removed; `chamber_smoke_base.png` remains untouched and is no longer used by this effect.

`NativeGunFx` creates the Vanilla particle through the client `ParticleEngine`, then sets its position exactly to the muzzle outlet, sets its speed, scales its visible quad to **35%** of the standard Vanilla smoke size, and limits lifetime to **6 ticks**. Velocity is **0.012 block/tick** along the muzzle axis plus **0.002 upward**. Count is **1 per shot**, within the requested 1–2 range. This keeps Vanilla's gray smoke sprite and animation while reducing its size and time on screen.

The confirmed first-person snapshot still validates the shot, but the particle waits for the next actual gun render and is emitted from that frame's accessory `muzzle_exit_anchor`. Third person uses the current `NativeGunFx.anchor` pose after `NativeMuzzleRendering.applyExit`. Both paths begin **0.008 block** just ahead of the front-face locator. A confirmed shot without a valid local snapshot retains the existing no-fabricated-FX behavior. Tracer and ordinary flash snapshot timing are unchanged. No suppressor dimensions are hard-coded.

The code change is in `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeGunFx.java`; the previous custom type and provider registrations are removed from `registry/AflParticles.java` and `client/AflParticleProviders.java`. The custom `client/SuppressorMuzzleSmokeParticle.java` and `assets/apocalypse_firstlight/particles/suppressor_muzzle_smoke.json` are deleted.

User visual checks: P9 with suppressor during hipfire and ADS bursts, muzzle alignment at different aim directions, third-person outlet alignment, Shader OFF, Oculus with Complementary, and Oculus with Sundial. Compilation does not verify graphical alignment or smoke visibility.
