package com.antaurora.apofirstlight.authoring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelFill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * The state an exported building is saved in (2026-10-09; docs/worldgen/fuel_stop_a1_design_v1.md "导出 NBT 时的状态").
 * It is applied to the template's copy at capture, never to the world:
 * <ul>
 *   <li>no power: every block entity's stored energy ({@code EnergyStored}) 0 and its powered flag ({@code Powered}) off;</li>
 *   <li>no fuel: a fuel holder's fuel is swapped for a fluid/FuelFill marker (the underground tank, a fuel can or drum, the
 *   portable generator), so it is filled at random where the building is placed. The pump's buffer and the dispenser's
 *   lines are emptied: they only dispense with power, and once there is power they refill from the tank, so they follow
 *   it;</li>
 *   <li>nothing in progress: the generators stopped (no pull, grip, warm-up, fuel debt, trip, load), the dispenser's nozzle
 *   holders gone, no plug in a hand ({@code PlugCarrier}), no container search started ({@code AflContainerSearch}).</li>
 * </ul>
 * Plugs stay in ({@code PlugHost}); a placed building turns their offsets with it (worldgen/structure/AflBlockEntityProcessor).
 */
public final class ExportState {
    private static final String NS = ApocalypseFirstLight.MOD_ID + ":";

    private ExportState() {
    }

    /** What was changed, by kind, and how many AFL block states are still lit (a building is exported dark). */
    public record Summary(Map<String, Integer> changes, int litStates) {
        public String text() {
            return "export state " + changes + ", lit AFL states " + litStates;
        }
    }

    public static Summary normalize(CompoundTag template) {
        Map<String, Integer> changes = new TreeMap<>();
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) continue;
            CompoundTag nbt = block.getCompound("nbt");
            String id = nbt.getString("id");
            if (nbt.contains("EnergyStored", Tag.TAG_ANY_NUMERIC) && nbt.getInt("EnergyStored") != 0) { nbt.putInt("EnergyStored", 0); count(changes, "energy"); }
            if (nbt.contains("Powered", Tag.TAG_BYTE) && nbt.getBoolean("Powered")) { nbt.putBoolean("Powered", false); count(changes, "powered"); }
            if (nbt.contains("PlugCarrier")) { nbt.remove("PlugCarrier"); count(changes, "plug_in_hand"); }
            if (nbt.contains("AflContainerSearch")) { nbt.remove("AflContainerSearch"); count(changes, "search"); }
            switch (id) {
                case NS + "underground_fuel_tank" -> {
                    nbt.remove("Tank");
                    nbt.put(FuelFill.KEY, FuelFill.marker(FuelFill.UNDERGROUND_TANK, null));
                    count(changes, "fill_underground_tank");
                }
                case NS + "fuel_can" -> {
                    ResourceLocation held = nbt.contains("Fluid", Tag.TAG_COMPOUND) ? ResourceLocation.tryParse(nbt.getCompound("Fluid").getString("FluidName")) : null;
                    nbt.remove("Fluid");
                    nbt.put(FuelFill.KEY, FuelFill.marker(FuelFill.FUEL_CONTAINER, held));
                    count(changes, "fill_fuel_container");
                }
                case NS + "portable_diesel_generator" -> {
                    nbt.remove("Fuel");
                    nbt.put(FuelFill.KEY, FuelFill.marker(FuelFill.PORTABLE_GENERATOR, null));
                    nbt.putBoolean("Running", false);
                    nbt.putDouble("FuelDebt", 0);
                    nbt.putLong("PullStart", -1);
                    nbt.putInt("Puller", -1);
                    nbt.remove("WarmUntil");
                    nbt.putInt("Gripper", -1);
                    nbt.putBoolean("PullGood", false);
                    nbt.putBoolean("PullCatches", false);
                    nbt.putBoolean("Tripped", false);
                    nbt.putFloat("Demand", 0);
                    count(changes, "fill_portable_generator");
                }
                case NS + "diesel_generator" -> {   // no fill rule (not at A1): empty and off
                    nbt.remove("Fuel");
                    nbt.putInt("Mode", 0);
                    nbt.putInt("Crank", 0);
                    nbt.putBoolean("Fault", false);
                    nbt.remove("Temp");
                    nbt.putFloat("Load", 0);
                    nbt.putDouble("FuelDebt", 0);
                    count(changes, "standby_generator_emptied");
                }
                case NS + "submersible_fuel_pump" -> {
                    nbt.remove("Buffer");
                    count(changes, "pump_buffer_emptied");
                }
                case NS + "fuel_dispenser" -> {
                    nbt.remove("GasolineLine");
                    nbt.remove("DieselLine");
                    nbt.putByte("Flow", (byte) 0);
                    for (String key : new ArrayList<>(nbt.getAllKeys())) if (key.startsWith("Holder")) nbt.remove(key);
                    count(changes, "dispenser_lines_emptied");
                }
                default -> {
                }
            }
        }
        int lit = 0;
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            if (state.getString("Name").startsWith(NS) && "true".equals(state.getCompound("Properties").getString("lit"))) lit++;
        }
        return new Summary(changes, lit);
    }

    private static void count(Map<String, Integer> changes, String kind) {
        changes.merge(kind, 1, Integer::sum);
    }
}
