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
    author: "Dein Name"
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
  bundle) und \`author\`. Optionale Felder: \`description\`, \`capabilities\`,
  \`icon\`, \`screenshots\`, \`homepage\`, \`license\`. Volle Beschreibung in
  \`registry/schema/README.md\` im Vitrine-Repo.
- \`assets/\` -- leerer Platzhalterordner fuer deine Ressourcen. Ein .vpkg
  legt Minecraft-Ressourcen im Vanilla-Resource-Pack-Layout ab:
  \`assets/<namespace>/textures/...\`, \`assets/<namespace>/sounds/...\`,
  \`assets/<namespace>/lang/...\`. \`icon\`/\`screenshots\` im Manifest sind
  eigene, relative Pfade (z. B. \`"icon.png"\` im Projekt-Wurzelordner) --
  du legst die Datei einfach dorthin und traegst denselben Pfad im Manifest
  ein.

## Naechste Schritte

1. \`vitrine.json\` anpassen: eigene \`id\`, \`name\`, \`version\`, \`category\`,
   \`author\` eintragen.
2. Eigene Ressourcen unter \`assets/<namespace>/...\` ablegen (keine fremden
   Grafiken/Sounds/Fonts -- Charter-Regel 6).
3. Pruefen: \`node cli/vitrine.js validate <dieser-ordner>\`
4. Bauen: \`node cli/vitrine.js pack <dieser-ordner> <name>.vpkg\`
5. Signieren: \`node cli/vitrine.js sign <name>.vpkg\`
   (erzeugt/nutzt einen lokalen Ed25519-Schluessel unter
   \`%USERPROFILE%\\.vitrine\\signing-key\` -- landet NIE im Repo.)
`;

module.exports = { defaultManifest, README_TEXT };
