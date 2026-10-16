# `registry/schema/` — `vitrine.json`-Schema v1

Diese Datei ist die **einzige** Quelle der Wahrheit fuer den Aufbau des
Vitrine-Paketmanifests (`vitrine.json`, liegt in jedem `.vpkg`-ZIP). Charter-
Regel 8: "Ein Schema, zwei Seiten." Sowohl der Designer (`designer/`, D2) als
auch der Java-Parser im Mod (`mod/`, P1) validieren dieselbe Datei
[`vitrine.schema.json`](vitrine.schema.json) — es gibt kein zweites,
abweichendes Format.

## Draft-Version

**JSON Schema Draft 2020-12** (`$schema:
"https://json-schema.org/draft/2020-12/schema"`). Grund: aktuellste breit
unterstuetzte Draft-Version, unterstuetzt von Ajv (JS/Designer-Seite) und von
gaengigen Java-Implementierungen (z. B. `networknt/json-schema-validator`,
das explizit `Version.V202012` kennt — Auswahl der konkreten Bibliothek ist
Sache von P1s Java-Parser).

**Kein Netzzugriff zur Validierung.** Die `$id` ist eine `urn:`-Kennung
(`urn:vitrine:schema:vitrine-manifest:v1`), keine `http(s)://`-Adresse — es
gibt keine `$ref` auf eine externe URL. Das Schema ist eine einzelne
in-sich-geschlossene Datei. Charter-Regel 10: der Client braucht zum Lesen
keinen Token, kein Netz.

## Pflichtfelder

| Feld | Typ | Bedeutung |
|---|---|---|
| `schemaVersion` | integer, `const 1` | Version dieses Schemas selbst. |
| `id` | string | Eindeutiger Namespace-Bezeichner, z. B. `"autor.paketname"` (mind. zwei Punkt-Segmente, Kleinbuchstaben/Ziffern/Bindestrich). |
| `name` | string | Anzeigename im Store. |
| `version` | string | SemVer 2.0.0, z. B. `"1.0.0"`. |
| `category` | enum | Eine von: `texturen`, `theme`, `hud`, `shader`, `sound`, `sprache`, `cosmetics`, `bundle` (siehe `ROADMAP.md`/`ASSETS-NEEDED.md`). |
| `author` | string | Name/Handle des Autors. |
| `license` | string | Lizenz-Kurzbezeichner der Paketinhalte, muss auf `registry/LICENSE-WHITELIST.md` stehen (GATE23) — Index-Bau (`registry/build-index.js`) und PR-Pruefung (`registry/scripts/validate-changed-packages.mjs`) lehnen nicht-whitelistete Werte ueber `registry/license-policy.mjs` automatisiert ab. |

## Optionale Felder

`description`, `capabilities` (Faehigkeiten-Whitelist nach Charter-Regel 4 —
`items` ist ein `enum` mit genau sieben Werten: `texturen`, `theme`, `hud`,
`shader`, `sound`, `sprache`, `cosmetics` (die `category`-Werte ohne
`bundle`); jeder andere Eintrag wird von Schema-Validator und Mod-Laufzeit
(`VpkgCapabilityGrant`) gleich abgelehnt — siehe G2),
`icon`, `screenshots` (relative Pfade innerhalb des `.vpkg`-ZIPs, kein `..`,
kein absoluter Pfad), `homepage` (nur `https://`).

Die Wurzelebene hat `additionalProperties: false` — ein unbekanntes Feld
(Tippfehler, veraltetes Feld) faellt beim Validieren sofort auf.

## Klasse A gegen Klasse B (GATE6)

`contentClass` unterscheidet die beiden Inhaltsklassen aus `PLAN.md`
Abschnitt 1 — **dieselbe Vokabel und dieselben zwei Werte** wie
`contentClass` in `registry-package.schema.json` (kein zweites Wort fuer
dieselbe Sache):

| `contentClass` | Klasse | Bedeutung |
|---|---|---|
| fehlt oder `resource` | A | Live-Inhalt, wirkt ohne Neustart. Jedes bestehende Manifest ist damit unveraendert gueltig. |
| `javamod` | B | Java-Mod als `.jar`, wird nach `mods/` gestaget und laeuft erst nach einem Neustart. |

Zusaetzliche optionale Felder: `hotApply` (boolean) und `javaMod` (Objekt mit
`loader`, `minecraftVersions`, `modId?`, `dependencies?`, `jar` mit
`fileName`/`sha256`/`size`).

Das `allOf` am Ende des Schemas ist die **einzige** Stelle, die mehrere
Felder verknuepft, und setzt zwei Regeln durch:

1. **Klasse B muss vollstaendig sein:** bei `contentClass: "javamod"` sind
   `hotApply: false` (exakt `false`, nicht bloss irgendein Boolean) und der
   `javaMod`-Block Pflicht, und darin wiederum `jar.sha256` — ein Angebot ohne
   bekannte Pruefsumme wird von `JavaModStager` grundsaetzlich abgelehnt, ein
   Manifest ohne `sha256` waere also garantiert nicht installierbar.
2. **Klasse A darf keine `.jar` mitbringen:** ohne `contentClass: "javamod"`
   ist ein `javaMod`-Objekt verboten und `hotApply` darf nur `true` sein. So
   kann sich nie eine `.jar` in ein Paket schmuggeln, das als "wirkt sofort"
   beworben wird.

Eine **Signatur steht bewusst nicht** im Manifest: signiert wird vom
Registry-Schluessel (`registry-package.schema.json`, `versions[].signature`).
Ein Paketautor kann damit gar nicht signieren — ein Signaturfeld hier waere
ein Vertrauensanker, den niemand haelt. Die Kette laeuft ueber die `sha256`.

Autorenanleitung mit vollstaendigem Beispiel: [`docs/klasse-b.md`](../../docs/klasse-b.md).

## Beispiele

- [`examples/valid/`](examples/valid/) — drei gueltige Manifeste (minimal, mit
  allen optionalen Feldern, und ein Klasse-B-Manifest, siehe unten).
- [`examples/lang/valid/`](examples/lang/valid/) und
  [`examples/sound/valid/`](examples/sound/valid/) — je eine **auslieferbare
  Beispiel-Paketquelle** (unentpackter Ordner mit `vitrine.json` + `assets/`,
  Ordnername = Paket-`id`) fuer die Kategorien `sprache` und `sound` (ROADMAP
  T7). Das `.vpkg` baut die CLI, es liegt nicht im Repo. Das Soundpaket fuehrt
  die fehlende `.ogg` als `ASSETS-NEEDED.md`-Eintrag statt als Platzhalter-
  Binaer (Charter-Regel 6). `../test/vitrine-schema.test.mjs` validiert jede
  `vitrine.json` dieser Ordner gegen dieses Schema.
- [`examples/valid/klasse-b-javamod.json`](examples/valid/klasse-b-javamod.json)
  — ein vollstaendiges **Klasse-B**-Manifest (`contentClass: "javamod"`).
- [`examples/invalid/`](examples/invalid/) — siebzehn ungueltige Manifeste,
  die letzten vier zu Klasse B: `14-klasse-a-mit-jar.json` (Klasse-A-Paket mit
  geschmuggeltem `javaMod`-Block), `15-klasse-b-ohne-sha256.json`,
  `16-klasse-b-hotapply-true.json` (luegt ueber "wirkt sofort") und
  `17-klasse-b-ohne-javamod-block.json`. Die ersten dreizehn Dateien:
  jedes mit genau einer anderen Fehlerart (fehlendes Pflichtfeld, falscher
  Typ, unbekanntes Zusatzfeld, ungueltiger Enum-Wert, ungueltiges SemVer,
  ungueltiges ID-Muster, leerer Pflichttext, `capabilities` falscher Typ,
  `capabilities`-Eintrag mit unerlaubtem Wert, falscher `schemaVersion`-
  Wert, Pfad-Ausbruchsversuch in `icon`, mehrere fehlende Pflichtfelder
  gleichzeitig, `capabilities`-Eintrag als Namespace-Punkt-Bezeichner
  (`hud.read` — nach altem Schema gueltig, seit G2 nicht mehr)). P1s
  "Fertig"-Kriterium auf der Java-Seite verlangt zehn fehlerhafte Manifeste
  mit je eigener Fehlermeldung — diese dreizehn Dateien liefern die
  Testgrundlage dafuer (drei mehr als das Minimum).

## Lokale Validierung (JS-Referenzimplementierung)

```
cd registry
npm install
npm run validate-examples
```

[`validate-examples.mjs`](validate-examples.mjs) prueft alle Dateien unter
`examples/valid/` als gueltig und alle unter `examples/invalid/` als
ungueltig (`@cfworker/json-schema`, MIT-lizenziert, ohne eigene
Laufzeit-Abhaengigkeiten, siehe `THIRD-PARTY.md` -- bewusst nicht Ajv, weil
dessen `fast-uri`-Abhaengigkeit BSD-3-Clause ist und damit gegen
Charter-Regel 5 verstoesst). Das ist die Referenzimplementierung fuer den
Designer (D2) — kein eigenstaendiges zweites Schema, nur eine andere
Bibliothek, die dieselbe Datei liest.

## Theme-Token-Schema (`theme.schema.json`, T1)

[`theme.schema.json`](theme.schema.json) ist die kanonische Quelle fuer alle
Theme-Tokens (Farben, Radien, Abstaende, Masse, Blur, Schriftgroessen,
Bewegung/Zustaende) — analog zu `vitrine.schema.json`, aber fuer ein
komplettes Theme statt fuer ein Paketmanifest. Ein Theme hat die Form
`{ schemaVersion: 1, light: {...}, dark: {...} }`; jede Variante ist ein
flaches Objekt mit punktgetrennten Token-Namen als Schluessel (z. B.
`"nav.width.expanded"`), keine verschachtelten Objekte — dieselbe Konvention,
die `designer/theme-tokens.js` (D3) bereits fuer sein internes Token-Set
benutzt.

**Wichtig — T1 ist reine Schema-Arbeit, keine Migration:** Weder
`mod/src/main/java/de/spassglas/vitrine/ui/theme/VitrineTheme.java` (S0)
noch `designer/theme-tokens.js` (D3) lesen/schreiben bereits dieses Schema.
Beide sind eigene, bewusst nicht-kanonische Platzhalter-Token-Sets (siehe
deren Kopfkommentare). Die Migration auf `theme.schema.json` ist T2
(ThemeManager, Mod-Seite) und die entsprechende Designer-Zeile vorbehalten.

Beispiele unter [`examples/theme/valid/`](examples/theme/valid/) (ein
vollstaendiges Theme "Klarglas", hell + dunkel) und
[`examples/theme/invalid/`](examples/theme/invalid/) (fehlendes Pflichtfeld,
falscher Typ, unbekanntes Zusatzfeld).

[`../test/theme-schema.test.mjs`](../test/theme-schema.test.mjs) ist der
Vollstaendigkeitsbeweis fuer T1s "Fertig"-Kriterium: das Skript liest alle
Java-Konstanten aus `VitrineTheme.java` und alle Token-Ids aus
`theme-tokens.js` **mechanisch** (Regex, keine von Hand gepflegte Liste) aus
und gleicht sie ueber eine explizite Zuordnungstabelle gegen die Eigenschaften
von `theme.schema.json` ab — kein Token aus einer der beiden Quellen darf
ohne Schema-Entsprechung bleiben.

Die S1-Kachelarbeit (`coder-mod`) hat themebare Kachel-/Reihenmasse ergaenzt
(`card.textBlock.height`, `card.textLineGap`, `card.badge.inset`,
`row.header.height`, `row.header.gap`, `chevron.width`, `statusIcon.drawSize`,
`typeIcon.drawSize`, `typeIcon.placeholderDrawSize`). Die `*_SOURCE_*`-Konstanten
in `VitrineTheme.java` sind dagegen die native Pixelaufloesung gelieferter
PNG-Grafiken (kein gestalterischer Spielraum) und stehen als ausdrueckliche,
einzeln begruendete Ausnahmemenge `ASSET_SOURCE_DIMENSIONS` im Test — bewusst
**nicht** im Schema, keine Wildcard-Ausnahme.

Die T5-Mod-Laufzeit (`coder-mod`) hat ausserdem sieben themebare
HUD-Rueckfalltokens ergaenzt (Render-Defaults, wenn ein HUD-Paket nichts
vorgibt): `hud.text.color`, `hud.bar.track.color`, `hud.bar.fill.color`,
`hud.bar.defaultWidth`, `hud.bar.defaultHeight`, `hud.icon.defaultSize`,
`hud.list.lineGap`. `hud.icon.defaultSize` ist eine gestalterische
Default-Zeichengroesse (kein rohes Asset-Pixelmass) und gehoert deshalb ins
Schema, nicht in `ASSET_SOURCE_DIMENSIONS`.

Die S3-Detailseite (`coder-mod`) hat elf themebare Detailseiten-Masse ergaenzt:
`detail.headerImage.width`, `detail.headerImage.height`, `detail.section.gap`,
`detail.heading.gap`, `detail.lineGap`, `detail.action.button.width`,
`detail.action.button.height`, `detail.screenshot.width`,
`detail.screenshot.height`, `detail.capability.iconSize`, `detail.scrollStep`.
`detail.headerImage.*` und `detail.screenshot.*` sind Anzeige-Zielmasse
(gestalterisch), nicht die native Bildaufloesung — deshalb ins Schema, nicht in
`ASSET_SOURCE_DIMENSIONS`.

Der S4-Faehigkeiten-Dialog (`coder-mod`) hat drei themebare Dialog-Masse
ergaenzt: `dialog.width`, `dialog.padding`, `dialog.buttonGap`. Echte themebare
Masse (aus `card.width`/`space.unit` abgeleitet), keine Asset-Pixelmasse —
deshalb ins Schema, nicht in `ASSET_SOURCE_DIMENSIONS`.

## HUD-Schema (`hud.schema.json`, T5 — nur Schema-Haelfte)

[`hud.schema.json`](hud.schema.json) ist die kanonische Quelle fuer ein
HUD-Paket (Kategorie `hud`): eine Liste rein deklarativer Widgets (`text`,
`icon`, `bar`, `list`) mit Anker (`top-left`, `bottom-center`, `center`, ...),
Pixel-Versatz, Skalierung (0.5–3.0) und einer Sichtbarkeitsbedingung
(`always`, `dimension`, `debugScreen`, `outsideMenu`). Dynamische Werte
kommen ausschliesslich aus der festen Enum `$defs.dataSource` (`fps`,
`coordinates.x`/`y`/`z`, `biome`, `time`) — es gibt **keine** Skript- oder
Ausdruckssprache, kein Platzhalter-/Template-System, keinen Formel-Support
(Charter-Regel 3: Klasse A fuehrt keinen Code aus). `additionalProperties`
ist ueberall `false`; jeder Widget-Typ ist ein eigener, geschlossener
Schema-Zweig.

**Wichtig — T5 ist hier nur zur Haelfte umgesetzt:** Diese Datei ist reine
Schema-Arbeit. Die Laufzeit im Mod (Java liest `hud.json` aus dem `.vpkg`
und zeichnet die Widgets tatsaechlich auf den Bildschirm) ist **nicht**
Teil dieser Aenderung und folgt als eigene, spaetere Teil-Aufgabe. Ebenso
fehlt noch der HUD-Editor im Designer (D5), der `hud.json` erzeugt.

Beispiele unter [`examples/hud/valid/`](examples/hud/valid/) — insbesondere
`fps-and-coordinates.json`, das genau T5s "Fertig"-Kriterium abbildet: eine
FPS-Anzeige oben links plus eine Koordinaten-Liste unten links, beide ueber
die Datenquellen-Enum, keine Java-Referenz irgendwo in der Datei — und unter
[`examples/hud/invalid/`](examples/hud/invalid/) (fehlendes Pflichtfeld,
unbekannter Widget-Typ, unbekannte Datenquelle, unbekannter Anker,
unbekannte Sichtbarkeitsbedingung, sowie explizit eine freie Formel als
`source`-Wert, um zu zeigen, dass die Whitelist geschlossen ist).

[`../test/hud-schema.test.mjs`](../test/hud-schema.test.mjs) prueft alle
Beispiele und beweist zusaetzlich mechanisch, dass das FPS+Koordinaten-
Szenario ausschliesslich ueber enumerierte Datenquellen funktioniert und
keine Java-Referenz enthaelt.

## Fuer die Mod-Seite (P1, Java-Parser)

Der Java-Parser validiert gegen exakt dieselbe Datei
`registry/schema/vitrine.schema.json` (z. B. per
`networknt/json-schema-validator` o. ae., MIT/Apache-lizenziert, Lizenz vor
Aufnahme an der Quelle pruefen und in `mod/`s eigene Abhaengigkeitsliste
eintragen). Die dreizehn Dateien unter `examples/invalid/` sind die Grundlage
fuer die zehn geforderten Fehlerfaelle aus P1s "Fertig"-Kriterium — jede
Datei verletzt genau eine Regel, damit der Parser eine eigene, praezise
Fehlermeldung je Fall ausgeben kann statt einer generischen "ungueltig"-
Meldung. `capabilities` ist ein geschlossenes `enum` (sieben Werte, wie
`category` ohne `bundle`); Schema-Validator und `VpkgCapabilityGrant`
beurteilen dasselbe `capabilities`-Manifest gleich (G2).

### Offen seit GATE6 (Klasse B)

Die Klasse-B-Regeln (siehe oben) brauchen fuenf Schluesselwoerter, die
`ManifestSchemaValidator.java` bisher **nicht** auswertet: `allOf`, `if`,
`then`/`else`, `not` sowie `minimum`/`maximum`. Der Browser-Auswerter
`designer/schema-validate.js` kann sie seit GATE6 und ist als Vorlage
gedacht — gleiche Reihenfolge der Pruefungen, gleiche Formulierungen. Solange
die Java-Seite sie ignoriert, wuerde sie ein Klasse-A-Manifest mit heimlichem
`javaMod`-Block stillschweigend durchwinken; die Feldregeln innerhalb von
`javaMod` (Pflichtfelder, Muster, `sha256`) greifen dagegen schon heute, weil
sie nur die bereits unterstuetzte Teilmenge benutzen.

Ausserdem muss die eingebettete Kopie
`mod/src/main/resources/de/spassglas/vitrine/manifest/vitrine.schema.json`
neu aus dieser Datei gezogen werden — `ManifestSchemaResourceSyncTest` haelt
beide byte-identisch und schlaegt sonst fehl.
