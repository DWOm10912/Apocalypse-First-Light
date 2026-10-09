package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.EnclosureGateBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * Trash enclosure V1 gate, drawn by the AFL Animated Block Mesh Runtime (docs/rendering/animated_block_mesh_runtime_v1.md)
 * from the master cell: one profile per hinge side (tools/build-trash-enclosure-v1.mjs), bones 'frame' (the post, still)
 * and 'leaf' on channel 'swing', following the block's OPEN (90 degrees outward in 0.9 s). Holds nothing; the block keeps
 * the state, the shapes and the interaction.
 */
public class EnclosureGateBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE_RIGHT =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/enclosure_gate_right.json");
    public static final ResourceLocation PROFILE_LEFT =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/enclosure_gate_left.json");

    public EnclosureGateBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.ENCLOSURE_GATE.get(), pos, state,
                state.hasProperty(EnclosureGateBlock.HINGE) && state.getValue(EnclosureGateBlock.HINGE) == DoorHingeSide.LEFT
                        ? PROFILE_LEFT : PROFILE_RIGHT);
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        BlockState state = getBlockState();
        return state.hasProperty(EnclosureGateBlock.OPEN) && state.getValue(EnclosureGateBlock.OPEN);
    }
}
