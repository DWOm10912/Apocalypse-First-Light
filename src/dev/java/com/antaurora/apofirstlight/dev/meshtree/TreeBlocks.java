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
 * {@code dev_white_pine_foliage} / {@code dev_white_pine_canopy} the same for white pine. Per species so a voxel view
 * (Voxy, a map) gets the species' colour and texture.</li>
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
            PINE_FOLIAGE_ID = new ResourceLocation("apocalypse_firstlight", "dev_white_pine_foliage"),
            PINE_CANOPY_ID = new ResourceLocation("apocalypse_firstlight", "dev_white_pine_canopy");

    /** The species as a block state property. */
    public enum SpeciesProperty implements StringRepresentable {
        WHITE_OAK(Species.WHITE_OAK), WHITE_PINE(Species.WHITE_PINE);
        final Species species;
        SpeciesProperty(Species species) { this.species = species; }
        @Override public String getSerializedName() { return species.id; }
        static SpeciesProperty of(Species s) { return values()[s.ordinal()]; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, TreeShape.VARIANTS - 1);
    public static final EnumProperty<SpeciesProperty> SPECIES = EnumProperty.create("species", SpeciesProperty.class);

    private static Root root;
    private static Wood wood;
    private static Foliage foliage, canopy, pineFoliage, pineCanopy;

    private TreeBlocks() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (FMLEnvironment.production) return;
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            root = new Root();
            wood = new Wood();
            foliage = new Foliage(Species.WHITE_OAK, true);
            canopy = new Foliage(Species.WHITE_OAK, false);
            pineFoliage = new Foliage(Species.WHITE_PINE, true);
            pineCanopy = new Foliage(Species.WHITE_PINE, false);
            helper.register(ROOT_ID, root);
            helper.register(WOOD_ID, wood);
            helper.register(FOLIAGE_ID, foliage);
            helper.register(CANOPY_ID, canopy);
            helper.register(PINE_FOLIAGE_ID, pineFoliage);
            helper.register(PINE_CANOPY_ID, pineCanopy);
        });
    }

    public static boolean ready() { return root != null; }
    public static Root root() { return require(root); }
    public static Wood wood() { return require(wood); }

    /** The crown block of a species: carrying leaf quads, or volume only. */
    public static Foliage crown(Species species, boolean carrier) {
        return require(species == Species.WHITE_PINE ? carrier ? pineFoliage : pineCanopy : carrier ? foliage : canopy);
    }

    public static Foliage[] crowns() {
        return new Foliage[]{require(foliage), require(canopy), require(pineFoliage), require(pineCanopy)};
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
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(VARIANT, 0).setValue(SPECIES, SpeciesProperty.WHITE_OAK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, VARIANT, SPECIES);
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
