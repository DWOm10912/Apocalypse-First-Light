package com.antaurora.apofirstlight.health;

import com.antaurora.apofirstlight.registry.AflSounds;
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
import com.antaurora.apofirstlight.sit.GroundSeat;
import com.antaurora.apofirstlight.sit.GroundSit;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The splint (fractures and splints V1, docs/gameplay/fracture_splint_v1_plan.md 5; actions V2,
 * docs/gameplay/splint_animation_v2_plan.md): two slats and gauze strips that set one broken limb, chosen by
 * Fractures#splintTarget (legs first). Hold right-click: a forearm takes 8 s ({@link #USE_TICKS}; the player action
 * item/splint_arm, standing or sat), a leg 6 s ({@link #LEG_USE_TICKS}; item/splint_leg) sat on the ground: standing, the
 * use sits the player down first (sit/GroundSit) and starts once sat (the held key tries again). Mirrored for a right limb.
 * Letting go cancels and uses nothing; it does not start without an unsplinted fracture, nor while swimming or driving. On
 * finishing the server sets that limb and uses one up (not in Creative); the splint then stays on the body
 * (client/BandageWrapRenderer) until the bone has healed or is broken again. After a leg the player stays sat (rest).
 */
public final class SplintItem extends Item {
    /** The use's length as the item tells vanilla: a forearm's. A leg's is shorter and ends from {@link #onUseTick}. */
    public static final int USE_TICKS = 160;
    public static final int LEG_USE_TICKS = 120;
    private static final int COOLDOWN_TICKS = 10;

    private record Cue(int tick, Supplier<SoundEvent> sound, float pitch) {
    }

    /**
     * The sounds on the V2 actions' keys (tools/medical-actions-v1a/author_splint_v2.cjs). The leg: the splint taken
     * (0 s), laid on (0.7 s) and opened to both sides (0.9 s); each tie's strip pushed under (1.45 / 2.7 / 3.95 s) and pulled
     * tight (+0.9 s); the hands back on the thigh (5.3 s). The forearm: the splint taken (0 s), laid on (1.0 / 1.3 s); each
     * tie round (2.0 / 3.4 / 4.8 s) and pulled tight between the teeth and the hand (+0.9 s); the forearm carried close
     * (7.3 s) and let down (7.5 s). The gauze takes the bandage's recordings; the wood's own knock is to be recorded.
     */
    private static final List<Cue> LEG_CUES = List.of(
            new Cue(0, AflSounds.MEDICAL_BANDAGE_GRAB, 0.95F), new Cue(14, AflSounds.MEDICAL_BANDAGE_PRESS, 0.9F),
            new Cue(18, AflSounds.MEDICAL_BANDAGE_PRESS, 0.85F),
            new Cue(29, AflSounds.MEDICAL_BANDAGE_WRAP_1, 1F), new Cue(47, AflSounds.MEDICAL_BANDAGE_TEAR, 1F),
            new Cue(54, AflSounds.MEDICAL_BANDAGE_WRAP_2, 1F), new Cue(72, AflSounds.MEDICAL_BANDAGE_TEAR, 0.95F),
            new Cue(79, AflSounds.MEDICAL_BANDAGE_WRAP_3, 1F), new Cue(97, AflSounds.MEDICAL_BANDAGE_TEAR, 1.05F),
            new Cue(106, AflSounds.MEDICAL_BANDAGE_PAT, 0.95F));
    private static final List<Cue> ARM_CUES = List.of(
            new Cue(0, AflSounds.MEDICAL_BANDAGE_GRAB, 0.95F), new Cue(20, AflSounds.MEDICAL_BANDAGE_PRESS, 0.9F),
            new Cue(26, AflSounds.MEDICAL_BANDAGE_PRESS, 0.85F),
            new Cue(40, AflSounds.MEDICAL_BANDAGE_WRAP_1, 1F), new Cue(58, AflSounds.MEDICAL_BANDAGE_TEAR, 1F),
            new Cue(68, AflSounds.MEDICAL_BANDAGE_WRAP_2, 1F), new Cue(86, AflSounds.MEDICAL_BANDAGE_TEAR, 0.95F),
            new Cue(96, AflSounds.MEDICAL_BANDAGE_WRAP_3, 1F), new Cue(114, AflSounds.MEDICAL_BANDAGE_TEAR, 1.05F),
            new Cue(146, AflSounds.MEDICAL_BANDAGE_PAT, 0.95F), new Cue(150, AflSounds.MEDICAL_BANDAGE_LOWER, 1F));

    public SplintItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    private static boolean busy(Player player) {
        Entity vehicle = player.getVehicle();
        return player.isSwimming() || player.isUnderWater() || vehicle != null && vehicle.getControllingPassenger() == player;
    }

    private static List<Fractures.View> fractures(Player player) {
        return player instanceof ServerPlayer server ? Fractures.views(server) : ClientHealth.fractures();
    }

    /** The limb this player's splint goes on now, or null (the own player and the server know it; others: their marks). */
    @Nullable
    public static BodyPart target(Player player) {
        if (player instanceof ServerPlayer || player.isLocalPlayer()) return Fractures.splintTarget(fractures(player));
        var marks = ClientHealth.marks(player.getUUID());
        return Fractures.splintTarget(marks.broken(), marks.splinted());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (busy(player)) return InteractionResultHolder.fail(stack);
        List<Fractures.View> list = fractures(player);
        BodyPart limb = Fractures.splintTarget(list);
        if (limb == null) {
            if (!level.isClientSide)
                player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fracture."
                        + (list.isEmpty() ? "no_fracture" : "all_splinted")), true);
            return InteractionResultHolder.fail(stack);
        }
        if (limb.leg() && !legReady(player)) {
            // a leg is splinted sat on the ground: sit down first (the held key tries again every few ticks)
            if (player instanceof ServerPlayer server && GroundSit.seat(server) == null) GroundSit.sit(server);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Sat down and settled (the leg's dressings too, MedicalItem). The client asks for a little more than the server (its seat came later), so it never starts a
     * use the server refuses.
     */
    static boolean legReady(Player player) {
        GroundSeat seat = GroundSit.seat(player);
        if (seat == null || !GroundSit.settled(player)) return false;
        return !player.level().isClientSide || seat.tickCount >= GroundSit.SIT_TICKS + 2;
    }

    /** The ticks the use has run (the actions' time; a leg's ends at {@link #LEG_USE_TICKS}). */
    public static int usedTicks(LivingEntity entity) {
        return entity.isUsingItem() ? USE_TICKS - entity.getUseItemRemainingTicks() : 0;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof Player player && busy(player)) {
            player.stopUsingItem();
            return;
        }
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        BodyPart limb = target(player);
        boolean leg = limb != null && limb.leg();
        if (leg && GroundSit.seat(player) == null) {   // off the seat (the ground went): the leg is not done standing
            player.stopUsingItem();
            return;
        }
        int tick = USE_TICKS - remaining;
        for (Cue cue : leg ? LEG_CUES : ARM_CUES)
            if (cue.tick == tick)
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), cue.sound.get(), SoundSource.PLAYERS, 1F, cue.pitch);
        if (leg && tick >= LEG_USE_TICKS) {
            // the leg's use is done before the item's length: finish it here (vanilla would at USE_TICKS)
            finishUsingItem(stack, level, player);   // uses the stack up in place
            player.stopUsingItem();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) return stack;
        if (!Fractures.splint(server)) return stack;
        PlayerHealth.markSync(server);
        BodyMarkSync.update(server);   // at once: the splint the use put on stays drawn
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight.splint.description");
    }
}
