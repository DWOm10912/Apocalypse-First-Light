package com.antaurora.apofirstlight.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/** Server-only block policy; no material energy/deflection model in V1. */
public enum BulletBlockInteraction {
    STOP, BREAK_AND_PASS;
    public static final TagKey<Block> GLASS = TagKey.create(Registries.BLOCK,
            new ResourceLocation("apocalypse_firstlight", "bullet_breakable_glass"));
    private static final ThreadLocal<BlockEvent.BreakEvent> ACTIVE = new ThreadLocal<>();
    public static boolean isBulletBreak(BlockEvent.BreakEvent event) { return ACTIVE.get() == event; }
    public static BulletBlockInteraction resolve(BlockState state) { return state.is(GLASS) ? BREAK_AND_PASS : STOP; }
    public static boolean breakGlass(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || !player.mayBuild() || player.isSpectator()
                || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var state = level.getBlockState(pos);
        if (resolve(state) != BREAK_AND_PASS) return false;
        var event = new BlockEvent.BreakEvent(level, pos, state, player);
        var previous = ACTIVE.get();
        ACTIVE.set(event);
        try {
            if (MinecraftForge.EVENT_BUS.post(event) || level.getBlockState(pos) != state) return false;
            // Waterlogged panes leave water, not air; fluid itself is not a bullet collider.
            return level.destroyBlock(pos, false, player)
                    && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty();
        } finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
