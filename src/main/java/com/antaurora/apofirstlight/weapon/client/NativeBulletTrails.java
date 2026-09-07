package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeTrailGeometry;
import com.antaurora.apofirstlight.weapon.NativeTrailProfile;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.ArrayDeque;

/** One server result, one rendered muzzle, one short-lived local ribbon. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeBulletTrails {
    public static final int MAX_ACTIVE_TRAILS = 128;
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<Trail> ACTIVE = new ArrayDeque<>();
    private static ClientLevel world;
    private record Pending(int shooter, long gun, Vec3 end, NativeTrailProfile profile, double received) {}
    private record Trail(Vec3 start, Vec3 direction, double distance, NativeTrailProfile profile, double born) {}
    private NativeBulletTrails() {}
    private static double now() { return world.getGameTime() + Minecraft.getInstance().getFrameTime(); }
    private static void checkWorld() {
        var current = Minecraft.getInstance().level;
        if (current != world) { world = current; PENDING.clear(); ACTIVE.clear(); }
        if (world != null) PENDING.removeIf(p -> now() - p.received > 3);
    }
    public static void shot(int shooter, long gun, Vec3 end) {
        checkWorld();
        if (world == null || !NativeTrailGeometry.finite(end)
                || !(world.getEntity(shooter) instanceof LivingEntity entity)
                || !(entity.getMainHandItem().getItem() instanceof NativeGunItem item)
                || GeoItem.getId(entity.getMainHandItem()) != gun) return;
        var profile = item.definition().trail();
        if (profile.mode() != NativeTrailProfile.Mode.SUBTLE) return;
        if (PENDING.size() >= MAX_ACTIVE_TRAILS) PENDING.removeFirst();
        PENDING.addLast(new Pending(shooter, gun, end, profile, now()));
    }
    private static boolean matches(Pending p, long gun, boolean firstPerson) {
        var mc = Minecraft.getInstance();
        boolean localFirst = mc.player != null && p.shooter == mc.player.getId()
                && mc.options.getCameraType().isFirstPerson();
        return p.gun == gun && firstPerson == localFirst;
    }
    public static boolean needsAnchor(long gun, boolean firstPerson) {
        checkWorld();
        return PENDING.stream().anyMatch(p -> matches(p, gun, firstPerson));
    }
    public static void anchor(long gun, boolean firstPerson, Vec3 origin, double now) {
        anchor(gun, firstPerson, origin, now, 0);
    }

    /** Internal barrel travel is hidden; the visible segment starts at the exit, never through a side wall. */
    public static void anchor(long gun, boolean firstPerson, Vec3 origin, double now, double barrelDistance) {
        if (!NativeTrailGeometry.finite(origin)) return;
        var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            var p = iterator.next();
            if (!matches(p, gun, firstPerson)) continue;
            iterator.remove();
            Vec3 delta = p.end.subtract(origin);
            double distance = delta.length();
            // No eye fallback for missing anchors; also reject implausible/stale coordinates.
            if (!Double.isFinite(distance) || distance <= p.profile.hideDistance() || distance > 128) continue;
            if (ACTIVE.size() >= MAX_ACTIVE_TRAILS) ACTIVE.removeFirst();
            ACTIVE.addLast(new Trail(origin, delta.scale(1 / distance), distance, p.profile,
                    now + barrelDistance / p.profile.speed()));
        }
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        checkWorld();
        if (world == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        double now = world.getGameTime() + event.getPartialTick();
        ACTIVE.removeIf(t -> now - t.born >= Math.min(NativeTrailGeometry.MAX_TICKS,
                t.distance / t.profile.speed() + NativeTrailGeometry.HIT_FADE_TICKS));
        if (ACTIVE.isEmpty()) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var out = buffers.getBuffer(TrailType.TYPE);
        var pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        for (var t : ACTIVE) {
            var segment = NativeTrailGeometry.segment(t.distance, now - t.born, t.profile);
            if (segment == null) continue;
            Vec3 head = t.start.add(t.direction.scale(segment.head()));
            Vec3 tail = t.start.add(t.direction.scale(segment.tail()));
            Vec3 side = NativeTrailGeometry.side(t.direction, camera.subtract(head.add(tail).scale(.5)));
            ribbon(out, pose, head.subtract(camera), tail.subtract(camera), side,
                    t.profile.outerWidth(), t.profile.outerColor(), t.profile.outerAlpha() * segment.alpha());
            ribbon(out, pose, head.subtract(camera), tail.subtract(camera), side,
                    t.profile.coreWidth(), t.profile.coreColor(), t.profile.coreAlpha() * segment.alpha());
        }
        buffers.endBatch(TrailType.TYPE);
    }
    private static void ribbon(VertexConsumer out, PoseStack pose, Vec3 head, Vec3 tail, Vec3 side,
                               double width, int color, double alpha) {
        Vec3 offset = side.scale(width / 2);
        vertex(out, pose, tail.subtract(offset), color, alpha);
        vertex(out, pose, head.subtract(offset), color, alpha);
        vertex(out, pose, head.add(offset), color, alpha);
        vertex(out, pose, tail.add(offset), color, alpha);
    }
    private static void vertex(VertexConsumer out, PoseStack pose, Vec3 p, int color, double alpha) {
        out.vertex(pose.last().pose(), (float)p.x, (float)p.y, (float)p.z)
                .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (int)(255 * alpha)).endVertex();
    }
    /** Untextured full-bright color; depth-tested, additive, no depth writes. */
    private static final class TrailType extends RenderType {
        static final RenderType TYPE = create("afl_bullet_trail", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 16384, false, false,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        private TrailType() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                0, false, false, () -> {}, () -> {}); }
    }
}
