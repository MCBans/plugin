# MCBans Minecraft plugin (multi-platform)

A single, generic MCBans plugin **core** plus thin per-platform adapters. The core speaks the
MCBans WebSocket plugin API (`/plugin/ws`, see [`../docs/PLUGIN-WS.md`](../docs/PLUGIN-WS.md)); each
adapter is just the glue that connects the core to one server platform.

```
plugin/
├── core/        Generic, platform-agnostic engine (NO Minecraft dependency).
│                  • /plugin/ws client (register handshake, ref-correlated request/response,
│                    real-time banSync/notice push, exponential-backoff reconnect)
│                  • ban / login model, command builders, cursor persistence
├── bukkit/      Bukkit-API adapter   → runs on CraftBukkit / Spigot / Paper
├── spigot/      Spigot-API adapter   → reuses :bukkit, Spigot-branded jar
├── sponge/      SpongeAPI 8 adapter
├── forge/       Forge 1.20.1 mod (server-side)
└── bungeecord/  BungeeCord proxy plugin — LOGIN / ban-check ONLY
```

## Architecture

All MCBans protocol logic lives **once**, in `:core`:

- `McBansSocketClient` — the persistent, authenticated WebSocket session (JDK
  `java.net.http.WebSocket`, so TLS is free). Handles the `register` handshake, `ref`-correlated
  request/response with per-request timeouts, unsolicited `banSync`/`notice` push (apply → advance →
  **persist** the cursor), and exponential-backoff-with-jitter reconnect that always re-registers.
- `McBansCore` — the typed facade adapters drive: `checkLogin(...)`, `globalBan(...)`,
  `tempBan(...)`, `unBan(...)`, `playerLookup(...)`, etc.
- The platform-specific bits are three small interfaces an adapter implements:
  `PluginLogger`, `CursorStore` (default `FileCursorStore`), and `BanSyncHandler`.

An adapter therefore only: builds a `McBansConfig` from its config system, instantiates
`McBansCore`, gates joins on its platform's login event, and (server-side) wires admin commands.

### Server vs. proxy split

- **Server adapters** (`bukkit`, `spigot`, `sponge`, `forge`) do everything: the on-join ban check,
  admin ban/unban commands, and applying real-time ban-sync (kick a player when a ban arrives).
- **The BungeeCord adapter does only the login gate.** On a proxy network the edge login check is
  the enforcement point, so it rejects banned players before they reach a backend server and
  registers no admin commands and a no-op ban-sync handler. **Run a server adapter on the backend
  servers** for ban issuing and live sync.

## Features & commands

Feature parity is modelled on the legacy `MCBans/MCBans` Bukkit plugin (v5.1.x). The shared brains
(API ops, command parsing, login gates, i18n) live in `core`; the rich command/notification UX is
wired into the **Bukkit/Spigot** adapter (Sponge/Forge/BungeeCord get the login gates + offline
failsafe via the same core).

**Commands** (Bukkit/Spigot):

| Command | Aliases | Permission | Notes |
|---|---|---|---|
| `/ban <player\|UUID> [g \| t <n> <m\|h\|d\|w>] <reason>` | | `mcbans.ban.local/global/temp` | `g` → global, `t` → temp modifier |
| `/globalban <player> <reason>` | `gban` | `mcbans.ban.global` | |
| `/tempban <player> <dur> <m\|h\|d\|w> [reason]` | `tban` | `mcbans.ban.temp` | |
| `/banip <ip> [reason]` | `ipban` | `mcbans.ban.ip` | |
| `/kick <player> [reason]` | | `mcbans.kick` | local kick |
| `/unban <player\|UUID\|ip>` | | `mcbans.unban` | clears offline cache |
| `/lookup <player>` | `lup` | `mcbans.lookup.player` | |
| `/banlookup <id>` | `blup` | `mcbans.lookup.ban` | |
| `/altlookup <player>` | `alup`, `alt` | `mcbans.lookup.alt` | premium |
| `/namelookup <player>` | `nlup` | `mcbans.lookup.player` | previous names |
| `/mcbs <setting> <value>` | | `mcbans.admin` | server settings |
| `/mcbans` | | — | status |

> `/rban` is registered for compatibility but behaves as a normal ban — the CoreProtect/HawkEye/
> LogBlock **rollback engine is not included** in this build.

**On-join (all server adapters):** ban / min-reputation / max-alts gates, with an **offline ban
cache** failsafe when the API is unreachable. Bukkit/Spigot additionally show staff notifications on
join — previous bans (`mcbans.view.bans`), alt accounts (`mcbans.view.alts`), recent name changes
(`mcbans.view.previous`), and the MCBans-staff notice (`mcbans.view.staff`).

**Localization:** message packs in `core/src/main/resources/messages/*.properties` (default + 9
translations, auto-converted from the legacy language files). Pick one with the `language:` setting.

**Events (Bukkit/Spigot):** `PlayerBanEvent`, `PlayerUnbanEvent`, `PlayerKickEvent` for other
plugins to listen to.

## Tests

Coverage is layered because mock-harness support differs per platform:

- **`core`** — JUnit tests for the version-agnostic logic: login parsing, command/duration parsing,
  i18n message resolution, and the shared `Identifiers` (UUID/IPv4) used by every adapter.
- **Bukkit / Spigot** — in-JVM tests on a mock server (**MockBukkit**): the plugin enables, all
  commands register, and the permission gate / argument validation behave correctly — no real
  network (config points at an unreachable host).
- **Sponge / Forge / BungeeCord** — no in-JVM mock harness exists, so their coverage is the boot
  smoke test below (a real server is launched), backed by the shared `core` unit tests.

```bash
./gradlew test          # core + Bukkit + Spigot unit tests
```

## Continuous integration

- `.github/workflows/build.yml` — builds every adapter on each push/PR (JDK 17, Gradle wrapper,
  cached ForgeGradle) and uploads the `McBans-*.jar` artifacts. Runs the unit tests as part of `build`.
- `.github/workflows/smoke.yml` — a matrix over all five platforms that **boots a real server** of
  each type and asserts the plugin enables (on demand + weekly, since it downloads servers).

## Local smoke test

`scripts/smoke-test.sh <bukkit|spigot|bungeecord|sponge|forge>` boots a real server of that platform
with the built jar and verifies the plugin loads/enables. It points the config at an unreachable
host with a dummy key, so the plugin fully initialises (registers commands/listeners) while the
WebSocket client just retries in the background — which is exactly what proves the jar is valid on
that platform. Servers are downloaded into the (gitignored) `.servertest/` dir on first use.

```bash
cd plugin && ./gradlew build
for p in bukkit spigot bungeecord sponge forge; do scripts/smoke-test.sh "$p"; done
```

All five platforms have been verified to load and enable: Bukkit/Spigot on Paper 1.20.4, BungeeCord,
SpongeVanilla 1.19.4 (API 8.2), and Forge 1.20.1.

## Build

Requires JDK 17 and network access to the platform mavens (Spigot, Sponge, Forge, Sonatype).

```bash
cd plugin
./gradlew build            # builds every adapter jar
./gradlew :bukkit:build    # just one
```

Output jars (self-contained; `:core` + a relocated Gson shaded in):

| Adapter | Jar | Drop into |
|---|---|---|
| Bukkit | `bukkit/build/libs/McBans-Bukkit-*.jar` | server `plugins/` |
| Spigot | `spigot/build/libs/McBans-Spigot-*.jar` | server `plugins/` |
| Sponge | `sponge/build/libs/McBans-Sponge-*.jar` | server `mods/` (or `plugins/`) |
| Forge | `forge/build/libs/McBans-Forge-*.jar` | server `mods/` |
| BungeeCord | `bungeecord/build/libs/McBans-BungeeCord-*.jar` | proxy `plugins/` |

> **Forge** brings its own ForgeGradle toolchain and `jarJar` bundling; its dependency versions
> (`forge:1.20.1-47.x`) may need bumping for other Minecraft versions. The Bukkit/Spigot/Sponge/
> Bungee modules use the Shadow plugin to produce a relocated fat jar.

## Configure

On first run each adapter writes a default config (then disables itself until you set `api-key`):

- **Bukkit / Spigot** — `plugins/McBans/config.yml`
- **Sponge** — `config/mcbans/mcbans.properties`
- **Forge** — `config/mcbans.properties`
- **BungeeCord** — `plugins/McBans/mcbans.properties`

Key settings: `apiKey` (your `servers.server_api`), `host` (default `www.mcbans.com`),
`protocol-version` (`3` recommended), `tls`, `language`, `prefix`, `failsafe` (deny on API error),
`minRep` (min reputation, `-1` disables), `enableMaxAlts`/`maxAlts`, `onJoinMCBansMessage`,
`sendDetailPrevBansOnJoin`, and the default reasons. See the generated file for the full annotated
list. (The Bukkit/Spigot `config.yml` uses these names; the Sponge/Forge/Bungee `.properties` use
kebab-case equivalents such as `min-reputation`, `enable-max-alts`.)
