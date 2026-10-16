# Building your own add-ons (Lua & JavaScript)

Vitrine add-ons extend Minecraft with **Lua** or **JavaScript**. An add-on
takes effect the moment it is switched on and disappears completely when it is
switched off. No restart, no world reload.

This guide takes you from zero to a published add-on. The full function list is
in the [API reference](#api-reference) below.

---

## 1. Your first add-on in two minutes

1. Start Minecraft with Vitrine, open the store (**`M`**) and go to
   **Settings → Folders & tools**. Turn on **Developer mode**.
2. Click **Developer folder → Open**. This opens
   `.minecraft/config/vitrine/dev-addons/`.
3. Create a folder `hello` with a file `main.lua`:

   ```lua
   vitrine.chat("Hello from my first add-on!")

   vitrine.key("H", function()
     local p = vitrine.player()
     vitrine.actionbar("You are at " .. math.floor(p.x) .. " " .. math.floor(p.y) .. " " .. math.floor(p.z))
   end)
   ```

4. Join a world. You see the chat message. Press **H**.
5. Change the text and **save**. Vitrine reloads the add-on within a second.

Prefer JavaScript? Name the file `main.js` instead:

```js
vitrine.chat("Hello from JavaScript!");
vitrine.key("H", function () {
  var p = vitrine.player();
  vitrine.actionbar("You are at " + Math.floor(p.x) + " " + Math.floor(p.y) + " " + Math.floor(p.z));
});
```

Errors and everything you print with `vitrine.log(...)` show up in the
**add-on console**: *Settings → Folders & tools → Add-on console*, or the
command `/vitrine-konsole`.

---

## 2. A real example: a HUD speedometer

```lua
local last = nil
local speed = 0

vitrine.on("tick", function()
  local p = vitrine.player()
  if not p then return end
  if last then
    local dx, dz = p.x - last.x, p.z - last.z
    speed = math.sqrt(dx * dx + dz * dz) * 20   -- blocks per second
  end
  last = p
end)

vitrine.on("hud", function(g)
  local text = string.format("%.1f m/s", speed)
  local w = g.textWidth(text) + 12
  g.round(g.width - w - 8, 8, w, 18, 6, "#B0101418")
  g.text(text, g.width - w - 2, 13, "#FFFFFF")
end)
```

## 3. A custom menu

```lua
local nightVision = vitrine.storage.get("nv") or false

vitrine.key("G", function()
  vitrine.menu("Quick menu", function(ui)
    ui.heading("World")
    ui.button("Make it day", function() vitrine.command("time set day") end, true)
    ui.button("Clear weather", function() vitrine.command("weather clear") end)
    ui.heading("Player")
    ui.toggle("Night vision", nightVision, function(on)
      nightVision = on
      vitrine.storage.set("nv", on)
      vitrine.command(on and "effect give @s night_vision infinite 0 true"
                         or "effect clear @s night_vision")
    end)
  end)
end)
```

---

## 4. Packaging and publishing

A packaged add-on is a normal Vitrine package (`.vpkg`):

```
my-addon/
  vitrine.json        manifest, with "capabilities": ["skript"]
  icon.png            square icon, 256×256 recommended
  scripts/main.lua    or scripts/main.js (exactly one main file)
  scripts/util.lua    more files, loaded with vitrine.require("util.lua")
  assets/...          optional textures, sounds and post shaders, resource-pack layout
```

Minimal `vitrine.json`:

```json
{
  "schemaVersion": 1,
  "id": "yourname.speedometer",
  "name": "Speedometer",
  "version": "1.0.0",
  "category": "addon",
  "author": "yourname",
  "description": "Shows your speed in the top-right corner.",
  "capabilities": ["skript"],
  "icon": "icon.png",
  "license": "MIT"
}
```

Validate, pack and sign with the CLI (Node.js 18+):

```
node cli/vitrine.js validate my-addon
node cli/vitrine.js pack my-addon my-addon.vpkg
node cli/vitrine.js sign my-addon.vpkg
```

`sign` creates your personal Ed25519 key on first use (in your user folder,
never in the project) and writes a signature next to the package. Installed
add-ons must be signed, and the player has to approve the `skript` capability
before the first install.

To test the finished package, drop the `.vpkg` into
`config/vitrine/library/`. It appears in the store right away.

To publish it in the public catalog, follow
[Publishing a package](packages.md#publishing).

---

## 5. Security model

- Add-ons run in a **sandbox**: no files, no operating system, no Java classes,
  no network.
  - Lua: no `io`, `os`, `require`, `debug`, `luajava`.
  - JavaScript: no `java`, no `Packages`, all Java classes blocked.
- Every call has a **50 ms** time limit, starting an add-on **1 s**. Endless
  loops are stopped.
- After **10 errors** an add-on is paused until its source changes.
- `vitrine.command` runs with exactly the rights you have: operator in your own
  single-player world, your normal rights on a server.

---

## API reference

Lua and JavaScript see the same global object `vitrine`. Examples are in Lua;
in JavaScript you write the same calls, e.g.
`vitrine.on("tick", function () { ... })`.

### Output

| Function | Effect |
|---|---|
| `vitrine.log(...)` | Writes to the add-on console. |
| `vitrine.chat(text)` | Chat message only you can see. |
| `vitrine.actionbar(text)` | Text above the hotbar. |
| `vitrine.title(title, subtitle, ticks)` | Big title in the middle of the screen. |

### Events, keys, timers

| Function | Effect |
|---|---|
| `vitrine.on(event, fn)` | Events: `tick` (20 per second), `hud` (fn receives `g`), `join`, `leave`, `chat` (text), `hurt` (damage), `unload` (right before the add-on is unloaded). |
| `vitrine.key("G", fn)` | Own key, only in game with no screen open. Names like `"g"`, `"f6"`, `"space"`, `"left.shift"`. |
| `vitrine.every(seconds, fn)` | Repeats fn; returns an id. |
| `vitrine.after(seconds, fn)` | Runs fn once; returns an id. |
| `vitrine.cancel(id)` | Stops a timer. |

### Reading the game

| Function | Returns |
|---|---|
| `vitrine.player()` | `name, x, y, z, yaw, pitch, health, maxHealth, food, onGround, sneaking, sprinting, dimension, item`, or `nil` when no world is open. |
| `vitrine.world()` | `time, day, raining, thundering, biome, singleplayer` |
| `vitrine.block(x, y, z)` | Block id, e.g. `"minecraft:stone"` |

### Changing the game

| Function | Effect |
|---|---|
| `vitrine.command(cmd)` | Runs a command (without the leading `/`). |
| `vitrine.sound(id, volume, pitch)` | Plays a sound, e.g. `"minecraft:entity.player.levelup"`. |
| `vitrine.particle(id, x, y, z, count)` | Spawns simple particles, e.g. `"minecraft:cloud"`. |
| `vitrine.shader(name)` | Screen filter: `"invert"`, `"creeper"`, `"spider"`, `"blur"`, or your own as `"myaddon:my_filter"` (from `assets/myaddon/shaders/post/my_filter.json`). `nil` turns it off. |

### HUD drawing

Inside `vitrine.on("hud", function(g) ... end)`:

| Function | Effect |
|---|---|
| `g.text(text, x, y, color)` | Draw text |
| `g.rect(x, y, w, h, color)` | Draw a rectangle |
| `g.round(x, y, w, h, radius, color)` | Draw a rounded rectangle |
| `g.textWidth(text)` | Width of a text |
| `g.width`, `g.height` | Screen size |

Colors: `"#RRGGBB"`, `"#AARRGGBB"` or a number `0xAARRGGBB`.

### Menus

```lua
vitrine.menu("Title", function(ui)
  ui.heading("Section")
  ui.text("Explanation")
  ui.button("Button", function() ... end, true)   -- true = blue main button
  ui.toggle("Switch", currentValue, function(on) ... end)
  ui.space(8)
  -- ui.refresh() rebuilds the menu, ui.close() closes it
end)
```

`vitrine.closeMenu()` closes the menu from outside.

### Storage and more files

| Function | Effect |
|---|---|
| `vitrine.storage.get(k)` | Reads a stored value |
| `vitrine.storage.set(k, v)` | Stores a value. Survives restarts, at most 1 MB per add-on. |
| `vitrine.storage.all()` | All stored values |
| `vitrine.require("file.lua")` | Runs another file from `scripts/` |
| `vitrine.addon.id`, `vitrine.addon.name` | Id and name of the add-on |

### "Restart required" label

Some things cannot be done without a restart, for example genuinely new block,
item or entity types, or deep changes to Minecraft's code. If your add-on needs
something like that, declare it:

```lua
vitrine.restartNeeded("custom block type")
```

The library then shows the add-on with **⚠ Restart required**.

---

## Example add-ons

`python scripts/make_platform_examples.py <target folder>` builds three
signed examples:

| Add-on | Language | Shows |
|---|---|---|
| **Quick menu** | Lua | Key G opens a custom menu: day/night, weather, night vision, coordinates HUD |
| **Cinema filter** | JavaScript | Key K cycles screen shaders |
| **Adventure helper** | Lua | Fall protection, a title for every new day, speed display |

## Roadmap

- WebAssembly for Rust, C, C++, Go and Zig
- A Python bridge
- "New" blocks and items based on existing types
- Controlling Iris shader packs, networking between add-ons
