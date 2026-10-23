package com.antaurora.apofirstlight.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Wood off a saw or a knife (2026-10-22, docs/gameplay/afl_workbench_v1_design.md 18; first users: client/workbench/
 * WorkbenchWork, the rip, the beam and the wooden knob): real little pieces, not billboards. Sawdust and chips are small
 * boxes, a knife's shaving a thin curled strip; each takes a few texels of the wood's own planks sprite (the inside of the
 * wood, whatever the bark), the faces shaded as a block's are, tumbling as it falls (gravity and air drag: sawdust drifts
 * down out of a kerf, a shaving flies off the knife on a parabola), and once it lands it settles flat on what it hit and lies
 * there a while before it shrinks away. Velocities in blocks a second. Spawned directly (no particle type: client-only).
 */
public final class WoodChipParticle extends TextureSheetParticle {
    /** What comes off: sawdust (a speck), a chip (a flake), a shaving (a curled strip). */
    public enum Kind { DUST, CHIP, SHAVING }

    private static final int SEGMENTS = 4, SHRINK = 12;
    private static final float TURN = (float) (Math.PI * 2);
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private final Kind kind;
    /** The piece's size (blocks: along, thickness, across) and a shaving's bend (rad over its length). */
    private final float len, thick, wid, curl;
    /** The texels it shows (block atlas). */
    private final float pu0, pv0, pu1, pv1;
    private final Quaternionf turn = new Quaternionf(), oTurn = new Quaternionf();
    private final Vector3f spinAxis;
    private float spin;
    private final float shade;
    @org.jetbrains.annotations.Nullable private Quaternionf flat;

    private WoodChipParticle(ClientLevel level, Vec3 at, Vec3 velocity, TextureAtlasSprite sprite, Kind kind, float scale) {
        super(level, at.x, at.y, at.z);
        setSprite(sprite);
        this.kind = kind;
        xd = velocity.x / 20;
        yd = velocity.y / 20;
        zd = velocity.z / 20;
        hasPhysics = true;
        float r = random.nextFloat();
        switch (kind) {
            case DUST -> {
                len = (0.0028F + 0.0030F * r) * scale;
                wid = len * (0.6F + 0.4F * random.nextFloat());
                thick = len * (0.4F + 0.4F * random.nextFloat());
                curl = 0;
                gravity = 0.5F;
                friction = 0.85F;
                lifetime = 60 + random.nextInt(50);
            }
            case CHIP -> {
                len = (0.006F + 0.007F * r) * scale;
                wid = len * (0.45F + 0.4F * random.nextFloat());
                thick = Math.max(0.0012F, len * 0.18F);
                curl = 0;
                gravity = 0.62F;
                friction = 0.95F;
                lifetime = 140 + random.nextInt(80);
            }
            default -> {
                len = (0.016F + 0.016F * r) * scale;
                wid = (0.0045F + 0.0035F * random.nextFloat()) * Math.max(0.6F, scale);
                thick = 0.0008F;
                curl = 2.6F + 2.2F * random.nextFloat();
                gravity = 0.55F;
                friction = 0.93F;
                lifetime = 180 + random.nextInt(100);
            }
        }
        setSize(Math.max(0.004F, Math.min(len, 0.02F)), Math.max(0.002F, thick));
        // a few texels of the sprite: a speck takes about one, a shaving a strip along the grain
        float su = sprite.getU1() - sprite.getU0(), sv = sprite.getV1() - sprite.getV0();
        float fu = kind == Kind.DUST ? 1 / 32F : kind == Kind.CHIP ? 1 / 12F : 1 / 4F, fv = kind == Kind.SHAVING ? 1 / 24F : fu * 0.7F;
        pu0 = sprite.getU0() + su * (1 - fu) * random.nextFloat();
        pv0 = sprite.getV0() + sv * (1 - fv) * random.nextFloat();
        pu1 = pu0 + su * fu;
        pv1 = pv0 + sv * fv;
        turn.rotateXYZ(random.nextFloat() * TURN, random.nextFloat() * TURN, random.nextFloat() * TURN);
        oTurn.set(turn);
        spinAxis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize();
        // tumbling: a speck slow, a flake and a shaving quicker (rad a tick)
        spin = (kind == Kind.DUST ? 0.15F : 0.35F) + 0.35F * random.nextFloat();
        shade = 0.92F + 0.08F * random.nextFloat();
    }

    /** One piece of {@code wood} (a log or planks block: its planks' sprite) at {@code at}. */
    public static void spawn(ClientLevel level, Kind kind, Vec3 at, Vec3 velocity, BlockState wood, float scale) {
        Minecraft.getInstance().particleEngine.add(new WoodChipParticle(level, at, velocity, sprite(wood), kind, scale));
    }

    /** Each wood block's planks (the sprite itself is looked up each time: a resource reload rebuilds the atlas). */
    private static final Map<Block, BlockState> INSIDE = new HashMap<>();

    /** The inside of the wood: the species' planks ({@code x_log} -> {@code x_planks}), else the block's own particle sprite. */
    private static TextureAtlasSprite sprite(BlockState wood) {
        BlockState shown = INSIDE.computeIfAbsent(wood.getBlock(), b -> {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id == null) return wood;
            String path = id.getPath().replace("stripped_", "");
            for (String end : new String[]{"_log", "_wood", "_beam"}) {
                if (!path.endsWith(end)) continue;
                Block planks = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id.getNamespace(), path.substring(0, path.length() - end.length()) + "_planks"));
                if (planks != null && planks != Blocks.AIR) return planks.defaultBlockState();
            }
            return wood;
        });
        return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(shown);
    }

    @Override
    public void tick() {
        oTurn.set(turn);
        super.tick();
        if (removed) return;
        if (!onGround) {
            // a shaving flutters a little as it falls
            if (kind == Kind.SHAVING && yd < 0) {
                xd += (random.nextFloat() - 0.5F) * 0.0016F;
                zd += (random.nextFloat() - 0.5F) * 0.0016F;
            }
            turn.premul(new Quaternionf().fromAxisAngleRad(spinAxis, spin));
            return;
        }
        // landed: settle flat on its broad side (its thickness, local y, up or down), turned as it fell
        if (flat == null) {
            Vector3f n = turn.transform(new Vector3f(0, 1, 0));
            if (n.y < 0) n.negate();
            flat = new Quaternionf().rotationTo(n, UP).mul(turn);
        }
        turn.slerp(flat, 0.45F);
        xd *= 0.5;
        zd *= 0.5;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    /** Lit as the air just over it: a piece on a bench top sits in the bench's own cell. */
    @Override
    protected int getLightColor(float partial) {
        BlockPos at = BlockPos.containing(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y) + 0.15, Mth.lerp(partial, zo, z));
        return level.hasChunkAt(at) ? LevelRenderer.getLightColor(level, at) : 0;
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer out, Camera camera, float partial) {
        Vec3 cam = camera.getPosition();
        float s = age + partial > lifetime - SHRINK ? Math.max(0, (lifetime - age - partial) / SHRINK) : 1;
        if (s <= 0) return;
        Quaternionf q = new Quaternionf(oTurn).slerp(turn, partial);
        Vector3f c = new Vector3f((float) (Mth.lerp(partial, xo, x) - cam.x),
                (float) (Mth.lerp(partial, yo, y) - cam.y) + thick * s / 2, (float) (Mth.lerp(partial, zo, z) - cam.z));
        int light = getLightColor(partial);
        if (kind == Kind.SHAVING) strip(out, q, c, s, light);
        else box(out, q, c, s, light);
    }

    /** The speck or flake: six faces of a box (only those toward the eye). */
    private void box(com.mojang.blaze3d.vertex.VertexConsumer out, Quaternionf q, Vector3f c, float s, int light) {
        float hx = len * s / 2, hy = thick * s / 2, hz = wid * s / 2;
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vector3f n = new Vector3f(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);
                Vector3f[] v = new Vector3f[4];
                for (int i = 0; i < 4; i++) {
                    float a = (i == 1 || i == 2) ? 1 : -1, b = (i >= 2) ? 1 : -1;
                    Vector3f p = switch (axis) {
                        case 0 -> new Vector3f(sign * hx, a * hy, b * hz);
                        case 1 -> new Vector3f(a * hx, sign * hy, b * hz);
                        default -> new Vector3f(a * hx, b * hy, sign * hz);
                    };
                    v[i] = q.transform(p).add(c);
                }
                face(out, v, q.transform(n), c, light);
            }
        }
    }

    /** The shaving: a thin strip curled along its length (both sides; the one toward the eye drawn). */
    private void strip(com.mojang.blaze3d.vertex.VertexConsumer out, Quaternionf q, Vector3f c, float s, int light) {
        float l = len * s, w = wid * s / 2, radius = l / curl;
        Vector3f[] along = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float th = curl * ((float) i / SEGMENTS - 0.5F);
            along[i] = new Vector3f(radius * Mth.sin(th), radius * (1 - Mth.cos(th)) - radius * (1 - Mth.cos(curl / 2)) / 2, 0);
        }
        for (int i = 0; i < SEGMENTS; i++) {
            Vector3f a = along[i], b = along[i + 1];
            Vector3f[] v = {
                    q.transform(new Vector3f(a.x, a.y, -w)).add(c), q.transform(new Vector3f(a.x, a.y, w)).add(c),
                    q.transform(new Vector3f(b.x, b.y, w)).add(c), q.transform(new Vector3f(b.x, b.y, -w)).add(c)};
            Vector3f n = new Vector3f(v[1]).sub(v[0]).cross(new Vector3f(v[3]).sub(v[0])).normalize();
            if (n.dot(c) > 0) n.negate();   // the side toward the eye (c: from the eye to the piece)
            face(out, v, n, c, light, (float) i / SEGMENTS, (float) (i + 1) / SEGMENTS);
        }
    }

    private void face(com.mojang.blaze3d.vertex.VertexConsumer out, Vector3f[] v, Vector3f n, Vector3f c, int light) {
        if (n.dot(c) >= 0) return;   // turned away from the eye
        face(out, v, n, c, light, 0, 1);
    }

    /** One quad, wound so its front is toward n, shaded as a block face of that normal, texels from..to along. */
    private void face(com.mojang.blaze3d.vertex.VertexConsumer out, Vector3f[] v, Vector3f n, Vector3f c, int light, float from, float to) {
        Vector3f wind = new Vector3f(v[1]).sub(v[0]).cross(new Vector3f(v[2]).sub(v[0]));
        boolean flip = wind.dot(n) < 0;
        float lit = shade * Math.min(1, n.x * n.x * 0.6F + n.z * n.z * 0.8F + n.y * n.y * (n.y > 0 ? 1F : 0.5F));
        float u0 = Mth.lerp(from, pu0, pu1), u1 = Mth.lerp(to, pu0, pu1);
        float[][] uv = {{u0, pv0}, {u0, pv1}, {u1, pv1}, {u1, pv0}};
        for (int k = 0; k < 4; k++) {
            int i = flip ? 3 - k : k;
            out.vertex(v[i].x, v[i].y, v[i].z).uv(uv[i][0], uv[i][1]).color(lit, lit, lit, alpha).uv2(light).endVertex();
        }
    }
}
