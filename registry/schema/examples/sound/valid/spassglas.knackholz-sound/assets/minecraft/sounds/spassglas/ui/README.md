# Fehlende Audio-Datei

`assets/minecraft/sounds.json` verweist auf:

```
assets/minecraft/sounds/spassglas/ui/knackholz_click.ogg
```

Diese `.ogg` liegt bewusst **nicht** im Repo. Vitrine nimmt keine fremden
Assets auf und erzeugt kein Platzhalter-Binaer, das man mit echtem Audio
verwechseln koennte (Charter-Regel 6). Ein Autor liefert die Datei nach --
siehe `ASSETS-NEEDED.md` im Repo-Wurzelverzeichnis, Abschnitt "Offen".

Anforderung an die Datei:

- Format: Ogg Vorbis, Mono, kurz (< 1 s), leise -- ein trockenes Holz-Klicken.
- Selbst aufgenommen oder unter CC0/CC-BY erzeugt, keine Mojang-Sounds,
  nichts aus einem anderen Client oder Marktplatz.
- Nach Einfuegen an genau den oben genannten Pfad ist die Paketquelle
  vollstaendig und `node cli/vitrine.js pack .` baut ein spielbares `.vpkg`.

Ohne die Datei ist das Beispiel strukturell komplett (Manifest + `sounds.json`
validieren), aber der Sound bleibt bei Vanilla, statt ersetzt zu werden.
