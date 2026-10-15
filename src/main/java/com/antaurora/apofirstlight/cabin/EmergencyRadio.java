package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.block.ModernOfficeDeskBlock;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.meshhit.MeshHitReachesDown;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Emergency Radio V1 (docs/models/emergency_radio_v1.md, 2026-10-14): the hand-crank emergency radio of the forest start
 * home B (docs/worldgen/forest_start_home_b_v1_plan.md, M2 F4), meshed from the user's paid cube model. A Mesh block
 * ({@link CabinPieces}, pieces cabin_mesh/radio/, tools/build-emergency-radio-v1.mjs) drawn facing north, 40 x 20 x 17.5
 * cm (the first draft's 20 cm the user found far too small in game); on a wooden table, kitchen counter or office desk
 * under it the pieces sink onto its top ({@code surface}), the whole body in that cell: the crosshair and bullets find it
 * from the side too ({@link MeshHitReachesDown}).
 * <ul>
 * <li>Right-click the front, top or back: the AM / FM / OFF slide switch ({@code on}; V1 knows FM and OFF). Right-click the
 * crank's end (the left end seen from the front) and hold: the crank turns a quarter each 4 ticks ({@code crank}) and winds
 * {@link #WIND} ticks of play in for stamina ({@code costs.crank}; winded, it winds less), up to {@link #CAPACITY}.</li>
 * <li>Switched on with charge it plays ({@code lit}: the dial and power lamp glow, block light 2) and the charge runs down
 * a tick a tick; at none it goes dark, the switch stays on and the next winding lights it again.</li>
 * <li>No broadcast reception in V1 (user 2026-10-14): it plays nothing yet; the hiss, the broadcasts and their sounds come
 * with the reception round (Firefly takes). The switch and crank sounds are vanilla stand-ins.</li>
 * </ul>
 * Mining (AGENTS.md audit): a small household appliance, not industrial: any pickaxe, no tier, no correct-tool requirement
 * (it breaks by hand and drops itself, keeping its charge).
 */
public final class EmergencyRadio {
    public static BlockEntityType<EmergencyRadioBlockEntity> RADIO;
    /** Ticks of play: a full radio (20 minutes), one crank quarter (3 s: a minute of winding plays 15 minutes). */
    public static final int CAPACITY = 24000, WIND = 60;
    /** In the north frame: the crank's end begins here (the case runs x 0.3..0.7, the crank stands out to 0.74). */
    public static final double CRANK_X = 0.696;
    /**
     * Heard as far as the infected hear it (blocks; forest home sounds V1, 2026-10-14): the switch, the crank, and the static
     * while it plays (client BlockLoopSoundController; EmergencyRadioBlockEntity's machine noise every 2 s).
     */
    public static final double SWITCH_RADIUS = 2, CRANK_RADIUS = 3, STATIC_RADIUS = 12;

    private EmergencyRadio() {}

    /** Called from CabinBlocks#register: the block entity type (after the blocks). */
    static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            RADIO = BlockEntityType.Builder.of(EmergencyRadioBlockEntity::new, CabinBlocks.block("emergency_radio")).build(null);
            helper.register(CabinBlocks.id("emergency_radio"), RADIO);
        });
    }

    /** What it stands on: the floor of its cell (a full top under it), or a piece of furniture whose top is lower. */
    public enum Surface implements StringRepresentable {
        // NIGHTSTAND: Wood Furniture V2 (2026-10-15), the nightstand top at 0.66
        FLOOR(0), TABLE(0.24F), COUNTER(0.10F), DESK(2.5F / 16), NIGHTSTAND(1 - WoodFurnitureV2.Nightstand.TOP);

        /** How far the radio sinks into the cell under it (blocks). */
        public final float sink;

        Surface(float sink) { this.sink = sink; }

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /** The surface under pos, or null when nothing there holds a radio. */
    public static @Nullable Surface surface(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        Block b = level.getBlockState(below).getBlock();
        if (b instanceof WoodFurniture.Table) return Surface.TABLE;
        if (b instanceof WoodFurniture.Counter) return Surface.COUNTER;
        if (b instanceof WoodFurnitureV2.Nightstand) return Surface.NIGHTSTAND;
        if (b instanceof ModernOfficeDeskBlock) return Surface.DESK;
        return Block.canSupportCenter(level, below, Direction.UP) ? Surface.FLOOR : null;
    }

    /** What a right-click at {@code hit} does (also the prompt: client WorldInteractionHint). */
    public enum Action { NONE, ON, OFF, CRANK }

    /** The radio: facing = its front; {@code on} the switch, {@code lit} playing, {@code crank} the arm's quarter. */
    public static final class Radio extends BaseEntityBlock implements CabinPieces, MeshHitReachesDown {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty ON = BooleanProperty.create("on"), LIT = BlockStateProperties.LIT;
        public static final IntegerProperty CRANK = IntegerProperty.create("crank", 0, 3);
        public static final EnumProperty<Surface> SURFACE = EnumProperty.create("surface", Surface.class);
        private static final VoxelShape[][] SHAPES = new VoxelShape[Surface.values().length][4];

        static {
            // px, north frame, as the mesh: the case, the handle, the knobs' front, the crank's sweep
            double[][] boxes = {{4.8, 0, 6.6, 11.2, 3.2, 9.4}, {6.0, 3.2, 7.76, 10.8, 4.12, 8.24}, {5.2, 0.36, 6.08, 8.0, 1.56, 6.6}, {11.2, 0.46, 6.86, 11.8, 2.74, 9.14}};
            for (Surface s : Surface.values()) for (int r = 0; r < 4; r++) SHAPES[s.ordinal()][r] = CabinBlocks.turned(r, boxes).move(0, -s.sink, 0);
        }

        Radio(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(LIT, false).setValue(CRANK, 0)
                    .setValue(SURFACE, Surface.FLOOR));
        }

        /** Block light: the lit dial. */
        static int light(BlockState s) { return s.getValue(LIT) ? 2 : 0; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ON, LIT, CRANK, SURFACE); }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            Surface s = surface(context.getLevel(), context.getClickedPos());
            return s == null ? null : defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(SURFACE, s);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return surface(level, pos) != null; }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(BlockState state, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (d != Direction.DOWN) return state;
            Surface s = surface(level, pos);
            return s == null ? Blocks.AIR.defaultBlockState() : state.setValue(SURFACE, s);
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

        @Override
        @SuppressWarnings("deprecation")
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(SURFACE).ordinal()][CabinBlocks.turn(state.getValue(FACING))];
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EmergencyRadioBlockEntity(pos, state); }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != RADIO) return null;
            return level.isClientSide
                    ? (l, p, s, be) -> ((EmergencyRadioBlockEntity) be).clientTick(s)
                    : (l, p, s, be) -> ((EmergencyRadioBlockEntity) be).serverTick(s);
        }

        /** The right-click at {@code hit}: the crank at the crank's end, else the switch. */
        public static Action action(BlockState state, BlockPos pos, Vec3 hit) {
            if (BoxStove.frame(state.getValue(FACING), pos, hit)[0] >= CRANK_X) return Action.CRANK;
            return state.getValue(ON) ? Action.OFF : Action.ON;
        }

        /** Where the prompt stands (world): over the crank's hub, or over the switch. */
        public static Vec3 promptPoint(BlockState state, BlockPos pos, Action a) {
            float y = -state.getValue(SURFACE).sink;
            return a == Action.CRANK ? BoxStove.world(state.getValue(FACING), pos, 0.74, y + 0.10, 0.5)
                    : BoxStove.world(state.getValue(FACING), pos, 0.5, y + 0.06, 0.36);
        }

        @Override
        @SuppressWarnings("deprecation")
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof EmergencyRadioBlockEntity radio)) return InteractionResult.PASS;
            Action a = action(state, pos, hit.getLocation());
            if (level.isClientSide) return InteractionResult.SUCCESS;
            switch (a) {
                case ON, OFF -> {
                    BlockState next = state.setValue(ON, a == Action.ON);
                    level.setBlock(pos, next, 3);
                    radio.switched(next);
                    noisy(level, player, state, pos, a, a == Action.ON ? AflSounds.EMERGENCY_RADIO_SWITCH_ON.get() : AflSounds.EMERGENCY_RADIO_SWITCH_OFF.get(), SWITCH_RADIUS);
                }
                case CRANK -> {
                    level.setBlock(pos, state.cycle(CRANK), 2);
                    boolean winded = player instanceof ServerPlayer sp && PlayerStamina.winded(sp);
                    radio.wind(winded ? Math.round(WIND * (float) StaminaConfig.get().winded.digSpeed) : WIND);
                    if (player instanceof ServerPlayer sp) PlayerStamina.spend(sp, StaminaConfig.get().costs.crank, false);
                    noisy(level, player, state, pos, a, AflSounds.EMERGENCY_RADIO_CRANK.get(), CRANK_RADIUS);
                }
                default -> { return InteractionResult.PASS; }
            }
            return InteractionResult.CONSUME;
        }

        /** At the switch or the crank, heard as far as its noise (the crank: one of eight 0.2 s ratchet slices a quarter). */
        private static void noisy(Level level, Player player, BlockState state, BlockPos pos, Action a, net.minecraft.sounds.SoundEvent sound, double radius) {
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(server, player, promptPoint(state, pos, a), sound, radius,
                        0.98F + level.random.nextFloat() * 0.04F, com.antaurora.apofirstlight.noise.NoiseType.INTERACTION, ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        }

        @Override
        public int cabinFlags(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        public void cabinPieces(BlockState state, int flags, Sink sink) {
            int r = CabinBlocks.turn(state.getValue(FACING));
            float dy = -state.getValue(SURFACE).sink;
            sink.add("radio/body", r, dy);
            sink.add(state.getValue(LIT) ? "radio/dial_lit" : "radio/dial_off", r, dy);
            sink.add(state.getValue(ON) ? "radio/slider_fm" : "radio/slider_off", r, dy);
            sink.add("radio/crank_" + state.getValue(CRANK), r, dy);
        }
    }
}
