# Installation

## Requirements

- A Legacy Fabric instance (**any supported Minecraft version** — the mod
  targets the whole Legacy Fabric range; 1.6.4 is the reference build, other
  versions are config-switchable builds, see below).
- Legacy Fabric Loader 0.18+ (0.18.4 tested).
- **Java 17** to run the game.
- **ffmpeg** on PATH — see [Video-Capture-Setup](Video-Capture-Setup).
- **Audio capture tool** for your OS — see [Audio-Setup](Audio-Setup).

## Install the mod

1. Download `obs-no-more-1.0.0.jar` from
   [Releases](https://github.com/TheRealRexo/OBS-No-More/releases).
2. Drop it into your instance's `mods/` folder. Remove older
   `obs-no-more-*.jar` files first so only one copy loads.
3. Launch the game. On first run the mod creates
   `config/obsnomore.json` and opens the **setup wizard** from the OBS
   button (bottom-right of the main/pause menu). The wizard verifies game
   capture and game audio before anything else.

## Other Legacy Fabric versions

The code only uses stable Legacy Fabric APIs, so other versions are a
config change, not a code change. In `gradle.properties` set the trio for
your target (latest yarn builds at https://legacyfabric.net/usage.html),
keeping `loader_version=0.18.4`:

| Minecraft | `minecraft_version` | `yarn_build` | `fabric_version` |
| --------- | ------------------- | ------------ | ---------------- |
| 1.6.4 (reference) | 1.6.4 | 604 | 1.13.5+1.6.4 |
| 1.7.10 | 1.7.10 | latest 1.7.10 | 1.13.5+1.7.10 |
| 1.8–1.8.9 | 1.8.9 | latest 1.8.9 | 1.13.5+1.8.9 |
| 1.9.4–1.13.2 | (version) | latest for it | 1.13.5+(version) |

Update `fabric.mod.json`'s `minecraft` dependency to match, rebuild
(`./gradlew build` — needs JDK 21 to run Gradle, JDK 17 toolchain to
compile), publish one file per Minecraft version.

## Re-running first setup

The wizard is one-time by design: after Skip/Finish the Settings screen
stops showing the Wizard button. To bring it back, quit the game, delete
the `"setup_done"` line (or set it `false`) in
`config/obsnomore.json`, and relaunch.
