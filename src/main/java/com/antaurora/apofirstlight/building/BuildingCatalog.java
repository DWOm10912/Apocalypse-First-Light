package com.antaurora.apofirstlight.building;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The building hammer's catalog (building hammer B0, docs/gameplay/building_hammer_b0.md): the building pieces it installs
 * and takes down. A piece is a block whose item is the finished piece made at the workbench (or found); installing takes
 * one of that item, taking it down gives one back. Only these blocks answer to the hammer: machines, terrain, furniture
 * and everything else are not in the catalog. B0 has one piece, the white oak beam.
 */
public final class BuildingCatalog {
    /**
     * One piece: its block and item share the id. Its orientations are the block state's own quarter turns: a pillar's
     * three axes (X, Z, Y), a horizontal block's four facings, else the one default state.
     */
    public record Piece(ResourceLocation id) {
        @Nullable
        public Block block() {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            return block == null || block == Blocks.AIR ? null : block;
        }

        @Nullable
        public Item item() {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item == null || item == Items.AIR ? null : item;
        }

        public List<BlockState> orientations() {
            Block block = block();
            if (block == null) return List.of();
            BlockState base = block.defaultBlockState();
            List<BlockState> states = new ArrayList<>();
            if (base.hasProperty(BlockStateProperties.AXIS)) {
                for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y})
                    states.add(base.setValue(BlockStateProperties.AXIS, axis));
            } else if (base.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                for (Direction d : Direction.Plane.HORIZONTAL) states.add(base.setValue(BlockStateProperties.HORIZONTAL_FACING, d));
            } else {
                states.add(base);
            }
            return states;
        }

        /** The state for an orientation index (wrapped into range). */
        @Nullable
        public BlockState state(int orientation) {
            var states = orientations();
            return states.isEmpty() ? null : states.get(Math.floorMod(orientation, states.size()));
        }
    }

    public static final List<Piece> PIECES = List.of(
            new Piece(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "white_oak_beam")));

    private BuildingCatalog() {}

    @Nullable
    public static Piece byId(ResourceLocation id) {
        for (Piece p : PIECES) if (p.id().equals(id)) return p;
        return null;
    }

    /** The piece a placed block is, or null when the hammer must leave it alone. */
    @Nullable
    public static Piece byBlock(Block block) {
        var id = ForgeRegistries.BLOCKS.getKey(block);
        return id == null ? null : byId(id);
    }
}
