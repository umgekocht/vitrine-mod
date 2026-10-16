"use strict";

/*
 * Vitrine CLI -- Inhalte fuer `vitrine.js init <ordner>` (D7). Erzeugt nur
 * Geruest + Anleitungstext, keine echten Assets (Charter-Regel 6: "Keine
 * fremden Assets" -- und auch keine eigenen Platzhalter-Grafiken, die spaeter
 * mit dem Repo verwechselt werden koennten).
 */

/**
 * Platzhalter-Manifest, das bereits gegen registry/schema/vitrine.schema.json
 * validiert (alle Pflichtfelder gesetzt, gueltige Muster) -- ein frisch
 * initialisiertes Projekt soll `validate` sofort ohne Fehler bestehen, auch
 * wenn Name/Autor/Kategorie noch angepasst werden muessen.
 */
function defaultManifest() {
  return {
    schemaVersion: 1,
    id: "autor.mein-paket",
    name: "Mein Paket",
    version: "0.1.0",
    category: "texturen",
    author: "Dein Name",
    license: "CC0-1.0"
  };
}

const README_TEXT = `# Vitrine-Paketprojekt

Dieser Ordner ist ein Geruest fuer ein Vitrine-Paket (.vpkg). Er enthaelt noch
keine echten Assets -- die kommen von dir.

## Was hier liegt

- \`vitrine.json\` -- das Manifest. Pflichtfelder: \`schemaVersion\` (immer 1),
  \`id\` (z. B. \`"autor.paketname"\`, mindestens zwei durch Punkt getrennte
  Kleinbuchstaben-Segmente), \`name\`, \`version\` (SemVer, z. B. \`"1.0.0"\`),
  \`category\` (eine von texturen/theme/hud/shader/sound/sprache/cosmetics/
  bundle/addon), \`author\` und \`license\` (muss auf der Whitelist in
  \`registry/LICENSE-WHITELIST.md\` stehen, z. B. \`"CC0-1.0"\` oder
  \`"MIT"\`). Optionale Felder: \`description\`, \`capabilities\`, \`icon\`,
  \`screenshots\`, \`homepage\`. Volle Beschreibung in
  \`registry/schema/README.md\` im Vitrine-Repo.
- \`assets/\` -- leerer Platzhalterordner fuer deine Ressourcen. Ein .vpkg
  legt Minecraft-Ressourcen im Vanilla-Resource-Pack-Layout ab:
  \`assets/<namespace>/textures/...\`, \`assets/<namespace>/sounds/...\`,
  \`assets/<namespace>/lang/...\`. \`icon\`/\`screenshots\` im Manifest sind
  eigene, relative Pfade (z. B. \`"icon.png"\` im Projekt-Wurzelordner) --
  du legst die Datei einfach dorthin und traegst denselben Pfad im Manifest
  ein.
- Addon (\`category: "addon"\`, \`capabilities: ["verhalten"]\`): statt
  \`assets/\` kann ein Addon ein \`data/\` (Minecraft-Datenpaket-Inhalte,
  z. B. \`data/<namespace>/recipe/...\`) und/oder ein \`regeln/\` ablegen
  (eine oder mehrere \`regeln/*.json\`-Dateien mit deklarativen
  "wenn <Ereignis>, dann <Aktionen>"-Regeln). \`validate\`/\`pack\` pruefen
  jede \`regeln/*.json\` gegen dieselbe Ereignis-/Aktions-/Befehls-Whitelist
  wie der Mod -- siehe \`mod/src/main/java/de/spassglas/vitrine/addon/AddonRule.java\`
  fuer das genaue Format und erlaubte Werte.

## Naechste Schritte

1. \`vitrine.json\` anpassen: eigene \`id\`, \`name\`, \`version\`, \`category\`,
   \`author\`, \`license\` eintragen.
2. Eigene Ressourcen unter \`assets/<namespace>/...\` ablegen (keine fremden
   Grafiken/Sounds/Fonts -- Charter-Regel 6).
3. Pruefen: \`node cli/vitrine.js validate <dieser-ordner>\`
4. Bauen: \`node cli/vitrine.js pack <dieser-ordner> <name>.vpkg\`
5. Signieren: \`node cli/vitrine.js sign <name>.vpkg\`
   (erzeugt/nutzt einen lokalen Ed25519-Schluessel unter
   \`%USERPROFILE%\\.vitrine\\signing-key\` -- landet NIE im Repo.)
`;

module.exports = { defaultManifest, README_TEXT };
