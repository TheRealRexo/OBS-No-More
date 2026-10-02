package com.obsnomore.stream;

import com.obsnomore.config.OverlayConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ffmpeg filter graph for 2-POV mode.
 *
 * <p>In 2-POV the streamer plays on a clean frame while viewers get
 * game + sources composited by ffmpeg: camera/image inputs are overlaid,
 * browser sources arrive as drawtext layers fed by per-source text files
 * this mod refreshes ({@code obsnomore/browser_<id>.txt}).
 */
public final class FilterGraph {
    private FilterGraph() {
    }

    public static class Graph {
        /** Extra -i inputs (camera devices, image files). */
        public final List<String> extraInputs = new ArrayList<String>();
        /** -filter_complex string, or null for plain scaling. */
        public String filter;
        /** Font file for drawtext, or "" when none found. */
        public String font = "";
    }

    public static File dir() {
        try {
            File d = FabricLoader.getInstance().getGameDir()
                    .resolve("obsnomore").toFile();
            d.mkdirs();
            return d;
        } catch (Throwable t) {
            File d = new File("obsnomore");
            d.mkdirs();
            return d;
        }
    }

    /** Per-source drawtext file for a browser source (stable across calls). */
    public static File browserFile(OverlayConfig.Source s) {
        String id = "x";
        try {
            if (s != null && s.id != null) {
                String clean = s.id.replaceAll("[^A-Za-z0-9]", "");
                if (!clean.isEmpty()) id = clean.substring(0, Math.min(8, clean.length()));
            }
        } catch (Throwable ignored) {
        }
        return new File(dir(), "browser_" + id + ".txt");
    }

    /** Refresh one browser source's drawtext file. */
    public static void dumpBrowserLines(OverlayConfig.Source s, List<String> lines) {
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(browserFile(s)));
            if (lines != null) {
                for (String l : lines) pw.println(l);
            }
            pw.close();
        } catch (Throwable ignored) {
        }
    }

    public static String findFont() {
        String[] candidates = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/noto/NotoSans-Regular.ttf",
            "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/Adwaita/AdwaitaSans-Regular.ttf",
            System.getProperty("user.home", "") + "/.fonts/DejaVuSans.ttf",
            "C:\\Windows\\Fonts\\arial.ttf",
            "/System/Library/Fonts/Helvetica.ttc",
            "/Library/Fonts/Arial.ttf",
        };
        for (String c : candidates) {
            try {
                if (new File(c).isFile()) return c;
            } catch (Throwable ignored) {
            }
        }
        // Last resort: ask fontconfig.
        try {
            Process p = new ProcessBuilder("fc-match", "-f", "%{file}\n", "sans").start();
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            try {
                br.close();
            } catch (Throwable ignored) {
            }
            if (line != null && new File(line.trim()).isFile()) return line.trim();
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * Builds overlay graph for the active scene. Coordinates are game pixels;
     * scaled by outW/capW into stream pixels. Optional pre-filters (e.g. a
     * macOS window crop) run before the output scale.
     */
    public static Graph build(OverlayConfig cfg, int capW, int capH,
                              int outW, int outH, List<String> preFilters) {
        Graph g = new Graph();
        g.font = findFont();
        double sx = capW <= 0 ? 1.0 : (double) outW / capW;
        double sy = capH <= 0 ? 1.0 : (double) outH / capH;
        OverlayConfig.Scene scene = cfg.activeScene();
        List<String> chains = new ArrayList<String>();
        StringBuilder base = new StringBuilder("[0:v]");
        if (preFilters != null) {
            for (String f : preFilters) {
                if (f == null || f.isEmpty()) continue;
                if (base.length() > 5) base.append(',');
                base.append(f);
            }
        }
        if (base.length() > 5) base.append(',');
        base.append("scale=").append(outW).append(':').append(outH)
                .append(":flags=bilinear[base]");
        chains.add(base.toString());
        String last = "[base]";
        int idx = 1;

        for (OverlayConfig.Source s : scene.sources) {
            if (s == null || !s.enabled) continue;
            String type = s.type == null ? "" : s.type;
            if (type.equals("camera") && s.device != null && !s.device.trim().isEmpty()) {
                // Camera as its own input, then overlay.
                g.extraInputs.add("-f");
                String dev = s.device.trim();
                switch (FFmpeg.os()) {
                    case WINDOWS:
                        g.extraInputs.add("dshow");
                        break;
                    case MAC:
                        g.extraInputs.add("avfoundation");
                        break;
                    default:
                        g.extraInputs.add("v4l2");
                        break;
                }
                g.extraInputs.add("-framerate");
                g.extraInputs.add(String.valueOf(Math.max(1, s.cam_fps)));
                g.extraInputs.add("-video_size");
                g.extraInputs.add(s.cam_w + "x" + s.cam_h);
                g.extraInputs.add("-i");
                g.extraInputs.add(dev);
                int cw = Math.max(16, (int) (s.cam_w * s.scale * sx * (s.stretch_x > 0.0f ? s.stretch_x : 1.0f)));
                int ch = Math.max(16, (int) (s.cam_h * s.scale * sy * (s.stretch_y > 0.0f ? s.stretch_y : 1.0f)));
                int cx = (int) (s.x * sx);
                int cy = (int) (s.y * sy);
                String lbl = "cam" + idx;
                chains.add("[" + idx + ":v]scale=" + cw + ":" + ch + "[" + lbl + "]");
                String out = "s" + idx;
                chains.add(last + "[" + lbl + "]overlay=" + cx + ":" + cy + "[" + out + "]");
                last = "[" + out + "]";
                idx++;
            } else if (type.equals("image") && s.path != null && !s.path.trim().isEmpty()) {
                File f = new File(s.path.trim());
                if (!f.isAbsolute()) {
                    try {
                        f = FabricLoader.getInstance().getGameDir()
                                .resolve("obsnomore").resolve(s.path.trim()).toFile();
                    } catch (Throwable ignored) {
                    }
                }
                if (f.isFile()) {
                    g.extraInputs.add("-loop");
                    g.extraInputs.add("1");
                    g.extraInputs.add("-i");
                    g.extraInputs.add(f.getAbsolutePath());
                    int cx = (int) (s.x * sx);
                    int cy = (int) (s.y * sy);
                    String out = "s" + idx;
                    chains.add(last + "[" + idx + ":v]overlay=" + cx + ":" + cy + "[" + out + "]");
                    last = "[" + out + "]";
                    idx++;
                }
            } else if (type.equals("browser")) {
                java.io.File shot = null;
                try {
                    shot = com.obsnomore.stream.Webshotter.currentShot(s);
                } catch (Throwable ignored) {
                }
                int cx = (int) (s.x * sx);
                int cy = (int) (s.y * sy);
                String out = "s" + idx;
                if (shot != null && shot.isFile()) {
                    // Rendered page: overlay the PNG scaled to the box.
                    float bscx = s.scale * (s.stretch_x > 0.0f ? s.stretch_x : 1.0f);
                    float bscy = s.scale * (s.stretch_y > 0.0f ? s.stretch_y : 1.0f);
                    int lines = Math.max(1, Math.min(25, s.max_messages));
                    int bw = Math.max(16, (int) (220 * bscx * sx));
                    int bh = Math.max(16, (int) ((lines * 11 + 18) * bscy * sy));
                    g.extraInputs.add("-loop");
                    g.extraInputs.add("1");
                    g.extraInputs.add("-i");
                    g.extraInputs.add(shot.getAbsolutePath());
                    String lbl = "web" + idx;
                    chains.add("[" + idx + ":v]scale=" + bw + ":" + bh + "[" + lbl + "]");
                    chains.add(last + "[" + lbl + "]overlay=" + cx + ":" + cy + "[" + out + "]");
                } else if (!g.font.isEmpty()) {
                    int fs = Math.max(10, (int) (13 * s.scale * sx * (s.stretch_x > 0.0f ? s.stretch_x : 1.0f)));
                    chains.add(last + "drawtext=fontfile='" + g.font + "':textfile='"
                            + browserFile(s).getAbsolutePath().replace("'", "")
                            + "':reload=1:x=" + cx + ":y=" + cy + ":fontsize=" + fs
                            + ":fontcolor=white:borderw=1:bordercolor=black[" + out + "]");
                } else {
                    continue;
                }
                last = "[" + out + "]";
                idx++;
            } else if (type.equals("text") && !g.font.isEmpty()
                    && s.text != null && !s.text.isEmpty()) {
                int fs = Math.max(10, (int) (14 * s.scale * sx * (s.stretch_x > 0.0f ? s.stretch_x : 1.0f)));
                int cx = (int) (s.x * sx);
                int cy = (int) (s.y * sy);
                String safe = s.text.replace("'", "").replace(":", ".");
                if (safe.length() > 120) safe = safe.substring(0, 120);
                String out = "s" + idx;
                chains.add(last + "drawtext=fontfile='" + g.font + "':text='" + safe
                        + "':x=" + cx + ":y=" + cy + ":fontsize=" + fs
                        + ":fontcolor=white:borderw=1:bordercolor=black[" + out + "]");
                last = "[" + out + "]";
                idx++;
            }
        }
        if (chains.size() == 1) {
            g.filter = null;
            return g;
        }
        // Rename the final output to [sFinal] so callers can map it.
        String lastChain = chains.get(chains.size() - 1);
        int lb = lastChain.lastIndexOf('[');
        String finalLabel = "[sFinal]";
        if (lb >= 0) {
            lastChain = lastChain.substring(0, lb) + finalLabel;
            chains.set(chains.size() - 1, lastChain);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chains.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(chains.get(i));
        }
        g.filter = sb.toString();
        return g;
    }
}
