package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.health.LightningInjury;
import com.antaurora.apofirstlight.health.LightningInjuryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightning injury V1 (docs/gameplay/lightning_injury_v1_plan.md 3.2): a strike aims at the tallest thing. Vanilla strikes
 * any living thing under the open sky within 3 blocks of the chosen column, even beside a taller tree. Here (a lightning
 * rod still first) a living thing is struck only when its head is as high as every block top within target_radius of it;
 * otherwise the strike goes to the tallest column within that radius of one of them (the tree beside them), and they get
 * a side flash or ground current. The one aimed at is told to LightningInjury, which treats it as a direct strike.
 */
@Mixin(ServerLevel.class)
public abstract class LightningTargetServerLevelMixin {
    @Shadow
    private Optional<BlockPos> findLightningRod(BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "findLightningTargetAround", at = @At("HEAD"), cancellable = true)
    private void afl$tallestFirst(BlockPos pos, CallbackInfoReturnable<BlockPos> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        var c = LightningInjuryConfig.get();
        if (!c.tallestFirst) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        if (findLightningRod(top).isPresent()) return;   // vanilla's own way to the rod
        AABB box = new AABB(top, new BlockPos(top.getX(), level.getMaxBuildHeight(), top.getZ())).inflate(3.0);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != null && e.isAlive() && level.canSeeSky(e.blockPosition()));
        if (near.isEmpty()) return;   // vanilla's column
        int r = c.targetRadius;
        List<LivingEntity> tallest = new ArrayList<>();
        for (LivingEntity e : near) if (e.getBoundingBox().maxY >= highest(level, e.getBlockX(), e.getBlockZ(), r)) tallest.add(e);
        if (!tallest.isEmpty()) {
            LivingEntity e = tallest.get(level.random.nextInt(tallest.size()));
            LightningInjury.aimed(level, e.blockPosition(), e);
            cir.setReturnValue(e.blockPosition());
            return;
        }
        LivingEntity e = near.get(level.random.nextInt(near.size()));
        BlockPos best = null;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, e.blockPosition().offset(dx, 0, dz));
                if (best == null || p.getY() > best.getY()) best = p;
            }
        cir.setReturnValue(best);
    }

    /** The highest block top (the first free Y over it) within r blocks of this column. */
    private static int highest(ServerLevel level, int x, int z, int r) {
        int y = Integer.MIN_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) y = Math.max(y, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
        return y;
    }
}
