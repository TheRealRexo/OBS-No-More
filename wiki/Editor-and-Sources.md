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
  Electron installed, the page renders as a real screenshot (refreshed
  every ~15 s, sooner with the info-bar **Reload** button) in the
  preview and in recordings; without any renderer, text/JSON lines
  render live instead. (This is the generic replacement for the old
  built-in chat/heart widgets — plug any feed URL back in if you want
  one.)
- Install Electron for rendered pages (needs Node.js first, then the
  runtime; verify with `electron --version`):
  - Windows: `winget install OpenJS.NodeJS.LTS`, then
    `npm install -g electron`
  - macOS: `brew install node`, then `npm install -g electron`
  - Arch/CachyOS: `sudo pacman -S --needed nodejs npm`, then
    `npm install -g electron`
  - Ubuntu/Debian/Mint: `sudo apt install nodejs npm`, then
    `npm install -g electron`
  - Fedora: `sudo dnf install nodejs npm`, then `npm install -g electron`
  - Bazzite/uBlue: `brew install node`, then `npm install -g electron`
- Fallback: if Electron is missing but a Chromium browser is installed
  (Chrome/Edge/`chromium`), the mod uses headless Chromium instead.
  No install needed beyond the browser itself.

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
