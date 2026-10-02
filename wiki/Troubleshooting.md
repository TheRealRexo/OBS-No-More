# Troubleshooting

## Recording/stream file exists but is empty

The session died instantly. Find out why in
`logs/output-client.log` (or the console): the mod logs the **full
ffmpeg command** as `[OBSNoMore] ffmpeg RECORD:` / `STREAM:` at start,
and the **last error lines** as `[OBSNoMore] session RECORD exited(N):`
on failure.

- **macOS, most common cause**: the Java process needs **Screen
  Recording permission** (System Settings → Privacy & Security → Screen
  Recording → enable your launcher/Java) or avfoundation captures
  nothing. Also check the screen index: Setup → Capture → display
  field, or leave blank for auto-detect (the chosen index is logged as
  `[OBSNoMore] avfoundation screen index: N`).
- **Bad crop**: if the window rect is wrong (e.g. accessibility denied
  for the bounds lookup), ffmpeg errors on an invalid crop. Approve the
  accessibility prompt, or set the region by hand.
- Paste the `ffmpeg` command line from the log into a terminal to
  reproduce outside the game.

## No ffmpeg found / recording and streaming disabled

Setup → ffmpeg shows the resolved binary or `(none)`. Install per
[Video-Capture-Setup](Video-Capture-Setup), or paste an explicit path
(then Scan drives / Use system PATH to confirm).

## Test capture FAILs

- **Window mode can't resolve**: check the window title (Windows,
  Setup → Capture) or install `xdotool` (Linux). Fall back to Region
  mode with the game window's x/y/w/h.
- **macOS accessibility prompt**: approve it for window bounds; without
  it the mod captures full-screen.
- **Wayland**: the game runs under XWayland — that path is supported. A
  pure-Wayland session with no X window falls back to manual region.

## Test audio is silent

- Run the distro install lines in [Audio-Setup](Audio-Setup), verify
  with `pactl info`.
- Linux: route the game to the `OBSNoMore Game Audio` sink
  (`pavucontrol` → Playback) when using the virtual output.
- Windows: set CABLE Input as output. macOS: route to BlackHole via a
  Multi-Output Device.

## RTMP Test says FAIL (connection refused …)

Server, key, or firewall. Verify the key (presets need key only),
the custom URL (must start with `rtmp://`), and that only enabled
slots are used (single-RTMP mode uses the first ON slot).

## Menu button / art missing

The button glyph is drawn procedurally and always shows; the PNG icon
and preview backdrops are read straight from the mod jar and registered
as runtime textures. Each asset logs one line at first use, e.g.
`[OBSNoMore] art assets/obsnomore/textures/gui/obs_button.png: dynamic
obsnomore:dynamic/obsnomore_1 (64x64)` — if you instead see `MISSING`,
`UNREADABLE`, `REGISTER-FAILED` or `ERROR`, paste that line in your
issue report. A legacy `FileNotFoundException: obsnomore:` means the
static-pipeline fallback was used (harmless; the dynamic path is tried
first).

## Wizard keeps coming back / never appears

It shows only while `"setup_done": false` in `config/obsnomore.json`.
Skip/Finish sets it true; deleting the line (or the file) brings the
wizard — and the Settings Wizard button — back.

## Still stuck?

Open an issue with: OS + distro, `config/obsnomore.json` (redact keys),
`logs/output-client.log` lines mentioning `OBSNoMore`, and what the
wizard Test buttons reported.
