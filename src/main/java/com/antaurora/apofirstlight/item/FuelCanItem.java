package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.FuelCanBlock;
import com.antaurora.apofirstlight.block.FuelSumpCoverBlock;
import com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity;
import com.antaurora.apofirstlight.fluid.FuelCanTransfers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A fuel container as an item (block/FuelCanBlock, docs/models/fuel_containers_v1.md): its fuel rides in the stack's
 * BlockEntityTag ({@link FuelCanBlockEntity#FLUID_KEY}), put back when it is set down; never stacks (cans of equal fuel
 * would share one tag). The drums are only set down. The jerry can pours ({@link #pours}):
 * <ul>
 *   <li>sneak + use on a block: set it down there;</li>
 *   <li>use on an open fill cover (block/FuelSumpCoverBlock, Kind.FILL) in reach: start pouring into its underground tank
 *   (2026-10-06, the user: a triggered pour instead of aiming the stream by hand). The can then pours by itself, held in the
 *   main hand: after {@link #POUR_DELAY} ticks (the cap unscrewed, the can tipped; the first-person animation), 1 L every
 *   {@link #POUR_TICKS} ticks (4 L a second) until it is empty, the tank will take no more, or the player steps away
 *   ({@link #POUR_RANGE}), turns to another slot or uses it again (which stops it). Nothing else takes a pour for now.</li>
 * </ul>
 * The pour in progress rides in the stack's own tag ({@link #POUR_KEY}: the cover and the game time it started), so the
 * first-person animation (client/JerryCanFirstPerson), the stream (client/FuelCanPourJets) and the arm pose follow it on
 * every client.
 */
public final class FuelCanItem extends BlockItem {
    /** Ticks a litre (1 mB) takes to pour: 4 L a second. */
    public static final int POUR_TICKS = 5;
    /**
     * Ticks from the start of a pour until fuel runs: the cap is unscrewed and the can tipped first (the first-person
     * animation's pour_start, 3.4 s; tools/author-jerry-can-first-person.mjs checks this line).
     */
    public static final int POUR_DELAY = 68;
    /** How far the eyes may be from the cover's middle while pouring (blocks). */
    public static final double POUR_RANGE = 3.0;
    /** The pour in progress: {Target: the fill cover (BlockPos.asLong), Start: the game time it started}. */
    public static final String POUR_KEY = "AflPour";
    /**
     * The spout while pouring, in view space (blocks: right, up, back; the eye at the origin) as the first-person pour pose
     * holds it (client/JerryCanFirstPerson, tools/author-jerry-can-first-person.mjs pour_loop): where other players' and
     * third-person streams start (client/FuelCanPourJets), and what the pouring player's view is turned to put over the
     * fill cover (client/JerryCanPourView).
     */
    public static final Vec3 SPOUT_VIEW = new Vec3(0.0175, -0.149, -0.716);

    private final boolean pours;

    public FuelCanItem(FuelCanBlock block, boolean pours, Properties properties) {
        super(block, properties.stacksTo(1));
        this.pours = pours;
    }

    public boolean pours() {
        return pours;
    }

    public int capacity() {
        return ((FuelCanBlock) getBlock()).size().capacity;
    }

    /** The fuel this stack holds (empty if none). */
    public static FluidStack fluid(ItemStack stack) {
        CompoundTag data = getBlockEntityData(stack);
        if (data == null || !data.contains(FuelCanBlockEntity.FLUID_KEY, Tag.TAG_COMPOUND)) return FluidStack.EMPTY;
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(data.getCompound(FuelCanBlockEntity.FLUID_KEY));
        return FuelCanBlockEntity.isFuel(fluid) ? fluid : FluidStack.EMPTY;
    }

    /** Puts this fuel into the stack (empty: drops the tag). */
    public static void setFluid(ItemStack stack, FluidStack fluid) {
        CompoundTag tag = stack.getTag();
        if (fluid.isEmpty() || fluid.getAmount() <= 0) {
            if (tag != null && tag.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
                tag.getCompound(BLOCK_ENTITY_TAG).remove(FuelCanBlockEntity.FLUID_KEY);
                if (tag.getCompound(BLOCK_ENTITY_TAG).isEmpty()) tag.remove(BLOCK_ENTITY_TAG);
                if (tag.isEmpty()) stack.setTag(null);
            }
            return;
        }
        stack.getOrCreateTagElement(BLOCK_ENTITY_TAG).put(FuelCanBlockEntity.FLUID_KEY, fluid.writeToNBT(new CompoundTag()));
    }

    /** Whether this stack is a can pouring into a fill cover. */
    public static boolean isPouring(ItemStack stack) {
        return stack.getItem() instanceof FuelCanItem can && can.pours && stack.getTag() != null && stack.getTag().contains(POUR_KEY, Tag.TAG_COMPOUND);
    }

    /** The fill cover a pouring can pours into (null if not pouring). */
    @Nullable
    public static BlockPos pourTarget(ItemStack stack) {
        return isPouring(stack) ? BlockPos.of(stack.getTag().getCompound(POUR_KEY).getLong("Target")) : null;
    }

    /** Game ticks since this can's pour started (negative if not pouring). */
    public static long pourTicks(ItemStack stack, Level level) {
        return isPouring(stack) ? level.getGameTime() - stack.getTag().getCompound(POUR_KEY).getLong("Start") : -1;
    }

    private static void startPour(ItemStack stack, BlockPos target, long now) {
        CompoundTag pour = new CompoundTag();
        pour.putLong("Target", target.asLong());
        pour.putLong("Start", now);
        stack.getOrCreateTag().put(POUR_KEY, pour);
    }

    private static void stopPour(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(POUR_KEY)) return;
        tag.remove(POUR_KEY);
        if (tag.isEmpty()) stack.setTag(null);
    }

    /** An open fill cover, the only thing a can pours into for now. */
    public static boolean takesPour(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FuelSumpCoverBlock cover && cover.kind() == FuelSumpCoverBlock.Kind.FILL && state.getValue(FuelSumpCoverBlock.OPEN);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        FluidStack fluid = fluid(stack);
        lines.add(fluid.isEmpty()
                ? Component.translatable("tooltip.apocalypse_firstlight.fuel_can.empty", capacity()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.apocalypse_firstlight.fuel_can.holds", fluid.getDisplayName(), fluid.getAmount(), capacity()).withStyle(ChatFormatting.GRAY));
        if (pours) lines.add(Component.translatable("tooltip.apocalypse_firstlight.fuel_can.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * A pouring can sets down only with sneak, and not while it pours (the pour crouches the player: client/JerryCanPourView);
     * otherwise the click falls through to {@link #use}.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (pours && (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive() || isPouring(context.getItemInHand()))) return InteractionResult.PASS;
        return super.useOn(context);
    }

    /**
     * Use: stop a pour in progress; else, on an open fill cover in reach, start one. PASS either way (a consuming result
     * would replay the equip bob, as the nozzle's); the pour itself runs on the server ({@link #inventoryTick}).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!pours || hand != InteractionHand.MAIN_HAND || level.isClientSide) return InteractionResultHolder.pass(stack);
        if (isPouring(stack)) {
            stopPour(stack);
            return InteractionResultHolder.pass(stack);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK || !takesPour(level, hit.getBlockPos())) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can.aim_fill_cover"), true);
            return InteractionResultHolder.pass(stack);
        }
        if (fluid(stack).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can.empty"), true);
            return InteractionResultHolder.pass(stack);
        }
        startPour(stack, hit.getBlockPos(), level.getGameTime());
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can.pouring"), true);
        return InteractionResultHolder.pass(stack);
    }

    /** The pour, while the can is in the selected slot (the main hand). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !isPouring(stack) || !(level instanceof ServerLevel server)) return;
        BlockPos target = pourTarget(stack);
        if (!selected || !(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || player.getMainHandItem() != stack
                || target == null || !level.isLoaded(target) || !takesPour(level, target)) {
            stopPour(stack);
            return;
        }
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > POUR_RANGE * POUR_RANGE) {
            stopPour(stack);
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can.too_far"), true);
            return;
        }
        long elapsed = pourTicks(stack, level) - POUR_DELAY;   // the cap comes off and the can tips first
        if (elapsed < 0 || elapsed % POUR_TICKS != 0) return;
        FluidStack held = fluid(stack);
        if (held.isEmpty()) {
            stopPour(stack);
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can.done"), true);
            return;
        }
        FluidStack one = held.copy();
        one.setAmount(1);
        IFluidHandler into = FuelCanTransfers.handler(server, target, Direction.UP);
        if (into == null || into.fill(one, IFluidHandler.FluidAction.EXECUTE) <= 0) {
            FluidStack there = into == null ? FluidStack.EMPTY : FuelCanTransfers.contents(into);
            String why = into == null || into.getTanks() == 0 || !into.isFluidValid(0, one) ? "wont_take" : !there.isEmpty() && !there.isFluidEqual(one) ? "other_fuel" : "full";
            stopPour(stack);
            player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fuel_can." + why), true);
            return;
        }
        held.shrink(1);
        setFluid(stack, held);   // (the sounds are the clients', client/JerryCanPourSounds, on the animation's clock)
    }

    /** Dropped, it stops pouring. */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        stopPour(stack);
        return true;
    }

    /**
     * The fuel it holds changes as it pours (a litre every POUR_TICKS), and so does its pour tag; that is still the same can
     * in hand, so no re-equip (as the guns' ammo, weapon/ConfiguredNativeGunItem): the re-equip dropped the first-person
     * view model and raised it again several times a second while pouring (in game, 2026-10-05).
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem() || oldStack.getCount() != newStack.getCount();
    }

    /** Pouring, both arms go over the can (client/JerryCanArmPose); first person is client/JerryCanFirstPerson. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        if (!pours) return;
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return hand == InteractionHand.MAIN_HAND && isPouring(stack) ? com.antaurora.apofirstlight.client.JerryCanArmPose.POUR : null;
            }
        });
    }

    /** Where the spout of a can being poured is, as the first-person pour pose holds it (the eyes, looking where they look). */
    public static Vec3 spout(Player player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        double yaw = Math.toRadians(player.getViewYRot(partialTick));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)), up = right.cross(look);   // the right level, from the yaw
        return player.getEyePosition(partialTick).add(right.scale(SPOUT_VIEW.x)).add(up.scale(SPOUT_VIEW.y)).add(look.scale(-SPOUT_VIEW.z));
    }
}
