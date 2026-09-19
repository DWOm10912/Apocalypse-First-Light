package com.antaurora.apofirstlight.client.hudlayout;

import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfigManager;
import com.antaurora.apofirstlight.weapon.client.NativeGunHud;
import com.antaurora.apofirstlight.weapon.client.NativeGunHudLayout;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public final class NativeGunHudLayoutAdapter implements ClientHudLayout {
    private static final Gson JSON=new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    public String id(){return "native_gun";}
    public String fileName(){return "native_gun_hud.json";}
    public ResourceLocation overlayId(){return new ResourceLocation("apocalypse_firstlight", "native_gun");}
    public JsonObject defaults(){return JSON.toJsonTree(NativeGunHudConfig.defaults()).getAsJsonObject();}
    public JsonObject current(){return JSON.toJsonTree(NativeGunHudConfigManager.get()).getAsJsonObject();}
    public JsonObject normalize(JsonObject source){return NativeGunHudConfig.migrate(source);}
    private NativeGunHudConfig config(JsonObject json){return NativeGunHudConfig.parse(json.toString(), ignored -> {});}
    public void apply(JsonObject json){NativeGunHudConfigManager.apply(config(json));}
    public void render(GuiGraphics g,JsonObject json,int width,int height){NativeGunHud.preview(g,width,height,config(json));}
    public List<HudEditableElement> elements(JsonObject json,int width,int height){
        var c=config(json);var f=NativeGunHudLayout.frame(c.global(),width,height);
        var result=new ArrayList<HudEditableElement>();
        result.add(element("global",new HudBounds(f.x(),f.y(),f.width(),f.height()),
                c.global().offsetX(),c.global().offsetY(),1,List.of(new HudEditableElement.Scale("global.scale",c.global().scale(),.25F,2)),
                resize("global",c.global().width(),c.global().height(),40,160,24,100,false,false,true)));
        var boxes=new java.util.LinkedHashMap<String,NativeGunHudLayout.Box>();
        boxes.put("silhouette",NativeGunHudLayout.silhouetteRegion(c));
        boxes.put("divider",NativeGunHudLayout.divider(c));
        boxes.put("weapon_name",NativeGunHudLayout.textRegion(c.weaponName().offsetX(),c.weaponName().offsetY(),c.weaponName().width(),c.weaponName().height()));
        boxes.put("ammo",NativeGunHudLayout.textRegion(c.ammo().offsetX(),c.ammo().offsetY(),c.ammo().width(),c.ammo().height()));
        boxes.put("fire_mode",NativeGunHudLayout.textRegion(c.fireMode().offsetX(),c.fireMode().offsetY(),c.fireMode().width(),c.fireMode().height()));
        for(var entry:boxes.entrySet()){
            String id=entry.getKey();var box=entry.getValue();
            var bounds=new HudBounds(f.x()+box.x()*f.scale(),f.y()+box.y()*f.scale(),
                    box.width()*f.scale(),box.height()*f.scale());
            float ox,oy;List<HudEditableElement.Scale> scales;HudEditableElement.Resize resize;
            switch(id){
                case "silhouette" -> {ox=c.silhouette().offsetX();oy=c.silhouette().offsetY();scales=scale(id+".scale",c.silhouette().scale());
                    resize=resize(id,c.silhouette().width(),c.silhouette().height(),1,160,1,100,false,false,false);}
                case "divider" -> {ox=c.divider().offsetX();oy=c.divider().offsetY();scales=List.of();
                    resize=resize(id,c.divider().width(),c.divider().height(),.5F,4,1,100,false,false,false);}
                case "weapon_name" -> {ox=c.weaponName().offsetX();oy=c.weaponName().offsetY();scales=scale(id+".scale",c.weaponName().scale());
                    resize=resize(id,c.weaponName().width(),c.weaponName().height(),8,160,1,100,false,false,false);}
                case "ammo" -> {ox=c.ammo().offsetX();oy=c.ammo().offsetY();scales=List.of(
                        new HudEditableElement.Scale("ammo.current_scale",c.ammo().currentScale(),.25F,3),
                        new HudEditableElement.Scale("ammo.reserve_scale",c.ammo().reserveScale(),.25F,3),
                        new HudEditableElement.Scale("ammo.separator_scale",c.ammo().separatorScale(),.25F,3));
                    resize=resize(id,c.ammo().width(),c.ammo().height(),8,160,1,100,false,false,false);}
                case "fire_mode" -> {ox=c.fireMode().offsetX();oy=c.fireMode().offsetY();scales=scale(id+".scale",c.fireMode().scale());
                    resize=resize(id,c.fireMode().width(),c.fireMode().height(),8,160,1,100,false,false,false);}
                default -> throw new IllegalStateException("Unknown native HUD element " + id);
            }
            result.add(element(id,bounds,ox,oy,1/f.scale(),scales,resize));
        }
        return result;
    }
    private static List<HudEditableElement.Scale> scale(String path,float value){return List.of(new HudEditableElement.Scale(path,value,.25F,3));}
    private static HudEditableElement.Resize resize(String id,float w,float h,float minW,float maxW,float minH,float maxH,
                                                     boolean centerX,boolean centerY,boolean anchored){
        return new HudEditableElement.Resize(id+".width",id+".height",w,h,minW,maxW,minH,maxH,centerX,centerY,anchored);
    }
    private static HudEditableElement element(String id,HudBounds b,float x,float y,float units,List<HudEditableElement.Scale> scales,
                                              HudEditableElement.Resize resize){
        return new HudEditableElement(id,b,id+".offset_x",id+".offset_y",x,y,units,false,500,scales,resize);
    }
    public HudBounds occupied(JsonObject json,int width,int height){
        var elements=elements(json,width,height);HudBounds union=elements.get(1).bounds();
        for(int i=2;i<elements.size();i++)union=union.union(elements.get(i).bounds());
        return union;
    }
}
