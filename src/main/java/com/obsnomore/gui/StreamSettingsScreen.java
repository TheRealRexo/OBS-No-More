package com.obsnomore.gui;

import com.obsnomore.config.OverlayConfig;
import com.obsnomore.stream.FFmpeg;
import com.obsnomore.stream.Hotkeys;
import com.obsnomore.stream.Presets;
import com.obsnomore.stream.RtmpTester;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full Setup (gear): stream slots + presets + connection tests, recording,
 * per-OS audio, discrete hotkey binding, ffmpeg location/scanner/links.
 */
public class StreamSettingsScreen extends Screen {
    private final Screen parent;
    private OverlayConfig cfg;
    private boolean refreshQueued = false;
    private int page;

    private static final String[] PAGES = {"Stream", "Record", "Audio", "Keys", "Deps", "Capture"};

    private final List<TextFieldWidget> fields = new ArrayList<TextFieldWidget>();
    private TextFieldWidget keyField0;
    private TextFieldWidget keyField1;
    private TextFieldWidget keyField2;
    private final List<TextFieldWidget> slotKeyFields = new ArrayList<TextFieldWidget>();
    private final List<Integer> slotKeyIdx = new ArrayList<Integer>();
    private final List<TextFieldWidget> slotUrlFields = new ArrayList<TextFieldWidget>();
    private final List<Integer> slotUrlIdx = new ArrayList<Integer>();
    private TextFieldWidget customUrlField;
    private TextFieldWidget bitrateField;
    private TextFieldWidget recPathField;
    private TextFieldWidget micField;
    private TextFieldWidget desktopField;
    private TextFieldWidget ffmpegField;
    private TextFieldWidget chromiumField;
    private TextFieldWidget electronField;
    private TextFieldWidget capDisplayField;
    private TextFieldWidget capXField;
    private TextFieldWidget capYField;
    private TextFieldWidget capWField;
    private TextFieldWidget capHField;
    private TextFieldWidget capTitleField;

    private List<com.obsnomore.stream.DepCheck.Row> depRows =
            new ArrayList<com.obsnomore.stream.DepCheck.Row>();

    private static final Map<Integer, RtmpTester.Result> TEST_RESULTS =
            new HashMap<Integer, RtmpTester.Result>();
    private static final Map<Integer, String> TEST_STATUS = new HashMap<Integer, String>();
    private static String lastTestStatus = "";
    private String scanStatus = "";
    private String saveNote = "";

    public StreamSettingsScreen(Screen parent) {
        this.parent = parent;
        this.cfg = OverlayConfig.get();
    }

    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void init() {
        cfg = OverlayConfig.get();
        buttons.clear();
        fields.clear();
        keyField0 = keyField1 = keyField2 = null;
        slotKeyFields.clear();
        slotKeyIdx.clear();
        slotUrlFields.clear();
        slotUrlIdx.clear();
        customUrlField = bitrateField = recPathField = null;
        micField = desktopField = ffmpegField = null;
        chromiumField = electronField = null;
        capDisplayField = capXField = capYField = null;
        capWField = capHField = capTitleField = null;

        for (int i = 0; i < PAGES.length; i++) {
            buttons.add(new ButtonWidget(100 + i, 10 + i * 68, 10, 64, 20, PAGES[i]));
        }
        if (cfg.setup_done) {
            // One-time wizard: button disappears after first setup so the
            // bottom row stays clean. (Wipe setup_done in the config to
            // bring the wizard back.)
            buttons.add(new ButtonWidget(1, width - 220, height - 30, 210, 20, "Done"));
        } else {
            buttons.add(new ButtonWidget(1, width - 110, height - 30, 100, 20, "Done"));
            buttons.add(new ButtonWidget(182, width - 220, height - 30, 100, 20, "Wizard"));
        }

        int y = 40;
        if (page == 0) {
            // One row per RTMP slot: preset | key | test | on/off | remove.
            // Custom-preset slots get their own URL row underneath.
            int shown = Math.min(cfg.streaming.slots.size(), 6);
            for (int i = 0; i < shown; i++) {
                OverlayConfig.RtmpSlot slot = cfg.streaming.slots.get(i);
                if (slot == null) continue;
                buttons.add(new ButtonWidget(110 + i, 10, y, 64, 20,
                        Presets.presetName(slot.preset)));
                TextFieldWidget kf = new TextFieldWidget(textRenderer, 79, y, 110, 20);
                kf.setMaxLength(256);
                kf.setText(slot.key == null ? "" : slot.key);
                fields.add(kf);
                slotKeyFields.add(kf);
                slotKeyIdx.add(Integer.valueOf(i));
                buttons.add(new ButtonWidget(120 + i, 194, y, 44, 20, "Test"));
                buttons.add(new ButtonWidget(143 + i, 243, y, 40, 20,
                        slot.enabled ? "ON" : "OFF"));
                if (cfg.streaming.slots.size() > 1) {
                    buttons.add(new ButtonWidget(134 + i, 288, y, 20, 20, "X"));
                }
                y += 22;
                if ("custom".equals(slot.preset)) {
                    TextFieldWidget uf = new TextFieldWidget(textRenderer, 10, y, 298, 20);
                    uf.setMaxLength(256);
                    uf.setText(slot.url == null ? "" : slot.url);
                    fields.add(uf);
                    slotUrlFields.add(uf);
                    slotUrlIdx.add(Integer.valueOf(i));
                    y += 22;
                }
            }
            if (cfg.streaming.slots.size() > shown) {
                saveNote = "+" + (cfg.streaming.slots.size() - shown)
                        + " more slot(s) in config/obsnomore.json";
            }
            if (!lastTestStatus.isEmpty()) y += 12;
            if (!cfg.streaming.multi_rtmp) y += 12;
            buttons.add(new ButtonWidget(152, 10, y + 2, 150, 20,
                    "Multi-RTMP: " + (cfg.streaming.multi_rtmp ? "ON" : "OFF")));
            buttons.add(new ButtonWidget(130, 165, y + 2, 143, 20, "+ Add slot"));
            y += 26;
            buttons.add(new ButtonWidget(131, 10, y, 150, 20,
                    "Encoder: " + shortEnc(cfg.streaming.encoder)));
            buttons.add(new ButtonWidget(132, 165, y, 155, 20,
                    "Quality: " + cfg.streaming.quality));
            y += 24;
            TextFieldWidget bf = new TextFieldWidget(textRenderer, 10, y, 70, 20);
            bf.setMaxLength(6);
            bf.setText(String.valueOf(cfg.streaming.video_bitrate_k));
            fields.add(bf);
            bitrateField = bf;
            buttons.add(new ButtonWidget(133, 85, y, 150, 20,
                    "x264: " + cfg.streaming.x264_preset));
        } else if (page == 1) {
            buttons.add(new ButtonWidget(140, 10, y, 150, 20, "Save as: " + cfg.recording.container));
            buttons.add(new ButtonWidget(141, 165, y, 155, 20, "Quality: " + cfg.recording.quality));
            y += 24;
            buttons.add(new ButtonWidget(142, 10, y, 200, 20,
                    "Encoder: " + shortEnc(cfg.recording.encoder)));
            y += 24;
            TextFieldWidget rf = new TextFieldWidget(textRenderer, 10, y, 310, 20);
            rf.setMaxLength(256);
            rf.setText(cfg.recording.path == null ? "" : cfg.recording.path);
            fields.add(rf);
            recPathField = rf;
        } else if (page == 2) {
            buttons.add(new ButtonWidget(150, 10, y, 200, 20,
                    "Mic: " + (cfg.streaming.mic.enabled ? "ON" : "OFF")));
            y += 24;
            TextFieldWidget mf = new TextFieldWidget(textRenderer, 10, y, 310, 20);
            mf.setMaxLength(256);
            mf.setText(cfg.streaming.mic.device == null ? "" : cfg.streaming.mic.device);
            fields.add(mf);
            micField = mf;
            y += 24;
            buttons.add(new ButtonWidget(151, 10, y, 200, 20,
                    "Desktop: " + (cfg.streaming.desktop.enabled ? "ON" : "OFF")));
            y += 24;
            TextFieldWidget df = new TextFieldWidget(textRenderer, 10, y, 310, 20);
            df.setMaxLength(256);
            df.setText(cfg.streaming.desktop.device == null ? "" : cfg.streaming.desktop.device);
            fields.add(df);
            desktopField = df;
            y += 24;
            buttons.add(new ButtonWidget(153, 10, y, 310, 20, "Create virtual output"));
        } else if (page == 3) {
            int ky = y;
            for (int i = 0; i < Hotkeys.ACTIONS.length; i++) {
                String action = Hotkeys.ACTIONS[i];
                buttons.add(new ButtonWidget(160 + i, 10, ky, 170, 20,
                        Hotkeys.label(action)));
                String keyLabel = action.equals(Hotkeys.capturing()) ? "> press key <"
                        : Hotkeys.keyName(cfg.hotkey(action));
                buttons.add(new ButtonWidget(170 + i, 185, ky, 135, 20, keyLabel));
                ky += 22;
            }
        } else if (page == 5) {
            capDisplayField = capField(cfg.capture.display, 70, y);
            y += 24;
            capXField = capIntField(cfg.capture.x, 70, y);
            capYField = capIntField(cfg.capture.y, 160, y);
            y += 24;
            capWField = capIntField(cfg.capture.w, 70, y);
            capHField = capIntField(cfg.capture.h, 160, y);
            y += 24;
            capTitleField = capField(
                    cfg.capture.title == null ? "" : cfg.capture.title, 200, y);
            y += 24;
            buttons.add(new ButtonWidget(190, 10, y, 310, 20,
                    "Follow mouse: " + (cfg.capture.follow_mouse ? "ON" : "OFF")));
        } else {
            TextFieldWidget ff = new TextFieldWidget(textRenderer, 10, y, 310, 20);
            ff.setMaxLength(512);
            String cur = cfg.ffmpeg_path == null || cfg.ffmpeg_path.isEmpty()
                    ? FFmpeg.locate() : cfg.ffmpeg_path;
            ff.setText(cur == null ? "" : cur);
            fields.add(ff);
            ffmpegField = ff;
            y += 24;
            buttons.add(new ButtonWidget(180, 10, y, 150, 20, "Scan drives"));
            buttons.add(new ButtonWidget(181, 165, y, 155, 20, "Use system PATH"));
            y += 24;
            buttons.add(new ButtonWidget(183, 10, y, 150, 20, "Rescan all"));
            buttons.add(new ButtonWidget(184, 165, y, 155, 20,
                    "Pages: " + pageRendererLabel()));
            depRows = com.obsnomore.stream.DepCheck.scan();
            // Manual renderer paths (only when the screen is tall enough
            // to fit them clear of the Done button; auto-detect otherwise,
            // or set them by hand in the config file).
            chromiumField = null;
            electronField = null;
            if (height >= 300) {
                y += 108;
                TextFieldWidget cf = new TextFieldWidget(textRenderer, 10, y, 310, 20);
                cf.setMaxLength(512);
                cf.setText(cfg.chromium_path == null ? "" : cfg.chromium_path);
                fields.add(cf);
                chromiumField = cf;
                y += 32;
                TextFieldWidget ef = new TextFieldWidget(textRenderer, 10, y, 310, 20);
                ef.setMaxLength(512);
                ef.setText(cfg.electron_path == null ? "" : cfg.electron_path);
                fields.add(ef);
                electronField = ef;
            }
        }
    }

    private String pageRendererLabel() {
        try {
            String m = com.obsnomore.stream.Webshotter.rendererMode();
            if (m.equals("chromium")) return "Chromium";
            if (m.equals("electron")) return "Electron";
        } catch (Throwable ignored) {
        }
        return "Auto";
    }

    private TextFieldWidget capField(String v, int x, int y) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 60, 20);
        f.setMaxLength(128);
        f.setText(v == null ? "" : v);
        fields.add(f);
        return f;
    }

    private TextFieldWidget capIntField(int v, int x, int y) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 60, 20);
        f.setMaxLength(8);
        f.setText(String.valueOf(v));
        fields.add(f);
        return f;
    }

    private static int capInt(TextFieldWidget f, int fallback) {
        if (f == null) return fallback;
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String firstCustomUrl() {
        for (OverlayConfig.RtmpSlot s : cfg.streaming.slots) {
            if (s != null && "custom".equals(s.preset)) {
                return s.url == null ? "" : s.url;
            }
        }
        return "";
    }

    private static String shortEnc(String e) {
        if (e == null) return "libx264";
        if (e.equals("libx264")) return "x264";
        if (e.equals("h264_nvenc")) return "NVENC";
        if (e.equals("h264_amf")) return "AMF";
        if (e.equals("h264_vaapi")) return "VAAPI";
        return e;
    }

    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (refreshQueued) {
            refreshQueued = false;
            init();
            return;
        }
        for (TextFieldWidget f : fields) {
            if (f != null) f.tick();
        }
    }

    @Override
    public void removed() {
        pushFields();
        cfg.save();
    }

    private void pushFields() {
        try {
            for (int k = 0; k < slotKeyFields.size(); k++) {
                int i = slotKeyIdx.get(k).intValue();
                TextFieldWidget f = slotKeyFields.get(k);
                if (f != null && i >= 0 && i < cfg.streaming.slots.size()
                        && cfg.streaming.slots.get(i) != null) {
                    cfg.streaming.slots.get(i).key = f.getText().trim();
                }
            }
            for (int k = 0; k < slotUrlFields.size(); k++) {
                int i = slotUrlIdx.get(k).intValue();
                TextFieldWidget f = slotUrlFields.get(k);
                if (f != null && i >= 0 && i < cfg.streaming.slots.size()
                        && cfg.streaming.slots.get(i) != null) {
                    cfg.streaming.slots.get(i).url = f.getText().trim();
                }
            }
            if (bitrateField != null) {
                try {
                    cfg.streaming.video_bitrate_k = Integer.parseInt(bitrateField.getText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (recPathField != null) cfg.recording.path = recPathField.getText().trim();
            if (micField != null) cfg.streaming.mic.device = micField.getText().trim();
            if (desktopField != null) cfg.streaming.desktop.device = desktopField.getText().trim();
            if (ffmpegField != null) cfg.ffmpeg_path = ffmpegField.getText().trim();
            if (chromiumField != null) cfg.chromium_path = chromiumField.getText().trim();
            if (electronField != null) cfg.electron_path = electronField.getText().trim();
            if (capDisplayField != null) {
                cfg.capture.display = capDisplayField.getText().trim();
            }
            if (capXField != null) cfg.capture.x = capInt(capXField, cfg.capture.x);
            if (capYField != null) cfg.capture.y = capInt(capYField, cfg.capture.y);
            if (capWField != null) cfg.capture.w = capInt(capWField, cfg.capture.w);
            if (capHField != null) cfg.capture.h = capInt(capHField, cfg.capture.h);
            if (capTitleField != null) cfg.capture.title = capTitleField.getText().trim();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        for (TextFieldWidget f : fields) {
            if (f != null) f.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyPressed(char chr, int code) {
        if (Hotkeys.capturing() != null) {
            Hotkeys.captureKey(code);
            init();
            return;
        }
        boolean handled = false;
        for (TextFieldWidget f : fields) {
            if (f != null && f.isFocused() && f.keyPressed(chr, code)) handled = true;
        }
        if (handled) return;
        if (code == 1) {
            pushFields();
            cfg.save();
            client.setScreen(parent);
            return;
        }
        super.keyPressed(chr, code);
    }

    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void buttonClicked(ButtonWidget button) {
        pushFields();
        if (button.id == 1) {
            cfg.save();
            client.setScreen(parent);
            return;
        }
        if (button.id >= 100 && button.id <= 105) {
            page = button.id - 100;
            if (page < 0) page = 0;
            if (page > 5) page = 5;
            // Status notes belong to the page that set them; a stale one
            // leaking onto another page reads as a false result.
            saveNote = "";
            queueRefresh();
            return;
        }
        switch (button.id) {
            case 110:
            case 111:
            case 112:
            case 113:
            case 114:
            case 115: {
                int i = button.id - 110;
                if (i < cfg.streaming.slots.size() && cfg.streaming.slots.get(i) != null) {
                    List<Presets.RtmpPreset> ps = Presets.rtmpPresets();
                    OverlayConfig.RtmpSlot slot = cfg.streaming.slots.get(i);
                    int cur = 0;
                    for (int k = 0; k < ps.size(); k++) {
                        if (ps.get(k).id.equals(slot.preset)) cur = k;
                    }
                    slot.preset = ps.get((cur + 1) % ps.size()).id;
                    cfg.save();
                    queueRefresh();
                }
                break;
            }
            case 120:
            case 121:
            case 122:
            case 123:
            case 124:
            case 125: {
                final int i = button.id - 120;
                if (i < cfg.streaming.slots.size()) {
                    OverlayConfig.RtmpSlot slot = cfg.streaming.slots.get(i);
                    final String url = Presets.buildUrl(slot.preset, slot.url, slot.key);
                    TEST_STATUS.put(i, "testing " + Presets.presetName(slot.preset) + "...");
                    lastTestStatus = "slot " + (i + 1) + ": testing...";
                    RtmpTester.test(FFmpeg.locate(), url, 10, new RtmpTester.Callback() {
                        @Override
                        public void done(RtmpTester.Result r) {
                            TEST_RESULTS.put(i, r);
                            TEST_STATUS.put(i, r.verdict + " (" + r.detail + ")");
                            lastTestStatus = "slot " + (i + 1) + ": " + r.verdict
                                    + " (" + r.detail + ")";
                            queueRefresh();
                        }
                    });
                }
                break;
            }
            case 143:
            case 144:
            case 145:
            case 146:
            case 147:
            case 148: {
                int i = button.id - 143;
                if (i < cfg.streaming.slots.size() && cfg.streaming.slots.get(i) != null) {
                    OverlayConfig.RtmpSlot slot = cfg.streaming.slots.get(i);
                    slot.enabled = !slot.enabled;
                    cfg.save();
                    queueRefresh();
                }
                break;
            }
            case 134:
            case 135:
            case 136:
            case 137:
            case 138:
            case 139: {
                int i = button.id - 134;
                if (cfg.streaming.slots.size() > 1 && i < cfg.streaming.slots.size()) {
                    cfg.streaming.slots.remove(i);
                    cfg.save();
                    saveNote = "slot removed (" + cfg.streaming.slots.size() + " total)";
                    queueRefresh();
                }
                break;
            }
            case 130: {
                OverlayConfig.RtmpSlot s = new OverlayConfig.RtmpSlot();
                cfg.streaming.slots.add(s);
                cfg.save();
                saveNote = "slot added (" + cfg.streaming.slots.size() + " total)";
                queueRefresh();
                break;
            }
            case 152: {
                cfg.streaming.multi_rtmp = !cfg.streaming.multi_rtmp;
                cfg.save();
                saveNote = cfg.streaming.multi_rtmp
                        ? "multi-RTMP on: all enabled slots"
                        : "single-RTMP: first enabled slot only";
                queueRefresh();
                break;
            }
            case 131: {
                List<String> keys = new ArrayList<String>(Presets.encoders().keySet());
                int cur = Math.max(0, keys.indexOf(cfg.streaming.encoder));
                cfg.streaming.encoder = keys.get((cur + 1) % keys.size());
                cfg.save();
                queueRefresh();
                break;
            }
            case 132: {
                List<Presets.Quality> qs = Presets.qualities();
                int cur = 0;
                for (int k = 0; k < qs.size(); k++) {
                    if (qs.get(k).id.equals(cfg.streaming.quality)) cur = k;
                }
                Presets.Quality q = qs.get((cur + 1) % qs.size());
                cfg.streaming.quality = q.id;
                cfg.streaming.video_bitrate_k = q.bitrateK;
                cfg.streaming.fps = q.fps;
                cfg.save();
                queueRefresh();
                break;
            }
            case 133: {
                String[] presets = {"ultrafast", "superfast", "veryfast", "faster", "medium"};
                int cur = 0;
                for (int k = 0; k < presets.length; k++) {
                    if (presets[k].equals(cfg.streaming.x264_preset)) cur = k;
                }
                cfg.streaming.x264_preset = presets[(cur + 1) % presets.length];
                cfg.save();
                queueRefresh();
                break;
            }
            case 140: {
                List<String> cs = Presets.containers();
                int cur = Math.max(0, cs.indexOf(cfg.recording.container));
                cfg.recording.container = cs.get((cur + 1) % cs.size());
                String p = cfg.recording.path;
                if (p != null && p.contains(".")) {
                    cfg.recording.path = p.substring(0, p.lastIndexOf('.') + 1)
                            + cfg.recording.container;
                    queueRefresh();
                }
                cfg.save();
                break;
            }
            case 141: {
                List<Presets.Quality> qs = Presets.qualities();
                int cur = 0;
                for (int k = 0; k < qs.size(); k++) {
                    if (qs.get(k).id.equals(cfg.recording.quality)) cur = k;
                }
                cfg.recording.quality = qs.get((cur + 1) % qs.size()).id;
                cfg.save();
                queueRefresh();
                break;
            }
            case 142: {
                List<String> keys = new ArrayList<String>(Presets.encoders().keySet());
                int cur = Math.max(0, keys.indexOf(cfg.recording.encoder));
                cfg.recording.encoder = keys.get((cur + 1) % keys.size());
                cfg.save();
                queueRefresh();
                break;
            }
            case 150:
                cfg.streaming.mic.enabled = !cfg.streaming.mic.enabled;
                cfg.save();
                queueRefresh();
                break;
            case 151:
                cfg.streaming.desktop.enabled = !cfg.streaming.desktop.enabled;
                cfg.save();
                queueRefresh();
                break;
            case 153: {
                pushFields();
                com.obsnomore.stream.VirtualAudio.Result vr =
                        com.obsnomore.stream.VirtualAudio.ensure();
                saveNote = vr.note == null ? "" : vr.note;
                if (vr.ok && vr.device != null && !vr.device.isEmpty()) {
                    cfg.streaming.desktop.device = vr.device;
                    cfg.streaming.desktop.enabled = true;
                    cfg.save();
                }
                queueRefresh();
                break;
            }
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
            case 165:
            case 166: {
                int i = button.id - 160;
                if (i >= 0 && i < Hotkeys.ACTIONS.length) {
                    Hotkeys.setCapturing(Hotkeys.ACTIONS[i]);
                    queueRefresh();
                }
                break;
            }
            case 170:
            case 171:
            case 172:
            case 173:
            case 174:
            case 175:
            case 176: {
                int i = button.id - 170;
                if (i >= 0 && i < Hotkeys.ACTIONS.length) {
                    Hotkeys.setCapturing(Hotkeys.ACTIONS[i]);
                    queueRefresh();
                }
                break;
            }
            case 180: {
                List<String> hits = FFmpeg.scan();
                if (!hits.isEmpty()) {
                    cfg.ffmpeg_path = hits.get(0);
                    cfg.save();
                    queueRefresh();
                }
                scanStatus = hits.isEmpty() ? "nothing found on common paths"
                        : "found " + hits.size() + ": " + hits.get(0);
                break;
            }
            case 181:
                cfg.ffmpeg_path = "";
                cfg.save();
                scanStatus = "cleared: will use system PATH";
                queueRefresh();
                break;
            case 183:
                pushFields();
                cfg.save();
                com.obsnomore.stream.Webshotter.reprobe();
                depRows = com.obsnomore.stream.DepCheck.scan();
                scanStatus = "rescanned all dependencies";
                queueRefresh();
                break;
            case 184: {
                pushFields();
                String m = "auto";
                try {
                    if (cfg.browser == null) cfg.browser = new OverlayConfig.Browser();
                    m = cfg.browser.renderer == null ? "auto" : cfg.browser.renderer;
                    if (m.equals("auto")) m = "chromium";
                    else if (m.equals("chromium")) m = "electron";
                    else m = "auto";
                    cfg.browser.renderer = m;
                } catch (Throwable ignored) {
                }
                cfg.save();
                scanStatus = "page renderer: " + m;
                queueRefresh();
                break;
            }
            case 182:
                pushFields();
                cfg.save();
                client.setScreen(new SetupWizardScreen(parent, this));
                break;
            case 190:
                cfg.capture.follow_mouse = !cfg.capture.follow_mouse;
                cfg.save();
                queueRefresh();
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        for (TextFieldWidget f : fields) {
            if (f != null) f.render();
        }
        super.render(mouseX, mouseY, tickDelta);
        int y = 40;
        if (page == 0) {
            textRenderer.draw("Preset | Key | Test | On | X (Custom adds URL row)", 10, y - 8,
                    0xFFAAAAAA);
            int ry = y;
            int shown = Math.min(cfg.streaming.slots.size(), 6);
            for (int i = 0; i < shown; i++) {
                OverlayConfig.RtmpSlot slot = cfg.streaming.slots.get(i);
                if (slot == null) continue;
                ry += 22;
                if ("custom".equals(slot.preset)) ry += 22;
            }
            // Mirror init()'s reserved rows exactly: +12 test status, +12
            // single-mode note; widget rows below start after both.
            int adj = 0;
            if (!lastTestStatus.isEmpty()) {
                String st = lastTestStatus;
                int maxW = width - 20;
                if (textRenderer.getStringWidth(st) > maxW) {
                    st = textRenderer.trimToWidth(st, maxW);
                }
                textRenderer.draw(st, 10, ry + 2, 0xFF55FFFF);
                adj += 12;
            }
            if (!cfg.streaming.multi_rtmp) {
                textRenderer.draw("Single-RTMP: only the first ON slot is used.", 10, ry + 2 + adj,
                        0xFFAAAAAA);
                adj += 12;
            }
            textRenderer.draw("Bitrate kbit (0 = preset default):", 10, ry + 72 + adj, 0xFFAAAAAA);
            if (!saveNote.isEmpty()) textRenderer.draw(saveNote, 10, ry + 84 + adj, 0xFF55FF55);
        } else if (page == 1) {
            textRenderer.draw("Output file (game-relative ok):", 10, y + 72, 0xFFAAAAAA);
        } else if (page == 2) {
            // Toggle buttons already say Mic:/Desktop: — device labels sit
            // in the gaps so nothing paints over anything.
            textRenderer.draw(FFmpeg.audioHint(), 10, y + 118, 0xFF777777);
            textRenderer.draw("OS profile: " + FFmpeg.os(), 10, y + 128, 0xFF777777);
            String needTools = "Need tools? " + com.obsnomore.stream.InstallGuide.audioInstall();
            if (textRenderer.getStringWidth(needTools) > width - 20) {
                needTools = textRenderer.trimToWidth(needTools, width - 20);
            }
            textRenderer.draw(needTools, 10, y + 138, 0xFF777777);
            String wikiLine = "Full steps: " + com.obsnomore.stream.InstallGuide.WIKI + "/Audio-Setup";
            if (textRenderer.getStringWidth(wikiLine) > width - 20) {
                wikiLine = textRenderer.trimToWidth(wikiLine, width - 20);
            }
            textRenderer.draw(wikiLine, 10, y + 148, 0xFF777777);
            if (!saveNote.isEmpty()) textRenderer.draw(saveNote, 10, y + 158, 0xFF55FF55);
        } else if (page == 3) {
            textRenderer.draw("Click a key, then press the new key (Esc cancels).", 10, y - 8,
                    0xFFAAAAAA);
        } else if (page == 5) {
            textRenderer.draw("Game window capture (tunnels only).", 10, y - 8, 0xFFFFFFFF);
            textRenderer.draw("display", 10, y + 5, 0xFFAAAAAA);
            textRenderer.draw("x", 10, y + 29, 0xFFAAAAAA);
            textRenderer.draw("y", 140, y + 29, 0xFFAAAAAA);
            textRenderer.draw("w", 10, y + 53, 0xFFAAAAAA);
            textRenderer.draw("h", 140, y + 53, 0xFFAAAAAA);
            textRenderer.draw("window title (Windows capture, blank = auto):", 10, y + 77,
                    0xFFAAAAAA);
            textRenderer.draw("Fallback region (only if game_only=false in config).", 10, y + 125,
                    0xFF777777);
            textRenderer.draw("macOS uses screen index + crop; keep w/h = game size.", 10, y + 135, 0xFF777777);
        } else {
            textRenderer.draw("ffmpeg binary path (blank = auto):", 10, y - 8, 0xFFAAAAAA);
            textRenderer.draw("Dependencies (green = found, red = missing):", 10, y + 72,
                    0xFFFFFFFF);
            int dry = y + 84;
            for (int i = 0; i < depRows.size() && i < 5; i++) {
                com.obsnomore.stream.DepCheck.Row r = depRows.get(i);
                if (r == null) continue;
                String line = r.name + ": " + (r.detail == null ? "" : r.detail);
                int maxW = width - 20;
                if (textRenderer.getStringWidth(line) > maxW) {
                    line = textRenderer.trimToWidth(line, maxW);
                }
                int col = r.na ? 0xFF777777 : (r.ok ? 0xFF55FF55 : 0xFFFF5555);
                textRenderer.draw(line, 10, dry, col);
                dry += 10;
            }
            if (!scanStatus.isEmpty()) textRenderer.draw(scanStatus, 10, dry + 2, 0xFF55FFFF);
            if (height >= 300) {
                textRenderer.draw("Chromium path (blank = auto):", 10, y + 148, 0xFFAAAAAA);
                textRenderer.draw("Electron path (blank = auto):", 10, y + 180, 0xFFAAAAAA);
            }
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return true;
    }

    private void queueRefresh() {
        refreshQueued = true;
    }
}
