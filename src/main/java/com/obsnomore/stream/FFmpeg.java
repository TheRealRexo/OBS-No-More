package com.obsnomore.stream;

import com.obsnomore.config.OverlayConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds ffmpeg and knows per-OS audio/download conventions.
 *
 * <p>Lookup order: explicit {@code ffmpeg_path} in config, then
 * {@code <gameDir>/obsnomore/ffmpeg[.exe]}, then system PATH. Nothing is
 * bundled (keeps the GitHub/Modrinth footprint small); the settings screen
 * shows the right download link when nothing is found.
 */
public final class FFmpeg {
    private FFmpeg() {
    }

    public enum Os {
        LINUX, WINDOWS, MAC, OTHER
    }

    public static Os os() {
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("win")) return Os.WINDOWS;
        if (n.contains("mac") || n.contains("darwin")) return Os.MAC;
        if (n.contains("nux") || n.contains("nix")) return Os.LINUX;
        return Os.OTHER;
    }

    public static String downloadLink() {
        switch (os()) {
            case WINDOWS:
                return "https://www.gyan.dev/ffmpeg/builds/ (full build)";
            case MAC:
                return "https://evermeet.cx/ffmpeg/ (or: brew install ffmpeg)";
            case LINUX:
            default:
                return "https://johnvansickle.com/ffmpeg/ (full static build)";
        }
    }

    public static String exeName() {
        return os() == Os.WINDOWS ? "ffmpeg.exe" : "ffmpeg";
    }

    /** Resolved binary or "" when missing. */
    public static String locate() {
        try {
            String configured = OverlayConfig.get().ffmpeg_path;
            if (configured != null && !configured.trim().isEmpty()) {
                File f = new File(configured.trim());
                if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
                if (f.isFile()) return f.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        try {
            File local = FabricLoader.getInstance().getGameDir()
                    .resolve("obsnomore").resolve(exeName()).toFile();
            if (local.isFile()) return local.getAbsolutePath();
        } catch (Throwable ignored) {
        }
        String found = onPath(exeName());
        return found == null ? "" : found;
    }

    private static String onPath(String exe) {
        try {
            String path = System.getenv("PATH");
            if (path == null) return null;
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, exe);
                if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Bounded drive scan (candidate dirs + PATH), like a launcher Java scan.
     * Never walks whole filesystems; returns matches for the GUI to pick from.
     */
    public static List<String> scan() {
        List<String> hits = new ArrayList<String>();
        String exe = exeName();
        List<String> dirs = new ArrayList<String>();
        String home = System.getProperty("user.home", "");
        switch (os()) {
            case WINDOWS:
                String pf = System.getenv("ProgramFiles");
                String pfx = System.getenv("ProgramFiles(x86)");
                if (pf != null) {
                    dirs.add(pf + "\\ffmpeg\\bin");
                    dirs.add(pf + "\\Gyan\\FFmpeg\\bin");
                }
                if (pfx != null) dirs.add(pfx + "\\ffmpeg\\bin");
                dirs.add("C:\\ffmpeg\\bin");
                break;
            case MAC:
                dirs.add("/opt/homebrew/bin");
                dirs.add("/usr/local/bin");
                dirs.add(home + "/bin");
                break;
            case LINUX:
            default:
                dirs.add("/usr/bin");
                dirs.add("/usr/local/bin");
                dirs.add(home + "/bin");
                dirs.add(home + "/.local/bin");
                dirs.add("/opt/ffmpeg/bin");
                dirs.add("/snap/bin");
                break;
        }
        try {
            String path = System.getenv("PATH");
            if (path != null) {
                for (String d : path.split(File.pathSeparator)) {
                    if (!dirs.contains(d)) dirs.add(d);
                }
            }
        } catch (Throwable ignored) {
        }
        for (String dir : dirs) {
            try {
                File f = new File(dir, exe);
                if (f.isFile() && !hits.contains(f.getAbsolutePath())) {
                    hits.add(f.getAbsolutePath());
                }
            } catch (Throwable ignored) {
            }
        }
        return hits;
    }

    // ------------------------------------------------------------------
    // Audio device arguments per OS. Devices come from config; hints for GUI.
    // ------------------------------------------------------------------

    public static String audioHint() {
        switch (os()) {
            case WINDOWS:
                return "dshow device, e.g. Microphone (USB Audio). Desktop loopback needs Stereo Mix / VB-Cable.";
            case MAC:
                return "avfoundation index, e.g. \":0\" mic, \":1\" desktop (BlackHole for loopback).";
            case LINUX:
            default:
                return "PulseAudio/PipeWire source, e.g. default, alsa_input..., or a .monitor for desktop.";
        }
    }

    /** Appends mic/desktop inputs for the current OS. Returns input count. */
    public static int appendAudioInputs(List<String> args, OverlayConfig.Streaming st,
                                        boolean desktopAllowed) {
        int n = 0;
        n += appendOneAudio(args, st.mic, true, true);
        n += appendOneAudio(args, st.desktop, false, desktopAllowed);
        return n;
    }

    private static int appendOneAudio(List<String> args, OverlayConfig.AudioIn in,
                                      boolean isMic, boolean allowed) {
        if (in == null || !in.enabled || !allowed) return 0;
        String dev = in.device == null || in.device.trim().isEmpty()
                ? defaultDevice(isMic) : in.device.trim();
        switch (os()) {
            case WINDOWS:
                args.add("-f");
                args.add("dshow");
                args.add("-i");
                args.add("audio=" + dev);
                return 1;
            case MAC:
                args.add("-f");
                args.add("avfoundation");
                args.add("-i");
                args.add(dev);
                return 1;
            case LINUX:
            default:
                args.add("-f");
                args.add("pulse");
                args.add("-i");
                args.add(dev);
                return 1;
        }
    }

    private static String defaultDevice(boolean isMic) {
        switch (os()) {
            case WINDOWS:
                return isMic ? "Microphone" : "Stereo Mix";
            case MAC:
                return isMic ? ":0" : ":0";
            case LINUX:
            default:
                return "default";
        }
    }
}
