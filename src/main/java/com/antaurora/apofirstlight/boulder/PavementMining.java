package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.road.RoadDamage;
import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A pick mattock strike on a road's asphalt (road Mesh-ification P0/P1, 2026-10-25, docs/worldgen/road_mesh_damage_v1_design.md
 * 4, 6): aimed at a road_surface block (BoulderStrike#aim), each strike breaks a piece of about RoadDamage.STRIKE_LITRES
 * out of the wearing course where it lands (RoadDamage#strike: 12.5 cm sub-cells, the hole widening from its rim) and
 * drops it as asphalt chunks. The road_surface blocks stay; their shape and the drawn pavement follow what is gone.
 * <p>
 * No hand breaks a road in survival (as natural rock, RockFace): no break progress, the break refused, the first hit says
 * it takes a pick. The driveway's gravel and the base under the asphalt wait for the earthwork (P2).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class PavementMining {
    private PavementMining() {}

    /** The Aim#node of a pavement strike (Aim#root the road_surface block struck). */
    public static final int NODE = -3;

    public static boolean pavement(BlockState state) { return state.is(AflBlocks.ROAD_SURFACE.get()); }

    /** One strike lands on the road at at: the piece of asphalt it breaks out, its effort, chips, sound and chunks. */
    static void strike(ServerPlayer p, ServerLevel level, BlockPos cell, Vec3 at) {
        if (!pavement(level.getBlockState(cell))) return;
        RoadDamage.Taken t = RoadDamage.strike(level, at, level.getRandom());
        if (t == null) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.pick.no_asphalt"), true);
            return;
        }
        PlayerStamina.spend(p, StaminaConfig.get().costs.pickStrike, false);
        BlockState asphalt = AflBlocks.ASPHALT.get().defaultBlockState();
        RockStrikeFx.send(level, at, p.getEyePosition(), asphalt, t.litres(), t.emptied());
        RockStrikeFx.sound(level, p, at, asphalt, t.emptied());
        // the chunks fall out of the strike, a little towards the striker
        Vec3 out = p.getEyePosition().subtract(at);
        Vec3 drop = at.add(out.lengthSqr() > 1e-6 ? out.normalize().scale(0.25) : Vec3.ZERO);
        BoulderStrike.drops(level, drop, AflItems.ASPHALT_CHUNK.get(), t.litres() / RoadSurfaceBlock.ASPHALT_CHUNK_LITRES);
    }

    @SubscribeEvent
    public static void speed(PlayerEvent.BreakSpeed e) {
        if (!e.getEntity().isCreative() && pavement(e.getState())) e.setNewSpeed(0);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void breaking(BlockEvent.BreakEvent e) {
        if (!e.getPlayer().isCreative() && pavement(e.getState())) e.setCanceled(true);
    }

    /** The first hit on a road says what it takes (server side, once per press). */
    @SubscribeEvent
    public static void hit(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || e.getEntity().isCreative()
                || !pavement(e.getLevel().getBlockState(e.getPos()))) return;
        e.getEntity().displayClientMessage(Component.translatable("message.apocalypse_firstlight.pick.needs_pick_road"), true);
    }
}
