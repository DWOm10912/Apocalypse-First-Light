package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.GunWorkbenchBlock;
import com.antaurora.apofirstlight.block.GunWorkbenchBlock.Part;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("afl_workbench_tests") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class GunWorkbenchGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void template(net.minecraftforge.event.server.ServerStartingEvent event) throws Exception {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var level=event.getServer().overworld();
        var tag=net.minecraft.nbt.TagParser.parseTag("{size:[16,10,16],entities:[],blocks:[],palette:[{Name:\"minecraft:air\"}]}");
        var blocks=new net.minecraft.nbt.ListTag();
        for(int x=0;x<16;x++) for(int y=0;y<10;y++) for(int z=0;z<16;z++) {
            var b=new net.minecraft.nbt.CompoundTag(); var pos=new net.minecraft.nbt.ListTag();
            for(int n:new int[]{x,y,z}) pos.add(net.minecraft.nbt.IntTag.valueOf(n));
            b.put("pos",pos);b.putInt("state",0);blocks.add(b);
        }
        tag.put("blocks",blocks);
        level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_workbench_tests","empty"))
                .load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    private static GunWorkbenchBlock block() { return AflBlocks.GUN_WORKBENCH.get(); }
    private static BlockPos root(GameTestHelper h) { return h.absolutePos(new BlockPos(4,2,4)); }
    private static ServerPlayer player(GameTestHelper h, GameType mode, Direction facing) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"workbench_test"));
        p.setGameMode(mode); p.setYRot(facing.getOpposite().toYRot());
        var r=root(h); p.setPos(r.getX()+5,r.getY(),r.getZ()+5); return p;
    }
    private static void clear(GameTestHelper h) {
        var r=root(h); var l=h.getLevel();
        l.getEntitiesOfClass(ItemEntity.class,new AABB(r).inflate(5)).forEach(ItemEntity::discard);
        for(var p:BlockPos.betweenClosed(r.offset(-2,0,-2),r.offset(2,2,2))) l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(var p:BlockPos.betweenClosed(r.offset(-2,-1,-2),r.offset(2,-1,2))) l.setBlock(p,Blocks.STONE.defaultBlockState(),3);
    }
    private static BlockPlaceContext context(GameTestHelper h, ServerPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.GUN_WORKBENCH.get(),2));
        var r=root(h);
        return new BlockPlaceContext(p,InteractionHand.MAIN_HAND,p.getMainHandItem(),
                new BlockHitResult(Vec3.atBottomCenterOf(r),Direction.UP,r.below(),false));
    }
    private static void place(GameTestHelper h, Direction d) {
        var p=player(h,GameType.SURVIVAL,d); var ctx=context(h,p);
        h.assertTrue(((BlockItem)AflItems.GUN_WORKBENCH.get()).place(ctx).consumesAction(),"place "+d);
        h.assertTrue(p.getMainHandItem().getCount()==1,"consume one item");
        for(var part:Part.values()) {
            var pos=GunWorkbenchBlock.partPosition(root(h),d,part); var s=h.getLevel().getBlockState(pos);
            h.assertTrue(s.equals(block().stateFor(d,part)),"part "+d+"/"+part);
            h.assertTrue(GunWorkbenchBlock.rootPosition(pos,s).equals(root(h)),"root resolution");
        }
    }
    private static void gone(GameTestHelper h, Direction d) {
        for(var part:Part.values()) h.assertTrue(h.getLevel().getBlockState(GunWorkbenchBlock.partPosition(root(h),d,part)).isAir(),"cleanup "+part);
    }
    private static int drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(root(h)).inflate(4)).stream()
                .filter(e->e.getItem().is(AflItems.GUN_WORKBENCH.get())).mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="empty")
    public static void workbenchFacingShapesAndPlacement(GameTestHelper h) {
        for(var d:Direction.Plane.HORIZONTAL) {
            clear(h); place(h,d);
            for(var part:Part.values()) {
                var p=GunWorkbenchBlock.partPosition(root(h),d,part); var s=h.getLevel().getBlockState(p);
                var shape=s.getCollisionShape(h.getLevel(),p); var bounds=shape.bounds();
                h.assertTrue(!shape.isEmpty() && bounds.minX>=0 && bounds.minY>=0 && bounds.minZ>=0
                        && bounds.maxX<=1 && bounds.maxY<=1 && bounds.maxZ<=1,"bounded collision");
                h.assertTrue(shape.toAabbs().stream().mapToDouble(b->b.getXsize()*b.getYsize()*b.getZsize()).sum()<.9,"not full cube");
                h.assertTrue(s.getLightEmission(h.getLevel(),p)==0 && !s.hasBlockEntity(),"no light or BE");
                h.assertTrue(s.getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.BLOCK,"no piston split");
                var saved=net.minecraft.nbt.NbtUtils.writeBlockState(s);
                h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved).equals(s),"state persistence");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void workbenchBlockedPlacement(GameTestHelper h) {
        for(var d:Direction.Plane.HORIZONTAL) for(var blocked:Part.values()) {
            clear(h); var obstacle=GunWorkbenchBlock.partPosition(root(h),d,blocked);
            h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
            var p=player(h,GameType.SURVIVAL,d); var ctx=context(h,p);
            // Explicit target context avoids vanilla treating an occupied root as an adjacent placement.
            ctx=BlockPlaceContext.at(ctx,root(h),Direction.UP);
            h.assertTrue(!block().canPlaceStructure(ctx,d),"blocked space "+d+blocked);
            h.assertTrue(!block().placeStructure(ctx,block().stateFor(d,Part.BASE)),"no partial placement");
            h.assertTrue(p.getMainHandItem().getCount()==2,"no item loss");
            for(var part:Part.values()) h.assertTrue(!h.getLevel().getBlockState(GunWorkbenchBlock.partPosition(root(h),d,part)).is(block()),"no orphan on failure");
        }
        clear(h); var d=Direction.NORTH;
        h.getLevel().setBlock(root(h).east().below(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!block().canPlaceStructure(context(h,player(h,GameType.SURVIVAL,d)),d),"both legs need support");
        clear(h); h.getLevel().setBlock(root(h).above(),Blocks.WATER.defaultBlockState(),3);
        h.assertTrue(!block().canPlaceStructure(context(h,player(h,GameType.SURVIVAL,d)),d),"no water displacement");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void workbenchSurvivalMiningTiers(GameTestHelper h) {
        Item[] tools={Items.AIR,Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.GOLDEN_PICKAXE,
                Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE,Items.DIAMOND_AXE};
        for(var part:Part.values()) for(var tool:tools) {
            clear(h); place(h,Direction.NORTH);
            var p=player(h,GameType.SURVIVAL,Direction.NORTH); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
            var pos=GunWorkbenchBlock.partPosition(root(h),Direction.NORTH,part);
            h.assertTrue(p.gameMode.destroyBlock(pos),"survival destroy "+part+tool);
            gone(h,Direction.NORTH);
            int expected=tool==Items.DIAMOND_PICKAXE||tool==Items.NETHERITE_PICKAXE?1:0;
            h.assertTrue(drops(h)==expected,"tier drop "+tool+" "+part+": "+drops(h));
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void workbenchCreativeCommandExplosion(GameTestHelper h) {
        for(var d:Direction.Plane.HORIZONTAL) for(var part:Part.values()) {
            clear(h); place(h,d);
            var p=player(h,GameType.CREATIVE,d);
            h.assertTrue(p.gameMode.destroyBlock(GunWorkbenchBlock.partPosition(root(h),d,part)),"creative destroy");
            gone(h,d); h.assertTrue(drops(h)==0,"creative no drop");
            clear(h); place(h,d);
            h.getLevel().setBlock(GunWorkbenchBlock.partPosition(root(h),d,part),Blocks.AIR.defaultBlockState(),3);
            gone(h,d); h.assertTrue(drops(h)==0,"command removal no drop");
            clear(h); place(h,d);
            h.getLevel().destroyBlock(GunWorkbenchBlock.partPosition(root(h),d,part),true);
            gone(h,d); h.assertTrue(drops(h)==1,"single ordinary drop");
        }
        clear(h); place(h,Direction.NORTH);
        var r=root(h);
        h.getLevel().explode(null,r.getX()+1,r.getY()+1,r.getZ()+.5,4,Level.ExplosionInteraction.TNT);
        gone(h,Direction.NORTH); h.assertTrue(drops(h)<=1,"explosion no duplicate");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void workbenchOrphanRepair(GameTestHelper h) {
        clear(h); place(h,Direction.NORTH);
        // A loaded orphan left by a missing neighboring chunk is repaired without another item.
        h.getLevel().setBlock(root(h),Blocks.AIR.defaultBlockState(),3);
        h.getLevel().setBlock(root(h).above(),block().stateFor(Direction.NORTH,Part.UPPER),3);
        h.runAfterDelay(5,()-> {gone(h,Direction.NORTH);h.assertTrue(drops(h)==0,"orphan no drop");h.succeed();});
    }
}
