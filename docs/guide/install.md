# Installing Vitrine

Vitrine is a client-side Fabric mod. You never need it on a server.

## What you need

| | Version |
|---|---|
| Minecraft Java Edition | 1.21.1 (more 1.21.x versions follow) |
| Fabric Loader | 0.16.0 or newer |
| Fabric API | any build for your Minecraft version |
| Java | 21 (the official launcher ships it) |

## Step by step

1. **Install Fabric.** Download the installer from <https://fabricmc.net/use/>,
   pick Minecraft 1.21.1 and click *Install*. A new profile called
   `fabric-loader-1.21.1` appears in the launcher.
2. **Download Fabric API** from Modrinth
   (<https://modrinth.com/mod/fabric-api>) for 1.21.1.
3. **Download Vitrine** from Modrinth or from the
   [GitHub releases](https://github.com/umgekocht/vitrine-mod/releases).
4. **Open your mods folder.** In the launcher go to *Installations*, hover the
   Fabric profile, click the folder icon and open `mods` (create it if it does
   not exist). The default path is:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. **Copy both jars** (Fabric API and Vitrine) into that folder.
6. **Start the Fabric profile.** On the title screen you now see the Vitrine
   button next to the menu buttons. In game, press **`M`** (changeable under
   *Options → Controls → Vitrine*).

The first time you open the store, a short introduction explains the three
things worth knowing: what applies instantly, what needs a restart, and how
permissions work.

## Optional companions

- **Iris** – needed if you want to use shader packs from the store.
- **Sodium** – works fine next to Vitrine.

## Where Vitrine keeps its files

```
.minecraft/config/vitrine/
  settings.json         your settings
  state.json            installed packages, on/off state, load order
  packs/                installed .vpkg files
  library/              drop .vpkg files here to install them locally
  dev-addons/           live-reloaded Lua/JavaScript add-ons (developer mode)
  cache/                catalog and image cache
  trash/                uninstalled packages (nothing is deleted outright)
```

Texture packs and mods you install from **Modrinth** go to the normal
`resourcepacks/`, `shaderpacks/` and `mods/` folders, so they also work
without Vitrine.

## Uninstalling

Remove `vitrine-<version>.jar` from `mods/`. Optionally delete
`config/vitrine/`. Packs installed from Modrinth stay in their normal folders.

## Building from source

You need JDK 21.

```
cd mod
./gradlew build          # macOS / Linux
gradlew.bat build        # Windows
```

The jar is written to a temporary build folder by default. Pass
`-Dvitrine.buildDir=<absolute path>` to choose the location; the jar then ends
up in `<path>/libs/`.
