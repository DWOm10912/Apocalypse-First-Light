package com.antaurora.apofirstlight.blockentity;

import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wall mirror (Mirror Reflection V1, docs/rendering/mirror_reflection_v1.md): holds no data. On the client it lists the
 * loaded mirrors, so the reflection renderer ({@code client/MirrorReflection}) can pick the one to render each frame
 * without scanning the world. It has no renderer: the reflection is drawn in MirrorReflection's own world pass, which also
 * scans loaded chunks for mirrors whose block entity does not exist yet.
 */
public class WallMirrorBlockEntity extends BlockEntity {
    private static final Set<BlockPos> CLIENT_MIRRORS = ConcurrentHashMap.newKeySet();

    public WallMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(AflBlockEntities.WALL_MIRROR.get(), pos, state);
    }

    /** The loaded mirrors of the client level (read on the render thread). */
    public static Set<BlockPos> clientMirrors() { return CLIENT_MIRRORS; }

    public static void clearClientMirrors() { CLIENT_MIRRORS.clear(); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) CLIENT_MIRRORS.add(worldPosition.immutable());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) CLIENT_MIRRORS.remove(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide()) CLIENT_MIRRORS.remove(worldPosition);
    }
}
