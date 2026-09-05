package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
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
        if (sound == AflSounds.INDUSTRIAL_WASTE_SPLASH.get() || sound == AflSounds.INDUSTRIAL_WASTE_SWIM.get()) {
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
                float expected = (float) Math.sqrt(0.2 * 0.2 * 0.2F) * 0.35F;
                for (Played sound : normal) {
                    near(h, sound.volume(), expected, "Normal swim volume");
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
                for (Played sound : fast) {
                    near(h, sound.volume(), (float) Math.sqrt(0.3 * 0.3 * 0.2F) * 0.35F * 1.15F, "Fast volume");
                    h.assertTrue(sound.pitch() >= 0.645F && sound.pitch() <= 1.45F, "Fast pitch out of range");
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
                        + "small/large volume, positional single dispatch, normal/fast cadence, stop and silent passed; normal={}, fast={}",
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
        h.assertTrue(large.sound() == AflSounds.INDUSTRIAL_WASTE_SPLASH.get(), "Wrong entry event");
        near(h, large.volume(), 0.4F, "Large splash volume");
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
        h.assertTrue(small.sound() == large.sound(), "Small splash must reuse the same event");
        near(h, small.volume(), 0.1F * 0.2F * 0.65F, "Small splash volume");
        h.assertTrue(small.pitch() >= 0.645F && small.pitch() <= 1.45F, "Small splash pitch out of range");
    }

    private static List<Played> swims() {
        return SOUNDS.stream().filter(sound -> sound.sound() == AflSounds.INDUSTRIAL_WASTE_SWIM.get()).toList();
    }

    private static void near(GameTestHelper h, float actual, float expected, String name) {
        h.assertTrue(Math.abs(actual - expected) < 0.0001F, name + ": expected " + expected + ", got " + actual);
    }
}
