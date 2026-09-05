package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.ChemicalReactorBlockEntity;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.SoundActions;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;

/** DEV-only: the existing jar task excludes this package. No player world is modified. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class IndustrialWasteGameTests {
    @SubscribeEvent
    public static void createTestTemplate(ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level = event.getServer().overworld();
        var template = TagParser.parseTag("{size:[12,6,12],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var airBlocks = new net.minecraft.nbt.ListTag();
        // Explicit air matters when a larger audio pool previously occupied a smaller fluid test's slot.
        // An empty block list is not a clear operation when reusing the GameTest world.
        for (int x = 0; x < 12; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 12; z++) {
            var block = new net.minecraft.nbt.CompoundTag();
            var position = new net.minecraft.nbt.ListTag();
            position.add(net.minecraft.nbt.IntTag.valueOf(x));
            position.add(net.minecraft.nbt.IntTag.valueOf(y));
            position.add(net.minecraft.nbt.IntTag.valueOf(z));
            block.put("pos", position);
            block.putInt("state", 0);
            airBlocks.add(block);
        }
        template.put("blocks", airBlocks);
        level.getStructureManager().getOrCreate(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "waste_empty"))
                .load(level.holderLookup(Registries.BLOCK), template);
    }

    private static FluidState fluid(GameTestHelper h, int x, int z) {
        return h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, 2, z)));
    }

    private static void basin(GameTestHelper h, int width, int depth) {
        for (int x = 1; x <= width + 2; x++) {
            for (int z = 1; z <= depth + 2; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
                h.setBlock(x, 2, z, x == 1 || z == 1 || x == width + 2 || z == depth + 2
                        ? Blocks.STONE : Blocks.AIR);
            }
        }
    }

    private static void placeBucket(GameTestHelper h, int x, int z) {
        var pos = h.absolutePos(new BlockPos(x, 2, z));
        h.assertTrue(((BucketItem) AflItems.INDUSTRIAL_WASTE_BUCKET.get())
                .emptyContents(h.makeMockSurvivalPlayer(), h.getLevel(), pos, null), "Bucket placement failed");
        h.assertTrue(fluid(h, x, z).isSource(), "Bucket did not place a source");
    }

    private static int sources(GameTestHelper h, int width, int depth) {
        int count = 0;
        for (int x = 2; x <= width + 1; x++) {
            for (int z = 2; z <= depth + 1; z++) {
                if (fluid(h, x, z).isSource()) count++;
            }
        }
        return count;
    }

    private static void scoop(GameTestHelper h, int x, int z) {
        var pos = h.absolutePos(new BlockPos(x, 2, z));
        var result = AflBlocks.INDUSTRIAL_WASTE.get().pickupBlock(h.getLevel(), pos, h.getLevel().getBlockState(pos));
        h.assertTrue(result.is(AflItems.INDUSTRIAL_WASTE_BUCKET.get()), "Wrong pickup bucket");
        var contents = FluidUtil.getFluidContained(result).orElse(FluidStack.EMPTY);
        h.assertTrue(contents.getAmount() == 1000 && contents.getFluid() == AflFluids.INDUSTRIAL_WASTE.get(),
                "Bucket must contain exactly 1000 mB industrial waste");
        h.assertTrue(!fluid(h, x, z).isSource(), "Pickup did not remove real source");
    }

    @GameTest(template = "waste_empty", timeoutTicks = 180)
    public static void singleSourceFlowsAndDrains(GameTestHelper h) {
        basin(h, 5, 5);
        placeBucket(h, 4, 4);
        h.runAtTickTime(60, () -> {
            h.assertTrue(sources(h, 5, 5) == 1, "Single source multiplied");
            h.assertTrue(!fluid(h, 3, 4).isEmpty() && !fluid(h, 3, 4).isSource(), "No normal flowing state");
            scoop(h, 4, 4);
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(sources(h, 5, 5) == 0, "Source regenerated after pickup");
            h.assertTrue(fluid(h, 3, 4).isEmpty(), "Flow did not recede after source pickup");
            h.succeed();
        });
    }

    @GameTest(template = "waste_empty", timeoutTicks = 80)
    public static void survivalBucketUseAndWaterPublicHooks(GameTestHelper h) {
        basin(h, 3, 3);
        // BucketItem's vanilla criteria require a ServerPlayer, unlike GameTest's plain mock Player.
        var player = new net.minecraftforge.common.util.FakePlayer(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "waste-bucket-test"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var eyeBase = h.absolutePos(new BlockPos(3, 3, 3));
        player.setPos(eyeBase.getX() + 0.5, eyeBase.getY(), eyeBase.getZ() + 0.5);
        player.setXRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.INDUSTRIAL_WASTE_BUCKET.get()));
        var placed = player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, placed.getObject());
        h.assertTrue(player.getMainHandItem().is(Items.BUCKET), "World placement did not consume full survival bucket");
        h.assertTrue(fluid(h, 3, 3).isSource(), "Survival bucket use did not place source");
        var pickedUp = player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(pickedUp.getObject().is(AflItems.INDUSTRIAL_WASTE_BUCKET.get()), "Survival pickup returned wrong item");
        h.assertTrue(!fluid(h, 3, 3).isSource(), "Survival bucket use left source behind");

        var waste = AflFluids.INDUSTRIAL_WASTE.get();
        var type = waste.getFluidType();
        var water = ForgeMod.WATER_TYPE.get();
        h.assertTrue(!waste.is(FluidTags.WATER) && !waste.is(FluidTags.LAVA), "Waste must not inherit water/lava identity");
        h.assertTrue(type.getDensity() == water.getDensity() && type.getViscosity() == water.getViscosity()
                && type.getTemperature() == water.getTemperature() && type.getLightLevel() == 0,
                "Water default material properties diverged");
        h.assertTrue(type.canSwim(player) && type.canPushEntity(player) && type.canDrownIn(player)
                && type.canExtinguish(player) && type.canHydrate(player)
                && type.getFallDistanceModifier(player) == water.getFallDistanceModifier(player),
                "Water public entity flags diverged");
        for (var sound : new net.minecraftforge.common.SoundAction[] {
                SoundActions.BUCKET_FILL, SoundActions.BUCKET_EMPTY, SoundActions.FLUID_VAPORIZE}) {
            h.assertTrue(type.getSound(sound) == water.getSound(sound), "Water sound action mismatch: " + sound);
        }
        h.assertTrue(type.getSound(SoundActions.BUCKET_FILL) == SoundEvents.BUCKET_FILL,
                "Industrial Waste bucket fill must use Vanilla Water sound");
        h.assertTrue(type.getSound(SoundActions.BUCKET_EMPTY) == SoundEvents.BUCKET_EMPTY,
                "Industrial Waste bucket empty must use Vanilla Water sound");
        h.assertTrue(waste.getTickDelay(h.getLevel()) == Fluids.WATER.getTickDelay(h.getLevel()), "Water tick delay mismatch");
        h.assertTrue(!waste.canConvertToSource(waste.defaultFluidState(), h.getLevel(), eyeBase)
                && !type.canConvertToSource(new FluidStack(waste, 1000)), "Source conversion must be disabled");
        h.succeed();
    }

    @GameTest(template = "waste_empty", timeoutTicks = 80)
    public static void fluidFlowsDownward(GameTestHelper h) {
        basin(h, 3, 3);
        h.setBlock(3, 0, 3, Blocks.STONE);
        h.setBlock(3, 1, 3, Blocks.AIR);
        placeBucket(h, 3, 3);
        h.runAtTickTime(40, () -> {
            var below = h.getLevel().getFluidState(h.absolutePos(new BlockPos(3, 1, 3)));
            h.assertTrue(below.getType() == AflFluids.FLOWING_INDUSTRIAL_WASTE.get()
                    && !below.isSource() && below.getValue(FlowingFluid.FALLING), "Downward flow not created");
            h.succeed();
        });
    }

    @GameTest(template = "waste_empty", timeoutTicks = 180)
    public static void twoSourcesDoNotFillGap(GameTestHelper h) {
        basin(h, 3, 1);
        placeBucket(h, 2, 2);
        placeBucket(h, 4, 2);
        h.runAtTickTime(80, () -> {
            h.assertTrue(sources(h, 3, 1) == 2, "S _ S generated a third source");
            h.assertTrue(!fluid(h, 3, 2).isSource() && !fluid(h, 3, 2).isEmpty(), "Gap must be flowing");
            scoop(h, 2, 2);
            h.assertTrue(sources(h, 3, 1) == 1, "First pickup must reduce count to one");
            scoop(h, 4, 2);
            h.assertTrue(sources(h, 3, 1) == 0, "Second pickup must reduce count to zero");
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(sources(h, 3, 1) == 0, "Source regenerated");
            h.succeed();
        });
    }

    @GameTest(template = "waste_empty", timeoutTicks = 160)
    public static void diagonalTwoByTwoStaysFinite(GameTestHelper h) {
        basin(h, 2, 2);
        placeBucket(h, 2, 2);
        placeBucket(h, 3, 3);
        h.runAtTickTime(100, () -> {
            h.assertTrue(sources(h, 2, 2) == 2, "2x2 layout generated extra sources");
            h.assertTrue(!fluid(h, 2, 3).isSource() && !fluid(h, 3, 2).isSource(), "Diagonal gaps became sources");
            scoop(h, 2, 2);
            scoop(h, 3, 3);
        });
        h.runAtTickTime(140, () -> {
            h.assertTrue(sources(h, 2, 2) == 0, "2x2 sources regenerated");
            h.succeed();
        });
    }

    @GameTest(template = "waste_empty", timeoutTicks = 100)
    public static void bucketTankPipeAndReactorStorage(GameTestHelper h) {
        var sourcePos = new BlockPos(3, 3, 3);
        var targetPos = new BlockPos(6, 1, 3);
        h.setBlock(sourcePos, AflBlocks.FLUID_TANK.get());
        h.setBlock(targetPos, AflBlocks.FLUID_TANK.get());
        for (int x = 3; x <= 6; x++) h.setBlock(x, 2, 3, AflBlocks.FLUID_PIPE.get());
        h.setBlock(9, 1, 3, AflBlocks.CHEMICAL_REACTOR.get());
        var source = (FluidTankBlockEntity) h.getBlockEntity(sourcePos);
        var target = (FluidTankBlockEntity) h.getBlockEntity(targetPos);
        var player = h.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AflItems.INDUSTRIAL_WASTE_BUCKET.get()));
        h.assertTrue(source.interactWithFluidContainer(player, InteractionHand.MAIN_HAND), "Bucket -> tank failed");
        h.assertTrue(source.getFluidAmount() == 1000, "Tank did not receive 1000 mB");
        h.assertTrue(player.getMainHandItem().is(Items.BUCKET), "Survival tank filling must return empty bucket");
        var reactor = (ChemicalReactorBlockEntity) h.getBlockEntity(new BlockPos(9, 1, 3));
        h.assertTrue(reactor.restoreWasteFluid(new FluidStack(AflFluids.INDUSTRIAL_WASTE.get(), 1000)) == 1000,
                "Reactor waste storage rejected fluid");
        h.assertTrue(reactor.getWasteFluid().getFluid() == AflFluids.INDUSTRIAL_WASTE.get(), "Wrong reactor waste fluid");
        final int[] previous = {0};
        for (int tick = 1; tick <= 50; tick++) {
            h.runAtTickTime(tick, () -> {
                int received = target.getFluidAmount();
                h.assertTrue(received - previous[0] >= 0 && received - previous[0] <= 25, "Exceeded 25 mB/t");
                h.assertTrue(source.getFluidAmount() + received == 1000, "Pipe duplicated/lost fluid");
                previous[0] = received;
            });
        }
        h.runAtTickTime(60, () -> {
            h.assertTrue(target.getFluidAmount() == 1000 && source.getFluidAmount() == 0, "Pipe transfer incomplete");
            h.succeed();
        });
    }
}
