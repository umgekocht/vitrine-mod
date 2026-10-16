# Lizenz-Whitelist für Paket-Autoren

Diese Liste gilt für **Klasse-A-Inhalte** (Texturen, Themes, HUD-Module,
Sounds, Sprachdateien — siehe `ROADMAP.md`, Technische Festlegungen: „Zwei
Inhaltsklassen"), die Autoren über diesen Katalog veröffentlichen. Sie ist
**nicht** dieselbe Liste wie [`THIRD-PARTY.md`](../THIRD-PARTY.md) — jene
regelt fremden **Code**, der in Vitrine selbst eingebaut wird (Charter-Regel
5: „nur MIT/Apache"). Hier geht es um **Content-Lizenzen** von Assets, die
Autoren Dritter *anbieten*, nicht in Vitrine einbauen.

## Warum eine eigene Liste

Charter-Regel 1 verlangt: **alles kostenlos**, keine Bezahlschranke, keine
Konto-Pflicht. Charter-Regel 5 (nur MIT/Apache) ist für **Code** gedacht, der
mitgeliefert und weiterverbreitet wird — ein Texturpaket oder ein Theme ist
kein Code im Sinne dieser Regel. Trotzdem muss jede zugelassene Lizenz
mindestens sicherstellen: kostenlose Nutzung, keine Sonderlizenz mit
Preisschranke „nur für Vitrine", keine Bedingung, die den Katalog selbst
rechtlich angreifbar macht (z. B. Lizenzen, die Weiterverbreitung ohne
Zustimmung verbieten — ein Katalog verbreitet zwangsläufig weiter, indem er
verlinkt und beschreibt).

## Erlaubt

| Lizenz | Kurzform | Hinweis |
|---|---|---|
| Creative Commons Zero | `CC0-1.0` | Public-Domain-Widmung, keine Auflagen — bevorzugt für neue Beiträge |
| Creative Commons Namensnennung | `CC-BY-4.0` (auch ältere 3.0/2.0) | Namensnennung des Autors verlangt, sonst keine Einschränkung |
| Creative Commons Namensnennung-Weitergabe | `CC-BY-SA-4.0` | wie `CC-BY`, zusätzlich: Ableitungen unter derselben Lizenz |
| MIT | `MIT` | falls ein Autor Code-nahe Assets (z. B. Shader-Quelltext) unter MIT stellt |
| Apache-2.0 | `Apache-2.0` | wie MIT, mit Patentklausel |
| Unlicense | `Unlicense` | Public-Domain-Äquivalent |
| Eigene, offene „freie Nutzung"-Erklärung | frei formuliert | nur wenn ausdrücklich **kostenlos, ohne Konto, ohne Einschränkung der Weitergabe über diesen Katalog** — im Zweifel: siehe unten |

## Nicht erlaubt

- **Jede Lizenz mit Preisschranke** — „kostenlos nur mit Kauf/Abo/Spende
  erzwungen", „nur für zahlende Nutzer", Paywall-Links. Verstößt gegen
  Charter-Regel 1, unabhängig vom Namen der Lizenz.
- **„Nur für Vitrine"-Sonderlizenzen.** Ein Autor darf sein Werk nicht so
  lizenzieren, dass es exklusiv an diesen einen Katalog gebunden ist und
  anderswo nicht frei genutzt werden darf — das widerspricht dem Charter-Ziel
  „kostenloser Marktplatz", nicht „exklusiver Vitrine-Shop".
- **Non-Commercial-Klauseln** (`CC-BY-NC*`), wenn sie die Weiterverbreitung
  über den Katalog selbst einschränken sollen — Vitrine selbst verlangt kein
  Geld, aber eine NC-Klausel schafft Unsicherheit für Nutzer, die den Inhalt
  z. B. auf einem Server mit Spenden-Button einsetzen. Im Zweifel: nicht
  aufnehmen, Autor um `CC-BY` oder `CC0` bitten.
- **All Rights Reserved / keine Lizenzangabe.** Ohne erkennbare, freie Lizenz
  gilt ein Werk als nicht zur Weiterverbreitung freigegeben — kein Eintrag.
- **Copyleft mit Registrierungspflicht oder Rückmeldepflicht an den Autor**
  (z. B. „nur nach persönlicher Erlaubnis") — das ist faktisch eine
  Konto-/Zugangsschranke, verstößt gegen Charter-Regel 1.
- Lizenzen fremder Marken/Stores (Mojang-/Bedrock-Marketplace-Bedingungen,
  Lizenzen anderer Client-Stores) — siehe auch Charter-Regel 6 („keine
  fremden Assets").

## Wo die Lizenz steht

Das `license`-Feld in `packages/<id>.json`
([Schema](schema/registry-package.schema.json)) ist **Pflicht**, ein
Freitext (z. B. `"CC-BY-4.0"`). Es wird automatisiert gegen diese Liste
geprüft: [`license-policy.mjs`](license-policy.mjs) kennt die kurzen,
maschinell erkennbaren Formen aus der „Erlaubt"-Tabelle oben
(`CC0-1.0`, `CC-BY-<2.0|2.5|3.0|4.0>`, `CC-BY-SA-<2.0|2.5|3.0|4.0>`, `MIT`,
`Apache-2.0`, `Unlicense`) sowie die Verbotsliste aus „Nicht erlaubt"
(leer, „All Rights Reserved"/„Alle Rechte vorbehalten", `CC-BY(-SA)-NC*`,
„nur für Vitrine"/„only for Vitrine", `proprietary`). Eine freie
Sonderlizenz aus der Tabellenzeile „Eigene, offene ‚freie Nutzung'-Erklärung"
braucht dafür einen expliziten Eintrag in `CUSTOM_ALLOWED_LICENSES`
(`license-policy.mjs`) — das geht selbst durch PR-Review, kein automatischer
Blanko-Freifahrtschein für beliebigen Freitext.

Diese Prüfung läuft an zwei Stellen, beide über dieselbe Funktion
(`licenseWhitelistViolation`): beim Index-Bau
([`build-index.js`](build-index.js), `loadPackages`) und bei der
PR-Prüfung ([`validate-changed-packages.mjs`](scripts/validate-changed-packages.mjs),
das `build-index.js` für seinen vollständigen zweiten Prüfdurchlauf
importiert) — ein Paket mit nicht gelisteter Lizenz kommt also weder in den
Index noch durch den PR-Check, ganz ohne menschliche Nachprüfung. Wenn du
unsicher bist, welche Lizenz passt: `CC0-1.0` ist immer sicher.

## Fragen

Im Zweifel: nicht veröffentlichen, im PR nachfragen. Siehe auch
[`CONTRIBUTING.md`](CONTRIBUTING.md) für den vollständigen Ablauf.
