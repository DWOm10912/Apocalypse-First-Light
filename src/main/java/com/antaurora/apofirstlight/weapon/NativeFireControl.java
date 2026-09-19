package com.antaurora.apofirstlight.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.WeakHashMap;

/** Server trigger edges and continuation only. Every bullet uses NativeGunActions.request. */
public final class NativeFireControl {
    private static final Map<ServerPlayer, State> STATES=new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> SEQUENCES=new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> SWITCH_AFTER=new WeakHashMap<>();
    private static final class State {
        final ItemStack stack;
        final int slot;
        final ResourceKey<Level> dimension;
        final NativeFireMode mode;
        boolean held=true;
        int remaining;
        long due;
        State(ServerPlayer p,NativeFireMode mode) {
            stack=p.getMainHandItem();slot=p.getInventory().selected;dimension=p.level().dimension();this.mode=mode;
        }
    }
    private NativeFireControl() {}
    public static void clear() { STATES.clear(); SEQUENCES.clear(); SWITCH_AFTER.clear(); }
    public static void cancel(ServerPlayer p) { STATES.remove(p); }
    public static void logout(ServerPlayer p) { cancel(p);SEQUENCES.remove(p);SWITCH_AFTER.remove(p); }
    public static void release(ServerPlayer p) {
        var s=STATES.get(p);
        if(s==null)return;
        s.held=false;
        if(s.mode!=NativeFireMode.BURST || s.remaining==0)cancel(p);
    }
    private static boolean valid(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu
                &&p.getMainHandItem().getItem() instanceof NativeGunItem;
    }
    public static void press(ServerPlayer p,int slot,long shotId,long gunId) {
        if(!valid(p)||slot!=p.getInventory().selected||slot<0||slot>8
                ||software.bernie.geckolib.animatable.GeoItem.getId(p.getMainHandItem())!=gunId)return;
        if(STATES.containsKey(p))return;
        var item=(NativeGunItem)p.getMainHandItem().getItem();var d=item.definition();
        var s=new State(p,NativeFireModes.current(p.getMainHandItem(),d));
        STATES.put(p,s); // Retain the edge even if this click is blocked by draw/cooldown.
        int before=NativeGunAmmo.read(s.stack,d);
        NativeGunActions.request(p,false,slot,shotId);
        if(NativeGunAmmo.read(s.stack,d)>=before)return;
        s.remaining=s.mode==NativeFireMode.BURST?d.fire().burstCount()-1:s.mode==NativeFireMode.AUTO?1:0;
        s.due=p.server.getTickCount()+d.fireIntervalTicks();
    }
    /** Called after action-session processing, never runs a second damage implementation. */
    public static void tick(ServerPlayer p) {
        var s=STATES.get(p);if(s==null)return;
        if(!valid(p)||p.getMainHandItem()!=s.stack||p.getInventory().selected!=s.slot
                ||p.level().dimension()!=s.dimension){cancel(p);return;}
        var d=((NativeGunItem)s.stack.getItem()).definition();
        if(NativeFireModes.current(s.stack,d)!=s.mode){cancel(p);return;}
        if(s.remaining==0){if(!s.held)cancel(p);return;}
        if(NativeGunAmmo.read(s.stack,d)==0){s.remaining=0;if(!s.held)cancel(p);return;}
        if(p.server.getTickCount()<s.due)return;
        if(NativeGunActions.busy(p)){s.remaining=0;if(!s.held)cancel(p);return;}
        int before=NativeGunAmmo.read(s.stack,d);
        long id=SEQUENCES.merge(p,-1L,(a,b)->a-1); // Negative IDs are server-scheduled visual shots.
        NativeGunActions.request(p,false,s.slot,id);
        if(NativeGunAmmo.read(s.stack,d)>=before){s.remaining=0;if(!s.held)cancel(p);return;}
        if(s.mode==NativeFireMode.BURST)s.remaining--;
        s.due=p.server.getTickCount()+d.fireIntervalTicks();
        if(s.remaining==0&&!s.held)cancel(p);
    }
    public static boolean switchMode(ServerPlayer p,int slot,long gunId) {
        if(!valid(p)||p.getInventory().selected!=slot||NativeGunActions.busy(p)||STATES.containsKey(p)
                ||p.server.getTickCount()<SWITCH_AFTER.getOrDefault(p,0L)
                ||software.bernie.geckolib.animatable.GeoItem.getId(p.getMainHandItem())!=gunId)return false;
        var stack=p.getMainHandItem();var d=((NativeGunItem)stack.getItem()).definition();
        if(!NativeFireModes.cycle(stack,d))return false;
        SWITCH_AFTER.put(p,p.server.getTickCount()+4L);
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.fire_mode",
                Component.translatable("fire_mode.apocalypse_firstlight."+NativeFireModes.current(stack,d).key())),true);
        // Only an actual server-accepted mode change reaches this shared selector sound.
        p.serverLevel().playSound(null,p.getX(),p.getY(),p.getZ(),
                com.antaurora.apofirstlight.registry.AflSounds.NATIVE_GUN_FIRE_MODE_SWITCH.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,.5F,1F);
        return true;
    }
}
