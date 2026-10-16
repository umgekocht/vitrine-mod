# Beitrags-Anleitung — ein Paket im Vitrine-Katalog veröffentlichen

Dieses Repo (`vitrine-registry`) ist der öffentliche Katalog von Vitrine.
Kein Backend, kein Konto, keine Bezahlschranke (Charter-Regel 1) — jeder
Eintrag ist eine Datei unter [`packages/`](packages/), gepflegt per Pull
Request.

## Voraussetzungen

- Dein Paket ist ein fertiges, signiertes `.vpkg`-Archiv, gebaut mit
  **Vitrine Designer** (`designer/`) oder von Hand nach
  [`schema/README.md`](schema/README.md).
- Das `.vpkg` liegt als **GitHub-Release-Asset in deinem eigenen Repo** —
  dieser Katalog hat kein Backend und hostet keine Dateien selbst (Technische
  Festlegung „Katalog ohne Backend", siehe `ROADMAP.md`). `vitrine-registry`
  verlinkt nur.
- Dein Inhalt ist kostenlos und deine Lizenz steht auf der
  [Lizenz-Whitelist](LICENSE-WHITELIST.md).

## Schritt für Schritt

1. **Release anlegen.** Lade `dein-paket.vpkg` als Asset an einen Release in
   deinem eigenen GitHub-Repo hoch (z. B. Tag `dein-paket-v1.0.0`). Notier dir
   die direkte Download-URL — sie muss auf `.vpkg` enden.
2. **Prüfsumme und Signatur.** Vitrine Designer zeigt beim Export SHA-256 und
   die ed25519-Signatur deines `.vpkg` an (siehe `designer/`). Beide kommen
   unverändert in den Eintrag — falsche Werte lässt der Client beim Download
   scheitern (Charter-Regel 3, P4/K3).
3. **Eintrag schreiben.** Lege `packages/<deine-id>.json` an, `<deine-id>`
   identisch mit dem `id`-Feld in deinem `vitrine.json`-Manifest (Muster
   `<namensraum>.<name>`, z. B. `dein-name.mein-textur-pack`). Nutze
   [`packages/spassglas.steinbrocken-textur.json`](packages/spassglas.steinbrocken-textur.json)
   als Vorlage. **Pflichtfelder** (vollständige Liste und Muster in
   [`schema/registry-package.schema.json`](schema/registry-package.schema.json)):

   | Feld | Bedeutung |
   |---|---|
   | `schemaVersion` | fest `1` |
   | `id` | wie im `.vpkg`-Manifest, muss zum Dateinamen passen |
   | `name` | Anzeigename im Store |
   | `category` | eine von `texturen`, `theme`, `hud`, `shader`, `sound`, `sprache`, `cosmetics`, `bundle` |
   | `author.name` | dein Name/Pseudonym |
   | `license` | Kurzbezeichner von der [Lizenz-Whitelist](LICENSE-WHITELIST.md), z. B. `CC-BY-4.0` oder `CC0-1.0` — Index-Bau und PR-Prüfung lehnen nicht-whitelistete Werte automatisiert ab (siehe `license-policy.mjs`) |
   | `repository` | https-URL deines Repos (wo die Release-Assets liegen) |
   | `versions[]` | mindestens eine Version: `version` (SemVer), `vpkgUrl` (https, endet auf `.vpkg`), `sha256` (64 Hex-Zeichen), `signature` (Base64), `size` (Bytes), `publishedAt` (ISO-8601) |
   | `latestVersion` | SemVer eines Eintrags aus `versions[]`, muss die höchste sein |
   | `updatedAt` | ISO-8601, wann dieser Registry-Eintrag zuletzt geändert wurde |

   Optional: `description`, `tags`, `icon` (https, `.png`), `homepage`.
   Lass `featured` und `popular` weg — beide Felder sind reine
   Redaktionsfelder für die Startseite ("Featured" bzw. "Beliebt") und werden
   ausschließlich von den Registry-Maintainern bei der PR-Prüfung gesetzt,
   nie vom Autor selbst (Interessenkonflikt). `popular` ist keine gemessene
   Downloadzahl — dieser Katalog hat bewusst kein Backend, das sowas seriös
   erfassen könnte, sondern eine redaktionelle Einschätzung wie `featured`.
   Enthält dein PR eines dieser Felder, wird es beim Review entfernt oder der
   PR zurückgewiesen.

4. **Lokal prüfen, bevor der PR raus geht:**

   ```
   cd registry
   npm install
   npm run dry-run
   ```

   Läuft das grün durch, validiert derselbe Code, den die PR-Prüfung in der
   Cloud ausführt, deinen Eintrag gegen
   [`schema/registry-package.schema.json`](schema/registry-package.schema.json)
   und baut probeweise den ganzen Index (siehe [README.md](README.md#index-builder)).

5. **Pull Request öffnen** gegen `main`, nur deine neue oder geänderte Datei
   unter `packages/`. Die Action [`pr-check.yml`](.github/workflows/pr-check.yml)
   läuft automatisch und muss grün sein, bevor der PR gemergt wird.
6. Nach dem Merge baut [`build-index.yml`](.github/workflows/build-index.yml)
   automatisch `index/v1/*`, `search.json`, `featured.json` neu — du musst
   nichts weiter tun.

## Eine neue Version veröffentlichen

Release wie in Schritt 1–2 anlegen, dann in deiner bestehenden
`packages/<id>.json` einen weiteren Eintrag an `versions[]` anhängen und
`latestVersion` sowie `updatedAt` aktualisieren. Ältere Versionen bleiben in
`versions[]` stehen — der Client braucht sie für Rückfälle (K5).

## Was einen PR ablehnt

- Verletzt das Schema (Pflichtfeld fehlt, falscher Typ, ungültiges Muster).
- Dateiname passt nicht zur `id` (`packages/<id>.json`).
- `latestVersion` zeigt auf keine oder nicht die höchste Version in `versions[]`.
- `id` kollidiert mit einem bestehenden Eintrag eines anderen Autors.
- Lizenz steht nicht auf der [Whitelist](LICENSE-WHITELIST.md) oder verlangt
  Geld/Konto für den Inhalt (Charter-Regel 1). Wird seit GATE23 automatisiert
  von `registry/license-policy.mjs` geprüft, nicht mehr nur beim manuellen
  Review.
- `.vpkg` liegt nicht als Release-Asset in einem https-Repo (keine
  Datei-Uploads in diesen Katalog).
- Dein PR setzt `featured` oder `popular` selbst — das entscheiden nur die
  Registry-Maintainer.

## Fragen zum Format selbst

Siehe [`README.md`](README.md) für den Unterschied zwischen dem
Paketmanifest-Schema (`vitrine.json`, im `.vpkg`) und dem Registry-Format
(dieser Katalog).
