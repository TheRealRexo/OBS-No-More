package com.obsnomore.stream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Live video-device preview (USB HDMI sticks etc.).
 *
 * <p>Runs ffmpeg as a decoder ({@code v4l2/dshow/avfoundation} to rawvideo
 * pipe) on a daemon thread, keeps the newest frame, and uploads it as a
 * Minecraft dynamic texture on the render thread. Best effort: any failure
 * leaves a labeled placeholder box. The same device is composited exactly
 * into the outgoing stream via filter_complex (see {@link FilterGraph}).
 */
public final class CameraManager {
    private CameraManager() {
    }

    private static volatile Thread decoder;
    private static volatile boolean want;
    private static volatile String currentDevice = "";
    private static final List<byte[]> pending = new ArrayList<byte[]>();
    private static volatile int frameW;
    private static volatile int frameH;
    private static Identifier texId;
    private static NativeImageBackedTexture tex;

    public static synchronized void ensure(String device, int w, int h, int fps) {
        if (device == null) device = "";
        if (want && device.equals(currentDevice)) return;
        stop();
        if (device.trim().isEmpty()) return;
        String ffmpeg = FFmpeg.locate();
        if (ffmpeg.isEmpty()) return;
        currentDevice = device;
        want = true;
        final String dev = device.trim();
        final int fw = Math.max(160, Math.min(1920, w <= 0 ? 640 : w));
        final int fh = Math.max(120, Math.min(1080, h <= 0 ? 480 : h));
        final int ff = Math.max(1, Math.min(60, fps <= 0 ? 15 : fps));
        decoder = new Thread(new Runnable() {
            @Override
            public void run() {
                decodeLoop(dev, fw, fh, ff);
            }
        }, "OBSNoMore-camera");
        decoder.setDaemon(true);
        decoder.start();
    }

    public static synchronized void stop() {
        want = false;
        currentDevice = "";
        Thread d = decoder;
        decoder = null;
        if (d != null) {
            try {
                d.interrupt();
            } catch (Throwable ignored) {
            }
        }
        synchronized (pending) {
            pending.clear();
        }
    }

    private static void decodeLoop(String dev, int w, int h, int fps) {
        String ffmpeg = FFmpeg.locate();
        List<String> a = new ArrayList<String>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel");
        a.add("error");
        a.add("-f");
        switch (FFmpeg.os()) {
            case WINDOWS:
                a.add("dshow");
                break;
            case MAC:
                a.add("avfoundation");
                break;
            default:
                a.add("v4l2");
                break;
        }
        a.add("-framerate");
        a.add(String.valueOf(fps));
        a.add("-video_size");
        a.add(w + "x" + h);
        a.add("-i");
        a.add(dev);
        a.add("-f");
        a.add("rawvideo");
        a.add("-pix_fmt");
        a.add("rgb24");
        a.add("-r");
        a.add(String.valueOf(fps));
        a.add("-");
        Process p = null;
        try {
            p = new ProcessBuilder(a).start();
            InputStream in = new BufferedInputStream(p.getInputStream());
            int need = w * h * 3;
            byte[] frame = new byte[need];
            while (want && !Thread.currentThread().isInterrupted()) {
                int off = 0;
                while (off < need && want) {
                    int n = in.read(frame, off, need - off);
                    if (n < 0) break;
                    off += n;
                }
                if (off < need) break;
                byte[] rgb = new byte[need];
                System.arraycopy(frame, 0, rgb, 0, need);
                synchronized (pending) {
                    pending.clear();
                    pending.add(rgb);
                }
                frameW = w;
                frameH = h;
            }
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Render-thread upload of the newest frame. Returns the texture id, or
     * null when no frame is available yet.
     */
    public static Identifier upload(MinecraftClient client) {
        byte[] rgb = null;
        synchronized (pending) {
            if (!pending.isEmpty()) rgb = pending.remove(0);
        }
        if (rgb == null || client == null || client.getTextureManager() == null) {
            return texId;
        }
        try {
            int w = frameW;
            int h = frameH;
            if (w <= 0 || h <= 0) return texId;
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            int[] px = new int[w * h];
            for (int i = 0, p = 0; i < px.length; i++, p += 3) {
                int r = rgb[p] & 255;
                int g = rgb[p + 1] & 255;
                int b = rgb[p + 2] & 255;
                px[i] = (255 << 24) | (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, 0, w, h, px, 0, w);
            tex = new NativeImageBackedTexture(img);
            tex.upload();
            if (texId == null) {
                texId = client.getTextureManager().registerDynamicTexture("obsnomore_cam", tex);
            } else {
                // Re-register under a fresh id so the manager picks up pixels.
                try {
                    texId = client.getTextureManager().registerDynamicTexture("obsnomore_cam", tex);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return texId;
    }
}
