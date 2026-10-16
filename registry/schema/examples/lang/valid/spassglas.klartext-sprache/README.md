# Beispiel-Sprachpaket "Klartext" (`spassglas.klartext-sprache`)

Auslieferbare Beispiel-Paketquelle fuer die Kategorie `sprache` (ROADMAP T7).
Unentpackter Ordner -- das `.vpkg` (ZIP mit `vitrine.json` an der Wurzel) baut
die CLI (`node cli/vitrine.js pack .`), es liegt **nicht** im Repo.

## Was es demonstriert

Ein Sprachpaket ueberschreibt einzelne Uebersetzungs-Keys einer bereits
vorhandenen Locale. Kein neuer Text-Renderer, kein Code -- nur Daten:

```
vitrine.json                       Manifest (category "sprache", capabilities ["sprache"])
assets/minecraft/lang/en_us.json   ueberschreibt sieben bekannte Vanilla-Menue-Keys
```

`en_us.json` benennt u. a. `menu.singleplayer`, `menu.multiplayer` und
`menu.options` sichtbar um ("Solo World", "Join a Server", "Settings"). Keys,
die die Datei nicht nennt, bleiben unveraendert -- das Paket ist ein
Overlay auf die Vanilla-Locale, kein vollstaendiger Ersatz.

Die englischen Zeichenketten sind selbst formuliert (Charter-Regel 6: keine
fremden Assets), kein Auszug aus einer Mojang-Sprachdatei.

## Hot-Wechsel

Der "wechselt hot"-Nachweis aus T7 liegt auf der Mod-Seite
(`coder-mod`, In-Test-Fixtures). Dieses Beispiel ist der auslieferbare Teil:
strukturell vollstaendig, ohne Platzhalter.
