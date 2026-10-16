# `vendor/vitrine-cli/` — mitgelieferte Kopie, nicht von Hand bearbeiten

Dieser Ordner ist eine **byte-identische Kopie** von drei Pfaden aus dem
Vitrine-Hauptrepo:

- `cli/vitrine.js` + `cli/lib/*.js` — die Vitrine-CLI (D7): `validate`,
  `pack`, `sign`. Keine Laufzeit-Abhängigkeit, nur `node:*`-Bordmittel.
- `designer/schema-validate.js` — derselbe Schema-Auswerter wie im Vitrine
  Designer (Charter-Regel 8: „ein Schema, zwei Seiten").
- `registry/schema/vitrine.schema.json` — das eine Manifest-Schema.

## Warum vendern statt aus dem Hauptrepo nachladen

`release.yml` in diesem Repo braucht diesen Code, um dein Paket beim Taggen
zu prüfen/bauen/signieren. Der naheliegende Weg wäre, das öffentliche
Vitrine-Hauptrepo (`umgekocht/vitrine-mod`) per zweitem `actions/checkout`
nachzuladen — das geht aber erst, sobald dieses Repo öffentlich ist
(ROADMAP-Zeile A4). Damit „Use this template" schon heute funktioniert, ohne
auf A4 zu warten, liegt der nötige Code hier direkt bei.

## Wie diese Kopie aktuell gehalten wird

Nur im **Vitrine-Hauptrepo** (nicht in einem aus dem Template erzeugten
Autoren-Repo!) mit:

```
node templates/package-repo/scripts/sync-vendor.mjs
```

`templates/package-repo/scripts/dry-run.mjs` prüft vor jedem Trockenlauf,
dass diese Kopie noch synchron ist, und bricht mit einer klaren Meldung ab,
falls `cli/`, `designer/schema-validate.js` oder
`registry/schema/vitrine.schema.json` sich geändert haben, ohne dass diese
Kopie nachgezogen wurde.

Sobald `umgekocht/vitrine-mod` öffentlich ist (A4), kann `release.yml` in einer
späteren Version wieder auf den zweiten `actions/checkout` umgestellt werden
— dieser Ordner entfiele dann. Bis dahin ist er der einzige Weg, wie ein
frisch aus dem Template erzeugtes Repo tatsächlich funktioniert.
