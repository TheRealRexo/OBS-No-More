package com.obsnomore.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Live game view for the editor preview canvas.
 *
 * <p>ffmpeg captures the real window for the stream, but the in-game editor
 * needs its own pixels: every ~500ms (editor open, world loaded) this reads
 * the back buffer back, flips it into a runtime texture and draws it fitted
 * into the preview panel — so the canvas shows the game plus the sources,
 * like OBS. When no world is loaded (main menu) or readback yields nothing
 * (e.g. software rasterizers return zeros), there is simply no view and the
 * editor falls back to the static backdrop.
 *
 * <p>All calls must happen on the render thread.
 */
public final class GameViewCapture {
    private GameViewCapture() {
    }

    private static ByteBuffer buf;
    private static int bufW = -1;
    private static int bufH = -1;
    private static NativeImageBackedTexture tex;
    private static Identifier texId;
    private static int texW = -1;
    private static int texH = -1;
    private static int regW = -1;
    private static int regH = -1;
    private static long lastCapture;
    private static boolean everNonZero;

    /** Throttled readback. Safe to call every frame from the editor. */
    public static void update(MinecraftClient client) {
        try {
            if (client == null || client.world == null) return;
            long now = System.currentTimeMillis();
            if (now - lastCapture < 500) return;
            lastCapture = now;
            int w;
            int h;
            try {
                w = Display.getWidth();
                h = Display.getHeight();
            } catch (Throwable t) {
                return;
            }
            if (w < 16 || h < 16 || w > 8192 || h > 8192) return;
            if (buf == null || w != bufW || h != bufH) {
                buf = ByteBuffer.allocateDirect(w * h * 3);
                bufW = w;
                bufH = h;
            }
            buf.clear();
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buf);
            buf.flip();
            if (isAllZero(buf)) return;
            everNonZero = true;
            if (tex == null || w != texW || h != texH) {
                tex = new NativeImageBackedTexture(w, h);
                texW = w;
                texH = h;
                texId = null;
            }
            int[] px;
            try {
                px = tex.getPixels();
            } catch (Throwable t) {
                return;
            }
            if (px == null || px.length < w * h) return;
            // Flip vertically (GL origin is bottom-left) into ARGB ints,
            // the same packing BufferedImage.getRGB produces.
            byte[] raw = new byte[w * 3];
            for (int y = 0; y < h; y++) {
                buf.position((h - 1 - y) * w * 3);
                buf.get(raw, 0, w * 3);
                int base = y * w;
                for (int x = 0; x < w; x++) {
                    int r = raw[x * 3] & 0xFF;
                    int g = raw[x * 3 + 1] & 0xFF;
                    int b = raw[x * 3 + 2] & 0xFF;
                    px[base + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
            }
            try {
                tex.upload();
            } catch (Throwable t) {
                return;
            }
            if (texId == null || w != regW || h != regH) {
                texId = null;
                if (client.getTextureManager() != null) {
                    try {
                        texId = client.getTextureManager()
                                .registerDynamicTexture("obsnomore_gameview", tex);
                        regW = w;
                        regH = h;
                    } catch (Throwable ignored) {
                    }
                }
            }
            PreviewTextures.completeTexture(texId);
        } catch (Throwable ignored) {
        }
    }

    public static boolean hasView() {
        return everNonZero && texId != null;
    }

    /** Draws the live view fitted over the layout rect (V-flipped). */
    public static void draw(int x, int y, int w, int h) {
        if (!hasView()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;
        try {
            // Vanilla-minimal state, see PreviewTextures.drawIcon.
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            client.getTextureManager().bindTexture(texId);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            Tessellator t = Tessellator.INSTANCE;
            t.begin();
            t.vertex(x, y + h, 0, 0.0, 1.0);
            t.vertex(x + w, y + h, 0, 1.0, 1.0);
            t.vertex(x + w, y, 0, 1.0, 0.0);
            t.vertex(x, y, 0, 0.0, 0.0);
            t.end();
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAllZero(ByteBuffer b) {
        try {
            int n = b.remaining();
            for (int i = 0; i < n; i += 4097) {
                if (b.get(i) != 0) return false;
            }
            return true;
        } catch (Throwable t) {
            return true;
        }
    }
}
