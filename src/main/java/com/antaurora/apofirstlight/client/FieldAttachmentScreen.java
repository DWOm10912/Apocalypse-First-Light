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

/** Transparent mouse owner. Rendering stays in the ordinary RenderHandEvent path. */
public final class FieldAttachmentScreen extends Screen implements AttachmentHudHost {
    private static long sequence;
    private long token;
    private final MaintenanceAttachmentHud hud=new MaintenanceAttachmentHud(this,this);
    private final GunInspectionController inspection=new GunInspectionController(true);
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
    @Override public void render(GuiGraphics g,int x,int y,float partial){if(FieldAttachmentViewState.isOpen())hud.render(g,x,y);}
    @Override public boolean mouseClicked(double x,double y,int button){
        inspection.cancelDrag();
        if(!FieldAttachmentViewState.isOpen())return true;
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
        if(hud.candidateListHit(x,y))hud.scroll(delta);
        else if(inspection.inViewport(x,y)&&!hud.blocksInspection(x,y))inspection.scroll(delta);
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256||NativeGunInput.FIELD_ATTACHMENT.matches(key,scan))onClose();
        else if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_R&&FieldAttachmentViewState.isOpen()&&!hud.inspectionModal())inspection.reset(System.nanoTime());
        return true;
    }
    @Override public void onClose(){FieldAttachmentViewState.exit("user closed");}
    @Override public void removed(){FieldAttachmentViewState.removed(this);}
}
