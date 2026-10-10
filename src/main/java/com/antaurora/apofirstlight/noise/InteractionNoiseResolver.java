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
    /** The radii (blocks); also how far these sounds are heard (client/NoiseSoundRanges, 2026-10-09). */
    public static final double WOODEN_DOOR = 4.0, IRON_DOOR = 6.0, WOODEN_TRAPDOOR = 3.0, IRON_TRAPDOOR = 5.0, FENCE_GATE = 3.0,
            CHEST = 3.0, BARREL = 3.0, BLOCK_PLACE = 4.0;

    private InteractionNoiseResolver() {
    }

    public static Optional<Result> resolveToggle(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            return Optional.of(new Result(Kind.DOOR, DoorBlock.isWoodenDoor(state) ? WOODEN_DOOR : IRON_DOOR));
        }
        if (block instanceof TrapDoorBlock) {
            return Optional.of(new Result(Kind.TRAPDOOR, state.is(Blocks.IRON_TRAPDOOR) ? IRON_TRAPDOOR : WOODEN_TRAPDOOR));
        }
        if (block instanceof FenceGateBlock) {
            return Optional.of(new Result(Kind.FENCE_GATE, FENCE_GATE));
        }
        return Optional.empty();
    }

    public static Optional<Result> resolveContainer(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            return Optional.of(new Result(Kind.CHEST_OPEN, CHEST));
        }
        if (state.getBlock() instanceof BarrelBlock) {
            return Optional.of(new Result(Kind.BARREL_OPEN, BARREL));
        }
        return Optional.empty();
    }

    public static Result blockPlace() {
        return new Result(Kind.BLOCK_PLACE, BLOCK_PLACE);
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
