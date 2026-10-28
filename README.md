# Aqua Browser

A glass web browser in the style of [Aqua MD](https://danii45.github.io/Aqua-MD/), for Kubuntu, Mac and Windows.

**Website and downloads:** https://danii45.github.io/Aqua-Browser/

- **Home and Work profiles.** Each has its own cookies, history, passwords and home page. Switch with the chip next to the address bar.
- **A home page you build.** Sixteen kinds of widget (clock, weather, search, shortcuts, notes, to-do, calendar, focus timer, countdowns, news feeds, pictures and more) that you drag around and resize.
- **Passwords and cards stay in the browser.** No Google account needed, and you can lock them with a master password.
- **Seven colours plus Auto.** These are the seven colours from Aqua MD. Auto fades through all of them on a timer.
- **Very customizable.** Adjust the glass, blur, corner roundness, tabs on top or down the side, and dot-matrix or plain address bar. You can also add your own CSS.
- **Ad blocking.** uBlock Origin is set up on first start.

## How it's made

Aqua Browser is **Mozilla's Firefox ESR**, the official build, with Aqua layered on top. Nothing is recompiled. `build/apply.py` takes the official build and:

1. Adds the Aqua interface. This is the `aqua/` folder, served as `chrome://aqua/`. It's loaded at start-up by `engine/aqua.cfg`, an AutoConfig file.
2. Adds default policies in `engine/distribution/policies.json`: no telemetry, no Firefox update prompts, and uBlock Origin.
3. Renames the app to Aqua Browser and swaps in Aqua's icons. Mozilla's trademark policy doesn't allow modified builds to be called Firefox.
4. Gives it its own app name, so its profiles live apart from any real Firefox on the same computer.

GitHub Actions (`.github/workflows/build.yml`) then does the rest:

- Downloads the official ESR builds and runs `apply.py`.
- Packages a `.deb` (`build/make-deb.sh`), a Mac `.zip` (`build/make-mac.sh`) and a Windows installer (`build/windows/installer.nsi`).
- Takes screenshots of the result on Linux and Windows.

To publish a release, run the workflow by hand with **release = yes**. Each release uses the same file names every time, so the website's download links always point at the newest one:

| System | File |
|---|---|
| Kubuntu / Ubuntu / Debian | `AquaBrowser-Linux-amd64.deb` |
| Mac (Apple Silicon and Intel) | `AquaBrowser-Mac.zip` |
| Windows 10 / 11 | `AquaBrowser-Windows-Setup.exe` |

## Layout

```
aqua/          the Aqua interface (window styling, home page, settings), served as chrome://aqua/
engine/        start-up hook (aqua.cfg), its pref file, and default policies
branding/      the icon
build/         apply.py, make-deb.sh, make-mac.sh, windows/installer.nsi, site.py
site/          the website source; build/site.py writes it to docs/ for GitHub Pages
test/          screenshot runs for Linux and Windows
```

## Legal

Aqua Browser is built on Firefox ESR, which Mozilla publishes under the [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/). The engine's source code is available from Mozilla. Firefox is a trademark of the Mozilla Foundation. Aqua Browser is not made or endorsed by Mozilla.
