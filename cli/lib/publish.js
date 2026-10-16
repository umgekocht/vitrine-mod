"use strict";

/*
 * Vitrine CLI -- `publish <paket-ordner-oder-vpkg>` (D9). Traegt ein bereits
 * gebautes und signiertes .vpkg in den Katalog `vitrine-registry` ein, indem
 * es "packages/<id>.json" dort per Pull Request anlegt/aktualisiert -- genau
 * der von Hand zu erledigende Teil, den
 * templates/package-repo/package-repo/.github/workflows/release.yml am Ende
 * optional automatisiert (dort per REGISTRY_PAT-Secret in einer GitHub
 * Action). `cli publish` macht denselben letzten Schritt lokal, ueber die
 * bereits angemeldete `gh`-CLI des Autors -- KEIN eigenes Token, keine
 * eigene Secret-Ablage (Charter-Regel 10, ALLOWED-REMOTES.md).
 *
 * -- Design-Entscheidung: woher kommt vpkgUrl? --------------------------
 * `cli publish` baut selbst KEIN GitHub-Release (das bleibt Sache von
 * `templates/package-repo`/D8 oder der Handarbeit des Autors: taggen, Release
 * mit dem .vpkg als Asset anlegen). Es gibt keine GitHub-Web-API, die aus
 * einer lokalen Datei einen Download-Link "erraet" -- stattdessen verlangt
 * `publish` zwei Angaben vom Aufrufer per Flag:
 *   --repo  https://github.com/<besitzer>/<repo>   (das Autoren-Repo mit dem Release)
 *   --tag   <tag>                                  (der bereits existierende Release-Tag)
 * Der Name des Release-Assets ist per Konvention der Dateiname des lokal
 * uebergebenen .vpkg (`path.basename(vpkgPath)`) -- exakt der Name, unter dem
 * es hochgeladen sein muss (wie in release.yml: "$VPKG"). Daraus ergibt sich
 * die tatsaechliche GitHub-Release-Asset-URL nach dem festen, von GitHub
 * selbst dokumentierten Muster
 * "https://github.com/<besitzer>/<repo>/releases/download/<tag>/<dateiname>"
 * (dieselbe Formel wie in release.yml: `$REPO_URL/releases/download/
 * $GITHUB_REF_NAME/$VPKG`). Im ECHTEN Lauf (nicht --dry-run) wird diese
 * Annahme vor dem Schreiben zusaetzlich mit der vorhandenen `gh`-CLI
 * gegengeprueft (`gh release view <tag> --repo <besitzer>/<repo> --json
 * assets`, rein lesend) -- das Asset muss dort tatsaechlich existieren, sonst
 * bricht `publish` ab, statt einen erfundenen Link einzutragen (Charter-Regel
 * 2: nichts vorgaukeln). --dry-run macht diesen (oder jeden anderen)
 * Netzwerkzugriff bewusst NICHT, wie im Fertig-Kriterium gefordert.
 *
 * -- Ablauf --------------------------------------------------------------
 * 1. Manifest lesen (aus dem Projektordner ODER direkt aus dem .vpkg, siehe
 *    resolvePackageSource()).
 * 2. Versions-Eintrag bauen: sha256/size aus den .vpkg-Bytes, Signatur aus
 *    der vorhandenen `<vpkg>.integrity.json`-Begleitdatei (cli/lib/
 *    signing.js) -- OHNE Begleitdatei bricht publish ab: registry-
 *    package.schema.json verlangt "signature" als Pflichtfeld je Version.
 * 3. --dry-run: zeigt den geplanten packages/<id>.json-Inhalt, Branchnamen,
 *    PR-Titel/-Text -- ohne jeden Netzwerkzugriff.
 *    ohne --dry-run: `gh release view` (lesend, s.o.), `gh repo clone`,
 *    Datei schreiben, gegen registry-package.schema.json pruefen, commit,
 *    `git push`, `gh pr create`.
 */

const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");

const { sidecarPathFor } = require("./signing.js");
const { readManifestFromVpkg } = require("./zip-reader.js");
const { validateManifest } = require("./schema.js");
const { validateRegistryEntry, REGISTRY_PACKAGE_SCHEMA_PATH } = require("./registry-schema.js");
const { mergeRegistryEntry } = require("./registry-entry.js");
const { MANIFEST_ENTRY_NAME } = require("./pack.js");

const DEFAULT_REGISTRY_SLUG = "umgekocht/vitrine-registry";
const DEFAULT_BASE_BRANCH = "main";

/** Node-Bordmittel-Ausfuehrung eines Kindprozesses, Rueckgabe: stdout als String. Ersetzbar fuer Tests. */
function defaultExec(command, args, options) {
  return execFileSync(command, args, Object.assign({ encoding: "utf8" }, options));
}

/**
 * Findet Manifest + .vpkg-Pfad aus dem einen Aufrufargument: entweder direkt
 * ein Pfad auf ein .vpkg (Manifest wird daraus gelesen, siehe zip-reader.js),
 * oder ein Projektordner mit vitrine.json UND genau einer *.vpkg-Datei direkt
 * darin (der Ausgabe von `cli pack`).
 */
function resolvePackageSource(sourcePath) {
  if (!fs.existsSync(sourcePath)) {
    throw new Error("'" + sourcePath + "' existiert nicht");
  }
  const stat = fs.statSync(sourcePath);

  if (stat.isFile()) {
    if (!sourcePath.toLowerCase().endsWith(".vpkg")) {
      throw new Error("'" + sourcePath + "' ist eine Datei, aber keine .vpkg -- erwartet wird ein Projektordner oder ein gebautes .vpkg");
    }
    const manifest = readManifestFromVpkg(sourcePath);
    return { manifest, vpkgPath: sourcePath };
  }

  const manifestPath = path.join(sourcePath, MANIFEST_ENTRY_NAME);
  if (!fs.existsSync(manifestPath)) {
    throw new Error("'" + manifestPath + "' existiert nicht -- kein " + MANIFEST_ENTRY_NAME + " im Projektordner");
  }
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));

  const candidates = fs
    .readdirSync(sourcePath, { withFileTypes: true })
    .filter((e) => e.isFile() && e.name.toLowerCase().endsWith(".vpkg"))
    .map((e) => path.join(sourcePath, e.name));

  if (candidates.length === 0) {
    throw new Error(
      "kein .vpkg direkt in '" + sourcePath + "' gefunden -- erst mit 'node cli/vitrine.js pack " + sourcePath +
        " " + path.join(sourcePath, manifest.id + "-" + manifest.version + ".vpkg") + "' bauen und signieren."
    );
  }
  if (candidates.length > 1) {
    throw new Error(
      "mehrere .vpkg-Dateien in '" + sourcePath + "' gefunden (" + candidates.map((c) => path.basename(c)).join(", ") +
        ") -- genauen Pfad statt des Ordners uebergeben."
    );
  }
  return { manifest, vpkgPath: candidates[0] };
}

function repoSlugFromUrl(repoUrl) {
  const match = /^https:\/\/github\.com\/([^\/\s]+)\/([^\/\s]+?)(?:\.git)?\/?$/.exec((repoUrl || "").trim());
  if (!match) {
    throw new Error("--repo muss eine https://github.com/<besitzer>/<repo>-URL sein, erhalten: '" + repoUrl + "'");
  }
  return match[1] + "/" + match[2];
}

function buildVpkgUrl(repoUrl, tag, assetName) {
  return repoUrl.replace(/\/+$/, "") + "/releases/download/" + tag + "/" + assetName;
}

/**
 * Prueft (lesend, per `gh release view`) dass `assetName` tatsaechlich am
 * Release `tag` in `repoSlug` haengt. Wird NUR im echten Lauf aufgerufen,
 * nie bei --dry-run.
 */
function verifyReleaseAsset(repoSlug, tag, assetName, exec) {
  let raw;
  try {
    raw = exec("gh", ["release", "view", tag, "--repo", repoSlug, "--json", "assets"]);
  } catch (e) {
    throw new Error(
      "Release '" + tag + "' in '" + repoSlug + "' konnte nicht gelesen werden (" + e.message +
        ") -- existiert der Tag als GitHub-Release? (siehe templates/package-repo, D8)"
    );
  }
  let data;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    throw new Error("Antwort von 'gh release view' war kein JSON: " + e.message);
  }
  const names = (data.assets || []).map((a) => a.name);
  if (!names.includes(assetName)) {
    throw new Error(
      "Release '" + tag + "' in '" + repoSlug + "' hat kein Asset '" + assetName + "'. Vorhandene Assets: " +
        (names.length ? names.join(", ") : "(keine)") + "."
    );
  }
}

/**
 * Baut den versions[]-Eintrag: liest sha256/size aus den .vpkg-Bytes und die
 * Signatur aus der Begleitdatei von `sign` (cli/lib/signing.js). Wirft, wenn
 * unsigniert oder wenn die Begleitdatei zu einem anderen Dateistand gehoert.
 */
function buildVersionEntry({ manifest, vpkgPath, vpkgUrl, now }) {
  const bytes = fs.readFileSync(vpkgPath);
  const sha256 = crypto.createHash("sha256").update(bytes).digest("hex");

  const sidecarPath = sidecarPathFor(vpkgPath);
  if (!fs.existsSync(sidecarPath)) {
    throw new Error(
      "'" + vpkgPath + "' ist nicht signiert (keine Begleitdatei '" + sidecarPath + "') -- " +
        "registry-package.schema.json verlangt eine Signatur je Version. Erst 'node cli/vitrine.js sign " +
        vpkgPath + "' ausfuehren."
    );
  }
  const sidecar = JSON.parse(fs.readFileSync(sidecarPath, "utf8"));
  if (sidecar.sha256 !== sha256) {
    throw new Error(
      "'" + sidecarPath + "' passt nicht mehr zu '" + vpkgPath + "' (sha256 weicht ab) -- " +
        "erneut mit 'sign' signieren, nachdem das Paket zuletzt gebaut wurde."
    );
  }

  return {
    version: manifest.version,
    vpkgUrl,
    sha256,
    signature: sidecar.signature,
    size: bytes.length,
    publishedAt: now || new Date().toISOString()
  };
}

function buildMeta(manifest, repoUrl, extra) {
  const meta = {
    id: manifest.id,
    name: manifest.name,
    category: manifest.category,
    author: { name: manifest.author },
    repository: repoUrl
  };
  if (manifest.description) meta.description = manifest.description;
  if (manifest.license) meta.license = manifest.license;
  if (extra) Object.assign(meta, extra);
  return meta;
}

function branchName(id, version) {
  return "publish-" + id.replace(/[^a-z0-9.-]/gi, "-") + "-" + version;
}

function prTitle(id, version) {
  return "Paket " + id + " " + version;
}

function prBody(id, repoUrl, tag) {
  return (
    "Veroeffentlicht mit `cli/vitrine.js publish` aus " + repoUrl + " (Tag " + tag + "). " +
    "Bitte Autor-Name/-URL und Kategorie vor dem Mergen pruefen -- siehe CONTRIBUTING.md."
  );
}

/**
 * Baut den vollstaendigen Plan (Manifest lesen, Versions-Eintrag, Doc-Merge,
 * Branch-/PR-Text) OHNE jeden Netzwerk- oder git-Zugriff. Wird sowohl vom
 * --dry-run-Pfad als auch (nach der `gh release view`-Pruefung) vom echten
 * Lauf verwendet, damit beide exakt denselben Dokumentinhalt berechnen.
 */
function buildPlan({ sourcePath, repoUrl, tag, assetName, existingDoc, now, meta: metaOverrides }) {
  const { manifest, vpkgPath } = resolvePackageSource(sourcePath);

  const manifestErrors = validateManifest(manifest);
  if (manifestErrors.length > 0) {
    throw new Error("Manifest in '" + sourcePath + "' verletzt registry/schema/vitrine.schema.json:\n  - " + manifestErrors.join("\n  - "));
  }

  const repoSlug = repoSlugFromUrl(repoUrl);
  const effectiveAssetName = assetName || path.basename(vpkgPath);
  const vpkgUrl = buildVpkgUrl(repoUrl, tag, effectiveAssetName);

  const versionEntry = buildVersionEntry({ manifest, vpkgPath, vpkgUrl, now });
  const meta = buildMeta(manifest, repoUrl, metaOverrides);
  const doc = mergeRegistryEntry(existingDoc || null, versionEntry, meta);

  const entryErrors = validateRegistryEntry(doc);
  if (entryErrors.length > 0) {
    throw new Error(
      "geplanter Eintrag verletzt " + path.basename(REGISTRY_PACKAGE_SCHEMA_PATH) + ":\n  - " + entryErrors.join("\n  - ")
    );
  }

  return {
    manifest,
    vpkgPath,
    repoSlug,
    assetName: effectiveAssetName,
    vpkgUrl,
    versionEntry,
    doc,
    entryPath: path.join("packages", manifest.id + ".json"),
    branch: branchName(manifest.id, manifest.version),
    prTitle: prTitle(manifest.id, manifest.version),
    prBody: prBody(manifest.id, repoUrl, tag)
  };
}

/** --dry-run: baut den Plan (kein Netz, keine `gh release view`-Pruefung -- absichtlich, siehe Kopfkommentar). */
function planDryRun(options) {
  return buildPlan(options);
}

/**
 * Fuehrt den echten Lauf aus: `gh release view` (lesend), `gh repo clone`,
 * Datei schreiben+pruefen, commit, push, `gh pr create`.
 */
function publishReal({ sourcePath, repoUrl, tag, assetName, registrySlug, base, exec, now }) {
  const effectiveExec = exec || defaultExec;
  const effectiveRegistrySlug = registrySlug || DEFAULT_REGISTRY_SLUG;
  const effectiveBase = base || DEFAULT_BASE_BRANCH;

  const repoSlug = repoSlugFromUrl(repoUrl);
  const { manifest, vpkgPath } = resolvePackageSource(sourcePath);
  const effectiveAssetName = assetName || path.basename(vpkgPath);

  verifyReleaseAsset(repoSlug, tag, effectiveAssetName, effectiveExec);

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), "vitrine-publish-"));
  const checkoutDir = path.join(workDir, "vitrine-registry");
  try {
    effectiveExec("gh", ["repo", "clone", effectiveRegistrySlug, checkoutDir, "--", "--depth", "1"]);

    const entryPath = path.join(checkoutDir, "packages", manifest.id + ".json");
    const existingDoc = fs.existsSync(entryPath) ? JSON.parse(fs.readFileSync(entryPath, "utf8")) : null;

    const plan = buildPlan({ sourcePath, repoUrl, tag, assetName: effectiveAssetName, existingDoc, now });

    fs.mkdirSync(path.dirname(entryPath), { recursive: true });
    fs.writeFileSync(entryPath, JSON.stringify(plan.doc, null, 2) + "\n", "utf8");

    effectiveExec("git", ["config", "user.name", "vitrine-cli-publish"], { cwd: checkoutDir });
    effectiveExec("git", ["config", "user.email", "actions@users.noreply.github.com"], { cwd: checkoutDir });
    effectiveExec("git", ["checkout", "-b", plan.branch], { cwd: checkoutDir });
    effectiveExec("git", ["add", path.join("packages", manifest.id + ".json")], { cwd: checkoutDir });
    effectiveExec("git", ["commit", "-m", plan.prTitle + " hinzufuegen"], { cwd: checkoutDir });
    effectiveExec("git", ["push", "origin", plan.branch], { cwd: checkoutDir });

    effectiveExec("gh", [
      "pr", "create",
      "--repo", effectiveRegistrySlug,
      "--title", plan.prTitle,
      "--body", plan.prBody,
      "--head", plan.branch,
      "--base", effectiveBase
    ]);

    return plan;
  } finally {
    fs.rmSync(workDir, { recursive: true, force: true });
  }
}

module.exports = {
  DEFAULT_REGISTRY_SLUG,
  DEFAULT_BASE_BRANCH,
  resolvePackageSource,
  repoSlugFromUrl,
  buildVpkgUrl,
  verifyReleaseAsset,
  buildVersionEntry,
  buildMeta,
  branchName,
  buildPlan,
  planDryRun,
  publishReal,
  defaultExec
};
