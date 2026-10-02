package com.obsnomore.stream;

import com.obsnomore.config.OverlayConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders browser-source URLs to PNGs with a headless Chromium, so web
 * pages (chat overlays, widgets, full HTML) show as real rendered pages
 * instead of scraped text. The PNG feeds the same image pipeline as image
 * sources, in the preview canvas and in 2-POV recordings.
 *
 * <p>Needs a Chromium binary on PATH (or the standard install location).
 * Without one, browser sources fall back to text-lines mode. Refresh is
 * throttled (~15s) and runs on daemon threads, never on the render thread.
 */
public final class Webshotter {
    private Webshotter() {
    }

    /** Seconds between refreshes of one source. */
    public static final long REFRESH_MS = 15000L;
    /** Screenshot raster size (drawn scaled into the source box). */
    public static final int SHOT_W = 960;
    public static final int SHOT_H = 540;

    private static boolean probed;
    private static String browserBin = "";

    private static final class Shot {
        File file;
        long fetchedAt;
        long retryAt;
        int blanks;
        boolean inFlight;
    }

    private static final Map<String, Shot> SHOTS = new HashMap<String, Shot>();

    /** Retry delay after a blank capture (a good shot may just need JS time). */
    public static final long BLANK_RETRY_MS = 3000L;

    /** Chromium-family binary path, or "" when none is installed. Cached. */
    public static synchronized String locateBrowser() {
        if (probed) return browserBin;
        probed = true;
        browserBin = probe();
        try {
            com.obsnomore.ObsLog.info("webshot browser probe: "
                    + (browserBin.isEmpty() ? "(none found)" : browserBin));
        } catch (Throwable ignored) {
        }
        return browserBin;
    }

    private static String probe() {
        switch (FFmpeg.os()) {
            case WINDOWS: {
                String w = runWhere("chrome.exe");
                if (w != null) return w;
                w = runWhere("msedge.exe");
                if (w != null) return w;
                String[] paths = {
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                };
                for (String p : paths) {
                    try {
                        if (new File(p).isFile()) return p;
                    } catch (Throwable ignored) {
                    }
                }
                return "";
            }
            case MAC: {
                String[] paths = {
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Chromium.app/Contents/MacOS/Chromium",
                };
                for (String p : paths) {
                    try {
                        if (new File(p).isFile()) return p;
                    } catch (Throwable ignored) {
                    }
                }
                return firstOnPath("chromium", "google-chrome");
            }
            default:
                return firstOnPath("chromium", "chromium-browser",
                        "google-chrome", "google-chrome-stable", "microsoft-edge");
        }
    }

    private static String runWhere(String exe) {
        try {
            Process p = new ProcessBuilder("where", exe).start();
            byte[] buf = new byte[1024];
            StringBuilder sb = new StringBuilder();
            try {
                int n;
                while ((n = p.getInputStream().read(buf)) > 0) {
                    sb.append(new String(buf, 0, n));
                }
            } catch (Throwable ignored) {
            }
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            for (String line : sb.toString().split("[\\r\\n]+")) {
                line = line.trim();
                if (!line.isEmpty() && new File(line).isFile()) return line;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String firstOnPath(String... names) {
        String path = System.getenv("PATH");
        if (path == null) return "";
        for (String dir : path.split(File.pathSeparator)) {
            for (String n : names) {
                try {
                    File f = new File(dir, n);
                    if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
                } catch (Throwable ignored) {
                }
            }
        }
        return "";
    }

    /** Stable PNG path for a source's rendered page. */
    public static File shotFile(OverlayConfig.Source s) {
        String id = "x";
        try {
            if (s != null && s.id != null) {
                String clean = s.id.replaceAll("[^A-Za-z0-9]", "");
                if (!clean.isEmpty()) id = clean.substring(0, Math.min(8, clean.length()));
            }
        } catch (Throwable ignored) {
        }
        return new File(FilterGraph.dir(), "web_" + id + ".png");
    }

    /** Fresh rendered shot, or null when none exists (yet). */
    public static File currentShot(OverlayConfig.Source s) {
        try {
            File f = shotFile(s);
            if (f.isFile() && f.length() > 1024) return f;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Kicks a background refresh when the shot is missing or stale. */
    public static void refresh(final OverlayConfig.Source s) {
        if (s == null || s.url == null || s.url.trim().isEmpty()) return;
        final String url = s.url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")
                && !url.startsWith("file://")) return;
        if (locateBrowser().isEmpty()) return;
        final String key = url;
        boolean stale;
        synchronized (SHOTS) {
            Shot e = SHOTS.get(key);
            if (e == null) {
                e = new Shot();
                SHOTS.put(key, e);
            }
            long now = System.currentTimeMillis();
            stale = !e.inFlight && now - e.fetchedAt > REFRESH_MS && now >= e.retryAt;
            if (stale) e.inFlight = true;
        }
        if (!stale) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File out = shotFile(s);
                    // Note: .png suffix required, headless chrome validates
                    // the screenshot file type by extension.
                    File tmp = new File(out.getAbsolutePath() + ".new.png");
                    List<String> cmd = new ArrayList<String>();
                    cmd.add(locateBrowser());
                    cmd.add("--headless");
                    cmd.add("--disable-gpu");
                    cmd.add("--no-sandbox");
                    cmd.add("--hide-scrollbars");
                    // Let JS widgets/feeds finish loading before the shot:
                    // without this the capture is a blank white page.
                    cmd.add("--virtual-time-budget=10000");
                    cmd.add("--run-all-compositor-stages-before-draw");
                    cmd.add("--window-size=" + SHOT_W + "," + SHOT_H);
                    cmd.add("--screenshot=" + tmp.getAbsolutePath());
                    cmd.add(url);
                    Process p = new ProcessBuilder(cmd).start();
                    boolean done = false;
                    try {
                        done = p.waitFor(40, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                    int exit = done ? p.exitValue() : -1;
                    if (!done) {
                        try {
                            p.destroy();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (tmp.isFile() && tmp.length() > 1024) {
                        long shotBytes = tmp.length();
                        boolean blank = isBlankShot(tmp);
                        synchronized (SHOTS) {
                            Shot e = SHOTS.get(key);
                            // A truly solid-color page would look blank
                            // forever: accept it after a few tries instead
                            // of re-spawning chrome every 3s indefinitely.
                            if (e != null) {
                                if (blank) e.blanks++;
                                else e.blanks = 0;
                                if (e.blanks >= 5) blank = false;
                            }
                        }
                        if (blank) {
                            // Blank white capture (page still loading): keep
                            // any good shot, retry soon instead of poisoning
                            // the cache with white for 15s.
                            try {
                                tmp.delete();
                            } catch (Throwable ignored) {
                            }
                            com.obsnomore.ObsLog.info("webshot blank, retry soon: " + url);
                            synchronized (SHOTS) {
                                Shot e = SHOTS.get(key);
                                if (e != null) e.retryAt = System.currentTimeMillis()
                                        + BLANK_RETRY_MS;
                            }
                        } else {
                            try {
                                if (out.isFile()) out.delete();
                            } catch (Throwable ignored) {
                            }
                            if (tmp.renameTo(out)) {
                                synchronized (SHOTS) {
                                    Shot e = SHOTS.get(key);
                                    if (e != null) {
                                        e.file = out;
                                        e.fetchedAt = System.currentTimeMillis();
                                    }
                                }
                                com.obsnomore.ObsLog.info("webshot ok (" + shotBytes
                                        + "B, exit " + exit + "): " + url);
                            } else {
                                try {
                                    tmp.delete();
                                } catch (Throwable ignored) {
                                }
                                com.obsnomore.ObsLog.info("webshot rename failed (exit "
                                        + exit + "): " + url);
                            }
                        }
                    } else {
                        com.obsnomore.ObsLog.info("webshot no shot (done=" + done
                                + " exit=" + exit + "): " + url);
                    }
                } catch (Throwable ignored) {
                } finally {
                    synchronized (SHOTS) {
                        Shot e = SHOTS.get(key);
                        if (e != null) e.inFlight = false;
                    }
                }
            }
        }, "OBSNoMore-webshot");
        t.setDaemon(true);
        t.start();
    }

    /**
     * True when a screenshot is (near-)uniform — a still-loading blank
     * page. Compares quantized colors (5 bits/channel): a blank page is
     * one bucket, while any rendered widget adds text/edges/AA shades.
     * A genuinely solid-color page would trip this too, so callers cap
     * consecutive blanks and eventually accept the shot.
     */
    static boolean isBlankShot(File png) {
        try {
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(png);
            if (img == null) return true;
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 8 || h < 8) return true;
            int step = Math.max(1, (w * h) / 12000);
            java.util.HashMap<Integer, Integer> buckets =
                    new java.util.HashMap<Integer, Integer>();
            int total = 0;
            int top = 0;
            for (int i = 0; i < w * h; i += step) {
                int rgb = img.getRGB(i % w, i / w);
                int key = ((rgb >> 19) & 0x1F) << 10
                        | ((rgb >> 11) & 0x1F) << 5
                        | ((rgb >> 3) & 0x1F);
                int n = buckets.containsKey(key) ? buckets.get(key) + 1 : 1;
                buckets.put(key, n);
                if (n > top) top = n;
                total++;
            }
            return total > 0 && top * 1000 / total >= 995;
        } catch (Throwable t) {
            return true;
        }
    }
}
