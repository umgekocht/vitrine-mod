<!-- Danke für den Beitrag. Bitte die Checkliste abhaken, bevor du den PR öffnest. -->

## Was ändert dieser PR

<!-- Kurz. Verweise auf das Issue, wenn es eins gibt (Fixes #123). -->

## Checkliste

- [ ] `cd mod && ./gradlew.bat build test` läuft lokal grün (bzw. die
      betroffene Suite: `registry` / `designer` / `cli`).
- [ ] Neue oder angepasste Tests decken die Änderung ab.
- [ ] Keine hartkodierten Farben, Radien, Blurs oder Schriftgrößen — alles aus
      Theme-Tokens (Charter 7).
- [ ] Charter aus `ROADMAP.md` beachtet: kostenlos, nichts vorgaukeln,
      Sicherheit zuerst, Fähigkeiten-Whitelist.
- [ ] Neue Abhängigkeit? Nur MIT/Apache, Lizenz an der Quelle geprüft, Zeile mit
      Datum in `THIRD-PARTY.md`. Sonst: keine neue Abhängigkeit.
- [ ] Schema geändert? Designer- und Mod-Seite gleichgezogen (ein Schema, zwei
      Seiten, `registry/schema/`).
- [ ] Kein absoluter Maschinenpfad, kein Benutzername, keine Secrets im Diff.

## Screenshots / Belege

<!-- Bei UI-Änderungen: vorher/nachher. Bei Verhalten: Testausgabe. -->
