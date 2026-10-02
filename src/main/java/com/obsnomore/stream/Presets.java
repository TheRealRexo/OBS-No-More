package com.obsnomore.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RTMP presets (server + key), quality presets and encoder choices.
 */
public final class Presets {
    private Presets() {
    }

    public static final class RtmpPreset {
        public final String id;
        public final String name;
        public final String url;
        public final boolean needsKeyOnly;

        public RtmpPreset(String id, String name, String url, boolean needsKeyOnly) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.needsKeyOnly = needsKeyOnly;
        }
    }

    public static List<RtmpPreset> rtmpPresets() {
        List<RtmpPreset> l = new ArrayList<RtmpPreset>();
        l.add(new RtmpPreset("twitch", "Twitch", "rtmp://live.twitch.tv/app/", true));
        l.add(new RtmpPreset("youtube", "YouTube", "rtmp://a.rtmp.youtube.com/live2/", true));
        l.add(new RtmpPreset("kick", "Kick", "rtmp://fa723fc1b171.global-contribute.live-video.net/app/", true));
        l.add(new RtmpPreset("custom", "Custom", "", false));
        return l;
    }

    public static String presetUrl(String presetId) {
        for (RtmpPreset p : rtmpPresets()) {
            if (p.id.equals(presetId)) return p.url;
        }
        return "";
    }

    public static String presetName(String presetId) {
        for (RtmpPreset p : rtmpPresets()) {
            if (p.id.equals(presetId)) return p.name;
        }
        return presetId;
    }

    /** server + "/" + key (OBS convention). */
    public static String buildUrl(String presetId, String customUrl, String key) {
        String base = "custom".equals(presetId) ? customUrl : presetUrl(presetId);
        if (base == null) base = "";
        base = base.trim();
        String k = key == null ? "" : key.trim();
        if (!k.isEmpty() && !base.endsWith("/")) base += "/";
        return base + k;
    }

    // ------------------------------------------------------------------

    public static final class Quality {
        public final String id;
        public final String label;
        public final int w;
        public final int h;
        public final int fps;
        public final int bitrateK;

        public Quality(String id, String label, int w, int h, int fps, int bitrateK) {
            this.id = id;
            this.label = label;
            this.w = w;
            this.h = h;
            this.fps = fps;
            this.bitrateK = bitrateK;
        }
    }

    public static List<Quality> qualities() {
        List<Quality> l = new ArrayList<Quality>();
        l.add(new Quality("720p30", "720p30 · 6M", 1280, 720, 30, 6000));
        l.add(new Quality("1080p30", "1080p30 · 8M", 1920, 1080, 30, 8000));
        l.add(new Quality("1080p60", "1080p60 · 12M", 1920, 1080, 60, 12000));
        return l;
    }

    public static Quality quality(String id) {
        for (Quality q : qualities()) {
            if (q.id.equals(id)) return q;
        }
        return qualities().get(1);
    }

    /** Highest preset whose bitrate fits in measuredKbps * headroom. */
    public static Quality recommend(double measuredKbps) {
        Quality best = qualities().get(0);
        for (Quality q : qualities()) {
            if (q.bitrateK <= measuredKbps * 0.7) best = q;
        }
        return best;
    }

    // ------------------------------------------------------------------

    public static Map<String, String> encoders() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("libx264", "x264 (CPU, always available)");
        m.put("h264_nvenc", "NVENC (NVIDIA GPU)");
        m.put("h264_amf", "AMF (AMD GPU)");
        m.put("h264_vaapi", "VAAPI (Linux GPU)");
        return m;
    }

    public static List<String> containers() {
        List<String> l = new ArrayList<String>();
        l.add("mp4");
        l.add("mkv");
        l.add("mov");
        l.add("flv");
        return l;
    }
}
