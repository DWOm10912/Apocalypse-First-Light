package com.antaurora.apofirstlight.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A boolean option of the shader pack in use, by name (Oculus' own classes, read by reflection): how a pack that draws an
 * AFL effect itself asks for AFL's data (AFL_VEHICLE_LIGHTS, docs/rendering/dynamic_lights_v1.md 3.1; AFL_RAIN,
 * docs/rendering/rain_v1.md). Read once per pack object (a changed option reloads the pack: a new object); false when no
 * pack is in use, the pack lacks the option, or Oculus cannot be read.
 */
public final class ShaderPackOption {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean reflected, failed;
    private static Method inUse, currentPack, packOptions, optionSet, isBoolean, optionValues, booleanValue;
    private static Object lastPack;
    private static final Map<String, Boolean> VALUES = new HashMap<>();

    private ShaderPackOption() {
    }

    /** The option's value in the pack in use; what (a few words) is logged when it is first read for a pack. */
    public static boolean on(String option, String what) {
        if (failed || !ModList.get().isLoaded("oculus")) return false;
        try {
            if (!reflected) {
                reflected = true;
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                inUse = iris.getMethod("isPackInUseQuick");
                currentPack = iris.getMethod("getCurrentPack");
                packOptions = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack").getMethod("getShaderPackOptions");
                Class<?> options = Class.forName("net.irisshaders.iris.shaderpack.option.ShaderPackOptions");
                optionSet = options.getMethod("getOptionSet");
                optionValues = options.getMethod("getOptionValues");
                isBoolean = Class.forName("net.irisshaders.iris.shaderpack.option.OptionSet").getMethod("isBooleanOption", String.class);
                booleanValue = Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues").getMethod("getBooleanValueOrDefault", String.class);
            }
            if (!(Boolean) inUse.invoke(null)) return false;
            Object pack = ((Optional<?>) currentPack.invoke(null)).orElse(null);
            if (pack == null) return false;
            if (pack != lastPack) {
                lastPack = pack;
                VALUES.clear();
            }
            Boolean v = VALUES.get(option);
            if (v == null) {
                Object o = packOptions.invoke(pack);
                // isBooleanOption first: Oculus warns on every read of an option the pack does not have
                v = (Boolean) isBoolean.invoke(optionSet.invoke(o), option) && (Boolean) booleanValue.invoke(optionValues.invoke(o), option);
                VALUES.put(option, v);
                LOGGER.info("AFL: shader pack option {} {} ({})", option, v ? "on" : "off or absent", what);
            }
            return v;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            failed = true;
            LOGGER.warn("AFL: cannot read the shader pack's options ({}): {}", what, e.toString());
            return false;
        }
    }
}
