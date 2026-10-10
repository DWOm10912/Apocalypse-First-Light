package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle;
import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.fluid.NozzleFill;
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
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
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
 * <p>
 * Fuel (2026-10-05): two small line buffers, gasoline and diesel ({@link #LINE_MB} each), filled from the pipes at the
 * Fuel Dispenser Sump under it (FuelDispenserSumpBlockEntity hands its ports {@link #fuelInput}). A nozzle clicked on a fuel
 * opening goes into it and fills it (2026-10-10, {@link #insert}, fluid/NozzleFill); held anywhere else it sprays. The sump's power port feeds the same lamp buffer as the master's own bottom port ({@link #energyInput}).
 * <p>
 * Spraying (2026-10-05): a nozzle held in use draws {@link #SPRAY_MB} a tick from its grade's line (only while the dispenser
 * has power, the lamp lit) and marks the nozzle running ({@link #flowing}, synced). The stream is each client's
 * (client/FuelNozzleJets: a LiquidJet from the held spout along the holder's view at {@link #NOZZLE_SPEED}); the sprayer's
 * own client reports where its stream lands ({@link #landed}, AflNetwork.FuelSprayHitsC2SPacket), so the stains lie exactly
 * under the stream that player sees (2026-10-05: a server-side jet from an estimated spout put them off). The server
 * checks the report and lays the fuel as stains (fluid/FuelSpills: saved, synced, slippery, soaking).
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

    /** Each grade's line buffer (mB): fuel that has reached the dispenser from its underground tank. */
    public static final int LINE_MB = 200;
    private final FluidTank gasolineLine = line(com.antaurora.apofirstlight.registry.AflFluids.GASOLINE);
    private final FluidTank dieselLine = line(com.antaurora.apofirstlight.registry.AflFluids.DIESEL);
    private LazyOptional<IFluidHandler> gasolineInput = LazyOptional.of(() -> fillOnly(gasolineLine));
    private LazyOptional<IFluidHandler> dieselInput = LazyOptional.of(() -> fillOnly(dieselLine));

    private FluidTank line(java.util.function.Supplier<? extends net.minecraft.world.level.material.Fluid> fuel) {
        return new FluidTank(LINE_MB, stack -> stack.getFluid().isSame(fuel.get())) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    /** A line as a pipe sees it: it takes fuel in, never gives any back (so it is never a pipe's source). */
    private static IFluidHandler fillOnly(FluidTank tank) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int index) {
                return tank.getFluid();
            }

            @Override
            public int getTankCapacity(int index) {
                return tank.getCapacity();
            }

            @Override
            public boolean isFluidValid(int index, @NotNull FluidStack stack) {
                return tank.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return tank.fill(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }

    public FluidTank line(FuelDispenserBlock.Grade grade) {
        return grade == FuelDispenserBlock.Grade.GASOLINE ? gasolineLine : dieselLine;
    }

    /** The fill-only handler of a grade's line (the sump's fluid ports). */
    public LazyOptional<IFluidHandler> fuelInput(FuelDispenserBlock.Grade grade) {
        return grade == FuelDispenserBlock.Grade.GASOLINE ? gasolineInput : dieselInput;
    }

    /** The lamp buffer's input (the sump's power port; the master's own bottom port is the same storage). */
    public LazyOptional<IEnergyStorage> energyInput() {
        return power.capability();
    }
    private final int[] sessions = new int[Nozzle.values().length];

    /** Spraying: mB a tick out of the nozzle. */
    public static final int SPRAY_MB = 10;
    /** Blocks a second out of the spout (client/FuelNozzleJets, under Earth gravity): held level, the stream lands ~3.5 blocks away. */
    public static final double NOZZLE_SPEED = 7.0;

    /** The fuel a grade's line and nozzles carry. */
    public static net.minecraft.world.level.material.Fluid fuel(FuelDispenserBlock.Grade grade) {
        return (grade == FuelDispenserBlock.Grade.GASOLINE ? com.antaurora.apofirstlight.registry.AflFluids.GASOLINE
                : com.antaurora.apofirstlight.registry.AflFluids.DIESEL).get();
    }
    /** Nozzles whose stream runs (bit per nozzle, synced to clients), and the tick each last ran. */
    private int flowing;
    private final long[] lastFlow = new long[Nozzle.values().length];

    // ---- the nozzle put into a fuel opening (fluid/NozzleFill, 2026-10-10) ----
    /** Ticks the automatic shut-off holds the nozzle in before it comes out by itself. */
    private static final int SHUT_OFF_HOLD = 8;
    /** Ticks after the nozzle went in or out in which a click does not toggle it again (a held button repeats; the client also waits for a release). */
    private static final int TOGGLE_GUARD = 6;
    /** The opening block each nozzle is in, and the heading it went in along (synced). */
    private final BlockPos[] inserted = new BlockPos[Nozzle.values().length];
    private final float[] insertHeading = new float[Nozzle.values().length];
    /** Nozzles whose fuel runs into their opening (bit per nozzle) and how full that is (0..32): synced, for the fill sound. */
    private int filling;
    private final byte[] share = new byte[Nozzle.values().length];
    private final long[] shutOff = new long[Nozzle.values().length], lastPour = new long[Nozzle.values().length], toggled = new long[Nozzle.values().length];
    /** Client: when each nozzle last went in or out (game time), and where it was in (its way back out). */
    public final long[] clientChanged = new long[Nozzle.values().length];
    public final BlockPos[] clientWas = new BlockPos[Nozzle.values().length];
    public final float[] clientWasHeading = new float[Nozzle.values().length];

    public FuelDispenserBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.FUEL_DISPENSER.get(), pos, state);
        java.util.Arrays.fill(shutOff, -1);
        java.util.Arrays.fill(toggled, Long.MIN_VALUE / 2);
        java.util.Arrays.fill(clientChanged, Long.MIN_VALUE / 2);
    }

    /** The opening block this nozzle is in, or null. */
    @Nullable
    public BlockPos inserted(Nozzle nozzle) {
        return inserted[nozzle.ordinal()];
    }

    public float insertHeading(Nozzle nozzle) {
        return insertHeading[nozzle.ordinal()];
    }

    /** True while this nozzle's fuel runs into its opening (synced). */
    public boolean filling(Nozzle nozzle) {
        return (filling & 1 << nozzle.ordinal()) != 0;
    }

    /** How full the opening this nozzle fills is, 0..1 (synced in 32 steps). */
    public float fillShare(Nozzle nozzle) {
        return share[nozzle.ordinal()] / 32F;
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
        for (Nozzle nozzle : Nozzle.values()) {   // a stream stops two ticks after its last use tick
            int i = nozzle.ordinal();
            if ((flowing & 1 << i) != 0 && level.getGameTime() - lastFlow[i] > 2) {
                flowing &= ~(1 << i);
                sync();
            }
        }
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
        long now = level.getGameTime();
        for (Nozzle nozzle : Nozzle.values()) {
            int i = nozzle.ordinal();
            if (inserted[i] == null) continue;
            ServerPlayer player = holders[i] == null ? null : level.getServer().getPlayerList().getPlayer(holders[i]);
            if (player == null) pull(nozzle, null);
            else fillTick((net.minecraft.server.level.ServerLevel) level, player, nozzle, now);
        }
    }

    /**
     * A nozzle in an opening (2026-10-10): 1 L every other tick (10 L a second) from its line while the dispenser has power;
     * when the opening takes no more, the automatic shut-off clicks and the nozzle comes out {@link #SHUT_OFF_HOLD} ticks
     * later. It also comes out when the opening goes (closed, broken) or the holder is more than a block past the reach.
     */
    private void fillTick(net.minecraft.server.level.ServerLevel server, ServerPlayer player, Nozzle nozzle, long now) {
        int i = nozzle.ordinal();
        Vec3 opening = NozzleFill.opening(server, inserted[i]);
        double keep = NozzleFill.REACH + 1;
        if (opening == null || player.getEyePosition().distanceToSqr(opening) > keep * keep) {
            pull(nozzle, opening);
            return;
        }
        if (shutOff[i] >= 0) {
            if (now - shutOff[i] >= SHUT_OFF_HOLD) pull(nozzle, opening);
            return;
        }
        if (now - lastPour[i] > 3) setFilling(i, false);
        boolean tell = now % 20 == 0;
        if (!lit()) {
            if (tell) say(player, "no_power");
            return;
        }
        if (now % 2 != 0) return;
        IFluidHandler into = NozzleFill.handler(server, inserted[i]);
        if (into == null) {
            pull(nozzle, opening);
            return;
        }
        FluidStack one = line(nozzle.grade).drain(1, IFluidHandler.FluidAction.SIMULATE);
        if (one.isEmpty()) {
            if (tell) say(player, "no_fuel");
            return;
        }
        if (into.fill(one, IFluidHandler.FluidAction.SIMULATE) <= 0) {   // the automatic shut-off
            shutOff[i] = now;
            setFilling(i, false);
            play(opening, AflSounds.FUEL_NOZZLE_SHUT_OFF.get(), 0.9F);
            say(player, "full");
            return;
        }
        into.fill(line(nozzle.grade).drain(1, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        lastPour[i] = now;
        setFilling(i, true);
        byte full = (byte) Math.round(NozzleFill.share(into) * 32);
        if (full != share[i]) {
            share[i] = full;
            sync();
        }
    }

    private void setFilling(int i, boolean on) {
        int was = filling;
        filling = on ? filling | 1 << i : filling & ~(1 << i);
        if (filling != was) sync();
    }

    /** The holder puts this nozzle into the opening of target (a click on it); false when it does not go in (told why). */
    public boolean insert(ServerPlayer player, Nozzle nozzle, BlockPos target) {
        int i = nozzle.ordinal();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !player.getUUID().equals(holders[i]) || inserted[i] != null
                || server.getGameTime() - toggled[i] < TOGGLE_GUARD) return false;
        Vec3 opening = NozzleFill.opening(server, target);
        if (opening == null) return false;
        if (player.getEyePosition().distanceToSqr(opening) > NozzleFill.REACH * NozzleFill.REACH) {
            say(player, "too_far");
            return false;
        }
        String refusal = NozzleFill.refusal(server, target, fuel(nozzle.grade));
        if (refusal != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(refusal), true);
            return false;
        }
        inserted[i] = target.immutable();
        toggled[i] = server.getGameTime();
        insertHeading[i] = NozzleFill.heading(player.getEyePosition(), opening);
        shutOff[i] = -1;
        lastPour[i] = Long.MIN_VALUE / 2;
        IFluidHandler into = NozzleFill.handler(server, target);
        share[i] = into == null ? 0 : (byte) Math.round(NozzleFill.share(into) * 32);
        play(opening, AflSounds.FUEL_NOZZLE_INSERT.get(), 0.8F);
        sync();
        return true;
    }

    /** The holder takes this nozzle out of its opening (a click). */
    public boolean pullOut(ServerPlayer player, Nozzle nozzle) {
        if (!player.getUUID().equals(holders[nozzle.ordinal()]) || inserted[nozzle.ordinal()] == null
                || level == null || level.getGameTime() - toggled[nozzle.ordinal()] < TOGGLE_GUARD) return false;
        pull(nozzle, null);
        return true;
    }

    private void pull(Nozzle nozzle, @Nullable Vec3 at) {
        int i = nozzle.ordinal();
        if (inserted[i] == null) return;
        Vec3 where = at != null ? at : level != null ? NozzleFill.opening(level, inserted[i]) : null;
        clearInsert(i);
        if (level != null) toggled[i] = level.getGameTime();
        if (where != null) play(where, AflSounds.FUEL_NOZZLE_PULL.get(), 0.8F);
        sync();
    }

    private void clearInsert(int i) {
        inserted[i] = null;
        shutOff[i] = -1;
        filling &= ~(1 << i);
        share[i] = 0;
    }

    /** One use tick of a held nozzle (FuelNozzleItem#onUseTick): fuel from its line along the stream, wherever it lands. */
    public void spray(ServerPlayer player, Nozzle nozzle) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        int i = nozzle.ordinal();
        boolean tell = server.getGameTime() % 10 == 0;
        if (!lit()) {
            if (tell) say(player, "no_power");
            return;
        }
        FluidStack drawn = line(nozzle.grade).drain(SPRAY_MB, IFluidHandler.FluidAction.EXECUTE);
        if (drawn.isEmpty()) {
            if (tell) say(player, "no_fuel");
            return;
        }
        lastFlow[i] = server.getGameTime();
        if ((flowing & 1 << i) == 0) {
            flowing |= 1 << i;
            sync();
        }
    }

    /** A report reaches this far from the sprayer's eyes at most; the stream flies up to 3 s after the nozzle stops. */
    private static final double HIT_REACH = 16.0;
    private static final int HIT_LATE_TICKS = 70, HITS_PER_REPORT = 8;

    /**
     * The sprayer's client: these are the points where its stream of this nozzle landed this tick (positions, the face hit).
     * Accepted only from the player holding that nozzle, while it runs or shortly after, near the player, in loaded chunks
     * and not in another liquid; each wets a stain of the nozzle's fuel (FuelSpills; the fuel is the server's choice).
     */
    public void landed(ServerPlayer player, Nozzle nozzle, java.util.List<Vec3> points, java.util.List<Direction> faces) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        int i = nozzle.ordinal();
        if (!player.getUUID().equals(holders[i]) || server.getGameTime() - lastFlow[i] > HIT_LATE_TICKS) return;
        com.antaurora.apofirstlight.fluid.FuelSpills spills = com.antaurora.apofirstlight.fluid.FuelSpills.get(server);
        boolean diesel = nozzle.grade == FuelDispenserBlock.Grade.DIESEL;
        Vec3 eye = player.getEyePosition();
        for (int k = 0; k < Math.min(HITS_PER_REPORT, Math.min(points.size(), faces.size())); k++) {
            Vec3 at = points.get(k);
            BlockPos cell = BlockPos.containing(at.add(Vec3.atLowerCornerOf(faces.get(k).getNormal()).scale(0.01)));
            if (at.distanceToSqr(eye) > HIT_REACH * HIT_REACH || !server.isLoaded(cell) || !server.getFluidState(cell).isEmpty()) continue;
            spills.wet(server, at, faces.get(k), diesel);
        }
    }

    /** True while this nozzle's stream runs (synced; FuelDispenserRenderer draws it). */
    public boolean flowing(Nozzle nozzle) {
        return (flowing & 1 << nozzle.ordinal()) != 0;
    }

    private static void say(ServerPlayer player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.apocalypse_firstlight.fuel_nozzle." + key), true);
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
            clearInsert(i);
        }
    }

    private void release(Nozzle nozzle, @Nullable ServerPlayer player, Release why) {
        int i = nozzle.ordinal();
        if (level == null) return;
        if (player != null) removeCopies(player, i, sessions[i]);
        holders[i] = null;
        sessions[i] = 0;
        clearInsert(i);
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
        gasolineInput.invalidate();
        dieselInput.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        power.reviveCaps();
        gasolineInput = LazyOptional.of(() -> fillOnly(gasolineLine));
        dieselInput = LazyOptional.of(() -> fillOnly(dieselLine));
    }

    // ---- persistence and client sync (clients only need the holders) ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag);
        gasolineLine.readFromNBT(tag.getCompound("GasolineLine"));
        flowing = tag.getByte("Flow");
        dieselLine.readFromNBT(tag.getCompound("DieselLine"));
        int[] saved = tag.getIntArray("Sessions");
        for (int i = 0; i < holders.length; i++) {
            holders[i] = tag.hasUUID("Holder" + i) ? tag.getUUID("Holder" + i) : null;
            sessions[i] = i < saved.length ? saved[i] : 0;
        }
        // the nozzles in openings (update tag only; on the client the change starts the nozzle's way in or out)
        filling = tag.getByte("Fill");
        for (int i = 0; i < inserted.length; i++) {
            BlockPos now = tag.contains("In" + i) ? BlockPos.of(tag.getLong("In" + i)) : null;
            if (level != null && level.isClientSide && !java.util.Objects.equals(now, inserted[i])) {
                clientChanged[i] = level.getGameTime();
                if (inserted[i] != null) {
                    clientWas[i] = inserted[i];
                    clientWasHeading[i] = insertHeading[i];
                }
            }
            inserted[i] = now;
            insertHeading[i] = tag.getFloat("InHeading" + i);
            share[i] = tag.getByte("Share" + i);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        power.save(tag);
        tag.put("GasolineLine", gasolineLine.writeToNBT(new CompoundTag()));
        tag.put("DieselLine", dieselLine.writeToNBT(new CompoundTag()));
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
        tag.putByte("Flow", (byte) flowing);
        tag.putByte("Fill", (byte) filling);
        for (int i = 0; i < inserted.length; i++) {
            if (inserted[i] == null) continue;
            tag.putLong("In" + i, inserted[i].asLong());
            tag.putFloat("InHeading" + i, insertHeading[i]);
            tag.putByte("Share" + i, share[i]);
        }
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
