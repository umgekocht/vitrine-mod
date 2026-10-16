# Making a Vitrine theme

A theme changes how the Vitrine store itself looks: colors, corner radii,
spacing, sizes and animation timing. Themes apply instantly, without a restart,
and never run code.

## Quickest way: Vitrine Designer

1. Open `designer/index.html` from this repository in your browser (double
   click, no server needed).
2. Set **Category** to `theme` in the *Manifest* section.
3. In the *Theme* section adjust colors and sizes for **light** and **dark**.
   The live preview shows a mock-up of the store; a warning appears when text
   contrast gets too low.
4. Click **Export package**. You get a `.vpkg` with the `theme.json` inside.
5. Drop the `.vpkg` into `.minecraft/config/vitrine/library/`, open the store,
   install it and switch it on in the library.

## By hand

A theme package is a zip (`.vpkg`) with two files at its root:

```
vitrine.json
theme.json
icon.png        (optional)
```

`vitrine.json`:

```json
{
  "schemaVersion": 1,
  "id": "yourname.midnight",
  "name": "Midnight",
  "version": "1.0.0",
  "category": "theme",
  "author": "yourname",
  "capabilities": ["theme"],
  "icon": "icon.png",
  "license": "CC-BY-4.0"
}
```

`theme.json` contains a full token set for `light` and for `dark`. Every token
is required; a theme is either complete or rejected, so start from the
complete example
[`registry/schema/examples/theme/valid/klarglas.json`](../../registry/schema/examples/theme/valid/klarglas.json)
and change what you need.

### The color tokens

| Token | Used for |
|---|---|
| `color.bg` | Page background |
| `color.surfaceSolid` | Cards, panels, dialogs |
| `color.surfaceTranslucent` | Glass surfaces (`{"hex": "#28282e", "alpha": 0.72}`) |
| `color.borderTranslucent` | Thin borders |
| `color.overlayScrim` | Darkening behind dialogs |
| `color.text` | Main text |
| `color.textMuted` | Secondary text |
| `color.disabledText` | Disabled controls |
| `color.accent` | Main buttons, selection, links |
| `color.accentContrast` | Text on top of the accent color |
| `color.danger` | Uninstall, errors |

Solid colors are `#RRGGBB`. Translucent colors are an object with `hex` and
`alpha` (0–1).

### Shapes, spacing and motion

The other tokens are integers in GUI pixels (or milliseconds for motion), for
example `radius.card`, `radius.control`, `space.gutter`, `nav.width.expanded`,
`card.width`, `dialog.width`. The complete list with explanations is in
[`registry/schema/theme.schema.json`](../../registry/schema/theme.schema.json).

### Check before you ship

```
node cli/vitrine.js validate my-theme
node cli/vitrine.js pack my-theme my-theme.vpkg
node cli/vitrine.js sign my-theme.vpkg
```

If a theme turns out to be broken in game, Vitrine keeps the current theme and
tells you why instead of showing a half-styled store.

## Tips for a good theme

- Keep text contrast at **4.5 : 1** or higher against `color.bg` and
  `color.surfaceSolid`.
- Use the accent color sparingly: Vitrine only paints the main action of a
  page in the accent color.
- Provide both a light and a dark variant that feel like the same theme.
- Add a 256×256 `icon.png` and at least one screenshot so people see what they
  get.
