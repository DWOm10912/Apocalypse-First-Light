package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BuildingLightBlock;
import com.antaurora.apofirstlight.block.DistributionPanelBlock;
import com.antaurora.apofirstlight.block.LogMount;
import com.antaurora.apofirstlight.block.WallSwitchBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.BuildingPowerZone;
import com.antaurora.apofirstlight.energy.PowerCableTransfer;
import com.antaurora.apofirstlight.menu.DistributionPanelMenu;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringStorage;
import com.antaurora.apofirstlight.wiring.WiringSupply;
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
 * door 'open' while someone has the screen open, the main and twelve branch handles on their '*_off' channels, each pull
 * box only while a cable plugs into its port. The same block entity serves the commercial panelboard and the residential
 * load center (their own mesh profiles).
 * <p>
 * Supply mode (Building Wiring V1 phase 2, docs/gameplay/building_wiring_v1_design.md 2, 5): {@link SupplyMode#LEGACY_AREA}
 * serves the building footprint as before (lighting, outlets and the top cable on the first three breakers); a
 * {@link SupplyMode#WIRED} panel serves only what its hidden wires reach (wiring/WiringSupply): four circuit breakers, one
 * per wire circuit (red, blue, green, yellow), and the top cable on a row of its own without a breaker (main breaker and the
 * trip only). A panel placed new is WIRED; one loaded without the field (old saves, old templates) is LEGACY_AREA and stays
 * so. Only {@link #setMode} changes it (dev command, the authoring bridge), never the wiring around it.
 * <p>
 * Light groups (phase 3, design 6): a WIRED panel keeps the on / off state of every light group, 8 per circuit
 * ({@link #groupOn}; saved as GroupOff, all on by default), so it survives a power loss. A wall switch on the group flips it
 * ({@link #toggleGroup}): every switch of the group turns with it and the group's lights look again at once. After each solve
 * the switches that disagree with their group (placed or loaded since) are set to it.
 */
public class DistributionPanelBlockEntity extends AflAnimatedMeshBlockEntity implements MenuProvider {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/distribution_panel.json");
    /** The residential load center's mesh (same rig and channels, 2026-10-13). */
    public static final ResourceLocation LOAD_CENTER_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/load_center.json");
    public static final int SLOTS = 12, CAPACITY = 4096, MAX_IN = 512, MAX_OUT = 512, ZONE_PERIOD = 100, TRIP_TICKS = 60;
    /** Circuit kinds; the fixed ones take the first slots, devices get dedicated slots after them (step 2). */
    public static final int KIND_NONE = 0, KIND_LIGHTING = 1, KIND_OUTLETS = 2, KIND_CABLE = 3;
    /** WIRED: circuit n's breaker is kind KIND_WIRE + n - 1 (slots 0-3); the top cable's row (slot 4) has no breaker. */
    public static final int KIND_WIRE = 4, KIND_WIRED_CABLE = 8;
    public enum SupplyMode { LEGACY_AREA, WIRED }
    /**
     * Screen data: the supply actually received (the trip rule's), the load, each branch's load and the storage % as plain
     * averages over the last {@link #VIEW_WINDOW} ticks (the trip rule keeps its own ~1 s averages), the capacity (what the
     * source network could give, capped at MAX_IN; 0 = no supply), the overload ticks toward a trip.
     */
    public static final int DATA_FLAGS = 0, DATA_SUPPLY = 1, DATA_LOAD = 2, DATA_STORED = 3, DATA_CAPACITY = 4, DATA_OVERLOAD = 5,
            DATA_SLOTS = 6, DATA_GROUPS = DATA_SLOTS + SLOTS * 3;
    /**
     * Light groups on the screen (phase 3), per circuit 1-4 from DATA_GROUPS + (n - 1) * GROUP_DATA: the groups in use (bit 0
     * always on, bits 1-8 the light groups, bit 9 the photocell), the groups switched off (bits 0-7 = groups 1-8), the other
     * loads (outlets, emergency lights), then the lights per group 0-8 and the photocell's.
     */
    public static final int GROUP_USED = 0, GROUP_OFF = 1, GROUP_OTHERS = 2, GROUP_LIGHTS = 3, GROUP_DATA = GROUP_LIGHTS + WiringNode.GROUPS + 2,
            PHOTOCELL_BIT = WiringNode.GROUPS + 1, DATA_COUNT = DATA_GROUPS + WiringNode.BREAKERS * GROUP_DATA;
    public static final int FLAG_MAIN = 1, FLAG_TRIPPED = 2, FLAG_ZONE_OK = 4, FLAG_ZONE_OPEN = 8, FLAG_DUPLICATE = 16, FLAG_FALLING = 32,
            FLAG_WIRED = 64, FLAG_CONFLICT = 128, FLAG_PARTIAL = 256;
    /** A branch's "off" field on the screen: 0 on, 1 off, 2 on but joined to another panel (no power). */
    public static final int BRANCH_ON = 0, BRANCH_OFF = 1, BRANCH_CONFLICT = 2;
    /**
     * The display window, ticks: a whole number of every device's settlement period (lamps 40, site lights, canopy lights
     * and the price sign 20, the intake pump's lamp 10), so each period's lump of draw counts exactly once and the readings
     * stand still (user 2026-10-09: with ~5 s exponential averages they still swung 111..117 FE/t, the storage 90..100 %).
     */
    public static final int VIEW_WINDOW = 120;
    private static final String MAIN_KEY = "MainOn", TRIP_KEY = "Tripped", OFF_KEY = "BranchOff", ENERGY_KEY = "EnergyStored", MODE_KEY = "SupplyMode", CONFLICT_KEY = "WiringConflict", GROUP_KEY = "GroupOff";

    private boolean mainOn;
    private boolean tripped;
    /** branch off flags by slot */
    private int branchOff;
    /** WIRED light groups switched off: bit (circuit - 1) * 8 + group - 1 (circuits 1-4, groups 1-8) */
    private int groupOff;
    private int stored;
    private int viewers;
    // live (not saved)
    private BuildingPowerZone.Footprint footprint;
    private long nextZone;
    private SupplyMode mode = SupplyMode.WIRED;
    private final int[] slotKind = new int[SLOTS];
    // WIRED (live, not saved): the last solve and when to solve again
    @Nullable private WiringSupply.Result wiring;
    /** The switches the last solve found working a group (to clear their mark when they stop). */
    private java.util.Set<BlockPos> switchesWorked = java.util.Set.of();
    private int solvedRevision = -1;
    private long nextSolve;
    /** The circuits joined to another panel (bit n = circuit n), as last synced: the wrench's view and Jade on the client. */
    private int syncedConflict;
    private final float[] slotLoad = new float[SLOTS];
    private float supplyAvg, loadAvg, capacityAvg;
    private long capacityTick = Long.MIN_VALUE;
    private int capacityThisTick;
    // the display window (live, not saved): each branch's draw and the storage per tick, and their running sums
    private final int[][] slotRing = new int[SLOTS][VIEW_WINDOW];
    private final long[] slotSum = new long[SLOTS];
    private final int[] storedRing = new int[VIEW_WINDOW];
    private long storedSum;
    private int ringAt, ringFill;
    private boolean falling;
    private int receivedThisTick, overloadTicks;
    private long receiveTick = Long.MIN_VALUE, outputTick = Long.MIN_VALUE;
    private int outputThisTick;

    private final class Input implements IEnergyStorage, com.antaurora.apofirstlight.energy.SupplyProbe {
        @Override public void networkSupply(long gameTime, int available) { DistributionPanelBlockEntity.this.networkSupply(gameTime, available); }
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
    }
    private final Input inputStorage = new Input();
    /** The top port: the cable circuit, gated by the main breaker and its own branch breaker. */
    private final IEnergyStorage outputStorage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) {
            int slot = cableSlot();
            if (!live() || slot < 0 || (mode == SupplyMode.LEGACY_AREA && branchIsOff(slot))) return 0;
            long t = level == null ? 0 : level.getGameTime();
            if (outputTick != t) { outputTick = t; outputThisTick = 0; }
            int given = Math.max(0, Math.min(max, Math.min(MAX_OUT - outputThisTick, stored)));
            if (!simulate && given > 0) { stored -= given; outputThisTick += given; slotLoadAdd(slot, given); setChanged(); }
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
            if (i == DATA_FLAGS) return (mainOn ? FLAG_MAIN : 0) | (tripped ? FLAG_TRIPPED : 0) | zoneFlags() | (falling ? FLAG_FALLING : 0) | wiringFlags();
            if (i == DATA_SUPPLY) return Math.round(supplyAvg);
            if (i == DATA_LOAD) { long sum = 0; for (long v : slotSum) sum += v; return Math.round((float) sum / Math.max(1, ringFill)); }
            if (i == DATA_STORED) return ringFill == 0 ? Math.round(100F * stored / CAPACITY) : Math.round(100F * storedSum / ringFill / CAPACITY);
            if (i == DATA_CAPACITY) return Math.round(capacityAvg);
            if (i == DATA_OVERLOAD) return overloadTicks;
            if (i >= DATA_GROUPS) return groupData((i - DATA_GROUPS) / GROUP_DATA + 1, (i - DATA_GROUPS) % GROUP_DATA);
            int s = (i - DATA_SLOTS) / 3, f = (i - DATA_SLOTS) % 3;
            if (s < 0 || s >= SLOTS) return 0;
            return switch (f) {
                case 0 -> slotKind[s];
                case 1 -> branchIsOff(s) ? BRANCH_OFF : conflict(s) ? BRANCH_CONFLICT : BRANCH_ON;
                default -> Math.round((float) slotSum[s] / Math.max(1, ringFill));
            };
        }
        @Override public void set(int i, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    public DistributionPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.DISTRIBUTION_PANEL.get(), pos, state,
                state.getBlock() instanceof DistributionPanelBlock b && b.kind() == DistributionPanelBlock.Kind.RESIDENTIAL ? LOAD_CENTER_PROFILE : PROFILE);
        applyMode();
    }

    public SupplyMode mode() { return mode; }
    public boolean wired() { return mode == SupplyMode.WIRED; }

    /** The last solve of a WIRED panel (null before the first, and for LEGACY_AREA). */
    @Nullable public WiringSupply.Result wiring() { return wiring; }

    /**
     * Sets the supply mode (the dev command, the authoring bridge; never automatic). The breakers start closed again, the old
     * mode's registrations go and the new mode's start on the next tick.
     */
    public void setMode(SupplyMode next) {
        if (next == mode) return;
        mode = next;
        applyMode();
        branchOff = 0;
        groupOff = 0;
        if (level != null && !level.isClientSide()) { BuildingPowerZone.unregister(level, worldPosition); WiringSupply.unregister(level, worldPosition); }
        footprint = null;
        nextZone = 0;
        wiring = null;
        solvedRevision = -1;
        syncedConflict = 0;
        sync();
    }

    /** The circuits joined to another panel, bit n = circuit n (server: the last solve; client: as synced). */
    public int conflictMask() {
        if (level != null && level.isClientSide()) return syncedConflict;
        int m = 0;
        if (mode == SupplyMode.WIRED && wiring != null) for (int c = 1; c <= WiringNode.CIRCUITS; c++) if (wiring.conflict[c]) m |= 1 << c;
        return m;
    }

    // ---- light groups (phase 3) ----

    private static int groupBit(int circuit, int group) {
        return circuit >= 1 && circuit <= WiringNode.BREAKERS && group >= 1 && group <= WiringNode.GROUPS ? 1 << (circuit - 1) * WiringNode.GROUPS + group - 1 : 0;
    }

    /** WIRED: light group 1-8 on circuit 1-4 is switched on (anything else: true). */
    public boolean groupOn(int circuit, int group) {
        return (groupOff & groupBit(circuit, group)) == 0;
    }

    /**
     * A wall switch flips light group 1-8 on circuit 1-4 (works without power; the state is saved): every switch of the group
     * follows, every light of it looks again now. Returns the new state.
     */
    public boolean toggleGroup(int circuit, int group) {
        int bit = groupBit(circuit, group);
        if (bit == 0 || mode != SupplyMode.WIRED) return true;
        groupOff ^= bit;
        sync();
        if (level instanceof ServerLevel server) applyGroup(server, circuit, group);
        return groupOn(circuit, group);
    }

    /** Sets light group 1-8 on circuit 1-4 (dev commands, templates); as a switch would. */
    public void setGroup(int circuit, int group, boolean on) {
        if (groupBit(circuit, group) != 0 && groupOn(circuit, group) != on) toggleGroup(circuit, group);
    }

    /** The group's switches to its state, its lights rechecked (loaded chunks only; the rest line up when they load). */
    private void applyGroup(ServerLevel server, int circuit, int group) {
        boolean on = groupOn(circuit, group);
        for (BlockPos p : WiringSupply.members(server, worldPosition, circuit, group)) {
            if (!server.isLoaded(p)) continue;
            BlockState s = server.getBlockState(p);
            if (s.getBlock() instanceof WallSwitchBlock sw) sw.set(server, p, s, on, true);
            else if (s.getBlock() instanceof BuildingLightBlock light) light.recheck(server, p, s);
        }
    }

    /**
     * After a solve: switches placed or loaded since then that disagree with their group are set to it and marked wired; the
     * ones this panel worked last time and no longer does (wire cut, group 0) lose the mark.
     */
    private void alignSwitches(ServerLevel server) {
        java.util.Set<BlockPos> now = new java.util.HashSet<>();
        for (java.util.Map.Entry<BlockPos, WiringSupply.Feed> e : WiringSupply.switches(server, worldPosition).entrySet()) {
            WiringSupply.Feed f = e.getValue();
            if (f.group() == WiringNode.ALWAYS || !server.isLoaded(e.getKey())) continue;
            now.add(e.getKey());
            BlockState s = server.getBlockState(e.getKey());
            if (s.getBlock() instanceof WallSwitchBlock sw) sw.set(server, e.getKey(), s, groupOn(f.circuit(), f.group()), true);
        }
        for (BlockPos p : switchesWorked) {
            if (now.contains(p) || !server.isLoaded(p) || WallSwitchBlock.panel(server, p) != null) continue;
            BlockState s = server.getBlockState(p);
            if (s.getBlock() instanceof WallSwitchBlock sw) sw.set(server, p, s, s.getValue(WallSwitchBlock.ON), false);
        }
        switchesWorked = now;
    }

    /** One field of a circuit's group data for the screen (GROUP_*). */
    private int groupData(int circuit, int field) {
        WiringSupply.Result r = wiring;
        if (mode != SupplyMode.WIRED || r == null || circuit < 1 || circuit > WiringNode.BREAKERS) return 0;
        if (field == GROUP_OFF) return groupOff >> (circuit - 1) * WiringNode.GROUPS & 0xFF;
        if (field == GROUP_OTHERS) return r.others[circuit];
        if (field == GROUP_USED) {
            int m = 0;
            for (int g = 0; g <= WiringNode.GROUPS; g++) if (r.used(circuit, g)) m |= 1 << g;
            if (r.used(circuit, WiringNode.PHOTOCELL)) m |= 1 << PHOTOCELL_BIT;
            return m;
        }
        int g = field - GROUP_LIGHTS;
        return r.lights[circuit][g == PHOTOCELL_BIT ? WiringNode.PHOTOCELL : g];
    }

    /** The breakers of the mode: LEGACY_AREA lighting, outlets, the top cable; WIRED four circuits and the cable's row. */
    private void applyMode() {
        java.util.Arrays.fill(slotKind, KIND_NONE);
        if (mode == SupplyMode.WIRED) {
            for (int n = 0; n < WiringNode.BREAKERS; n++) slotKind[n] = KIND_WIRE + n;
            slotKind[WiringNode.BREAKERS] = KIND_WIRED_CABLE;
        } else {
            slotKind[0] = KIND_LIGHTING;
            slotKind[1] = KIND_OUTLETS;
            slotKind[2] = KIND_CABLE;
        }
    }

    public IEnergyStorage input() { return inputStorage; }

    /** The circuits' load, FE/t (about a 2 s average); a generator inlet asks its generator for this much when it is more than the rating. */
    public float loadNow() { return loadAvg; }

    /** What the feeding network's sources could give this tick (SupplyProbe; summed if several networks feed it). */
    public void networkSupply(long gameTime, int available) {
        if (capacityTick != gameTime) { capacityTick = gameTime; capacityThisTick = 0; }
        capacityThisTick = (int) Math.min(Integer.MAX_VALUE, (long) capacityThisTick + Math.max(0, available));
    }
    public boolean live() { return mainOn && !tripped; }

    /** The outlet circuit has power: main on, the outlets branch on, something in the buffer (Power Outlets V1). */
    public boolean outletsLive() { return mode == SupplyMode.LEGACY_AREA && live() && !branchIsOff(slotOf(KIND_OUTLETS)) && stored > 0; }

    /** What a wall outlet's plug draws, from the buffer, counted as the outlet circuit's load. */
    public int drawOutlets(int fe, boolean simulate) {
        if (!outletsLive() || fe <= 0) return 0;
        int given = Math.min(fe, stored);
        if (!simulate) { stored -= given; slotLoadAdd(slotOf(KIND_OUTLETS), given); setChanged(); }
        return given;
    }
    /** The lighting circuit has power: main on, the lighting branch on, something in the buffer (Building Lights V1). */
    public boolean lightingLive() { return mode == SupplyMode.LEGACY_AREA && live() && !branchIsOff(slotOf(KIND_LIGHTING)) && stored > 0; }

    /**
     * WIRED: circuit n (1-4) has power: main on, not tripped, its breaker closed, not joined to another panel, something in
     * the buffer (design 5.2).
     */
    public boolean circuitLive(int circuit) {
        int slot = circuit - 1;
        return mode == SupplyMode.WIRED && circuit >= 1 && circuit <= WiringNode.BREAKERS && live() && !branchIsOff(slot) && !conflict(slot) && stored > 0;
    }

    /** WIRED: what a device on circuit n takes, from the buffer, counted as that circuit's load. */
    public int drawCircuit(int circuit, int fe, boolean simulate) {
        if (!circuitLive(circuit) || fe <= 0) return 0;
        int given = Math.min(fe, stored);
        if (!simulate) { stored -= given; slotLoadAdd(circuit - 1, given); setChanged(); }
        return given;
    }

    /** What the building's lights and the emergency lights' chargers take, from the buffer, counted as the lighting circuit's load. */
    public int drawLighting(int fe, boolean simulate) {
        if (!lightingLive() || fe <= 0) return 0;
        int given = Math.min(fe, stored);
        if (!simulate) { stored -= given; slotLoadAdd(slotOf(KIND_LIGHTING), given); setChanged(); }
        return given;
    }
    private boolean branchIsOff(int slot) { return slot >= 0 && (branchOff & (1 << slot)) != 0; }
    private int slotOf(int kind) { for (int i = 0; i < SLOTS; i++) if (slotKind[i] == kind) return i; return -1; }
    private void slotLoadAdd(int slot, int fe) { if (slot >= 0) tickLoad[slot] += fe; }
    private int cableSlot() { return slotOf(mode == SupplyMode.WIRED ? KIND_WIRED_CABLE : KIND_CABLE); }
    /** A WIRED circuit's slot whose wire also reaches another panel. */
    private boolean conflict(int slot) {
        return mode == SupplyMode.WIRED && wiring != null && slot >= 0 && slot < WiringNode.BREAKERS && wiring.conflict[slot + 1];
    }
    private int wiringFlags() {
        if (mode != SupplyMode.WIRED) return 0;
        int f = FLAG_WIRED;
        if (wiring != null) {
            for (boolean c : wiring.conflict) if (c) f |= FLAG_CONFLICT;
            if (wiring.partial) f |= FLAG_PARTIAL;
        }
        return f;
    }
    private int zoneFlags() {
        if (footprint == null) return 0;
        return switch (footprint.status()) { case OK -> FLAG_ZONE_OK; case OPEN -> FLAG_ZONE_OPEN; case DUPLICATE -> FLAG_DUPLICATE; };
    }

    // ---- server tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (mode == SupplyMode.LEGACY_AREA) {
            if (footprint == null || now >= nextZone) {
                footprint = BuildingPowerZone.register(server, worldPosition, BuildingPowerZone.compute(server, worldPosition));
                nextZone = now + ZONE_PERIOD;
            }
        } else if (wiring == null || solvedRevision != WiringStorage.revision() || now >= nextSolve) {
            // after any wiring edit or chunk load (the revision), else every 5 s (a device placed on a tap, a chunk unloaded)
            solve(server, now);
        }
        // the top port seeds its cable network; the network pulls from outputStorage this tick (level END)
        if (live()) PowerCableTransfer.transferFrom(server, worldPosition, Direction.UP, outputStorage, MAX_OUT);
        // hidden-wiring circuits: the lights (energy/BuildingLights) and the outlets' plugs (energy/PowerPlugs) draw from the
        // buffer themselves, through drawLighting / drawOutlets (LEGACY_AREA) or drawCircuit (WIRED)
        // averages over about 2 s; the cable circuit's load is what the network took last tick (level END)
        float supply = receiveTick == now - 1 || receiveTick == now ? receivedThisTick : 0;
        supplyAvg += (supply - supplyAvg) * 0.05F;
        float load = 0;
        int at = ringAt;
        for (int s = 0; s < SLOTS; s++) {
            slotLoad[s] += (tickLoad[s] - slotLoad[s]) * 0.05F;
            int draw = Math.round(tickLoad[s]);
            slotSum[s] += draw - slotRing[s][at];
            slotRing[s][at] = draw;
            load += tickLoad[s]; tickLoad[s] = 0;
        }
        loadAvg += (load - loadAvg) * 0.05F;
        // the storage a whole window ago is at the same phase of every period: their difference is the real drift
        int before = storedRing[at];
        falling = ringFill >= VIEW_WINDOW && stored - before < -VIEW_WINDOW;   // losing more than 1 FE a tick
        storedSum += stored - before;
        storedRing[at] = stored;
        ringAt = (at + 1) % VIEW_WINDOW;
        ringFill = Math.min(VIEW_WINDOW, ringFill + 1);
        // the capacity: the network's supply as the cables last settled it (level END), at most what the input takes
        float capacity = capacityTick == now - 1 || capacityTick == now ? Math.min(MAX_IN, capacityThisTick) : 0;
        capacityAvg += (capacity - capacityAvg) * 0.05F;
        // overload: power arrives but the circuits take all of it and the buffer stays dry (the load wants more than the
        // supply can give); a dead source is no trip, the building just has no power
        if (live() && stored < CAPACITY / 20 && supplyAvg > 0.5F && loadAvg >= supplyAvg * 0.95F) {
            if (++overloadTicks >= TRIP_TICKS) trip(server);
        } else overloadTicks = 0;
    }

    private void solve(ServerLevel server, long now) {
        wiring = WiringSupply.solve(server, worldPosition, getBlockState());
        solvedRevision = WiringStorage.revision();
        nextSolve = now + ZONE_PERIOD;
        int conflict = conflictMask();
        if (conflict != syncedConflict) { syncedConflict = conflict; sync(); }
        alignSwitches(server);
    }

    /**
     * WIRED: solves the wiring now instead of on the next tick (phase 5: the wrench's group picker counts a device tapped a
     * moment ago; a group just set lines its switches up and lets its light look again at once).
     */
    public void solveNow() {
        if (level instanceof ServerLevel server && mode == SupplyMode.WIRED) solve(server, server.getGameTime());
    }

    private void trip(ServerLevel server) {
        tripped = true; overloadTicks = 0;
        server.playSound(null, worldPosition, com.antaurora.apofirstlight.registry.AflSounds.BREAKER_TRIP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        sync();
    }

    // ---- breakers (from the menu) ----

    public void toggleMain(Player player) {
        if (tripped) { tripped = false; mainOn = false; }   // a tripped main resets to off first, as a real handle does
        else mainOn = !mainOn;
        click(player); sync();
    }

    public void toggleBranch(int slot, Player player) {
        if (slot < 0 || slot >= SLOTS || slotKind[slot] == KIND_NONE || slotKind[slot] == KIND_WIRED_CABLE) return;   // the cable's row has no breaker
        branchOff ^= 1 << slot;
        click(player); sync();
    }

    private void click(Player player) {
        if (level != null) level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    // ---- menu: the door stands open while someone looks ----

    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }

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
            return slot < 0 || slot >= SLOTS || slotKind[slot] == KIND_NONE || slotKind[slot] == KIND_WIRED_CABLE || branchIsOff(slot);   // spares stand off
        }
        return false;
    }

    /**
     * Each pull box shows only while a cable plugs into its port (the top one above, the bottom one below); on a log wall's
     * pad the port_*_log set (the same pull box at the face centre, the conduit to the panel standing out; LogMount).
     */
    @Override
    public boolean meshPartVisible(String part) {
        boolean log = part.endsWith("_log");
        String port = log ? part.substring(0, part.length() - 4) : part;
        boolean up = port.equals("port_up");
        if (!up && !port.equals("port_down")) return true;
        return log == LogMount.mounted(getBlockState()) && level != null && DistributionPanelBlock.cabled(level, worldPosition, up ? Direction.UP : Direction.DOWN);
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
        if (level != null && !level.isClientSide()) { BuildingPowerZone.unregister(level, worldPosition); WiringSupply.unregister(level, worldPosition); }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) { BuildingPowerZone.unregister(level, worldPosition); WiringSupply.unregister(level, worldPosition); }
    }

    // ---- save / sync ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(MAIN_KEY, mainOn);
        tag.putBoolean(TRIP_KEY, tripped);
        tag.putInt(OFF_KEY, branchOff);
        tag.putInt(ENERGY_KEY, stored);
        tag.putString(MODE_KEY, mode == SupplyMode.WIRED ? "wired" : "legacy_area");
        if (groupOff != 0) tag.putInt(GROUP_KEY, groupOff);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mainOn = tag.getBoolean(MAIN_KEY);
        tripped = tag.getBoolean(TRIP_KEY);
        branchOff = tag.getInt(OFF_KEY);
        groupOff = tag.getInt(GROUP_KEY);   // none: every group on
        stored = Math.max(0, Math.min(CAPACITY, tag.getInt(ENERGY_KEY)));
        // no field: an old save or template, the building footprint as before (design 2: decided by the field alone)
        mode = "wired".equals(tag.getString(MODE_KEY)) ? SupplyMode.WIRED : SupplyMode.LEGACY_AREA;
        applyMode();
        syncedConflict = tag.getInt(CONFLICT_KEY);   // only in the update tag (the client); a save has none
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putInt(CONFLICT_KEY, conflictMask());
        return tag;
    }
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
