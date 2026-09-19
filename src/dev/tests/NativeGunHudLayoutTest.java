import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.weapon.client.NativeGunHudLayout;
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
        check(d.equals(NativeGunHudConfig.parse(Files.readString(resource),warnings::add)),"packaged defaults match fallback");
        check(d.equals(NativeGunHudConfig.parse("{}",warnings::add)),"missing fields");
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
            check(icon.x()>=0 && icon.x()+icon.width()<=NativeGunHudLayout.divider(d).x()-3,"left column");
            check(icon.width()/size[0]>=.599F && icon.width()/size[0]<=.651F,"60-65 percent silhouette");
        }
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
