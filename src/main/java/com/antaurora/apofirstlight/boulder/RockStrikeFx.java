package com.antaurora.apofirstlight.boulder;

import com.antaurora.apofirstlight.earthwork.RockTypes;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * A pick strike's particles (docs/gameplay/rock_strike_fx_v1.md; user 2026-10-24: the vanilla block crumbs and crit stars
 * were still in): where it landed, the way out of the face (toward the striker), the rock struck (its colour on the
 * client), the litres it broke off (how many chips and how much dust), whether a block or a boulder went (a bigger burst)
 * and whether the rock sparks under steel (quartz-bearing: the granite and sandstone looks). To every player within
 * {@link #RANGE}; client/RockStrikeParticles makes them. Boulders, bare rock ground and carved rock alike.
 */
public record RockStrikeFx(Vec3 at, Vec3 out, int state, float litres, boolean broke, boolean sparks) {
    static SimpleChannel channel;
    /** blocks: chips and dust are not seen farther */
    static final double RANGE = 48;

    static void encode(RockStrikeFx p, FriendlyByteBuf b) {
        b.writeFloat((float) p.at.x);
        b.writeFloat((float) p.at.y);
        b.writeFloat((float) p.at.z);
        b.writeFloat((float) p.out.x);
        b.writeFloat((float) p.out.y);
        b.writeFloat((float) p.out.z);
        b.writeVarInt(p.state);
        b.writeFloat(p.litres);
        b.writeByte((p.broke ? 1 : 0) | (p.sparks ? 2 : 0));
    }

    static RockStrikeFx decode(FriendlyByteBuf b) {
        Vec3 at = new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), out = new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
        int state = b.readVarInt();
        float litres = b.readFloat();
        int flags = b.readUnsignedByte();
        return new RockStrikeFx(at, out, state, litres, (flags & 1) != 0, (flags & 2) != 0);
    }

    static void handle(RockStrikeFx p, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.antaurora.apofirstlight.client.RockStrikeParticles.spawn(p)));
        c.setPacketHandled(true);
    }

    /** A strike's particles to everyone near: at the rock point, out of the face toward eye, the rock struck. */
    static void send(ServerLevel level, Vec3 at, Vec3 eye, BlockState rock, double litres, boolean broke) {
        if (channel == null) return;
        Vec3 out = eye.subtract(at);
        out = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize();
        RockStrikeFx p = new RockStrikeFx(at, out, Block.getId(rock), (float) litres, broke, sparks(rock));
        channel.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, level.dimension())), p);
    }

    /**
     * A strike's sound (docs 2): the strike by the rock's hardness, heard as far as the infected hear it (its noise,
     * BoulderStrike#RADIUS); on a block or boulder that went, the chunk falling (a take without the pick in it, user
     * 2026-10-24) on top of it in the same tick, no noise of its own.
     */
    static void sound(ServerLevel level, ServerPlayer p, Vec3 at, BlockState rock, boolean broke) {
        RangedSound.playWithNoise(level, p, at, (hard(rock) ? AflSounds.PICK_STRIKE_HARD : AflSounds.PICK_STRIKE_SOFT).get(), BoulderStrike.RADIUS,
                0.95F + 0.1F * level.random.nextFloat(), NoiseType.INTERACTION, AflItems.PICK_MATTOCK.getId());
        if (broke) RangedSound.play(level, at, AflSounds.ROCK_BREAK.get(), SoundSource.BLOCKS, BoulderStrike.RADIUS, 1.0F,
                0.95F + 0.1F * level.random.nextFloat());
    }

    /** Hard rock rings under the pick: the granite and sandstone looks, and stone the rock table does not know. */
    static boolean hard(BlockState s) {
        return sparks(s) || !s.hasProperty(BoulderBlocks.ROCK) && RockTypes.of(s.getBlock()) == null;
    }

    /** Quartz-bearing rock strikes sparks off steel: the granite and sandstone looks (rocks by RockTypes, boulders by their rock). */
    static boolean sparks(BlockState s) {
        if (s.hasProperty(BoulderBlocks.ROCK)) {
            BoulderRock r = s.getValue(BoulderBlocks.ROCK);
            return r == BoulderRock.GRANITE || r == BoulderRock.SANDSTONE;
        }
        RockTypes.Rock r = RockTypes.of(s.getBlock());
        return r != null && (r.look().equals("granite") || r.look().equals("sandstone"));
    }
}
