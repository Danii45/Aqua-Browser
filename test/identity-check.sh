#!/bin/bash
# Checks that Aqua Browser keeps its profiles in its own folder (not Firefox's) when started the normal way.
#   bash test/identity-check.sh <launcher> <where profiles.ini should appear> <out-dir>
set -u
L="$1"; EXPECT="$2"; OUT="$3"
mkdir -p "$OUT"
"$L" --headless --screenshot "$OUT/identity-check.png" https://example.com > "$OUT/identity-check.log" 2>&1 &
PID=$!
for i in $(seq 1 45); do [ -f "$EXPECT" ] && [ -f "$OUT/identity-check.png" ] && break; sleep 2; done
kill "$PID" 2>/dev/null; sleep 2; kill -9 "$PID" 2>/dev/null
if [ -f "$EXPECT" ]; then
  echo "Aqua's own profile folder is in use:"; cat "$EXPECT"
else
  echo "MISSING: no profiles.ini at $EXPECT"; tail -40 "$OUT/identity-check.log"; exit 1
fi
