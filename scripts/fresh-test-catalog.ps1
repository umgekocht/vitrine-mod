<#
.SYNOPSIS
    Vitrine A1-Frisch-Test, Katalog-/Cache-Teil -- ohne GUI-Fenster, ohne
    erhoehte Rechte (GATE121).

.DESCRIPTION
    scripts\fresh-test.ps1 deckt die volle A1-Klickstrecke ab ("installieren,
    hot sehen, deinstallieren"), aber nur mit einem echten, offenen
    Minecraft-Fenster und (fuer -SimulateNoNetwork) nur in einem erhoehten
    PowerShell-Fenster (Administrator, wegen New-NetFirewallRule). Beides
    laesst sich nicht automatisiert rot/gruen fahren -- das bleibt eine
    Mensch-Aufgabe (siehe PROGRESS.md, Abschnitt "Menschen-Aufgaben").

    Was sich aber ganz ohne Fenster und ohne Administrator automatisiert
    pruefen laesst: der Katalog-/Cache-Teil, also der HTTP-Vertrag, auf den
    sich der Mod-seitige HttpCatalogClient/EtagCache verlassen (mod/, dort
    NICHT angefasst):
      1. Die einkompilierte Index-URL ist erreichbar und liefert gueltiges,
         gegen registry/schema/registry-index-page.schema.json geprueftes
         JSON -- dasselbe Schema, das der Designer schreibt und der Mod
         liest (Charter-Regel 8, "ein Schema, zwei Seiten").
      2. Ein zweiter Abruf mit If-None-Match liefert 304 Not Modified --
         genau das Verhalten, auf das EtagCache.java sich verlaesst.
      3. Ein nicht aufloesbarer Host (reservierte ".invalid"-TLD, RFC 2606)
         wirft einen Netzfehler -- simuliert "Katalog nicht erreichbar" ohne
         New-NetFirewallRule/erhoehtes Fenster.

    Die eigentliche Pruefung steckt in
    registry/scripts/check-live-catalog.mjs (Node, wiederverwendet das
    echte Schema statt es zweimal zu pflegen). Dieses Skript ist nur ein
    duenner Wrapper, der node/registry-node_modules prueft und den
    Rueckgabewert durchreicht.

.PARAMETER IndexUrl
    Ueberschreibt die gepruefte Index-URL (Default: die einkompilierte
    DEFAULT_INDEX_URL aus CatalogEndpoints.java). Nur fuer eigene Tests
    dieses Skripts gedacht, nicht fuer den normalen Lauf.

.EXAMPLE
    powershell -File scripts\fresh-test-catalog.ps1
#>

[CmdletBinding()]
param(
    [string]$IndexUrl
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$RegistryDir = Join-Path $RepoRoot "registry"
$CheckScript = Join-Path $RegistryDir "scripts\check-live-catalog.mjs"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

if (-not (Test-Path $CheckScript)) {
    throw "Pruefskript nicht gefunden unter $CheckScript -- Repo-Layout pruefen."
}

Write-Step "Voraussetzungen"
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Host "FEHLER: 'node' ist nicht im PATH. registry/ braucht Node (npm, nicht pnpm) -- siehe registry/package.json." -ForegroundColor Red
    exit 1
}
Write-Host "node: $($node.Source)"

$nodeModules = Join-Path $RegistryDir "node_modules\@cfworker"
if (-not (Test-Path $nodeModules)) {
    Write-Host "FEHLER: registry/node_modules fehlt (@cfworker/json-schema nicht installiert)." -ForegroundColor Red
    Write-Host "Zuerst ausfuehren: cd registry && npm install" -ForegroundColor Red
    exit 1
}
Write-Host "registry/node_modules vorhanden."

Write-Step "Katalog-/Cache-Pruefung (node registry/scripts/check-live-catalog.mjs)"
$nodeArgs = @($CheckScript)
if ($IndexUrl) {
    $nodeArgs += @("--url", $IndexUrl)
}
& node @nodeArgs
$checkExitCode = $LASTEXITCODE

Write-Host ""
Write-Host "Hinweis: das ist nur der Katalog-/Cache-Teil. Die Klickstrecke" -ForegroundColor Yellow
Write-Host "'Store oeffnen, installieren, hot sehen, deinstallieren' im echten" -ForegroundColor Yellow
Write-Host "Fenster bleibt eine Mensch-Aufgabe -- siehe scripts\fresh-test.ps1" -ForegroundColor Yellow
Write-Host "und PROGRESS.md, Abschnitt 'Menschen-Aufgaben'." -ForegroundColor Yellow

exit $checkExitCode
