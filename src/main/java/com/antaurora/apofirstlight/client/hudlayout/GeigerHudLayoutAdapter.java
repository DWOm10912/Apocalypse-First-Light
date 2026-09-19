package com.antaurora.apofirstlight.client.hudlayout;

import com.antaurora.apofirstlight.client.GeigerHudOverlay;
import com.antaurora.apofirstlight.client.config.GeigerHudConfig;
import com.antaurora.apofirstlight.client.config.GeigerHudConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public final class GeigerHudLayoutAdapter implements ClientHudLayout {
    private static final Gson JSON=new Gson();
    public String id(){return "geiger_counter";}
    public String fileName(){return "geiger_hud.json";}
    public ResourceLocation overlayId(){return new ResourceLocation("apocalypse_firstlight","geiger_counter");}
    public JsonObject defaults(){return JSON.toJsonTree(GeigerHudConfig.defaults()).getAsJsonObject();}
    public JsonObject current(){return JSON.toJsonTree(GeigerHudConfigManager.get()).getAsJsonObject();}
    public void apply(JsonObject json){GeigerHudConfigManager.apply(GeigerHudConfigManager.sanitize(json));}
    public void render(GuiGraphics g,JsonObject json,int width,int height){GeigerHudOverlay.preview(g,width,height,GeigerHudConfigManager.sanitize(json));}
    public List<HudEditableElement> elements(JsonObject json,int width,int height){
        var c=GeigerHudConfigManager.sanitize(json);float scale=c.hudScale();
        float x=Math.round(width-128*scale-8-c.offsetX()),y=Math.round(height-48*scale-8-c.offsetY());
        var result=new ArrayList<HudEditableElement>();
        result.add(element("global",new HudBounds(x,y,128*scale,48*scale),"offsetX","offsetY",c.offsetX(),c.offsetY(),-1,
                List.of(new HudEditableElement.Scale("hudScale",scale,.5F,2))));
        result.add(element("symbol",new HudBounds(x+c.symbol().x()*scale,y+c.symbol().y()*scale,
                18*c.symbol().scale()*scale,18*c.symbol().scale()*scale),"symbol.x","symbol.y",
                c.symbol().x(),c.symbol().y(),1/scale,List.of(new HudEditableElement.Scale("symbol.scale",c.symbol().scale(),.5F,2))));
        var rows=new GeigerHudConfig.RowOffset[]{c.rows().radiation(),c.rows().dose(),c.rows().zone()};
        String[] names={"radiation","dose","zone"};var lines=GeigerHudOverlay.lines();var font=Minecraft.getInstance().font;
        var textRows=new ArrayList<HudEditableElement>();HudBounds textBounds=null;
        for(int i=0;i<3;i++){
            var r=rows[i];
            // Text fontScale is intentionally independent of hudScale in the existing renderer.
            var b=new HudBounds(x+(c.text().x()+r.offsetX())*scale,
                    y+(c.text().y()+c.text().lineSpacing()*i+r.offsetY())*scale,
                    font.width(lines[i])*c.text().fontScale(),font.lineHeight*c.text().fontScale());
            textBounds=textBounds==null?b:textBounds.union(b);
            textRows.add(element("rows."+names[i],b,"rows."+names[i]+".offsetX","rows."+names[i]+".offsetY",
                    r.offsetX(),r.offsetY(),1/scale,List.of()));
        }
        result.add(element("text",textBounds,"text.x","text.y",c.text().x(),c.text().y(),1/scale,
                List.of(new HudEditableElement.Scale("text.fontScale",c.text().fontScale(),.5F,2))));
        result.addAll(textRows);return result;
    }
    private static HudEditableElement element(String id,HudBounds b,String xp,String yp,float x,float y,float units,List<HudEditableElement.Scale> scales){
        return new HudEditableElement(id,b,xp,yp,x,y,units,true,1000,scales);
    }
    public HudBounds occupied(JsonObject json,int width,int height){
        var elements=elements(json,width,height);HudBounds union=elements.get(0).bounds();
        for(int i=1;i<elements.size();i++)union=union.union(elements.get(i).bounds());return union;
    }
}
