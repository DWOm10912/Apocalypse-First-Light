package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Vertices written straight into off-heap memory and handed to a {@link BufferBuilder} in one copy
 * ({@link BufferBuilder#putBulkData}), for the many small quads a frame of the rain and the glass water (2026-10-18,
 * docs/rendering/rain_v1.md section 3.5): BufferBuilder writes element by element through bounds-checked ByteBuffer
 * calls, which was half the render thread's time on a rainy night drive. The bytes are the format's own layout, so the
 * draw (program, uniforms, index buffer) is the one BufferBuilder would have made. Render thread only.
 */
public final class FastVertices {
    private ByteBuffer buffer;
    private long base, at, end;

    public FastVertices(int bytes) {
        buffer = MemoryUtil.memAlloc(bytes);
        base = at = MemoryUtil.memAddress(buffer);
        end = base + bytes;
    }

    public void reset() {
        at = base;
    }

    public int bytes() {
        return (int) (at - base);
    }

    /** Back to the first n bytes written (dropping what came after). */
    public void truncate(int n) {
        at = base + Math.min(n, bytes());
    }

    /** The address of byte offset n (reading back what was written). */
    public long address(int n) {
        return base + n;
    }

    /** The address of the next n bytes, taken (grown when needed). */
    public long take(int n) {
        if (at + n > end) grow(n);
        long p = at;
        at += n;
        return p;
    }

    private void grow(int n) {
        int used = bytes(), size = Math.max(buffer.capacity() * 2, used + n);
        buffer = MemoryUtil.memRealloc(buffer, size);
        base = MemoryUtil.memAddress(buffer);
        at = base + used;
        end = base + size;
    }

    /** What was written, appended to b (which must be building, in the format the bytes were written for); then reset. */
    public void into(BufferBuilder b) {
        int n = bytes();
        if (n > 0) {
            buffer.limit(n);
            buffer.position(0);
            b.putBulkData(buffer);
            buffer.clear();
        }
        reset();
    }

    /** A colour as four bytes r g b a (ABGR int, little endian: the vertex formats' Color element). */
    public static int rgba(int r, int g, int b, int a) {
        return (r & 0xFF) | (g & 0xFF) << 8 | (b & 0xFF) << 16 | (a & 0xFF) << 24;
    }

    /** Two shorts u, v as one int (UV1 / UV2 elements). */
    public static int shorts(int u, int v) {
        return (u & 0xFFFF) | (v & 0xFFFF) << 16;
    }

    /** A normal as BufferBuilder writes it (three signed bytes, then a padding byte). */
    public static int normal(float x, float y, float z) {
        return n(x) | n(y) << 8 | n(z) << 16;
    }

    private static int n(float v) {
        return (int) (Math.max(-1.0F, Math.min(1.0F, v)) * 127.0F) & 0xFF;
    }

    public static void f(long p, float v) {
        MemoryUtil.memPutFloat(p, v);
    }

    public static void i(long p, int v) {
        MemoryUtil.memPutInt(p, v);
    }
}
