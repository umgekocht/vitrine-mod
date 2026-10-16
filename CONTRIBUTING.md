# Mitwirken an Vitrine

Danke, dass du helfen willst. Vitrine ist ein kostenloser Client-Mod plus
statischer Designer — klein genug, dass ein Pull Request schnell geprüft ist,
wenn er ein paar Regeln einhält.

## Build und Tests müssen grün sein

Vor jedem Pull Request lokal:

```
cd mod && ./gradlew.bat build test      # Windows  (macOS/Linux: ./gradlew build test)
```

Für die Teile außerhalb von `mod/`:

```
cd registry && npm install && npm test
cd cli && npm test                         # keine Abhängigkeiten, nur Node-Bordmittel
cd designer && node --test test/*.test.js   # statische Seite, kein npm
```

Ein roter Build wird nicht gemergt (Charter-Regel 9). JDK 21 ist Pflicht,
Node 18+ für Designer/CLI/Registry. Kein pnpm, keine Workspaces — Laufwerk Z:
kann keine Symlinks und kein `fs.watch`.

## Charter — die Regeln, die nie gebrochen werden

Der vollständige Wortlaut steht in [`ROADMAP.md`](ROADMAP.md) unter „Charter".
Kurz, was für Beiträge am häufigsten zählt:

1. **Alles kostenlos.** Keine Preise, keine Währung, keine Bezahlschranke,
   keine Werbung, kein Konto-Zwang.
2. **Nichts vorgaukeln.** Was einen Neustart braucht, heißt „Neustart nötig".
   Kein Fortschrittsbalken, der nichts tut. Kein „hot" auf einem Paket, das es
   nicht ist.
3. **Sicherheit vor Bequemlichkeit.** Zip-Slip, Zip-Bomben, Bildmaße, Prüfsumme
   und Signatur werden **vor** dem Anwenden geprüft. Klasse A führt keinen
   fremden Code aus.
4. **Fähigkeiten sind eine Whitelist.** Default ist Verbot.
5. **Nur MIT/Apache** bei fremdem Code. Lizenz **an der Quelle** prüfen
   (LICENSE-Datei des Repos), transitive Abhängigkeiten zählen mit, Ergebnis
   mit Datum nach [`THIRD-PARTY.md`](THIRD-PARTY.md). Im Zweifel: nicht
   einbinden.
6. **Keine fremden Assets.** Keine Mojang-/Marketplace-Grafiken, keine Logos,
   Texturen, Fonts oder Sounds anderer Clients. Vanilla-Texturen aus dem
   laufenden Spiel referenzieren ist erlaubt, Kopieren ins Repo nicht.
7. **Keine hartkodierten Farben, Radien, Blurs, Schriftgrößen.** Alles kommt
   aus Theme-Tokens — ohne Ausnahme.
8. **Ein Schema, zwei Seiten.** Designer und Mod lesen dieselbe Datei unter
   `registry/schema/`. Kein zweites, abweichendes Format. Änderst du ein
   Schema, zieh beide Seiten nach.
9. **Jeder Commit grün.**
10. **Keine Secrets im Repo.** Keine Tokens, keine `.env`.

## Einen Bug melden

„New Issue" → **Bug report**. Bitte ausfüllen: Vitrine-Version, Minecraft- und
Fabric-Loader-Version, Fabric-API-Version, Schritte zum Nachstellen, erwartetes
und tatsächliches Verhalten, das Log bzw. den Absturzbericht (`.minecraft/logs/latest.log`
oder `crash-reports/`) und ob es um ein bestimmtes Paket geht.

## Einen Vorschlag machen

„New Issue" → **Feature request**. Beschreib das Problem, nicht nur die Lösung,
und ordne es einer Charter-Regel und einem Milestone aus `ROADMAP.md` zu, wenn
du kannst.

## Pull Requests

- Ein Thema pro PR, kleine Diffs.
- Neue oder angepasste Tests für die Änderung.
- Kein absoluter Maschinenpfad, kein Benutzername im Code.
- Die Checkliste in der PR-Vorlage abhaken.
- `mod/` und der Rest werden von getrennten Personen betreut — trenn PRs
  entsprechend, wenn möglich.
