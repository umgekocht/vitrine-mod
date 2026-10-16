"""Erzeugt je Paket-Kategorie ein scharfes Vorschaubild (1536x864, 16:9).

Pakete ohne eigenes Bild zeigten bisher alle dasselbe Vitrine-"V" -- und das
pixelig. Jetzt: ruhiger Farbverlauf je Kategorie plus das grosse, weiss
gezeichnete Kategorie-Symbol aus den hochaufgeloesten Icons.

Aufruf:  python scripts/make_category_placeholders.py
"""
import io
import json
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "textures", "gui")
ICONS = os.path.join(GUI, "icons")
W, H = 1536, 864

# Kategorie -> (Symbol-Datei, Farbe oben links, Farbe unten rechts)
CATEGORIES = {
    "texturen": ("cap_texturen.png", (46, 125, 90), (18, 40, 34)),
    "theme": ("cap_theme.png", (124, 77, 196), (34, 24, 58)),
    "hud": ("cap_hud.png", (38, 110, 200), (16, 30, 58)),
    "shader": ("cap_shader.png", (214, 140, 40), (58, 36, 16)),
    "sound": ("cap_sound.png", (200, 70, 110), (56, 20, 34)),
    "sprache": ("cap_sprache.png", (30, 150, 170), (14, 42, 50)),
    "cosmetics": ("cap_cosmetics.png", (190, 90, 60), (52, 26, 20)),
    "bundle": ("nav_bibliothek_filled.png", (70, 90, 120), (24, 28, 38)),
    "addon": ("category_addon.png", (40, 150, 120), (16, 44, 40)),
}


def gradient(c1, c2):
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            t = (x / W * 0.6 + y / H * 0.4)
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return img


def main():
    for cat, (icon_file, c1, c2) in CATEGORIES.items():
        base = gradient(c1, c2).convert("RGBA")
        # weicher Lichtkreis hinter dem Symbol
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(glow)
        r = 300
        d.ellipse((W // 2 - r, H // 2 - r, W // 2 + r, H // 2 + r), fill=(255, 255, 255, 38))
        glow = glow.filter(ImageFilter.GaussianBlur(60))
        base = Image.alpha_composite(base, glow)

        icon = Image.open(os.path.join(ICONS, icon_file)).convert("RGBA")
        size = 260
        icon = icon.resize((size, size), Image.LANCZOS)
        # Schatten
        shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sh = Image.new("RGBA", icon.size, (0, 0, 0, 0))
        sh.putalpha(icon.getchannel("A").point(lambda a: int(a * 0.45)))
        shadow.paste(sh, ((W - size) // 2 + 10, (H - size) // 2 + 14), sh)
        shadow = shadow.filter(ImageFilter.GaussianBlur(14))
        base = Image.alpha_composite(base, shadow)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        layer.paste(icon, ((W - size) // 2, (H - size) // 2), icon)
        base = Image.alpha_composite(base, layer)

        out = os.path.join(GUI, "placeholder_" + cat + ".png")
        base.convert("RGB").save(out, optimize=True)
        with io.open(out + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)
            f.write("\n")
        print("geschrieben:", os.path.basename(out))


if __name__ == "__main__":
    main()
