"""Macht die grossen GUI-Bilder glatt statt pixelig.

Minecraft zeichnet GUI-Texturen ohne Glaettung (naechster Nachbar). Das
512x288-Kachelbild wird im Hero auf ~1700 Bildschirmpixel gezogen und zerfaellt
in Bloecke. Hier: jedes Bild aus BIlder/ einmal hochwertig (Lanczos) auf das
SCALE-fache hochrechnen und eine .png.mcmeta mit "blur": true daneben legen,
damit Minecraft beim Zeichnen linear filtert.

Aufruf:  python scripts/smooth_gui_images.py
"""
import io
import json
import os

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "textures", "gui")
ICONS = os.path.join(GUI, "icons")
SRC = os.path.join(ROOT, "BIlder")

# (Quelle in BIlder/ oder None = vorhandene Datei, Ziel, Faktor)
JOBS = [
    ("placeholder_card.png", os.path.join(GUI, "placeholder_card.png"), 3),
    ("empty_state.png", os.path.join(GUI, "empty_state.png"), 3),
]
# Kategorie-Kacheln (32 px) aus dem Original-ZIP-Stand.
for name in ("bundle", "cosmetics", "hud", "shader", "sound", "sprache", "texturen", "theme"):
    JOBS.append((None, os.path.join(ICONS, "category_" + name + ".png"), 2))


def write_mcmeta(png_path):
    with io.open(png_path + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)
        f.write("\n")


def main():
    for src_name, target, factor in JOBS:
        src = Image.open(os.path.join(SRC, src_name) if src_name else target).convert("RGBA")
        w, h = src.size
        if src_name is None and w > 32:
            print("schon hochgerechnet:", os.path.basename(target))
            write_mcmeta(target)
            continue
        big = src.resize((w * factor, h * factor), Image.LANCZOS)
        big = big.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
        big.save(target, optimize=True)
        write_mcmeta(target)
        print(os.path.basename(target), (w, h), "->", big.size)


if __name__ == "__main__":
    main()
