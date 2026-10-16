"use strict";

/*
 * Vitrine CLI -- reine, netzwerk- und dateisystemfreie Bausteine fuer den
 * Inhalt von "packages/<id>.json" im Katalog-Repo (D9, siehe
 * registry/CONTRIBUTING.md und registry/schema/registry-package.schema.json).
 * Diese Datei fasst nichts an -- weder Platte noch Netz -- damit sowohl der
 * `--dry-run`-Pfad als auch der echte Lauf von `cli publish` exakt denselben
 * Code fuer den Dokumentinhalt benutzen (cli/lib/publish.js orchestriert
 * beide Pfade und ruft hierher nur mit bereits eingelesenen JS-Objekten).
 *
 * Verbessert gegenueber dem Vorbild in
 * templates/package-repo/package-repo/.github/workflows/release.yml (dort
 * setzt der Action-Schritt `latestVersion` unbedingt auf die soeben
 * veroeffentlichte Version): hier wird `latestVersion` als die per SemVer
 * hoechste aller `versions[].version`-Eintraege berechnet, damit ein
 * nachtraeglich veroeffentlichter Patch fuer eine aeltere Nebenlinie
 * `latestVersion` nicht versehentlich zurueckdreht.
 */

const SEMVER_RE = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$/;

function parseSemVer(version) {
  const m = SEMVER_RE.exec(version);
  if (!m) {
    throw new Error("'" + version + "' ist kein gueltiges SemVer 2.0.0");
  }
  return { major: Number(m[1]), minor: Number(m[2]), patch: Number(m[3]), prerelease: m[4] || null };
}

/** SemVer-2.0.0-Vergleich (Precedence-Regeln, https://semver.org/#spec-item-11). >0 wenn a > b. */
function compareSemVer(a, b) {
  const pa = parseSemVer(a);
  const pb = parseSemVer(b);
  if (pa.major !== pb.major) return pa.major - pb.major;
  if (pa.minor !== pb.minor) return pa.minor - pb.minor;
  if (pa.patch !== pb.patch) return pa.patch - pb.patch;
  if (pa.prerelease === pb.prerelease) return 0;
  if (pa.prerelease === null) return 1; // Release > Pre-Release
  if (pb.prerelease === null) return -1;

  const idsA = pa.prerelease.split(".");
  const idsB = pb.prerelease.split(".");
  const len = Math.max(idsA.length, idsB.length);
  for (let i = 0; i < len; i++) {
    const ia = idsA[i];
    const ib = idsB[i];
    if (ia === undefined) return -1;
    if (ib === undefined) return 1;
    const numA = /^\d+$/.test(ia);
    const numB = /^\d+$/.test(ib);
    if (numA && numB) {
      const diff = Number(ia) - Number(ib);
      if (diff !== 0) return diff;
    } else if (numA) {
      return -1;
    } else if (numB) {
      return 1;
    } else if (ia !== ib) {
      return ia < ib ? -1 : 1;
    }
  }
  return 0;
}

/** Hoechste Version (SemVer-Precedence) aus einer Liste von versions[]-Eintraegen. */
function highestVersion(versions) {
  return versions.reduce((best, entry) => (compareSemVer(entry.version, best) > 0 ? entry.version : best), versions[0].version);
}

/**
 * Baut oder aktualisiert das packages/<id>.json-Dokument (unveraendertes
 * Eingabeobjekt `existingDoc` bleibt unangetastet -- es wird eine Kopie
 * zurueckgegeben). Wirft, wenn `versionEntry.version` bereits in
 * `existingDoc.versions` vorkommt oder die `id` nicht passt.
 *
 * @param {object|null} existingDoc bereits vorhandener Eintrag oder null (neues Paket)
 * @param {object} versionEntry {version, vpkgUrl, sha256, signature, size, publishedAt}
 * @param {object} meta {id, name, category, author, repository, description?, license?, homepage?, icon?}
 */
function mergeRegistryEntry(existingDoc, versionEntry, meta) {
  let doc;
  if (existingDoc) {
    if (existingDoc.id !== meta.id) {
      throw new Error("bestehender Eintrag hat id '" + existingDoc.id + "', erwartet '" + meta.id + "'");
    }
    doc = JSON.parse(JSON.stringify(existingDoc));
    if (doc.versions.some((v) => v.version === versionEntry.version)) {
      throw new Error(
        "Version '" + versionEntry.version + "' ist in 'packages/" + meta.id +
          ".json' bereits vorhanden -- Version in vitrine.json anheben, bevor erneut veroeffentlicht wird."
      );
    }
    doc.versions = doc.versions.concat([versionEntry]);
  } else {
    doc = {
      schemaVersion: 1,
      id: meta.id,
      name: meta.name,
      category: meta.category,
      author: meta.author,
      repository: meta.repository,
      versions: [versionEntry],
      latestVersion: versionEntry.version,
      updatedAt: versionEntry.publishedAt
    };
    if (meta.description) doc.description = meta.description;
    if (meta.license) doc.license = meta.license;
    if (meta.homepage) doc.homepage = meta.homepage;
    if (meta.icon) doc.icon = meta.icon;
    if (meta.tags && meta.tags.length > 0) doc.tags = meta.tags;
  }

  doc.latestVersion = highestVersion(doc.versions);
  doc.updatedAt = versionEntry.publishedAt;
  return doc;
}

module.exports = { parseSemVer, compareSemVer, highestVersion, mergeRegistryEntry };
