package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.WaterDispenserBlock;
import com.antaurora.apofirstlight.blockmesh.AflAnimatedMeshBlockEntity;
import com.antaurora.apofirstlight.energy.CompressorAppliance;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Water Dispenser V2 (lower half): the mesh host (block_mesh_profiles/water_dispenser.json, no animation; the indicator
 * LEDs' unlit / lit sets follow LIT) and the power: {@link CompressorAppliance} in lights-only mode
 * (machine_balance/water_dispenser.json), fed only through the power port on the lower half's back.
 */
public final class WaterDispenserBlockEntity extends AflAnimatedMeshBlockEntity implements CompressorAppliance.Host {
    public static final ResourceLocation MESH_PROFILE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "block_mesh_profiles/water_dispenser.json");

    private final CompressorAppliance power = new CompressorAppliance(this, MachineBalanceManager::waterDispenser);

    public WaterDispenserBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.WATER_DISPENSER.get(), pos, state, MESH_PROFILE);
    }

    /** Server, every tick. */
    public void serverTick() {
        power.serverTick();
    }

    // ---- power: indicator lights only ----

    @Override
    public boolean lit() {
        return getBlockState().getValue(WaterDispenserBlock.LIT);
    }

    @Override
    public void setLit(boolean lit) {
        if (level != null && getBlockState().getBlock() instanceof WaterDispenserBlock block) block.setLit(level, worldPosition, lit);
    }

    /** No compressor: never used. */
    @Override
    public Vec3 compressorPosition() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public void syncAppliance() {
        setChanged();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && side != null && getBlockState().getBlock() instanceof WaterDispenserBlock block
                && block.hasPowerPort(getBlockState(), side)) return power.capability().cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        power.invalidateCaps();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        power.reviveCaps();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        power.save(tag);
    }

    // ---- AFL Animated Block Mesh Runtime ----

    /** No animation channels. */
    @Override
    protected boolean meshAnimationTarget(String channel) {
        return false;
    }

    /** The lit LED set (LabPBR emissive, full brightness) while LIT, else the unlit one. */
    @Override
    public boolean meshPartVisible(String part) {
        return switch (part) {
            case "lights" -> !lit();
            case "lights_lit" -> lit();
            default -> true;
        };
    }

    @Override
    public boolean meshPartEmissive(String part) {
        return "lights_lit".equals(part);
    }
}
