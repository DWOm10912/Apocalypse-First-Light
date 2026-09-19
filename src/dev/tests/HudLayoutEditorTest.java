import com.antaurora.apofirstlight.client.hudlayout.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Pure session/source tests, not a claim of Minecraft screen/visual verification. */
public final class HudLayoutEditorTest {
    private static int checks;
    private static void check(boolean value,String description){checks++;if(!value)throw new AssertionError(description);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001F;}
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static final class Sample implements HudLayoutDescriptor {
        final String id;JsonObject applied;
        Sample(String id){this.id=id;}
        public String id(){return id;}
        public String fileName(){return id+".json";}
        public JsonObject defaults(){return json("{\"x\":0,\"y\":0,\"scale\":1,\"width\":40,\"height\":30,\"nested\":{\"known\":2}}");}
        public void apply(JsonObject value){applied=value.deepCopy();}
        public List<HudEditableElement> elements(JsonObject j,int w,int h){
            float x=j.get("x").getAsFloat(),y=j.get("y").getAsFloat(),scale=j.get("scale").getAsFloat();
            float boxWidth=j.get("width").getAsFloat(),boxHeight=j.get("height").getAsFloat();
            return List.of(new HudEditableElement("global",new HudBounds(w-100+x,h-100+y,boxWidth*scale,boxHeight*scale),
                    "x","y",x,y,1/scale,false,500,List.of(new HudEditableElement.Scale("scale",scale,.25F,2)),
                    new HudEditableElement.Resize("width","height",boxWidth,boxHeight,8,160,8,100,false,false,false)));
        }
        public HudBounds occupied(JsonObject j,int w,int h){return elements(j,w,h).get(0).bounds();}
    }
    @FunctionalInterface interface IOAction {void run() throws Exception;}
    private static void rejects(IOAction action,String message) throws Exception {
        boolean failed=false;try{action.run();}catch(java.io.IOException expected){failed=true;}check(failed,message);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of("build"),"hud-editor-tests-").toAbsolutePath();
        Files.writeString(root.resolve("build.gradle"),"");Files.writeString(root.resolve("settings.gradle"),"");
        Files.writeString(root.resolve("gradlew"),"");Files.createDirectory(root.resolve("run"));
        Path source=root.resolve("src/main/resources/assets/apocalypse_firstlight/gui/layout");Files.createDirectories(source);
        var a=new Sample("first");var b=new Sample("second");var registry=new HudLayoutRegistry();
        registry.register(a);registry.register(b);
        check(registry.all().size()==2 && registry.get("first")==a && registry.get("second")==b,"registry dispatch");
        check(registry.get("unknown")==null,"unknown id");
        var original=a.defaults();original.addProperty("future_field","keep me");original.getAsJsonObject("nested").addProperty("future",17);
        Files.writeString(source.resolve(a.fileName()),original.toString());Files.writeString(source.resolve(b.fileName()),b.defaults().toString());
        byte[] other=Files.readAllBytes(source.resolve(b.fileName()));
        var store=new HudLayoutSourceStore(true,root.resolve("run"),null);
        check(store.path(a).equals(source.resolve(a.fileName()).toRealPath()),"resolve cwd parent source");
        rejects(()->new HudLayoutSourceStore(false,root,root),"production refused even with valid checkout");
        Path outside=Files.createTempDirectory("hud-no-project-");
        rejects(()->new HudLayoutSourceStore(true,outside,null),"missing source root refused");
        rejects(()->store.path("../other.json"),"path traversal refused");
        rejects(()->store.path("absent.json"),"never create missing layout");
        var session=new HudLayoutSession(registry.get("first"),store);
        var secondSession=new HudLayoutSession(registry.get("second"),store);
        check(!session.dirty() && session.selected()==null,"clean session");
        check(!session.select(5,5,320,180),"outside/current-only selection");
        check(session.select(225,85,320,180),"select current layout");
        check(secondSession.selected()==null,"no shared selection");
        session.drag(10,5,320,180);
        check(session.preview().get("x").getAsInt()==10 && session.preview().get("y").getAsInt()==5,"drag changes draft");
        check(a.applied==null && b.applied==null,"preview does not mutate live snapshots");
        check(store.read(a).json().get("x").getAsInt()==0,"drag does not write disk");
        session.scroll(1,false,320,180);check(near(session.preview().get("scale").getAsFloat(),1.05F),"wheel 0.05");
        session.scroll(-1,true,320,180);check(near(session.preview().get("scale").getAsFloat(),1.04F),"shift wheel 0.01");
        for(int i=0;i<100;i++)session.scroll(1,false,320,180);
        check(near(session.preview().get("scale").getAsFloat(),2),"upper scale clamp");
        for(int i=0;i<100;i++)session.scroll(-1,false,320,180);
        check(near(session.preview().get("scale").getAsFloat(),.25F),"lower scale clamp");
        session.cancel();check(!session.dirty() && session.preview().equals(original),"cancel restores draft");
        check(store.read(a).json().equals(original),"cancel no writes");
        session.select(225,85,320,180);
        var start=session.selectedElement(320,180);
        var right=session.handleAt(start,260,95);
        check(right.right() && !right.left() && !right.top(),"right edge hit");
        session.resizeFrom(start,right,12,0);
        check(near(session.preview().get("width").getAsFloat(),52),"horizontal resize");
        session.cancel();session.select(225,85,320,180);start=session.selectedElement(320,180);
        var left=session.handleAt(start,220,95);
        session.resizeFrom(start,left,8,0);
        check(near(session.preview().get("width").getAsFloat(),32)
                && near(session.preview().get("x").getAsFloat(),8),"left resize fixes opposite edge");
        session.cancel();session.select(225,85,320,180);start=session.selectedElement(320,180);
        var bottom=session.handleAt(start,230,110);
        session.resizeFrom(start,bottom,0,9);
        check(near(session.preview().get("height").getAsFloat(),39),"vertical resize");
        session.cancel();session.select(225,85,320,180);start=session.selectedElement(320,180);
        var corner=session.handleAt(start,260,110);
        session.resizeFrom(start,corner,10,8);
        check(near(session.preview().get("width").getAsFloat(),50)
                && near(session.preview().get("height").getAsFloat(),38),"corner resize");
        session.cancel();session.select(225,85,320,180);
        var anchored=new HudEditableElement("global",new HudBounds(220,80,40,30),"x","y",0,0,1,false,500,List.of(),
                new HudEditableElement.Resize("width","height",40,30,8,160,8,100,false,false,true));
        session.resizeFrom(anchored,new HudLayoutSession.Handle(false,true,false,true),10,6);
        check(near(session.preview().get("width").getAsFloat(),50)
                && near(session.preview().get("height").getAsFloat(),36)
                && near(session.preview().get("x").getAsFloat(),10)
                && near(session.preview().get("y").getAsFloat(),6),"bottom-right anchor fixes top-left on right/bottom resize");
        session.cancel();session.select(225,85,320,180);start=session.selectedElement(320,180);
        corner=session.handleAt(start,260,110);session.resizeFrom(start,corner,10,8);
        session.save();session.reload();
        check(near(session.preview().get("width").getAsFloat(),50)
                && near(session.preview().get("height").getAsFloat(),38),"resize save/reload round trip");
        var beforeMove=session.elements(320,180).get(0).bounds();
        session.select(beforeMove.x()+8,beforeMove.y()+8,320,180);
        session.drag(13,7,320,180);session.save();session.reload();
        var afterMove=session.elements(320,180).get(0).bounds();
        check(near(afterMove.x(),beforeMove.x()+13) && near(afterMove.y(),beforeMove.y()+7)
                && near(afterMove.width(),beforeMove.width()),"resize then move save/reload preserves screen bounds");
        session.reset();session.save();
        session.select(225,85,320,180);session.drag(15,8,320,180);session.save();
        check(!session.dirty() && a.applied.get("x").getAsInt()==15,"save applies selected snapshot");
        check(store.read(a).json().get("x").getAsInt()==15,"saved source");
        check(store.read(a).json().get("future_field").getAsString().equals("keep me"),"unknown root field retained");
        check(Arrays.equals(other,Files.readAllBytes(source.resolve(b.fileName()))) && b.applied==null,"other JSON byte-identical and unapplied");
        session.reset();check(session.dirty() && session.preview().get("x").getAsInt()==0,"reset own default preview");
        check(session.preview().getAsJsonObject("nested").get("future").getAsInt()==17,"reset preserves unknown nested field");
        check(store.read(a).json().get("x").getAsInt()==15,"reset does not write");
        session.reload();check(session.preview().get("x").getAsInt()==15 && !session.dirty(),"reload source");
        session.select(240,93,320,180);session.drag(2,2,320,180);
        Files.writeString(source.resolve(a.fileName()),a.defaults().toString());
        rejects(session::save,"external change conflict");check(session.dirty(),"failed save retains draft");
        check(store.read(a).json().equals(a.defaults()),"failed save leaves source intact");
        session.reload();var before=session.preview();Files.writeString(source.resolve(a.fileName()),"{broken");
        rejects(session::reload,"invalid JSON controlled failure");check(session.preview().equals(before),"failed reload retains preview");
        check(Files.readString(source.resolve(a.fileName())).equals("{broken"),"invalid JSON not overwritten");
        for(int[] wh:new int[][]{{320,180},{640,360},{960,540}}){
            var bounds=a.occupied(a.defaults(),wh[0],wh[1]);
            check(bounds.x()==wh[0]-100 && bounds.y()==wh[1]-100,"scaled resolution bounds");
        }
        check(Arrays.equals(other,Files.readAllBytes(source.resolve(b.fileName()))),"other source still byte-identical after all actions");
        try(var entries=Files.list(source)){check(entries.noneMatch(p->p.toString().endsWith(".tmp")),"no temporary file leftovers");}
        Path lang=Path.of("src/main/resources/assets/apocalypse_firstlight/lang");
        var en=json(Files.readString(lang.resolve("en_us.json")));var zh=json(Files.readString(lang.resolve("zh_cn.json")));
        var keys=en.keySet().stream().filter(k->k.startsWith("gui.apocalypse_firstlight.hud_layout.")).toList();
        check(keys.size()==20 && keys.stream().allMatch(zh::has),"editor translations paired");
        System.out.println("HudLayoutEditorTest PASS: "+checks+" checks; no client launched");
    }
}
