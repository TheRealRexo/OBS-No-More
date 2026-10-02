package com.obsnomore.stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort auto-detection for the minimal scene: game window region and
 * game-audio (desktop loopback) device, per OS.
 *
 * <p>Everything degrades gracefully: when a helper binary is missing we
 * return "" and the GUI falls back to manual fields.
 */
public final class DeviceDetect {
    private DeviceDetect() {
    }

    /** Game window top-left in screen pixels, or null when unknown. */
    public static int[] gameWindowAt() {
        int[] rect = gameWindowRect();
        if (rect != null) return new int[] {rect[0], rect[1]};
        return null;
    }

    /**
     * Game window rect as {x, y, w, h}, or null when unknown.
     * Linux: xdotool (X11 and XWayland). macOS: AppleScript window bounds
     * (best effort — may need accessibility approval, then returns null).
     * Windows: rect lookup is unreliable from Java; use {@link #gameWindowTitle}.
     */
    public static int[] gameWindowRect() {
        switch (FFmpeg.os()) {
            case MAC:
                return macWindowRect();
            case WINDOWS:
                return null;
            default:
                return linuxWindowRect();
        }
    }

    private static int[] linuxWindowRect() {
        try {
            int[] r = xdotoolRect("Minecraft 1.6.4");
            if (r != null) return r;
            r = xdotoolRect("Minecraft");
            if (r != null) return r;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] xdotoolRect(String name) {
        Process p = null;
        try {
            p = new ProcessBuilder("xdotool", "search", "--name", name).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String id = br.readLine();
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            try {
                br.close();
            } catch (Throwable ignored) {
            }
            if (id == null || id.trim().isEmpty()) return null;
            p = new ProcessBuilder("xdotool", "getwindowgeometry", id.trim()).start();
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            int x = -1;
            int y = -1;
            int w = -1;
            int h = -1;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Position:")) {
                    String[] parts = line.substring(9).trim().split("[, ]+");
                    x = Integer.parseInt(parts[0]);
                    y = Integer.parseInt(parts[1]);
                } else if (line.startsWith("Geometry:")) {
                    String[] parts = line.substring(9).trim().split("x");
                    w = Integer.parseInt(parts[0]);
                    h = Integer.parseInt(parts[1]);
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
            if (x >= 0 && y >= 0 && w > 0 && h > 0) return new int[] {x, y, w, h};
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static int[] xdotoolWindow(String name) {
        int[] r = xdotoolRect(name);
        if (r != null) return new int[] {r[0], r[1]};
        return null;
    }

    private static int[] macWindowRect() {
        // First normal window of the java process (Minecraft renders in one).
        String script = "tell application \"System Events\" to get {position, size} "
                + "of first window of (first process whose name contains \"java\")";
        String out = runOut("osascript", "-e", script);
        if (out == null) return null;
        try {
            String[] nums = out.replaceAll("[^0-9, \\-]", "").split("[, ]+");
            List<Integer> v = new ArrayList<Integer>();
            for (String n : nums) {
                n = n.trim();
                if (!n.isEmpty()) v.add(Integer.parseInt(n));
            }
            if (v.size() >= 4 && v.get(2) > 0 && v.get(3) > 0) {
                return new int[] {v.get(0), v.get(1), v.get(2), v.get(3)};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Game window title for gdigrab {@code title=} capture (Windows).
     * Queries running windows via PowerShell; "" when unknown.
     */
    public static String gameWindowTitle() {
        if (FFmpeg.os() != FFmpeg.Os.WINDOWS) return "";
        String ps = "Get-Process | Where-Object {$_.MainWindowTitle -like '*Minecraft*'} "
                + "| Select-Object -First 1 -ExpandProperty MainWindowTitle";
        String out = runOut("powershell", "-NoProfile", "-Command", ps);
        if (out == null || out.isEmpty()) {
            out = runOut("powershell", "-NoProfile", "-Command",
                    "Get-Process | Where-Object {$_.MainWindowTitle -like '*java*'} "
                    + "| Select-Object -First 1 -ExpandProperty MainWindowTitle");
        }
        return out == null ? "" : out.trim();
    }

    /**
     * True on a Wayland session with no usable X (no xdotool target):
     * x11grab has nothing to grab there.
     */
    public static boolean isWaylandWithoutX() {
        if (FFmpeg.os() != FFmpeg.Os.LINUX) return false;
        try {
            String session = System.getenv("XDG_SESSION_TYPE");
            String wayland = System.getenv("WAYLAND_DISPLAY");
            boolean waylandSession = (session != null && session.toLowerCase().contains("wayland"))
                    || (wayland != null && !wayland.isEmpty()
                    && (System.getenv("DISPLAY") == null || System.getenv("DISPLAY").isEmpty()));
            if (!waylandSession) return false;
            // XWayland present? Then xdotool + x11grab still work.
            String ids = runOut("xdotool", "search", "--name", "Minecraft");
            return ids == null || ids.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Default game-audio tunnel device for this OS, or "".
     * Only virtual tunnels are offered (never raw desktop loops); empty
     * means "press Virtual first", not "type a monitor by hand". */
    public static String gameAudioDevice() {
        List<String> cands = VirtualAudio.gameTunnelCandidates();
        if (!cands.isEmpty()) return cands.get(0);
        return "";
    }

    /** Candidate audio devices for cycling in the GUI. */
    public static List<String> audioCandidates(boolean desktop) {
        List<String> out = new ArrayList<String>();
        if (desktop) {
            // Game tunnels only: raw desktop loopback is never offered.
            out.addAll(VirtualAudio.gameTunnelCandidates());
            return out;
        }
        switch (FFmpeg.os()) {
            case WINDOWS:
                out.addAll(dshowAudioDevices());
                if (out.isEmpty()) {
                    out.add("Microphone");
                }
                break;
            case MAC:
                out.add(":0");
                out.add(":1");
                break;
            default: {
                out.add("default");
                String list = runOut("pactl", "list", "short", "sources");
                if (list != null) {
                    for (String line : list.split("\n")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2 && !out.contains(parts[1])) out.add(parts[1]);
                    }
                }
                break;
            }
        }
        return out;
    }

    private static List<String> dshowAudioDevices() {
        List<String> out = new ArrayList<String>();
        Process p = null;
        try {
            String ffmpeg = FFmpeg.locate();
            if (ffmpeg == null || ffmpeg.isEmpty()) ffmpeg = "ffmpeg";
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

    /**
     * avfoundation video devices as {index, name} pairs, parsed from
     * ffmpeg's device list. Empty when ffmpeg is missing or the probe fails.
     */
    public static List<String[]> avfoundationVideoDevices() {
        List<String[]> out = new ArrayList<String[]>();
        Process p = null;
        try {
            String ffmpeg = FFmpeg.locate();
            if (ffmpeg == null || ffmpeg.isEmpty()) ffmpeg = "ffmpeg";
            p = new ProcessBuilder(ffmpeg, "-hide_banner", "-f", "avfoundation",
                    "-list_devices", "true", "-i", "").start();
            // Device list goes to stderr on ffmpeg.
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            boolean video = false;
            while ((line = br.readLine()) != null) {
                String low = line.toLowerCase();
                if (low.contains("avfoundation video devices")) video = true;
                else if (low.contains("avfoundation audio devices")) video = false;
                else if (video) {
                    int a = line.indexOf('[');
                    int b = line.indexOf(']', a + 1);
                    if (a >= 0 && b > a) {
                        String idx = line.substring(a + 1, b).trim();
                        String name = line.substring(b + 1).trim();
                        if (!idx.isEmpty() && !name.isEmpty()) {
                            out.add(new String[] {idx, name});
                        }
                    }
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

    /**
     * Best screen index for avfoundation capture: first video device whose
     * name looks like a screen, else the first video device, else null.
     */
    public static String macScreenIndexAuto() {
        List<String[]> devs = avfoundationVideoDevices();
        String first = null;
        for (String[] d : devs) {
            if (first == null) first = d[0];
            String n = d[1].toLowerCase();
            if (n.contains("display") || n.contains("screen") || n.contains("capture")) {
                return d[0];
            }
        }
        return first;
    }

    private static String runOut(String... cmd) {
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
