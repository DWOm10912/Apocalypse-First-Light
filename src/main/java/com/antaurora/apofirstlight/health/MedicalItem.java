package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.registry.AflSounds;
import com.antaurora.apofirstlight.sit.GroundSit;
import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Health V1a medical items (docs/gameplay/survival_health_v1a.md 7): the bandage covers a wound, medical alcohol cleans one
 * (a game stand-in for cleaning a wound, not medical advice). Hold right-click; the use is as long as the player action of
 * the part it works on (docs/gameplay/medical_actions_by_part_v1.md; first and third person alike): the bandage 4 s on a
 * forearm (item/bandage_wrap), 5 s on the head, 6 s on the chest or belly, 5 s on a leg (item/bandage_head, _chest,
 * _belly, _leg; {@link #useTicks}); the alcohol 4.6 s everywhere (item/alcohol_clean on a forearm, item/alcohol_head,
 * _chest, _belly, _leg). A leg's is done sat on the ground: a standing player sits first (sit/GroundSit) and stays sat.
 * Keyed in tools/medical-actions-v1a/ (tools/check-medical-actions-v1a.cjs checks these ticks); client/MedicalPourJets the
 * poured stream; the models are equipment meshes, tools/build-equipment-meshes-v1.mjs. The server plays the sounds on the
 * actions' keyframes ({@link #cues}); letting go cancels and uses nothing. On finishing,
 * the server applies it to the wound that needs it most (Wounds#target) and uses one item up (not in Creative). It does not start when no wound takes it (the
 * client checks the same rule on its synced wounds), nor while swimming or driving.
 */
public final class MedicalItem extends Item {
    /** A forearm's bandage and every part's alcohol (the length vanilla is told for the alcohol). */
    public static final int BANDAGE_TICKS = 80, ALCOHOL_TICKS = 92;
    /**
     * The bandage by part (docs/gameplay/medical_actions_by_part_v1.md): the head 5 s, the chest or belly 6 s (the length
     * vanilla is told; the shorter ones end from {@link #onUseTick}), a leg 5 s sat on the ground.
     */
    public static final int BANDAGE_HEAD_TICKS = 100, BANDAGE_TORSO_TICKS = 120, BANDAGE_LEG_TICKS = 100;
    /** After a use, this long before the next (holding right-click would otherwise start again at once). */
    private static final int COOLDOWN_TICKS = 10;
    /** Full volume: the use sounds carry 4 blocks, sounds.json's attenuation_distance (quiet hand work, doc 7.6). */
    private static final float CUE_VOLUME = 1F;

    /** A sound at a use tick (0 = the first tick of the use), at a pitch. */
    private record Cue(int tick, Supplier<SoundEvent> sound, float pitch) {
    }

    /**
     * The bandage's sounds on item/bandage_wrap's keyframes (doc 7.6): the roll taken out, the loose end pressed on
     * (0.53 s), the three turns (0.6 / 1.43 / 2.27 s), the end laid on (3.1 s), torn off (3.3 s), the two presses
     * (3.42 / 3.66 s), the arms down (3.75 s).
     */
    private static final List<Cue> BANDAGE_CUES = List.of(
            new Cue(0, AflSounds.MEDICAL_BANDAGE_GRAB, 1F), new Cue(10, AflSounds.MEDICAL_BANDAGE_PRESS, 1F),
            new Cue(12, AflSounds.MEDICAL_BANDAGE_WRAP_1, 1F), new Cue(29, AflSounds.MEDICAL_BANDAGE_WRAP_2, 1F),
            new Cue(45, AflSounds.MEDICAL_BANDAGE_WRAP_3, 1F), new Cue(62, AflSounds.MEDICAL_BANDAGE_LAY, 1F),
            new Cue(66, AflSounds.MEDICAL_BANDAGE_TEAR, 1F), new Cue(68, AflSounds.MEDICAL_BANDAGE_PAT, 1F),
            new Cue(73, AflSounds.MEDICAL_BANDAGE_PAT, 1.06F), new Cue(75, AflSounds.MEDICAL_BANDAGE_LOWER, 1F));
    /**
     * The alcohol's on item/alcohol_clean's (doc 7.6): three turns open (0.5 / 0.8 / 1.1 s) and the last of the thread
     * (1.4 s), the cap off (1.6 s), the pour (2.15 s, 0.7 s long), the cap back on the thread (3.5 s), three turns tight
     * (3.55 / 3.82 / 4.09 s) and home (4.27 s). The bottle picked up, tipped and put down are silent (user 2026-10-19).
     */
    private static final List<Cue> ALCOHOL_CUES = List.of(
            new Cue(10, AflSounds.MEDICAL_ALCOHOL_TWIST_1, 1F), new Cue(16, AflSounds.MEDICAL_ALCOHOL_TWIST_2, 1F),
            new Cue(22, AflSounds.MEDICAL_ALCOHOL_TWIST_1, 0.94F), new Cue(28, AflSounds.MEDICAL_ALCOHOL_THREAD_CLICK, 1F),
            new Cue(32, AflSounds.MEDICAL_ALCOHOL_CAP_OFF, 1F), new Cue(43, AflSounds.MEDICAL_ALCOHOL_POUR, 1F),
            new Cue(70, AflSounds.MEDICAL_ALCOHOL_CAP_ON, 1F), new Cue(71, AflSounds.MEDICAL_ALCOHOL_TWIST_2, 1.05F),
            new Cue(76, AflSounds.MEDICAL_ALCOHOL_TWIST_1, 1.05F), new Cue(82, AflSounds.MEDICAL_ALCOHOL_TWIST_2, 1F),
            new Cue(85, AflSounds.MEDICAL_ALCOHOL_THREAD_CLICK, 1.1F));
    /**
     * The bandage on the head, the trunk and a leg (tools/medical-actions-v1a/author_dress_v1.cjs): the roll taken out, the
     * loose end pressed on, the three turns, the end laid on, torn off, two presses, the arms down. Head: wrapping from
     * 0.8 s, a turn a second; trunk: from 0.9 s, 1.4 s a turn; leg (sat): from 0.7 s, a turn a second.
     */
    private static final List<Cue> BANDAGE_HEAD_CUES = wrapCues(10, 16, 20, 76, 89);
    private static final List<Cue> BANDAGE_TORSO_CUES = wrapCues(12, 18, 28, 102, 113);
    private static final List<Cue> BANDAGE_LEG_CUES = wrapCues(10, 14, 20, 74, 88);

    /** grab at 0, the end pressed on, three turns {@code turn} apart from {@code wrap}, laid on, torn, patted twice, lowered. */
    private static List<Cue> wrapCues(int press, int wrap, int turn, int lay, int lower) {
        return List.of(new Cue(0, AflSounds.MEDICAL_BANDAGE_GRAB, 1F), new Cue(press, AflSounds.MEDICAL_BANDAGE_PRESS, 1F),
                new Cue(wrap, AflSounds.MEDICAL_BANDAGE_WRAP_1, 1F), new Cue(wrap + turn, AflSounds.MEDICAL_BANDAGE_WRAP_2, 1F),
                new Cue(wrap + 2 * turn, AflSounds.MEDICAL_BANDAGE_WRAP_3, 1F), new Cue(lay, AflSounds.MEDICAL_BANDAGE_LAY, 1F),
                new Cue(lay + 4, AflSounds.MEDICAL_BANDAGE_TEAR, 1F), new Cue(lay + 7, AflSounds.MEDICAL_BANDAGE_PAT, 1F),
                new Cue(lay + 10, AflSounds.MEDICAL_BANDAGE_PAT, 1.06F), new Cue(lower, AflSounds.MEDICAL_BANDAGE_LOWER, 1F));
    }

    private final Wounds.Treatment treatment;

    public MedicalItem(Wounds.Treatment treatment, Properties properties) {
        super(properties);
        this.treatment = treatment;
    }

    public Wounds.Treatment treatment() {
        return treatment;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return treatment == Wounds.Treatment.BANDAGE ? BANDAGE_TORSO_TICKS : ALCOHOL_TICKS;
    }

    /** The use's length on this part (ticks): the bandage's by part, the alcohol's the same everywhere. */
    public static int useTicks(Wounds.Treatment treatment, @Nullable BodyPart part) {
        if (treatment == Wounds.Treatment.ALCOHOL) return ALCOHOL_TICKS;
        if (part == null || part.arm()) return BANDAGE_TICKS;
        return part == BodyPart.HEAD ? BANDAGE_HEAD_TICKS : part.leg() ? BANDAGE_LEG_TICKS : BANDAGE_TORSO_TICKS;
    }

    private static List<Cue> cues(Wounds.Treatment treatment, @Nullable BodyPart part) {
        if (treatment == Wounds.Treatment.ALCOHOL) return ALCOHOL_CUES;
        if (part == null || part.arm()) return BANDAGE_CUES;
        return part == BodyPart.HEAD ? BANDAGE_HEAD_CUES : part.leg() ? BANDAGE_LEG_CUES : BANDAGE_TORSO_CUES;
    }

    /** Busy with something that leaves no hand free for it. */
    private static boolean busy(Player player) {
        Entity vehicle = player.getVehicle();
        return player.isSwimming() || player.isUnderWater() || vehicle != null && vehicle.getControllingPassenger() == player;
    }

    private static List<Wounds.View> wounds(Player player) {
        return player instanceof ServerPlayer server ? Wounds.views(server) : ClientHealth.wounds();
    }

    /**
     * The part this treatment goes on now (the wound Wounds#target picks), or null: the server and the own player know the
     * wounds, the others' come with their marks.
     */
    @Nullable
    public static BodyPart target(Player player, Wounds.Treatment treatment) {
        if (player instanceof ServerPlayer || player.isLocalPlayer()) {
            List<Wounds.View> wounds = wounds(player);
            int i = Wounds.target(wounds, treatment);
            return i < 0 ? null : wounds.get(i).part();
        }
        var marks = ClientHealth.marks(player.getUUID());
        return treatment == Wounds.Treatment.BANDAGE ? marks.bandageTargetPart() : marks.cleanTargetPart();
    }

    /** Using a medical item on a leg (sat on the ground the whole time, as the leg's splint). */
    public static boolean onLeg(Player player) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof MedicalItem item)) return false;
        BodyPart part = target(player, item.treatment);
        return part != null && part.leg();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (busy(player)) return InteractionResultHolder.fail(stack);
        List<Wounds.View> wounds = wounds(player);
        int target = Wounds.target(wounds, treatment);
        if (target < 0) {
            if (!level.isClientSide) {
                String key = treatment == Wounds.Treatment.BANDAGE && Wounds.needsCleaning(wounds) ? "clean_first"
                        : Wounds.outOfReach(wounds, treatment) ? "out_of_reach" : wounds.isEmpty() ? "no_wound" : treatment == Wounds.Treatment.BANDAGE ? "all_bandaged" : "all_cleaned";
                player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wound." + key), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (wounds.get(target).part().leg() && !SplintItem.legReady(player)) {
            // a leg is dressed sat on the ground, as it is splinted: sit down first (the held key tries again)
            if (player instanceof ServerPlayer server && GroundSit.seat(server) == null) GroundSit.sit(server);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof Player player && busy(player)) {
            player.stopUsingItem();
            return;
        }
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        BodyPart part = target(player, treatment);
        if (part != null && part.leg() && GroundSit.seat(player) == null) {   // off the seat: the leg is not dressed standing
            player.stopUsingItem();
            return;
        }
        // to everyone near, the user too (null source): the action plays the same for all
        int tick = getUseDuration(stack) - remaining;
        for (Cue cue : cues(treatment, part))
            if (cue.tick == tick)
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), cue.sound.get(), SoundSource.PLAYERS, CUE_VOLUME, cue.pitch);
        if (tick >= useTicks(treatment, part) && tick < getUseDuration(stack)) {
            // this part's use is done before the item's length: finish it here (vanilla would at its end)
            finishUsingItem(stack, level, player);   // uses the stack up in place
            player.stopUsingItem();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) return stack;
        if (!Wounds.treat(server, treatment)) return stack;
        PlayerHealth.markSync(server);
        BodyMarkSync.update(server);   // at once: the wrap the use drew stays on (client/BandageWrapRenderer)
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight." + (treatment == Wounds.Treatment.BANDAGE ? "bandage" : "medical_alcohol") + ".description");
    }
}
