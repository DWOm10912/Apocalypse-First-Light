package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.GroundItemRenderer;
import com.antaurora.apofirstlight.client.LiquidRenderTypes;
import com.antaurora.apofirstlight.client.blockmesh.AflMovingGlass;
import com.antaurora.apofirstlight.client.blockmesh.AflStaticMeshModel;
import com.antaurora.apofirstlight.vehicle.CabStowage;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.VehiclePhysics;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.WiperSystem;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
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
import net.minecraft.client.renderer.LightTexture;
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
import org.joml.Quaternionf;

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
 * entity's angles: hood and tailgate about +X, doors about Y (outward), the steering wheel about its column (the steer
 * angle times {@link #STEERING_RATIO}).
 * <p>Driving (V0-b package 1): the body takes the entity's whole orientation (pitch and roll, PickupTruckEntity#orientation);
 * each road wheel moves up and down with its suspension travel, the front ones turn by their own steer angle (Ackermann)
 * and all turn by their roll. The chassis and axles stay with the body for now.
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
            PLAIN = p -> !isPainted(p) && lamp(p) == null, PAINT = PickupTruckRenderer::isPainted;

    /**
     * Lamp groups (design doc 8.10), by part name suffix (tools/pickup-t15: the lamp_* materials, body_dial, *_needle): each
     * its own cache selector, drawn at full light while lit, its vertex colour scaled by the glow (the tail lamp dim with
     * the lights, bright braking). The front amber lamps (parking lamps and side markers, which are also the front turn
     * signals) and the rear amber turn sections are split by side (_l / _r; design doc 8.12).
     */
    private enum Lamp {
        LOW("_lamp_low"), HIGH("_lamp_high"), PARK_L("_lamp_park_l"), PARK_R("_lamp_park_r"), TURN_L("_amber_l"), TURN_R("_amber_r"),
        TAIL("_lamp_tail"), REVERSE("_lamp_reverse"), PLATE("_lamp_plate"), DOME("_lamp_dome"), DIAL("_dial"), NEEDLE("_needle");

        static final Lamp[] ALL = values();
        final String suffix;
        final MeshGpuCache.PartSelector selector;

        Lamp(String suffix) {
            this.suffix = suffix;
            this.selector = p -> p.name().endsWith(suffix);
        }
    }

    /** Tail lamps with the lights only (the stop lamp is the same section, full when braking); the dash lights. */
    private static final float TAIL_GLOW = 0.55F, DASH_GLOW = 0.85F;
    /** A lit lens added onto itself this strongly (times its glow; no shader pack: PickupTruckRenderer#lampGlow). */
    private static final float LAMP_ADD = 1.0F;
    /** The dome lamp is on while a door is open more than this (deg). */
    private static final float DOME_DOOR = 4.0F;
    private static final float[] WHITE = {1, 1, 1};
    /** Steering wheel turns per wheel turn: 540 degrees each way (the rig's lock) for the 32 degree road wheel lock. */
    private static final float STEERING_RATIO = (float) PickupTruckLayout.STEERING_RATIO;
    /** The steering column (model frame, unit; the rig's concept axis (cos 27, -sin 27, 0) turned to the model). */
    private static final float COLUMN_Y = (float) PickupTruckLayout.COLUMN_Y, COLUMN_Z = (float) PickupTruckLayout.COLUMN_Z;

    private record Piece(ResourceLocation geometry, ResourceLocation texture, boolean worn) {
        Piece(String id, ResourceLocation texture, boolean worn) {
            this(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + id + ".geo.json"), texture, worn);
        }
    }

    /** The pose of one frame: bone turns (the steering wheel in rad, the wipers and their stalk in deg) and paint. */
    private record State(float hood, float tailgate, float doorL, float doorR, float filler, float steering, float[] main, float[] second, int light,
                         float tach, float speedo, float fuel, float temp, float leverFore, float leverAcross, float transfer,
                         float wiperL, float wiperR, float wiperStalk, float[] hvac, float[] glow,
                         CabStowage.KeySpot keySpot, float key, float stalk, float knob, float visorL, float visorR, float glovebox, float ashtray,
                         float[] keyCarry) {}
    /** The heater sliders' travel (blocks, to the right: model +X; tools/pickup-t15/cab.mjs). */
    private static final float HVAC_TRAVEL = 0.06F;
    /**
     * The wipers' axis (model frame, unit): the glass normal, inward (tools/pickup-t15/common.mjs HINGES.wiper_l; + turns a
     * parked arm up the glass).
     */
    private static final float WIPER_Y = -0.5912F, WIPER_Z = 0.8066F;

    /** Each lamp group's glow this frame (0: off). */
    private static float[] glow(PickupTruckEntity truck, float doorL, float doorR, float partialTick) {
        float[] g = new float[Lamp.ALL.length];
        boolean on = truck.headlights();
        g[Lamp.LOW.ordinal()] = on ? 1 : 0;
        g[Lamp.HIGH.ordinal()] = truck.highBeam() ? 1 : 0;
        g[Lamp.PLATE.ordinal()] = on ? 1 : 0;
        // a flashing side's amber lamps follow the flasher (the parking lamp goes dark in its off half), the other side's
        // parking lamp stays with the lights
        int signal = truck.turnSignal();
        boolean hazard = truck.hazard();
        for (boolean right : new boolean[]{false, true}) {
            boolean flashing = hazard || signal == (right ? 1 : -1), lit = truck.turnLit(right, partialTick);
            g[(right ? Lamp.PARK_R : Lamp.PARK_L).ordinal()] = flashing ? (lit ? 1 : 0) : on ? 1 : 0;
            g[(right ? Lamp.TURN_R : Lamp.TURN_L).ordinal()] = lit ? 1 : 0;
        }
        g[Lamp.TAIL.ordinal()] = truck.braking() ? 1 : on ? TAIL_GLOW : 0;
        g[Lamp.REVERSE.ordinal()] = truck.reversing() ? 1 : 0;
        g[Lamp.DOME.ordinal()] = Math.max(doorL, doorR) > DOME_DOOR ? 1 : 0;
        g[Lamp.DIAL.ordinal()] = g[Lamp.NEEDLE.ordinal()] = on ? DASH_GLOW : 0;
        return g;
    }
    /**
     * Gauge needles (design doc 8.5): each turns clockwise (as the driver sees it, about the dial axis, model -Z) from the
     * start of its sweep: tachometer 0-5000 rpm and speedometer 0-180 km/h over 270 degrees, fuel empty - full and coolant
     * 40-120 C over 120 degrees (tools/pickup-t15/cab.mjs cluster()).
     */
    private static final float BIG_SWEEP = (float) Math.toRadians(270), SMALL_SWEEP = (float) Math.toRadians(120);

    private static float needle(float value, float full, float sweep) {
        return Mth.clamp(value / full, 0.0F, 1.02F) * sweep;
    }

    /** One cargo item as drawn: the item entity standing in for it (never in the level), its place (model frame) and turn. */
    private record CargoItem(ItemEntity entity, float x, float z, boolean along) {}

    private record Cargo(int version, List<CargoItem> items) {}

    private static final java.util.Map<PickupTruckEntity, Cargo> CARGO = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** The bed floor's top (the ribs) and the cargo cell size (blocks): 9 cells along the bed from its front wall, 7 across. */
    private static final float CARGO_FLOOR = 0.772F, CELL_ALONG = 0.20F, CELL_ACROSS = 0.216F, BED_FRONT = 0.40F, BED_HALF = 0.757F;

    /**
     * Drawing into a screen (VehicleSpawnerScreen's preview), not the world: every part on the CPU, the glass as entity
     * translucent (the moving-glass queue belongs to the world's terrain pass), no lamp glow, cargo or shadow pass.
     */
    public static boolean preview;

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
        float doorL = truck.doorAngle(false, partialTick), doorR = truck.doorAngle(true, partialTick);
        WiperSystem wipers = truck.wipers(partialTick);
        State s = new State(truck.hoodAngle(partialTick), truck.tailgateAngle(partialTick), doorL,
                doorR, truck.fillerAngle(partialTick), truck.steerAngle(partialTick) * STEERING_RATIO,
                tint(truck.paint(), wear), tint(truck.secondPaint(), wear), light,
                needle(truck.tachometer(partialTick), 5000, BIG_SWEEP), needle(truck.speedometer(partialTick), 180, BIG_SWEEP),
                needle(truck.fuelGauge(partialTick), 1, SMALL_SWEEP), needle(truck.coolantGauge(partialTick) - 40, 80, SMALL_SWEEP),
                truck.leverFore(partialTick), truck.leverAcross(partialTick), truck.transferFore(partialTick),
                wipers.left(), wipers.right(), wipers.stalk(),
                new float[]{truck.hvacSlider(0, partialTick), truck.hvacSlider(1, partialTick), truck.hvacSlider(2, partialTick)},
                glow(truck, doorL, doorR, partialTick),
                truck.keySpot(), truck.keyTurn(partialTick), truck.stalkTurn(partialTick), truck.knobTurn(partialTick),
                truck.visorAngle(false, partialTick), truck.visorAngle(true, partialTick), truck.gloveboxAngle(partialTick), truck.ashtrayOut(partialTick),
                truck.keyCarry(partialTick));
        double distance = entityRenderDispatcher.distanceToSqr(truck);
        // shadow pass: the outer shell and the road wheels only (the cab, chassis, spare and wear add nothing to the
        // shadow the shell and wheels already cast)
        boolean shadow = !preview && AflShaderCompat.activeShadowPass();
        boolean interior = preview || !shadow && distance < INTERIOR_RANGE * INTERIOR_RANGE, under = preview || !shadow && distance < UNDER_RANGE * UNDER_RANGE;
        pose.pushPose();
        pose.mulPose(truck.orientation(partialTick, new Quaternionf()));
        // passes 1 and 2 from the GPU cache, sent when the batch closes (a CPU fallback goes to buffers as before)
        try (GpuGeometry.Batch batch = GpuGeometry.Batch.begin()) {
            // pass 1: cutout per atlas
            draw(EXT, pose, buffers, s, MeshLayer.CUTOUT, null);
            draw(FRONT, pose, buffers, s, MeshLayer.CUTOUT, null);
            drawWheels(truck, partialTick, pose, buffers, s, under);
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
        if (!preview && distance < INTERIOR_RANGE * INTERIOR_RANGE) drawCargo(truck, pose, buffers, light);
        if (!shadow) {
            // pass 3: glass, queued as terrain glass (after the translucent terrain, so after every entity) with or without a
            // shader pack: entity glass is flushed as soon as the next entity asks for another render type, writing its depth
            // before the passengers are drawn, and a rider behind a shut window vanished (user 2026-10-16, no shader pack)
            TextureAtlasSprite glass = preview ? null : AflStaticMeshModel.atlasSprite(BLOCK_GLASS);
            glass(EXT, pose, buffers, s, glass);
            glass(FRONT, pose, buffers, s, glass);
            if (interior) glass(CAB, pose, buffers, s, glass);
            // pass 4, without a shader pack (no bloom there): the lit lenses once more, added onto themselves
            if (!AflShaderCompat.shaderPackInUse() && !preview) {
                lampGlow(EXT, pose, buffers, s);
                lampGlow(FRONT, pose, buffers, s);
                if (interior) lampGlow(CAB, pose, buffers, s);
            }
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
        if (layer == MeshLayer.CUTOUT && MeshGpuCache.enabled() && !preview) {
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
            if (bone.neverRender() || hidden(bone.name(), s) || mesh.parts(bone.name(), MeshLayer.CUTOUT).isEmpty()) continue;
            pose.pushPose();
            pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
            hinge(pose, bone.name(), s);
            if (wear) {
                cachedOrCpu(mesh, bone, PAINT, texture, pose, buffers, s.light(), WHITE);
            } else {
                cachedOrCpu(mesh, bone, MAIN_PAINT, texture, pose, buffers, s.light(), s.main());
                cachedOrCpu(mesh, bone, SECOND_PAINT, texture, pose, buffers, s.light(), s.second());
                cachedOrCpu(mesh, bone, PLAIN, texture, pose, buffers, s.light(), WHITE);
                for (Lamp lamp : Lamp.ALL) {
                    float g = s.glow()[lamp.ordinal()];
                    cachedOrCpu(mesh, bone, lamp.selector, texture, pose, buffers, g > 0 ? LightTexture.FULL_BRIGHT : s.light(),
                            g > 0 ? new float[]{g, g, g} : WHITE);
                }
            }
            pose.popPose();
        }
    }

    /** One bone's selected cutout parts (pose at the bone's pivot): from the GPU cache, else on the CPU (flat / smooth split). */
    private static void cachedOrCpu(MeshModel mesh, MeshBone bone, MeshGpuCache.PartSelector selector, ResourceLocation texture,
                                    PoseStack pose, MultiBufferSource buffers, int light, float[] c) {
        if (MeshGpuCache.drawBone(mesh, bone.name(), MeshLayer.CUTOUT, selector, texture, pose, light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1)) return;
        List<MeshPart> parts = mesh.parts(bone.name(), MeshLayer.CUTOUT).stream().filter(selector::accepts).toList();
        if (parts.isEmpty()) return;
        MeshRenderer.Topology smooth = MeshRendering.smoothTopology();
        boolean split = smooth == MeshRenderer.Topology.TRIANGLES;
        MeshRenderer.renderParts(parts, split ? MeshRenderer.PartFilter.FLAT : MeshRenderer.PartFilter.ALL, MeshRenderer.Topology.QUADS, pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1, null);
        if (split) MeshRenderer.renderParts(parts, MeshRenderer.PartFilter.SMOOTH, smooth, pose, buffers.getBuffer(MeshRendering.cutoutType(texture, smooth)),
                light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1, null);
    }

    private static void drawParts(MeshModel mesh, PoseStack pose, VertexConsumer out, State s, MeshLayer layer, boolean wear,
                                  MeshRenderer.PartFilter filter, MeshRenderer.Topology topology) {
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender() || hidden(bone.name(), s)) continue;
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
                Lamp lamp = wear ? null : lamp(part);
                float g = lamp == null ? 0 : s.glow()[lamp.ordinal()];
                if (g > 0) c = new float[]{g, g, g};
                MeshRenderer.renderParts(List.of(part), filter, topology, pose, out, g > 0 ? LightTexture.FULL_BRIGHT : s.light(), OverlayTexture.NO_OVERLAY,
                        c == null ? 1 : c[0], c == null ? 1 : c[1], c == null ? 1 : c[2], 1, null);
            }
            pose.popPose();
        }
    }

    /**
     * The lit lamps' lenses added onto themselves (LiquidRenderTypes#lampGlow, as vanilla's glowing eyes), by their glow
     * times {@link #LAMP_ADD}: a white lens goes white, a red one bright red, so the lamp itself reads as lit without a
     * shader pack's bloom (user 2026-10-16: only the flare looked lit). Not the dash: a backlit dial does not shine.
     */
    private static void lampGlow(Piece piece, PoseStack pose, MultiBufferSource buffers, State s) {
        MeshModel mesh = VextrisMeshes.byGeometry(piece.geometry());
        if (mesh == null) return;
        VertexConsumer out = null;
        for (MeshBone bone : mesh.roots()) {
            if (bone.neverRender() || hidden(bone.name(), s)) continue;
            boolean pushed = false;
            for (MeshPart part : mesh.parts(bone.name(), MeshLayer.CUTOUT)) {
                Lamp lamp = lamp(part);
                if (lamp == null || lamp == Lamp.DIAL || lamp == Lamp.NEEDLE) continue;
                float g = s.glow()[lamp.ordinal()] * LAMP_ADD;
                if (g <= 0) continue;
                if (!pushed) {
                    pose.pushPose();
                    pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
                    hinge(pose, bone.name(), s);
                    pushed = true;
                }
                if (out == null) out = buffers.getBuffer(LiquidRenderTypes.lampGlow(piece.texture()));
                MeshRenderer.renderParts(List.of(part), MeshRenderer.PartFilter.ALL, MeshRenderer.Topology.QUADS, pose, out, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, g, g, g, 1, null);
            }
            if (pushed) pose.popPose();
        }
    }

    /** One mesh's glass: queued in AflMovingGlass at sprite, else (no sprite, or it cannot be baked) drawn as entity translucent. */
    private static void glass(Piece piece, PoseStack pose, MultiBufferSource buffers, State s, TextureAtlasSprite sprite) {
        MeshModel mesh = VextrisMeshes.byGeometry(piece.geometry());
        if (mesh == null || !mesh.hasTranslucent()) return;
        if (sprite != null) {
            boolean queued = true;
            for (MeshBone bone : mesh.roots()) {
                if (bone.neverRender() || hidden(bone.name(), s)) continue;
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

    /** Bones not drawn this frame: the key when it is not in the truck (nor in the driver's hand on its way in). */
    private static boolean hidden(String bone, State s) {
        return bone.equals("key") && s.keySpot() == CabStowage.KeySpot.ELSEWHERE && s.keyCarry() == null;
    }

    /**
     * The key (pose at its pivot, the cylinder centre): in the ignition turned to OFF / ON / START; elsewhere in the cab
     * lying flat at its spot, riding with the compartment it is in (vehicle/CabStowage); in the driver's hand on its way
     * in (G, PickupTruckEntity#keyCarry) pointing at the cylinder.
     */
    private static void keyPose(PoseStack pose, State s) {
        if (s.keyCarry() != null) {
            pose.translate(s.keyCarry()[0], s.keyCarry()[1], s.keyCarry()[2]);
            return;
        }
        CabStowage.KeySpot spot = s.keySpot();
        if (spot.at == null) {
            pose.mulPose(Axis.ZP.rotationDegrees(s.key()));
            return;
        }
        float[] k = CabStowage.KEY_PIVOT;
        pose.translate(-k[0], -k[1], -k[2]);
        if (spot.in != null) {
            float[] c = switch (spot.in) {
                case VISOR_L -> CabStowage.VISOR_L_PIVOT;
                case VISOR_R -> CabStowage.VISOR_R_PIVOT;
                case GLOVEBOX -> CabStowage.GLOVEBOX_PIVOT;
                case ASHTRAY -> CabStowage.ASHTRAY_PIVOT;
            };
            pose.translate(c[0], c[1], c[2]);
            switch (spot.in) {
                case VISOR_L -> pose.mulPose(Axis.XP.rotationDegrees(s.visorL()));
                case VISOR_R -> pose.mulPose(Axis.XP.rotationDegrees(s.visorR()));
                case GLOVEBOX -> pose.mulPose(Axis.XP.rotationDegrees(s.glovebox()));
                case ASHTRAY -> pose.translate(0, 0, s.ashtray() * CabStowage.ASHTRAY_OUT);
            }
            pose.translate(-c[0], -c[1], -c[2]);
        }
        pose.translate(spot.at[0], spot.at[1], spot.at[2]);
        pose.mulPose(Axis.YP.rotationDegrees(spot.yaw + CabStowage.KEY_FLAT_YAW));
        pose.mulPose(Axis.ZP.rotationDegrees(CabStowage.KEY_FLAT_ROLL));
    }

    /** A bone's hinge turn (hood and tailgate about +X, doors about Y, outward). */
    private static void hinge(PoseStack pose, String bone, State s) {
        switch (bone) {
            case "hood" -> pose.mulPose(Axis.XP.rotationDegrees(s.hood()));
            case "tailgate" -> pose.mulPose(Axis.XP.rotationDegrees(s.tailgate()));
            case "door_l" -> pose.mulPose(Axis.YP.rotationDegrees(-s.doorL()));
            case "door_r" -> pose.mulPose(Axis.YP.rotationDegrees(s.doorR()));
            case "fuel_door" -> pose.mulPose(Axis.YP.rotationDegrees(-s.filler()));   // left side, hinged at its front edge like door_l
            case "steering" -> pose.mulPose(new Quaternionf().rotationAxis(s.steering(), 0.0F, COLUMN_Y, COLUMN_Z));   // + clockwise: to the right
            case "tach" -> pose.mulPose(new Quaternionf().rotationAxis(s.tach(), 0.0F, 0.0F, -1.0F));
            case "speedo" -> pose.mulPose(new Quaternionf().rotationAxis(s.speedo(), 0.0F, 0.0F, -1.0F));
            case "fuel_gauge" -> pose.mulPose(new Quaternionf().rotationAxis(s.fuel(), 0.0F, 0.0F, -1.0F));
            case "temp_gauge" -> pose.mulPose(new Quaternionf().rotationAxis(s.temp(), 0.0F, 0.0F, -1.0F));
            case "shift" -> pose.mulPose(PickupTruckLayout.leverTurn(s.leverFore(), s.leverAcross()));   // the H gate (ShiftLever)
            case "transfer" -> pose.mulPose(PickupTruckLayout.transferTurn(s.transfer()));   // 2H - 4H - N - 4L (TransferLever)
            case "wiper_l" -> pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.wiperL()), 0.0F, WIPER_Y, WIPER_Z));
            case "wiper_r" -> pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.wiperR()), 0.0F, WIPER_Y, WIPER_Z));
            // the wiper stalk steps down about the column axis (+ down; WiperSystem)
            case "wiper_stalk" -> pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.wiperStalk()), 0.0F, COLUMN_Y, COLUMN_Z));
            case "hvac_mode" -> pose.translate(s.hvac()[0] * HVAC_TRAVEL, 0, 0);
            case "hvac_temp" -> pose.translate(s.hvac()[1] * HVAC_TRAVEL, 0, 0);
            case "hvac_fan" -> pose.translate(s.hvac()[2] * HVAC_TRAVEL, 0, 0);
            // the cab's key, stalk, knob and compartments (2026-10-21; vehicle/CabStowage, tools/pickup-t15 PIVOTS / HINGES)
            case "turn_stalk" -> pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.stalk()), 0.0F, COLUMN_Y, COLUMN_Z));
            case "light_knob" -> {
                // with the turn stalk about its root, then about the stalk's end
                float[] p = CabStowage.STALK_PIVOT, k = CabStowage.KNOB_PIVOT, a = CabStowage.KNOB_AXIS;
                pose.translate(p[0] - k[0], p[1] - k[1], p[2] - k[2]);
                pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.stalk()), 0.0F, COLUMN_Y, COLUMN_Z));
                pose.translate(k[0] - p[0], k[1] - p[1], k[2] - p[2]);
                pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(s.knob()), a[0], a[1], a[2]));
            }
            case "visor_l" -> pose.mulPose(Axis.XP.rotationDegrees(s.visorL()));
            case "visor_r" -> pose.mulPose(Axis.XP.rotationDegrees(s.visorR()));
            case "glovebox" -> pose.mulPose(Axis.XP.rotationDegrees(s.glovebox()));
            case "ashtray" -> pose.translate(0, 0, s.ashtray() * CabStowage.ASHTRAY_OUT);
            case "key" -> keyPose(pose, s);
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

    /**
     * The wheel mesh at the four hubs, each raised by its suspension travel, the front ones turned by their steer angle,
     * all rolled by their turn (the left ones turned half a turn about Y last); with spare, the spare (outer face down).
     */
    private static void drawWheels(PickupTruckEntity truck, float partialTick, PoseStack pose, MultiBufferSource buffers, State s, boolean spare) {
        float steer = truck.steerAngle(partialTick);
        for (int i = 0; i < HUBS.length; i++) {
            float[] hub = HUBS[i];
            pose.pushPose();
            pose.translate(hub[0], hub[1] + truck.wheelTravel(i, partialTick), hub[2]);
            float turn = (float) VehiclePhysics.wheelSteer(VehicleSpec.T15, steer, i);
            if (turn != 0) pose.mulPose(Axis.YP.rotation(-turn));
            pose.mulPose(Axis.XP.rotation(-truck.wheelSpin(i, partialTick)));
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

    /** The lamp group of a part, or null. */
    private static Lamp lamp(MeshPart part) {
        String name = part.name();
        for (Lamp lamp : Lamp.ALL) if (name.endsWith(lamp.suffix)) return lamp;
        return null;
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
