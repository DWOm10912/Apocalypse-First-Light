package com.antaurora.apofirstlight.worldgen.structure;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflStructureProcessors;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the block positions that AFL block entities save relative to themselves along with a turned or mirrored building
 * (2026-10-09, docs/worldgen/fuel_stop_a1_design_v1.md "导出 NBT 时的状态"). Vanilla's template placement turns block
 * states but copies block entity data as it is. So without this, a plugged-in appliance's socket offset ({@code PlugHost},
 * energy/PlugCord) or a meter box's panel offset ({@code Panel}) points the wrong way in a building placed at 90 / 180 / 270
 * degrees, and the plug misses its socket.
 * <p>
 * Turned keys: {@code PlugHost} on any block entity (every plugged appliance), {@code Outlet} on a power strip (its V1 key,
 * read when there is no PlugHost), {@code Panel} on a service meter box. An offset turns as a direction: mirror, then rotate,
 * about the origin (the template's own transform, pivot zero). Socket numbers stay: they count in the host's own frame.
 * Add it to the placement settings of every AFL building ({@code settings.addProcessor(AflBlockEntityProcessor.INSTANCE)}),
 * or as {@code {"processor_type": "apocalypse_firstlight:block_entity"}} in a processor list.
 */
public final class AflBlockEntityProcessor extends StructureProcessor {
    public static final AflBlockEntityProcessor INSTANCE = new AflBlockEntityProcessor();
    public static final Codec<AflBlockEntityProcessor> CODEC = Codec.unit(() -> INSTANCE);
    private static final String NS = ApocalypseFirstLight.MOD_ID + ":";

    private AflBlockEntityProcessor() {
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        CompoundTag nbt = current.nbt();   // the template's copy for this placement
        if (nbt == null || settings.getRotation() == Rotation.NONE && settings.getMirror() == Mirror.NONE) return current;
        String id = nbt.getString("id");
        turn(nbt, "PlugHost", settings);
        if (id.equals(NS + "power_strip")) turn(nbt, "Outlet", settings);
        if (id.equals(NS + "service_meter_box")) turn(nbt, "Panel", settings);
        return current;
    }

    private static void turn(CompoundTag nbt, String key, StructurePlaceSettings settings) {
        if (nbt.contains(key, Tag.TAG_LONG)) nbt.putLong(key, turnOffset(BlockPos.of(nbt.getLong(key)), settings.getMirror(), settings.getRotation()).asLong());
    }

    /** A saved offset (from the block entity to another block) as the building turns. */
    public static BlockPos turnOffset(BlockPos offset, Mirror mirror, Rotation rotation) {
        return StructureTemplate.transform(offset, mirror, rotation, BlockPos.ZERO);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return AflStructureProcessors.BLOCK_ENTITY.get();
    }
}
