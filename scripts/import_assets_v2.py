"""Uebernimmt umgekocht's Asset-Paket v2 (vitrine-assets-v2.zip) in den Mod.

- icons_hd/*: auf 2x der Zeichengroesse herunterrechnen (Lanczos). Minecraft
  verkleinert ohne Mipmaps; groessere Texturen flimmern (Befund 2026-10-06).
- ui/*@4x.png (Nine-Slice-Rahmen, Kippschalter): auf 2x der Nennsize, also
  passend zur Vitrine-GUI-Skalierung 2 (VitrineGuiScale).
- empty_state, placeholder_card, logo: Originale nach BIlder/ und gui/.
- docs/brand: Master-Dateien uebernehmen.
Jede GUI-Textur bekommt blur-mcmeta (glatt statt pixelig).

Aufruf:  python scripts/import_assets_v2.py <entpackter-ordner>
"""
import io
import json
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "textures", "gui")

# Zeichengroesse in GUI-Einheiten je Icon-Familie -> Datei bekommt 2x davon.
DRAW = {"nav_": 20, "cap_": 20, "ui_": 16, "category_": 32}


def mcmeta(path):
    with io.open(path + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)
        f.write("\n")


def main(src):
    sgui = os.path.join(src, "assets", "vitrine", "textures", "gui")
    # Icons
    hd = os.path.join(sgui, "icons_hd")
    out_icons = os.path.join(GUI, "icons")
    for name in sorted(os.listdir(hd)):
        size = next(v for k, v in DRAW.items() if name.startswith(k)) * 2
        img = Image.open(os.path.join(hd, name)).convert("RGBA").resize((size, size), Image.LANCZOS)
        target = os.path.join(out_icons, name)
        img.save(target, optimize=True)
        mcmeta(target)
    # Nine-Slice & Schalter (aus @4x auf 2x)
    out_ui = os.path.join(GUI, "ui")
    os.makedirs(out_ui, exist_ok=True)
    for name in sorted(os.listdir(os.path.join(sgui, "ui"))):
        if not name.endswith("@4x.png"):
            continue
        img = Image.open(os.path.join(sgui, "ui", name)).convert("RGBA")
        img = img.resize((img.width // 2, img.height // 2), Image.LANCZOS)
        target = os.path.join(out_ui, name.replace("@4x", ""))
        img.save(target, optimize=True)
        mcmeta(target)
    shutil.copyfile(os.path.join(sgui, "ui", "ui_textures.md"), os.path.join(out_ui, "ui_textures.md"))
    # Einzelbilder
    for name in ("empty_state.png", "placeholder_card.png"):
        shutil.copyfile(os.path.join(sgui, name), os.path.join(ROOT, "BIlder", name))
    for name in ("logo.png", "logo_text_only.png"):
        shutil.copyfile(os.path.join(sgui, name), os.path.join(GUI, name))
        mcmeta(os.path.join(GUI, name))
    # Brand-Master
    brand = os.path.join(ROOT, "docs", "brand")
    shutil.copytree(os.path.join(src, "docs", "brand", "icons_hd"), os.path.join(brand, "icons_hd"), dirs_exist_ok=True)
    for name in ("empty_state_master.png", "vitrine-icon.png"):
        shutil.copyfile(os.path.join(src, "docs", "brand", name), os.path.join(brand, name))
    print("uebernommen")


if __name__ == "__main__":
    main(sys.argv[1])
