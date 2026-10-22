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
        NativeGunActions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }
    @GameTest(template="network_empty", timeoutTicks=100)
    public static void nativeFireAndDryFire(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "br51_01_fire"));
        var gun=new ItemStack(AflItems.BR51_01.get()); p.getInventory().setItem(0,gun); p.getInventory().selected=0;
        h.assertTrue(gun.getItem() instanceof NativeGunItem && !(gun.getItem() instanceof NativeAnimatedWeaponItem), "Production native gun");
        h.assertTrue(D.magazineCapacity()==20 && D.fireIntervalTicks()==4 && D.baseDamage()==18
                && D.ammoType().getPath().equals("762x51mm_round"), "Formal rifle ammunition, unchanged balance");
        p.getInventory().setItem(1,new ItemStack(AflItems.ROUND_9MM.get(),32));
        h.assertTrue(NativeGunAmmo.reserve(p.getInventory(),D)==0, "Rifle rejects pistol rounds");
        NativeGunAmmo.set(gun,D,20); // a new gun is empty; this fixture starts with a full magazine
        NativeGunActions.request(p,false,0);
        for(int i=0;i<20;i++) NativeGunActions.request(p,false,0);
        h.assertTrue(NativeGunAmmo.read(gun,D)==19,"One accepted debit under spam");
        h.runAfterDelay(4,()->{
            tick(p); NativeGunAmmo.set(gun,D,1); NativeGunActions.request(p,false,0);
            h.assertTrue(NativeGunAmmo.read(gun,D)==0,"Last round succeeds");
        });
        h.runAfterDelay(8,()->{
            tick(p); NativeGunActions.request(p,false,0); NativeGunActions.request(p,true,0);
            h.assertTrue(NativeGunAmmo.read(gun,D)==0,"Dry/reload cannot create ammo");
            h.assertTrue(NativeGunShot.damageAt(D,0)==18,"Body damage in HP"); h.succeed();
        });
    }
    /** A survivor with empty pockets holding {@code mags} full 20-round magazines (V3: rounds live in magazine items). */
    private static net.minecraft.server.level.ServerPlayer survivor(GameTestHelper h, String name, int mags) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected=0;
        var pockets=com.antaurora.apofirstlight.inventory.grid.Pockets.of(p);
        if(pockets!=null) pockets.set(new net.minecraft.nbt.ListTag());
        var view=com.antaurora.apofirstlight.inventory.grid.PlayerGrids.view(p);
        for(int i=0;i<mags;i++){
            var mag=new ItemStack(AflItems.BR51_01_MAGAZINE.get()); NativeMagazines.setRounds(mag,20);
            if(!view.get(com.antaurora.apofirstlight.inventory.grid.PlayerGrids.Target.POCKETS).insert(mag)) throw new IllegalStateException("no room in the pockets");
        }
        view.commit();
        return p;
    }
    private static int carried(net.minecraft.server.level.ServerPlayer p) {
        return com.antaurora.apofirstlight.inventory.grid.PlayerGrids.stacks(p).stream().filter(s->s.is(AflItems.BR51_01_MAGAZINE.get()))
                .mapToInt(NativeMagazines::rounds).sum();
    }
    @GameTest(template="network_empty", timeoutTicks=150)
    public static void reloadTimelinesAndNoDuplication(GameTestHelper h) {
        var p=survivor(h,"br51_01_reload",2);
        var gun=new ItemStack(AflItems.BR51_01.get()); p.getInventory().setItem(0,gun);
        NativeGunAmmo.initialize(gun,D); NativeGunAmmo.set(gun,D,10); NativeGunAmmo.setChambered(gun,D,true);   // 9 + 1
        h.assertTrue(NativeMagazines.system(D) && NativeMagazines.fitted(gun,D).is(AflItems.BR51_01_MAGAZINE.get()),"V3 loads from magazine items");
        h.assertTrue(NativeGunAnimations.ticks("br51_01_v3_native","reload_tactical")==48 && NativeGunAnimations.ticks("br51_01_v3_native","reload_empty")==58,"Source duration ceil");
        NativeGunActions.request(p,true,0); NativeGunActions.request(p,false,0);
        h.runAfterDelay(33,()->{tick(p); h.assertTrue(NativeGunAmmo.read(gun,D)==10,"No swap before the magazine seats");});
        h.runAfterDelay(35,()->{tick(p);
            h.assertTrue(NativeMagazines.magazineRounds(gun,D)==20 && NativeGunAmmo.read(gun,D)==21 && carried(p)==29,"One tactical swap, the old 9 kept");});
        h.runAfterDelay(50,()->{tick(p);
            NativeGunAmmo.set(gun,D,0); NativeGunAmmo.setChambered(gun,D,false); NativeGunActions.request(p,true,0);});
        h.runAfterDelay(109,()->{tick(p);tick(p);
            h.assertTrue(NativeMagazines.magazineRounds(gun,D)+NativeMagazines.chamber(gun,D)==20 && NativeGunAmmo.chambered(gun),"Empty reload: the 20 in, one chambered at the end");
            h.assertTrue(carried(p)==9,"Rounds conserved: the full one went in, the emptied one came out with none "+carried(p));h.succeed();});
    }
    @GameTest(template="network_empty",timeoutTicks=100)
    public static void cancelReloadAndSoundResources(GameTestHelper h) {
        var p=survivor(h,"br51_01_cancel",1);
        var gun=new ItemStack(AflItems.BR51_01.get());p.getInventory().setItem(0,gun);
        NativeGunAmmo.initialize(gun,D);NativeGunAmmo.set(gun,D,0);
        for(String clip:java.util.List.of("reload_tactical","reload_empty","draw","first_draw","put_away","inspect","inspect_empty",
                "inspect_no_mag","inspect_empty_no_mag","unload","unload_empty","shoot"))
            for(var cue:NativeGunAnimations.cues("br51_01_v3_native",clip))h.assertTrue(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(cue.sound()),"Registered cue "+cue.sound());
        NativeGunActions.request(p,true,0);p.getInventory().selected=2;tick(p);
        h.runAfterDelay(60,()->{tick(p);h.assertTrue(NativeGunAmmo.read(gun,D)==0 && carried(p)==20,"Canceled reload swaps nothing");h.succeed();});
    }
}
