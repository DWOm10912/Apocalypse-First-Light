package com.antaurora.apofirstlight.noise;

import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public final class InteractionNoiseResolver {
    private InteractionNoiseResolver() {
    }

    public static Optional<Result> resolveToggle(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            return Optional.of(new Result(Kind.DOOR, DoorBlock.isWoodenDoor(state) ? 4.0 : 6.0));
        }
        if (block instanceof TrapDoorBlock) {
            return Optional.of(new Result(Kind.TRAPDOOR, state.is(Blocks.IRON_TRAPDOOR) ? 5.0 : 3.0));
        }
        if (block instanceof FenceGateBlock) {
            return Optional.of(new Result(Kind.FENCE_GATE, 3.0));
        }
        return Optional.empty();
    }

    public static Optional<Result> resolveContainer(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            return Optional.of(new Result(Kind.CHEST_OPEN, 3.0));
        }
        if (state.getBlock() instanceof BarrelBlock) {
            return Optional.of(new Result(Kind.BARREL_OPEN, 3.0));
        }
        return Optional.empty();
    }

    public static Result blockPlace() {
        return new Result(Kind.BLOCK_PLACE, 4.0);
    }

    public enum Kind {
        DOOR,
        TRAPDOOR,
        FENCE_GATE,
        CHEST_OPEN,
        BARREL_OPEN,
        BLOCK_PLACE
    }

    public record Result(Kind kind, double radius) {
    }
}
