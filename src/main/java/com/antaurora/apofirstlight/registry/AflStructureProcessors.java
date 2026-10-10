package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.structure.AflBlockEntityProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Structure processors for placing AFL buildings. */
public final class AflStructureProcessors {
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ApocalypseFirstLight.MOD_ID);

    /** Turns block entities' saved offsets with the building (worldgen/structure/AflBlockEntityProcessor). */
    public static final RegistryObject<StructureProcessorType<AflBlockEntityProcessor>> BLOCK_ENTITY =
            PROCESSORS.register("block_entity", () -> () -> AflBlockEntityProcessor.CODEC);

    private AflStructureProcessors() {
    }
}
