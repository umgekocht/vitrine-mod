# THIRD-PARTY.md — fremder Code in Vitrine

Regel aus der Charter: **nur MIT oder Apache-2.0**, Lizenz **an der Quelle**
geprüft (LICENSE-Datei des Repos — nicht das POM, nicht `package.json`, nicht
aus dem Gedächtnis), transitive Abhängigkeiten zählen mit. Jede Aufnahme
bekommt hier eine Zeile mit Datum. Im Zweifel: nicht einbinden.

| Bibliothek | Version | Lizenz | Quelle der Prüfung (URL zur LICENSE) | geprüft am | wofür |
|---|---|---|---|---|---|
| Fabric Loader | 0.16.9 | Apache-2.0 | https://github.com/FabricMC/fabric-loader/blob/master/LICENSE | 2026-08-31 | Mod-Loader |
| Schrift Inter (variabel, Google-Fonts-Build) | 4.x | OFL-1.1 | https://github.com/google/fonts/blob/main/ofl/inter/OFL.txt | 2026-10-06 | glatte UI-Schrift in Vitrine-Bildschirmen; Lizenztext liegt als mod/.../assets/vitrine/font/Inter-OFL.txt im Jar |
| Fabric API | 0.116.0+1.21.1 | Apache-2.0 | https://github.com/FabricMC/fabric/blob/1.21/LICENSE | 2026-08-31 | Client-Hooks |
| Fabric Loom (Gradle-Plugin) | 1.9.2 | MIT | https://github.com/FabricMC/fabric-loom/blob/dev/1.9/LICENSE | 2026-09-01 | Gradle-Build (remap, Minecraft-Deps) — nicht im Jar |
| Fabric Yarn Mappings | 1.21.1+build.3 | CC0-1.0 (siehe Ausnahme unten) | https://github.com/FabricMC/yarn/blob/1.21.1/LICENSE | 2026-09-01 | Build-Zeit-Mappings zum Remappen von Bytecode — nicht im Jar |
| TestNG | 7.10.2 | Apache-2.0 | https://github.com/testng-team/testng/blob/master/LICENSE.txt | 2026-08-31 | Testrahmen — nur `testImplementation`, nicht im Jar |
| LuaJ | 3.0.1 | MIT | https://github.com/luaj/luaj/blob/master/LICENSE | 2026-10-13 | Lua runtime for Vitrine add-ons, bundled jar-in-jar, unmodified |
| Mozilla Rhino | 1.7.15 | MPL-2.0 | https://github.com/mozilla/rhino/blob/master/LICENSE.txt | 2026-10-13 | JavaScript runtime for Vitrine add-ons, bundled jar-in-jar, unmodified; source: https://github.com/mozilla/rhino/tree/Rhino1_7_15_Release |
| Gradle Wrapper | 8.12 | Apache-2.0 | https://github.com/gradle/gradle/blob/master/LICENSE | 2026-09-01 | Build-Bootstrap (`gradlew`), mit `gradle wrapper` aus der per SHA-256 geprüften Gradle-8.12-Distribution erzeugt (`distributionSha256Sum` in `gradle-wrapper.properties` gegen services.gradle.org verifiziert) — nicht im Jar |
| SLF4J API | 2.0.9 | MIT | jar-intern `META-INF/LICENSE.txt` + `Bundle-License` in `META-INF/MANIFEST.MF` (transitiv über Fabric Loader, geprüft im lokalen Gradle-Cache) | 2026-09-01 | Logging-Fassade, von `Vitrine.java` direkt importiert — nicht im Jar (kommt zur Laufzeit vom Loader/Minecraft) |
| Gson | 2.10.1 | Apache-2.0 | https://github.com/google/gson/blob/main/LICENSE | 2026-09-02 | JSON-(De-)Serialisierung in `de.spassglas.vitrine.config` (Einstellungsdatei); transitiv über `com.mojang:minecraft` vorhanden (`./gradlew dependencies --configuration compileClasspath` bestätigt `com.google.code.gson:gson:2.10.1`), keine zusätzliche `build.gradle`-Abhängigkeit nötig — nicht im Jar (kommt zur Laufzeit von Minecraft/Loader) |
| @cfworker/json-schema | 4.1.1 | MIT | https://api.github.com/repos/cfworker/cfworker/license (LICENSE-Datei im Repo, MIT, Copyright Jeremy Danyow) | 2026-09-02 (weiterverwendet für K1 am 2026-09-03) | `registry/package.json` (devDependency) — validiert die Beispielmanifeste unter `registry/schema/examples/` gegen `registry/schema/vitrine.schema.json` (`registry/schema/validate-examples.mjs`), unterstützt Draft 2020-12; reine Build-/Entwicklungszeit-Prüfung, nicht Teil des Mod-Jars, nicht Teil der statischen Designer-Seite. Seit K1 (2026-09-03) zusätzlich genutzt von `registry/build-index.js` (Validierung jedes `packages/<id>.json`-Eintrags gegen `registry-package.schema.json`) und `registry/test/build-index.test.mjs` (Prüfung der erzeugten `index/v1/index.json`/`search.json`/`featured.json` gegen ihre Schemas). Seit K6 (2026-09-03) außerdem von `registry/scripts/validate-changed-packages.mjs`. Seit T1 (2026-09-06) außerdem von `registry/test/theme-schema.test.mjs` (Validierung der Beispiel-Themes unter `registry/schema/examples/theme/` gegen `registry/schema/theme.schema.json`) — keine neue Abhängigkeit, dieselbe bereits geprüfte Bibliothek. Bewusst **statt** Ajv gewählt: Ajv 8.17+ zieht mit `fast-uri` eine transitive BSD-3-Clause-Abhängigkeit, `@cfworker/json-schema` hat laut `npm view` keine eigenen Laufzeit-Abhängigkeiten (`node_modules/` enthält nach `npm install` genau dieses eine Paket) |
| actions/checkout | v7.0.1 (SHA `3d3c42e5aac5ba805825da76410c181273ba90b1`) | MIT | https://raw.githubusercontent.com/actions/checkout/3d3c42e5aac5ba805825da76410c181273ba90b1/LICENSE (Copyright GitHub, Inc. and contributors) | 2026-09-03 (weiterverwendet für D8 am 2026-09-06) | `registry/.github/workflows/pr-check.yml`, `registry/.github/workflows/build-index.yml` — Code auschecken in beiden CI-Workflows des künftigen `vitrine-registry`-Repos (K6), per festem Commit-SHA statt `@main`/`@v7` eingebunden. Seit D8 (2026-09-06) außerdem in `templates/package-repo/package-repo/.github/workflows/release.yml` — checkt das eigene Autoren-Repo **und** (zweiter Schritt) das öffentliche Hauptrepo `umgekocht/vitrine-mod` in einen Unterordner aus, um `cli/`, `designer/schema-validate.js` und `registry/schema/vitrine.schema.json` ohne neue Abhängigkeit bereitzustellen |
| actions/setup-node | v7.0.0 (SHA `820762786026740c76f36085b0efc47a31fe5020`) | MIT | https://raw.githubusercontent.com/actions/setup-node/820762786026740c76f36085b0efc47a31fe5020/LICENSE (Copyright GitHub, Inc. and contributors) | 2026-09-03 (weiterverwendet für D8 am 2026-09-06) | `registry/.github/workflows/pr-check.yml`, `registry/.github/workflows/build-index.yml` — Node.js einrichten in beiden CI-Workflows, per festem Commit-SHA eingebunden. Seit D8 (2026-09-06) außerdem in `templates/package-repo/package-repo/.github/workflows/release.yml` |
| actions/setup-java | v6.0.1 (SHA `de7274f081f381c8f8158605e0321c36c376e2e6`) | MIT | https://raw.githubusercontent.com/actions/setup-java/de7274f081f381c8f8158605e0321c36c376e2e6/LICENSE (Copyright GitHub, Inc. and contributors) | 2026-09-11 | `.github/workflows/release.yml` (A5, Wurzel-Repo `vitrine`) — richtet JDK 21 (Temurin) ein, um `mod/gradlew build` bei einem `v*`-Tag auf dem Runner auszuführen; per festem Commit-SHA statt `@v6` eingebunden |
| softprops/action-gh-release | v3.0.3 (SHA `efb35369e0ad2afab669f228072c1b0d510eae64`) | MIT | https://raw.githubusercontent.com/softprops/action-gh-release/efb35369e0ad2afab669f228072c1b0d510eae64/LICENSE (Copyright 2019-current Doug Tangren) | 2026-09-11 | `.github/workflows/release.yml` (A5, Wurzel-Repo `vitrine`) — legt bei einem `v*`-Tag das GitHub-Release an (falls noch nicht vorhanden) und hängt den gebauten Mod-Jar sowie dessen `.sha256`-Datei als Assets an; per festem Commit-SHA eingebunden |
| Brigadier | 1.3.10 (Minecraft-1.21.1-Bibliothek `com.mojang:brigadier`, siehe `mojang_minecraft_info.json` der Loom-Version-Manifest) | MIT | https://api.github.com/repos/Mojang/brigadier (Lizenzfeld `mit`, LICENSE-Text im Repo mit Microsoft-Copyright) | 2026-09-05 | Befehls-Parsing fuer `de.spassglas.vitrine.hotapply.HotApplyCommand` (`/vitrine hotapply <packId>`, H2-Debug-Einstiegspunkt) — kommt als Minecraft-Laufzeit-Bibliothek vom Loader/Spiel, wird nicht ins Jar eingebettet |
| JSZip | 3.10.1 | MIT (dual-lizenziert MIT/GPLv3, "at your choice" — MIT gewaehlt; buendelt intern pako als eigene Abhaengigkeit, siehe eigene Zeile unten) | `npm pack jszip@3.10.1` entpackt, `LICENSE.markdown` im Paket geoeffnet und gelesen (Copyright Stuart Knightley u.a., MIT-Text vollstaendig vorhanden); Original-Quelle https://github.com/Stuk/jszip/blob/main/LICENSE.markdown; voller MIT-Text seit GATE27 (2026-09-16) zusaetzlich im Repo mitgeliefert unter [`designer/vendor/jszip.LICENSE.txt`](designer/vendor/jszip.LICENSE.txt), statt nur auf den externen Link zu verweisen | 2026-09-06 (LICENSE-Volltext ergaenzt 2026-09-16) | `designer/vendor/jszip.min.js` (Datei aus `dist/jszip.min.js` des npm-Pakets 1:1 uebernommen, keine Aenderung) — D6, ZIP-Bau fuer den `.vpkg`-Export direkt im Browser (klassisches `<script>`-Tag, kein Bundling, kein CDN, funktioniert offline unter `file://`), eingebunden ueber `designer/vpkg-export.js` |
| pako | ~1.0.2 (transitive Abhaengigkeit von JSZip 3.10.1, als Deflate/Inflate-Implementierung in `dist/jszip.min.js` mit hineincompiliert) | MIT | https://raw.githubusercontent.com/nodeca/pako/master/LICENSE (LICENSE-Datei an der Quelle gelesen, nicht aus `npm view`/`package.json`-Metadaten; Copyright (C) 2014-2017 by Vitaly Puzrin and Andrei Tuputcyn) | 2026-10-04 | Steckt mit in `designer/vendor/jszip.min.js` (JSZip buendelt pako beim eigenen Build in die Minified-Datei); der MIT-Pflichtvermerk ist zusaetzlich im Volltext in [`designer/vendor/jszip.LICENSE.txt`](designer/vendor/jszip.LICENSE.txt) abgedruckt |
| lie | 3.3.0 (transitive Abhaengigkeit von JSZip 3.10.1 -- `lib/external.js` von JSZip ruft `require("lie")` fuer die Promise-Implementierung auf, mit in `dist/jszip.min.js` hineincompiliert) | MIT | `npm pack jszip@3.10.1`/`npm pack lie@3.3.0` entpackt, `package.json` (`"dependencies": {"lie": "~3.3.0", ...}`) und `license.md` im `lie`-Paket geoeffnet und gelesen (Copyright (c) 2014-2018 Calvin Metcalf, Jordan Harband, MIT-Text vollstaendig vorhanden); Original-Quelle https://github.com/calvinmetcalf/lie/blob/master/license.md | 2026-10-05 | Steckt mit in `designer/vendor/jszip.min.js`; der MIT-Pflichtvermerk ist zusaetzlich im Volltext in [`designer/vendor/jszip.LICENSE.txt`](designer/vendor/jszip.LICENSE.txt) abgedruckt |
| immediate | 3.0.6 (transitive Abhaengigkeit von `lie` 3.3.0 -- `lie`s `package.json` listet `"immediate": "~3.0.5"`, mit in `dist/jszip.min.js` hineincompiliert) | MIT | `npm pack immediate@3.0.6` entpackt, `LICENSE.txt` im Paket geoeffnet und gelesen (Copyright (c) 2012 Barnesandnoble.com, llc, Donavon West, Domenic Denicola, Brian Cavalier, MIT-Text vollstaendig vorhanden); Original-Quelle https://github.com/calvinmetcalf/immediate/blob/master/LICENSE.txt | 2026-10-05 | Steckt mit in `designer/vendor/jszip.min.js`; der MIT-Pflichtvermerk ist zusaetzlich im Volltext in [`designer/vendor/jszip.LICENSE.txt`](designer/vendor/jszip.LICENSE.txt) abgedruckt |
| setimmediate | 1.0.5 (transitive Abhaengigkeit von JSZip 3.10.1 -- `package.json` von JSZip listet `"setimmediate": "^1.0.5"` direkt, mit in `dist/jszip.min.js` hineincompiliert) | MIT | `npm pack setimmediate@1.0.5` entpackt, `LICENSE.txt` im Paket geoeffnet und gelesen (Copyright (c) 2012 Barnesandnoble.com, llc, Donavon West, and Domenic Denicola, MIT-Text vollstaendig vorhanden); Original-Quelle https://github.com/YuzuJS/setImmediate/blob/master/LICENSE.txt | 2026-10-05 | Steckt mit in `designer/vendor/jszip.min.js`; der MIT-Pflichtvermerk ist zusaetzlich im Volltext in [`designer/vendor/jszip.LICENSE.txt`](designer/vendor/jszip.LICENSE.txt) abgedruckt |

Jede Zeile bekommt beim ersten Einbinden ein echtes Prüfdatum. Ohne Prüfdatum
ist die Abhängigkeit nicht abgenommen.

**Ausnahme Yarn Mappings (CC0-1.0):** Charter-Regel 5 verlangt „nur MIT/Apache
bei fremdem Code". Yarn-Mappings sind kein Code, der mitgeliefert wird, sondern
Bytecode-Umbenennungstabellen, die Loom ausschließlich zur Build-Zeit anwendet
(`remapJar`) — sie stecken nicht im fertigen Jar und werden nicht verlinkt
(verifiziert: `unzip -l` gegen die gebaute `vitrine-<version>.jar` enthält
keine Mapping-/Yarn-Artefakte). CC0-1.0 ist eine Public-Domain-Widmung ohne
Namensnennungs- oder Lizenztext-Pflicht — also ohne die Pflichten, die die
Whitelist verhindern soll, auch wenn CC0 anders als Apache-2.0 keine
ausdrückliche Patentlizenz gewährt (für eine reine Build-Zeit-Abhängigkeit
ohne Auslieferung folgenlos). Es gibt zudem keine MIT/Apache-lizenzierte
Alternative für Fabric-Mappings — Mojangs eigene Mappings stehen unter einer
restriktiveren Nutzungslizenz, Intermediary ist ebenfalls CC0. Deshalb:
bewusst aufgenommen, mit dieser Begründung, nicht nur aus der
Whitelist-Prüfung heraus.

## Bewusst **nicht** aufgenommen

| Kandidat | Grund |
|---|---|
| `com.mojang:minecraft` (`mod/build.gradle`, `minecraft` dependency) | Kein Fremdcode-Einbau im Sinne von Regel 5 — Loom lädt das Spiel-Jar nur als Build-/Laufzeit-Abhängigkeit gegen die installierte Minecraft-Lizenz (Mojang-EULA), es wird nichts davon in `vitrine-<version>.jar` mitgeliefert oder verteilt. |
| `readable-stream` (transitive Abhängigkeit von JSZip 3.10.1, `package.json` listet `"readable-stream": "~2.3.6"`) | Fällt beim Browser-Build von JSZip über dessen `browser`-Feld raus (Node-Stream-Polyfill, nur für den Node-Build gebraucht) — steckt nicht in `dist/jszip.min.js` und damit nicht in `designer/vendor/jszip.min.js` (geprüft: `npm pack jszip@3.10.1`, `package.json`-Feld `browser` ersetzt die Node-Stream-Pfade; kein Treffer auf stream-spezifische Bezeichner im Minified-Bundle). |

## Assets

Vitrine liefert **keine** fremden Grafiken, Sounds oder Fonts mit.
Vanilla-Texturen werden zur Laufzeit referenziert, nicht ins Repo kopiert.

Die 44 GUI-Grafiken unter `mod/src/main/resources/assets/vitrine/textures/gui/**`
sind eigene Anfertigungen von umgekocht, geliefert am 2026-08-31 (siehe
[ASSETS-NEEDED.md](ASSETS-NEEDED.md) für Maße und Verwendungszweck je Datei),
Lizenz Apache-2.0 wie der Rest des `mod/`-Quellbaums.

Die Branding-Grafiken `docs/site/img/banner.jpg`, `docs/site/img/mark.png`,
`docs/brand/banner.png` und `docs/brand/vitrine-icon.png` sind ebenfalls
eigene Anfertigungen von umgekocht für das Vitrine-Projekt (kein fremdes
Bildmaterial, keine EXIF/XMP-Spuren von anderswoher), geliefert am
2026-08-31 (`docs/brand/*.png`, siehe [ASSETS-NEEDED.md](ASSETS-NEEDED.md))
bzw. 2026-09-01 (`docs/site/img/*`), Lizenz Apache-2.0 wie der Rest des
Repos — veröffentlicht unter `docs/site/` (Projektseite) und als
Repo-/Designer-Branding unter `docs/brand/`, eingetragen am 2026-10-05.

Die Platzhaltertextur
`templates/package-repo/package-repo/assets/minecraft/textures/item/example_placeholder.png`
(D8, 2026-09-06) ist keine kopierte Grafik, sondern eine per Skript
(`templates/package-repo/tools/generate-placeholder-texture.mjs`, reine
Node-Bordmittel) erzeugte, einfarbige 16x16-Fläche — dient nur dazu, die
Ordnerstruktur eines `.vpkg` zu zeigen, nicht als echte Textur gedacht.
