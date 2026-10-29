#!/bin/bash
# Launches the built browser on a virtual screen in a few different setups and screenshots each one,
# so the look can be checked without a person. Also keeps the browser's console output.
#   bash test/screenshots.sh path/to/firefox-folder out-dir
set -uo pipefail
APP="$1"; OUT="$2"
mkdir -p "$OUT"
run() { # name, extra prefs (user.js lines), urls...
  local name="$1" prefs="$2"; shift 2
  local P; P="$(mktemp -d)"
  cat > "$P/user.js" <<PREFS
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("devtools.console.stdout.chrome", true);
user_pref("browser.dom.window.dump.enabled", true);
user_pref("extensions.autoDisableScopes", 0);
user_pref("browser.sessionstore.resume_from_crash", false);
user_pref("datareporting.policy.firstRunURL", "");
user_pref("aqua.debug", true);
$prefs
PREFS
  # Window size (the --window-size flag only works headless): xulstore keeps the main window's size.
  echo '{"chrome://browser/content/browser.xhtml":{"main-window":{"screenX":"0","screenY":"0","width":"1440","height":"900","sizemode":"normal"}}}' > "$P/xulstore.json"
  echo "== $name" | tee -a "$OUT/console.log"
  xvfb-run -a -s "-screen 0 1440x900x24" bash -c "
    XUL_APP_FILE='$APP/browser/application.ini' '$APP/firefox' --no-remote -profile '$P' $* >>'$OUT/console.log' 2>&1 &
    PID=\$!
    sleep 28
    import -window root '$OUT/$name.png'
    kill \$PID 2>/dev/null; sleep 2; kill -9 \$PID 2>/dev/null
  "
  echo "   shot $name"
}
run 01-welcome "" 
run 02-home 'user_pref("aqua.welcomed", true);'
run 03-side-tabs-orange 'user_pref("aqua.welcomed", true); user_pref("aqua.color", "orange"); user_pref("sidebar.revamp", true); user_pref("sidebar.verticalTabs", true);' "https://example.com" "https://www.wikipedia.org"
run 04-page-blue 'user_pref("aqua.welcomed", true);' "https://www.wikipedia.org"
run 05-settings 'user_pref("aqua.welcomed", true); user_pref("browser.startup.page", 1); user_pref("browser.startup.homepage", "chrome://aqua/content/settings.html");'
run 06-passwords 'user_pref("aqua.welcomed", true); user_pref("aqua.color", "teal");' "about:logins"
run 07-auto-purple 'user_pref("aqua.welcomed", true); user_pref("aqua.color", "purple"); user_pref("aqua.addressFont", "dot");' "https://example.com"
grep -iE "aqua|error|exception" "$OUT/console.log" | grep -viE "glxtest|libEGL|mesa|dbus|fontconfig|gfx|CanvasRenderer|webgl|GStreamer|ATK|libva|Crash Annotation|telemetry" | head -80 > "$OUT/aqua-errors.txt" || true
echo "done: $(ls "$OUT"/*.png | wc -l) screenshots"
