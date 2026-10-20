package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import com.antaurora.apofirstlight.fluid.LiquidJet;
import com.antaurora.apofirstlight.health.MedicalItem;
import com.antaurora.apofirstlight.health.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The medical alcohol poured over a wound (health V1a, 2026-10-19, docs/gameplay/survival_health_v1a.md 7.3; the user
 * asked for the liquid to really run out): while a player near this client tips the bottle over the held-out forearm, a
 * thin clear trickle runs from its mouth, a LiquidJet as the jerry can's stream (client/FuelCanPourJets), short-lived
 * (it runs off the arm and is gone), drawn in the fuel stains' world pass. Only a look: the treatment itself is the
 * server's, when the use finishes (health/MedicalItem).
 * <p>Where the mouth is: the player skeleton's bottle prop (player action item/alcohol_clean, player_props/medical_alcohol.json),
 * first and third person alike: its point {@code mouth}, the bottle's axis from it to {@code mouth_tip} (1 px further out along
 * the axis), as last drawn (client/skeleton/SkeletonActions#propPoint). It pours through the action's pour,
 * {@link #POUR_FROM} to {@link #POUR_TO} into the use, while that axis points down (world y under {@link #DOWN}); the stream
 * leaves the mouth slowly, so it falls straight onto the held-out forearm under it, and ends about there ({@link #LIFE}).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MedicalPourJets {
    /** The pour in the use (s): the action item/alcohol_clean's bottle tipped over the forearm. */
    static final double POUR_FROM = 2.1, POUR_TO = 2.9;
    /**
     * Range (blocks), the stream's look (speed, radius, break-up), how fast it leaves the mouth along the axis' level part and
     * downward (blocks a second), the axis' world y under which it pours, and a parcel's life (s): about its fall from the
     * mouth onto the forearm (2 px, about 0.13 s), then a little longer as it runs off the sleeve.
     */
    private static final double RANGE = 32.0, SPEED = 0.9, RADIUS = 0.006, BREAKUP = 0.45, OUT = 0.12, FALL = 0.3, DOWN = -0.2, LIFE = 0.25;
    private static final int ALPHA = 110, SIDES = 5, RGB = 0xDDE6EA;
    private static final ResourceLocation STILL = new ResourceLocation("block/water_still");

    private static final class Pour {
        final LiquidJet jet = new LiquidJet(LiquidJet.EARTH_GRAVITY, LIFE);
        boolean emitting;
    }

    private record Mouth(Vec3 at, Vec3 axis) {
    }

    private static final Map<UUID, Pour> POURS = new HashMap<>();
    @Nullable
    private static ClientLevel trackedLevel;

    private MedicalPourJets() {
    }

    static boolean isEmpty() {
        return POURS.isEmpty();
    }

    private static boolean alcohol(ItemStack stack) {
        return stack.getItem() instanceof MedicalItem medical && medical.treatment() == Wounds.Treatment.ALCOHOL;
    }

    /** The bottle's mouth and axis (world) while {@code player} pours, else null. */
    @Nullable
    private static Mouth pouring(Player player, float partialTick) {
        if (!player.isUsingItem() || !alcohol(player.getUseItem())) return null;
        double seconds = (player.getTicksUsingItem() + partialTick) / 20.0;
        if (seconds < POUR_FROM || seconds > POUR_TO) return null;
        Vec3 at = SkeletonActions.propPoint(player, "mouth"), tip = SkeletonActions.propPoint(player, "mouth_tip");
        if (at == null || tip == null) return null;
        Vec3 axis = tip.subtract(at).normalize();
        return axis.y < DOWN ? new Mouth(at, axis) : null;
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
            Mouth mouth = player.position().distanceToSqr(viewer) < RANGE * RANGE ? pouring(player, 1.0F) : null;
            Pour pour = POURS.get(player.getUUID());
            if (mouth != null) {
                if (pour == null) POURS.put(player.getUUID(), pour = new Pour());
                Vec3 out = new Vec3(mouth.axis().x * OUT, -FALL, mouth.axis().z * OUT);   // slowly over the lip, then it falls
                for (int k = 0; k < 2; k++) pour.jet.emit(mouth.at(), out, k == 0 ? LiquidJet.STEP : 0.0);
                pour.emitting = true;
            } else if (pour != null && pour.emitting) {
                pour.jet.gap();
                pour.emitting = false;
            }
        }
        for (Iterator<Map.Entry<UUID, Pour>> it = POURS.entrySet().iterator(); it.hasNext(); ) {
            Pour pour = it.next().getValue();
            pour.jet.tick(level, null, (parcel, hit) -> { });
            if (!pour.emitting && pour.jet.isEmpty()) it.remove();
        }
    }

    /** The trickles (entity-translucent on the block atlas, the leaks' batch; the caller ends it). */
    static void render(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 camera, float partialTick) {
        TextureAtlasSprite sprite = null;
        for (Map.Entry<UUID, Pour> entry : POURS.entrySet()) {
            Pour pour = entry.getValue();
            if (pour.jet.isEmpty()) continue;
            if (sprite == null) sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STILL);
            Player player = level.getPlayerByUUID(entry.getKey());
            Mouth mouth = pour.emitting && player != null ? pouring(player, partialTick) : null;
            BlockPos origin = BlockPos.containing(pour.jet.parcels().peekLast().pos);
            pose.pushPose();
            pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
            LiquidJetRenderer.render(pose, buffers, level, origin, pour.jet, mouth == null ? null : mouth.at(), partialTick, sprite, RGB, ALPHA, RADIUS, SPEED, SIDES, BREAKUP);
            pose.popPose();
        }
    }
}
