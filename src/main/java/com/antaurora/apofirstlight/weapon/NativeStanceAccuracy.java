package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-only, transient movement sampling. Never reads model bones or recoil state. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NativeStanceAccuracy {
    public static final double MOVEMENT_THRESHOLD = .01; // blocks per server tick
    public static final int STAND_RECOVERY_TICKS = 5, CROUCH_RECOVERY_TICKS = 3;
    private static final boolean DEBUG = Boolean.getBoolean("afl.nativeAccuracyDebug");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public enum Stance { CROUCH_STILL, CROUCH_MOVING, STAND_STILL, WALKING, SPRINTING, AIRBORNE }
    private static final Map<ServerPlayer, Sample> SAMPLES = new WeakHashMap<>();
    private static final class Sample {
        double x,z,speed; long time,lastMoving = Long.MIN_VALUE;
        net.minecraft.world.level.Level level;
        Stance previousMoving = Stance.STAND_STILL;
    }
    public record Result(Stance stance,double horizontalSpeed,double baseDegrees,double recoveryPenalty,double finalDegrees) {}
    private NativeStanceAccuracy() {}
    public static Stance classify(boolean ground,boolean sprint,boolean crouch,double speed) {
        if (!ground) return Stance.AIRBORNE;
        if (sprint) return Stance.SPRINTING;
        if (speed > MOVEMENT_THRESHOLD) return crouch ? Stance.CROUCH_MOVING : Stance.WALKING;
        return crouch ? Stance.CROUCH_STILL : Stance.STAND_STILL;
    }
    public static double multiplier(NativeAccuracyProfile p,Stance stance) {
        return switch(stance) {
            case CROUCH_STILL -> p.crouchStill(); case CROUCH_MOVING -> p.crouchMoving();
            case STAND_STILL -> 1; case WALKING -> p.walking();
            case SPRINTING -> p.sprinting(); case AIRBORNE -> p.airborne();
        };
    }
    public static double recovery(double previous,double current,long elapsed,int duration) {
        return Math.max(0,previous-current)*Math.max(0,1-Math.max(0,elapsed)/(double)duration);
    }
    private static Sample sample(ServerPlayer p) {
        long now=p.level().getGameTime(); Sample s=SAMPLES.get(p);
        if(s==null || s.level!=p.level() || now<s.time) {
            s=new Sample();s.x=p.getX();s.z=p.getZ();s.time=now;s.level=p.level();SAMPLES.put(p,s);
        }
        double velocity=Math.sqrt(p.getDeltaMovement().horizontalDistanceSqr());
        if(now>s.time) {
            double distance=Math.hypot(p.getX()-s.x,p.getZ()-s.z);
            // Teleports are discontinuities, not a persistent movement penalty.
            s.speed=distance>4 ? velocity : Math.max(velocity,distance/(now-s.time));
            if(distance>4)s.lastMoving=Long.MIN_VALUE;
            s.x=p.getX();s.z=p.getZ();s.time=now;
        } else s.speed=Math.max(s.speed,velocity);
        Stance stance=classify(p.onGround(),p.isSprinting(),p.isCrouching(),s.speed);
        if(stance!=Stance.CROUCH_STILL && stance!=Stance.STAND_STILL) {s.lastMoving=now;s.previousMoving=stance;}
        return s;
    }
    public static Result evaluate(ServerPlayer p,NativeGunDefinition d) {
        Sample s=sample(p);Stance stance=classify(p.onGround(),p.isSprinting(),p.isCrouching(),s.speed);
        double mult=multiplier(d.accuracy(),stance), penalty=0;
        if((stance==Stance.CROUCH_STILL||stance==Stance.STAND_STILL)&&s.lastMoving!=Long.MIN_VALUE)
            penalty=recovery(multiplier(d.accuracy(),s.previousMoving),mult,p.level().getGameTime()-s.lastMoving,
                    p.isCrouching()?CROUCH_RECOVERY_TICKS:STAND_RECOVERY_TICKS);
        Result result=new Result(stance,s.speed,d.spreadDegrees(),d.spreadDegrees()*penalty,d.spreadDegrees()*(mult+penalty));
        if(DEBUG)LOG.info("[AFL ACCURACY] gun={} base={} stance={} speed={} recoveryDegrees={} finalDegrees={}",
                d.id(),result.baseDegrees(),stance,s.speed,result.recoveryPenalty(),result.finalDegrees());
        return result;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p) {
            if(p.isAlive())sample(p);else SAMPLES.remove(p);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {SAMPLES.remove(e.getEntity());}
}
