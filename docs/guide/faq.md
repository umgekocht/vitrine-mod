# FAQ

**Is Vitrine really free?**
Yes. No prices, no coins, no ads, no account. Everything is open source under
Apache-2.0.

**Do I need it on the server?**
No. Vitrine is client-only. Data packs from the store go into your currently
open single-player world (or a LAN world you host).

**Which Minecraft versions are supported?**
1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10 and 1.21.11 with Fabric. Versions older than 1.14
cannot run Fabric mods, so they are not planned.

**What needs a restart?**
Only Java mods (`.jar`). Texture packs, shaders, themes, HUD modules, sounds,
languages, data packs and Lua/JavaScript add-ons apply instantly. Anything that
needs a restart is labelled so.

**Where does the Modrinth content come from?**
From the public Modrinth API. Files are downloaded from Modrinth's CDN and
checked against Modrinth's own hashes. Vitrine is not affiliated with Modrinth.

**Shaders don't do anything.**
Shader packs need **Iris** installed.

**The store says "No connection".**
Installed packages keep working. The store shows the last cached catalog until
you are online again.

**A package broke my textures.**
Vitrine already rolled it back. Open the library to see the reason; you can
change the load order (higher wins) or switch packages off.

**Can I change the key?**
Yes: *Options → Controls → Vitrine*. The default is `M`.

**How do I make my own content?**
- Add-ons: [addons.md](addons.md)
- Themes: [themes.md](themes.md)
- Textures, HUD, sounds, languages: [packages.md](packages.md)

**How do I translate Vitrine?**
Language files are in `mod/src/main/resources/assets/vitrine/lang/`. Copy
`en_us.json`, translate the values (keep every `%s`) and open a pull request.

**Is this an official Minecraft product?**
No. Not approved by or associated with Mojang or Microsoft.
