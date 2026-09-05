package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.client.ExplosionTinnitusEnvelope;
import com.antaurora.apofirstlight.explosion.ExplosionTinnitusProfile;

public class TinnitusTimelineTest {
    static int assertions;
    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        float previousVolume = 0, previousAlpha = 0;
        int previousDuration = 0;
        for (int i = 15; i <= 100; i++) {
            float severity = i / 100F;
            ExplosionTinnitusEnvelope state = new ExplosionTinnitusEnvelope();
            check(state.trigger(severity) == ExplosionTinnitusEnvelope.TriggerResult.RESTART, "initial start");
            check(state.volume() >= previousVolume, "louder when closer");
            check(state.overlayAlpha(0) >= previousAlpha, "stronger overlay when closer");
            check(state.remainingTicks() >= previousDuration, "longer when closer");
            previousVolume = state.volume();
            previousAlpha = state.overlayAlpha(0);
            previousDuration = state.remainingTicks();
            float volume = state.volume(), alpha = state.overlayAlpha(0);
            int duration = state.remainingTicks();
            for (int tick = 1; tick <= duration; tick++) {
                state.tick();
                check(state.volume() <= volume && state.volume() >= 0, "sound monotonic fade");
                check(state.overlayAlpha(0) <= alpha && state.overlayAlpha(0) >= 0, "overlay monotonic fade");
                check(Math.abs(state.volume() - volume) < 0.03, "no abrupt sound cutoff");
                check(state.overlayAlpha(0.5F) <= state.overlayAlpha(0), "render interpolation");
                if (tick >= Math.ceil(duration * 0.85)) check(state.overlayAlpha(0) == 0, "overlay early recovery");
                volume = state.volume();
                alpha = state.overlayAlpha(0);
            }
            check(!state.active() && volume == 0 && alpha == 0, "precise stop");
        }
        ExplosionTinnitusEnvelope state = new ExplosionTinnitusEnvelope();
        check(state.trigger(0.14F) == ExplosionTinnitusEnvelope.TriggerResult.IGNORE, "far no effect");
        check(state.trigger(Float.NaN) == ExplosionTinnitusEnvelope.TriggerResult.IGNORE, "invalid packet");
        state.trigger(0.5F);
        for (int i = 0; i < 30; i++) state.tick();
        int remaining = state.remainingTicks();
        check(state.trigger(0.9F) == ExplosionTinnitusEnvelope.TriggerResult.RESTART, "strong restart");
        check(state.remainingTicks() >= remaining, "strong extends remaining");
        for (int i = 0; i < 30; i++) state.tick();
        float volume = state.volume(), alpha = state.overlayAlpha(0);
        remaining = state.remainingTicks();
        check(state.trigger(0.2F) == ExplosionTinnitusEnvelope.TriggerResult.EXTEND, "weak must not restart sound");
        check(state.remainingTicks() == remaining + 20, "weak +1 second");
        check(state.severity() == 0.9F && state.volume() >= volume && state.overlayAlpha(0) >= alpha,
                "weak never downgrades heavy");
        for (int i = 0; i < 500; i++) state.trigger(0.2F);
        check(state.remainingTicks() == 170, "duration capped to asset length from playback start");
        for (int i = 0; i < 170; i++) state.tick();
        check(!state.active(), "no ghost state after media");
        state.trigger(1);
        state.clear();
        check(!state.active() && state.volume() == 0 && state.overlayAlpha(0) == 0, "cleanup");
        checkOverlaySeparation();
        System.out.println("PASS: " + assertions + " tinnitus timeline assertions (no graphical/audio runtime)");
    }

    private static void checkOverlaySeparation() {
        float previousPeak = 1;
        for (int step = 0; step <= 1200; step++) {
            double distance = step / 100.0;
            float audioSeverity = ExplosionTinnitusProfile.severity(4, distance);
            float visualSeverity = ExplosionTinnitusProfile.overlaySeverity(audioSeverity);
            check(Math.abs(visualSeverity - Math.max(0, 1 - distance / 12)) < 0.000001,
                    "overlay must recover linear normalized distance");
            var audio = new ExplosionTinnitusEnvelope();
            var visual = new ExplosionTinnitusEnvelope();
            audio.trigger(audioSeverity);
            visual.triggerOverlay(audioSeverity);
            check(audio.active() == (audioSeverity >= 0.15F), "audio threshold changed");
            check(visual.active() == (visualSeverity >= 0.08F), "visual threshold");
            float peak = visual.overlayAlpha(0);
            check(peak >= 0 && peak <= 0.60F && peak <= previousPeak, "clamped monotonic visual gradient");
            previousPeak = peak;
            if (audio.active()) check(audio.remainingTicks() == visual.remainingTicks(), "fade duration changed");
            int duration = visual.remainingTicks();
            float previous = peak;
            for (int t = 0; t <= duration; t++) {
                float alpha = visual.overlayAlpha(0);
                check(alpha >= 0 && alpha <= previous, "visual fade regression");
                if (t >= Math.ceil(duration * 0.85)) check(alpha == 0, "visual must finish at 85 percent");
                previous = alpha;
                visual.tick();
            }
        }
        check(Math.abs(ExplosionTinnitusProfile.peakOverlayAlpha(
                ExplosionTinnitusProfile.overlaySeverity(ExplosionTinnitusProfile.severity(4, 4))) - 0.46F)
                < 0.000001, "four-block peak must be 0.46");
        var audio = new ExplosionTinnitusEnvelope();
        var visual = new ExplosionTinnitusEnvelope();
        audio.trigger(0.9F);
        visual.triggerOverlay(0.9F);
        for (int i = 0; i < 80; i++) { audio.tick(); visual.tick(); }
        int audioRemaining = audio.remainingTicks();
        float audioVolume = audio.volume(), peak = visual.overlayAlpha(0);
        float distant = ExplosionTinnitusProfile.severity(4, 10);
        check(audio.trigger(distant) == ExplosionTinnitusEnvelope.TriggerResult.IGNORE, "visual-only restarted audio");
        check(visual.triggerOverlay(distant) == ExplosionTinnitusEnvelope.TriggerResult.EXTEND, "weak visual refresh");
        check(audio.remainingTicks() == audioRemaining && audio.volume() == audioVolume,
                "visual-only explosion changed current sound duration/volume");
        check(visual.overlayAlpha(0) >= peak && visual.severity() == 0.9F, "weak visual downgraded heavy");
        check(visual.triggerOverlay(1) == ExplosionTinnitusEnvelope.TriggerResult.RESTART, "strong visual upgrade");
        for (int i = 0; i < 1000; i++) visual.triggerOverlay(1);
        check(visual.remainingTicks() == 200 && visual.overlayAlpha(0) <= 0.60F, "stacking/cap regression");
        visual.clear();
        for (float invalid : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY, 1.01F, 0}) {
            check(visual.triggerOverlay(invalid) == ExplosionTinnitusEnvelope.TriggerResult.IGNORE,
                    "invalid visual severity accepted");
        }
        check(!visual.active() && visual.overlayAlpha(0) == 0, "visual cleanup");
        System.out.println("TNT distance | normalized | audioSeverity | overlaySeverity | peakAlpha | timelineSeconds | audio");
        for (double d : new double[]{1, 3, 4, 5, 11, 11.05, 12}) {
            float a = ExplosionTinnitusProfile.severity(4, d);
            float v = ExplosionTinnitusProfile.overlaySeverity(a);
            boolean visible = ExplosionTinnitusProfile.shouldTriggerOverlay(a);
            System.out.printf(java.util.Locale.ROOT, "%.2f | %.4f | %.4f | %.4f | %.4f | %.2f | %s%n",
                    d, Math.max(0, 1 - d / 12), a, v,
                    visible ? ExplosionTinnitusProfile.peakOverlayAlpha(v) : 0,
                    visible ? ExplosionTinnitusProfile.durationTicks(a) / 20.0 : 0,
                    ExplosionTinnitusProfile.shouldTrigger(a));
        }
    }
}
