# `templates/package-repo/` — Quelle des Vorlagen-Repos `vitrine-package-template`

Dieser Ordner ist der **lokale Bauplatz** fuer das oeffentliche, als
GitHub-Template markierte Repo `vitrine-package-template` (siehe
`ALLOWED-REMOTES.md`, ROADMAP D8). Das eigentliche Veroeffentlichen
(`gh repo create ... --template`, das Setzen des ersten echten Tags, der
Beweis „einmal echt durchgespielt") ist ein separater Auslieferungsschritt,
der von hier **nicht** ausgefuehrt wird -- dieser Ordner liefert nur den
Inhalt, der 1:1 in dieses neue Repo kommt.

## Aufbau

- [`package-repo/`](package-repo/) -- **das ist die Vorlage selbst.** Alles
  darunter wird zum Wurzelinhalt von `vitrine-package-template` und landet
  damit in jedem Repo, das ein Paket-Autor per „Use this template" erzeugt:
  - [`vitrine.json`](package-repo/vitrine.json) -- Beispiel-Manifest, gueltig
    gegen [`registry/schema/vitrine.schema.json`](../../registry/schema/vitrine.schema.json).
  - [`assets/minecraft/textures/item/example_placeholder.png`](package-repo/assets/minecraft/textures/item/example_placeholder.png)
    -- eine selbst erzeugte, einfarbige 16x16-Platzhaltertextur (siehe
    [`tools/generate-placeholder-texture.mjs`](tools/generate-placeholder-texture.mjs)),
    **kein** fremdes Asset (Charter-Regel 6). Zeigt nur die Ordnerstruktur.
  - [`README.md`](package-repo/README.md) -- Anleitung fuer den Paket-Autor:
    `vitrine.json` anpassen, Version taggen.
  - [`.github/workflows/release.yml`](package-repo/.github/workflows/release.yml)
    -- die Release-Automatik: Tag `v*` ⇒ Manifest validieren ⇒ `.vpkg` bauen
    ⇒ signieren (falls Schluessel-Secret vorhanden, sonst klar als
    unsigniert markiert) ⇒ GitHub-Release mit `.vpkg`-Asset ⇒ optional PR
    gegen `vitrine-registry`. Nutzt dafuer
    [`vendor/vitrine-cli/`](package-repo/vendor/vitrine-cli/) -- eine
    mitgelieferte Kopie von `cli/`, `designer/schema-validate.js` und
    `registry/schema/vitrine.schema.json` (siehe dort README): das
    oeffentliche Hauptrepo `umgekocht/vitrine-mod` existiert (Stand D8) noch
    nicht (ROADMAP A4 offen), ein zweiter `actions/checkout` dagegen wuerde
    also fehlschlagen -- genau das wurde beim echten Durchspielen dieser
    Zeile entdeckt und dadurch behoben. Keine neue npm-Abhaengigkeit, kein
    `npx` auf eine noch nicht veroeffentlichte CLI. Explizites, minimales
    `permissions: contents: write`, `actions/checkout` und
    `actions/setup-node` per festem Commit-SHA (nicht `@main`/`@v4`).
- [`tools/generate-placeholder-texture.mjs`](tools/generate-placeholder-texture.mjs)
  -- **kein** Teil der Vorlage selbst (liegt bewusst ausserhalb von
  `package-repo/`). Erzeugt die eine Platzhaltertextur deterministisch mit
  Node-Bordmitteln (`node:zlib` fuer die PNG-Kompression, eine
  handgeschriebene CRC-32-Tabelle) -- dokumentiert, wie und warum diese Datei
  entstanden ist, und erlaubt, sie bei Bedarf neu zu erzeugen.
- [`scripts/dry-run.mjs`](scripts/dry-run.mjs) -- lokaler Trockenlauf, der
  genau die Schritte aus `release.yml` nachstellt (Vendor-Sync-Check ⇒
  validate ⇒ pack ⇒ M1-Sicherheitspruefungen am Zip ⇒ sign mit
  Wegwerf-Schluessel), ohne `gh`, ohne Push, ohne echten Tag. Siehe
  Kommentar am Kopf der Datei fuer Details.
- [`scripts/sync-vendor.mjs`](scripts/sync-vendor.mjs) -- haelt
  `package-repo/vendor/vitrine-cli/` byte-identisch mit `cli/`,
  `designer/schema-validate.js` und `registry/schema/vitrine.schema.json`.
  Nach jeder Aenderung an einer dieser drei Quellen hier ausfuehren, bevor
  erneut veroeffentlicht wird; `dry-run.mjs` prueft das automatisch
  (`--check`) und bricht sonst ab.

## Trockenlauf ausfuehren

```
node templates/package-repo/scripts/dry-run.mjs
```

Laeuft nur innerhalb dieses Monorepos (braucht `cli/vitrine.js` unter
`../../cli/`, relativ vom Skript aus) -- das veroeffentlichte
`vitrine-package-template`-Repo braucht diesen Trockenlauf nicht, dort
uebernimmt `release.yml` dieselben Schritte in der Cloud.

## Was noch offen ist (Auslieferung, nicht Teil dieses Ordners)

- `vitrine-package-template` als oeffentliches, als Template markiertes Repo
  unter dem Konto aus `ALLOWED-REMOTES.md` anlegen und den Inhalt von
  [`package-repo/`](package-repo/) dorthin pushen.
- Einmal ueber „Use this template" ein Repo erzeugen, `vitrine.json`
  anpassen, taggen, pruefen, dass `release.yml` ohne Handarbeit ein
  gueltiges `.vpkg` als Release-Asset produziert -- Link ins Log/`PROGRESS.md`
  (D8s „Fertig"-Kriterium).
- Optional: `REGISTRY_PAT`- und `VITRINE_SIGNING_KEY_B64`-Secrets in einem
  echten Autoren-Repo hinterlegen, um den signierten Fall und den
  automatischen Registry-PR einmal echt zu sehen.
