import com.antaurora.apofirstlight.weapon.NativeHitEffect;
import com.antaurora.apofirstlight.client.HitParticleAnimation;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;

/** Pure preset/animation/resource checks, not client visual verification. */
public final class NativeHitEffectTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        check(NativeHitEffect.parse(null)==NativeHitEffect.NONE,"null default");
        check(NativeHitEffect.parse("")==NativeHitEffect.NONE,"empty default");
        check(NativeHitEffect.NONE.onHit(true).isEmpty(),"ordinary gun unchanged");
        check(NativeHitEffect.DIZZY_STARS.onHit(false).isEmpty(),"MISS/BLOCK no entity, no effect");
        check(NativeHitEffect.DIZZY_STARS.onHit(true).equals("dizzy_stars"),"entity preset");
        boolean rejected=false;try{NativeHitEffect.parse("unknown");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"unknown preset validation");
        int[] weights=new int[3];for(int roll=0;roll<100;roll++)weights[HitParticleAnimation.variant(roll)]++;
        check(weights[0]==50&&weights[1]==30&&weights[2]==20,"50/30/20 weights");
        for(int lifetime=8;lifetime<=12;lifetime++){
            var seen=new HashSet<Integer>();int last=-1;float alpha=1;
            for(int age=0;age<lifetime;age++){
                int frame=HitParticleAnimation.frame(age,lifetime);float next=HitParticleAnimation.alpha(age,lifetime);
                check(frame>=last&&frame<=7,"non-looping frame");
                check(next>=0&&next<=alpha,"bounded fade");
                if(age*2<=lifetime)check(next==1,"first half opaque");
                seen.add(frame);last=frame;alpha=next;
            }
            check(seen.size()==8,"every frame visited");
            check(HitParticleAnimation.alpha(lifetime,lifetime)==0,"fade zero");
            check(HitParticleAnimation.frame(lifetime+2,lifetime)==7,"never loops");
        }
        for(String name:new String[]{"cat_bule_star","cat_dizzy","cat_yellow_star"}){
            Path p=Path.of("src/main/resources/assets/apocalypse_firstlight/textures/particle",name+".png");
            var image=ImageIO.read(p.toFile());check(image.getWidth()==128&&image.getHeight()==16,"8 frames: "+name);
            check(!java.nio.file.Files.exists(Path.of(p+".mcmeta")),"no shared atlas animation");
        }
        System.out.println("NativeHitEffectTest PASS: "+checks+" checks; no in-world verification");
    }
}
