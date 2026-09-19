package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.registry.AflItems;
import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.TickEvent;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.*;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class NativeFireModeGameTests {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void load(net.minecraftforge.event.level.LevelEvent.Load e) {
        if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l)
            l.getStructureManager().getOrCreate(new ResourceLocation("afl_fire_mode_tests","empty"))
                    .fillFromWorld(l,new net.minecraft.core.BlockPos(0,300,0),new net.minecraft.core.Vec3i(1,1,1),false,net.minecraft.world.level.block.Blocks.STRUCTURE_VOID);
    }
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return List.of(new TestFunction("fire_modes","afl_fire_mode_tests:contracts","afl_fire_mode_tests:empty",40,0,true,NativeFireModeGameTests::contracts),
                new TestFunction("fire_modes","afl_fire_mode_tests:server","afl_fire_mode_tests:empty",240,0,true,NativeFireModeGameTests::server));
    }
    private static JsonObject json(String id) {
        try(var in=NativeFireModeGameTests.class.getResourceAsStream("/data/apocalypse_firstlight/native_guns/"+id+".json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private static NativeGunDefinition definition(String fire) {
        var o=json("cat");o.add("fire",JsonParser.parseString(fire));
        return NativeGunData.parse(new ResourceLocation("apocalypse_firstlight","cat"),o,false);
    }
    private static void contracts(GameTestHelper h) {
        for(String id:List.of("p9_01","br51_01","hr55")) {
            var d=NativeGunData.parse(new ResourceLocation("apocalypse_firstlight",id),json(id),false);
            h.assertTrue(d.fire().modes().equals(List.of(NativeFireMode.SEMI)),"legacy SEMI "+id);
            var stack=new ItemStack(AflItems.CAT.get());var before=stack.copy();
            h.assertTrue(!NativeFireModes.cycle(stack,d)&&ItemStack.matches(before,stack),"single mode has no mutation");
        }
        for(String modes:List.of("[\"semi\"]","[\"semi\",\"auto\"]","[\"semi\",\"burst\",\"auto\"]"))
            definition("{\"modes\":"+modes+",\"default_mode\":\"semi\",\"interval_ticks\":2}");
        for(String fire:List.of("{\"mode\":\"invalid\"}","{\"modes\":[\"semi\"],\"default_mode\":\"auto\"}",
                "{\"mode\":\"semi\",\"burst_count\":0}","{\"mode\":\"semi\",\"burst_count\":2.5}",
                "{\"mode\":\"semi\",\"burst_count\":\"3\"}","{\"mode\":\"semi\",\"burst_count\":33}",
                "{\"modes\":[]}","{\"modes\":[\"semi\",\"semi\"]}")) {
            boolean rejected=false;try{NativeFireProfile.parse(JsonParser.parseString(fire).getAsJsonObject());}catch(RuntimeException e){rejected=true;}
            h.assertTrue(rejected,"invalid fire rejected: "+fire);
        }
        var d=definition("{\"modes\":[\"semi\",\"burst\",\"auto\"],\"default_mode\":\"semi\",\"interval_ticks\":2}");
        var stack=new ItemStack(AflItems.CAT.get());
        for(var mode:List.of(NativeFireMode.BURST,NativeFireMode.AUTO,NativeFireMode.SEMI)) {
            h.assertTrue(NativeFireModes.cycle(stack,d)&&NativeFireModes.current(stack,d)==mode,"ordered cycle");
            stack=ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(NativeFireModes.current(stack,d)==mode,"serialized stack mode");
        }
        stack.getOrCreateTag().putString(NativeFireModes.TAG,"auto");
        NativeFireModes.sanitize(stack,NativeGunDefinition.P9_01);
        h.assertTrue(stack.getTag().getString(NativeFireModes.TAG).equals("semi"),"removed mode falls back and persists");
        h.succeed();
    }
    private static void tick(ServerPlayer p){NativeGunActions.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void press(ServerPlayer p){NativeFireControl.press(p,0,1,GeoItem.getId(p.getMainHandItem()));}
    private static int ammo(ServerPlayer p){return NativeGunAmmo.read(p.getMainHandItem(),((NativeGunItem)p.getMainHandItem().getItem()).definition());}
    private static void server(GameTestHelper h) {
        // One fixture adds BURST to the existing registered test stack; no production data mutation.
        final java.lang.reflect.Field field;
        final Map<ResourceLocation,NativeGunDefinition> original;
        try{field=NativeGunData.class.getDeclaredField("server");field.setAccessible(true);original=(Map<ResourceLocation,NativeGunDefinition>)field.get(null);
            var next=new HashMap<>(original);var d=definition("{\"modes\":[\"semi\",\"burst\",\"auto\"],\"default_mode\":\"semi\",\"interval_ticks\":2,\"burst_count\":3}");next.put(d.id(),d);field.set(null,Map.copyOf(next));
        }catch(Exception e){throw new RuntimeException(e);}
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"fire_mode_probe"));
        p.getInventory().selected=0;p.getInventory().setItem(0,new ItemStack(AflItems.CAT.get()));
        GeoItem.getOrAssignId(p.getMainHandItem(),h.getLevel());
        var d=((NativeGunItem)p.getMainHandItem().getItem()).definition();NativeGunAmmo.set(p.getMainHandItem(),d,30);tick(p);
        for(int i=1;i<=180;i++)h.runAfterDelay(i,()->tick(p));
        h.runAfterDelay(30,()->{press(p);press(p);});
        h.runAfterDelay(38,()->{h.assertTrue(ammo(p)==29,"SEMI held/repeated press only one");NativeFireControl.release(p);
            h.assertTrue(!NativeFireControl.switchMode(p,1,GeoItem.getId(p.getMainHandItem())),"wrong slot rejected");
            h.assertTrue(!NativeFireControl.switchMode(p,0,-99),"wrong identity rejected");
            h.assertTrue(NativeFireControl.switchMode(p,0,GeoItem.getId(p.getMainHandItem())),"switch to burst");press(p);NativeFireControl.release(p);});
        h.runAfterDelay(46,()->{h.assertTrue(ammo(p)==26,"burst finishes after release");
            NativeGunAmmo.set(p.getMainHandItem(),d,2);press(p);NativeFireControl.release(p);});
        h.runAfterDelay(54,()->{h.assertTrue(ammo(p)==0,"partial burst only consumes remaining 2");
            NativeGunAmmo.set(p.getMainHandItem(),d,1);press(p);NativeFireControl.release(p);});
        h.runAfterDelay(62,()->{h.assertTrue(ammo(p)==0,"partial burst only one");
            NativeGunAmmo.set(p.getMainHandItem(),d,20);h.assertTrue(NativeFireControl.switchMode(p,0,GeoItem.getId(p.getMainHandItem())),"switch auto");press(p);});
        h.runAfterDelay(69,()->{h.assertTrue(ammo(p)==16,"AUTO exact two-tick cadence");NativeFireControl.release(p);});
        h.runAfterDelay(76,()->{h.assertTrue(ammo(p)==16,"release stops auto");press(p);NativeGunActions.request(p,true,0);});
        h.runAfterDelay(84,()->{h.assertTrue(ammo(p)==15,"reload request cancels continuation");NativeFireControl.cancel(p);
            p.getInventory().selected=1;tick(p);});
        h.runAfterDelay(90,()->{p.getInventory().selected=0;tick(p);});
        h.runAfterDelay(120,()->{h.assertTrue(ammo(p)==15,"switch back does not resume auto");press(p);p.setHealth(0);tick(p);});
        h.runAfterDelay(128,()->{h.assertTrue(ammo(p)==14,"death cancels auto");
            NativeFireControl.logout(p);try{field.set(null,original);}catch(Exception e){throw new RuntimeException(e);}h.succeed();});
        for(var mode:List.of(NativeFireMode.BURST,NativeFireMode.AUTO)) {
            for(String reason:List.of("slot","stack","reload","death","spectator","logout"))
                cancellation(h,mode,reason);
            emptyMagazine(h,mode);
        }
        for(String id:List.of("p9_01","br51_01","hr55"))legacy(h,id);
    }

    private static ServerPlayer fixture(GameTestHelper h,String name,ItemStack stack) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));
        p.setPos(h.absolutePos(net.minecraft.core.BlockPos.ZERO).getX(),300,0);
        p.setXRot(-90); // No fixture damages another fixture or world structure.
        p.getInventory().selected=0;p.getInventory().setItem(0,stack);
        GeoItem.getOrAssignId(stack,h.getLevel());tick(p);
        // Leave tick 32 to the reload fixture, so its packet precedes the next scheduled shot.
        for(int i=1;i<=100;i++)if(i!=32)h.runAfterDelay(i,()->tick(p));
        return p;
    }
    private static void cancellation(GameTestHelper h,NativeFireMode mode,String reason) {
        var stack=new ItemStack(AflItems.CAT.get());
        stack.getOrCreateTag().putString(NativeFireModes.TAG,mode.key());
        var p=fixture(h,mode.key()+"_"+reason,stack);
        var d=((NativeGunItem)stack.getItem()).definition();NativeGunAmmo.set(stack,d,20);
        if(reason.equals("reload")) {
            p.getInventory().setItem(2,new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(d.ammoType()),64));
            h.runAfterDelay(32,()->{
                NativeGunActions.request(p,true,0);tick(p);
                h.assertTrue(NativeGunActions.busy(p)&&ammo(p)==19,mode+" reload started before continuation");
            });
        }
        h.runAfterDelay(30,()->{
            press(p);h.assertTrue(ammo(p)==19,mode+" starts before "+reason);
            switch(reason) {
                case "slot" -> p.getInventory().selected=1;
                case "stack" -> p.getInventory().setItem(0,stack.copy());
                case "reload" -> { /* Scheduled at the first legal reload tick above. */ }
                case "death" -> p.setHealth(0);
                case "spectator" -> p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                case "logout" -> NativeGunActions.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                default -> throw new AssertionError(reason);
            }
            tick(p);
        });
        h.runAfterDelay(55,()->{
            h.assertTrue(NativeGunAmmo.read(stack,d)==19,mode+" cancelled by "+reason);
            if(reason.equals("stack"))h.assertTrue(ammo(p)==19,"replacement stack not fired");
            if(reason.equals("slot")){p.getInventory().selected=0;tick(p);}
            if(reason.equals("reload")){p.getInventory().selected=1;tick(p);p.getInventory().selected=0;tick(p);}
        });
        h.runAfterDelay(95,()->{
            h.assertTrue(NativeGunAmmo.read(stack,d)==19,mode+" no stale resume after "+reason);
            NativeFireControl.logout(p);
        });
    }
    private static void emptyMagazine(GameTestHelper h,NativeFireMode mode) {
        var stack=new ItemStack(AflItems.CAT.get());stack.getOrCreateTag().putString(NativeFireModes.TAG,mode.key());
        var p=fixture(h,mode.key()+"_empty",stack);var d=((NativeGunItem)stack.getItem()).definition();
        NativeGunAmmo.set(stack,d,2);
        h.runAfterDelay(30,()->press(p));
        h.runAfterDelay(40,()->{h.assertTrue(ammo(p)==0,mode+" stops empty");NativeGunAmmo.set(stack,d,10);});
        h.runAfterDelay(48,()->{h.assertTrue(ammo(p)==10,mode+" held empty does not resume after refill");
            NativeFireControl.release(p);press(p);NativeFireControl.release(p);});
        h.runAfterDelay(58,()->{h.assertTrue(ammo(p)==(mode==NativeFireMode.BURST?7:9),mode+" fresh edge works after empty");NativeFireControl.logout(p);});
    }
    private static void legacy(GameTestHelper h,String id) {
        var stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("apocalypse_firstlight",id)));
        var p=fixture(h,id,stack);var d=((NativeGunItem)stack.getItem()).definition();NativeGunAmmo.set(stack,d,5);
        h.runAfterDelay(30,()->{
            var before=stack.copy();
            h.assertTrue(!NativeFireControl.switchMode(p,0,GeoItem.getId(stack)),id+" server rejects single-mode switch");
            h.assertTrue(ItemStack.matches(before,stack),id+" single-mode stack unchanged");
            press(p);press(p);
        });
        h.runAfterDelay(45,()->{h.assertTrue(ammo(p)==4,id+" stays SEMI when held");NativeFireControl.release(p);press(p);NativeFireControl.release(p);});
        h.runAfterDelay(58,()->{h.assertTrue(ammo(p)==3,id+" new press fires once");NativeFireControl.logout(p);});
    }
}
