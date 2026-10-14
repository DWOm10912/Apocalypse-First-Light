package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Building Wiring V1, wires going with their blocks (docs/gameplay/building_wiring_v1_design.md 8, a phase 1 core part):
 * <ul>
 *   <li>a player breaking a block in survival: its nodes go, one Insulated Copper Wire each drops in the cell; in creative
 *   nothing drops ({@link #broken}, lowest priority, skipped when cancelled);</li>
 *   <li>anything else that turns a cell into something that cannot hold wire (explosions, pistons, /setblock, /fill, fluids,
 *   fire, code): its nodes go without a refund ({@link #changed}, from ServerLevel#onBlockStateChange);</li>
 *   <li>a cell turned into another block that can hold wire keeps its wire.</li>
 * </ul>
 * A refund is the number of nodes really removed, so the same wire can never be paid back twice: whichever comes second
 * (a cut and a break in one tick, the break and the block change after it) finds nothing left. Links to the removed nodes
 * are removed with them on both sides; taps toward a device that is broken stay in the wall for the next device.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringRemoval {
    private WiringRemoval() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        int n = BuildingWiring.clear(level, pos);
        if (n > 0 && !event.getPlayer().isCreative()) Block.popResource(level, pos, new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), n));
    }

    /**
     * A block changed on the server (the new state is in the level already): a cell that cannot hold wire any more loses it;
     * one that still can keeps it, without the slope links its new block no longer makes.
     */
    public static void changed(ServerLevel level, BlockPos pos, BlockState now) {
        if (!BuildingWiring.any(level, pos)) return;
        if (!BuildingWiring.wireable(level, pos, now)) BuildingWiring.clear(level, pos);
        else BuildingWiring.checkSlopes(level, pos);
    }
}
