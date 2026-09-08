package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData;
import com.antaurora.apofirstlight.energy.ThermalFuelDefinitions;
import mezz.jei.api.runtime.IJeiRuntime;
import java.util.List;

/** Called only on the client thread; recipes use the server-synchronized balance snapshot. */
public final class ThermalJeiRecipes {
    private static IJeiRuntime runtime;
    private static List<ThermalFuelDefinitions.DisplayFuel> visible = List.of();
    private ThermalJeiRecipes() {}
    public static void start(IJeiRuntime value) { runtime = value; refresh(); }
    public static void stop() { runtime = null; visible = List.of(); }
    public static void refresh() {
        if (runtime == null) return;
        var manager = runtime.getRecipeManager();
        manager.hideRecipes(AflJeiPlugin.THERMAL_GENERATION, visible);
        visible = ThermalFuelDefinitions.displayFuels(ClientThermalGeneratorFuelData.snapshot());
        manager.addRecipes(AflJeiPlugin.THERMAL_GENERATION, visible);
        if (!visible.isEmpty()) manager.unhideRecipeCategory(AflJeiPlugin.THERMAL_GENERATION);
    }
}
