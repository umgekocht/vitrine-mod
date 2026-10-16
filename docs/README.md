# Vitrine — Nutzer-Doku

Drei Anleitungen, die zusammen den ganzen Weg abdecken: von „nichts installiert"
bis „mein eigenes Paket steht im Katalog".

1. **[install.md](install.md)** — Vitrine im Minecraft-Client installieren und
   den Store öffnen.
2. **[build-package.md](build-package.md)** — ein eigenes Paket bauen
   (`.vpkg`), mit **Vitrine Designer** im Browser oder mit der **CLI**.
3. **[publish-package.md](publish-package.md)** — das Paket veröffentlichen, so
   dass es im Katalog erscheint und jeder es im Store installieren kann.

Jede Datei endet mit einem nummerierten Kurzpfad „von null bis fertig".

Ein Sonderweg für Fortgeschrittene:

4. **[klasse-b.md](klasse-b.md)** — einen **Java-Mod (`.jar`)** über Vitrine
   anbieten. Eigener Ablauf, kein `.vpkg`, Band „Neustart nötig", im Store
   standardmäßig ausgeschaltet.

Weitere Referenz im Repo:

- [`registry/CONTRIBUTING.md`](../registry/CONTRIBUTING.md) — genaues Format
  eines Katalog-Eintrags.
- [`registry/LICENSE-WHITELIST.md`](../registry/LICENSE-WHITELIST.md) — welche
  Inhalts-Lizenzen der Katalog annimmt.
- [`registry/schema/README.md`](../registry/schema/README.md) — alle Felder von
  `vitrine.json` (Paketmanifest).
- [`docs/UI-SPEC.md`](UI-SPEC.md) — Aufbau der Store-Oberfläche (Terminologie:
  Nav-Rail, Hero, Kachelreihen, Detailseite, Bibliothek).
- [`docs/HUD-SPEC.md`](HUD-SPEC.md) — HUD-Paket-Schema (Widgets, Anker,
  Datenquellen, Sichtbarkeit) und HUD-Laufzeit/-Editor im Spiel.
