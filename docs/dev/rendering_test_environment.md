# Development Renderer Test Environment

## Current setup

The normal ForgeGradle `runClient` is the daily AFL client and automatically installs the same renderer versions used by the packaged PCL test client:

- `embeddium-0.3.31+mc1.20.1.jar`
- `oculus-mc1.20.1-1.8.0.jar`

`build.gradle` supplies these optional renderer mods through `runtimeOnly fg.deobf(...)` from Curse Maven. Distant Horizons is no longer included in the development runtime. Keep shaders off for baseline terrain reviews.

Run the daily client with:

```powershell
.\gradlew.bat runClient --offline
```

Place local shader packs in `run/shaderpacks/`. Shader packs are local test inputs and must not be committed or packaged with AFL. Oculus shader selection remains local client state under `run/`.

## Pure Forge smoke test

`runClientPureForge` uses the separate `run-pure-forge/` working directory. Pass `-PaflWithoutShaders` to omit the optional renderer stack from its runtime classpath.

```powershell
.\gradlew.bat runClientPureForge -PaflWithoutShaders --offline
```

Use this client before release and after rendering changes to check startup, world entry, custom models, HUD rendering, fluids, and translucent materials. Keep claims distinct: reaching the title screen proves startup only; world, model, HUD, fluid, transparency, and named-shader behavior require their own in-world visual checks.

## Dependency boundary

`mods.toml` must not declare Embeddium or Oculus as mandatory AFL dependencies. The published AFL JAR must not contain their classes or JARs. The pinned CurseForge file IDs are in `gradle.properties`. Client startup and in-world compatibility still require manual verification.
