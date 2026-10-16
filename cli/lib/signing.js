"use strict";

/*
 * Vitrine CLI -- `sign <paket.vpkg>` (D7). Erzeugt/nutzt einen lokalen
 * Ed25519-Schluessel (Node-Bordmittel node:crypto, seit Node 12 nativ, keine
 * neue Abhaengigkeit) und schreibt eine Begleitdatei im Format, das
 * mod/.../VpkgIntegritySidecar.java liest -- das ist die Bruecke zum Mod
 * (H1).
 *
 * Format-Abgleich mit VpkgIntegritySidecar/VpkgIntegrityValidator (Java):
 *   - "sha256": Hex-Kleinbuchstaben der SHA-256-Pruefsumme der vollstaendigen
 *     Dateibytes (Buffer.toString("hex") ist bereits Kleinbuchstaben, wie
 *     Javas HexFormat.of().formatHex()).
 *   - "signature": Base64 der Ed25519-Signatur -- ueber den 32-Byte-SHA-256-
 *     DIGEST, nicht ueber die Dateibytes selbst (siehe
 *     VpkgIntegrityValidator-Klassendoku: "Die Ed25519-Signatur selbst liegt
 *     nicht ueber der ganzen Datei, sondern ueber deren 32-Byte-SHA-256-
 *     Digest"). crypto.sign(null, digestBuffer, privateKey) signiert Ed25519
 *     "pur" (kein Pre-Hash-Parameter noetig/erlaubt) genau die uebergebenen
 *     Bytes als Nachricht -- deckungsgleich mit Javas
 *     Signature.getInstance("Ed25519").update(actualDigest).
 *   - "publicKey": Base64 der X.509-SubjectPublicKeyInfo-DER-Kodierung, exakt
 *     das Format, das Javas KeyFactory.getInstance("Ed25519")
 *     .generatePublic(new X509EncodedKeySpec(bytes)) erwartet
 *     (decodeEd25519PublicKey in VpkgIntegrityValidator.java) und das
 *     PublicKey#getEncoded() unter Java ohnehin liefert.
 *   - Dateiname der Begleitdatei: "<paket.vpkg>" + ".integrity.json", exakt
 *     VpkgIntegritySidecar.FILE_SUFFIX/sidecarPathFor().
 *
 * Schluesselablage: NIE im Repo (Charter/ALLOWED-REMOTES.md). Default
 * "%USERPROFILE%\.vitrine\signing-key" (Windows) bzw. "~/.vitrine/signing-key"
 * (POSIX) -- ueberschreibbar per VITRINE_SIGNING_KEY_DIR (nur fuer Tests).
 */

const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");

const SIDECAR_SUFFIX = ".integrity.json";

function defaultKeyDir() {
  return process.env.VITRINE_SIGNING_KEY_DIR || path.join(os.homedir(), ".vitrine");
}

function keyPaths(keyDir) {
  return {
    privateKeyPath: path.join(keyDir, "signing-key"),
    publicKeyPath: path.join(keyDir, "signing-key.pub")
  };
}

/**
 * Liefert das lokale Ed25519-Schluesselpaar aus `keyDir` -- erzeugt es beim
 * ersten Aufruf frisch, danach wird derselbe Schluessel wiederverwendet
 * (jedes weitere signierte Paket bleibt einem Autor zuordenbar).
 */
function ensureKeyPair(keyDir) {
  const dir = keyDir || defaultKeyDir();
  const { privateKeyPath, publicKeyPath } = keyPaths(dir);

  if (fs.existsSync(privateKeyPath)) {
    const privatePem = fs.readFileSync(privateKeyPath, "utf8");
    const privateKey = crypto.createPrivateKey(privatePem);
    return { privateKey, privateKeyPath, publicKeyPath, generated: false, keyDir: dir };
  }

  const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519");
  fs.mkdirSync(dir, { recursive: true });

  const privatePem = privateKey.export({ type: "pkcs8", format: "pem" });
  const publicPem = publicKey.export({ type: "spki", format: "pem" });

  fs.writeFileSync(privateKeyPath, privatePem, { mode: 0o600 });
  try {
    // Best effort -- unter Windows setzt chmod keine echte ACL, schadet aber nicht.
    fs.chmodSync(privateKeyPath, 0o600);
  } catch {
    // ignorieren: Plattform ohne POSIX-Berechtigungen
  }
  fs.writeFileSync(publicKeyPath, publicPem, { mode: 0o644 });

  return { privateKey, privateKeyPath, publicKeyPath, generated: true, keyDir: dir };
}

/** Pfad der Begleitdatei zu `vpkgPath`, exakt wie VpkgIntegritySidecar.sidecarPathFor(). */
function sidecarPathFor(vpkgPath) {
  return vpkgPath + SIDECAR_SUFFIX;
}

/**
 * Signiert `vpkgPath` und schreibt die Begleitdatei. Liefert
 * {sidecarPath, sidecar, keyInfo}.
 */
function signFile(vpkgPath, keyDir) {
  if (!fs.existsSync(vpkgPath)) {
    throw new Error("Datei '" + vpkgPath + "' existiert nicht");
  }
  const keyInfo = ensureKeyPair(keyDir);
  const fileBytes = fs.readFileSync(vpkgPath);
  const digest = crypto.createHash("sha256").update(fileBytes).digest(); // 32 rohe Bytes
  const signature = crypto.sign(null, digest, keyInfo.privateKey);
  const publicKey = crypto.createPublicKey(keyInfo.privateKey);
  const publicKeyDer = publicKey.export({ type: "spki", format: "der" });

  const sidecar = {
    sha256: digest.toString("hex"),
    signature: signature.toString("base64"),
    publicKey: publicKeyDer.toString("base64")
  };

  const sidecarPath = sidecarPathFor(vpkgPath);
  fs.writeFileSync(sidecarPath, JSON.stringify(sidecar, null, 2) + "\n", "utf8");

  return { sidecarPath, sidecar, keyInfo };
}

module.exports = { defaultKeyDir, keyPaths, ensureKeyPair, sidecarPathFor, signFile, SIDECAR_SUFFIX };
