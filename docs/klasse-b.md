# Klasse B — einen Java-Mod (`.jar`) über Vitrine anbieten

Diese Seite ist für Autorinnen und Autoren, die **echten Java-Code** ausliefern
wollen statt Texturen, Themes, HUDs, Sounds oder Sprachdateien. Das ist bei
Vitrine ein eigener, bewusst unbequemer Weg — **Klasse B**.

Wenn du kein `.jar` ausliefern willst, bist du hier falsch: nimm
[build-package.md](build-package.md).

---

## 1. Warum Klasse B überhaupt getrennt ist

Die JVM lädt Klassen **einmal**, und Mixins werden **beim Start** eingespielt.
Echter Java-Code lässt sich in Minecraft nicht nachladen — wer das verspricht,
meint etwas anderes. Vitrine täuscht das nicht vor, sondern trennt sauber
(siehe [PLAN.md](../PLAN.md), Abschnitt 1):

| | **Klasse A** | **Klasse B** |
|---|---|---|
| Was | Texturen, Theme, HUD, Sound, Sprache, Shader, Cosmetics, Bundle | ein Java-Mod als `.jar` |
| `contentClass` | `resource` (oder weggelassen) | `javamod` |
| Wirkt | sofort, ohne Neustart | erst nach einem **Neustart** |
| Ausgeliefert als | `.vpkg` (ZIP) | blankes `.jar`-Release-Asset |
| Kann Code ausführen | **nein** — es gibt keinen Pfad dorthin | ja, genau darum geht es |
| Im Store standardmäßig | an | **aus** (muss bewusst eingeschaltet werden) |

Klasse B ist deshalb kein „Klasse A mit einer Datei mehr", sondern ein eigener
Ablauf mit eigener Beschriftung.

---

## 2. Was die Nutzerin sieht

Das ist der Teil, den du beim Bauen im Kopf behalten solltest — er ist der
Grund für jedes Pflichtfeld weiter unten:

1. **Klasse B ist in den Einstellungen aus.** Wer dein Paket überhaupt sehen
   will, muss Java-Mods zuerst bewusst einschalten. Ohne diesen Schritt taucht
   dein Paket im Store gar nicht auf.
2. **Die Karte trägt ein Band „Neustart nötig".** Nicht geraten, sondern aus
   `hotApply: false` in deinem Manifest gelesen.
3. **Vor dem Installieren:** Klartextwarnung mit deinem Autorennamen und dem
   Hinweis, dass hier fremder Java-Code ins Spiel kommt.
4. **Beim Installieren** prüft Vitrine in dieser Reihenfolge — und bricht beim
   ersten Fehler ab, *bevor* irgendetwas nach `mods/` wandert:
   SHA‑256 der heruntergeladenen Datei → Ed25519-Signatur der Registry →
   Konflikt mit einer schon geladenen Mod-Id → erst dann das Verschieben nach
   `mods/` (nie überschreiben) → Eintrag in `staged-mods.json`.
5. **Danach:** die Mod liegt in `mods/`, läuft aber noch nicht. Es erscheint
   ein Sammelbanner „Neustart nötig" mit einem Knopf **„Jetzt neu starten"**.
   Bis zum Neustart kann die Nutzerin die Mod jederzeit wieder zurücknehmen.

Wenn eines dieser Versprechen in deinem Paket nicht stimmt, ist das Paket
kaputt — nicht die Oberfläche.

---

## 3. Das Manifest

Maßgeblich ist immer
[`registry/schema/vitrine.schema.json`](../registry/schema/vitrine.schema.json).
Designer und Mod lesen **dieselbe Datei** — es gibt kein zweites Format.

Ein vollständiges Klasse-B-Manifest (dieselbe Datei liegt als Beispiel unter
`registry/schema/examples/valid/klasse-b-javamod.json`):

```json
{
  "schemaVersion": 1,
  "id": "beispiel.leuchtkompass",
  "name": "Leuchtkompass",
  "version": "1.0.0",
  "category": "hud",
  "contentClass": "javamod",
  "hotApply": false,
  "javaMod": {
    "loader": "fabric",
    "minecraftVersions": ["1.21.1", "1.21.4"],
    "modId": "leuchtkompass",
    "dependencies": ["fabric-api"],
    "jar": {
      "fileName": "leuchtkompass-1.0.0.jar",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "size": 20480
    }
  },
  "author": "Dein Name",
  "description": "…",
  "license": "MIT"
}
```

### Die Klasse-B-Felder

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `contentClass` | ja, `"javamod"` | Ohne dieses Wort ist dein Paket Klasse A. Fehlt es, gilt `resource`. |
| `hotApply` | ja, `false` | Woraus das Band „Neustart nötig" entsteht. `true` ist hier ein Fehler, kein Schreibfehler. |
| `javaMod.loader` | ja | `fabric` oder `quilt`. |
| `javaMod.minecraftVersions` | ja, mind. 1 | z. B. `["1.21.1"]`. Der Store bietet dein Paket **nicht** an, wenn die laufende Version fehlt — besser gar nicht anbieten als nach dem Neustart ein kaputtes Spiel. |
| `javaMod.modId` | nein | Die Mod-Id aus deiner `fabric.mod.json`. Verhindert doppeltes Stagen, wenn dieselbe Mod schon geladen ist. |
| `javaMod.dependencies` | nein | Harte Mod-Ids, die vorher da sein müssen, z. B. `fabric-api`. Bewusst ohne Versionsbereich. |
| `javaMod.jar.fileName` | ja | Der Name, unter dem die Datei in `mods/` landet. Kein Pfad, kein `..`, kein Laufwerksbuchstabe, endet auf `.jar`. |
| `javaMod.jar.sha256` | **ja** | 64 Zeichen hex, klein. Ohne Prüfsumme wird nie gestaget — siehe unten. |
| `javaMod.jar.size` | ja | Größe in Bytes. |

### Zwei Regeln, die das Schema hart durchsetzt

1. **Ein Klasse-A-Paket darf kein `javaMod` tragen.** Schreibst du den Block in
   ein Paket mit `contentClass: "resource"` (oder ohne `contentClass`), ist das
   Manifest ungültig. So kann sich nie eine `.jar` in ein Paket schmuggeln, das
   als „wirkt sofort" beworben wird.
2. **Ein Klasse-B-Paket muss vollständig sein.** `hotApply: false` **und** der
   `javaMod`-Block **und** darin `jar.sha256` — fehlt eines davon, ist das
   Manifest ungültig. Ein Angebot ohne bekannte Prüfsumme wird von
   `JavaModStager` grundsätzlich abgelehnt; ein Manifest ohne `sha256` wäre also
   ein Paket, das garantiert nicht installierbar ist. Solche Pakete fallen hier
   auf und nicht erst bei der Nutzerin.

Kaputte Gegenbeispiele liegen als Testfixtures unter
`registry/schema/examples/invalid/14-…` bis `17-…`.

---

## 4. Warum im Manifest keine Signatur steht

Weil du sie gar nicht erzeugen könntest. Signiert wird mit dem
**Registry-Schlüssel**, und dessen privates Gegenstück hat nur die Registry.
Ein Signaturfeld in deinem Manifest wäre ein Vertrauensanker, den niemand hält
— ein Angreifer könnte sich mit seinem eigenen Schlüssel selbst „gültig"
signieren.

Die Kette läuft deshalb so:

```
Registry signiert den Katalog-Eintrag (versions[].signature, ed25519)
        └─ Eintrag nennt jarUrl + sha256 der .jar
              └─ dein Manifest nennt dieselbe sha256
                    └─ Vitrine prüft die heruntergeladene Datei dagegen,
                       BEVOR sie nach mods/ verschoben wird
```

Dein Beitrag ist die **richtige** `sha256` — mehr nicht, aber die muss stimmen.

---

## 5. Paket bauen und veröffentlichen

Der wichtigste Unterschied zu Klasse A: **du baust kein `.vpkg`.** Ein
Klasse-B-Paket wird als blankes `.jar` veröffentlicht; der Katalog-Eintrag zeigt
über `jarUrl` direkt darauf (siehe
`registry/schema/registry-package.schema.json`, `$defs/javamodVersionItem`).
Der Vitrine Designer bricht den `.vpkg`-Export für Klasse-B-Projekte deshalb
ausdrücklich ab, statt eine Datei zu bauen, die nie jemand herunterlädt.

1. **Mod normal bauen.** Fabric/Quilt, `gradlew build` — dabei entsteht dein
   `.jar` in `build/libs/`.

2. **Prüfsumme und Größe bestimmen.**

   ```powershell
   # Windows (PowerShell)
   (Get-FileHash .\build\libs\leuchtkompass-1.0.0.jar -Algorithm SHA256).Hash.ToLower()
   (Get-Item .\build\libs\leuchtkompass-1.0.0.jar).Length
   ```

   ```bash
   # Linux/macOS
   sha256sum build/libs/leuchtkompass-1.0.0.jar
   stat -c %s build/libs/leuchtkompass-1.0.0.jar
   ```

3. **Manifest schreiben.** Entweder von Hand nach dem Muster oben, oder im
   **Vitrine Designer**: im Manifest-Formular `contentClass` auf
   `javamod – Klasse B, Neustart nötig` stellen. Dann klappt der Abschnitt
   `javaMod` auf, `hotApply` steht auf `false`, und über dem Abschnitt steht der
   Hinweis, was die Nutzerin später zu sehen bekommt. Jedes Feld wird live gegen
   dasselbe Schema geprüft, das auch der Mod liest.

4. **`.jar` als Release-Asset veröffentlichen.** Genau wie bei Klasse A das
   `.vpkg`: eigenes GitHub-Repo, Release mit Versions-Tag, `.jar` anhängen.
   Der Katalog hostet keine Dateien, er verlinkt nur.

5. **Katalog-Eintrag anmelden.** Pull Request gegen das Katalog-Repo mit einer
   `packages/<id>.json`. Für Klasse B:

   ```json
   {
     "schemaVersion": 1,
     "id": "beispiel.leuchtkompass",
     "name": "Leuchtkompass",
     "category": "hud",
     "contentClass": "javamod",
     "author": { "name": "Dein Name" },
     "license": "MIT",
     "repository": "https://github.com/deinname/leuchtkompass",
     "versions": [
       {
         "version": "1.0.0",
         "jarUrl": "https://github.com/deinname/leuchtkompass/releases/download/v1.0.0/leuchtkompass-1.0.0.jar",
         "fileName": "leuchtkompass-1.0.0.jar",
         "sha256": "0123…",
         "signature": "…",
         "size": 20480,
         "dependencies": ["fabric-api"],
         "publishedAt": "2026-09-24T00:00:00.000Z"
       }
     ],
     "latestVersion": "1.0.0",
     "updatedAt": "2026-09-24T00:00:00.000Z"
   }
   ```

   `sha256` muss mit der im Manifest übereinstimmen. `signature` setzen die
   Registry-Maintainer beim Zusammenführen — siehe
   [`registry/CONTRIBUTING.md`](../registry/CONTRIBUTING.md).

6. **Lokal gegenprüfen**, bevor du den PR aufmachst:

   ```bash
   node registry/schema/validate-examples.mjs
   npm test --prefix registry
   ```

---

## 6. Was du als Autor versprichst

Klasse B ist der einzige Weg, auf dem fremder Code in ein fremdes Spiel kommt.
Deshalb gilt:

- **Deine Lizenz muss auf der
  [Lizenz-Whitelist](../registry/LICENSE-WHITELIST.md) stehen.** „Alle Rechte
  vorbehalten" wird nicht angenommen.
- **Die `sha256` gehört zu genau der `.jar`, die im Release liegt.** Tauschst du
  die Datei unter demselben Tag aus, ist dein Paket ab dem Moment nicht mehr
  installierbar — das ist Absicht.
- **Neue Version = neue Version.** Kein stilles Nachschieben unter derselben
  Versionsnummer.
- **`minecraftVersions` ehrlich führen.** Lieber eine Version weniger angeben
  als einen Absturz nach dem Neustart.

---

## Kurzpfad von null bis fertig

1. Mod bauen → `.jar` in `build/libs/`.
2. `sha256` und Größe bestimmen.
3. `vitrine.json` mit `contentClass: "javamod"`, `hotApply: false` und dem
   `javaMod`-Block schreiben (Designer oder von Hand).
4. Gegen das Schema prüfen: `node registry/schema/validate-examples.mjs`.
5. `.jar` als GitHub-Release-Asset veröffentlichen.
6. PR mit `packages/<id>.json` (`contentClass: "javamod"`, `jarUrl`) gegen das
   Katalog-Repo.
7. Nach dem Merge: dein Paket erscheint im Store — für alle, die Java-Mods
   eingeschaltet haben — mit dem Band **„Neustart nötig"**.
