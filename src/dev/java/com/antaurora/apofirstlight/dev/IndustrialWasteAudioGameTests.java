package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Records actual server sound dispatch, not an assertion that a speaker/client heard it. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class IndustrialWasteAudioGameTests {
    private record Played(SoundEvent sound, float volume, float pitch, Vec3 position) { }
    private static final List<Played> SOUNDS = new ArrayList<>();
    private static boolean recording;

    @SubscribeEvent
    public static void captureSound(PlayLevelSoundEvent.AtPosition event) {
        if (!recording || !(event.getLevel().getServer() instanceof GameTestServer) || event.getSound() == null) return;
        SoundEvent sound = event.getSound().value();
        if (isWaterInteractionSound(sound)) {
            SOUNDS.add(new Played(sound, event.getNewVolume(), event.getNewPitch(), event.getPosition()));
        }
    }

    @GameTest(template = "waste_empty", timeoutTicks = 100)
    public static void entrySwimCadenceAndVariations(GameTestHelper h) {
        for (int x = 1; x <= 11; x++) {
            for (int z = 1; z <= 10; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
                h.setBlock(x, 2, z, x == 1 || x == 11 || z == 1 || z == 10
                        ? Blocks.STONE : AflBlocks.INDUSTRIAL_WASTE.get());
            }
        }
        h.runAtTickTime(1, () -> {
            recording = true;
            try {
                var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "waste-audio-test"));
                player.setGameMode(GameType.SURVIVAL);
                var cow = EntityType.COW.create(h.getLevel());
                var item = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.STONE));
                Vec3 wet = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))).add(0, 0.1, 0);
                Vec3 dry = wet.add(0, 3, 0);
                checkEntries(h, player, dry, wet);
                checkEntries(h, cow, dry, wet);
                checkEntries(h, item, dry, wet);

                // Spawn-in-fluid is not a new entry, matching Vanilla firstTick suppression.
                SOUNDS.clear();
                var spawnedWet = EntityType.COW.create(h.getLevel());
                spawnedWet.setPos(wet);
                spawnedWet.setDeltaMovement(0, -2, 0);
                spawnedWet.baseTick();
                spawnedWet.baseTick();
                h.assertTrue(SOUNDS.isEmpty(), "Spawned-in-fluid entity splashed");

                SOUNDS.clear();
                player.setPos(wet);
                player.baseTick();
                player.setSprinting(false);
                for (int i = 0; i < 12; i++) {
                    player.setDeltaMovement(0.2, 0, 0);
                    player.move(MoverType.SELF, new Vec3(0.2, 0, 0));
                }
                List<Played> normal = swims();
                h.assertTrue(!normal.isEmpty() && normal.size() < 12, "Normal swim did not use distance cadence");
                float normalVolume = weightedVolume(new Vec3(0.2, 0, 0), 0.35F);
                for (Played sound : normal) {
                    h.assertTrue(sound.sound() == SoundEvents.PLAYER_SWIM, "Normal swim did not use Vanilla Player sound");
                    near(h, sound.volume(), normalVolume, "Normal swim volume");
                    h.assertTrue(sound.pitch() >= 0.6F && sound.pitch() <= 1.4F, "Normal pitch out of vanilla range");
                }
                SOUNDS.clear();
                player.setSprinting(true);
                for (int i = 0; i < 12; i++) {
                    player.setDeltaMovement(0.3, 0, 0);
                    player.move(MoverType.SELF, new Vec3(0.3, 0, 0));
                }
                List<Played> fast = swims();
                h.assertTrue(!fast.isEmpty() && fast.size() < 12, "Fast swim cadence missing or per-move spam");
                float fastVolume = weightedVolume(new Vec3(0.3, 0, 0), 0.35F);
                for (Played sound : fast) {
                    h.assertTrue(sound.sound() == SoundEvents.PLAYER_SWIM, "Fast swim invented a separate event");
                    near(h, sound.volume(), fastVolume, "Fast swim volume");
                    h.assertTrue(sound.pitch() >= 0.6F && sound.pitch() <= 1.4F, "Fast pitch out of vanilla range");
                }
                SOUNDS.clear();
                for (int i = 0; i < 20; i++) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.move(MoverType.SELF, Vec3.ZERO);
                }
                h.assertTrue(SOUNDS.isEmpty(), "Stationary entity kept swimming/splashing");
                player.setSilent(true);
                player.setPos(dry);
                player.baseTick();
                player.setPos(wet);
                player.setDeltaMovement(0, -2, 0);
                player.baseTick();
                h.assertTrue(SOUNDS.isEmpty(), "Silent entity played splash");
                ApocalypseFirstLight.LOGGER.info("[AFL WASTE AUDIO TEST] player/cow/item entry, re-entry, firstTick, "
                        + "Vanilla small/large events and parameters, positional single dispatch, Vanilla swim cadence/parameters, "
                        + "stop and silent passed; normal={}, fast={}",
                        normal.size(), fast.size());
                h.succeed();
            } finally {
                recording = false;
                SOUNDS.clear();
            }
        });
    }

    private static void checkEntries(GameTestHelper h, Entity entity, Vec3 dry, Vec3 wet) {
        entity.setPos(dry);
        entity.baseTick();
        SOUNDS.clear();
        entity.setPos(wet);
        entity.setDeltaMovement(0, -2, 0);
        entity.baseTick();
        h.assertTrue(SOUNDS.size() == 1, "Expected exactly one entry sound for " + entity.getType());
        Played large = SOUNDS.get(0);
        SoundEvent expectedLarge = entity instanceof Player
                ? SoundEvents.PLAYER_SPLASH_HIGH_SPEED : SoundEvents.GENERIC_SPLASH;
        h.assertTrue(large.sound() == expectedLarge, "Wrong Vanilla high-speed splash event");
        near(h, large.volume(), 0.5F, "Large splash volume");
        h.assertTrue(large.pitch() >= 0.6F && large.pitch() <= 1.4F, "Large splash pitch out of vanilla range");
        h.assertTrue(large.position().distanceTo(wet) < 0.001, "Sound not at entity world position");
        for (int i = 0; i < 20; i++) entity.baseTick();
        h.assertTrue(SOUNDS.size() == 1, "Repeated splash while immersed");
        entity.setPos(dry);
        entity.baseTick();
        entity.setPos(wet);
        entity.setDeltaMovement(0, -0.1, 0);
        entity.baseTick();
        h.assertTrue(SOUNDS.size() == 2, "Re-entry did not produce exactly one sound");
        Played small = SOUNDS.get(1);
        SoundEvent expectedSmall = entity instanceof Player ? SoundEvents.PLAYER_SPLASH : SoundEvents.GENERIC_SPLASH;
        h.assertTrue(small.sound() == expectedSmall, "Wrong Vanilla small splash event");
        near(h, small.volume(), 0.025F, "Small splash volume");
        h.assertTrue(small.pitch() >= 0.6F && small.pitch() <= 1.4F, "Small splash pitch out of vanilla range");
    }

    private static List<Played> swims() {
        return SOUNDS.stream().filter(sound -> sound.sound() == SoundEvents.PLAYER_SWIM
                || sound.sound() == SoundEvents.GENERIC_SWIM).toList();
    }

    private static boolean isWaterInteractionSound(SoundEvent sound) {
        return sound == SoundEvents.PLAYER_SPLASH || sound == SoundEvents.PLAYER_SPLASH_HIGH_SPEED
                || sound == SoundEvents.GENERIC_SPLASH || sound == SoundEvents.PLAYER_SWIM
                || sound == SoundEvents.GENERIC_SWIM;
    }

    private static float weightedVolume(Vec3 velocity, float scale) {
        return Math.min(1.0F, (float) Math.sqrt(velocity.x * velocity.x * 0.2F
                + velocity.y * velocity.y + velocity.z * velocity.z * 0.2F) * scale);
    }

    private static void near(GameTestHelper h, float actual, float expected, String name) {
        h.assertTrue(Math.abs(actual - expected) < 0.0001F, name + ": expected " + expected + ", got " + actual);
    }
}
