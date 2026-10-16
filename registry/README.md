# `registry/` — Vitrine-Katalog: Format und Index-Builder (M4/K1)

Charter-Regel 8: „Ein Schema, zwei Seiten." Die Dateien unter
[`schema/`](schema/) sind die einzige Quelle der Wahrheit fuer das
Katalog-Format. Der Index-Builder hier (`build-index.js`) schreibt genau
dieses Format, der Katalog-Client im Mod (`mod/`, K2) liest spaeter
dieselben Dateien — es gibt kein zweites, abweichendes Format.

Dieser Ordner enthaelt zwei getrennte Dinge:

- Das **Paketmanifest-Schema** (`vitrine.json`, liegt in jedem `.vpkg`) unter
  [`schema/vitrine.schema.json`](schema/vitrine.schema.json) — siehe
  [`schema/README.md`](schema/README.md).
- Das **Registry-Format** (dieser Katalog, `packages/`, `index/`,
  `search.json`, `featured.json`) — beschrieben unten. Beide Formate teilen
  sich `id`, `category`, `version`/`versions[].version`, sind aber
  eigenstaendige Schemas: das Registry-Format transportiert zusaetzlich
  Download-URL, Pruefsumme und Signatur, die im `.vpkg` selbst nicht stehen.

## Registry-Format

Vier Dateiarten, live spaeter im separaten Repo `vitrine-registry`
(GitHub Pages/raw, kein Backend — siehe `ROADMAP.md`, Technische
Festlegungen):

| Datei | Schema | Inhalt |
|---|---|---|
| `packages/<id>.json` | [`schema/registry-package.schema.json`](schema/registry-package.schema.json) | Ein Eintrag pro veroeffentlichtem Paket: Metadaten, Autor, Kategorie, alle Versionen mit Download-URL, SHA-256, ed25519-Signatur. Von Autoren per PR gepflegt — siehe [`CONTRIBUTING.md`](CONTRIBUTING.md). |
| `index/v1/index.json`, `index/v1/index-2.json`, ... | [`schema/registry-index-page.schema.json`](schema/registry-index-page.schema.json) | Paginierte Liste aller Pakete (Kurzeintraege), erzeugt vom Builder. Seite 1 heisst `index.json`, jede weitere `index-<n>.json`. |
| `index/v1/search.json` | [`schema/registry-search.schema.json`](schema/registry-search.schema.json) | Flache, nicht paginierte Liste mit Suchfeldern (Stichworte), erzeugt vom Builder. |
| `index/v1/featured.json` | [`schema/registry-featured.schema.json`](schema/registry-featured.schema.json) | Zwei getrennt kuratierte Listen: `items` (Startseiten-Abschnitt "Featured", aus dem optionalen `featured`-Feld je Paketeintrag) und `popular` (Startseiten-Abschnitt "Beliebt", aus dem optionalen `popular`-Feld — ebenfalls redaktionell, keine gemessene Downloadzahl, siehe "Technische Festlegung: Katalog ohne Backend"). |

`packages/<id>.json` ist die **Quelle**, alles unter `index/v1/` ist
**generiert** — nie von Hand pflegen, immer per `build-index.js` neu bauen.

`index/v1/` ist ein **fester, versionierter Pfad** (ROADMAP, Technische
Festlegungen: „Fester Katalog-Pfad im Jar... `…/index/v1/index.json`") — die
im Jar einkompilierte Standard-URL zeigt genau dahin. `v1` ist der API-Pfad
des Katalogformats (`CATALOG_VERSION` in `build-index.js`), nicht dasselbe
wie `schemaVersion` in den einzelnen Dateien, faellt aktuell aber zusammen.
Eine spaetere, nicht abwaertskompatible Formataenderung bekommt `index/v2/`
als zusaetzlichen, neuen Pfad — `index/v1/` bleibt fuer alte Clients stehen.

## Index-Builder

```
cd registry
npm install
node build-index.js --packages packages --out .
```

Ohne Argumente liest der Builder `registry/packages/` und schreibt nach
`registry/index/v1/` (relativ zum Skript). Optionen: `--page-size N`
(Standard 24), `--now ISO` (fuer reproduzierbare Laeufe, z. B. in CI — sonst
`new Date().toISOString()`).

Der Builder prueft jede `packages/<id>.json` gegen
`registry-package.schema.json`, gegen den Dateinamen (muss `<id>.json`
heissen), auf doppelte `id`s und darauf, dass `latestVersion` tatsaechlich
die hoechste in `versions` gelistete SemVer-Version ist. Ein einziger
kaputter Eintrag bricht den ganzen Lauf ab (Exit-Code 1) — kein halber Index.

## Beispielpakete

[`packages/`](packages/) enthaelt drei Beispielpakete (Klasse-A-Inhalte:
Textur, Theme, HUD) als Fixtures fuer Builder-Test und Dokumentation:

- `spassglas.steinbrocken-textur` (Kategorie `texturen`, zwei Versionen, `featured`)
- `spassglas.mitternacht-theme` (Kategorie `theme`, `featured` und `popular`)
- `spassglas.kompakt-hud` (Kategorie `hud`, mit `minVitrineVersion`, `popular`)

## Test

```
cd registry
npm test
```

[`test/build-index.test.mjs`](test/build-index.test.mjs) laesst den Builder
gegen die drei Beispielpakete laufen (in ein temporaeres Verzeichnis, nicht
nach `registry/index/`) und prueft jede erzeugte Datei mit
`@cfworker/json-schema` gegen ihr Schema — dieselbe Bibliothek wie
`schema/validate-examples.mjs` (MIT, siehe `THIRD-PARTY.md`). Zusaetzlich:
Paginierung bei kleiner `--page-size` ueber mehrere Seiten, und Ablehnung
eines Registry-Pakets, das sein Schema verletzt.

## Fuer die Mod-Seite (K2, Katalog-Client)

Der Katalog-Client laedt `index/v1/index.json` (und folgt bei Bedarf
`nextPage`), optional `index/v1/search.json` fuer die Store-Suche und
`index/v1/featured.json` (Felder `items` und `popular`) fuer die kuratierte
Startseite (X2) — per HTTPS-GET gegen eine Host-Whitelist, mit Timeout und
Groessenlimit (K2s eigenes „Fertig"-Kriterium). Alle drei Dateien sind in sich
geschlossen (keine `$ref` auf externe URLs, keine weiteren Nachlade-Schritte
ausser der Pagination selbst).

## Fuer Paket-Autoren (M4/K6)

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Schritt fuer Schritt: eigenes
  `.vpkg` als Release, Eintrag unter `packages/<id>.json`, Pull Request.
- [`LICENSE-WHITELIST.md`](LICENSE-WHITELIST.md) — welche Content-Lizenzen
  fuer Klasse-A-Inhalte erlaubt sind (CC0/CC-BY & Co.), was Charter-Regel 1
  ausschliesst (Preisschranken, „nur fuer Vitrine"-Sonderlizenzen).

## GitHub Actions (`.github/workflows/`)

Dieser Ordner ist der zukuenftige Inhalt des eigenstaendigen Repos
`vitrine-registry` (siehe `ALLOWED-REMOTES.md`) — die Actions liegen deshalb
bereits hier unter [`registry/.github/workflows/`](.github/workflows/), auch
wenn das Repo selbst erst in K7 angelegt und gepusht wird. Beide Workflows
haben ein explizites, minimales `permissions:`-Feld und binden
`actions/checkout` und `actions/setup-node` per festem Commit-SHA ein (nicht
`@main`/`@v4`):

- [`pr-check.yml`](.github/workflows/pr-check.yml) — bei jedem Pull Request:
  Manifest-Beispiele gegen `vitrine.schema.json`, `npm test`, geaenderte
  `packages/<id>.json` gegen `registry-package.schema.json`
  (`scripts/validate-changed-packages.mjs`). Nur `contents: read`, schreibt
  nichts.
- [`build-index.yml`](.github/workflows/build-index.yml) — nach jedem Merge
  auf `main`, der `packages/` oder den Builder aendert: baut `index/v1/*`
  neu und committet es zurueck, wenn sich etwas geaendert hat. Braucht
  `contents: write`, sonst nichts.

## Lokaler Trockenlauf

```
cd registry
npm install
npm run dry-run
```

[`scripts/dry-run.mjs`](scripts/dry-run.mjs) simuliert lokal genau die
Schritte beider Actions, ohne `gh`, ohne Push, ohne Netzzugriff: Beispiele
pruefen, Tests laufen lassen, alle Pakete unter `packages/` gegen
`registry-package.schema.json` pruefen (`scripts/validate-changed-packages.mjs`
im Trockenlauf-Modus, ohne PR-Kontext), und den Index **zweimal** in getrennte
temporaere Verzeichnisse bauen, um Byte-fuer-Byte-Reproduzierbarkeit von
`index/v1/*` zu beweisen (K6s eigenes „Fertig"-Kriterium).
