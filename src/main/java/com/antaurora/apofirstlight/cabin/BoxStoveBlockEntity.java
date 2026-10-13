package com.antaurora.apofirstlight.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Box Stove V1 (docs/models/box_stove_v1.md): the stove's fire and its cook top. {@code fuel}: burn ticks left (at most
 * {@link BoxStove#CAPACITY}: three logs); while lit it burns one a tick with the door shut, two with it open (more air);
 * at nothing the fire goes out. The block state follows: {@code lit}, {@code load} (the splits drawn inside). Two cook
 * spots on the top, one item each: anything with a smoking recipe cooks there while the fire burns, at the furnace's pace
 * (twice the recipe's smoker time); the result stays on the stove until taken. Without the fire the progress cools away.
 * The cook-top items and the fuel go to clients (client BoxStoveRenderer draws the items; the fuel is the prompt's: the
 * client burns its copy down in step).
 */
public class BoxStoveBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(BoxStove.SPOTS.length, ItemStack.EMPTY);
    private final int[] progress = new int[BoxStove.SPOTS.length], total = new int[BoxStove.SPOTS.length];
    private int fuel;
    /** Client: the chimney's smoke outlet, whether a rain cap is over it, and when it was found (client BoxStoveFire). */
    public @Nullable Vec3 clientChimney;
    public boolean clientChimneyCap;
    public long clientChimneyAt = Long.MIN_VALUE, clientPuff, clientLitAt = Long.MIN_VALUE;

    public BoxStoveBlockEntity(BlockPos pos, BlockState state) { super(BoxStove.STOVE, pos, state); }

    public int fuel() { return fuel; }

    public ItemStack item(int spot) { return items.get(spot); }

    /** The cook progress of a spot, 0..1 (0 when nothing cooks there). */
    public float cooked(int spot) { return total[spot] <= 0 ? 0 : Math.min(1F, progress[spot] / (float) total[spot]); }

    /** Whether {@code ticks} more fuel goes in (it must fit by half at least). */
    public boolean fits(int ticks) { return fuel + ticks / 2 <= BoxStove.CAPACITY; }

    /** The spot an item goes to when aimed at `aimed`: that one if free, else the other free one; -1: none. */
    public int freeSpot(int aimed) {
        if (aimed >= 0 && items.get(aimed).isEmpty()) return aimed;
        for (int k = 0; k < items.size(); k++) if (items.get(k).isEmpty()) return k;
        return -1;
    }

    /** The spot an empty hand takes from when aimed at `aimed`: that one if it holds something, else the other; -1: none. */
    public int takeSpot(int aimed) {
        if (aimed >= 0 && !items.get(aimed).isEmpty()) return aimed;
        for (int k = 0; k < items.size(); k++) if (!items.get(k).isEmpty()) return k;
        return -1;
    }

    public void addFuel(int ticks) {
        fuel = Math.min(BoxStove.CAPACITY, fuel + ticks);
        follow(getBlockState(), getBlockState().getValue(BoxStove.Stove.LIT));
        changed();
    }

    public void light() {
        if (fuel > 0) follow(getBlockState(), true);
        changed();
    }

    public void put(int spot, ItemStack stack) {
        items.set(spot, stack);
        progress[spot] = 0;
        total[spot] = level == null ? 0 : BoxStove.recipe(level, stack).map(r -> 2 * r.getCookingTime()).orElse(0);
        changed();
    }

    public ItemStack take(int spot) {
        ItemStack out = items.get(spot);
        items.set(spot, ItemStack.EMPTY);
        progress[spot] = total[spot] = 0;
        changed();
        return out;
    }

    /** The load drawn inside: the fuel left in logs' worth, rounded up, 0..3. */
    static int load(int fuel) { return fuel <= 0 ? 0 : Math.min(3, (fuel + BoxStove.LOG_TICKS - 1) / BoxStove.LOG_TICKS); }

    /** Sets lit / load on the block when they changed. */
    private void follow(BlockState state, boolean lit) {
        if (level == null || level.isClientSide) return;
        BlockState next = state.setValue(BoxStove.Stove.LIT, lit && fuel > 0).setValue(BoxStove.Stove.LOAD, load(fuel));
        if (next != state) level.setBlock(worldPosition, next, 3);
    }

    void serverTick(BlockState state) {
        boolean lit = state.getValue(BoxStove.Stove.LIT);
        if (lit) {
            fuel = Math.max(0, fuel - (state.getValue(BoxStove.Stove.OPEN) ? 2 : 1));
            follow(state, fuel > 0);
            setChanged();
        }
        boolean done = false;
        for (int k = 0; k < items.size(); k++) {
            ItemStack s = items.get(k);
            if (s.isEmpty() || total[k] <= 0) continue;
            if (!lit) { progress[k] = Math.max(0, progress[k] - 2); continue; }
            if (++progress[k] < total[k]) continue;
            int spot = k;
            BoxStove.recipe(level, s).ifPresent(r -> items.set(spot, r.assemble(new SimpleContainer(s), level.registryAccess())));
            progress[k] = total[k] = 0;
            done = true;
        }
        if (done) changed();
    }

    /** Client: the fuel burns down here too (for the prompt). */
    public void clientBurn(BlockState state) {
        if (state.getValue(BoxStove.Stove.LIT)) fuel = Math.max(0, fuel - (state.getValue(BoxStove.Stove.OPEN) ? 2 : 1));
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
        tag.putInt("Fuel", fuel);
        tag.putIntArray("Progress", progress);
        tag.putIntArray("Total", total);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        fuel = tag.getInt("Fuel");
        int[] p = tag.getIntArray("Progress"), t = tag.getIntArray("Total");
        for (int k = 0; k < progress.length; k++) { progress[k] = k < p.length ? p[k] : 0; total[k] = k < t.length ? t[k] : 0; }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
