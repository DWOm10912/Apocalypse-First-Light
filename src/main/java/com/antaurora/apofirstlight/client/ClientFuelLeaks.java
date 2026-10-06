package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bullet holes in fuel containers on this client (2026-10-05, docs/gameplay/fuel_fire_v1.md "第二阶段"), synced from
 * fluid/FuelLeaks. A leaking hole shoots a thin stream (its own fluid/LiquidJet, two parcels a tick at the synced speed,
 * straight out of the hole, a little turbulence), drawn like the nozzle's (LiquidJetRenderer) with droplets where it
 * lands; the stains it leaves are the server's. A hole of a burning container carries a flame and smokes.
 * Drawn by ClientFuelStains in its pass.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFuelLeaks {
    private static final double RANGE = 64.0, TURBULENCE = 0.03, RADIUS = 0.008;
    private static final int ALPHA = 165, SIDES = 6;

    private static final class Leak {
        final long id;
        final Vec3 at;
        final Direction face;
        final boolean diesel;
        final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, 3.0);
        boolean flowing, burning, emitting;
        float speed;

        Leak(long id, Vec3 at, Direction face, boolean diesel) {
            this.id = id;
            this.at = at;
            this.face = face;
            this.diesel = diesel;
        }

        Fluid fluid() {
            return (diesel ? AflFluids.DIESEL : AflFluids.GASOLINE).get();
        }
    }

    private static final Map<Long, Leak> LEAKS = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private ClientFuelLeaks() {
    }

    public static void apply(List<AflNetwork.FuelLeakS2CPacket.Leak> upserts, long[] removed, boolean reset) {
        if (reset) {
            LEAKS.clear();
            trackedLevel = Minecraft.getInstance().level;
        }
        for (long id : removed) LEAKS.remove(id);
        for (AflNetwork.FuelLeakS2CPacket.Leak l : upserts) {
            Leak leak = LEAKS.computeIfAbsent(l.id(), id -> new Leak(id, l.at(), l.face(), l.diesel()));
            leak.flowing = l.flowing();
            leak.burning = l.burning();
            leak.speed = l.speed();
        }
    }

    public static boolean isEmpty() {
        return LEAKS.isEmpty();
    }

    /** Every hole (where, which face): BulletHoles draws them as long as they last. */
    static void forEachHole(java.util.function.BiConsumer<Vec3, Direction> out) {
        for (Leak leak : LEAKS.values()) out.accept(leak.at, leak.face);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            LEAKS.clear();
            trackedLevel = level;
        }
        if (level == null || minecraft.player == null || minecraft.isPaused() || LEAKS.isEmpty()) return;
        RandomSource random = level.random;
        Vec3 viewer = minecraft.player.position();
        for (Leak leak : LEAKS.values()) {
            boolean near = leak.at.distanceToSqr(viewer) < RANGE * RANGE;
            Vec3 n = Vec3.atLowerCornerOf(leak.face.getNormal());
            if (leak.flowing && near) {
                for (int k = 0; k < 2; k++) {
                    double t = TURBULENCE * leak.speed;
                    Vec3 velocity = n.scale(leak.speed).add(random.triangle(0, t), random.triangle(0, t), random.triangle(0, t));
                    leak.jet.emit(leak.at.add(n.scale(0.02)), velocity, k == 0 ? LiquidJet.STEP : 0.0);
                }
                leak.emitting = true;
            } else if (leak.emitting) {
                leak.jet.gap();
                leak.emitting = false;
            }
            if (!leak.jet.isEmpty()) {
                Fluid fluid = leak.fluid();
                leak.jet.tick(level, null, (parcel, hit) -> {
                    if (random.nextFloat() < 0.3F) LiquidDroplet.spawnOf(level, hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.03)),
                            new Vec3(random.triangle(0, 0.4), 0.3 + random.nextDouble() * 0.3, random.triangle(0, 0.4)), fluid, 0.012F);
                });
            }
            if (leak.burning && near && random.nextFloat() < 0.15F) {
                Vec3 p = leak.at.add(n.scale(0.15)).add(0, 0.4, 0);
                level.addParticle(leak.diesel ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, leak.diesel ? 0.03 : 0.05, 0);
            }
        }
    }

    /** The streams (entity-translucent on the block atlas; the caller ends that batch). */
    static void renderStreams(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, float partialTick) {
        for (Leak leak : LEAKS.values()) {
            if (leak.jet.isEmpty() || leak.at.distanceToSqr(camera) > RANGE * RANGE) continue;
            Fluid fluid = leak.fluid();
            FluidStack stack = new FluidStack(fluid, 1000);
            IClientFluidTypeExtensions client = IClientFluidTypeExtensions.of(fluid);
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(client.getStillTexture(stack));
            int rgb = client.getTintColor(stack) & 0xFFFFFF;
            BlockPos origin = BlockPos.containing(leak.at);
            pose.pushPose();
            pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
            LiquidJetRenderer.render(pose, buffers, level, origin, leak.jet, leak.flowing ? leak.at : null, partialTick, sprite, rgb, ALPHA, RADIUS,
                    Math.max(1.0, leak.speed), SIDES);
            pose.popPose();
        }
    }

    /** The flames at burning holes (into the flame batch FuelFlames uses). */
    static void renderFlames(VertexConsumer out, PoseStack pose, Vec3 camera, double now) {
        for (Leak leak : LEAKS.values()) {
            if (!leak.burning || leak.at.distanceToSqr(camera) > RANGE * RANGE) continue;
            Vec3 base = leak.at.add(Vec3.atLowerCornerOf(leak.face.getNormal()).scale(0.12)).add(0, -0.08, 0);
            double width = leak.diesel ? 0.3 : 0.36, height = leak.diesel ? 0.42 : 0.6;
            FuelFlames.flame(out, pose.last().pose(), pose.last().normal(), camera, base, width, height, now * 1.2 + (leak.id * 17 % 48), leak.diesel);
        }
    }
}
