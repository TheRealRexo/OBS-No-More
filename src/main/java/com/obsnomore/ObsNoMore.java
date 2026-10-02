package com.obsnomore;

import com.obsnomore.config.OverlayConfig;
import com.obsnomore.data.WebFetcher;
import com.obsnomore.stream.Hotkeys;
import com.obsnomore.stream.StreamManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

/**
 * OBS No More: in-game OBS Studio replacement (multi-RTMP streaming +
 * ffmpeg recording) with an OBS-style scene editor and sources.
 */
public class ObsNoMore implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "obsnomore";

    private static int tickCounter;
    private static volatile String lastAction = "";

    @Override
    public void onInitialize() {
        OverlayConfig.load();
        WebFetcher.get().start();
        Hotkeys.setHandler(new Hotkeys.Handler() {
            @Override
            public boolean fire(String action) {
                return onHotkey(action);
            }

            @Override
            public boolean uiWantsKeys() {
                try {
                    MinecraftClient c = MinecraftClient.getInstance();
                    if (c == null || c.currentScreen == null) return false;
                    String n = c.currentScreen.getClass().getName();
                    return n.contains("StreamOverlayEditorScreen")
                            || n.contains("StreamSettingsScreen");
                } catch (Throwable t) {
                    return false;
                }
            }
        });
        com.obsnomore.ObsLog.info("[OBSNoMore] initialized (config/obsnomore.json)");
    }

    @Override
    public void onInitializeClient() {
        OverlayConfig.load();
        WebFetcher.get().start();
    }

    /** Per client tick: scene-text refresh + hotkeys. */
    public static void onClientTick(MinecraftClient client) {
        tickCounter++;
        try {
            OverlayConfig cfg = OverlayConfig.get();
            if ((StreamManager.isStreaming() || StreamManager.isRecording())
                    && tickCounter % 20 == 0
                    && "2-POV".equals(cfg.pov_mode)) {
                StreamManager.dumpSceneText(cfg);
            }
        } catch (Throwable ignored) {
        }
        try {
            Hotkeys.poll(client);
        } catch (Throwable ignored) {
        }
    }

    static boolean onHotkey(String action) {
        OverlayConfig cfg = OverlayConfig.get();
        if (action.equals(OverlayConfig.HK_START_STREAM)) {
            lastAction = StreamManager.startStream();
            return true;
        }
        if (action.equals(OverlayConfig.HK_STOP_STREAM)) {
            StreamManager.stopStream();
            lastAction = "stream stopped";
            return true;
        }
        if (action.equals(OverlayConfig.HK_START_RECORD)) {
            lastAction = StreamManager.startRecord();
            return true;
        }
        if (action.equals(OverlayConfig.HK_STOP_RECORD)) {
            StreamManager.stopRecord();
            lastAction = "record stopped";
            cfg.save();
            return true;
        }
        if (action.equals(OverlayConfig.HK_PAUSE_RECORD)) {
            lastAction = StreamManager.pauseRecord();
            return true;
        }
        if (action.equals(OverlayConfig.HK_NEXT_SCENE)) {
            cfg.cycleScene(1);
            lastAction = "scene: " + cfg.activeScene().name;
            return true;
        }
        if (action.equals(OverlayConfig.HK_PREV_SCENE)) {
            cfg.cycleScene(-1);
            lastAction = "scene: " + cfg.activeScene().name;
            return true;
        }
        return false;
    }

    public static String lastAction() {
        return lastAction;
    }

    public static void setAction(String s) {
        lastAction = s == null ? "" : s;
    }

    public static void clearAction() {
        lastAction = "";
    }
}
