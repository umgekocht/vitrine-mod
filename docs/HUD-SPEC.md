# HUD-SPEC.md — das HUD-Schema und die HUD-Laufzeit

Diese Datei beschreibt das **HUD-Paket** (ROADMAP T5/T6, Kategorie `hud`):
wie ein Autor ein `hud.json` schreibt, was der Mod daraus zur Laufzeit zeigt,
und wie ein Spieler Widgets im Spiel verschieben kann, ohne das Paket selbst
zu ändern. Kanonische Schema-Quelle ist
[`registry/schema/hud.schema.json`](../registry/schema/hud.schema.json) —
Charter-Regel 8 „ein Schema, zwei Seiten": Designer und Mod lesen dieselbe
Datei, diese Spec dupliziert sie nicht, sondern erklärt sie in Prosa.

## Warum es dieses Format gibt

Charter-Regel 3: **Klasse A führt keinen Code aus.** Ein HUD-Paket ist damit
keine Skript- oder Formelsprache, sondern eine feste, abzählbare Liste von
Widgets mit festen Feldern. `additionalProperties` ist im Schema überall
`false` — ein Autor kann nichts außerhalb der hier beschriebenen Enums
ausdrücken, und der Mod muss nie fremden Code interpretieren, nur Daten.

## Aufbau einer `hud.json`

```json
{
  "schemaVersion": 1,
  "widgets": [ { "id": "fps", "type": "text", "anchor": "top-left",
                 "content": { "kind": "dynamic", "source": "fps", "label": "FPS: " } } ]
}
```

- `schemaVersion`: fest `1`.
- `widgets`: 1–64 Widgets, in Zeichenreihenfolge (später gelistete liegen
  über früheren). Jede `id` muss innerhalb des Pakets eindeutig sein — das
  prüft JSON Schema nicht selbst, sondern `HudLayoutParser` zur Laufzeit
  (eine doppelte Id wirft eine `HudParseException`, statt eine Id leise zu
  überschreiben).

Jedes Widget hat vier gemeinsame, optionale Felder plus `id`/`type`/`anchor`:

| Feld | Default, wenn weggelassen | Bedeutung |
|---|---|---|
| `offset` | `{x:0, y:0}` | Pixel-Versatz (unskaliert) vom Anker aus, −4096..4096 je Achse |
| `scale` | `1.0` | Skalierungsfaktor 0.5–3.0 |
| `visibility` | `{type: "always"}` | Sichtbarkeitsbedingung, siehe unten |
| — | — | `id`, `type`, `anchor` sind immer Pflicht |

## Anker (`$defs/anchor`)

Feste Enum, keine freien Prozent-/Pixelkoordinaten der Bildschirmposition
selbst: `top-left`, `top-right`, `bottom-left`, `bottom-right`, `top-center`,
`bottom-center`, `center`. `offset` verschiebt relativ dazu; negative Werte
schieben von rechten/unteren Ankern aus gesehen weiter nach außen.

## Datenquellen (`$defs/dataSource`)

Die einzige Brücke zu echten Spielwerten — eine geschlossene Whitelist, kein
Ausdruck, kein Platzhalter-Template:

| Quelle | Art | Default-Nachkommastellen |
|---|---|---|
| `fps` | Zahl | 0 |
| `coordinates.x` / `.y` / `.z` | Zahl | 1 |
| `biome` | Text | — |
| `time` | Text | — |

Der Mod liest jeden Wert selbst aus dem Spielzustand (`HudDataResolver`);
ein Paket kann nie eine eigene Berechnung einschleusen. Numerische Quellen
dürfen `decimals` (0–3) überschreiben; fehlt es, entscheidet der Renderer
über eine sinnvolle Default-Genauigkeit (siehe Tabelle).

## Sichtbarkeit (`$defs/visibility`)

Vier feste Bedingungstypen, je mit eigenen festen Parametern:

- `{"type": "always"}` — immer sichtbar (Default, wenn `visibility` fehlt).
- `{"type": "dimension", "dimension": "minecraft:overworld"}` — nur in der
  angegebenen Dimension (exakter String-Vergleich, Format `namespace:pfad`).
- `{"type": "debugScreen", "debugScreen": "on"|"off"}` — abhängig vom
  Vanilla-Debug-Bildschirm (F3).
- `{"type": "outsideMenu"}` — nur wenn kein Bildschirm/Menü (Inventar, Chat,
  Pause, …) offen ist.

## Widget-Typen

Vier feste Typen, jeder ein eigener, geschlossener Schema-Zweig:

1. **`text`** — eine Zeile `content` (siehe „Textinhalt" unten).
2. **`icon`** — `texture` (relativer PNG-Pfad im `.vpkg`, kein `..`, kein
   absoluter Pfad), optional `width`/`height` (1–512 px, unskaliert).
3. **`bar`** — ein Fortschrittsbalken für genau eine `source`, normiert über
   `min`/`max` (Default `0`/`1`), optional `width` (1–1024), `height`
   (1–64) und `color` (`#rrggbb`). Fehlt `color`, greift der Theme-Rückfall
   (siehe unten).
4. **`list`** — 1–16 `entries`, je ein „Textinhalt" untereinander.

### Textinhalt (`$defs/textContent`)

Entweder fest oder dynamisch:

- `{"kind": "static", "value": "…"}` — fester Text, nie interpoliert.
- `{"kind": "dynamic", "source": "…", "label": "…"?, "decimals": …?}` —
  Wert aus einer Datenquelle, optional mit festem Text-Präfix (`label`,
  **kein** Platzhalter/Template) und Nachkommastellen.

## Theme-Rückfalltokens

Ein HUD-Paket muss nicht jede Farbe/Größe selbst angeben. Fehlende Werte
fallen auf sieben Theme-Tokens zurück (`registry/schema/theme.schema.json`,
Abschnitt HUD), damit ein HUD zum aktiven Theme passt statt hartkodierte
Farben zu erzwingen:

| Token | Greift für |
|---|---|
| `hud.text.color` | Text-/Listen-Widgets ohne eigene Farbe |
| `hud.bar.track.color` | leerer Teil eines Balkens |
| `hud.bar.fill.color` | Balken ohne eigenes `color` |
| `hud.bar.defaultWidth` / `hud.bar.defaultHeight` | Balken ohne `width`/`height` |
| `hud.icon.defaultSize` | Icon ohne `width`/`height` |
| `hud.list.lineGap` | Zeilenabstand zwischen `list`-Einträgen |

## Laufzeit im Mod (`mod/src/main/java/de/spassglas/vitrine/hud/`)

Rein informativ — der Mod-Quellbaum ist `coder-mod`s Bereich, diese Spec
beschreibt nur, was dort bereits existiert, damit Designer-Seite und
Dokumentation nicht an der Realität vorbeireden:

- `HudPackageLoader` liest `hud/*.json` aus einem bereits durch die
  M1-Sicherheitskette (Zip-Prüfung, Fähigkeit `hud`) gelaufenen `.vpkg` und
  liefert ein `HudPackage` — es gibt keinen Pfad, auf dem roher Dateiinhalt
  direkt gerendert wird.
- `HudLayoutParser` validiert jede Datei gegen eine im Jar eingebettete,
  byte-identische Kopie von `hud.schema.json` (`HudSchemaValidator`, ein
  reiner Draft-2020-12-Teilmengen-Auswerter, keine neue Abhängigkeit) und
  erzwingt zusätzlich eindeutige Widget-Ids.
- `ActiveHudRegistry` hält die Layouts aller aktiven Pakete; später
  aktivierte Pakete zeichnen über früheren (wie Ressourcen-Pack-Reihenfolge).
- `HudDataResolver`/`HudDataSource` lesen die sechs Datenquellen aus dem
  echten Spielzustand.

### In-Game-Editor (ROADMAP T6) und Overrides

Ein Spieler kann HUD-Widgets im Spiel verschieben, umverankern oder
aus-/einblenden, **ohne das Paket zu verändern**: `HudLayoutOverrides`
speichert pro `(Paket-Id, Widget-Id)` einen Versatz-/Anker-/Sichtbarkeits-
Override getrennt vom schreibgeschützten Paket-Layout, persistiert atomar
unter `config/vitrine/hud/overrides.json` (`HudOverridesStore`, gleiches
Muster wie `SettingsManager`). Eine kaputte Datei führt zu leeren Overrides
plus Backup, nie zu einem Absturz. `ActiveHudRegistry.overrides` legt diese
Anpassungen beim Zeichnen über das Paket-Layout (`HudOverrideResolver`) —
ohne Anpassungen verhält sich das Spiel exakt wie ohne T6.

## Designer-Seite (`designer/hud-editor.js`)

Der HUD-Editor im Designer (`designer/hud-editor.js` + `app.js`) baut
`project.hud` genau in der Form, die `hud.schema.json` vorschreibt — kein
Zwischenformat. Anker-/Datenquellen-/Sichtbarkeits-/Widget-Typ-Enums werden
nicht im Editor-Code dupliziert, sondern bei jedem Aufruf aus dem
mitgelieferten Schema gelesen (`designer/hud-schema-data.js`, maschinell
erzeugt von `registry/schema/generate-designer-schema.mjs` — bei jeder
Schemaänderung neu laufen lassen). Live-Validierung je Widget-Entwurf läuft
vor dem Hinzufügen zur Liste gegen dasselbe Schema.

## Beispiele

[`registry/schema/examples/hud/valid/`](../registry/schema/examples/hud/valid/)
zeigt gültige Pakete (u. a. `fps-and-coordinates.json`: FPS oben links plus
eine Koordinatenliste unten links, nur über die Datenquellen-Enum, keine
Java-Referenz irgendwo in der Datei).
[`registry/schema/examples/hud/invalid/`](../registry/schema/examples/hud/invalid/)
zeigt abgelehnte Fälle — fehlendes Pflichtfeld, unbekannter Widget-Typ,
unbekannte Datenquelle, unbekannter Anker, unbekannte Sichtbarkeitsbedingung
und ausdrücklich eine freie Formel als `source`-Wert, um zu belegen, dass
die Whitelist geschlossen ist.

## Siehe auch

- [`registry/schema/hud.schema.json`](../registry/schema/hud.schema.json) —
  die kanonische Schema-Datei selbst.
- [`registry/schema/README.md`](../registry/schema/README.md) — Abschnitt
  „HUD-Schema" mit Entwicklungsgeschichte/Testverweisen.
- [`docs/UI-SPEC.md`](UI-SPEC.md) — Store-Oberfläche, Abschnitt Kategorien.
- [`docs/build-package.md`](build-package.md) — Abschnitt „HUD" im
  Paket-Bau-Ablauf.
