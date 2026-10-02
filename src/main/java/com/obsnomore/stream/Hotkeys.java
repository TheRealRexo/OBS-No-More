package com.obsnomore.stream;

import com.obsnomore.config.OverlayConfig;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Discrete OBS-style hotkeys with in-GUI rebinding.
 * Polled once per client tick with edge detection; suppressed while the
 * player is typing in one of our text fields or capturing a new binding.
 */
public final class Hotkeys {
    private Hotkeys() {
    }

    public static final String[] ACTIONS = {
        OverlayConfig.HK_START_STREAM,
        OverlayConfig.HK_STOP_STREAM,
        OverlayConfig.HK_START_RECORD,
        OverlayConfig.HK_STOP_RECORD,
        OverlayConfig.HK_PAUSE_RECORD,
        OverlayConfig.HK_NEXT_SCENE,
        OverlayConfig.HK_PREV_SCENE,
    };

    public static String label(String action) {
        if (action.equals(OverlayConfig.HK_START_STREAM)) return "Start Streaming";
        if (action.equals(OverlayConfig.HK_STOP_STREAM)) return "Stop Streaming";
        if (action.equals(OverlayConfig.HK_START_RECORD)) return "Start Recording";
        if (action.equals(OverlayConfig.HK_STOP_RECORD)) return "Stop Recording";
        if (action.equals(OverlayConfig.HK_PAUSE_RECORD)) return "Pause Recording";
        if (action.equals(OverlayConfig.HK_NEXT_SCENE)) return "Next Scene";
        if (action.equals(OverlayConfig.HK_PREV_SCENE)) return "Prev Scene";
        return action;
    }

    private static final Map<Integer, Boolean> DOWN = new HashMap<Integer, Boolean>();
    private static volatile String capturingAction;
    private static volatile boolean suppress;

    public interface Handler {
        /** Return true if the keypress was consumed. */
        boolean fire(String action);

        boolean uiWantsKeys();
    }

    private static Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    public static void setCapturing(String actionOrNull) {
        capturingAction = actionOrNull;
    }

    public static String capturing() {
        return capturingAction;
    }

    public static void setSuppress(boolean s) {
        suppress = s;
    }

    /** Called by the GUI capture row: assigns the pressed key. */
    public static boolean captureKey(int code) {
        String action = capturingAction;
        if (action == null) return false;
        if (code == 1) {
            capturingAction = null;
            return true;
        }
        OverlayConfig.get().hotkeys.put(action, code);
        OverlayConfig.get().save();
        capturingAction = null;
        return true;
    }

    public static String keyName(int code) {
        try {
            String n = Keyboard.getKeyName(code);
            return n == null ? ("key#" + code) : n;
        } catch (Throwable t) {
            return "key#" + code;
        }
    }

    /** Poll from the client tick mixin. */
    public static void poll(MinecraftClient client) {
        if (suppress || capturingAction != null) return;
        Handler h = handler;
        if (h == null) return;
        try {
            if (h.uiWantsKeys()) return;
        } catch (Throwable ignored) {
            return;
        }
        OverlayConfig cfg = OverlayConfig.get();
        for (String action : ACTIONS) {
            int code = cfg.hotkey(action);
            if (code <= 0) continue;
            boolean down;
            try {
                down = Keyboard.isKeyDown(code);
            } catch (Throwable t) {
                continue;
            }
            Boolean was = DOWN.get(code);
            if (down && (was == null || !was)) {
                try {
                    h.fire(action);
                } catch (Throwable ignored) {
                }
            }
            DOWN.put(code, down);
        }
    }
}
