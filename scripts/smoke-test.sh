#!/usr/bin/env bash
# Boot a real server of each supported platform with the built MCBans jar and verify the plugin
# loads/enables. This is a LOAD smoke test (no live MCBans backend): the config points at an
# unreachable host with a dummy key, so the plugin fully enables (registers commands/listeners) and
# the WebSocket client just retries in the background — exactly what proves the jar is platform-valid.
#
# Usage: scripts/smoke-test.sh <bukkit|spigot|bungeecord|sponge|forge>
# Servers are cached under .servertest/. Exits 0 on PASS, 1 on FAIL.
set -uo pipefail

PLATFORM="${1:?usage: smoke-test.sh <bukkit|spigot|bungeecord|sponge|forge>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.servertest"
RUN="$WORK/$PLATFORM"
LOG="$RUN/server.log"
MC_PAPER="1.20.4"
TIMEOUT="${SMOKE_TIMEOUT:-220}"
mkdir -p "$WORK"

DUMMY_KEY="smoke-test-key-000000000000"
DUMMY_HOST="127.0.0.1:1"   # unreachable on purpose

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }

jarfor() {
  case "$1" in
    bukkit)     echo "$ROOT/bukkit/build/libs/McBans-Bukkit-1.0.0-SNAPSHOT.jar";;
    spigot)     echo "$ROOT/spigot/build/libs/McBans-Spigot-1.0.0-SNAPSHOT.jar";;
    bungeecord) echo "$ROOT/bungeecord/build/libs/McBans-BungeeCord-1.0.0-SNAPSHOT.jar";;
    sponge)     echo "$ROOT/sponge/build/libs/McBans-Sponge-1.0.0-SNAPSHOT.jar";;
    forge)      echo "$ROOT/forge/build/libs/McBans-Forge-1.0.0-SNAPSHOT.jar";;
  esac
}

# Boot $1=command (array via "$@" after shift), watch $LOG for success/failure markers, then stop.
run_and_watch() {
  local ok_re="$1"; local fail_re="$2"; shift 2
  : > "$LOG"
  # Run in a new session so the whole server process tree can be killed via its process group
  # (killing the wrapper alone would orphan the JVM and leak its bound port).
  setsid bash -c "cd '$RUN' && exec \"\$@\" >'$LOG' 2>&1" _ "$@" &
  local pid=$!
  local i=0 result=2
  while [ $i -lt "$TIMEOUT" ]; do
    if grep -qE "$ok_re" "$LOG"; then result=0; break; fi
    if grep -qE "$fail_re" "$LOG"; then result=1; break; fi
    kill -0 "$pid" 2>/dev/null || { result=3; break; }
    sleep 2; i=$((i+2))
  done
  # stop the server (whole process group), then hard-kill any stragglers.
  kill -TERM "-$pid" 2>/dev/null; sleep 4; kill -KILL "-$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  return $result
}

report() {
  echo "----- MCBans log lines ($PLATFORM) -----"
  grep -iE "mcbans" "$LOG" | head -20
  echo "----------------------------------------"
  case "$1" in
    0) green "[$PLATFORM] PASS — plugin loaded and enabled";;
    1) red   "[$PLATFORM] FAIL — error during load (see $LOG)";;
    3) red   "[$PLATFORM] FAIL — server exited before the plugin enabled (see $LOG)";;
    *) red   "[$PLATFORM] FAIL — timed out after ${TIMEOUT}s (see $LOG)";;
  esac
}

case "$PLATFORM" in
  bukkit|spigot)
    PAPER="$WORK/paper.jar"
    [ -f "$PAPER" ] || { echo "Missing $PAPER (download Paper $MC_PAPER first)"; exit 1; }
    rm -rf "$RUN"; mkdir -p "$RUN/plugins/McBans"
    cp "$(jarfor "$PLATFORM")" "$RUN/plugins/"
    echo "eula=true" > "$RUN/eula.txt"
    cat > "$RUN/plugins/McBans/config.yml" <<YML
apiKey: '$DUMMY_KEY'
host: '$DUMMY_HOST'
tls: false
language: default
YML
    # offline-mode + flat world keeps first-boot fast and avoids Mojang auth.
    # Distinct port per platform so sequential runs never clash on a not-yet-freed socket.
    PORT=$([ "$PLATFORM" = bukkit ] && echo 25561 || echo 25562)
    cat > "$RUN/server.properties" <<PROP
online-mode=false
level-type=flat
spawn-protection=0
max-players=1
server-port=$PORT
PROP
    run_and_watch "MCBans enabled" "Could not load|NoClassDefFoundError|ClassNotFoundException|error occurred while enabling" \
      java -Xmx1500M -jar "$PAPER" --nogui --noconsole
    report $?
    ;;

  bungeecord)
    BUNGEE="$WORK/bungeecord.jar"
    [ -f "$BUNGEE" ] || { echo "Missing $BUNGEE"; exit 1; }
    rm -rf "$RUN"; mkdir -p "$RUN/plugins/McBans"
    cp "$(jarfor bungeecord)" "$RUN/plugins/"
    cat > "$RUN/plugins/McBans/mcbans.properties" <<PROP
api-key=$DUMMY_KEY
host=$DUMMY_HOST
tls=false
PROP
    run_and_watch "MCBans \(login gate\) enabled|MCBans .* enabled" "NoClassDefFoundError|ClassNotFoundException|Error loading plugin" \
      java -Xmx512M -jar "$BUNGEE"
    report $?
    ;;

  sponge)
    JAR="$WORK/spongevanilla.jar"
    if [ ! -f "$JAR" ]; then
      echo "Resolving SpongeVanilla (API 8.2 / MC 1.19.4) ..."
      API="https://dl.spongepowered.org/api/v2/groups/org.spongepowered/artifacts/spongevanilla"
      VER=$(curl -sL -m 40 "$API/versions?tags=,minecraft:1.19.4&offset=0&limit=1" \
        | python3 -c "import sys,json;print(list(json.load(sys.stdin)['artifacts'].keys())[0])" 2>/dev/null)
      if [ -n "$VER" ]; then
        DL=$(curl -sL -m 40 "$API/versions/$VER" | python3 -c "
import sys,json
for a in json.load(sys.stdin).get('assets',[]):
    if a.get('classifier')=='universal' and a.get('extension')=='jar':
        print(a['downloadUrl']); break" 2>/dev/null)
        echo "  -> $VER ($DL)"
        [ -n "$DL" ] && curl -sL -m 240 -o "$JAR" "$DL"
      fi
    fi
    [ -s "$JAR" ] || { red "[sponge] could not download SpongeVanilla"; exit 1; }
    rm -rf "$RUN"; mkdir -p "$RUN/mods" "$RUN/config/mcbans"
    cp "$(jarfor sponge)" "$RUN/mods/"
    echo "eula=true" > "$RUN/eula.txt"
    cat > "$RUN/server.properties" <<PROP
online-mode=false
level-type=flat
spawn-protection=0
max-players=1
server-port=25564
PROP
    cat > "$RUN/config/mcbans/mcbans.properties" <<PROP
api-key=$DUMMY_KEY
host=$DUMMY_HOST
tls=false
PROP
    # Note: the "Failed to load properties" line Sponge prints on first boot is benign (it
    # regenerates server.properties), so it is deliberately NOT in the failure pattern.
    run_and_watch "MCBans enabled" "NoClassDefFoundError|ClassNotFoundException|Failed to construct|Could not load plugin" \
      java -Xmx1500M -jar "$JAR" --nogui
    report $?
    ;;

  forge)
    FV="1.20.1-47.2.0"
    INST="$WORK/forge-$FV-installer.jar"
    [ -f "$INST" ] || curl -sL -o "$INST" \
      "https://maven.minecraftforge.net/net/minecraftforge/forge/$FV/forge-$FV-installer.jar"
    mkdir -p "$RUN/config" "$RUN/mods"
    ARGS="$RUN/libraries/net/minecraftforge/forge/$FV/unix_args.txt"
    # Forge's installServer pulls ~200 MB; only run it once (re-runs reuse the install).
    if [ ! -f "$ARGS" ]; then
      ( cd "$RUN" && java -jar "$INST" --installServer >install.log 2>&1 )
    fi
    echo "eula=true" > "$RUN/eula.txt"
    rm -f "$RUN"/mods/McBans-*.jar
    cp "$(jarfor forge)" "$RUN/mods/"
    cat > "$RUN/server.properties" <<PROP
online-mode=false
level-type=flat
spawn-protection=0
max-players=1
server-port=25563
PROP
    cat > "$RUN/config/mcbans.properties" <<PROP
api-key=$DUMMY_KEY
host=$DUMMY_HOST
tls=false
PROP
    [ -f "$ARGS" ] || { red "[forge] installer did not produce server args ($ARGS)"; tail -20 "$RUN/install.log"; exit 1; }
    run_and_watch "MCBans enabled" "NoClassDefFoundError|ClassNotFoundException|Failed to load|construct.*mcbans" \
      java -Xmx2G "@$ARGS" nogui
    report $?
    ;;

  *) echo "unknown platform: $PLATFORM"; exit 1;;
esac
