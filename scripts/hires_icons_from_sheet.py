"""Erzeugt hochaufgeloeste GUI-Icons aus BIlder/icon_sheet.png.

Die ausgelieferten Icons sind 16-20 px gross. Minecraft vergroessert sie bei
hoher GUI-Skalierung pixelig; ihre grauen Kantenpixel werden dabei zu
sichtbarem "Rauschen". Das Icon-Sheet zeigt dieselben Icons glatt in rund
fuenffacher Groesse. Dieses Skript schneidet jedes Icon aus dem Sheet, macht
den dunklen Hintergrund transparent, richtet es exakt so aus wie das kleine
Original (gleiche relative Position und Groesse im Quadrat) und schreibt es
mit OUT_SCALE-facher Kantenlaenge unter demselben Namen zurueck. Dazu eine
.png.mcmeta mit "blur": true, damit Minecraft weich statt pixelig skaliert.

Die Zeichencode-Stellen rechnen UV in Anteilen der Textur (region == texture
size), deshalb ist keine Code-Aenderung noetig.

Aufruf:  python scripts/hires_icons_from_sheet.py
"""
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(ROOT, "BIlder", "icon_sheet.png")
ICONS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "textures", "gui", "icons")
OUT_SCALE = 2  # = Darstellungsgroesse bei Vitrine-GUI-Skalierung 2; groesser flimmert beim Verkleinern
CELL = 200
COLS = 7

# Reihenfolge exakt wie im Sheet (zeilenweise, 7 Spalten).
ORDER = [
    "cap_cosmetics", "cap_hud", "cap_shader", "cap_sound", "cap_sprache", "cap_texturen", "cap_theme",
    "category_bundle", "category_cosmetics", "category_hud", "category_shader", "category_sound",
    "category_sprache", "category_texturen",
    "category_theme", "nav_bibliothek_filled", "nav_bibliothek_outline", "nav_cosmetics_filled",
    "nav_cosmetics_outline", "nav_einstellungen_filled", "nav_einstellungen_outline",
    "nav_hud_filled", "nav_hud_outline", "nav_shader_filled", "nav_shader_outline", "nav_sound_sprache_filled",
    "nav_sound_sprache_outline", "nav_start_filled",
    "nav_start_outline", "nav_texturen_filled", "nav_texturen_outline", "nav_themes_filled", "nav_themes_outline",
    "ui_back", "ui_check",
    "ui_chevron_left", "ui_chevron_right", "ui_download", "ui_overflow", "ui_search", "ui_warning",
]
# Kategorie-Kacheln tragen im Sheet ihre Beschriftung im Bild -- die bleiben.
SKIP_PREFIX = ("category_",)


def alpha_from_dark_bg(img, bg):
    """Weiss mit Deckkraft aus der Helligkeit ueber dem Hintergrund."""
    bg_l = sum(bg[:3]) / 3
    out = Image.new("RGBA", img.size)
    src = img.load()
    dst = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b = src[x, y][:3]
            lum = (r + g + b) / 3
            a = max(0.0, min(1.0, (lum - bg_l) / (235 - bg_l)))
            dst[x, y] = (255, 255, 255, int(round(a * 255)))
    return out


def main():
    sheet = Image.open(SHEET).convert("RGB")
    bg = sheet.getpixel((5, 5))
    done = []
    for idx, name in enumerate(ORDER):
        if name.startswith(SKIP_PREFIX):
            continue
        col, row = idx % COLS, idx // COLS
        # Icon-Bereich der Zelle ohne Beschriftung darunter.
        cell = sheet.crop((col * CELL + 10, row * CELL + 5, col * CELL + CELL - 10, row * CELL + 158))
        hi = alpha_from_dark_bg(cell, bg)
        hi_box = hi.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()

        small_path = os.path.join(ICONS, name + ".png")
        # Ausrichtung immer am kleinen Original aus umgekocht's Asset-Paket messen.
        with zipfile.ZipFile(os.path.join(ROOT, "BIlder", "vitrineassets.zip")) as z:
            small = Image.open(io.BytesIO(z.read("assets/vitrine/textures/gui/icons/" + name + ".png"))).convert("RGBA")
        n = small.size[0]
        sm_box = small.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()

        # Massstab so, dass die Icon-Breite/Hoehe im Sheet der des Originals entspricht.
        sx = (hi_box[2] - hi_box[0]) / (sm_box[2] - sm_box[0])
        sy = (hi_box[3] - hi_box[1]) / (sm_box[3] - sm_box[1])
        s = (sx + sy) / 2
        # Quadrat im Sheet, das dem n*n-Original entspricht.
        left = hi_box[0] - sm_box[0] * s
        top = hi_box[1] - sm_box[1] * s
        square = hi.crop((int(round(left)), int(round(top)),
                          int(round(left + n * s)), int(round(top + n * s))))
        out = square.resize((n * OUT_SCALE, n * OUT_SCALE), Image.LANCZOS)
        out.save(small_path)
        with io.open(small_path + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)
            f.write("\n")
        done.append((name, n, n * OUT_SCALE))

    for name, n, big in done:
        print(f"{name}: {n}px -> {big}px")
    print(len(done), "Icons ersetzt")


if __name__ == "__main__":
    main()
