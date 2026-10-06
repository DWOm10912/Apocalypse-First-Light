package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This client's copy of the fuel stains (FuelStainIndex.CLIENT, synced by AflNetwork.FuelStainS2CPacket from the level's
 * FuelSpills) and their drawing (2026-10-05, docs/models/fuel_dispenser_v1.md "滋油"): every stain within
 * {@link #RANGE} blocks of the camera, after the block entities, in its fuel's tint, translucent and without depth writes
 * (LiquidRenderTypes: overlapping stains flickered): floor stains on a full block top as joined pools (FuelPuddleMesher),
 * the rest as decals (LiquidDecals), oldest first so a newer one lies over an older one.
 * A stain keeps its look for 40% of its life, then fades and shrinks to 70% until it dries away. Variant and turn come
 * from the stain's id, so every client draws it the same; each is cut to its surface. A wall stain's run flows down to the
 * lower edge of its surface (FuelStainIndex.Stain#runLength); while it still flows the run is full strength and it drips off
 * that edge (most just after a wetting, fewer as the flow ends); a ceiling stain drips from where it is. Once the flow is
 * over the run thins to a faint film ({@link #FILM}) over {@link #THIN_TICKS}. Cleared on a level change; dried ones are
 * dropped every second.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFuelStains {
    private static final double RANGE = 64.0, DRIP_RANGE = 32.0;
    private static final float EDGE_DRIP = 0.25F, CEILING_DRIP = 0.12F, FILM = 0.35F;
    private static final int THIN_TICKS = 200;
    private static final int ALPHA = 150;
    @Nullable
    private static ClientLevel trackedLevel;

    private ClientFuelStains() {
    }

    public static void apply(List<FuelStainIndex.Stain> upserts, long[] removed) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        for (long id : removed) {
            FuelStainIndex.Stain gone = FuelStainIndex.CLIENT.remove(id);
            if (gone == null) continue;
            FuelPuddleMesher.touched(gone);
            if (gone.burning()) Scorches.burntOut(gone, now);   // burnt out: the ground under it is scorched, embers cooling
        }
        for (FuelStainIndex.Stain stain : upserts) {
            FuelStainIndex.CLIENT.put(stain);
            FuelPuddleMesher.touched(stain);
            if (stain.burning()) Scorches.burning(stain);   // remembers how large it burnt
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != trackedLevel) {
            FuelStainIndex.CLIENT.clear();
            FuelPuddleMesher.clear();
            Scorches.clear();
            trackedLevel = level;
        }
        if (level == null) return;
        long now = level.getGameTime();
        Scorches.tick(now);
        if (now % 20 == 0) FuelStainIndex.CLIENT.expire(now, FuelPuddleMesher::touched);
        if (now % FuelPuddleMesher.FADE_REBUILD == 0) FuelPuddleMesher.refreshDrying(now);
        var player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().isPaused()) return;
        FuelFlames.tick(level, player.position(), now);
        for (FuelStainIndex.Stain stain : FuelStainIndex.CLIENT.all()) {   // drips off wall edges and ceilings
            if (stain.floor() || !stain.flowing(now) || stain.pos.distanceToSqr(player.position()) > DRIP_RANGE * DRIP_RANGE) continue;
            float left = 1.0F - (float) (now - stain.wet) / stain.flowTicks();   // most just after a wetting
            Vec3 n = Vec3.atLowerCornerOf(stain.face.getNormal()), from;
            if (stain.face == net.minecraft.core.Direction.DOWN) {
                if (level.random.nextFloat() >= CEILING_DRIP * left) continue;
                from = stain.pos.add(0, -0.03, 0);
            } else {
                if (!stain.runAtEdge(now) || level.random.nextFloat() >= EDGE_DRIP * left) continue;
                from = stain.pos.add(FuelStainIndex.axisV(stain.face).scale(stain.v1)).add(n.scale(0.03));
            }
            LiquidDroplet.spawnOf(level, from, new Vec3(0, -0.3, 0), (stain.diesel ? AflFluids.DIESEL : AflFluids.GASOLINE).get(), 0.016F);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || FuelStainIndex.CLIENT.isEmpty() && BulletHoles.isEmpty() && Scorches.isEmpty() && FireBlockFlames.isEmpty() && FireFx.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int gasoline = tint(AflFluids.GASOLINE.get()), diesel = tint(AflFluids.DIESEL.get());
        double now = level.getGameTime() + event.getPartialTick();
        FireStats.begin();   // timed while F3 is open
        BulletHoles.render(pose, buffers, level, camera, now);   // first: fuel running down a holed wall lies over the holes
        buffers.endBatch(LiquidRenderTypes.HOLE);
        FireStats.lap(FireStats.HOLES);
        // floors: pools (one field per cell, no overlaps); walls, ceilings and floors without a full top: decals, oldest first
        Scorches.renderChar(pose, buffers, level, camera, RANGE, now);   // burnt ground under what fuel is left
        FuelPuddleMesher.rebuild(level, now);
        FuelPuddleMesher.render(pose, buffers, level, camera, RANGE, now, gasoline, diesel, ALPHA);
        java.util.List<FuelStainIndex.Stain> decals = new java.util.ArrayList<>();
        for (FuelStainIndex.Stain stain : FuelStainIndex.CLIENT.all()) {
            if (stain.floor() && FuelPuddleMesher.pooled(stain) || stain.pos.distanceToSqr(camera) > RANGE * RANGE) continue;
            decals.add(stain);
        }
        decals.sort(java.util.Comparator.comparingLong(s -> s.id));
        for (FuelStainIndex.Stain stain : decals) {
            float life = stain.life(), age = (float) (now - stain.wet);
            float fade = age < 0.4F * life ? 1.0F : Math.max(0.0F, 1.0F - (age - 0.4F * life) / (0.6F * life));
            if (fade <= 0.0F) continue;
            LiquidDecals.draw(pose, buffers, level, camera, stain.pos, stain.face, FuelStainIndex.axisU(stain.face), FuelStainIndex.axisV(stain.face),
                    stain.id, stain.size * (0.7F + 0.3F * fade), stain.u0, stain.u1, stain.v0, stain.v1, visibleRun(stain, now), runStrength(stain, now),
                    stain.diesel ? diesel : gasoline, Math.round(ALPHA * fade));
        }
        buffers.endBatch(LiquidRenderTypes.DECAL);
        Scorches.renderEmbers(pose, buffers, camera, RANGE, now);
        buffers.endBatch(LiquidRenderTypes.EMBER);
        FireStats.lap(FireStats.GROUND);
        ClientFuelLeaks.renderStreams(pose, buffers, level, camera, event.getPartialTick());   // fuel leaking out of bullet holes
        buffers.endBatch(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        FireStats.lap(FireStats.STREAMS);
        FireFx.renderSmoke(pose, buffers, level, event.getCamera(), event.getPartialTick());   // smoke, then the flames' light over it
        buffers.endBatch(LiquidRenderTypes.SMOKE);
        FireStats.lap(FireStats.SMOKE);
        FuelFlames.render(pose, buffers, camera, now);   // last: they add light onto everything behind them
        ClientFuelLeaks.renderFlames(buffers.getBuffer(LiquidRenderTypes.FLAME), pose, camera, now);
        FireBlockFlames.render(pose, buffers, level, camera, now);   // vanilla fire blocks, in the same flames
        buffers.endBatch(LiquidRenderTypes.FLAME);
        FireStats.lap(FireStats.FLAMES);
        FuelFlames.renderGlows(pose, buffers, camera, now);   // their glow on the floor
        FireBlockFlames.renderGlows(pose, buffers, level, camera, now);
        FireFx.renderSparks(pose, buffers, event.getCamera(), event.getPartialTick());
        buffers.endBatch(LiquidRenderTypes.GLOW);
        FireFx.renderStreaks(pose, buffers, event.getCamera(), event.getPartialTick());   // sparks off steel
        buffers.endBatch(LiquidRenderTypes.SPARK);
        FireStats.lap(FireStats.GLOWS);
        FireStats.end();
    }

    /**
     * The part of a run drawn as streaks: at most about twice the stain size below the hit (user, 2026-10-05: long thin
     * threads down a whole pillar looked ugly). Further down the fuel goes on as a film too thin to see; it still reaches
     * the lower edge and drips there (Stain#runAtEdge).
     */
    private static float visibleRun(FuelStainIndex.Stain stain, double now) {
        return Math.min(stain.runLength(now), Math.max(0.25F, stain.size * 2.0F));
    }

    /** A run at full strength while it flows, then thinning to a faint film. */
    private static float runStrength(FuelStainIndex.Stain stain, double now) {
        double after = now - stain.wet - stain.flowTicks();
        return after <= 0 ? 1.0F : (float) Math.max(FILM, 1.0 - after / THIN_TICKS * (1.0 - FILM));
    }

    private static int tint(net.minecraft.world.level.material.Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor(new FluidStack(fluid, 1000)) & 0xFFFFFF;
    }
}
