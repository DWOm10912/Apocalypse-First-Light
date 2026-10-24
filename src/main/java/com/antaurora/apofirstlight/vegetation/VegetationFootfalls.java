package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where feet come down in the mesh plants (AFL vegetation, docs/gameplay/vegetation_v1_design.md 12; user 2026-10-23:
 * "从上面踏过应该直接压断"): on the server, every living thing at least MeshPlantBlock#TREAD_WIDTH wide, on the ground,
 * steps once per stride of the distance it covers: a person about 0.5 m creeping, 0.7 m walking, 0.75 m sprinting;
 * wider bodies a longer stride. The feet alternate left and right of its middle (0.13 m each side for a person), a little
 * ahead of it, along the way it moves. Landing from a jump or a fall puts both feet down. A foot in a mesh plant's block
 * leaves a footprint there (MeshPlantBlock#footfall) and rustles (doc 19.3): heard as far as the step's own noise (a
 * player's: MovementNoiseEvents#footstepRadius; anything else as a walk, {@value #MOB_RUSTLE_RADIUS} blocks), quieter
 * creeping and among plants already down. It makes no noise of its own for the infected: the step already does. One
 * rustle plays out before the same walker starts the next (doc 19.5: every step restarting it was noise), at
 * {@value #RUSTLE_VOLUME} of the take's level (it is heard at the walker's own feet).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class VegetationFootfalls {
    /** How far a non-player's rustle carries (blocks): a person's walking step. */
    static final double MOB_RUSTLE_RADIUS = 4.0;
    /** A rustle's volume walking upright through standing plants (doc 19.5: 1 was loud in first person). */
    static final float RUSTLE_VOLUME = 0.45F;
    /**
     * Per walker: last x, last z, distance since the last step, the next foot (-1 left, 1 right), on the ground (1) or not,
     * the game tick until which its last rustle still plays.
     */
    private static final Map<LivingEntity, double[]> GAIT = new WeakHashMap<>();

    private VegetationFootfalls() {}

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity walker = event.getEntity();
        if (!(walker.level() instanceof ServerLevel level)) return;
        if (walker.getBbWidth() < MeshPlantBlock.TREAD_WIDTH || walker.isSpectator() || walker.isPassenger() || !walker.isAlive()) {
            GAIT.remove(walker);
            return;
        }
        double[] g = GAIT.get(walker);
        if (g == null) {
            GAIT.put(walker, new double[]{walker.getX(), walker.getZ(), 0, 1, walker.onGround() ? 1 : 0, 0});
            return;
        }
        double dx = walker.getX() - g[0], dz = walker.getZ() - g[1], d = Math.sqrt(dx * dx + dz * dz);
        g[0] = walker.getX();
        g[1] = walker.getZ();
        if (!walker.onGround()) {
            g[4] = 0;
            return;
        }
        int heading = d > 0.01 ? MeshPlantBlock.heading(dx, dz) : facing(walker);
        if (g[4] == 0) {
            // landed: both feet
            g[4] = 1;
            g[2] = 0;
            step(level, walker, g, heading, -1);
            step(level, walker, g, heading, 1);
            return;
        }
        if (d > 2) { g[2] = 0; return; }   // a teleport, not a step
        g[2] += d;
        double stride = (0.45 + 1.1 * Math.min(d, 0.3)) * Math.sqrt(walker.getBbWidth() / 0.6);
        if (g[2] < stride) return;
        g[2] -= stride;
        g[3] = -g[3];
        step(level, walker, g, heading, (int) g[3]);
    }

    /** The rustle a foot makes in a species (doc 19.3): short turf and rosettes, tall grass and stems, the bramble. */
    static SoundEvent rustle(MeshPlantBlock.Species species) {
        return switch (species) {
            case BLUEGRASS, YARROW, PENNSYLVANIA_SEDGE, PLANTAIN, DANDELION -> AflSounds.VEGETATION_RUSTLE_LOW.get();
            case BIG_BLUESTEM, LITTLE_BLUESTEM, BRACKEN_FERN, PURPLE_CONEFLOWER, BLACK_EYED_SUSAN, CANADA_GOLDENROD, CATTAIL,
                 COMMON_REED -> AflSounds.VEGETATION_RUSTLE_TALL.get();
            case ALLEGHENY_BLACKBERRY -> AflSounds.VEGETATION_RUSTLE_BRAMBLE.get();
        };
    }

    /** How long a rustle plays (ticks, the takes' lengths: 0.64, 0.97, 0.50 s): the same walker's next waits for it. */
    static int rustleTicks(MeshPlantBlock.Species species) {
        SoundEvent sound = rustle(species);
        return sound == AflSounds.VEGETATION_RUSTLE_TALL.get() ? 18 : sound == AflSounds.VEGETATION_RUSTLE_LOW.get() ? 12 : 10;
    }

    private static int facing(LivingEntity walker) {
        float yaw = walker.getYRot() * Mth.DEG_TO_RAD;
        return MeshPlantBlock.heading(-Mth.sin(yaw), Mth.cos(yaw));
    }

    /** One foot (side -1 left, 1 right of the way walked) comes down. */
    private static void step(ServerLevel level, LivingEntity walker, double[] g, int heading, int side) {
        float hx = MeshPlantBlock.headingX(heading), hz = MeshPlantBlock.headingZ(heading);
        // (-hz, hx) is the right of the way walked
        double gait = 0.13 * walker.getBbWidth() / 0.6;
        double fx = walker.getX() + hx * 0.12 - hz * gait * side, fz = walker.getZ() + hz * 0.12 + hx * gait * side;
        int bx = Mth.floor(fx), bz = Mth.floor(fz), by = Mth.floor(walker.getY() + 0.01);
        // the plant is in the feet's block, or one lower (a foot over a step's edge)
        for (int y = by; y >= by - 1; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MeshPlantBlock plant) {
                boolean standing = state.getValue(MeshPlantBlock.TRODDEN) == MeshPlantBlock.STANDING;
                plant.footfall(level, pos, state, heading, fx - bx, fz - bz);
                long now = level.getGameTime();
                if (now < g[5]) return;   // the last rustle still plays
                g[5] = now + rustleTicks(plant.species);
                double radius = walker instanceof Player player ? MovementNoiseEvents.footstepRadius(player) : MOB_RUSTLE_RADIUS;
                float volume = RUSTLE_VOLUME * (walker.isCrouching() ? 0.55F : 1) * (standing ? 1 : 0.6F);
                RangedSound.play(level, new Vec3(fx, y + 0.25, fz), rustle(plant.species), SoundSource.BLOCKS, radius, volume,
                        0.9F + level.getRandom().nextFloat() * 0.2F);
                return;
            }
        }
    }
}
