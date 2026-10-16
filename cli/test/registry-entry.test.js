"use strict";

/*
 * Testet cli/lib/registry-entry.js: reine SemVer-Vergleichs- und
 * Dokument-Merge-Logik fuer packages/<id>.json (D9), ohne Datei-/Netzzugriff.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const { compareSemVer, highestVersion, mergeRegistryEntry } = require("../lib/registry-entry.js");

test("compareSemVer: Major/Minor/Patch, Pre-Release < Release, numerische Pre-Release-Kennungen", () => {
  assert.ok(compareSemVer("2.0.0", "1.9.9") > 0);
  assert.ok(compareSemVer("1.2.0", "1.10.0") < 0);
  assert.ok(compareSemVer("1.0.0", "1.0.0-beta.1") > 0);
  assert.ok(compareSemVer("1.0.0-beta.2", "1.0.0-beta.10") < 0);
  assert.equal(compareSemVer("1.0.0", "1.0.0"), 0);
});

test("highestVersion findet die per SemVer hoechste Version, unabhaengig von der Reihenfolge im Array", () => {
  const versions = [{ version: "1.2.0" }, { version: "1.10.0" }, { version: "1.9.0" }];
  assert.equal(highestVersion(versions), "1.10.0");
});

test("mergeRegistryEntry: neues Paket ohne vorhandenen Eintrag", () => {
  const versionEntry = {
    version: "1.0.0",
    vpkgUrl: "https://github.com/a/b/releases/download/v1.0.0/a.b-1.0.0.vpkg",
    sha256: "a".repeat(64),
    signature: "c2lnbmF0dXI=",
    size: 100,
    publishedAt: "2026-01-01T00:00:00.000Z"
  };
  const meta = { id: "a.b", name: "A B", category: "texturen", author: { name: "A" }, repository: "https://github.com/a/b" };

  const doc = mergeRegistryEntry(null, versionEntry, meta);
  assert.equal(doc.schemaVersion, 1);
  assert.equal(doc.id, "a.b");
  assert.deepEqual(doc.versions, [versionEntry]);
  assert.equal(doc.latestVersion, "1.0.0");
  assert.equal(doc.updatedAt, "2026-01-01T00:00:00.000Z");
});

test("mergeRegistryEntry: haengt eine weitere Version an ein bestehendes Dokument an, ohne das Original zu mutieren", () => {
  const existing = {
    schemaVersion: 1,
    id: "a.b",
    name: "A B",
    category: "texturen",
    author: { name: "A" },
    repository: "https://github.com/a/b",
    versions: [
      {
        version: "1.0.0",
        vpkgUrl: "https://github.com/a/b/releases/download/v1.0.0/a.b-1.0.0.vpkg",
        sha256: "a".repeat(64),
        signature: "c2ln",
        size: 100,
        publishedAt: "2026-01-01T00:00:00.000Z"
      }
    ],
    latestVersion: "1.0.0",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
  const originalJson = JSON.stringify(existing);

  const newVersion = {
    version: "1.1.0",
    vpkgUrl: "https://github.com/a/b/releases/download/v1.1.0/a.b-1.1.0.vpkg",
    sha256: "b".repeat(64),
    signature: "c2ln2",
    size: 120,
    publishedAt: "2026-02-01T00:00:00.000Z"
  };
  const meta = { id: "a.b", name: "A B", category: "texturen", author: { name: "A" }, repository: "https://github.com/a/b" };

  const doc = mergeRegistryEntry(existing, newVersion, meta);
  assert.equal(doc.versions.length, 2);
  assert.equal(doc.latestVersion, "1.1.0");
  assert.equal(doc.updatedAt, "2026-02-01T00:00:00.000Z");
  assert.equal(JSON.stringify(existing), originalJson, "Eingabeobjekt darf nicht mutiert werden");
});

test("mergeRegistryEntry: eine spaeter veroeffentlichte, aber semantisch aeltere Version darf 'latestVersion' nicht zuruecksetzen", () => {
  const existing = {
    schemaVersion: 1,
    id: "a.b",
    name: "A B",
    category: "texturen",
    author: { name: "A" },
    repository: "https://github.com/a/b",
    versions: [
      {
        version: "2.0.0",
        vpkgUrl: "https://github.com/a/b/releases/download/v2.0.0/a.b-2.0.0.vpkg",
        sha256: "a".repeat(64),
        signature: "c2ln",
        size: 100,
        publishedAt: "2026-02-01T00:00:00.000Z"
      }
    ],
    latestVersion: "2.0.0",
    updatedAt: "2026-02-01T00:00:00.000Z"
  };
  const patchForOldLine = {
    version: "1.0.1",
    vpkgUrl: "https://github.com/a/b/releases/download/v1.0.1/a.b-1.0.1.vpkg",
    sha256: "b".repeat(64),
    signature: "c2ln2",
    size: 90,
    publishedAt: "2026-03-01T00:00:00.000Z"
  };
  const meta = { id: "a.b", name: "A B", category: "texturen", author: { name: "A" }, repository: "https://github.com/a/b" };

  const doc = mergeRegistryEntry(existing, patchForOldLine, meta);
  assert.equal(doc.latestVersion, "2.0.0", "2.0.0 bleibt hoechste Version trotz spaeter veroeffentlichtem 1.0.1");
  assert.equal(doc.updatedAt, "2026-03-01T00:00:00.000Z", "updatedAt spiegelt trotzdem den letzten Aenderungszeitpunkt");
});

test("mergeRegistryEntry lehnt eine bereits vorhandene Version desselben Pakets ab", () => {
  const existing = {
    schemaVersion: 1,
    id: "a.b",
    name: "A B",
    category: "texturen",
    author: { name: "A" },
    repository: "https://github.com/a/b",
    versions: [
      {
        version: "1.0.0",
        vpkgUrl: "https://github.com/a/b/releases/download/v1.0.0/a.b-1.0.0.vpkg",
        sha256: "a".repeat(64),
        signature: "c2ln",
        size: 100,
        publishedAt: "2026-01-01T00:00:00.000Z"
      }
    ],
    latestVersion: "1.0.0",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
  const duplicate = { ...existing.versions[0] };
  const meta = { id: "a.b", name: "A B", category: "texturen", author: { name: "A" }, repository: "https://github.com/a/b" };

  assert.throws(() => mergeRegistryEntry(existing, duplicate, meta), /bereits vorhanden/);
});

test("mergeRegistryEntry lehnt eine id ab, die nicht zum bestehenden Eintrag passt", () => {
  const existing = {
    schemaVersion: 1,
    id: "a.b",
    name: "A B",
    category: "texturen",
    author: { name: "A" },
    repository: "https://github.com/a/b",
    versions: [],
    latestVersion: "1.0.0",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
  const versionEntry = {
    version: "1.0.0",
    vpkgUrl: "https://github.com/a/b/releases/download/v1.0.0/a.b-1.0.0.vpkg",
    sha256: "a".repeat(64),
    signature: "c2ln",
    size: 100,
    publishedAt: "2026-01-01T00:00:00.000Z"
  };
  const meta = { id: "c.d", name: "C D", category: "texturen", author: { name: "A" }, repository: "https://github.com/a/b" };

  assert.throws(() => mergeRegistryEntry(existing, versionEntry, meta), /id/);
});
