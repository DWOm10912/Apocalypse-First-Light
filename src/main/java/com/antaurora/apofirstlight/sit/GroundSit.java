package com.antaurora.apofirstlight.sit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.MedicalItem;
import com.antaurora.apofirstlight.health.SplintItem;
import com.antaurora.apofirstlight.registry.AflEntities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sitting on the ground (docs/gameplay/splint_animation_v2_plan.md 3): the sit key (client/GroundSitClient, N) sits down
 * and gets up; jump or sneak get up too. Only standing on a collision face (a block's or a mesh block's: not in the air,
 * in water, on a ladder, riding or flying). Sat, the player rides a {@link GroundSeat} no one drives, which is rest
 * (PlayerHealth#resting: rest regeneration, fractures heal x rest). Seated the eye is {@link #EYE} high and the box
 * {@link #HEIGHT}; the body's clips are body.sit_down, body.sit, body.stand_up.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GroundSit {
    /** The clips' lengths in ticks: body.sit_down 1.25 s, body.stand_up 1.0 s (tools/medical-actions-v1a/author_splint_v2.cjs). */
    public static final int SIT_TICKS = 25, STAND_TICKS = 20;
    /**
     * Seated: the eye over the ground (blocks; body.sit's posed eye as ActionPose#cameraEye places it, 17.6 model px at
     * the player's 15/16 scale: author_splint_v2.cjs) and the box's height (the head's top 1.29).
     */
    public static final float EYE = 1.03F, HEIGHT = 1.3F;
    /** How far under the feet a collision face still holds the seat (blocks). */
    private static final double SUPPORT = 0.06;

    private GroundSit() {
    }

    /** The ground seat this entity sits on, or null. */
    @Nullable
    public static GroundSeat seat(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof GroundSeat seat ? seat : null;
    }

    /** Sat down and not getting up (the leg's splint starts only then). */
    public static boolean settled(Player player) {
        GroundSeat seat = seat(player);
        return seat != null && seat.state() == GroundSeat.State.SAT;
    }

    /** A collision face (any block's, mesh blocks' included) under this box's bottom, within {@link #SUPPORT}. */
    public static boolean supported(Level level, AABB box) {
        AABB under = new AABB(box.minX + 0.01, box.minY - SUPPORT, box.minZ + 0.01, box.maxX - 0.01, box.minY + 1.0E-4, box.maxZ - 0.01);
        for (VoxelShape shape : level.getBlockCollisions(null, under)) if (!shape.isEmpty()) return true;
        return false;
    }

    /** Server: whether this player could sit down where it stands. */
    public static boolean canSit(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && p.onGround() && !p.isInWater()
                && !p.isInLava() && !p.onClimbable() && !p.isFallFlying() && !p.isSwimming() && !p.getAbilities().flying
                && !p.isUsingItem() && supported(p.level(), p.getBoundingBox());
    }

    /** Server: sits the player down where it stands, facing its body's way; a message when it cannot. */
    public static boolean sit(ServerPlayer p) {
        if (seat(p) != null) return true;
        if (!canSit(p)) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.sit.cannot"), true);
            return false;
        }
        GroundSeat seat = new GroundSeat(AflEntities.GROUND_SEAT.get(), p.level());
        seat.moveTo(p.getX(), p.getY(), p.getZ(), p.yBodyRot, 0F);
        if (!p.level().addFreshEntity(seat)) return false;
        if (p.startRiding(seat, true)) return true;   // forced: sneaking or just off another seat
        seat.discard();
        return false;
    }

    /** Server: gets up (after the sit-down, over {@link #STAND_TICKS}); not while the hands splint a leg. */
    public static void stand(ServerPlayer p) {
        GroundSeat seat = seat(p);
        if (seat == null || treatingLeg(p)) return;
        seat.rise();
    }

    /** Splinting or dressing a leg (sat on the ground the whole time; health/MedicalItem#onLeg). */
    public static boolean treatingLeg(Player p) {
        return treatedLeg(p) != null;
    }

    /** The leg the hands splint or dress now, or null. */
    @Nullable
    public static BodyPart treatedLeg(Player p) {
        if (!p.isUsingItem()) return null;
        BodyPart limb = p.getUseItem().getItem() instanceof SplintItem ? SplintItem.target(p)
                : p.getUseItem().getItem() instanceof MedicalItem item ? MedicalItem.target(p, item.treatment()) : null;
        return limb != null && limb.leg() ? limb : null;
    }

    /** Seated: the lower eye and box (both sides; GroundSeat refreshes the rider's dimensions as it gets on and off). */
    @SubscribeEvent
    public static void size(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p) || seat(p) == null) return;
        event.setNewSize(EntityDimensions.scalable(event.getNewSize().width, HEIGHT));
        event.setNewEyeHeight(EYE);
    }

    // ---------------------------------------------------------------- the request

    /** Client to server: sit down ({@code down}) or get up. */
    public record Request(boolean down) {
        static void encode(Request p, FriendlyByteBuf b) {
            b.writeBoolean(p.down);
        }

        static Request decode(FriendlyByteBuf b) {
            return new Request(b.readBoolean());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Request.class, Request::encode, Request::decode, GroundSit::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void handle(Request packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        ServerPlayer p = c.getSender();
        c.enqueueWork(() -> {
            if (p == null) return;
            if (packet.down) sit(p);
            else stand(p);
        });
        c.setPacketHandled(true);
    }
}
