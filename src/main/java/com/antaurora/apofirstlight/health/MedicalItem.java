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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Health V1a medical items (docs/gameplay/survival_health_v1a.md 7): the bandage covers a wound, medical alcohol cleans one
 * (a game stand-in for cleaning a wound, not medical advice). Hold right-click: the bandage takes 4 s, the alcohol 4.6 s,
 * as long as their player actions (item/bandage_wrap, item/alcohol_clean, first and third person alike; keyed in Blockbench,
 * tools/medical-actions-v1a/, tools/check-medical-actions-v1a.cjs checks these ticks; client/MedicalPourJets the poured
 * stream; the models are equipment meshes, tools/build-equipment-meshes-v1.mjs). The server plays the sounds on the
 * actions' keyframes ({@link #BANDAGE_CUES}, {@link #ALCOHOL_CUES}); letting go cancels and uses nothing. On finishing,
 * the server applies it to the wound that needs it most (Wounds#target) and uses one item up (not in Creative). It does not start when no wound takes it (the
 * client checks the same rule on its synced wounds), nor while swimming or driving.
 */
public final class MedicalItem extends Item {
    public static final int BANDAGE_TICKS = 80, ALCOHOL_TICKS = 92;
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
        return treatment == Wounds.Treatment.BANDAGE ? BANDAGE_TICKS : ALCOHOL_TICKS;
    }

    /** Busy with something that leaves no hand free for it. */
    private static boolean busy(Player player) {
        Entity vehicle = player.getVehicle();
        return player.isSwimming() || player.isUnderWater() || vehicle != null && vehicle.getControllingPassenger() == player;
    }

    private List<Wounds.View> wounds(Player player) {
        return player instanceof ServerPlayer server ? Wounds.views(server) : ClientHealth.wounds();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (busy(player)) return InteractionResultHolder.fail(stack);
        List<Wounds.View> wounds = wounds(player);
        if (Wounds.target(wounds, treatment) < 0) {
            if (!level.isClientSide) {
                String key = treatment == Wounds.Treatment.BANDAGE && Wounds.needsCleaning(wounds) ? "clean_first"
                        : wounds.isEmpty() ? "no_wound" : treatment == Wounds.Treatment.BANDAGE ? "all_bandaged" : "all_cleaned";
                player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wound." + key), true);
            }
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
        if (level.isClientSide) return;
        // to everyone near, the user too (null source): the action plays the same for all
        int tick = getUseDuration(stack) - remaining;
        for (Cue cue : treatment == Wounds.Treatment.BANDAGE ? BANDAGE_CUES : ALCOHOL_CUES)
            if (cue.tick == tick)
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), cue.sound.get(), SoundSource.PLAYERS, CUE_VOLUME, cue.pitch);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) return stack;
        if (!Wounds.treat(server, treatment)) return stack;
        PlayerHealth.markSync(server);
        BandageSync.update(server);   // at once: the wrap the use drew stays on (client/BandageWrapRenderer)
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight." + (treatment == Wounds.Treatment.BANDAGE ? "bandage" : "medical_alcohol") + ".description");
    }
}
