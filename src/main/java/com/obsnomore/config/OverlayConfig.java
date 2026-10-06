package com.obsnomore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OBS No More configuration ({@code config/obsnomore.json}).
 *
 * <p>Scene model (OBS-like, but deliberately limited: no display capture, no
 * window capture — other programs can't be pulled in. Accepted inputs are the
 * Minecraft view itself plus generic overlay sources):
 * <ul>
 *   <li>{@code game} — the Minecraft view (always the base layer).</li>
 *   <li>{@code browser} — web content (chat endpoints, counters, widgets).</li>
 *   <li>{@code camera} — video input device (USB HDMI etc.).</li>
 *   <li>{@code text} — static label.</li>
 *   <li>{@code image} — PNG overlay.</li>
 * </ul>
 *
 * <p>POV modes: {@code 1-POV} shows viewers exactly what the streamer sees
 * (overlays baked in). {@code 2-POV} keeps the streamer's game clean while
 * viewers get game + sources composited by ffmpeg.
 */
public class OverlayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static OverlayConfig INSTANCE;

    public String pov_mode = "1-POV";
    public int active_scene = 0;
    public List<Scene> scenes = new ArrayList<Scene>();
    public Streaming streaming = new Streaming();
    public Recording recording = new Recording();
    public Browser browser = new Browser();
    public String ffmpeg_path = "";
    /** Manual Chromium override (blank = auto-detect). Set in Setup Deps. */
    public String chromium_path = "";
    /** Manual Electron override (blank = auto-detect). Set in Setup Deps. */
    public String electron_path = "";
    /** First-run wizard completed. */
    public boolean setup_done = false;
    /**
     * Pre-dock hidden-source rescue already ran once. The rescue must never
     * re-run: it would yank user-placed sources back every time the editor
     * opens, making moves look unsaved.
     */
    public boolean layout_rescued = false;

    /** Schema version for one-time migrations of loaded configs. */
    public int schema = 0;

    /** Discrete hotkeys (LWJGL key codes), rebindable in the GUI. */
    public Map<String, Integer> hotkeys = defaultHotkeys();

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    public static class Scene {
        public String name = "Main";
        public List<Source> sources = new ArrayList<Source>();
    }

    public static class Source {
        public String id = UUID.randomUUID().toString();
        public String type = "text";
        public String name = "Source";
        public boolean enabled = true;
        public boolean locked = false;
        public int x = 10;
        public int y = 180;
        public float scale = 1.0f;
        /** Non-uniform free resize multipliers (Shift + corner/edge drag). */
        public float stretch_x = 1.0f;
        public float stretch_y = 1.0f;
        // browser (max lines + feed URL)
        public int max_messages = 8;
        public String url = "";
        // image crops
        public int crop_top_px = 0;
        public int crop_bottom_px = 0;
        public int crop_left_px = 0;
        public int crop_right_px = 0;
        // camera
        public String device = "";
        public int cam_w = 640;
        public int cam_h = 480;
        public int cam_fps = 30;
        // text
        public String text = "Hello stream!";
        public int color = 0xFFFFFFFF;
        // image
        public String path = "";
    }

    public static class RtmpSlot {
        /** twitch | youtube | kick | custom */
        public String preset = "custom";
        /** full base URL for custom, or preset template override */
        public String url = "";
        public String key = "";
        /** false = skipped when streaming (lets you keep keys without using them) */
        public boolean enabled = true;
    }

    public static class AudioIn {
        public boolean enabled = false;
        public String device = "";
        public double gain = 1.0;
    }

    public static class Streaming {
        public List<RtmpSlot> slots = defaultSlots();
        /** false = stream to the first enabled slot only (single-RTMP mode) */
        public boolean multi_rtmp = true;
        public String encoder = "libx264";
        public String quality = "1080p30";
        public int video_bitrate_k = 8000;
        public int fps = 30;
        public String x264_preset = "veryfast";
        public String transition = "Cut";
        public int transition_ms = 300;
        public AudioIn mic = new AudioIn();
        public AudioIn desktop = new AudioIn();
    }

    public static class Recording {
        /** gameDir-relative output, e.g. obsnomore/capture.mp4 */
        public String path = "obsnomore/capture.mp4";
        public String container = "mp4";
        public String encoder = "libx264";
        public String quality = "1080p30";
    }

    /** Browser-source page renderer choice. */
    public static class Browser {
        /** auto | chromium | electron (auto prefers Electron, falls back) */
        public String renderer = "auto";
    }

    /** Window-capture source (the Minecraft window region). */
    public static class Capture {
        /**
         * Game-only capture (default true): video must resolve through the
         * game-window tunnel and desktop audio must be a virtual game tunnel.
         * Anything else is skipped at session start. Set false by hand in
         * the config file to allow manual regions / raw loopback devices.
         * This flag is config-file-only on purpose (never shown in the GUI).
         */
        public boolean game_only = true;
        /**
         * "window" (default) captures the game window itself per OS
         * (gdigrab title / x11grab window rect / avfoundation + crop),
         * falling back to "region" below when it can't be resolved.
         */
        public String mode = "window";
        /** X display / screen selector, e.g. ":0.0" (Linux). */
        public String display = "";
        public int x = 0;
        public int y = 0;
        public int w = 854;
        public int h = 480;
        public int fps = 30;
        public boolean follow_mouse = false;
        /** Windows gdigrab window title override (empty = desktop region). */
        public String title = "";
    }

    public Capture capture = new Capture();

    // ------------------------------------------------------------------
    // Hotkey defaults (LWJGL2 codes, discrete key per action)
    // ------------------------------------------------------------------

    public static final String HK_START_STREAM = "start_stream";
    public static final String HK_STOP_STREAM = "stop_stream";
    public static final String HK_START_RECORD = "start_record";
    public static final String HK_STOP_RECORD = "stop_record";
    public static final String HK_PAUSE_RECORD = "pause_record";
    public static final String HK_NEXT_SCENE = "next_scene";
    public static final String HK_PREV_SCENE = "prev_scene";

    public static Map<String, Integer> defaultHotkeys() {
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put(HK_START_STREAM, 64); // F6
        m.put(HK_STOP_STREAM, 65); // F7
        m.put(HK_START_RECORD, 66); // F8
        m.put(HK_STOP_RECORD, 67); // F9
        m.put(HK_PAUSE_RECORD, 68); // F10
        m.put(HK_NEXT_SCENE, 88); // F12 (F11 is vanilla fullscreen)
        m.put(HK_PREV_SCENE, 210); // Insert
        return m;
    }

    public static List<RtmpSlot> defaultSlots() {
        List<RtmpSlot> l = new ArrayList<RtmpSlot>();
        l.add(new RtmpSlot());
        l.add(new RtmpSlot());
        l.add(new RtmpSlot());
        return l;
    }

    public int hotkey(String action) {
        if (hotkeys == null) hotkeys = defaultHotkeys();
        Integer v = hotkeys.get(action);
        if (v == null) {
            v = defaultHotkeys().get(action);
            if (v == null) v = 0;
            hotkeys.put(action, v);
        }
        return v;
    }

    // ------------------------------------------------------------------
    // Scenes
    // ------------------------------------------------------------------

    public Scene activeScene() {
        if (scenes == null || scenes.isEmpty()) ensureDefaults();
        if (active_scene < 0 || active_scene >= scenes.size()) active_scene = 0;
        return scenes.get(active_scene);
    }

    public void cycleScene(int dir) {
        if (scenes == null || scenes.isEmpty()) ensureDefaults();
        active_scene = (active_scene + dir + scenes.size()) % scenes.size();
        save();
    }

    private void ensureDefaults() {
        if (scenes == null) scenes = new ArrayList<Scene>();
        if (scenes.isEmpty()) {
            // Generic OBS-style start: one empty scene plus a welcome label
            // so the preview shows something on first open.
            Scene main = new Scene();
            main.name = "Main";
            Source hello = newSource("text");
            hello.name = "Welcome";
            hello.text = "Welcome! Open the OBS menu to edit.";
            main.sources.add(hello);
            scenes.add(main);
        }
        if (streaming == null) streaming = new Streaming();
        if (browser == null) browser = new Browser();
        if (browser.renderer == null || (!browser.renderer.equals("chromium")
                && !browser.renderer.equals("electron"))) {
            browser.renderer = "auto";
        }
        if (capture == null) capture = new Capture();
        if (capture.mode == null
                || (!capture.mode.equals("window") && !capture.mode.equals("region"))) {
            capture.mode = "window";
        }
        if (streaming.slots == null || streaming.slots.isEmpty()) {
            streaming.slots = defaultSlots();
        }
        // Drop null slot entries left by hand edits.
        for (int i = streaming.slots.size() - 1; i >= 0; i--) {
            if (streaming.slots.get(i) == null) streaming.slots.remove(i);
        }
        if (streaming.slots.isEmpty()) streaming.slots = defaultSlots();
        if (streaming.mic == null) streaming.mic = new AudioIn();
        if (streaming.desktop == null) streaming.desktop = new AudioIn();
        if (recording == null) recording = new Recording();
        if (hotkeys == null || hotkeys.isEmpty()) hotkeys = defaultHotkeys();
        for (Map.Entry<String, Integer> e : defaultHotkeys().entrySet()) {
            if (!hotkeys.containsKey(e.getKey())) hotkeys.put(e.getKey(), e.getValue());
        }
        // Migrate scene keys off vanilla-reserved F11 (fullscreen toggle).
        if (Integer.valueOf(87).equals(hotkeys.get(HK_NEXT_SCENE))) {
            hotkeys.put(HK_NEXT_SCENE, 88);
        }
        // v2: per-slot enable flags + single/multi-RTMP switch. Configs
        // written before these existed stream everything, so migrate to
        // all-enabled + multi on, then never touch user choices again.
        if (schema < 2) {
            if (streaming.slots != null) {
                for (RtmpSlot s : streaming.slots) {
                    if (s != null) s.enabled = true;
                }
            }
            streaming.multi_rtmp = true;
            schema = 2;
        }
        // v3: chat/pulsoid source types are gone, replaced by the generic
        // browser source. Convert survivors that carry a URL, drop the rest.
        if (schema < 3) {
            if (scenes != null) {
                for (Scene scene : scenes) {
                    if (scene == null || scene.sources == null) continue;
                    List<Source> kept = new ArrayList<Source>();
                    for (Source s : scene.sources) {
                        if (s == null) continue;
                        String t = s.type == null ? "" : s.type;
                        if (!t.equals("chat") && !t.equals("pulsoid")) {
                            kept.add(s);
                            continue;
                        }
                        String url = s.url == null ? "" : s.url.trim();
                        if (url.isEmpty()) continue; // demo/sim placeholders: drop
                        Source b = newSource("browser");
                        b.name = (s.name == null || s.name.isEmpty()) ? "Browser" : s.name;
                        b.enabled = s.enabled;
                        b.x = s.x;
                        b.y = s.y;
                        b.scale = s.scale;
                        b.stretch_x = s.stretch_x;
                        b.stretch_y = s.stretch_y;
                        b.max_messages = s.max_messages;
                        b.url = url;
                        kept.add(b);
                    }
                    scene.sources = kept;
                }
            }
            schema = 3;
        }
        // v4: game-only tunnels by default (window video + virtual audio).
        // Missing in older files (loads as false), so opt them in; owners
        // can still hand-set game_only=false afterwards and it sticks.
        if (schema < 4) {
            if (capture != null) capture.game_only = true;
            schema = 4;
        }
        if (ffmpeg_path == null) ffmpeg_path = "";
        if (pov_mode == null
                || (!pov_mode.equals("1-POV") && !pov_mode.equals("2-POV"))) {
            pov_mode = "1-POV";
        }
    }

    public static Source newSource(String type) {
        Source s = new Source();
        s.type = type;
        if (type.equals("browser")) {
            s.name = "Browser";
            s.x = 10;
            s.y = 40;
            s.max_messages = 8;
            s.url = "";
        } else if (type.equals("camera")) {
            s.name = "Cam";
            s.x = 300;
            s.y = 20;
            s.device = "";
        } else if (type.equals("text")) {
            s.name = "Label";
            s.x = 10;
            s.y = 20;
            s.text = "Hello stream!";
        } else if (type.equals("image")) {
            s.name = "Logo";
            s.x = 10;
            s.y = 20;
            s.path = "";
            s.crop_bottom_px = 0;
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Load / save (+ v1 migration from stream_overlay.json)
    // ------------------------------------------------------------------

    public static synchronized OverlayConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static File configFile() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir();
            return dir.resolve("obsnomore.json").toFile();
        } catch (Throwable t) {
            File dir = new File("config");
            dir.mkdirs();
            return new File(dir, "obsnomore.json");
        }
    }

    public static File legacyFile() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir();
            return dir.resolve("stream_overlay.json").toFile();
        } catch (Throwable t) {
            return new File(new File("config"), "stream_overlay.json");
        }
    }

    public static synchronized OverlayConfig load() {
        File f = configFile();
        if (f.exists()) {
            try (Reader r = new FileReader(f)) {
                OverlayConfig cfg =
                        GSON.fromJson(r, OverlayConfig.class);
                if (cfg == null) cfg = new OverlayConfig();
                INSTANCE = cfg;
                cfg.ensureDefaults();
                cfg.save();
                return cfg;
            } catch (Exception e) {
                System.err.println("[OBSNoMore] Failed to read config, using defaults: " + e);
            }
        }
        OverlayConfig cfg = migrateLegacy();
        if (cfg == null) cfg = new OverlayConfig();
        INSTANCE = cfg;
        cfg.ensureDefaults();
        cfg.save();
        return cfg;
    }

    /** One-time import of the v1 flat schema into scene "Main". */
    private static OverlayConfig migrateLegacy() {
        File f = legacyFile();
        if (!f.exists()) return null;
        try (Reader r = new FileReader(f)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            OverlayConfig cfg = new OverlayConfig();
            String chatUrl = root.has("socialninja_url")
                    ? root.get("socialninja_url").getAsString() : "";
            String heartUrl = root.has("pulsoid_url")
                    ? root.get("pulsoid_url").getAsString() : "";
            Scene main = new Scene();
            main.name = "Main";
            if (root.has("chat") && root.get("chat").isJsonObject()) {
                JsonObject c = root.getAsJsonObject("chat");
                Source feed = newSource("browser");
                feed.name = "Chat";
                feed.url = chatUrl == null ? "" : chatUrl;
                if (c.has("enabled")) feed.enabled = c.get("enabled").getAsBoolean();
                if (c.has("x")) feed.x = c.get("x").getAsInt();
                if (c.has("y")) feed.y = c.get("y").getAsInt();
                if (c.has("scale")) feed.scale = c.get("scale").getAsFloat();
                if (c.has("max_messages")) feed.max_messages = c.get("max_messages").getAsInt();
                if (feed.url != null && !feed.url.isEmpty()) main.sources.add(feed);
            }
            if (root.has("pulsoid") && root.get("pulsoid").isJsonObject()) {
                JsonObject p = root.getAsJsonObject("pulsoid");
                Source feed = newSource("browser");
                feed.name = "Heart rate";
                feed.url = heartUrl == null ? "" : heartUrl;
                if (p.has("enabled")) feed.enabled = p.get("enabled").getAsBoolean();
                if (p.has("x")) feed.x = p.get("x").getAsInt();
                if (p.has("y")) feed.y = p.get("y").getAsInt();
                if (p.has("scale")) feed.scale = p.get("scale").getAsFloat();
                if (feed.url != null && !feed.url.isEmpty()) main.sources.add(feed);
            }
            cfg.scenes.add(main);
            com.obsnomore.ObsLog.info("[OBSNoMore] Migrated legacy stream_overlay.json");
            return cfg;
        } catch (Exception e) {
            System.err.println("[OBSNoMore] Legacy migration failed: " + e);
            return null;
        }
    }

    public synchronized void save() {
        ensureDefaults();
        // Clamp dynamic ranges.
        for (Scene scene : scenes) {
            if (scene.sources == null) scene.sources = new ArrayList<Source>();
            for (Source s : scene.sources) {
                if (s.scale < 0.25f) s.scale = 0.25f;
                if (s.scale > 4.0f) s.scale = 4.0f;
                if (s.stretch_x <= 0.0f) s.stretch_x = 1.0f;
                if (s.stretch_y <= 0.0f) s.stretch_y = 1.0f;
                if (s.stretch_x < 0.25f) s.stretch_x = 0.25f;
                if (s.stretch_x > 4.0f) s.stretch_x = 4.0f;
                if (s.stretch_y < 0.25f) s.stretch_y = 0.25f;
                if (s.stretch_y > 4.0f) s.stretch_y = 4.0f;
                s.crop_top_px = clampCrop(s.crop_top_px);
                s.crop_bottom_px = clampCrop(s.crop_bottom_px);
                s.crop_left_px = clampCrop(s.crop_left_px);
                s.crop_right_px = clampCrop(s.crop_right_px);
                if (s.max_messages < 1) s.max_messages = 1;
                if (s.max_messages > 25) s.max_messages = 25;
                if (s.name == null) s.name = s.type;
                if (s.id == null) s.id = UUID.randomUUID().toString();
            }
        }
        File f = configFile();
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (Writer w = new FileWriter(f)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            System.err.println("[OBSNoMore] Failed to save config: " + e);
        }
    }

    private static int clampCrop(int v) {
        if (v < 0) return 0;
        if (v > 120) return 120;
        return v;
    }

    /** Append-only snapshot for debugging (used by tests). */
    JsonObject debugSnapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("pov", pov_mode);
        o.addProperty("scenes", scenes.size());
        JsonArray names = new JsonArray();
        for (Scene s : scenes) {
            names.add(new com.google.gson.JsonPrimitive(s.name + ":" + s.sources.size()));
        }
        o.add("scene_list", names);
        return o;
    }
}
