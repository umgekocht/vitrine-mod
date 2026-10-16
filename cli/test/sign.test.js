"use strict";

/*
 * Testet cli/lib/signing.js gegen das Format, das
 * mod/.../VpkgIntegritySidecar.java liest (sha256/signature/publicKey) --
 * inklusive einer echten Ed25519-Verifikation mit node:crypto (unabhaengig
 * von cli/lib/signing.js#signFile nachgebaut, damit der Test nicht denselben
 * Fehler zweimal machen kann). Die Java-seitige Interoperabilitaet (X.509-
 * SPKI-Schluessel, Signatur ueber den rohen 32-Byte-Digest) wurde manuell
 * mit einem eigenstaendigen java.security-Programm gegengeprueft (siehe
 * PROGRESS.md/Antwort dieses Zeilenauftrags) -- ohne Gradle-Abhaengigkeit
 * innerhalb dieses Tests, weil mod/ nicht Sache von D7 ist.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");

const { signFile, ensureKeyPair, sidecarPathFor } = require("../lib/signing.js");

function makeTempDir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

test("signFile erzeugt eine Begleitdatei mit sha256/signature/publicKey, die mit node:crypto unabhaengig verifizierbar ist", () => {
  const keyDir = makeTempDir("vitrine-cli-sign-keys-");
  const workDir = makeTempDir("vitrine-cli-sign-pkg-");
  const vpkgPath = path.join(workDir, "beispiel.vpkg");
  fs.writeFileSync(vpkgPath, "vollkommen-plausible-vpkg-bytes-fuer-den-test", "utf8");

  const { sidecarPath, sidecar } = signFile(vpkgPath, keyDir);

  assert.equal(sidecarPath, sidecarPathFor(vpkgPath));
  assert.equal(sidecarPath, vpkgPath + ".integrity.json", "Dateiname muss exakt VpkgIntegritySidecar.FILE_SUFFIX entsprechen");
  assert.ok(fs.existsSync(sidecarPath));

  const onDisk = JSON.parse(fs.readFileSync(sidecarPath, "utf8"));
  assert.deepEqual(Object.keys(onDisk).sort(), ["publicKey", "sha256", "signature"]);
  assert.deepEqual(onDisk, sidecar);

  // sha256: 64 Hex-Kleinbuchstaben, muss zur tatsaechlichen Datei passen.
  assert.match(onDisk.sha256, /^[0-9a-f]{64}$/);
  const actualDigest = crypto.createHash("sha256").update(fs.readFileSync(vpkgPath)).digest();
  assert.equal(onDisk.sha256, actualDigest.toString("hex"));

  // Unabhaengige Verifikation: eigener Aufbau des PublicKey-Objekts aus der
  // DER-Kodierung, eigener Aufruf von crypto.verify -- nutzt nicht
  // signFile()s eigene Rueckgabe der bereits konstruierten Schluesselobjekte.
  const publicKeyDer = Buffer.from(onDisk.publicKey, "base64");
  const publicKey = crypto.createPublicKey({ key: publicKeyDer, format: "der", type: "spki" });
  const signature = Buffer.from(onDisk.signature, "base64");
  const valid = crypto.verify(null, actualDigest, publicKey, signature);
  assert.equal(valid, true, "Ed25519-Signatur muss ueber den SHA-256-Digest der Datei gueltig sein");

  // Signatur ueber die ROHEN Dateibytes (statt des Digests) darf NICHT
  // gueltig sein -- sonst waere versehentlich die Datei statt des Digests signiert.
  const invalid = crypto.verify(null, fs.readFileSync(vpkgPath), publicKey, signature);
  assert.equal(invalid, false);

  fs.rmSync(keyDir, { recursive: true, force: true });
  fs.rmSync(workDir, { recursive: true, force: true });
});

test("ensureKeyPair erzeugt den Schluessel nur beim ersten Mal, verwendet ihn danach wieder", () => {
  const keyDir = makeTempDir("vitrine-cli-sign-reuse-");

  const first = ensureKeyPair(keyDir);
  assert.equal(first.generated, true);
  const firstPrivatePem = fs.readFileSync(first.privateKeyPath, "utf8");

  const second = ensureKeyPair(keyDir);
  assert.equal(second.generated, false);
  const secondPrivatePem = fs.readFileSync(second.privateKeyPath, "utf8");

  assert.equal(firstPrivatePem, secondPrivatePem, "zweiter Aufruf muss denselben Schluessel wiederverwenden");

  fs.rmSync(keyDir, { recursive: true, force: true });
});

test("signFile: zwei Pakete desselben Autors tragen denselben oeffentlichen Schluessel", () => {
  const keyDir = makeTempDir("vitrine-cli-sign-samekey-");
  const workDir = makeTempDir("vitrine-cli-sign-samekey-pkg-");
  const a = path.join(workDir, "a.vpkg");
  const b = path.join(workDir, "b.vpkg");
  fs.writeFileSync(a, "paket-a", "utf8");
  fs.writeFileSync(b, "paket-b", "utf8");

  const resultA = signFile(a, keyDir);
  const resultB = signFile(b, keyDir);
  assert.equal(resultA.sidecar.publicKey, resultB.sidecar.publicKey);
  assert.notEqual(resultA.sidecar.sha256, resultB.sidecar.sha256);
  assert.notEqual(resultA.sidecar.signature, resultB.sidecar.signature);

  fs.rmSync(keyDir, { recursive: true, force: true });
  fs.rmSync(workDir, { recursive: true, force: true });
});

test("sign: nicht existierendes Paket liefert eine klare Fehlermeldung", () => {
  const keyDir = makeTempDir("vitrine-cli-sign-missing-");
  assert.throws(() => signFile(path.join(keyDir, "existiert-nicht.vpkg"), keyDir), /existiert nicht/);
  fs.rmSync(keyDir, { recursive: true, force: true });
});
