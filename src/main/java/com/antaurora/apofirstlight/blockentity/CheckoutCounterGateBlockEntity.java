package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.CheckoutCounterGateBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * Checkout Counter V1 gate, drawn by the AFL Animated Block Mesh Runtime (docs/rendering/animated_block_mesh_runtime_v1.md):
 * one profile per hinge side (tools/build-checkout-counter-v1.mjs GATE_ANIMATION), bones 'flap' and 'door' on channels
 * of the same names, both following the block's OPEN (the flap folds over the neighbour in 0.8 s, the door swings in
 * 0.4 s). Holds nothing; the block keeps the state, the shapes and the interaction.
 */
public class CheckoutCounterGateBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE_LEFT =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/checkout_counter_gate_left.json");
    public static final ResourceLocation PROFILE_RIGHT =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/checkout_counter_gate_right.json");

    public CheckoutCounterGateBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.CHECKOUT_COUNTER_GATE.get(), pos, state,
                state.hasProperty(CheckoutCounterGateBlock.HINGE) && state.getValue(CheckoutCounterGateBlock.HINGE) == DoorHingeSide.RIGHT
                        ? PROFILE_RIGHT : PROFILE_LEFT);
    }

    /** Both channels ('flap', 'door') open with the gate. */
    @Override
    protected boolean meshAnimationTarget(String channel) {
        BlockState state = getBlockState();
        return state.hasProperty(CheckoutCounterGateBlock.OPEN) && state.getValue(CheckoutCounterGateBlock.OPEN);
    }
}
