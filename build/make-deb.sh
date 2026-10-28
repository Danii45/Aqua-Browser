#!/bin/bash
# Packages the prepared engine folder as a Kubuntu/Ubuntu/Debian .deb.
#   bash build/make-deb.sh path/to/firefox-folder
set -euo pipefail
SRC="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="$(cat "$ROOT/VERSION")"
PKG="$(mktemp -d)/pkg"
mkdir -p "$PKG/opt/aqua-browser" "$PKG/usr/bin" "$PKG/usr/share/applications" "$PKG/DEBIAN" "$PKG/usr/lib/aqua-browser"
cp -a "$SRC/." "$PKG/opt/aqua-browser/"
ln -s /opt/aqua-browser/firefox "$PKG/usr/bin/aqua-browser"
for s in 16 32 48 64 128 256 512; do
  mkdir -p "$PKG/usr/share/icons/hicolor/${s}x${s}/apps"
  python3 -c "from PIL import Image; Image.open('$ROOT/branding/icon-1024.png').convert('RGBA').resize(($s,$s), Image.LANCZOS).save('$PKG/usr/share/icons/hicolor/${s}x${s}/apps/aqua-browser.png')"
done
cat > "$PKG/usr/share/applications/aqua-browser.desktop" <<DESK
[Desktop Entry]
Name=Aqua Browser
GenericName=Web Browser
Comment=Browse the web
Exec=aqua-browser %u
Icon=aqua-browser
Terminal=false
Type=Application
StartupNotify=true
StartupWMClass=aqua-browser
Categories=Network;WebBrowser;
MimeType=text/html;text/xml;application/xhtml+xml;x-scheme-handler/http;x-scheme-handler/https;application/pdf;
Keywords=web;browser;internet;
Actions=new-window;new-private-window;profiles;

[Desktop Action new-window]
Name=New Window
Exec=aqua-browser --new-window

[Desktop Action new-private-window]
Name=New Private Window
Exec=aqua-browser --private-window

[Desktop Action profiles]
Name=Choose a Profile
Exec=aqua-browser --ProfileManager
DESK
# Chromium-style sandboxes need user namespaces, which Ubuntu 24+ and Kubuntu only allow with an AppArmor profile.
cat > "$PKG/usr/lib/aqua-browser/apparmor-profile" <<'AA'
abi <abi/4.0>,
include <tunables/global>

profile aqua-browser /opt/aqua-browser/{firefox,firefox-bin} flags=(unconfined) {
  userns,
  include if exists <local/aqua-browser>
}
AA
SIZE=$(du -sk "$PKG" | cut -f1)
cat > "$PKG/DEBIAN/control" <<CTRL
Package: aqua-browser
Version: $VER
Architecture: amd64
Maintainer: Aqua Browser <aqua-browser@users.noreply.github.com>
Installed-Size: $SIZE
Depends: libgtk-3-0t64 | libgtk-3-0, libasound2t64 | libasound2, libx11-xcb1, libdbus-1-3, libxtst6
Recommends: libpci3
Section: web
Priority: optional
Homepage: https://github.com/Danii45/Aqua-Browser
Description: Aqua Browser — a glass, retro web browser in the style of Aqua MD
 A customizable web browser built on Mozilla's open-source engine, with Home and Work profiles,
 a home page of widgets, a built-in password and card manager, and ad blocking.
CTRL
cat > "$PKG/DEBIAN/postinst" <<'POST'
#!/bin/sh
set -e
if command -v apparmor_parser >/dev/null 2>&1 && [ -d /etc/apparmor.d ]; then
  cp -f /usr/lib/aqua-browser/apparmor-profile /etc/apparmor.d/aqua-browser
  if apparmor_parser --skip-kernel-load --debug /etc/apparmor.d/aqua-browser >/dev/null 2>&1; then
    apparmor_parser -r /etc/apparmor.d/aqua-browser 2>/dev/null || true
  else
    rm -f /etc/apparmor.d/aqua-browser
  fi
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
exit 0
POST
cat > "$PKG/DEBIAN/postrm" <<'RM'
#!/bin/sh
set -e
if [ "$1" = "remove" ] || [ "$1" = "purge" ]; then
  if [ -f /etc/apparmor.d/aqua-browser ]; then
    command -v apparmor_parser >/dev/null 2>&1 && apparmor_parser -R /etc/apparmor.d/aqua-browser 2>/dev/null || true
    rm -f /etc/apparmor.d/aqua-browser
  fi
fi
exit 0
RM
chmod 755 "$PKG/DEBIAN/postinst" "$PKG/DEBIAN/postrm"
mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/AquaBrowser-Linux-$VER-amd64.deb"
dpkg-deb --build --root-owner-group -Zxz "$PKG" "$OUT"
echo "built $OUT ($(du -h "$OUT" | cut -f1))"
