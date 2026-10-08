package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BeverageCoolerBlock;
import com.antaurora.apofirstlight.block.BeverageCoolerDoorRaycast;
import com.antaurora.apofirstlight.block.ChargingStationBlock;
import com.antaurora.apofirstlight.block.ChestFreezerBlock;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.block.RetailShelfSingleBlock;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import com.antaurora.apofirstlight.blockentity.RetailShelfSingleBlockEntity;
import com.antaurora.apofirstlight.blockentity.VendingMachineBlockEntity;
import com.antaurora.apofirstlight.blockentity.ChargingStationBlockEntity;
import com.antaurora.apofirstlight.block.VendingMachineBlock;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * World interaction prompt (formerly VendingMachineHint): one {@link AttachmentHintStyle} label with a shared fade. Each
 * block decides from the crosshair whether it offers an action and which; the server-side use() applies the same test.
 * The label sits at the crosshair (vending machine), or at a world prompt anchor projected to the screen (Mesh Shape
 * regions, e.g. the locker's lock), falling back to the crosshair when the anchor is behind the camera.
 */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class WorldInteractionHint {
    private record Target(Component label, Vec3 anchor) {}
    private static float fade;
    private static long last=System.nanoTime();
    private static Component label;
    private static java.util.List<com.antaurora.apofirstlight.client.ui.AflKeyHint> keys=java.util.List.of();
    private static Vec3 anchor;
    private static final Matrix4f VIEW=new Matrix4f(), PROJECTION=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;

    /** This frame's world view / projection (with view bobbing), for projecting prompt anchors. */
    @SubscribeEvent public static void captureCamera(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY) return;
        VIEW.set(event.getPoseStack().last().pose());PROJECTION.set(event.getProjectionMatrix());camera=event.getCamera().getPosition();
    }

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post event) {
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        float step=Math.min(.2f,(now-last)/1_000_000_000f)/AttachmentHintStyle.FADE_SECONDS;last=now;
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui) {fade=0;return;}
        Target target=null;
        if(!mc.player.isSpectator() && mc.hitResult instanceof BlockHitResult hit) {
            // sneaking with an empty hand on a plug-in appliance acts on its plug: that hint comes before the doors and lids
            if(mc.player.isShiftKeyDown()) target=powerOutlets(mc,hit);
            if(target==null) target=vendingMachine(mc,hit);
            if(target==null) target=coolerDoor(mc,hit);
            if(target==null) target=freezerLid(mc,hit);
            if(target==null) target=dumpsterLid(mc,hit);
            if(target==null) target=retailContents(mc,hit);
            if(target==null) target=counterGate(mc,hit);
            if(target==null) target=storefrontGlazing(mc,hit);
            if(target==null) target=aluminumCornice(mc,hit);
            if(target==null) target=eyebrowCanopy(mc,hit);
            if(target==null) target=woodDoorStyle(mc,hit);
            if(target==null) target=buildingPower(mc,hit);
            if(target==null) target=powerOutlets(mc,hit);
            if(target==null) target=fuelDispenser(mc,hit);
            if(target==null) target=intakePump(mc,hit);
            if(target==null) target=fuelContainer(mc,hit);
            if(target==null) target=fuelSumpCover(mc,hit);
            if(target==null) target=chargingStation(mc,hit);
            if(target==null) target=powerCable(mc,hit);
            if(target==null) target=fluidPipe(mc,hit);
            if(target==null) target=meshInteraction(mc,hit);
        }
        if(target!=null) {label=target.label();anchor=target.anchor();keys=keysFor(mc,label);}   // keep the last label while fading out
        fade=Math.max(0,Math.min(1,fade+(target!=null?step:-step)));
        if(fade<=.03f||label==null) return;
        int w=event.getWindow().getGuiScaledWidth(),h=event.getWindow().getGuiScaledHeight();
        float[] at=anchor==null?null:project(anchor,w,h);
        AttachmentHintStyle.draw(event.getGuiGraphics(),keys,label,at==null?w/2:Math.round(at[0]),at==null?h/2:Math.round(at[1]),w,fade);
    }

    /**
     * The key caps in front of a prompt (docs/ui/afl_overlay_ui_style_v1.md 6), following the player's bindings: the use key
     * for every action, sneak + use to pick a container up, none for prompts that only tell (cannot charge, needs a crowbar,
     * needs a container).
     */
    private static java.util.List<com.antaurora.apofirstlight.client.ui.AflKeyHint> keysFor(Minecraft mc,Component label) {
        String key=label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t?t.getKey():"";
        var use=com.antaurora.apofirstlight.client.ui.AflKeyHint.of(mc.options.keyUse);
        if(key.endsWith(".cannot_charge")||key.endsWith(".needs_crowbar")||key.endsWith(".needs_container")) return java.util.List.of();
        if(key.endsWith("fuel_container.pick_up")||key.contains(".storefront_glazing.")||key.contains(".aluminum_cornice.")||key.contains(".metal_eyebrow_canopy.")) return java.util.List.of(com.antaurora.apofirstlight.client.ui.AflKeyHint.of(mc.options.keyShift),use);
        return java.util.List.of(use);
    }

    private static float[] project(Vec3 world,int width,int height) {
        var v=new Vector4f((float)(world.x-camera.x),(float)(world.y-camera.y),(float)(world.z-camera.z),1);
        VIEW.transform(v);PROJECTION.transform(v);
        if(v.w<=.05f) return null;
        return new float[]{(v.x/v.w*.5f+.5f)*width,(.5f-v.y/v.w*.5f)*height};
    }

    private static Target vendingMachine(Minecraft mc,BlockHitResult hit) {
        if(CrowbarSmashClient.active() || !mc.player.getMainHandItem().is(AflItems.CROWBAR.get())) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        return s.getBlock() instanceof VendingMachineBlock && !s.getValue(VendingMachineBlock.BROKEN)
                && VendingMachineBlock.frontPoint(s,hit.getBlockPos(),mc.player.getEyePosition(),hit)!=null
                ? new Target(Component.translatable("hint.apocalypse_firstlight.break_glass"),null) : null;
    }

    /**
     * Beverage cooler doors, resolved like a click (BeverageCoolerDoorInput first ray-tests open leaves, also where they
     * swing out of their cells, then the vanilla hit): open / close, drawn at the door's pull handle.
     */
    private static Target coolerDoor(Minecraft mc,BlockHitResult hit) {
        Vec3 eye=mc.player.getEyePosition();
        Vec3 end=eye.add(mc.player.getViewVector(1.0F).scale(mc.gameMode==null?4.5:mc.gameMode.getPickRange()));
        var leaf=BeverageCoolerDoorRaycast.find(mc.level,mc.player,eye,end);
        if(leaf!=null) {
            var facing=mc.level.getBlockState(leaf.master()).getValue(BeverageCoolerBlock.FACING);
            return new Target(Component.translatable("hint.apocalypse_firstlight.beverage_cooler.close"),
                    BeverageCoolerBlock.promptAnchor(leaf.master(),facing,leaf.left(),true));
        }
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof BeverageCoolerBlock)) return null;
        var door=BeverageCoolerBlock.promptDoor(mc.level,hit.getBlockPos(),s,hit.getLocation());
        return door==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.beverage_cooler."+(door.open()?"close":"open")),
                BeverageCoolerBlock.promptAnchor(door.master(),door.facing(),door.left(),door.open()));
    }

    /**
     * Searchable retail fixtures (2026-10-01), resolved like their use(): a shelf aimed at its decks, the cooler aimed
     * behind an open door, the vending machine aimed at its broken glass. "Search" while hidden slots remain, else "View";
     * drawn at the crosshair.
     */
    private static Target retailContents(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var pos=hit.getBlockPos();var s=mc.level.getBlockState(pos);var eye=mc.player.getEyePosition();
        String key=null;Boolean complete=null;
        if(s.getBlock() instanceof RetailShelfSingleBlock) {
            var lower=s.getValue(RetailShelfSingleBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER?pos.below():pos;
            if(mc.level.getBlockEntity(lower) instanceof RetailShelfSingleBlockEntity shelf
                    &&RetailShelfSingleBlock.getClickedCell(eye,mc.level.getBlockState(lower).getValue(RetailShelfSingleBlock.FACING),lower,hit)>=0) {
                key="retail_shelf";complete=shelf.isSearchCompleteForPrompt();
            }
        } else if(s.getBlock() instanceof BeverageCoolerBlock) {
            var master=BeverageCoolerBlock.masterPosition(pos,s);
            if(BeverageCoolerBlock.aimsInside(mc.level,master,eye,hit.getLocation())
                    &&mc.level.getBlockEntity(master) instanceof BeverageCoolerBlockEntity cooler) {
                key="beverage_cooler";complete=cooler.isSearchCompleteForPrompt();
            }
        } else if(s.getBlock() instanceof com.antaurora.apofirstlight.block.CheckoutCounterBlock counter&&counter.opensFrom(s,hit.getDirection())
                &&mc.level.getBlockEntity(pos) instanceof com.antaurora.apofirstlight.blockentity.CheckoutCounterBlockEntity box) {
            key="checkout_counter";complete=box.isSearchCompleteForPrompt();
        } else if(s.getBlock() instanceof com.antaurora.apofirstlight.block.BackBarShelfBlock&&hit.getDirection()==s.getValue(com.antaurora.apofirstlight.block.BackBarShelfBlock.FACING)
                &&mc.level.getBlockEntity(com.antaurora.apofirstlight.block.BackBarShelfBlock.lower(s,pos)) instanceof com.antaurora.apofirstlight.blockentity.BackBarShelfBlockEntity bar) {
            key="back_bar_shelf";complete=bar.isSearchCompleteForPrompt();
        } else if(s.getBlock() instanceof com.antaurora.apofirstlight.block.StorageRackBlock&&hit.getDirection()==s.getValue(com.antaurora.apofirstlight.block.StorageRackBlock.FACING)
                &&mc.level.getBlockEntity(com.antaurora.apofirstlight.block.StorageRackBlock.lower(s,pos)) instanceof com.antaurora.apofirstlight.blockentity.StorageRackBlockEntity rack) {
            key="storage_rack";complete=rack.isSearchCompleteForPrompt();
        } else if(s.getBlock() instanceof VendingMachineBlock&&s.getValue(VendingMachineBlock.BROKEN)
                &&VendingMachineBlock.frontPoint(s,pos,eye,hit)!=null
                &&mc.level.getBlockEntity(VendingMachineBlock.lower(s,pos)) instanceof VendingMachineBlockEntity machine) {
            key="vending_machine";complete=machine.isSearchCompleteForPrompt();
        }
        return key==null?null:new Target(Component.translatable("hint.apocalypse_firstlight."+key+"."+(complete?"view":"search")),null);
    }

    /** Checkout counter gate (any face, as CheckoutCounterGateBlock#use): open / close, at the crosshair. */
    /** Storefront glazing, only while sneaking with an empty hand (as StorefrontGlazingBlock#use): mullion, or transom on the top quarter. */
    private static Target storefrontGlazing(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().isEmpty()) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.StorefrontGlazingBlock)) return null;
        boolean transom=com.antaurora.apofirstlight.block.StorefrontGlazingBlock.inTransomZone(hit.getBlockPos(),hit.getLocation().y);
        return new Target(Component.translatable("hint.apocalypse_firstlight.storefront_glazing."+(transom?"transom":"mullion")),null);
    }

    /** Aluminum cornice, only while sneaking with an empty hand (as AluminumCorniceBlock#use): the red band on or off. */
    private static Target aluminumCornice(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().isEmpty()) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.AluminumCorniceBlock)) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.aluminum_cornice."+(s.getValue(com.antaurora.apofirstlight.block.AluminumCorniceBlock.BAND)?"band_off":"band_on")),null);
    }

    /** Metal eyebrow canopy, only while sneaking with an empty hand (as MetalEyebrowCanopyBlock#use): the tie rod on or off. */
    private static Target eyebrowCanopy(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().isEmpty()) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.MetalEyebrowCanopyBlock)) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.metal_eyebrow_canopy."+(s.getValue(com.antaurora.apofirstlight.block.MetalEyebrowCanopyBlock.ROD)?"rod_off":"rod_on")),null);
    }

    /** Power Outlets V1: what right-click does with a plug: into a socket, out of one, or a device's own plug (sneaking); a strip's switch otherwise. */
    private static Target powerOutlets(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!mc.player.getMainHandItem().isEmpty()) return null;
        var pos=hit.getBlockPos();var s=mc.level.getBlockState(pos);String k="hint.apocalypse_firstlight.";
        boolean carrying=com.antaurora.apofirstlight.client.PlugCordRenderer.localCarrying();
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.WallOutletBlock){
            if(carrying) return new Target(Component.translatable(k+"plug.plug_in"),null);
            int socket=com.antaurora.apofirstlight.energy.PowerPlugs.aimedSocket(mc.level,pos,hit);
            return com.antaurora.apofirstlight.block.WallOutletBlock.used(s,socket)?new Target(Component.translatable(k+"plug.unplug"),null):null;
        }
        net.minecraft.core.BlockPos owner=plugOwner(pos,s);
        var cord=owner==null?null:com.antaurora.apofirstlight.energy.PowerPlugs.owner(mc.level,owner);
        if(cord==null) return null;
        boolean strip=s.getBlock() instanceof com.antaurora.apofirstlight.block.PowerStripBlock;
        if(mc.player.isShiftKeyDown()) return new Target(Component.translatable(k+"plug."+(cord.carrierId()==mc.player.getId()?"put_back":cord.host()!=null?"unplug":"take")),null);
        if(!strip) return null;
        if(carrying&&!owner.equals(com.antaurora.apofirstlight.client.PlugCordRenderer.localCarriedOwner())) return new Target(Component.translatable(k+"plug.plug_in"),null);
        return new Target(Component.translatable(k+"power_strip."+(s.getValue(com.antaurora.apofirstlight.block.PowerStripBlock.ON)?"switch_off":"switch_on")),null);
    }

    /** The cell of the block entity that keeps a device's power cord: a strip itself, an appliance's master / lower cell. */
    private static net.minecraft.core.BlockPos plugOwner(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState s) {
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.PowerStripBlock) return pos;
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.BeverageCoolerBlock) return com.antaurora.apofirstlight.block.BeverageCoolerBlock.masterPosition(pos,s);
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.ChestFreezerBlock) return com.antaurora.apofirstlight.block.ChestFreezerBlock.masterPosition(pos,s);
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.VendingMachineBlock) return com.antaurora.apofirstlight.block.VendingMachineBlock.lower(s,pos);
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.WaterDispenserBlock)
            return s.getValue(com.antaurora.apofirstlight.block.WaterDispenserBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER?pos.below():pos;
        return null;
    }

    /** Building Power V1: the panel opens its screen; the meter box names what right-click does to the disconnect. */
    private static Target buildingPower(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var pos=hit.getBlockPos();var s=mc.level.getBlockState(pos);
        if(s.getBlock() instanceof com.antaurora.apofirstlight.block.DistributionPanelBlock) return new Target(Component.translatable("hint.apocalypse_firstlight.distribution_panel.open"),null);
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.ServiceMeterBoxBlock)) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.service_meter_box."+(s.getValue(com.antaurora.apofirstlight.block.ServiceMeterBoxBlock.ON)?"turn_off":"turn_on")),null);
    }

    /** Commercial wood door, only while sneaking with an empty hand (as CommercialWoodDoorBlock#use): the next look. */
    private static Target woodDoorStyle(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().isEmpty()) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.CommercialWoodDoorBlock)) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.commercial_wood_door.to_"
                +s.getValue(com.antaurora.apofirstlight.block.CommercialWoodDoorBlock.STYLE).next().getSerializedName()),null);
    }

    private static Target counterGate(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.CheckoutCounterGateBlock)) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.checkout_counter_gate."
                +(s.getValue(com.antaurora.apofirstlight.block.CheckoutCounterGateBlock.OPEN)?"close":"open")),null);
    }

    /**
     * Chest freezer, resolved like ChestFreezerBlock#use: open the hit half, close the open half from the stacked lids
     * (drawn above the grip of the lid that moves), or search / view the contents from the open half's well (drawn in
     * its middle); nothing while a lid slides.
     */
    private static Target freezerLid(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof ChestFreezerBlock)) return null;
        var prompt=ChestFreezerBlock.prompt(mc.level,hit.getBlockPos(),s,hit.getLocation());
        return prompt==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.chest_freezer."+prompt.key()),prompt.anchor());
    }

    /**
     * Commercial dumpster, resolved like CommercialDumpsterBlock#use (from the player's view ray): open the aimed half's lid
     * (drawn at its grip); on an open half, search / view (drawn over its mouth); on an open lid, shut it (drawn on the lid).
     */
    private static Target dumpsterLid(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.CommercialDumpsterBlock)) return null;
        var prompt=com.antaurora.apofirstlight.block.CommercialDumpsterBlock.prompt(mc.level,hit.getBlockPos(),s,hit.getLocation(),mc.player);
        return prompt==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.dumpster."+prompt.key()),prompt.anchor());
    }

    /**
     * Charging station tray, resolved like ChargingStationBlock#use (main hand first, then the off hand): place a chargeable
     * item, "cannot charge" for anything else, or take the item back (either hand empty) with its synced FE; drawn above
     * the tray.
     */
    private static Target chargingStation(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof ChargingStationBlock)) return null;
        var master=ChargingStationBlock.masterPosition(hit.getBlockPos(),s);
        if(!(mc.level.getBlockEntity(master) instanceof ChargingStationBlockEntity station)||!station.isMaster()) return null;
        Vec3 anchor=ChargingStationBlock.sourceToWorld(master,s.getValue(ChargingStationBlock.FACING),
                ChargingStationBlock.ITEM_X,ChargingStationBlock.ITEM_Y+2.5,ChargingStationBlock.ITEM_Z);
        var main=mc.player.getMainHandItem();var off=mc.player.getOffhandItem();
        if(station.item().isEmpty()) {
            if(main.isEmpty()&&off.isEmpty()) return null;
            boolean chargeable=ChargingStationBlockEntity.canCharge(main)||ChargingStationBlockEntity.canCharge(off);
            return new Target(Component.translatable("hint.apocalypse_firstlight.charging_station."+(chargeable?"place":"cannot_charge")),anchor);
        }
        if(!main.isEmpty()&&!off.isEmpty()) return null;
        return new Target(Component.translatable("hint.apocalypse_firstlight.charging_station.take",
                String.format(Locale.ROOT,"%,d",station.itemEnergy()),String.format(Locale.ROOT,"%,d",station.itemCapacity())),anchor);
    }

    /**
     * Power cable, while sneaking with an empty main hand: the cable-to-cable side a click would cut or join
     * (PowerCableBlock#toggleSide), drawn on that side of the cable.
     */
    private static Target powerCable(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!PowerCableBlock.canToggle(mc.player,InteractionHand.MAIN_HAND)) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof PowerCableBlock)) return null;
        Direction side=PowerCableBlock.promptToggleSide(mc.level,hit.getBlockPos(),s,hit.getLocation(),hit.getDirection());
        if(side==null) return null;
        boolean connected=s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side));
        Vec3 anchor=Vec3.atCenterOf(hit.getBlockPos()).add(side.getStepX()*.3,side.getStepY()*.3,side.getStepZ()*.3);
        return new Target(Component.translatable("hint.apocalypse_firstlight.power_cable."+(connected?"cut":"join")),anchor);
    }

    /**
     * Fluid pipe, as the power cable: while sneaking with an empty main hand, the pipe-to-pipe side a click would cut or
     * join (FluidPipeBlock#toggleSide), drawn on that side of the pipe.
     */
    private static Target fluidPipe(Minecraft mc,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||!PowerCableBlock.canToggle(mc.player,InteractionHand.MAIN_HAND)) return null;
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.FluidPipeBlock)) return null;
        Direction side=com.antaurora.apofirstlight.block.FluidPipeBlock.promptToggleSide(mc.level,hit.getBlockPos(),s,hit.getLocation(),hit.getDirection());
        if(side==null) return null;
        boolean connected=s.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side));
        Vec3 anchor=Vec3.atCenterOf(hit.getBlockPos()).add(side.getStepX()*.35,side.getStepY()*.35,side.getStepZ()*.35);
        return new Target(Component.translatable("hint.apocalypse_firstlight.fluid_pipe."+(connected?"cut":"join")),anchor);
    }

    /**
     * Fuel dispenser, as FuelDispenserBlock#use: with an empty main hand, take the aimed holstered nozzle (gasoline / diesel);
     * holding one of its nozzles, hang it back. Drawn at the nozzle's hood.
     */
    private static Target fuelDispenser(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.FuelDispenserBlock)) return null;
        var prompt=com.antaurora.apofirstlight.block.FuelDispenserBlock.prompt(mc.level,hit.getBlockPos(),s,hit.getLocation(),mc.player);
        return prompt==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.fuel_dispenser."+prompt.key()),prompt.anchor());
    }

    /** Intake pump, as IntakePumpBlock#use: with an empty main hand, turn the isolator on / off; drawn at its handle. */
    private static Target intakePump(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof com.antaurora.apofirstlight.block.IntakePumpBlock)) return null;
        var prompt=com.antaurora.apofirstlight.block.IntakePumpBlock.prompt(mc.level,hit.getBlockPos(),s,mc.player);
        return prompt==null?null:new Target(Component.translatable("hint.apocalypse_firstlight.intake_pump."+prompt.key()),prompt.anchor());
    }

    /**
     * Pump manhole cover / fuel fill cover, as FuelSumpCoverBlock#use: the manhole lid opens and shuts with the crowbar (an
     * empty hand is told it needs one), the fill cover's lid by hand. Drawn over the lid.
     */
    private static Target fuelSumpCover(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        var prompt=com.antaurora.apofirstlight.block.FuelSumpCoverBlock.prompt(hit.getBlockPos(),s,mc.player);
        return prompt==null?null:new Target(Component.translatable("hint.apocalypse_firstlight."+prompt.key()),prompt.anchor());
    }

    /**
     * Fuel containers and the hand pump (docs/models/fuel_containers_v1.md), as FuelCanBlock / HandFuelPumpBlock / the held
     * nozzle, can and pump: fill with the nozzle, pour with a can, set a pump on a drum or an open fill cover, crank it or
     * take it off, pick a can up. Drawn over the block.
     */
    private static Target fuelContainer(Minecraft mc,BlockHitResult hit) {
        var key=com.antaurora.apofirstlight.block.FuelCanBlock.hint(mc.level,hit.getBlockPos(),mc.player);
        if(key==null) return null;
        // at the middle of what is aimed at (2026-10-05: 0.6 over the cell's centre put the label far up by the Jade box, user)
        var shape=mc.level.getBlockState(hit.getBlockPos()).getShape(mc.level,hit.getBlockPos());
        Vec3 at=shape.isEmpty()?hit.getLocation():shape.bounds().getCenter().add(Vec3.atLowerCornerOf(hit.getBlockPos()));
        return new Target(Component.translatable("hint.apocalypse_firstlight.fuel_container."+key),at);
    }

    /** Any Mesh Shape interaction block: aimed region + the block's own state -> prompt, drawn at the region's anchor. */
    private static Target meshInteraction(Minecraft mc,BlockHitResult hit) {
        var s=mc.level.getBlockState(hit.getBlockPos());
        if(!(s.getBlock() instanceof AflMeshInteractionBlock block)) return null;
        var region=block.meshInteraction(s,hit.getBlockPos(),mc.player);
        if(region==null) return null;
        var key=block.interactionHintKey(mc.level,s,hit.getBlockPos(),region.region());
        return key==null?null:new Target(Component.translatable(key),region.anchor());
    }
}
