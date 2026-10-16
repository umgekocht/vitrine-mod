# Beispiel-Soundpaket "Knackholz" (`spassglas.knackholz-sound`)

Auslieferbare Beispiel-Paketquelle fuer die Kategorie `sound` (ROADMAP T7).
Unentpackter Ordner -- das `.vpkg` (ZIP mit `vitrine.json` an der Wurzel) baut
die CLI (`node cli/vitrine.js pack .`), es liegt **nicht** im Repo.

## Was es demonstriert

Ein Soundpaket ersetzt ein bestehendes Sound-Event durch eigenes Audio. Nur
Daten, kein Code:

```
vitrine.json                                          Manifest (category "sound", capabilities ["sound"])
assets/minecraft/sounds.json                          weist "ui.button.click" ("replace": true) einer eigenen Datei zu
assets/minecraft/sounds/spassglas/ui/knackholz_click.ogg   <- FEHLT, siehe unten
```

`sounds.json` ueberschreibt das harmlose UI-Klick-Event. `"replace": true`
sorgt dafuer, dass das Vanilla-Geraeusch ersetzt und nicht ergaenzt wird.

## Platzhalter-Weg fuer die Audio-Datei

Statt einer OGG-Attrappe (Charter-Regel 6: kein fremdes Audio, kein
Platzhalter-Binaer) liegt unter `assets/minecraft/sounds/spassglas/ui/` eine
`README.md` mit dem exakten erwarteten Pfad und den Anforderungen an die
Datei. Der zugehoerige Eintrag steht in `ASSETS-NEEDED.md` (Repo-Wurzel),
Abschnitt "Offen". Ein Autor liefert die `.ogg` nach; danach ist die
Paketquelle vollstaendig.

Ohne die Datei validieren Manifest und `sounds.json` weiterhin -- das Beispiel
ist strukturell komplett, nur das Audio fehlt.

## Hot-Wechsel

Der "wechselt hot"-Nachweis aus T7 liegt auf der Mod-Seite (`coder-mod`,
In-Test-Fixtures). Dieses Beispiel ist der auslieferbare Teil.
