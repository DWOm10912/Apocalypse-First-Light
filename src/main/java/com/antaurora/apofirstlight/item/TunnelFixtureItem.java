package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small tunnel part a player took down (block/TunnelFixtureBlock; Tunnel parts removable V1.1, 2026-10-16), placed
 * again as the whole part: on a wall (facing out of it, centred on the click), under a ceiling (hanging from the click),
 * or standing on a floor (facing the player, in 16 directions), as Kind#mount says. Its variant (the egress sign's way
 * and distance, the name plate's name) rides in the stack's "Bone" tag. Every cell the part needs must be open (air or
 * replaceable), road_structure (taken over, its layers kept) or another fixture's (holding both), except that a wall
 * part's cells on the wall's side of the clicked face keep the wall's blocks (its boxes reach into the wall); and the
 * part clear of entities, else nothing is placed.
 */
public final class TunnelFixtureItem extends BlockItem {
    private final TunnelFixtureBlock.Kind kind;

    public TunnelFixtureItem(TunnelFixtureBlock block, Properties properties) {
        super(block, properties);
        this.kind = block.kind();
    }

    public TunnelFixtureBlock.Kind kind() { return kind; }

    /** The stack's variant, else the kind's default. */
    @Nullable public static String bone(ItemStack stack, TunnelFixtureBlock.Kind kind) {
        return stack.getTag() != null && stack.getTag().contains("Bone") ? stack.getTag().getString("Bone") : kind.defaultBone();
    }

    /** The stacks the creative tab lists: every egress sign and name plate, else the one item. */
    public List<ItemStack> variants() {
        List<String> bones = switch (kind) {
            case EGRESS_SIGN -> {
                List<String> out = new java.util.ArrayList<>();
                for (String side : new String[]{"l", "r"}) for (int d = 25; d <= 150; d += 25) out.add(side + d);
                yield out;
            }
            case NAME_PLATE -> List.of(TunnelMesh.NAMES);
            default -> java.util.Collections.singletonList(null);
        };
        List<ItemStack> out = new java.util.ArrayList<>();
        for (String b : bones) out.add(TunnelFixtureBlock.stack(kind, b));
        return out;
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        TunnelMesh.Placement p = placement(ctx);
        if (p == null) return InteractionResult.FAIL;
        TunnelFixtures.Part part = TunnelFixtures.Part.of(p);
        long[] cells = part.cells();
        double[] b = part.bounds();
        // no entity (the player included) inside the part's boxes' bounds
        if (!level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(b[0], b[1], b[2], b[3], b[4], b[5]),
                e -> e.isAlive() && e.blocksBuilding).isEmpty()) return InteractionResult.FAIL;
        BlockPos on = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        List<BlockPos> take = new java.util.ArrayList<>();
        for (long c : cells) {
            BlockPos q = BlockPos.of(c);
            BlockState s = level.getBlockState(q);
            if (!level.isInWorldBounds(q)) return InteractionResult.FAIL;
            if (s.isAir() || s.canBeReplaced() || s.is(AflBlocks.ROAD_STRUCTURE.get()) || s.getBlock() instanceof TunnelFixtureBlock) {
                take.add(q);
                continue;
            }
            // a wall part's boxes reach into the wall it hangs on (to be thick enough to aim at): the wall's blocks stay
            if (kind.mount == TunnelFixtureBlock.Mount.WALL && behind(q, on, face)) continue;
            return InteractionResult.FAIL;
        }
        if (take.isEmpty()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState state = getBlock().defaultBlockState();
        TunnelFixtureBlockEntity.Entry entry = new TunnelFixtureBlockEntity.Entry(kind, p);
        for (BlockPos q : take) {
            BlockState old = level.getBlockState(q);
            // a cell another fixture holds takes this part too
            if (old.getBlock() instanceof TunnelFixtureBlock && level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity be) {
                be.add(entry);
                level.sendBlockUpdated(q, old, old, 3);
                continue;
            }
            int structure = old.is(AflBlocks.ROAD_STRUCTURE.get()) ? old.getValue(RoadStructureBlock.LAYERS) : 0;
            level.setBlock(q, state, 3);
            if (level.getBlockEntity(q) instanceof TunnelFixtureBlockEntity be) {
                be.set(List.of(entry), structure);
                level.sendBlockUpdated(q, state, state, 3);
            }
        }
        BlockPos at = take.get(0);
        level.playSound(null, at, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
        Player player = ctx.getPlayer();
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /** Where the part goes for this click (null: the clicked face does not suit its mount). */
    @Nullable private TunnelMesh.Placement placement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Vec3 hit = ctx.getClickLocation();
        String bone = bone(ctx.getItemInHand(), kind);
        double[][] boxes = TunnelFixtures.boxes(kind.mesh, bone);
        double cx = 0, cy = 0;
        {
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
            for (double[] bx : boxes) {
                x0 = Math.min(x0, bx[0]); x1 = Math.max(x1, bx[3]);
                y0 = Math.min(y0, bx[1]); y1 = Math.max(y1, bx[4]);
            }
            cx = (x0 + x1) / 2;
            cy = (y0 + y1) / 2;
        }
        double[] ey = {0, 1, 0}, ez;
        double ox = hit.x, oy = hit.y, oz = hit.z;
        switch (kind.mount) {
            case WALL -> {
                if (face.getAxis() == Direction.Axis.Y) return null;
                ez = new double[]{face.getStepX(), 0, face.getStepZ()};
                double[] ex = cross(ey, ez);
                ox -= ex[0] * cx;
                oz -= ex[2] * cx;
                oy -= cy;
            }
            case CEILING -> {
                if (face != Direction.DOWN) return null;
                ez = facingPlayer(ctx);
                // the lane signal hangs on its rod (its mesh's top) from the click
                oy -= LANE_SIGNAL_TOP;
            }
            case FLOOR -> {
                if (face != Direction.UP) return null;
                ez = facingPlayer(ctx);
            }
            default -> { return null; }
        }
        return new TunnelMesh.Placement(kind.mesh, bone, ox, oy, oz, cross(ey, ez), ey, ez);
    }

    /** Whether cell q lies in the clicked block's layer along the clicked face or deeper (the wall's side of the face). */
    private static boolean behind(BlockPos q, BlockPos on, Direction face) {
        return (q.getX() - on.getX()) * face.getStepX() + (q.getY() - on.getY()) * face.getStepY() + (q.getZ() - on.getZ()) * face.getStepZ() <= 0;
    }

    /** The lane signal mesh's top over its origin (the rod's end), m. */
    private static final double LANE_SIGNAL_TOP = 3.8;

    /** A horizontal front toward the player, in 16 directions. */
    private static double[] facingPlayer(BlockPlaceContext ctx) {
        float yaw = ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot();
        double snapped = Math.toRadians(Math.round(yaw / 22.5) * 22.5);
        // the player looks along (-sin yaw, cos yaw); the part faces back at them
        return new double[]{Math.sin(snapped), 0, -Math.cos(snapped)};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String b = bone(stack, kind);
        if (kind == TunnelFixtureBlock.Kind.EGRESS_SIGN && b != null && b.length() > 1)
            tooltip.add(Component.translatable("tooltip.apocalypse_firstlight.tunnel_egress_sign." + (b.charAt(0) == 'l' ? "left" : "right"), b.substring(1)));
        else if (kind == TunnelFixtureBlock.Kind.NAME_PLATE && b != null)
            tooltip.add(Component.translatable("tooltip.apocalypse_firstlight.tunnel_name." + b));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.antaurora.apofirstlight.client.road.TunnelFixtureItemRenderer();
                return renderer;
            }
        });
    }
}
