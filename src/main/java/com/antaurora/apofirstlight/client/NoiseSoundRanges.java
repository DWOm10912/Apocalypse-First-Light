package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.noise.BlockBreakNoiseResolver;
import com.antaurora.apofirstlight.noise.InteractionNoiseResolver;
import com.antaurora.apofirstlight.noise.movement.MovementNoiseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Vanilla's own sounds heard as far as the infected hear the same action (audio pass 2026-10-09, the user: "原版那些也缩";
 * docs "声音与感染者听觉" section 14). Vanilla plays them over 16 blocks; their noise is 2-12. Each is caught as it starts
 * (PlaySoundEvent, before it resolves) and wrapped so that its resolved Sound fades out at the noise radius:
 * <ul>
 *   <li>a door, trapdoor or fence gate opening or closing: the radius of the block at the sound (InteractionNoiseResolver,
 *   so AFL's steel door, which opens by hand, counts as a wooden door as its noise does); by the sound's name when the
 *   block is gone;</li>
 *   <li>a chest or barrel opening or closing: 3;</li>
 *   <li>a block placed (a block sound type's place sound, played as BLOCKS): 4;</li>
 *   <li>a block broken (LevelRenderer's levelEvent 2001, mixin/client/LevelRendererBreakSoundMixin): the block's break
 *   noise (BlockBreakNoiseResolver, 4-12);</li>
 *   <li>a player's step (a block sound type's step sound, played as PLAYERS): the gait of the player it comes from, 2 / 4 / 8;</li>
 *   <li>a player landing hard (the small / big fall sound, and the block's fall sound with it): 8 / 12. Vanilla sounds a
 *   landing only from a damaging fall (over 3 blocks), where the noise is 8 (up to 6 blocks) or 12.</li>
 * </ul>
 * A listener beyond the radius gets nothing, so no subtitle either (1.20.1's subtitles ignore distance). Mobs' steps and
 * every other sound keep vanilla's range.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoiseSoundRanges {
    private static Set<ResourceLocation> places, steps, falls;   // every block sound type's, collected on first use
    private static @Nullable BlockState breaking;                // while LevelRenderer plays a block's break (levelEvent 2001)
    private static @Nullable BlockPos breakingAt;
    private static long bigFallTick = Long.MIN_VALUE;
    private static Vec3 bigFallAt = Vec3.ZERO;

    private NoiseSoundRanges() {
    }

    /** levelEvent 2001 starts (the block broken there) or ends (nulls). */
    public static void breaking(@Nullable BlockPos at, @Nullable BlockState state) {
        breakingAt = at;
        breaking = state;
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof RangedSoundInstance || sound instanceof Ranged || sound instanceof TickableSoundInstance
                || sound.isRelative() || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double radius = radius(mc, sound);
        if (radius <= 0) return;
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        if (listener.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > radius * radius) event.setSound(null);
        else event.setSound(new Ranged(sound, radius));
    }

    private static double radius(Minecraft mc, SoundInstance sound) {
        ResourceLocation id = sound.getLocation();
        String path = id.getPath();
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        if (breaking != null && breakingAt != null && sound.getSource() == SoundSource.BLOCKS && at.distanceToSqr(breakingAt.getCenter()) < 1.0) {
            return BlockBreakNoiseResolver.resolve(breaking).radius();
        }
        if (path.endsWith("door.open") || path.endsWith("door.close") || path.endsWith("fence_gate.open") || path.endsWith("fence_gate.close")) {
            Optional<InteractionNoiseResolver.Result> block = InteractionNoiseResolver.resolveToggle(mc.level.getBlockState(BlockPos.containing(at)));
            if (block.isPresent()) return block.get().radius();
            if (!mc.level.getBlockState(BlockPos.containing(at)).isAir()) return 0;   // another block with a door's sound (a fuel sump lid)
            boolean iron = path.contains("iron");
            if (path.contains("trapdoor")) return iron ? InteractionNoiseResolver.IRON_TRAPDOOR : InteractionNoiseResolver.WOODEN_TRAPDOOR;
            if (path.contains("fence_gate")) return InteractionNoiseResolver.FENCE_GATE;
            return iron ? InteractionNoiseResolver.IRON_DOOR : InteractionNoiseResolver.WOODEN_DOOR;
        }
        if (id.getNamespace().equals("minecraft")) {
            if (path.equals("block.chest.open") || path.equals("block.chest.close")) return InteractionNoiseResolver.CHEST;
            if (path.equals("block.barrel.open") || path.equals("block.barrel.close")) return InteractionNoiseResolver.BARREL;
        }
        if (places == null) collect();
        if (sound.getSource() == SoundSource.PLAYERS) {
            long now = mc.level.getGameTime();
            if (id.getNamespace().equals("minecraft") && path.equals("entity.player.big_fall")) {
                bigFallTick = now;
                bigFallAt = at;
                return MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS;
            }
            if (id.getNamespace().equals("minecraft") && path.equals("entity.player.small_fall")) return MovementNoiseEvents.HEAVY_LANDING_RADIUS;
            if (falls.contains(id)) {   // played right after the small or big fall sound
                return now - bigFallTick <= 1 && bigFallAt.distanceToSqr(at) < 4.0
                        ? MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS : MovementNoiseEvents.HEAVY_LANDING_RADIUS;
            }
            if (steps.contains(id)) {
                Player walker = null;
                double best = 4.0;
                for (Player player : mc.level.players()) {
                    double d = player.position().distanceToSqr(at);
                    if (d < best) { best = d; walker = player; }
                }
                return walker == null ? 0 : MovementNoiseEvents.footstepRadius(walker);
            }
        }
        if (sound.getSource() == SoundSource.BLOCKS && places.contains(id)) return InteractionNoiseResolver.BLOCK_PLACE;
        return 0;
    }

    @SuppressWarnings("deprecation")
    private static void collect() {
        Set<ResourceLocation> place = new HashSet<>(), step = new HashSet<>(), fall = new HashSet<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                try {
                    SoundType type = state.getSoundType();
                    place.add(type.getPlaceSound().getLocation());
                    step.add(type.getStepSound().getLocation());
                    fall.add(type.getFallSound().getLocation());
                } catch (RuntimeException ignored) {
                    // a block whose sound type needs a level: it keeps vanilla's range
                }
            }
        }
        places = place;
        steps = step;
        falls = fall;
    }

    /** The sound as it was, but its resolved Sound fades out at the radius (and its volume kept at most 1, the range is max(volume, 1) x that). */
    private static final class Ranged implements SoundInstance {
        private final SoundInstance inner;
        private final int radius;
        private @Nullable Sound sound;

        Ranged(SoundInstance inner, double radius) {
            this.inner = inner;
            this.radius = Math.max(1, (int) Math.round(radius));
        }

        @Override public ResourceLocation getLocation() { return inner.getLocation(); }

        @Override
        public @Nullable WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents events = inner.resolve(manager);
            Sound s = inner.getSound();
            sound = s == null || s == SoundManager.EMPTY_SOUND || s == SoundManager.INTENTIONALLY_EMPTY_SOUND ? s
                    : new Sound(s.getLocation().toString(), s.getVolume(), s.getPitch(), s.getWeight(), s.getType(), s.shouldStream(),
                    s.shouldPreload(), radius);
            return events;
        }

        @Override public Sound getSound() { return sound != null ? sound : inner.getSound(); }
        @Override public SoundSource getSource() { return inner.getSource(); }
        @Override public boolean isLooping() { return inner.isLooping(); }
        @Override public boolean isRelative() { return inner.isRelative(); }
        @Override public int getDelay() { return inner.getDelay(); }
        @Override public float getVolume() { return Math.min(inner.getVolume(), 1.0F); }
        @Override public float getPitch() { return inner.getPitch(); }
        @Override public double getX() { return inner.getX(); }
        @Override public double getY() { return inner.getY(); }
        @Override public double getZ() { return inner.getZ(); }
        @Override public Attenuation getAttenuation() { return inner.getAttenuation(); }
        @Override public boolean canStartSilent() { return inner.canStartSilent(); }
        @Override public boolean canPlaySound() { return inner.canPlaySound(); }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return inner.getStream(buffers, sound, looping);
        }
    }
}
