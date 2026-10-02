package com.obsnomore.stream;

import com.obsnomore.config.OverlayConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the ffmpeg stream/record sessions.
 *
 * <p>ffmpeg captures the Minecraft window itself (x11grab / gdigrab /
 * avfoundation) — Java never touches pixels, so there is no per-frame cost
 * on the game thread.
 *
 * <p>1-POV: the captured frame already contains HUD + overlays (WYSIWYG).
 * 2-POV: overlay widgets stay hidden locally while the stream gets game +
 * sources composited by ffmpeg filter_complex.
 */
public final class StreamManager {
    private StreamManager() {
    }

    private static Session stream;
    private static Session record;
    private static volatile String streamStatus = "stream: off";
    private static volatile String recordStatus = "record: off";
    private static volatile int recordPart = 1;
    private static volatile long streamStartedAt = 0;
    private static volatile long recordStartedAt = 0;

    public static boolean isStreaming() {
        return stream != null && stream.isRunning();
    }

    public static boolean isRecording() {
        return record != null && record.isRunning();
    }

    /** 2-POV: hide overlay widgets locally while the stream carries them. */
    public static boolean cleanLocal() {
        OverlayConfig cfg = OverlayConfig.get();
        return "2-POV".equals(cfg.pov_mode) && (isStreaming() || isRecording());
    }

    public static String streamStatus() {
        return streamStatus + (stream == null ? "" : " | " + stream.statusLine());
    }

    public static String recordStatus() {
        return recordStatus + (record == null ? "" : " | " + record.statusLine());
    }

    // ------------------------------------------------------------------
    // Capture input per OS (the Minecraft window region)
    // ------------------------------------------------------------------

    /** Capture input args for tests/other builders (no game binary prefix). */
    public static boolean buildCaptureForTest(List<String> a, OverlayConfig cfg) {
        int fps = 30;
        try {
            fps = cfg.streaming.fps > 0 ? cfg.streaming.fps : 30;
        } catch (Throwable ignored) {
        }
        return captureInput(a, cfg, fps);
    }

    /**
     * Appends game-capture input args.
     *
     * @return true when a game tunnel was wired. With {@code game_only} on
     * (default) only the game-window tunnel counts — a manual desktop
     * region is refused so streams/recordings can never silently capture
     * the whole desktop. Hand-set {@code game_only=false} in the config
     * file to allow the manual region.
     */
    static boolean captureInput(List<String> a, OverlayConfig cfg, int fps) {
        return captureInput(a, cfg, fps, new ArrayList<String>());
    }

    /** Same, but collects pre-scale video filters (e.g. macOS crop). */
    static boolean captureInput(List<String> a, OverlayConfig cfg, int fps,
                                List<String> videoFilters) {
        OverlayConfig.Capture c = cfg.capture;
        if (c == null) {
            c = new OverlayConfig.Capture();
            cfg.capture = c;
        }
        FFmpeg.Os os = FFmpeg.os();
        boolean gameOnly = c.game_only;
        if (c.mode == null || !"region".equals(c.mode)) {
            if (tryWindowCapture(a, cfg, c, fps, os, videoFilters)) return true;
            if (gameOnly) return false;
        }
        regionCapture(a, cfg, c, fps, os);
        return !gameOnly;
    }

    /** Joins pre-filters with the output scale into one -vf. */
    static void addScaledVf(List<String> a, List<String> pre, int outW, int outH) {
        StringBuilder sb = new StringBuilder();
        if (pre != null) {
            for (String f : pre) {
                if (f == null || f.isEmpty()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(f);
            }
        }
        if (sb.length() > 0) sb.append(',');
        sb.append("scale=").append(outW).append(':').append(outH).append(":flags=bilinear");
        a.add("-vf");
        a.add(sb.toString());
    }

    /**
     * Game-window capture per OS.
     * Windows: gdigrab by window title. Linux: x11grab over the live window
     * rect (works on X11 and XWayland). macOS: avfoundation screen capture
     * cropped to the window rect when it can be resolved.
     *
     * @return true when window args were appended.
     */
    static boolean tryWindowCapture(List<String> a, OverlayConfig cfg,
                                    OverlayConfig.Capture c, int fps, FFmpeg.Os os,
                                    List<String> videoFilters) {
        try {
            switch (os) {
                case WINDOWS: {
                    String title = c.title == null ? "" : c.title.trim();
                    if (title.isEmpty()) title = DeviceDetect.gameWindowTitle();
                    if (title == null || title.isEmpty()) return false;
                    a.add("-f");
                    a.add("gdigrab");
                    a.add("-framerate");
                    a.add(String.valueOf(fps));
                    a.add("-i");
                    a.add("title=" + title);
                    return true;
                }
                case MAC: {
                    int[] rect = DeviceDetect.gameWindowRect();
                    // Full-screen capture is NOT game-specific: a missing
                    // window rect fails the window path outright.
                    if (rect == null) return false;
                    c.w = rect[2];
                    c.h = rect[3];
                    a.add("-f");
                    a.add("avfoundation");
                    a.add("-framerate");
                    a.add(String.valueOf(fps));
                    a.add("-i");
                    a.add(macScreenIndex(c) + ":");
                    if (videoFilters != null) {
                        videoFilters.add("crop=" + rect[2] + ":" + rect[3]
                                + ":" + rect[0] + ":" + rect[1]);
                    }
                    return true;
                }
                default: {
                    // Pure Wayland without X has no xdotool/x11grab target;
                    // fail so the caller falls back to the manual region.
                    if (DeviceDetect.isWaylandWithoutX()) return false;
                    int[] rect = DeviceDetect.gameWindowRect();
                    if (rect == null) return false;
                    c.x = rect[0];
                    c.y = rect[1];
                    c.w = rect[2];
                    c.h = rect[3];
                    regionCapture(a, cfg, c, fps, os);
                    return true;
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** avfoundation video device index for the screen: configured, probed, else 1. */
    static String macScreenIndex(OverlayConfig.Capture c) {
        try {
            if (c != null && c.display != null && c.display.trim().matches("\\d+")) {
                return c.display.trim();
            }
        } catch (Throwable ignored) {
        }
        try {
            String auto = DeviceDetect.macScreenIndexAuto();
            if (auto != null && !auto.isEmpty()) {
                com.obsnomore.ObsLog.info("[OBSNoMore] avfoundation screen index: " + auto);
                return auto;
            }
        } catch (Throwable ignored) {
        }
        return "1";
    }

    static void regionCapture(List<String> a, OverlayConfig cfg,
                              OverlayConfig.Capture c, int fps, FFmpeg.Os os) {
        int w = c.w > 0 ? c.w : 854;
        int h = c.h > 0 ? c.h : 480;
        switch (FFmpeg.os()) {
            case WINDOWS:
                a.add("-f");
                a.add("gdigrab");
                a.add("-framerate");
                a.add(String.valueOf(fps));
                if (c.title != null && !c.title.trim().isEmpty()) {
                    a.add("-i");
                    a.add("title=" + c.title.trim());
                } else {
                    a.add("-offset_x");
                    a.add(String.valueOf(c.x));
                    a.add("-offset_y");
                    a.add(String.valueOf(c.y));
                    a.add("-video_size");
                    a.add(w + "x" + h);
                    a.add("-i");
                    a.add("desktop");
                }
                break;
            case MAC:
                a.add("-f");
                a.add("avfoundation");
                a.add("-framerate");
                a.add(String.valueOf(fps));
                a.add("-video_size");
                a.add(w + "x" + h);
                a.add("-i");
                a.add(macScreenIndex(c) + ":");
                break;
            case LINUX:
            default: {
                String disp = c.display == null || c.display.trim().isEmpty()
                        ? System.getenv("DISPLAY") : c.display.trim();
                if (disp == null || disp.isEmpty()) disp = ":0.0";
                a.add("-f");
                a.add("x11grab");
                a.add("-framerate");
                a.add(String.valueOf(fps));
                a.add("-video_size");
                a.add(w + "x" + h);
                if (c.follow_mouse) {
                    a.add("-follow_mouse");
                    a.add("centered");
                }
                a.add("-i");
                a.add(disp + "+" + c.x + "," + c.y);
                break;
            }
        }
        // Normalize output frame rate (drops/dups to exactly fps).
        a.add("-r");
        a.add(String.valueOf(fps));
    }

    // ------------------------------------------------------------------
    // Stream
    // ------------------------------------------------------------------

    public static synchronized String startStream() {
        if (isStreaming()) return "already streaming";
        OverlayConfig cfg = OverlayConfig.get();
        String ffmpeg = FFmpeg.locate();
        if (ffmpeg.isEmpty()) {
            streamStatus = "no ffmpeg (see Setup)";
            return streamStatus;
        }
        List<String> urls = new ArrayList<String>();
        boolean multi = cfg.streaming.multi_rtmp;
        for (OverlayConfig.RtmpSlot slot : cfg.streaming.slots) {
            if (slot == null || !slot.enabled) continue;
            String url = Presets.buildUrl(slot.preset, slot.url, slot.key);
            if (!url.isEmpty() && url.startsWith("rtmp")) urls.add(url);
            if (!multi && !urls.isEmpty()) break;
        }
        if (urls.isEmpty()) {
            streamStatus = "no RTMP servers enabled (see Setup > Stream)";
            return streamStatus;
        }
        Presets.Quality q = Presets.quality(cfg.streaming.quality);
        int fps = cfg.streaming.fps > 0 ? cfg.streaming.fps : q.fps;
        List<String> a = new ArrayList<String>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel");
        a.add("warning");
        a.add("-y");
        List<String> videoFilters = new ArrayList<String>();
        if (!captureInput(a, cfg, fps, videoFilters)) {
            streamStatus = "game window not found (game_only: fix window capture or set game_only=false)";
            return streamStatus;
        }
        boolean desktopAllowed = desktopTunnelAllowed(cfg);
        String audioNote = desktopSkippedNote(cfg);
        FilterGraph.Graph g = null;
        boolean twoPov = "2-POV".equals(cfg.pov_mode);
        if (twoPov) {
            g = FilterGraph.build(cfg, cfg.capture.w, cfg.capture.h, q.w, q.h, videoFilters);
            a.addAll(g.extraInputs);
            dumpSceneText(cfg);
        }
        int videoExtras = countInputs(g == null ? null : g.extraInputs);
        List<Integer> audioIdx = new ArrayList<Integer>();
        int audioStart = 1 + videoExtras;
        int audioInputs = appendAudioReturningIndices(a, cfg, audioStart, audioIdx, desktopAllowed);
        if (g != null && g.filter != null) {
            a.add("-filter_complex");
            a.add(g.filter);
            a.add("-map");
            a.add("[sFinal]");
            for (int idx : audioIdx) {
                a.add("-map");
                a.add(idx + ":a?");
            }
        } else {
            addScaledVf(a, videoFilters, q.w, q.h);
        }
        Session.videoOut(a, cfg.streaming.encoder, cfg.streaming.x264_preset,
                cfg.streaming.video_bitrate_k > 0 ? cfg.streaming.video_bitrate_k : q.bitrateK,
                false);
        Session.audioOut(a, audioInputs > 0);
        Session.teeOutputs(a, urls);
        stream = new Session(Session.Kind.STREAM, a);
        final List<String> urlsF = urls;
        stream.setListener(new Session.Listener() {
            @Override
            public void onState(String line) {
                streamStatus = line + " -> " + urlsF.size() + " server(s)";
            }
        });
        streamStatus = "connecting...";
        if (!stream.start()) {
            String s = streamStatus;
            stream = null;
            streamStartedAt = 0;
            return s;
        }
        streamStartedAt = System.currentTimeMillis();
        return "streaming to " + urls.size() + " server(s)" + audioNote;
    }

    public static synchronized void stopStream() {
        if (stream != null) {
            stream.stop();
            stream = null;
        }
        streamStatus = "stream: off";
        streamStartedAt = 0;
    }

    /** ms since the current stream started, 0 when idle. */
    public static long streamElapsed() {
        if (!isStreaming() || streamStartedAt <= 0) return 0;
        return Math.max(0, System.currentTimeMillis() - streamStartedAt);
    }

    /** ms since the current recording part started, 0 when idle. */
    public static long recordElapsed() {
        if (!isRecording() || recordStartedAt <= 0) return 0;
        return Math.max(0, System.currentTimeMillis() - recordStartedAt);
    }

    // ------------------------------------------------------------------
    // Record (pause = stop + resume into a new part file)
    // ------------------------------------------------------------------

    public static synchronized String startRecord() {
        if (isRecording()) return "already recording";
        OverlayConfig cfg = OverlayConfig.get();
        String ffmpeg = FFmpeg.locate();
        if (ffmpeg.isEmpty()) {
            recordStatus = "no ffmpeg (see Setup)";
            return recordStatus;
        }
        Presets.Quality q = Presets.quality(cfg.recording.quality);
        int fps = cfg.streaming.fps > 0 ? cfg.streaming.fps : q.fps;
        File out = recordFile(cfg, recordPart);
        if (out == null) {
            recordStatus = "bad record path";
            return recordStatus;
        }
        List<String> a = new ArrayList<String>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel");
        a.add("warning");
        a.add("-y");
        List<String> videoFilters = new ArrayList<String>();
        if (!captureInput(a, cfg, fps, videoFilters)) {
            recordStatus = "game window not found (game_only: fix window capture or set game_only=false)";
            return recordStatus;
        }
        boolean desktopAllowed = desktopTunnelAllowed(cfg);
        String audioNote = desktopSkippedNote(cfg);
        boolean twoPov = "2-POV".equals(cfg.pov_mode);
        FilterGraph.Graph g = null;
        if (twoPov) {
            g = FilterGraph.build(cfg, cfg.capture.w, cfg.capture.h, q.w, q.h, videoFilters);
            a.addAll(g.extraInputs);
            dumpSceneText(cfg);
        }
        int videoExtras = countInputs(g == null ? null : g.extraInputs);
        List<Integer> audioIdx = new ArrayList<Integer>();
        int audioStart = 1 + videoExtras;
        int audioInputs = appendAudioReturningIndices(a, cfg, audioStart, audioIdx, desktopAllowed);
        if (g != null && g.filter != null) {
            a.add("-filter_complex");
            a.add(g.filter);
            a.add("-map");
            a.add("[sFinal]");
            for (int idx : audioIdx) {
                a.add("-map");
                a.add(idx + ":a?");
            }
        } else {
            addScaledVf(a, videoFilters, q.w, q.h);
        }
        String container = cfg.recording.container;
        if (container == null || container.isEmpty()) container = "mp4";
        int bitrate = cfg.streaming.video_bitrate_k > 0 ? cfg.streaming.video_bitrate_k : q.bitrateK;
        Session.videoOut(a, cfg.recording.encoder, cfg.streaming.x264_preset,
                bitrate, container.equals("mp4"));
        Session.audioOut(a, audioInputs > 0);
        a.add(out.getAbsolutePath());
        record = new Session(Session.Kind.RECORD, a);
        record.setCurrentFile(out);
        final File outF = out;
        record.setListener(new Session.Listener() {
            @Override
            public void onState(String line) {
                recordStatus = line + " " + outF.getName();
            }
        });
        recordStatus = "recording...";
        if (!record.start()) {
            String s = recordStatus;
            record = null;
            recordStartedAt = 0;
            return s;
        }
        recordStartedAt = System.currentTimeMillis();
        return "recording " + out.getName() + audioNote;
    }

    public static synchronized void stopRecord() {
        if (record != null) {
            record.stop();
            record = null;
        }
        recordStatus = "record: off";
        recordStartedAt = 0;
    }

    public static synchronized String pauseRecord() {
        if (!isRecording()) return startRecord();
        stopRecord();
        recordPart++;
        return startRecord();
    }

    private static int countInputs(List<String> extra) {
        if (extra == null) return 0;
        int n = 0;
        for (String s : extra) {
            if ("-i".equals(s)) n++;
        }
        return n;
    }

    /** Appends mic/desktop inputs, recording their indices for -map. */
    private static int appendAudioReturningIndices(List<String> a, OverlayConfig cfg,
                                                   int startIdx, List<Integer> idx,
                                                   boolean desktopAllowed) {
        int n = FFmpeg.appendAudioInputs(a, cfg.streaming, desktopAllowed);
        for (int k = 0; k < n; k++) idx.add(startIdx + k);
        return n;
    }

    /**
     * Game-only audio gate: with {@code game_only} on, the desktop input
     * is wired only when it names a virtual game tunnel. Mic is always
     * allowed (already device-specific).
     */
    static boolean desktopTunnelAllowed(OverlayConfig cfg) {
        try {
            if (cfg == null || cfg.capture == null || !cfg.capture.game_only) return true;
            if (cfg.streaming == null || cfg.streaming.desktop == null) return true;
            if (!cfg.streaming.desktop.enabled) return true;
            String dev = cfg.streaming.desktop.device;
            if (dev == null || dev.trim().isEmpty()) return false;
            return VirtualAudio.isGameTunnel(dev);
        } catch (Throwable t) {
            return true;
        }
    }

    /** User-facing note when the desktop input was skipped, else "". */
    static String desktopSkippedNote(OverlayConfig cfg) {
        try {
            if (!desktopTunnelAllowed(cfg)
                    && cfg.streaming.desktop.enabled) {
                return "; desktop audio skipped (not a game tunnel — see wiki Audio-Setup)";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static File recordFile(OverlayConfig cfg, int part) {        try {
            String rel = cfg.recording.path;
            if (rel == null || rel.trim().isEmpty()) rel = "obsnomore/capture.mp4";
            rel = rel.trim();
            if (part > 1) {
                int dot = rel.lastIndexOf('.');
                if (dot > 0) rel = rel.substring(0, dot) + "_part" + part + rel.substring(dot);
                else rel = rel + "_part" + part;
            }
            File f = new File(rel);
            if (!f.isAbsolute()) {
                f = FabricLoader.getInstance().getGameDir().resolve(rel).toFile();
            }
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------

    /** Refresh browser-source drawtext files for ffmpeg (2-POV). */
    public static void dumpSceneText(OverlayConfig cfg) {
        try {
            OverlayConfig.Scene scene = cfg.activeScene();
            if (scene == null || scene.sources == null) return;
            for (OverlayConfig.Source s : scene.sources) {
                if (s == null || !s.enabled || !"browser".equals(s.type)) continue;
                List<String> lines = com.obsnomore.data.WebFetcher.get()
                        .getLines(s.url, Math.max(1, Math.min(25, s.max_messages)));
                FilterGraph.dumpBrowserLines(s, lines);
            }
        } catch (Throwable ignored) {
        }
    }
}
