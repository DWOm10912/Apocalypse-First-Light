package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The four 1x1 pockets of the base clothing (docs/ui/afl_inventory_grid_v0_plan.md 1): always there, kept with the
 * player (capability {@code apocalypse_firstlight:pockets}, saved as a grid item list). A death drops them with the
 * inventory, or the survivor's corpse takes them (SurvivorCorpses); keepInventory and returning from the End keep them. The owner's client gets a copy
 * whenever they change (GridPackets.Pockets).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class Pockets implements INBTSerializable<CompoundTag> {
    public static final GridLayout LAYOUT = GridLayout.of(1, 1, 1, 1, 1, 1, 1, 1);
    public static final Capability<Pockets> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "pockets");

    private ListTag items = new ListTag();

    /** The saved list (owned by this object; read it, do not change it). */
    public ListTag items() { return items; }

    public void set(ListTag items) { this.items = items; }

    @Nullable
    public static Pockets of(Player player) {
        return player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items.copy());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        items = tag.getList("Items", Tag.TAG_COMPOUND).copy();
    }

    private static final class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final Pockets data = new Pockets();
        private final LazyOptional<Pockets> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return data.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag tag) { data.deserializeNBT(tag); }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(ID, new Provider());
    }

    /**
     * The new body gets what the old one still had in its pockets: after a death only with keepInventory (otherwise the
     * pockets dropped with the inventory, InventoryGridMixin, or went into the survivor's corpse), and on the way back
     * from the End.
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var original = event.getOriginal();
        original.reviveCaps();
        var from = of(original);
        var to = of(event.getEntity());
        if (from != null && to != null) to.set(from.items.copy());
        original.invalidateCaps();
    }

    /** Server: drops everything in the pockets (a death without keepInventory, with the inventory). */
    public static void dropAll(ServerPlayer player) {
        var pockets = of(player);
        if (pockets == null || pockets.items.isEmpty()) return;
        for (var stack : GridStorage.load(LAYOUT, pockets.items, false).stacks()) {
            if (!stack.isEmpty()) player.drop(stack, true, false);
        }
        pockets.set(new ListTag());
        GridPackets.sendPockets(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GridPackets.sendPockets(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GridPackets.sendPockets(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GridPackets.sendPockets(player);
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(Pockets.class);
        }

        /** The pockets weigh like everything else carried (Weight V1). */
        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
            event.enqueueWork(() -> com.antaurora.apofirstlight.weight.PlayerMassSources.registerExtraEquipment(ID,
                    player -> {
                        var pockets = of(player);
                        return pockets == null ? java.util.List.of() : GridStorage.load(LAYOUT, pockets.items, false).stacks();
                    }));
        }
    }
}
