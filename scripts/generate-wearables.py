import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("pretupid-client/src/main/resources/assets/pretupid")

PALETTE = {
    "black": (28, 28, 34),
    "charcoal": (52, 52, 60),
    "red": (196, 48, 48),
    "gold": (232, 186, 52),
    "gold_dark": (176, 128, 30),
    "ruby": (212, 40, 90),
    "sapphire": (52, 110, 230),
    "emerald": (40, 180, 100),
    "halo": (255, 232, 140),
    "pink": (240, 120, 170),
    "cyan": (70, 200, 220),
    "yellow": (250, 220, 70),
    "white": (240, 240, 240),
    "purple": (98, 58, 168),
    "purple_dark": (70, 40, 122),
    "star": (255, 230, 120),
    "orange": (230, 120, 40),
    "fur": (90, 70, 60),
    "blue": (60, 120, 220),
    "green": (70, 170, 80),
    "silver": (180, 186, 196),
    "graphite": (40, 44, 52),
}
NAMES = list(PALETTE)


def palette_texture():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for index, name in enumerate(NAMES):
        x = (index % 8) * 2
        y = (index // 8) * 2
        r, g, b = PALETTE[name]
        for dx in range(2):
            for dy in range(2):
                shade = 1.0 if (dx + dy) % 2 == 0 else 0.92
                image.putpixel((x + dx, y + dy), (int(r * shade), int(g * shade), int(b * shade), 255))
    return image


def uv(color):
    index = NAMES.index(color)
    x = (index % 8) * 2
    y = (index // 8) * 2
    return [x + 0.5, y + 0.5, x + 1.5, y + 1.5]


def box(start, end, color):
    faces = {face: {"uv": uv(color), "texture": "#palette"} for face in ("north", "south", "east", "west", "up", "down")}
    return {"from": start, "to": end, "faces": faces}


TOP = 14.4

HATS = {
    "top_hat": [
        box([0, TOP, 0], [16, TOP + 1, 16], "black"),
        box([3, TOP + 1, 3], [13, TOP + 2.2, 13], "red"),
        box([3, TOP + 2.2, 3], [13, TOP + 9.6, 13], "black"),
    ],
    "crown": [
        box([1.6, TOP - 0.4, 1.6], [14.4, TOP + 2, 2.6], "gold"),
        box([1.6, TOP - 0.4, 13.4], [14.4, TOP + 2, 14.4], "gold"),
        box([1.6, TOP - 0.4, 2.6], [2.6, TOP + 2, 13.4], "gold"),
        box([13.4, TOP - 0.4, 2.6], [14.4, TOP + 2, 13.4], "gold"),
        box([1.6, TOP + 2, 1.6], [3.4, TOP + 4, 2.6], "gold"),
        box([7.1, TOP + 2, 1.6], [8.9, TOP + 4.6, 2.6], "gold"),
        box([12.6, TOP + 2, 1.6], [14.4, TOP + 4, 2.6], "gold"),
        box([1.6, TOP + 2, 13.4], [3.4, TOP + 4, 14.4], "gold"),
        box([7.1, TOP + 2, 13.4], [8.9, TOP + 4.6, 14.4], "gold"),
        box([12.6, TOP + 2, 13.4], [14.4, TOP + 4, 14.4], "gold"),
        box([7.2, TOP + 0.4, 1.2], [8.8, TOP + 1.6, 1.6], "ruby"),
        box([7.2, TOP + 0.4, 14.4], [8.8, TOP + 1.6, 14.8], "sapphire"),
        box([1.2, TOP + 0.4, 7.2], [1.6, TOP + 1.6, 8.8], "emerald"),
        box([14.4, TOP + 0.4, 7.2], [14.8, TOP + 1.6, 8.8], "emerald"),
    ],
    "halo": [
        box([3, TOP + 4, 3], [13, TOP + 4.8, 4], "halo"),
        box([3, TOP + 4, 12], [13, TOP + 4.8, 13], "halo"),
        box([3, TOP + 4, 4], [4, TOP + 4.8, 12], "halo"),
        box([12, TOP + 4, 4], [13, TOP + 4.8, 12], "halo"),
    ],
    "party_hat": [
        box([4, TOP, 4], [12, TOP + 2.4, 12], "pink"),
        box([5, TOP + 2.4, 5], [11, TOP + 4.8, 11], "cyan"),
        box([6, TOP + 4.8, 6], [10, TOP + 7.2, 10], "pink"),
        box([7, TOP + 7.2, 7], [9, TOP + 9.2, 9], "cyan"),
        box([6.8, TOP + 9.2, 6.8], [9.2, TOP + 10.8, 9.2], "yellow"),
    ],
    "wizard_hat": [
        box([0, TOP, 0], [16, TOP + 0.8, 16], "purple_dark"),
        box([3, TOP + 0.8, 3], [13, TOP + 3.6, 13], "purple"),
        box([4.5, TOP + 3.6, 4.5], [11.5, TOP + 6.4, 11.5], "purple"),
        box([6, TOP + 6.4, 6.5], [10, TOP + 9, 10.5], "purple"),
        box([7, TOP + 9, 8], [9, TOP + 11.4, 10], "purple"),
        box([7.4, TOP + 1.6, 2.6], [8.6, TOP + 2.8, 3], "star"),
    ],
    "cat_ears": [
        box([2.4, TOP, 6], [5.6, TOP + 2.4, 8], "fur"),
        box([3.2, TOP + 2.4, 6], [4.8, TOP + 3.6, 8], "fur"),
        box([3, TOP + 0.4, 5.6], [5, TOP + 2.2, 6], "pink"),
        box([10.4, TOP, 6], [13.6, TOP + 2.4, 8], "fur"),
        box([11.2, TOP + 2.4, 6], [12.8, TOP + 3.6, 8], "fur"),
        box([11, TOP + 0.4, 5.6], [13, TOP + 2.2, 6], "pink"),
    ],
    "propeller_cap": [
        box([1.6, TOP - 0.8, 1.6], [8, TOP + 1.6, 14.4], "red"),
        box([8, TOP - 0.8, 1.6], [14.4, TOP + 1.6, 14.4], "blue"),
        box([3, TOP - 0.8, -1.6], [13, TOP - 0.2, 1.6], "yellow"),
        box([7.5, TOP + 1.6, 7.5], [8.5, TOP + 3.6, 8.5], "silver"),
        box([2, TOP + 3.6, 7.4], [14, TOP + 4.2, 8.6], "yellow"),
    ],
    "headphones": [
        box([1, TOP, 7], [15, TOP + 1.2, 9], "graphite"),
        box([0.2, TOP - 4, 7.2], [1.2, TOP, 8.8], "graphite"),
        box([14.8, TOP - 4, 7.2], [15.8, TOP, 8.8], "graphite"),
        box([-0.4, TOP - 9, 5.4], [1.4, TOP - 4, 10.6], "cyan"),
        box([14.6, TOP - 9, 5.4], [16.4, TOP - 4, 10.6], "cyan"),
    ],
}

BOOTS = {
    "sneakers": ("white", "red", "white"),
    "cowboy_boots": ("fur", "orange", "gold_dark"),
    "flame_boots": ("red", "orange", "yellow"),
    "frost_boots": ("cyan", "white", "sapphire"),
    "gold_boots": ("gold", "gold_dark", "halo"),
}


def boot_texture(base, accent, trim):
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def put(x, y, color):
        image.putpixel((x, y), PALETTE[color] + (255,))

    for x in range(8, 12):
        for y in range(16, 20):
            put(x, y, accent)
    for face in range(4):
        x0 = face * 4
        for x in range(x0, x0 + 4):
            for y in range(25, 32):
                color = base
                if y == 25:
                    color = trim
                elif y == 31:
                    color = accent
                elif face == 1 and y in (28, 29) and x in (x0 + 1, x0 + 2):
                    color = accent
                put(x, y, color)
    return image


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


(ROOT / "textures/item").mkdir(parents=True, exist_ok=True)
palette_texture().save(ROOT / "textures/item/wearable_palette.png")
for name, elements in HATS.items():
    write_json(ROOT / f"models/item/hat/{name}.json", {
        "textures": {"palette": "pretupid:item/wearable_palette", "particle": "pretupid:item/wearable_palette"},
        "elements": elements,
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    })
    write_json(ROOT / f"items/hat/{name}.json", {"model": {"type": "minecraft:model", "model": f"pretupid:item/hat/{name}"}})

for name, colors in BOOTS.items():
    texture = ROOT / f"textures/entity/equipment/humanoid/{name}.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    boot_texture(*colors).save(texture)
    write_json(ROOT / f"equipment/{name}.json", {"layers": {"humanoid": [{"texture": f"pretupid:{name}"}]}})
