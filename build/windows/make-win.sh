#!/bin/bash
# On the Windows build machine (Git Bash): puts Aqua's icon and name on the programs, builds the installer,
# then installs it silently to check it works.
#   bash build/windows/make-win.sh work/core
set -euo pipefail
CORE="$1"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
V="$(cat "$ROOT/VERSION")"
[ -f "$ROOT/dist-aqua.ico" ] || { echo "dist-aqua.ico missing: run build/apply.py windows first"; exit 1; }

# Mozilla's signature would no longer match once the icon changes, so take it off first.
ST="$(ls /c/Program\ Files\ \(x86\)/Windows\ Kits/10/bin/10.*/x64/signtool.exe 2>/dev/null | tail -1 || true)"
echo "signtool: ${ST:-not found}"
for exe in "$CORE/firefox.exe" "$CORE/private_browsing.exe"; do
  [ -f "$exe" ] || continue
  if [ -n "$ST" ]; then "$ST" remove //s "$exe" || echo "(could not remove signature from $exe)"; fi
  "$ROOT/rcedit.exe" "$exe" --set-icon "$ROOT/dist-aqua.ico" \
    --set-version-string ProductName "Aqua Browser" --set-version-string FileDescription "Aqua Browser" \
    --set-version-string CompanyName "Aqua Browser" --set-version-string InternalName "Aqua Browser" \
    --set-version-string LegalTrademarks "" --set-product-version "$V"
  echo "branded $exe"
done

NSIS="/c/Program Files (x86)/NSIS/makensis.exe"
mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/AquaBrowser-Windows-$V-Setup.exe"
"$NSIS" -V2 "-DSRC=$(cygpath -w "$(cd "$CORE" && pwd)")" "-DOUTFILE=$(cygpath -w "$OUT")" \
  "-DVERSION=$V" "-DICON=$(cygpath -w "$ROOT/dist-aqua.ico")" "$(cygpath -w "$ROOT/build/windows/installer.nsi")"
ls -la "$ROOT/dist"

"$OUT" //S
for i in $(seq 1 30); do [ -f "$LOCALAPPDATA/Programs/Aqua Browser/aqua.cfg" ] && break; sleep 2; done
ls "$LOCALAPPDATA/Programs/Aqua Browser/" | head -50
test -f "$LOCALAPPDATA/Programs/Aqua Browser/firefox.exe"
test -f "$LOCALAPPDATA/Programs/Aqua Browser/aqua.cfg"
echo "installed OK"
