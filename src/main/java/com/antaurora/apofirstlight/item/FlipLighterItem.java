package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.fluid.FuelSpills;
import com.antaurora.apofirstlight.fluid.FuelStainIndex;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Flip lighter V1 (docs/gameplay/flip_lighter_v1.md, 2026-10-20): an old silver windproof lighter. Use (right click) in the
 * main hand: closed, flick the lid open; open, strike the wheel (it catches {@link #CATCH_CHANCE} of the time while it has
 * fuel, else only sparks); lit, nothing. Sneak + use while open or lit: snap it shut (lit, that puts the flame out). Lit,
 * use on a block lights only a campfire, candle or candle cake, or sets off TNT; the box stove takes its flame too
 * (cabin/BoxStove#igniter). It never sets fire to a block.
 * <p>
 * The server alone decides; the state rides in the stack's tag so every client draws the same (client/FlipLighterRenderer:
 * the lid, wheel and flame; client/skeleton/SkeletonActions: the hand's action item/flip_lighter_&lt;act&gt;;
 * client/FlipLighterClient: the first-person camera dip, the light). {@code Open}, {@code Lit}; {@code Used}: burn ticks
 * used of {@link #FUEL_TICKS}; {@code Act} and {@code ActAt}: the last act (open, strike, spark, close, close_lit, out)
 * and the game tick it started, the action's clock; {@code Snd} / {@code SndAt}: a sound due at that tick (an act's sound
 * comes with its motion: the lid flicked at 0.3 s). The times below are the actions' (tools/author-flip-lighter-actions.mjs).
 */
public final class FlipLighterItem extends Item {
    public static final String OPEN = "Open", LIT = "Lit", USED = "Used", ACT = "Act", ACT_AT = "ActAt", SND = "Snd", SND_AT = "SndAt";
    /** About 30 minutes of flame on a full fill. */
    public static final int FUEL_TICKS = 36000;
    /** Burn ticks a box stove's kindling takes to catch from it. */
    public static final int STOVE_LIGHT_TICKS = 100;
    public static final float CATCH_CHANCE = 0.85F;
    /** Heard (and noise) this far: blocks. */
    public static final double SOUND_RADIUS = 4.0;
    /** The lid, flicked open: seconds into "open" (the hand comes up first). */
    public static final float LID_OPEN_FROM = 0.30F, LID_OPEN_TO = 0.42F;
    /** The lid, snapped shut: seconds into "close" / "close_lit". */
    public static final float LID_CLOSE_FROM = 0.08F, LID_CLOSE_TO = 0.18F;
    /** The wheel rolled by the thumb, seconds into "strike" / "spark"; the flame catches at {@link #CATCH_AT}. */
    public static final float WHEEL_FROM = 0.10F, WHEEL_TO = 0.22F, CATCH_AT = 0.16F;
    /** The flame dies over this long in "out". */
    public static final float OUT_TIME = 0.4F;
    /** The lid's open angle (deg): it stands on the hinge side, held by the cam. */
    public static final float LID_OPEN_DEG = 105F;
    /** Ticks an act keeps the lighter busy (the next use is ignored until then). */
    private static final int BUSY_OPEN = 10, BUSY_STRIKE = 10, BUSY_SPARK = 7, BUSY_CLOSE = 6;

    public FlipLighterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ---------------------------------------------------------------- state

    public static boolean isOpen(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(OPEN);
    }

    public static boolean isLit(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(LIT);
    }

    public static int used(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().getInt(USED);
    }

    /** Fuel left, 0..1. */
    public static float fuel(ItemStack stack) {
        return 1F - Mth.clamp(used(stack) / (float) FUEL_TICKS, 0F, 1F);
    }

    /** The last act, or null. */
    @Nullable
    public static String act(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(ACT) ? null : tag.getString(ACT);
    }

    public static long actAt(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().getLong(ACT_AT);
    }

    /** Seconds since the last act at this partial tick (huge without one). */
    public static double actSeconds(ItemStack stack, Level level, float partial) {
        return act(stack) == null ? 1e6 : (level.getGameTime() - actAt(stack) + partial) / 20.0;
    }

    /** A lit lighter's flame is out in the air (past the strike's catch): it lights things. */
    public static boolean burning(ItemStack stack, Level level) {
        if (!isLit(stack)) return false;
        return !"strike".equals(act(stack)) || actSeconds(stack, level, 0F) >= CATCH_AT;
    }

    private static void act(ItemStack stack, Level level, String act) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ACT, act);
        tag.putLong(ACT_AT, level.getGameTime());
    }

    private static boolean busy(ItemStack stack, Level level) {
        String act = act(stack);
        if (act == null) return false;
        int busy = switch (act) {
            case "open" -> BUSY_OPEN;
            case "strike" -> BUSY_STRIKE;
            case "spark" -> BUSY_SPARK;
            case "close", "close_lit" -> BUSY_CLOSE;
            default -> 0;
        };
        return level.getGameTime() - actAt(stack) < busy;
    }

    /** A sound due {@code delay} ticks from now (one at a time; inventoryTick plays it). */
    private static void soundIn(ItemStack stack, Level level, SoundEvent sound, int delay) {
        CompoundTag tag = stack.getOrCreateTag();
        var id = ForgeRegistries.SOUND_EVENTS.getKey(sound);
        if (id == null) return;
        tag.putString(SND, id.toString());
        tag.putLong(SND_AT, level.getGameTime() + delay);
    }

    /** Shut and out, silently (put away, dropped); the fuel used stays. */
    private static void stow(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        for (String k : new String[]{OPEN, LIT, ACT, ACT_AT, SND, SND_AT}) tag.remove(k);
        if (tag.isEmpty()) stack.setTag(null);
    }

    // ---------------------------------------------------------------- use

    /**
     * Use in the air (or on a block that takes no flame): the next step. PASS on both sides: a consuming result would
     * replay the equip bob, and the server alone decides (the client sees the result in the stack's tag).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide || busy(stack, level)) return InteractionResultHolder.pass(stack);
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean(OPEN)) {
            tag.putBoolean(OPEN, true);
            act(stack, level, "open");
            soundIn(stack, level, AflSounds.FLIP_LIGHTER_OPEN.get(), 6);   // the lid flicked at 0.30 s
        } else if (player.isSecondaryUseActive()) {   // shut only on a sneak: a plain use on a lit lighter keeps it lit
            boolean lit = tag.getBoolean(LIT);
            tag.putBoolean(OPEN, false);
            tag.putBoolean(LIT, false);
            act(stack, level, lit ? "close_lit" : "close");
            soundIn(stack, level, AflSounds.FLIP_LIGHTER_CLOSE.get(), 3);   // shut at 0.18 s
        } else if (!tag.getBoolean(LIT)) {
            boolean catches = used(stack) < FUEL_TICKS && level.random.nextFloat() < CATCH_CHANCE;
            if (catches) tag.putBoolean(LIT, true);
            act(stack, level, catches ? "strike" : "spark");
            soundIn(stack, level, AflSounds.FLIP_LIGHTER_STRIKE.get(), 2);   // the wheel at 0.10 s
            // the flint's sparks: gasoline vapour catches from a spark (as a leak's: fluid/FuelLeaks)
            if (level instanceof ServerLevel server) expose(server, flamePoint(player), 0);
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * Lit, on a block: light only what is made to be lit (campfire, candle, candle cake, TNT; the box stove in its own
     * use). Never sets fire to a block: on anything else this passes and the use goes on as in the air (both sides agree,
     * so the client does not also send a use).
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (context.getHand() != InteractionHand.MAIN_HAND || !burning(stack, level) || busy(stack, level)) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean lightable = CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state);
        boolean tnt = state.getBlock() instanceof TntBlock;
        if (!lightable && !tnt) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.CONSUME;   // no swing: the hand is the action's
        if (lightable) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        } else {
            state.onCaughtFire(level, pos, context.getClickedFace(), player);
            level.removeBlock(pos, false);
        }
        burn(stack, 20);
        return InteractionResult.CONSUME;
    }

    /** Uses up {@code ticks} of fuel. */
    public static void burn(ItemStack stack, int ticks) {
        stack.getOrCreateTag().putInt(USED, Math.min(FUEL_TICKS, used(stack) + ticks));
    }

    /** Where a player's lighter flame is, about (world): held out before the chest, right of and below the view (the LIT hold). */
    public static Vec3 flamePoint(LivingEntity holder) {
        Vec3 look = holder.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return holder.getEyePosition().add(flat.scale(0.29)).add(right.scale(0.12)).add(0, -0.09, 0);
    }

    /** A flame (ticks) or a spark (0) at this point: fuel stains within reach catch (fluid/FuelSpills#expose). */
    private static void expose(ServerLevel level, Vec3 at, int ticks) {
        FuelSpills spills = FuelSpills.get(level);
        for (FuelStainIndex.Stain s : spills.index().within(at, 0.9)) spills.expose(level, s, ticks);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(level instanceof ServerLevel server) || stack.getTag() == null) return;
        CompoundTag tag = stack.getTag();
        long now = level.getGameTime();
        boolean held = selected && entity instanceof LivingEntity living && living.getMainHandItem() == stack;
        if (!held) {   // put away: shut (a lit one goes out)
            if (tag.getBoolean(OPEN) || tag.contains(ACT)) stow(stack);
            return;
        }
        if (tag.contains(SND) && now >= tag.getLong(SND_AT)) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(tag.getString(SND)));
            tag.remove(SND);
            tag.remove(SND_AT);
            if (sound != null) RangedSound.playWithNoise(server, entity, flamePoint((LivingEntity) entity), sound, SOUND_RADIUS,
                    0.96F + level.random.nextFloat() * 0.08F, NoiseType.INTERACTION, ForgeRegistries.ITEMS.getKey(this));
        }
        if (!tag.getBoolean(LIT)) return;
        Vec3 flame = flamePoint((LivingEntity) entity);
        if (entity.isUnderWater() || used(stack) >= FUEL_TICKS) {
            tag.putBoolean(LIT, false);
            act(stack, level, "out");
            if (entity.isUnderWater()) level.playSound(null, flame.x, flame.y, flame.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.8F);
            return;
        }
        if (now % 20 == 0) burn(stack, 20);
        if (now % 10 == 5 && burning(stack, level)) expose(server, flame, 10);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        stow(stack);
        return true;
    }

    /** The lid, wheel and flame change the tag all the time: never bob the hand for it. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    // ---------------------------------------------------------------- fuel bar and tooltip

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return used(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * fuel(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE8B04A;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.flip_lighter.fuel", Math.round(fuel(stack) * 100)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.flip_lighter.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
