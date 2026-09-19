import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.weapon.client.NativeGunHudLayout;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Standalone Java/Gson checks: no Minecraft client or world required. */
public final class NativeGunHudLayoutTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static boolean close(float a, float b) { return Math.abs(a-b)<.001F; }
    public static void main(String[] args) throws Exception {
        var warnings=new ArrayList<String>();
        var d=NativeGunHudConfig.defaults();
        var resource=Path.of("src/main/resources/assets/apocalypse_firstlight/gui/layout/native_gun_hud.json");
        var tuned=NativeGunHudConfig.parse(Files.readString(resource),warnings::add);
        check(warnings.isEmpty(),"artist-tuned packaged layout is valid (need not equal fallback)");
        check(NativeGunHudLayout.textScale(tuned.weaponName().scale(),tuned.weaponName().width(),
                tuned.weaponName().height(),18,9)<=.667F,"source layout caps short names without weapon-specific logic");
        check(d.equals(NativeGunHudConfig.parse("{}",warnings::add)),"missing fields");
        var legacy=NativeGunHudConfig.parse("{\"weapon_name\":{\"max_width\":70},\"silhouette\":{\"scale\":0.6}}",warnings::add);
        check(close(legacy.weaponName().width(),70) && close(legacy.weaponName().height(),d.weaponName().height()),"legacy max_width fallback");
        check(close(legacy.silhouette().width(),d.silhouette().width()),"legacy silhouette bounds fallback");
        var old=NativeGunHudConfig.parse("""
                {"silhouette":{"offset_x":18,"offset_y":19,"width":36,"height":38},
                 "weapon_name":{"offset_x":64,"width":38},"ammo":{"offset_x":64,"width":38},
                 "fire_mode":{"offset_x":64,"width":38}}
                """,warnings::add);
        check(old.schemaVersion()==2 && close(old.silhouette().offsetX(),0) && close(old.silhouette().offsetY(),0),"legacy silhouette centre migrates");
        check(close(old.weaponName().offsetX(),45) && close(old.ammo().offsetX(),45)
                && close(old.fireMode().offsetX(),45),"legacy text centres migrate");
        var oldDraft=JsonParser.parseString("{\"weapon_name\":{\"offset_x\":64,\"width\":38,\"future\":42},\"future_root\":true}").getAsJsonObject();
        var migrated=NativeGunHudConfig.migrate(oldDraft);
        check(migrated.get("schema_version").getAsInt()==2 && close(migrated.getAsJsonObject("weapon_name").get("offset_x").getAsFloat(),45),"editor legacy draft converts once");
        check(migrated.get("future_root").getAsBoolean() && migrated.getAsJsonObject("weapon_name").get("future").getAsInt()==42,"migration preserves unknown fields");
        check(migrated.equals(NativeGunHudConfig.migrate(migrated)) && !oldDraft.has("schema_version"),"migration idempotent and source untouched");
        var dimensions=NativeGunHudConfig.parse("{\"global\":{\"width\":120,\"height\":60},\"weapon_name\":{\"width\":80,\"height\":8}}",warnings::add);
        check(dimensions.global().width()==120 && dimensions.global().height()==60 && dimensions.weaponName().width()==80,"explicit dimensions");
        var invalid=NativeGunHudConfig.parse("{\"global\":{\"width\":0},\"silhouette\":{\"height\":-3},\"weapon_name\":{\"width\":0,\"height\":0}}",warnings::add);
        check(invalid.global().width()==d.global().width() && invalid.silhouette().height()==d.silhouette().height()
                && invalid.weaponName().width()==d.weaponName().width() && invalid.weaponName().height()==d.weaponName().height(),"invalid dimensions fallback");
        var custom=NativeGunHudConfig.parse("""
                {"anchor":null,"global":{"scale":-1,"offset_x":7},
                 "silhouette":{"alpha":2,"color":"oops"},"divider":false,
                 "ammo":{"gap":3,"reserve_scale":1e100},"fire_mode":{"scale":"NaN"}}
                """,warnings::add);
        check(custom.global().offsetX()==7 && custom.global().scale()==1,"independent numeric fallback");
        check(custom.silhouette().equals(d.silhouette()),"color and alpha fallback");
        check(custom.divider().equals(d.divider()),"section fallback");
        check(custom.ammo().gap()==3 && custom.ammo().reserveScale()==1,"infinite number fallback");
        check(custom.fireMode().equals(d.fireMode()),"non-numeric fallback");
        check(!warnings.isEmpty(),"warnings emitted");
        check(d.equals(NativeGunHudConfig.parse("{broken",warnings::add)),"broken JSON fallback");
        for(int[] size:new int[][]{{320,180},{427,240},{640,360},{960,540}}) {
            var f=NativeGunHudLayout.frame(d.global(),size[0],size[1]);
            check(close(f.x(),size[0]-94) && close(f.y(),size[1]-99),"default anchor");
            check(close(f.x()+f.width(),size[0]-10),"right margin");
            check(f.y()+f.height()<size[1]-48*.85F-8,"bundled Geiger separation");
            check(f.y()+f.height()<size[1]-48-8,"fallback Geiger separation");
        }
        for(int[] size:new int[][]{{36,22},{60,12},{59,23},{60,20}}) {
            var icon=NativeGunHudLayout.silhouette(d,size[0],size[1]);
            check(close(icon.width()/icon.height(),(float)size[0]/size[1]),"aspect ratio");
            var box=NativeGunHudLayout.silhouetteRegion(d);
            check(icon.x()>=box.x()-.001F && icon.x()+icon.width()<=box.x()+box.width()+.001F,"icon contained in own box");
            check(icon.width()/size[0]>=.599F && icon.width()/size[0]<=.651F,"60-65 percent silhouette");
        }
        var region=NativeGunHudLayout.silhouetteRegion(d);
        check(close(region.width(),d.silhouette().width()) && close(region.height(),d.silhouette().height()),"silhouette edit box");
        for(int[] screen:new int[][]{{320,180},{640,360}}) {
            var f=NativeGunHudLayout.frame(d.global(),screen[0],screen[1]);
            for(float localX:new float[]{0,d.global().width()/2,d.global().width()-20}) {
                var positioned=NativeGunHudConfig.parse("""
                        {"schema_version":2,"silhouette":{"offset_x":%s,"width":20},
                         "divider":{"offset_x":%s,"width":1},
                         "weapon_name":{"offset_x":%s,"width":20},
                         "ammo":{"offset_x":%s,"width":20},
                         "fire_mode":{"offset_x":%s,"width":20}}
                        """.formatted(localX,localX,localX,localX,localX),warnings::add);
                check(close(f.x()+NativeGunHudLayout.silhouetteRegion(positioned).x()*f.scale(),f.x()+localX*f.scale()),"silhouette local X");
                check(close(f.x()+NativeGunHudLayout.divider(positioned).x()*f.scale(),f.x()+localX*f.scale()),"divider local X");
                check(close(f.x()+NativeGunHudLayout.textRegion(positioned.weaponName().offsetX(),5,20,8).x()*f.scale(),f.x()+localX*f.scale()),"name local X");
                check(close(f.x()+NativeGunHudLayout.textRegion(positioned.ammo().offsetX(),5,20,8).x()*f.scale(),f.x()+localX*f.scale()),"ammo local X");
                check(close(f.x()+NativeGunHudLayout.textRegion(positioned.fireMode().offsetX(),5,20,8).x()*f.scale(),f.x()+localX*f.scale()),"mode local X");
                check(close(NativeGunHudLayout.row(localX,20).center(),localX+10),"text align stays inside own box");
            }
        }
        var separated=NativeGunHudConfig.parse("""
                {"schema_version":2,"global":{"width":100},"silhouette":{"offset_x":60,"offset_y":2,"width":30,"height":20},
                 "divider":{"offset_x":1,"offset_y":5,"width":1,"height":15}}
                """,warnings::add);
        check(close(NativeGunHudLayout.silhouetteRegion(separated).x(),60)
                && NativeGunHudLayout.silhouette(separated,36,22).x()>=60
                && close(NativeGunHudLayout.divider(separated).x(),1),"silhouette and divider freely placed independently");
        check(close(NativeGunHudLayout.textScale(1.4F,38,8,18,9),8F/9),"short C.A.T never grows beyond box height");
        check(NativeGunHudLayout.textScale(.75F,80,12,18,9)<=.75F,"short text never exceeds configured scale");
        check(NativeGunHudLayout.textScale(.75F,38,12,100,9)<.75F,"long name shrinks");
        check(d.weaponName().offsetX()==d.ammo().offsetX() && d.ammo().offsetX()==d.fireMode().offsetX(),"common text axis");
        check(d.weaponName().offsetY()+9*d.weaponName().scale()<d.ammo().offsetY(),"name-ammo gap");
        check(d.ammo().offsetY()+9*d.ammo().currentScale()<d.fireMode().offsetY(),"ammo-mode gap");
        for(int x:new int[]{-500,0,500}) for(int y:new int[]{-500,0,500}) {
            var g=new NativeGunHudConfig.Global(x,y,2,160,100,10,61);
            var f=NativeGunHudLayout.frame(g,320,180);
            check(f.x()>=4 && f.y()>=4 && f.x()+f.width()<=316.001F && f.y()+f.height()<=176.001F,"screen bounds");
        }
        check(NativeGunHudConfig.argb("#FFFFFF",0)==0x00FFFFFF,"transparent color");
        System.out.println("NativeGunHudLayoutTest PASS: "+checks+" checks (no runtime visual verification)");
    }
}
