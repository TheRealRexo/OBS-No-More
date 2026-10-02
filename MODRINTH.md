# Summary (Modrinth, ≤255 chars — paste into the Summary field)

In-game OBS Studio replacement (Legacy Fabric): stream to multiple RTMP servers and record with ffmpeg — scenes, sources, hotkeys, no external OBS needed.

# Description (Modrinth Description tab)

OBS No More puts OBS Studio inside Minecraft — **every Legacy Fabric
version** (1.6.4 reference build, config-switchable builds for the rest):
stream to Twitch, YouTube, Kick or any RTMP server, and record
MP4/MKV/MOV/FLV, without leaving the game.

## What it does

- **Scenes & sources** in an OBS-style in-game editor: game view plus
  cameras, text labels, image overlays, and browser sources (any
  text/JSON web endpoint — chat feeds, counters, widgets). No display
  capture, no window capture: other programs can't be pulled in.
- **1-POV / 2-POV**: viewers see exactly your screen, or your game stays
  clean while viewers get game + sources composited by ffmpeg.
- **Multi-RTMP or single**: one encode fanned out to every enabled slot
  via the `tee` muxer, or first-enabled-slot-only mode. Twitch/YouTube/
  Kick presets need only the key; Custom takes a full server URL.
- **Recording**: discrete Start/Stop/Pause (pause splits a new
  `_partN` file), x264/NVENC/AMF/VAAPI encoders.
- **RTMP advisor**: per-slot connection tests with connect time,
  measured bitrate and a recommended quality.
- **Hotkeys**: one key per action (F6–F10, F12/Insert), rebindable
  in-game. F11 stays vanilla fullscreen.
- **First-run wizard** that proves game capture and game audio actually
  work before anything else.

## Requirements

- A Legacy Fabric instance (any supported Minecraft version), Loader
  0.18+, **Java 17**
- **ffmpeg** on PATH
- **Audio capture tool** for your OS (PipeWire/`pactl`, VB-Cable, or
  BlackHole — one command each)

Full per-OS install commands: **PUT_WIKI_URL_HERE** (Audio-Setup and
Video-Capture-Setup pages — Arch, Ubuntu, Debian, Mint, Fedora,
Bazzite/uBlue, Windows, macOS).

## Links

- Guides: PUT_WIKI_URL_HERE
- Source + releases: https://github.com/TheRealRexo/OBS-No-More
- License: GPL-3.0-only
