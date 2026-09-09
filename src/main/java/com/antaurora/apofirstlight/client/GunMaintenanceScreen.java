package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.menu.GunMaintenanceMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Transparent mouse owner, not a container preview. All central geometry is the real world. */
public final class GunMaintenanceScreen extends Screen implements MenuAccess<GunMaintenanceMenu> {
    private final GunMaintenanceMenu menu;
    private int ticks;
    private final MaintenanceAttachmentHud attachments=new MaintenanceAttachmentHud(this);
    public void attachmentResult(int phase){attachments.result(phase);}
    public GunMaintenanceScreen(GunMaintenanceMenu menu,Inventory inventory,Component title){super(title);this.menu=menu;}
    @Override public GunMaintenanceMenu getMenu(){return menu;}
    @Override protected void init(){MaintenanceModeClientState.INSTANCE.enter(this,menu.bench.getBlockPos());KeyMapping.releaseAll();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        var state=MaintenanceModeClientState.INSTANCE;
        if(state.exitFinished()){closeNow();return;}
        if(!state.leaving())attachments.tick();
        // Chunk/BE packets may follow the opening packet. No fake world preview while waiting.
        if(++ticks>10 && (!MaintenanceModeClientState.INSTANCE.valid()||minecraft.player==null||minecraft.player.containerMenu!=menu))closeNow();
    }
    private void closeNow(){if(minecraft.player!=null)minecraft.player.closeContainer();else minecraft.setScreen(null);}
    @Override public void onClose(){
        if(MaintenanceModeClientState.INSTANCE.leaving())return;
        attachments.removed();
        // Cancel any pending server operation immediately, while retaining this input shield for the visual exit.
        if(minecraft.getConnection()!=null)minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(menu.containerId));
        MaintenanceModeClientState.INSTANCE.beginExit();
    }
    private void clickSound(){minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),1f,.35f));}
    @Override public void removed(){attachments.removed();MaintenanceModeClientState.INSTANCE.removed(this);KeyMapping.releaseAll();}
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
        if(!state.ready())return;
        int x0=hotbarX(),y=hotbarY();
        if(attachments.selecting()){attachments.render(g,mouseX,mouseY);return;}
        for(int i=0;i<9;i++){
            int x=x0+i*20;var stack=menu.slots.get(i).getItem();boolean hover=mouseX>=x&&mouseX<x+20&&mouseY>=y&&mouseY<y+20;
            if(hover)state.hoveredHotbarSlot=i;
            g.fill(x,y,x+20,y+20,hover?0xff718279:0xcc52565a);g.fill(x+1,y+1,x+19,y+19,0xc0202426);
            if(i==placeholderSlot()){
                // Drawing only: never create a slot, assign an Inventory stack, or expose it to item use.
                g.renderItem(menu.synchronizedBench().getItem(0),x+2,y+2);g.flush();
                // Item render types do not consistently respect global alpha. Composite 58% slot
                // background over the icon, yielding a reliable 42% visual contribution for all guns.
                g.pose().pushPose();g.pose().translate(0,0,300);
                g.fill(x+1,y+1,x+19,y+19,0x94202426);
                g.pose().popPose();
            }else{g.renderItem(stack,x+2,y+2);g.renderItemDecorations(font,stack,x+2,y+2);}
        }
        if(takeButtonVisible()){
            int x=takeButtonX();g.fill(x,y,x+20,y+20,0xcc52565a);g.fill(x+1,y+1,x+19,y+19,0xc0202426);
            g.drawCenteredString(font,"<",x+10,y+6,0xffc0cbc5);
        }
        var tooltip=returnTooltip(mouseX,mouseY);
        if(tooltip!=null)g.renderTooltip(font,tooltip,mouseX,mouseY);
        else if(menu.bench.isEmpty())g.drawCenteredString(font,Component.translatable("screen.apocalypse_firstlight.maintenance_select"),width/2,y-14,0xffcccccc);
        attachments.render(g,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!MaintenanceModeClientState.INSTANCE.ready())return true;
        if(attachments.click(x,y,button))return true;
        if(button!=0)return true;
        if(y>=hotbarY()&&y<hotbarY()+20&&x>=hotbarX()&&x<hotbarX()+180){
            int slot=(int)(x-hotbarX())/20;
            if(slot==placeholderSlot()){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,GunMaintenanceMenu.RETURN_GUN);}
            else if(menu.bench.isEmpty()&&GunMaintenanceBenchBlockEntity.accepts(menu.slots.get(slot).getItem())){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,slot);}
        }else if(takeButtonVisible()&&inside(x,y,takeButtonX(),hotbarY())){clickSound();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,GunMaintenanceMenu.TAKE_GUN);}
        // World-space clicks intentionally do nothing in V2.1; reserved for attachment/repair targets.
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256||minecraft.options.keyInventory.matches(key,scan)){if(!attachments.back())onClose();}return true;}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(MaintenanceModeClientState.INSTANCE.ready())attachments.scroll(delta);return true;}
}
