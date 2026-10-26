package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.noise.RangedSound;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Folding knife V1 (docs/gameplay/folding_knife_v1.md, 2026-10-22): a traditional lockback, clip-point blade, walnut scales,
 * brass bolsters. Use (right click) in the main hand: closed, open it with both hands (a nail in the nick, drawn out against
 * the backspring, the lock clicks); open, sneak + use folds it (press the lock bar, the spring snaps it shut). Open and
 * locked it is a light single-target melee weapon (weapons/melee tag, the melee slot); closed it strikes as a bare fist.
 * Put away or dropped, it folds silently.
 * <p>
 * The server alone decides; the state rides in the stack's tag as the flip lighter's does (client/FoldingKnifeRenderer: the
 * blade's turn; client/skeleton/SkeletonActions: the hands' action item/folding_knife_&lt;act&gt;; client/FoldingKnifeClient:
 * the camera dip, the swing in the air). {@code Open}; {@code Act} / {@code ActAt}: the last act (open, close) and its tick;
 * {@code Snd} / {@code SndAt}: a sound due at a tick. The times are the actions' (tools/author-folding-knife-actions.mjs) and
 * the sounds' (open_1: the nail catches at 0.00 s, the lock at 0.83 s; close_1: the lock bar at 0.00 s, the snap at 0.78 s).
 */
public final class FoldingKnifeItem extends Item {
    public static final String OPEN = "Open", ACT = "Act", ACT_AT = "ActAt", SND = "Snd", SND_AT = "SndAt";
    /** Heard (and noise) this far: blocks. */
    public static final double SOUND_RADIUS = 4.0;
    /** A stab carries a little further. */
    public static final double STAB_RADIUS = 6.0;
    /** The open / close sound starts this far into its act (the left hand reaches the blade first). */
    public static final float SOUND_AT = 0.40F;
    /** The blade drawn out, seconds into "open" (180 deg to 0); it locks as the sound's click lands (1.23 s). */
    public static final float OPEN_FROM = 0.42F, OPEN_TO = 1.20F;
    /** The blade folded, seconds into "close": pushed to {@link #CLOSE_PUSH_DEG} by hand, then the spring snaps it shut. */
    public static final float CLOSE_FROM = 0.45F, CLOSE_PUSH = 1.08F, CLOSE_TO = 1.18F, CLOSE_PUSH_DEG = 150F;
    /** Ticks an act keeps the knife busy: no use, no attack until then. */
    private static final int BUSY_OPEN = 36, BUSY_CLOSE = 32;
    private static final int SOUND_DELAY = Math.round(SOUND_AT * 20);

    /** Open and locked, in the main hand: player base 1 + 3 = 4 damage, 4 - 2 = 2 attacks a second (a 10-tick recharge). */
    private final Multimap<Attribute, AttributeModifier> openModifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Folding knife damage", 3.0, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Folding knife speed", -2.0, AttributeModifier.Operation.ADDITION))
            .build();

    public FoldingKnifeItem() {
        super(new Properties().durability(600).setNoRepair());
    }

    // ---------------------------------------------------------------- state

    public static boolean isOpen(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(OPEN);
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

    /** Opening or folding: the hands are on the blade. */
    public static boolean busy(ItemStack stack, Level level) {
        String act = act(stack);
        if (act == null) return false;
        int busy = switch (act) {
            case "open" -> BUSY_OPEN;
            case "close" -> BUSY_CLOSE;
            default -> 0;
        };
        long since = level.getGameTime() - actAt(stack);
        return since >= 0 && since < busy;
    }

    /** Open, locked and not being folded: it cuts. */
    public static boolean armed(ItemStack stack, Level level) {
        return isOpen(stack) && !busy(stack, level);
    }

    /** The blade's angle from open (0) toward closed (180 deg) at this partial tick. */
    public static float bladeDegrees(ItemStack stack, Level level, float partial) {
        String act = act(stack);
        double t = actSeconds(stack, level, partial);
        if ("open".equals(act) && t < OPEN_TO) {
            double u = smooth((t - OPEN_FROM) / (OPEN_TO - OPEN_FROM));
            return (float) (180.0 * (1.0 - u));
        }
        if ("close".equals(act) && t < CLOSE_TO) {
            if (t < CLOSE_PUSH) return (float) (CLOSE_PUSH_DEG * smooth((t - CLOSE_FROM) / (CLOSE_PUSH - CLOSE_FROM)));
            double u = (t - CLOSE_PUSH) / (CLOSE_TO - CLOSE_PUSH);   // the spring: fast, accelerating
            return (float) (CLOSE_PUSH_DEG + (180.0 - CLOSE_PUSH_DEG) * u * u);
        }
        return isOpen(stack) ? 0F : 180F;
    }

    private static double smooth(double u) {
        u = Math.max(0.0, Math.min(1.0, u));
        return u * u * (3.0 - 2.0 * u);
    }

    private static void act(ItemStack stack, Level level, String act) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ACT, act);
        tag.putLong(ACT_AT, level.getGameTime());
    }

    /** A sound due {@code delay} ticks from now (one at a time; inventoryTick plays it). */
    private static void soundIn(ItemStack stack, Level level, SoundEvent sound, int delay) {
        CompoundTag tag = stack.getOrCreateTag();
        var id = ForgeRegistries.SOUND_EVENTS.getKey(sound);
        if (id == null) return;
        tag.putString(SND, id.toString());
        tag.putLong(SND_AT, level.getGameTime() + delay);
    }

    /** Folded, silently (put away, dropped). */
    private static void stow(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        for (String k : new String[]{OPEN, ACT, ACT_AT, SND, SND_AT}) tag.remove(k);
        if (tag.isEmpty()) stack.setTag(null);
    }

    /** Where a player's knife is, about (world): before the chest, right of and below the view. */
    public static Vec3 handPoint(LivingEntity holder) {
        Vec3 look = holder.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return holder.getEyePosition().add(flat.scale(0.35)).add(right.scale(0.15)).add(0, -0.3, 0);
    }

    private void play(ServerLevel level, Entity source, Vec3 at, SoundEvent sound, double radius) {
        RangedSound.playWithNoise(level, source, at, sound, radius, 0.96F + level.random.nextFloat() * 0.08F,
                NoiseType.INTERACTION, ForgeRegistries.ITEMS.getKey(this));
    }

    // ---------------------------------------------------------------- use

    /** Closed: open it; open, on a sneak: fold it. PASS on both sides (no equip bob; the client sees the tag). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide || busy(stack, level)) return InteractionResultHolder.pass(stack);
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean(OPEN)) {
            tag.putBoolean(OPEN, true);
            act(stack, level, "open");
            soundIn(stack, level, AflSounds.FOLDING_KNIFE_OPEN.get(), SOUND_DELAY);
        } else if (player.isSecondaryUseActive()) {
            tag.putBoolean(OPEN, false);
            act(stack, level, "close");
            soundIn(stack, level, AflSounds.FOLDING_KNIFE_CLOSE.get(), SOUND_DELAY);
        }
        return InteractionResultHolder.pass(stack);
    }

    // ---------------------------------------------------------------- melee

    /** Open and locked, in the main hand: the knife's damage and speed; closed (or folding) a bare fist. */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND && isOpen(stack) ? openModifiers : super.getAttributeModifiers(slot, stack);
    }

    /** No attack while the hands are on the blade (opening, folding). */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return busy(stack, player.level());
    }

    /** A landed stab (vanilla calls this only on a hit that did damage): the stab's sound, one use of the edge. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!isOpen(stack)) return false;
        if (attacker.level() instanceof ServerLevel server) {
            play(server, attacker, target.position().add(0, target.getBbHeight() * 0.6, 0), AflSounds.FOLDING_KNIFE_STAB.get(), STAB_RADIUS);
        }
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    /**
     * The tip on a block: the first left click on it (server), open and not busy. By what the block sounds like (its break
     * sound's name): metal, wood or stone; soft blocks (dirt, sand, wool...) keep only their own dig sound.
     */
    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class BlockHits {
        @SubscribeEvent
        public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            Player player = event.getEntity();
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof FoldingKnifeItem knife) || !armed(stack, level)) return;
            BlockState state = level.getBlockState(event.getPos());
            String name = state.getSoundType(level, event.getPos(), player).getBreakSound().getLocation().getPath();
            SoundEvent sound = name.contains("metal") || name.contains("anvil") || name.contains("chain") || name.contains("lantern")
                    || name.contains("copper") || name.contains("netherite") ? AflSounds.FOLDING_KNIFE_HIT_METAL.get()
                    : name.contains("wood") || name.contains("bamboo") || name.contains("ladder") || name.contains("scaffolding")
                    ? AflSounds.FOLDING_KNIFE_HIT_WOOD.get()
                    : name.contains("stone") || name.contains("deepslate") || name.contains("basalt") || name.contains("tuff")
                    || name.contains("calcite") || name.contains("brick") || name.contains("glass") || name.contains("bone")
                    ? AflSounds.FOLDING_KNIFE_HIT_STONE.get() : null;
            if (sound == null) return;
            Vec3 at = event.getFace() == null ? Vec3.atCenterOf(event.getPos())
                    : Vec3.atCenterOf(event.getPos()).add(Vec3.atLowerCornerOf(event.getFace().getNormal()).scale(0.5));
            knife.play(level, player, at, sound, SOUND_RADIUS);
        }
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return false;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(level instanceof ServerLevel server) || stack.getTag() == null) return;
        CompoundTag tag = stack.getTag();
        boolean held = selected && entity instanceof LivingEntity living && living.getMainHandItem() == stack;
        if (!held) {
            if (tag.getBoolean(OPEN) || tag.contains(ACT)) stow(stack);
            return;
        }
        if (tag.contains(SND) && level.getGameTime() >= tag.getLong(SND_AT)) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(tag.getString(SND)));
            tag.remove(SND);
            tag.remove(SND_AT);
            if (sound != null) play(server, entity, handPoint((LivingEntity) entity), sound, SOUND_RADIUS);
        }
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        stow(stack);
        return true;
    }

    /** The blade's state changes the tag: never bob the hand for it. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.folding_knife.description").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.folding_knife.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
