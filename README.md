<img src="docs/icon.png" width="128" alt="Music Unbound icon">

# Music Unbound

A lightweight, **client-side** music manager for **NeoForge 1.21.1** that still feels like vanilla.

- **Every song, everywhere.** No more biome locks or Creative-only tracks. Any Overworld song can play anywhere in the Overworld.
- **The newest music, backported.** The 10 background tracks added after 1.21.1 play in 1.21.1:
  - Chase the Skies (1.21.6): *Below and Above, Broken Clocks, Fireflies, Lilypad, O's Piano*
  - Chaos Cubed (26.2): *Ebb, Home, Memories, Nightly, Shores*
- **Version presets.** Hear the game as it sounded in Alpha & Beta, the 1.7 Music Update, Update Aquatic, Caves & Cliffs, Tricky Trials and more. Each preset can also use that version's real gaps between songs.
- **Track picker.** Turn any song on or off, separately for in-game and the title screen.
- **Frequency.** Default, Frequent, Constant, Era-accurate, or your own min/max minutes.
- **Dedicated music kept where it matters.** The Nether, End, Deep Dark and underwater can keep their own music. Each is a toggle.
- **1.21.6 extras.** A Now Playing toast, music that keeps playing while paused, and the current track shown in the pause menu.

Music discs are never added to the rotation.

## Where to find the settings
*Options → Music & Sounds → Music Unbound...*, or the Config button in the mod list.

## Works on any server
Music Unbound is client-only. Install it on your client and join any server, vanilla or modded. The server doesn't need it.

## About the new tracks (downloads)
Minecraft's music can't be redistributed inside a mod, so this mod **does not bundle any audio**. Instead:
- On first launch it downloads the 10 newer tracks (about 33 MB) **from Mojang's official asset servers** (`piston-meta.mojang.com`, `resources.download.minecraft.net`). This is the same source the Minecraft launcher uses.
- Files go into the launcher's shared `assets/objects` folder and are checked with SHA-1. If you have already played 1.21.6 or newer, nothing is downloaded.
- After that, the tracks work offline.
- You can turn this off with *Download new tracks* in the settings. The mod then only uses tracks already on your disk.

No other network requests are made, and no data is sent anywhere.

## Building
JDK 21, then:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

## License
MIT (code only). Minecraft music © Mojang Studios and its composers, and is never distributed by this project.
