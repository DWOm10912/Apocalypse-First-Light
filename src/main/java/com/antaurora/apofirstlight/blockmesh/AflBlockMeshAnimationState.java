package com.antaurora.apofirstlight.blockmesh;

import java.util.HashMap;
import java.util.Map;

/** Per-instance client visual state. No serialization, gameplay mutation, packets or tick loop. */
public final class AflBlockMeshAnimationState {
    private final Map<String, Transition> channels = new HashMap<>();
    /** Loop channels (Animation#loops): their phase and speed. */
    private final Map<String, Rotor> rotors = new HashMap<>();
    private AflBlockMeshProfile profile;
    /** Bumped whenever a channel's target or the profile changes (client/blockmesh/AflMeshChunking re-checks then). */
    private int version;

    public void configure(AflBlockMeshProfile next) {
        if (profile == next) return;
        profile = next;
        version++;
        channels.clear(); // Initial load/reload snaps to authority; never keeps stale definitions.
        rotors.clear();
    }

    public int version() {
        return version;
    }

    public void target(String channel, boolean active, double tick) {
        target(channel, active ? 1.0 : 0.0, tick);
    }

    /**
     * A value channel's target (AflAnimatedMeshHost#meshChannelValue), clamped to 0..1: it eases from the shown value over
     * the channel's duration times the distance, as a boolean channel does between its ends.
     */
    public void target(String channel, double value, double tick) {
        var animation = profile == null ? null : profile.animations().get(channel);
        if (animation == null) return;
        double target = Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        if (animation.loops()) {
            var rotor = rotors.get(channel);
            if (rotor == null) { rotors.put(channel, new Rotor(animation, target, tick)); version++; }
            else if (rotor.target != target) { rotor.retarget(target, tick); version++; }
            return;
        }
        var transition = channels.get(channel);
        if (transition == null) {
            channels.put(channel, new Transition(animation, target, tick));
            version++;
        } else if (transition.target != target) {
            version++;
            // State packets can arrive between frames within the same client tick.
            // Never reverse from a time earlier than the pose already shown to the player.
            tick = Math.max(tick, transition.lastSample);
            double current = transition.sample(tick);
            transition.from = current;
            transition.target = target;
            transition.start = tick;
            transition.duration = animation.durationTicks() * Math.abs(target - current);
        }
    }

    public double sample(String channel, double tick) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.sample(tick);
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.sample(tick);
    }

    /** The channel's resting value at tick: its target (0..1) once the transition has finished, -1 while it moves. */
    public double settled(String channel, double tick) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.speed(Math.max(tick, rotor.lastSample)) > 0 ? -1 : rotor.sample(tick);   // stopped: its angle
        var transition = channels.get(channel);
        if (transition == null) return 0;
        return transition.duration <= 0 || Math.max(tick, transition.lastSample) - transition.start >= transition.duration ? transition.target : -1;
    }

    /** Where the channel is heading or resting (0..1). */
    public double targetValue(String channel) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.target > 0 ? 0.5 : rotor.sample(rotor.lastSample);   // turning: never a resting pose
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.target;
    }

    /**
     * A loop channel: the phase (turns, 0..1) at {@link #start}, the speed then and its target (0..1 of one turn per
     * loopTicks), reached linearly over durationTicks x the change. The phase at any tick is the integral of that speed,
     * so every frame of every pass agrees.
     */
    private static final class Rotor {
        final AflBlockMeshProfile.Animation animation;
        double phase, from, target, start, ramp, lastSample;

        Rotor(AflBlockMeshProfile.Animation animation, double speed, double tick) {
            this.animation = animation;
            this.from = this.target = speed;
            this.start = this.lastSample = tick;
        }

        double speed(double tick) {
            double tau = tick - start;
            return ramp <= 0 || tau >= ramp ? target : from + (target - from) * Math.max(0, tau) / ramp;
        }

        double turns(double tick) {
            double tau = Math.max(0, tick - start), integral;
            if (ramp <= 0) integral = target * tau;
            else if (tau <= ramp) integral = from * tau + (target - from) * tau * tau / (2 * ramp);
            else integral = (from + target) / 2 * ramp + target * (tau - ramp);
            return phase + integral / animation.loopTicks();
        }

        double sample(double tick) {
            tick = Math.max(tick, lastSample);
            lastSample = tick;
            double t = turns(tick);
            return t - Math.floor(t);
        }

        void retarget(double next, double tick) {
            tick = Math.max(tick, lastSample);
            double t = turns(tick), now = speed(tick);
            phase = t - Math.floor(t);
            from = now;
            target = next;
            start = tick;
            ramp = animation.durationTicks() * Math.abs(next - now);
        }
    }

    private static final class Transition {
        final AflBlockMeshProfile.Animation animation;
        double from, target, start, duration, lastSample;
        Transition(AflBlockMeshProfile.Animation animation, double target, double tick) {
            this.animation = animation;
            this.from = this.target = target;
            this.start = tick;
            this.lastSample = tick;
        }
        double sample(double tick) {
            tick = Math.max(tick, lastSample);
            lastSample = tick;
            if (duration <= 0) return target;
            double t = Math.max(0, Math.min(1, (tick - start) / duration));
            return from + (target - from) * animation.easing().apply(t);
        }
    }
}
