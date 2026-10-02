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
        boolean inFlight;
    }

    private static final Map<String, Shot> SHOTS = new HashMap<String, Shot>();

    /** Chromium-family binary path, or "" when none is installed. Cached. */
    public static synchronized String locateBrowser() {
        if (probed) return browserBin;
        probed = true;
        browserBin = probe();
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
            stale = !e.inFlight && System.currentTimeMillis() - e.fetchedAt > REFRESH_MS;
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
                    cmd.add("--window-size=" + SHOT_W + "," + SHOT_H);
                    cmd.add("--screenshot=" + tmp.getAbsolutePath());
                    cmd.add(url);
                    Process p = new ProcessBuilder(cmd).start();
                    boolean done = false;
                    try {
                        done = p.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                    if (!done) {
                        try {
                            p.destroy();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (tmp.isFile() && tmp.length() > 1024) {
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
                        } else {
                            try {
                                tmp.delete();
                            } catch (Throwable ignored) {
                            }
                        }
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
}
