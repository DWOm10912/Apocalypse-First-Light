package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.PlugSocketHost;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.fluid.FuelPourTarget;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Portable diesel generator V1 (docs/machines/portable_diesel_generator_v1.md, tools/build-portable-diesel-generator-v1.mjs):
 * a 5 kW open-frame air-cooled diesel set with a recoil starter, one block, FACING its control panel (toward the player who
 * places it). No opened screen:
 * <ul>
 *   <li>right-click the T handle on the starter (stopped, standing on the starter's side within reach): grip it, the recoil
 *   QTE (client/PortableGeneratorPull: the view fixed, a timing bar, a press per pull; network/PortableGeneratorPullC2SPacket);</li>
 *   <li>right-click the red STOP knob on the injection pump: stop;</li>
 *   <li>right-click the tank's filler cap: open / close it (CAP); with it open a jerry can poured at the set fills the tank
 *   ({@link FuelPourTarget});</li>
 *   <li>right-click a breaker button while tripped: reset;</li>
 *   <li>the two NEMA 5-15R duplexes on the panel are sockets ({@link PlugSocketHost}): plugs of appliances and power strips go
 *   in as into a wall outlet (the game's one plug).</li>
 * </ul>
 * Regions in the structure frame (north-facing px: x from the west edge, y up, z 0 = the front face), from the generator's
 * FACTS; the mesh hit gives the point on the model, so small parts can be told apart.
 */
public class PortableDieselGeneratorBlock extends HorizontalDirectionalBlock implements EntityBlock, FuelPourTarget, PlugSocketHost {
    /** The filler cap open. */
    public static final BooleanProperty CAP = BooleanProperty.create("cap");
    // structure frame px (tools/build-portable-diesel-generator-v1.mjs FACTS)
    private static final double[] SHAPE = {0.632, 0, 3.056, 13.905, 10.784, 12.944};
    private static final double[] HANDLE = inflate(new double[]{13.222, 5.062, 6.095, 14.182, 6.102, 8.175}, 0.5);
    private static final double[] STOP = inflate(new double[]{8.64, 4.16, 4.784, 9.6, 5.12, 5.584}, 0.3);
    private static final double[] CAP_SHUT = inflate(new double[]{9.84, 10.16, 6.16, 11.6, 11.232, 8}, 0.2);
    /** The open cap stands up behind its hinge: the region reaches over it. */
    private static final double[] CAP_OPEN = {9.6, 10.0, 6.0, 11.9, 12.4, 9.8};
    private static final double[] BREAKERS = inflate(new double[]{3.469, 6.474, 4.352, 4.563, 6.742, 4.704}, 0.2);
    private static final double[] OUTLETS = inflate(new double[]{4.861, 4.304, 4.352, 6.339, 5.264, 4.704}, 0.15);
    /** The four socket faces' centres (duplex 0 top, bottom; duplex 1 top, bottom). */
    private static final Vec3[] SOCKETS = {new Vec3(6.08, 4.995, 4.576), new Vec3(6.08, 4.573, 4.576), new Vec3(5.12, 4.995, 4.576), new Vec3(5.12, 4.573, 4.576)};
    /** The T handle's grip at rest, the pull (the grip's travel at full pull), the rope guide on the starter. */
    public static final Vec3 HANDLE_REST = new Vec3(13.702, 5.542, 7.135), PULL = new Vec3(7.352, 4.558, -1.617), GUIDE = new Vec3(13.408, 5.36, 7.2);
    public static final Vec3 POUR_OPENING = new Vec3(10.72, 10.304, 7.04), PANEL_MIDDLE = new Vec3(5.6, 5.36, 4.672),
            ENGINE = new Vec3(10.56, 4.8, 8), EXHAUST = new Vec3(5.92, 5.28, 11.28);
    /** Pulling: the eye at most this far from the grip (blocks), on the starter's side of the set. */
    public static final double PULL_REACH = 2.2;
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(Block.box(SHAPE[0], SHAPE[1], SHAPE[2], SHAPE[3], SHAPE[4], SHAPE[5]));
    private static final float PITCH_SPREAD = 0.06F;

    private static double[] inflate(double[] b, double d) {
        return new double[]{b[0] - d, b[1] - d, b[2] - d, b[3] + d, b[4] + d, b[5] + d};
    }

    public PortableDieselGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CAP, false));
    }

    // ---- the structure frame ----

    public static Vec3 world(BlockPos pos, BlockState state, Vec3 structure) {
        return DieselGeneratorBlock.toWorld(structure, pos, state.getValue(FACING));
    }

    public static Vec3 local(BlockPos pos, BlockState state, Vec3 world) {
        return DieselGeneratorBlock.toStructure(world, pos, state.getValue(FACING));
    }

    /** A structure-frame direction (px) in the world (blocks). */
    public static Vec3 worldDirection(BlockState state, Vec3 d) {
        Vec3 a = world(BlockPos.ZERO, state, Vec3.ZERO), b = world(BlockPos.ZERO, state, d);
        return b.subtract(a);
    }

    /** The T handle's grip at pull value v (0 rest .. 1 fully out), world. */
    public static Vec3 handle(BlockPos pos, BlockState state, double v) {
        return world(pos, state, HANDLE_REST.add(PULL.scale(v)));
    }

    private static boolean in(Vec3 p, double[] b) {
        return p.x >= b[0] && p.x <= b[3] && p.y >= b[1] && p.y <= b[4] && p.z >= b[2] && p.z <= b[5];
    }

    /** What a click at {@code hit} does (also the hint's prompt), or null; carrying: a plug in the player's hand. */
    public enum Action { PULL, STOP, OPEN_CAP, CLOSE_CAP, POUR, RESET, PLUG_IN, UNPLUG }

    @Nullable
    public Action action(BlockGetter level, BlockPos pos, BlockState state, Vec3 hit, Player player, boolean carrying) {
        if (!(level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator)) return null;
        Vec3 p = local(pos, state, hit);
        boolean open = state.getValue(CAP);
        var held = player.getMainHandItem();
        if (open && (held.getItem() instanceof FuelCanItem can && can.pours() || held.getItem() instanceof com.antaurora.apofirstlight.item.CreativeFuelBarrelItem))
            return in(p, CAP_OPEN) ? Action.POUR : null;
        if (in(p, open ? CAP_OPEN : CAP_SHUT)) return open ? Action.CLOSE_CAP : Action.OPEN_CAP;
        if (!held.isEmpty()) return null;
        if (in(p, OUTLETS)) {
            if (carrying) return Action.PLUG_IN;
            return generator.socketUsed(aimedSocket(pos, state, hit)) ? Action.UNPLUG : null;
        }
        if (in(p, BREAKERS)) return generator.tripped() ? Action.RESET : null;
        if (in(p, STOP)) return generator.running() ? Action.STOP : null;
        if (in(p, HANDLE)) return !generator.running() && generator.gripper() < 0 && canReach(pos, state, player) ? Action.PULL : null;
        return null;
    }

    /** The eye near enough to the grip, not across the set from the starter. */
    public static boolean canReach(BlockPos pos, BlockState state, Player player) {
        Vec3 grip = handle(pos, state, 0), eye = player.getEyePosition();
        if (eye.distanceTo(grip) > PULL_REACH) return false;
        Vec3 pull = worldDirection(state, PULL), toPlayer = eye.subtract(grip);
        return toPlayer.x * pull.x + toPlayer.z * pull.z > -0.15;
    }

    /** The prompt anchor for an action (world). */
    public Vec3 anchor(BlockPos pos, BlockState state, Action action, Vec3 hit) {
        return switch (action) {
            case PULL -> handle(pos, state, 0).add(0, 0.12, 0);
            case OPEN_CAP, CLOSE_CAP, POUR -> world(pos, state, POUR_OPENING.add(0, 2.2, 0));
            default -> hit;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        Action action = action(level, pos, state, hit.getLocation(), player, PowerPlugs.carried(player) != null);
        if (action == null || action == Action.POUR) return InteractionResult.PASS;
        if (action == Action.PLUG_IN || action == Action.UNPLUG) return PowerPlugs.useSocketHost(level, pos, player, hit);
        if (!(level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator)) return InteractionResult.PASS;
        switch (action) {
            case PULL -> {
                if (level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.antaurora.apofirstlight.client.PortableGeneratorPull.enter(pos));
                else if (!generator.grip(player)) return InteractionResult.CONSUME;
            }
            case STOP -> { if (!level.isClientSide) generator.stop(); }
            case RESET -> { if (!level.isClientSide) generator.resetBreakers(); }
            case OPEN_CAP, CLOSE_CAP -> {
                boolean open = action == Action.OPEN_CAP;
                level.setBlock(pos, state.setValue(CAP, open), UPDATE_CLIENTS | UPDATE_NEIGHBORS);
                Vec3 at = world(pos, state, POUR_OPENING);
                level.playSound(player, at.x, at.y, at.z, open ? AflSounds.DISTRIBUTION_PANEL_OPEN.get() : AflSounds.DISTRIBUTION_PANEL_CLOSE.get(),
                        SoundSource.BLOCKS, 0.5F, 1.45F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
                level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            }
            default -> {}
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- fuel: the tank takes a can's pour while its cap is open ----

    @Override
    public boolean takesPour(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(this) && state.getValue(CAP);
    }

    @Override
    public Vec3 pourOpening(BlockGetter level, BlockPos pos, BlockState state) {
        return world(pos, state, POUR_OPENING);
    }

    @Override
    @Nullable
    public IFluidHandler pourHandler(ServerLevel level, BlockPos pos, BlockState state) {
        return takesPour(level, pos, state) && level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator ? generator.fuelTank() : null;
    }

    @Override
    public String refusal() {
        return "diesel_only";
    }

    // ---- sockets: the panel's two NEMA 5-15R duplexes ----

    @Override
    public int sockets(Level level, BlockPos pos, BlockState state) {
        return SOCKETS.length;
    }

    @Override
    public boolean socketUsed(Level level, BlockPos pos, int socket) {
        return level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator && generator.socketUsed(socket);
    }

    @Override
    public void setSocketUsed(Level level, BlockPos pos, int socket, boolean used) {
        if (level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator) generator.setSocketUsed(socket, used);
    }

    @Override
    public Vec3 socketPoint(BlockPos pos, BlockState state, int socket) {
        return world(pos, state, SOCKETS[Math.max(0, Math.min(SOCKETS.length - 1, socket))]);
    }

    @Override
    public Vec3 socketAxis(BlockState state, int socket) {
        return Vec3.atLowerCornerOf(state.getValue(FACING).getNormal());
    }

    @Override
    public Vec3 socketUp(BlockState state, int socket) {
        return new Vec3(0, 1, 0);
    }

    @Override
    public int aimedSocket(BlockPos pos, BlockState state, Vec3 hit) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < SOCKETS.length; i++) { double d = socketPoint(pos, state, i).distanceToSqr(hit); if (d < bestD) { bestD = d; best = i; } }
        return best;
    }

    @Override
    public boolean acceptsPlug(Level level, BlockPos pos, PlugCord cord) {
        return !pos.equals(cord.ownerPos());
    }

    @Override
    public int draw(Level level, BlockPos pos, int fe, boolean simulate) {
        return level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator ? generator.draw(fe, simulate) : 0;
    }

    @Override
    public boolean live(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity generator && generator.outputOn();
    }

    // ---- block ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;   // the resting parts in the chunk (AflStaticMeshModel), the rest by the block entity renderer
    }

    @Override
    @SuppressWarnings("deprecation")
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableDieselGeneratorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AflBlockEntities.PORTABLE_DIESEL_GENERATOR.get()) return null;
        return level.isClientSide
                ? (l, p, s, be) -> com.antaurora.apofirstlight.client.PortableGeneratorPull.tick((PortableDieselGeneratorBlockEntity) be)
                : (l, p, s, be) -> ((PortableDieselGeneratorBlockEntity) be).serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CAP);
    }
}
