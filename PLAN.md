# Music Unbound — Plan (NeoForge 1.21.1, client-only)

> Goal: a lightweight, client-only mod that gives full control over background music and still feels like vanilla.
> Status: **M0–M4 implemented and committed (0.1.0). M5 (your in-game check, Create server test, publishing) remains.** License: MIT.

---

## 1. Decisions

| Topic | Decision |
|---|---|
| Name / id | **Music Unbound** / `musicunbound` |
| Platform | NeoForge 21.1.x (MC 1.21.1), Java 21, **client-only**: works on vanilla and modded servers, and nothing is needed on the server |
| Biome locks | Removed: any Overworld track can play anywhere in the Overworld |
| Exceptions (keep dedicated music) | **Nether**, **End**, **Deep Dark** (Ancestry, Deeper). Each one is a **toggle** |
| Underwater | Not an exception by default (uses the normal pool), but it gets a toggle too |
| Creative lock | Removed: Creative and Survival use the same pool |
| Context-locked tracks | Every one is **toggleable** in the rotation: menu tracks, creative tracks, underwater tracks, Boss, Alpha (credits). **Exception:** *The End* only ever plays in the End |
| Music discs | **Never** in the rotation |
| New tracks | The 10 background tracks added after 1.21.1, downloaded at runtime from Mojang's official asset server (not bundled) |
| UI depth | **Presets + track picker**: one vanilla-style screen. No rule editor |
| Presets | **All (default on first launch)**, Vanilla 1.21.1, Vanilla latest, **version presets** (§3), New only (the 10 added after 1.21.1), Custom |
| Delay | Frequency presets like the 1.21.6 option (Default / Frequent / Constant / **Era**) + **Custom** min/max minutes |
| Events | **Postponed**: not in v1 (§8) |
| Extras | Now Playing toast, music keeps playing while paused, current track shown in the pause menu |
| Menu music | Chosen in the track picker (separate "Menu" column) |
| Language | English only |
| Publishing | Modrinth (+ maybe CurseForge), MIT license, GitHub repo |

### Verified: tracks missing from 1.21.1
Checked against Mojang's asset indexes (1.21.1 has 60 music files, 26.3 has 70; nothing was removed):

| Version | Tracks | Composer |
|---|---|---|
| 1.21.6 Chase the Skies | Below and Above, Broken Clocks, Fireflies, Lilypad, O's Piano | Amos Roddy |
| 26.2 Chaos Cubed | Ebb, Home, Memories, Nightly, Shores | fingerspit |

Total download is about **33 MB**, once. Every other audio addition since 1.21.1 was a music disc (Tears, Lava Chicken, Bounce), and discs are excluded.

---

## 2. Architecture (how it hooks into vanilla)

Design rule: **reuse vanilla's `MusicManager`** for timing, fading and streaming. The mod only decides *which track* plays and *how long the wait is*.

```
 ┌────────────────────┐   every tick    ┌──────────────────────┐
 │ vanilla            │ ──────────────▶ │ getSituationalMusic()│──┐
 │ MusicManager       │                 └──────────────────────┘  │ SelectMusicEvent (NeoForge)
 │ (delay, fade,      │ ◀── Music(track, minDelay, maxDelay) ─────┘ or a small mixin
 │  streaming)        │                         ▲
 └────────────────────┘                         │
                                     ┌──────────┴──────────┐
                                     │  MusicDirector      │  ← context (menu / overworld / nether /
                                     │  (mod)              │     end / deep dark / underwater / boss)
                                     │  · active preset    │
                                     │  · enabled tracks   │
                                     │  · exceptions       │
                                     │  · no-repeat history│
                                     └─────────────────────┘
```

### Components
1. **Track catalog** (`tracks.json`, bundled, tiny)
   One entry per track: id, file path, title, artist, **`addedIn` (update)**, tags (`menu`, `creative`, `underwater`, `nether`, `end`, `boss`, `credits`…), and the vanilla mappings for both 1.21.1 and latest. Every preset is a filter over this data. Supporting a future Minecraft version means editing this file only.
2. **Per-track sound events**
   `assets/musicunbound/sounds.json` defines one sound event per track (for example `track/aerie` → `minecraft:music/game/aerie`, `stream: true`). It contains no audio, only references. These events are **not registered** in the game registry (used via `Holder.direct`), so a client-only mod causes no registry mismatch with servers.
3. **MusicDirector**: works out the current context, applies exceptions, picks a weighted random track from the active pool (avoiding the last N tracks), and returns a vanilla `Music` object with the chosen delay.
4. **Remote track provider** (new tracks)
   - Background thread at startup: version manifest → latest release → asset index → entries under `minecraft/sounds/music/` that 1.21.1 doesn't have.
   - Checks the shared `.minecraft/assets/objects/<hh>/<sha1>` first (already there if you ever launched 1.21.6+). Otherwise downloads from `resources.download.minecraft.net` and **checks the SHA-1**.
   - Saves the resolved list to `config/musicunbound/remote_tracks.json`, so later launches work offline.
   - A built-in, always-on client resource pack (`AddPackFindersEvent`) serves those files as `minecraft:sounds/music/game/*.ogg`. Music is streamed when it plays, so no resource reload is needed: the director only picks a new track once its file exists.
5. **Settings**: NeoForge `ModConfigSpec` client TOML. No Cloth Config or other dependency.

---

## 3. Presets

### Version presets ("the music as it was in version X")
Each preset is **cumulative**: every background track that existed in that version. Biome and Creative locks are removed as usual. Only versions that changed the music get a preset.

| Preset | Added in this update | Total |
|---|---|---|
| **Alpha & Beta (a1.2.6 – b1.8.1)** | Minecraft, Clark, Sweden, Dry Hands, Wet Hands, Mice on Venus, Subwoofer Lullaby, Living Mice, Haggstrom, Danny, Key, Oxygène | 12 |
| **Music Update (1.7, Nov 2013)** | Menu: Mutation, Moog City 2, Beginning 2, Floating Trees · Creative: Biome Fest, Blind Spots, Haunt Muskie, Aria Math, Dreiton, Taswell · Nether: Concrete Halls, Dead Voxel, Warmth, Ballad of the Cats · The End, Boss, Alpha | 29 |
| **Update Aquatic (1.13)** | Axolotl, Dragon Fish, Shuniji | 32 |
| **Nether Update (1.16)** | Rubedo, Chrysopoeia, So Below | 35 |
| **Caves & Cliffs (1.18)** | Stand Tall, Left to Bloom, Wending, Infinite Amethyst, One More Day, Floating Dream, Comforting Memories, An Ordinary Day | 43 |
| **The Wild Update (1.19)** | Ancestry, Aerie, Firebugs, Labyrinthine | 47 |
| **Trails & Tales (1.20)** | Echo in the Wind, A Familiar Room, Bromeliad, Crescent Dunes | 51 |
| **Tricky Trials (1.21)** | Featherfall, Watcher, Puzzlebox, komorebi, pokopoko, yakusoku, Deeper, Eld Unknown, Endless | 60 |
| **Chase the Skies (1.21.6)** | Below and Above, Broken Clocks, Fireflies, Lilypad, O's Piano | 65 |
| **Chaos Cubed (26.2)** | Ebb, Home, Memories, Nightly, Shores | 70 (= All) |

Counts were checked: 60 matches the 1.21.1 asset index and 70 matches 26.3.

- **Menu music in Alpha / Beta presets:** those versions had **no title screen music**, so the authentic default is a silent menu. The Menu column can still be changed.
- The **Tracks screen is grouped by update** (same groups as above). Each group has a "toggle all" checkbox.

### Era timing (verified by decompiling a1.2.6, b1.8.1, 1.7.10, 1.12.2, 1.13.2, 1.21.1, 26.3)
When the Frequency setting is **Era-accurate**, a version preset also uses that version's gaps between songs:

| Era | Gaps between songs |
|---|---|
| Alpha / Beta | 10–20 min everywhere (the first song came 0–10 min after joining). **Identical in a1.2.6 and b1.8.1** |
| 1.7 – 1.13 | Survival 10–20 min · Creative **1–3 min** · Nether **1–3 min** · End 5–20 min · Underwater (1.13) 10–20 min · Menu 1–30 s |
| 1.16 → latest | Survival, Creative, Nether, Underwater 10–20 min · End 5–20 min · Menu 1–30 s |

Vanilla 1.21.6+ frequency values (from 26.3's code): **Frequent** caps the gap at 10 min, and **Constant** waits 5 s.

➡ Alpha and Beta have the same songs and the same timing, so they are **merged into one "Alpha & Beta" preset**.

### Other presets
`All` (**default on first launch**) · `Vanilla 1.21.1` (mod does nothing except the extras) · `Vanilla latest` (current vanilla's mapping, including the new tracks) · `New only` (the 10 added after 1.21.1) · `Custom` (any checkbox change).

---

## 4. Settings UI (vanilla look)

Opened by a **"Music Settings…"** button added to *Options → Music & Sounds*, and from the NeoForge mod list's Config button. Built only from vanilla widgets.

**Main screen**
- Preset (cycle button, see §3)
- Frequency: `Default (10–20 min)` · `Frequent` · `Constant` · `Era` · `Custom` → min/max sliders
- Exceptions: Nether ☑ · End ☑ · Deep Dark ☑ · Underwater ☐
- Now Playing toast ☑ · Play while paused ☑ · Show track in pause menu ☑
- `Tracks…` button

**Tracks… screen** (scrollable list, grouped by update)
`▶ preview` · Title — Artist · `[Game ☑]` `[Menu ☑]` · status (for example "downloading…" or "unavailable offline")
Changing a checkbox switches the preset to `Custom`. A "Reset to preset" button undoes this. *The End* has no Game checkbox (End only).

---

## 5. Extras (backports from 1.21.6+)
- **Now Playing toast**: a vanilla-style toast with title and artist. The 1.21.1 language files don't have these strings, so the mod ships them.
- **Play while paused**: small mixin so pausing doesn't pause the music channel.
- **Pause menu info**: a line of text added to `PauseScreen` (♪ Title — Artist).

---

## 6. Milestones

| # | Deliverable | Done when |
|---|---|---|
| M0 | NeoForge MDK in IntelliJ, git repo, client-only setup, empty mod loads | Loads in the dev client (singleplayer) · built jar in your own instance joins your Create server (which doesn't have the mod) |
| M1 | Track catalog + MusicDirector + all presets + frequency (era timing verified by decompiling) | Biome locks and the creative lock are gone, exceptions work, and the delay is respected |
| M2 | Settings screen + Tracks screen (grouped by update, Menu column) | Everything above can be configured in-game and is saved |
| M3 | Remote track provider + built-in pack | The 10 new tracks play, survive an offline restart, and fail cleanly with no internet |
| M4 | Toast, play while paused, pause menu info | — |
| M5 | Polish, testing on vanilla / modded servers, GitHub + Modrinth release | v1.0.0 published |

**Lightweight target:** jar under 200 KB, no dependencies besides NeoForge, no work per frame.

**Dev environment:** IntelliJ IDEA ✅, JDK 21 ✅, git repo ✅ (GitHub remote to be added before release).

---

## 7. Risks / to verify while building
- Check that NeoForge 21.1's `SelectMusicEvent` gives enough control. If not, fall back to one small mixin on `Minecraft#getSituationalMusic`.
- 1.21.1 sound loading warns about missing files. The built-in pack must always report the new tracks as present, so they aren't dropped before they finish downloading.
- Vanilla "fade on context change" behaviour (for example entering the Nether) must still work with the custom pools.
- **Platform rules:** check Modrinth's and CurseForge's rules for mods that download files at runtime. The mod downloads official Mojang assets, not code, but this must be disclosed in the description.
- Era timing values (§3) are from memory and must be confirmed in the old game code.
- The name "Music Unbound" was free on Modrinth when checked (2026-10-14). Check again, and check CurseForge, before release.

---

## 8. Not planned for v1: Events (kept for reference)

Postponed: the plan is to use the mod for a while first and decide afterwards. Fixed event→song mappings were **rejected** (the same song every nightfall gets repetitive). If events come back, the likely form is: **an event just starts a random song from your normal pool, only if nothing is playing.**

### Detection notes (still valid if events come back)
| Trigger | How | Reliability |
|---|---|---|
| Dimension change | Client level changes | Exact |
| Sunrise / nightfall | Day time crosses 23000 / 13000 | Exact |
| Thunderstorm / rain starts | Weather switches from off to on | Exact |
| Structures, singleplayer / LAN host | Ask the integrated server (`getStructureWithPieceAt`), run on the server thread | Exact |
| Structures, remote server | Signature blocks or mobs in nearby loaded chunks (trial spawner / vault, end portal frame, blaze spawner, guardians…). The block check uses the chunk palette (`maybeHas`), which is cheap | Best guess; some structures can't be detected (mansion, jungle temple) |
| Biome entry | — | **Rejected**: biome changes happen too often |

**Anti-flicker rules (if implemented):**
- One cooldown shared by all events.
- A structure must stay detected for about 5 s.
- The same structure only triggers once per session.

### Rejected: fixed event → song mapping (archived)
Arrive in Overworld → Home, Minecraft, Sweden… · Sunrise → Featherfall, Left to Bloom… · Nightfall → Nightly, Fireflies… · Thunderstorm → Haunt Muskie, Taswell… · Trial Chambers → Watcher, Puzzlebox… · Stronghold → Concrete Halls… · Bastion → Chrysopoeia… · Village → A Familiar Room, Home…

---

## 9. v0.2 — from your first in-game review

| Your feedback | Decision |
|---|---|
| Too many clicks to reach a preset | **Playlist picker screen** (vanilla list like *Language…*) with sections: Built-in · Versions · Your playlists. Two clicks to reach any playlist |
| ▶ then leaving Tracks starts a new song | ▶ becomes **Play now**: the song becomes the real background track and keeps playing |
| Menu too messy, labels too long | Short main screen + vanilla-style sub-screens: **Playlist ▸**, **Frequency**, **Places ▸**, **Display & extras ▸**. The custom sliders only show for *Custom* |
| Toast shows "!" and lacks "Now Playing" | Music disc icon, yellow **Now Playing**, then *Title — Artist* (like 1.21.6) |
| Want a player | **Pause menu player**: title, progress bar with mm:ss / mm:ss, ⏮ ⏯ ⏭. Keybinds for skip / previous / pause (unbound by default) |
| Several playlists | **User playlists**: New / Duplicate / Rename / Delete, saved in `config/musicunbound/playlists.json`. Built-ins are read-only, with "Duplicate to edit". Vanilla widgets only, no extra libraries |

Also in v0.2 (your "yes to all"):
- jukebox fade
- smooth fades
- era-accurate first song
- favourites ★ (2× more likely)
- search box
- current-track highlight + tooltips (album, version, length)
- auto-detect future Mojang tracks
- modded biome music (opt-in, "Other mods" group)
- events (start a song early if silent: dimension, sunrise/nightfall, thunderstorm; shared cooldown)
- auto-publish CI (tag → GitHub Release + Modrinth)

**Build order:**
- **Phase 1 (internals, in parallel):** playback controller · fades · playlists storage · dynamic catalog · CI.
- **Phase 2 (screens):** settings + playlist picker/editor · pause player + toast + keybinds.
