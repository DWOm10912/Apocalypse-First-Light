package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Native worldgen registries for the Rural Structure/StructurePiece path. */
public final class RuralNaturalWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ApocalypseFirstLight.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<StructureType<RuralNaturalStructure>> RURAL_STRUCTURE =
            STRUCTURE_TYPES.register("rural", () -> () -> RuralNaturalStructure.CODEC);
    public static final RegistryObject<StructurePieceType> RURAL_PIECE =
            STRUCTURE_PIECES.register("rural", () -> RuralNaturalPiece::new);

    private RuralNaturalWorldgen() {
    }
}
