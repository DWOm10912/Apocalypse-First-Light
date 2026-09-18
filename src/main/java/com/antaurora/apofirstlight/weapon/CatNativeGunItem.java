package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/** C.A.T flavor behavior; all gun mechanics remain in ConfiguredNativeGunItem. */
public final class CatNativeGunItem extends ConfiguredNativeGunItem {
    private static final int MIN_IDLE_TICKS = 12 * 20;
    private static final int MAX_IDLE_TICKS = 25 * 20;
    private final Map<ServerPlayer, IdleState> idleStates = new WeakHashMap<>();

    private static final class IdleState {
        private final ItemStack stack;
        private long dueTick;
        private long lastHeldTick;

        private IdleState(ItemStack stack, long dueTick, long lastHeldTick) {
            this.stack = stack;
            this.dueTick = dueTick;
            this.lastHeldTick = lastHeldTick;
        }
    }

    public CatNativeGunItem(ResourceLocation definitionId, NativeAnimatedWeaponItem.Profile profile) {
        super(definitionId, profile);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        long now = level.getGameTime();
        if (!selected || player.getMainHandItem() != stack) {
            IdleState current = idleStates.get(player);
            if (current != null && current.stack == stack) idleStates.remove(player);
            return;
        }

        IdleState state = idleStates.get(player);
        if (state == null || state.stack != stack || now - state.lastHeldTick > 1) {
            idleStates.put(player, new IdleState(stack, now + randomDelay(player), now));
            return;
        }
        state.lastHeldTick = now;
        if (now < state.dueTick) return;
        if (NativeGunActions.busy(player)) {
            state.dueTick = now + randomDelay(player);
            return;
        }

        var sound = player.getRandom().nextBoolean() ? AflSounds.CAT_IDLE_1.get() : AflSounds.CAT_IDLE_2.get();
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 0.55F, 1.0F);
        state.dueTick = now + randomDelay(player);
    }

    private static int randomDelay(ServerPlayer player) {
        return MIN_IDLE_TICKS + player.getRandom().nextInt(MAX_IDLE_TICKS - MIN_IDLE_TICKS + 1);
    }
}
