#!/bin/bash
# On a Mac: turns the Firefox ESR disk image into "Aqua Browser.app", signs it ad hoc, and zips it with an installer.
#   bash build/make-mac.sh path/to/Firefox.dmg
set -euo pipefail
DMG="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="$(cat "$ROOT/VERSION")"
WORK="$(mktemp -d)"
hdiutil attach -nobrowse -readonly -mountpoint "$WORK/mnt" "$DMG" >/dev/null
SRC="$(find "$WORK/mnt" -maxdepth 1 -name '*.app' | head -1)"
mkdir -p "$WORK/out"
cp -R "$SRC" "$WORK/out/Aqua Browser.app"
hdiutil detach "$WORK/mnt" >/dev/null
python3 "$ROOT/build/apply.py" mac "$WORK/out/Aqua Browser.app"
codesign --remove-signature "$WORK/out/Aqua Browser.app" 2>/dev/null || true
codesign --force --deep --sign - "$WORK/out/Aqua Browser.app"
codesign --verify --deep --strict "$WORK/out/Aqua Browser.app" && echo "ad hoc signature OK"
cat > "$WORK/out/Install Aqua Browser.command" <<'INST'
#!/bin/bash
# Installs Aqua Browser: copies it to Applications, signs it for this Mac, and opens it.
set -e
cd "$(dirname "$0")"
APP="Aqua Browser.app"; DEST="/Applications/$APP"
echo "Installing Aqua Browser…"
if pgrep -f "/Applications/Aqua Browser.app/" >/dev/null; then osascript -e 'quit app "Aqua Browser"' || true; sleep 2; fi
rm -rf "$DEST"
cp -R "$APP" "$DEST"
xattr -cr "$DEST"
codesign --force --deep --sign - "$DEST"
# Make macOS pick up Aqua's icon now (it caches app icons).
touch "$DEST"
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$DEST" 2>/dev/null || true
echo "Done. Opening Aqua Browser."
open "$DEST"
INST
chmod +x "$WORK/out/Install Aqua Browser.command"
cat > "$WORK/out/READ ME FIRST.txt" <<READ
Aqua Browser $VER for Mac (Apple Silicon and Intel)

To install: right-click "Install Aqua Browser.command", choose Open, then Open again.
It copies Aqua Browser to Applications, signs it for your Mac with Apple's own codesign tool, and opens it.

Or by hand: drag "Aqua Browser.app" to Applications, then in Terminal run:
  xattr -cr "/Applications/Aqua Browser.app" && codesign --force --deep --sign - "/Applications/Aqua Browser.app"
READ
mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/AquaBrowser-Mac-$VER.zip"
rm -f "$OUT"
(cd "$WORK/out" && zip -q -r -y "$OUT" "Aqua Browser.app" "Install Aqua Browser.command" "READ ME FIRST.txt")
echo "built $OUT ($(du -h "$OUT" | cut -f1))"
