package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.ThermalGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflMenus;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("afl_thermal_model_tests")
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class ThermalGeneratorModelGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level=event.getServer().overworld();
        var tag=net.minecraft.nbt.TagParser.parseTag("{size:[12,6,12],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks=new net.minecraft.nbt.ListTag();
        for(int x=0;x<12;x++)for(int y=0;y<6;y++)for(int z=0;z<12;z++){
            var b=new net.minecraft.nbt.CompoundTag();var position=new net.minecraft.nbt.ListTag();
            for(int n:new int[]{x,y,z})position.add(net.minecraft.nbt.IntTag.valueOf(n));
            b.put("pos",position);b.putInt("state",0);blocks.add(b);
        }
        tag.put("blocks",blocks);
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_thermal_model_tests","empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return List.of(
                test("dynamic_states_light", ThermalDynamicGameTest::states),
                test("fluid_lighting", FluidLightingGameTest::lights),
                test("thermal_particle_profile", ThermalParticlesGameTest::profile),
                test("fluid_visual_hold", FluidVisualHoldGameTest::run),
                test("placement_shapes_menu", ThermalGeneratorModelGameTests::placementShapes),
                test("survival_drops", ThermalGeneratorModelGameTests::survivalDrops),
                test("energy_fuels", ThermalGeneratorBalanceGameTests::exactFuelEnergyAndFullBufferPause),
                test("fluid_energy_priority", ThermalFluidGameTests::energyPriority),
                test("fluid_persistence_jade_data", ThermalFluidGameTests::persistence),
                test("fluid_ports", ThermalFluidGameTests::ports),
                test("fluid_pipe_north", h -> ThermalFluidGameTests.pipes(h, Direction.NORTH)),
                test("fluid_pipe_south", h -> ThermalFluidGameTests.pipes(h, Direction.SOUTH)),
                test("fluid_pipe_east", h -> ThermalFluidGameTests.pipes(h, Direction.EAST)),
                test("fluid_pipe_west", h -> ThermalFluidGameTests.pipes(h, Direction.WEST)),
                test("reactor_horizontal", ReactorIntegrationGameTests::horizontalWaste),
                test("reactor_jade_saved", ReactorIntegrationGameTests::jadeDataAndSavedItemTooltips));
    }

    private static TestFunction test(String name, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("thermal_model", "afl_thermal_model_tests:"+name,
                "afl_thermal_model_tests:empty", 120, 0L, true, body);
    }

    private static VoxelShape rotate(VoxelShape shape) {
        final VoxelShape[] result={Shapes.empty()};
        shape.forAllBoxes((x0,y0,z0,x1,y1,z1)->result[0]=Shapes.or(result[0],Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));
        return result[0];
    }

    public static void placementShapes(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));
        var block=AflBlocks.THERMAL_GENERATOR.get();
        var north=block.defaultBlockState().getShape(level,pos);
        var expected=north;
        var expectedCollision=block.defaultBlockState().getCollisionShape(level,pos);
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"thermal_shape"));
            player.setGameMode(GameType.SURVIVAL);player.setYRot(facing.getOpposite().toYRot());
            player.setPos(pos.getX()+3,pos.getY(),pos.getZ()+3);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.THERMAL_GENERATOR.get(),2));
            var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),
                    new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false));
            h.assertTrue(((BlockItem)AflItems.THERMAL_GENERATOR.get()).place(ctx).consumesAction(),"placement");
            var state=level.getBlockState(pos);
            h.assertTrue(state.getValue(ThermalGeneratorBlock.FACING)==facing,"placement facing "+facing);
            h.assertTrue(player.getMainHandItem().getCount()==1,"one item consumed");
            var be=level.getBlockEntity(pos);
            h.assertTrue(be instanceof ThermalGeneratorBlockEntity,"existing BE");
            var generator=(ThermalGeneratorBlockEntity)be;
            h.assertTrue(generator.createMenu(1,player.getInventory(),player).getType()==AflMenus.THERMAL_GENERATOR.get(),"existing menu");
            for(boolean lit:new boolean[]{false,true}) {
                var s=state.setValue(ThermalGeneratorBlock.LIT,lit);level.setBlock(pos,s,3);
                h.assertTrue(level.getBlockEntity(pos)==be,"lit preserves BE");
                var shape=s.getShape(level,pos);var collision=s.getCollisionShape(level,pos);
                h.assertTrue(!Shapes.joinIsNotEmpty(shape,expected,BooleanOp.NOT_SAME),"rotated shape "+facing);
                h.assertTrue(!Shapes.joinIsNotEmpty(collision,expectedCollision,BooleanOp.NOT_SAME),"rotated detailed collision");
                h.assertTrue(Shapes.joinIsNotEmpty(shape,collision,BooleanOp.NOT_SAME),"selection is independent of collision");
                h.assertTrue(shape.toAabbs().size()<=2,"simple body and exhaust outline");
                h.assertTrue(!s.canOcclude(),"no full-cube occlusion");
                var bounds=shape.bounds();
                h.assertTrue(bounds.minX>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxZ<=1,"one-cell footprint");
                h.assertTrue(Math.abs(bounds.maxY-17.2/16)<1e-6,"exhaust height");
                double volume=collision.toAabbs().stream().mapToDouble(b->b.getXsize()*b.getYsize()*b.getZsize()).sum();
                h.assertTrue(volume<.95&&volume>.6,"simplified non-cube envelope");
                var restored=NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),NbtUtils.writeBlockState(s));
                h.assertTrue(restored.equals(s),"state save/load");
                h.assertTrue(s.rotate(Rotation.CLOCKWISE_90).getValue(ThermalGeneratorBlock.FACING)==facing.getClockWise(),"rotation");
            }
            expected=rotate(expected);
            expectedCollision=rotate(expectedCollision);
        }
        // Empty corner above roof must miss; actual exhaust stack must be selectable.
        h.assertTrue(north.clip(new Vec3(.05,2,.05),new Vec3(.05,1,.05),BlockPos.ZERO)==null,"roof air stays empty");
        h.assertTrue(north.clip(new Vec3(4.5/16,2,12.2/16),new Vec3(4.5/16,.9,12.2/16),BlockPos.ZERO)!=null,"exhaust selectable");
        h.succeed();
    }

    public static void survivalDrops(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));
        for(Item tool:new Item[]{Items.AIR,Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.GOLDEN_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE,Items.DIAMOND_AXE}) {
            level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).forEach(ItemEntity::discard);
            level.setBlock(pos,AflBlocks.THERMAL_GENERATOR.get().defaultBlockState(),3);
            var generator=(ThermalGeneratorBlockEntity)level.getBlockEntity(pos);
            generator.setItem(ThermalGeneratorBlockEntity.FUEL_SLOT,new ItemStack(Items.COAL,3));
            generator.restoreLiquid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 777));
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"thermal_mining"));
            player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
            h.assertTrue(player.gameMode.destroyBlock(pos),"survival destroy");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4));
            int machine=drops.stream().filter(e->e.getItem().is(AflItems.THERMAL_GENERATOR.get())).mapToInt(e->e.getItem().getCount()).sum();
            int fuel=drops.stream().filter(e->e.getItem().is(Items.COAL)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(machine==((tool==Items.DIAMOND_PICKAXE||tool==Items.NETHERITE_PICKAXE)?1:0),"tool tier "+tool);
            h.assertTrue(fuel==3,"fuel slot contents retained on removal");
            if (machine == 1) {
                var dropped = drops.stream().filter(e -> e.getItem().is(AflItems.THERMAL_GENERATOR.get())).findFirst().orElseThrow().getItem().copy();
                player.setYRot(0);player.setPos(pos.getX()+3,pos.getY(),pos.getZ()+3);
                player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
                level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
                var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,dropped,
                        new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false));
                h.assertTrue(((BlockItem)dropped.getItem()).place(ctx).consumesAction(),"re-place saved tank");
                var restored=(ThermalGeneratorBlockEntity)level.getBlockEntity(pos);
                h.assertTrue(restored.getLiquidAmount()==777,"survival drop/place preserves lava");
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            }
        }
        h.succeed();
    }
}
