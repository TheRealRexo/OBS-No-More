# Streaming

## RTMP slots

Setup → Stream shows one row per slot: **preset | key | Test | ON/OFF |
remove**. Add more with **+ Add slot** (first 6 shown; beyond that, edit
`config/obsnomore.json`).

- **Twitch / YouTube / Kick presets need only the key** — the server URL
  is built in. Paste the key, nothing else.
- **Custom** slots take a full server URL row underneath plus the key.
- Every enabled slot streams the **same single encode** (ffmpeg `tee`
  muxer), so multi-streaming costs one encode.

## Single vs multi

**Multi-RTMP: ON** (default) streams to every enabled slot at once.
**OFF** streams to the first enabled slot only — the cheap way to keep
backup keys around without using them. Toggle in Setup → Stream.

## Testing a slot

**Test** runs a ~10s probe and reports a verdict: connect time, measured
bitrate, drops, plus a recommended quality preset. `FAIL (connection
refused …)` with no crash means the server/key/firewall needs a look —
see [Troubleshooting](Troubleshooting).

## Encoders, quality, bitrate

- **Encoder**: x264 (CPU, always works), NVENC (NVIDIA), AMF (AMD),
  VAAPI (Linux GPU). Pick what your hardware has; x264 `veryfast` is the
  safe default.
- **Quality**: 720p30 · 6M, 1080p30 · 8M, 1080p60 · 12M. The bitrate
  field overrides the preset default when non-zero.
- The bottom status bar shows LIVE time, scene, fps and bitrate while
  streaming.

## 1-POV / 2-POV

- **1-POV**: viewers see exactly your screen (overlays baked in).
- **2-POV**: your game stays clean; viewers get game + sources
  composited by ffmpeg. Toggle in Controls or with a hotkey.
