package com.antaurora.apofirstlight.weight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Function;

/**
 * Ownership, not GUI slot enumeration. Providers must expose only additional, disjoint carried items.
 * Besides the physical mass, the load: each stack's mass × its source's load factor (worn armor
 * penalties.armorLoadFactor, extra equipment its registered factor, everything else 1) × its item's carry factor.
 */
public final class PlayerMassSources {
    private record Extra(double loadFactor, Function<ServerPlayer, Iterable<ItemStack>> provider) {}
    private static final Map<ResourceLocation, Extra> EXTRA = new LinkedHashMap<>();
    private PlayerMassSources() {}

    /** Physical mass by source; load in grams, also by source. */
    public record Carried(MassResult mass, long loadGrams, Map<String, Long> loadBySource) {
        public Carried { loadBySource = Map.copyOf(loadBySource); }
    }

    public static void registerExtraEquipment(ResourceLocation id, Function<ServerPlayer, Iterable<ItemStack>> provider) {
        registerExtraEquipment(id, 1.0, provider);
    }
    /** loadFactor: how well the equipment spreads its load (a future backpack about 0.90; not a mass reduction). */
    public static void registerExtraEquipment(ResourceLocation id, double loadFactor, Function<ServerPlayer, Iterable<ItemStack>> provider) {
        if (!(loadFactor > 0) || Double.isInfinite(loadFactor)) throw new IllegalArgumentException("Invalid load factor " + loadFactor);
        if (EXTRA.putIfAbsent(id, new Extra(loadFactor, provider)) != null) throw new IllegalArgumentException("Duplicate equipment source " + id);
    }
    public static Carried calculate(ServerPlayer player, ItemMassData.Snapshot data) {
        var out = new Sum(data);
        var inv = player.getInventory();
        out.add("inventory", 1.0, inv.items); // ALL 36, including locked occupied slots.
        out.add("armor", data.penalties().armorLoadFactor(), inv.armor);
        out.add("offhand", 1.0, inv.offhand);
        out.add("cursor", 1.0, player.containerMenu.getCarried());
        // Known Vanilla ownership contract: slot 0 is RESULT, inputs are 1..4 / 1..9.
        for (int i = 1; i <= 4; i++) out.add("crafting", 1.0, player.inventoryMenu.getSlot(i).getItem());
        if (player.containerMenu instanceof CraftingMenu) for (int i = 1; i <= 9; i++)
            out.add("crafting", 1.0, player.containerMenu.getSlot(i).getItem());
        EXTRA.forEach((id, extra) -> {
            try { out.add("equipment:" + id, extra.loadFactor(), extra.provider().apply(player)); }
            catch (RuntimeException ex) { out.mass.issue("equipment_provider_failed:" + id); }
        });
        return new Carried(out.mass.build(), out.load, out.loadBySource);
    }

    private static final class Sum {
        final ItemMassData.Snapshot data;
        final MassResult.Builder mass = new MassResult.Builder();
        final Map<String, Long> loadBySource = new LinkedHashMap<>();
        long load;
        Sum(ItemMassData.Snapshot data) { this.data = data; }
        void add(String source, double factor, Iterable<ItemStack> stacks) { for (var stack : stacks) add(source, factor, stack); }
        void add(String source, double factor, ItemStack stack) {
            var result = StackMassCalculator.mass(stack, data);
            mass.add(source, result);
            if (stack.isEmpty()) return;
            double f = factor * data.carryFactor(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            long grams = f == 1.0 ? result.grams() : Math.round(result.grams() * f); // Math.round saturates
            load = MassResult.add(load, grams);
            loadBySource.merge(source, grams, MassResult::add);
        }
    }
}
