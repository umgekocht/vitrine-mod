# Security

Vitrine installs content made by other people, so safety is built in at every
step. This page explains what protects you.

## Two classes of content

| | Class A | Class B |
|---|---|---|
| What | Textures, themes, HUD, shaders, sounds, languages, cosmetics, data packs, add-ons | Java mods (`.jar`) |
| Runs code? | No (add-ons only inside the sandbox) | Yes, with full rights |
| Restart | Never | Always |
| Default | On | Shown in the store, every install confirmed separately |

Java mods are clearly labelled *Restart required*, and the install dialog warns
that a mod runs with full rights. You can hide them completely in *Settings →
Show Java mods*.

## Checks before anything is applied

Every `.vpkg` is checked **before** it is applied:

- **Zip slip**: absolute paths, `..`, symlinks
- **Zip bombs**: total size, compression ratio, number of entries
- **File types**: only known resource types; no executables, scripts outside
  `scripts/`, or archives inside archives
- **Images**: PNG dimensions checked before decoding
- **SHA-256 checksum** against the catalog entry
- **Ed25519 signature** of the author

If something fails, nothing is installed and you get a plain-language message.

## Permissions are an allow-list

Each package declares capabilities (`texturen`, `theme`, `hud`, `shader`,
`sound`, `sprache`, `cosmetics`, `skript`, …). The default is **deny**. Before
the first install you see exactly those permissions and approve them.

## Add-on sandbox

Lua and JavaScript add-ons cannot touch files, the operating system, the network
or Java classes. Every call has a time limit, endless loops are stopped, and an
add-on that keeps failing is paused. Details: [addons.md](addons.md#5-security-model).

## Automatic rollback

If a resource reload fails after a package was switched on, Vitrine switches it
off again, reloads, and tells you why. The game stays usable.

## Modrinth downloads

Files from Modrinth are verified against the SHA-512 hash Modrinth publishes.
Required dependencies of a mod are fetched only if *Install dependencies* is
on.

## Nothing is deleted outright

Uninstalled packages go to `config/vitrine/trash/`, so you can recover them.

## Reporting a vulnerability

Please open a private security advisory on GitHub
(*Security → Report a vulnerability*) instead of a public issue.
