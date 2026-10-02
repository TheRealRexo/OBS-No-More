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
  Chromium browser installed, the page renders as a real screenshot
  (refreshed every ~15 s) in the preview and in recordings; without
  one, text/JSON lines render live instead. (This is the generic
  replacement for the old built-in chat/heart widgets — plug any feed
  URL back in if you want one.)
- Install Chromium for rendered pages:
  - Arch: `pacman -S chromium`
  - Ubuntu/Debian/Mint: `apt install chromium`
  - Fedora: `dnf install chromium`
  - Bazzite/uBlue: `rpm-ostree install chromium` (+ reboot)
  - Windows: `winget install Google.Chrome`
  - macOS: `brew install --cask google-chrome`

**Properties** (info-bar button, Filters button, or mixer **Props**):
name, X/Y position, scale, and the content field (text / path / device /
URL), with Save & Close persisting to disk. Also editable inline via the
small field under the menu bar (Enter saves).

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

- **Scenes**: select / + / −, cycle button when more than 3 exist.
- **Audio Mixer**: desktop + mic mute toggles, gain −/+, level slider,
  totals readout.
- **Scene Transitions**: Cut/Fade + duration, Go to preview it.
- **Controls**: Start/Stop Stream, Start/Stop Rec, 1-POV/2-POV toggle,
  Settings, Exit.
