package com.antaurora.apofirstlight.client.hudlayout;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;

/** Transparent client tool. Red layouts never participate in selection or IO. */
public final class HudLayoutEditorScreen extends Screen {
    private final HudLayoutSession session;
    private final ClientHudLayout adapter;
    private final HudLayoutRegistry registry;
    private boolean confirming;
    private Component status=Component.empty();
    private HudEditableElement dragging;
    private double dragX,dragY;

    public HudLayoutEditorScreen(ClientHudLayout adapter,HudLayoutSourceStore store,HudLayoutRegistry registry) throws IOException {
        super(text("title"));this.adapter=adapter;this.registry=registry;
        this.session=new HudLayoutSession(adapter,store);
    }
    public ClientHudLayout layout(){return adapter;}
    public static Component text(String key,Object... args){return Component.translatable("gui.apocalypse_firstlight.hud_layout."+key,args);}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){KeyMapping.releaseAll();buttons();}
    private void buttons(){
        clearWidgets();dragging=null;
        if(confirming){
            int w=Math.min(180,width-16),x=(width-w)/2,y=height/2-24;
            button("save_exit",x,y,w,()->save());
            button("discard_exit",x,y+24,w,()->discard());
            button("back",x,y+48,w,()->{confirming=false;buttons();});
        }else{
            int w=Math.min(100,(width-20)/4),x=6;
            button("save",x,22,w,()->save());button("cancel",x+w+2,22,w,()->discard());
            button("reset",x+2*(w+2),22,w,()->session.reset());
            button("reload",x+3*(w+2),22,w,()->{try{session.reload();status=text("reloaded",adapter.id());}catch(Exception e){failure(e);}});
            button("select_next",6,46,Math.min(140,width-12),()->session.cycleSelection(width,height));
        }
    }
    private void button(String key,int x,int y,int width,Runnable action){
        addRenderableWidget(Button.builder(text(key),ignored->action.run()).bounds(x,y,width,20).build());
    }
    private void failure(Exception e){status=text("failed",e.getMessage());}
    private void save(){
        try{
            session.save();
            if(minecraft.player!=null)minecraft.player.displayClientMessage(text("saved",adapter.fileName()),false);
            minecraft.setScreen(null);
        }catch(Exception e){failure(e);}
    }
    private void discard(){session.cancel();minecraft.setScreen(null);}
    @Override public void onClose(){
        if(confirming){confirming=false;buttons();return;}
        if(session.dirty()){confirming=true;buttons();}else discard();
    }
    @Override public void removed(){dragging=null;KeyMapping.releaseAll();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        // Draw previews without mutating the live HUD config managers.
        adapter.render(g,session.preview(),width,height);
        for(var descriptor:registry.all()){
            if(descriptor==adapter || !(descriptor instanceof ClientHudLayout other))continue;
            var b=other.occupied(other.current(),width,height);
            box(g,b,0x38FF3030,0xBBFF4040);
            g.drawString(font,other.id(),(int)b.x(),Math.max(2,(int)b.y()-10),0xFFFF8888,true);
        }
        for(var element:session.elements(width,height))
            box(g,element.bounds(),0,element.id().equals(session.selected())?0xFFFFFFFF:0xBB40CCEE);
        g.fill(0,0,width,90,0xAA101820);
        g.drawString(font,text("current",adapter.id()),6,6,0xFFFFFFFF,true);
        g.drawString(font,text("selected",session.selected()==null?"—":session.selected()),152,51,0xFF88EEFF,true);
        g.drawString(font,text("help"),6,70,0xFFD0D0D0,true);
        g.drawString(font,text("occupied"),6,81,0xFFFF8888,true);
        if(!status.getString().isEmpty())g.drawWordWrap(font,status,6,94,Math.max(20,width-12),0xFFFFDD99);
        if(confirming){
            g.fill(0,0,width,height,0x99000000);
            g.drawCenteredString(font,text("unsaved"),width/2,height/2-40,0xFFFFFFFF);
            // Failure remains visible over the confirmation shade.
            if(!status.getString().isEmpty())g.drawWordWrap(font,status,6,6,width-12,0xFFFFDD99);
        }
        super.render(g,mouseX,mouseY,partial);
    }
    private static void box(GuiGraphics g,HudBounds b,int fill,int edge){
        int x=(int)Math.floor(b.x()),y=(int)Math.floor(b.y());
        int r=(int)Math.ceil(b.x()+b.width()),bottom=(int)Math.ceil(b.y()+b.height());
        if(fill!=0)g.fill(x,y,r,bottom,fill);
        g.fill(x,y,r,y+1,edge);g.fill(x,bottom-1,r,bottom,edge);
        g.fill(x,y,x+1,bottom,edge);g.fill(r-1,y,r,bottom,edge);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(confirming || button!=0)return true;
        if(session.select(x,y,width,height)){
            dragging=session.selectedElement(width,height);dragX=x;dragY=y;
        }else dragging=null;
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(!confirming && button==0 && dragging!=null){session.dragFrom(dragging,x-dragX,y-dragY);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){dragging=null;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!confirming)session.scroll(delta,hasShiftDown(),width,height);
        return true;
    }
}
