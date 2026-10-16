# Wie veröffentliche ich mein Paket

„Veröffentlichen" heißt bei Vitrine: dein `.vpkg` liegt als **Release-Asset in
deinem eigenen GitHub-Repo**, und im Katalog-Repo
[`vitrine-registry`](https://github.com/umgekocht/vitrine-registry) gibt es eine
kleine JSON-Datei, die darauf zeigt. Der Katalog hat **kein Backend** und
hostet keine Dateien — er verlinkt nur (Charter: „Katalog ohne Backend").

Voraussetzung: ein fertiges, **signiertes** `.vpkg` (siehe
**[build-package.md](build-package.md)**) und ein kostenloser GitHub-Account.
Deine Inhalts-Lizenz muss auf der
[Lizenz-Whitelist](../registry/LICENSE-WHITELIST.md) stehen — im Zweifel
`CC0-1.0`.

Es gibt zwei Wege. **Weg A** (Template-Repo) automatisiert fast alles und ist
für die meisten Autoren der einfachste. **Weg B** (`vitrine publish`) macht den
Registry-Schritt lokal von deinem Rechner aus.

---

## Weg A — Template-Repo

Das öffentliche Repo **`vitrine-package-template`** ist als GitHub-Template
markiert. Es enthält ein Beispiel-Manifest, die richtige Ordnerstruktur und
eine GitHub-Action, die auf jeden Versions-Tag reagiert.

1. **Repo erzeugen.** Auf `vitrine-package-template` den grünen Knopf
   **„Use this template" → „Create a new repository"**. Du bekommst eine Kopie
   unter deinem Account, z. B. `deinname/klare-steine`.

2. **`vitrine.json` anpassen.** `id`, `name`, `author`, `description`,
   `category` auf dein Paket setzen; `version` auf `1.0.0`. Regeln für die
   Felder: siehe [build-package.md](build-package.md#das-manifest-vitrinejson).

3. **Ressourcen einlegen.** Deine Dateien unter `assets/<namespace>/...`
   ablegen, die mitgelieferte Platzhaltertextur ersetzen oder löschen.

4. **Lokal prüfen** (optional, aber empfohlen):

   ```
   node vendor/vitrine-cli/cli/vitrine.js validate .
   ```

   Die CLI-Kopie liegt schon im Repo — kein zusätzlicher Schritt.

5. **Taggen.** `version` in `vitrine.json` und Tag müssen zusammenpassen:

   ```
   git commit -am "Klare Steine 1.0.0"
   git tag v1.0.0
   git push origin main --tags
   ```

6. **Die Action `release.yml` läuft automatisch** und macht:
   Manifest prüfen → `.vpkg` bauen → signieren (falls ein Schlüssel-Secret
   `VITRINE_SIGNING_KEY_B64` hinterlegt ist; sonst bleibt das Paket unsigniert
   und das steht klar im Action-Log) → **GitHub-Release für den Tag anlegen mit
   dem `.vpkg` als Anhang**.

7. **In den Katalog eintragen.**
   - Hast du im Repo ein Secret `REGISTRY_PAT` (ein GitHub-Token mit
     Schreibrechten für PRs) hinterlegt, öffnet die Action den Pull Request an
     `vitrine-registry` selbst.
   - Sonst machst du diesen letzten Schritt von Hand — entweder mit
     `vitrine publish` (Weg B unten, du gibst nur `--repo` und `--tag` an) oder
     indem du `packages/<id>.json` nach
     [`registry/CONTRIBUTING.md`](../registry/CONTRIBUTING.md) selbst schreibst
     und einen PR öffnest.

Neue Version später: `version` erhöhen, neuen Tag setzen, pushen. Alte Tags und
Releases bleiben stehen — Clients können bei Bedarf auf eine ältere Version
zurückfallen.

---

## Weg B — `vitrine publish` von deinem Rechner

Dieser Weg trägt ein **bereits gebautes, signiertes** `.vpkg`, das schon als
Release-Asset in deinem Repo hängt, per Pull Request in den Katalog ein.

### Voraussetzungen

- Node.js 18+.
- Die **GitHub-CLI `gh`** ist installiert und angemeldet (`gh auth login`).
  Vitrine legt **kein** eigenes Token an — es nutzt deine `gh`-Anmeldung.
- Dein `.vpkg` liegt als Release-Asset an einem Tag in deinem Repo, und die
  Datei `dein-paket.vpkg.integrity.json` aus `vitrine sign` liegt daneben auf
  deiner Platte. Der Asset-Name muss dem Dateinamen deines lokalen `.vpkg`
  entsprechen.

Ein Release mit Asset legst du entweder über Weg A an (Tag → Action) oder von
Hand: `gh release create v1.0.0 dein-paket.vpkg --repo deinname/dein-repo`.

### 1. Trockenlauf — zeigt alles, ändert nichts

```
node cli/vitrine.js publish dein-paket.vpkg \
  --repo https://github.com/deinname/dein-repo \
  --tag v1.0.0 \
  --dry-run
```

Der Trockenlauf macht **keinen** Netzzugriff. Er zeigt dir:

- die berechnete `vpkgUrl`
  (`https://github.com/deinname/dein-repo/releases/download/v1.0.0/dein-paket.vpkg`),
- `sha256`, `signature` und `size` (aus der `.vpkg`-Datei und der
  `.integrity.json`-Begleitdatei),
- den kompletten geplanten Inhalt von `packages/<id>.json`,
- Branch-Name, PR-Titel und PR-Text.

Passt alles, geht's echt weiter. Fehlt die Signatur-Begleitdatei, bricht
`publish` hier ab — dann erst `node cli/vitrine.js sign dein-paket.vpkg`.

### 2. Echter Lauf

Denselben Befehl **ohne `--dry-run`**:

```
node cli/vitrine.js publish dein-paket.vpkg \
  --repo https://github.com/deinname/dein-repo \
  --tag v1.0.0
```

Jetzt passiert:

1. `gh release view` prüft **lesend**, dass das Asset am Tag wirklich existiert
   — sonst Abbruch (kein erfundener Link).
2. `gh repo clone` holt `vitrine-registry` in einen temporären Ordner.
3. `packages/<id>.json` wird angelegt bzw. um die neue Version ergänzt und
   gegen `registry-package.schema.json` geprüft.
4. Commit, `git push` in einen Branch deines Forks/Klons, `gh pr create` gegen
   `main`.

Standard-Zielrepo ist `umgekocht/vitrine-registry`. Über `--registry
<besitzer/repo>` und `--base <branch>` lässt sich das ändern; `--asset
<dateiname>` überschreibt den angenommenen Asset-Namen.

---

## Was im Registry-PR passiert

- Die Action **`pr-check.yml`** validiert deine geänderte Datei unter
  `packages/` gegen
  [`registry-package.schema.json`](../registry/schema/registry-package.schema.json)
  (Pflichtfelder, Typen, Muster, Dateiname = `id`, `latestVersion` ist die
  höchste Version). Sie muss grün sein.
- Ein Mensch sieht sich Autor-Name, Kategorie und Lizenz an, bevor gemergt wird
  — die automatische Prüfung deckt das Format ab, nicht die Lizenz-Whitelist.
- **Nach dem Merge** baut die Action **`build-index.yml`** die Katalog-Dateien
  `index/v1/index.json`, `search.json` und `featured.json` neu. Ab da erscheint
  dein Paket im Store — im Client ggf. erst nach Ablauf des lokalen
  Katalog-Caches oder einem Neustart.

Prüfen, ob es durch ist:

```
curl https://umgekocht.github.io/vitrine-registry/index/v1/index.json
```

Deine `id` sollte im JSON auftauchen.

Was einen PR ablehnt (Kurzfassung, Details in
[`registry/CONTRIBUTING.md`](../registry/CONTRIBUTING.md)): Schema verletzt;
Dateiname passt nicht zur `id`; `latestVersion` falsch; `id` kollidiert mit
einem fremden Eintrag; Lizenz nicht auf der
[Whitelist](../registry/LICENSE-WHITELIST.md) oder verlangt Geld/Konto; `.vpkg`
liegt nicht als Release-Asset in einem `https`-Repo.

---

## Von null bis fertig — Kurzpfad

Ausgangspunkt: signiertes `dein-paket.vpkg` samt `.integrity.json` (aus
**[build-package.md](build-package.md)**).

1. GitHub-Account anlegen, Inhalts-Lizenz wählen
   ([Whitelist](../registry/LICENSE-WHITELIST.md), im Zweifel `CC0-1.0`).
2. **Weg A:** von `vitrine-package-template` per „Use this template" ein Repo
   erzeugen, `vitrine.json` + Ressourcen einsetzen, `git tag v1.0.0` +
   `git push --tags`. Die Action baut Release + `.vpkg`-Asset.
   **Weg B:** eigenes Repo, `gh release create v1.0.0 dein-paket.vpkg`.
3. `node cli/vitrine.js publish dein-paket.vpkg --repo <url> --tag v1.0.0
   --dry-run` — Plan ansehen.
4. Denselben Befehl ohne `--dry-run` — Pull Request an `vitrine-registry`
   entsteht. (Bei Weg A mit `REGISTRY_PAT` macht das die Action selbst.)
5. Warten, bis `pr-check.yml` grün ist und ein Maintainer mergt.
6. `curl https://umgekocht.github.io/vitrine-registry/index/v1/index.json` —
   deine `id` steht drin.
7. Im Spiel `M` drücken: dein Paket ist im Store installierbar.

Der ganze Weg zusammen: **[install.md](install.md)** →
**[build-package.md](build-package.md)** → diese Datei.
