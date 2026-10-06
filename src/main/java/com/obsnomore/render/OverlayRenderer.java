package com.obsnomore.render;

import com.obsnomore.config.OverlayConfig;
import com.obsnomore.data.WebFetcher;
import com.obsnomore.stream.CameraManager;
import com.obsnomore.stream.StreamManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Native scene renderer: draws the active scene's sources.
 *
 * <p>Source types: {@code game} (always the base layer, nothing to draw),
 * {@code browser} (web content: chat endpoints, counters, widgets),
 * {@code camera}, {@code text}, {@code image}.
 * No display capture / window capture is offered — other programs can't be
 * pulled in. In 2-POV mode widgets stay hidden locally while streaming or
 * recording (viewers get them composited by ffmpeg instead).
 */
public final class OverlayRenderer {
    private OverlayRenderer() {
    }

    public static final int BROWSER_BASE_W = 220;
    public static final int BROWSER_LINE_H = 11;
    public static final int CAMERA_BASE_W = 160;
    public static final int CAMERA_BASE_H = 120;
    public static final int IMAGE_BASE_W = 160;
    public static final int IMAGE_BASE_H = 90;

    // ------------------------------------------------------------------
    // Sizes per source (uniform scale x non-uniform stretch)
    // ------------------------------------------------------------------

    /** Horizontal effective scale (uniform scale x free stretch). */
    public static float scX(OverlayConfig.Source s) {
        if (s == null) return 1.0f;
        return s.scale * (s.stretch_x > 0.0f ? s.stretch_x : 1.0f);
    }

    /** Vertical effective scale (uniform scale x free stretch). */
    public static float scY(OverlayConfig.Source s) {
        if (s == null) return 1.0f;
        return s.scale * (s.stretch_y > 0.0f ? s.stretch_y : 1.0f);
    }

    public static int sourceWidth(OverlayConfig.Source s) {
        if (s == null) return 0;
        float sx = scX(s);
        String t = s.type == null ? "" : s.type;
        if (t.equals("browser")) return Math.round(BROWSER_BASE_W * sx);
        if (t.equals("camera")) {
            int bw = s.cam_w > 0 ? s.cam_w : CAMERA_BASE_W * 4;
            return Math.max(24, Math.round(bw * 0.25f * sx));
        }
        if (t.equals("image")) {
            return Math.max(16, Math.round((IMAGE_BASE_W - s.crop_left_px - s.crop_right_px) * sx));
        }
        if (t.equals("text")) {
            MinecraftClient c = MinecraftClient.getInstance();
            String str = s.text == null ? "" : s.text;
            int w = c != null && c.textRenderer != null
                    ? c.textRenderer.getStringWidth(str) : str.length() * 6;
            return (int) (w * sx) + 10;
        }
        return 0;
    }

    public static int sourceHeight(OverlayConfig.Source s) {
        if (s == null) return 0;
        float sy = scY(s);
        String t = s.type == null ? "" : s.type;
        if (t.equals("browser")) {
            int lines = Math.max(1, Math.min(25, s.max_messages));
            return lines * Math.round(BROWSER_LINE_H * sy) + (int) (18 * sy);
        }
        if (t.equals("camera")) {
            return Math.max(18, Math.round(CAMERA_BASE_H * sy));
        }
        if (t.equals("image")) {
            int h = IMAGE_BASE_H - s.crop_top_px - s.crop_bottom_px;
            return Math.max(12, Math.round(h * sy));
        }
        if (t.equals("text")) return (int) (18 * sy);
        return 0;
    }

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    /** Gameplay HUD: sources only with no screen. */
    public static void renderHud(MinecraftClient client, boolean inScreen) {
        if (StreamManager.cleanLocal()) return;
        OverlayConfig.Scene scene = OverlayConfig.get().activeScene();
        enableBlend();
        try {
            for (OverlayConfig.Source s : scene.sources) {
                if (s == null || !s.enabled) continue;
                if (!inScreen && client.currentScreen == null) {
                    renderSource(client, s, false);
                }
            }
        } finally {
            disableBlend();
        }
    }

    /** Text labels stay visible over pause/inventory/etc. */
    public static void renderOverScreen(MinecraftClient client) {
        if (client == null) return;
        if (client.currentScreen != null
                && client.currentScreen.getClass().getName().contains("StreamOverlayEditorScreen")) {
            return;
        }
        if (StreamManager.cleanLocal()) return;
        OverlayConfig.Scene scene = OverlayConfig.get().activeScene();
        enableBlend();
        try {
            for (OverlayConfig.Source s : scene.sources) {
                if (s == null || !s.enabled) continue;
                String t = s.type == null ? "" : s.type;
                if (t.equals("text")) renderSource(client, s, false);
            }
        } finally {
            disableBlend();
        }
    }

    /** Editor live preview of one source (POV suppression bypassed). */
    public static void renderSource(MinecraftClient client, OverlayConfig.Source s,
                                    boolean editorPreview) {
        if (client == null || s == null) return;
        String t = s.type == null ? "" : s.type;
        if (t.equals("browser")) renderBrowser(client, s);
        else if (t.equals("text")) renderText(client, s);
        else if (t.equals("image")) renderImage(client, s, editorPreview);
        else if (t.equals("camera")) renderCamera(client, s, editorPreview);
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    public static void renderBrowser(MinecraftClient client, OverlayConfig.Source s) {
        if (client == null || client.textRenderer == null) return;
        TextRenderer tr = client.textRenderer;
        float scx = scX(s);
        float scy = scY(s);
        int w = sourceWidth(s);
        int h = sourceHeight(s);
        int x = s.x;
        int y = s.y;

        fill(x, y, x + w, y + h, 0x90000000);
        drawBorder(x, y, x + w, y + h, 0x60FFFFFF);

        // No title bar: source names are for the editor list only. Viewers
        // must never see labels like "Heart Rate" baked over the content.
        // Rendered page screenshot when a browser is available, refreshed
        // in the background; plain text lines otherwise.
        boolean shotDrawn = false;
        try {
            com.obsnomore.stream.Webshotter.refresh(s);
            java.io.File shot = com.obsnomore.stream.Webshotter.currentShot(s);
            if (shot != null) {
                Identifier id = PreviewTextures.dynamicFile(shot.getAbsolutePath());
                if (id != null) {
                    enableBlend();
                    try {
                        PreviewTextures.drawIcon(id, x + 2, y + 2, w - 4, h - 4);
                        shotDrawn = true;
                    } finally {
                        disableBlend();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (shotDrawn) return;

        List<String> msgs =
                WebFetcher.get().getLines(s.url, Math.max(1, Math.min(25, s.max_messages)));
        if (msgs.size() == 1 && hasBrowserUrl(s)
                && com.obsnomore.stream.Webshotter.locateElectron().isEmpty()
                && com.obsnomore.stream.Webshotter.locateBrowser().isEmpty()) {
            String need = "Need Electron: " + com.obsnomore.stream.InstallGuide.electronInstall();
            need = trimToFit(tr, need, x + 5, w - 10);
            tr.draw(need, x + 5, y + 4, 0xFFFFAA00);
        }
        int lineH = Math.round(BROWSER_LINE_H * scy);
        int maxLines = Math.max(1, Math.min(25, s.max_messages));
        int start = Math.max(0, msgs.size() - maxLines);
        int yy = y + 4;
        for (int i = start; i < msgs.size(); i++) {
            String line = trimToFit(tr, msgs.get(i), x + 5, w - 10);
            tr.draw(line, x + 5, yy, 0xFFFFFFFF);
            yy += lineH;
            if (yy > y + h - 3) break;
        }
    }

    private static boolean hasBrowserUrl(OverlayConfig.Source s) {
        if (s == null || s.url == null) return false;
        String u = s.url.trim();
        return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("file://");
    }

    public static void renderText(MinecraftClient client, OverlayConfig.Source s) {
        if (client == null || client.textRenderer == null) return;
        String str = s.text == null ? "" : s.text;
        int w = sourceWidth(s);
        int h = sourceHeight(s);
        fill(s.x, s.y, s.x + w, s.y + h, 0x80000000);
        client.textRenderer.draw(str, s.x + 5, s.y + 4, s.color);
    }

    public static void renderImage(MinecraftClient client, OverlayConfig.Source s,
                                   boolean editorPreview) {
        if (client == null) return;
        int w = sourceWidth(s);
        int h = sourceHeight(s);
        Identifier id = imageId(s);
        boolean drawn = false;
        if (id != null) {
            enableBlend();
            try {
                PreviewTextures.drawIcon(id, s.x, s.y, w, h);
                drawn = true;
            } finally {
                disableBlend();
            }
        }
        if (!drawn) {
            fill(s.x, s.y, s.x + w, s.y + h, 0x80101010);
            if (client.textRenderer != null) {
                client.textRenderer.draw("IMG?", s.x + 4, s.y + 4, 0xFFAAAAAA);
            }
        }
        drawBorder(s.x, s.y, s.x + w, s.y + h, editorPreview ? 0xFF55FFFF : 0x60FFFFFF);
    }

    static Identifier imageId(OverlayConfig.Source s) {
        if (s == null || s.path == null) return null;
        String p = s.path.trim();
        if (p.isEmpty()) return null;
        try {
            if (p.startsWith("id:")) p = p.substring(3);
            int colon = p.indexOf(':');
            if (colon > 0) {
                return new Identifier(p.substring(0, colon), p.substring(colon + 1));
            }
            return new Identifier("obsnomore", p);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void renderCamera(MinecraftClient client, OverlayConfig.Source s,
                                    boolean editorPreview) {
        if (client == null) return;
        int w = sourceWidth(s);
        int h = sourceHeight(s);
        boolean drawn = false;
        if (s.device != null && !s.device.trim().isEmpty() && client.textRenderer != null) {
            try {
                CameraManager.ensure(s.device, s.cam_w, s.cam_h, s.cam_fps);
                Identifier id = CameraManager.upload(client);
                if (id != null) {
                    enableBlend();
                    try {
                        PreviewTextures.drawIcon(id, s.x, s.y, w, h);
                        drawn = true;
                    } finally {
                        disableBlend();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (!drawn) {
            fill(s.x, s.y, s.x + w, s.y + h, 0x80101018);
            if (client.textRenderer != null) {
                client.textRenderer.draw("CAM", s.x + 4, s.y + 4, 0xFF55FFFF);
                client.textRenderer.draw(s.device == null || s.device.isEmpty()
                        ? "no device" : s.device, s.x + 4, s.y + 14, 0xFF888888);
            }
        }
        drawBorder(s.x, s.y, s.x + w, s.y + h, editorPreview ? 0xFF55FFFF : 0x60FFFFFF);
    }

    // ------------------------------------------------------------------
    // GL helpers
    // ------------------------------------------------------------------

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Plain colored rect using the exact vanilla fill sequence
     * (blend on, texture off, glColor4f, no-arg begin, z=0 vertices).
     * Nothing more: extra state juggling silently drops these quads on
     * some stacks (software renderers / renderer replacers).
     */
    public static void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x0 > x1) { int t = x0; x0 = x1; x1 = t; }
        if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
        float a = ((argb >> 24) & 255) / 255.0f;
        float r = ((argb >> 16) & 255) / 255.0f;
        float g = ((argb >> 8) & 255) / 255.0f;
        float b = (argb & 255) / 255.0f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        Tessellator t = Tessellator.INSTANCE;
        t.begin();
        t.vertex(x0, y1, 0);
        t.vertex(x1, y1, 0);
        t.vertex(x1, y0, 0);
        t.vertex(x0, y0, 0);
        t.end();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawBorder(int x0, int y0, int x1, int y1, int argb) {
        fill(x0, y0, x0 + 1, y1, argb);
        fill(x1 - 1, y0, x1, y1, argb);
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
    }

    /**
     * Resets multitexture / texenv state left over from world, panorama or
     * lightmap rendering. Stale GL_TEXTURE1 bindings and COMBINE env modes
     * otherwise corrupt or swallow foreign quads (both textured and flat).
     */
    public static void resetTextureState() {
        int active = 0;
        try {
            active = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        } catch (Throwable ignored) {
        }
        try {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        } catch (Throwable ignored) {
        }
        try {
            if (active != 0
                    && active != org.lwjgl.opengl.GL13.GL_TEXTURE0) {
                org.lwjgl.opengl.GL13.glActiveTexture(active);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String trimToFit(TextRenderer tr, String text, int x, int avail) {
        if (text == null) return "";
        int tw = tr.getStringWidth(text);
        if (tw <= avail || avail <= 8) return text;
        while (text.length() > 1 && tr.getStringWidth(text + "...") > avail) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
