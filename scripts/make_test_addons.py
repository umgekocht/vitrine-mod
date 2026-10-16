"""Baut drei Test-Pakete fuer Addons (ROADMAP M10) und legt sie signiert in
die lokale Vitrine-Bibliothek eines Profils.

  spassglas.mehr-rezepte      Addon (Datenpaket): Sattel, Namensschild, Faden aus Wolle
  spassglas.inventar-behalten Addon (Datenpaket): Spielregel keepInventory an
  spassglas.biom-anzeige      HUD-Modul: Biom und Uhrzeit oben links

Aufruf:  python scripts/make_test_addons.py <ziel-library-ordner>
Braucht den Signaturschluessel unter %USERPROFILE%/.vitrine/signing-key
(cli/vitrine.js sign).
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine", "font", "inter.ttf")
CLI = os.path.join(ROOT, "cli", "vitrine.js")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(data if isinstance(data, str) else json.dumps(data, indent=2) + "\n")


def preview(path, title, lines, c1, c2):
    W, H = 1280, 720
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line((0, y, W, y), fill=tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)))
    f1 = ImageFont.truetype(FONT, 76)
    f2 = ImageFont.truetype(FONT, 40)
    d.text((80, 110), title, font=f1, fill=(255, 255, 255))
    y = 260
    for line in lines:
        d.rounded_rectangle((80, y, 80 + int(d.textlength(line, font=f2)) + 48, y + 70), radius=14, fill=(0, 0, 0, 0),
                            outline=(255, 255, 255), width=3)
        d.text((104, y + 12), line, font=f2, fill=(255, 255, 255))
        y += 100
    # Paket-Icons duerfen hoechstens 1024 px breit sein (PackageImageBytes).
    img.resize((960, 540), Image.LANCZOS).save(path, optimize=True)


def manifest(pid, name, category, caps, desc):
    return {"schemaVersion": 1, "id": pid, "name": name, "version": "1.0.0", "category": category,
            "author": "Spassglas Studio", "description": desc, "capabilities": caps, "icon": "icon.png",
            "license": "CC-BY-4.0"}


def recipe(pattern, key, result, count=1):
    return {"type": "minecraft:crafting_shaped", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"id": result, "count": count}}


def build(work):
    # 1. Mehr Rezepte
    p = os.path.join(work, "mehr-rezepte")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.mehr-rezepte", "Mehr Rezepte", "addon", ["verhalten"],
        "Neue Crafting-Rezepte: Sattel aus Leder, Namensschild aus Papier und Faden, Faden aus Wolle. "
        "Wirkt sofort in deiner Welt, ohne Neustart."))
    base = os.path.join(p, "data", "vitrine_rezepte", "recipe")
    write(os.path.join(base, "sattel.json"), recipe(["LLL", "L L"], {"L": "minecraft:leather"}, "minecraft:saddle"))
    write(os.path.join(base, "namensschild.json"),
          recipe([" S", "P "], {"S": "minecraft:string", "P": "minecraft:paper"}, "minecraft:name_tag"))
    write(os.path.join(base, "faden.json"),
          {"type": "minecraft:crafting_shapeless", "ingredients": [{"tag": "minecraft:wool"}],
           "result": {"id": "minecraft:string", "count": 4}})
    preview(os.path.join(p, "icon.png"), "Mehr Rezepte",
            ["6 Leder  ->  Sattel", "Papier + Faden  ->  Namensschild", "Wolle  ->  4 Faden"],
            (150, 90, 40), (60, 30, 20))

    # 2. Inventar behalten
    p = os.path.join(work, "inventar-behalten")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.inventar-behalten", "Inventar behalten", "addon", ["verhalten"],
        "Beim Sterben behaeltst du dein Inventar (Spielregel keepInventory). Wird beim Aktivieren sofort "
        "gesetzt. Beim Deaktivieren bleibt die Regel stehen, bis du sie mit /gamerule zuruecksetzt."))
    write(os.path.join(p, "data", "vitrine_inventar", "function", "an.mcfunction"),
          "gamerule keepInventory true\n"
          "tellraw @a {\"text\":\"Vitrine: Inventar behalten ist an.\",\"color\":\"green\"}\n")
    write(os.path.join(p, "data", "minecraft", "tags", "function", "load.json"),
          {"values": ["vitrine_inventar:an"]})
    preview(os.path.join(p, "icon.png"), "Inventar behalten",
            ["keepInventory: an", "Sterben ohne Verlust"], (40, 130, 90), (16, 50, 40))

    # 3. Biom-Anzeige (HUD, im Store unter Addons)
    p = os.path.join(work, "biom-anzeige")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.biom-anzeige", "Biom-Anzeige", "hud", ["hud"],
        "Zeigt oben links, in welchem Biom du stehst, und die Uhrzeit."))
    write(os.path.join(p, "hud", "main.json"), {"schemaVersion": 1, "widgets": [{
        "id": "biom-liste", "type": "list", "anchor": "top-left", "offset": {"x": 4, "y": 4}, "scale": 1.0,
        "visibility": {"type": "outsideMenu"},
        "entries": [
            {"kind": "dynamic", "source": "biome", "label": "Biom: "},
            {"kind": "dynamic", "source": "time", "label": "Zeit: "}]}]})
    preview(os.path.join(p, "icon.png"), "Biom-Anzeige", ["Biom: plains", "Zeit: 08:15"],
            (60, 140, 210), (20, 50, 90))
    # 4. Gluecks-Bergbau (Live-Regeln, AD2): wirkt sofort beim Einschalten
    p = os.path.join(work, "gluecks-bergbau")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.gluecks-bergbau", "Gluecks-Bergbau", "addon", ["verhalten"],
        "Beim Abbauen von Stein findest du manchmal Bonus-Erz, Diamanterz gibt Eile und XP. "
        "Live-Regeln: wirken sofort, ohne Neustart und ohne Welt-Neuladen."))
    write(os.path.join(p, "regeln", "bergbau.json"), {"regeln": [
        {"wenn": "block_abgebaut", "ziel": "minecraft:stone", "chance": 0.05,
         "dann": [{"geben": "minecraft:raw_iron", "anzahl": 1},
                  {"nachricht": "Gluecksfund: Eisen!", "actionbar": True}]},
        {"wenn": "block_abgebaut", "ziel": "minecraft:diamond_ore",
         "dann": [{"effekt": "minecraft:haste", "sekunden": 30, "stufe": 2}, {"xp": 20},
                  {"befehl": "particle minecraft:happy_villager ~ ~1 ~ 0.5 0.5 0.5 0 30"}]},
        {"wenn": "block_abgebaut", "ziel": "minecraft:deepslate_diamond_ore",
         "dann": [{"effekt": "minecraft:haste", "sekunden": 30, "stufe": 2}, {"xp": 20}]}]})
    preview(os.path.join(p, "icon.png"), "Gluecks-Bergbau", ["Stein: 5% Bonus-Eisen", "Diamant: Eile + XP"],
            (90, 90, 110), (30, 30, 45))

    # 5. Starthilfe (Live-Regeln): Begruessung, Brot beim Beitreten, Tipp alle 5 Minuten
    p = os.path.join(work, "starthilfe")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.starthilfe", "Starthilfe", "addon", ["verhalten"],
        "Begruesst dich beim Betreten der Welt mit Brot und Fackeln, ein Kuchen gibt Regeneration. "
        "Live-Regeln, ohne Neustart."))
    write(os.path.join(p, "regeln", "start.json"), {"regeln": [
        {"wenn": "beigetreten", "dann": [{"nachricht": "Willkommen, {spieler}! Starthilfe ist aktiv."},
                                         {"geben": "minecraft:bread", "anzahl": 4},
                                         {"geben": "minecraft:torch", "anzahl": 16}]},
        {"wenn": "item_benutzt", "ziel": "minecraft:cake",
         "dann": [{"effekt": "minecraft:regeneration", "sekunden": 10, "stufe": 1}]},
        {"wenn": "mob_besiegt", "ziel": "minecraft:zombie", "chance": 0.2,
         "dann": [{"geben": "minecraft:carrot", "anzahl": 2}]}]})
    preview(os.path.join(p, "icon.png"), "Starthilfe", ["Brot + Fackeln beim Start", "Zombies: Karotten"],
            (200, 150, 60), (70, 45, 15))
    return ["mehr-rezepte", "inventar-behalten", "biom-anzeige", "gluecks-bergbau", "starthilfe"]


def main(target):
    os.makedirs(target, exist_ok=True)
    work = tempfile.mkdtemp(prefix="vitrine-addons-")
    for name in build(work):
        src = os.path.join(work, name)
        out = os.path.join(target, "spassglas." + name + ".vpkg")
        for cmd in (["validate", src], ["pack", src, out], ["sign", out]):
            r = subprocess.run(["node", CLI] + cmd, capture_output=True, text=True, encoding="utf-8")
            if r.returncode != 0:
                raise SystemExit(name + ": " + " ".join(cmd) + " fehlgeschlagen:\n" + r.stdout + r.stderr)
        print("fertig:", out)
    shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main(sys.argv[1])
