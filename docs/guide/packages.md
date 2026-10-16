# Texture, HUD, sound and language packages

Everything you can install in Vitrine is a **package**: a `.vpkg` file. A
`.vpkg` is a zip archive with a manifest called `vitrine.json` at its root and
your resources next to it. Vitrine never extracts it; it reads straight from
the zip.

| Category (`category`) | What it changes | Capability |
|---|---|---|
| `texturen` | Block, item, GUI textures (resource-pack layout) | `texturen` |
| `theme` | Look of the Vitrine store, see [themes.md](themes.md) | `theme` |
| `hud` | On-screen widgets (FPS, coordinates, biome, time …) | `hud` |
| `shader` | Post-processing shaders | `shader` |
| `sound` | Sound replacements (`.ogg`) | `sound` |
| `sprache` | Language files | `sprache` |
| `cosmetics` | Cosmetics | `cosmetics` |
| `addon` | Lua / JavaScript add-ons, see [addons.md](addons.md) | `skript` |
| `bundle` | A container of several packages | – |

All of these apply **instantly, without a restart**.

## The manifest

| Field | Example | Rule |
|---|---|---|
| `schemaVersion` | `1` | always `1` |
| `id` | `"anna.clear-stone"` | at least two dot-separated parts; lowercase letters, digits, hyphen; starts with a letter |
| `name` | `"Clear Stone"` | 1–80 characters |
| `version` | `"1.0.0"` | SemVer |
| `category` | `"texturen"` | see table above |
| `author` | `"anna"` | 1–80 characters |

Optional: `description`, `capabilities`, `icon` (a `.png` inside the zip),
`screenshots`, `homepage` (`https://` only), `license` (SPDX id such as
`CC-BY-4.0`). Full field reference:
[`registry/schema/README.md`](../../registry/schema/README.md).

**Capabilities are an allow-list.** A package gets only what it declares, and
the player sees the list before installing. Declare exactly what your package
does, nothing more.

## Layout inside the zip

```
vitrine.json
icon.png
assets/<namespace>/textures/...
assets/<namespace>/sounds/...  + sounds.json
assets/<namespace>/lang/...
hud.json                         (HUD packages)
theme.json                       (theme packages)
```

Use only content you made yourself or are allowed to share. No Mojang assets.

## HUD packages

A HUD package contains a `hud.json` with declarative widgets: text, icon, bar
and list, an anchor (corner or edge), a scale and a data source from a fixed
list (`fps`, `coordinates.x/y/z`, `biome`, `time`, …). There is no formula or
script language in HUD packages; for logic, write an [add-on](addons.md).
Players can move every widget in game with the **HUD editor** (*Settings →
Arrange HUD*). Examples:
[`registry/schema/examples/hud/valid/`](../../registry/schema/examples/hud/valid/).

## Building a package

**With Vitrine Designer** (no install): open `designer/index.html`, fill the
form, add textures or HUD widgets, click *Export package*. See
[designer-and-cli.md](designer-and-cli.md).

**With the CLI** (Node.js 18+):

```
node cli/vitrine.js init my-pack          # creates a skeleton
node cli/vitrine.js validate my-pack
node cli/vitrine.js pack my-pack my-pack.vpkg
node cli/vitrine.js sign my-pack.vpkg
```

Test it by dropping the `.vpkg` into `.minecraft/config/vitrine/library/`.

## Publishing

Vitrine's catalog has no server. Your `.vpkg` lives as a **release asset in
your own GitHub repository**, and the catalog repository
[`umgekocht/vitrine-registry`](https://github.com/umgekocht/vitrine-registry)
contains a small JSON file pointing to it.

### Option A: template repository (easiest)

1. Open `umgekocht/vitrine-package-template` and click **Use this template**.
2. Edit `vitrine.json`, put your files under `assets/`.
3. Tag a version that matches `version` in the manifest:

   ```
   git tag v1.0.0
   git push origin main --tags
   ```

4. The included GitHub Action validates, packs, signs (if you added your key as
   the secret `VITRINE_SIGNING_KEY_B64`) and creates a release with the
   `.vpkg` attached.
5. Open a pull request to `vitrine-registry` adding `packages/<id>.json`, or
   let the CLI do it (option B).

### Option B: `vitrine publish`

Requires the GitHub CLI (`gh auth login`). Vitrine never creates tokens; it
uses your existing `gh` login.

```
node cli/vitrine.js publish my-pack.vpkg --repo https://github.com/you/my-pack --tag v1.0.0 --dry-run
node cli/vitrine.js publish my-pack.vpkg --repo https://github.com/you/my-pack --tag v1.0.0
```

The dry run shows exactly what would be submitted without touching the network.
The real run checks that the release asset exists and opens the pull request.

Accepted content licenses are listed in
[`registry/LICENSE-WHITELIST.md`](../../registry/LICENSE-WHITELIST.md). When in
doubt, use `CC0-1.0` or `CC-BY-4.0`.
