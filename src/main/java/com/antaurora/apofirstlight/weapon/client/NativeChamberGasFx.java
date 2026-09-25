package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ChamberGasParticle;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.antaurora.apofirstlight.weapon.NativeBreakActionChambers;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.ArrayList;
import java.util.List;

/** Local presentation only: an animation cue selects spent chambers, final bone poses emit vapor. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeChamberGasFx {
    public static final String CUE = "chamber_eject_fx";
    private static final boolean AXIS_DEBUG = !net.minecraftforge.fml.loading.FMLEnvironment.production
            && Boolean.getBoolean("afl.chamberGasAxisDebug");
    private static final double JET_STAGGER = .025, CLOUD_STAGGER = .012;
    // Small V1.2 profile. Speeds are blocks/tick, sizes are billboard half-widths in blocks.
    private record Layer(int count, double speed, double spread, double drag, int minLife,
                         int maxLife, float size, float alpha, double buoyancy, float growth) {}
    private static final Layer JET_CORE = new Layer(3, .21, .035, .65, 4, 7, .034F, .55F, .002, 2.8F);
    private static final Layer EXPANSION_CLOUD = new Layer(3, .14, .085, .52, 6, 10, .045F, .22F, .0025, 1.45F);
    private static final Layer RESIDUAL = new Layer(1, .009, .003, .92, 10, 18, .0245F, .138F, .0015, 2.2F);
    private static final int RESIDUAL_TICKS = 20;
    private static final int RESIDUAL_INITIAL_INTERVAL = 3, RESIDUAL_INTERVAL_GROWTH = 4;
    private static final RandomSource RANDOM = RandomSource.create();
    private static Session session;

    private record Session(ClientLevel world, long gun, String clip, double start, List<Emitter> emitters) {}
    private static final class Emitter {
        final String bone;
        boolean burst;
        double next;
        Emitter(String bone) { this.bone = bone; }
    }

    /** Installed before Gecko samples the action, including its earlier camera-bone preparation pass. */
    static <T extends GeoItem> void bind(T item, long id, AnimationState<T> state) {
        if (!(item instanceof NativeGunItem gun) || !"silverwood_12".equals(gun.animationAsset())) return;
        var action = item.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().get("action");
        if (action == null) return;
        var perspective = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        action.setCustomInstructionKeyframeHandler(event -> {
            if (!CUE.equals(event.getKeyframeData().getInstructions()) || perspective == null
                    || !perspective.firstPerson() || !local(id) || action.getCurrentAnimation() == null) return;
            String clip = action.getCurrentAnimation().animation().name();
            if (!clip.startsWith("reload_") || action.getTriggeredAnimation() == null) return;
            // Never replay an old cue after seeking/skipping most of a clip (e.g. hidden hand rendering).
            if (event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2) return;
            var mc = Minecraft.getInstance();
            if (session != null && session.world == mc.level && session.gun == id && session.clip.equals(clip)) return;
            // The server syncs the held stack before triggering reload. Read BEFORE its later ammo commit.
            var chambers = NativeBreakActionChambers.read(mc.player.getMainHandItem(), gun.definition());
            var emitters = new ArrayList<Emitter>(2);
            if (chambers.upper() == NativeBreakActionChambers.State.SPENT) emitters.add(new Emitter("upper_chamber_fx"));
            if (chambers.lower() == NativeBreakActionChambers.State.SPENT) emitters.add(new Emitter("lower_chamber_fx"));
            session = new Session(mc.level, id, clip, mc.level.getGameTime() + mc.getFrameTime(), emitters);
        });
    }

    private static boolean local(long id) {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.player.isAlive() && !mc.isPaused()
                && mc.screen == null && mc.options.getCameraType().isFirstPerson()
                && GeoItem.getId(mc.player.getMainHandItem()) == id;
    }

    /** Called on the final animated, pivot-translated locator, never on an ejecting shell bone. */
    static void anchor(long id, String clip, String name, PoseStack pose, MultiBufferSource buffers,
                       float partial, int light) {
        if (AXIS_DEBUG && local(id)) debugAxis(name, pose, buffers);
        if (session == null || session.gun != id) return;
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (!local(id) || session.world != mc.level || !session.clip.equals(clip)) { session = null; return; }
        double age = mc.level.getGameTime() + partial - session.start;
        if (age < 0 || age >= RESIDUAL_TICKS) return;
        for (var emitter : session.emitters) {
            if (!emitter.bone.equals(name) || age < emitter.next) continue;
            var matrix = NativeGunFx.firstPersonToWorld(pose);
            if (matrix == null) return;
            var p = matrix.transformProject(new Vector3f());
            Vec3 axis = NativeGunFx.firstPersonDirection(pose, 0, 0, 1);
            Vec3 lateral = NativeGunFx.firstPersonDirection(pose, 1, 0, 0);
            Vec3 origin = mc.gameRenderer.getMainCamera().getPosition().add(p.x, p.y, p.z);
            if (!Double.isFinite(origin.lengthSqr()) || axis.lengthSqr() < .5) return;
            if (!emitter.burst) {
                // A missed render never queues a delayed burst at a later hand pose.
                if (age > 2) { emitter.burst = true; emitter.next = RESIDUAL_TICKS; continue; }
                spawn(JET_CORE, origin, axis, lateral, light, 1);
                spawn(EXPANSION_CLOUD, origin, axis, lateral, light, 1);
                emitter.burst = true;
                emitter.next = age + RESIDUAL_INITIAL_INTERVAL;
            } else {
                spawn(RESIDUAL, origin, axis, lateral, light, (float)(1 - age / RESIDUAL_TICKS));
                // Starts every 2 ticks, then 3, 4 and 5; no catch-up bursts on low FPS.
                emitter.next = age + RESIDUAL_INITIAL_INTERVAL
                        + (int)(age / RESIDUAL_TICKS * RESIDUAL_INTERVAL_GROWTH);
            }
        }
    }

    private static void spawn(Layer layer, Vec3 origin, Vec3 axis, Vec3 lateral, int light, float strength) {
        var mc = Minecraft.getInstance();
        for (int i = 0; i < layer.count; i++) {
            double jitter = RANDOM.nextDouble() * 2 - 1;
            boolean residual = layer == RESIDUAL;
            boolean jet = layer == JET_CORE;
            // Both perpendicular directions come from the chamber orientation, never screen/world up.
            Vec3 across = axis.cross(lateral).normalize();
            Vec3 spread = lateral.scale(jitter).add(across.scale(RANDOM.nextDouble() * 2 - 1));
            if (spread.lengthSqr() > 1) spread = spread.normalize();
            Vec3 velocity = layer == RESIDUAL
                    ? axis.scale(layer.speed * .25).add(0, layer.speed, 0).add(lateral.scale(jitter * layer.spread))
                    : axis.scale(.95).add(spread.scale(layer.spread))
                        .normalize().scale(layer.speed * (.95 + RANDOM.nextDouble() * .10));
            // The first jet particle is exactly on the centerline; the rest form a short axial core.
            Vec3 birth = residual
                    ? origin.add(lateral.scale(jitter * .005)).add(axis.scale(RANDOM.nextDouble() * .01))
                    : origin.add(axis.scale(i * (jet ? JET_STAGGER : CLOUD_STAGGER)));
            var particle = mc.particleEngine.createParticle(AflParticles.CHAMBER_GAS.get(),
                    birth.x, birth.y, birth.z, velocity.x, velocity.y, velocity.z);
            if (particle instanceof ChamberGasParticle gas)
                gas.configure(layer.minLife + RANDOM.nextInt(layer.maxLife - layer.minLife + 1),
                        layer.size * (.85F + RANDOM.nextFloat() * .3F),
                        layer.alpha * strength * (residual ? .85F + RANDOM.nextFloat() * .3F : .95F + RANDOM.nextFloat() * .1F),
                        layer.drag, layer.buoyancy, layer.growth, light, residual ? ChamberGasParticle.Stage.RESIDUAL
                                : jet ? ChamberGasParticle.Stage.JET_CORE : ChamberGasParticle.Stage.EXPANSION_CLOUD);
        }
    }

    /** Draw the exact world-space emission ray through the hand pass, without one-frame stale poses. */
    private static void debugAxis(String name, PoseStack pose, MultiBufferSource buffers) {
        var matrix = NativeGunFx.firstPersonToWorld(pose);
        if (matrix == null) return;
        Vec3 axis = NativeGunFx.firstPersonDirection(pose, 0, 0, 1);
        if (axis.lengthSqr() < .5) return;
        var origin = matrix.transformProject(new Vector3f());
        var end = origin.add((float)axis.x * .18F, (float)axis.y * .18F, (float)axis.z * .18F, new Vector3f());
        // Map the world ray back only for debug drawing; particle direction never uses this inverse.
        var localEnd = matrix.invert().transformProject(end);
        boolean upper = name.equals("upper_chamber_fx");
        var out = buffers.getBuffer(RenderType.lines());
        var normal = new Vector3f(localEnd).normalize();
        float r = upper ? .1F : 1F, g = upper ? 1F : .7F, b = upper ? 1F : .1F;
        out.vertex(pose.last().pose(), 0, 0, 0).color(r, g, b, 1)
                .normal(pose.last().normal(), normal.x, normal.y, normal.z).endVertex();
        out.vertex(pose.last().pose(), localEnd.x, localEnd.y, localEnd.z).color(r, g, b, 1)
                .normal(pose.last().normal(), normal.x, normal.y, normal.z).endVertex();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || session == null) return;
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (session.world != mc.level || !local(session.gun)
                || mc.level.getGameTime() - session.start > 80) session = null;
    }

    private NativeChamberGasFx() {}
}
