package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.HandFuelPumpBlock;
import com.antaurora.apofirstlight.fluid.FuelCanTransfers;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The hand pump's crank (block/HandFuelPumpBlock, docs/models/fuel_containers_v1.md). Every use of a held empty hand keeps
 * the crank turning a little longer ({@link #KEEP} ticks: held use repeats every 4); while it turns, a litre (1 mB) moves
 * every {@link #TICKS_PER_LITRE} ticks (2 L a second) from what the pump stands on (a drum; through an open fill cover,
 * the underground tank) into the container its hose reaches (HandFuelPumpBlock#target). The one cranking is
 * told why nothing moves (no container under the hose, it is full, it holds the other fuel, nothing left to draw). Turning
 * or not is synced; the client turns the crank (client/HandFuelPumpRenderer).
 */
public class HandFuelPumpBlockEntity extends BlockEntity {
    public static final int TICKS_PER_LITRE = 10, KEEP = 6;
    /** The crank's speed, degrees a tick (one turn a second). */
    public static final float DEGREES_PER_TICK = 18.0F;

    private long crankUntil, turned;
    private int uses;
    @Nullable
    private UUID cranker;
    private boolean cranking;
    private float angle, previousAngle;

    public HandFuelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.HAND_FUEL_PUMP.get(), pos, state);
    }

    /** A use by {@code player}: the crank keeps turning {@link #KEEP} ticks more (the server moves the fuel). */
    public void crank(Player player) {
        if (level == null || level.isClientSide) return;
        long now = level.getGameTime();
        if (now >= crankUntil) turned = 0;
        crankUntil = now + KEEP;
        cranker = player.getUUID();
        if (uses++ % 2 == 0) level.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.35F, 1.6F);   // placeholder: the crank's ratchet
    }

    public boolean cranking() {
        return cranking;
    }

    /** The crank's angle (degrees) at this frame. */
    public float angle(float partialTick) {
        return previousAngle + (angle - previousAngle) * partialTick;
    }

    public void clientTick() {
        previousAngle = angle;
        if (cranking) angle += DEGREES_PER_TICK;
        if (angle >= 360.0F) {
            angle -= 360.0F;
            previousAngle -= 360.0F;
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        boolean active = now < crankUntil;
        if (active != cranking) {
            cranking = active;
            setChanged();
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (!active || turned++ % TICKS_PER_LITRE != TICKS_PER_LITRE - 1) return;
        String problem = move(server);
        Player player = cranker == null ? null : server.getPlayerByUUID(cranker);
        if (problem != null && player != null) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.hand_fuel_pump." + problem), true);
        } else if (problem == null && turned % 20 < TICKS_PER_LITRE) {
            BlockPos to = HandFuelPumpBlock.target(server, worldPosition, getBlockState());
            if (to != null) server.playSound(null, to, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.25F, 1.4F);
        }
    }

    /** One litre from the source into the hose's container; null when it went, else the reason it did not. */
    @Nullable
    private String move(ServerLevel level) {
        IFluidHandler from = source(level), to = target(level);
        if (to == null) return "no_target";
        if (from == null) return "no_source";
        FluidStack drawn = from.drain(1, IFluidHandler.FluidAction.SIMULATE);
        if (drawn.isEmpty()) return "dry";
        if (to.fill(drawn, IFluidHandler.FluidAction.SIMULATE) <= 0) {
            FluidStack there = FuelCanTransfers.contents(to);
            return !there.isEmpty() && !there.isFluidEqual(drawn) ? "other_fuel" : "full";
        }
        to.fill(from.drain(1, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return null;
    }

    /** What it draws from: the drum it stands on, or (an open fill cover) the tank down the riser. */
    @Nullable
    public IFluidHandler source(ServerLevel level) {
        BlockPos below = worldPosition.below();
        return FuelCanTransfers.handler(level, below, Direction.UP);
    }

    /** What its hose drops into, or null. */
    @Nullable
    public IFluidHandler target(ServerLevel level) {
        BlockPos to = HandFuelPumpBlock.target(level, worldPosition, getBlockState());
        return to == null ? null : FuelCanTransfers.handler(level, to, Direction.UP);
    }

    /** The hose reaches into the next cells (client/HandFuelPumpRenderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.5);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("Cranking", cranking);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        cranking = tag.getBoolean("Cranking");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) cranking = tag.getBoolean("Cranking");
    }
}
