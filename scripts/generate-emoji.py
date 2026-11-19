import json
import sys
from pathlib import Path

from PIL import Image

CELL = 32
COLUMNS = 32
ROWS = 32
FIRST_CODEPOINT = 0xF0000
ROOT = Path(__file__).resolve().parent.parent / "pretupid-client" / "src" / "main" / "resources" / "assets" / "pretupid"
LICENSE = """Emoji artwork: Twemoji by Twitter, Inc. and other contributors
https://github.com/jdecked/twemoji
Licensed under CC-BY 4.0: https://creativecommons.org/licenses/by/4.0/

Emoji names and ordering: emoji-data by Cal Henderson (iamcal)
https://github.com/iamcal/emoji-data
Licensed under the MIT License.

The images were scaled down to 32 pixels and packed into font sheets for Pretupid Client.
"""


def unified_to_text(unified):
    return "".join(chr(int(part, 16)) for part in unified.split("-"))


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate-emoji.py <path to the emoji-datasource-twitter package>")
    package = Path(sys.argv[1])
    entries = json.loads((package / "emoji.json").read_text(encoding="utf-8"))
    entries = [entry for entry in entries if entry.get("has_img_twitter") and entry.get("category") != "Component"]
    entries.sort(key=lambda entry: entry["sort_order"])
    images_dir = package / "img" / "twitter" / "64"
    per_sheet = COLUMNS * ROWS
    sheets_dir = ROOT / "textures" / "font" / "emoji"
    sheets_dir.mkdir(parents=True, exist_ok=True)
    for stale in sheets_dir.glob("*.png"):
        stale.unlink()
    providers = []
    index = []
    sheet_count = (len(entries) + per_sheet - 1) // per_sheet
    for sheet in range(sheet_count):
        image = Image.new("RGBA", (COLUMNS * CELL, ROWS * CELL), (0, 0, 0, 0))
        rows = []
        for row in range(ROWS):
            chars = []
            for column in range(COLUMNS):
                slot = sheet * per_sheet + row * COLUMNS + column
                if slot >= len(entries):
                    chars.append("\u0000")
                    continue
                entry = entries[slot]
                codepoint = FIRST_CODEPOINT + slot
                source = Image.open(images_dir / entry["image"]).convert("RGBA")
                scaled = source.resize((CELL, CELL), Image.Resampling.LANCZOS)
                image.paste(scaled, (column * CELL, row * CELL))
                chars.append(chr(codepoint))
                index.append({
                    "c": codepoint,
                    "n": entry["short_names"],
                    "u": unified_to_text(entry["unified"]),
                    "q": unified_to_text(entry["non_qualified"]) if entry.get("non_qualified") else None,
                    "g": entry["category"],
                })
            rows.append("".join(chars))
        if all(char == "\u0000" for char in rows[-1]):
            while rows and all(char == "\u0000" for char in rows[-1]):
                rows.pop()
            image = image.crop((0, 0, COLUMNS * CELL, len(rows) * CELL))
        image.save(sheets_dir / f"sheet_{sheet}.png", optimize=True)
        providers.append({"type": "bitmap", "file": f"pretupid:font/emoji/sheet_{sheet}.png", "ascent": 8, "height": 9, "chars": rows})
    (ROOT / "font").mkdir(parents=True, exist_ok=True)
    (ROOT / "font" / "emoji.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True), encoding="utf-8")
    (ROOT / "emoji").mkdir(parents=True, exist_ok=True)
    (ROOT / "emoji" / "emoji.json").write_text(json.dumps(index, ensure_ascii=True, separators=(",", ":")), encoding="utf-8")
    (ROOT / "emoji" / "LICENSE.txt").write_text(LICENSE, encoding="utf-8")
    print(f"{len(entries)} emoji in {sheet_count} sheets")


if __name__ == "__main__":
    main()
