package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshCache;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Ephemeral successful-shot consumers. No entities, ammo state, prediction or server physics. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunFx {
    public static final ResourceLocation FLASH_TEXTURE = id("textures/effects/p9_01_muzzle_flash.png");
    public static final ResourceLocation CASING_MODEL = id("item/9x19mm_casing");
    public static final ResourceLocation RIFLE_CASING_MODEL = id("item/762x51mm_casing");
    public static final ResourceLocation HEAVY_RIFLE_CASING_MODEL = id("item/12_7x55mm_casing");
    public static final float FLASH_TICKS = 1.0F, FLASH_SCALE = .17F, CASING_SCALE = .072F;
    /** Static Hybrid Mesh casing: .aflmesh sidecar + GeckoLib geo, shared ammo atlas, world scale of the block-unit mesh.
     *  fx*: the ejection-only low-poly casing (same size, bone and axis); geometry/texture: the item's high-detail fallback. */
    private record MeshCasing(ResourceLocation geometry, ResourceLocation texture, ResourceLocation fxGeometry, ResourceLocation fxTexture, float scale) {}
    // Casing item model id -> Pure Mesh casing. Calibres not listed keep their baked item-model quads.
    // 9 mm: .62 keeps the old Cube casing's ejected size (8.762 px * CASING_SCALE / 1.01862 mesh units).
    private static final java.util.Map<ResourceLocation, MeshCasing> MESH_CASINGS = java.util.Map.of(
            id("item/50_ae_casing"), new MeshCasing(id("geo/50_ae_casing.geo.json"), id("textures/item/blackridge_50ae_ammo_v1.png"),
                    id("geo/50_ae_casing_fx.geo.json"), id("textures/item/50_ae_casing_fx.png"), .65F),
            CASING_MODEL, new MeshCasing(id("geo/9x19mm_casing.geo.json"), id("textures/item/9x19mm_ammo_v1.png"),
                    id("geo/9x19mm_casing_fx.geo.json"), id("textures/item/9x19mm_casing_fx.png"), .62F),
            // 7.62x51mm: .41 keeps the old cube casing's flight size (0.061 block long)
            RIFLE_CASING_MODEL, new MeshCasing(id("geo/762x51mm_casing.geo.json"), id("textures/item/762x51mm_ammo_v1.png"),
                    id("geo/762x51mm_casing_fx.geo.json"), id("textures/item/762x51mm_casing_fx.png"), .41F),
            // 12.7x55mm (HR55): .436 keeps the old cube casing's flight size (14.2 px * CASING_SCALE = 0.0639 block long)
            HEAVY_RIFLE_CASING_MODEL, new MeshCasing(id("geo/12_7x55mm_casing.geo.json"), id("textures/item/12_7x55mm_ammo_v1.png"),
                    id("geo/12_7x55mm_casing_fx.geo.json"), id("textures/item/12_7x55mm_casing_fx.png"), .436F));
    public static final int MAX_CASINGS = 64, CASING_TICKS = 50;
    public static final double GRAVITY = .04, DRAG = .98;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private record Frozen(NativeShotVisualSnapshot.Snapshot snapshot,Shot shot,NativeFlashLifetime lifetime){}
    private static long renderFrame;
    private static final List<Frozen> FROZEN=new ArrayList<>();
    public static void frozen(NativeShotVisualSnapshot.Snapshot snapshot,int shooter,long gun){
        checkWorld();if(world==null)return;
        if(snapshot.suppressed()){
            // Present the gas at the next actual accessory exit pose. A delayed server
            // confirmation can leave the frozen world-space muzzle behind the moving viewmodel.
            for (int i=SHOTS.size()-1;i>=0;i--) {
                var shot=SHOTS.get(i);
                if (shot.frozen && shot.shooter==shooter && shot.gun==gun
                        && shot.debugShotId==snapshot.shotId()) { shot.gasPending=true; break; }
            }
            return;
        }
        var shot=new Shot(shooter,gun,clock(Minecraft.getInstance().getFrameTime()));shot.flashStart=shot.received;
        if(FROZEN.size()>=128)FROZEN.remove(0);FROZEN.add(new Frozen(snapshot,shot,new NativeFlashLifetime()));
    }
    private static final ArrayDeque<Casing> CASINGS = new ArrayDeque<>();
    private static ClientLevel world;
    private static final Matrix4f WORLD_VIEW = new Matrix4f(), WORLD_PROJECTION = new Matrix4f();
    private static boolean viewValid;
    private NativeGunFx() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path); }
    private static double clock(float partial) { return world.getGameTime() + partial; }
    private static void checkWorld() {
        var current = Minecraft.getInstance().level;
        if (current != world) { world = current; SHOTS.clear(); CASINGS.clear(); FROZEN.clear(); viewValid = false; }
    }

    private static boolean currentLocalFlash(Frozen flash) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.isPaused() && mc.player.isAlive()
                && mc.options.getCameraType().isFirstPerson() && mc.player.getId() == flash.shot.shooter
                && software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem()) == flash.shot.gun;
    }

    @SubscribeEvent
    public static void flashFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        renderFrame++;
        NativeGunFxDebug.frame(renderFrame);
        checkWorld();
        long nanos = System.nanoTime();
        FROZEN.removeIf(f -> !currentLocalFlash(f) || f.lifetime.expired(nanos));
    }

    public static void shot(int shooter, long gun) {
        shot(shooter,gun,false);
    }
    public static void shot(int shooter,long gun,boolean frozen){
        shot(shooter,gun,frozen,0);
    }
    public static void shot(int shooter,long gun,boolean frozen,long debugShotId){
        checkWorld();
        if (world == null || world.getEntity(shooter) == null) return;
        if (SHOTS.size() >= 128) SHOTS.remove(0);
        var shot=new Shot(shooter, gun, clock(Minecraft.getInstance().getFrameTime()));shot.frozen=frozen;shot.debugShotId=debugShotId;SHOTS.add(shot);
    }

    public static void anchor(long gun, boolean firstPerson, String name, PoseStack anchor,
                              MultiBufferSource buffers, float partial) {
        anchor(gun, firstPerson, name, anchor, buffers, partial, CASING_MODEL);
    }

    public static void anchor(long gun, boolean firstPerson, String name, PoseStack anchor,
                              MultiBufferSource buffers, float partial, ResourceLocation casingModel) {
        anchor(gun, firstPerson, name, anchor, buffers, partial, casingModel, 0);
    }

    public static void anchor(long gun, boolean firstPerson, String name, PoseStack anchor,
                              MultiBufferSource buffers, float partial, ResourceLocation casingModel, float barrelExitOffset) {
        anchor(gun,firstPerson,name,anchor,buffers,partial,casingModel,barrelExitOffset,false);
    }
    public static void anchor(long gun, boolean firstPerson, String name, PoseStack anchor,
                              MultiBufferSource buffers, float partial, ResourceLocation casingModel, float barrelExitOffset,boolean suppressed) {
        checkWorld();
        if (world == null || !viewValid) return;
        var mc = Minecraft.getInstance();
        double now = clock(partial);
        // Only the first frame uses the frozen world muzzle. The timed remainder follows
        // the actual final bone/attachment pose, including recoil, ADS and sway.
        if (firstPerson && name.equals("muzzle_anchor") && !suppressed) {
            long nanos = System.nanoTime();
            for (var flash : FROZEN) {
                if (flash.shot.gun != gun || !currentLocalFlash(flash)) continue;
                float age = flash.lifetime.attachedAge(nanos, renderFrame);
                if (age < 0) continue;
                var exit = NativeRenderMatrices.detachedCopy(anchor);
                exit.translate(0, 0, -barrelExitOffset / 16);
                drawFlash(exit, buffers, age, flash.shot);
                if(NativeGunFxDebug.ENABLED)NativeGunFxDebug.log("FLASH_ATTACHED_SUBMIT",flash.snapshot.shotId(),"age="+age+" gun="+gun+" buffer="+buffers.getClass().getName());
                if (Boolean.getBoolean("afl.shotSnapshotDebug"))
                    ApocalypseFirstLight.LOGGER.info("[SHOT FLASH] attached id={} age={} frame={}", flash.snapshot.shotId(), age, renderFrame);
            }
        }
        if(firstPerson&&name.equals("muzzle_anchor")){
            var matrix=new Matrix4f(WORLD_VIEW).invert().mul(new Matrix4f(WORLD_PROJECTION).invert()).mul(FirstPersonProjectionSanitizer.sanitize(RenderSystem.getProjectionMatrix(),WORLD_PROJECTION)).mul(anchor.last().pose());
            var exit=matrix.transformProject(new Vector3f(0,0,-barrelExitOffset/16));
            var ahead=matrix.transformProject(new Vector3f(0,0,-barrelExitOffset/16-.01f));
            if(NativeGunFxDebug.ENABLED)NativeGunFxDebug.sample(gun,anchor.last().pose(),RenderSystem.getProjectionMatrix(),WORLD_VIEW,WORLD_PROJECTION,
                    mc.gameRenderer.getMainCamera().getPosition(),mc.gameRenderer.getMainCamera().getPosition().add(exit.x,exit.y,exit.z));
            var rawDirection=new Vec3(ahead.x-exit.x,ahead.y-exit.y,ahead.z-exit.z);
            var normalized=rawDirection.normalize();
            boolean accepted=NativeShotVisualSnapshot.presented(gun,mc.gameRenderer.getMainCamera().getPosition().add(exit.x,exit.y,exit.z),
                    normalized,suppressed);
            if(NativeGunFxDebug.ENABLED)NativeGunFxDebug.direction(rawDirection.length(),normalized,accepted);
        }
        if (name.equals("muzzle_anchor") && NativeBulletTrails.needsAnchor(gun, firstPerson)) {
            // Same projection conversion as the established casing birth position.
            var matrix = new Matrix4f(WORLD_VIEW).invert();
            if (firstPerson)
                matrix.mul(new Matrix4f(WORLD_PROJECTION).invert()).mul(FirstPersonProjectionSanitizer.sanitize(RenderSystem.getProjectionMatrix(),WORLD_PROJECTION));
            matrix.mul(anchor.last().pose());
            var p = matrix.transformProject(new Vector3f());
            var exit = matrix.transformProject(new Vector3f(0, 0, -barrelExitOffset / 16));
            NativeBulletTrails.anchor(gun, firstPerson,
                    mc.gameRenderer.getMainCamera().getPosition().add(exit.x, exit.y, exit.z), now,
                    p.distance(exit));
        }
        for (Shot shot : SHOTS) {
            if (shot.gun != gun || now - shot.received > 3) continue;
            boolean localFirst = mc.player != null && shot.shooter == mc.player.getId()
                    && mc.options.getCameraType().isFirstPerson();
            if (firstPerson != localFirst) continue;
            if (name.equals("ejection_anchor") && !shot.ejected) {
                // Convert the rendered locator to world space, never substitute an eye/hand offset.
                var matrix = firstPerson ? firstPersonToWorld(anchor)
                        : new Matrix4f(WORLD_VIEW).invert().mul(anchor.last().pose());
                var p = matrix.transformProject(new Vector3f());
                var camera = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 origin = camera.add(p.x, p.y, p.z);
                if(NativeGunFxDebug.ENABLED)NativeGunFxDebug.log("CASING_BIRTH",shot.debugShotId,"gun="+gun+" firstPerson="+firstPerson+" origin="+origin+" camera="+camera+" cameraDistance="+origin.distanceTo(camera));
                // Live visual check: local -X ejects left; reverse only the lateral basis.
                Vec3 right = direction(matrix, 1, 0, 0), up = direction(matrix, 0, 1, 0);
                Vec3 forward = direction(matrix, 0, 0, -1);
                Vec3 velocity = right.scale(.24 + RANDOM.nextDouble() * .06)
                        .add(up.scale(.12 + RANDOM.nextDouble() * .04))
                        .add(forward.scale((RANDOM.nextDouble() - .5) * .08));
                if (CASINGS.size() >= MAX_CASINGS) CASINGS.removeFirst();
                CASINGS.addLast(new Casing(origin, velocity, casingModel));
                shot.ejected = true;
            }
            if (name.equals("muzzle_anchor")) {
                if(shot.frozen){
                    if(firstPerson && shot.gasPending){
                        shot.gasPending=false;
                        suppressorGasAtAnchor(anchor,true,barrelExitOffset,mc.gameRenderer.getMainCamera().getPosition());
                    }
                    continue;
                }
                if(suppressed){
                    if(Double.isNaN(shot.flashStart)){
                        shot.flashStart=now;
                        suppressorGasAtAnchor(anchor,firstPerson,barrelExitOffset,mc.gameRenderer.getMainCamera().getPosition());
                    }
                    continue;
                }
                if (Double.isNaN(shot.flashStart)) shot.flashStart = now;
                float age = (float)(now - shot.flashStart);
                if (age >= 0 && age < FLASH_TICKS) {
                    var exit = NativeRenderMatrices.detachedCopy(anchor);
                    exit.translate(0, 0, -barrelExitOffset / 16);
                    drawFlash(exit, buffers, age, shot);
                }
            }
        }
    }

    private static Vec3 direction(Matrix4f matrix, float x, float y, float z) {
        Vector3f v = matrix.transformDirection(new Vector3f(x, y, z)).normalize();
        return new Vec3(v.x, v.y, v.z);
    }

    private static void suppressorGasAtAnchor(PoseStack anchor,boolean firstPerson,float barrelExitOffset,Vec3 camera) {
        var matrix=firstPerson?firstPersonToWorld(anchor):new Matrix4f(WORLD_VIEW).invert().mul(anchor.last().pose());
        if(matrix==null)return;
        // The locator is at the front cap. Start just beyond its face so the
        // small billboard is visibly born at the opening rather than inside it.
        float z=-barrelExitOffset/16-.008F;
        var p=matrix.transformProject(new Vector3f(0,0,z));
        var ahead=matrix.transformProject(new Vector3f(0,0,z-.01F));
        suppressorGas(camera.add(p.x,p.y,p.z),new Vec3(ahead.x-p.x,ahead.y-p.y,ahead.z-p.z));
    }

    private static void suppressorGas(Vec3 origin, Vec3 forward) {
        if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
                || !Double.isFinite(forward.x) || !Double.isFinite(forward.y) || !Double.isFinite(forward.z)
                || forward.lengthSqr() < 1e-12) return;
        Vec3 velocity=forward.normalize().scale(.012).add(0,.002,0);
        var smoke=Minecraft.getInstance().particleEngine.createParticle(
                net.minecraft.core.particles.ParticleTypes.SMOKE,origin.x,origin.y,origin.z,
                velocity.x,velocity.y,velocity.z);
        if(smoke!=null){
            smoke.setPos(origin.x,origin.y,origin.z);
            smoke.setParticleSpeed(velocity.x,velocity.y,velocity.z);
            smoke.scale(.35F);
            smoke.setLifetime(6);
        }
    }

    /** Reuse casing birth conversion for a final animated FP locator; returns camera-relative world space. */
    static Matrix4f firstPersonToWorld(PoseStack anchor) {
        checkWorld();
        if (world == null || !viewValid) return null;
        return new Matrix4f(WORLD_VIEW).invert()
                .mul(new Matrix4f(WORLD_PROJECTION).invert())
                .mul(FirstPersonProjectionSanitizer.sanitize(RenderSystem.getProjectionMatrix(), WORLD_PROJECTION))
                .mul(anchor.last().pose());
    }

    /** Orientation only: no projection/FOV, translation or inherited axis lengths in a velocity. */
    static Vec3 firstPersonDirection(PoseStack anchor, float x, float y, float z) {
        checkWorld();
        if (world == null || !viewValid) return Vec3.ZERO;
        var orientation = new Matrix4f(WORLD_VIEW).invert().mul(anchor.last().pose()).normalize3x3();
        var direction = orientation.transformDirection(new Vector3f(x, y, z)).normalize();
        if (!Float.isFinite(direction.x) || !Float.isFinite(direction.y) || !Float.isFinite(direction.z))
            return Vec3.ZERO;
        return new Vec3(direction.x, direction.y, direction.z);
    }

    private static void drawFlash(PoseStack source, MultiBufferSource buffers, float age, Shot shot) {
        var pose = NativeRenderMatrices.detachedCopy(source);
        // Remove inherited model scale for a single preset size in both perspectives.
        Vector3f scale = pose.last().pose().getScale(new Vector3f());
        pose.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(shot.roll));
        float size = FLASH_SCALE * shot.scale;
        pose.scale(size, size, size);
        float alpha = (age < .6F ? 1 - age * (.25F / .6F) : .75F * (1 - age) / .4F) * shot.alpha;
        VertexConsumer out = buffers.getBuffer(FlashType.TYPE);
        // Three orthogonal double-sided quads: muzzle-facing plus two intersecting axial planes.
        quad(out, pose, alpha, new float[][]{{-.5F,-.5F,0},{.5F,-.5F,0},{.5F,.5F,0},{-.5F,.5F,0}});
        quad(out, pose, alpha, new float[][]{{-.5F,0,-.5F},{.5F,0,-.5F},{.5F,0,.5F},{-.5F,0,.5F}});
        quad(out, pose, alpha, new float[][]{{0,-.5F,-.5F},{0,.5F,-.5F},{0,.5F,.5F},{0,-.5F,.5F}});
    }

    private static void quad(VertexConsumer out, PoseStack pose, float alpha, float[][] vertices) {
        for (int i = 0; i < 4; i++) {
            float[] p = vertices[i];
            out.vertex(pose.last().pose(), p[0], p[1], p[2]).color(1F,1F,1F,alpha)
                    .uv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 1 : 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.last().normal(),0,0,1).endVertex();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkWorld();
        if (world == null || Minecraft.getInstance().isPaused()) return;
        SHOTS.removeIf(s -> world.getGameTime() - s.received > 4);
        CASINGS.removeIf(c -> ++c.age > CASING_TICKS);
        for (Casing casing : CASINGS) casing.tick();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        checkWorld();
        if (world == null) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            WORLD_VIEW.set(event.getPoseStack().last().pose());
            WORLD_PROJECTION.set(event.getProjectionMatrix());
            viewValid = true;
        }
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            double time=clock(event.getPartialTick());
            long nanos = System.nanoTime();
            FROZEN.removeIf(f->time-f.shot.received>3 || !currentLocalFlash(f) || f.lifetime.expired(nanos));
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            boolean drewSnapshot = false;
            for(var f:FROZEN){
                if (!f.lifetime.presentSnapshot(nanos, renderFrame)) continue;
                drewSnapshot = true;
                var pose=event.getPoseStack();var p=f.snapshot.muzzle().subtract(event.getCamera().getPosition());var d=f.snapshot.barrelDirection();
                pose.pushPose();pose.translate(p.x,p.y,p.z);
                pose.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0,0,-1),new Vector3f((float)d.x,(float)d.y,(float)d.z)));
                drawFlash(pose,buffers,0,f.shot);pose.popPose();
                if(NativeGunFxDebug.ENABLED)NativeGunFxDebug.log("FLASH_WORLD_SUBMIT",f.snapshot.shotId(),"origin="+f.snapshot.muzzle()+" camera="+event.getCamera().getPosition()+" stage="+event.getStage()+" buffer="+buffers.getClass().getName());
                if(Boolean.getBoolean("afl.shotSnapshotDebug"))com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SHOT FLASH] presented id={} gun={} origin={}",f.snapshot.shotId(),f.shot.gun,f.snapshot.muzzle());
            }
            if(drewSnapshot)buffers.endBatch(FlashType.TYPE);
            // Retain the shot's 50 ms clock, not a detached world-space flash core.
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CASINGS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        var out = buffers.getBuffer(type);
        var meshSnapshot = AflMeshCache.snapshot();
        var meshTypes = new java.util.LinkedHashSet<RenderType>();
        Vec3 camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        for (Casing c : CASINGS) {
            Vec3 position = c.previous.lerp(c.position, event.getPartialTick());
            Vec3 rotation = c.oldRotation.lerp(c.rotation, event.getPartialTick());
            pose.pushPose();
            pose.translate(position.x-camera.x, position.y-camera.y, position.z-camera.z);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)rotation.x));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)rotation.y));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)rotation.z));
            int light = LevelRenderer.getLightColor(world, BlockPos.containing(position));
            MeshCasing meshCasing = MESH_CASINGS.get(c.model);
            if (meshCasing != null) {
                // Ejection draws the FX-only low-poly casing; the item's high-detail mesh is only a fallback if it is missing.
                boolean fx = meshSnapshot.get(meshCasing.fxGeometry()) != null && GeckoLibCache.getBakedModels().get(meshCasing.fxGeometry()) != null;
                var geometry = fx ? meshCasing.fxGeometry() : meshCasing.geometry();
                var casingMesh = meshSnapshot.get(geometry);
                var casingGeo = GeckoLibCache.getBakedModels().get(geometry);
                if (casingMesh != null && casingGeo != null) {
                    pose.scale(meshCasing.scale(), meshCasing.scale(), meshCasing.scale());
                    // The source casing starts at Y=0. Center all of its parts before the existing tumbling rotations.
                    var parts = casingMesh.parts("casing");
                    if (!parts.isEmpty()) {
                        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                        for (var part : parts) {
                            var b = part.bounds();
                            minX = Math.min(minX, b.minX()); minY = Math.min(minY, b.minY()); minZ = Math.min(minZ, b.minZ());
                            maxX = Math.max(maxX, b.maxX()); maxY = Math.max(maxY, b.maxY()); maxZ = Math.max(maxZ, b.maxZ());
                        }
                        pose.translate(-(minX + maxX) / 2, -(minY + maxY) / 2, -(minZ + maxZ) / 2);
                    }
                    var meshType = RenderType.entityCutoutNoCull(fx ? meshCasing.fxTexture() : meshCasing.texture());
                    meshTypes.add(meshType);
                    var vertices = buffers.getBuffer(meshType);
                    for (GeoBone bone : casingGeo.topLevelBones()) renderCasingMesh(casingMesh, bone, pose, vertices, light);
                }
            } else {
                var model = mc.getModelManager().getModel(c.model);
                pose.scale(CASING_SCALE,CASING_SCALE,CASING_SCALE);
                pose.translate(-.5,-.5,-.5);
                var random = RandomSource.create(0);
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                    random.setSeed(0);
                    for (var q : model.getQuads(null, side, random)) out.putBulkData(pose.last(),q,1,1,1,light,OverlayTexture.NO_OVERLAY);
                }
                random.setSeed(0);
                for (var q : model.getQuads(null, null, random)) out.putBulkData(pose.last(),q,1,1,1,light,OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        buffers.endBatch(type);
        for (var meshType : meshTypes) buffers.endBatch(meshType);
    }

    private static void renderCasingMesh(AflMeshModel mesh, GeoBone bone, PoseStack pose,
                                         VertexConsumer vertices, int light) {
        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, bone);
            AflMeshRenderer.render(mesh, bone, pose, vertices, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            if (!bone.isHidingChildren())
                for (GeoBone child : bone.getChildBones()) renderCasingMesh(mesh, child, pose, vertices, light);
        } finally { pose.popPose(); }
    }

    private static final class Shot {
        long debugShotId;
        boolean frozen;
        final int shooter; final long gun; final double received;
        final float roll = (RANDOM.nextFloat() - .5F) * 24, scale = .9F + RANDOM.nextFloat() * .2F;
        final float alpha = .95F + RANDOM.nextFloat() * .05F;
        double flashStart = Double.NaN; boolean ejected, gasPending;
        Shot(int shooter, long gun, double received) { this.shooter = shooter; this.gun = gun; this.received = received; }
    }

    private static final class Casing {
        final ResourceLocation model;
        Vec3 position, previous, velocity, rotation, oldRotation, spin;
        int age, bounces; boolean sounded, resting;
        Casing(Vec3 position, Vec3 velocity, ResourceLocation model) {
            this.model = model;
            this.position = this.previous = position; this.velocity = velocity;
            rotation = oldRotation = new Vec3(RANDOM.nextDouble()*360,RANDOM.nextDouble()*360,RANDOM.nextDouble()*360);
            spin = new Vec3(angular(),angular(),angular());
        }
        static double angular() { return (RANDOM.nextBoolean() ? 1 : -1) * (15 + RANDOM.nextDouble()*20); }
        void tick() {
            previous = position; oldRotation = rotation;
            if (resting) return;
            var hit = world.clip(new ClipContext(position,position.add(velocity),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                position = hit.getLocation().add(n.scale(.07));
                double impact = Math.abs(velocity.dot(n));
                if (!sounded && impact > .04) {
                    world.playLocalSound(position.x,position.y,position.z,AflSounds.CASING_LANDING.get(),
                            SoundSource.PLAYERS,.18F,.95F+RANDOM.nextFloat()*.1F,false);
                    sounded = true;
                }
                velocity = velocity.subtract(n.scale(velocity.dot(n)*1.32)).scale(.65);
                spin = spin.scale(.5);
                if (hit.getDirection() == net.minecraft.core.Direction.UP && (++bounces >= 2 || impact < .06)) {
                    resting = true; velocity = Vec3.ZERO;
                }
            } else position = position.add(velocity);
            if (!resting) velocity = velocity.add(0,-GRAVITY,0).scale(DRAG);
            rotation = rotation.add(spin);
        }
    }

    /** RenderType owns state setup/teardown; no leaked blend/depth state in Gecko/HUD. */
    private static final class FlashType extends RenderType {
        static final RenderType TYPE = create("afl_muzzle_flash",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(FLASH_TEXTURE,false,false))
                        // SRC_ALPHA + ONE: additive with the per-frame fade respected.
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
        private FlashType() { super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,0,false,false,()->{},()->{}); }
    }
}
