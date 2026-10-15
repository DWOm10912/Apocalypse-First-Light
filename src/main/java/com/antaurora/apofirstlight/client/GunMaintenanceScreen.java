package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.antaurora.apofirstlight.client.ui.AflItemStrip;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.weapon.client.NativeGunInput;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GridSource;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent mouse owner, not a container preview. All central geometry is the real world. The gun picker along the
 * bottom is an AflItemStrip (the hovered gun lifts): the guns the player carries, in the weapon positions and the hand,
 * then in the pockets, rig and backpack (docs/native_guns/attachments/gun_maintenance_attachment_interaction_v1.md);
 * the take button a rounded button, and the key hint panel sits top right (docs/ui/afl_overlay_ui_style_v1.md).
 */
public final class GunMaintenanceScreen extends Screen implements MenuAccess<GunMaintenanceMenu> {
    private final GunMaintenanceMenu menu;
    private int ticks;
    private int refusalTicks;
    private Component refusalReason;
    private final MaintenanceAttachmentHud attachments=new MaintenanceAttachmentHud(this);
    private final GunInspectionController inspection=new GunInspectionController(false);
    private final AflItemStrip hotbar=new AflItemStrip();
    /** A picker cell: where the gun is (a hotbar index or a GridSource), the gun, a short place label; ghost = on the bench. */
    private record Pick(int source,ItemStack stack,Component place,Component label,boolean ghost){}
    private final List<Pick> picks=new ArrayList<>();
    private final AflKeyHintPanel keyHints=new AflKeyHintPanel();
    public GunInspectionController inspection(){return inspection;}
    public void inspectionFrame(){inspection.frame(menu.synchronizedBench().getItem(0),System.nanoTime());}
    public void attachmentResult(int phase){attachments.result(phase);}
    public GunMaintenanceScreen(GunMaintenanceMenu menu,Inventory inventory,Component title){super(title);this.menu=menu;}
    @Override public GunMaintenanceMenu getMenu(){return menu;}
    @Override protected void init(){MaintenanceModeClientState.INSTANCE.enter(this,menu.bench.getBlockPos());inspection.resize(width,height);KeyMapping.releaseAll();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        var state=MaintenanceModeClientState.INSTANCE;
        if(state.exitFinished()){closeNow();return;}
        if(refusalTicks>0)refusalTicks--;
        if(!state.leaving())attachments.tick();
        // Chunk/BE packets may follow the opening packet. No fake world preview while waiting.
        if(++ticks>10 && (!MaintenanceModeClientState.INSTANCE.valid()||minecraft.player==null||minecraft.player.containerMenu!=menu))closeNow();
    }
    private void closeNow(){if(minecraft.player!=null)minecraft.player.closeContainer();else minecraft.setScreen(null);}
    @Override public void onClose(){
        if(MaintenanceModeClientState.INSTANCE.leaving())return;
        inspection.cancelDrag();
        attachments.removed();
        // Cancel any pending server operation immediately, while retaining this input shield for the visual exit.
        if(minecraft.getConnection()!=null)minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(menu.containerId));
        MaintenanceModeClientState.INSTANCE.beginExit();
    }
    private void clickSound(){minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),1f,.35f));}
    @Override public void removed(){inspection.cancelDrag();attachments.removed();MaintenanceModeClientState.INSTANCE.removed(this);KeyMapping.releaseAll();}
    /** The picker's left edge (it is centred; its width follows the number of guns). */
    public int hotbarX(){return (width-picks.size()*20)/2;}
    public int hotbarY(){return height-28;}
    public boolean gunHit(double x,double y){return MaintenanceGunPicking.hit(x,y,width,height);}
    /** Where the viewer's own gun on the bench came from (a hotbar index or a GridSource), or -1. */
    public int placeholderSlot(){return minecraft.player==null?-1:menu.originFor(minecraft.player.getUUID());}
    public boolean takeButtonVisible(){return minecraft.player!=null&&menu.canShowTakeButton(minecraft.player.getUUID());}
    public int takeButtonX(){return hotbarX()+picks.size()*20+4;}
    private int pickAt(double x,double y){
        return y>=hotbarY()&&y<hotbarY()+20&&x>=hotbarX()&&x<hotbarX()+picks.size()*20?(int)(x-hotbarX())/20:-1;
    }
    public Component returnTooltip(double x,double y){
        int cell=pickAt(x,y);
        if(cell>=0&&picks.get(cell).ghost())return Component.translatable("gui.apocalypse_firstlight.gun_maintenance.return_weapon");
        if(takeButtonVisible()&&inside(x,y,takeButtonX(),hotbarY()))return Component.translatable("gui.apocalypse_firstlight.gun_maintenance.take_weapon");
        return null;
    }
    private static Component place(String key){return Component.translatable("screen.apocalypse_firstlight.inventory."+key);}
    private static Component label(String key){return Component.translatable("screen.apocalypse_firstlight.maintenance_place."+key);}
    /**
     * The guns the player carries, in order: the weapon positions and the hand (Survival / Adventure; the whole hotbar
     * otherwise), then the pockets, rig and backpack by section, row, column. The gun on the bench stays as a ghost where
     * it came from.
     */
    private void collectPicks(){
        picks.clear();
        var player=minecraft.player;
        if(player==null)return;
        boolean limited=!PlayerStorageCapacity.unrestricted(player);
        int origin=placeholderSlot();
        var onBench=menu.synchronizedBench().getItem(0);
        for(int i=0;i<9;i++){
            boolean ghost=i==origin;
            if(limited&&i>=HotbarRoles.USED&&!ghost)continue;
            var stack=ghost?onBench:player.getInventory().getItem(i);
            if(!ghost&&!GunMaintenanceBenchBlockEntity.accepts(stack))continue;
            Component where,tag;
            if(!limited||i>=HotbarRoles.USED){where=Component.literal(String.valueOf(i+1));tag=where;}
            else if(i==HotbarRoles.HAND_SLOT){where=place("hands");tag=label("hands");}
            else{where=place(i==HotbarRoles.PRIMARY_SLOT?"primary":i==HotbarRoles.PISTOL_SLOT?"pistol":"melee");tag=Component.literal(String.valueOf(i+1));}
            picks.add(new Pick(i,stack,where,tag,ghost));
        }
        var originCell=GridSource.cell(origin);
        var view=PlayerGrids.view(player);
        for(var target:PlayerGrids.Target.values()){
            var storage=view.get(target);
            if(storage==null)continue;
            String key=target.name().toLowerCase(java.util.Locale.ROOT);
            var here=new ArrayList<Pick>();var order=new ArrayList<Integer>();
            for(var e:storage.entries()){
                if(!GunMaintenanceBenchBlockEntity.accepts(e.stack()))continue;
                here.add(new Pick(GridSource.encode(target,e.section(),e.x(),e.y()),e.stack(),place(key),label(key),false));
                order.add(e.section()<<10|e.y()<<5|e.x());
            }
            if(originCell!=null&&originCell.target()==target){
                here.add(new Pick(origin,onBench,place(key),label(key),true));
                order.add(originCell.section()<<10|originCell.y()<<5|originCell.x());
            }
            var index=new ArrayList<Integer>();
            for(int i=0;i<here.size();i++)index.add(i);
            index.sort(Comparator.comparingInt(order::get));
            for(int i:index)picks.add(here.get(i));
        }
        while(picks.size()>16)picks.remove(picks.size()-1);
    }
    private static boolean inside(double x,double y,int left,int top){return x>=left&&x<left+20&&y>=top&&y<top+20;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        var state=MaintenanceModeClientState.INSTANCE;state.hoveredGun=false;state.hoveredHotbarSlot=-1;
        keyHints.render(g,width,keyRows(mouseX,mouseY),state.ready()&&!state.leaving());
        if(!state.ready())return;
        int x0=hotbarX(),y=hotbarY();
        var stored=menu.synchronizedBench().getItem(0);
        if(!stored.isEmpty()){
            Component name=stored.getHoverName();int maxWidth=Math.min(220,width-16);
            if(font.width(name)>maxWidth)name=Component.literal(font.plainSubstrByWidth(name.getString(),maxWidth-font.width("…"))+"…");
            var anchor=MaintenanceHotspots.projectWorld(MaintenanceCameraController.world(
                    state.bench().getBlockPos(),state.facing(),new net.minecraft.world.phys.Vec3(1.0375,16.5/16,.84)),width,height);
            if(anchor!=null){
                int labelWidth=font.width(name);
                int center=net.minecraft.util.Mth.clamp((int)Math.round(anchor.x()),8+labelWidth/2,width-8-(labelWidth+1)/2);
                int top=net.minecraft.util.Mth.clamp((int)Math.round(anchor.y())-font.lineHeight/2,4,height-font.lineHeight-4);
                g.drawCenteredString(font,name,center,top,0xffcccccc);
            }
        }
        if(attachments.selecting()){attachments.render(g,mouseX,mouseY);return;}
        collectPicks();
        x0=hotbarX();
        int hovered=pickAt(mouseX,mouseY);
        state.hoveredHotbarSlot=hovered;
        hotbar.render(g,x0,y,picks.size(),hovered,hovered,false,1,(graphics,i,x,cy)->{
            var pick=picks.get(i);
            if(pick.ghost()){
                // Drawing only: never create a slot, assign an Inventory stack, or expose it to item use.
                graphics.renderItem(pick.stack(),x,cy);graphics.flush();
                // Item render types do not consistently respect global alpha: a 58% veil of the tile backing over the
                // icon leaves a reliable 42% ghost for every gun.
                graphics.pose().pushPose();graphics.pose().translate(0,0,300);
                AflUiShapes.fill(graphics,x-1,cy-1,18,18,AflUiStyle.RADIUS_TILE,AflUiStyle.BACK,0.58F);
                graphics.pose().popPose();
            }else{graphics.renderItem(pick.stack(),x,cy);graphics.renderItemDecorations(font,pick.stack(),x,cy);}
            // where it is, small in the top-left corner (1 / 2 weapon positions, hand, pockets, rig, backpack)
            graphics.pose().pushPose();graphics.pose().translate(x-1,cy-1,310);graphics.pose().scale(0.75F,0.75F,1);
            graphics.drawString(font,pick.label(),1,1,pick.ghost()?0x99D8D8D8:0xFFD8D8D8,true);
            graphics.pose().popPose();
        });
        if(takeButtonVisible()){
            int x=takeButtonX();boolean hover=inside(mouseX,mouseY,x,y);
            AflUiDraw.button(g,x+1,y+1,18,18,hover,false,true,1);
            AflUiDraw.centred(g,font,Component.literal("<"),x+10,y+6,AflUiStyle.TEXT,1);
        }
        var tooltip=returnTooltip(mouseX,mouseY);
        if(tooltip!=null)g.renderTooltip(font,tooltip,mouseX,mouseY);
        else if(hovered>=0)g.renderComponentTooltip(font,List.of(picks.get(hovered).stack().getHoverName(),
                picks.get(hovered).place().copy().withStyle(net.minecraft.ChatFormatting.GRAY)),mouseX,mouseY);
        else if(refusalTicks>0&&refusalReason!=null)AflUiDraw.centred(g,font,refusalReason,width/2f,y-14,0xFFDDDD,1);
        else if(menu.bench.isEmpty())AflUiDraw.centred(g,font,Component.translatable(picks.isEmpty()
                ?"screen.apocalypse_firstlight.maintenance_no_guns":"screen.apocalypse_firstlight.maintenance_select"),width/2f,y-14,AflUiStyle.TEXT,1);
        attachments.render(g,mouseX,mouseY);
    }
    /** What each input does right now (docs/ui/afl_overlay_ui_style_v1.md 8); the bench view zooms but does not turn. */
    private List<AflKeyHintPanel.Row> keyRows(double x,double y){
        var exit=FieldAttachmentScreen.row(AflKeyHint.of(minecraft.options.keyInventory),"exit");
        if(attachments.pending())return List.of(exit);
        if(attachments.selecting())return attachments.paged()
                ?List.of(FieldAttachmentScreen.row(AflKeyHint.MOUSE_LEFT,"install"),FieldAttachmentScreen.row(AflKeyHint.MOUSE_SCROLL,"page"),FieldAttachmentScreen.row(AflKeyHint.MOUSE_RIGHT,"back"))
                :List.of(FieldAttachmentScreen.row(AflKeyHint.MOUSE_LEFT,"install"),FieldAttachmentScreen.row(AflKeyHint.MOUSE_RIGHT,"back"));
        if(attachments.selectedSlot()!=null)return List.of(FieldAttachmentScreen.row(AflKeyHint.MOUSE_LEFT,"confirm"),FieldAttachmentScreen.row(AflKeyHint.MOUSE_RIGHT,"back"),exit);
        boolean onHotbar=pickAt(x,y)>=0;
        return List.of(FieldAttachmentScreen.row(AflKeyHint.MOUSE_LEFT,onHotbar?"place_gun":"select_slot"),FieldAttachmentScreen.row(AflKeyHint.MOUSE_SCROLL,"zoom"),
                FieldAttachmentScreen.row(AflKeyHint.of(NativeGunInput.RESET_INSPECTION),"reset"),exit);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        inspection.cancelDrag();
        if(!MaintenanceModeClientState.INSTANCE.ready())return true;
        if(NativeGunInput.RESET_INSPECTION.matchesMouse(button)&&!attachments.inspectionModal()){inspection.reset(System.nanoTime());return true;}
        if(attachments.click(x,y,button))return true;
        if(inspection.inViewport(x,y)&&!attachments.blocksInspection(x,y)){
            inspection.beginDrag(x,y,button);return true;
        }
        if(button!=0)return true;
        int cell=pickAt(x,y);
        if(cell>=0){
            var pick=picks.get(cell);
            if(pick.ghost()){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,GunMaintenanceMenu.RETURN_GUN);}
            else if(menu.bench.isEmpty()){
                clickSound();
                if(!com.antaurora.apofirstlight.weapon.AttachmentModificationPolicy.allowed(pick.stack())){
                    refusalReason=((com.antaurora.apofirstlight.weapon.NativeGunItem)pick.stack().getItem()).inspectionRefusalReason();
                    refusalTicks=60;
                }
                else GridPackets.sendBenchInsert(menu.containerId,pick.source(),pick.stack().copy());
            }
        }else if(takeButtonVisible()&&inside(x,y,takeButtonX(),hotbarY())){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,GunMaintenanceMenu.TAKE_GUN);}
        // Single clicks on empty world-space areas still do not take or modify the gun.
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==256||minecraft.options.keyInventory.matches(key,scan)){inspection.cancelDrag();if(!attachments.back())onClose();}
        else if(NativeGunInput.RESET_INSPECTION.matches(key,scan)&&MaintenanceModeClientState.INSTANCE.ready()&&!attachments.inspectionModal())inspection.reset(System.nanoTime());
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(MaintenanceModeClientState.INSTANCE.ready()&&!attachments.blocksInspection(x,y))inspection.drag(x,y,button);
        else inspection.cancelDrag();
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){inspection.cancelDrag();return true;}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!MaintenanceModeClientState.INSTANCE.ready())return true;
        AflKeyHint.scrolled();
        if(attachments.candidateListHit(x,y))attachments.scroll(delta);
        else if(inspection.inViewport(x,y)&&!attachments.blocksInspection(x,y))inspection.scroll(delta);
        return true;
    }
}
