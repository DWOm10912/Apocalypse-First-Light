# Development Renderer Test Environment

## Current setup

The normal ForgeGradle `runClient` is the daily AFL client and automatically installs the same renderer versions used by the packaged PCL test client:

- `embeddium-0.3.31+mc1.20.1.jar`
- `oculus-mc1.20.1-1.8.0.jar`

They are resolved and userdev-remapped through the dedicated `shaderClientRuntime` configuration, then copied to `run/mods/` by `prepareShaderClientMods`. ForgeGradle remapping is required because Oculus references production/SRG names that do not directly match the Mojang-mapped development client. This configuration is development-only: neither artifact is on AFL's main compile/runtime classpath, bundled into the AFL JAR, or declared as a dependency in `META-INF/mods.toml`.

Run the daily client with:

```powershell
.\gradlew.bat runClient --offline
```

Place local shader packs in `run/shaderpacks/`. Shader packs are local test inputs and must not be committed or packaged with AFL. Oculus shader selection remains local client state under `run/`.

## Pure Forge smoke test

`runClientPureForge` uses the separate `run-pure-forge/` working directory. It does not receive the renderer-stack copy task, so Embeddium and Oculus are absent unless somebody manually places unrelated files in that instance.

```powershell
.\gradlew.bat runClientPureForge --offline
```

Use this client before release and after rendering changes to check startup, world entry, custom models, HUD rendering, fluids, and translucent materials. Keep claims distinct: reaching the title screen proves startup only; world, model, HUD, fluid, transparency, and named-shader behavior require their own in-world visual checks.

## Dependency boundary

`mods.toml` must not declare Embeddium or Oculus as mandatory AFL dependencies. The published AFL JAR must not contain either mod's classes or JAR. Updating the PCL renderer versions requires updating the two pinned CurseForge file IDs in `gradle.properties` and rerunning both client environments.
