package com.obsnomore.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Overlay art registered into the game at runtime, the same way mods like
 * BTWCE/Nightmare register their own textures instead of relying on the
 * static resource pipeline: PNG bytes are read straight out of this mod's
 * jar and uploaded via {@code NativeImageBackedTexture} +
 * {@code registerDynamicTexture}. The legacy resource manager does not
 * reliably resolve mod-jar assets on this stack, so static
 * {@code bindTexture} lookups 404 even for bundled files.
 */

public final class PreviewTextures {
    private PreviewTextures() {
    }

    /** Run-folder art dir (kept for user notes / future file watching). */
    public static final String DIR_NAME = "obsnomore";
    public static final String GAMEPLAY_FILE = "preview_gameplay.png";
    public static final String PAUSED_FILE = "preview_paused.png";

    public static final Identifier ICON_ID =
            new Identifier("obsnomore", "textures/gui/obs_button.png");
    public static final Identifier GAMEPLAY_ID =
            new Identifier("obsnomore", "textures/gui/preview_gameplay.png");
    public static final Identifier PAUSED_ID =
            new Identifier("obsnomore", "textures/gui/preview_paused.png");

    /** Small icon drawn on the menu OBS buttons. */
    public static Identifier getIcon() {
        return dynamic(ICON_ID, "assets/obsnomore/textures/gui/obs_button.png");
    }

    /** Editor canvas background when opened outside a world (main menu). */
    public static Identifier getGameplay() {
        return dynamic(GAMEPLAY_ID, "assets/obsnomore/textures/gui/preview_gameplay.png");
    }

    /** Editor canvas background when opened from the pause menu (in-game). */
    public static Identifier getPaused() {
        return dynamic(PAUSED_ID, "assets/obsnomore/textures/gui/preview_paused.png");
    }

    private static final Map<String, Identifier> DYNAMIC = new HashMap<String, Identifier>();
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<String>();
    private static void logOnce(String assetPath, String how) {
        try {
            synchronized (LOGGED) {
                if (!LOGGED.add(assetPath)) return;
            }
            com.obsnomore.ObsLog.info("[OBSNoMore] art " + assetPath + ": " + how);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Registers a bundled PNG as a runtime texture and returns its dynamic
     * id. Falls back to the static id when anything goes wrong, so a
     * resource-pack override still has a chance through the normal pipeline.
     * Must run on the render thread (GL upload).
     */
    static Identifier dynamic(Identifier fallback, String assetPath) {
        synchronized (DYNAMIC) {
            Identifier hit = DYNAMIC.get(assetPath);
            if (hit != null) return hit;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getTextureManager() == null) return fallback;
            InputStream in = openAsset(assetPath);
            if (in == null) {
                logOnce(assetPath, "MISSING (not in classloader nor mod jar)");
                return fallback;
            }
            BufferedImage img;
            try {
                img = ImageIO.read(in);
            } finally {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (img == null) {
                logOnce(assetPath, "UNREADABLE (ImageIO null)");
                return fallback;
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = client.getTextureManager().registerDynamicTexture("obsnomore", tex);
            if (id == null) {
                logOnce(assetPath, "REGISTER-FAILED (null id)");
                return fallback;
            }
            completeTexture(id);
            synchronized (DYNAMIC) {
                DYNAMIC.put(assetPath, id);
            }
            logOnce(assetPath, "dynamic " + id + " (" + img.getWidth() + "x" + img.getHeight() + ")");
            return id;
        } catch (Throwable t) {
            logOnce(assetPath, "ERROR " + t);
            return fallback;
        }
    }

    /**
     * Opens a bundled asset, bypassing the classloader when it hides
     * non-class resources: falls back to reading our own jar as a zip.
     */
    private static InputStream openAsset(String assetPath) {
        try {
            InputStream in = PreviewTextures.class.getResourceAsStream("/" + assetPath);
            if (in != null) return in;
        } catch (Throwable ignored) {
        }
        try {
            java.security.CodeSource cs = PreviewTextures.class
                    .getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return null;
            File loc = new File(cs.getLocation().toURI());
            if (loc.isDirectory()) {
                File f = new File(loc, assetPath);
                if (f.isFile()) return new java.io.FileInputStream(f);
                return null;
            }
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(loc);
            java.util.zip.ZipEntry e = zip.getEntry(assetPath);
            if (e == null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
                return null;
            }
            final java.util.zip.ZipFile zf = zip;
            InputStream raw = zf.getInputStream(e);
            // Buffer fully so the zip can close while the image decodes.
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                    (int) Math.max(1024, e.getSize()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = raw.read(buf)) > 0) bos.write(buf, 0, n);
            try {
                raw.close();
            } catch (Throwable ignored) {
            }
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
            return new java.io.ByteArrayInputStream(bos.toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Makes a runtime texture actually sampleable. Fresh GL textures default
     * to a mipmapped min filter with no mipmaps (incomplete texture), which
     * strict drivers sample as BLACK. Vanilla GUI textures get LINEAR set
     * elsewhere; dynamic ones never do — so set it here, every time.
     * Must run on the render thread with the texture bound-able.
     */
    static void completeTexture(Identifier id) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getTextureManager() == null || id == null) return;
            OverlayRenderer.resetTextureState();
            client.getTextureManager().bindTexture(id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            // 0x812F = CLAMP_TO_EDGE (GL12 in LWJGL2); keeps NPOT art samplable.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, 0x812F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, 0x812F);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Dynamic texture for an arbitrary image file on disk (web screenshots,
     * user images). Cache key includes mtime so refreshed files re-upload.
     * Returns null when the file is missing/unreadable. Render thread only.
     */
    public static Identifier dynamicFile(String absPath) {
        File f;
        try {
            if (absPath == null || absPath.isEmpty()) return null;
            f = new File(absPath);
            if (!f.isFile()) return null;
        } catch (Throwable t) {
            return null;
        }
        String key;
        try {
            key = "file:" + f.getAbsolutePath() + "@" + f.lastModified();
        } catch (Throwable t) {
            return null;
        }
        synchronized (DYNAMIC) {
            Identifier hit = DYNAMIC.get(key);
            if (hit != null) return hit;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getTextureManager() == null) return null;
            BufferedImage img;
            InputStream in = null;
            try {
                in = new java.io.FileInputStream(f);
                img = ImageIO.read(in);
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Throwable ignored) {
                }
            }
            if (img == null) return null;
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = client.getTextureManager().registerDynamicTexture("obsnomore", tex);
            if (id == null) return null;
            completeTexture(id);
            synchronized (DYNAMIC) {
                DYNAMIC.put(key, id);
            }
            return id;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draws a texture stretched over the full screen (opaque background). */
    public static boolean drawFullscreen(Identifier id, int screenW, int screenH) {
        if (id == null || screenW <= 0 || screenH <= 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return false;
        try {
            // Vanilla-minimal state, see drawIcon.
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            client.getTextureManager().bindTexture(id);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            Tessellator t = Tessellator.INSTANCE;
            t.begin();
            t.vertex(0, screenH, 0, 0.0, 1.0);
            t.vertex(screenW, screenH, 0, 1.0, 1.0);
            t.vertex(screenW, 0, 0, 1.0, 0.0);
            t.vertex(0, 0, 0, 0.0, 0.0);
            t.end();
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Draws a blended icon quad.
     *
     * <p>Vanilla-minimal state (mirrors DrawableHelper.drawTexture exactly:
     * bind + color + tessellate, blend on). Touching depth/alpha/lighting/
     * fog/multitexture state around textured draws silently drops them on
     * some stacks (llvmpipe + renderer replacers).
     */
    public static void drawIcon(Identifier id, int x, int y, int w, int h) {
        if (id == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;
        try {
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            client.getTextureManager().bindTexture(id);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            // Immediate mode: no Tessellator involvement at all. Renderer
            // replacers patch Tessellator paths; fixed-function quads are
            // unambiguous on every GL implementation.
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2i(x, y + h);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2i(x + w, y + h);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2i(x + w, y);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2i(x, y);
            GL11.glEnd();
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
    }
}
