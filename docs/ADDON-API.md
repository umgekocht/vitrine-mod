# Vitrine-Addon-Plattform: API v1

Mit Vitrine-Addons erweiterst du Minecraft in **Lua** oder **JavaScript**. Ein Addon wirkt beim
Einschalten sofort und verschwindet beim Ausschalten restlos, ohne Neustart.

## Aufbau eines Addons

```
mein-addon/
  vitrine.json        Manifest, mit "capabilities": ["skript"]
  icon.png            Vorschaubild
  scripts/main.lua    oder scripts/main.js (genau eine Hauptdatei)
  scripts/hilfe.lua   weitere Dateien, laden mit vitrine.require("hilfe.lua")
  assets/...          optional: Texturen, Sounds, eigene Shader wie in einem Ressourcenpaket
```

Paket bauen und signieren:

```
node cli/vitrine.js validate mein-addon
node cli/vitrine.js pack mein-addon mein-addon.vpkg
node cli/vitrine.js sign mein-addon.vpkg
```

### Entwickler-Modus: speichern genügt

Schalte in den Einstellungen unter **Ordner und Werkzeuge** den **Entwickler-Modus** ein. Lege
dann je Addon einen Ordner an: `config/vitrine/dev-addons/<name>/main.lua` (oder `main.js`).

- Jede gespeicherte Änderung lädt das Addon innerhalb einer Sekunde neu.
- Fehler und `vitrine.log` landen in der **Addon-Konsole**. Du öffnest sie in den Einstellungen
  oder mit dem Befehl `/vitrine-konsole`.

## Sicherheit

- Addons laufen in einer Sandbox: kein Zugriff auf Dateien, das Betriebssystem, Java-Klassen oder
  fremden Code.
  - Lua: kein `io`, `os`, `require`, `debug`, `luajava`.
  - JavaScript: kein `java`, kein `Packages`.
- Jeder Aufruf hat ein Zeitlimit von 50 ms, der Start eines Addons 1 s. Endlosschleifen werden
  abgebrochen.
- Nach 10 Fehlern wird ein Addon angehalten, bis sich sein Quelltext ändert.
- Installierte Pakete brauchen eine gültige Signatur und die Zustimmung des Spielers zur
  Fähigkeit `skript`.

## Funktionen

Lua und JavaScript sehen dasselbe globale Objekt `vitrine`. Die Beispiele sind in Lua geschrieben.
In JavaScript ist es dieselbe Schreibweise: `vitrine.on("tick", function () { ... })`.

### Ausgabe

| Funktion | Wirkung |
|---|---|
| `vitrine.log(...)` | Schreibt in die Addon-Konsole. |
| `vitrine.chat(text)` | Zeigt eine Nachricht nur für dich im Chat. |
| `vitrine.actionbar(text)` | Zeigt Text über der Hotbar. |
| `vitrine.title(titel, untertitel, ticks)` | Zeigt einen großen Titel in Bildschirmmitte. |

### Ereignisse, Tasten, Zeitgeber

| Funktion | Wirkung |
|---|---|
| `vitrine.on(ereignis, fn)` | Ereignisse: `tick` (20 pro Sekunde), `hud` (fn bekommt `g`), `join`, `leave`, `chat` (text), `hurt` (schaden), `unload` (vor dem Entladen). |
| `vitrine.key("G", fn)` | Eigene Taste, nur im Spiel ohne offenes Menü. Namen wie `"g"`, `"f6"`, `"space"`, `"left.shift"`. |
| `vitrine.every(sek, fn)` | Wiederholt fn; gibt eine ID zurück. |
| `vitrine.after(sek, fn)` | Führt fn einmal aus; gibt eine ID zurück. |
| `vitrine.cancel(id)` | Stoppt einen Zeitgeber. |

### Spiel lesen

| Funktion | Rückgabe |
|---|---|
| `vitrine.player()` | `name, x, y, z, yaw, pitch, health, maxHealth, food, onGround, sneaking, sprinting, dimension, item`, oder `nil`, wenn keine Welt offen ist. |
| `vitrine.world()` | `time, day, raining, thundering, biome, singleplayer` |
| `vitrine.block(x, y, z)` | Block-ID, z. B. `"minecraft:stone"` |

### Spiel verändern

| Funktion | Wirkung |
|---|---|
| `vitrine.command(cmd)` | Führt einen Befehl aus. In Einzelspieler-Welten mit Operator-Rechten, auf Servern mit deinen Rechten. |
| `vitrine.sound(id, lautstärke, tonhöhe)` | Spielt einen Sound, z. B. `"minecraft:entity.player.levelup"`. |
| `vitrine.particle(id, x, y, z, anzahl)` | Zeigt einfache Partikel, z. B. `"minecraft:cloud"`. |
| `vitrine.shader(name)` | Bildfilter: `"invert"`, `"creeper"`, `"spider"`, `"blur"`, eigener Shader als `"meinaddon:mein_filter"` (aus `assets/meinaddon/shaders/post/mein_filter.json`). `nil` schaltet ihn ab. |

### Oberfläche

**HUD** über `vitrine.on("hud", function(g) ... end)`:

| Funktion | Wirkung |
|---|---|
| `g.text(text, x, y, farbe)` | Text zeichnen |
| `g.rect(x, y, b, h, farbe)` | Rechteck zeichnen |
| `g.round(x, y, b, h, radius, farbe)` | Abgerundetes Rechteck zeichnen |
| `g.textWidth(text)` | Breite eines Texts |
| `g.width`, `g.height` | Größe des Bildschirms |

Farben schreibst du als `"#RRGGBB"`, `"#AARRGGBB"` oder als Zahl `0xAARRGGBB`.

**Menüs:**

```lua
vitrine.menu("Titel", function(ui)
  ui.heading("Abschnitt")
  ui.text("Erklärung")
  ui.button("Knopf", function() ... end, true)   -- true = blauer Hauptknopf
  ui.toggle("Schalter", aktuellerWert, function(an) ... end)
  ui.space(8)
  -- ui.refresh() baut das Menü neu, ui.close() schließt es
end)
```

`vitrine.closeMenu()` schließt das Menü von außen.

### Speicher, weitere Dateien

| Funktion | Wirkung |
|---|---|
| `vitrine.storage.get(k)` | Liest einen gespeicherten Wert |
| `vitrine.storage.set(k, v)` | Speichert einen Wert. Er bleibt über Neustarts erhalten, höchstens 1 MB je Addon. |
| `vitrine.storage.all()` | Gibt alle gespeicherten Werte zurück |
| `vitrine.require("datei.lua")` | Führt eine weitere Datei aus dem Ordner `scripts/` aus |
| `vitrine.addon.id`, `vitrine.addon.name` | Kennung und Name des Addons |

### Kennzeichnung „Neustart nötig“

Manches lässt sich nicht ohne Neustart umsetzen. Dazu gehören echte neue Block-, Item- und
Entity-Typen und Eingriffe tief in Minecrafts Code. Braucht ein Addon so etwas, meldet es das an:

```lua
vitrine.restartNeeded("eigener Block-Typ")
```

Die Bibliothek zeigt das Addon dann mit **⚠ Neustart nötig**.

## Beispiele

Bauen mit `python scripts/make_platform_examples.py <zielordner>`:

| Addon | Sprache | Was es zeigt |
|---|---|---|
| **Schnellmenü** | Lua | Taste G öffnet ein eigenes Menü: Tag/Nacht, Wetter, Nachtsicht, Koordinaten im HUD |
| **Kino-Filter** | JavaScript | Taste K wechselt Bildfilter (Shader) |
| **Abenteuer-Helfer** | Lua | Fallschutz, Titel zu jedem neuen Tag, Tempo-Anzeige |

## Was als Nächstes kommt

- WebAssembly für Rust, C, C++, Go und Zig.
- Python-Bridge für echtes Python mit pip.
- „Neue“ Blöcke und Items auf Basis vorhandener Typen.
- Steuerung von Iris-Shaderpacks und Netzwerk zwischen Addons.
