package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bullet holes on this client (2026-10-05, docs/native_guns/native_bullet_holes_v1.md). Where a bullet stops on a block
 * (AflNetwork.BulletImpactS2CPacket) a hole decal goes on the face it struck and a few bits of the block fly off. The
 * decal is one of three holes of the block's material (steel, stone, wood: by its sound type) from the hole sheet
 * (tools/build-bullet-holes-v1.mjs), turned at random (wood only end for end: the grain stays along the face's u axis),
 * drawn colour-only just off the face (LiquidRenderTypes.HOLE). Holes are this client's only: at most {@link #MAX}, the
 * oldest going first; each lasts {@link #LIFE} ticks, fading over the last {@link #FADE}; one whose block changed goes
 * at once. Holes in fuel containers come from the synced leaks (ClientFuelLeaks) and last as long as the leak does.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BulletHoles {
    private static final int MAX = 512, LIFE = 6000, FADE = 600;
    private static final double RANGE = 48.0;
    private static final int STEEL = 0, STONE = 1, WOOD = 2;
    /** Decal size (blocks) by material: the hole and its marks fill a little over half of it. */
    private static final float[] SIZE = {0.16F, 0.18F, 0.18F};

    private record Hole(Vec3 at, Direction face, BlockPos block, BlockState state, int material, int variant, float angle, long born) {}

    private static final Deque<Hole> HOLES = new ArrayDeque<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private BulletHoles() {
    }

    public static boolean isEmpty() {
        return HOLES.isEmpty() && ClientFuelLeaks.isEmpty();
    }

    /** A bullet struck {@code block} at {@code at} on {@code face}. */
    public static void impact(Vec3 at, Direction face, BlockPos block, boolean holed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockState state = level.getBlockState(block);
        if (state.isAir()) return;
        RandomSource random = level.random;
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        for (int i = 0; i < 5; i++) {   // bits of the block knocked out of the hole
            Vec3 v = n.scale(0.08 + random.nextDouble() * 0.12).add(random.triangle(0, 0.08), random.nextDouble() * 0.06, random.triangle(0, 0.08));
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x + n.x * 0.05, at.y + n.y * 0.05, at.z + n.z * 0.05, v.x, v.y, v.z);
        }
        if (holed) return;   // a fuel container's hole: drawn from its leak
        int material = material(state);
        float angle = material == WOOD ? (random.nextBoolean() ? 0.0F : Mth.PI) : random.nextFloat() * Mth.TWO_PI;
        HOLES.addLast(new Hole(at, face, block.immutable(), state, material, random.nextInt(3), angle, level.getGameTime()));
        while (HOLES.size() > MAX) HOLES.removeFirst();
    }

    private static int material(BlockState state) {
        SoundType sound = state.getSoundType();
        if (sound == SoundType.METAL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.ANVIL || sound == SoundType.COPPER
                || sound == SoundType.CHAIN || sound == SoundType.LANTERN) return STEEL;
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.LADDER) return WOOD;
        return STONE;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != trackedLevel) {
            HOLES.clear();
            trackedLevel = level;
        }
        if (level == null || HOLES.isEmpty() || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        HOLES.removeIf(h -> now - h.born > LIFE || level.isLoaded(h.block) && level.getBlockState(h.block) != h.state);
    }

    static void render(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, double now) {
        if (isEmpty()) return;
        VertexConsumer out = buffers.getBuffer(LiquidRenderTypes.HOLE);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (Hole h : HOLES) {
            if (h.at.distanceToSqr(camera) > RANGE * RANGE) continue;
            double age = now - h.born;
            int alpha = age < LIFE - FADE ? 255 : (int) Math.max(0, 255 * (LIFE - age) / FADE);
            if (alpha > 0) quad(out, matrix, normals, level, camera, h.at, h.face, h.block, h.material, h.variant, h.angle, alpha);
        }
        ClientFuelLeaks.forEachHole((at, face) -> {   // fuel container holes: steel or the container's own material
            if (at.distanceToSqr(camera) > RANGE * RANGE) return;
            BlockPos block = BlockPos.containing(at.subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01)));
            long seed = Double.doubleToLongBits(at.x * 31 + at.y * 17 + at.z);
            int material = material(level.getBlockState(block));
            float angle = material == WOOD ? 0.0F : (seed & 1023) / 1023.0F * Mth.TWO_PI;
            quad(out, matrix, normals, level, camera, at, face, block, material, (int) Math.floorMod(seed >> 10, 3), angle, 255);
        });
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, Matrix3f normals, ClientLevel level, Vec3 camera, Vec3 at, Direction face,
                             BlockPos block, int material, int variant, float angle, int alpha) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal()), u = FuelStainIndex.axisU(face), v = FuelStainIndex.axisV(face);
        double half = SIZE[material] / 2;
        // kept on its block's face (a hole near an edge would hang over it), lifted off it a little more far away
        Vec3 local = at.subtract(Vec3.atLowerCornerOf(block));
        Vec3 su = u.scale(Math.signum(u.x + u.y + u.z)), sv = v.scale(Math.signum(v.x + v.y + v.z));   // the face's axes, positive
        double lu = local.dot(su), lv = local.dot(sv), margin = half * 0.6;
        Vec3 centre = at.add(su.scale(Mth.clamp(lu, margin, 1 - margin) - lu)).add(sv.scale(Mth.clamp(lv, margin, 1 - margin) - lv));
        centre = centre.add(n.scale(0.002 + 0.0001 * Math.sqrt(centre.distanceToSqr(camera))));
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3 a = u.scale(cos * half).add(v.scale(sin * half)), b = u.scale(-sin * half).add(v.scale(cos * half));
        float u0 = variant / 3.0F, u1 = u0 + 1 / 3.0F, v0 = material / 3.0F, v1 = v0 + 1 / 3.0F;
        int light = LevelRenderer.getLightColor(level, block.relative(face));
        vertex(out, matrix, normals, centre.subtract(a).subtract(b), camera, u0, v1, light, n, alpha);
        vertex(out, matrix, normals, centre.add(a).subtract(b), camera, u1, v1, light, n, alpha);
        vertex(out, matrix, normals, centre.add(a).add(b), camera, u1, v0, light, n, alpha);
        vertex(out, matrix, normals, centre.subtract(a).add(b), camera, u0, v0, light, n, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, Vec3 p, Vec3 camera, float u, float v, int light, Vec3 n, int alpha) {
        out.vertex(matrix, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)).color(255, 255, 255, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normals, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
