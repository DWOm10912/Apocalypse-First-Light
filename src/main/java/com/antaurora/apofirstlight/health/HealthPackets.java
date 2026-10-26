package com.antaurora.apofirstlight.health;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Survival and health V1a: the own regeneration state, the wounds, the fractures and the screen effect's strength to the
 * client; and what everyone drawing a player needs of its body (BodyMarkSync): which parts wear a dressing or a splint, which
 * limbs are broken, which part the next bandage goes on (the gauze and splints on the body, the splint action's limb;
 * client/BandageWrapRenderer, client/skeleton/SkeletonActions); a hurting landing or knockdown, to play it (FallLanding,
 * Knockdown); and a hit with a direction, for the body's flinch (Knockdown, client/skeleton/HitReactClient).
 */
public final class HealthPackets {
    private HealthPackets() {
    }

    /**
     * {@code regen}: {@link PlayerHealth.Regen} ordinal; {@code visual}: the wound screen effect (0..1, before the client's
     * setting); the wounds as {@link Wounds.View}s; the fractures as {@link Fractures.View}s.
     */
    public record State(byte regen, float visual, List<Wounds.View> wounds, List<Fractures.View> fractures) {
        static void encode(State p, FriendlyByteBuf b) {
            b.writeByte(p.regen);
            b.writeFloat(p.visual);
            b.writeVarInt(p.wounds.size());
            for (Wounds.View w : p.wounds) {
                b.writeByte(w.stage().ordinal());
                b.writeBoolean(w.cleaned());
                b.writeBoolean(w.bandaged());
                b.writeFloat(w.recovery());
                b.writeByte(w.part().ordinal());
                b.writeByte(w.kind().ordinal());
            }
            b.writeVarInt(p.fractures.size());
            for (Fractures.View f : p.fractures) {
                b.writeByte(f.part().ordinal());
                b.writeBoolean(f.splinted());
                b.writeFloat(f.progress());
            }
        }

        static State decode(FriendlyByteBuf b) {
            byte regen = b.readByte();
            float visual = b.readFloat();
            int n = Math.min(16, b.readVarInt());
            List<Wounds.View> wounds = new ArrayList<>(n);
            Wounds.Stage[] stages = Wounds.Stage.values();
            Wounds.Kind[] kinds = Wounds.Kind.values();
            for (int i = 0; i < n; i++) {
                Wounds.Stage stage = stages[Math.max(0, Math.min(stages.length - 1, b.readByte()))];
                boolean cleaned = b.readBoolean(), bandaged = b.readBoolean();
                float recovery = b.readFloat();
                BodyPart part = BodyPart.byOrdinal(b.readByte());
                wounds.add(new Wounds.View(stage, cleaned, bandaged, recovery, part, kinds[Math.max(0, Math.min(kinds.length - 1, b.readByte()))]));
            }
            int m = Math.min(8, b.readVarInt());
            List<Fractures.View> fractures = new ArrayList<>(m);
            for (int i = 0; i < m; i++) fractures.add(new Fractures.View(BodyPart.byOrdinal(b.readByte()), b.readBoolean(), b.readFloat()));
            return new State(regen, visual, List.copyOf(wounds), List.copyOf(fractures));
        }
    }

    /**
     * A player's body as others draw it, masks by {@link BodyPart#bit}: the parts with a bandaged wound, the splinted limbs,
     * the broken limbs; and the parts the next bandage and the next cleaning go on (BodyPart ordinal, -1: none; the actions'
     * forearm, client/BandageWrapRenderer#workedArm). To the player and its trackers.
     */
    public record Marks(UUID player, int bandaged, int splinted, int broken, int bandageTarget, int cleanTarget, List<SkinMark> skin,
                        Fern fern) {
        public static final Marks NONE = new Marks(new UUID(0, 0), 0, 0, 0, -1, -1, List.of(), Fern.NONE);

        static void encode(Marks p, FriendlyByteBuf b) {
            b.writeUUID(p.player);
            b.writeByte(p.bandaged);
            b.writeByte(p.splinted);
            b.writeByte(p.broken);
            b.writeByte(p.bandageTarget);
            b.writeByte(p.cleanTarget);
            b.writeVarInt(p.skin.size());
            for (SkinMark m : p.skin) {
                b.writeByte(m.part().ordinal());
                b.writeByte(m.kind().ordinal());
                b.writeByte(m.stage().ordinal());
                b.writeBoolean(m.bandaged());
                b.writeInt(m.seed());
                b.writeByte(m.healed());
            }
            b.writeByte(p.fern.strength());
            b.writeByte(p.fern.part() == null ? -1 : p.fern.part().ordinal());
            b.writeInt(p.fern.seed());
        }

        static Marks decode(FriendlyByteBuf b) {
            UUID player = b.readUUID();
            int bandaged = b.readUnsignedByte(), splinted = b.readUnsignedByte(), broken = b.readUnsignedByte();
            int bandageTarget = b.readByte(), cleanTarget = b.readByte();
            int n = Math.min(16, b.readVarInt());
            Wounds.Kind[] kinds = Wounds.Kind.values();
            Wounds.Stage[] stages = Wounds.Stage.values();
            List<SkinMark> skin = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                BodyPart part = BodyPart.byOrdinal(b.readByte());
                Wounds.Kind kind = kinds[Mth.clamp(b.readByte(), 0, kinds.length - 1)];
                Wounds.Stage stage = stages[Mth.clamp(b.readByte(), 0, stages.length - 1)];
                skin.add(new SkinMark(part, kind, stage, b.readBoolean(), b.readInt(), Mth.clamp(b.readByte(), 0, 15)));
            }
            int strength = Mth.clamp(b.readByte(), 0, 15), fernPart = b.readByte(), seed = b.readInt();
            return new Marks(player, bandaged, splinted, broken, bandageTarget, cleanTarget, List.copyOf(skin),
                    new Fern(strength, fernPart < 0 ? null : BodyPart.byOrdinal(fernPart), seed));
        }

        public boolean bandaged(BodyPart part) {
            return (bandaged & part.bit()) != 0;
        }

        public boolean splinted(BodyPart part) {
            return (splinted & part.bit()) != 0;
        }

        /** The part the next bandage goes on, or null. */
        public BodyPart bandageTargetPart() {
            return bandageTarget < 0 ? null : BodyPart.byOrdinal(bandageTarget);
        }

        /** The part the next cleaning goes on, or null. */
        public BodyPart cleanTargetPart() {
            return cleanTarget < 0 ? null : BodyPart.byOrdinal(cleanTarget);
        }

        boolean empty() {
            return bandaged == 0 && splinted == 0 && broken == 0 && bandageTarget < 0 && cleanTarget < 0 && skin.isEmpty() && fern.strength() == 0;
        }

        boolean sameBody(Marks o) {
            return o != null && bandaged == o.bandaged && splinted == o.splinted && broken == o.broken && bandageTarget == o.bandageTarget
                    && cleanTarget == o.cleanTarget && skin.equals(o.skin) && fern.equals(o.fern);
        }
    }

    /**
     * One wound's mark on the skin (client/WoundSkin): where, what made it, its stage, whether it is dressed, the seed that
     * places it, how far it has healed (0..15 sixteenths).
     */
    public record SkinMark(BodyPart part, Wounds.Kind kind, Wounds.Stage stage, boolean bandaged, int seed, int healed) {}

    /** Lightning's fern-like mark on the skin (LightningInjury): strength 0..15 (0 none), the part the current came in by, its seed. */
    public record Fern(int strength, @Nullable BodyPart part, int seed) {
        public static final Fern NONE = new Fern(0, null, 0);
    }

    /**
     * A landing that hurt (FallLanding): the player (entity id), {@link FallLanding#HARD} or {@link FallLanding#FALL_OVER},
     * and whether it falls on the right hand (the action mirrored); or a knockdown (Knockdown: {@link FallLanding#KNOCK_FRONT}
     * .. {@link FallLanding#COLLAPSE}, mirrored = onto the right side), or getting up. To the player and its trackers.
     */
    public record Landing(int entity, byte kind, boolean mirror) {
        static void encode(Landing p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeByte(p.kind);
            b.writeBoolean(p.mirror);
        }

        static Landing decode(FriendlyByteBuf b) {
            return new Landing(b.readVarInt(), b.readByte(), b.readBoolean());
        }
    }

    /**
     * A hit that came from somewhere (Knockdown): the player (entity id), the world yaw (degrees, Minecraft's) from it to
     * where the hit came from, and its strength (health lost / max health, 0..1). To the player and its trackers.
     */
    public record Hit(int entity, float yaw, float strength) {
        static void encode(Hit p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeFloat(p.yaw);
            b.writeFloat(p.strength);
        }

        static Hit decode(FriendlyByteBuf b) {
            return new Hit(b.readVarInt(), b.readFloat(), Mth.clamp(b.readFloat(), 0, 1));
        }
    }

    /**
     * A living player's ragdoll (survivor/LiveRagdolls, docs/gameplay/injury_knockdown_v1_plan.md 9): its boxes
     * (RagdollSnapshot relative to {@code origin}) every tick while it falls ({@code state} FALLING, {@code root} where the
     * player's own box follows it); once still, its last pose with how it gets up ({@code state} FallLanding.KNOCK_FRONT or
     * KNOCK_BACK, the get-up played from LiveRagdolls#from; COLLAPSE: it stays down) and the place and yaw the get-up stands
     * at; ENDED: dropped with no get-up. To the player and its trackers.
     */
    public record Ragdoll(int entity, net.minecraft.world.phys.Vec3 origin, float[] pose, byte state, net.minecraft.world.phys.Vec3 root,
                          float yaw) {
        static void encode(Ragdoll p, FriendlyByteBuf b) {
            b.writeVarInt(p.entity);
            b.writeDouble(p.origin.x);
            b.writeDouble(p.origin.y);
            b.writeDouble(p.origin.z);
            com.antaurora.apofirstlight.survivor.SurvivorPackets.writePose(b, p.pose);
            b.writeByte(p.state);
            b.writeDouble(p.root.x);
            b.writeDouble(p.root.y);
            b.writeDouble(p.root.z);
            b.writeFloat(p.yaw);
        }

        static Ragdoll decode(FriendlyByteBuf b) {
            return new Ragdoll(b.readVarInt(), new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    com.antaurora.apofirstlight.survivor.SurvivorPackets.readPose(b), b.readByte(),
                    new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat());
        }
    }

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, State.class, State::encode, State::decode, HealthPackets::handleState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Marks.class, Marks::encode, Marks::decode, HealthPackets::handleMarks,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Landing.class, Landing::encode, Landing::decode, HealthPackets::handleLanding,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Hit.class, Hit::encode, Hit::decode, HealthPackets::handleHit,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Ragdoll.class, Ragdoll::encode, Ragdoll::decode, HealthPackets::handleRagdoll,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void handleRagdoll(Ragdoll packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleState(State packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHealth.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleLanding(Landing packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.skeleton.FallLandingClient.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleHit(Hit packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.antaurora.apofirstlight.client.skeleton.HitReactClient.accept(packet)));
        c.setPacketHandled(true);
    }

    private static void handleMarks(Marks packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHealth.acceptMarks(packet)));
        c.setPacketHandled(true);
    }
}
