# Development Renderer Test Environment

## Current setup

The normal ForgeGradle `runClient` is the daily AFL client and automatically installs the same renderer versions used by the packaged PCL test client:

- `embeddium-0.3.31+mc1.20.1.jar`
- `oculus-mc1.20.1-1.8.0.jar`
- `DistantHorizons-3.0.3-b-1.20.1-fabric-forge.jar` (Forge-compatible build)

`build.gradle` supplies these optional mods through `runtimeOnly fg.deobf(...)` from Curse Maven. Distant Horizons is used only to inspect distant Terrain V2 coastlines, sea, roads, bridges, and future city layouts in the development client. It is not an AFL compile dependency, JAR inclusion, or `mods.toml` prerequisite. Players and dedicated servers do not need it. Keep shaders off for baseline terrain reviews and adjust LOD distance locally (for example, 256–512 chunks) without committing personal DH configuration.

Run the daily client with:

```powershell
.\gradlew.bat runClient --offline
```

Place local shader packs in `run/shaderpacks/`. Shader packs are local test inputs and must not be committed or packaged with AFL. Oculus shader selection remains local client state under `run/`.

## Pure Forge smoke test

`runClientPureForge` uses the separate `run-pure-forge/` working directory. Pass `-PaflWithoutShaders` to omit the optional renderer stack and Distant Horizons from its runtime classpath.

```powershell
.\gradlew.bat runClientPureForge -PaflWithoutShaders --offline
```

Use this client before release and after rendering changes to check startup, world entry, custom models, HUD rendering, fluids, and translucent materials. Keep claims distinct: reaching the title screen proves startup only; world, model, HUD, fluid, transparency, and named-shader behavior require their own in-world visual checks.

## Dependency boundary

`mods.toml` must not declare Embeddium, Oculus, or Distant Horizons as mandatory AFL dependencies. The published AFL JAR must not contain their classes or JARs. The pinned CurseForge file IDs are in `gradle.properties`. Client startup and in-world compatibility still require manual verification.
