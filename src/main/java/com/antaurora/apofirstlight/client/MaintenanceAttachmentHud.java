package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.antaurora.apofirstlight.client.ui.AflItemStrip;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.ui.AflUiTween;

/**
 * Small overlay state machine; no container slots or local attachment mutations. Drawn in the overlay style
 * (docs/ui/afl_overlay_ui_style_v1.md): hover labels (AttachmentHintStyle), the slot's context panel (fades in and rises
 * 3 GUI px over 120 ms), rounded buttons, and the candidate row as an AflItemStrip (cells stagger in, the hovered
 * candidate lifts, the amber pill follows it).
 */
public final class MaintenanceAttachmentHud {
    private final net.minecraft.client.gui.screens.Screen screen;
    private final AttachmentHudHost host;
    private final AttachmentCandidatePage page=new AttachmentCandidatePage();
    private NativeAttachment.Slot locked,hovered;
    private ItemStack expected=ItemStack.EMPTY;
    private long revision,last=System.nanoTime(),noticeUntil;
    private float fade;
    private final AflItemStrip candidates=new AflItemStrip();
    private NativeAttachment.Slot panelFor;
    private long panelAt;
    private boolean wasSelecting;
    private boolean selection,pending;
    private int hx,hy;
    private int pressed=-1;
    private long pressedUntil;
    private Runnable afterPress;
    private net.minecraft.client.resources.sounds.SimpleSoundInstance operationSound;
    public MaintenanceAttachmentHud(GunMaintenanceScreen screen){this(screen,new AttachmentHudHost(){
        public ItemStack gun(){return screen.getMenu().synchronizedBench().getItem(0);}
        public long revision(){return screen.getMenu().synchronizedBench().attachmentRevision();}
        public MaintenanceHotspots.Point project(NativeAttachment.Slot slot){return MaintenanceHotspots.project(slot,screen.width,screen.height);}
        public void submit(ItemStack expected,long revision,NativeAttachment.Slot slot,int source,ItemStack stack){
            var menu=screen.getMenu();
            AflNetwork.requestMaintenance(new MaintenanceActionRequest(menu.containerId,menu.bench.getBlockPos(),revision,expected,slot,source,stack));
        }
        public void action(MaintenanceActionState action){MaintenanceModeClientState.INSTANCE.action=action;}
    });}
    public MaintenanceAttachmentHud(net.minecraft.client.gui.screens.Screen screen,AttachmentHudHost host){this.screen=screen;this.host=host;}
    public NativeAttachment.Slot selectedSlot(){return locked;}
    public boolean pending(){return pending;}
    private int hotbarX(){return (screen.width-182)/2+1;}
    private int hotbarY(){return screen.height-28;}
    private Minecraft mc(){return Minecraft.getInstance();}
    private ItemStack gun(){return host.gun();}
    private Component text(String key){return Component.translatable("gui.apocalypse_firstlight.gun_maintenance."+key);}
    private Component title(NativeAttachment.Slot slot){return text(switch(slot){case SIGHT->"sight";case MUZZLE->"muzzle";case MAGAZINE->"magazine";});}
    private Component installed(NativeAttachment.Slot slot){var item=NativeAttachments.stored(gun(),slot);return item.isEmpty()?text("none"):item.getHoverName();}
    public boolean selecting(){return selection;}
    /** The attachment slot hotspot under the mouse, or null (key hints). */
    public NativeAttachment.Slot hotspotAt(double x,double y){return hit(x,y);}
    /** Candidates on more than one page (key hints show paging). */
    public boolean paged(){return page.candidates.size()>AttachmentCandidatePage.PAGE_SIZE;}
    /** Maintenance viewport input must yield to modal candidates, transactions and context panels. */
    public boolean blocksInspection(double x,double y){
        return inspectionModal()||(locked!=null&&inside(x,y,hx,hy,140,65))||hit(x,y)!=null;
    }
    public boolean inspectionModal(){return selection||pending||afterPress!=null;}
    public boolean candidateListHit(double x,double y){return selection&&inside(x,y,hotbarX(),hotbarY()-28,180,48);}
    public static int contextX(double anchorX,int width){return Math.max(4,Math.min(width-144,(int)anchorX+(anchorX<width/2d?-164:24)));}
    public void tick(){
        if(afterPress!=null&&System.nanoTime()>=pressedUntil){var action=afterPress;afterPress=null;action.run();}
        if(selection&&mc().player!=null&&locked!=null)page.refresh(mc().player.getInventory(),gun(),locked);
        if(gun().isEmpty()){locked=null;selection=false;}
    }
    private NativeAttachment.Slot hit(double x,double y){
        NativeAttachment.Slot best=null;double distance=Double.MAX_VALUE;
        for(var slot:NativeAttachment.Slot.values()){
            var p=host.project(slot);
            if(p!=null&&p.hit(x,y)){double d=Math.hypot(x-p.x(),y-p.y());if(d<distance){distance=d;best=slot;}}
        }return best;
    }
    public void render(GuiGraphics g,int mx,int my){
        long now=System.nanoTime();float step=Math.min(.2f,(now-last)/1_000_000_000f)/.15f;last=now;
        var current=hit(mx,my);
        if(current!=null&&current!=hovered){hovered=current;fade=0;}
        fade=Math.max(0,Math.min(1,fade+(current!=null?step:-step)));
        if(locked==null&&hovered!=null&&fade>.03){
            var point=host.project(hovered);
            if(point!=null){var label=NativeAttachments.stored(gun(),hovered).isEmpty()?Component.literal("+ ").append(title(hovered)):installed(hovered);
                AttachmentHintStyle.draw(g,label,(int)point.x(),(int)point.y(),screen.width,fade);
            }
        }
        if(locked!=null&&!selection){
            var point=host.project(locked);
            if(locked!=panelFor){panelFor=locked;panelAt=now;}
            if(point!=null){hx=contextX(point.x(),screen.width);hy=Math.max(4,Math.min(screen.height-106,(int)point.y()-24));
                // the panel fades in and rises 3 GUI px; clicks use its resting place
                float shown=AflUiTween.Ease.OUT.apply(AflUiTween.clamp01((now-panelAt)/120_000_000f)),dy=AflUiTween.snap((1-shown)*3);
                g.pose().pushPose();g.pose().translate(0,dy,0);
                AflUiDraw.panel(g,hx,hy,140,65,shown);
                AflUiDraw.text(g,mc().font,title(locked),hx+7,hy+6,AflUiStyle.TEXT,shown);
                AflUiDraw.text(g,mc().font,installed(locked),hx+7,hy+20,AflUiStyle.TEXT_DIM,shown);
                drawButton(g,0,hx+6,hy+38,62,20,text(NativeAttachments.stored(gun(),locked).isEmpty()?"modify":"replace"),mx,my,shown);
                if(!NativeAttachments.stored(gun(),locked).isEmpty())drawButton(g,1,hx+74,hy+38,60,20,text("remove"),mx,my,shown);
                g.pose().popPose();
            }
        }else panelFor=null;
        if(selection&&!wasSelecting)candidates.appear();
        wasSelecting=selection;
        if(selection){
            int y=hotbarY(),hoveredCell=inside(mx,my,hotbarX(),y,180,20)?(mx-hotbarX())/20:-1;
            if(hoveredCell>=0&&page.at(hoveredCell)==null)hoveredCell=-1;
            candidates.render(g,hotbarX(),y,9,hoveredCell,hoveredCell,false,pending?0.6F:1,(graphics,i,x,cy)->{
                var entry=page.at(i);
                if(entry==null)return;
                graphics.renderItem(entry.source(),x,cy);
                graphics.renderItemDecorations(mc().font,entry.source(),x,cy,String.valueOf(entry.totalCount()));
            });
            var hoveredEntry=hoveredCell<0?null:page.at(hoveredCell);
            if(hoveredEntry!=null)g.renderTooltip(mc().font,hoveredEntry.source().getHoverName(),mx,my);
            AflUiDraw.centred(g,mc().font,page.candidates.isEmpty()?text("no_compatible_attachment"):title(locked),screen.width/2f,y-14,AflUiStyle.TEXT,1);
            if(paged())AflUiDraw.centred(g,mc().font,Component.translatable("gui.apocalypse_firstlight.gun_maintenance.page",page.page+1,(page.candidates.size()+8)/9),screen.width/2f,y-27,AflUiStyle.TEXT_DIM,1);
        }
        if(System.currentTimeMillis()<noticeUntil)AflUiDraw.centred(g,mc().font,text("state_changed"),screen.width/2f,hotbarY()-40,AflUiStyle.TEXT,1);
        if(pending)AflUiDraw.centred(g,mc().font,text("processing"),screen.width/2f,hotbarY()-40,AflUiStyle.TEXT,1);
    }
    private void drawButton(GuiGraphics g,int id,int x,int y,int w,int h,Component label,int mx,int my,float alpha){
        boolean down=pressed==id&&System.nanoTime()<pressedUntil;
        float sink=AflUiDraw.button(g,x,y,w,h,inside(mx,my,x,y,w,h),down,!pending,alpha);
        AflUiDraw.centred(g,mc().font,label,x+w/2f,y+6+sink,pending?AflUiStyle.TEXT_DISABLED:AflUiStyle.TEXT,alpha);
    }
    private void clickSound(){mc().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),1f,.35f));}
    private void press(int id,Runnable action){
        if(pending||afterPress!=null)return;
        clickSound();pressed=id;pressedUntil=System.nanoTime()+80_000_000L;afterPress=action;
    }
    private static boolean inside(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    public boolean back(){
        afterPress=null;
        if(pending){clickSound();return false;} // Esc exits the menu, so the server cancels the operation.
        if(selection){clickSound();selection=false;locked=null;return true;}
        if(locked!=null){clickSound();locked=null;return true;}return false;
    }
    public boolean click(double x,double y,int button){
        if(pending||afterPress!=null)return true;
        if(button==1)return back();
        if(button!=0)return false;
        if(selection){
            if(inside(x,y,hotbarX(),hotbarY(),180,20)){
                int cell=(int)(x-hotbarX())/20;var e=page.at(cell);if(e!=null)press(2+cell,()->submit(e.sourceSlot(),e.source()));
            }
            return true;
        }
        if(locked!=null&&inside(x,y,hx,hy,140,65)){
            if(inside(x,y,hx+6,hy+38,62,20))press(0,()->{selection=true;page.page=0;tick();});
            else if(inside(x,y,hx+74,hy+38,60,20)&&!NativeAttachments.stored(gun(),locked).isEmpty())press(1,()->submit(-1,ItemStack.EMPTY));
            return true;
        }
        var target=hit(x,y);
        if(target!=null){locked=target;expected=gun().copy();revision=host.revision();return true;}
        return false;
    }
    private void submit(int source,ItemStack stack){
        if(pending||locked==null)return;pending=true;
        host.action(locked==NativeAttachment.Slot.MAGAZINE
                ?(source<0?MaintenanceActionState.REMOVING_MAGAZINE:MaintenanceActionState.INSTALLING_MAGAZINE)
                :locked==NativeAttachment.Slot.SIGHT
                ?(source<0?MaintenanceActionState.REMOVING_SIGHT:MaintenanceActionState.INSTALLING_SIGHT)
                :(source<0?MaintenanceActionState.REMOVING_MUZZLE:MaintenanceActionState.INSTALLING_MUZZLE));
        host.submit(expected.copy(),revision,locked,source,stack.copy());
    }
    public void result(int phase){
        if(phase==2){
            if(!pending||operationSound!=null)return;
            operationSound=net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.antaurora.apofirstlight.registry.AflSounds.ATTACHMENT_OPERATION.get(),1f,.8f);
            mc().getSoundManager().play(operationSound);return;
        }
        boolean success=phase==1;
        if(operationSound!=null&&!success)mc().getSoundManager().stop(operationSound);
        operationSound=null;
        pending=false;
        host.action(MaintenanceActionState.IDLE);
        if(success){selection=false;locked=null;}else{
            noticeUntil=System.currentTimeMillis()+1800;expected=gun().copy();revision=host.revision();tick();
        }
    }
    public void removed(){afterPress=null;if(operationSound!=null)mc().getSoundManager().stop(operationSound);operationSound=null;pending=false;}
    public void scroll(double delta){if(selection&&!pending&&afterPress==null){page.page+=delta<0?1:-1;tick();}}
}
