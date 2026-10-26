**The short answers:**
- **Motion vectors:** the mod never submits previous-frame transforms or previous-frame vertex positions, and the C API structs have no field for either.
- **Skinning:** the shim exposes a skinning API, but the mod never uses it. Entity parts stay rigid, with one retained instance per part.
- **Depth:** no GL depth is sent to Remix. Depth only flows the other way, from Remix into GL, for the rain/snow pass.
- **Present:** the GLFW buffer swap is replaced by Remix's present. Vanilla's GL world image is drawn over by an opaque full-screen copy of the Remix image. The vanilla HUD is then drawn in GL on top, and the finished framebuffer is copied back to Remix, which presents it on its Vulkan swapchain.
- **D3D9:** the mod never creates a D3D9 device.

# rtx-remix-minecraft 0.1.0: mod-side subsystem map (`src/main/java`)

**Path aliases.** `M/` = `src/main/java/com/nvidia/rtxremix/minecraft/`. `J/` = `rtxremix-java/src/com/nvidia/rtxremix/` (the shim, cited only where the mod calls into it). All paths are relative to the repo root.

**RemixAPI facade methods the mod calls.** Found by grepping `src/main/java` for calls to each facade method:
`startup`, `submit`, `present`, `setupCamera`, `setCameraMediumMaterial`, `createMesh`, `destroyMesh`, `createMaterial`, `updateMaterial`, `destroyMaterial`, `createTextureFromMemory`, `createTextureFromFile`, `updateTextureFromMemory`, `destroyTexture`, `sceneInstanceAdd`, `sceneInstanceUpdate`, `sceneInstanceRemove`, `drawInstance(long,float[],int,int,long)`, `createLight`, `destroyLight`, `drawLightInstance`, `setConfigVariable`, `getConfigVariableResolved`, `acquireConfigLayer`, `setConfigLayerBlending`, `releaseConfigLayer`, `isConfigLayersSupported`, `dxvkGetInteropInfo`, `shutdown`.

**Never called by the mod:** `drawInstanced`, `drawInstance(InstanceInfo)` (the overload carrying the bone and GPU-instancing extensions), `dxvkCreateD3D9`, `dxvkRegisterD3D9Device`, `dxvkGetExternalSwapchain`, `dxvkGetVkImage`, `dxvkCopyRenderingOutput`, `dxvkSetDefaultOutput*`. The facade is at `J/RemixAPI.java:145-920`.

**There is no "createInstance" call.** Instances come in two forms:
- **Retained:** `sceneInstanceAdd`, then `sceneInstanceUpdate`, then `sceneInstanceRemove`, wrapped by `M/asset/RemixInstance.java:111-134,154-157`.
- **Immediate:** `drawInstance`.

**Lights** are created once with `createLight` and then submitted every frame with `drawLightInstance` (`M/asset/RemixLight.java:34-52,89`). A light update runs `createLight` again with the same hash and then destroys the old handle (`RemixLight.java:58-80`).

---

## 1. Terrain geometry capture

| Stage | Class / method (file:line) | Fed by | Remix API |
|---|---|---|---|
| Section build start | `SectionBuilderMixin.rtxremix$onBuildHead` (`M/mixin/SectionBuilderMixin.java:86-114`). Latches capture via `VanillaSectionBuilds.beginBuild` and starts `BlockQuadEmitter.beginSection` | `SectionBuilder.build` @HEAD | – |
| Per-block emit | `rtxremix$captureBlock` redirects `BlockState.isOpaqueFullCube` (`:117-135`). It calls `BlockQuadEmitter.processBlock` (`M/terrain/capture/BlockQuadEmitter.java:269`: opacity, face masks, light rules) and then `emitBlock` (`:318`). Fabric-renderer (FRAPI) models go through `FabricBakedModel.emitBlockQuads` into `RemixRenderContext` (`BlockQuadEmitter.java:358,522`) | @Redirect | – |
| Vanilla GL raster suppressed | `rtxremix$renderTypeForVanilla` returns `INVISIBLE` while capturing (`SectionBuilderMixin.java:138-145`). `BuiltChunkBuffersMixin` skips the per-layer `VertexBuffer`s (`M/mixin/BuiltChunkBuffersMixin.java:45-73`) | @Redirect | – |
| Fluids | `rtxremix$redirectFluid` (`SectionBuilderMixin.java:148-161`) calls `BlockQuadEmitter.emitFluid` / `emitFluidQuads` (`BlockQuadEmitter.java:369,544-640`) | @Redirect of `BlockRenderManager.renderFluid` | – |
| Bucketing (worker thread) | `DirectQuadWriter` writes 64-byte `HardcodedVertex` records into (category, bucketKey) buckets. Layout (`J/HardcodedVertex.java:17-31`): position, normal, uv, RGBA, and half2x16 sprite-UV bounds. It detects coplanar decals and assigns them `CAT_OVERLAY` (`DirectQuadWriter.java:304-358,500-566`), and adds `_sss` suffixes to bucket keys (`:636-651`). Layer classification is at `BlockQuadEmitter.java:1000-1007` | – | – |
| Section result | `SectionBuilderMixin.rtxremix$onBuildReturn` calls `finishSection`, producing `SectionBlockData` / `SectionMeshData` (off-heap, reference-counted: `M/terrain/capture/SectionMeshData.java:8-80`). The result is passed to `BlockDataQueue.enqueue` (`SectionBuilderMixin.java:164-195`) | `build` @RETURN | – |
| Render-thread drain | `ChunkManager.updateVisibleChunks` (`M/terrain/ChunkManager.java:607-760`) calls `drainBlockData` (`:875`), which feeds `TerrainState` | `RemixRenderer.java:813` | – |
| Column merge (worker) | `submitDirtyBuilds` (`ChunkManager.java:1215-1315`) skips categories whose fingerprint is unchanged. It runs `BlockMeshBuilder.buildColumnMesh` (`M/terrain/BlockMeshBuilder.java:77-160`) on `meshBuildExecutor`. Pages are byte-capped, 8 MiB by default (`:41-43,259-261`). Each bucket's material comes from `resolveMaterial` (`:384-393`) | – | – (a CPU-side `RemixMesh` object only) |
| Native install | `installNextPage` (`ChunkManager.java:1405-1459`) calls `RemixMeshStore.create`, which calls **`createMesh`** (`M/asset/RemixMesh.java:208-222`). It then creates `RemixInstance(mesh)` with `columnTransform(cx,cz)` (translation `cx*16, 0, cz*16`; `:1927-1931`) and the `HIDDEN` category, and `drawFast` issues **`sceneInstanceAdd`** | Per-frame budget | `createMesh`, `sceneInstanceAdd` |
| Activation / rollback | `activateNextPage` clears `HIDDEN`; `rollbackNextActiveInstallPage` sets it again. Both go through **`sceneInstanceUpdate`** (`:1469-1500`). Replaced pages leave through `TerrainAssetRetirementQueue` | – | `sceneInstanceUpdate`, `sceneInstanceRemove`, `destroyMesh` |
| Per-frame draw | `ChunkManager.draw` (`:1729-1796`) does **not** resubmit terrain instances. They are re-added only after `hideRetainedInstances` (`:1798-1812`). The per-frame work is drawing terrain lights | – | `drawLightInstance` |
| Instance blend | `blendExtForCategory` (`:1919-1925`): CUTOUT uses `textureTimesVertexColorAlphaTest(128)`. OVERLAY uses `AlphaBlend(1)` plus the `DECAL_STATIC` category (`:1414-1417`) | – | `InstanceInfoBlendEXT` pNext |

Supporting code for visibility, pacing and allocation: `ChunkRenderingDataPreparerMixin`, `BuiltChunkStorageMixin`, `BuiltChunkMixin`, `terrain/visibility/*`, `ChunkDataSenderMixin`, `LightStorageMixin`. Categories are defined at `ChunkManager.java:16-20`.

## 2. Entity mesh submission

| Aspect | Where | Notes |
|---|---|---|
| Hook: entities vanilla draws | `WorldRendererMixin.rtxremix$captureEntityInPlace`, an @WrapOperation on `WorldRenderer.renderEntity` (`M/mixin/WorldRendererMixin.java:351-399`). Calls `RemixRenderer.renderEntityInPlace` (`M/RemixRenderer.java:653-668`), then `RemixEntityPass.captureAndSubmit` (`M/entity/RemixEntityPass.java:250-336`) | Vanilla's GL call is skipped. It re-runs (shadows off) only for the outline post-processor (`:379-397`) |
| Hook: block entities | `BlockEntityRenderDispatcherMixin`, an @WrapMethod on `render(BlockEntity,…)` (`M/mixin/BlockEntityRenderDispatcherMixin.java:32-51`). Calls `renderBlockEntityInPlace` (`RemixRenderer.java:684-705`), then `RemixBlockEntityPass` | Canvas parts get `DECAL_STATIC` (`RemixBlockEntityPass.java:56`) |
| Hook: block-breaking crack | `rtxremix$captureBlockDamageInPlace` (`WorldRendererMixin.java:411-441`) calls `RemixBlockDamagePass` | Gets `DECAL_STATIC` (`RemixBlockDamagePass.java:55`) |
| Entities outside the frustum | `finishFrame` / `captureOffFrustum` (`RemixEntityPass.java:369-495`): closest-first under a `MAX_ENTITIES` budget. Entities that own a light bypass the budget (`:496-508`). `snapshotPose` / `replayPose` (`:600-711`) reuse cuboid-only poses for distant entities; the world transform is still recomputed each frame | Called at `RemixRenderer.java:827` |
| Capture boundary | `EntityRenderDispatcherMixin` HEAD/RETURN on `render(Entity,…)` (`:30-52`) opens and closes `EntityRenderContext` (`M/entity/EntityRenderContext.java:16-34`) | Nested captures append bones |
| VertexConsumer capture | `RemixVertexConsumerProvider.getBuffer` returns one `RemixCapturingConsumer` per `RenderLayer` (`M/entity/RemixVertexConsumerProvider.java:120-126`). The consumer stores **local, untransformed** vertices and records a "bone" (pose matrix) whenever the pose changes (`M/entity/RemixCapturingConsumer.java:27-41`) | |
| ModelPart / Cuboid | `CuboidMixin`: @Inject HEAD on `renderCuboid`, cancellable (`M/mixin/CuboidMixin.java:38-76`). Passes the immutable cuboid sides, sprite, tint and pose to `acceptCuboid`. `ModelPartMixin` (`M/mixin/ModelPartMixin.java:55-100`) adds a pooled `rotate` and a singleton iterator | Cuboids are never expanded into vertices |
| Baked item quads | `BakedQuadMixin` stores a bake-time geometry digest (`M/mixin/BakedQuadMixin.java:14-52`) | Used as the mesh-pool key |
| Submit path | `submitEntity`, then `submitLayer`, then `submitRigidPart` (`M/entity/RemixEntitySubmitter.java:785-903,1183-1240`) | |
| Rigid parts (the default) | `acquirePooledMesh` (`~:1440-1466`) keys the mesh by cuboid identity plus sprite, or by vertex hash, and builds it with **`createMesh`** on a miss. `drawRetainedPart` (`~:1385-1416`) multiplies the entity root by the part's bone matrix on the CPU (`composeRigidTransform :2748`; root from `buildWorldTransform :2738`, absolute world translation). The result is **one retained instance per (owner, category, layer, part)**: `sceneInstanceAdd`, then `sceneInstanceUpdate` only when transform, mesh, flags or blend change (`RemixInstance.java:59-85,117-123`) | Tint comes from an interned per-instance `InstanceInfoBlendEXT` |
| Streamed parts | `isStreamedPerFrame` is true for TRIANGLE_STRIP layers (leads) and the beacon-beam program (`M/entity/RenderLayerUtils.java:144-166`). For these, `createTransientMesh` (`:1952-1977`) builds a **new mesh every frame** and points the same instance at it with `sceneInstanceUpdate`. Old meshes are destroyed one frame later (`retireTransientMeshes :724-735`) | Cuboids are never transient (`:1211`) |
| Changing vertices in other layers | A hashed part whose vertices change gets a new hash. That is a pool miss, so a new pooled mesh is built and the same instance handle is updated to it. Cold meshes are evicted (`:717`; cap `MAX_MESH_CACHE_ENTRIES`, default 8192, `:155-157`) | |
| Retirement | `endFrame` (`:705-722`) calls `sceneInstanceRemove` for parts not seen this frame, before `submit` (`RemixRenderer.java:987-1006`) | |
| Categories | Local player in first person: `THIRD_PERSON_PLAYER_MODEL` and `THIRD_PERSON_PLAYER_BODY` (`:1615-1620`). Map decorations: `DECAL_NO_OFFSET`. Particle-atlas parts: `PARTICLE` (`:1159-1167`) | |
| Dynamic textures | Maps and skins arrive via `NativeImageBackedTextureMixin`, `PlayerSkinTextureMixin`, `TextureManagerMixin` and `DynamicTextureCache`. They are refreshed once per frame (`:859-862`) with **`updateTextureFromMemory`** (`:2438-2475`). Glyph pages are mirrored (`GlyphAtlasTextureMixin`, `NativeImageUploadMixin`, `GlStateManagerUploadMixin`) | |

## 3. Dynamic item meshes

| Piece | Where | What it does |
|---|---|---|
| Block-item layer | `ItemRendererMixin` HEAD/RETURN on `ItemRenderer.renderItem(…BakedModel)` (`M/mixin/ItemRendererMixin.java:36-77`) | Records the item's terrain layer so held glass or ice uses the terrain atlas material (`RemixEntitySubmitter.atlasSurfaceMode / atlasLayerType`, `~:905-957`) |
| Held lit element | `ItemRendererMixin`, injected AFTER `renderBakedItemModel` (`:54-66`), calls `HeldItemEffectsRenderer.render` (with `HeldItemFlame`, `HeldItemSmoke`) | Adds flame and smoke quads in the item's pose, with the `PARTICLE` category |
| Holder and hand | `HeldItemRendererMixin` HEAD/RETURN on `renderItem(LivingEntity,…)` and `renderFirstPersonItem` (`M/mixin/HeldItemRendererMixin.java:38-86`) | Whether the element burns is decided once, in `HeldLightState` |
| Items held by other entities | Go through the normal entity capture | Baked quads become hashed rigid pooled meshes |
| First-person view model | `GameRendererMixin` redirects `HeldItemRenderer.renderItem` inside `renderHand` (`M/mixin/GameRendererMixin.java:33-53`). Calls `RemixRenderer.captureFirstPersonModel` (`:1685-1694`), then `RemixFirstPersonPass.capture` (`M/entity/RemixFirstPersonPass.java:75-138`) | Vanilla's own animation runs into a capturing `Immediate`. The hand FOV and `VIEW_MODEL_SCALE=0.1` are baked into the bones (`:44-55`) |
| View-model submission | `submitPending` calls `submitEntity(result, cameraToWorld, VIEW_MODEL, …)` (`:158-166`), from `RemixRenderer.java:863` | Same retained-instance path. A second `setupCamera(VIEW_MODEL)` with identical parameters is sent (`RemixRenderer.java:1239-1249`) |

## 4. Materials

| Item | Where | Details |
|---|---|---|
| Atlas capture | `BlockTextureManager.initialize` (`M/texture/BlockTextureManager.java:247-277`) calls `captureAtlas` (`:280-295`). The stitched RGBA8 atlas is uploaded with **`createTextureFromMemory`** (`M/asset/RemixTexture.java:46`). Reload comes from the Fabric reload listener (`M/RtxRemixMod.java:556-567`) | |
| Per-sprite entries | `buildSprites` (`:298-345`) loads `_n`, `_s` and `_sss` per sprite, bakes the `_n` AO into albedo, and uploads per-sprite albedo and PBR textures (`buildPBR :970`) | `SpriteEntry.hasSSS` and `hasTransparency` are read by `DirectQuadWriter` (`:286-289,322-330`) |
| Per-sprite materials | `getMaterial(se, lt)` / `createMaterial` (`:1069-1126`) | **No caller outside the class** (grep of `getMaterial(` across `src/main/java`). Terrain, held blocks and terrain particles all use the atlas-level materials in the next row |
| Atlas-level materials | `buildAtlasMaterials` (`:410-535`) builds atlas-sized normal, height, roughness, metallic, emissive, SSS-thickness and SSS-albedo images. It creates `atlasMaterials[5]` (SOLID, CUTOUT, CUTOUT_MIPPED, TRANS, GLASS) and `atlasMaterialsSSS[5]` (`:507-513`) | Accessed via `getAtlasMaterial` / `getAtlasMaterialSSS` (`:946-955`). Per-vertex UV bounds keep sampling and POM inside each sprite |
| Opaque / cutout | `buildAtlasOpaqueMaterialInfo` (`:547-570`): `MaterialInfoOpaqueEXT` with `useDrawCallAlphaState(true)`. Non-solid layers use alpha test type 4, reference 128. Adds roughness, metallic and height (`displaceIn 0.25`), a normal texture (`NORMAL_DX`), and an emissive texture with intensity | |
| LabPBR decode | `M/texture/LabPBRConverter.java`: roughness = `(1-s)^2` (`:33-36`); metallic if `_s.g >= 230` (`:38`); SSS if `_s.b >= 65` (`:40`); normal from `_n.rg` (`:48-62`); AO from `_n.b` (`:66-77`); height from `_n.a` (`:79-82`); emission where `_s.a < 255`, at `alpha/254` (`:138-178`) | `BuiltinEmission` adds or scales emission for small emitters |
| Emissive | Terrain intensity = 100 × a user multiplier of 1–50 (`:59-62,838`). The Copper Beacon lens has its own emission-scaled atlas material (`:577-606`). Entity EMISSIVE layers use the albedo as emissive with `BLEND_TYPE_ALPHA_EMISSIVE` (`RemixEntitySubmitter.java:2597-2637`) | |
| Subsurface `_sss` | `buildAtlasSSS` (`:612-632`). Solid blocks use the diffusion profile, with the `_sss` albedo (or the atlas) as the radius texture. Cutout blocks use the thin model: thickness texture, single-scattering albedo, measurement distance and anisotropy. Selected through `_sss` bucket keys and `BlockMeshBuilder.resolveMaterial`. Entities: `RemixEntitySubmitter.java:2652-2668` | `MaterialInfoOpaqueSubsurfaceEXT` |
| Translucent (stained glass, ice) | `buildAtlasTransMaterialInfo` (`:639-659`): `MaterialInfoTranslucentEXT`, IOR 1.52, atlas as transmittance texture, distance 0.35, no diffuse layer | |
| Clear glass | `createAtlasGlassMaterial` (`:661-674`): translucent, IOR 1.52, `useDiffuseLayer(true)` | Gets its own "glass" bucket (`BlockQuadEmitter.java:1000`) |
| Entity materials | `ensureMaterial` (`RemixEntitySubmitter.java:2089-2200`) loads `_n`, `_s` and `_sss` (`:2202-2204,2325-2327`). `createMaterial` (`:2591-2650`) handles OPAQUE, CUTOUT (alpha test), TRANSLUCENT (opaque material with alpha blend) and EMISSIVE. Block-atlas items borrow the terrain atlas materials (`:2107`) | Store calls: `createMaterial` / `updateMaterial` (`M/asset/RemixMaterial.java:51,69`) |
| Animated sprites | `SpriteContentsUploadMixin` (@HEAD `upload(IIII[NativeImage])`, `:25-44`) and `SpriteAtlasTextureMixin` (around `tickAnimatedSprites`, `:24-37`) collect patches in `AtlasAnimationForwarder`. The flush (`RemixRenderer.java:~920-945`) updates albedo, emissive, roughness, metallic and SSS with **`updateTextureFromMemory`** (`AtlasAnimationForwarder.java:422`) | |

## 5. Camera and depth

- **Camera.** `RemixRenderer.updateCamera` (`M/RemixRenderer.java:1190-1256`):
  - Builds forward, right and up vectors from the camera's yaw and pitch.
  - Takes the FOV from `GameRenderer.getFov`, made accessible by the access widener.
  - Fills `CameraInfoParameterizedEXT` with the position (absolute world position as float), forward/up/right, `fovYInDegrees`, aspect, near 0.05 and far 1000 (`:1222-1231`).
  - Calls `setupCamera` twice per frame: once for `CameraType.WORLD` and once for `VIEW_MODEL`, with identical parameters (`:1234-1249`).
- **No matrices, no jitter.** The mod passes no view or projection matrix. `grep -rni jitter src/main/java` finds nothing, so any jitter is runtime-internal.
- **Camera paths.** Poses are applied before `GameRenderer.render` (`MinecraftClientMixin.java:170-193`, calling `onMinecraftWorldRenderFrameStart :607`).
- **Underwater medium.** `updateCameraMedium` (`:1285-1318`) calls `setCameraMediumMaterial` with the water medium material when the camera is submerged in water, otherwise 0. It then calls `ConfLayerDriver.update(…, underwater, …)`.
- **GL depth sent to Remix: none.** Depth only flows from Remix to GL:
  - `GlInterop` imports Remix's colour and primary-depth images into GL (`M/gl/GlInterop.java:28-35,73-93,153`).
  - `RemixWeatherRenderer` writes that depth into `gl_FragDepth` (`DepthSeeder`, `M/weather/RemixWeatherRenderer.java:296-317`).
  - It draws with a matching `perspective(fov, aspect, 0.05, 1000)` (`:78-86`).

## 6. Motion vectors

**Searches run:**
- `grep -rniE 'motion ?vector|velocity|prevTransform|previousTransform|lastTransform|prevFrame|previousFrame|prev_?pos|history' src/main/java`
- `grep -niE 'prev|motion|velocity|history|jitter'` over `rtxremix-java/native/include/remix_c.h` and `rtxremix-java/src`

**What exists:**
- **The API has nowhere to put previous-frame data.** `remixapi_InstanceInfo` is `{sType, pNext, categoryFlags, mesh, transform, doubleSided}` (`rtxremix-java/native/include/remix_c.h:690-697`). `SceneInstanceAdd`, `SceneInstanceUpdate` and `SceneInstanceRemove` take only current info (`:703-716`).
- **The mod submits current-frame data only.** That covers retained add/update (`RemixInstance.java:126-134`) and every `drawInstance` call:
  - sky: `SkyRenderer.java:835`
  - clouds: `CloudRenderer.java:611,633`
  - particles: `RemixParticleRenderer.java:260`
  - block outline: `RemixBlockOutlinePass.java:187`
- **Particles.** Positions use vanilla's `lerp(tickDelta, prevPos, pos)` (`RemixParticleRenderer.java:385-387`). The previous position is never sent to Remix.
- **Unused category bit.** `IGNORE_MOTION_BLUR` exists (`J/InstanceCategoryBit.java:17`) but the mod never uses it.

**What the runtime is expected to derive.** Retained instance handles keep their identity across frames, so the runtime tracks per-instance transform history. The mod's comments say so directly:
- "using Remix skinning here only adds CPU bookkeeping and makes instance motion history harder to track" (`RemixEntitySubmitter.java:82-84`)
- "retaining its per-frame view-model transform/history update" (`RemixRenderer.java:1241-1245`)
- "current/previous perspective correction at identity for stable motion vectors" (`RemixFirstPersonPass.java:49-55`)

How dxvk-remix handles immediate `drawInstance` calls, or meshes that are replaced every frame, can't be checked from this repo.

**Consequences in the mod:**
- Streamed entity meshes (leads, beams) and particle buckets get a new mesh whenever their contents change (`RemixEntitySubmitter.java:1952-1977`; `RemixParticleRenderer.java:832`). There is no per-vertex history.
- Terrain instances are static; they change only when a page is replaced.
- Replayed off-screen poses lag their animation but keep the current world transform (`RemixEntityPass.java:47-50`).

## 7. Lights

| Light | Source | Shape | Lifecycle |
|---|---|---|---|
| Terrain block lights | Rules in `rtx_remix/block_lights/*.json` (`M/light/BlockLightRuleManager.java:45,289-305`), resolved per block on the worker in `processBlock` (`BlockQuadEmitter.java:269-310`). Defaults in `BlockLightDefaults` / `BlockLightIntensityControls` | `ChunkManager.buildBlockLightInfo` (`:1097-1145`): **Sphere** (optional `LightInfoLightShaping`), **Cylinder**, **Rect**, **Disk** (`M/light/LightShape.java`) | Created, updated and destroyed through a budgeted queue (32 ops and 0.75 ms per frame; `ChunkManager.java:120-330,954-1004`). Each surviving `TerrainLight` gets `drawLightInstance` every frame, after a column box cull, the section-connectivity flood, and a power-aware reach test with hysteresis (`:1760-1781`; `TerrainLight.java:59`) |
| Entity and held lights | `LightManager.updateEntityLights` (`M/light/LightManager.java:202-277`): dropped items, hand lights (`:378`), local first-person lights (`:392-470`, volumetric contribution removed at `:415-420`), display beacons (`:605`), held beacon (`:743`) | Sphere (`:538`) or Disk with shaping (`:511`) | Rebuilt every frame (`putLight :562`), drawn in `drawActiveLights` (`:1196-1209`). Never culled |
| Placed Copper Beacon | `ensurePlacedBeaconLight` (`:283-350`) | Disk with shaping | Every frame |
| Sun and moon | `updateSkyLight` / `updateDistantLight` (`:1036-1084`) | **Distant**, 0.53° / 0.52° angular diameter (`:35-38`), dimmed by rain and thunder (`:1109-1124`) | Store update |
| Sky dome | `SkyRenderer.publishSkyDome` (`M/sky/SkyRenderer.java:366-393`) | **Dome** using the generated sky texture | Every frame |
| Weather dome | `updateWeatherDomeLight` (`:395-438`) | **Dome** with opacity and `BLEND_TYPE_ALPHA`, using the DDS overcast texture | |
| Sky ambient | `publishSkyAmbient` (`:538-570`) | **Rect** at y=325, half-size 8000, facing down | |

## 8. Sky and weather

- **Sky dome.**
  - `AtmosphericSkyGenerator` builds an equirectangular RGBA16F Rayleigh+Mie texture (`SkyRenderer.java:9-14,506-514`), patched with `updateTextureFromMemory` (`:465-520`).
  - The End and skyless dimensions such as the Nether use fixed domes (`:241-331`).
  - Vanilla `renderSky` is not cancelled; no mixin targets it. Its GL output is simply covered by the opaque overlay (section 10).
- **Sun, moon and stars.**
  - Sun and moon are textured quads (`:620-700`), drawn with `drawInstance(…, SKY, …)` (`:834-841`), alongside the distant lights.
  - Stars are copied from vanilla `buildStarsBuffer` at RETURN (`WorldRendererMixin.java:169-191`).
- **Rain and snow are drawn in GL, not path-traced.**
  - The `WorldRendererMixin.renderWeather` hooks (`:556-617`) capture the texture and defer the `BuiltBuffer`.
  - The draws are replayed after compositing, depth-tested against Remix depth, using the `rain_refraction` core shader (registered at `RemixWeatherRenderer.java:65-71`; files in `src/main/resources/assets/rtx-remix-renderer/shaders/core/rain_refraction.*`; called from `RtxRemixMod.java:691`).
  - Weather also drives the overcast dome, the sun and moon dimming, and the `weather/rain.conf` and `thunder.conf` layers.
- **Fog and volumetrics.**
  - Vanilla fog has no effect on Remix.
  - Volumetrics are runtime-side, driven by conf layers via `ConfLayerDriver` (`M/logic/ConfLayerDriver.java:175-191`): water, view_distance, volumetrics/settling, weather/*, time/{dawn,day,dusk,night}, plus biome and zone confs. These use `acquireConfigLayer` and `setConfigLayerBlending`.
  - Each light also carries a `volumetricRadianceScale`.
- **Underwater.** The water camera-medium material plus the `water.conf` layer at priority 20000.
- **Clouds.**
  - `CloudRenderer` (`M/cloud/CloudRenderer.java:40-50`) draws either procedural Perlin-Worley clouds (marching cubes, a palette of 32 meshes) or a voxelised vanilla `clouds.png`.
  - Material is opaque with subsurface (`:541-560`).
  - Drawn with `drawInstance` once per visible cloud, category `IGNORE_BAKED_LIGHTING` (`:64,611,633`).
  - Vanilla clouds are cancelled at `WorldRendererMixin.java:537-554`.

## 9. Particles

- **Hooks.** The vanilla particle draw is removed by redirecting `ParticleManager.renderParticles` (`WorldRendererMixin.java:521-535`). Accessors read particle state: `ParticleManagerAccessor`, `ParticleAccessor`, `BillboardParticleAccessor`, `SpriteBillboardParticleAccessor`. `ParticleStateMixin` adds a fast air-collision path and slot state.
- **Pipeline.**
  - `RemixParticleRenderer.prepare` runs on a worker (`RemixRenderer.java:1008-1055`) and builds world-space billboard quads per bucket (material × category × blend).
  - The result is published at the top of the next frame (`:1082`), so particles trail the scene by one frame.
  - `submit` (`RemixParticleRenderer.java:241-270`) recreates a bucket's mesh when its contents change (`:356-360,820-845`) and calls `drawInstance` with the identity transform.
- **Materials.**
  - Terrain-sheet particles use the atlas CUTOUT material with alpha test 128 and no category.
  - All others use `ParticleAtlasManager`'s material with `AlphaBlend(10)` and the `PARTICLE` category (`:100-110,144,305-310`).
  - `ParticleTint` darkens soot.
- **Custom particles.** `particle/rapids/*`.

## 10. Frame submission and present

| Step | Hook | What happens |
|---|---|---|
| GL context | `WindowMixin`, injected before `glfwCreateWindow` in `Window.<init>` (`M/mixin/WindowMixin.java:31-45`) | Forces a GL 4.4 core context, needed for the `EXT_memory_object` / `EXT_semaphore` interop |
| Remix startup | `CLIENT_STARTED` (`M/RtxRemixMod.java:480-500`) calls `initializeRemix` (`:2134-2141`), then `RemixRenderer.initialize` (`:223-437`) | Gets the HWND from `GLFWNativeWin32.glfwGetWin32Window` (`:501-503`) and checks GL extensions (`:467-499`). Calls `startup(StartupInfo.hwnd(HWND).interopImage(true))` (`:297-302`), then `dxvkGetInteropInfo` and `new GlInterop` (`:307-320`). **No D3D9 device is created.** GL and Vulkan share images and semaphores through Win32 handles |
| Frame begin | `MinecraftClientMixin` before `GameRenderer.render` (camera poses). `GameRendererMixin` `render` HEAD calls `followRemixMenuVisibility` (`:108-114`). `WorldRenderEvents.START` (`RtxRemixMod.java:573-585`). `WorldRendererMixin` `render` HEAD calls `beginEntityFrame` (`:452-475`) | |
| Vanilla world pass | Terrain layers (solid, cutout_mipped, cutout, translucent, tripwire) are cancelled in `renderLayer` (`WorldRendererMixin.java:505-519,247-253`), along with particles and clouds. Entities, block entities and cracks are captured in place | The rest of the GL frame still runs (sky, outline, debug), but it gets painted over |
| Scene ready | `WorldRenderEvents.END` sets `remixFrameReadyToSubmit` (`RtxRemixMod.java:618-623`) | |
| **Submit and composite** | Before `InGameOverlayRenderer.renderOverlays` (`GameRendererMixin.java:55-72`), or the `renderWorld` TAIL fallback (`:74-80`). Calls `submitRemixFrameIfReady` (`RtxRemixMod.java:652-697`), then `renderAndSubmitFrame` (`RemixRenderer.java:731-985`) | Order inside: previous particles, camera ×2 plus medium, lights, terrain, entities, first person, close entity frame, outline, sky, clouds, atlas flush, **`submit(null)`** (`:1320-1322`) |
| Remix image into GL | `blitVkImageToGL` (`:1373-1385`): GL waits on the Vulkan semaphore (`GlInterop.java:96-109`). `TextureOverlay.render` then draws a full-screen opaque quad, depth and blend off (`M/gl/TextureOverlay.java:20-111`). Rain and snow are drawn on top | Vanilla's GL world image is fully replaced here |
| GUI / HUD | Vanilla draws the HUD and screens in GL on top of the Remix image | There is **no** Remix UI-layer routing. The runtime's own menus are a separate overlay, controlled through `rtx.showUI`, `RemixInputGate`, `KeyboardMixin` and `MouseMixin` |
| **Present** | `RenderSystemMixin` redirects `glfwSwapBuffers` in `RenderSystem.flipFrame` (`M/mixin/RenderSystemMixin.java:78-89`) to `presentFrameVk` (`RemixRenderer.java:1331-1358`). That calls `blitGlToRemix` (`:1387-1403`; submits first if no world frame did, e.g. in menus), which copies FBO 0 into the interop colour image with `glBlitNamedFramebuffer` (`GlInterop.java:127-147`). It then signals and calls `present(hwndOverride=HWND, skipVulkanSwapchain=0, interopFlipY=1, clientDoneSemaphore_ValueToWait)` | **The GL swap never runs** while the renderer is initialized. Remix's Vulkan swapchain on the same window presents the GL composite (world + weather + HUD). Before init, or if init failed, the normal GLFW swap runs |
| RTX toggled off | `renderAndSubmitFrame` returns early (`:761-768`), but submit, blit and present still run so the semaphores stay in step | |
| Other | `MinecraftClientMixin` is otherwise profiling, plus shutdown at `close` TAIL. `UnfocusedFramerateMixin` caps FPS when the window is unfocused | |

## 11. Water and translucent surfaces

- **Water geometry** (`BlockQuadEmitter.emitFluidQuads`, `:544-640`):
  - Water has no sprite and goes into the `"water"` bucket (`DirectQuadWriter.java:271-298`), translucent category.
  - UVs are tiled in world space (`wx/tileSize`, tile size 10, `BlockTextureManager.java:681`) instead of using the vanilla sprite.
  - The top surface is emitted twice, once facing up and once facing down.
  - Water tops hidden under a ceiling are culled (`FluidTopOcclusion`). The rebuild margin is widened to match (`WorldRendererMixin.java:205-233`).
  - Lava goes into the solid bucket with its vanilla sprites.
- **Water material** (`BlockTextureManager.buildWaterMaterials`, `:736-799`):
  - `waterSurfaceMaterial`: white 1×1 albedo. Its normal map is the DDS flipbook `textures/water_normals_flipbook.dds`, loaded with **`createTextureFromFile`** (`:679,744`) and animated as a 10×12 sprite sheet at 20 fps. Translucent, with IOR 1.33 and transmittance (0.6, 0.9, 0.89) over 1 block, no diffuse layer. These values are live-editable (`:842-858`).
  - `waterCameraMediumMaterial`: translucent, IOR 1.33, transmittance 0.98 per block. Used as the camera medium when submerged.
- **Instance blend for translucent terrain.** `textureTimesVertexColor()`.
- **Refraction** comes from the runtime's translucent material type:
  - Stained glass and ice: IOR 1.52 with atlas transmittance.
  - Clear glass: the diffuse-layer variant.
  - Held translucent blocks borrow these (`RemixEntitySubmitter.java:~950-957`).
  - Entity TRANSLUCENT layers are **not** refractive. They use opaque materials with alpha blend (`:2601-2602`).

## 12. Skinning

- **Grep result.** `grep -rniE 'skinning|MeshInfoSkinning|blendWeight|blendIndices|boneTransforms|BoneTransformsEXT|bonesPerVertex' src/main/java` finds only comments disclaiming skinning (`EntityRenderContext.java:30-33`, `RemixEntitySubmitter.java:82-84`) and the CPU-side bone-matrix list (`EntityRenderContext.java:63,179-184,269-280`).
- **The shim supports skinning; the mod doesn't use it.** Exposed in the shim: `J/MeshInfoSkinning.java`, `MeshInfoSurfaceTriangles.skinning_*` (`:22-25`), `InstanceInfoBoneTransformsEXT`, and `RemixAPI.drawInstance(InstanceInfo)` (`J/RemixAPI.java:534-571`). Not used by the mod: `RemixMeshSurface.toNative` never sets skinning (`M/asset/RemixMeshSurface.java:134-170`), and `drawInstanced` and `InstanceInfo.builder` have no callers.
- **Approach: rigid parts.** Local geometry goes into pooled meshes. Each part's bone matrix is multiplied with the entity root on the CPU to give a **per-part retained instance transform** (`composeRigidTransform`, `:2743-2790`).
- **No CPU vertex baking for rigid parts.** The exceptions:
  - streamed strips and beams, which are rebuilt every frame;
  - geometry submitted through matrix-less `vertex(x,y,z)` calls, which stays under the root;
  - banner and shield pattern stacks, which are baked into one mesh at shell offsets (`submitCanvasPatterns` / `emitCanvasPatterns`).

---

## A. Mixin table

Source: `src/main/resources/rtx-remix-renderer.mixins.json`. Settings: `compatibilityLevel JAVA_21`, `defaultRequire 1`. There are **73 mixins**: 3 in the common `"mixins"` list (loaded on both sides, including a dedicated server) and 70 in `"client"`.

| # | Mixin (`M/mixin/…:line`) | Side | Target (yarn) | Injections (method → @At) | Purpose |
|---|---|---|---|---|---|
| 1 | ChunkHolderMixin:18 | **common** | `server.world.ChunkHolder` | none (adds `rtxremix$tickEntry`) | Caches one `ChunkWithHolder` per holder |
| 2 | ServerChunkManagerMixin:26 | **common** | `ServerChunkManager` | @Redirect `tickChunks` on `Lists.newArrayListWithCapacity` and on NEW `ChunkWithHolder` | Allocation-free server tick list |
| 3 | WallBlockMixin:17 | **common** | `block.WallBlock` | @Inject HEAD `shouldConnectTo` and `shouldHavePost` (cancellable) | Walls connect to the Copper Beacon mount |
| 4 | BakedQuadMixin:14 | client | `render.model.BakedQuad` | none (implements a cache interface) | Geometry digest for entity mesh pooling |
| 5 | BillboardParticleAccessor:12 | client | `particle.BillboardParticle` | @Accessor `scale`; @Invoker min/max U and V | Particle quad data |
| 6 | BiomeMixin:38 | client | `world.biome.Biome` | HEAD/RETURN `getGrassColorAt`, `getFoliageColor` | Cached biome colours |
| 7 | BlockBufferBuilderPoolMixin:18 | client | `render.chunk.BlockBufferBuilderPool` | @ModifyVariable HEAD `allocate` | Caps concurrent section builds |
| 8 | BlockEntityRenderDispatcherMixin:32 | client | `BlockEntityRenderDispatcher` | @WrapMethod `render(BlockEntity,F,MatrixStack,VCP)` | In-place block-entity capture |
| 9 | BuiltChunkBuffersMixin:39 | client | `ChunkBuilder$BuiltChunk` | @Redirect `<init>` on `Collectors.toMap`; RETURN `getBuffer` | Skips GL `VertexBuffer`s |
| 10 | BuiltChunkMixin:39 | client | `ChunkBuilder$BuiltChunk` | HEAD/RETURN `setOrigin`; RETURN `createRebuildTask`; HEAD `method_60908`; HEAD `clear` | Slot move log, block-entity flags, snapshot reset |
| 11 | BuiltChunkStorageMixin:26 | client | `render.BuiltChunkStorage` | HEAD `updateCameraPosition` (cancel) | Torus repositioning, section access |
| 12 | ChunkDataSenderMixin:80 | client (acts on the integrated server) | `server.network.ChunkDataSender` | HEAD `makeBatch`, `unload`, `add`, `sendChunkBatches`; @WrapOperation `sendPacket`, `sendChunkData` | Paced chunk delivery and unloads |
| 13 | ChunkLightProviderAccessor:17 | client | `ChunkLightProvider` | @Accessor `lightStorage` | Light snapshot republish |
| 14 | ChunkRendererRegionBuilderMixin:20 | client | `ChunkRendererRegionBuilder` | none (@Shadow `chunks`) | Releases snapshot references |
| 15 | ChunkRendererRegionMixin:25 | client | `ChunkRendererRegion` | RETURN `<init>` | Retains the 9 chunk snapshots |
| 16 | ChunkRenderingDataPreparerMixin:67 | client | `render.ChunkRenderingDataPreparer` | HEAD/RETURN on lambdas `method_52826/28/34/35/21/25`; @Redirect on `Util.getMainWorkerExecutor`, `AtomicReference.set`, `LinkedHashSet.add`, `BlockingQueue.isEmpty`, `Queues.newArrayDeque` | Visibility walk on a low-priority executor; frustum index |
| 17 | ChunkSkyLightMixin:25 | client | `ChunkSkyLight` | none (@Shadow; adds a method) | Seeded surface scan |
| 18 | ClientChunkManagerMixin:32 | client | `world.ClientChunkManager` | HEAD `onLightUpdate` (cancel); `loadChunkFromPacket` HEAD×2/RETURN; `unload` HEAD/RETURN | Rebuild bookkeeping |
| 19 | ClientChunkMixin:22 | client | `ChunkRendererRegionBuilder$ClientChunk` | @WrapOperation NEW `RenderedChunk` | Pooled snapshot |
| 20 | ClientChunkSnapshotAccess:11 | client | same as 19 | @Accessor `renderedChunk` | Snapshot access |
| 21 | ClientWorldColorMixin:20 | client | `ClientWorld` | @WrapOperation `calculateColor` on NEW `BlockPos$Mutable` | Thread-local mutable position |
| 22 | ClientWorldMixin:21 | client | `ClientWorld` | HEAD/RETURN `tick`, `tickEntities`, `doRandomBlockDisplayTicks` | Profiling |
| 23 | CuboidMixin:31 | client | `ModelPart$Cuboid` | HEAD `renderCuboid` (cancel) | Cuboid capture by identity |
| 24 | Deadmau5FeatureRendererMixin:16 | client | `Deadmau5FeatureRenderer` | @Redirect `render` on `getName` | Cached name |
| 25 | DownloadingTerrainScreenMixin:23 | client | `DownloadingTerrainScreen` | HEAD `close` (cancel) | Holds the loading screen until the join settles |
| 26 | EntityRenderDispatcherAccessor:16 | client | `EntityRenderDispatcher` | @Accessor `renderShadows` | Restores the shadow setting |
| 27 | EntityRenderDispatcherMixin:27 | client | `EntityRenderDispatcher` | HEAD/RETURN `render(Entity,…)` | Capture boundary |
| 28 | FoliageColorsMixin:19 | client | `FoliageColors` | RETURN `setColorMap` | Colour cache invalidation |
| 29 | FrustumAccessor:12 | client | `render.Frustum` | @Accessor `frustumIntersection`, x, y, z | Culling |
| 30 | GameRendererMixin:30 | client | `render.GameRenderer` | @Redirect `renderHand` on `HeldItemRenderer.renderItem`; `renderHand` before `renderOverlays`; `renderWorld` TAIL; `render` HEAD/RETURN and `Profiler.swap` | First-person capture, **submit point**, menu-visibility follow |
| 31 | GlStateManagerUploadMixin:27 | client | `GlStateManager` | HEAD `_upload` | Glyph mirror |
| 32 | GlyphAtlasTextureMixin:27 | client | `font.GlyphAtlasTexture` | @WrapOperation `getGlyphRenderer` on `upload` | Glyph upload bracket |
| 33 | GrassColorsMixin:19 | client | `GrassColors` | RETURN `setColorMap` | Colour cache invalidation |
| 34 | HeldItemRendererMixin:29 | client | `HeldItemRenderer` | HEAD/RETURN `renderItem(LivingEntity,…)`, `renderFirstPersonItem` | Holder and hand into context |
| 35 | ItemRendererMixin:33 | client | `ItemRenderer` | HEAD/RETURN `renderItem(…BakedModel)`; AFTER `renderBakedItemModel` | Block-item layer; held flame |
| 36 | KeyboardMixin:29 | client | `Keyboard` | HEAD `onKey`, `onChar` (cancel) | Overlay input gate |
| 37 | LightStorageMixin:52 | client (also integrated server) | `LightStorage` | HEAD `notifyChanges` (cancel) | Light snapshot policy |
| 38 | LivingEntityRendererNameMixin:16 | client | `LivingEntityRenderer` | @Redirect `shouldFlipUpsideDown` on `getName` | Cached name |
| 39 | MatrixStackAccessor:14 | client | `MatrixStack` | @Accessor `stack` | Recycling stack |
| 40 | MatrixStackEntryAccessor:12 | client | `MatrixStack$Entry` | @Accessor `canSkipNormalization` | Recycled entries |
| 41 | MinecraftClientMixin:20 | client | `MinecraftClient` | `render` HEAD/RETURN plus ~16 INVOKE points; `tick` HEAD/RETURN plus ~24 points; `close` TAIL | Profiling; camera pose before `GameRenderer.render` (`:170-193`); shutdown |
| 42 | ModelPartMixin:42 | client | `ModelPart` | HEAD `rotate` (cancel); @WrapOperation `renderCuboids` on `List.iterator` | Allocation-free capture |
| 43 | MouseMixin:25 | client | `Mouse` | HEAD `onCursorPos`, `getX`, `getY`, `lockCursor`, `onMouseButton`, `onMouseScroll` | Overlay input gate |
| 44 | MultipartBakedModelMixin:43 | client | `MultipartBakedModel` | HEAD `getQuads` (cancel) | Allocation-free |
| 45 | NativeImageAccessor:11 | client | `NativeImage` | @Accessor `pointer` | Pixel pointer |
| 46 | NativeImageBackedTextureMixin:30 | client | `NativeImageBackedTexture` | HEAD `upload` | Dynamic textures |
| 47 | NativeImageUploadMixin:26 | client | `NativeImage` | HEAD `uploadInternal` | Glyph mirror |
| 48 | PackedIntegerArrayMixin:22 | client | `PackedIntegerArray` | HEAD `copy` (cancel) | Pooled snapshot arrays |
| 49 | ParticleAccessor:11 | client | `particle.Particle` | @Accessor pos, prevPos, rgba, angle, dead | Particle capture |
| 50 | ParticleManagerAccessor:17 | client | `ParticleManager` | @Accessor `particles`, `spriteAwareFactories` | Particle capture |
| 51 | ParticleManagerMixin:27 | client | `ParticleManager` | HEAD/RETURN `tick`, `tickParticles` | Profiling |
| 52 | ParticleStateMixin:25 | client | `Particle` | @Redirect `move` on `adjustMovementForCollisions` | Fast collision; slot state |
| 53 | PlayerEntityNameMixin:13 | client | `PlayerEntity` | none (interface) | Cached name |
| 54 | PlayerSkinTextureMixin:29 | client | `PlayerSkinTexture` | HEAD `uploadTexture` | Skin capture |
| 55 | RebuildTaskMixin:27 | client | `ChunkBuilder$BuiltChunk$RebuildTask` | HEAD/RETURN `run` | Snapshot release |
| 56 | RenderSystemMixin:20 | client | `RenderSystem` | @Redirect `flipFrame` on `glfwSwapBuffers` | **Remix present replaces the swap** |
| 57 | RenderableChunksMixin:26 | client | `ChunkRenderingDataPreparer$RenderableChunks` | @Redirect `<init>` NEW ×3 | Reused buffers |
| 58 | RenderedChunkMixin:26 | client | `render.chunk.RenderedChunk` | none (@Shadow) | Reference-counted pooled snapshot |
| 59 | SectionBuilderMixin:52 | client | `render.chunk.SectionBuilder` | HEAD/RETURN `build`; @Redirect `isOpaqueFullCube`, `getRenderType`, `renderFluid` | **Terrain capture** |
| 60 | SpriteAtlasAccessor:15 | client | `SpriteAtlasTexture` | @Accessor `sprites`, `width`, `height` | Atlas capture |
| 61 | SpriteAtlasTextureMixin:19 | client | `SpriteAtlasTexture` | HEAD/RETURN `tickAnimatedSprites`; RETURN `upload` | Animation bracket; atlas reupload |
| 62 | SpriteBillboardParticleAccessor:12 | client | `SpriteBillboardParticle` | @Accessor `sprite` | Particle UV bounds |
| 63 | SpriteContentsAccessor:13 | client | `SpriteContents` | @Accessor `image`, `animation` | Animation |
| 64 | SpriteContentsUploadMixin:18 | client | `SpriteContents` | HEAD `upload(IIII[NativeImage])` | Animated patches to Remix |
| 65 | TextureManagerMixin:36 | client | `TextureManager` | HEAD `registerTexture` | Dynamic texture tracking |
| 66 | ThreadExecutorMixin:21 | client | `ThreadExecutor` | HEAD/RETURN `executeTask` | Profiling |
| 67 | TorchBlockAccessor:13 | client | `TorchBlock` | @Accessor `particle` | Held torch flame |
| 68 | UnfocusedFramerateMixin:29 | client | `MinecraftClient` | @ModifyReturnValue `getFramerateLimit` | FPS cap when unfocused |
| 69 | VertexBufferMixin:20 | client | `gl.VertexBuffer` | @Redirect `glBufferData` | DYNAMIC buffer upload path |
| 70 | WeightedBakedModelMixin:36 | client | `WeightedBakedModel` | HEAD `getQuads` (cancel) | Allocation-free |
| 71 | WindowMixin:25 | client | `util.Window` | `<init>` before `glfwCreateWindow`; TAIL | GL 4.4 core context |
| 72 | WorldChunkMixin:28 | client | `WorldChunk` | @WrapOperation `loadFromPacket` on `refreshSurfaceY` | Seeded sky-light scan |
| 73 | WorldRendererMixin:101 | client | `render.WorldRenderer` | `buildStarsBuffer` RETURN; `scheduleBlockRenders` ×2 HEAD (cancel); @WrapOperation `renderEntity`, `renderDamage`, `Profiler.swap`; `render` HEAD/RETURN; `renderLayer` HEAD (cancel); @Redirect `renderParticles`; `renderClouds` HEAD (cancel); `renderWeather` HEAD/RETURN with @WrapOperation `setShaderTexture` and `drawWithGlobalProgram`; sliced @Redirect of the block-entity `ObjectArrayList.iterator`; `setupTerrain` and `updateChunks` HEAD/RETURN; `updateChunks` NEW `ChunkRendererRegionBuilder` and around `rebuild` / `upload` | Hosts Remix inside vanilla's world render |

MixinExtras annotations are used (`@WrapOperation`, `@WrapMethod`, `@ModifyReturnValue`); `io.github.llamalad7` is imported in 9 files.

## B. Fabric API usage

There are 39 distinct `net.fabricmc` imports across 19 files.

| API | Files | Non-import references | What it's used for |
|---|---|---|---|
| `ModInitializer` / `ClientModInitializer` | `RtxRemixCommonMod`, `RtxRemixMod` | 1 / 1 | Entrypoints |
| `PreLaunchEntrypoint` | `WorkerPoolCap` | 1 | Caps the worker pool before launch |
| `FabricLoader`, `ModContainer` | `RemixRenderer`, `RtxRemixMod`, `NativeExtractor` | 8 | Game and config dirs; payload extraction |
| `EnvType` / `@Environment` | the 3 `Rapid*Particle` classes | 6 | Client-only particles |
| `WorldRenderEvents` (+ `WorldRenderContext`) | `RtxRemixMod`, `RemixRenderer`, `RemixBlockOutlinePass` | 11 | START, AFTER_SETUP, BEFORE/AFTER_ENTITIES, BEFORE_BLOCK_OUTLINE, BLOCK_OUTLINE, BEFORE_DEBUG_RENDER, AFTER_TRANSLUCENT, LAST, END (`RtxRemixMod.java:573-623`) |
| `CoreShaderRegistrationCallback` | `RemixWeatherRenderer` | 1 | The `rain_refraction` shader |
| `EntityModelLayerRegistry` | `RtxRemixMod` | 2 | Copper Beacon model layers |
| `ClientLifecycleEvents` | `RtxRemixMod` | 2 | CLIENT_STARTED, CLIENT_STOPPING |
| `ClientTickEvents` | `RtxRemixMod` | 2 | END_CLIENT_TICK |
| `ClientChunkEvents` | `RtxRemixMod` | 1 | CHUNK_UNLOAD |
| `ServerChunkEvents` | `RtxRemixMod` | 2 | CHUNK_LOAD (zone marker scan) |
| `ClientPlayConnectionEvents` | `RtxRemixMod` | 2 | JOIN, DISCONNECT |
| `ClientPlayNetworking` | `CopperBeaconScreen` | 2 | Sends the beacon payload |
| `PayloadTypeRegistry`, `ServerPlayNetworking` | `CopperBeaconUpdatePayload` | 1 / 1 | C2S payload and its receiver |
| `ClientCommandRegistrationCallback`, `ClientCommandManager`, `FabricClientCommandSource` | `RtxRemixMod` | 1 / 107 | The `/rtx` command tree (`:748`) |
| `KeyBindingHelper` | `RtxRemixMod` | 2 | G and F8 key bindings (`:359,370`) |
| `ScreenEvents`, `Screens` | `RtxRemixMod` | 1 / 2 | Adds the settings button to the Options screen |
| `ResourceManagerHelper`, `SimpleSynchronousResourceReloadListener` | `RtxRemixMod` | 1 | Reloads light rules and textures |
| `FluidRenderHandler` / `FluidRenderHandlerRegistry` | `BlockQuadEmitter`, `WaterRapidParticleSpawner` | 2 | Fluid sprites and colours |
| FRAPI: `FabricBakedModel`, `RenderContext`, `QuadEmitter` / `QuadView` / `MutableQuadView`, `RenderMaterial` | `BlockQuadEmitter`, `RemixRenderContext` | 3 / ~47 | Captures FRAPI block models |
| `ParticleFactoryRegistry`, `FabricParticleTypes` | `RapidFoamParticles` | 3 / 3 | Custom particles |
| `ItemGroupEvents` | `SpotLightBlock` | 1 | Adds the beacon to the item group |
| `ExtendedScreenHandlerType` / `ExtendedScreenHandlerFactory` | beacon handler and block entity | 1 / 1 | Beacon GUI |

Vanilla registries are used directly too:
- `Registry.register` for BLOCK and ITEM (`SpotLightBlock.java:115-116`), BLOCK_ENTITY_TYPE (`CopperBeaconBlockEntity.java:103`), SCREEN_HANDLER (`CopperBeaconScreenHandler.java:47`) and PARTICLE_TYPE (`RapidFoamParticles.java:23`).
- `BlockEntityRendererFactories` and `HandledScreens` at `RtxRemixMod.java:334-336`.

No `ModelLoadingPlugin` or other model-loading API is used.

## C. Access widener

File: `src/main/resources/rtx-remix-renderer.accesswidener`, v2, named mappings. 62 entries: 13 accessible classes, 36 accessible fields, 12 accessible methods, 1 mutable field.

| Class group | Entries | Purpose |
|---|---|---|
| `ModelPart$Cuboid`, `$Quad`, `$Vertex` | Cuboid `sides`; Quad (class, `direction`, `vertices`); Vertex (class, `pos`, `u`, `v`) | Rebuilding cuboid geometry |
| `SpriteTexturedVertexConsumer` | `delegate`, `sprite` | Looking through the sprite wrapper |
| `RenderLayer$MultiPhase` / `$MultiPhaseParameters`, `RenderPhase`, `RenderPhase$Texture` | `getPhases`; `depthTest`, `program`, `texture`, `transparency`; 10 `RenderPhase` constants (transparency, depth-test and program); `Texture.getId` | Surface-mode classification in `RenderLayerUtils` |
| `ChunkRenderingDataPreparer$ChunkInfo` / `$ChunkInfoList` / `$RenderableChunks` | 11 on ChunkInfo (constructor, fields, methods; `propagationLevel` also mutable); ChunkInfoList (class, constructor, `current`); RenderableChunks class | Porting the visibility walk |
| `BuiltChunkStorage` | `getRenderedChunk` | Visibility |
| `ChunkBuilder$BuiltChunk$RebuildTask`, `ChunkBuilder$Result`, `RenderedChunk` | classes | Snapshot release |
| `GameRenderer` | `getFov` | Remix camera FOV |
| `SpriteContents` / `$Animation` / `$AnimationFrame` | `animation`; `frames`; `index`, `time` | Animated-sprite LabPBR |
| `SimpleOption$SliderCallbacks` | class | Settings sliders |
| `ServerChunkManager$ChunkWithHolder` | class, constructor, `chunk` | Server tick-list mixins (common side) |
| `PalettedContainer` / `$Data`, `ChunkToNibbleArrayMap`, `BlockLightStorage$Data`, `SkyLightStorage$Data` | `data`; Data class; `arrays`; BlockLightStorage$Data class; `columnToTopSection`, `minSectionY` | Pooled snapshots and light snapshots |

## D. Java 21-only features (in `src/main/java` and `rtxremix-java/src`)

| Feature | Count | Where |
|---|---|---|
| Record patterns (`instanceof X(`, `case X(`) | 0 | – |
| Type-pattern `switch` (`case T t ->`, `when`), `case null` | 0 / 0 | All 51 switches are on enums or constants |
| `List.getFirst()` (Java 21) | 4 | `ModelPartMixin.java:94`, `FirstPersonShadowModelPlacement.java:142`, `CameraController.java:361`, `CameraPath.java:183` |
| `List.getLast()` (Java 21) | 3 | `CameraController.java:129,361`, `CameraPath.java:87`. The one at `RecyclingMatrixStack.java:32` is on a `Deque` (Java 6) |
| `List.reversed()` | 0 | The only hit is `Comparator.reversed()` at `ChunkManager.java:594` |
| `addFirst` / `addLast` / `removeFirst` / `removeLast` | 12 | All on `ArrayDeque` (Java 6) |
| `SequencedCollection` types | 0 | – |
| `Math.clamp` (Java 21) | 20, in 13 files | e.g. `SkyRenderer.java:129,133`, `CopperBeaconState.java:67-73`, `CopperBeaconScreen.java:258` |
| `StringBuilder.repeat(char,int)` (Java 21) | 1 | `rtxremix-java/src/com/nvidia/rtxremix/CommandLineRedaction.java:118` |
| `Thread.ofVirtual`, `StringTemplate`, `HexFormat`, the 3-argument `String.indexOf`, `splitWithDelimiters`, `Character.isEmoji`, unnamed `_`, `ScopedValue`, `StructuredTaskScope`, `java.lang.foreign` | 0 each | – |
| Older features, for context | – | Records: 91. `instanceof` patterns: ~80. Sealed interface: 1 (`BlockLightRuleManager.java:580`). Text blocks: 4. `Stream.toList`: 3. `var`: 33. `yield`: 7 |

**Total: 28 call sites use Java 21-only APIs** (7 `getFirst`/`getLast`, 20 `Math.clamp`, 1 `StringBuilder.repeat`). No Java 21 language features are used. All 28 are easy to back-port.

**Build targets:**
- Toolchain `languageVersion 21` (`build.gradle:130-134`).
- `options.release = 21` (`:248-251`).
- The shim is compiled in the same source set (`:138-144`).
- Mixin `compatibilityLevel JAVA_21`.
- `fabric.mod.json` requires `java >= 21`, `minecraft 1.21.1`, `fabricloader >= 0.16.5`, `fabric-api >= 0.116.10+1.21.1`.
- Loom 1.15.5; yarn `1.21.1+build.3:v2` (`build.gradle:10,209-217`).

## E. Server/common-side pieces

| Piece | Where | Needed for a client-only renderer? |
|---|---|---|
| `RtxRemixCommonMod.onInitialize` | `M/RtxRemixCommonMod.java:33-47`. Registers `SpotLightBlock` (block, item and item group; `SpotLightBlock.java:107-118`), `CopperBeaconBlockEntity`, `CopperBeaconScreenHandler`, and the C2S `CopperBeaconUpdatePayload` with its server receiver (`CopperBeaconUpdatePayload.java:39-52`) | **Optional.** It exists only for the Copper Beacon content, which uses a synced registry. Removing it means removing the beacon references in `RtxRemixMod.java:328-336`, `LightManager`, `RemixEntitySubmitter` (lens), `BlockLightDefaults`, `CopperBeaconScreen` and `WallBlockMixin`. Eight files outside `block/` import beacon classes |
| `ChunkHolderMixin`, `ServerChunkManagerMixin` | common | Optional. Allocation optimisations for the integrated server |
| `WallBlockMixin` | common | Optional. Beacon wall connections only |
| `ChunkDataSenderMixin`, `LightStorageMixin` | Listed as client, but act on the integrated server | Optional performance policies, each with an `rtxremix.*` kill switch |
| Zone scan and world conf layers | `RtxRemixMod.java:551-555`; `ConfLayerDriver` | Optional; single-player only |

## F. Size (lines per top-level package under `M/`)

| Package | Files | Lines |
|---|---|---|
| root (`RtxRemixMod` 2440, `RemixRenderer` 1695, …) | 9 | 4,918 |
| terrain (incl. capture/, visibility/) | 50 | 10,450 |
| entity | 20 | 8,174 |
| mixin | 73 | 6,551 |
| light | 14 | 3,590 |
| texture | 7 | 3,078 |
| camera | 9 | 2,641 |
| cloud | 8 | 2,556 |
| logic | 7 | 2,031 |
| sky | 3 | 1,852 |
| benchmark | 2 | 1,851 |
| block | 11 | 1,680 |
| settings | 12 | 1,588 |
| profiling | 5 | 1,495 |
| particle (incl. rapids/) | 8 | 1,485 |
| asset | 15 | 1,333 |
| util | 6 | 890 |
| gl | 3 | 527 |
| screen | 1 | 469 |
| weather | 1 | 427 |
| input | 3 | 179 |
| **Total `src/main/java`** | **267** | **57,765** |
| `rtxremix-java/src` (for reference) | – | 3,978 |

A few line references marked `~` are approximate to within ±10 lines.