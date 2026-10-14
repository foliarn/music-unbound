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

---

# v0.2 additions (contract)

## Phase 1: internals (parallel, each agent owns its files)

| Owner | Files | Public API other code relies on |
|---|---|---|
| A1 Playback | `music/Playback.java`, `music/MusicDirector.java` (refactor), `client/NowPlaying.java` (tracking moves to Playback) | see "Playback API" |
| A2 Audio | `audio/MusicFader.java`, `mixin/MusicManagerMixin.java` | `MusicFader.register()`, `MusicFader.fadeOutAndStop(SoundInstance)` |
| B1 Playlists | `playlist/Playlist.java`, `playlist/PlaylistStore.java` | see "Playlist API" |
| B2 Dynamic catalog | `data/TrackCatalog.java` (dynamic tracks), `remote/RemoteTrackProvider.java`, `data/ModdedMusic.java` | `TrackCatalog.tracks()` may grow at runtime (thread-safe) |
| D CI | `.github/workflows/publish.yml` | — |

Shared glue already in place (supervisor): `Track.length()` (seconds, 0 = unknown), config keys `SMOOTH_FADES`, `JUKEBOX_FADE`, `EVENT_*`, and the mixin accessors `SoundManagerAccessor` (→ SoundEngine) and `SoundEngineAccessor` (`instanceToChannel`, `instanceBySource`, `calculateVolume`).

### Playback API (`com.musicunbound.music.Playback`, static, render thread)
- `@Nullable Track current()`, `@Nullable SoundInstance currentInstance()`
- `int elapsedSeconds()` (excludes paused time), `boolean isPaused()`
- `void playNow(Track)`: becomes the real background song (not a preview). Keeps playing even if not in the pool.
- `void next()`: skip to the next pick immediately. `void previous()`: replay the previously played track. `void togglePause()`.
- `void startNextIfSilent()`: used by events. Starts the next pick now if nothing is playing.
- `List<Track> history()`: most recent first.

### Playlist API (`com.musicunbound.playlist`)
- `record Playlist(String id, String name, Kind kind, @Nullable String eraId, Set<String> game, Set<String> menu)` with `enum Kind { NORMAL, VANILLA_1_21_1, VANILLA_LATEST }`, plus `boolean builtIn()` (`id` starts with `builtin:`).
- `PlaylistStore` (static):
  - Reading: `builtIns()`, `userPlaylists()`, `all()`, `active()`, `byId(id)`.
  - Changing: `setActive(id)`, `create(name, @Nullable copyFromId)`, `rename(id, name)`, `delete(id)`, `setTracks(id, game, menu)`.
  - Favourites: `isFavourite(trackId)`, `setFavourite(trackId, bool)`.
  - Saved in `config/musicunbound/playlists.json`.
