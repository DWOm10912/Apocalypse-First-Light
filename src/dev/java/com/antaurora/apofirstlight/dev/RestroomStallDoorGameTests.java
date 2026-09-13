package com.antaurora.apofirstlight.dev;
import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.*;
import com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity;
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
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestroomStallDoorGameTests {
    @GameTestGenerator public static Collection<TestFunction> tests(){return List.of(new TestFunction("restroom_stall_door","apocalypse_firstlight:restroom_stall_door_v1",
            "apocalypse_firstlight:retail_shelf_empty",400,0,true,h->run(h,0)));}
    private static void reset(GameTestHelper h,BlockPos p){
        var l=h.getLevel();l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(5)).forEach(ItemEntity::discard);
        for(var q:BlockPos.betweenClosed(p.offset(-3,0,-3),p.offset(3,3,3)))l.setBlock(q,Blocks.AIR.defaultBlockState(),3);
        for(var q:BlockPos.betweenClosed(p.offset(-3,-1,-3),p.offset(3,-1,3)))l.setBlock(q,Blocks.STONE.defaultBlockState(),3);
    }
    private static void run(GameTestHelper h,int index){
        if(index==8){mining(h);h.succeed();ApocalypseFirstLight.LOGGER.info("[AFL STALL DOOR TEST] PASS eight facing/hinge transitions, spam, attachments/rebuild, shapes/rays, double halves, mining tiers");return;}
        var p=h.absolutePos(new BlockPos(7,2,7));reset(h,p);var l=h.getLevel();
        Direction facing=List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST).get(index/2);
        DoorHingeSide hinge=index%2==0?DoorHingeSide.LEFT:DoorHingeSide.RIGHT;
        var side=facing.getClockWise();var a=p.relative(side);var b=p.relative(side.getOpposite());
        l.setBlock(a,AflBlocks.RESTROOM_PARTITION.get().defaultBlockState(),3);l.setBlock(b,AflBlocks.RESTROOM_PARTITION.get().defaultBlockState(),3);
        var original=l.getBlockState(a).getShape(l,a).toAabbs();
        var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"stall_test"));player.setGameMode(GameType.SURVIVAL);
        player.setPos(p.getX()+3,p.getY(),p.getZ()+3);player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.RESTROOM_STALL_DOOR.get()));
        double offset=hinge==DoorHingeSide.LEFT?.3:-.3;
        Vec3 click=Vec3.atBottomCenterOf(p).add(side.getStepX()*offset,0,side.getStepZ()*offset);
        var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),new BlockHitResult(click,Direction.UP,p.below(),false));
        h.assertTrue(((BlockItem)AflItems.RESTROOM_STALL_DOOR.get()).place(ctx).consumesAction(),"BlockItem placement "+index);
        var s=l.getBlockState(p);h.assertTrue(s.is(AflBlocks.RESTROOM_STALL_DOOR.get()),"door exists");
        h.assertTrue(s.getValue(RestroomStallDoorBlock.FACING)==facing&&s.getValue(RestroomStallDoorBlock.HINGE)==hinge,"orientation "+index);
        h.assertTrue(l.getBlockEntity(p) instanceof RestroomStallDoorBlockEntity&&l.getBlockEntity(p.above())==null,"only lower BE");
        h.assertTrue(l.getBlockState(p.above()).getValue(RestroomStallDoorBlock.HALF)==DoubleBlockHalf.UPPER,"upper reserved");
        for(var q:List.of(a,b)){
            h.assertTrue(OfficeCubiclePartitionBlock.connectionMask(l.getBlockState(q))==0,"door excluded from graph");
            h.assertTrue(l.getBlockState(q).getValue(RestroomPartitionBlock.DOOR_SUPPORT)!=0,"support attached");
            h.assertTrue(!l.getBlockState(q).getShape(l,q).isEmpty(),"support shape");
        }
        var closed=s.getCollisionShape(l,p);h.assertTrue(Math.abs(closed.bounds().minY-3.5/16)<1e-6,"bottom gap");
        var door=(RestroomStallDoorBlock)s.getBlock();var hit=new BlockHitResult(Vec3.atCenterOf(p.above()),facing,p.above(),false);
        door.use(l.getBlockState(p.above()),l,p.above(),player,InteractionHand.MAIN_HAND,hit);
        var be=(RestroomStallDoorBlockEntity)l.getBlockEntity(p);
        h.assertTrue(!be.begin(),"spam ignored");
        h.assertTrue(!l.getBlockState(p).getValue(RestroomStallDoorBlock.OPEN),"no instant commit");
        h.runAfterDelay(6,()->h.assertTrue(!l.getBlockState(p).getValue(RestroomStallDoorBlock.OPEN),"closed through tick6"));
        h.runAfterDelay(8,()->{
            var opened=l.getBlockState(p);h.assertTrue(opened.getValue(RestroomStallDoorBlock.OPEN)&&l.getBlockState(p.above()).getValue(RestroomStallDoorBlock.OPEN),"commit both halves");
            var shape=RestroomStallDoorBlock.leafShape(opened);
            Vec3 center=Vec3.atBottomCenterOf(p);Vec3 from=center.add(facing.getStepX(),.5,facing.getStepZ()),to=center.add(-facing.getStepX(),.5,-facing.getStepZ());
            h.assertTrue(shape.clip(from,to,p)==null,"open aperture clear");
            double x=hinge==DoorHingeSide.LEFT?.85:.15;
            Vec3 localStart=new Vec3(.5,.8,-.2), localEnd=new Vec3(x,.8,-.2);
            Vec3 start=world(p,localStart,facing),end=world(p,localEnd,facing);
            var leafHit=RestroomDoorRaycast.find(l,player,start,end);
            h.assertTrue(leafHit!=null,"out of cell leaf hit "+index);
            h.assertTrue(leafHit.getBlockPos().equals(p),"ray retains exact door position "+index);
            door.use(l.getBlockState(leafHit.getBlockPos()),l,leafHit.getBlockPos(),player,InteractionHand.MAIN_HAND,leafHit);
            h.assertTrue(l.getBlockState(p).getValue(RestroomStallDoorBlock.OPEN),"closing retains open collision");
            h.runAfterDelay(8,()->{
                h.assertTrue(!l.getBlockState(p).getValue(RestroomStallDoorBlock.OPEN),"close committed");
                h.assertTrue(closed.toAabbs().equals(l.getBlockState(p).getCollisionShape(l,p).toAabbs()),"no shape drift");
                l.removeBlock(p.above(),false);
                h.assertTrue(l.getBlockState(p).isAir(),"removing upper clears lower");
                h.assertTrue(l.getBlockState(a).getValue(RestroomPartitionBlock.DOOR_SUPPORT)==0,"support restored");
                h.assertTrue(original.equals(l.getBlockState(a).getShape(l,a).toAabbs()),"ordinary shape restored exactly");
                run(h,index+1);
            });
        });
    }
    private static Vec3 world(BlockPos p,Vec3 v,Direction f){double x=v.x,z=v.z;int n=switch(f){case EAST->1;case SOUTH->2;case WEST->3;default->0;};for(int i=0;i<n;i++){double a=x;x=1-z;z=a;}return new Vec3(p.getX()+x,p.getY()+v.y,p.getZ()+z);}
    private static void mining(GameTestHelper h){
        var p=h.absolutePos(new BlockPos(7,2,7));var l=h.getLevel();var block=AflBlocks.RESTROOM_STALL_DOOR.get();
        reset(h,p);l.setBlock(p,block.defaultBlockState(),2);l.setBlock(p.above(),block.defaultBlockState().setValue(RestroomStallDoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        var builder=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"stall_builder"));builder.setPos(p.getX()+3,p.getY(),p.getZ()+3);
        builder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AflItems.RESTROOM_PARTITION.get()));
        var placement=new BlockPlaceContext(builder,InteractionHand.MAIN_HAND,builder.getMainHandItem(),new BlockHitResult(Vec3.atBottomCenterOf(p.east()),Direction.UP,p.east().below(),false));
        h.assertTrue(((BlockItem)AflItems.RESTROOM_PARTITION.get()).place(placement).consumesAction(),"partition placement after door");
        h.assertTrue(l.getBlockState(p.east()).getValue(RestroomPartitionBlock.DOOR_SUPPORT)==8,"partition placed after door attaches");
        l.removeBlock(p.east(),false);h.assertTrue(l.getBlockState(p).is(block),"partition removal retains door");
        l.removeBlock(p.below(),false);h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"floor removal cleans both halves");
        reset(h,p);l.setBlock(p,block.defaultBlockState(),2);l.setBlock(p.above(),block.defaultBlockState().setValue(RestroomStallDoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        var creative=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"stall_creative"));creative.setGameMode(GameType.CREATIVE);
        creative.gameMode.destroyBlock(p.above());
        h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).isEmpty(),"creative upper gives no drops");
        var tools=List.of(Items.AIR,Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE);
        for(int i=0;i<tools.size();i++)for(boolean upper:List.of(false,true)){
            reset(h,p);l.setBlock(p,block.defaultBlockState(),2);l.setBlock(p.above(),block.defaultBlockState().setValue(RestroomStallDoorBlock.HALF,DoubleBlockHalf.UPPER),3);
            var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"stall_mine"));player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tools.get(i)));
            h.assertTrue(player.gameMode.destroyBlock(upper?p.above():p),"mining succeeds");
            int drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3)).stream().filter(e->e.getItem().is(AflItems.RESTROOM_STALL_DOOR.get())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(drops==(i>=3?1:0),"single tool-gated drop "+i+" upper="+upper+" actual="+drops);
            h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"both halves removed");
        }
    }
}
