package com.antaurora.apofirstlight.client;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * The GPU time of one stretch of drawing (render thread; GL_TIME_ELAPSED queries, a ring of them read a few frames late
 * so nothing waits): begin() / end() around it, at most once a frame each, never nested with another timer. micros() is
 * a running mean (about the last 30 frames); 0 until the first result is in. For the dev status lines (/afl_render).
 */
public final class GpuTimer {
    private static final int RING = 4;
    private final int[] queries = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int next;
    private boolean open;
    private double mean = -1;

    /** Off unless -Dafl.gputimer=true: reading a query's state is itself a wait on the driver thread. */
    public static boolean enabled = Boolean.getBoolean("afl.gputimer");

    public void begin() {
        if (open || !enabled) return;
        if (queries[0] == 0) for (int i = 0; i < RING; i++) queries[i] = GL15.glGenQueries();
        collect();
        if (pending[next]) return;   // the oldest result is not in yet: skip this frame
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[next]);
        open = true;
    }

    public void end() {
        if (!open) return;
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending[next] = true;
        next = (next + 1) % RING;
        open = false;
    }

    private void collect() {
        for (int i = 0; i < RING; i++) {
            if (!pending[i] || GL15.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            long ns = GL33.glGetQueryObjecti64(queries[i], GL15.GL_QUERY_RESULT);
            pending[i] = false;
            double us = ns / 1000.0;
            mean = mean < 0 ? us : mean + (us - mean) / 30.0;
        }
    }

    public long micros() { return mean < 0 ? 0 : Math.round(mean); }
}
