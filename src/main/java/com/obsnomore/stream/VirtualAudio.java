package com.obsnomore.stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates (or finds) a virtual audio device for game-audio capture.
 *
 * <p>Many systems have no desktop-loopback device out of the box. Where the
 * OS lets us create one from the command line we do (Linux Pulse/PipeWire
 * null sink); where it needs a driver install (Windows VB-Cable, macOS
 * BlackHole) we detect an existing one or explain what to install.
 */
public final class VirtualAudio {
    private VirtualAudio() {
    }

    public static final String LINUX_SINK = "obsnomore_game";

    public static class Result {
        public boolean ok;
        /** ffmpeg device string to record, or "" when none. */
        public String device = "";
        /** Human-readable outcome for the GUI. */
        public String note = "";
    }

    /**
     * True when a configured desktop device captures specifically game
     * audio through a virtual tunnel (not the whole desktop). With
     * {@code game_only} on, anything else is skipped at session start —
     * re-enable it by hand-editing the config.
     */
    public static boolean isGameTunnel(String device) {
        if (device == null) return false;
        String dev = device.trim();
        if (dev.isEmpty()) return false;
        switch (FFmpeg.os()) {
            case WINDOWS: {
                String low = dev.toLowerCase();
                return low.contains("cable") || low.contains("voicemeeter")
                        || low.contains("virtual");
            }
            case MAC: {
                String name = macDeviceName(dev);
                if (name == null) return false;
                String low = name.toLowerCase();
                return low.contains("blackhole") || low.contains("virtual")
                        || low.contains("loopback") || low.contains("soundflower");
            }
            default:
                return dev.equals(LINUX_SINK + ".monitor");
        }
    }

    /** Game-tunnel device candidates for pickers (never raw desktop loops). */
    public static List<String> gameTunnelCandidates() {
        List<String> out = new ArrayList<String>();
        switch (FFmpeg.os()) {
            case WINDOWS:
                for (String d : dshowAudio()) {
                    String low = d.toLowerCase();
                    if (low.contains("cable") || low.contains("voicemeeter")
                            || low.contains("virtual")) {
                        if (!out.contains(d)) out.add(d);
                    }
                }
                break;
            case MAC:
                out.addAll(macTunnelIndices());
                break;
            default:
                if (hasSink(LINUX_SINK)) out.add(LINUX_SINK + ".monitor");
                break;
        }
        return out;
    }

    /** Resolve an avfoundation ":N" index to its device name, or null. */
    static String macDeviceName(String dev) {
        if (dev == null) return null;
        String idx = dev.trim();
        if (idx.startsWith(":")) idx = idx.substring(1).trim();
        if (!idx.matches("\\d+")) return null;
        String list = runOut(FFmpeg.locate(), "-hide_banner", "-f", "avfoundation",
                "-list_devices", "true", "-i", "");
        if (list == null) return null;
        for (String line : list.split("\n")) {
            // NOTE: every list line carries an "[AVFoundation indev @ ...]"
            // log prefix — only a DIGITS-ONLY bracket is a device index.
            String found = parseAvIndex(line);
            if (found == null || !found.equals(idx)) continue;
            int b = line.indexOf(']', line.indexOf('[' + found));
            String name = b >= 0 ? line.substring(b + 1).trim() : "";
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    /**
     * First digits-only "[N]" bracket on an avfoundation list line, or null.
     * Skips the "[AVFoundation indev @ ...]" log prefix present on every line.
     */
    static String parseAvIndex(String line) {
        if (line == null) return null;
        int from = 0;
        while (true) {
            int a = line.indexOf('[', from);
            if (a < 0) return null;
            int b = line.indexOf(']', a + 1);
            if (b <= a) return null;
            String inside = line.substring(a + 1, b).trim();
            if (inside.matches("\\d+")) return inside;
            from = b + 1;
        }
    }

    private static List<String> macTunnelIndices() {
        List<String> out = new ArrayList<String>();
        String list = runOut(FFmpeg.locate(), "-hide_banner", "-f", "avfoundation",
                "-list_devices", "true", "-i", "");
        if (list == null) return out;
        for (String line : list.split("\n")) {
            String low = line.toLowerCase();
            if (!(low.contains("blackhole") || low.contains("virtual")
                    || low.contains("loopback") || low.contains("soundflower"))) continue;
            String idx = parseAvIndex(line);
            if (idx != null) {
                String key = ":" + idx;
                if (!out.contains(key)) out.add(key);
            }
        }
        return out;
    }

    public static Result ensure() {
        Result r = new Result();
        switch (FFmpeg.os()) {
            case WINDOWS:
                return ensureWindows(r);
            case MAC:
                return ensureMac(r);
            default:
                return ensureLinux(r);
        }
    }

    // ------------------------------------------------------------------
    // Linux: module-null-sink via pactl (works on PulseAudio + PipeWire).
    // ------------------------------------------------------------------

    private static Result ensureLinux(Result r) {
        if (hasSink(LINUX_SINK)) {
            r.ok = true;
            r.device = LINUX_SINK + ".monitor";
            r.note = "virtual output ready: " + r.device;
            return r;
        }
        String out = runOut("pactl", "load-module", "module-null-sink",
                "sink_name=" + LINUX_SINK,
                "sink_properties=device.description=OBSNoMore-Game-Audio");
        if (out != null && hasSink(LINUX_SINK)) {
            r.ok = true;
            r.device = LINUX_SINK + ".monitor";
            r.note = "created virtual output. Route the game to 'OBSNoMore Game Audio' "
                    + "(e.g. pavucontrol), then record " + r.device;
            return r;
        }
        r.note = "pactl failed (no Pulse/PipeWire?). Use your desktop .monitor instead.";
        return r;
    }

    private static boolean hasSink(String name) {
        String list = runOut("pactl", "list", "short", "sinks");
        if (list == null) return false;
        for (String line : list.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[1].equals(name)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Windows: detect VB-Audio Cable (or similar); can't install drivers.
    // ------------------------------------------------------------------

    private static Result ensureWindows(Result r) {
        List<String> devs = gameTunnelCandidates();
        if (!devs.isEmpty()) {
            r.ok = true;
            r.device = devs.get(0);
            r.note = "virtual input ready: " + r.device;
            return r;
        }
        r.note = "no virtual input found. Install VB-Audio Cable, set it as "
                + "your output, then Detect again.";
        return r;
    }

    private static List<String> dshowAudio() {
        List<String> out = new ArrayList<String>();
        Process p = null;
        try {
            String ffmpeg = FFmpeg.locate();
            if (ffmpeg == null || ffmpeg.isEmpty()) return out;
            p = new ProcessBuilder(ffmpeg, "-hide_banner", "-list_devices", "true",
                    "-f", "dshow", "-i", "dummy").start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            boolean audio = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("DirectShow audio devices")) audio = true;
                else if (line.contains("DirectShow video devices")) audio = false;
                else if (audio && line.contains("\"")) {
                    int a = line.indexOf('"');
                    int b = line.indexOf('"', a + 1);
                    if (a >= 0 && b > a) out.add(line.substring(a + 1, b));
                }
            }
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            try {
                br.close();
            } catch (Throwable ignored) {
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
        return out;
    }

    // ------------------------------------------------------------------
    // macOS: detect BlackHole (or similar); can't create drivers via CLI.
    // ------------------------------------------------------------------

    private static Result ensureMac(Result r) {
        List<String> devs = gameTunnelCandidates();
        if (!devs.isEmpty()) {
            r.ok = true;
            r.device = devs.get(0);
            r.note = "virtual input ready: " + r.device;
            return r;
        }
        r.note = "no virtual input found. Install BlackHole, route game audio "
                + "to it, then Detect again.";
        return r;
    }

    private static String runOut(String... cmd) {
        if (cmd.length == 0 || cmd[0] == null || cmd[0].isEmpty()) return null;
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            try {
                br.close();
            } catch (Throwable ignored) {
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
