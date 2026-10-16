"""Baut die Sprachdateien der Mod aus Uebersetzungstabellen (eine Zeile je Schluessel,
in derselben Reihenfolge wie en_us.json: "<nr>\t<text>").

Prueft je Zeile, dass Nummer, Platzhalter (%s, %d) und Anzahl stimmen; fehlt eine
Zeile oder passt ein Platzhalter nicht, wird der englische Text genommen und
gemeldet. Aufruf: python scripts/build_translations.py <ordner-mit-tsv>
"""
import collections
import json
import os
import re
import sys

LANG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "mod", "src", "main", "resources", "assets", "vitrine", "lang")
PH = re.compile(r"%(\d+\$)?[sd]")


def main(src):
    en = json.load(open(os.path.join(LANG, "en_us.json"), encoding="utf-8"),
                   object_pairs_hook=collections.OrderedDict)
    keys = list(en.keys())
    for name in sorted(os.listdir(src)):
        if not name.endswith(".tsv"):
            continue
        code = name[:-4]
        rows = {}
        for line in open(os.path.join(src, name), encoding="utf-8"):
            line = line.rstrip("\n")
            if "\t" not in line:
                continue
            nr, text = line.split("\t", 1)
            rows[int(nr)] = text.replace("\\n", "\n")
        out = collections.OrderedDict()
        bad = []
        for i, k in enumerate(keys, start=1):
            t = rows.get(i)
            if t is None or len(PH.findall(t)) != len(PH.findall(en[k])):
                bad.append(f"{i} {k}")
                t = en[k]
            out[k] = t
        with open(os.path.join(LANG, code + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"{code}: {len(keys) - len(bad)}/{len(keys)} uebersetzt" + (f", Englisch fuer: {bad[:8]}" if bad else ""))


if __name__ == "__main__":
    main(sys.argv[1])
