package com.obsnomore.stream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency scan for the Setup Deps page: every external tool the mod
 * relies on, with its resolved path or the install command when missing.
 * Re-runs detectors live (no caches kept here); probe caches upstream are
 * cleared by {@link Webshotter#reprobe()} before a rescan.
 */
public final class DepCheck {
    private DepCheck() {
    }

    public static final class Row {
        /** Short id: ffmpeg, chromium, electron, audio, winlookup. */
        public String name = "";
        /** True when found/usable. */
        public boolean ok;
        /** Path when found, install command when missing. */
        public String detail = "";
        /** True when not applicable on this OS (shown gray). */
        public boolean na;
    }

    public static List<Row> scan() {
        List<Row> rows = new ArrayList<Row>();
        rows.add(ffmpeg());
        rows.add(chromium());
        rows.add(electron());
        rows.add(audio());
        rows.add(winlookup());
        return rows;
    }

    private static Row row(String name, boolean ok, String detail) {
        Row r = new Row();
        r.name = name;
        r.ok = ok;
        r.detail = detail == null ? "" : detail;
        return r;
    }

    private static Row ffmpeg() {
        String f;
        try {
            f = FFmpeg.locate();
        } catch (Throwable t) {
            f = "";
        }
        if (f != null && !f.isEmpty()) return row("ffmpeg", true, f);
        return row("ffmpeg", false, InstallGuide.ffmpegInstall());
    }

    private static Row chromium() {
        String b;
        try {
            b = Webshotter.locateBrowser();
        } catch (Throwable t) {
            b = "";
        }
        if (b != null && !b.isEmpty()) return row("chromium", true, b);
        return row("chromium", false, InstallGuide.browserInstall());
    }

    private static Row electron() {
        String e;
        try {
            e = Webshotter.locateElectron();
        } catch (Throwable t) {
            e = "";
        }
        if (e != null && !e.isEmpty()) return row("electron", true, e);
        return row("electron", false, InstallGuide.electronInstall());
    }

    private static Row audio() {
        try {
            switch (FFmpeg.os()) {
                case WINDOWS:
                case MAC: {
                    com.obsnomore.config.OverlayConfig cfg =
                            com.obsnomore.config.OverlayConfig.get();
                    String dev = cfg != null && cfg.streaming != null
                            && cfg.streaming.desktop != null
                            ? cfg.streaming.desktop.device : "";
                    boolean on = cfg != null && cfg.streaming != null
                            && cfg.streaming.desktop != null
                            && cfg.streaming.desktop.enabled;
                    if (on && dev != null && !dev.isEmpty()
                            && VirtualAudio.isGameTunnel(dev)) {
                        return row("audio", true, dev);
                    }
                    return row("audio", false, InstallGuide.audioInstall());
                }
                default: {
                    String pactl = onPath("pactl");
                    if (pactl != null) return row("audio", true, pactl);
                    String pw = onPath("pw-cli");
                    if (pw != null) {
                        return row("audio", true, pw + " (no pactl: tunnel via pw)");
                    }
                    return row("audio", false, InstallGuide.audioInstall());
                }
            }
        } catch (Throwable t) {
            return row("audio", false, InstallGuide.audioInstall());
        }
    }

    private static Row winlookup() {
        try {
            switch (FFmpeg.os()) {
                case WINDOWS:
                case MAC: {
                    Row r = row("winlookup", true, "built-in");
                    return r;
                }
                default: {
                    if (DeviceDetect.isWayland()) {
                        Row r = row("winlookup", true, "Wayland GL pipe");
                        r.na = true;
                        return r;
                    }
                    String x = onPath("xdotool");
                    if (x != null) return row("winlookup", true, x);
                    return row("winlookup", false,
                            "install xdotool (see wiki Video-Capture)");
                }
            }
        } catch (Throwable t) {
            Row r = row("winlookup", false, "see wiki Video-Capture");
            return r;
        }
    }

    /** First match on PATH, or null. */
    static String onPath(String... names) {
        try {
            String path = System.getenv("PATH");
            if (path == null) return null;
            for (String dir : path.split(File.pathSeparator)) {
                for (String n : names) {
                    try {
                        File f = new File(dir, n);
                        if (f.isFile() && f.canExecute()) {
                            return f.getAbsolutePath();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
