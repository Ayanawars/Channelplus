#!/usr/bin/env python3
"""Import the two Jungle Team packs using its Enigma2 lamedb channel names.

Usage: python import_picons.py /path/to/Picon-enigma2-Movistar /path/to/Canales-enigma2
Sources must be local git checkouts. No network access is needed by the app.
"""
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKS = {"transparent": "jungle-picon-Movistar-Transparente", "color": "jungle-picon-Movistar-color"}


def generate(source, channels):
    assets = ROOT / "owntv_core_tdt/core/src/main/assets/picons"
    records = {}
    by_service = {}
    for style, folder in PACKS.items():
        for path in sorted((source / folder / "picon").glob("*.png")):
            if re.fullmatch(r"1_0_[0-9a-fA-F_]+", path.stem):
                fields = path.stem.split("_")
                key = tuple(int(x, 16) for x in fields[3:7])
                by_service.setdefault(key, {})[style] = path
            elif not re.match(r"\d+_", path.stem) and path.stem != "picon_default":
                name = re.sub(r"^picon_(?:color_)?", "", path.stem).replace("_", " ")
                # Named extras supplement the authoritative service/name matches below.
                records.setdefault(name, {})[style] = path
    for db in sorted(channels.rglob("lamedb")):
        lines = db.read_text(errors="replace").splitlines()
        start = lines.index("services") + 1
        for i in range(start, len(lines) - 2, 3):
            fields = lines[i].split(":")
            if len(fields) < 5:
                continue
            try:
                sid, namespace, tsid, onid = (int(x, 16) for x in fields[:4])
            except ValueError:
                continue
            name = lines[i + 1].strip()
            if re.search(r"\b(?:TEST|SID)\b", name, re.I) or name in {"PORTADA", "GUÍA", "GUÍA FÁCIL", "CONTRATO", "ALQUILER", "ALQUILER HD", "BAJO DEMANDA"}:
                continue
            images = by_service.get((sid, tsid, onid, namespace))
            if images:
                records.setdefault(name, {}).update(images)
    if assets.exists():
        shutil.rmtree(assets)
    assets.mkdir(parents=True)
    rows = []
    provenance = []
    for name, images in sorted(records.items()):
        paths = []
        originals = {}
        for style in PACKS:
            image = images.get(style)
            if image is None:
                paths.append("")
                continue
            data = image.read_bytes()
            filename = hashlib.sha256(data).hexdigest()[:24] + ".png"
            target = assets / style / filename
            target.parent.mkdir(exist_ok=True)
            target.write_bytes(data)
            paths.append(f"{style}/{filename}")
            originals[style] = str(image.relative_to(source))
        rows.append("\t".join([name, *paths]))
        provenance.append({"name": name, "sources": originals})
    (assets / "catalog.tsv").write_text("\n".join(rows) + "\n")
    shutil.copyfile(source / "LICENSE", assets / "LICENSE")
    metadata = {
        "picons": {"url": "https://github.com/jungla-team/Picon-enigma2-Movistar", "commit": subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()},
        "channel_names": {"url": "https://github.com/jungla-team/Canales-enigma2", "commit": subprocess.check_output(["git", "-C", str(channels), "rev-parse", "HEAD"], text=True).strip()},
        "changes": "Only Transparente and Color. Images unchanged; duplicate bytes stored once per style. Channel/name index added.",
        "entries": provenance,
    }
    (assets / "SOURCES.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    print(f"Imported {len(rows)} channel names; {len(list(assets.rglob('*.png')))} unique style images.")


if __name__ == "__main__":
    generate(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
