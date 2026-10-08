# Highway V2-0 offline demo

See [中文原型报告](../../docs/worldgen/highway_v2_0_mesh_prototype.md), [architecture audit](../../docs/worldgen/highway_v2_0_architecture_audit.md), and [design contract](../../docs/worldgen/highway_v2_0_design_contract.md).

```powershell
node tools/highway-v2-0/generate.mjs
node tools/highway-v2-0/verify.mjs
node tools/highway-v2-0/generate.mjs --check
node tools/highway-v2-0/verify.mjs --check
node tools/highway-v2-0/verify-loader.mjs
```

Run from repository root. `verify-loader` uses an existing JDK17+ and cached Gson, optionally selected with `JAVA_HOME` / `GRADLE_USER_HOME`. No download, Gradle, Minecraft or world write. Open `demo/preview.html` for the offline interactive preview. It is a geometry/color inspector, not a Minecraft/PBR renderer.

`recipe.json` → `geometry.mjs` → generated Free Model sources in `src/main/blockbench/dev/highway_v2_0/` → **existing** `../export-afl-mesh.mjs` → four isolated `.aflmesh.json`/`.geo.json` pairs. Do not copy demo assets into runtime resources or register these IDs automatically. Generated outputs are never hand edited.

The default four-lane, 38 m wide, 256 m long road is a feasibility example. Other parameters need corresponding acceptance expectations; six lanes, merge topology and real chunk clipping through curves are not implemented. `validation.json` and `loader-validation.json` distinguish geometry and actual loader checks from unperformed client/runtime tests.
