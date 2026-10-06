package com.obsnomore.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Copy-paste install commands for the external tools the mod shells out
 * to (ffmpeg, xdotool, pactl). Audio/video capture prerequisites differ
 * per OS and, on Linux, per distro family — this maps the current machine
 * to the right one-liner. Full steps live on the wiki; the GUI shows the
 * short form.
 *
 * <p>Wiki: https://github.com/TheRealRexo/OBS-No-More/wiki
 */
public final class InstallGuide {
    private InstallGuide() {
    }

    public static final String WIKI = "https://github.com/TheRealRexo/OBS-No-More/wiki";

    public enum Distro {
        ARCH, UBUNTU, DEBIAN, MINT, FEDORA, UBLUE, OTHER_LINUX
    }

    /** Linux distro family from /etc/os-release (+ rpm-ostree marker). */
    public static Distro linuxDistro() {
        String id = "";
        String like = "";
        try {
            File f = new File("/etc/os-release");
            if (f.isFile()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("ID=")) id = unquote(line.substring(3)).toLowerCase();
                    else if (line.startsWith("ID_LIKE=")) like = unquote(line.substring(8)).toLowerCase();
                }
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        boolean ostree = new File("/run/ostree-booted").exists()
                || new File("/ostree").exists();
        if (ostree || id.equals("bazzite") || id.equals("bluefin") || id.equals("aurora")
                || id.equals("ucore") || id.contains("ublue")) {
            return Distro.UBLUE;
        }
        if (id.equals("linuxmint") || like.contains("linuxmint")) return Distro.MINT;
        if (id.equals("ubuntu")) return Distro.UBUNTU;
        if (id.equals("debian")) return Distro.DEBIAN;
        if (id.equals("fedora")) return Distro.FEDORA;
        if (id.equals("arch") || like.contains("arch")) return Distro.ARCH;
        if (like.contains("ubuntu")) return Distro.UBUNTU;
        if (like.contains("debian")) return Distro.DEBIAN;
        if (like.contains("fedora")) return Distro.FEDORA;
        return Distro.OTHER_LINUX;
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Short audio-tool install line for the current machine (GUI-sized). */
    public static String audioInstall() {
        switch (FFmpeg.os()) {
            case WINDOWS:
                return "winget install VB-Audio.VBCABLE";
            case MAC:
                return "brew install blackhole-2ch";
            default:
                break;
        }
        switch (linuxDistro()) {
            case ARCH:
                return "pacman -S pipewire pipewire-pulse wireplumber pavucontrol libpulse";
            case UBUNTU:
            case DEBIAN:
            case MINT:
                return "apt install pavucontrol pulseaudio-utils (pipewire stack, see wiki)";
            case FEDORA:
                return "dnf install wireplumber pavucontrol pulseaudio-utils";
            case UBLUE:
                return "rpm-ostree install pavucontrol pulseaudio-utils (+ reboot)";
            default:
                return "install PipeWire/PulseAudio + pavucontrol + pactl (see wiki)";
        }
    }

    /** Short video-capture prerequisite line for the current machine. */
    public static String videoInstall() {
        switch (FFmpeg.os()) {
            case WINDOWS:
                return "built into ffmpeg (gdigrab) - nothing extra";
            case MAC:
                return "built into ffmpeg (avfoundation) - nothing extra";
            default:
                break;
        }
        switch (linuxDistro()) {
            case ARCH:
                return "pacman -S xdotool (window lookup)";
            case UBUNTU:
            case DEBIAN:
            case MINT:
                return "apt install xdotool (window lookup)";
            case FEDORA:
                return "dnf install xdotool (window lookup)";
            case UBLUE:
                return "rpm-ostree install xdotool (+ reboot)";
            default:
                return "install xdotool (window lookup)";
        }
    }

    /** Short ffmpeg install line for the current machine. */
    public static String ffmpegInstall() {
        switch (FFmpeg.os()) {
            case WINDOWS:
                return "winget install Gyan.FFmpeg";
            case MAC:
                return "brew install ffmpeg";
            default:
                break;
        }
        switch (linuxDistro()) {
            case ARCH:
                return "pacman -S ffmpeg";
            case UBUNTU:
            case DEBIAN:
            case MINT:
                return "apt install ffmpeg";
            case FEDORA:
                return "ffmpeg via RPM Fusion (see wiki)";
            case UBLUE:
                return "brew install ffmpeg";
            default:
                return "install ffmpeg (see wiki)";
        }
    }

    /** Short headless-Chromium install line (browser-source pages). */
    public static String browserInstall() {
        switch (FFmpeg.os()) {
            case WINDOWS:
                return "winget install Eloston.UngoogledChromium";
            case MAC:
                return "brew install --cask chromium";
            default:
                break;
        }
        switch (linuxDistro()) {
            case ARCH:
                return "pacman -Syu chromium";
            case UBUNTU:
                return "apt install chromium-browser";
            case DEBIAN:
            case MINT:
                return "apt install chromium";
            case FEDORA:
                return "dnf install chromium";
            case UBLUE:
                return "rpm-ostree install chromium (+ reboot)";
            default:
                return "install Chromium (see wiki)";
        }
    }

    /** Full multi-line audio steps for the wiki authors / debugging. */
    public static List<String> audioInstallFull() {
        List<String> out = new ArrayList<String>();
        switch (FFmpeg.os()) {
            case WINDOWS:
                out.add("winget install VB-Audio.VBCABLE");
                out.add("Reboot, set VB-Cable as your output, then Detect.");
                return out;
            case MAC:
                out.add("brew install blackhole-2ch");
                out.add("Route game audio to BlackHole (Audio MIDI Setup), then Detect.");
                return out;
            default:
                break;
        }
        switch (linuxDistro()) {
            case ARCH:
                out.add("sudo pacman -S --needed pipewire pipewire-pulse wireplumber pavucontrol libpulse xdotool ffmpeg");
                out.add("systemctl --user enable --now pipewire pipewire-pulse wireplumber");
                break;
            case UBUNTU:
                out.add("sudo apt install pavucontrol pulseaudio-utils xdotool ffmpeg");
                out.add("22.04: also sudo apt install pipewire pipewire-pulse wireplumber, then enable + mask pulseaudio (wiki).");
                break;
            case MINT:
            case DEBIAN:
                out.add("sudo apt install pavucontrol pulseaudio-utils xdotool ffmpeg");
                out.add("If no sound server: add pipewire pipewire-pulse wireplumber, enable, mask pulseaudio (wiki).");
                break;
            case FEDORA:
                out.add("sudo dnf install wireplumber pavucontrol pulseaudio-utils xdotool");
                out.add("ffmpeg needs RPM Fusion (wiki one-liner).");
                break;
            case UBLUE:
                out.add("rpm-ostree install pavucontrol pulseaudio-utils xdotool");
                out.add("Reboot (layering needs it). ffmpeg: brew install ffmpeg.");
                break;
            default:
                out.add("Install PipeWire/PulseAudio, pavucontrol, pactl, xdotool, ffmpeg.");
                break;
        }
        out.add("Verify: pactl info");
        return out;
    }
}
