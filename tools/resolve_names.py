#!/usr/bin/env python3
"""Resolve gtceu_names.json components into per-language id -> name maps.

A dedicated server only translates with en_us strings from mod jars, so KubeJS-defined
content stays as raw keys and other languages are missing. This script rebuilds every
name from its component JSON with lang files from the pack: assets/*/lang/<lang>.json in
the vanilla server jar, then every mod jar, then kubejs/assets (which overrides jars, as on
the client). The server jar has only en_us; other vanilla languages live in Mojang's client
assets, so pass a folder with e.g. ru_ru.json from the 1.20.1 asset index via --vanilla-lang.

Usage: resolve_names.py <gtceu_names.json> <server dir> <out dir> [--vanilla-lang DIR] [lang ...]
Writes <out dir>/names.<lang>.json: {"items"|"fluids"|"machines"|"recipe_types": {id: name}}.
English keeps the server-resolved string unless it is a raw key. Missing translations fall
back to en_us per key, then to the English name, then to a name made from the id.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

LANG_PATH = re.compile(r"^assets/[^/]+/lang/([a-z]{2}_[a-z]{2})\.json$")
FORMAT = re.compile(r"%(?:(\d+)\$)?([sd%])")
RAW_KEY = re.compile(r"(^|\s)[a-z0-9_]*[a-z][a-z0-9_]*(\.[a-z0-9_]+)+($|\s)")


def load_json(data):
    try:
        return json.loads(data.decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        return {}


def collect_langs(server_dir, langs, vanilla_dir):
    tables = {lang: {} for lang in langs}
    vanilla = sorted((server_dir / "libraries" / "net" / "minecraft" / "server").glob("*/server-*-extra.jar"))
    for jar in vanilla[-1:] + sorted((server_dir / "mods").glob("*.jar")):
        try:
            with zipfile.ZipFile(jar) as zf:
                for name in zf.namelist():
                    m = LANG_PATH.match(name)
                    if m and m.group(1) in tables:
                        tables[m.group(1)].update(load_json(zf.read(name)))
        except zipfile.BadZipFile:
            continue
        if vanilla_dir and jar in vanilla:
            for lang in langs:
                path = vanilla_dir / f"{lang}.json"
                if path.exists():
                    tables[lang].update(load_json(path.read_bytes()))
    for lang in langs:
        for path in sorted((server_dir / "kubejs" / "assets").glob(f"*/lang/{lang}.json")):
            tables[lang].update(load_json(path.read_bytes()))
    return tables


def render(component, table, fallback_table):
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(render(c, table, fallback_table) for c in component)
    if not isinstance(component, dict):
        return str(component)
    if "translate" in component:
        key = component["translate"]
        pattern = table.get(key) or fallback_table.get(key) or component.get("fallback") or key
        args = [render(a, table, fallback_table) for a in component.get("with", [])]
        counter = iter(range(len(args)))

        def sub(m):
            if m.group(2) == "%":
                return "%"
            index = int(m.group(1)) - 1 if m.group(1) else next(counter, len(args))
            return args[index] if index < len(args) else ""

        text = FORMAT.sub(sub, pattern)
    else:
        text = component.get("text", "")
    return text + "".join(render(c, table, fallback_table) for c in component.get("extra", []))


TIERS = {"ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"}


def from_id(id_):
    words = id_.split(":", 1)[-1].split("_")
    return " ".join(w.upper() if w in TIERS else w.capitalize() for w in words)


def main():
    args = sys.argv[1:]
    vanilla_dir = None
    if "--vanilla-lang" in args:
        i = args.index("--vanilla-lang")
        vanilla_dir = Path(args[i + 1])
        del args[i:i + 2]
    if len(args) < 3:
        sys.exit(__doc__)
    names = json.loads(Path(args[0]).read_text(encoding="utf-8"))
    server_dir, out_dir = Path(args[1]), Path(args[2])
    langs = args[3:] or ["en_us", "ru_ru"]
    tables = collect_langs(server_dir, set(langs) | {"en_us"}, vanilla_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    english = {}
    for section, entries in names.items():
        english[section] = {}
        for id_, entry in entries.items():
            name = entry["en"]
            if RAW_KEY.search(name):
                name = render(entry["component"], tables["en_us"], {})
            english[section][id_] = from_id(id_) if RAW_KEY.search(name) else name

    for lang in langs:
        resolved, gaps = {}, 0
        for section, entries in names.items():
            resolved[section] = {}
            for id_, entry in entries.items():
                name = english[section][id_]
                if lang != "en_us":
                    rendered = render(entry["component"], tables[lang], tables["en_us"])
                    if RAW_KEY.search(rendered):
                        gaps += 1
                    else:
                        name = rendered
                resolved[section][id_] = name
        out = out_dir / f"names.{lang.split('_')[0]}.json"
        out.write_text(json.dumps(resolved, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
        print(f"{out}: {len(tables[lang])} lang keys, {sum(map(len, resolved.values()))} names, "
              f"{gaps} fell back to English")


if __name__ == "__main__":
    main()
