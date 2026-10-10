package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PortableDieselGeneratorBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflFluids;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * Portable diesel generator V1 (docs/machines/portable_diesel_generator_v1.md): a 5 kW open-frame diesel set.
 * <ul>
 *   <li>{@link #RATED} FE/t (5 kW at 0.25 kW a FE/t, as the standby set) through the panel's four sockets, shared
 *   (block/PortableDieselGeneratorBlock is the socket host); nothing else: no cable port (V1, user 2026-10-09).</li>
 *   <li>A {@link #TANK} L diesel tank (1 mB = 1 L), filled by a jerry can poured into the open filler.</li>
 *   <li>Fuel {@link #IDLE_L} + {@link #L_PER_KW} x kW litres a game hour (1000 ticks): 1.75 L/h at full load (a 186F-class
 *   engine), 0.4 L/h idling; a full tank lasts about 8.6 game hours at full load.</li>
 *   <li>The recoil start, a QTE (2026-10-09, user: "我其实比较喜欢那种QTE互动"): the player grips the T handle
 *   ({@link #grip}, the block's use), the client fixes the view and shows a timing bar (client/PortableGeneratorPull); each
 *   press is a pull ({@link #pull}, a packet: timed in the bar's zone or not) of {@link #PULL_TICKS} ticks, the handle out
 *   and guided back. At the yank's end ({@link #CATCH_AT}) a cold engine catches on {@link #COLD_GOOD} of well-timed pulls
 *   and {@link #COLD_POOR} of the rest, a warm one (it ran within {@link #WARM_TICKS} ticks) on {@link #WARM_GOOD} /
 *   {@link #WARM_POOR}, an empty tank never. A pull costs stamina (stamina_v1.json costs.recoil_pull). Real small diesels
 *   often want two or three good pulls cold and one warm.</li>
 *   <li>The breakers: the plugged loads' demand (what they ask for, averaged over about 2.5 s) over the rating trips them;
 *   the output stays off until a breaker is pressed ({@link #resetBreakers}).</li>
 *   <li>Infected hearing (noise/NoiseSystem, MACHINE): a pull 8 blocks, the engine catching and running every
 *   {@link #NOISE_INTERVAL} ticks {@link #RUN_NOISE} (an open frame, louder than the canopy standby set's 24).</li>
 * </ul>
 * Synced: the mode, the pull's start and who pulls, the breakers, the sockets, the voltmeter, the fuel and the hours.
 */
public class PortableDieselGeneratorBlockEntity extends AflAnimatedMeshBlockEntity {
    public static final ResourceLocation PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/portable_diesel_generator.json");
    public static final int RATED = 20, TANK = 15, HOUR_TICKS = 1000, SOCKETS = 4;
    public static final double IDLE_L = 0.4, L_PER_KW = 0.27, KW_PER_FE_TICK = 0.25;
    /**
     * A pull: the handle out from YANK_AT, guided back from RELEASE_AT, the next pull from PULL_TICKS; it catches at CATCH_AT,
     * 0.45 s in, as the recording's first firing comes after its pull (tools/build-portable-generator-sounds-v1.mjs: the
     * pull sound ends there and the start sound begins with the firing).
     */
    public static final int PULL_TICKS = 12, YANK_AT = 0, RELEASE_AT = 6, CATCH_AT = 9;
    /** The run loop, once running: waits 20 ticks, fades in over 18, under the start sound's last second. */
    public static final int[] LOOP_FADE_IN = {20, 18};
    public static final float COLD_GOOD = 0.55F, COLD_POOR = 0.12F, WARM_GOOD = 1.0F, WARM_POOR = 0.6F;
    /** A grip with no pull for this long lets go (ticks); and when the player walks off or dies. */
    public static final int GRIP_TIMEOUT = 600;
    public static final int WARM_TICKS = 1200;
    public static final double PULL_NOISE = 8, RUN_NOISE = 32;   // a pull under the breach threshold (10): the starter, a few compressions
    public static final int NOISE_INTERVAL = 40;
    /** The voltmeter's reading while the output is on (of its 150 V scale). */
    public static final double VOLTS = 121.0 / 150.0;
    /** Demand average per tick (about 2.5 s) and the margin over the rating that trips the breakers. */
    private static final float DEMAND_RATE = 0.02F, TRIP_MARGIN = 0.5F;
    private static final String FUEL_KEY = "Fuel", RUNNING_KEY = "Running", HOURS_KEY = "Hours", DEBT_KEY = "FuelDebt", PULL_KEY = "PullStart",
            PULLER_KEY = "Puller", WARM_KEY = "WarmUntil", GRIP_KEY = "Gripper", GOOD_KEY = "PullGood", CATCHES_KEY = "PullCatches", TRIP_KEY = "Tripped", SOCKETS_KEY = "Sockets", DEMAND_KEY = "Demand";

    private final FluidTank fuel = new FluidTank(TANK, stack -> stack.getFluid().isSame(AflFluids.DIESEL.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            sync();
        }
    };
    private boolean running, tripped;
    private double hours, fuelDebt;
    private long pullStart = -1, warmUntil = Long.MIN_VALUE, gripTick;
    private int puller = -1, gripper = -1, usedSockets;
    private boolean pullGood, pullCatches;
    private float demand;
    // what the sockets gave and were asked for, per tick (the loads pull during the level's tick, before or after ours)
    private long drawTick = Long.MIN_VALUE;
    private int givenThisTick, askedThisTick, givenLastTick, askedLastTick;
    private long shownHourTenths = -1;
    /** Client only (client/PortableGeneratorPull): the running state last seen, the tick of the last exhaust puff. */
    public boolean clientRunning;
    public long clientPuff;
    /** Client only: the last pull's start, kept past the server's reset (the view's shake outlasts the pull). */
    public long clientLastPull = Long.MIN_VALUE;

    public PortableDieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.PORTABLE_DIESEL_GENERATOR.get(), pos, state, PROFILE);
    }

    // ---- state ----

    public FluidTank fuelTank() { return fuel; }
    public boolean running() { return running; }
    public boolean tripped() { return tripped; }
    public boolean outputOn() { return running && !tripped; }
    public int fuelLitres() { return fuel.getFluidAmount(); }
    public double hours() { return hours; }
    public int puller() { return puller; }
    /** Who holds the T handle (the QTE), or -1. */
    public int gripper() { return gripper; }
    public long pullStart() { return pullStart; }
    /** A pull under way (both sides: the start is synced, the clock is the level's). */
    public boolean pulling() { return pullStart >= 0 && level != null && level.getGameTime() - pullStart < PULL_TICKS; }
    /** Ticks into the pull, with the partial tick (client), or -1. */
    public double pullAge(float partialTick) {
        return pullStart < 0 || level == null ? -1 : level.getGameTime() - pullStart + partialTick;
    }
    public float loadFe() { return Math.min(demand, RATED); }
    public boolean socketUsed(int socket) { return socket >= 0 && socket < SOCKETS && (usedSockets >> socket & 1) != 0; }

    public void setSocketUsed(int socket, boolean used) {
        if (socket < 0 || socket >= SOCKETS) return;
        int next = used ? usedSockets | 1 << socket : usedSockets & ~(1 << socket);
        if (next != usedSockets) { usedSockets = next; sync(); }
    }

    // ---- controls ----

    /** The T handle taken (the QTE starts on the client): refused while running or held by someone else. */
    public boolean grip(Player player) {
        if (level == null || running || gripper >= 0 && gripper != player.getId()) return false;
        gripper = player.getId();
        gripTick = level.getGameTime();
        sync();
        return true;
    }

    /** The handle let go (the player left the QTE). */
    public void release(Player player) {
        if (gripper != player.getId()) return;
        gripper = -1;
        sync();
    }

    /** One pull by the player holding the handle (ignored while running or while a pull is under way); good: timed in the zone. */
    public void pull(net.minecraft.server.level.ServerPlayer player, boolean good) {
        if (level == null || running || pulling() || gripper != player.getId()) return;
        pullStart = level.getGameTime();
        puller = player.getId();
        pullGood = good;
        gripTick = pullStart;
        com.antaurora.apofirstlight.stamina.PlayerStamina.spend(player, com.antaurora.apofirstlight.stamina.StaminaConfig.get().costs.recoilPull, false);
        // decided now, so the sound is the right one: a catching pull leads into the start sound (one recording), a failing
        // one turns the engine over and lets it stop (two recordings, at random)
        float chance = pullStart < warmUntil ? (good ? WARM_GOOD : WARM_POOR) : (good ? COLD_GOOD : COLD_POOR);
        pullCatches = fuel.getFluidAmount() > 0 && level.getRandom().nextFloat() < chance;
        sound((pullCatches ? AflSounds.PORTABLE_GENERATOR_PULL : AflSounds.PORTABLE_GENERATOR_PULL_FAIL).get(), PULL_NOISE, good ? 1.0F : 0.95F);
        noise(PULL_NOISE);
        sync();
    }

    /** The STOP knob: the fuel cut, the engine runs down. */
    public void stop() {
        if (!running || level == null) return;
        running = false;
        warmUntil = level.getGameTime() + WARM_TICKS;
        sound(AflSounds.PORTABLE_GENERATOR_STOP.get(), RUN_NOISE, 1.0F);
        sync();
    }

    /** A breaker button pressed: back on (the loads may trip it again). */
    public void resetBreakers() {
        if (!tripped || level == null) return;
        tripped = false;
        demand = 0;
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.3F);
        sync();
    }

    /** The sockets: up to what is left of the rating this tick, while running and not tripped. */
    public int draw(int fe, boolean simulate) {
        if (level == null || fe <= 0) return 0;
        long now = level.getGameTime();
        if (drawTick != now) {
            askedLastTick = drawTick == now - 1 ? askedThisTick : 0; givenLastTick = drawTick == now - 1 ? givenThisTick : 0;
            drawTick = now; givenThisTick = 0; askedThisTick = 0;
        }
        if (!simulate) askedThisTick += fe;
        if (!outputOn()) return 0;
        int give = Math.min(fe, RATED - givenThisTick);
        if (give <= 0) return 0;
        if (!simulate) givenThisTick += give;
        return give;
    }

    /** Heard as far as the noise that goes with it (noise/RangedSound): the pulls 8, the start and the stop 32. */
    private void sound(SoundEvent sound, double radius, float pitch) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 at = PortableDieselGeneratorBlock.world(worldPosition, getBlockState(), PortableDieselGeneratorBlock.ENGINE);
        com.antaurora.apofirstlight.noise.RangedSound.play(server, at, sound, SoundSource.BLOCKS, radius, 1.0F, pitch);
    }

    private void noise(double radius) {
        if (level instanceof ServerLevel server) NoiseSystem.emit(new NoiseEvent(null, engineWorld(), NoiseType.MACHINE,
                server.getGameTime(), AflBlocks.PORTABLE_DIESEL_GENERATOR.getId(), radius), server);
    }

    /** The engine's middle (sounds, the run loop), world. */
    public Vec3 engineWorld() {
        return PortableDieselGeneratorBlock.world(worldPosition, getBlockState(), PortableDieselGeneratorBlock.ENGINE);
    }

    /** The exhaust outlet (smoke), world. */
    public Vec3 exhaustWorld() {
        return PortableDieselGeneratorBlock.world(worldPosition, getBlockState(), PortableDieselGeneratorBlock.EXHAUST);
    }

    // ---- server tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (pullStart >= 0) {
            long age = now - pullStart;
            if (age == CATCH_AT && !running && pullCatches && fuel.getFluidAmount() > 0) {
                running = true;
                sound(AflSounds.PORTABLE_GENERATOR_START.get(), RUN_NOISE, 1.0F);
                noise(RUN_NOISE);
                sync();
            }
            if (age >= PULL_TICKS) { pullStart = -1; puller = -1; if (running) gripper = -1; sync(); }
        }
        // the grip lets go: no pull for a while, the player gone, dead, or out of reach
        if (gripper >= 0 && !pulling()) {
            var holder = server.getEntity(gripper);
            if (now - gripTick > GRIP_TIMEOUT || !(holder instanceof Player p) || !p.isAlive()
                    || !PortableDieselGeneratorBlock.canReach(worldPosition, getBlockState(), p)) { gripper = -1; sync(); }
        }
        // the loads' demand last tick (they draw during the level's tick)
        int asked = drawTick == now - 1 ? askedThisTick : drawTick == now ? askedLastTick : 0;
        int given = drawTick == now - 1 ? givenThisTick : drawTick == now ? givenLastTick : 0;
        demand += ((outputOn() ? asked : 0) - demand) * DEMAND_RATE;   // off or tripped: it falls away (no trip on the next start)
        if (running && !tripped && demand > RATED + TRIP_MARGIN) {
            tripped = true;
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.7F, 0.8F);
            sync();
        }
        if (running) {
            fuelDebt += (IDLE_L + L_PER_KW * (tripped ? 0 : given) * KW_PER_FE_TICK) / HOUR_TICKS;
            while (fuelDebt >= 1) {
                FluidStack drained = fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
                fuelDebt -= 1;
                if (drained.isEmpty()) {   // ran dry: it stops
                    running = false; fuelDebt = 0; warmUntil = now + WARM_TICKS;
                    sound(AflSounds.PORTABLE_GENERATOR_STOP.get(), RUN_NOISE, 0.9F);
                    sync();
                    break;
                }
            }
            hours += 1.0 / HOUR_TICKS;
            long tenths = (long) Math.floor(hours * 10);
            if (tenths != shownHourTenths) { shownHourTenths = tenths; sync(); }
            if (Math.floorMod(now + worldPosition.asLong(), NOISE_INTERVAL) == 0) noise(RUN_NOISE);
            setChanged();
        }
    }

    private void sync() {
        if (level == null || level.isClientSide) return;
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // ---- AFL Animated Block Mesh Runtime ----

    /** Hour meter drum i (0 = thousands .. 3 = units, 4 = tenths): its digit. */
    public int hourDigit(int i) {
        long tenths = (long) Math.floor(hours * 10) % 100000;
        long[] div = {10000, 1000, 100, 10, 1};
        return (int) (tenths / div[i] % 10);
    }

    @Override
    protected boolean meshAnimationTarget(String channel) {
        return meshChannelValue(channel) > 0.5;
    }

    @Override
    public double meshChannelValue(String channel) {
        BlockState state = getBlockState();
        return switch (channel) {
            case "pull" -> { double age = pullAge(0); yield age >= YANK_AT && age < RELEASE_AT ? 1 : 0; }   // read every frame
            case "cap" -> state.hasProperty(PortableDieselGeneratorBlock.CAP) && state.getValue(PortableDieselGeneratorBlock.CAP) ? 1 : 0;
            case "volts" -> outputOn() ? VOLTS : 0;
            case "fuel" -> Math.max(0, Math.min(1, fuel.getFluidAmount() / (double) TANK));
            case "trip" -> tripped ? 1 : 0;
            default -> channel.length() == 2 && channel.charAt(0) == 'h' ? hourDigit(channel.charAt(1) - '0') / 10.0 : 0;
        };
    }

    /** Only the cap moves the hit mesh (the handle is out for a moment, the needles and drums are tiny). */
    @Override
    public boolean meshChannelAffectsHits(String channel) {
        return channel.equals("cap");
    }

    // ---- save, sync ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel.readFromNBT(tag.getCompound(FUEL_KEY));
        running = tag.getBoolean(RUNNING_KEY);
        hours = Math.max(0, tag.getDouble(HOURS_KEY));
        fuelDebt = tag.getDouble(DEBT_KEY);
        pullStart = tag.contains(PULL_KEY) ? tag.getLong(PULL_KEY) : -1;
        puller = tag.contains(PULLER_KEY) ? tag.getInt(PULLER_KEY) : -1;
        warmUntil = tag.contains(WARM_KEY) ? tag.getLong(WARM_KEY) : Long.MIN_VALUE;
        gripper = tag.contains(GRIP_KEY) ? tag.getInt(GRIP_KEY) : -1;
        pullGood = tag.getBoolean(GOOD_KEY);
        pullCatches = tag.getBoolean(CATCHES_KEY);
        tripped = tag.getBoolean(TRIP_KEY);
        usedSockets = tag.getInt(SOCKETS_KEY);
        demand = tag.getFloat(DEMAND_KEY);
        refreshMeshAnimationTargets();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(FUEL_KEY, fuel.writeToNBT(new CompoundTag()));
        tag.putBoolean(RUNNING_KEY, running);
        tag.putDouble(HOURS_KEY, hours);
        tag.putDouble(DEBT_KEY, fuelDebt);
        tag.putLong(PULL_KEY, pullStart);
        tag.putInt(PULLER_KEY, puller);
        tag.putLong(WARM_KEY, warmUntil);
        tag.putInt(GRIP_KEY, gripper);
        tag.putBoolean(GOOD_KEY, pullGood);
        tag.putBoolean(CATCHES_KEY, pullCatches);
        tag.putBoolean(TRIP_KEY, tripped);
        tag.putInt(SOCKETS_KEY, usedSockets);
        tag.putFloat(DEMAND_KEY, demand);
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
        return Component.translatable("jade.apocalypse_firstlight.portable_diesel_generator." + (running ? tripped ? "tripped" : "running" : "off"));
    }
}
