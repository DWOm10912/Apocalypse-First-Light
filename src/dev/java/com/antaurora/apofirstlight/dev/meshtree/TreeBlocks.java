package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh Trees (docs/rendering/mesh_trees_p0a.md C, mesh_trees_p0b.md C/D): the development-only cell blocks of the
 * sample trees. Registered only outside production (the dev package, assets and tags are dropped from the jar). No items,
 * no drops, no ticking, no block entities: a tree is its root plus plain cells, and every cell's geometry and collision
 * follow from the root's state (species, variant, facing) and the cell's offset from it.
 * <ul>
 * <li>{@code dev_tree_root}: the anchor at the trunk base, the tree's identity; collides like its trunk.</li>
 * <li>{@code dev_tree_wood}: trunk and branch cells; a cell a thick branch passes collides with that branch's voxels
 * (1/8 block), looked up through the tree index on either side ({@link TreeIndex}), so client and server agree.</li>
 * <li>{@code dev_tree_foliage} / {@code dev_tree_canopy}: white oak crown cells (carrying leaf quads / volume only);
 * {@code dev_<species>_foliage} / {@code dev_<species>_canopy} the same for every other species (white pine since P0-B,
 * P3: red oak, shagbark hickory, red maple, eastern hemlock). Per species so a voxel view (Voxy, a map) gets the
 * species' colour and texture.</li>
 * </ul>
 * In the vanilla tags {@code minecraft:logs} (root, wood) and {@code minecraft:leaves} (crowns), so Terrain Smooth treats
 * them as tree and leaves without any change, and Voxy treats the crowns as leaves.
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TreeBlocks {
    public static final ResourceLocation ROOT_ID = new ResourceLocation("apocalypse_firstlight", "dev_tree_root"),
            WOOD_ID = new ResourceLocation("apocalypse_firstlight", "dev_tree_wood"),
            FOLIAGE_ID = new ResourceLocation("apocalypse_firstlight", "dev_tree_foliage"),
            CANOPY_ID = new ResourceLocation("apocalypse_firstlight", "dev_tree_canopy"),
            STUMP_ID = new ResourceLocation("apocalypse_firstlight", "dev_tree_stump");

    /** The species as a block state property. */
    public enum SpeciesProperty implements StringRepresentable {
        WHITE_OAK(Species.WHITE_OAK), WHITE_PINE(Species.WHITE_PINE), RED_OAK(Species.RED_OAK), SHAGBARK_HICKORY(Species.SHAGBARK_HICKORY),
        RED_MAPLE(Species.RED_MAPLE), EASTERN_HEMLOCK(Species.EASTERN_HEMLOCK);
        final Species species;
        SpeciesProperty(Species species) { this.species = species; }
        @Override public String getSerializedName() { return species.id; }
        static SpeciesProperty of(Species s) { return values()[s.ordinal()]; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, TreeShape.VARIANTS - 1);
    public static final EnumProperty<SpeciesProperty> SPECIES = EnumProperty.create("species", SpeciesProperty.class);
    /**
     * P2 (docs/rendering/mesh_trees_p2_felling.md, D): the root's cut stage (0 untouched, 1..4 the notch deepens) and the
     * notch's direction (8 steps of 45 degrees, toward the first chopper). In the block state, so the notch is saved
     * with the chunk, reaches every client with the block, and survives level changes, reloads and rebuilds.
     */
    public static final IntegerProperty CHOP = IntegerProperty.create("chop", 0, FellingRules.STAGES);
    public static final IntegerProperty CUT = IntegerProperty.create("cut", 0, FellingRules.SIDES - 1);

    private static Root root;
    private static Wood wood;
    private static Stump stump;
    /** Crown blocks by species ordinal: carrying leaf quads, volume only. */
    private static Foliage[] carriers, volumes;

    private TreeBlocks() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (FMLEnvironment.production) return;
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            root = new Root();
            wood = new Wood();
            stump = new Stump();
            helper.register(ROOT_ID, root);
            helper.register(WOOD_ID, wood);
            carriers = new Foliage[Species.values().length];
            volumes = new Foliage[Species.values().length];
            for (Species s : Species.values()) {
                carriers[s.ordinal()] = new Foliage(s, true);
                volumes[s.ordinal()] = new Foliage(s, false);
                helper.register(crownId(s, true), carriers[s.ordinal()]);
                helper.register(crownId(s, false), volumes[s.ordinal()]);
            }
            helper.register(STUMP_ID, stump);
        });
    }

    public static boolean ready() { return root != null; }
    public static Root root() { return require(root); }
    public static Wood wood() { return require(wood); }
    public static Stump stump() { return require(stump); }

    /** A species' crown block id: white oak's from P0-A, every other species {@code dev_<id>_foliage} / {@code _canopy}. */
    public static ResourceLocation crownId(Species s, boolean carrier) {
        if (s == Species.WHITE_OAK) return carrier ? FOLIAGE_ID : CANOPY_ID;
        return new ResourceLocation("apocalypse_firstlight", "dev_" + s.id + (carrier ? "_foliage" : "_canopy"));
    }

    /** The crown block of a species: carrying leaf quads, or volume only. */
    public static Foliage crown(Species species, boolean carrier) {
        return require(require(carrier ? carriers : volumes)[species.ordinal()]);
    }

    public static Foliage[] crowns() {
        Foliage[] all = new Foliage[2 * require(carriers).length];
        for (int i = 0; i < carriers.length; i++) { all[2 * i] = carriers[i]; all[2 * i + 1] = volumes[i]; }
        return all;
    }

    private static <T> T require(T block) {
        if (block == null) throw new IllegalStateException("Mesh tree sample blocks are registered in development runs only");
        return block;
    }

    public static boolean isTree(BlockState state) {
        Block b = state.getBlock();
        return root != null && (b == root || b == wood || b instanceof Foliage);
    }

    /** Quarter turns of a root state's facing (south 0, west 1, north 2, east 3). */
    public static int turns(BlockState rootState) {
        return rootState.getValue(FACING).get2DDataValue();
    }

    public static Species species(BlockState rootState) {
        return rootState.getValue(SPECIES).species;
    }

    public static TreeShape.Layout layoutOf(BlockState rootState) {
        return TreeShape.layout(species(rootState), rootState.getValue(VARIANT), turns(rootState));
    }

    public static BlockState rootState(Species species, int variant, int turns) {
        return root().defaultBlockState().setValue(SPECIES, SpeciesProperty.of(species)).setValue(VARIANT, variant)
                .setValue(FACING, Direction.from2DDataValue(turns));
    }

    /** The stump a felled root leaves: the same species, variant and facing (its trunk below the cut). */
    public static BlockState stumpState(BlockState rootState) {
        return stump().defaultBlockState().setValue(SPECIES, rootState.getValue(SPECIES)).setValue(VARIANT, rootState.getValue(VARIANT))
                .setValue(FACING, rootState.getValue(FACING));
    }

    // ---------------------------------------------------------------------------------------------- collision

    private static final Map<TreeShape.Cell, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final Map<TreeShape.Cell, VoxelShape> OUTLINES = new ConcurrentHashMap<>();
    private static final ThreadLocal<ArrayList<TreeIndex.Root>> FOUND = ThreadLocal.withInitial(ArrayList::new);

    /** The collision of a wood cell: the voxels of every tree's thick branches there (usually one tree). */
    static VoxelShape collision(BlockGetter level, BlockPos pos, boolean outline) {
        if (root == null) return Shapes.empty();
        var found = FOUND.get();
        found.clear();
        TreeIndex.of(level).roots(pos.getX(), pos.getY(), pos.getZ(), found);
        VoxelShape shape = null;
        for (var r : found) {
            var cell = r.cellAt(pos.getX(), pos.getY(), pos.getZ());
            if (cell == null || cell.solid == null) continue;
            if (outline && !(cell.x == 0 && cell.z == 0 && cell.y <= 2)) continue;   // only the trunk base is picked
            VoxelShape s = (outline ? OUTLINES : SHAPES).computeIfAbsent(cell, outline ? TreeBlocks::outline : TreeBlocks::voxels);
            shape = shape == null ? s : Shapes.or(shape, s);
        }
        found.clear();
        return shape == null ? Shapes.empty() : shape;
    }

    /** A cell's collision voxels as boxes: rectangles per layer, stacked where consecutive layers repeat them. */
    private static VoxelShape voxels(TreeShape.Cell cell) {
        int n = TreeShape.SUB;
        var rects = new ArrayList<int[]>();                       // x0, x1, z0, z1, y0, y1 (voxel units, end exclusive)
        var open = new java.util.HashMap<Long, int[]>();
        for (int y = 0; y < n; y++) {
            boolean[] used = new boolean[n * n];
            var layer = new java.util.HashMap<Long, int[]>();
            for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
                if (used[z * n + x] || !cell.solid(x, y, z)) continue;
                int x1 = x;
                while (x1 < n && cell.solid(x1, y, z) && !used[z * n + x1]) x1++;
                int z1 = z + 1;
                outer:
                while (z1 < n) {
                    for (int xx = x; xx < x1; xx++) if (!cell.solid(xx, y, z1) || used[z1 * n + xx]) break outer;
                    z1++;
                }
                for (int zz = z; zz < z1; zz++) for (int xx = x; xx < x1; xx++) used[zz * n + xx] = true;
                long key = ((long) x << 24) | ((long) x1 << 16) | ((long) z << 8) | z1;
                int[] prev = open.get(key);
                if (prev != null && prev[5] == y) { prev[5] = y + 1; layer.put(key, prev); }
                else { int[] r = {x, x1, z, z1, y, y + 1}; rects.add(r); layer.put(key, r); }
            }
            open = layer;
        }
        VoxelShape s = Shapes.empty();
        for (int[] r : rects)
            s = Shapes.joinUnoptimized(s, Shapes.box(r[0] / (double) n, r[4] / (double) n, r[2] / (double) n, r[1] / (double) n, r[5] / (double) n, r[3] / (double) n), BooleanOp.OR);
        return s.optimize();
    }

    /** The trunk base's outline: the box around its voxels (the voxels' own edges would draw a staircase). */
    private static VoxelShape outline(TreeShape.Cell cell) {
        int n = TreeShape.SUB, x0 = n, y0 = n, z0 = n, x1 = 0, y1 = 0, z1 = 0;
        for (int y = 0; y < n; y++) for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            if (!cell.solid(x, y, z)) continue;
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
            x1 = Math.max(x1, x + 1); y1 = Math.max(y1, y + 1); z1 = Math.max(z1, z + 1);
        }
        return x1 <= x0 ? Shapes.empty() : Shapes.box(x0 / (double) n, y0 / (double) n, z0 / (double) n, x1 / (double) n, y1 / (double) n, z1 / (double) n);
    }

    private static BlockBehaviour.Properties woody() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().dynamicShape()
                .sound(SoundType.WOOD).isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** The anchor: tree identity and trunk base. On the server it keeps the server's index and tells the clients. */
    public static final class Root extends Block {
        Root() {
            super(woody());
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(VARIANT, 0).setValue(SPECIES, SpeciesProperty.WHITE_OAK)
                    .setValue(CHOP, 0).setValue(CUT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, VARIANT, SPECIES, CHOP, CUT);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos, false);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos, true);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        // light never asks the tree index (the light engine runs on its own threads, where the side is unknown):
        // trunks and branches let sky light through and occlude nothing
        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            // mob paths (P1, docs/rendering/mesh_trees_p1_forest.md G): blocked where the cell collides. The voxels are no
            // full block, so vanilla would call the cell open and walkers would run into the trunk and hop against it.
            // Crown cells stay open (no collision, no LeavesBlock, so not the LEAVES path type either).
            return collision(level, pos, false).isEmpty() && super.isPathfindable(state, level, pos, type);
        }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
            super.onPlace(state, level, pos, old, moving);
            if (level instanceof ServerLevel server && !old.equals(state)) TreeCommands.rootChanged(server, pos, state);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
            super.onRemove(state, level, pos, now, moving);
            if (level instanceof ServerLevel server && now.getBlock() != this) TreeCommands.rootChanged(server, pos, now);
        }
    }

    /** Trunk and branch cells. */
    public static final class Wood extends Block {
        Wood() {
            super(woody());
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos, false);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return collision(level, pos, true);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        // light never asks the tree index (the light engine runs on its own threads, where the side is unknown):
        // trunks and branches let sky light through and occlude nothing
        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            // mob paths (P1, docs/rendering/mesh_trees_p1_forest.md G): blocked where the cell collides. The voxels are no
            // full block, so vanilla would call the cell open and walkers would run into the trunk and hop against it.
            // Crown cells stay open (no collision, no LeavesBlock, so not the LEAVES path type either).
            return collision(level, pos, false).isEmpty() && super.isPathfindable(state, level, pos, type);
        }
    }

    /**
     * P2 (docs/rendering/mesh_trees_p2_felling.md, J): what a felled tree leaves at its root. Its trunk below the cut (the
     * cut height follows the root position, FellingRules#cutHeight) with an end-grain top; it collides with the trunk's
     * own voxels below the cut and needs no tree index (species, variant and facing are in its state). Mining (block
     * audit): not breakable in survival (strength -1, like the tree cells; a stump tool comes later), no drops, no
     * mineable tag; creative players can remove it. Blocks mob paths; in the logs tag for Terrain Smooth.
     */
    public static final class Stump extends Block {
        Stump() {
            super(woody());
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(VARIANT, 0).setValue(SPECIES, SpeciesProperty.WHITE_OAK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, VARIANT, SPECIES);
        }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return stumpShape(state, pos, false);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return stumpShape(state, pos, true);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 0; }

        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }
    }

    private static final Map<Integer, VoxelShape> STUMP_SHAPES = new ConcurrentHashMap<>();

    /** The stump's collision: the root cell's trunk voxels in the layers below the cut; the outline is their box. */
    static VoxelShape stumpShape(BlockState state, BlockPos pos, boolean outline) {
        float cut = FellingRules.cutHeight(species(state), pos.getX(), pos.getY(), pos.getZ());
        int layers = Math.max(1, Math.min(TreeShape.SUB, Math.round(cut * TreeShape.SUB)));
        int key = ((((species(state).ordinal() * TreeShape.VARIANTS + state.getValue(VARIANT)) * 4 + turns(state)) * 16 + layers) << 1) | (outline ? 1 : 0);
        return STUMP_SHAPES.computeIfAbsent(key, k -> {
            var cell = layoutOf(state).cell(0, 0, 0);
            if (cell == null || cell.solid == null) return Shapes.box(0.3, 0, 0.3, 0.7, layers / (double) TreeShape.SUB, 0.7);
            var cutCell = new TreeShape.Cell(0, 0, 0, TreeShape.WOOD);
            cutCell.solid = new long[TreeShape.SUB];
            for (int y = 0; y < layers; y++) cutCell.solid[y] = cell.solid[y];
            return outline ? outline(cutCell) : voxels(cutCell);
        });
    }

    /** Crown cells of a species: no collision, not targetable, they dim sky light by one like leaves. */
    public static final class Foliage extends Block {
        public final Species species;
        public final boolean carrier;

        Foliage(Species species, boolean carrier) {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(-1.0F, 3600000.0F).noCollission().noOcclusion()
                    .noLootTable().sound(SoundType.GRASS).isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false));
            this.species = species;
            this.carrier = carrier;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return 1; }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return false; }

        /** Only the volume proxy (Voxy, maps, {@code lod proxy}) uses culled faces: crown cubes hide the faces between them. */
        @Override
        public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
            return adjacent.getBlock() instanceof Foliage || super.skipRendering(state, adjacent, direction);
        }
    }

    /** The block state for a layout cell (the root cell is the root state). */
    static BlockState stateFor(TreeShape.Cell cell, BlockState rootState) {
        if (cell.x == 0 && cell.y == 0 && cell.z == 0) return rootState;
        if (cell.kind == TreeShape.WOOD) return wood().defaultBlockState();
        return crown(species(rootState), cell.carries()).defaultBlockState();
    }
}
