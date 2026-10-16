"""Uebernimmt umgekocht's vitrine-assets-v4.zip in die Mod (Stand 2026-10-07).

- Icons (nav_*, ui_*, category_*, badge_*, vitrine_button): 1:1 uebernommen.
- Nine-Slice-Rahmen und Kippschalter: die @4x-Datei unter dem normalen Namen
  (NineSlice rechnet mit relativen UVs, die Aufloesung ist frei).
- Collections und Banner-Platzhalter: auf 1200x640 bzw. 1600x600 verkleinert,
  damit sie im Grafikspeicher klein bleiben.
- Jede neue PNG bekommt eine .mcmeta mit blur/clamp (weich skaliert, kein Flimmern).
- Das App-Icon (docs/brand/vitrine-icon.png) wird das Mod-Icon (256x256).

Aufruf: python scripts/import_assets_v4.py <entpackter-zip-ordner>
"""
import json
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "vitrine")
META = json.dumps({"texture": {"blur": True, "clamp": True}}) + "\n"


def meta(png):
    m = png + ".mcmeta"
    if not os.path.exists(m):
        with open(m, "w", encoding="utf-8", newline="\n") as f:
            f.write(META)


def main(src):
    gui_src = os.path.join(src, "assets", "vitrine", "textures", "gui")
    gui_dst = os.path.join(DST, "textures", "gui")
    count = 0
    for d, _, files in os.walk(gui_src):
        rel = os.path.relpath(d, gui_src)
        for name in files:
            if not name.endswith(".png") or "@4x" in name:
                continue
            src_file = os.path.join(d, name)
            if rel == "ui":
                hi = os.path.join(d, name[:-4] + "@4x.png")
                if os.path.exists(hi):
                    src_file = hi
            out_dir = os.path.join(gui_dst, rel) if rel != "." else gui_dst
            os.makedirs(out_dir, exist_ok=True)
            out = os.path.join(out_dir, name)
            if rel == "collections":
                Image.open(src_file).convert("RGBA").resize((1200, 640), Image.LANCZOS).save(out, optimize=True)
            elif name == "banner_placeholder.png":
                Image.open(src_file).convert("RGBA").resize((1600, 600), Image.LANCZOS).save(out, optimize=True)
            else:
                shutil.copyfile(src_file, out)
            meta(out)
            count += 1
    icon = os.path.join(src, "docs", "brand", "vitrine-icon.png")
    if os.path.exists(icon):
        Image.open(icon).convert("RGBA").resize((256, 256), Image.LANCZOS).save(os.path.join(DST, "icon.png"))
        docs = os.path.join(ROOT, "docs", "brand")
        os.makedirs(docs, exist_ok=True)
        shutil.copyfile(icon, os.path.join(docs, "vitrine-icon.png"))
    for md in ("icons/icons_readme.md", "ui/ui_textures.md"):
        p = os.path.join(gui_src, md)
        if os.path.exists(p):
            shutil.copyfile(p, os.path.join(ROOT, "docs", "assets-v4-" + os.path.basename(md)))
    print("uebernommen:", count)


if __name__ == "__main__":
    main(sys.argv[1])
