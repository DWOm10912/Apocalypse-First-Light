package com.antaurora.apofirstlight.vehicle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A vehicle from the Creative tab (user 2026-10-21; docs/vehicles/first_pickup_v0_design.md section 8.22.4): a use on the
 * ground opens the spawner (client/vehicle/VehicleSpawnerScreen: colour, wear, fuel, where its key is), which sends
 * {@link VehiclePackets.Spawn}; the truck stands there facing the way the player looks, its key in the cab (paired by its
 * UUID, as every truck's). Kept in Creative, used up otherwise. No recipe (Creative only for now).
 */
public class VehicleSpawnerItem extends Item {
    /** A factory colour: main and the lower band under the side moulding (the same for one colour). */
    public record Paint(String id, int main, int second) {}

    /** The T15's factory colours (also the dev command's presets, PickupTruckCommands). */
    public static final List<Paint> PAINTS = List.of(
            new Paint("white", 0xE9E7E1, 0xE9E7E1),
            new Paint("silver", 0xA8ACAF, 0xA8ACAF),
            new Paint("deep_red", 0x7B1E1A, 0x7B1E1A),
            new Paint("forest_green", 0x2F4A35, 0xCFC19F),
            new Paint("navy", 0x1F2C45, 0x1F2C45),
            new Paint("tan", 0x8A6A45, 0x8A6A45),
            new Paint("light_blue", 0x7D9FB8, 0x7D9FB8),
            new Paint("dark_grey", 0x3D3F41, 0x3D3F41));
    /** Where the key waits (the spawner's choice): the driver's visor (default), the glovebox, the ignition, any spot in the cab. */
    public static final CabStowage.KeySpot[] KEY_SPOTS = {CabStowage.KeySpot.VISOR_L, CabStowage.KeySpot.GLOVEBOX, CabStowage.KeySpot.IGNITION};
    public static final int KEY_RANDOM = KEY_SPOTS.length;
    /** Furthest a truck is set down from the player (blocks), and nearest along the look (its tailgate clear of them). */
    private static final double REACH = 12.0, CLEAR = 3.6;
    /** The client's spawner screen (set at client setup): the clicked point and whether it was a floor (a block's top). */
    public static volatile BiConsumer<Vec3, Boolean> clientOpen = (at, floor) -> {};

    public VehicleSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide && context.getPlayer() != null)
            clientOpen.accept(context.getClickLocation(), context.getClickedFace() == Direction.UP);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId().replaceFirst("^item\\.", "tooltip.")).withStyle(ChatFormatting.GRAY));
    }

    /** Server: a spawner's choice from the screen; only with the spawner in a hand, near enough. */
    static void spawn(ServerPlayer player, VehiclePackets.Spawn p) {
        InteractionHand hand = player.getMainHandItem().getItem() instanceof VehicleSpawnerItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof VehicleSpawnerItem ? InteractionHand.OFF_HAND : null;
        if (hand == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        if (!Double.isFinite(at.x + at.y + at.z) || at.distanceTo(player.position()) > REACH) return;
        ServerLevel level = player.serverLevel();
        boolean floor = p.floor();
        // the tailgate clear of the player: pushed out along the look when the click was too close
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        double along = (at.x - player.getX()) * look.x + (at.z - player.getZ()) * look.z;
        if (along < CLEAR) {
            at = at.add(look.scale(CLEAR - along));
            floor = false;
        }
        double y = floor ? at.y : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at)).getY();
        PickupTruckEntity truck = PickupTruckEntity.create(level, new Vec3(at.x, y, at.z), player.getYRot());
        Paint paint = PAINTS.get(Mth.clamp(p.paint(), 0, PAINTS.size() - 1));
        truck.setPaint(paint.main(), paint.second());
        truck.setWear(p.wear());
        truck.setFuel(Mth.clamp(p.fuel(), 0, PickupTruckEntity.TANK));
        truck.setKeySpot(p.key() >= 0 && p.key() < KEY_SPOTS.length ? KEY_SPOTS[p.key()] : randomInCab(level.getRandom()));
        level.addFreshEntity(truck);
        if (!player.getAbilities().instabuild) {
            ItemStack stack = player.getItemInHand(hand);
            stack.shrink(1);
        }
    }

    /** A found truck's spot, but always in the cab (the spawner's truck has its key). */
    private static CabStowage.KeySpot randomInCab(RandomSource random) {
        CabStowage.KeySpot spot;
        do spot = CabStowage.KeySpot.random(random);
        while (spot == CabStowage.KeySpot.ELSEWHERE);
        return spot;
    }
}
