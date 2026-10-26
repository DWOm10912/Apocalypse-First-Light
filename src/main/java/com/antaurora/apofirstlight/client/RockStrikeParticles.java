package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.boulder.BoulderBlocks;
import com.antaurora.apofirstlight.boulder.RockStrikeFx;
import com.antaurora.apofirstlight.registry.AflParticles;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pick strike's particles (RockStrikeFx; docs/gameplay/rock_strike_fx_v1.md), in place of vanilla's block crumbs and crit
 * stars:
 * <ul>
 *   <li>chips ({@link Chip}; rock_chip_0..5, pixel shards from tools/rock/build-rock-particle-sprites.mjs): thrown out of the
 *   face toward the striker in a cone, tumbling, in the colour of the rock's fresh break (its cut sprite,
 *   textures/block/rock/cut; a few darker, the weathered rind); they fall, bounce once or twice off what they land on
 *   (carved rock included), slide to a stop and shrink away;</li>
 *   <li>dust (FireFx#rockDust: its own sheet of thin powdery haze, textures/effect/rock_dust, in FireFx's world pass, capped
 *   with the smoke): the rock's powder, much paler than the rock, out of the face just in front of it, spreading and
 *   slowly settling; with grit, specks of the rock's grains raining out of it;</li>
 *   <li>sparks (FireFx#pickSparks: the bullet's sparks off steel, user 2026-10-24), on quartz-bearing rock and not every
 *   strike.</li>
 * </ul>
 * How many: by the litres broken off, three times for a block or boulder that went; halved at Decreased particles, a
 * token few at Minimal.
 */
public final class RockStrikeParticles {
    private RockStrikeParticles() {}

    /** the share of strikes on quartz-bearing rock that throw sparks */
    static final float SPARK_CHANCE = 0.45F;
    private static final Map<Integer, Integer> COLOURS = new ConcurrentHashMap<>();

    public static void spawn(RockStrikeFx p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        ParticleStatus status = mc.options.particles().get();
        float scale = status == ParticleStatus.ALL ? 1 : status == ParticleStatus.DECREASED ? 0.5F : 0.15F;
        int rgb = colour(Block.stateById(p.state()));
        float r = FastColor.ARGB32.red(rgb) / 255F, g = FastColor.ARGB32.green(rgb) / 255F, b = FastColor.ARGB32.blue(rgb) / 255F;
        RandomSource rnd = level.random;
        Vec3 out = p.out(), at = p.at().add(out.scale(0.03));
        float burst = p.broke() ? 3 : 1;
        int chips = Math.max(1, Math.round((5 + p.litres() / 7) * burst * scale));
        for (int i = 0; i < chips; i++) {
            Vec3 v = cone(out, 0.65, rnd).scale(0.08 + rnd.nextDouble() * 0.17 * (p.broke() ? 1.3 : 1));
            Particle c = mc.particleEngine.createParticle(AflParticles.ROCK_CHIP.get(), at.x, at.y, at.z, v.x, v.y + 0.04, v.z);
            if (c == null) continue;
            float shade = rnd.nextFloat() < 0.25F ? 0.62F + rnd.nextFloat() * 0.1F : 0.84F + rnd.nextFloat() * 0.2F;
            c.setColor(Math.min(1, r * shade), Math.min(1, g * shade), Math.min(1, b * shade));
            if (p.broke() && c instanceof Chip chip && rnd.nextFloat() < 0.3F) chip.bigger();
        }
        // dust: rock crushed to powder is much paler than the rock (fine grains scatter light); it starts just out of the face
        Vec3 puff = at.add(out.scale(0.05));
        int dust = Math.round((2 + p.litres() / 40) * burst);   // FireFx thins it at the reduced settings itself
        for (int i = 0; i < dust; i++) {
            Vec3 v = cone(out, 0.9, rnd).scale(0.025 + rnd.nextDouble() * 0.04);
            float lift = 0.45F + rnd.nextFloat() * 0.15F;
            int pale = FastColor.ARGB32.color(255, Math.round((r + (1 - r) * lift) * 255), Math.round((g + (1 - g) * lift) * 255),
                    Math.round((b + (1 - b) * lift) * 255));
            FireFx.rockDust(level, puff.x, puff.y, puff.z, v.x, v.y, v.z, (p.broke() ? 0.15F : 0.09F) + rnd.nextFloat() * 0.05F, pale,
                    0.6F + rnd.nextFloat() * 0.25F, 35);
        }
        // grit: the coarse grains in the dust, seen as specks raining out of it
        int grit = Math.round((4 + p.litres() / 15) * burst * scale);
        for (int i = 0; i < grit; i++) {
            Vec3 v = cone(out, 1.0, rnd).scale(0.02 + rnd.nextDouble() * 0.06);
            Particle c = mc.particleEngine.createParticle(AflParticles.ROCK_CHIP.get(), puff.x, puff.y, puff.z, v.x, v.y, v.z);
            if (c == null) continue;
            float lift = 0.2F + rnd.nextFloat() * 0.2F;
            c.setColor(r + (1 - r) * lift, g + (1 - g) * lift, b + (1 - b) * lift);
            if (c instanceof Chip chip) chip.grit();
        }
        if (p.sparks() && status != ParticleStatus.MINIMAL && rnd.nextFloat() < SPARK_CHANCE)
            FireFx.pickSparks(level, at, out, Math.max(1, Math.round((2 + rnd.nextInt(4)) * scale)));
    }

    /** A unit direction within about spread radians of axis. */
    private static Vec3 cone(Vec3 axis, double spread, RandomSource rnd) {
        Vec3 v = axis.add(new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).scale(spread * 0.6));
        return v.lengthSqr() < 1e-6 ? axis : v.normalize();
    }

    /**
     * The rock's fresh-break colour (ARGB): the mean of its cut sprite (a boulder: its rock's fresh one), else of its
     * particle sprite, else its map colour. Read once per state from the resource.
     */
    static int colour(BlockState state) {
        return COLOURS.computeIfAbsent(Block.getId(state), k -> {
            Minecraft mc = Minecraft.getInstance();
            var rm = mc.getResourceManager();
            ResourceLocation tex;
            if (state.hasProperty(BoulderBlocks.ROCK)) {
                tex = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/rock/cut/" + state.getValue(BoulderBlocks.ROCK).id + "_fresh.png");
            } else {
                TextureAtlasSprite s = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
                ResourceLocation name = s.contents().name();
                String last = name.getPath().substring(name.getPath().lastIndexOf('/') + 1);
                ResourceLocation cut = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/rock/cut/" + last + ".png");
                tex = rm.getResource(cut).isPresent() ? cut : new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
            }
            try (var in = rm.open(tex); NativeImage img = NativeImage.read(in)) {
                long sr = 0, sg = 0, sb = 0, n = 0;
                for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                    int c = img.getPixelRGBA(x, y);   // ABGR
                    if (FastColor.ABGR32.alpha(c) < 128) continue;
                    sr += FastColor.ABGR32.red(c);
                    sg += FastColor.ABGR32.green(c);
                    sb += FastColor.ABGR32.blue(c);
                    n++;
                }
                if (n > 0) return FastColor.ARGB32.color(255, (int) (sr / n), (int) (sg / n), (int) (sb / n));
            } catch (Exception ignored) {
                // the map colour below
            }
            return 0xFF000000 | state.getBlock().defaultMapColor().col;
        });
    }

    /**
     * A chip of rock: thrown, tumbling, bouncing a little, sliding to a stop, shrinking away at the end. Its own move:
     * vanilla's stops a particle for good the first time it lands, so it could neither bounce nor slide.
     */
    public static final class Chip extends TextureSheetParticle {
        private float size, spin;
        private int bounces;

        Chip(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z);
            xd = vx;
            yd = vy;
            zd = vz;
            setSize(0.04F, 0.04F);
            gravity = 1.0F;
            friction = 0.98F;
            lifetime = 30 + random.nextInt(35);
            quadSize = size = 0.012F + random.nextFloat() * random.nextFloat() * 0.03F;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            spin = (random.nextFloat() - 0.5F) * 0.9F;
            pickSprite(sprites);
        }

        /** a piece of a block or boulder that went: up to twice as big */
        void bigger() {
            quadSize = size = size * (1.4F + random.nextFloat() * 0.6F);
        }

        /** a grain of grit out of the dust: a speck, falling out without bouncing, gone sooner */
        void grit() {
            quadSize = size = 0.004F + random.nextFloat() * 0.004F;
            bounces = 2;
            spin = 0;
            lifetime = 15 + random.nextInt(15);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            oRoll = roll;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            yd -= 0.04 * gravity;
            double falling = yd;
            move(xd, yd, zd);
            if (onGround) {
                if (falling < -0.08 && bounces < 2) {
                    yd = -falling * 0.3;
                    bounces++;
                } else yd = 0;
                xd *= 0.55;
                zd *= 0.55;
                spin *= 0.4F;
            }
            xd *= friction;
            yd *= friction;
            zd *= friction;
            roll += spin;
        }

        @Override
        public void move(double dx, double dy, double dz) {
            double wx = dx, wy = dy, wz = dz;
            if (hasPhysics && (dx != 0 || dy != 0 || dz != 0) && dx * dx + dy * dy + dz * dz < 10000) {
                Vec3 v = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), getBoundingBox(), level, List.of());
                dx = v.x;
                dy = v.y;
                dz = v.z;
            }
            if (dx != 0 || dy != 0 || dz != 0) {
                setBoundingBox(getBoundingBox().move(dx, dy, dz));
                setLocationFromBoundingbox();
            }
            onGround = wy != dy && wy < 0;
            if (wx != dx) xd = 0;
            if (wz != dz) zd = 0;
        }

        @Override
        public float getQuadSize(float partialTick) {
            return size * Mth.clamp((lifetime - age - partialTick) / 8F, 0, 1);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        public static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
                return new Chip(level, x, y, z, vx, vy, vz, sprites);
            }
        }
    }
}
