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
import java.util.List;

/**
 * Transparent mouse owner, not a container preview. All central geometry is the real world. The player's hotbar row is
 * an AflItemStrip (the hovered gun lifts), the take button a rounded button, and the key hint panel sits top right
 * (docs/ui/afl_overlay_ui_style_v1.md).
 */
public final class GunMaintenanceScreen extends Screen implements MenuAccess<GunMaintenanceMenu> {
    private final GunMaintenanceMenu menu;
    private int ticks;
    private int refusalTicks;
    private Component refusalReason;
    private final MaintenanceAttachmentHud attachments=new MaintenanceAttachmentHud(this);
    private final GunInspectionController inspection=new GunInspectionController(false);
    private final AflItemStrip hotbar=new AflItemStrip();
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
    public int hotbarX(){return (width-182)/2+1;}
    public int hotbarY(){return height-28;}
    public boolean gunHit(double x,double y){return MaintenanceGunPicking.hit(x,y,width,height);}
    public int placeholderSlot(){return minecraft.player==null?-1:menu.originSlotFor(minecraft.player.getUUID());}
    public boolean takeButtonVisible(){return minecraft.player!=null&&menu.canShowTakeButton(minecraft.player.getUUID());}
    public int takeButtonX(){return hotbarX()+184;}
    public Component returnTooltip(double x,double y){
        int slot=placeholderSlot();
        if(slot>=0&&inside(x,y,hotbarX()+slot*20,hotbarY()))return Component.translatable("gui.apocalypse_firstlight.gun_maintenance.return_weapon");
        if(takeButtonVisible()&&inside(x,y,takeButtonX(),hotbarY()))return Component.translatable("gui.apocalypse_firstlight.gun_maintenance.take_weapon");
        return null;
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
        int hovered=mouseY>=y&&mouseY<y+20&&mouseX>=x0&&mouseX<x0+180?(mouseX-x0)/20:-1;
        state.hoveredHotbarSlot=hovered;
        hotbar.render(g,x0,y,9,hovered,hovered,false,1,(graphics,i,x,cy)->{
            var stack=menu.slots.get(i).getItem();
            if(i==placeholderSlot()){
                // Drawing only: never create a slot, assign an Inventory stack, or expose it to item use.
                graphics.renderItem(menu.synchronizedBench().getItem(0),x,cy);graphics.flush();
                // Item render types do not consistently respect global alpha: a 58% veil of the tile backing over the
                // icon leaves a reliable 42% ghost for every gun.
                graphics.pose().pushPose();graphics.pose().translate(0,0,300);
                AflUiShapes.fill(graphics,x-1,cy-1,18,18,AflUiStyle.RADIUS_TILE,AflUiStyle.BACK,0.58F);
                graphics.pose().popPose();
            }else{graphics.renderItem(stack,x,cy);graphics.renderItemDecorations(font,stack,x,cy);}
        });
        if(takeButtonVisible()){
            int x=takeButtonX();boolean hover=inside(mouseX,mouseY,x,y);
            AflUiDraw.button(g,x+1,y+1,18,18,hover,false,true,1);
            AflUiDraw.centred(g,font,Component.literal("<"),x+10,y+6,AflUiStyle.TEXT,1);
        }
        var tooltip=returnTooltip(mouseX,mouseY);
        if(tooltip!=null)g.renderTooltip(font,tooltip,mouseX,mouseY);
        else if(refusalTicks>0&&refusalReason!=null)AflUiDraw.centred(g,font,refusalReason,width/2f,y-14,0xFFDDDD,1);
        else if(menu.bench.isEmpty())AflUiDraw.centred(g,font,Component.translatable("screen.apocalypse_firstlight.maintenance_select"),width/2f,y-14,AflUiStyle.TEXT,1);
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
        boolean onHotbar=y>=hotbarY()&&y<hotbarY()+20&&x>=hotbarX()&&x<hotbarX()+180;
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
        if(y>=hotbarY()&&y<hotbarY()+20&&x>=hotbarX()&&x<hotbarX()+180){
            int slot=(int)(x-hotbarX())/20;
            if(slot==placeholderSlot()){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,GunMaintenanceMenu.RETURN_GUN);}
            else if(menu.bench.isEmpty()&&GunMaintenanceBenchBlockEntity.accepts(menu.slots.get(slot).getItem())){
                clickSound();
                if(!com.antaurora.apofirstlight.weapon.AttachmentModificationPolicy.allowed(menu.slots.get(slot).getItem())){
                    refusalReason=((com.antaurora.apofirstlight.weapon.NativeGunItem)menu.slots.get(slot).getItem().getItem()).inspectionRefusalReason();
                    refusalTicks=60;
                }
                else minecraft.gameMode.handleInventoryButtonClick(menu.containerId,slot);
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
