package com.obsnomore.gui;

import com.obsnomore.config.OverlayConfig;
import com.obsnomore.stream.AudioTester;
import com.obsnomore.stream.DeviceDetect;
import com.obsnomore.stream.FFmpeg;
import com.obsnomore.stream.FilterGraph;
import com.obsnomore.stream.StreamManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * First-run setup wizard: guarantees the minimal scene works — the game
 * window capture plus game audio — before anything else.
 *
 * <p>Page 0: welcome. Page 1: game window region (detect + test record).
 * Page 2: game audio device (detect + cycle + listen test). Page 3: summary.
 * Finish marks {@code setup_done} and opens the scene editor.
 */
public class SetupWizardScreen extends Screen {
    private final Screen parentMenu;
    private final Screen next;
    private OverlayConfig cfg;
    private boolean refreshQueued = false;
    private int page;

    private final List<TextFieldWidget> fields = new ArrayList<TextFieldWidget>();
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget wField;
    private TextFieldWidget hField;
    private TextFieldWidget audioField;

    private String status = "";
    private String audioStatus = "";

    public SetupWizardScreen(Screen parentMenu, Screen next) {
        this.parentMenu = parentMenu;
        this.next = next;
        this.cfg = OverlayConfig.get();
    }

    @Override
    public void init() {
        cfg = OverlayConfig.get();
        buttons.clear();
        fields.clear();
        xField = yField = wField = hField = audioField = null;
        if (page == 0) {
            buttons.add(new ButtonWidget(1, width / 2 - 105, height - 60, 100, 20, "Skip"));
            buttons.add(new ButtonWidget(2, width / 2 + 5, height - 60, 100, 20, "Start setup"));
        } else if (page == 1) {
            xField = numField(cfg.capture.x, 150, 70);
            yField = numField(cfg.capture.y, 230, 70);
            wField = numField(cfg.capture.w, 150, 94);
            hField = numField(cfg.capture.h, 230, 94);
            buttons.add(new ButtonWidget(10, 150, 120, 160, 20, "Detect window"));
            buttons.add(new ButtonWidget(11, 150, 144, 160, 20, "Test capture"));
            buttons.add(new ButtonWidget(3, 10, height - 30, 100, 20, "Back"));
            buttons.add(new ButtonWidget(4, width - 110, height - 30, 100, 20, "Next"));
        } else if (page == 2) {
            audioField = new TextFieldWidget(textRenderer, 150, 70, 200, 20);
            audioField.setMaxLength(256);
            String dev = cfg.streaming.desktop.enabled && cfg.streaming.desktop.device != null
                    && !cfg.streaming.desktop.device.isEmpty()
                    ? cfg.streaming.desktop.device
                    : DeviceDetect.gameAudioDevice();
            if (dev == null) dev = "";
            audioField.setText(dev);
            fields.add(audioField);
            buttons.add(new ButtonWidget(20, 150, 94, 96, 20, "Detect"));
            buttons.add(new ButtonWidget(21, 250, 94, 96, 20, "Cycle"));
            buttons.add(new ButtonWidget(22, 150, 118, 96, 20, "Test (3s)"));
            buttons.add(new ButtonWidget(23, 250, 118, 96, 20, "Virtual"));
            buttons.add(new ButtonWidget(3, 10, height - 30, 100, 20, "Back"));
            buttons.add(new ButtonWidget(4, width - 110, height - 30, 100, 20, "Next"));
        } else {
            buttons.add(new ButtonWidget(3, 10, height - 30, 100, 20, "Back"));
            buttons.add(new ButtonWidget(5, width - 110, height - 30, 100, 20, "Finish"));
        }
    }

    private TextFieldWidget numField(int v, int x, int y) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 60, 20);
        f.setMaxLength(6);
        f.setText(String.valueOf(v));
        fields.add(f);
        return f;
    }

    private static int num(TextFieldWidget f, int fallback) {
        if (f == null) return fallback;
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void pushPage() {
        try {
            if (page == 1) {
                cfg.capture.x = num(xField, cfg.capture.x);
                cfg.capture.y = num(yField, cfg.capture.y);
                cfg.capture.w = num(wField, cfg.capture.w);
                cfg.capture.h = num(hField, cfg.capture.h);
            } else if (page == 2 && audioField != null) {
                cfg.streaming.desktop.device = audioField.getText().trim();
                cfg.streaming.desktop.enabled = !cfg.streaming.desktop.device.isEmpty();
            }
            cfg.save();
        } catch (Throwable ignored) {
        }
    }

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
        pushPage();
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
        boolean handled = false;
        for (TextFieldWidget f : fields) {
            if (f != null && f.isFocused() && f.keyPressed(chr, code)) handled = true;
        }
        if (handled) return;
        if (code == 1) {
            pushPage();
            client.setScreen(parentMenu);
            return;
        }
        super.keyPressed(chr, code);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void buttonClicked(ButtonWidget button) {
        pushPage();
        switch (button.id) {
            case 1:
                cfg.setup_done = true;
                cfg.save();
                client.setScreen(next);
                break;
            case 2:
                page = 1;
                status = "";
                queueRefresh();
                break;
            case 3:
                page = Math.max(0, page - 1);
                status = "";
                audioStatus = "";
                queueRefresh();
                break;
            case 4:
                page = Math.min(3, page + 1);
                status = "";
                audioStatus = "";
                queueRefresh();
                break;
            case 5:
                cfg.setup_done = true;
                cfg.save();
                client.setScreen(next);
                break;
            case 10: {
                int[] pos = DeviceDetect.gameWindowAt();
                if (pos != null) {
                    cfg.capture.x = pos[0];
                    cfg.capture.y = pos[1];
                    cfg.save();
                    status = "window at " + pos[0] + "," + pos[1];
                    queueRefresh();
                } else {
                    status = "no game window found (xdotool missing?) - set x/y by hand";
                }
                break;
            }
            case 11:
                status = "recording 3s test clip...";
                CaptureTester.test(new CaptureTester.Callback() {
                    @Override
                    public void done(CaptureTester.Result r) {
                        status = r.ok ? "PASS: " + r.detail : "FAIL: " + r.detail;
                    }
                });
                break;
            case 20: {
                String dev = DeviceDetect.gameAudioDevice();
                if (dev != null && !dev.isEmpty() && audioField != null) {
                    audioField.setText(dev);
                    status = "";
                    audioStatus = "detected: " + dev;
                } else {
                    audioStatus = "nothing detected - pick from Cycle or type it";
                }
                break;
            }
            case 21: {
                List<String> cands = DeviceDetect.audioCandidates(true);
                if (!cands.isEmpty() && audioField != null) {
                    String cur = audioField.getText();
                    int i = cands.indexOf(cur);
                    audioField.setText(cands.get((i + 1) % cands.size()));
                    audioStatus = "";
                } else {
                    audioStatus = "no candidates found";
                }
                break;
            }
            case 22: {
                final String dev = audioField == null ? "" : audioField.getText().trim();
                if (dev.isEmpty()) {
                    audioStatus = "enter a device first";
                    break;
                }
                audioStatus = "listening 3s...";
                AudioTester.test(FFmpeg.locate(), dev, 3, new AudioTester.Callback() {
                    @Override
                    public void done(AudioTester.Result r) {
                        audioStatus = r.verdict + (r.detail.isEmpty() ? "" : " (" + r.detail + ")");
                    }
                });
                break;
            }
            case 23: {
                pushPage();
                com.obsnomore.stream.VirtualAudio.Result vr =
                        com.obsnomore.stream.VirtualAudio.ensure();
                if (vr.ok && vr.device != null && !vr.device.isEmpty()
                        && audioField != null) {
                    audioField.setText(vr.device);
                    cfg.streaming.desktop.device = vr.device;
                    cfg.streaming.desktop.enabled = true;
                    cfg.save();
                }
                audioStatus = vr.note == null ? "" : vr.note;
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        for (TextFieldWidget f : fields) {
            if (f != null) f.render();
        }
        super.render(mouseX, mouseY, tickDelta);
        int cx = width / 2;
        if (page == 0) {
            center(40, "OBS No More setup", 0xFF55FFFF);
            center(58, "Minimal scene = game window + game audio.", 0xFFFFFFFF);
            center(70, "This wizard makes sure both actually work.", 0xFFAAAAAA);
            center(92, "No display capture, no window capture.", 0xFF777777);
            center(102, "Only Minecraft, devices and your overlays.", 0xFF777777);
        } else if (page == 1) {
            left(30, "Step 1/3 - capture the game window", 0xFF55FFFF);
            left(58, "x", 0xFFAAAAAA);
            left(82, "y", 0xFFAAAAAA);
            left(58 + 80, "w", 0xFFAAAAAA);
            left(82 + 80, "h", 0xFFAAAAAA);
            left(162, "Game window auto-captured; region is fallback.", 0xFF777777);
            if (!status.isEmpty()) left(168, status, 0xFF55FFFF);
        } else if (page == 2) {
            left(30, "Step 2/3 - capture game audio", 0xFF55FFFF);
            left(58, "Desktop (loopback) device:", 0xFFFFFFFF);
            left(142, FFmpeg.audioHint(), 0xFF777777);
            centerFit(154, "Need tools? " + com.obsnomore.stream.InstallGuide.audioInstall()
                    + "  (wiki: Audio-Setup)", 0xFF777777);
            if (!audioStatus.isEmpty()) left(166, audioStatus, 0xFF55FFFF);
        } else {
            center(50, "Minimal scene ready.", 0xFF55FFFF);
            center(66, "Game window + game audio verified above.", 0xFFFFFFFF);
            center(82, "Add cameras, text, and images", 0xFFAAAAAA);
            center(94, "anytime in the scene editor.", 0xFFAAAAAA);
        }
    }

    private void center(int y, String s, int color) {
        textRenderer.draw(s, width / 2 - textRenderer.getStringWidth(s) / 2, y, color);
    }

    private void left(int y, String s, int color) {
        textRenderer.draw(s, 10, y, color);
    }

    private void centerFit(int y, String s, int color) {
        if (textRenderer.getStringWidth(s) > width - 20) {
            s = textRenderer.trimToWidth(s, width - 20);
        }
        textRenderer.draw(s, 10, y, color);
    }

    @Override
    public boolean shouldPauseGame() {
        return true;
    }

    // ------------------------------------------------------------------
    // Tiny built-in capture check (single ffmpeg run, daemon thread).
    // ------------------------------------------------------------------

    static final class CaptureTester {
        static final class Result {
            boolean ok;
            String detail = "";
        }

        interface Callback {
            void done(Result r);
        }

        static void test(final Callback cb) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    cb.done(CaptureTester.execute());
                }
            }, "OBSNoMore-capture-test");
            t.setDaemon(true);
            t.start();
        }

        private static Result execute() {
            Result r = new Result();
            OverlayConfig cfg = OverlayConfig.get();
            String ffmpeg = FFmpeg.locate();
            if (ffmpeg.isEmpty()) {
                r.detail = "no ffmpeg binary";
                return r;
            }
            File out = new File(FilterGraph.dir(), "wizard-test.mp4");
            List<String> a = new ArrayList<String>();
            a.add(ffmpeg);
            a.add("-hide_banner");
            a.add("-loglevel");
            a.add("error");
            a.add("-y");
            // Reuse the exact capture input + 720p scale from real sessions.
            List<String> probe = new ArrayList<String>();
            if (!StreamManager.buildCaptureForTest(probe, cfg)) {
                r.detail = "game window not found (fix window capture or set game_only=false)";
                return r;
            }
            a.addAll(probe);
            a.add("-t");
            a.add("3");
            a.add("-vf");
            a.add("scale=640:360:flags=bilinear");
            a.add("-c:v");
            a.add("libx264");
            a.add("-preset");
            a.add("ultrafast");
            a.add("-pix_fmt");
            a.add("yuv420p");
            a.add(out.getAbsolutePath());
            try {
                Process p = new ProcessBuilder(a).start();
                try {
                    p.waitFor();
                } catch (InterruptedException ignored) {
                }
                long size = out.isFile() ? out.length() : 0;
                if (size > 50000 && validVideo(ffmpeg, out)) {
                    r.ok = true;
                    r.detail = "clip " + (size / 1024) + "KB, decodes clean";
                } else {
                    r.detail = "clip too small (" + size + "B) - fix region/ffmpeg";
                }
            } catch (Throwable t) {
                r.detail = String.valueOf(t.getMessage());
            }
            return r;
        }

        private static boolean validVideo(String ffmpeg, File f) {
            if (f == null || !f.isFile()) return false;
            Process p = null;
            try {
                p = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error",
                        "-v", "error", "-i", f.getAbsolutePath(),
                        "-f", "null", "-").start();
                try {
                    p.waitFor();
                } catch (InterruptedException ignored) {
                }
                return p.exitValue() == 0;
            } catch (Throwable t) {
                return false;
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

    private void queueRefresh() {
        refreshQueued = true;
    }
}
