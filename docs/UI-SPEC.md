# UI-SPEC.md — der Store sieht aus wie der Microsoft Store

umgekocht's Vorgabe (2026-08-30): **Design so nah am Microsoft Store wie möglich.**
Aufbau, Anordnung, Verhalten und Formensprache werden nachgebaut. Diese Datei
ist für die Store-Oberfläche genauso verbindlich wie die ROADMAP — `critic-ux`
prüft dagegen.

## Was übernommen wird und was nicht

**Übernommen (Aufbau und Verhalten):** Nav-Rail links, Suchleiste oben, Hero-
Karussell, horizontale Kachelreihen mit Überschrift und „Alle anzeigen",
Detailseite mit großem Aktionsknopf oben links, Bibliothek als eigener
Bereich, Download-Fortschritt in der Leiste, abgerundete Karten mit weichem
Schatten, durchscheinende Flächen (Acrylic/Mica-Anmutung), ruhige Ein- und
Ausblendungen.

**Nicht übernommen (gehört Microsoft, kommt nicht in den Jar):**

- **Segoe UI** und jede andere Microsoft-Schrift. Vitrine benutzt die
  Minecraft-Schrift bzw. eine frei lizenzierte Schrift (SIL OFL/Apache) und legt
  die Lizenz in `THIRD-PARTY.md` ab.
- **Fluent-Icons**, Store-Logo, Produktbilder, Screenshots aus dem Store.
  Alle Icons werden selbst gezeichnet — siehe `ASSETS-NEEDED.md`.
- Der **Name** „Microsoft Store", Microsoft-Marken, alles, was nach
  Zugehörigkeit aussieht.

Kurz: **Layout ja, Assets nein.**

---

## Raster und Maße (alles als Theme-Tokens, keine Zahl hartkodiert)

| Token | Wert | Wofür |
|---|---|---|
| `space.unit` | 4 px | Basiseinheit, alle Abstände sind Vielfache |
| `space.gutter` | 20 px | Abstand zwischen Karten |
| `space.page` | 32 px | Seitenrand des Inhaltsbereichs |
| `radius.card` | 8 px | Karten, Bilder |
| `radius.control` | 4 px | Knöpfe, Felder |
| `nav.width.expanded` | 280 px | Nav-Rail ausgeklappt |
| `nav.width.compact` | 56 px | Nav-Rail eingeklappt (schmales Fenster) |
| `header.height` | 56 px | Kopfleiste mit Suche |
| `card.width` | 200 px | Standardkachel |
| `card.aspect` | 16:9 | Bildbereich der Kachel |
| `hero.aspect` | 21:9 | Karussellbild |
| `elevation.rest` / `.hover` | 2 / 8 | Schattentiefe |

**Umbruchpunkte** (GUI-Skalierung berücksichtigen): unter 1000 px Nav-Rail
kompakt; unter 700 px Nav-Rail als Überlagerung, Kachelreihen scrollen weiter
horizontal.

## Typo-Stufen

`title.large` 28 · `title` 20 · `subtitle` 16 · `body` 14 · `caption` 12.
Nur diese fünf. Semibold für Überschriften und Preis-/Aktionszeilen, Regular
für Fließtext.

---

## Bildschirme

### 1. Kopfleiste (immer sichtbar)

Links Zurück-Pfeil (nur wenn es eine Rückkehr gibt), mittig die **Suchleiste**
mit Lupensymbol und Platzhaltertext, rechts ein Knopf für Einstellungen und die
**Download-Anzeige** (Pfeil nach unten, mit Zähler, wenn etwas läuft).

Klick auf die Suche öffnet ein Vorschlagsfeld darunter: letzte Suchen, dann
Live-Treffer aus `search.json`, jeweils mit kleinem Bild und Typ.

Klick auf die Download-Anzeige öffnet ein Flyout mit laufenden und zuletzt
abgeschlossenen Installationen, je mit Fortschrittsbalken, Größe und Abbrechen.

### 2. Nav-Rail links

Vertikale Liste mit Icon + Text, aktiver Eintrag mit farbigem Balken links und
gefülltem Icon:

- **Start** (Hero + kuratierte Reihen)
- **Texturen**
- **Themes**
- **HUD**
- **Sound & Sprache**
- **Cosmetics**
- **Shader**
- Trennlinie
- **Bibliothek** (installiert, Updates)
- unten abgesetzt: **Einstellungen**

Im schmalen Fenster nur Icons, Text als Tooltip beim Überfahren.

### 3. Start

1. **Hero-Karussell**, 21:9, 3–5 kuratierte Einträge aus `featured.json`.
   Punkte-Anzeige unten rechts, Pfeile links/rechts beim Überfahren,
   automatischer Wechsel alle 7 Sekunden — **pausiert**, sobald die Maus darauf
   liegt oder der Nutzer selbst blättert.
2. **Kachelreihen**, jede mit Überschrift, kurzer Unterzeile und „Alle
   anzeigen ›" rechts. Reihen: *Neu und beliebt*, *Empfohlene Themes*,
   *Texturen für 1.21*, *Zuletzt aktualisiert*, *Deine Updates* (nur wenn es
   welche gibt).
3. Horizontal scrollen mit Mausrad, Pfeiltasten und Ziehen; Chevron-Knöpfe
   erscheinen beim Überfahren an den Rändern.

### 4. Kachel

Bild (16:9, Platzhalter mit Typ-Symbol, wenn keines vorhanden) · Name
(einzeilig, mit Auslassungspunkten) · Autor (`caption`, gedämpft) · Typ-Badge
· rechts unten Statuszeichen: **Installiert** (Haken), **Update** (Pfeil),
**Neustart nötig** (Warnzeichen, nur Klasse B).

Beim Überfahren: leicht anheben (`elevation.hover`), Rahmen aufhellen. Klick
öffnet die Detailseite; Rechtsklick öffnet ein kleines Menü (Installieren,
Details, Autor, Melden).

### 5. Detailseite

Kopfbereich: großes Bild links (16:9), rechts Name (`title.large`), Autor als
Link, Typ-Badge, Sterne/Downloads aus der GitHub-API (read-only), Größe,
Lizenz, Version, MC-Versionen.

Darunter der **Aktionsknopf** — der einzige gefüllte Knopf auf der Seite:
`Installieren` → während des Ladens ein Fortschrittsring mit `Abbrechen` →
danach `Öffnen`/`Aktivieren` und daneben ein Menü mit `Deinstallieren`.
Bei Klasse B steht statt dessen `Herunterladen (Neustart nötig)`.

Dann in dieser Reihenfolge:

- **Screenshots** als horizontale Galerie, Klick öffnet groß.
- **Beschreibung**, aufklappbar ab 6 Zeilen („Mehr anzeigen").
- **Das darf dieses Paket** — die Fähigkeiten im Klartext, mit Icon je Eintrag.
  Bei Klasse A zusätzlich der Satz: „Führt keinen Programmcode aus."
- **Details**: Version, Größe, veröffentlicht am, Lizenz, Abhängigkeiten,
  Konflikte, Prüfsumme (kopierbar).
- **Änderungen** (CHANGELOG), aufklappbar.
- **Weitere Pakete dieses Autors** als Kachelreihe.

### 6. Bibliothek

Kopfzeile mit `Updates verfügbar (n)` und Knopf **Alle aktualisieren**.
Darunter eine Liste (keine Kacheln): Bild klein, Name, Version, Typ,
Größe, rechts ein Schalter **aktiv/inaktiv** und ein Überlaufmenü
(Aktualisieren, Deinstallieren, Ordner öffnen, Details).

Zweiter Abschnitt **Reihenfolge**: nur Ressourcen-Pakete, per Ziehen sortierbar,
mit dem Hinweis „weiter oben gewinnt bei gleicher Textur".

### 7. Zustände

Jeder Zustand hat Bild, Überschrift, einen Satz Erklärung und genau einen
Knopf:

| Zustand | Überschrift | Knopf |
|---|---|---|
| Bibliothek leer | „Noch nichts installiert" | „Zum Store" |
| Suche ohne Treffer | „Nichts gefunden für …" | „Suche löschen" |
| Kein Netz | „Keine Verbindung — du siehst den Stand von <Datum>" | „Erneut versuchen" |
| Katalog kaputt | „Katalog konnte nicht gelesen werden" | „Erneut versuchen" |
| Paket kaputt | „<Name> konnte nicht geladen werden" | „Details" |
| Lädt | Platzhalter-Kacheln (Skeleton), kein Spinner | — |

### 8. Fähigkeiten-Dialog

Erscheint vor der ersten Installation eines Pakets. Titel „<Name> möchte:",
darunter die Fähigkeiten als Liste mit Icon und Klartext, unten zwei
gleichwertige Knöpfe: **Abbrechen** und **Installieren**. Kein vorausgewähltes
Häkchen, keine Dunkelmuster, Abbrechen ist genauso leicht erreichbar.

---

## Bewegung

Alles kurz und ruhig: 150 ms für Überfahren, 250 ms für Seitenwechsel
(Einblenden + 8 px Aufwärtsbewegung), 400 ms fürs Karussell. Eine Kurve
(`ease-out`). Kein Federn, kein Hüpfen. Bei „Animationen reduzieren" in den
Einstellungen: alles sofort, ohne Übergang.

## Barrierefreiheit

Kontrast mindestens 4.5:1 für Text, 3:1 für Bedienelemente. Alles per Tastatur
erreichbar (Tab in Leserichtung, Pfeiltasten innerhalb einer Reihe, ESC
schließt), sichtbarer Fokusrahmen aus einem eigenen Token. Kein Zustand nur
über Farbe — immer zusätzlich Symbol oder Text.

## Theme

Das Standard-Theme **„Klarglas"** ist die helle und die dunkle Variante dieser
Anmutung: gedämpfte Graustufen, eine einzige Akzentfarbe (aus dem Theme, nicht
hartkodiert), durchscheinende Flächen über dem Spielhintergrund. Jeder Wert in
dieser Datei ist ein Token — `critic-theme` greppt nach Hex-Literalen und
Zahlenkonstanten im Store-Code.
