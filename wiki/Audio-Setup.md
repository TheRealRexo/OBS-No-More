# Audio Setup

The mod records **game audio** (desktop loopback), not your mic input
chain — pick the loopback/virtual device in the wizard (Step 2/3) or
Setup → Audio, then **Test (3s)** to prove sound arrives. The mod needs
two things from your system: a sound server with a monitor/loopback, and
the `pactl` client (Linux) to find and create devices.

## Linux

The mod auto-creates an `obsnomore_game` virtual output (PulseAudio and
PipeWire both work) and can select its `.monitor` for you — press
**Virtual** in the wizard or Setup → Audio. If `pactl` is missing or no
sound server runs, install per your distro first:

**Arch / CachyOS / EndeavourOS / Manjaro:**
```sh
sudo pacman -S --needed pipewire pipewire-pulse wireplumber pavucontrol libpulse xdotool ffmpeg
systemctl --user enable --now pipewire pipewire-pulse wireplumber
```

**Ubuntu 23.10+ / 24.04+** (PipeWire is default):
```sh
sudo apt install pavucontrol pulseaudio-utils xdotool ffmpeg
```

**Ubuntu 22.04 / Linux Mint** (PulseAudio is default — switch stacks):
```sh
sudo apt install pipewire pipewire-pulse wireplumber pavucontrol pulseaudio-utils xdotool ffmpeg
systemctl --user --now enable pipewire pipewire-pulse wireplumber
systemctl --user mask pulseaudio.service pulseaudio.socket
# then reboot
```

**Debian 12+** (PipeWire is default):
```sh
sudo apt install pavucontrol pulseaudio-utils xdotool ffmpeg
# if no sound server is running, also add: pipewire pipewire-pulse wireplumber
```

**Fedora 40+:**
```sh
sudo dnf install wireplumber pavucontrol pulseaudio-utils xdotool
# ffmpeg needs RPM Fusion (see Video-Capture-Setup)
```

**Bazzite and other uBlue / Fedora Atomic distros** (immutable root — layer, then reboot):
```sh
rpm-ostree install pavucontrol pulseaudio-utils xdotool
systemctl reboot
# ffmpeg without layering: brew install ffmpeg
```

Verify on any distro:
```sh
pactl info
pactl list short sinks
```
You should see your outputs (each has a `.monitor` the mod can record).
Route the game to the `OBSNoMore Game Audio` sink (e.g. in `pavucontrol`
→ Playback tab) if you use the virtual output.

## Windows

```powershell
winget install VB-Audio.VBCABLE
```
Reboot, set **CABLE Input** as your Windows output device, then press
**Detect** in the mod — it scans DirectShow devices for cable/voicemeeter
entries automatically. (If the winget id differs on your machine,
`winget search VB-Audio` to find it.)

## macOS

```sh
brew install blackhole-2ch
```
Open **Audio MIDI Setup**, create a Multi-Output Device combining
BlackHole with your speakers so you keep hearing the game, route game
audio to it, then **Detect** in the mod (it scans avfoundation devices
for BlackHole/loopback entries).

## In the mod

- **Wizard Step 2/3**: Detect (auto-pick) → Cycle (step through
  candidates) → Test 3s (proves signal) → Virtual (create/select virtual
  output). Next only when the test hears sound.
- **Setup → Audio**: same device fields plus **Create virtual output**.
