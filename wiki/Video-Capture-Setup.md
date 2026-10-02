# Video Capture Setup

The mod captures **the game window itself** (never your whole desktop —
no display/window capture of other programs). Window mode is the default;
if the window can't be resolved it falls back to the manual x/y/w/h region
automatically. Switch modes in Setup → Capture.

## What the mod needs per OS

**Linux (X11 and XWayland):** `xdotool` for the live window rect +
ffmpeg with `x11grab`:
```sh
# Arch / CachyOS / EndeavourOS / Manjaro
sudo pacman -S --needed xdotool ffmpeg
# Ubuntu 23.10+/24.04, Debian 12+, Mint
sudo apt install xdotool ffmpeg
# Fedora (+ RPM Fusion for a full ffmpeg with libx264)
sudo dnf install https://mirrors.rpmfusion.org/free/fedora/rpmfusion-free-release-$(rpm -E %fedora).noarch.rpm -y
sudo dnf install xdotool ffmpeg
# Bazzite / uBlue
rpm-ostree install xdotool   # then reboot; ffmpeg via: brew install ffmpeg
```

**Wayland note:** on Wayland sessions (native or XWayland) `x11grab`
reads BLACK — the compositor never composites into the X root pixmap —
so the mod feeds game frames straight from the GPU (in-process GL pipe)
instead. No extra Wayland tooling needed on GNOME/KDE, and `xdotool` is
not required there. On X11 sessions the mod uses the classic
`xdotool` + `x11grab` window-rect path. If recording shows the game
correctly in preview but the mp4 is black, check `obsnomore/diag.log`
for the `GL pipe capture` line to confirm which path was used.

**Windows:** nothing extra — window capture uses built-in `gdigrab`
(`title=`) and the title is auto-detected via PowerShell (override it in
Setup → Capture if your window title differs). Install FFmpeg:
```powershell
winget install Gyan.FFmpeg
```

**macOS:** nothing extra — built-in `avfoundation` screen capture cropped
to the detected window rect (screen index selectable in Setup → Capture).
Install FFmpeg:
```sh
brew install ffmpeg
```
First run may show a macOS accessibility prompt for reading window
bounds — approve it, otherwise the mod falls back to full-screen capture.

## Verify it

Wizard Step 1/3 → **Detect window** → **Test capture** records a 3s clip
and reports PASS only if the clip decodes clean. If it fails: check the
mode (Window vs Region), the title/rect, and that ffmpeg exists
(Setup → ffmpeg shows the resolved binary).
