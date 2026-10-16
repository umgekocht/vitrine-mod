# Wie installiere ich Vitrine

Vitrine ist eine **Fabric-Mod für Minecraft Java Edition**. Sie bringt einen
Store ins Spiel, in dem du Texture-Packs, Themes, HUD-Module, Sounds und
Sprachen durchstöbern und mit einem Klick aktivieren kannst — die meisten davon
**ohne Neustart**.

Diese Anleitung geht davon aus, dass du Minecraft noch nie modden warst.

---

## 1. Voraussetzungen

| Was | Version | Woher |
|---|---|---|
| Minecraft Java Edition | **1.21.1** | offizieller Launcher |
| Java | **21** (oder neuer) | <https://adoptium.net> — „Temurin 21" |
| Fabric Loader | **0.16.9** oder neuer | <https://fabricmc.net/use/installer/> |
| Fabric API (Mod) | **0.116.0+1.21.1** oder passend zu 1.21.1 | <https://modrinth.com/mod/fabric-api> |
| Vitrine (Mod-Jar) | siehe unten — heute selbst gebaut | dieses Repo |

> Vitrine ist **nur ein Client-Mod**. Auf dem Server muss nichts installiert
> werden. Vitrine ist nicht mit Minecraft/Mojang verbunden.

### Fabric Loader einrichten

1. Fabric-Installer von <https://fabricmc.net/use/installer/> herunterladen und
   starten.
2. Reiter „Client", **Minecraft-Version 1.21.1** wählen, „Install".
3. Minecraft-Launcher öffnen — es gibt jetzt ein Profil **„fabric-loader-1.21.1"**.
   Einmal damit starten und wieder schließen. Dabei entsteht der Ordner
   `mods/` in deinem `.minecraft`-Verzeichnis.

Wo `.minecraft` liegt:

| System | Pfad |
|---|---|
| Windows | `%APPDATA%\.minecraft` |
| macOS | `~/Library/Application Support/minecraft` |
| Linux | `~/.minecraft` |

### Fabric API ablegen

Lade die zu 1.21.1 passende `fabric-api-*.jar` von Modrinth herunter und lege
sie in `.minecraft/mods/`. Ohne Fabric API startet Vitrine nicht.

---

## 2. Den Vitrine-Jar besorgen

### Heute: aus dem Quellcode bauen

Ein offizielles Release gibt es **noch nicht**. Der reguläre Weg im Moment ist,
den Jar selbst zu bauen. Dafür brauchst du nur Java 21 und dieses Repo — Gradle
lädt sich der Wrapper selbst.

```
git clone <dieses-repo>
cd <repo>/mod
./gradlew.bat build -Dvitrine.buildDir=C:\vitrine-build      # Windows
./gradlew build -Dvitrine.buildDir=/tmp/vitrine-build        # macOS / Linux
```

Der erste Lauf dauert ein paar Minuten (Abhängigkeiten). Danach liegt der Jar
unter:

```
C:\vitrine-build\libs\vitrine-0.1.0.jar
```

(Die Zahl ist die Mod-Version aus `mod/gradle.properties` und ändert sich mit
der Zeit. Ignoriere Dateien mit `-sources` im Namen — die brauchst du nicht.)

**Warum `-Dvitrine.buildDir`?** `mod/build.gradle` schreibt Build-Ausgaben
**nicht** nach `mod/build/` (das würde `clean` den Quellbaum gefährden lassen),
sondern per Vorgabe in einen Wegwerf-Ordner unter dem System-Temp mit einem
nicht vorhersehbaren Namen. Mit `-Dvitrine.buildDir=<pfad>` gibst du selbst
einen festen Ort vor — der Jar liegt dann unter `<pfad>\libs\`. Der Pfad
**muss** absolut sein, rein ASCII (kein Umlaut, auch nicht über den
Windows-Kontonamen) und **außerhalb** des Repos liegen; sonst bricht der Build
mit einer klaren Meldung ab. Lässt du die Option weg, nennt Gradle den
verwendeten Ordner im Log — der Jar liegt dann in `<dieser Ordner>\libs\`.

### Später: fertigen Jar herunterladen

Sobald es Releases gibt, entfällt der Bau-Schritt: Du lädst dann die fertige
`vitrine-<version>.jar` von der Releases-Seite des Mod-Repos herunter — der
Rest dieser Anleitung bleibt gleich. Bis dahin gilt der Bau-Weg oben.

---

## 3. Jar installieren

Kopiere die gebaute `vitrine-0.1.0.jar` (aus `C:\vitrine-build\libs\` bzw. dem
im Gradle-Log genannten Ordner) nach `.minecraft/mods/`.

In `mods/` liegen jetzt mindestens zwei Dateien:

```
.minecraft/mods/
  fabric-api-0.116.0+1.21.1.jar
  vitrine-0.1.0.jar
```

---

## 4. Spiel starten

1. Launcher öffnen, das Fabric-Profil (`fabric-loader-1.21.1`) wählen, „Spielen".
2. Warten, bis das Hauptmenü erscheint. Wenn das Spiel bis hier lädt, ist Vitrine
   aktiv.

Beim **allerersten Start** legt Vitrine seinen Konfigurationsordner an:

```
.minecraft/config/vitrine/
```

Darin entstehen mit der Zeit `settings.json` (deine Einstellungen), `packs/`
und `library/` (deine Pakete), `cache/` (heruntergeladener Katalog) und
`state.json` (was gerade aktiv ist). Du musst hier **nichts** von Hand
bearbeiten — Vitrine verwaltet den Ordner selbst. Ist die Datei mal kaputt,
fällt Vitrine auf Standardwerte zurück und legt ein Backup an, statt
abzustürzen.

---

## 5. Store öffnen

Drücke im Spiel die Taste **`M`** (Haupt- oder Pausenmenü, oder einfach im
Spiel). Es gibt zusätzlich einen Knopf im Hauptmenü und im Pausenmenü.

Die Taste lässt sich in den Minecraft-Steuerungseinstellungen unter
„Vitrine-Store öffnen" umlegen.

### Was du beim ersten Start siehst

- Links die **Nav-Rail** mit Start, Bibliothek und den Kategorien (Texturen,
  Themes, HUD, Shader, Sound & Sprache, Kosmetik, Einstellungen).
- Oben eine Kopfleiste mit Suche.
- Im Inhaltsbereich zunächst wenig: ohne Internet und ohne eigene Pakete zeigt
  Vitrine eine Platzhalter-Reihe und den Hinweis **„Noch nichts hier"** bzw.
  **„Kein Netz — installierte Pakete funktionieren weiterhin"**. Das ist
  erwartetes Verhalten, kein Fehler.

Sobald der Online-Katalog erreichbar ist, füllen sich Start-Seite und
Kategorien mit installierbaren Paketen. Ein Klick auf eine Kachel öffnet die
Detailseite mit dem Aktionsknopf „Installieren".

### Eigene Pakete lokal testen (ohne Veröffentlichen)

Lege eine fertige `.vpkg`-Datei in

```
.minecraft/config/vitrine/library/
```

Der Store zeigt sie ohne Neustart als lokales Angebot an. So prüfst du ein
selbst gebautes Paket (siehe **[build-package.md](build-package.md)**), bevor
du es veröffentlichst.

---

## Von null bis fertig — Kurzpfad

1. Java 21 installieren (Temurin).
2. Minecraft 1.21.1 einmal im offiziellen Launcher starten.
3. Fabric Loader für 1.21.1 installieren, Fabric-Profil einmal starten und
   schließen.
4. `fabric-api-*.jar` (für 1.21.1) nach `.minecraft/mods/` legen.
5. Dieses Repo klonen, `cd mod`,
   `./gradlew.bat build -Dvitrine.buildDir=C:\vitrine-build` (bzw. `./gradlew
   build -Dvitrine.buildDir=/tmp/vitrine-build`).
6. `C:\vitrine-build\libs\vitrine-<version>.jar` nach `.minecraft/mods/`
   kopieren.
7. Spiel über das Fabric-Profil starten.
8. Im Spiel `M` drücken — der Vitrine-Store öffnet sich.
9. Optional: eine `.vpkg` in `.minecraft/config/vitrine/library/` legen, um ein
   eigenes Paket zu testen.

Weiter mit **[build-package.md](build-package.md)**, wenn du ein eigenes Paket
bauen willst.
