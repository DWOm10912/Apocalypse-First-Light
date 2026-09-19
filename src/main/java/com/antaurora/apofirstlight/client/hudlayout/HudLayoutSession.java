package com.antaurora.apofirstlight.client.hudlayout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;

/** One screen owns one draft and selection. Preview never mutates live loader snapshots. */
public final class HudLayoutSession {
    private final HudLayoutDescriptor layout;
    private final HudLayoutSourceStore store;
    private JsonObject draft, baseline;
    private byte[] sourceBytes;
    private String selected;
    public HudLayoutSession(HudLayoutDescriptor layout, HudLayoutSourceStore store) throws IOException {
        this.layout = layout; this.store = store; reload();
    }
    public HudLayoutDescriptor layout() { return layout; }
    public JsonObject preview() { return draft.deepCopy(); }
    public boolean dirty() { return !draft.equals(baseline); }
    public String selected() { return selected; }
    public List<HudEditableElement> elements(int width, int height) { return layout.elements(draft, width, height); }
    public HudEditableElement selectedElement(int width, int height) {
        return elements(width, height).stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
    }
    public boolean select(double x, double y, int width, int height) {
        var elements = elements(width, height);
        // Adapters put the overall container first. Children win overlaps.
        for (int i = elements.size()-1; i >= 0; i--) if (elements.get(i).bounds().contains(x,y)) {
            selected = elements.get(i).id(); return true;
        }
        selected = null; return false;
    }
    public void drag(double dx, double dy, int width, int height) {
        var e = selectedElement(width,height);
        dragFrom(e,dx,dy);
    }
    /** Whole gesture delta avoids losing sub-unit motion for integer-coordinate schemas. */
    public void dragFrom(HudEditableElement e,double dx,double dy) {
        if (e == null || !e.draggable()) return;
        if (!e.id().equals(selected)) return;
        setNumber(draft,e.xPath(),coordinate(e.x() + (float)dx*e.unitsPerPixel(),e));
        setNumber(draft,e.yPath(),coordinate(e.y() + (float)dy*e.unitsPerPixel(),e));
    }
    public void cycleSelection(int width,int height) {
        var all=elements(width,height);if(all.isEmpty())return;
        int index=-1;
        for(int i=0;i<all.size();i++)if(all.get(i).id().equals(selected))index=i;
        selected=all.get((index+1)%all.size()).id();
    }
    private static float coordinate(float value, HudEditableElement e) {
        value = Math.max(-e.offsetLimit(),Math.min(e.offsetLimit(),value));
        return e.integral() ? Math.round(value) : value;
    }
    public void scroll(double direction, boolean fine, int width, int height) {
        var e=selectedElement(width,height);
        if(e==null || !e.scalable() || direction==0) return;
        var primary=e.scales().get(0);
        float ratio=(primary.value()+(fine?.01F:.05F)*(direction>0?1:-1))/primary.value();
        for(var s:e.scales()) ratio=Math.max(s.min()/s.value(),Math.min(s.max()/s.value(),ratio));
        for(var s:e.scales()) setNumber(draft,s.path(),s.value()*ratio);
    }
    public void reset() { mergeDefaults(draft,layout.defaults()); }
    public void cancel() { draft=baseline.deepCopy(); selected=null; }
    public void reload() throws IOException {
        var loaded=store.read(layout); // Do not lose the draft on a failed read.
        draft=loaded.json().deepCopy(); baseline=loaded.json().deepCopy(); sourceBytes=loaded.bytes(); selected=null;
    }
    public void save() throws IOException {
        sourceBytes=store.save(layout,draft,sourceBytes);
        layout.apply(draft.deepCopy()); baseline=draft.deepCopy();
    }
    public static void setNumber(JsonObject root,String path,float value) {
        String[] keys=path.split("\\."); JsonObject current=root;
        for(int i=0;i<keys.length-1;i++) {
            if(!current.has(keys[i]) || !current.get(keys[i]).isJsonObject()) current.add(keys[i],new JsonObject());
            current=current.getAsJsonObject(keys[i]);
        }
        current.addProperty(keys[keys.length-1],value);
    }
    private static void mergeDefaults(JsonObject target,JsonObject defaults) {
        for(var entry:defaults.entrySet()) {
            JsonElement old=target.get(entry.getKey());
            if(old!=null && old.isJsonObject() && entry.getValue().isJsonObject())
                mergeDefaults(old.getAsJsonObject(),entry.getValue().getAsJsonObject());
            else target.add(entry.getKey(),entry.getValue().deepCopy());
        }
    }
}
