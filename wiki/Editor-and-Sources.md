# Editor and Sources

Open the editor with the **OBS button** (bottom-left of the main/pause
menus). Layout mirrors OBS Studio: preview canvas on top, **Scenes |
Sources | Audio Mixer | Scene Transitions | Controls** docks below, status
bar at the bottom.

## Sources

Add with **+Cam / +Txt / +Img / +Web** in the Sources dock:

- **Camera** — USB/HDMI capture device (device string in Properties).
- **Text** — static label.
- **Image** — PNG overlay (path in Properties).
- **Browser** — web pages and text/JSON endpoints: chat overlays,
  widgets, counters, now-playing. Put the URL in Properties. With
  headless Chromium installed, the page renders as a real screenshot
  (refreshed every ~15 s, sooner with the info-bar **Reload** button)
  in the preview and in recordings; without one, text/JSON lines render
  live instead. (This is the generic replacement for the old built-in
  chat/heart widgets — plug any feed URL back in if you want one.)
  Rendering is software (`--disable-gpu`): identical everywhere, no
  display or GPU drivers needed.
- Install headless Chromium for rendered pages (modern Chromium runs
  headless natively via `--headless` — no special binary needed;
  Chrome or Edge work too and are auto-detected):
  - Ubuntu / Debian / Mint:
    `sudo apt update && sudo apt install -y chromium-browser`
    (Debian/Mint package is named `chromium`)
  - Fedora / RHEL / CentOS: `sudo dnf install -y chromium`
  - Arch / Manjaro / CachyOS: `sudo pacman -Syu chromium`
  - Windows (PowerShell): `winget install Eloston.UngoogledChromium`
    (alternatives: `choco install chromium`,
    `scoop bucket add extras && scoop install chromium`)
  - macOS: `brew install --cask chromium`
    (or `brew install chromium` for the no-wrapper binary)

**Properties** (info-bar button, Filters button, or mixer **Props**):
name, X/Y position, scale, and the content field (text / path / device /
URL), with Save & Close persisting to disk. Also editable inline via the
small field under the menu bar (Enter saves).

**Reload** (info bar): re-captures the selected browser source's page
immediately instead of waiting for the ~15 s auto-refresh.

## Transforms (canvas)

- Drag body: move. Arrow keys: nudge. **Del**: remove.
- **Red handles**: resize — corners keep aspect (opposite corner
  anchored), **Shift** = free non-uniform stretch, edge handles stretch
  one axis.
- **Green handles**: hold **Alt** (Option on macOS, Ctrl also works) for
  crop mode — switchable mid-drag — with live `87 px` readouts on
  croppable (image) sources.
- **Eye**: visibility toggle. **Lock**: pin a source in place.

## Docks

- **Scenes**: select / + / −, **Rename** (type the new name in the field
  under the menu bar, Enter saves), cycle button when more than 3 exist.
- **Audio Mixer**: desktop + mic mute toggles, gain −/+, level slider,
  totals readout.
- **Scene Transitions**: Cut/Fade + duration, Go to preview it.
- **Controls**: Start/Stop Stream, Start/Stop Rec, 1-POV/2-POV toggle,
  Settings, Exit.
