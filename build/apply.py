#!/usr/bin/env python3
"""Turns an official Firefox ESR build into Aqua Browser.

    python3 build/apply.py linux path/to/firefox            # the unpacked Linux tarball folder
    python3 build/apply.py mac   "path/to/Aqua Browser.app" # Firefox.app, copied and renamed
    python3 build/apply.py windows path/to/core             # the unpacked Windows installer's core folder

What it does:
  * adds Aqua (aqua.cfg start-up hook, the chrome://aqua/ package, default policies);
  * renames the app to Aqua Browser and swaps in Aqua's icons (Mozilla's trademark policy doesn't allow
    changed builds to be called Firefox);
  * gives it its own app name, so its profiles live apart from any real Firefox on the computer.
"""
import configparser, io, os, plistlib, re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION = (ROOT / "VERSION").read_text().strip()
ICON = ROOT / "branding" / "icon-1024.png"

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow: pip install pillow")


def png(size):
    im = Image.open(ICON).convert("RGBA").resize((size, size), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()


def copy_tree(src, dst):
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)


def add_aqua(res: Path):
    """res is the engine's resource folder (GreD): the Linux 'firefox' folder, or Contents/Resources on Mac."""
    shutil.copy(ROOT / "engine" / "aqua.cfg", res / "aqua.cfg")
    (res / "defaults" / "pref").mkdir(parents=True, exist_ok=True)
    auto = (ROOT / "engine" / "defaults" / "pref" / "aqua-autoconfig.js").read_text()
    auto += f'pref("aqua.version", "{VERSION}");\n'
    (res / "defaults" / "pref" / "aqua-autoconfig.js").write_text(auto)
    (res / "distribution").mkdir(exist_ok=True)
    shutil.copy(ROOT / "engine" / "distribution" / "policies.json", res / "distribution" / "policies.json")
    copy_tree(ROOT / "aqua", res / "aqua")


def edit_application_ini(path: Path):
    cp = configparser.RawConfigParser()
    cp.optionxform = str
    cp.read(path)
    cp.set("App", "Vendor", "Aqua")
    cp.set("App", "Name", "AquaBrowser")
    cp.set("App", "RemotingName", "aqua-browser")
    if cp.has_section("Crash Reporter"):
        cp.set("Crash Reporter", "Enabled", "0")
    with open(path, "w") as f:
        cp.write(f, space_around_delimiters=False)
    return cp.get("App", "Version", fallback="?")


BRAND_FTL = {
    "-brand-shorter-name": "Aqua",
    "-brand-short-name": "Aqua Browser",
    "-brand-shortcut-name": "Aqua Browser",
    "-brand-full-name": "Aqua Browser",
    "-brand-product-name": "Aqua Browser",
    "-vendor-short-name": "Aqua",
}
TRADEMARK = "Aqua Browser is built on Mozilla’s open-source browser engine. Firefox is a trademark of the Mozilla Foundation; Aqua Browser is not made or endorsed by Mozilla."


def read_jar(jar: Path):
    """Reads a zip/jar into {name: bytes}. Mozilla 'optimizes' omni.ja by moving the central directory to the
    front of the file, which trips up unzip, Python's zipfile and some 7-Zips, so this reads the offsets itself:
    as written (Mozilla's layout), or shifted by any bytes stuck on the front (ordinary zips)."""
    import struct, zlib
    data = jar.read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit(f"{jar} is not a zip file")
    _, _, _, _, total, cd_size, cd_off, _ = struct.unpack("<IHHHHIIH", data[eocd:eocd + 22])
    shift = 0
    if data[cd_off:cd_off + 4] != b"PK\x01\x02":
        shift = eocd - cd_size - cd_off
        if data[cd_off + shift:cd_off + shift + 4] != b"PK\x01\x02":
            raise SystemExit(f"{jar}: can't find its central directory")
    out, p = {}, cd_off + shift
    for _ in range(total):
        (sig, _, _, flags, method, _, _, _, csize, usize, nlen, xlen, clen, _, _, _, lho) = struct.unpack("<IHHHHHHIIIHHHHHII", data[p:p + 46])
        name = data[p + 46:p + 46 + nlen].decode("utf-8" if flags & 0x800 else "cp437")
        p += 46 + nlen + xlen + clen
        for off in (lho, lho + shift):
            if data[off:off + 4] == b"PK\x03\x04":
                break
        else:
            raise SystemExit(f"{jar}: bad entry {name}")
        lnlen, lxlen = struct.unpack("<HH", data[off + 26:off + 30])
        raw = data[off + 30 + lnlen + lxlen: off + 30 + lnlen + lxlen + csize]
        if method == 0:
            body = raw
        elif method == 8:
            body = zlib.decompress(raw, -15)
        else:
            raise SystemExit(f"{jar}: {name} uses unsupported compression {method}")
        out[name] = body
    return out


def unpack_jar(jar: Path, dest: Path):
    for name, body in read_jar(jar).items():
        if name.endswith("/"):
            (dest / name).mkdir(parents=True, exist_ok=True)
            continue
        target = dest / name
        if not target.resolve().is_relative_to(dest.resolve()):
            raise SystemExit(f"{jar}: unsafe path {name}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)


def pack_jar(src: Path, jar: Path):
    # A plain zip: Firefox reads ordinary zips as well as its own optimized ones.
    import zipfile
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for f in sorted(src.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(src).as_posix())


def rebrand_omni(omni: Path):
    """Swap the names and logos inside browser/omni.ja."""
    tmp = Path(tempfile.mkdtemp())
    unpack_jar(omni, tmp)
    changed = 0
    for ftl in tmp.glob("localization/*/branding/brand.ftl"):
        lines = []
        for line in ftl.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^(-[\w-]+)\s*=", line)
            if m and m.group(1) in BRAND_FTL:
                line = f"{m.group(1)} = {BRAND_FTL[m.group(1)]}"
            elif line.startswith("trademarkInfo"):
                line = f"trademarkInfo = {TRADEMARK}"
            lines.append(line)
        ftl.write_text("\n".join(lines) + "\n", encoding="utf-8")
        changed += 1
    for props in tmp.glob("chrome/*/locale/branding/brand.properties"):
        out = []
        for line in props.read_text(encoding="utf-8").splitlines():
            k = line.split("=", 1)[0].strip()
            if k == "brandShorterName": line = "brandShorterName=Aqua"
            elif k in ("brandShortName", "brandFullName", "brandProductName"): line = f"{k}=Aqua Browser"
            elif k == "vendorShortName": line = "vendorShortName=Aqua"
            out.append(line)
        props.write_text("\n".join(out) + "\n", encoding="utf-8")
        changed += 1
    brand = tmp / "chrome" / "browser" / "content" / "branding"
    if brand.exists():
        for f in brand.iterdir():
            if f.suffix == ".png":
                try:
                    w, h = Image.open(f).size
                except Exception:
                    continue
                im = Image.open(ICON).convert("RGBA").resize((min(w, h), min(w, h)), Image.LANCZOS)
                canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0)); canvas.paste(im, ((w - im.width) // 2, (h - im.height) // 2))
                canvas.save(f, "PNG"); changed += 1
            elif f.suffix == ".svg" and "wordmark" in f.name:
                f.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 172 42"><text x="0" y="30" font-family="sans-serif" font-size="26" font-weight="600" fill="context-fill">Aqua Browser</text></svg>')
                changed += 1
            elif f.suffix == ".svg" and "logo" in f.name:
                f.write_text((ROOT / "branding" / "icon.svg").read_text()); changed += 1
    changed += replace_art(tmp)
    omni.unlink()
    pack_jar(tmp, omni)
    shutil.rmtree(tmp, ignore_errors=True)
    print(f"  rebranded {changed} files in {omni.name}")


# Mozilla's mascot and logo artwork (the fox, "Kit") is part of its trademarks, so it's swapped for Aqua's icon.
ART = re.compile(r"((^|[-_])(fox|kit)([-_.]|$)|mascot|puzzle-fox|^firefox\.svg$|intro-illustration)", re.I)


def replace_art(tmp: Path):
    n = 0
    for f in tmp.rglob("*"):
        if not f.is_file() or not ART.search(f.name):
            continue
        if f.suffix.lower() == ".svg":
            f.write_text((ROOT / "branding" / "icon.svg").read_text()); n += 1
        elif f.suffix.lower() in (".png", ".webp"):
            try:
                w, h = Image.open(f).size
            except Exception:
                continue
            side = max(1, int(min(w, h) * .8))
            im = Image.open(ICON).convert("RGBA").resize((side, side), Image.LANCZOS)
            canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0)); canvas.paste(im, ((w - side) // 2, (h - side) // 2))
            canvas.save(f, "PNG" if f.suffix.lower() == ".png" else "WEBP"); n += 1
    return n


def rebrand_gre(omni: Path):
    """The engine-wide omni.ja (toolkit pages such as the password manager) has mascot art too."""
    if not omni.exists():
        return
    tmp = Path(tempfile.mkdtemp())
    unpack_jar(omni, tmp)
    n = replace_art(tmp)
    if n:
        omni.unlink()
        pack_jar(tmp, omni)
    shutil.rmtree(tmp, ignore_errors=True)
    print(f"  replaced {n} mascot images in {omni.parent.name}/{omni.name}")


WATCH = ("CustomizableUI", "AboutNewTab", "MigrationUtils", "ShellService", "SearchService", "PlacesUtils", "PrivateBrowsingUtils")


def log_modules(res: Path):
    """Prints where the modules Aqua uses live in this engine version (they move between versions)."""
    for jar in (res / "omni.ja", res / "browser" / "omni.ja"):
        if not jar.exists():
            continue
        try:
            names = read_jar(jar).keys()
        except SystemExit as e:
            print(f"  (could not list {jar.name}: {e})"); continue
        for n in sorted(names):
            base = n.rsplit("/", 1)[-1]
            if base.endswith(".sys.mjs") and base[:-8] in WATCH:
                print(f"  module {base[:-8]}: {jar.parent.name}/{jar.name} -> {n}")
            elif re.search(r"(fox|kit|mascot|illustration)", base, re.I) and re.search(r"\.(svg|png|webp|avif)$", base, re.I):
                print(f"  art {jar.parent.name}/{jar.name} -> {n}")


def linux(app: Path):
    add_aqua(app)
    ver = edit_application_ini(app / "application.ini")
    rebrand_omni(app / "browser" / "omni.ja")
    rebrand_gre(app / "omni.ja")
    log_modules(app)
    icons = app / "browser" / "chrome" / "icons" / "default"
    if icons.exists():
        for f in icons.glob("default*.png"):
            size = int(re.sub(r"\D", "", f.stem) or 128)
            f.write_bytes(png(size))
    print(f"Aqua Browser {VERSION} on engine {ver} (Linux) ready in {app}")


def mac(app: Path):
    res = app / "Contents" / "Resources"
    add_aqua(res)
    ver = edit_application_ini(res / "application.ini")
    rebrand_omni(res / "browser" / "omni.ja")
    rebrand_gre(res / "omni.ja")
    log_modules(res)
    plist_path = app / "Contents" / "Info.plist"
    with open(plist_path, "rb") as f:
        pl = plistlib.load(f)
    pl.update({
        "CFBundleName": "Aqua Browser",
        "CFBundleDisplayName": "Aqua Browser",
        "CFBundleIdentifier": "app.aquabrowser.browser2",
        "CFBundleGetInfoString": f"Aqua Browser {VERSION}",
        "CFBundleShortVersionString": VERSION,
    })
    with open(plist_path, "wb") as f:
        plistlib.dump(pl, f)
    # Icon: build an .icns with Apple's iconutil (only on a Mac) and drop it in over Firefox's.
    iconset = Path(tempfile.mkdtemp()) / "aqua.iconset"
    iconset.mkdir()
    for s in (16, 32, 128, 256, 512):
        (iconset / f"icon_{s}x{s}.png").write_bytes(png(s))
        (iconset / f"icon_{s}x{s}@2x.png").write_bytes(png(s * 2))
    icon_name = pl.get("CFBundleIconFile", "firefox.icns")
    if not icon_name.endswith(".icns"):
        icon_name += ".icns"
    if shutil.which("iconutil"):
        subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(res / icon_name)], check=True)
    else:
        print("  (no iconutil here: keeping the engine's icon)")
    print(f"Aqua Browser {VERSION} on engine {ver} (Mac) ready in {app}")


def windows(app: Path):
    add_aqua(app)
    ver = edit_application_ini(app / "application.ini")
    rebrand_omni(app / "browser" / "omni.ja")
    rebrand_gre(app / "omni.ja")
    log_modules(app)
    # Mozilla's updater, maintenance service and default-browser agent would update/report as Firefox; Aqua
    # updates through its own releases instead (updates are also switched off in policies.json).
    for name in ("updater.exe", "updater.ini", "update-settings.ini", "maintenanceservice.exe",
                 "maintenanceservice_installer.exe", "default-browser-agent.exe"):
        p = app / name
        if p.exists():
            p.unlink()
    icons = app / "browser" / "chrome" / "icons" / "default"
    if icons.exists():
        for f in icons.glob("default*.png"):
            size = int(re.sub(r"\D", "", f.stem) or 128)
            f.write_bytes(png(size))
    # Start-menu tiles.
    ve = app / "browser" / "VisualElements"
    if ve.exists():
        for f in ve.glob("*.png"):
            try:
                w, h = Image.open(f).size
            except Exception:
                continue
            im = Image.open(ICON).convert("RGBA").resize((min(w, h), min(w, h)), Image.LANCZOS)
            canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0)); canvas.paste(im, ((w - im.width) // 2, (h - im.height) // 2))
            canvas.save(f, "PNG")
    # The .ico that build/windows puts on firefox.exe (with rcedit) and the installer.
    Image.open(ICON).convert("RGBA").save(ROOT / "dist-aqua.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"Aqua Browser {VERSION} on engine {ver} (Windows) ready in {app}")


if __name__ == "__main__":
    modes = {"linux": linux, "mac": mac, "windows": windows}
    if len(sys.argv) != 3 or sys.argv[1] not in modes:
        sys.exit(__doc__)
    modes[sys.argv[1]](Path(sys.argv[2]))
