package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The stream out of a jerry can being poured (2026-10-05, docs/models/fuel_containers_v1.md; item/FuelCanItem): every
 * player near this client pouring a can with fuel in it into a fill cover, once its cap is off and it is tipped
 * (FuelCanItem.POUR_DELAY), pours a thin stream of that fuel from the spout (in first person the view model's spout, else
 * FuelCanItem.spout) down into the cover's opening, a short arc under gravity: a LiquidJet like the leaks'
 * (client/ClientFuelLeaks), drawn in the fuel stains' world pass. The fuel itself goes into the tank on the server.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FuelCanPourJets {
    private static final double RANGE = 32.0, SPEED = 0.9, RADIUS = 0.011, BREAKUP = 0.35;
    private static final int ALPHA = 170, SIDES = 6;

    private static final class Pour {
        final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, 1.5);
        Fluid fluid;
        boolean emitting;
    }

    private static final Map<UUID, Pour> POURS = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private FuelCanPourJets() {
    }

    static boolean isEmpty() {
        return POURS.isEmpty();
    }

    /** The fuel {@code player} pours this tick (after the cap is off and the can tipped: FuelCanItem.POUR_DELAY), or null. */
    @Nullable
    private static Fluid pouring(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (FuelCanItem.pourTicks(stack, player.level()) < FuelCanItem.POUR_DELAY) return null;
        FluidStack fluid = FuelCanItem.fluid(stack);
        return fluid.isEmpty() ? null : fluid.getFluid();
    }

    /** Leaving {@code from}, the velocity (blocks a second) that falls into the cover's opening: a short arc. */
    private static Vec3 into(Vec3 from, BlockPos cover) {
        Vec3 to = new Vec3(cover.getX() + 0.5, cover.getY() + 0.45, cover.getZ() + 0.5);
        double t = Mth.clamp(Math.sqrt(2 * Math.max(0.04, from.y - to.y) / LiquidJet.EARTH_GRAVITY), 0.12, 0.6);
        return to.subtract(from).scale(1 / t).add(0, 0.5 * LiquidJet.EARTH_GRAVITY * t, 0);
    }

    /**
     * The can's spout: in first person where the view model drew it (client/JerryCanFirstPerson's spout bone, moved from
     * the hand pass to the world's view); otherwise where the pour pose holds it (FuelCanItem.spout, the server's too).
     */
    private static Vec3 spout(Player player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        var fp = player == mc.player && mc.options.getCameraType().isFirstPerson() ? JerryCanFirstPerson.spout() : null;
        if (fp != null) return ViewFov.handToWorld(player, partialTick, fp.x(), fp.y(), fp.z());
        return FuelCanItem.spout(player, partialTick);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != trackedLevel) {
            POURS.clear();
            trackedLevel = level;
        }
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;
        Vec3 viewer = minecraft.player.position();
        for (Player player : level.players()) {
            Fluid fluid = player.position().distanceToSqr(viewer) < RANGE * RANGE ? pouring(player) : null;
            Pour pour = POURS.get(player.getUUID());
            if (fluid != null) {
                if (pour == null) POURS.put(player.getUUID(), pour = new Pour());
                pour.fluid = fluid;
                BlockPos cover = FuelCanItem.pourTarget(player.getMainHandItem());
                Vec3 from = spout(player, 1.0F), velocity = into(from, cover);
                for (int k = 0; k < 2; k++) pour.jet.emit(from, velocity, k == 0 ? LiquidJet.STEP : 0.0);
                pour.emitting = true;
            } else if (pour != null && pour.emitting) {
                pour.jet.gap();
                pour.emitting = false;
            }
        }
        for (Iterator<Map.Entry<UUID, Pour>> it = POURS.entrySet().iterator(); it.hasNext(); ) {
            Pour pour = it.next().getValue();
            pour.jet.tick(level, null, (parcel, hit) -> {
                if (level.random.nextFloat() < 0.25F) LiquidDroplet.spawnOf(level, hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.03)),
                        new Vec3(level.random.triangle(0, 0.3), 0.25 + level.random.nextDouble() * 0.25, level.random.triangle(0, 0.3)), pour.fluid, 0.012F);
            });
            if (!pour.emitting && pour.jet.isEmpty()) it.remove();
        }
    }

    /** The streams (entity-translucent on the block atlas, the leaks' batch; the caller ends it). */
    static void render(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, float partialTick) {
        for (Map.Entry<UUID, Pour> entry : POURS.entrySet()) {
            Pour pour = entry.getValue();
            if (pour.jet.isEmpty() || pour.fluid == null) continue;
            Player player = level.getPlayerByUUID(entry.getKey());
            Vec3 head = pour.emitting && player != null ? spout(player, partialTick) : null;
            FluidStack stack = new FluidStack(pour.fluid, 1000);
            IClientFluidTypeExtensions client = IClientFluidTypeExtensions.of(pour.fluid);
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(client.getStillTexture(stack));
            int rgb = client.getTintColor(stack) & 0xFFFFFF;
            BlockPos origin = BlockPos.containing(pour.jet.parcels().peekLast().pos);
            pose.pushPose();
            pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
            LiquidJetRenderer.render(pose, buffers, level, origin, pour.jet, head, partialTick, sprite, rgb, ALPHA, RADIUS, SPEED, SIDES, BREAKUP);
            pose.popPose();
        }
    }
}
