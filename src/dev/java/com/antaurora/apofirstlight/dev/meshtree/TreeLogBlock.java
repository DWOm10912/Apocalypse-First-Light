package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesh Trees P2.2 (docs/rendering/mesh_trees_p2_felling.md, V): a species' log as a placeable building block, one cubic
 * metre of wood, axis X / Y / Z like a vanilla log. Development only (registered by FellingRegistry outside production).
 * <ul>
 * <li>Looks: the rounded-square prism of {@link LogShape} (TreeLogModel); the species' corner radius sets its look
 * (white oak squarer, white pine rounder).</li>
 * <li>Collision, outline and support: the full block (it stacks, carries, takes torches and doors like a vanilla log).
 * Light: blocks it fully like a vanilla log (opacity 15, no sky light through), so log walls keep a house dark.</li>
 * <li>Face culling: the occlusion shape is a cross, the full block without its four corner columns of the corner radius.
 * Its face on each side is the flat middle band of that side: a neighbour face behind the band hides the log's flat side,
 * and the log never hides a neighbour's whole face (the rounded corners leave grooves through which it shows).</li>
 * <li>Mining (block audit, AGENTS.md: wood follows vanilla rules): vanilla log values (strength 2, wood sound, bass note,
 * lava ignites it, fire 5 / 5); axe through the dev {@code minecraft:logs} tag (in {@code minecraft:mineable/axe}); no
 * tool tier, no correct-tool requirement (hand breaking drops it, slowly); drops itself (dev loot table).</li>
 * </ul>
 */
public final class TreeLogBlock extends RotatedPillarBlock {
    public final Species species;
    /** The occlusion cross per axis (X, Y, Z). */
    private final VoxelShape[] occlusion = new VoxelShape[3];

    TreeLogBlock(Species species) {
        super(BlockBehaviour.Properties.of()
                .mapColor(state -> state.getValue(AXIS) == Direction.Axis.Y ? endColor(species) : barkColor(species))
                .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
        this.species = species;
        double r = LogShape.radius(species) * 16, s = 16 - r;
        occlusion[0] = Shapes.or(Block.box(0, r, 0, 16, s, 16), Block.box(0, 0, r, 16, 16, s));
        occlusion[1] = Shapes.or(Block.box(r, 0, 0, s, 16, 16), Block.box(0, 0, r, 16, 16, s));
        occlusion[2] = Shapes.or(Block.box(r, 0, 0, s, 16, 16), Block.box(0, r, 0, 16, s, 16));
    }

    /** End grain on a map: white oak's yellow-brown heartwood, white pine's cream. */
    private static MapColor endColor(Species s) { return s == Species.WHITE_PINE ? MapColor.SAND : MapColor.WOOD; }

    /** Bark on a map: white oak's pale grey plates, white pine's dark grey-brown. */
    private static MapColor barkColor(Species s) { return s == Species.WHITE_PINE ? MapColor.COLOR_BROWN : MapColor.COLOR_LIGHT_GRAY; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return occlusion[state.getValue(AXIS).ordinal()];
    }

    // the cross occlusion would make the block count as partly transparent (opacity 1); a log blocks light like a cube
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return level.getMaxLightLevel(); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return false; }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 5; }
}
