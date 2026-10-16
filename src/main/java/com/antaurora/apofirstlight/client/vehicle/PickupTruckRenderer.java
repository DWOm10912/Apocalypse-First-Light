package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GroundItemRenderer;
import com.antaurora.apofirstlight.client.blockmesh.AflMovingGlass;
import com.antaurora.apofirstlight.client.blockmesh.AflStaticMeshModel;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.GpuGeometry;
import com.antaurora.vextris.client.MeshGpuCache;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.MeshRendering;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshBone;
import com.antaurora.vextris.mesh.MeshLayer;
import com.antaurora.vextris.mesh.MeshModel;
import com.antaurora.vextris.mesh.MeshPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The T15 pickup body V1 (PickupTruckEntity) from its AFL meshes (tools/build-pickup-t15.mjs; rig data in
 * src/main/blockbench/vehicles/pickup_t15.rig.json). Seven Pure Meshes, each with its geo for the bone pivots:
 * <ul><li>pickup_t15 (cab shell, doors, bed, tailgate) and pickup_t15_front (front clip, hood): always;</li>
 * <li>pickup_t15_wheel: one wheel at its hub, drawn at the four corners (the left ones turned half a turn about Y) and
 * as the spare under the bed (turned so its outer face points down) within {@link #UNDER_RANGE} blocks;</li>
 * <li>pickup_t15_cab + pickup_t15_seat (interior): within {@link #INTERIOR_RANGE} blocks;</li>
 * <li>pickup_t15_chassis, and pickup_t15_engine while the hood is open: within {@link #UNDER_RANGE} blocks.</li></ul>
 * In a shader pack's shadow pass only pickup_t15, pickup_t15_front and the four road wheels are drawn (cutout, no wear):
 * everything else sits inside or under that shell and adds nothing to its shadow (2026-10-16: one truck was about
 * 0.5 million vertices a frame with shaders, every one submitted by the CPU through Oculus' extended format).
 * Atlases: pickup_t15 (outside + wheel), pickup_t15_cab (interior), pickup_t15_under (engine + chassis). Bones turn by the
 * entity's angles: hood and tailgate about +X, doors about Y (outward), the steering wheel stays at rest.
 * <p>Passes, in this order (a plain buffer source flushes one render type before the next, so the order holds):
 * <ol><li>cutout, each mesh's atlas: painted parts (*_paint / *_paint2) multiplied by the truck's main / second colour,
 * faded by wear; everything else untinted;</li>
 * <li>cutout, the wear overlay for round(wear x 10) (pickup_t15_wear_1..10, laid out like the outside atlas) over the
 * outside's painted parts: a texel is opaque exactly where that wear tenth has worn the paint through (the vanilla
 * cutout tests texture alpha only). Same vertices and pose as pass 1, so the depths are equal and the later draw wins;</li>
 * <li>translucent: the glass (skipped in a shader pack's shadow pass). It is queued in AflMovingGlass and drawn after the
 * translucent terrain as terrain glass with the block texture pickup_t15_glass (the baked glass texel). With a shader pack:
 * Sundial Lite has no translucent entity program, so entity glass overwrote the material and light of what is behind it
 * and the view out looked dull. Without one (2026-10-16): entity glass was flushed, depth and all, as soon as the next
 * entity (the rider) asked for another render type, so a rider behind a shut window was not drawn. Entity translucent is
 * only the fallback when the sprite cannot be baked.</li></ol>
 * Shading normals (Vextris 0.1.1 format 3, crease_angle in the sidecars): without a shader pack every part goes into the
 * one QUADS buffer of its pass (smooth parts keep their corner normals there); with one, Oculus would overwrite QUADS
 * normals per face, so each pass draws its flat parts as QUADS and then its smooth parts into Vextris' TRIANGLES twin of the
 * same render type ({@link MeshRendering#smoothTopology()}). Both use the same split diagonal, so the wear pass still
 * lands on exactly the paint pass's depths.
 * <p>GPU cache (Vextris 0.1.1-alpha.4, 2026-10-16): passes 1 and 2 go through {@link MeshGpuCache#drawBone}, baked once
 * per (mesh, root bone, selector) and drawn with the bone's matrix, inside one {@link GpuGeometry.Batch} per truck (main
 * and shadow pass alike). The colour, light and texture are per draw, so the wear pass reuses the paint's entry and all
 * trucks share one copy; Vextris keeps the flat / smooth topology split and rebakes after a shader or resource change.
 * Reason: one truck was about 0.4 million vertices a frame submitted by the CPU (JFR on the user's client: 87% of the
 * render thread, about 15 FPS with the truck in view). The glass is submitted after the batch closes (entity glass
 * flushed first would write its depth before the cached cab is drawn). With -Dvextris.gpuCache=false every pass falls
 * back to the CPU path below.
 * The model's front is -Z: a world yaw psi is a model turn of 180 - psi about +Y.
 */
public final class PickupTruckRenderer extends EntityRenderer<PickupTruckEntity> {
    /** The cab interior (pickup_t15_cab + pickup_t15_seat) is drawn within this distance (blocks). */
    private static final double INTERIOR_RANGE = 20.0;
    /** The chassis, the spare and the engine bay are drawn within this distance (blocks). */
    private static final double UNDER_RANGE = 16.0;
    /** With a shader pack: the glass as terrain glass (AflMovingGlass), this block-atlas texture (tools/build-pickup-t15.mjs). */
    private static final ResourceLocation BLOCK_GLASS = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/pickup_t15_glass.png");
    private static final ResourceLocation OUTSIDE = texture("pickup_t15"), INTERIOR = texture("pickup_t15_cab"), UNDER = texture("pickup_t15_under");
    private static final ResourceLocation[] WEAR = new ResourceLocation[11];
    static { for (int i = 1; i <= 10; i++) WEAR[i] = texture("pickup_t15_wear_" + i); }
    private static final Piece EXT = new Piece("pickup_t15", OUTSIDE, true), FRONT = new Piece("pickup_t15_front", OUTSIDE, true),
            WHEEL = new Piece("pickup_t15_wheel", OUTSIDE, false), CAB = new Piece("pickup_t15_cab", INTERIOR, false),
            SEAT = new Piece("pickup_t15_seat", INTERIOR, false), ENGINE = new Piece("pickup_t15_engine", UNDER, false),
            CHASSIS = new Piece("pickup_t15_chassis", UNDER, false);
    /** Wheel hubs (blocks, model frame) and whether the wheel is turned (the left side); the spare's centre. */
    private static final float[][] HUBS = {{-0.712F, 0.36F, -1.60F, 1}, {0.712F, 0.36F, -1.60F, 0}, {-0.712F, 0.36F, 1.25F, 1}, {0.712F, 0.36F, 1.25F, 0}};
    private static final float[] SPARE = {0.0F, 0.50F, 1.86F};
    /** GPU cache selectors (constants: each is part of a cache key): main paint, second paint, untinted, and the wear pass. */
    private static final MeshGpuCache.PartSelector MAIN_PAINT = p -> isPainted(p) && !isSecond(p), SECOND_PAINT = PickupTruckRenderer::isSecond,
            PLAIN = p -> !isPainted(p), PAINT = PickupTruckRenderer::isPainted;
    private static final float[] WHITE = {1, 1, 1};

    private record Piece(ResourceLocation geometry, ResourceLocation texture, boolean worn) {
        Piece(String id, ResourceLocation texture, boolean worn) {
            this(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + id + ".geo.json"), texture, worn);
        }
    }

    /** The pose of one frame: bone turns and paint. */
    private record State(float hood, float tailgate, float doorL, float doorR, float filler, float[] main, float[] second, int light) {}

    /** One cargo item as drawn: the item entity standing in for it (never in the level), its place (model frame) and turn. */
    private record CargoItem(ItemEntity entity, float x, float z, boolean along) {}

    private record Cargo(int version, List<CargoItem> items) {}

    private static final java.util.Map<PickupTruckEntity, Cargo> CARGO = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** The bed floor's top (the ribs) and the cargo cell size (blocks): 9 cells along the bed from its front wall, 7 across. */
    private static final float CARGO_FLOOR = 0.772F, CELL_ALONG = 0.20F, CELL_ACROSS = 0.216F, BED_FRONT = 0.40F, BED_HALF = 0.757F;

    public PickupTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.6F;
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/vehicle/" + name + ".png");
    }

    @Override
    public void render(PickupTruckEntity truck, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float wear = truck.wear();
        State s = new State(truck.hoodAngle(partialTick), truck.tailgateAngle(partialTick), truck.doorAngle(false, partialTick),
                truck.doorAngle(true, partialTick), truck.fillerAngle(partialTick), tint(truck.paint(), wear), tint(truck.secondPaint(), wear), light);
        double distance = entityRenderDispatcher.distanceToSqr(truck);
        // shadow pass: the outer shell and the road wheels only (the cab, chassis, spare and wear add nothing to the
        // shadow the shell and wheels already cast)
        boolean shadow = AflShaderCompat.activeShadowPass();
        boolean interior = !shadow && distance < INTERIOR_RANGE * INTERIOR_RANGE, under = !shadow && distance < UNDER_RANGE * UNDER_RANGE;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        // passes 1 and 2 from the GPU cache, sent when the batch closes (a CPU fallback goes to buffers as before)
        try (GpuGeometry.Batch batch = GpuGeometry.Batch.begin()) {
            // pass 1: cutout per atlas
            draw(EXT, pose, buffers, s, MeshLayer.CUTOUT, null);
            draw(FRONT, pose, buffers, s, MeshLayer.CUTOUT, null);
            drawWheels(pose, buffers, s, under);
            if (interior) {
                draw(CAB, pose, buffers, s, MeshLayer.CUTOUT, null);
                draw(SEAT, pose, buffers, s, MeshLayer.CUTOUT, null);
            }
            if (under) {
                draw(CHASSIS, pose, buffers, s, MeshLayer.CUTOUT, null);
                if (s.hood() > 0.5F) draw(ENGINE, pose, buffers, s, MeshLayer.CUTOUT, null);
            }
            // pass 2: the wear overlay over the outside's paint
            int level = shadow ? 0 : Mth.clamp(Math.round(wear * 10.0F), 0, 10);
            if (level > 0) {
                draw(EXT, pose, buffers, s, MeshLayer.CUTOUT, WEAR[level]);
                draw(FRONT, pose, buffers, s, MeshLayer.CUTOUT, WEAR[level]);
            }
        }
        if (distance < INTERIOR_RANGE * INTERIOR_RANGE) drawCargo(truck, pose, buffers, light);
        if (!shadow) {
            // pass 3: glass, queued as terrain glass (after the translucent terrain, so after every entity) with or without a
            // shader pack: entity glass is flushed as soon as the next entity asks for another render type, writing its depth
            // before the passengers are drawn, and a rider behind a shut window vanished (user 2026-10-16, no shader pack)
            TextureAtlasSprite glass = AflStaticMeshModel.atlasSprite(BLOCK_GLASS);
            glass(EXT, pose, buffers, s, glass);
            glass(FRONT, pose, buffers, s, glass);
            if (interior) glass(CAB, pose, buffers, s, glass);
        }
        pose.popPose();
        super.render(truck, entityYaw, partialTick, pose, buffers, light);
    }

    /**
     * One mesh, every root bone at its pivot. overlay != null: the wear pass (painted parts only, that texture, no tint).
     * Flat parts (or every part without a shader pack) first, then, with a shader pack, the smooth parts as TRIANGLES.
     */
    private static void draw(Piece piece, PoseStack pose, MultiBufferSource buffers, State s, MeshLayer layer, ResourceLocation overlay) {
        MeshModel mesh = VextrisMeshes.byGeometry(piece.geometry());
        if (mesh == null || layer == MeshLayer.TRANSLUCENT && !mesh.hasTranslucent()) return;
        ResourceLocation texture = overlay != null ? overlay : piece.texture();
        if (layer == MeshLayer.CUTOUT && MeshGpuCache.enabled()) {
            cached(mesh, pose, buffers, s, texture, overlay != null);
            return;
        }
        MeshRenderer.Topology smooth = MeshRendering.smoothTopology();
        boolean split = smooth == MeshRenderer.Topology.TRIANGLES && mesh.hasSmoothParts();
        VertexConsumer out = buffers.getBuffer(layer == MeshLayer.TRANSLUCENT ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture));
        drawParts(mesh, pose, out, s, layer, overlay != null, split ? MeshRenderer.PartFilter.FLAT : MeshRenderer.PartFilter.ALL, MeshRenderer.Topology.QUADS);
        if (!split) return;
        // a new buffer request ends the QUADS batch of a plain buffer source, so the smooth parts come strictly after it
        out = buffers.getBuffer(layer == MeshLayer.TRANSLUCENT ? MeshRendering.translucentType(texture, smooth) : MeshRendering.cutoutType(texture, smooth));
        drawParts(mesh, pose, out, s, layer, overlay != null, MeshRenderer.PartFilter.SMOOTH, smooth);
    }

    /**
     * One mesh's cutout from the Vextris GPU cache: per root bone (pivot, hinge) the main paint, the second paint and the
     * untinted parts, or for the wear pass the painted parts in the wear texture (same baked entry as no other selector:
     * the vertices are the paint's, so the depths match). A selector the cache does not take is drawn on the CPU.
     */
    private static void cached(MeshModel mesh, PoseStack pose, MultiBufferSource buffers, State s, ResourceLocation texture, boolean wear) {
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender() || mesh.parts(bone.name(), MeshLayer.CUTOUT).isEmpty()) continue;
            pose.pushPose();
            pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
            hinge(pose, bone.name(), s);
            if (wear) {
                cachedOrCpu(mesh, bone, PAINT, texture, pose, buffers, s, WHITE);
            } else {
                cachedOrCpu(mesh, bone, MAIN_PAINT, texture, pose, buffers, s, s.main());
                cachedOrCpu(mesh, bone, SECOND_PAINT, texture, pose, buffers, s, s.second());
                cachedOrCpu(mesh, bone, PLAIN, texture, pose, buffers, s, WHITE);
            }
            pose.popPose();
        }
    }

    /** One bone's selected cutout parts (pose at the bone's pivot): from the GPU cache, else on the CPU (flat / smooth split). */
    private static void cachedOrCpu(MeshModel mesh, MeshBone bone, MeshGpuCache.PartSelector selector, ResourceLocation texture,
                                    PoseStack pose, MultiBufferSource buffers, State s, float[] c) {
        if (MeshGpuCache.drawBone(mesh, bone.name(), MeshLayer.CUTOUT, selector, texture, pose, s.light(), OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1)) return;
        List<MeshPart> parts = mesh.parts(bone.name(), MeshLayer.CUTOUT).stream().filter(selector::accepts).toList();
        if (parts.isEmpty()) return;
        MeshRenderer.Topology smooth = MeshRendering.smoothTopology();
        boolean split = smooth == MeshRenderer.Topology.TRIANGLES;
        MeshRenderer.renderParts(parts, split ? MeshRenderer.PartFilter.FLAT : MeshRenderer.PartFilter.ALL, MeshRenderer.Topology.QUADS, pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), s.light(), OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1, null);
        if (split) MeshRenderer.renderParts(parts, MeshRenderer.PartFilter.SMOOTH, smooth, pose, buffers.getBuffer(MeshRendering.cutoutType(texture, smooth)),
                s.light(), OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1, null);
    }

    private static void drawParts(MeshModel mesh, PoseStack pose, VertexConsumer out, State s, MeshLayer layer, boolean wear,
                                  MeshRenderer.PartFilter filter, MeshRenderer.Topology topology) {
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender()) continue;
            List<MeshPart> parts = mesh.parts(bone.name(), layer);
            if (parts.isEmpty()) continue;
            pose.pushPose();
            pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
            hinge(pose, bone.name(), s);
            for (MeshPart part : parts) {
                boolean painted = isPainted(part);
                if (wear && !painted) continue;
                if (filter == MeshRenderer.PartFilter.FLAT && part.smooth() || filter == MeshRenderer.PartFilter.SMOOTH && !part.smooth()) continue;
                float[] c = wear || !painted ? null : isSecond(part) ? s.second() : s.main();
                MeshRenderer.renderParts(List.of(part), filter, topology, pose, out, s.light(), OverlayTexture.NO_OVERLAY,
                        c == null ? 1 : c[0], c == null ? 1 : c[1], c == null ? 1 : c[2], 1, null);
            }
            pose.popPose();
        }
    }

    /** One mesh's glass: queued in AflMovingGlass at sprite, else (no sprite, or it cannot be baked) drawn as entity translucent. */
    private static void glass(Piece piece, PoseStack pose, MultiBufferSource buffers, State s, TextureAtlasSprite sprite) {
        MeshModel mesh = VextrisMeshes.byGeometry(piece.geometry());
        if (mesh == null || !mesh.hasTranslucent()) return;
        if (sprite != null) {
            boolean queued = true;
            for (MeshBone bone : mesh.roots()) {
                if (bone.neverRender()) continue;
                List<MeshPart> parts = mesh.parts(bone.name(), MeshLayer.TRANSLUCENT);
                if (parts.isEmpty()) continue;
                pose.pushPose();
                pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
                hinge(pose, bone.name(), s);
                queued &= AflMovingGlass.queue(parts, pose.last().pose(), sprite, s.light());
                pose.popPose();
            }
            if (queued) return;
        }
        draw(piece, pose, buffers, s, MeshLayer.TRANSLUCENT, null);
    }

    /** A bone's hinge turn (hood and tailgate about +X, doors about Y, outward). */
    private static void hinge(PoseStack pose, String bone, State s) {
        switch (bone) {
            case "hood" -> pose.mulPose(Axis.XP.rotationDegrees(s.hood()));
            case "tailgate" -> pose.mulPose(Axis.XP.rotationDegrees(s.tailgate()));
            case "door_l" -> pose.mulPose(Axis.YP.rotationDegrees(-s.doorL()));
            case "door_r" -> pose.mulPose(Axis.YP.rotationDegrees(s.doorR()));
            case "fuel_door" -> pose.mulPose(Axis.YP.rotationDegrees(-s.filler()));   // left side, hinged at its front edge like door_l
            default -> { }
        }
    }

    /**
     * The bed's cargo (the synced grid, PickupTruckEntity#cargo): each item lies flat on the bed floor over its cells, as a
     * ground item does (GroundItemRenderer: meshes on their measured bottom, guns at their real length), its long side
     * along the longer side of its cells. Grid x runs from the bed's front wall to the tailgate, y from the right side to
     * the left.
     */
    private void drawCargo(PickupTruckEntity truck, PoseStack pose, MultiBufferSource buffers, int light) {
        var grid = truck.cargo();
        if (grid.entries().isEmpty()) return;
        Cargo cargo = CARGO.get(truck);
        if (cargo == null || cargo.version() != truck.cargoVersion()) {
            List<CargoItem> items = new java.util.ArrayList<>();
            for (var e : grid.entries()) {
                var size = grid.footprint(e);
                float along = BED_FRONT + (e.x() + size.w() / 2.0F) * CELL_ALONG, across = BED_HALF - (e.y() + size.h() / 2.0F) * CELL_ACROSS;
                Vec3 at = truck.point(-along, across, CARGO_FLOOR);
                ItemEntity stand = new ItemEntity(truck.level(), at.x, at.y, at.z, e.stack().copy());
                items.add(new CargoItem(stand, across, along, size.w() >= size.h()));
            }
            CARGO.put(truck, cargo = new Cargo(truck.cargoVersion(), items));
        }
        for (CargoItem item : cargo.items()) {
            if (!(entityRenderDispatcher.getRenderer(item.entity()) instanceof GroundItemRenderer ground)) continue;
            pose.pushPose();
            pose.translate(item.x(), CARGO_FLOOR, item.z());
            if (item.along()) pose.mulPose(Axis.YP.rotationDegrees(90.0F));   // the item's long side (local +x) along the bed
            ground.renderResting(item.entity(), pose, buffers, light);
            pose.popPose();
        }
    }

    /** The wheel mesh at the four hubs (the left ones turned about Y) and, with spare, as the spare (outer face down). */
    private static void drawWheels(PoseStack pose, MultiBufferSource buffers, State s, boolean spare) {
        for (float[] hub : HUBS) {
            pose.pushPose();
            pose.translate(hub[0], hub[1], hub[2]);
            if (hub[3] != 0) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            draw(WHEEL, pose, buffers, s, MeshLayer.CUTOUT, null);
            pose.popPose();
        }
        if (!spare) return;
        pose.pushPose();
        pose.translate(SPARE[0], SPARE[1], SPARE[2]);
        pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        draw(WHEEL, pose, buffers, s, MeshLayer.CUTOUT, null);
        pose.popPose();
    }

    private static boolean isPainted(MeshPart part) {
        return part.name().endsWith("_paint") || part.name().endsWith("_paint2");
    }

    private static boolean isSecond(MeshPart part) {
        return part.name().endsWith("_paint2");
    }

    /** The paint colour as vertex colour, faded by wear: less saturated and a little chalkier (design doc section 2). */
    static float[] tint(int rgb, float wear) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b, fade = 0.35F * wear, lift = 1.0F + 0.08F * wear;
        return new float[]{Mth.clamp(Mth.lerp(fade, r, lum) * lift, 0, 1), Mth.clamp(Mth.lerp(fade, g, lum) * lift, 0, 1),
                Mth.clamp(Mth.lerp(fade, b, lum) * lift, 0, 1)};
    }

    @Override
    public ResourceLocation getTextureLocation(PickupTruckEntity truck) {
        return OUTSIDE;
    }
}
