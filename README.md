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

Key settings: `api-key` (your `servers.server_api`), `host` (default `www.mcbans.com`),
`protocol-version` (`3` recommended), `tls`, `fail-open`, `kick-message` (`{reason}` is
substituted). See the generated file for the full annotated list.
