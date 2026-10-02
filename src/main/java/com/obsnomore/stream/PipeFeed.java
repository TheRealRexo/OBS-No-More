package com.obsnomore.stream;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-process game-frame pipe for sessions where OS capture reads BLACK —
 * Linux Wayland (native and XWayland): the compositor never composites
 * into the X root pixmap, so x11grab captures nothing. Instead the render
 * thread reads the back buffer (glReadPixels, like the editor's live
 * view) and a daemon writer feeds rgb24 frames to ffmpeg stdin.
 *
 * <p>Works for both POVs with no filter-graph changes: in 1-POV the HUD
 * overlays are already baked into the frames; in 2-POV
 * {@code cleanLocal()} hides them and ffmpeg composites sources onto the
 * clean game frames via the normal [0:v] graph.
 *
 * <p>Backpressure never stalls the game: the queue is tiny and full
 * queues drop frames; the writer paces output to the session fps and
 * repeats the last frame across menus/loading so timestamps keep moving.
 */
public final class PipeFeed {
    private PipeFeed() {
    }

    private static volatile boolean wantFrames;
    private static volatile Session session;
    private static volatile int frameW;
    private static volatile int frameH;
    private static volatile int frameFps = 60;

    private static final ArrayBlockingQueue<byte[]> QUEUE = new ArrayBlockingQueue<byte[]>(4);
    private static final ArrayDeque<byte[]> POOL = new ArrayDeque<byte[]>();
    private static volatile byte[] lastFrame;
    private static volatile Thread writer;

    private static ByteBuffer glBuf;
    private static int glW = -1;
    private static int glH = -1;
    private static long lastOfferMs;

    /** Current window framebuffer size, or {0,0} when unknown. */
    public static int[] displaySize() {
        try {
            int w = Display.getWidth();
            int h = Display.getHeight();
            if (w >= 16 && h >= 16 && w <= 8192 && h <= 8192) {
                return new int[]{w, h};
            }
        } catch (Throwable ignored) {
        }
        return new int[]{0, 0};
    }

    public static int pipeW() {
        return frameW;
    }

    public static int pipeH() {
        return frameH;
    }

    public static synchronized void attach(Session s, int w, int h, int fps) {
        detach();
        if (s == null || w < 16 || h < 16) return;
        session = s;
        frameW = w;
        frameH = h;
        frameFps = Math.max(1, Math.min(120, fps));
        QUEUE.clear();
        synchronized (POOL) {
            POOL.clear();
        }
        lastFrame = null;
        lastOfferMs = 0;
        wantFrames = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                writeLoop();
            }
        }, "OBSNoMore-pipefeed");
        t.setDaemon(true);
        writer = t;
        t.start();
        com.obsnomore.ObsLog.info("[OBSNoMore] GL pipe feed on (" + w + "x" + h
                + "@" + frameFps + ")");
    }

    public static synchronized void detach() {
        wantFrames = false;
        session = null;
        Thread t = writer;
        writer = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
        QUEUE.clear();
    }

    public static boolean active() {
        return wantFrames;
    }

    /**
     * Render thread only (called after the HUD renders). Reads the frame,
     * flips it top-down and queues it; drops when the writer is behind.
     */
    public static void offer() {
        if (!wantFrames) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return;
            int w = frameW;
            int h = frameH;
            if (w < 16 || h < 16) return;
            long interval = 1000L / Math.max(1, frameFps);
            long now = System.currentTimeMillis();
            if (now - lastOfferMs < interval) return;
            lastOfferMs = now;
            int[] size = displaySize();
            // Fixed frame size per session: skip while resized mid-session.
            if (size[0] != w || size[1] != h) return;
            byte[] f = grabFrame(w, h);
            if (f == null) return;
            submit(f);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Reads the current back buffer and returns it top-down rgb24, or null.
     * Needs a current GL context on the calling thread.
     */
    static byte[] grabFrame(int w, int h) {
        try {
            if (glBuf == null || w != glW || h != glH) {
                glBuf = ByteBuffer.allocateDirect(w * h * 3);
                glW = w;
                glH = h;
            }
            glBuf.clear();
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, glBuf);
            byte[] f = takeBuffer(w * h * 3);
            byte[] row = new byte[w * 3];
            for (int y = 0; y < h; y++) {
                glBuf.position((h - 1 - y) * w * 3);
                glBuf.get(row, 0, w * 3);
                System.arraycopy(row, 0, f, y * w * 3, w * 3);
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hands a fresh frame to the writer (drops it when the writer is behind). */
    static void submit(byte[] f) {
        if (f == null) return;
        try {
            lastFrame = f;
            if (!QUEUE.offer(f)) {
                giveBuffer(f);
            }
        } catch (Throwable ignored) {
            giveBuffer(f);
        }
    }

    private static void writeLoop() {
        long interval = 1000L / Math.max(1, frameFps);
        long due = System.currentTimeMillis();
        while (wantFrames) {
            try {
                byte[] f = QUEUE.poll(250, TimeUnit.MILLISECONDS);
                if (f == null) f = lastFrame;
                if (f == null) continue;
                long now = System.currentTimeMillis();
                if (now < due) {
                    try {
                        Thread.sleep(due - now);
                    } catch (InterruptedException done) {
                        giveBuffer(f);
                        return;
                    }
                } else if (now - due > interval * 2) {
                    due = now;
                }
                due += interval;
                Session s = session;
                OutputStream out = s == null ? null : s.stdin();
                if (out == null) {
                    giveBuffer(f);
                    return;
                }
                try {
                    out.write(f);
                } catch (Throwable t) {
                    giveBuffer(f);
                    return;
                }
                giveBuffer(f);
            } catch (InterruptedException done) {
                return;
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static byte[] takeBuffer(int n) {
        try {
            synchronized (POOL) {
                while (!POOL.isEmpty()) {
                    byte[] b = POOL.pollFirst();
                    if (b != null && b.length == n) return b;
                }
            }
        } catch (Throwable ignored) {
        }
        return new byte[n];
    }

    private static void giveBuffer(byte[] b) {
        if (b == null) return;
        // Buffers are only ever written on the render thread (offer) and
        // only read on the writer thread after handoff, so recycling here
        // cannot race a live frame.
        try {
            synchronized (POOL) {
                if (POOL.size() < 6) POOL.addLast(b);
            }
        } catch (Throwable ignored) {
        }
    }
}
