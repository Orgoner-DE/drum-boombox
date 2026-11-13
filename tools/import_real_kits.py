#!/usr/bin/env python3
"""Copy real CC0 recordings into three extra kits (Dusty, Live, Crate).

Source: the 50 processed one-shots of BAP-50 (Orgoner-DE.github.io/boombap/samples),
built from Virtuosity Drums and VCSL (both CC0 1.0, real acoustic recordings).

Usage:
  python3 tools/import_real_kits.py /path/to/Orgoner-DE.github.io/boombap/samples
Writes app/src/main/assets/samples/{dusty,live,crate}/<key>.wav and appends the kits
to app/src/main/assets/samples/manifest.json (existing kits stay untouched).
"""
import json
import os
import shutil
import sys
import wave

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "samples")

KEYS = ["kick", "snare", "clap", "rim", "hat_c", "hat_o", "shaker", "ride",
        "tom_lo", "tom_mid", "tom_hi", "cowbell", "perc1", "perc2", "perc3", "crash"]
LABELS = ["Kick", "Snare", "Clap", "Rim", "HH C", "HH O", "Shaker", "Ride",
          "Tom L", "Tom M", "Tom H", "Glocke", "Perc 1", "Perc 2", "Perc 3", "Crash"]

# Sample-IDs aus samples.json je Pad (Reihenfolge wie KEYS)
KITS = [
    ("dusty", "Dusty", ["k01", "s02", "c01", "s09", "h03", "h07", "p02", "y01",
                        "t01", "t03", "t02", "p03", "p01", "m01", "m04", "y03"]),
    ("live", "Live", ["k02", "s01", "c02", "s09", "h01", "h05", "p02", "y02",
                      "t01", "t03", "t02", "p03", "p04", "p05", "s08", "y04"]),
    ("crate", "Crate", ["k03", "s04", "c03", "p07", "h02", "h06", "p01", "y01",
                        "k09", "p04", "p05", "p06", "p08", "m03", "m02", "y03"]),
]


def frames(path):
    with wave.open(path, "rb") as w:
        assert w.getframerate() == 44100 and w.getsampwidth() == 2, path
        return w.getnframes()


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    src_dir = sys.argv[1]
    doc = json.load(open(os.path.join(src_dir, "samples.json"), encoding="utf-8"))
    by_id = {s["id"]: s for s in doc["samples"]}
    manifest_path = os.path.join(ROOT, "manifest.json")
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    manifest["kits"] = [k for k in manifest["kits"] if k["id"] not in {k[0] for k in KITS}]
    for kit_id, label, ids in KITS:
        out_dir = os.path.join(ROOT, kit_id)
        os.makedirs(out_dir, exist_ok=True)
        pads = []
        for pad, (key, lab, sid) in enumerate(zip(KEYS, LABELS, ids)):
            s = by_id[sid]
            src = os.path.join(src_dir, os.path.basename(s["file"]))
            dst = os.path.join(out_dir, key + ".wav")
            shutil.copyfile(src, dst)
            pads.append({"pad": pad, "key": key, "label": lab, "path": f"samples/{kit_id}/{key}.wav",
                         "frames": frames(dst), "source": s["name"]})
        manifest["kits"].append({"id": kit_id, "label": label, "real": True, "pads": pads})
        print(kit_id, "ok")
    manifest["version"] = "0.2.0"
    manifest["license"] = "CC0-1.0"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
