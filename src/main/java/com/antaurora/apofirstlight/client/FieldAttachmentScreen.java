package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.weapon.client.*;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent mouse owner. Rendering stays in the ordinary RenderHandEvent path. Top right: the key hint panel
 * (docs/ui/afl_overlay_ui_style_v1.md 8), its rows following what a click would do now.
 */
public final class FieldAttachmentScreen extends Screen implements AttachmentHudHost {
    private static long sequence;
    private long token;
    private final MaintenanceAttachmentHud hud=new MaintenanceAttachmentHud(this,this);
    private final GunInspectionController inspection=new GunInspectionController(true);
    private final AflKeyHintPanel keyHints=new AflKeyHintPanel();
    public GunInspectionController inspection(){return inspection;}
    public FieldAttachmentScreen(){super(Component.translatable("key.apocalypse_firstlight.field_attachment"));}
    @Override protected void init(){inspection.resize(width,height);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public ItemStack gun(){
        var p=Minecraft.getInstance().player;return p==null?ItemStack.EMPTY:p.getMainHandItem();
    }
    @Override public long revision(){return 0;}
    @Override public MaintenanceHotspots.Point project(NativeAttachment.Slot slot){return FieldAttachmentHotspots.project(slot);}
    public NativeAttachment.Slot selectedSlot(){return hud.selectedSlot();}
    public boolean pending(){return hud.pending();}
    @Override public void submit(ItemStack expected,long revision,NativeAttachment.Slot slot,int source,ItemStack stack){
        var p=minecraft.player;
        if(p==null||!FieldAttachmentViewState.isOpen()){hud.result(0);return;}
        token=++sequence;
        AflNetwork.requestFieldAttachment(new FieldAttachmentActionRequest(token,p.getInventory().selected,
                GeoItem.getId(expected),expected,slot,source,stack));
    }
    public void result(long resultToken,int phase){
        if(token!=resultToken||!FieldAttachmentViewState.isOpen())return;
        hud.result(phase);if(phase!=2)token=0;
    }
    public void cancelOperation(){
        inspection.cancelDrag();
        if(token!=0&&Minecraft.getInstance().getConnection()!=null)AflNetwork.cancelFieldAttachment(token);
        token=0;hud.removed();
    }
    @Override public void tick(){if(FieldAttachmentViewState.isOpen())hud.tick();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        boolean open=FieldAttachmentViewState.isOpen();
        if(open)hud.render(g,x,y);
        keyHints.render(g,width,keyRows(x,y),open);
    }
    /** What each input does right now (docs/ui/afl_overlay_ui_style_v1.md 8). */
    private List<AflKeyHintPanel.Row> keyRows(double x,double y){
        var exit=row(AflKeyHint.of(NativeGunInput.FIELD_ATTACHMENT),"exit");
        if(hud.pending())return List.of(exit);
        if(hud.selecting()){
            var rows=new ArrayList<AflKeyHintPanel.Row>();
            rows.add(row(AflKeyHint.MOUSE_LEFT,"install"));
            if(hud.paged())rows.add(row(AflKeyHint.MOUSE_SCROLL,"page"));
            rows.add(row(AflKeyHint.MOUSE_RIGHT,"back"));
            return rows;
        }
        if(hud.selectedSlot()!=null)return List.of(row(AflKeyHint.MOUSE_LEFT,"confirm"),row(AflKeyHint.MOUSE_RIGHT,"back"),exit);
        return List.of(row(AflKeyHint.MOUSE_LEFT,hud.hotspotAt(x,y)!=null?"select_slot":"rotate"),row(AflKeyHint.MOUSE_MIDDLE,"pan"),
                row(AflKeyHint.MOUSE_SCROLL,"zoom"),row(AflKeyHint.of(NativeGunInput.RESET_INSPECTION),"reset"),exit);
    }
    static AflKeyHintPanel.Row row(AflKeyHint key,String label){
        return new AflKeyHintPanel.Row(key,Component.translatable("gui.apocalypse_firstlight.key_hint."+label));
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        inspection.cancelDrag();
        if(!FieldAttachmentViewState.isOpen())return true;
        if(NativeGunInput.RESET_INSPECTION.matchesMouse(button)&&!hud.inspectionModal()){inspection.reset(System.nanoTime());return true;}
        if(hud.click(x,y,button))return true;
        if(inspection.inViewport(x,y)&&!hud.blocksInspection(x,y))inspection.beginDrag(x,y,button);
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        // Hit targets decide who owns mouse-down, not subsequent captured drag events.
        if(FieldAttachmentViewState.isOpen()&&!hud.inspectionModal())inspection.drag(x,y,button);
        else inspection.cancelDrag();
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){inspection.cancelDrag();return true;}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!FieldAttachmentViewState.isOpen())return true;
        AflKeyHint.scrolled();
        if(hud.candidateListHit(x,y))hud.scroll(delta);
        else if(inspection.inViewport(x,y)&&!hud.blocksInspection(x,y))inspection.scroll(delta);
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256||NativeGunInput.FIELD_ATTACHMENT.matches(key,scan))onClose();
        else if(NativeGunInput.RESET_INSPECTION.matches(key,scan)&&FieldAttachmentViewState.isOpen()&&!hud.inspectionModal())inspection.reset(System.nanoTime());
        return true;
    }
    @Override public void onClose(){FieldAttachmentViewState.exit("user closed");}
    @Override public void removed(){FieldAttachmentViewState.removed(this);}
}
