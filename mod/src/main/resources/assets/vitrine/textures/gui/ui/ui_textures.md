# Vitrine UI textures

Every file ships twice: `<name>.png` at nominal size and
`<name>@4x.png` for HiDPI. Insets below are for the nominal file;
multiply by 4 for the @4x variant.

## Nine-slice

Stretch the middle row/column only, keep the corners fixed.
These are shaded with a vertical gradient, so horizontal stretching
is seamless; vertical stretching keeps the top highlight in place.

| file | size | slice inset |
|------|------|-------------|
| panel_frame.png | 48x48 | 14 |
| panel_frame_flat.png | 48x48 | 14 |
| label_frame.png | 32x32 | 10 |
| search_field.png | 40x40 | 13 |
| search_field_focus.png | 40x40 | 13 |
| button_accent.png | 40x40 | 13 |
| button_accent_hover.png | 40x40 | 13 |
| button_accent_pressed.png | 40x40 | 13 |
| button_enabled.png | 40x40 | 13 |
| button_disabled.png | 40x40 | 13 |

## Fixed size

| file | size | note |
|------|------|------|
| toggle_off.png | 52x32 | track grey, knob left |
| toggle_on.png | 52x32 | track green (#34C759), knob right |

Animate a switch by cross-fading the two, or by moving the knob and
tinting the track between #40454F and #34C759.

## Palette

| role | hex |
|------|-----|
| window background | #1E2026 |
| panel | #23252B |
| panel highlight | #2E323B |
| field | #1A1C22 |
| border | #3A3F4C |
| accent | #3B74E8 |
| accent pressed | #2556BE |
| success | #34C759 |
| text | #E8ECF4 |
| text muted | #787F8E |
