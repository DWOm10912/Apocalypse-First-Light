package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("afl_vending_tests")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class VendingMachineGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void template(net.minecraftforge.event.server.ServerStartingEvent e) throws Exception {
        if (!(e.getServer() instanceof GameTestServer)) return;
        var level=e.getServer().overworld();
        var tag=net.minecraft.nbt.TagParser.parseTag("{size:[16,8,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_vending_tests","empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    @GameTestGenerator public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("vending", "afl_vending_tests:integration", "afl_vending_tests:empty",200,0,true,VendingMachineGameTests::run));
    }
    private static Vec3 world(BlockPos p,Direction f,Vec3 v) {
        Vec3 local=switch(f) {case EAST->new Vec3(1-v.z,v.y,v.x);case SOUTH->new Vec3(1-v.x,v.y,1-v.z);case WEST->new Vec3(v.z,v.y,1-v.x);default->v;};
        return local.add(Vec3.atLowerCornerOf(p));
    }
    private static boolean place(net.minecraft.world.entity.player.Player player,BlockPos p) {
        var stack=player.getMainHandItem();
        var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,
                new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false));
        return ((BlockItem)AflItems.VENDING_MACHINE.get()).place(context).consumesAction();
    }
    private static void run(GameTestHelper h) {
        var l=h.getLevel();var b=(VendingMachineBlock)AflBlocks.VENDING_MACHINE.get();
        BlockPos p=h.absolutePos(new BlockPos(6,2,6));
        var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"vending_test"));player.setGameMode(GameType.SURVIVAL);
        for(Direction f:Direction.Plane.HORIZONTAL) {
            l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
            player.setYRot(f.getOpposite().toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.VENDING_MACHINE.get()));
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false));
            h.assertTrue(((BlockItem)AflItems.VENDING_MACHINE.get()).place(context).consumesAction(),"placement "+f);
            h.assertTrue(l.getBlockState(p).getValue(VendingMachineBlock.FACING)==f,"facing "+f);
            var be=(VendingMachineBlockEntity)l.getBlockEntity(p);
            h.assertTrue(be!=null&&l.getBlockEntity(p.above())==null,"lower-only BE");
            h.assertTrue(!be.put(0,new ItemStack(Items.APPLE))&&be.take(0).isEmpty(),"intact storage locked");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.CROWBAR.get()));
            Vec3 eye=world(p,f,new Vec3(.55,1.2,-2));player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);
            Vec3 point=world(p,f,new Vec3(.55,1.2,.18/16));
            var hit=new BlockHitResult(point,f,p.above(),false);
            h.assertTrue(VendingMachineBlock.frontPoint(l.getBlockState(p.above()),p.above(),eye,new BlockHitResult(point,f.getOpposite(),p.above(),false))==null,"reject back face");
            b.use(l.getBlockState(p.above()),l,p.above(),player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(l.getBlockState(p).getValue(VendingMachineBlock.BROKEN)&&l.getBlockState(p.above()).getValue(VendingMachineBlock.BROKEN),"both broken");
            h.assertTrue(l.getBlockEntity(p)==be,"state retains BE");
            for(int slot=0;slot<VendingMachineBlockEntity.SIZE;slot++) {
                h.assertTrue(be.put(slot,new ItemStack(Items.APPLE,5)),"insert "+slot);
                h.assertTrue(be.getItem(slot).getCount()==1,"single item");
                Vec3 front=new Vec3(VendingMachineBlockEntity.displayX(slot),VendingMachineBlockEntity.displayY(slot),.18/16);
                h.assertTrue(VendingMachineBlock.slot(front)==slot,"slot mapping "+slot);
                Vec3 target=world(p,f,front);BlockPos half=front.y>=1?p.above():p;
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                b.use(l.getBlockState(half),l,half,player,InteractionHand.MAIN_HAND,new BlockHitResult(target,f,half,false));
                h.assertTrue(be.getItem(slot).isEmpty(),"take via block "+slot);
            }
            be.put(3,new ItemStack(Items.DIAMOND));
            var saved=be.saveWithoutMetadata();var restored=new VendingMachineBlockEntity(p,l.getBlockState(p));restored.load(saved);
            h.assertTrue(restored.getItem(3).is(Items.DIAMOND),"NBT roundtrip");
            var legacy=new net.minecraft.nbt.CompoundTag();
            var oldItems=net.minecraft.core.NonNullList.withSize(8,ItemStack.EMPTY);
            oldItems.set(0,new ItemStack(Items.APPLE));oldItems.set(1,new ItemStack(Items.DIAMOND));
            oldItems.set(2,new ItemStack(Items.STICK));oldItems.set(3,new ItemStack(Items.IRON_INGOT));
            net.minecraft.world.ContainerHelper.saveAllItems(legacy,oldItems);
            var migrated=new VendingMachineBlockEntity(p,l.getBlockState(p));migrated.load(legacy);
            h.assertTrue(migrated.getItem(0).is(Items.APPLE)&&migrated.getItem(1).isEmpty()
                    &&migrated.getItem(2).is(Items.DIAMOND)&&migrated.getItem(3).is(Items.STICK)
                    &&migrated.getItem(5).is(Items.IRON_INGOT),"V1 two-column NBT migration");
            h.assertTrue(!b.getCollisionShape(l.getBlockState(p),l,p,net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),"broken collision remains");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
            h.assertTrue(player.gameMode.destroyBlock(p.above()),"mine upper");
            h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"no orphan");
            var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(AflItems.VENDING_MACHINE.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"one machine");
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==1,"contents once");
            ItemStack droppedMachine=drops.stream().filter(e->e.getItem().is(AflItems.VENDING_MACHINE.get()))
                    .findFirst().orElseThrow().getItem().copy();
            h.assertTrue(com.antaurora.apofirstlight.item.VendingMachineBlockItem.hasBrokenGlass(droppedMachine),"broken tag on drop");
            drops.forEach(ItemEntity::discard);
            player.setItemInHand(InteractionHand.MAIN_HAND,droppedMachine);
            h.assertTrue(place(player,p),"replace broken machine "+f);
            h.assertTrue(l.getBlockState(p).getValue(VendingMachineBlock.BROKEN)
                    &&l.getBlockState(p.above()).getValue(VendingMachineBlock.BROKEN),"broken state persists after replacing");
            h.assertTrue(l.getBlockEntity(p) instanceof VendingMachineBlockEntity fresh
                    &&fresh.getItem(3).isEmpty(),"machine item does not silently retain removed contents");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
            player.gameMode.destroyBlock(p);
            l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).forEach(ItemEntity::discard);
        }
        for(Item tool:new Item[]{Items.AIR,Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE}) {
            var s=b.defaultBlockState();l.setBlock(p,s,2);l.setBlock(p.above(),s.setValue(VendingMachineBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));player.gameMode.destroyBlock(p);
            int count=l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).stream().filter(e->e.getItem().is(AflItems.VENDING_MACHINE.get())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(count==((tool==Items.DIAMOND_PICKAXE||tool==Items.NETHERITE_PICKAXE)?1:0),"mining tier "+tool);
            l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).forEach(ItemEntity::discard);
        }
        var s=b.defaultBlockState();
        l.setBlock(p,s,2);l.setBlock(p.above(),s.setValue(VendingMachineBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        player.setGameMode(GameType.CREATIVE);player.gameMode.destroyBlock(p.above());
        h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"creative cleanup");
        h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).isEmpty(),"creative no machine item");
        l.setBlock(p.above(),Blocks.STONE.defaultBlockState(),3);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.VENDING_MACHINE.get()));
        var blocked=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false));
        h.assertTrue(!((BlockItem)AflItems.VENDING_MACHINE.get()).place(blocked).consumesAction(),"blocked upper rejects placement");
        l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);
        l.setBlock(p,s,2);l.setBlock(p.above(),s.setValue(VendingMachineBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        l.setBlock(p.below(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"support removal cleanup");
        h.succeed();
    }
}
