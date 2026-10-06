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
  widgets, counters, now-playing. Put the URL in Properties. With a
  renderer installed (Chromium or Electron — pick on the Setup Deps
  page), the page renders as a real screenshot
  (refreshed every ~15 s, sooner with the info-bar **Reload** button)
  in the preview and in recordings; without one, text/JSON lines render
  live instead. (This is the generic replacement for the old built-in
  chat/heart widgets — plug any feed URL back in if you want one.)
  Rendering is software (`--disable-gpu`): identical everywhere, no
  display or GPU drivers needed.
- Install a renderer for rendered pages (pick the engine on the Setup
  Deps page — **Pages**: Auto / Chromium / Electron):
  - Electron (`npm install -g electron` after installing Node.js;
    verify with `electron --version`):
    - Windows: `winget install OpenJS.NodeJS.LTS`, then npm command
    - macOS: `brew install node`, then npm command
    - Arch/CachyOS: `sudo pacman -S --needed nodejs npm`, then npm command
    - Ubuntu/Debian/Mint: `sudo apt install nodejs npm`, then npm command
    - Fedora: `sudo dnf install nodejs npm`, then npm command
    - Bazzite/uBlue: `brew install node`, then npm command
  - Headless Chromium (fallback; Chrome or Edge work too):
  - Ubuntu / Debian / Mint:
    `sudo apt update && sudo apt install -y chromium-browser`
    (Debian/Mint package is named `chromium`)
  - Fedora / RHEL / CentOS: `sudo dnf install -y chromium`
  - Arch / Manjaro / CachyOS: `sudo pacman -Syu chromium`
  - Windows (PowerShell): `winget install Eloston.UngoogledChromium`
    (alternatives: `choco install chromium`,
    `scoop bucket add extras && scoop install chromium`)
  - macOS: install **Google Chrome** (`brew install --cask google-chrome`)
    — it is auto-detected and is currently the working macOS route:
    `brew install --cask chromium` is **disabled upstream** (fails with
    `Cask 'chromium' has been disabled because it does not pass the macOS
    Gatekeeper check!`, disabled 2026-09-01). Microsoft Edge from the Mac
    App Store works too.
- **Manual paths**: if auto-detect misses your install, type the full
  binary path into **Chromium path** / **Electron path** on the Setup
  Deps page (tall screens only — otherwise set `chromium_path` /
  `electron_path` by hand in `config/obsnomore.json`), then **Rescan
  all** and watch the row flip green with your path.

**Properties** (info-bar button, Filters button, or mixer **Props**):
name, X/Y position, scale, and the content field (text / path / device /
URL), with Save & Close persisting to disk. Also editable inline via the
small field under the menu bar (Enter saves).

**Reload** (info bar): re-captures the selected browser source's page
immediately instead of waiting for the ~15 s auto-refresh.

**Setup Deps page (formerly ffmpeg)**: green = found with its
path, red = missing with the install command. **Rescan all** re-detects
everything (use it right after installing something), and **Pages**
cycles the page renderer: **Auto** (Electron first, Chromium fallback),
**Chromium**, or **Electron**.

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
