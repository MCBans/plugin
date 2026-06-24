#!/usr/bin/env bash
# Full end-to-end integration test on a REAL Spigot (Paper) server.
#
# Boots Paper with the built McBans-Spigot jar, pointed at a protocol-compatible mock of the MCBans
# /plugin/ws endpoint (scripts/e2e/mock-server.js). Then it drives the Paper *console* and asserts a
# full round trip over the real WebSocket protocol, BOTH directions:
#
#   plugin -> server : register handshake; /gban /ban /tempban /banip /unban emit the right frames
#   server -> plugin : a pushed `notice` is surfaced; ban writes get a `banSync` push back
#
# A live MCBans backend (Spring API + MySQL) is intentionally not required: the mock exercises the
# exact wire protocol the plugin speaks, so this validates the plugin end to end deterministically.
#
# Usage: scripts/e2e-test.sh        (exit 0 = PASS)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.servertest"
RUN="$WORK/e2e"
LOG="$RUN/server.log"
FRAMES="$RUN/frames.log"
MOCKLOG="$RUN/mock.log"
PORT=8085
MC_PAPER="1.20.4"
JAR="$ROOT/spigot/build/libs/McBans-Spigot-1.0.0-SNAPSHOT.jar"
TIMEOUT="${E2E_TIMEOUT:-240}"

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }

[ -f "$JAR" ] || { red "Missing $JAR — run: ./gradlew :spigot:build"; exit 1; }

# Paper (reused across the smoke + e2e tests).
PAPER="$WORK/paper.jar"
if [ ! -f "$PAPER" ]; then
  echo "Downloading Paper $MC_PAPER ..."
  B=$(curl -sL -m 40 "https://api.papermc.io/v2/projects/paper/versions/$MC_PAPER/builds" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['builds'][-1]['build'])" 2>/dev/null)
  [ -n "$B" ] && curl -sL -m 180 -o "$PAPER" \
    "https://api.papermc.io/v2/projects/paper/versions/$MC_PAPER/builds/$B/downloads/paper-$MC_PAPER-$B.jar"
fi
[ -s "$PAPER" ] || { red "could not download Paper"; exit 1; }

# ---- fresh server dir ----
rm -rf "$RUN"; mkdir -p "$RUN/plugins/McBans"
cp "$JAR" "$RUN/plugins/"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<PROP
online-mode=false
level-type=flat
spawn-protection=0
max-players=1
server-port=25569
PROP
cat > "$RUN/plugins/McBans/config.yml" <<YML
apiKey: 'e2e-test-key'
host: '127.0.0.1:$PORT'
tls: false
language: default
YML
: > "$FRAMES"

cleanup() {
  [ -n "${PAPER_PID:-}" ] && kill -TERM "-$PAPER_PID" 2>/dev/null
  [ -n "${MOCK_PID:-}" ] && kill "$MOCK_PID" 2>/dev/null
  exec 3>&- 2>/dev/null || true
  sleep 1
  [ -n "${PAPER_PID:-}" ] && kill -KILL "-$PAPER_PID" 2>/dev/null
}
trap cleanup EXIT

# ---- start the mock /plugin/ws server ----
FRAMES="$FRAMES" PORT="$PORT" BANNED="BannedGuy" node "$ROOT/scripts/e2e/mock-server.js" > "$MOCKLOG" 2>&1 &
MOCK_PID=$!
sleep 1
grep -q "listening" "$MOCKLOG" || { red "mock server failed to start"; cat "$MOCKLOG"; exit 1; }

# ---- boot Paper with a FIFO stdin so we can drive the console ----
FIFO="$RUN/stdin.pipe"; mkfifo "$FIFO"
setsid bash -c "cd '$RUN' && exec java -Xmx1500M -jar '$PAPER' --nogui < '$FIFO' > '$LOG' 2>&1" &
PAPER_PID=$!
exec 3>"$FIFO"   # hold the write end open so the server's stdin stays open

console() { printf '%s\n' "$1" >&3; }

# ---- wait for the plugin to register against the mock ----
i=0; registered=0
while [ $i -lt "$TIMEOUT" ]; do
  grep -q "Registered as server #" "$LOG" && { registered=1; break; }
  grep -qE "MCBans enabled" "$LOG" || true
  kill -0 "$PAPER_PID" 2>/dev/null || break
  sleep 2; i=$((i+2))
done
[ "$registered" = 1 ] || { red "[e2e] plugin never registered (see $LOG)"; tail -20 "$LOG"; exit 1; }
green "plugin registered with the mock server"

# ---- drive the console through the full command surface ----
console "mcbans"
sleep 1
console "gban GriefUser x-ray cheating"
sleep 1
console "ban LocalGuy spamming chat"
sleep 1
console "tempban TempGuy 10m flooding"
sleep 1
console "banip 203.0.113.9 bad ip"
sleep 1
console "unban GriefUser"
sleep 3      # let the async frames land

# ---- assertions ----
fail=0
assert_frame() { # <cmd> <needle> <label>
  if grep -q "\"cmd\":\"$1\".*$2" "$FRAMES"; then green "  ✓ $3"; else red "  ✗ $3 (no $1 frame matching $2)"; fail=1; fi
}
assert_log() { # <needle> <label>
  if grep -qF "$1" "$LOG"; then green "  ✓ $2"; else red "  ✗ $2 (log missing: $1)"; fail=1; fi
}

echo "----- assertions -----"
assert_log    "Registered as server #1"            "register handshake completed"
assert_log    "E2E mock notice: hello plugin"      "server->plugin notice push surfaced"
assert_log    "Connected & registered"             "/mcbans status reports connected"
assert_frame  "globalBan"  "GriefUser"             "/gban -> globalBan frame"
assert_frame  "localBan"   "LocalGuy"              "/ban -> localBan frame"
assert_frame  "tempBan"    "TempGuy"               "/tempban -> tempBan frame"
assert_frame  "ipBan"      "203.0.113.9"           "/banip -> ipBan frame"
assert_frame  "unBan"      "GriefUser"             "/unban -> unBan frame"

console "stop"
sleep 2

echo "----------------------"
if [ "$fail" = 0 ]; then green "[e2e] PASS — full Spigot <-> /plugin/ws round trip verified"; exit 0; fi
red "[e2e] FAIL — see $LOG / $FRAMES"; exit 1
