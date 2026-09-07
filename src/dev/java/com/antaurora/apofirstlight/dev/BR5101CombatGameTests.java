package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
public class BR5101CombatGameTests {
    private static final NativeGunDefinition D = NativeGunDefinition.BR51_01;
    private static void tick(net.minecraft.server.level.ServerPlayer p) {
        P901Actions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }
    @GameTest(template="network_empty", timeoutTicks=100)
    public static void nativeFireAndDryFire(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "br51_01_fire"));
        var gun=new ItemStack(AflItems.BR51_01.get()); p.getInventory().setItem(0,gun); p.getInventory().selected=0;
        h.assertTrue(gun.getItem() instanceof NativeGunItem && !(gun.getItem() instanceof NativeAnimatedWeaponItem), "Production native gun");
        h.assertTrue(D.magazineCapacity()==20 && D.fireIntervalTicks()==4 && D.baseDamage()==18 && D.ammoType().equals(NativeGunDefinition.P9_01.ammoType()), "Temporary definition");
        P901Actions.request(p,false,0);
        for(int i=0;i<20;i++) P901Actions.request(p,false,0);
        h.assertTrue(NativeGunAmmo.read(gun,D)==19,"One accepted debit under spam");
        h.runAfterDelay(4,()->{
            tick(p); NativeGunAmmo.set(gun,D,1); P901Actions.request(p,false,0);
            h.assertTrue(NativeGunAmmo.read(gun,D)==0,"Last round succeeds");
        });
        h.runAfterDelay(8,()->{
            tick(p); P901Actions.request(p,false,0); P901Actions.request(p,true,0);
            h.assertTrue(NativeGunAmmo.read(gun,D)==0,"Dry/reload cannot create ammo");
            h.assertTrue(NativeGunShot.damageAt(D,0)==18,"Body damage in HP"); h.succeed();
        });
    }
    @GameTest(template="network_empty", timeoutTicks=150)
    public static void reloadTimelinesAndNoDuplication(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"br51_01_reload"));
        var gun=new ItemStack(AflItems.BR51_01.get()); p.getInventory().selected=0; p.getInventory().setItem(0,gun);
        p.getInventory().setItem(1,new ItemStack(AflItems.ROUND_9MM.get(),32)); NativeGunAmmo.set(gun,D,10);
        h.assertTrue(NativeGunAnimations.ticks("br51_01","reload_tactical")==52 && NativeGunAnimations.ticks("br51_01","reload_empty")==57,"Source duration ceil");
        P901Actions.request(p,true,0); P901Actions.request(p,false,0);
        h.runAfterDelay(51,()->{tick(p); h.assertTrue(NativeGunAmmo.read(gun,D)==10,"No refill before end");});
        h.runAfterDelay(52,()->{
            tick(p); tick(p); h.assertTrue(NativeGunAmmo.read(gun,D)==20 && NativeGunAmmo.reserve(p.getInventory(),D)==22,"One tactical commit");
            NativeGunAmmo.set(gun,D,0); P901Actions.request(p,true,0);
        });
        h.runAfterDelay(108,()->{tick(p);h.assertTrue(NativeGunAmmo.read(gun,D)==0,"Empty waits full57 ticks");});
        h.runAfterDelay(109,()->{
            tick(p);tick(p);h.assertTrue(NativeGunAmmo.read(gun,D)==20 && NativeGunAmmo.reserve(p.getInventory(),D)==2,"Empty consumes20 rounds once");h.succeed();
        });
    }
    @GameTest(template="network_empty",timeoutTicks=100)
    public static void cancelReloadAndSoundResources(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"br51_01_cancel"));
        var gun=new ItemStack(AflItems.BR51_01.get());p.getInventory().setItem(0,gun);p.getInventory().selected=0;
        NativeGunAmmo.set(gun,D,0);p.getInventory().setItem(1,new ItemStack(AflItems.ROUND_9MM.get(),3));
        for(String clip:java.util.List.of("reload_tactical","reload_empty","draw","put_away","inspect","shoot"))
            for(var cue:NativeGunAnimations.cues("br51_01",clip))h.assertTrue(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(cue.sound()),"Registered cue "+cue.sound());
        P901Actions.request(p,true,0);p.getInventory().selected=2;tick(p);
        h.runAfterDelay(60,()->{tick(p);h.assertTrue(NativeGunAmmo.read(gun,D)==0 && NativeGunAmmo.reserve(p.getInventory(),D)==3,"Canceled reload commits nothing");
            h.assertTrue(NativeGunAmmo.transfer(p.getInventory(),gun,D)==3 && NativeGunAmmo.read(gun,D)==3,"Partial reserve works");h.succeed();});
    }
}
