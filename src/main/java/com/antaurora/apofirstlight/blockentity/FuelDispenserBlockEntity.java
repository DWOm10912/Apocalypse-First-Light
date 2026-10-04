package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle;
import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fuel Dispenser V1 master cell: who holds which nozzle (docs/models/fuel_dispenser_v1.md). The holstered state itself is
 * the master block state's nozzle property (it switches the baked nozzle model); this keeps the holder and a session
 * number that the tethered {@link FuelNozzleItem} carries, so a stale or copied item is recognised and removed.
 * <p>
 * Every server tick each nozzle that is out is checked: its holder must be online, alive, in this level, within
 * {@link #BREAKAWAY} of the nozzle's outlet and still hold that very nozzle in the main hand. Otherwise the nozzle returns to
 * its holster: the holder let go (switched slots, dropped it, put it away, died, left), or walked off and the breakaway
 * coupling let go. Any copies of it in the holder's inventory are removed.
 */
public class FuelDispenserBlockEntity extends BlockEntity implements CompressorAppliance.Host {
    /** Hose the outlet's retractor can pay out (blocks, outlet to hand); the live hose is drawn up to it, then goes taut. */
    public static final double HOSE_LENGTH = 4.5;
    /** Past this distance from the outlet to the hand the breakaway coupling lets go. */
    public static final double BREAKAWAY = 5.0;

    private enum Release { HUNG, LOST, BREAKAWAY }

    /** The lamp's power: lights only, fed through the port on the master's bottom face (machine_balance/fuel_dispenser.json). */
    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::fuelDispenser);
    private final UUID[] holders = new UUID[Nozzle.values().length];
    private final int[] sessions = new int[Nozzle.values().length];

    public FuelDispenserBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.FUEL_DISPENSER.get(), pos, state);
    }

    @Nullable
    public UUID holder(Nozzle nozzle) {
        return holders[nozzle.ordinal()];
    }

    public boolean anyOut() {
        for (UUID holder : holders) if (holder != null) return true;
        return false;
    }

    /** True while this nozzle is out with this player under this session (FuelNozzleItem's validity test). */
    public boolean isHeldBy(int nozzle, UUID player, int session) {
        return nozzle >= 0 && nozzle < holders.length && player.equals(holders[nozzle]) && sessions[nozzle] == session && session != 0;
    }

    private Direction facing() {
        return getBlockState().getValue(FuelDispenserBlock.FACING);
    }

    public Vec3 outlet(Nozzle nozzle) {
        return nozzle.outlet(worldPosition, facing());
    }

    /** Where the server takes a holder's hand to be: about waist height (the client draws to the real hand). */
    private static Vec3 hand(ServerPlayer player) {
        return player.position().add(0, player.isCrouching() ? 0.75 : 0.95, 0);
    }

    public void serverTick() {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        power.serverTick();
        for (Nozzle nozzle : Nozzle.values()) {
            int i = nozzle.ordinal();
            if (holders[i] == null) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(holders[i]);
            if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level
                    || !FuelNozzleItem.matches(player.getMainHandItem(), level, worldPosition, i, sessions[i])) {
                release(nozzle, player, Release.LOST);
            } else if (outlet(nozzle).distanceToSqr(hand(player)) > BREAKAWAY * BREAKAWAY) {
                release(nozzle, player, Release.BREAKAWAY);
            }
        }
    }

    /** Takes a holstered nozzle into the player's empty main hand. */
    public boolean take(ServerPlayer player, Nozzle nozzle) {
        int i = nozzle.ordinal();
        BlockState state = getBlockState();
        if (level == null || holders[i] != null || !state.getValue(nozzle.property) || !player.getMainHandItem().isEmpty()) return false;
        int session;
        do session = level.random.nextInt(); while (session == 0);
        holders[i] = player.getUUID();
        sessions[i] = session;
        player.setItemInHand(InteractionHand.MAIN_HAND, FuelNozzleItem.create(nozzle.grade.nozzleItem(), worldPosition, level.dimension(), i, session));
        level.setBlock(worldPosition, state.setValue(nozzle.property, false), Block.UPDATE_ALL);
        play(nozzle.hood(worldPosition, facing()), AflSounds.FUEL_NOZZLE_TAKE.get(), 0.9F);
        level.gameEvent(player, GameEvent.ITEM_INTERACT_FINISH, worldPosition);
        sync();
        return true;
    }

    /** Hangs the player's nozzle (in the main hand) back in its holster. */
    public boolean hangUp(ServerPlayer player, Nozzle nozzle) {
        int i = nozzle.ordinal();
        if (!FuelNozzleItem.matches(player.getMainHandItem(), level, worldPosition, i, sessions[i]) || !player.getUUID().equals(holders[i])) return false;
        release(nozzle, player, Release.HUNG);
        return true;
    }

    /** The dispenser is going away: every nozzle that is out leaves its holder's hand (the block state goes with the block). */
    public void releaseAll() {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        for (Nozzle nozzle : Nozzle.values()) {
            int i = nozzle.ordinal();
            if (holders[i] == null) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(holders[i]);
            if (player != null) removeCopies(player, i, sessions[i]);
            holders[i] = null;
            sessions[i] = 0;
        }
    }

    private void release(Nozzle nozzle, @Nullable ServerPlayer player, Release why) {
        int i = nozzle.ordinal();
        if (level == null) return;
        if (player != null) removeCopies(player, i, sessions[i]);
        holders[i] = null;
        sessions[i] = 0;
        BlockState state = getBlockState();
        if (state.getBlock() instanceof FuelDispenserBlock && !state.getValue(nozzle.property)) {
            level.setBlock(worldPosition, state.setValue(nozzle.property, true), Block.UPDATE_ALL);
        }
        Vec3 hood = nozzle.hood(worldPosition, facing());
        switch (why) {
            case HUNG -> play(hood, AflSounds.FUEL_NOZZLE_HANG.get(), 0.9F);
            case LOST -> play(hood, AflSounds.FUEL_NOZZLE_HANG.get(), 0.6F);
            case BREAKAWAY -> {
                play(player != null ? hand(player) : hood, AflSounds.FUEL_NOZZLE_BREAKAWAY.get(), 1.0F);
                play(hood, AflSounds.FUEL_NOZZLE_HANG.get(), 0.6F);
            }
        }
        sync();
    }

    /** Removes every copy of this nozzle (this session) from the player: inventory slots and the stack on the cursor. */
    private void removeCopies(ServerPlayer player, int nozzle, int session) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (FuelNozzleItem.matches(inventory.getItem(slot), level, worldPosition, nozzle, session)) inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (FuelNozzleItem.matches(player.containerMenu.getCarried(), level, worldPosition, nozzle, session)) player.containerMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private void play(Vec3 at, SoundEvent sound, float volume) {
        if (level != null) level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, 0.95F + level.random.nextFloat() * 0.1F);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- power: the lamp only ----

    @Override
    public boolean lit() {
        return getBlockState().getValue(FuelDispenserBlock.LIT);
    }

    @Override
    public void setLit(boolean lit) {
        if (level != null && getBlockState().getBlock() instanceof FuelDispenserBlock block) block.setLit(level, worldPosition, lit);
    }

    /** No compressor: never used. */
    @Override
    public Vec3 compressorPosition() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public void syncAppliance() {
        setChanged();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && getBlockState().getBlock() instanceof FuelDispenserBlock block
                && block.hasPowerPort(getBlockState(), side)) return power.capability().cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        power.invalidateCaps();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        power.reviveCaps();
    }

    // ---- persistence and client sync (clients only need the holders) ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag);
        int[] saved = tag.getIntArray("Sessions");
        for (int i = 0; i < holders.length; i++) {
            holders[i] = tag.hasUUID("Holder" + i) ? tag.getUUID("Holder" + i) : null;
            sessions[i] = i < saved.length ? saved[i] : 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        power.save(tag);
        writeHolders(tag);
        tag.putIntArray("Sessions", sessions.clone());
    }

    private void writeHolders(CompoundTag tag) {
        for (int i = 0; i < holders.length; i++) if (holders[i] != null) tag.putUUID("Holder" + i, holders[i]);
    }

    /**
     * The holders, plus a mask of the nozzles that are out: never empty, because the block entity data packet drops an
     * empty tag and the client would then keep drawing a hose that was hung back.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeHolders(tag);
        int out = 0;
        for (int i = 0; i < holders.length; i++) if (holders[i] != null) out |= 1 << i;
        tag.putByte("Out", (byte) out);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** With a nozzle out, the live hose reaches up to the hose length from the outlets. */
    @Override
    public AABB getRenderBoundingBox() {
        AABB body = new AABB(worldPosition).inflate(1.0, 0, 1.0).expandTowards(0, 3, 0);
        return anyOut() ? body.inflate(HOSE_LENGTH + 1.0) : body;
    }
}
