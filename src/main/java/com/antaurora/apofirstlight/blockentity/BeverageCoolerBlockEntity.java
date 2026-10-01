package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshHost;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshAnimationState;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The master cell's block entity: door transitions and the AFL Animated Block Mesh Runtime host (Beverage Cooler V2,
 * tools/build-beverage-cooler-v2.mjs; channels {@code left_open} / {@code right_open}). BlockState owns the durable door
 * poses and is committed {@link BeverageCoolerBlock#ANIMATION_TICKS} after a click; the server announces each started
 * transition with a block event, so clients start the swing at the click instead of at the commit.
 */
public final class BeverageCoolerBlockEntity extends BlockEntity implements AflAnimatedMeshHost {
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/beverage_cooler.json");
    private static final int EVENT_LEFT_DOOR = 1;
    private static final int EVENT_RIGHT_DOOR = 2;

    private final AflBlockMeshAnimationState meshAnimation = new AflBlockMeshAnimationState();
    private Boolean pendingLeft;
    private Boolean pendingRight;
    private long leftFinishTick;
    private long rightFinishTick;
    /** Client: door targets announced by the server and not yet committed to the block state. */
    private Boolean announcedLeft;
    private Boolean announcedRight;

    public BeverageCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.BEVERAGE_COOLER.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = getBlockState().getValue(BeverageCoolerBlock.FACING).getCounterClockWise();
        BlockPos other = worldPosition.relative(right);
        return new AABB(Math.min(worldPosition.getX(), other.getX()) - 1.5, worldPosition.getY(),
                Math.min(worldPosition.getZ(), other.getZ()) - 1.5,
                Math.max(worldPosition.getX(), other.getX()) + 2.5, worldPosition.getY() + 2.1,
                Math.max(worldPosition.getZ(), other.getZ()) + 2.5);
    }

    public boolean startDoor(boolean left, boolean targetOpen, long tick) {
        if (left ? pendingLeft != null : pendingRight != null) return false;
        if (left) {
            pendingLeft = targetOpen;
            leftFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        } else {
            pendingRight = targetOpen;
            rightFinishTick = tick + BeverageCoolerBlock.ANIMATION_TICKS;
        }
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), left ? EVENT_LEFT_DOOR : EVENT_RIGHT_DOOR, targetOpen ? 1 : 0);
        }
        return true;
    }

    public void completeDue(long tick) {
        if (!(getBlockState().getBlock() instanceof BeverageCoolerBlock block) || level == null) return;
        if (pendingLeft != null && tick >= leftFinishTick) {
            boolean target = pendingLeft;
            pendingLeft = null;
            block.commitDoor(level, worldPosition, true, target);
        }
        if (pendingRight != null && tick >= rightFinishTick) {
            boolean target = pendingRight;
            pendingRight = null;
            block.commitDoor(level, worldPosition, false, target);
        }
    }

    public long ticksUntilNextCompletion(long tick) {
        long left = pendingLeft == null ? Long.MAX_VALUE : Math.max(1, leftFinishTick - tick);
        long right = pendingRight == null ? Long.MAX_VALUE : Math.max(1, rightFinishTick - tick);
        return Math.min(left, right) == Long.MAX_VALUE ? 0 : Math.min(left, right);
    }

    /** Server: true broadcasts the door event to nearby clients; client: the announced swing starts now. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_LEFT_DOOR && id != EVENT_RIGHT_DOOR) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            if (id == EVENT_LEFT_DOOR) announcedLeft = param != 0;
            else announcedRight = param != 0;
            refreshMeshAnimationTargets();
        }
        return true;
    }

    private boolean doorTarget(boolean left) {
        Boolean announced = left ? announcedLeft : announcedRight;
        return announced != null ? announced
                : getBlockState().getValue(left ? BeverageCoolerBlock.LEFT_OPEN : BeverageCoolerBlock.RIGHT_OPEN);
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    public ResourceLocation meshProfile() {
        return MESH_PROFILE;
    }

    @Override
    public AflBlockMeshAnimationState meshAnimation() {
        return meshAnimation;
    }

    @Override
    public Direction meshFacing() {
        return getBlockState().getValue(BeverageCoolerBlock.FACING);
    }

    @Override
    public void refreshMeshAnimationTargets() {
        AflAnimatedMeshHost.refreshTargets(level, MESH_PROFILE, meshAnimation,
                channel -> "left_open".equals(channel) ? doorTarget(true) : "right_open".equals(channel) && doorTarget(false));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshMeshAnimationTargets();
    }

    /** The committed state catches up with an announced swing: follow the block state again. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (announcedLeft != null && state.getValue(BeverageCoolerBlock.LEFT_OPEN) == announcedLeft) announcedLeft = null;
        if (announcedRight != null && state.getValue(BeverageCoolerBlock.RIGHT_OPEN) == announcedRight) announcedRight = null;
        refreshMeshAnimationTargets();
    }
}
