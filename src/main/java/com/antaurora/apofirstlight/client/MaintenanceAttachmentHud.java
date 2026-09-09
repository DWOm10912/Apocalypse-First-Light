package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.*;
import com.antaurora.apofirstlight.network.AflNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Small overlay state machine; no container slots or local attachment mutations. */
public final class MaintenanceAttachmentHud {
    private final GunMaintenanceScreen screen;
    private final AttachmentCandidatePage page=new AttachmentCandidatePage();
    private NativeAttachment.Slot locked,hovered;
    private ItemStack expected=ItemStack.EMPTY;
    private long revision,last=System.nanoTime(),noticeUntil;
    private float fade,pageFade;
    private boolean selection,pending;
    private int hx,hy;
    private int pressed=-1;
    private long pressedUntil;
    private Runnable afterPress;
    private net.minecraft.client.resources.sounds.SimpleSoundInstance operationSound;
    public MaintenanceAttachmentHud(GunMaintenanceScreen screen){this.screen=screen;}
    private Minecraft mc(){return Minecraft.getInstance();}
    private ItemStack gun(){return screen.getMenu().synchronizedBench().getItem(0);}
    private Component text(String key){return Component.translatable("gui.apocalypse_firstlight.gun_maintenance."+key);}
    private Component title(NativeAttachment.Slot slot){return text(switch(slot){case SIGHT->"sight";case MUZZLE->"muzzle";case MAGAZINE->"magazine";});}
    private Component installed(NativeAttachment.Slot slot){var item=NativeAttachments.stored(gun(),slot);return item.isEmpty()?text("none"):item.getHoverName();}
    public boolean selecting(){return selection;}
    public static int contextX(double anchorX,int width){return Math.max(4,Math.min(width-144,(int)anchorX+(anchorX<width/2d?-164:24)));}
    public void tick(){
        if(afterPress!=null&&System.nanoTime()>=pressedUntil){var action=afterPress;afterPress=null;action.run();}
        if(selection&&mc().player!=null&&locked!=null)page.refresh(mc().player.getInventory(),gun(),locked);
        if(gun().isEmpty()){locked=null;selection=false;}
    }
    private NativeAttachment.Slot hit(double x,double y){
        NativeAttachment.Slot best=null;double distance=Double.MAX_VALUE;
        for(var slot:NativeAttachment.Slot.values()){
            var p=MaintenanceHotspots.project(slot,screen.width,screen.height);
            if(p!=null&&p.hit(x,y)){double d=Math.hypot(x-p.x(),y-p.y());if(d<distance){distance=d;best=slot;}}
        }return best;
    }
    public void render(GuiGraphics g,int mx,int my){
        long now=System.nanoTime();float step=Math.min(.2f,(now-last)/1_000_000_000f)/.15f;last=now;
        var current=hit(mx,my);
        if(current!=null&&current!=hovered){hovered=current;fade=0;}
        fade=Math.max(0,Math.min(1,fade+(current!=null?step:-step)));
        pageFade=Math.max(0,Math.min(1,pageFade+(selection?step:-step)));
        if(locked==null&&hovered!=null&&fade>.03){
            var point=MaintenanceHotspots.project(hovered,screen.width,screen.height);
            if(point!=null){var label=NativeAttachments.stored(gun(),hovered).isEmpty()?Component.literal("+ ").append(title(hovered)):installed(hovered);
                int x=Math.max(4,Math.min(screen.width-mc().font.width(label)-8,(int)point.x()+14));int y=Math.max(4,(int)point.y()-16);
                g.fill(x-3,y-3,x+mc().font.width(label)+3,y+12,((int)(fade*190)<<24)|0x272b2e);
                g.drawString(mc().font,label,x,y,((int)(fade*255)<<24)|0xdddddd,false);
            }
        }
        if(locked!=null&&!selection){
            var point=MaintenanceHotspots.project(locked,screen.width,screen.height);
            if(point!=null){hx=contextX(point.x(),screen.width);hy=Math.max(4,Math.min(screen.height-106,(int)point.y()-24));
                g.fill(hx,hy,hx+140,hy+65,0xdd65686b);g.fill(hx+1,hy+1,hx+139,hy+64,0xeb272b2e);
                g.drawString(mc().font,title(locked),hx+7,hy+6,0xffdddddd,false);
                g.drawString(mc().font,installed(locked),hx+7,hy+20,0xffb8babc,false);
                drawButton(g,0,hx+6,hy+38,62,20,text(NativeAttachments.stored(gun(),locked).isEmpty()?"modify":"replace"),mx,my);
                if(!NativeAttachments.stored(gun(),locked).isEmpty())drawButton(g,1,hx+74,hy+38,60,20,text("remove"),mx,my);
            }
        }
        if(selection){
            int y=screen.hotbarY();
            for(int i=0;i<9;i++){
                int x=screen.hotbarX()+i*20;var entry=page.at(i);
                buttonPlate(g,2+i,x,y,20,20,entry!=null&&inside(mx,my,x,y,20,20),entry!=null&&!pending);
                if(entry!=null){g.renderItem(entry.source(),x+2,y+2);g.renderItemDecorations(mc().font,entry.source(),x+2,y+2,String.valueOf(entry.totalCount()));
                    if(inside(mx,my,x,y,20,20))g.renderTooltip(mc().font,entry.source().getHoverName(),mx,my);
                }
            }
            g.drawCenteredString(mc().font,page.candidates.isEmpty()?text("no_compatible_attachment"):title(locked),screen.width/2,y-14,0xffcccccc);
            if(page.candidates.size()>9)g.drawCenteredString(mc().font,Component.translatable("gui.apocalypse_firstlight.gun_maintenance.page",page.page+1,(page.candidates.size()+8)/9),screen.width/2,y-27,0xffcccccc);
            if(pageFade<1){g.pose().pushPose();g.pose().translate(0,0,400);g.fill(screen.hotbarX(),y,screen.hotbarX()+180,y+20,((int)((1-pageFade)*200)<<24)|0x202426);g.pose().popPose();}
        }
        if(System.currentTimeMillis()<noticeUntil)g.drawCenteredString(mc().font,text("state_changed"),screen.width/2,screen.hotbarY()-40,0xffcccccc);
        if(pending)g.drawCenteredString(mc().font,text("processing"),screen.width/2,screen.hotbarY()-40,0xffdddddd);
    }
    private void buttonPlate(GuiGraphics g,int id,int x,int y,int w,int h,boolean hover,boolean enabled){
        boolean down=pressed==id&&System.nanoTime()<pressedUntil;
        int border=down?0xff686d70:!enabled?0xff505456:hover?0xffc1c6c9:0xff858b8f;
        int top=down?0xff292e31:!enabled?0xff303437:hover?0xff5b6267:0xff42494e;
        g.fill(x,y,x+w,y+h,border);g.fillGradient(x+1,y+1,x+w-1,y+h-1,top,down?0xff25292c:0xff30363a);
        if(hover&&enabled&&!down)g.fill(x+2,y+1,x+w-2,y+2,0xff91999e);
    }
    private void drawButton(GuiGraphics g,int id,int x,int y,int w,int h,Component label,int mx,int my){
        buttonPlate(g,id,x,y,w,h,inside(mx,my,x,y,w,h),!pending);
        boolean down=pressed==id&&System.nanoTime()<pressedUntil;
        g.drawCenteredString(mc().font,label,x+w/2,y+6+(down?1:0),pending?0xff969a9d:0xfff0f1f2);
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
            if(inside(x,y,screen.hotbarX(),screen.hotbarY(),180,20)){
                int cell=(int)(x-screen.hotbarX())/20;var e=page.at(cell);if(e!=null)press(2+cell,()->submit(e.sourceSlot(),e.source()));
            }
            return true;
        }
        if(locked!=null&&inside(x,y,hx,hy,140,65)){
            if(inside(x,y,hx+6,hy+38,62,20))press(0,()->{selection=true;page.page=0;tick();});
            else if(inside(x,y,hx+74,hy+38,60,20)&&!NativeAttachments.stored(gun(),locked).isEmpty())press(1,()->submit(-1,ItemStack.EMPTY));
            return true;
        }
        var target=hit(x,y);
        if(target!=null){locked=target;expected=gun().copy();revision=screen.getMenu().synchronizedBench().attachmentRevision();return true;}
        return false;
    }
    private void submit(int source,ItemStack stack){
        if(pending||locked==null)return;pending=true;
        MaintenanceModeClientState.INSTANCE.action=locked==NativeAttachment.Slot.MAGAZINE
                ?(source<0?MaintenanceActionState.REMOVING_MAGAZINE:MaintenanceActionState.INSTALLING_MAGAZINE)
                :locked==NativeAttachment.Slot.SIGHT
                ?(source<0?MaintenanceActionState.REMOVING_SIGHT:MaintenanceActionState.INSTALLING_SIGHT)
                :(source<0?MaintenanceActionState.REMOVING_MUZZLE:MaintenanceActionState.INSTALLING_MUZZLE);
        var menu=screen.getMenu();
        AflNetwork.requestMaintenance(new MaintenanceActionRequest(menu.containerId,menu.bench.getBlockPos(),revision,expected.copy(),locked,source,stack.copy()));
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
        MaintenanceModeClientState.INSTANCE.action=MaintenanceActionState.IDLE;
        if(success){selection=false;locked=null;}else{
            noticeUntil=System.currentTimeMillis()+1800;expected=gun().copy();revision=screen.getMenu().synchronizedBench().attachmentRevision();tick();
        }
    }
    public void removed(){afterPress=null;if(operationSound!=null)mc().getSoundManager().stop(operationSound);operationSound=null;pending=false;}
    public void scroll(double delta){if(selection&&!pending&&afterPress==null){page.page+=delta<0?1:-1;tick();}}
}
