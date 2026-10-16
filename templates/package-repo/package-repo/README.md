# Dein Vitrine-Paket

Dieses Repo wurde aus dem Vorlagen-Repo `vitrine-package-template` per
„Use this template" erzeugt. Es baut, prueft und veroeffentlicht dein
`.vpkg`-Paket automatisch, sobald du einen Tag setzt -- ohne Handarbeit.

## Was hier liegt

- [`vitrine.json`](vitrine.json) -- das Manifest. Pflichtfelder:
  `schemaVersion` (immer `1`), `id` (z. B. `"dein-name.dein-paket"`,
  mindestens zwei durch Punkt getrennte Kleinbuchstaben-Segmente), `name`,
  `version` (SemVer, z. B. `"1.0.0"`), `category` (eine von
  `texturen`/`theme`/`hud`/`shader`/`sound`/`sprache`/`cosmetics`/`bundle`)
  und `author`. Optional: `description`, `capabilities`, `icon`,
  `screenshots`, `homepage`, `license`. Volle Beschreibung im Vitrine-Repo
  unter `registry/schema/README.md`.
- [`assets/minecraft/textures/item/example_placeholder.png`](assets/minecraft/textures/item/example_placeholder.png)
  -- eine einfarbige 16x16-Platzhaltertextur, damit dieser Ordner nicht leer
  ist und der Pfad die richtige Struktur zeigt (Vanilla-Resource-Pack-Layout:
  `assets/<namespace>/textures/...`). Sie ist **keine echte Grafik** --
  ersetze sie durch deine eigene Ressource, bevor du etwas Ernstes
  veroeffentlichst. Vitrine nimmt keine fremden Assets ins eigene Repo auf
  (Charter-Regel 6); dieselbe Regel gilt fuer deinen Paketinhalt.
- [`LICENSE`](LICENSE) -- der volle Lizenztext zur Standard-Lizenz `CC0-1.0`
  (Public-Domain-Widmung), passend zum `license`-Feld im mitgelieferten
  `vitrine.json`. **Waehlst du eine andere Lizenz** aus der
  [Lizenz-Whitelist](https://github.com/umgekocht/vitrine-mod/blob/main/registry/schema/README.md)
  (z. B. `CC-BY-4.0`), musst du sowohl `license` in `vitrine.json` **als auch**
  diese `LICENSE`-Datei entsprechend ersetzen -- der Dateiinhalt muss immer
  zur gewaehlten Lizenz passen, nie nur das Manifest-Feld allein.
- [`.github/workflows/release.yml`](.github/workflows/release.yml) -- die
  Release-Automatik (siehe unten).
- [`vendor/vitrine-cli/`](vendor/vitrine-cli/) -- mitgelieferte Kopie der
  Vitrine-CLI, die `release.yml` benutzt. Nicht von Hand bearbeiten, siehe
  README dort.

## Vitrine.json anpassen

1. `id`: eindeutiger Bezeichner, `<dein-name>.<paketname>`, nur
   Kleinbuchstaben/Ziffern/Bindestrich je Segment.
2. `name`, `author`, `description`, `category` auf dein Paket anpassen.
3. `version` auf `1.0.0` setzen, sobald du zum ersten Mal wirklich
   veroeffentlichst (SemVer -- Major.Minor.Patch).
4. Eigene Ressourcen unter `assets/<namespace>/...` ablegen, die
   Platzhaltertextur entfernen oder ersetzen.
5. Optional: `icon` (quadratisches PNG, relativer Pfad im Repo) und
   `screenshots` ergaenzen.
6. `license` passend zur eigenen Wahl setzen -- und die Datei
   [`LICENSE`](LICENSE) im Repo-Wurzelordner **ersetzen**, falls du nicht bei
   `CC0-1.0` bleibst. Manifest-Feld und `LICENSE`-Datei muessen immer
   uebereinstimmen.

Lokal pruefen, bevor du taggst:

```
node vendor/vitrine-cli/cli/vitrine.js validate .
```

(Die `vendor/vitrine-cli/`-Kopie liegt bereits in deinem Repo, kein
zusaetzlicher Auschecken-Schritt noetig -- dieselbe CLI laeuft auch in
`.github/workflows/release.yml`.)

## Eine Version veroeffentlichen (Tag ⇒ Release ⇒ `.vpkg`)

1. Commit alle Aenderungen an `vitrine.json` und deinen Ressourcen.
2. Setz die `version` in `vitrine.json` auf denselben Wert wie dein Tag
   (z. B. `version: "1.0.0"`, Tag `v1.0.0`).
3. `git tag v1.0.0 && git push origin v1.0.0`
4. Die Action [`release.yml`](.github/workflows/release.yml) laeuft
   automatisch: sie prueft dein Manifest, baut das `.vpkg`, signiert es (falls
   ein Schluessel-Secret hinterlegt ist -- sonst bleibt es unsigniert, das
   steht dann klar im Log), und veroeffentlicht ein GitHub-Release fuer den
   Tag mit dem `.vpkg` als Anhang.
5. Den Link zum Release-Asset (und Pruefsumme/Signatur, falls signiert)
   traegst du -- oder die Action, falls du `REGISTRY_PAT` gesetzt hast -- in
   einen Pull Request gegen `vitrine-registry` ein. Siehe dort
   `CONTRIBUTING.md` fuer das genaue Format.

## Eine neue Version veroeffentlichen

Wiederhole die Schritte oben mit erhoehter `version` und neuem Tag. Alte Tags
und Releases bleiben stehen -- Vitrine-Clients koennen bei Bedarf auf eine
aeltere Version zurueckfallen (K5).
