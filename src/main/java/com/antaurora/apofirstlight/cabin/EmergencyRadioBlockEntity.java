package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emergency Radio V1 (docs/models/emergency_radio_v1.md): the radio's charge, in ticks of play left (at most
 * {@link EmergencyRadio#CAPACITY}). The crank winds it up; while the switch is on it plays one tick a tick, and the block's
 * {@code lit} (the dial and lamp) follows: on with charge. The charge goes to clients (the prompt's; the client plays its
 * copy down in step) and stays with the item when the radio is broken (the loot table copies "Charge").
 */
public class EmergencyRadioBlockEntity extends BlockEntity {
    private int charge;

    public EmergencyRadioBlockEntity(BlockPos pos, BlockState state) { super(EmergencyRadio.RADIO, pos, state); }

    public int charge() { return charge; }

    /** Winds `ticks` of play in (capped); a switched-on radio lights. */
    void wind(int ticks) {
        charge = Math.min(EmergencyRadio.CAPACITY, charge + ticks);
        follow(getBlockState());
        changed();
    }

    /** The switch was moved: lit follows. */
    void switched(BlockState state) {
        follow(state);
        changed();
    }

    /** Sets lit on the block when it changed (on with charge). */
    private void follow(BlockState state) {
        if (level == null || level.isClientSide || !(state.getBlock() instanceof EmergencyRadio.Radio)) return;
        BlockState next = state.setValue(EmergencyRadio.Radio.LIT, state.getValue(EmergencyRadio.Radio.ON) && charge > 0);
        if (next != state) level.setBlock(worldPosition, next, 3);
    }

    void serverTick(BlockState state) {
        if (!state.getValue(EmergencyRadio.Radio.LIT)) return;
        charge = Math.max(0, charge - 1);
        if (charge == 0) { follow(state); changed(); }
        else if (level.getGameTime() % 20 == 0) setChanged();
    }

    /** Client: the charge plays down here too (for the prompt and Jade between updates). */
    void clientTick(BlockState state) {
        if (state.getValue(EmergencyRadio.Radio.LIT)) charge = Math.max(0, charge - 1);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Charge", charge);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        charge = Math.max(0, Math.min(EmergencyRadio.CAPACITY, tag.getInt("Charge")));
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
