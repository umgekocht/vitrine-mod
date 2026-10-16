# Vitrine Designer and the CLI

Two free tools build the same `.vpkg` format and check against the same schema,
so you can switch between them at any time.

## Vitrine Designer

A static web page. No install, no server, no account.

1. Open `designer/index.html` with a double click.
2. **Manifest**: fill in id, name, version, category, author. A live validator
   reports errors with the same wording as the CLI and the mod.
3. **Theme** (category `theme`): colors, radii, spacing and sizes for light and
   dark, with a live preview of the store and a contrast warning.
4. **Textures**: load images, map them to a vanilla texture path, check sizes,
   compare before and after.
5. **HUD** (category `hud`): click widgets together, choose anchor, scale and
   data source. The designer writes the `hud.json` the mod loads unchanged.
6. **Export**: validates everything, builds the `.vpkg` in the browser and shows
   its SHA-256 checksum.

Your project is stored in the browser's local storage. In a private window it
is lost when you close it.

The browser cannot sign packages. Sign the exported file with the CLI
(`sign`) before you publish it.

## CLI

Requires Node.js 18 or newer. Run from the repository root:

| Command | What it does |
|---|---|
| `node cli/vitrine.js init <folder>` | Creates a package skeleton with a valid manifest |
| `node cli/vitrine.js validate <folder>` | Checks the manifest and all files against the schema and the security rules |
| `node cli/vitrine.js pack <folder> <out.vpkg>` | Builds the `.vpkg` |
| `node cli/vitrine.js sign <package.vpkg>` | Signs with your Ed25519 key (created on first use in your user folder) and writes `<package>.vpkg.integrity.json` |
| `node cli/vitrine.js publish <package.vpkg> --repo <url> --tag <tag> [--dry-run]` | Adds the package to the public catalog via a pull request |

Keep your signing key private. It never belongs in a repository; if you sign
in GitHub Actions, store it as an encrypted secret.
