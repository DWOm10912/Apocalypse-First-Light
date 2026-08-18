package com.antaurora.apofirstlight.registry;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class AflBlockSetTypes {
    public static final BlockSetType AFL_POPLAR = BlockSetType.OAK;
    public static final BlockSetType AFL_STEEL = BlockSetType.register(new BlockSetType(
            "apocalypse_firstlight_steel",
            true,
            SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE,
            SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundEvents.STONE_BUTTON_CLICK_ON));

    private AflBlockSetTypes() {
    }
}
