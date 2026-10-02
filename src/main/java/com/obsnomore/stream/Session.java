package com.obsnomore.stream;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * One ffmpeg child process. ffmpeg captures the game window itself
 * (x11grab / gdigrab / avfoundation), so Java never touches pixels:
 * no stdin pipe, no per-frame work on any thread.
 */
public class Session {
    public enum Kind { STREAM, RECORD }

    public interface Listener {
        void onState(String line);
    }

    private final Kind kind;
    private final List<String> args;
    private volatile Process process;
    private volatile boolean running;
    private volatile String status = "idle";
    private volatile long startedAt;
    private Thread watchThread;
    private Listener listener;
    private volatile File currentFile;

    public Session(Kind kind, List<String> args) {
        this.kind = kind;
        this.args = args;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setCurrentFile(File f) {
        this.currentFile = f;
    }

    public synchronized boolean start() {
        if (running) return true;
        try {
            logCommand();
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(false);
            process = pb.start();
            running = true;
            startedAt = System.currentTimeMillis();
            status = "running";
            say(kind + " started");
            watchThread = new Thread(this::watchLoop, "OBSNoMore-watch-" + kind);
            watchThread.setDaemon(true);
            watchThread.start();
            return true;
        } catch (Throwable t) {
            status = "failed: " + t.getMessage();
            say(status);
            running = false;
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            if (process != null) {
                OutputStreamCloser.closeStdin(process);
                process.destroy();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (process != null) process.waitFor();
        } catch (Throwable ignored) {
        }
        status = "stopped";
        say(status);
    }

    public boolean isRunning() {
        try {
            if (!running || process == null) return false;
            process.exitValue();
            running = false;
            return false;
        } catch (IllegalThreadStateException alive) {
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public String statusLine() {
        long secs = running ? (System.currentTimeMillis() - startedAt) / 1000 : 0;
        String extra = "";
        if (kind == Kind.RECORD && currentFile != null) extra = " " + currentFile.getName();
        return (running ? "ON " : "OFF ") + secs + "s" + extra;
    }

    private void watchLoop() {
        java.io.BufferedReader err = null;
        try {
            err = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream()));
            String line;
            java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<String>();
            while ((line = err.readLine()) != null) {
                tail.addLast(line);
                while (tail.size() > 8) tail.removeFirst();
                if (line.contains("frame=") || line.contains("bitrate=")) {
                    status = shortProgress(line);
                    say(status);
                }
            }
            int code = process.waitFor();
            running = false;
            StringBuilder detail = new StringBuilder();
            for (String t : tail) {
                t = t.trim().replaceAll("\\s+", " ");
                if (t.isEmpty() || t.startsWith("frame=")) continue;
                if (detail.length() > 0) detail.append(" | ");
                detail.append(t);
                if (detail.length() > 220) break;
            }
            if (detail.length() > 220) detail.setLength(220);
            status = "exited(" + code + ") " + detail.toString().trim();
            say(status);
            if (code != 0) {
                com.obsnomore.ObsLog.info("[OBSNoMore] session " + kind + " exited(" + code + "): "
                        + detail.toString().trim());
            }
        } catch (Throwable t) {
            running = false;
            status = "error: " + t.getMessage();
            say(status);
        } finally {
            try {
                if (err != null) err.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String shortProgress(String line) {
        if (line == null) return "";
        line = line.trim().replaceAll("\\s+", " ");
        if (line.length() > 90) line = line.substring(line.length() - 90);
        return line;
    }

    private void say(String line) {
        try {
            if (listener != null) listener.onState(line);
        } catch (Throwable ignored) {
        }
    }

    /** Full ffmpeg command, logged once per session for debugging. */
    private void logCommand() {
        try {
            StringBuilder sb = new StringBuilder("[OBSNoMore] ffmpeg " + kind + ":");
            for (String a : args) {
                sb.append(' ');
                if (a != null && (a.contains(" ") || a.contains("\""))) {
                    sb.append('"').append(a.replace("\"", "\\\"")).append('"');
                } else {
                    sb.append(a);
                }
            }
            com.obsnomore.ObsLog.info(sb.toString());
        } catch (Throwable ignored) {
        }
    }

    /** Best-effort stdin close so ffmpeg finalizes promptly on stop. */
    private static final class OutputStreamCloser {
        static void closeStdin(Process p) {
            try {
                p.getOutputStream().close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Command builders
    // ------------------------------------------------------------------

    public static void videoOut(List<String> a, String encoder, String x264Preset,
                                int bitrateK, boolean faststart) {
        String enc = encoder == null || encoder.isEmpty() ? "libx264" : encoder;
        a.add("-c:v");
        a.add(enc);
        if (enc.equals("libx264")) {
            a.add("-preset");
            a.add(x264Preset == null || x264Preset.isEmpty() ? "veryfast" : x264Preset);
            a.add("-tune");
            a.add("zerolatency");
        } else if (enc.equals("h264_nvenc") || enc.equals("h264_amf")) {
            a.add("-preset");
            a.add("p4");
        }
        a.add("-b:v");
        a.add(bitrateK + "k");
        a.add("-maxrate");
        a.add((int) (bitrateK * 1.2) + "k");
        a.add("-bufsize");
        a.add((bitrateK * 2) + "k");
        a.add("-pix_fmt");
        a.add("yuv420p");
        a.add("-g");
        a.add("60");
        if (faststart) {
            a.add("-movflags");
            a.add("+faststart");
        }
    }

    public static void audioOut(List<String> a, boolean hasAudio) {
        if (hasAudio) {
            a.add("-c:a");
            a.add("aac");
            a.add("-b:a");
            a.add("160k");
            a.add("-ar");
            a.add("48000");
            a.add("-ac");
            a.add("2");
        }
    }

    /** Stream outputs via the tee muxer (one encode, N servers). */
    public static void teeOutputs(List<String> a, List<String> rtmpUrls) {
        if (rtmpUrls.isEmpty()) return;
        if (rtmpUrls.size() == 1) {
            a.add("-f");
            a.add("flv");
            a.add(rtmpUrls.get(0));
            return;
        }
        StringBuilder tee = new StringBuilder();
        for (int i = 0; i < rtmpUrls.size(); i++) {
            if (i > 0) tee.append('|');
            tee.append("[f=flv]").append(rtmpUrls.get(i));
        }
        a.add("-f");
        a.add("tee");
        a.add(tee.toString());
    }
}
