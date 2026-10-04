package com.obsnomore.gui;

import com.obsnomore.config.OverlayConfig;
import com.obsnomore.data.WebFetcher;
import com.obsnomore.render.OverlayRenderer;
import com.obsnomore.render.PreviewTextures;
import com.obsnomore.stream.CameraManager;
import com.obsnomore.stream.StreamManager;
import com.obsnomore.stream.Webshotter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * OBS-style scene editor.
 *
 * <p>Layout mirrors OBS Studio: a live canvas plus docked panels —
 * <b>Scenes</b> (select/add/delete), <b>Sources</b> (select/add/delete) and
 * <b>Controls</b> (Stream, Record, POV, Setup). Sources are generic building
 * blocks (camera, text label, image); the canvas supports drag-to-move,
 * corner resize and ALT + edge/corner crop.
 */
public class StreamOverlayEditorScreen extends Screen {
    private static final int OBS_BUTTON_ID = 9001;

    private final Screen parent;
    private OverlayConfig cfg;

    private enum DragMode {
        NONE, MOVE,
        SCALE_TL, SCALE_TR, SCALE_BL, SCALE_BR,
        STRETCH_X, STRETCH_Y,
        CROP_T, CROP_B, CROP_L, CROP_R,
        CROP_TL, CROP_TR, CROP_BL, CROP_BR
    }

    private enum Position {
        NONE, BODY,
        CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR,
        EDGE_T, EDGE_B, EDGE_L, EDGE_R
    }

    private OverlayConfig.Source selected;
    private DragMode dragMode = DragMode.NONE;
    private boolean dragging = false;
    private float lastMouseX;
    private float lastMouseY;
    private float dragStartScale;
    private int dragStartW, dragStartH, dragAnchorX, dragAnchorY;

    /** OBS colors: red = transform, green = crop. */
    static final int HANDLE_RESIZE = 0xFFFF3B30;
    static final int HANDLE_CROP = 0xFF4CD964;

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    /** Crop modifier: Alt (Option on macOS) with Ctrl as a fallback. */
    static boolean cropModifier() {
        return isAltDown() || isCtrlDown();
    }

    /** Shift: free (non-uniform) resize while corner-dragging. */
    static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private boolean showSettings = false;
    private boolean refreshQueued = false;
    // Button rects for OBS-blue selection outlines (x,y,w,h), -1 = absent.
    private final int[][] sceneRects = new int[3][4];
    private final int[][] srcRects = new int[3][4];
    // Per-source properties form (repurposed settings overlay).
    private TextFieldWidget propNameField;
    private TextFieldWidget propXField;
    private TextFieldWidget propYField;
    private TextFieldWidget propScaleField;
    private TextFieldWidget propContentField;
    private TextFieldWidget propField;
    private boolean renamingScene;
    private String settingsError = "";

    public StreamOverlayEditorScreen(Screen parent) {
        this.parent = parent;
        this.cfg = OverlayConfig.get();
    }

    // ------------------------------------------------------------------
    // Setup — OBS docks: Scenes | Sources | Controls
    // ------------------------------------------------------------------

    // OBS Studio layout: top menu bar, central preview canvas,
    // bottom dock strip (Scenes | Sources | Audio Mixer | Transitions | Controls),
    // status bar at the very bottom.
    static final int TOP_H = 14;
    static final int STATUS_H = 12;

    int dockH() {
        return height < 200 ? 64 : 80;
    }

    int dockY() {
        return height - STATUS_H - dockH();
    }

    @Override
    public void init() {
        cfg = OverlayConfig.get();
        buttons.clear();
        if (showSettings) {
            int cx = width / 2;
            int fw = Math.min(320, width - 40);
            int fx = (width - fw) / 2;
            int fy = 52;
            OverlayConfig.Source s = selected;
            String content = propContentValue(s);
            propNameField = makePropField(fx, fy, fw, s == null || s.name == null ? "" : s.name);
            fy += 28;
            propXField = makePropField(fx, fy, fw, s == null ? "0" : String.valueOf(s.x));
            fy += 28;
            propYField = makePropField(fx, fy, fw, s == null ? "0" : String.valueOf(s.y));
            fy += 28;
            propScaleField = makePropField(fx, fy, fw,
                    s == null ? "1.0" : String.valueOf(s.scale));
            fy += 28;
            propContentField = null;
            if (needsPropContent(s)) {
                propContentField = makePropField(fx, fy, fw, content);
                fy += 28;
            }
            buttons.add(new ButtonWidget(20, cx - 105, height - 60, 100, 20, "Save & Close"));
            buttons.add(new ButtonWidget(21, cx + 5, height - 60, 100, 20, "Cancel"));
            buttons.add(new ButtonWidget(2, cx - 105, height - 32, 205, 20, "Back (no save)"));
            propField = null;
            return;
        }
        propNameField = propXField = propYField = propScaleField = propContentField = null;
        int dy = dockY();
        int dh = dockH();
        // Rescue: sources saved by pre-dock layouts can sit entirely behind
        // the dock strip (invisible but "there"). Lift those just into view.
        // Runs ONCE ever (layout_rescued): re-running would drag user-placed
        // sources back on every open, making moves look unsaved.
        int viewBottom = dy - 32;
        boolean rescued = false;
        boolean doRescue = false;
        try {
            doRescue = !cfg.layout_rescued;
        } catch (Throwable ignored) {
        }
        try {
            if (doRescue) {
                for (OverlayConfig.Source s : cfg.activeScene().sources) {
                    if (s == null) continue;
                    if (s.y >= viewBottom) {
                        int h = Math.max(12, OverlayRenderer.sourceHeight(s));
                        s.y = Math.max(TOP_H + 20, viewBottom - h - 8);
                        rescued = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (doRescue) {
                cfg.layout_rescued = true;
                cfg.save();
            }
        } catch (Throwable ignored) {
        }
        if (rescued) {
            com.obsnomore.ObsNoMore.setAction("Moved hidden sources into view");
        }
        int listH = 13;
        // Column geometry (fractions of width).
        int sx = 2;
        int sw = Math.max(64, width * 18 / 100);
        int sox = sx + sw + 2;
        int sow = Math.max(84, width * 25 / 100);
        int mx = sox + sow + 2;
        int mw = Math.max(64, width * 19 / 100);
        int tx = mx + mw + 2;
        int tw = Math.max(52, width * 14 / 100);
        int cx0 = tx + tw + 2;
        int cw = Math.max(60, width - cx0 - 2);
        // ---- Scenes dock ----
        List<OverlayConfig.Scene> scenes = cfg.scenes;
        int sy = dy + 12;
        for (int i = 0; i < 3; i++) {
            sceneRects[i][0] = -1;
        }
        for (int i = 0; i < scenes.size() && i < 3; i++) {
            OverlayConfig.Scene sc = scenes.get(i);
            String nm = (i == cfg.active_scene ? "> " : "") + shortName(sc == null ? "?" : sc.name, 12);
            int bx = sx + 2;
            int by = sy + i * (listH + 1);
            int bw2 = sw - 4;
            buttons.add(new ButtonWidget(60 + i, bx, by, bw2, listH, nm));
            sceneRects[i][0] = bx;
            sceneRects[i][1] = by;
            sceneRects[i][2] = bw2;
            sceneRects[i][3] = listH;
        }
        int sby = sy + 3 * (listH + 1) + 1;
        if (sby + 12 <= dy + dh) {
            buttons.add(new ButtonWidget(64, sx + 2, sby, 20, 12, "+"));
            buttons.add(new ButtonWidget(65, sx + 24, sby, 20, 12, "-"));
            buttons.add(new ButtonWidget(3, sx + 46, sby, sw - 48, 12, scenes.size() > 1 ? ((cfg.active_scene + 1) + "/" + scenes.size()) : "1/1"));
        }
        int sby2 = sby + 13;
        if (sby2 + 12 <= dy + dh) {
            buttons.add(new ButtonWidget(66, sx + 2, sby2, sw - 4, 12, "Rename"));
        }
        // ---- Sources dock ----
        List<OverlayConfig.Source> sources = cfg.activeScene().sources;
        int qy = dy + 12;
        for (int i = 0; i < 3; i++) {
            srcRects[i][0] = -1;
        }
        for (int i = 0; i < sources.size() && i < 3; i++) {
            OverlayConfig.Source src = sources.get(i);
            String nm = (src.enabled ? "" : "(x) ") + (i + 1) + "." + shortName(src == null ? "?" : src.name, 10);
            if (src.locked) nm = "*" + nm;
            int bx = sox + 2;
            int by = qy + i * (listH + 1);
            int bw2 = sow - 4;
            buttons.add(new ButtonWidget(50 + i, bx, by, bw2, listH, nm));
            srcRects[i][0] = bx;
            srcRects[i][1] = by;
            srcRects[i][2] = bw2;
            srcRects[i][3] = listH;
        }
        int aby = qy + 3 * (listH + 1) + 1;
        if (aby + 12 <= dy + dh) {
            // Add-row: generic OBS-style sources (camera, text, image, web).
            int bw = (sow - 4) / 4;
            String[] plus = {"+Cam", "+Txt", "+Img", "+Web"};
            for (int i = 0; i < 4; i++) {
                buttons.add(new ButtonWidget(30 + i, sox + 2 + i * bw, aby, bw, 11, plus[i]));
            }
            int aby2 = aby + 12;
            if (aby2 + 11 <= dy + dh) {
                int third = (sow - 4) / 3;
                buttons.add(new ButtonWidget(36, sox + 2, aby2, third, 11, "Del"));
                buttons.add(new ButtonWidget(70, sox + 2 + third, aby2, third, 11, "Eye"));
                buttons.add(new ButtonWidget(71, sox + 2 + third * 2, aby2, sow - 4 - third * 2, 11, "Lock"));
            }
        }
        // ---- Audio Mixer dock ----
        buttons.add(new ButtonWidget(90, mx + 2, dy + 13, 30, 12, cfg.streaming.desktop.enabled ? "D:On" : "D:Off"));
        buttons.add(new ButtonWidget(92, mx + 34, dy + 13, 14, 12, "-"));
        buttons.add(new ButtonWidget(93, mx + 50, dy + 13, 14, 12, "+"));
        buttons.add(new ButtonWidget(91, mx + 2, dy + 27, 30, 12, cfg.streaming.mic.enabled ? "M:On" : "M:Off"));
        buttons.add(new ButtonWidget(94, mx + 34, dy + 27, 14, 12, "-"));
        buttons.add(new ButtonWidget(95, mx + 50, dy + 27, 14, 12, "+"));
        if (dy + 52 <= dy + dh) {
            buttons.add(new ButtonWidget(2, mx + 2, dy + 41, mw - 4, 11, "Props"));
        }
        // ---- Scene Transitions dock ----
        String tr = cfg.streaming.transition == null ? "Cut" : cfg.streaming.transition;
        buttons.add(new ButtonWidget(100, tx + 2, dy + 13, tw - 4, 12, tr));
        buttons.add(new ButtonWidget(101, tx + 2, dy + 27, (tw - 6) / 2, 12, "-ms"));
        buttons.add(new ButtonWidget(102, tx + 4 + (tw - 6) / 2, dy + 27, (tw - 6) / 2, 12, "+ms"));
        if (dy + 52 <= dy + dh) {
            buttons.add(new ButtonWidget(103, tx + 2, dy + 41, tw - 4, 11, "Go"));
        }
        // ---- Controls dock ----
        int bxx = cx0 + 2;
        int bww = cw - 4;
        int byy = dy + 12;
        int bh = 12;
        buttons.add(new ButtonWidget(38, bxx, byy, bww, bh, StreamManager.isStreaming() ? "Stop Stream" : "Start Stream"));
        buttons.add(new ButtonWidget(39, bxx, byy + 13, bww, bh, StreamManager.isRecording() ? "Stop Rec" : "Start Rec"));
        buttons.add(new ButtonWidget(37, bxx, byy + 26, bww, bh, cfg.pov_mode));
        if (byy + 39 + 11 <= dy + dh) {
            int half = (bww - 2) / 2;
            buttons.add(new ButtonWidget(40, bxx, byy + 39, half, 11, "Settings"));
            buttons.add(new ButtonWidget(1, bxx + half + 2, byy + 39, bww - half - 2, 11, "Exit"));
        }
        // Property field floats just under the menu bar (over the canvas).
        if (renamingScene) {
            OverlayConfig.Scene asc = cfg.activeScene();
            int fw = Math.min(260, width - 20);
            propField = new TextFieldWidget(textRenderer, 10, TOP_H + 3, fw, 16);
            propField.setMaxLength(64);
            propField.setText(asc == null || asc.name == null ? "" : asc.name);
            propField.setFocused(true);
        } else if (needsPropField(selected)) {
            int fw = Math.min(260, width - 20);
            propField = new TextFieldWidget(textRenderer, 10, TOP_H + 3, fw, 16);
            propField.setMaxLength(256);
            propField.setText(propValue(selected));
        } else {
            propField = null;
        }
        // Info-bar buttons (right side of the "No source selected" strip).
        int infoY = dy - 18;
        buttons.add(new ButtonWidget(74, width - 172, infoY + 2, 52, 12, "Reload"));
        buttons.add(new ButtonWidget(72, width - 118, infoY + 2, 58, 12, "Properties"));
        buttons.add(new ButtonWidget(73, width - 58, infoY + 2, 52, 12, "Filters"));
    }

    private static String shortName(String s, int max) {
        if (s == null) return "?";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean needsPropField(OverlayConfig.Source s) {
        if (s == null) return false;
        String t = s.type == null ? "" : s.type;
        return t.equals("text") || t.equals("image") || t.equals("camera")
                || t.equals("browser");
    }

    private TextFieldWidget makePropField(int x, int y, int w, String v) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 15);
        f.setMaxLength(256);
        f.setText(v == null ? "" : v);
        return f;
    }

    private static boolean needsPropContent(OverlayConfig.Source s) {
        if (s == null || s.type == null) return false;
        String t = s.type;
        return t.equals("text") || t.equals("image") || t.equals("camera")
                || t.equals("browser");
    }

    private static String propContentLabel(OverlayConfig.Source s) {
        if (s == null || s.type == null) return "Value:";
        String t = s.type;
        if (t.equals("text")) return "Text:";
        if (t.equals("image")) return "Image path:";
        if (t.equals("camera")) return "Camera device:";
        if (t.equals("browser")) return "Page URL:";
        return "Value:";
    }

    private static String propContentValue(OverlayConfig.Source s) {
        if (s == null) return "";
        String t = s.type == null ? "" : s.type;
        if (t.equals("text")) return s.text == null ? "" : s.text;
        if (t.equals("image")) return s.path == null ? "" : s.path;
        if (t.equals("camera")) return s.device == null ? "" : s.device;
        if (t.equals("browser")) return s.url == null ? "" : s.url;
        return "";
    }

    private static int propInt(TextFieldWidget f, int fallback) {
        if (f == null) return fallback;
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float propFloat(TextFieldWidget f, float fallback) {
        if (f == null) return fallback;
        try {
            return Float.parseFloat(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Writes the properties form back to the selected source. */
    private void savePropsPanel() {
        if (selected == null) return;
        if (propNameField != null) {
            String n = propNameField.getText().trim();
            if (!n.isEmpty()) selected.name = n;
        }
        if (propXField != null) selected.x = propInt(propXField, selected.x);
        if (propYField != null) selected.y = propInt(propYField, selected.y);
        if (propScaleField != null) {
            float v = propFloat(propScaleField, selected.scale);
            if (v < 0.25f) v = 0.25f;
            if (v > 4.0f) v = 4.0f;
            selected.scale = v;
        }
        if (propContentField != null) {
            String v = propContentField.getText();
            String t = selected.type == null ? "" : selected.type;
            if (t.equals("text")) selected.text = v;
            else if (t.equals("image")) selected.path = v.trim();
            else if (t.equals("camera")) selected.device = v.trim();
            else if (t.equals("browser")) selected.url = v.trim();
        }
        settingsError = "";
        cfg.save();
    }

    private void tickPropForm() {
        if (propNameField != null) propNameField.tick();
        if (propXField != null) propXField.tick();
        if (propYField != null) propYField.tick();
        if (propScaleField != null) propScaleField.tick();
        if (propContentField != null) propContentField.tick();
    }

    private void clickPropForm(int mouseX, int mouseY, int button) {
        if (propNameField != null) propNameField.mouseClicked(mouseX, mouseY, button);
        if (propXField != null) propXField.mouseClicked(mouseX, mouseY, button);
        if (propYField != null) propYField.mouseClicked(mouseX, mouseY, button);
        if (propScaleField != null) propScaleField.mouseClicked(mouseX, mouseY, button);
        if (propContentField != null) propContentField.mouseClicked(mouseX, mouseY, button);
    }

    /** @return true when a form field consumed the key. */
    private boolean keyPropForm(char chr, int code) {
        boolean handled = false;
        if (propNameField != null && propNameField.isFocused()
                && propNameField.keyPressed(chr, code)) handled = true;
        if (!handled && propXField != null && propXField.isFocused()
                && propXField.keyPressed(chr, code)) handled = true;
        if (!handled && propYField != null && propYField.isFocused()
                && propYField.keyPressed(chr, code)) handled = true;
        if (!handled && propScaleField != null && propScaleField.isFocused()
                && propScaleField.keyPressed(chr, code)) handled = true;
        if (!handled && propContentField != null && propContentField.isFocused()
                && propContentField.keyPressed(chr, code)) handled = true;
        return handled;
    }

    private static String propValue(OverlayConfig.Source s) {
        if (s == null) return "";
        String t = s.type == null ? "" : s.type;
        if (t.equals("text")) return s.text == null ? "" : s.text;
        if (t.equals("image")) return s.path == null ? "" : s.path;
        if (t.equals("camera")) return s.device == null ? "" : s.device;
        if (t.equals("browser")) return s.url == null ? "" : s.url;
        return "";
    }

    private void applyPropField() {
        if (propField == null) return;
        if (renamingScene) {
            renamingScene = false;
            String v = propField.getText().trim();
            if (!v.isEmpty()) {
                cfg.activeScene().name = v;
                cfg.save();
            }
            queueRefresh();
            return;
        }
        if (selected == null) return;
        String v = propField.getText();
        String t = selected.type == null ? "" : selected.type;
        if (t.equals("text")) selected.text = v;
        else if (t.equals("image")) selected.path = v;
        else if (t.equals("camera")) selected.device = v;
        else if (t.equals("browser")) selected.url = v.trim();
        cfg.save();
    }

    @Override
    public void tick() {
        if (refreshQueued) {
            refreshQueued = false;
            init();
            return;
        }
        if (showSettings) {
            tickPropForm();
        } else if (propField != null) {
            propField.tick();
        }
        try {
            OverlayConfig.Source cam = null;
            for (OverlayConfig.Source s : cfg.activeScene().sources) {
                if (s != null && s.enabled && "camera".equals(s.type)
                        && s.device != null && !s.device.trim().isEmpty()) {
                    cam = s;
                    break;
                }
            }
            if (cam != null) CameraManager.ensure(cam.device, cam.cam_w, cam.cam_h, cam.cam_fps);
            else CameraManager.stop();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void removed() {
        applyPropField();
        cfg.save();
    }

    @Override
    public boolean shouldPauseGame() {
        return true;
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void buttonClicked(ButtonWidget button) {
        if (!showSettings) applyPropField();
        if (button.id == 1) {
            applyPropField();
            cfg.save();
            client.setScreen(parent);
            return;
        }
        if (button.id == 2) {
            if (selected == null) {
                com.obsnomore.ObsNoMore.setAction("Select a source first, then open Properties");
                return;
            }
            showSettings = !showSettings;
            settingsError = "";
            queueRefresh();
            return;
        }
        if (showSettings) {
            if (button.id == 20) {
                savePropsPanel();
                showSettings = false;
                queueRefresh();
                return;
            }
            if (button.id == 21) {
                showSettings = false;
                settingsError = "";
                queueRefresh();
                return;
            }
            return;
        }
        if (button.id == 37) {
            cfg.pov_mode = "1-POV".equals(cfg.pov_mode) ? "2-POV" : "1-POV";
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 38) {
            if (StreamManager.isStreaming()) StreamManager.stopStream();
            else StreamManager.startStream();
            queueRefresh();
            return;
        }
        if (button.id == 39) {
            if (StreamManager.isRecording()) StreamManager.stopRecord();
            else StreamManager.startRecord();
            queueRefresh();
            return;
        }
        if (button.id == 40) {
            applyPropField();
            client.setScreen(new StreamSettingsScreen(this));
            return;
        }
        if (button.id == 36) {
            if (selected != null) {
                cfg.activeScene().sources.remove(selected);
                selected = null;
                cfg.save();
                queueRefresh();
            }
            return;
        }
        if (button.id == 3) {
            cfg.cycleScene(1);
            selected = null;
            queueRefresh();
            return;
        }
        if (button.id == 66) {
            renamingScene = true;
            queueRefresh();
            return;
        }
        if (button.id == 64) {
            OverlayConfig.Scene scene = new OverlayConfig.Scene();
            scene.name = "Scene " + (cfg.scenes.size() + 1);
            cfg.scenes.add(scene);
            cfg.active_scene = cfg.scenes.size() - 1;
            selected = null;
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 65) {
            if (cfg.scenes.size() > 1 && cfg.active_scene >= 0
                    && cfg.active_scene < cfg.scenes.size()) {
                cfg.scenes.remove(cfg.active_scene);
                cfg.active_scene = Math.max(0, cfg.active_scene - 1);
                selected = null;
                cfg.save();
                queueRefresh();
            }
            return;
        }
        if (button.id >= 60 && button.id <= 63) {
            int idx = button.id - 60;
            if (idx >= 0 && idx < cfg.scenes.size()) {
                applyPropField();
                cfg.active_scene = idx;
                selected = null;
                cfg.save();
                queueRefresh();
            }
            return;
        }
        if (button.id >= 30 && button.id <= 33) {
            String[] types = {"camera", "text", "image", "browser"};
            OverlayConfig.Source s = OverlayConfig.newSource(types[button.id - 30]);
            s.x = Math.max(10, width / 2 - 60);
            s.y = Math.max(100, height / 2 - 40);
            cfg.activeScene().sources.add(s);
            selected = s;
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id >= 50 && button.id <= 54) {
            int idx = button.id - 50;
            List<OverlayConfig.Source> sources = cfg.activeScene().sources;
            if (idx >= 0 && idx < sources.size()) {
                applyPropField();
                selected = sources.get(idx);
                queueRefresh();
            }
            return;
        }
        if (button.id == 72) {
            if (selected == null) {
                com.obsnomore.ObsNoMore.setAction("Select a source first, then open Properties");
                return;
            }
            showSettings = true;
            settingsError = "";
            queueRefresh();
            return;
        }
        if (button.id == 73) {
            if (selected == null) {
                com.obsnomore.ObsNoMore.setAction("Select a source first, then open Properties");
                return;
            }
            showSettings = true;
            settingsError = "";
            queueRefresh();
            return;
        }
        if (button.id == 70) {
            if (selected != null) {
                selected.enabled = !selected.enabled;
                cfg.save();
                queueRefresh();
            }
            return;
        }
        if (button.id == 71) {
            if (selected != null) {
                selected.locked = !selected.locked;
                cfg.save();
                queueRefresh();
            }
            return;
        }
        if (button.id == 74) {
            if (selected != null && "browser".equals(selected.type)) {
                Webshotter.reloadNow(selected);
                com.obsnomore.ObsNoMore.setAction("Reloading page...");
            } else {
                com.obsnomore.ObsNoMore.setAction("Select a browser source first, then Reload");
            }
            queueRefresh();
            return;
        }
        if (button.id == 90) {
            cfg.streaming.desktop.enabled = !cfg.streaming.desktop.enabled;
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 91) {
            cfg.streaming.mic.enabled = !cfg.streaming.mic.enabled;
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 92) {
            cfg.streaming.desktop.gain = clampGain(cfg.streaming.desktop.gain - 0.1);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 93) {
            cfg.streaming.desktop.gain = clampGain(cfg.streaming.desktop.gain + 0.1);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 94) {
            cfg.streaming.mic.gain = clampGain(cfg.streaming.mic.gain - 0.1);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 95) {
            cfg.streaming.mic.gain = clampGain(cfg.streaming.mic.gain + 0.1);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 100) {
            String t = cfg.streaming.transition;
            cfg.streaming.transition = "Fade".equals(t) ? "Cut" : "Fade";
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 101) {
            cfg.streaming.transition_ms = Math.max(0, cfg.streaming.transition_ms - 50);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 102) {
            cfg.streaming.transition_ms = Math.min(2000, cfg.streaming.transition_ms + 50);
            cfg.save();
            queueRefresh();
            return;
        }
        if (button.id == 103) {
            com.obsnomore.ObsNoMore.setAction("Transition (" + cfg.streaming.transition + " " + cfg.streaming.transition_ms + "ms)");
            queueRefresh();
            return;
        }
    }

    private static double clampGain(double g) {
        if (g < 0.0) return 0.0;
        if (g > 2.0) return 2.0;
        return Math.round(g * 10.0) / 10.0;
    }

    private static float clampScale(float s) {
        if (s < 0.25f) return 0.25f;
        if (s > 4.0f) return 4.0f;
        return Math.round(s * 10.0f) / 10.0f;
    }

    // ------------------------------------------------------------------
    // Mouse: OBS-style transform.
    // Red handles = resize (corners uniform, Shift = free/non-uniform,
    // edges stretch one axis). Green = crop while Alt/Option (Ctrl works
    // too) is held; Alt can be pressed or released mid-drag to switch.
    // ------------------------------------------------------------------

    /** Which handle/zone the active drag started in (modifiers evaluated live). */
    private Position dragZone = Position.NONE;

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (showSettings) {
            clickPropForm(mouseX, mouseY, button);
            return;
        }
        if (propField != null) propField.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        computePreview();
        // Canvas editing happens inside the closed preview panel only.
        if (!inPreview(mouseX, mouseY)) return;
        float lx = (mouseX - previewX) / previewK;
        float ly = (mouseY - previewY) / previewK;

        OverlayConfig.Source hit = pickWidget(lx, ly);
        if (hit != null) selected = hit;

        Position pos = pickPosition(lx, ly);
        if (selected != null && !selected.locked && pos != Position.NONE) {
            dragZone = pos;
            dragMode = zoneMode(pos);
            dragging = true;
            lastMouseX = lx;
            lastMouseY = ly;
            dragStartScale = selected.scale;
            dragStartSX = selected.stretch_x > 0.0f ? selected.stretch_x : 1.0f;
            dragStartSY = selected.stretch_y > 0.0f ? selected.stretch_y : 1.0f;
            syncDragOrigin(lx, ly);
            captureScaleState();
        } else {
            if (hit == null) selected = null;
            dragZone = Position.NONE;
            dragMode = DragMode.NONE;
            dragging = false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (dragging) {
            dragging = false;
            dragMode = DragMode.NONE;
            cfg.save();
        }
    }

    /** Initial mode for a press zone; crop/shift re-evaluated live each frame. */
    private static DragMode zoneMode(Position pos) {
        switch (pos) {
            case BODY:
                return DragMode.MOVE;
            case CORNER_TL:
                return DragMode.SCALE_TL;
            case CORNER_TR:
                return DragMode.SCALE_TR;
            case CORNER_BL:
                return DragMode.SCALE_BL;
            case CORNER_BR:
                return DragMode.SCALE_BR;
            case EDGE_T:
            case EDGE_B:
                return DragMode.STRETCH_Y;
            case EDGE_L:
            case EDGE_R:
                return DragMode.STRETCH_X;
            default:
                return DragMode.NONE;
        }
    }

    /** Effective mode right now: Alt/Ctrl flips edge/corner drags to crop. */
    private DragMode liveMode() {
        if (selected == null || dragZone == Position.NONE) return DragMode.NONE;
        boolean crop = cropModifier() && croppable(selected);
        switch (dragZone) {
            case BODY:
                return DragMode.MOVE;
            case CORNER_TL:
                return crop ? DragMode.CROP_TL : DragMode.SCALE_TL;
            case CORNER_TR:
                return crop ? DragMode.CROP_TR : DragMode.SCALE_TR;
            case CORNER_BL:
                return crop ? DragMode.CROP_BL : DragMode.SCALE_BL;
            case CORNER_BR:
                return crop ? DragMode.CROP_BR : DragMode.SCALE_BR;
            case EDGE_T:
                return crop ? DragMode.CROP_T : DragMode.STRETCH_Y;
            case EDGE_B:
                return crop ? DragMode.CROP_B : DragMode.STRETCH_Y;
            case EDGE_L:
                return crop ? DragMode.CROP_L : DragMode.STRETCH_X;
            case EDGE_R:
                return crop ? DragMode.CROP_R : DragMode.STRETCH_X;
            default:
                return DragMode.NONE;
        }
    }

    private static boolean croppable(OverlayConfig.Source s) {
        if (s == null || s.type == null) return false;
        return s.type.equals("image");
    }

    @Override
    public void keyPressed(char chr, int code) {
        if (showSettings) {
            if (keyPropForm(chr, code)) return;
            if (code == 28 || code == 156) {
                ButtonWidget save = findButton(20);
                if (save != null) buttonClicked(save);
                return;
            }
            if (code == 1) {
                showSettings = false;
                queueRefresh();
                return;
            }
        } else {
            if (propField != null && propField.isFocused()) {
                if (propField.keyPressed(chr, code)) {
                    if (code == 28 || code == 156) applyPropField();
                    return;
                }
            }
            int dx = 0, dy = 0;
            if (code == 203) dx = -1;
            if (code == 205) dx = 1;
            if (code == 200) dy = -1;
            if (code == 208) dy = 1;
            if ((dx != 0 || dy != 0) && selected != null) {
                selected.x += dx;
                selected.y += dy;
                cfg.save();
                return;
            }
            if (code == 1) {
                applyPropField();
                cfg.save();
                client.setScreen(parent);
                return;
            }
        }
        super.keyPressed(chr, code);
    }

    @SuppressWarnings("unchecked")
    private ButtonWidget findButton(int id) {
        for (Object o : (List<ButtonWidget>) (List<?>) buttons) {
            ButtonWidget b = (ButtonWidget) o;
            if (b.id == id) return b;
        }
        return null;
    }

    /** Called every frame from render() to apply active drag deltas. */
    /** Mouse position in layout coords (preview-mapped). */
    private void applyDrag(float mouseX, float mouseY) {
        if (!dragging || selected == null || dragZone == Position.NONE) return;
        // Modifiers are live: Alt can be pressed/released mid-drag to flip
        // between resizing and cropping, like OBS.
        dragMode = liveMode();
        if (dragMode == DragMode.NONE) return;
        float dx = mouseX - lastMouseX;
        float dy = mouseY - lastMouseY;
        if (dx == 0.0f && dy == 0.0f) return;

        switch (dragMode) {
            case MOVE:
                selected.x += Math.round(dx);
                selected.y += Math.round(dy);
                break;
            case SCALE_TL:
            case SCALE_TR:
            case SCALE_BL:
            case SCALE_BR:
                if (isShiftDown()) applyFreeStretch(dragMode, mouseX, mouseY);
                else applyCornerScale(dragMode, mouseX, mouseY);
                break;
            case STRETCH_X:
                applyStretchX(mouseX);
                break;
            case STRETCH_Y:
                applyStretchY(mouseY);
                break;
            case CROP_T:
                selected.crop_top_px = clampCrop(dragStartCropTopHolder + Math.round(totalDy(mouseY) / 2.0f));
                break;
            case CROP_B:
                selected.crop_bottom_px = clampCrop(dragStartCropBottomHolder - Math.round(totalDy(mouseY) / 2.0f));
                break;
            case CROP_L:
                selected.crop_left_px = clampCrop(dragStartCropLeftHolder + Math.round(totalDx(mouseX) / 2.0f));
                break;
            case CROP_R:
                selected.crop_right_px = clampCrop(dragStartCropRightHolder - Math.round(totalDx(mouseX) / 2.0f));
                break;
            case CROP_TL:
                selected.crop_left_px = clampCrop(dragStartCropLeftHolder + Math.round(totalDx(mouseX) / 2.0f));
                selected.crop_top_px = clampCrop(dragStartCropTopHolder + Math.round(totalDy(mouseY) / 2.0f));
                break;
            case CROP_TR:
                selected.crop_right_px = clampCrop(dragStartCropRightHolder - Math.round(totalDx(mouseX) / 2.0f));
                selected.crop_top_px = clampCrop(dragStartCropTopHolder + Math.round(totalDy(mouseY) / 2.0f));
                break;
            case CROP_BL:
                selected.crop_left_px = clampCrop(dragStartCropLeftHolder + Math.round(totalDx(mouseX) / 2.0f));
                selected.crop_bottom_px = clampCrop(dragStartCropBottomHolder - Math.round(totalDy(mouseY) / 2.0f));
                break;
            case CROP_BR:
                selected.crop_right_px = clampCrop(dragStartCropRightHolder - Math.round(totalDx(mouseX) / 2.0f));
                selected.crop_bottom_px = clampCrop(dragStartCropBottomHolder - Math.round(totalDy(mouseY) / 2.0f));
                break;
            default:
                break;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /**
     * Uniform corner scaling with the opposite corner anchored (OBS-style).
     * Scale factor comes from the mouse distance to the anchor vs. drag start.
     */
    private void applyCornerScale(DragMode corner, float mouseX, float mouseY) {
        double d0 = Math.hypot(dragOriginX - dragAnchorX, dragOriginY - dragAnchorY);
        double d = Math.hypot(mouseX - dragAnchorX, mouseY - dragAnchorY);
        if (d0 < 2.0 || d < 2.0) return;
        float s = clampScale(dragStartScale * (float) (d / d0));
        int w = (int) (dragStartW * (s / dragStartScale));
        int h = (int) (dragStartH * (s / dragStartScale));
        if (w < 8 || h < 8) return;
        selected.scale = s;
        anchorForCorner(corner, w, h);
    }

    /**
     * Free (non-uniform) corner resize while Shift is held: each axis tracks
     * the pointer independently, anchored at the opposite corner.
     */
    private void applyFreeStretch(DragMode corner, float mouseX, float mouseY) {
        double d0x = Math.abs(dragOriginX - dragAnchorX);
        double d0y = Math.abs(dragOriginY - dragAnchorY);
        double dx = Math.abs(mouseX - dragAnchorX);
        double dy = Math.abs(mouseY - dragAnchorY);
        if (d0x < 2.0 || d0y < 2.0 || dx < 2.0 || dy < 2.0) return;
        float sx = clampStretch(dragStartSX * (float) (dx / d0x));
        float sy = clampStretch(dragStartSY * (float) (dy / d0y));
        int w = (int) (dragStartW * (sx / dragStartSX));
        int h = (int) (dragStartH * (sy / dragStartSY));
        if (w < 8 || h < 8) return;
        selected.stretch_x = sx;
        selected.stretch_y = sy;
        anchorForCorner(corner, w, h);
    }

    /** Single-axis stretch from an edge handle, anchored at the far edge. */
    private void applyStretchX(float mouseX) {
        double d0 = Math.abs(dragOriginX - dragAnchorX);
        double d = Math.abs(mouseX - dragAnchorX);
        if (d0 < 2.0 || d < 2.0) return;
        float sx = clampStretch(dragStartSX * (float) (d / d0));
        int w = (int) (dragStartW * (sx / dragStartSX));
        if (w < 8) return;
        selected.stretch_x = sx;
        if (dragZone == Position.EDGE_L) selected.x = dragAnchorX - w;
    }

    /** Single-axis stretch from an edge handle, anchored at the far edge. */
    private void applyStretchY(float mouseY) {
        double d0 = Math.abs(dragOriginY - dragAnchorY);
        double d = Math.abs(mouseY - dragAnchorY);
        if (d0 < 2.0 || d < 2.0) return;
        float sy = clampStretch(dragStartSY * (float) (d / d0));
        int h = (int) (dragStartH * (sy / dragStartSY));
        if (h < 8) return;
        selected.stretch_y = sy;
        if (dragZone == Position.EDGE_T) selected.y = dragAnchorY - h;
    }

    private void anchorForCorner(DragMode corner, int w, int h) {
        switch (corner) {
            case SCALE_TL:
                selected.x = dragAnchorX - w;
                selected.y = dragAnchorY - h;
                break;
            case SCALE_TR:
                selected.y = dragAnchorY - h;
                break;
            case SCALE_BL:
                selected.x = dragAnchorX - w;
                break;
            case SCALE_BR:
            default:
                break;
        }
    }

    private static float clampStretch(float s) {
        if (s < 0.25f) return 0.25f;
        if (s > 4.0f) return 4.0f;
        return Math.round(s * 100.0f) / 100.0f;
    }

    /** Captures widget origin/size plus the anchored edge/corner. */
    private void captureScaleState() {
        if (selected == null) return;
        int w = OverlayRenderer.sourceWidth(selected);
        int h = OverlayRenderer.sourceHeight(selected);
        dragStartW = w;
        dragStartH = h;
        switch (dragZone) {
            case CORNER_TL:
                dragAnchorX = selected.x + w;
                dragAnchorY = selected.y + h;
                break;
            case CORNER_TR:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y + h;
                break;
            case CORNER_BL:
                dragAnchorX = selected.x + w;
                dragAnchorY = selected.y;
                break;
            case CORNER_BR:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y;
                break;
            case EDGE_T:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y + h;
                break;
            case EDGE_B:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y;
                break;
            case EDGE_L:
                dragAnchorX = selected.x + w;
                dragAnchorY = selected.y;
                break;
            case EDGE_R:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y;
                break;
            default:
                dragAnchorX = selected.x;
                dragAnchorY = selected.y;
                break;
        }
    }

    private float dragStartSX = 1.0f;
    private float dragStartSY = 1.0f;

    private float dragOriginX;
    private float dragOriginY;
    private int dragStartCropTopHolder, dragStartCropBottomHolder,
            dragStartCropLeftHolder, dragStartCropRightHolder;

    private float totalDx(float mouseX) {
        return mouseX - dragOriginX;
    }

    private float totalDy(float mouseY) {
        return mouseY - dragOriginY;
    }

    private void syncDragOrigin(float mouseX, float mouseY) {
        dragOriginX = mouseX;
        dragOriginY = mouseY;
        dragStartCropTopHolder = selected.crop_top_px;
        dragStartCropBottomHolder = selected.crop_bottom_px;
        dragStartCropLeftHolder = selected.crop_left_px;
        dragStartCropRightHolder = selected.crop_right_px;
    }

    private static int clampCrop(int v) {
        if (v < 0) return 0;
        if (v > 120) return 120;
        return v;
    }

    // ------------------------------------------------------------------
    // Hit testing (topmost source first)
    // ------------------------------------------------------------------

    private OverlayConfig.Source pickWidget(float lx, float ly) {
        List<OverlayConfig.Source> sources = cfg.activeScene().sources;
        for (int i = sources.size() - 1; i >= 0; i--) {
            OverlayConfig.Source s = sources.get(i);
            if (s == null || !s.enabled) continue;
            float x0 = toScreenX(s.x) - 4;
            float y0 = toScreenY(s.y) - 4;
            float x1 = toScreenX(s.x + OverlayRenderer.sourceWidth(s)) + 4;
            float y1 = toScreenY(s.y + OverlayRenderer.sourceHeight(s)) + 4;
            if (inBoxF(toScreenX(lx), toScreenY(ly), x0, y0, x1, y1)) return s;
        }
        return null;
    }

    private Position pickPosition(float lx, float ly) {
        if (selected == null) {
            OverlayConfig.Source hit = pickWidget(lx, ly);
            if (hit == null) return Position.NONE;
            selected = hit;
        }
        float mx = toScreenX(lx);
        float my = toScreenY(ly);
        float x = toScreenX(selected.x);
        float y = toScreenY(selected.y);
        float w = OverlayRenderer.sourceWidth(selected) * previewK;
        float h = OverlayRenderer.sourceHeight(selected) * previewK;
        float r = 6.0f;
        if (inBoxF(mx, my, x - r, y - r, x + r, y + r)) return Position.CORNER_TL;
        if (inBoxF(mx, my, x + w - r, y - r, x + w + r, y + r)) return Position.CORNER_TR;
        if (inBoxF(mx, my, x - r, y + h - r, x + r, y + h + r)) return Position.CORNER_BL;
        if (inBoxF(mx, my, x + w - r, y + h - r, x + w + r, y + h + r)) return Position.CORNER_BR;
        // Edge midpoints: crop handles for croppable sources, axis-stretch
        // handles for everything else (OBS-style green/red edges).
        if (inBoxF(mx, my, x - 4, y - 7, x + w + 4, y + 3)) return Position.EDGE_T;
        if (inBoxF(mx, my, x - 4, y + h - 3, x + w + 4, y + h + 7)) return Position.EDGE_B;
        if (inBoxF(mx, my, x - 7, y - 4, x + 3, y + h + 4)) return Position.EDGE_L;
        if (inBoxF(mx, my, x + w - 3, y - 4, x + w + 7, y + h + 4)) return Position.EDGE_R;
        return Position.BODY;
    }

    private static boolean inBoxF(float mx, float my, float x0, float y0, float x1, float y1) {
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    private static boolean inBox(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    // ------------------------------------------------------------------
    // Closed preview panel: the layout space (full screen, WxH) is shown
    // letterboxed inside a bounded rect, OBS-style. All canvas rendering
    // is scissored to that rect; editing maps through it.
    // ------------------------------------------------------------------

    float previewX, previewY, previewW, previewH, previewK = 1.0f;

    void computePreview() {
        int zoomY = dockY() - 32;
        int ax0 = 8;
        int ay0 = TOP_H + 4;
        int ax1 = Math.max(ax0 + 10, width - 8);
        int ay1 = Math.max(ay0 + 10, zoomY - 2);
        float k = Math.min((ax1 - ax0) / (float) width, (ay1 - ay0) / (float) height);
        if (k <= 0.05f) k = 0.05f;
        if (k > 1.0f) k = 1.0f;
        previewK = k;
        previewW = width * k;
        previewH = height * k;
        previewX = ax0 + ((ax1 - ax0) - previewW) / 2.0f;
        previewY = ay0 + ((ay1 - ay0) - previewH) / 2.0f;
    }

    float toScreenX(float lx) {
        return previewX + lx * previewK;
    }

    float toScreenY(float ly) {
        return previewY + ly * previewK;
    }

    boolean inPreview(float mx, float my) {
        return mx >= previewX && mx <= previewX + previewW
                && my >= previewY && my <= previewY + previewH;
    }

    private void scissorPreview(boolean on) {
        try {
            if (!on) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                return;
            }
            float f = (float) org.lwjgl.opengl.Display.getWidth() / (float) width;
            int x = (int) (previewX * f);
            int y = (int) (org.lwjgl.opengl.Display.getHeight() - (previewY + previewH) * f);
            int w = Math.max(1, (int) (previewW * f));
            int h = Math.max(1, (int) (previewH * f));
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, w, h);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        int dy = dockY();
        int dh = dockH();
        computePreview();
        // Preview stage backdrop (dark) + bounded black panel.
        OverlayRenderer.enableBlend();
        try {
            OverlayRenderer.fill(8, TOP_H + 4, width - 8, dockY() - 34, 0xFF0E0E10);
            OverlayRenderer.fill((int) previewX, (int) previewY,
                    (int) (previewX + previewW), (int) (previewY + previewH), 0xFF000000);
        } finally {
            OverlayRenderer.disableBlend();
        }
        OverlayRenderer.drawBorder((int) previewX, (int) previewY,
                (int) (previewX + previewW), (int) (previewY + previewH), 0xFF45484D);
        // Sources render inside the closed preview: scissored quad with the
        // layout space scaled to fit, so the panel shows what goes out.
        // Live game pixels first (what the stream sees), else backdrop art.
        OverlayConfig.Scene scene = cfg.activeScene();
        try {
            com.obsnomore.render.GameViewCapture.update(client);
            boolean liveView = com.obsnomore.render.GameViewCapture.hasView();
            net.minecraft.util.Identifier bgId = null;
            if (!liveView) {
                try {
                    boolean inGame = false;
                    try {
                        inGame = client != null && client.world != null;
                    } catch (Throwable ignored) {
                    }
                    if (parent != null) {
                        String pn = parent.getClass().getName();
                        if (pn.contains("GameMenuScreen")) inGame = true;
                    }
                    bgId = inGame ? PreviewTextures.getPaused() : PreviewTextures.getGameplay();
                } catch (Throwable ignored) {
                }
            }
            scissorPreview(true);
            GL11.glPushMatrix();
            GL11.glTranslatef(previewX, previewY, 0.0f);
            GL11.glScalef(previewK, previewK, 1.0f);
            try {
                if (liveView) {
                    try {
                        com.obsnomore.render.GameViewCapture.draw(0, 0, width, height);
                    } catch (Throwable ignored) {
                    }
                } else if (bgId != null) {
                    try {
                        PreviewTextures.drawFullscreen(bgId, width, height);
                    } catch (Throwable ignored) {
                    }
                }
                applyDrag((mouseX - previewX) / previewK, (mouseY - previewY) / previewK);

                OverlayRenderer.enableBlend();
                try {
                    for (OverlayConfig.Source src : scene.sources) {
                        if (src != null && src.enabled) OverlayRenderer.renderSource(client, src, true);
                    }
                } finally {
                    OverlayRenderer.disableBlend();
                }
            } finally {
                GL11.glPopMatrix();
                scissorPreview(false);
            }
        } catch (Throwable ignored) {
            try {
                scissorPreview(false);
            } catch (Throwable ignored2) {
            }
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        if (!showSettings) {
            boolean cropMode = cropModifier() && croppable(selected);
            for (OverlayConfig.Source src : scene.sources) {
                if (src == null || !src.enabled) continue;
                drawSelectionBox(src, src == selected, cropMode && src == selected);
            }
            if (cropMode) drawCropLabels(selected);
            if (propField != null) propField.render();
            drawObsChrome(scene);
        } else {
            int fw = Math.min(340, width - 30);
            int fx = (width - fw) / 2;
            OverlayRenderer.enableBlend();
            try {
                OverlayRenderer.fill(fx - 12, 30, fx + fw + 12, height - 16, 0xE0101010);
            } finally {
                OverlayRenderer.disableBlend();
            }
            OverlayRenderer.drawBorder(fx - 12, 30, fx + fw + 12, height - 16, 0xFF55FFFF);
            OverlayConfig.Source ps = selected;
            String ptitle = ps == null ? "Properties" : "Properties: " + ps.name
                    + (ps.type == null ? "" : " (" + ps.type + ")");
            textRenderer.draw(ptitle, fx, 34, 0xFF55FFFF);
            int ly = 56;
            textRenderer.draw("Name:", fx, ly - 10, 0xFFAAAAAA);
            if (propNameField != null) propNameField.render();
            ly += 26;
            textRenderer.draw("X:", fx, ly - 10, 0xFFAAAAAA);
            if (propXField != null) propXField.render();
            ly += 26;
            textRenderer.draw("Y:", fx, ly - 10, 0xFFAAAAAA);
            if (propYField != null) propYField.render();
            ly += 26;
            textRenderer.draw("Scale (0.25 - 4.0):", fx, ly - 10, 0xFFAAAAAA);
            if (propScaleField != null) propScaleField.render();
            ly += 26;
            if (propContentField != null) {
                textRenderer.draw(propContentLabel(ps), fx, ly - 10, 0xFFAAAAAA);
                propContentField.render();
            }
        }

        super.render(mouseX, mouseY, tickDelta);
        if (!showSettings) drawListSelection();
    }

    // OBS-blue outline around the active scene row and selected source row.
    private void drawListSelection() {
        OverlayRenderer.enableBlend();
        try {
            if (cfg.active_scene >= 0 && cfg.active_scene < 3
                    && sceneRects[cfg.active_scene][0] >= 0) {
                int[] r = sceneRects[cfg.active_scene];
                outlineBlue(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1);
            }
            if (selected != null) {
                List<OverlayConfig.Source> all = cfg.activeScene().sources;
                int idx = all.indexOf(selected);
                if (idx >= 0 && idx < 3 && srcRects[idx][0] >= 0) {
                    int[] r = srcRects[idx];
                    outlineBlue(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1);
                }
            }
        } finally {
            OverlayRenderer.disableBlend();
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void outlineBlue(int x0, int y0, int x1, int y1) {
        OverlayRenderer.drawBorder(x0, y0, x1, y1, 0xFF2E6BF0);
        OverlayRenderer.drawBorder(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF2E6BF0);
    }

    // ------------------------------------------------------------------
    // OBS Studio chrome: menu bar, preview toolbar, info bar, docks, status.
    // ------------------------------------------------------------------

    private void drawObsChrome(OverlayConfig.Scene scene) {
        int dy = dockY();
        int dh = dockH();
        // Column geometry (matches init()).
        int sx = 2;
        int sw = Math.max(64, width * 18 / 100);
        int sox = sx + sw + 2;
        int sow = Math.max(84, width * 25 / 100);
        int mx = sox + sow + 2;
        int mw = Math.max(64, width * 19 / 100);
        int tx = mx + mw + 2;
        int tw = Math.max(52, width * 14 / 100);
        int cx0 = tx + tw + 2;
        OverlayRenderer.enableBlend();
        try {
            // Top menu bar, per-dock columns (1px dirt gaps = dividers),
            // preview toolbar, info bar and status bar. Textured tinted
            // dirt: rasterizes everywhere vanilla dirt does.
            OverlayRenderer.fill(0, 0, width, TOP_H, 0xFF2B2D30);
            OverlayRenderer.fill(0, TOP_H - 1, width, TOP_H, 0xFF45484D);
            OverlayRenderer.fill(sx, dy, sx + sw, height - STATUS_H, 0xFF2B2D30);
            OverlayRenderer.fill(sox, dy, sox + sow, height - STATUS_H, 0xFF2B2D30);
            OverlayRenderer.fill(mx, dy, mx + mw, height - STATUS_H, 0xFF2B2D30);
            OverlayRenderer.fill(tx, dy, tx + tw, height - STATUS_H, 0xFF2B2D30);
            OverlayRenderer.fill(cx0, dy, width, height - STATUS_H, 0xFF2B2D30);
            OverlayRenderer.fill(0, dy - 1, width, dy, 0xFF45484D);
            OverlayRenderer.fill(0, height - STATUS_H, width, height, 0xFF1B1D1F);
            OverlayRenderer.fill(0, height - STATUS_H - 1, width, height - STATUS_H, 0xFF45484D);
        } finally {
            OverlayRenderer.disableBlend();
        }
        // Menu bar text.
        textRenderer.draw("File  Edit  View  Docks  Profile  Collection  Tools  Help", 4, 3, 0xFFCCCCCC);
        String ver = "OBS No More";
        textRenderer.draw(ver, width - textRenderer.getStringWidth(ver) - 4, 3, 0xFF888888);
        // Dock headers.
        textRenderer.draw("Scenes", sx + 3, dy + 3, 0xFFBBBBBB);
        textRenderer.draw("Sources", sox + 3, dy + 3, 0xFFBBBBBB);
        textRenderer.draw("Mixer", mx + 3, dy + 3, 0xFFBBBBBB);
        textRenderer.draw("Transition", tx + 3, dy + 3, 0xFFBBBBBB);
        textRenderer.draw("Controls", cx0 + 3, dy + 3, 0xFFBBBBBB);
        if (dy + 63 <= dy + dh) {
            textRenderer.draw(mixerSummary(), mx + 3, dy + 55, 0xFF888888);
        }
        if (dy + 52 <= dy + dh) {
            String tr = (cfg.streaming.transition == null ? "Cut" : cfg.streaming.transition)
                    + " " + cfg.streaming.transition_ms + "ms";
            textRenderer.draw(tr, tx + 3, dy + 54 > height - STATUS_H - 8 ? height - STATUS_H - 9 : dy + dh - 20, 0xFF888888);
        }
        // Preview toolbar row (zoom display + canvas scale note).
        int zoomY = dy - 32;
        OverlayRenderer.enableBlend();
        try {
            OverlayRenderer.fill(0, zoomY, width, zoomY + 12, 0xFF232529);
            OverlayRenderer.fill(0, zoomY + 11, width, zoomY + 12, 0xFF45484D);
        } finally {
            OverlayRenderer.disableBlend();
        }
        textRenderer.draw("-  " + Math.round(previewK * 100.0f) + "%  +   Scaled (preview)", 4, zoomY + 2, 0xFF999999);
        // Info bar: selection summary + Properties/Filters (like OBS).
        int infoY = dy - 18;
        OverlayRenderer.enableBlend();
        try {
            OverlayRenderer.fill(0, infoY, width, infoY + 16, 0xFF2B2D30);
            OverlayRenderer.fill(0, infoY + 15, width, infoY + 16, 0xFF45484D);
        } finally {
            OverlayRenderer.disableBlend();
        }
        String hint = selectionLine();
        int maxHint = width - 184;
        if (maxHint > 40 && textRenderer.getStringWidth(hint) > maxHint) {
            hint = textRenderer.trimToWidth(hint, maxHint);
        }
        textRenderer.draw(hint, 4, infoY + 4, 0xFFDDDDDD);
        drawMixerSlider(mx, dy, dh);
        // Status bar: live state, scene, fps, timers.
        String st = statusLine(scene);
        textRenderer.draw(st, 4, height - 10, 0xFFAAAAAA);
    }

    // Vertical gain slider visual in the Mixer dock (right edge).
    private void drawMixerSlider(int mx, int dy, int dh) {
        int mw = Math.max(64, width * 19 / 100);
        int sx = mx + mw - 14;
        int sy0 = dy + 13;
        int sy1 = Math.min(dy + dh - 4, dy + 52);
        if (sy1 - sy0 < 16 || sx + 8 > width) return;
        double gain = 0.0;
        boolean live = false;
        try {
            if (cfg.streaming.desktop.enabled) {
                gain = Math.max(gain, cfg.streaming.desktop.gain);
                live = true;
            }
            if (cfg.streaming.mic.enabled) {
                gain = Math.max(gain, cfg.streaming.mic.gain);
                live = true;
            }
        } catch (Throwable ignored) {
        }
        double frac = Math.max(0.0, Math.min(1.0, gain / 2.0));
        int fillH = (int) ((sy1 - sy0) * frac);
        OverlayRenderer.enableBlend();
        try {
            OverlayRenderer.fill(sx, sy0, sx + 7, sy1, 0xFF101214);
            if (fillH > 0) {
                OverlayRenderer.fill(sx, sy1 - fillH, sx + 7, sy1,
                        live ? 0xFF4C8DFF : 0xFF555555);
            }
            OverlayRenderer.fill(sx, sy0, sx + 7, sy0 + 1, 0xFF666666);
            int mid = (sy0 + sy1) / 2;
            OverlayRenderer.fill(sx, mid, sx + 7, mid + 1, 0xFF666666);
            if (!live) {
                textRenderer.draw("x", sx + 1, sy1 - 8, 0xFFFF5555);
            }
        } finally {
            OverlayRenderer.disableBlend();
        }
    }

    private String mixerSummary() {
        try {
            int d = (int) Math.round(cfg.streaming.desktop.gain * 100);
            int m = (int) Math.round(cfg.streaming.mic.gain * 100);
            return "D" + d + " M" + m;
        } catch (Throwable t) {
            return "";
        }
    }

    private String statusLine(OverlayConfig.Scene scene) {
        String sname = scene == null || scene.name == null ? "Scene" : scene.name;
        String live = StreamManager.isStreaming() ? "LIVE" : "OFFLINE";
        String rec = StreamManager.isRecording() ? "REC " + fmtTime(StreamManager.recordElapsed()) : "rec off";
        String line = live + " " + fmtTime(StreamManager.streamElapsed()) + " | " + rec
                + " | " + sname + " | " + cfg.pov_mode
                + " | " + cfg.streaming.fps + "fps | " + cfg.streaming.video_bitrate_k + "kbps";
        int maxW = width - 8;
        if (textRenderer.getStringWidth(line) > maxW) line = textRenderer.trimToWidth(line, maxW);
        return line;
    }

    private static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        String two = m < 10 ? "0" + m : "" + m;
        String three = sec < 10 ? "0" + sec : "" + sec;
        return (h < 10 ? "0" + h : "" + h) + ":" + two + ":" + three;
    }

    private String selectionLine() {
        if (selected == null) {
            return "Drag corners: resize (Shift=free) - edges: stretch - Alt/Option: crop - arrows: nudge";
        }
        String mode = "";
        if (dragging && dragZone != Position.NONE) {
            DragMode m = liveMode();
            if (m != DragMode.NONE && m.name().startsWith("CROP")) mode = "[CROP] ";
            else if (isShiftDown() && dragZone.name().startsWith("CORNER")) mode = "[FREE] ";
        } else if (cropModifier() && croppable(selected)) {
            mode = "[CROP] ";
        }
        String info = mode + "'" + selected.name + "' (" + selected.type + ") x=" + selected.x
                + " y=" + selected.y + " scale=" + String.format("%.1f", selected.scale);
        if (selected.stretch_x != 1.0f || selected.stretch_y != 1.0f) {
            info += " free=" + String.format("%.2f", selected.stretch_x)
                    + "x" + String.format("%.2f", selected.stretch_y);
        }
        if (croppable(selected)) {
            info += " crop T=" + selected.crop_top_px + " B=" + selected.crop_bottom_px
                    + " L=" + selected.crop_left_px + " R=" + selected.crop_right_px;
        }
        String extra = com.obsnomore.ObsNoMore.lastAction();
        if (!extra.isEmpty()) info += "  |  " + extra;
        return info;
    }

    private void drawSelectionBox(OverlayConfig.Source s, boolean isSelected, boolean cropMode) {
        int x = (int) toScreenX(s.x);
        int y = (int) toScreenY(s.y);
        int w = Math.max(2, (int) (OverlayRenderer.sourceWidth(s) * previewK));
        int h = Math.max(2, (int) (OverlayRenderer.sourceHeight(s) * previewK));
        int color = isSelected
                ? (cropMode ? HANDLE_CROP : HANDLE_RESIZE)
                : 0x8000AA00;
        OverlayRenderer.enableBlend();
        try {
            OverlayRenderer.drawBorder(x, y, x + w, y + h, color);
            if (isSelected) {
                int hc = cropMode ? HANDLE_CROP : HANDLE_RESIZE;
                // 4 corner handles.
                dotHandle(x - 2, y - 2, hc);
                dotHandle(x + w - 3, y - 2, hc);
                dotHandle(x - 2, y + h - 3, hc);
                dotHandle(x + w - 3, y + h - 3, hc);
                // 4 edge-midpoint handles (OBS transform dots).
                dotHandle(x + w / 2 - 2, y - 2, hc);
                dotHandle(x + w / 2 - 2, y + h - 3, hc);
                dotHandle(x - 2, y + h / 2 - 2, hc);
                dotHandle(x + w - 3, y + h / 2 - 2, hc);
            }
        } finally {
            OverlayRenderer.disableBlend();
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** OBS crop readout: live px amounts at each cropped edge. */
    private void drawCropLabels(OverlayConfig.Source s) {
        if (s == null) return;
        int x = (int) toScreenX(s.x);
        int y = (int) toScreenY(s.y);
        int w = Math.max(2, (int) (OverlayRenderer.sourceWidth(s) * previewK));
        int h = Math.max(2, (int) (OverlayRenderer.sourceHeight(s) * previewK));
        if (s.crop_top_px > 0) {
            textRenderer.draw(s.crop_top_px + " px", x + w / 2 - 12, y - 10, HANDLE_CROP);
        }
        if (s.crop_bottom_px > 0) {
            textRenderer.draw(s.crop_bottom_px + " px", x + w / 2 - 12, y + h + 2, HANDLE_CROP);
        }
        if (s.crop_left_px > 0) {
            textRenderer.draw(s.crop_left_px + " px", x - 30, y + h / 2 - 4, HANDLE_CROP);
        }
        if (s.crop_right_px > 0) {
            textRenderer.draw(s.crop_right_px + " px", x + w + 4, y + h / 2 - 4, HANDLE_CROP);
        }
    }

    private static void dotHandle(int x, int y, int color) {
        OverlayRenderer.fill(x, y, x + 5, y + 5, color);
        OverlayRenderer.drawBorder(x, y, x + 5, y + 5, 0xFF000000);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long timeSinceClick) {
        super.mouseDragged(mouseX, mouseY, button, timeSinceClick);
    }

    public static int obsButtonId() {
        return OBS_BUTTON_ID;
    }

    /** Opens the editor from menu mixins (first run goes via the wizard). */
    public static void open(MinecraftClient client, Screen parent) {
        WebFetcher.get().start();
        if (!OverlayConfig.get().setup_done) {
            client.setScreen(new SetupWizardScreen(parent,
                    new StreamOverlayEditorScreen(parent)));
        } else {
            client.setScreen(new StreamOverlayEditorScreen(parent));
        }
    }

    private void queueRefresh() {
        refreshQueued = true;
    }
}
