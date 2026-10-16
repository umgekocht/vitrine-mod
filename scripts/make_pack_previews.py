"""Erzeugt echte Vorschaubilder (1280x720) fuer die Pakete des Starter-Katalogs.

Jedes Bild zeigt, was das Paket tatsaechlich tut -- gebaut aus seinem eigenen
Inhalt, nichts ausgedacht:
  texturen  -> eine Wand aus den Block-Texturen des Pakets (pixelgenau)
  theme     -> der Vitrine-Store in den Farben aus theme.json
  hud       -> eine Spielszene mit den HUD-Elementen aus hud/main.json
  sprache   -> die uebersetzten Texte aus der Sprachdatei

Aufruf:  python scripts/make_pack_previews.py <packs-ordner>
Schreibt <paket>/icon.png (die Katalog-URL zeigt bereits darauf).
"""
import glob
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "font", "inter.ttf")
W, H = 1280, 720


def font(size, bold=False):
    f = ImageFont.truetype(FONT, size)
    if bold:
        try:
            f.set_variation_by_axes([14, 650])
        except Exception:
            pass
    return f


def hexcolor(value, default="#202028"):
    if isinstance(value, dict):
        value = value.get("hex", default)
    value = (value or default).lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def textures_preview(pack):
    tex = sorted(glob.glob(os.path.join(pack, "assets", "minecraft", "textures", "block", "*.png")))
    imgs = [Image.open(t).convert("RGBA") for t in tex]
    img = Image.new("RGB", (W, H))
    size = 120
    i = 0
    for y in range(0, H, size):
        for x in range(0, W, size):
            block = imgs[(x // size + y // size * 3) % len(imgs)].resize((size, size), Image.NEAREST)
            img.paste(block, (x, y), block)
            i += 1
    # weiche Vignette, damit es wie eine Szene und nicht wie ein Raster wirkt
    vignette = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(vignette)
    d.ellipse((-W * 0.2, -H * 0.3, W * 1.2, H * 1.3), fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(120))
    dark = Image.new("RGB", (W, H), (10, 10, 14))
    return Image.composite(img, dark, vignette)


def theme_preview(pack):
    """Hell und dunkel nebeneinander (schraeg geteilt), wenn das Theme beide hat."""
    theme = json.load(open(os.path.join(pack, "theme.json"), encoding="utf-8"))
    dark = theme_variant(theme.get("dark") or theme.get("light"))
    if not theme.get("light") or not theme.get("dark"):
        return dark
    light = theme_variant(theme["light"])
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon([(W * 0.62, 0), (W, 0), (W, H), (W * 0.42, H)], fill=255)
    return Image.composite(light, dark, mask)


def theme_variant(t):
    bg, surface = hexcolor(t["color.bg"]), hexcolor(t["color.surfaceSolid"])
    text, muted, accent = hexcolor(t["color.text"]), hexcolor(t["color.textMuted"]), hexcolor(t["color.accent"])
    border = hexcolor(t.get("color.borderTranslucent"), "#444450")
    img = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, W, 90), fill=surface)
    d.rounded_rectangle((150, 24, W - 180, 66), radius=10, fill=bg, outline=border)
    d.text((172, 34), "Search packages", font=font(22), fill=muted)
    d.rectangle((0, 90, 110, H), fill=surface)
    for i in range(6):
        c = accent if i == 0 else muted
        d.rounded_rectangle((35, 130 + i * 80, 75, 170 + i * 80), radius=8, outline=c, width=4)
    d.rounded_rectangle((150, 120, W - 40, 380), radius=18, fill=accent)
    d.text((190, 300), "Featured", font=font(54, True), fill=hexcolor(t.get("color.accentContrast"), "#000000"))
    for i in range(4):
        x = 150 + i * 275
        d.rounded_rectangle((x, 420, x + 250, 690), radius=14, fill=surface, outline=border)
        d.rounded_rectangle((x + 12, 432, x + 238, 560), radius=10, fill=bg)
        d.text((x + 16, 580), "Package " + str(i + 1), font=font(26, True), fill=text)
        d.text((x + 16, 620), "Spassglas Studio", font=font(20), fill=muted)
    return img


def hud_preview(pack):
    hud = json.load(open(os.path.join(pack, "hud", "main.json"), encoding="utf-8"))
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):  # Himmel
        t = y / H
        d.line((0, y, W, y), fill=(int(120 + 60 * t), int(170 + 40 * t), 235))
    d.rectangle((0, int(H * 0.68), W, H), fill=(92, 140, 60))
    d.rectangle((0, int(H * 0.68) + 26, W, H), fill=(120, 86, 58))
    samples = {"fps": "120", "coordinates.x": "-214", "coordinates.y": "71",
               "coordinates.z": "1036", "time": "13:42"}
    lines = []
    for w in hud["widgets"]:
        entries = [w["content"]] if w["type"] == "text" else w.get("entries", [])
        for e in entries:
            lines.append(e.get("label", "") + samples.get(e.get("source", ""), "?"))
    f = font(40, True)
    pad = 18
    widths = [d.textlength(l, font=f) for l in lines]
    box_w = int(max(widths)) + pad * 2
    box_h = len(lines) * 54 + pad * 2
    x0 = W - box_w - 40
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rounded_rectangle((x0, 40, x0 + box_w, 40 + box_h), radius=14, fill=(0, 0, 0, 140))
    img = Image.alpha_composite(img.convert("RGBA"), overlay)
    d = ImageDraw.Draw(img)
    for i, line in enumerate(lines):
        d.text((x0 + box_w - pad - widths[i], 40 + pad + i * 54), line, font=f, fill=(255, 255, 255))
    return img.convert("RGB")


def language_preview(pack):
    lang = glob.glob(os.path.join(pack, "assets", "minecraft", "lang", "*.json"))[0]
    entries = list(json.load(open(lang, encoding="utf-8")).values())
    img = Image.new("RGB", (W, H), (24, 26, 34))
    d = ImageDraw.Draw(img)
    for y in range(H):
        d.line((0, y, W, y), fill=(int(20 + 20 * y / H), int(60 + 50 * y / H), int(80 + 40 * y / H)))
    y = 120
    for i, text in enumerate(entries[:5]):
        bw = int(d.textlength(text, font=font(44, True))) + 60
        d.rounded_rectangle(((W - bw) // 2, y, (W + bw) // 2, y + 80), radius=12,
                            fill=(255, 255, 255) if i == 0 else (0, 0, 0))
        d.text(((W - bw) // 2 + 30, y + 14), text, font=font(44, True),
               fill=(20, 20, 26) if i == 0 else (255, 255, 255))
        y += 105
    return img


def main(packs_dir):
    for pack in sorted(glob.glob(os.path.join(packs_dir, "*", "vitrine.json"))):
        pack = os.path.dirname(pack)
        category = json.load(open(os.path.join(pack, "vitrine.json"), encoding="utf-8"))["category"]
        make = {"texturen": textures_preview, "theme": theme_preview, "hud": hud_preview,
                "sprache": language_preview}.get(category)
        if not make:
            print("uebersprungen (keine Vorlage):", os.path.basename(pack), category)
            continue
        out = os.path.join(pack, "icon.png")
        make(pack).save(out, optimize=True)
        print("geschrieben:", os.path.basename(pack), os.path.getsize(out), "Bytes")


if __name__ == "__main__":
    main(sys.argv[1])
