package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Two reserved cells; only the lower cell owns the animation and drop. */
public final class RestroomStallDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final int ANIMATION_TICKS = 7;
    private static final ThreadLocal<Boolean> PLAYER_REMOVAL = ThreadLocal.withInitial(()->false);
    private static final Map<Direction,VoxelShape>[][] SHAPES = shapes();

    public RestroomStallDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false)
                .setValue(HINGE,DoorHingeSide.LEFT).setValue(HALF,DoubleBlockHalf.LOWER));
    }
    public static BlockPos master(BlockPos pos, BlockState state) {
        return state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
    public static boolean supports(BlockState state, Direction towardDoor) {
        return state.getBlock() instanceof RestroomStallDoorBlock
                && state.getValue(HALF)==DoubleBlockHalf.LOWER
                && state.getValue(FACING).getAxis()!=towardDoor.getAxis();
    }
    private static boolean partition(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RestroomPartitionBlock;
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var level=ctx.getLevel(); var pos=ctx.getClickedPos();
        if(pos.getY()+1>=level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(pos.above())
                || !level.getBlockState(pos.above()).canBeReplaced(BlockPlaceContext.at(ctx,pos.above(),Direction.UP))
                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return null;
        var player=ctx.getPlayer();
        if(player!=null && (!level.mayInteract(player,pos.above()) || !player.mayUseItemAt(pos.above(),Direction.UP,ctx.getItemInHand()))) return null;
        Direction facing=ctx.getHorizontalDirection().getOpposite();
        int best=-1;
        for(Direction candidate:List.of(facing,facing.getClockWise(),facing.getCounterClockWise(),facing.getOpposite())) {
            Direction side=candidate.getClockWise();
            int score=(partition(level,pos.relative(side))?1:0)+(partition(level,pos.relative(side.getOpposite()))?1:0);
            if(score>best){best=score;facing=candidate;}
        }
        // Source front looks along +Z; its high-X hinge is visual LEFT.
        Direction left=facing.getClockWise();
        boolean l=partition(level,pos.relative(left)), r=partition(level,pos.relative(left.getOpposite()));
        double click=(ctx.getClickLocation().x-pos.getX()-.5)*left.getStepX()
                +(ctx.getClickLocation().z-pos.getZ()-.5)*left.getStepZ();
        DoorHingeSide hinge=l!=r?(l?DoorHingeSide.LEFT:DoorHingeSide.RIGHT):(click>=0?DoorHingeSide.LEFT:DoorHingeSide.RIGHT);
        BlockState state=defaultBlockState().setValue(FACING,facing).setValue(HINGE,hinge);
        return state.canSurvive(level,pos) && level.isUnobstructed(state.setValue(HALF,DoubleBlockHalf.UPPER),pos.above(),CollisionContext.empty())?state:null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
        level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),UPDATE_ALL);
        level.updateNeighborsAt(pos,this);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(HALF)==DoubleBlockHalf.UPPER
                ? level.getBlockState(pos.below()).is(this) && level.getBlockState(pos.below()).getValue(HALF)==DoubleBlockHalf.LOWER
                : level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    @Override public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean upper=state.getValue(HALF)==DoubleBlockHalf.UPPER;
        if(dir==(upper?Direction.DOWN:Direction.UP)) {
            if(PLAYER_REMOVAL.get()) return state;
            if(!neighbor.is(this) || neighbor.getValue(HALF)==state.getValue(HALF)) return Blocks.AIR.defaultBlockState();
            return state.setValue(FACING,neighbor.getValue(FACING)).setValue(HINGE,neighbor.getValue(HINGE)).setValue(OPEN,neighbor.getValue(OPEN));
        }
        if(!upper && dir==Direction.DOWN && !state.canSurvive(level,pos)) {
            if(level instanceof ServerLevel server) server.destroyBlock(pos,true);
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos lower=master(pos,state);
        if(player.isSpectator() || !level.mayInteract(player,lower) || !player.mayUseItemAt(lower,hit.getDirection(),player.getItemInHand(hand))) return InteractionResult.FAIL;
        if(!level.isClientSide && level.getBlockEntity(lower) instanceof RestroomStallDoorBlockEntity door && door.begin()) {
            level.scheduleTick(lower,this,ANIMATION_TICKS);
            level.playSound(null,lower, state.getValue(OPEN)?net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE:net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS,.6F,1.15F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if(level.getBlockEntity(pos) instanceof RestroomStallDoorBlockEntity door) door.complete();
    }
    public void commit(Level level, BlockPos pos, boolean open) {
        var lower=level.getBlockState(pos);var upper=level.getBlockState(pos.above());
        if(!lower.is(this)||!upper.is(this)||lower.getValue(HALF)!=DoubleBlockHalf.LOWER||upper.getValue(HALF)!=DoubleBlockHalf.UPPER) return;
        level.setBlock(pos,lower.setValue(OPEN,open),UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE);
        level.setBlock(pos.above(),upper.setValue(OPEN,open),UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE);
        level.updateNeighborsAt(pos,this);level.updateNeighborsAt(pos.above(),this);
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide && state.getValue(HALF)==DoubleBlockHalf.UPPER && !player.isCreative() && player.hasCorrectToolForDrops(state))
            popResource(level,pos.below(),new ItemStack(this));
        // Remove the peer without updateOrDestroy synthesizing an unqualified lower-half drop.
        BlockPos peer=state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos.above();
        PLAYER_REMOVAL.set(true);
        try { if(level.getBlockState(peer).is(this))level.setBlock(peer,Blocks.AIR.defaultBlockState(),UPDATE_ALL); }
        finally { PLAYER_REMOVAL.remove(); }
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return state.getValue(HALF)==DoubleBlockHalf.LOWER?super.getDrops(state,builder):List.of();
    }
    @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override public BlockState mirror(BlockState s,Mirror m){return m==Mirror.NONE?s:rotate(s,m.getRotation(s.getValue(FACING))).cycle(HINGE);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos,BlockState s){return s.getValue(HALF)==DoubleBlockHalf.LOWER?new RestroomStallDoorBlockEntity(pos,s):null;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,HINGE,HALF);}
    public static VoxelShape leafShape(BlockState s){return SHAPES[(s.getValue(OPEN)?2:0)+(s.getValue(HINGE)==DoorHingeSide.RIGHT?1:0)][s.getValue(HALF)==DoubleBlockHalf.UPPER?1:0].get(s.getValue(FACING));}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return leafShape(s);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return leafShape(s);}
    @Override public VoxelShape getInteractionShape(BlockState s,BlockGetter l,BlockPos p){return leafShape(s);}
    @Override public VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p){return Shapes.empty();}
    @SuppressWarnings("unchecked") private static Map<Direction,VoxelShape>[][] shapes(){
        Map<Direction,VoxelShape>[][] result=new Map[4][2];
        for(int variant=0;variant<4;variant++)for(int half=0;half<2;half++){
            double y0=half==0?3.5:0,y1=half==0?16:13.5;
            VoxelShape shape=variant<2?box(1.5,y0,7.25,14.5,y1,8.75)
                    :variant==2?box(12.8,y0,-6.15,14.3,y1,6.85):box(1.7,y0,-6.15,3.2,y1,6.85);
            result[variant][half]=HorizontalShapeUtils.rotations(shape);
        }
        return result;
    }
}
