package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.DistributionPanelBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.BuildingPowerZone;
import com.antaurora.apofirstlight.energy.PowerCableTransfer;
import com.antaurora.apofirstlight.menu.DistributionPanelMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Distribution Panel (Building Power V1, docs/models/building_power_v1.md): the building's main breaker and branch
 * circuits. Power comes in through the bottom port (a cable from a generator / energy cell, or the building's Service
 * Meter Box, which forwards into {@link #input()}) into a small buffer; while the main breaker is on and not tripped the
 * panel feeds the circuits each tick: the building's hidden wiring (lights and outlets, Building Power V1 step 2) and the
 * top port, whose cable network it seeds like a producer ("外接电缆"). Overload: when the circuits want clearly more than the
 * supply delivers, with the buffer run dry, for 3 s, the main breaker trips. Drawn by the AFL Animated Block Mesh Runtime:
 * door 'open' while someone has the screen open, the main and twelve branch handles on their '*_off' channels, the top
 * pull box only while a cable plugs in above.
 */
public class DistributionPanelBlockEntity extends AflAnimatedMeshBlockEntity implements MenuProvider {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/distribution_panel.json");
    public static final int SLOTS = 12, CAPACITY = 4096, MAX_IN = 512, MAX_OUT = 512, ZONE_PERIOD = 100, TRIP_TICKS = 60;
    /** Circuit kinds; the fixed ones take the first slots, devices get dedicated slots after them (step 2). */
    public static final int KIND_NONE = 0, KIND_LIGHTING = 1, KIND_OUTLETS = 2, KIND_CABLE = 3;
    public static final int DATA_FLAGS = 0, DATA_SUPPLY = 1, DATA_LOAD = 2, DATA_STORED = 3, DATA_SLOTS = 4, DATA_COUNT = DATA_SLOTS + SLOTS * 3;
    public static final int FLAG_MAIN = 1, FLAG_TRIPPED = 2, FLAG_ZONE_OK = 4, FLAG_ZONE_OPEN = 8, FLAG_DUPLICATE = 16;
    private static final String MAIN_KEY = "MainOn", TRIP_KEY = "Tripped", OFF_KEY = "BranchOff", ENERGY_KEY = "EnergyStored";

    private boolean mainOn;
    private boolean tripped;
    /** branch off flags by slot */
    private int branchOff;
    private int stored;
    private int viewers;
    // live (not saved)
    private BuildingPowerZone.Footprint footprint;
    private long nextZone;
    private final int[] slotKind = {KIND_LIGHTING, KIND_OUTLETS, KIND_CABLE, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final float[] slotLoad = new float[SLOTS];
    private float supplyAvg, loadAvg;
    private int receivedThisTick, overloadTicks;
    private long receiveTick = Long.MIN_VALUE, outputTick = Long.MIN_VALUE;
    private int outputThisTick;

    private final IEnergyStorage inputStorage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            long t = level == null ? 0 : level.getGameTime();
            if (receiveTick != t) { receiveTick = t; receivedThisTick = 0; }
            int accepted = Math.max(0, Math.min(max, Math.min(MAX_IN - receivedThisTick, CAPACITY - stored)));
            if (!simulate && accepted > 0) { stored += accepted; receivedThisTick += accepted; setChanged(); }
            return accepted;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    /** The top port: the cable circuit, gated by the main breaker and its own branch breaker. */
    private final IEnergyStorage outputStorage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) {
            if (!live() || branchIsOff(slotOf(KIND_CABLE))) return 0;
            long t = level == null ? 0 : level.getGameTime();
            if (outputTick != t) { outputTick = t; outputThisTick = 0; }
            int given = Math.max(0, Math.min(max, Math.min(MAX_OUT - outputThisTick, stored)));
            if (!simulate && given > 0) { stored -= given; outputThisTick += given; slotLoadAdd(slotOf(KIND_CABLE), given); setChanged(); }
            return given;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };
    private LazyOptional<IEnergyStorage> inputCap = LazyOptional.of(() -> inputStorage), outputCap = LazyOptional.of(() -> outputStorage);
    private final float[] tickLoad = new float[SLOTS];

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            if (i == DATA_FLAGS) return (mainOn ? FLAG_MAIN : 0) | (tripped ? FLAG_TRIPPED : 0) | zoneFlags();
            if (i == DATA_SUPPLY) return Math.round(supplyAvg);
            if (i == DATA_LOAD) return Math.round(loadAvg);
            if (i == DATA_STORED) return Math.round(100F * stored / CAPACITY);
            int s = (i - DATA_SLOTS) / 3, f = (i - DATA_SLOTS) % 3;
            if (s < 0 || s >= SLOTS) return 0;
            return switch (f) { case 0 -> slotKind[s]; case 1 -> branchIsOff(s) ? 1 : 0; default -> Math.round(slotLoad[s]); };
        }
        @Override public void set(int i, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    public DistributionPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.DISTRIBUTION_PANEL.get(), pos, state, PROFILE);
    }

    public IEnergyStorage input() { return inputStorage; }
    public boolean live() { return mainOn && !tripped; }
    private boolean branchIsOff(int slot) { return slot >= 0 && (branchOff & (1 << slot)) != 0; }
    private int slotOf(int kind) { for (int i = 0; i < SLOTS; i++) if (slotKind[i] == kind) return i; return -1; }
    private void slotLoadAdd(int slot, int fe) { if (slot >= 0) tickLoad[slot] += fe; }
    private int zoneFlags() {
        if (footprint == null) return 0;
        return switch (footprint.status()) { case OK -> FLAG_ZONE_OK; case OPEN -> FLAG_ZONE_OPEN; case DUPLICATE -> FLAG_DUPLICATE; };
    }

    // ---- server tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (footprint == null || now >= nextZone) {
            footprint = BuildingPowerZone.register(server, worldPosition, BuildingPowerZone.compute(server, worldPosition));
            nextZone = now + ZONE_PERIOD;
        }
        // the top port seeds its cable network; the network pulls from outputStorage this tick (level END)
        if (live()) PowerCableTransfer.transferFrom(server, worldPosition, Direction.UP, outputStorage, MAX_OUT);
        // hidden-wiring circuits (lights, outlets): Building Power V1 step 2 adds their devices here
        // averages over about 2 s; the cable circuit's load is what the network took last tick (level END)
        float supply = receiveTick == now - 1 || receiveTick == now ? receivedThisTick : 0;
        supplyAvg += (supply - supplyAvg) * 0.05F;
        float load = 0;
        for (int s = 0; s < SLOTS; s++) { slotLoad[s] += (tickLoad[s] - slotLoad[s]) * 0.05F; load += tickLoad[s]; tickLoad[s] = 0; }
        loadAvg += (load - loadAvg) * 0.05F;
        // overload: power arrives but the circuits take all of it and the buffer stays dry (the load wants more than the
        // supply can give); a dead source is no trip, the building just has no power
        if (live() && stored < CAPACITY / 20 && supplyAvg > 0.5F && loadAvg >= supplyAvg * 0.95F) {
            if (++overloadTicks >= TRIP_TICKS) trip(server);
        } else overloadTicks = 0;
    }

    private void trip(ServerLevel server) {
        tripped = true; overloadTicks = 0;
        server.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.6F);
        sync();
    }

    // ---- breakers (from the menu) ----

    public void toggleMain(Player player) {
        if (tripped) { tripped = false; mainOn = false; }   // a tripped main resets to off first, as a real handle does
        else mainOn = !mainOn;
        click(player); sync();
    }

    public void toggleBranch(int slot, Player player) {
        if (slot < 0 || slot >= SLOTS || slotKind[slot] == KIND_NONE) return;
        branchOff ^= 1 << slot;
        click(player); sync();
    }

    private void click(Player player) {
        if (level != null) level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    // ---- menu: the door stands open while someone looks ----

    @Override public Component getDisplayName() { return Component.translatable("block.apocalypse_firstlight.distribution_panel"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DistributionPanelMenu(id, inventory, this, data);
    }

    public void startOpen() { viewers++; setOpen(true); }
    public void stopOpen() { viewers = Math.max(0, viewers - 1); if (viewers == 0) setOpen(false); }
    private void setOpen(boolean open) {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.hasProperty(DistributionPanelBlock.OPEN) && state.getValue(DistributionPanelBlock.OPEN) != open) {
            level.setBlock(worldPosition, state.setValue(DistributionPanelBlock.OPEN, open), Block.UPDATE_CLIENTS);
            // the door sounds sit on the 10-tick door animation (tools/build-distribution-panel-sounds-v1.mjs), so the pitch
            // only varies by +-2 %; opening first releases the flush latch
            if (open) level.playSound(null, worldPosition, AflSounds.DISTRIBUTION_PANEL_LATCH.get(), SoundSource.BLOCKS, 0.6F, 1.04F + level.random.nextFloat() * 0.02F);
            level.playSound(null, worldPosition, open ? AflSounds.DISTRIBUTION_PANEL_OPEN.get() : AflSounds.DISTRIBUTION_PANEL_CLOSE.get(),
                    SoundSource.BLOCKS, 0.8F, 0.98F + level.random.nextFloat() * 0.04F);
        }
    }

    // ---- mesh ----

    @Override
    protected boolean meshAnimationTarget(String channel) {
        if (channel.equals("open")) return getBlockState().hasProperty(DistributionPanelBlock.OPEN) && getBlockState().getValue(DistributionPanelBlock.OPEN);
        if (channel.equals("main_off")) return !live();
        if (channel.startsWith("b") && channel.endsWith("_off")) {
            int slot;
            try { slot = Integer.parseInt(channel.substring(1, channel.length() - 4)); } catch (NumberFormatException e) { return false; }
            return slot < 0 || slot >= SLOTS || slotKind[slot] == KIND_NONE || branchIsOff(slot);   // spares stand off
        }
        return false;
    }

    /** The top pull box shows only while a cable plugs in above. */
    @Override
    public boolean meshPartVisible(String part) {
        if (!part.equals("port_up")) return true;
        if (level == null) return false;
        BlockState above = level.getBlockState(worldPosition.above());
        return above.getBlock() instanceof PowerCableBlock && PowerCableBlock.isConnected(above, Direction.DOWN);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }

    // ---- capabilities: bottom in, top out ----

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            if (side == Direction.DOWN) return inputCap.cast();
            if (side == Direction.UP) return outputCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); inputCap.invalidate(); outputCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); inputCap = LazyOptional.of(() -> inputStorage); outputCap = LazyOptional.of(() -> outputStorage); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) BuildingPowerZone.unregister(level, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) BuildingPowerZone.unregister(level, worldPosition);
    }

    // ---- save / sync ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(MAIN_KEY, mainOn);
        tag.putBoolean(TRIP_KEY, tripped);
        tag.putInt(OFF_KEY, branchOff);
        tag.putInt(ENERGY_KEY, stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mainOn = tag.getBoolean(MAIN_KEY);
        tripped = tag.getBoolean(TRIP_KEY);
        branchOff = tag.getInt(OFF_KEY);
        stored = Math.max(0, Math.min(CAPACITY, tag.getInt(ENERGY_KEY)));
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        refreshMeshAnimationTargets();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        refreshMeshAnimationTargets();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

}
