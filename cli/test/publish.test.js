"use strict";

/*
 * Testet cli/lib/publish.js (D9): den --dry-run-Pfad vollstaendig ohne
 * jeglichen Netzzugriff (kein `exec`-Parameter existiert dafuer ueberhaupt),
 * und den echten Lauf (publishReal) mit einer injizierten `exec`-Funktion,
 * die "gh"/"git" NICHT wirklich aufruft, sondern ein geklontes
 * vitrine-registry-Checkout auf der lokalen Platte simuliert (kein Netz in
 * diesem Test -- der EINE echte Lauf gegen das echte vitrine-registry-Repo
 * ist ein separater, von Hand ausgefuehrter Beweis, siehe PROGRESS.md).
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const { packDirectory } = require("../lib/pack.js");
const { signFile } = require("../lib/signing.js");
const {
  resolvePackageSource,
  repoSlugFromUrl,
  buildVpkgUrl,
  verifyReleaseAsset,
  planDryRun,
  publishReal
} = require("../lib/publish.js");
const { validateRegistryEntry } = require("../lib/registry-schema.js");

function makeTempDir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function buildSignedFixture({ id, version } = {}) {
  const root = makeTempDir("vitrine-cli-publish-fixture-");
  const projectDir = path.join(root, "proj");
  fs.mkdirSync(projectDir);
  const manifest = {
    schemaVersion: 1,
    id: id || "demo.publish-fixture",
    name: "Publish Fixture",
    version: version || "1.0.0",
    category: "texturen",
    author: "Fixture Autor",
    license: "CC0-1.0",
    description: "Testpaket fuer publish.test.js"
  };
  fs.writeFileSync(path.join(projectDir, "vitrine.json"), JSON.stringify(manifest, null, 2), "utf8");

  const vpkgPath = path.join(projectDir, manifest.id + "-" + manifest.version + ".vpkg");
  packDirectory(projectDir, vpkgPath);

  const keyDir = path.join(root, "keys");
  signFile(vpkgPath, keyDir);

  return { root, projectDir, vpkgPath, manifest };
}

test("resolvePackageSource: direkter .vpkg-Pfad liest das Manifest aus dem Archiv", () => {
  const { vpkgPath, manifest, root } = buildSignedFixture();
  const resolved = resolvePackageSource(vpkgPath);
  assert.deepEqual(resolved.manifest, manifest);
  assert.equal(resolved.vpkgPath, vpkgPath);
  fs.rmSync(root, { recursive: true, force: true });
});

test("resolvePackageSource: Projektordner mit genau einem .vpkg", () => {
  const { projectDir, manifest, root } = buildSignedFixture();
  const resolved = resolvePackageSource(projectDir);
  assert.deepEqual(resolved.manifest, manifest);
  fs.rmSync(root, { recursive: true, force: true });
});

test("resolvePackageSource: Projektordner ohne .vpkg liefert eine klare Fehlermeldung", () => {
  const root = makeTempDir("vitrine-cli-publish-nofixture-");
  fs.writeFileSync(path.join(root, "vitrine.json"), JSON.stringify({ id: "a.b", version: "1.0.0" }), "utf8");
  assert.throws(() => resolvePackageSource(root), /kein \.vpkg/);
  fs.rmSync(root, { recursive: true, force: true });
});

test("resolvePackageSource: Projektordner mit mehreren .vpkg-Dateien ist mehrdeutig", () => {
  const { projectDir, root } = buildSignedFixture();
  fs.writeFileSync(path.join(projectDir, "zweites.vpkg"), "irgendwas", "utf8");
  assert.throws(() => resolvePackageSource(projectDir), /mehrere \.vpkg/);
  fs.rmSync(root, { recursive: true, force: true });
});

test("repoSlugFromUrl: akzeptiert https://github.com/<besitzer>/<repo>, lehnt alles andere ab", () => {
  assert.equal(repoSlugFromUrl("https://github.com/umgekocht/demo-repo"), "umgekocht/demo-repo");
  assert.equal(repoSlugFromUrl("https://github.com/umgekocht/demo-repo/"), "umgekocht/demo-repo");
  assert.equal(repoSlugFromUrl("https://github.com/umgekocht/demo-repo.git"), "umgekocht/demo-repo");
  assert.throws(() => repoSlugFromUrl("git@github.com:umgekocht/demo-repo.git"), /https:\/\/github\.com/);
  assert.throws(() => repoSlugFromUrl(""), /https:\/\/github\.com/);
});

test("buildVpkgUrl: feste GitHub-Release-Asset-Formel", () => {
  assert.equal(
    buildVpkgUrl("https://github.com/umgekocht/demo-repo", "v1.0.0", "demo.publish-fixture-1.0.0.vpkg"),
    "https://github.com/umgekocht/demo-repo/releases/download/v1.0.0/demo.publish-fixture-1.0.0.vpkg"
  );
});

test("verifyReleaseAsset: wirft, wenn das Asset laut 'gh release view' nicht existiert", () => {
  const fakeExec = () => JSON.stringify({ assets: [{ name: "anderes-asset.vpkg" }] });
  assert.throws(
    () => verifyReleaseAsset("umgekocht/demo-repo", "v1.0.0", "demo.publish-fixture-1.0.0.vpkg", fakeExec),
    /kein Asset/
  );
});

test("verifyReleaseAsset: laesst passieren, wenn das Asset in der Antwort steckt", () => {
  const fakeExec = () => JSON.stringify({ assets: [{ name: "demo.publish-fixture-1.0.0.vpkg" }] });
  assert.doesNotThrow(() => verifyReleaseAsset("umgekocht/demo-repo", "v1.0.0", "demo.publish-fixture-1.0.0.vpkg", fakeExec));
});

test("planDryRun baut einen vollstaendigen, schema-gueltigen Plan OHNE dass irgendein Netzwerkaufruf noetig ist", () => {
  const { projectDir, manifest, vpkgPath, root } = buildSignedFixture();

  const plan = planDryRun({
    sourcePath: projectDir,
    repoUrl: "https://github.com/umgekocht/demo-repo",
    tag: "v1.0.0"
  });

  assert.equal(plan.manifest.id, manifest.id);
  assert.equal(plan.vpkgPath, vpkgPath);
  assert.equal(plan.repoSlug, "umgekocht/demo-repo");
  assert.equal(plan.vpkgUrl, "https://github.com/umgekocht/demo-repo/releases/download/v1.0.0/" + path.basename(vpkgPath));
  assert.equal(plan.doc.versions.length, 1);
  assert.equal(plan.doc.versions[0].version, "1.0.0");
  assert.equal(plan.doc.latestVersion, "1.0.0");
  assert.deepEqual(validateRegistryEntry(plan.doc), []);
  assert.equal(plan.branch, "publish-demo.publish-fixture-1.0.0");

  fs.rmSync(root, { recursive: true, force: true });
});

test("planDryRun wirft, wenn das .vpkg nicht signiert ist (kein Aufruf von 'sign' zuvor)", () => {
  const root = makeTempDir("vitrine-cli-publish-unsigned-");
  const projectDir = path.join(root, "proj");
  fs.mkdirSync(projectDir);
  const manifest = {
    schemaVersion: 1,
    id: "demo.unsigned",
    name: "Unsigniert",
    version: "1.0.0",
    category: "texturen",
    author: "Autor",
    license: "CC0-1.0"
  };
  fs.writeFileSync(path.join(projectDir, "vitrine.json"), JSON.stringify(manifest), "utf8");
  const vpkgPath = path.join(projectDir, "demo.unsigned-1.0.0.vpkg");
  packDirectory(projectDir, vpkgPath);

  assert.throws(
    () => planDryRun({ sourcePath: projectDir, repoUrl: "https://github.com/umgekocht/demo-repo", tag: "v1.0.0" }),
    /nicht signiert/
  );

  fs.rmSync(root, { recursive: true, force: true });
});

test("planDryRun wirft, wenn die Signatur-Begleitdatei zu einem anderen Dateistand gehoert", () => {
  const { projectDir, vpkgPath, root } = buildSignedFixture();
  // Datei nach dem Signieren veraendern, ohne erneut zu signieren.
  fs.appendFileSync(vpkgPath, Buffer.from("veraendert-nach-dem-signieren"));

  assert.throws(
    () => planDryRun({ sourcePath: projectDir, repoUrl: "https://github.com/umgekocht/demo-repo", tag: "v1.0.0" }),
    /passt nicht mehr/
  );

  fs.rmSync(root, { recursive: true, force: true });
});

/**
 * Baut eine Spionage-Version von `exec`, die "gh repo clone" durch das
 * Anlegen eines lokalen Ordners simuliert (optional mit vorbefuellter
 * packages/<id>.json, um den "bereits vorhandenes Paket"-Zweig zu pruefen)
 * und alle anderen Aufrufe (git config/checkout/add/commit/push, gh release
 * view, gh pr create) nur protokolliert -- NIE wirklich ausfuehrt. Kein
 * einziger echter Netzwerk- oder Prozessaufruf in diesem Test.
 */
function makeFakeExec({ existingPackageJson, assetName } = {}) {
  const calls = [];
  function fakeExec(command, args, options) {
    calls.push({ command, args, cwd: options && options.cwd });
    if (command === "gh" && args[0] === "release" && args[1] === "view") {
      return JSON.stringify({ assets: [{ name: assetName }] });
    }
    if (command === "gh" && args[0] === "repo" && args[1] === "clone") {
      // Realer Aufruf in publishReal(): ["repo", "clone", registrySlug, checkoutDir, "--", "--depth", "1"]
      // -- args[2] ist der Registry-Slug-String, der Zielordner steht in args[3].
      const dest = args[3];
      fs.mkdirSync(path.join(dest, "packages"), { recursive: true });
      if (existingPackageJson) {
        fs.writeFileSync(
          path.join(dest, "packages", existingPackageJson.id + ".json"),
          JSON.stringify(existingPackageJson, null, 2) + "\n",
          "utf8"
        );
      }
      return "";
    }
    if (command === "git" || (command === "gh" && args[0] === "pr")) {
      return "";
    }
    throw new Error("unerwarteter Aufruf in diesem Test: " + command + " " + args.join(" "));
  }
  return { fakeExec, calls };
}

test("publishReal: neues Paket -- schreibt packages/<id>.json, committet, pusht, oeffnet PR, OHNE echtes gh/git", () => {
  const { projectDir, manifest, vpkgPath, root } = buildSignedFixture();
  const assetName = path.basename(vpkgPath);
  const { fakeExec, calls } = makeFakeExec({ assetName });

  const plan = publishReal({
    sourcePath: projectDir,
    repoUrl: "https://github.com/umgekocht/demo-repo",
    tag: "v1.0.0",
    exec: fakeExec,
    now: "2026-09-06T00:00:00.000Z"
  });

  assert.equal(plan.doc.id, manifest.id);
  assert.equal(plan.doc.versions.length, 1);
  assert.deepEqual(validateRegistryEntry(plan.doc), []);

  // "git" hat pro Aufruf genau ein Subcommand (checkout/commit/push, gefolgt von
  // Optionen wie "-b"/"-m"/"origin" -- nicht Teil des Befehlsnamens), "gh" braucht
  // dagegen zwei Teile ("release view", "repo clone", "pr create"), um eindeutig zu sein.
  const commandNames = calls.map((c) =>
    c.command === "git" ? c.command + " " + c.args[0] : c.command + " " + c.args.slice(0, 2).join(" ")
  );
  assert.ok(commandNames.some((c) => c === "gh release view"));
  assert.ok(commandNames.some((c) => c === "gh repo clone"));
  assert.ok(commandNames.some((c) => c === "git checkout"));
  assert.ok(commandNames.some((c) => c === "git commit"));
  assert.ok(commandNames.some((c) => c === "git push"));
  assert.ok(commandNames.some((c) => c === "gh pr create"));

  // Reihenfolge: clone kommt vor jedem git-Aufruf, git-Aufrufe kommen vor 'gh pr create'.
  const cloneIdx = calls.findIndex((c) => c.command === "gh" && c.args[0] === "repo");
  const firstGitIdx = calls.findIndex((c) => c.command === "git");
  const prIdx = calls.findIndex((c) => c.command === "gh" && c.args[0] === "pr");
  assert.ok(cloneIdx < firstGitIdx);
  assert.ok(firstGitIdx < prIdx);

  fs.rmSync(root, { recursive: true, force: true });
});

test("publishReal: bestehendes Paket -- haengt eine weitere Version an, statt zu ueberschreiben", () => {
  const { projectDir, manifest, vpkgPath, root } = buildSignedFixture({ id: "demo.existing-pkg", version: "1.1.0" });
  const assetName = path.basename(vpkgPath);
  const existingPackageJson = {
    schemaVersion: 1,
    id: manifest.id,
    name: "Publish Fixture (alt)",
    category: "texturen",
    author: { name: "Fixture Autor" },
    license: "CC0-1.0",
    repository: "https://github.com/umgekocht/demo-repo",
    versions: [
      {
        version: "1.0.0",
        vpkgUrl: "https://github.com/umgekocht/demo-repo/releases/download/v1.0.0/demo.existing-pkg-1.0.0.vpkg",
        sha256: "a".repeat(64),
        signature: "A".repeat(86) + "==",
        size: 10,
        publishedAt: "2026-01-01T00:00:00.000Z"
      }
    ],
    latestVersion: "1.0.0",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
  const { fakeExec } = makeFakeExec({ existingPackageJson, assetName });

  const plan = publishReal({
    sourcePath: projectDir,
    repoUrl: "https://github.com/umgekocht/demo-repo",
    tag: "v1.1.0",
    exec: fakeExec,
    now: "2026-09-06T00:00:00.000Z"
  });

  assert.equal(plan.doc.versions.length, 2);
  assert.equal(plan.doc.versions.map((v) => v.version).sort().join(","), "1.0.0,1.1.0");
  assert.equal(plan.doc.latestVersion, "1.1.0");
  assert.deepEqual(validateRegistryEntry(plan.doc), []);

  fs.rmSync(root, { recursive: true, force: true });
});

test("publishReal: bricht ab, wenn das Release-Asset laut 'gh release view' fehlt -- ohne zu klonen/pushen", () => {
  const { projectDir, root } = buildSignedFixture();
  const { fakeExec, calls } = makeFakeExec({ assetName: "voellig-anderer-name.vpkg" });

  assert.throws(
    () =>
      publishReal({
        sourcePath: projectDir,
        repoUrl: "https://github.com/umgekocht/demo-repo",
        tag: "v1.0.0",
        exec: fakeExec
      }),
    /kein Asset/
  );

  assert.ok(!calls.some((c) => c.command === "gh" && c.args[0] === "repo"), "darf nach fehlgeschlagener Asset-Pruefung nicht klonen");
  assert.ok(!calls.some((c) => c.command === "git"), "darf nach fehlgeschlagener Asset-Pruefung keine git-Kommandos ausfuehren");

  fs.rmSync(root, { recursive: true, force: true });
});
