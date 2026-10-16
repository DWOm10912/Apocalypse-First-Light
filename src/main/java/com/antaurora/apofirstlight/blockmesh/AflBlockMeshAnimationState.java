package com.antaurora.apofirstlight.blockmesh;

import java.util.HashMap;
import java.util.Map;

/** Per-instance client visual state. No serialization, gameplay mutation, packets or tick loop. */
public final class AflBlockMeshAnimationState {
    private final Map<String, Transition> channels = new HashMap<>();
    /** Loop channels (Animation#loops): their phase and speed. */
    private final Map<String, Rotor> rotors = new HashMap<>();
    /** Follower channels (Animation#follows): position and speed at their last target. */
    private final Map<String, Follower> followers = new HashMap<>();
    private AflBlockMeshProfile profile;
    /** Bumped whenever a channel's target or the profile changes (client/blockmesh/AflMeshChunking re-checks then). */
    private int version;

    public void configure(AflBlockMeshProfile next) {
        if (profile == next) return;
        profile = next;
        version++;
        channels.clear(); // Initial load/reload snaps to authority; never keeps stale definitions.
        rotors.clear();
        followers.clear();
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
        if (animation.follows()) {
            var follower = followers.get(channel);
            if (follower == null) { followers.put(channel, new Follower(animation, target, tick)); version++; }
            else if (follower.target != target) { follower.retarget(target, tick); version++; }
            return;
        }
        var transition = channels.get(channel);
        if (transition == null) {
            channels.put(channel, new Transition(animation, target, tick));
            version++;
        } else if (transition.goal != target) {
            version++;
            // State packets can arrive between frames within the same client tick.
            // Never reverse from a time earlier than the pose already shown to the player.
            tick = Math.max(tick, transition.lastSample);
            double current = transition.sample(tick), end = target;
            // a wrapping channel: the short way round (end may be just past 1 or below 0; sample wraps it back)
            if (animation.wraps()) end = target + Math.rint(current - target);
            transition.from = current;
            transition.target = end;
            transition.goal = target;
            transition.start = tick;
            transition.duration = animation.durationTicks() * Math.abs(end - current);
        }
    }

    public double sample(String channel, double tick) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.sample(tick);
        var follower = followers.get(channel);
        if (follower != null) return follower.sample(tick);
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.sample(tick);
    }

    /** The channel's resting value at tick: its target (0..1) once the transition has finished, -1 while it moves. */
    public double settled(String channel, double tick) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.speed(Math.max(tick, rotor.lastSample)) > 0 ? -1 : rotor.sample(tick);   // stopped: its angle
        var follower = followers.get(channel);
        if (follower != null) return follower.settled(tick) ? follower.target : -1;
        var transition = channels.get(channel);
        if (transition == null) return 0;
        return transition.duration <= 0 || Math.max(tick, transition.lastSample) - transition.start >= transition.duration ? transition.goal : -1;
    }

    /** Where the channel is heading or resting (0..1). */
    public double targetValue(String channel) {
        var rotor = rotors.get(channel);
        if (rotor != null) return rotor.target > 0 ? 0.5 : rotor.sample(rotor.lastSample);   // turning: never a resting pose
        var follower = followers.get(channel);
        if (follower != null) return follower.target;
        var transition = channels.get(channel);
        return transition == null ? 0 : transition.goal;
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

    /**
     * A follower channel (a gauge needle), 2026-10-15: one cubic Hermite segment per target, from the shown position x0 and
     * speed v0 to the new target over duration ticks, so position and speed never jump.
     * <ul><li>duration: 1.25 x the time since the previous target, clamped to followTicks .. 4 x followTicks. While the
     * host keeps sending (its sync interval, which jitters by a tick or two) the next value therefore arrives shortly
     * before the needle reaches the current one: it plays the stream back about one interval late, at the reading's own
     * rate, without stopping between steps (a segment that ran out first would stop the needle for a moment);</li>
     * <li>end speed: in a stream (the gap at most 1.5 x the duration) the segment's mean speed, so a steady reading moves
     * the needle at a constant speed, no pulsing; after a pause (an isolated change: the key turned, a refill) 0, an
     * eased swing that settles on the value.</li></ul>
     * Past the end the needle rests on the target. The earlier critically damped chase (time constant followTicks)
     * reached each 1 % step within about a second and then waited for the next: it stepped (user 2026-10-15). Clamped
     * to 0..1.
     */
    private static final class Follower {
        final double minTicks, maxTicks;
        double target, x0, v0, v1, start, duration, lastTarget, lastSample;

        Follower(AflBlockMeshProfile.Animation animation, double target, double tick) {
            this.minTicks = animation.followTicks();
            this.maxTicks = 4 * animation.followTicks();
            this.target = this.x0 = target;
            this.start = this.lastSample = this.lastTarget = tick;
        }

        private double position(double s) {
            if (s >= duration) return target;
            double d = duration, u = s / d, u2 = u * u, u3 = u2 * u;
            return (2 * u3 - 3 * u2 + 1) * x0 + (u3 - 2 * u2 + u) * d * v0 + (-2 * u3 + 3 * u2) * target + (u3 - u2) * d * v1;
        }

        private double velocity(double s) {
            if (s >= duration) return 0;
            double d = duration, u = s / d, u2 = u * u;
            return ((6 * u2 - 6 * u) * x0 + (-6 * u2 + 6 * u) * target) / d + (3 * u2 - 4 * u + 1) * v0 + (3 * u2 - 2 * u) * v1;
        }

        double sample(double tick) {
            tick = Math.max(tick, lastSample);
            lastSample = tick;
            return Math.max(0, Math.min(1, position(Math.max(0, tick - start))));
        }

        /** At rest on the target once the segment has run out. */
        boolean settled(double tick) {
            return Math.max(tick, lastSample) - start >= duration;
        }

        void retarget(double next, double tick) {
            tick = Math.max(tick, lastSample);
            double s = Math.max(0, tick - start), x = position(s), v = velocity(s), gap = tick - lastTarget;
            x0 = Math.max(0, Math.min(1, x));
            v0 = x0 == x ? v : 0;   // pinned at an end: it starts from rest there
            duration = Math.max(minTicks, Math.min(maxTicks, 1.25 * gap));   // a quarter longer: the next value arrives before the segment ends
            v1 = gap <= 1.5 * duration ? (next - x0) / duration : 0;
            target = next;
            start = lastTarget = tick;
        }
    }

    private static final class Transition {
        final AflBlockMeshProfile.Animation animation;
        /** target: where the easing ends (a wrapping channel's may lie outside 0..1); goal: the value asked for. */
        double from, target, goal, start, duration, lastSample;
        Transition(AflBlockMeshProfile.Animation animation, double target, double tick) {
            this.animation = animation;
            this.from = this.target = this.goal = target;
            this.start = tick;
            this.lastSample = tick;
        }
        double sample(double tick) {
            tick = Math.max(tick, lastSample);
            lastSample = tick;
            double t = duration <= 0 ? 1 : Math.max(0, Math.min(1, (tick - start) / duration));
            double v = from + (target - from) * animation.easing().apply(t);
            return animation.wraps() ? v - Math.floor(v) : v;
        }
    }
}
