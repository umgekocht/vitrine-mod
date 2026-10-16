# Wie baue ich ein Paket

Ein Vitrine-Paket ist eine Datei mit der Endung **`.vpkg`**. Technisch ist das
ein **ZIP-Archiv mit einer Datei `vitrine.json` an der Wurzel** (dem Manifest)
und deinen Ressourcen daneben. Vitrine entpackt ein `.vpkg` nie — es liest
direkt aus dem ZIP.

Es gibt zwei gleichwertige Wege, ein `.vpkg` zu bauen:

- **A — Vitrine Designer** im Browser: Formular ausfüllen, Vorschau ansehen,
  Paket exportieren. Kein Terminal nötig.
- **B — die CLI** (`cli/vitrine.js`): Ordner anlegen, Dateien von Hand
  bearbeiten, prüfen, packen, signieren.

Beide erzeugen dasselbe Format und prüfen gegen **dasselbe Schema**
([`registry/schema/vitrine.schema.json`](../registry/schema/vitrine.schema.json)) —
du kannst mitten im Ablauf wechseln.

---

## Vorab: Klasse A oder Klasse B?

- **Klasse A** sind Live-Inhalte: Texturen, Themes, HUD-Module, Shader, Sounds,
  Sprachen, Kosmetik. Sie werden **im laufenden Spiel** aktiv, ohne Neustart,
  und führen **keinen eigenen Code** aus. Alles in dieser Anleitung dreht sich
  um Klasse A.
- **Klasse B** sind echte Java-Mods (`.jar`). Die brauchen einen Neustart, sind
  im Store standardmäßig ausgeblendet und kommen erst später. Für Klasse B ist
  diese Anleitung nicht gedacht.

### Fähigkeiten-Whitelist

Ein Paket bekommt **nur** die Fähigkeiten, die es im Manifest unter
`capabilities` ausdrücklich anmeldet. Alles andere ist verboten — das ist die
Voreinstellung (Charter-Regel 4).

Die Mod-Laufzeit kennt genau **sieben** feste Bezeichner, jeweils **ohne
Punkt** — sie decken sich 1:1 mit den `category`-Werten des Manifests (nur
`bundle` fehlt, ein Bundle ist bloß ein Container):

`texturen`, `theme`, `hud`, `shader`, `sound`, `sprache`, `cosmetics`

Ein HUD-Paket meldet `hud` an, ein Textur-Paket `texturen`, und so weiter.
**Jeder andere Eintrag** (z. B. `hud.read` oder `texturen.override` — solche
Formen tauchen nur als Muster-Beispiele im Schema auf) besteht zwar die
Schema-Prüfung, führt aber **beim Installieren zu einer Ablehnung des Pakets**.

Für ein einfaches Textur- oder Theme-Paket kannst du `capabilities` auch ganz
weglassen. Trag nur ein, was dein Paket wirklich tut; im Store sieht der Nutzer
diese Liste vor der Installation.

---

## Das Manifest `vitrine.json`

Egal welcher Weg — am Ende steckt dieses Manifest im `.vpkg`. Pflichtfelder:

| Feld | Beispiel | Regel |
|---|---|---|
| `schemaVersion` | `1` | immer `1` |
| `id` | `"annas-pixel.klare-steine"` | mind. zwei durch Punkt getrennte Segmente, je Segment nur Kleinbuchstaben/Ziffern/Bindestrich, beginnt mit Buchstabe. Kein `..`, keine Leerzeichen. Wird zum Dateinamen im Katalog. |
| `name` | `"Klare Steine"` | Anzeigename, 1–80 Zeichen |
| `version` | `"1.0.0"` | SemVer (`Major.Minor.Patch`) |
| `category` | `"texturen"` | eine von: `texturen`, `theme`, `hud`, `shader`, `sound`, `sprache`, `cosmetics`, `bundle` |
| `author` | `"Anna"` | Name/Handle, 1–80 Zeichen |

Optionale Felder: `description`, `capabilities` (Array), `icon` (relativer
`.png`-Pfad im ZIP), `screenshots` (Array relativer Bildpfade), `homepage` (nur
`https://`), `license` (Kurzbezeichner, z. B. `"CC-BY-4.0"`). Vollständige
Beschreibung: [`registry/schema/README.md`](../registry/schema/README.md).

### Ressourcen-Layout im ZIP

Minecraft-Ressourcen liegen im normalen Resource-Pack-Layout:

```
vitrine.json
assets/<namespace>/textures/...
assets/<namespace>/sounds/...
assets/<namespace>/lang/...
icon.png            (falls im Manifest als "icon": "icon.png" angegeben)
```

Nur **eigene** Inhalte — keine fremden Grafiken, Sounds oder Fonts
(Charter-Regel 6).

---

## Weg A — Vitrine Designer im Browser

Der Designer ist eine **statische Seite ohne Installation**.

1. Im Repo die Datei **`designer/index.html`** per Doppelklick im Browser
   öffnen. Kein Server, kein Build, keine Internetverbindung nötig. Dein Projekt
   wird im Browser gespeichert (bei einem privaten Fenster geht es beim
   Schließen verloren — der Designer weist darauf hin).

2. **Manifest** (linke Nav, Abschnitt „Manifest"): Formular ausfüllen. Ein
   Live-Validator meldet Fehler mit demselben Wortlaut wie die CLI und der Mod.

3. **Theme** (nur wenn `category` = `theme`): Farben, Radien, Abstände, Blur,
   Schriftgrößen für Hell und Dunkel setzen. Die Live-Vorschau zeigt ein Mockup
   des Vitrine-Menüs; eine Kontrastwarnung erscheint bei zu geringem
   Text-Hintergrund-Kontrast.

4. **Texturen**: Bilddateien laden, einem Vanilla-Pfad zuordnen, Maße prüfen
   lassen, Vorher-Nachher ansehen.

5. **HUD** (nur wenn `category` = `hud`): deklarative Widgets zusammenklicken
   (Text, Icon, Balken, Liste), Anker und Skalierung wählen, Datenquelle aus
   der festen Liste (`fps`, `coordinates.x/y/z`, `biome`, `time`). Es gibt
   keine Formel- oder Skriptsprache. Der Designer erzeugt daraus die
   `hud.json`, die der Mod unverändert lädt.

6. **Export**: Knopf „Paket exportieren". Der Designer prüft das Manifest gegen
   das Schema und jede Textur gegen die Sicherheitsregeln, baut das `.vpkg` im
   Browser (JSZip) und zeigt die **SHA-256-Prüfsumme** an. Über die Knöpfe
   lädst du das `.vpkg` und die Prüfsummen-Datei herunter.

> Der Browser kann **nicht signieren** — er hat keinen Zugriff auf deinen
> lokalen Signierschlüssel. Die ed25519-**Signatur** fügst du danach mit
> `node cli/vitrine.js sign dein-paket.vpkg` hinzu (siehe unten). Zum lokalen
> Testen im Spiel reicht das unsignierte `.vpkg`; zum **Veröffentlichen** ist
> die Signatur Pflicht.

---

## Weg B — die CLI

Voraussetzung: **Node.js 18 oder neuer**. Keine npm-Installation nötig — die
CLI nutzt nur Node-Bordmittel. Alle Befehle von der Repo-Wurzel aus.

### 1. Projekt anlegen

```
node cli/vitrine.js init mein-paket
```

Legt `mein-paket/` an mit einem gültigen Platzhalter-`vitrine.json`, einer
`README.md` und einem leeren `assets/`-Ordner.

### 2. Dateien bearbeiten

- `mein-paket/vitrine.json` — eigene `id`, `name`, `version`, `category`,
  `author` eintragen.
- `mein-paket/assets/<namespace>/...` — deine Ressourcen ablegen.
- Optional `mein-paket/icon.png` und im Manifest `"icon": "icon.png"`.

### 3. Prüfen

```
node cli/vitrine.js validate mein-paket
```

Meldet jede Schema-Verletzung einzeln und im selben Wortlaut wie Designer und
Mod. Muss fehlerfrei durchlaufen, bevor es weitergeht.

### 4. Packen

```
node cli/vitrine.js pack mein-paket mein-paket.vpkg
```

Baut das `.vpkg` (validiert dabei das Manifest erneut). Versteckte Dateien,
`.git`, `node_modules` und Symlinks kommen nicht mit ins Archiv.

### 5. Signieren

```
node cli/vitrine.js sign mein-paket.vpkg
```

Beim ersten Aufruf erzeugt die CLI ein ed25519-Schlüsselpaar unter

```
%USERPROFILE%\.vitrine\signing-key        (privat, PEM — bleibt lokal)
%USERPROFILE%\.vitrine\signing-key.pub    (öffentlich)
```

(unter macOS/Linux: `~/.vitrine/`). Jeder weitere `sign`-Aufruf nutzt
denselben Schlüssel, damit deine Pakete einem Autor zuzuordnen bleiben. Der
**private Schlüssel darf nie ins Repo** und nie weitergegeben werden.

`sign` schreibt eine Begleitdatei **`mein-paket.vpkg.integrity.json`** mit
`sha256`, `signature` (Base64) und `publicKey`. Diese Datei gehört zum Paket —
sie wird beim Veröffentlichen ausgelesen. Baust du das `.vpkg` neu, musst du
neu signieren (die Begleitdatei prüft die Prüfsumme).

### 6. Im Spiel testen

`mein-paket.vpkg` nach `.minecraft/config/vitrine/library/` kopieren, Spiel
starten, `M` drücken — das Paket erscheint als lokales Angebot und lässt sich
installieren.

---

## Von null bis fertig — Kurzpfad (CLI)

1. Node.js 18+ installieren.
2. `node cli/vitrine.js init mein-paket`
3. `mein-paket/vitrine.json` anpassen (`id`, `name`, `version`, `category`,
   `author`).
4. Ressourcen unter `mein-paket/assets/<namespace>/...` ablegen.
5. `node cli/vitrine.js validate mein-paket` — fehlerfrei?
6. `node cli/vitrine.js pack mein-paket mein-paket.vpkg`
7. `node cli/vitrine.js sign mein-paket.vpkg` (erzeugt beim ersten Mal den
   lokalen Schlüssel).
8. `mein-paket.vpkg` in `config/vitrine/library/` legen und im Spiel prüfen.
9. Läuft es? Weiter mit **[publish-package.md](publish-package.md)**.

Zum Installieren des Spiels selbst siehe **[install.md](install.md)**.
