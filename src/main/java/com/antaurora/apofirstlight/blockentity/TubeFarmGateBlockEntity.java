package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.TubeFarmGateBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rural Driveway V1 tube farm gate, drawn by the AFL Animated Block Mesh Runtime (docs/rendering/animated_block_mesh_runtime_v1.md)
 * from the master cell (tools/build-rural-driveway-v1.mjs): 'frame' (posts, hinge pins, chain) still; 'swing' (a pure
 * pivot) turns +90 degrees on channel 'open_front', its child 'leaf' -90 degrees on 'open_back', one of them at a time
 * (the block's OPEN and SWING), 1.2 s each. The sign bones 'sign_front' / 'sign_back' ride on the leaf and show as the
 * block's SIGN says. Holds nothing; the block keeps the state, the shapes and the interaction.
 */
public class TubeFarmGateBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/tube_farm_gate.json");

    public TubeFarmGateBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.TUBE_FARM_GATE.get(), pos, state, PROFILE);
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(TubeFarmGateBlock.OPEN) || !state.getValue(TubeFarmGateBlock.OPEN)) return false;
        TubeFarmGateBlock.Swing swing = state.getValue(TubeFarmGateBlock.SWING);
        return switch (channel) {
            case "open_front" -> swing == TubeFarmGateBlock.Swing.FRONT;
            case "open_back" -> swing == TubeFarmGateBlock.Swing.BACK;
            default -> false;
        };
    }

    @Override
    public boolean meshPartVisible(String part) {
        BlockState state = getBlockState();
        TubeFarmGateBlock.Sign sign = state.hasProperty(TubeFarmGateBlock.SIGN) ? state.getValue(TubeFarmGateBlock.SIGN) : TubeFarmGateBlock.Sign.NONE;
        return switch (part) {
            case "sign_front" -> sign == TubeFarmGateBlock.Sign.FRONT;
            case "sign_back" -> sign == TubeFarmGateBlock.Sign.BACK;
            default -> true;
        };
    }
}
