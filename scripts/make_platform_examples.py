"""Baut drei Beispiel-Addons fuer die Vitrine-Addon-Plattform (Lua/JavaScript)
und signiert sie per CLI (Stand 2026-10-14):

  spassglas.schnellmenue    Lua         eigenes Menue auf Taste G
  spassglas.kino-filter     JavaScript  Bild-Filter (Shader) auf Taste K
  spassglas.abenteuer       Lua         Spiellogik + HUD: Fallschutz, Tageszaehler, Tempo

Aufruf: python scripts/make_platform_examples.py <zielordner>
"""
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_test_addons import CLI, manifest, preview, write  # noqa: E402

SCHNELLMENUE = r'''-- Schnellmenue: Taste G oeffnet ein eigenes Menue (Vitrine-Addon-Plattform, Lua)
local hudAn = vitrine.storage.get("hud") == true

local function oeffnen()
  vitrine.menu("Schnellmenü", function(ui)
    ui.heading("Welt")
    ui.button("Tag machen", function() vitrine.command("time set day") vitrine.sound("minecraft:block.note_block.chime") end)
    ui.button("Nacht machen", function() vitrine.command("time set night") end)
    ui.button("Wetter: klar", function() vitrine.command("weather clear") end)
    ui.space(6)
    ui.heading("Spieler")
    ui.toggle("Nachtsicht", vitrine.storage.get("nacht") == true, function(an)
      vitrine.storage.set("nacht", an)
      if an then vitrine.command("effect give @s night_vision infinite 0 true")
      else vitrine.command("effect clear @s night_vision") end
    end)
    ui.toggle("Koordinaten im HUD", hudAn, function(an) hudAn = an vitrine.storage.set("hud", an) end)
    ui.space(6)
    ui.button("Schließen", function() ui.close() end, true)
  end)
end

vitrine.key("G", oeffnen)

vitrine.on("hud", function(g)
  if not hudAn then return end
  local p = vitrine.player()
  if p == nil then return end
  local t = string.format("X %d  Y %d  Z %d", p.x, p.y, p.z)
  g.round(4, 4, g.textWidth(t) + 12, 16, 6, 0x90000000)
  g.text(t, 10, 8, "#E8ECF4")
end)

vitrine.log("bereit - Taste G öffnet das Menü")
'''

KINO = r'''// Kino-Filter: Taste K wechselt Bild-Filter (Vitrine-Addon-Plattform, JavaScript)
const filter = [null, "invert", "creeper", "spider", "blur"];
const namen = ["Aus", "Negativ", "Creeper-Sicht", "Spinnen-Sicht", "Weichzeichner"];
let i = Number(vitrine.storage.get("filter") || 0);

function setzen() {
  vitrine.shader(filter[i]);
  vitrine.actionbar("Kino-Filter: " + namen[i]);
  vitrine.storage.set("filter", i);
}

vitrine.key("K", function () {
  i = (i + 1) % filter.length;
  setzen();
  vitrine.sound("minecraft:ui.button.click", 0.6, 1.4);
});

vitrine.on("join", function () { if (i > 0) setzen(); });
vitrine.log("bereit - Taste K wechselt den Filter");
'''

ABENTEUER = r'''-- Abenteuer-Helfer: Spiellogik und HUD (Vitrine-Addon-Plattform, Lua)
local letzterTag = -1
local hoechsterPunkt = nil

-- Tageszaehler: zum Morgen jedes neuen Tages ein Titel
vitrine.every(2, function()
  local w = vitrine.world()
  if w == nil then return end
  if letzterTag >= 0 and w.day > letzterTag then
    vitrine.title("Tag " .. math.floor(w.day + 1), "Ein neuer Morgen")
    vitrine.sound("minecraft:entity.player.levelup", 0.5, 1.2)
  end
  letzterTag = w.day
end)

-- Fallschutz: wer tiefer als 12 Bloecke faellt, schwebt sanft
vitrine.on("tick", function()
  local p = vitrine.player()
  if p == nil then return end
  if p.onGround then
    hoechsterPunkt = p.y
  elseif hoechsterPunkt ~= nil and hoechsterPunkt - p.y > 12 then
    vitrine.command("effect give @s slow_falling 3 0 true")
    vitrine.particle("minecraft:cloud", p.x, p.y, p.z, 12)
    hoechsterPunkt = p.y
  end
end)

-- Treffer: kurzes Aufleuchten in der Aktionsleiste
vitrine.on("hurt", function(schaden)
  vitrine.actionbar(string.format("-%.1f ♥", schaden / 2))
end)

-- HUD: Tempo in Bloecken pro Sekunde
local alt = nil
local tempo = 0
vitrine.every(0.5, function()
  local p = vitrine.player()
  if p == nil then alt = nil return end
  if alt ~= nil then
    tempo = math.sqrt((p.x - alt.x) ^ 2 + (p.z - alt.z) ^ 2) * 2
  end
  alt = p
end)
vitrine.on("hud", function(g)
  local t = string.format("%.1f m/s", tempo)
  local x = g.width - g.textWidth(t) - 16
  g.round(x - 6, 4, g.textWidth(t) + 12, 16, 6, 0x90000000)
  g.text(t, x, 8, "#9FD0FF")
end)

vitrine.on("join", function() vitrine.chat("§aAbenteuer-Helfer aktiv: Fallschutz, Tageszähler, Tempo.") end)
'''


def build(work):
    out = []
    p = os.path.join(work, "schnellmenue")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.schnellmenue", "Schnellmenü", "addon", ["skript"],
        "Taste G öffnet ein eigenes Menü: Tag/Nacht, Wetter, Nachtsicht und Koordinaten im HUD. "
        "Beispiel für die Vitrine-Addon-Plattform (Lua), wirkt sofort ohne Neustart."))
    write(os.path.join(p, "scripts", "main.lua"), SCHNELLMENUE)
    preview(os.path.join(p, "icon.png"), "Schnellmenü", ["Taste G: eigenes Menü", "Lua-Addon"], (59, 116, 232), (20, 40, 90))
    out.append("schnellmenue")

    p = os.path.join(work, "kino-filter")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.kino-filter", "Kino-Filter", "addon", ["skript"],
        "Taste K wechselt Bild-Filter: Negativ, Creeper-Sicht, Spinnen-Sicht, Weichzeichner. "
        "Beispiel für Shader per Addon (JavaScript), ohne Neustart."))
    write(os.path.join(p, "scripts", "main.js"), KINO)
    preview(os.path.join(p, "icon.png"), "Kino-Filter", ["Taste K: Filter wechseln", "JavaScript-Addon"], (150, 60, 200), (50, 20, 70))
    out.append("kino-filter")

    p = os.path.join(work, "abenteuer")
    write(os.path.join(p, "vitrine.json"), manifest(
        "spassglas.abenteuer", "Abenteuer-Helfer", "addon", ["skript"],
        "Fallschutz bei tiefen Stürzen, Titel zu jedem neuen Tag, Tempo-Anzeige im HUD. "
        "Beispiel für Spiellogik per Addon (Lua), ohne Neustart."))
    write(os.path.join(p, "scripts", "main.lua"), ABENTEUER)
    preview(os.path.join(p, "icon.png"), "Abenteuer", ["Fallschutz, Tage, Tempo", "Lua-Addon"], (40, 150, 100), (15, 55, 40))
    out.append("abenteuer")
    return out


def main(target):
    os.makedirs(target, exist_ok=True)
    work = tempfile.mkdtemp(prefix="vitrine-platform-")
    for name in build(work):
        src = os.path.join(work, name)
        dst = os.path.join(target, "spassglas." + name + ".vpkg")
        for cmd in (["validate", src], ["pack", src, dst], ["sign", dst]):
            r = subprocess.run(["node", CLI] + cmd, capture_output=True, text=True, encoding="utf-8")
            if r.returncode != 0:
                raise SystemExit(name + ": " + " ".join(cmd) + " fehlgeschlagen:\n" + r.stdout + r.stderr)
        print("fertig:", dst)


if __name__ == "__main__":
    main(sys.argv[1])
