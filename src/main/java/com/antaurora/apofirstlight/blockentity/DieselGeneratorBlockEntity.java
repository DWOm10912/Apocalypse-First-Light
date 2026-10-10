package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.DieselGeneratorBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.PowerCableTransfer;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Diesel standby generator V1 (docs/machines/diesel_standby_generator_v1.md), on the master cell. Numbers (the user left
 * them to me, 2026-10-09):
 * <ul>
 *   <li>rated {@link #RATED} FE/t (1 FE/t is about 0.25 kW, so about 100 kW): the most the cable network can take from it in
 *   a tick, through the standard power port in the master's back face; it gives only what is taken (no buffer). 400
 *   because the A1 store's cold start after a blackout took about three energy cells (384 FE/t) before the panel stopped
 *   tripping (user test, 2026-10-09);</li>
 *   <li>a {@link #TANK} L diesel tank (1 mB = 1 L), filled by a jerry can through the fill box (block/DieselGeneratorBlock)
 *   or by pipe through the standard fluid port on the step end ({@link #pipeInlet}, fill only);</li>
 *   <li>fuel {@link #IDLE_L} + {@link #L_PER_KW} x kW litres a game hour (1000 ticks): about 1.5 L/h idling, 29.5 L/h at
 *   full load, as a real 100 kW set;</li>
 *   <li>start: {@link #CRANK_TICKS} ticks of cranking (the key at START), then it runs (7 ticks: the engine in the start
 *   recording fires 0.35 s after the starter engages, tools/build-diesel-generator-sounds-v1.mjs); with no fuel it cranks and fails
 *   (fault lamp). Running out of fuel stops it with the fault lamp; the next start clears the fault. No overload trip of
 *   its own: the panel downstream trips on overload; past full load the load needle stays at 100 %.</li>
 * </ul>
 * The panel shows (AFL Animated Block Mesh Runtime value channels, tools/build-diesel-generator-v1.mjs): the load (the
 * load the network took, smoothed over about 2 s, 0..125 %), the fuel (also the tank's own gauge), the coolant (warms to
 * about 85 C in a minute running, cools when stopped) and the oil pressure (about 4 bar running) as show only, the run
 * hours (game hours; four whole digits and tenths), three lamps (run, low fuel under 1/8, fault) and the key switch.
 * The rain flap on the stack is open and the radiator fan turns while it runs. Synced when a shown value moves (at most twice a second for the
 * needles) or the state changes.
 */
public class DieselGeneratorBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/diesel_generator.json");
    public static final int RATED = 400, TANK = 600, LOW_FUEL = TANK / 8, CRANK_TICKS = 7, HOUR_TICKS = 1000;
    /** The run loop, once running: waits 12 ticks (the start sound's flare settles), fades in over 16 under its fade-out. */
    public static final int[] LOOP_FADE_IN = {12, 16};
    /**
     * Infected hearing (noise/NoiseSystem, MACHINE, from the engine): the starter 12 blocks, the engine catching and then
     * running every NOISE_INTERVAL ticks 24, the range the player hears it in (sounds.json attenuation 24). Over 10, so
     * infected may break through to it (infected/breach); a running set is a lure. Stopping makes none.
     */
    public static final double CRANK_NOISE = 12, RUN_NOISE = 24;
    public static final int NOISE_INTERVAL = 40;
    public static final double IDLE_L = 1.5, L_PER_KW = 0.28, KW_PER_FE_TICK = 0.25;
    /** Key positions on the 'key' channel (OFF, RUN, START: tools/build-diesel-generator-v1.mjs KEY_RUN). */
    public static final double KEY_RUN = 0.529412;
    private static final float AMBIENT_C = 20F, WARM_C = 82F;
    private static final int NEEDLE_SYNC_TICKS = 10;
    private static final String FUEL_KEY = "Fuel", MODE_KEY = "Mode", CRANK_KEY = "Crank", FAULT_KEY = "Fault", HOURS_KEY = "Hours",
            TEMP_KEY = "Temp", OIL_KEY = "Oil", LOAD_KEY = "Load", DEBT_KEY = "FuelDebt";

    public enum Mode { OFF, CRANKING, RUNNING }

    private final FluidTank fuel = new FluidTank(TANK, stack -> stack.getFluid().isSame(AflFluids.DIESEL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private Mode mode = Mode.OFF;
    private int crank;
    private boolean fault;
    private double hours, fuelDebt;
    private float temp = AMBIENT_C, oil, loadAvg;
    private long extractTick = Long.MIN_VALUE;
    private int extractedThisTick;
    /** What clients were last sent (needle steps, lamps, digits). */
    private long sentKey = Long.MIN_VALUE;
    private long lastSync = Long.MIN_VALUE;
    /** Client only (client/DieselGeneratorClient): the mode last seen, the tick of the last exhaust puff. */
    public Mode clientMode = Mode.OFF;
    public long clientPuff;

    /** The port: gives up to RATED a tick while it runs; nothing else. */
    private final IEnergyStorage output = new IEnergyStorage() {
        private int budget() {
            long now = level == null ? 0 : level.getGameTime();
            if (extractTick != now) { extractTick = now; extractedThisTick = 0; }
            return mode == Mode.RUNNING ? Math.max(0, RATED - extractedThisTick) : 0;
        }

        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }

        @Override public int extractEnergy(int max, boolean simulate) {
            int give = Math.max(0, Math.min(max, budget()));
            if (!simulate) extractedThisTick += give;
            return give;
        }

        @Override public int getEnergyStored() { return budget(); }
        @Override public int getMaxEnergyStored() { return RATED; }
        @Override public boolean canExtract() { return mode == Mode.RUNNING; }
        @Override public boolean canReceive() { return false; }
    };
    private LazyOptional<IEnergyStorage> outputCapability = LazyOptional.of(() -> output);

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.DIESEL_GENERATOR.get(), pos, state, PROFILE);
    }

    // ---- state ----

    public FluidTank fuelTank() { return fuel; }

    /** The pipe port's view of the tank (DieselGeneratorPortBlockEntity): diesel goes in, nothing comes out. */
    private final net.minecraftforge.fluids.capability.IFluidHandler inlet = new net.minecraftforge.fluids.capability.IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public @NotNull FluidStack getFluidInTank(int tank) { return fuel.getFluid(); }
        @Override public int getTankCapacity(int tank) { return TANK; }
        @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return fuel.isFluidValid(stack); }
        @Override public int fill(FluidStack resource, FluidAction action) { return fuel.fill(resource, action); }
        @Override public @NotNull FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public @NotNull FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    public net.minecraftforge.fluids.capability.IFluidHandler pipeInlet() { return inlet; }
    public Mode mode() { return mode; }
    public boolean fault() { return fault; }
    public boolean engineOn() { return mode != Mode.OFF; }
    public boolean running() { return mode == Mode.RUNNING; }
    public int fuelLitres() { return fuel.getFluidAmount(); }
    public float loadFe() { return loadAvg; }
    public double hours() { return hours; }
    private Direction facing() { return getBlockState().getValue(DieselGeneratorBlock.FACING); }

    /** The panel: turn the key to START (a fault clears; the engine cranks, then runs or fails). */
    public void start(Player player) {
        if (mode != Mode.OFF) return;
        fault = false;
        mode = Mode.CRANKING;
        crank = 0;
        // an empty tank: the starter alone (the start sound has the engine firing in it). The start recording runs on into the
        // engine running, so it is heard as far as the running engine; the crank alone as far as cranking
        if (fuel.getFluidAmount() > 0) sound(AflSounds.DIESEL_GENERATOR_START.get(), RUN_NOISE, 1.0F);
        else sound(AflSounds.DIESEL_GENERATOR_CRANK.get(), CRANK_NOISE, 1.0F);
        noise(CRANK_NOISE);
        click();
        sync(true);
    }

    /** The panel (the key to OFF) or the emergency stop. */
    public void stop(boolean emergency) {
        if (mode == Mode.OFF) return;
        boolean wasRunning = mode == Mode.RUNNING;
        mode = Mode.OFF;
        crank = 0;
        if (wasRunning) sound(AflSounds.DIESEL_GENERATOR_STOP.get(), RUN_NOISE, 1.0F);
        click();
        sync(true);
    }

    private void click() {
        if (level == null) return;
        Vec3 at = panelWorld();
        level.playSound(null, at.x, at.y, at.z, AflSounds.DISTRIBUTION_PANEL_LATCH.get(), SoundSource.BLOCKS, 0.6F, 1.35F);
    }

    /** Heard as far as the noise that goes with it (noise/RangedSound): the crank 12, the start and the stop 24. */
    private void sound(SoundEvent sound, double radius, float pitch) {
        if (!(level instanceof ServerLevel server)) return;
        com.antaurora.apofirstlight.noise.RangedSound.play(server, engineWorld(), sound, SoundSource.BLOCKS, radius, 1.0F, pitch);
    }

    private void noise(double radius) {
        if (level instanceof ServerLevel server) NoiseSystem.emit(new NoiseEvent(null, engineWorld(), NoiseType.MACHINE,
                server.getGameTime(), AflBlocks.DIESEL_GENERATOR.getId(), radius), server);
    }

    private Vec3 panelWorld() {
        return DieselGeneratorBlock.toWorld(DieselGeneratorBlock.PANEL_MIDDLE, origin(), facing());
    }

    /** The engine's middle (sounds), world. */
    public Vec3 engineWorld() {
        return DieselGeneratorBlock.toWorld(new Vec3(26, 12, 8), origin(), facing());
    }

    /** The stack's top (smoke), world. */
    public Vec3 stackWorld() {
        return DieselGeneratorBlock.toWorld(DieselGeneratorBlock.STACK_TOP, origin(), facing());
    }

    private BlockPos origin() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof DieselGeneratorBlock block ? block.origin(worldPosition, state) : worldPosition;
    }

    // ---- server tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        // what the network took last tick (it pulls at the level's end, after the block entities tick)
        int load = extractTick == now - 1 ? extractedThisTick : 0;
        float ratio = load / (float) RATED;
        switch (mode) {
            case CRANKING -> {
                oil += (1.2F - oil) * 0.15F;
                if (++crank >= CRANK_TICKS) {
                    if (fuel.getFluidAmount() <= 0) { mode = Mode.OFF; fault = true; sync(true); }   // cranked, never caught
                    else { mode = Mode.RUNNING; noise(RUN_NOISE); sync(true); }
                }
            }
            case RUNNING -> {
                // fuel: idle + per kW, litres a game hour
                fuelDebt += (IDLE_L + L_PER_KW * load * KW_PER_FE_TICK) / HOUR_TICKS;
                while (fuelDebt >= 1) {
                    FluidStack drained = fuel.drain(1, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    fuelDebt -= 1;
                    if (drained.isEmpty()) { mode = Mode.OFF; fault = true; fuelDebt = 0; sound(AflSounds.DIESEL_GENERATOR_STOP.get(), RUN_NOISE, 0.9F); sync(true); break; }
                }
                hours += 1.0 / HOUR_TICKS;
                temp += (WARM_C + 6F * ratio - temp) / 300F;
                oil += (4.6F - 0.6F * ratio - oil) * 0.1F;
                setChanged();
            }
            default -> {
                temp += (AMBIENT_C - temp) / 1200F;
                oil += (0F - oil) * 0.2F;
            }
        }
        if (mode != Mode.RUNNING) ratio = 0;
        loadAvg += (ratio * RATED - loadAvg) * 0.025F;
        // the port seeds its cable network; the network pulls from output at the level's end
        if (mode == Mode.RUNNING) PowerCableTransfer.transferFrom(server, worldPosition, facing().getOpposite(), output, RATED);
        // running: heard again every NOISE_INTERVAL ticks, staggered by position so several sets do not query together
        if (mode == Mode.RUNNING && Math.floorMod(now + worldPosition.asLong(), NOISE_INTERVAL) == 0) noise(RUN_NOISE);
        sync(false);
    }

    // ---- what the panel shows (value channels, both sides from the synced fields) ----

    public double loadNeedle() { return Math.max(0, Math.min(1, loadAvg / RATED / 1.25)); }
    public double fuelNeedle() { return Math.max(0, Math.min(1, fuel.getFluidAmount() / (double) TANK)); }
    public double tempNeedle() { return Math.max(0, Math.min(1, (temp - 40) / 80.0)); }
    public double oilNeedle() { return Math.max(0, Math.min(1, oil / 10.0)); }
    public double keyValue() { return mode == Mode.CRANKING ? 1 : mode == Mode.RUNNING || fault ? KEY_RUN : 0; }
    /** Hour meter drum i (0 = thousands .. 3 = units, 4 = tenths): its digit. */
    public int hourDigit(int i) {
        long tenths = (long) Math.floor(hours * 10) % 100000;
        long[] div = {10000, 1000, 100, 10, 1};
        return (int) (tenths / div[i] % 10);
    }
    public boolean runLamp() { return mode == Mode.RUNNING; }
    public boolean lowLamp() { return fuel.getFluidAmount() < LOW_FUEL; }
    public boolean faultLamp() { return fault; }

    /** The shown state as one number: a sync goes out when it changes. */
    private long shownKey() {
        long k = mode.ordinal();
        k = k * 2 + (fault ? 1 : 0);
        k = k * 2 + (lowLamp() ? 1 : 0);
        k = k * 101 + Math.round(loadNeedle() * 100);
        k = k * 201 + Math.round(fuelNeedle() * 200);
        k = k * 101 + Math.round(tempNeedle() * 100);
        k = k * 101 + Math.round(oilNeedle() * 100);
        k = k * 100000 + (long) Math.floor(hours * 10) % 100000;
        return k;
    }

    private void sync(boolean now) {
        if (level == null || level.isClientSide) return;
        long key = shownKey(), time = level.getGameTime();
        if (key == sentKey && !now) return;
        if (!now && time - lastSync < NEEDLE_SYNC_TICKS) return;
        sentKey = key;
        lastSync = time;
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // ---- AFL Animated Block Mesh Runtime ----

    @Override
    protected boolean meshAnimationTarget(String channel) {
        return meshChannelValue(channel) > 0.5;
    }

    @Override
    public double meshChannelValue(String channel) {
        BlockState state = getBlockState();
        return switch (channel) {
            case "doors" -> state.hasProperty(DieselGeneratorBlock.OPEN) && state.getValue(DieselGeneratorBlock.OPEN) ? 1 : 0;
            case "fill" -> state.hasProperty(DieselGeneratorBlock.FILL) && state.getValue(DieselGeneratorBlock.FILL) ? 1 : 0;
            case "flap" -> running() ? 1 : 0;
            case "fan" -> running() ? 1 : 0;   // a loop channel: the fan's speed (it spins up and down over 1.5 s)
            case "load" -> loadNeedle();
            case "fuel" -> fuelNeedle();
            case "temp" -> tempNeedle();
            case "oil" -> oilNeedle();
            case "key" -> keyValue();
            default -> channel.length() == 2 && channel.charAt(0) == 'h' ? hourDigit(channel.charAt(1) - '0') / 10.0 : 0;
        };
    }

    /** Only the doors and the fill lid move the hit mesh; needles, the key, the drums, the flap and the fan keep it at 0. */
    @Override
    public boolean meshChannelAffectsHits(String channel) {
        return channel.equals("doors") || channel.equals("fill");
    }

    @Override
    public boolean meshPartVisible(String part) {
        return switch (part) {
            case "lamp_run" -> !runLamp();
            case "lamp_run_lit" -> runLamp();
            case "lamp_low" -> !lowLamp();
            case "lamp_low_lit" -> lowLamp();
            case "lamp_fault" -> !faultLamp();
            case "lamp_fault_lit" -> faultLamp();
            default -> true;
        };
    }

    @Override
    public boolean meshPartEmissive(String part) {
        return part.endsWith("_lit");
    }

    // ---- capability: the port ----

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side == facing().getOpposite()) return outputCapability.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        outputCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        outputCapability = LazyOptional.of(() -> output);
    }

    // ---- save, sync ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel.readFromNBT(tag.getCompound(FUEL_KEY));
        Mode[] modes = Mode.values();
        int m = tag.getInt(MODE_KEY);
        mode = m >= 0 && m < modes.length ? modes[m] : Mode.OFF;
        crank = tag.getInt(CRANK_KEY);
        fault = tag.getBoolean(FAULT_KEY);
        hours = Math.max(0, tag.getDouble(HOURS_KEY));
        temp = tag.contains(TEMP_KEY) ? tag.getFloat(TEMP_KEY) : AMBIENT_C;
        oil = tag.getFloat(OIL_KEY);
        loadAvg = tag.getFloat(LOAD_KEY);
        fuelDebt = tag.getDouble(DEBT_KEY);
        refreshMeshAnimationTargets();   // client: the needles and lamps from the packet
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(FUEL_KEY, fuel.writeToNBT(new CompoundTag()));
        tag.putInt(MODE_KEY, mode.ordinal());
        tag.putInt(CRANK_KEY, crank);
        tag.putBoolean(FAULT_KEY, fault);
        tag.putDouble(HOURS_KEY, hours);
        tag.putFloat(TEMP_KEY, temp);
        tag.putFloat(OIL_KEY, oil);
        tag.putFloat(LOAD_KEY, loadAvg);
        tag.putDouble(DEBT_KEY, fuelDebt);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Jade: the state word. */
    public Component statusText() {
        String key = switch (mode) { case RUNNING -> "running"; case CRANKING -> "cranking"; default -> fault ? "fault" : "off"; };
        return Component.translatable("jade.apocalypse_firstlight.diesel_generator." + key);
    }
}
