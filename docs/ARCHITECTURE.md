# Music Unbound — Architecture (implementation contract)

Read with `PLAN.md`. NeoForge 21.1.259, Minecraft 1.21.1, Java 21, Mojang mappings + Parchment.
Decompiled reference sources (Minecraft + NeoForge, read-only): see the path given in your task prompt (`src121`).
**Always check the real 1.21.1 signatures in those sources, never guess an API.**

## Packages (base `com.musicunbound`)

| Package / class | Milestone | Responsibility |
|---|---|---|
| `MusicUnbound` | all | `@Mod(dist = CLIENT)` entry point. Only wiring: calls each feature's `register(...)` |
| `data.Track`, `data.Era`, `data.TrackCatalog` | M1 | Load `assets/musicunbound/tracks.json` (from the classpath, with Gson) |
| `config.MusicConfig` | M1 | `ModConfigSpec` CLIENT config (all settings) |
| `music.Preset`, `music.Frequency` | M1 | Preset → track sets, frequency → delays |
| `music.TrackAvailability` | M1 | Says whether a track can be played (remote tracks only once downloaded) |
| `music.MusicDirector` | M1 | `SelectMusicEvent` listener: picks the track and the delay |
| `remote.*` | M3 | Downloads the 10 new tracks and serves them through a built-in resource pack |
| `client.ui.*` | M2 | Settings screen + Tracks screen + button in Music & Sounds |
| `client.NowPlaying`, `client.NowPlayingToast` | M4 | Current-track tracking, toast, pause menu line |
| `mixin.*` | M3/M4 | `MinecraftMixin` (capture asset dir), `SoundEngineMixin` (play while paused) |

## Data files (generated, do not hand-edit)
- `assets/musicunbound/tracks.json`:
  - `eras[]`: `{id, name, menuMusic, timing:{game,creative,nether,end,underwater:[min,max] ticks}}`, ordered oldest → newest.
  - `tracks[]`: `{id, title, artist, era, category, file, remote, gameDefault, gameAllowed, menuAll, menuEras[]}`.
    - `category` ∈ overworld, menu, creative, underwater, nether, deep_dark, end, boss, credits.
    - `file` is the sound file id, for example `minecraft:music/game/aerie`.
  - `vanillaLatest`: `{ "<sound event id, e.g. minecraft:music.overworld.forest>": [{id, weight}] }`. This is the 26.3 pools, with event references already expanded.
- `assets/musicunbound/sounds.json`: one sound event per track, `musicunbound:track.<id>` → the vanilla file, `stream: true`.
  - These sound events are **never registered** in a registry. Use `Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("musicunbound", "track." + id)))`.

## Rules
- Client-only: nothing may register registry content or network payloads.
- No dependencies besides NeoForge. Use vanilla widgets only for UI.
- Nothing heavy per frame. Per-tick work must be O(1)-ish.
- Keep classes small and readable. Follow the existing code style. Comments only where the logic is not obvious.
