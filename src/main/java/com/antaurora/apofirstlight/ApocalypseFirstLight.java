package com.antaurora.apofirstlight;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ApocalypseFirstLight.MOD_ID)
public class ApocalypseFirstLight {
    public static final String MOD_ID = "apocalypse_firstlight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApocalypseFirstLight() {
        // Minimal bootstrap. Gameplay systems will be added in later stages.
    }
}
