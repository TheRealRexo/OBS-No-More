# OBS No More — Minecraft 1.6.4-1.13.2 (Legacy Fabric)

[![build](https://github.com/TheRealRexo/OBS-No-More/actions/workflows/build.yml/badge.svg)](https://github.com/TheRealRexo/OBS-No-More/actions/workflows/build.yml)

> An In-game OBS Studio replacement: stream to multiple RTMP servers and
> record with ffmpeg, with your favorite scene editor and sources, no
> external OBS needed.

Client-side mod for **Minecraft 1.6.4->1.13.2** (Legacy Fabric) by **TheRealRexo**.
Stream to Twitch/YouTube/Kick (or any RTMP) and record MP4/MKV/MOV/FLV
without leaving the game — scenes, sources, hotkeys and stream health
checks included.

## What it does

- **Scenes & sources** (OBS-style, in-game editor): game view plus generic
  overlay sources — USB/HDMI cameras, text labels, image overlays, and
  browser sources (any text/JSON web endpoint: chat feeds, counters,
  widgets — add your chat back through a URL if you want it).
  No display capture, no window capture — other programs can't be pulled in.
- **1-POV / 2-POV**: 1-POV streams exactly what you see (overlays baked in).
  2-POV keeps your game clean while viewers get game + sources composited
  by ffmpeg.
- **Multi-RTMP**: up to 3 slots by default (append more freely) via one
  encode with the `tee` muxer. Twitch/YouTube/Kick presets need only the key;
  Custom slots take a full server URL + key. Each slot has its own ON/OFF
  switch and connection Test, and Multi-RTMP can be switched off to stream
  to the first enabled slot only.
- **Recording**: mp4/mkv/mov/flv, discrete Start/Stop/Pause (pause = new part
  file: `capture.mp4`, `capture_part2.mp4`, …).
- **RTMP advisor**: per-slot Test streams generated content for ~10s and
  reports connect time, bitrate, drops + a recommended quality.
- **Hotkeys**: discrete key per action (Start/Stop Stream, Start/Stop/Pause
  Record, Next/Prev Scene), rebindable in Setup → Keys.
- **Game capture**: window mode by default — gdigrab window title on
  Windows, live window rect via x11grab on Linux (X11 and XWayland),
  avfoundation screen cropped to the window on macOS — with automatic
  fallback to the manual x/y/w/h region when the window can't be resolved.
  Switch modes in Setup → Capture.
- **Game audio**: per-OS loopback devices plus one-click virtual output
  creation (Pulse/PipeWire null sink on Linux; VB-Cable / BlackHole
  detection with install guidance on Windows/macOS) in the wizard and
  Setup → Audio.
- **ffmpeg**: never bundled. Auto-detect order: `config/obsnomore/ffmpeg`,
  then system PATH, then Setup shows per-OS download links
  (Linux: johnvansickle.com full static · Windows: gyan.dev full ·
  macOS: evermeet.cx / brew). Audio devices are per-OS too
  (PulseAudio/PipeWire, dshow/WASAPI, avfoundation).
- **Browser sources**: rendered pages need the Electron runtime
  (`npm install -g electron` after installing Node.js — per-OS commands
  in the wiki); a headless Chromium works as fallback. Without either,
  browser sources show live text/JSON lines instead.

## Config (`config/obsnomore.json`)

Auto-created on first launch; used to store all settings (and some secret overrides).

## Editor & setup

- Pause/main menus get an OBS button (bottom-left) → `StreamOverlayEditorScreen`,
  laid out like OBS Studio: top menu bar, central preview canvas, bottom dock
  strip (**Scenes** | **Sources** | **Audio Mixer** | **Scene Transitions** |
  **Controls**), status bar with LIVE/REC state, scene, fps and bitrate.
  Select + drag to move (locked sources stay put). OBS-style transform:
  red handles resize (corners uniform with opposite corner anchored,
  Shift = free non-uniform, edges stretch one axis), hold Alt/Option
  (Ctrl works too, mid-drag switching supported) for green crop handles
  with live px readouts on croppable image sources. Arrows nudge, Del removes,
  Eye toggles visibility,
  Lock pins a source. `+Cam/+Txt/+Img/+Web` adds sources; the Properties panel
  (info bar or mixer dock) edits name, position, scale and content per
  source with Save & Close persisting to disk. Mixer toggles desktop/mic
  and gain, Transitions picks Cut/Fade + duration, Controls holds
  Start/Stop Stream, Start/Stop Rec, POV, Settings, Exit.
- Settings → `StreamSettingsScreen` (tabs): **Stream** (per-slot preset, key,
  custom URL, Test + verdicts, ON/OFF, remove, +Add slot, Multi-RTMP
  single/multi switch, encoder, quality, bitrate, x264 preset), **Record**
  (container, quality, encoder, path), **Audio** (mic/desktop per OS),
  **Keys** (click → press new key, Esc cancels), **ffmpeg** (path, drive
  scan, download link), **Capture** (window region x/y/w/h, fps, display,
  gdigrab title, follow-mouse).

## Target / build

- Minecraft **1.6.4-1.13.2**, yarn `1.6.4+build.604`, loader `0.18.4`,
  Legacy Fabric API `1.13.5+1.6.4`, Java 8 bytecode.
- `loom 1.16-SNAPSHOT` needs **Gradle 9.x** (wrapper pins `gradle-9.8.0`)
  running on **JVM 21+**, compiling with a **JDK 17 toolchain**:

```sh
JAVA_HOME=<path-to-jre-21> \
  ./gradlew build -Porg.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk
# jar: build/libs/obs-no-more-x.x.x.jar
```

## Custom assets (no code changes)

The menu button glyph is drawn procedurally (always visible). The bundled
PNGs are registered into the game at runtime (read straight from the mod
jar and uploaded as dynamic textures — the same approach big content mods
use instead of relying on the static resource pipeline, which does not
reliably resolve mod-jar assets on Legacy Fabric):

| File | Used for | Format |
| ---- | -------- | ------ |
| `textures/gui/obs_button.png` | menu button icon (over the glyph) | square PNG + alpha, 16/32/64px |
| `textures/gui/preview_gameplay.png` | editor backdrop (main menu) | 16:9 PNG, any size |
| `textures/gui/preview_paused.png` | editor backdrop (pause) | 16:9 PNG, any size |
| `icon.png` | ModMenu icon | square PNG, 128px+ |

## Verified working features

- Boots with a full modded pack (Better than wolves and nightmare mode), no mixin conflicts; config auto-creates and
  the legacy flat config migrates into scene "Main" once.
- ModMenu (https://modrinth.com/mod/modmenu-btw) shows "OBS No More" by TheRealRexo with its description.
- OBS-style editor: menu bar, preview canvas, Scenes / Sources / Audio Mixer /
  Scene Transitions / Controls docks, status bar; select, drag, corner resize
  with anchored opposite corner, Shift free-resize, edge stretch, Alt/Ctrl
  crop with px labels, sources hidden behind the docks
  are lifted into view on open, arrows nudge, Del
  removes, Eye/Lock toggles, scene add/delete/cycle, POV toggle, per-source
  Properties panel, first-run setup wizard.
- Scenes, sources, mixer gains/mutes, transition choice and hotkeys all
  persist to `config/obsnomore.json`.
- Streaming: multi-RTMP via one encode (`tee`) or single-slot mode,
  presets need only the key, per-slot ON/OFF + advisor verdicts, per-OS
  encoders and audio devices.
- Recording: mp4/mkv/mov/flv, discrete Start/Stop/Pause where pause starts a
  new `_partN` file.
- Hotkeys F6–F10 + F12/Insert with in-GUI rebinding (F11 stays vanilla
  fullscreen, so scenes use F12/Insert).

## Verified tests

- Xvfb test runs (Temurin 17): boot, config create + migration
  (schema v3, chat→browser conversion, slots enabled, welcome source).
- Window capture args: Linux resolves to x11grab region, falls back cleanly
  with no window; virtual null-sink creation verified on PipeWire.
- Browser source screenshot-verified: add via +Web, URL via Properties,
  live local feed renders in preview; 2-POV filter chains per-source
  drawtext files.
- Record 12s 1080p30 → valid mp4 (ffprobe: h264/1080p/30fps).
- 2-POV record → mp4 with ffmpeg-composited chat + BPM drawtext frames.
- RTMP: per-slot key + custom URL typed in Setup > Stream persist to config;
  per-slot ON/OFF + Multi-RTMP single toggle persist; Test reports graceful
  FAIL on refused hosts, no crash; advisor self-test proven both ways
  standalone (refused → FAIL; live listener → STABLE with a measured
  ~6088 kbps recommendation).
- Editor screenshot-verified: source select, Properties panel edit (rename
  persisted), Stream page with visible key/URL fields, Capture tab.
- Hotkeys switch scenes (config change persists); GUI rebinding saves; pause
  creates valid `_part2` files; editor interactions screenshot-verified.
  
## License

GPL-3.0-only (see LICENSE). Matches the GitHub repo license.

## Legacy Fabric version support

The code only uses APIs that are stable across all Legacy Fabric versions
(`Screen`, `ButtonWidget`, `TextFieldWidget`, `MinecraftClient`,
`InGameHud`, mixins, Gson), so cutting a build for another version is a
config change, not a code change. In `gradle.properties`, set the trio for
your target (latest yarn builds listed at
https://legacyfabric.net/usage.html), keeping `loader_version=0.18.4` and
`loom_version=1.16-SNAPSHOT`:

| Minecraft | `minecraft_version` | `yarn_build` | `fabric_version` |
| --------- | ------------------- | ------------ | ---------------- |
| 1.6.4 (verified) | 1.6.4 | 604 | 1.13.5+1.6.4 |
| 1.7.10 | 1.7.10 | latest 1.7.10 | 1.13.5+1.7.10 |
| 1.8–1.8.9 | 1.8.9 | latest 1.8.9 | 1.13.5+1.8.9 |
| 1.9.4–1.13.2 | (version) | latest for it | 1.13.5+(version) |

Also update `fabric.mod.json`'s `minecraft` dependency to match, rebuild,
and publish one Modrinth file per Minecraft version. The legacy fabric 1.6.4-1.13.2 jar in
Releases is the tested reference build.
