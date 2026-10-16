<#
.SYNOPSIS
    Vitrine A1 "Frisch-Test": startet den echten Mod-Client mit einem leeren,
    frisch angelegten Profil.

.DESCRIPTION
    ROADMAP A1 verlangt ein Skript, das ein leeres Minecraft-Profil anlegt
    (kein vorhandenes config/vitrine/, kein Cache, keine Anmeldung), mit dem
    gebauten Jar startet und durch Store-oeffnen / Katalog-laden / Paket-
    installieren / hot-sehen / deinstallieren fuehrt.

    Was dieses Skript automatisiert:
      1. Loescht mod/run (falls vorhanden) -- das entfernt config/vitrine/,
         gespeicherte Welten, Optionen und jeden Vitrine-Katalog-Cache, so
         dass jeder Lauf mit einem leeren Profil beginnt.
      2. Baut den Mod (gradlew build), sofern nicht -SkipBuild angegeben ist.
      3. Startet den Minecraft-Client ueber gradlew runClient -- NICHT den
         gepackten libs/vitrine-*.jar, sondern die Fabric-Loom-Dev-Umgebung
         (Yarn-gemappte Minecraft-Symbole). Das laeuft OHNE Mojang-Anmeldung
         -- Loom nutzt einen Offline-Dummy-Benutzer. Warum kein Start des
         gepackten Jars: siehe docs/fresh-test.md, Abschnitt "Warum gradlew
         runClient und nicht der gepackte Jar (Machbarkeitsbefund GATE1)" --
         dort auch der volle Beleg aus einem echten Lauf: echtes Fenster,
         echte GPU/Audio-Geraete, Vitrine initialisiert sauber aus leerem
         Profil.
      4. Wertet danach mod/run/logs/latest.log aus: sind Einstellungen und
         Paket-Zustand tatsaechlich frisch angelegt worden (keine
         Handeinrichtung noetig)?

    Was dieses Skript NICHT automatisiert:
      Store oeffnen, im Katalog stoebern, ein Paket anklicken/installieren,
      pruefen dass die Wirkung sofort ("hot") sichtbar ist, und wieder
      deinstallieren -- das sind Mausklicks im echten Spielfenster. Dieser
      Agent/dieses Skript hat kein Werkzeug fuer GUI-Eingaben. Die Checkliste
      unten wird im Terminal ausgegeben; ein Mensch fuehrt sie im offenen
      Fenster durch, waehrend das Spiel laeuft.

.PARAMETER SkipBuild
    Ueberspringt den Gradle-Build-Schritt (nur sinnvoll, wenn gerade schon
    gebaut wurde und das Jar aktuell ist).

.PARAMETER SimulateNoNetwork
    ROADMAP A3: fuehrt denselben Frisch-Test durch, blockiert aber waehrend
    "gradlew runClient" den Zugriff auf den einkompilierten Katalog-Host
    (umgekocht.github.io) per Windows-Firewall-Regel -- nicht das ganze Netz,
    nur diesen einen Host, damit Gradle/Loom-Metadatenzugriff unberuehrt
    bleibt. Beweist, dass der erste Start ohne Netz benutzbar bleibt (Store
    zeigt eine ehrliche "kein Netz"-Meldung statt abzustuerzen, lokale/
    installierte Pakete bleiben bedienbar). Braucht ein erhoehtes
    PowerShell-Fenster (Administrator) -- ohne das bricht das Skript sauber
    mit Exit-Code 1 ab, kein Crash. Ohne diesen Schalter aendert sich am
    bisherigen Verhalten nichts.

.EXAMPLE
    powershell -File scripts\fresh-test.ps1

.EXAMPLE
    powershell -File scripts\fresh-test.ps1 -SimulateNoNetwork
#>

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SimulateNoNetwork
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ModDir = Join-Path $RepoRoot "mod"
$RunDir = Join-Path $ModDir "run"

# ROADMAP A3: einzelner Host, den -SimulateNoNetwork blockiert -- muss zum
# in mod/src/main/java/.../catalog/CatalogEndpoints.java einkompilierten
# DEFAULT_INDEX_URL-Host passen.
$NoNetworkHost = "umgekocht.github.io"
$NoNetworkFirewallRulePrefix = "Vitrine-FreshTest-NoNetwork-Block"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Remove-NoNetworkFirewallRules {
    Get-NetFirewallRule -DisplayName "$NoNetworkFirewallRulePrefix*" -ErrorAction SilentlyContinue |
        Remove-NetFirewallRule -ErrorAction SilentlyContinue
}

# Eigene JAVA_HOME je Aufruf statt eine vom System erraten zu lassen --
# ueberschreibbar mit $env:VITRINE_JAVA_HOME, falls JDK 21 woanders liegt.
$JavaHome = $env:VITRINE_JAVA_HOME
if (-not $JavaHome) { $JavaHome = "C:/Program Files/Java/jdk-21" }

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

if (-not (Test-Path $ModDir)) {
    throw "mod/ nicht gefunden unter $ModDir -- Skript erwartet das Vitrine-Repo-Layout (scripts/ neben mod/)."
}

if ($SimulateNoNetwork -and -not (Test-IsAdministrator)) {
    Write-Host "FEHLER: -SimulateNoNetwork braucht ein erhoehtes PowerShell-Fenster (Administrator), um eine Firewall-Regel anzulegen." -ForegroundColor Red
    Write-Host "Siehe PROGRESS.md Menschen-Aufgaben. Skript wird abgebrochen (kein Build, keine Aenderung an mod/run)." -ForegroundColor Red
    exit 1
}

Write-Step "1/4 Frisches Profil: mod/run wird entfernt (config/vitrine, Cache, Welten, Optionen)"
if (Test-Path $RunDir) {
    $fileCountBefore = @(Get-ChildItem -Path $RunDir -Recurse -File -ErrorAction SilentlyContinue).Count
    Remove-Item -Path $RunDir -Recurse -Force
    Write-Host "mod/run geloescht ($fileCountBefore Datei(en)) -- naechster Start ist ein leeres Profil."
} else {
    Write-Host "mod/run existierte nicht -- ohnehin frisch."
}

$vitrineConfigOutsideRun = Join-Path $ModDir "config\vitrine"
if (Test-Path $vitrineConfigOutsideRun) {
    throw "Unerwartet: $vitrineConfigOutsideRun existiert noch. Frisch-Test abgebrochen -- das darf fuer A1 nicht vorhanden sein."
}

if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
    throw "JAVA_HOME '$JavaHome' enthaelt kein bin\java.exe. Setze `$env:VITRINE_JAVA_HOME auf ein JDK 21."
}
$env:JAVA_HOME = $JavaHome

Push-Location $ModDir
try {
    if (-not $SkipBuild) {
        Write-Step "2/4 Bauen (gradlew build)"
        & ./gradlew.bat build --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "gradlew build fehlgeschlagen (Exit $LASTEXITCODE) -- Frisch-Test kann nicht mit einem ungueltigen Jar starten."
        }
    } else {
        Write-Step "2/4 Bauen uebersprungen (-SkipBuild)"
    }

    # mod/build.gradle leitet den Build-Output NICHT nach mod/build/ um,
    # sondern (wegen der SMB-Freigabe Z:) standardmaessig nach
    # %TEMP%\vitrine-mod-build-<sha256-praefix des Projektpfads> (oder nach
    # -Dvitrine.buildDir=<pfad>, falls gesetzt). Denselben Hash hier
    # nachbilden, um das Jar zu bestaetigen -- schlaegt das fehl, ist das nur
    # eine Warnung, kein Abbruch: runClient braucht die Klassen direkt aus
    # dem Build, nicht den Jar-Pfad, den dieses Skript hier nur zur Bestaetigung sucht.
    $buildDirOverride = $env:VITRINE_BUILD_DIR
    if ($buildDirOverride) {
        $effectiveBuildDir = $buildDirOverride
    } else {
        $sha256 = [Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($ModDir))
        $projectHash = ($hashBytes[0..7] | ForEach-Object { $_.ToString("x2") }) -join ""
        $effectiveBuildDir = Join-Path $env:TEMP "vitrine-mod-build-$projectHash"
    }
    $jar = Get-ChildItem -Path (Join-Path $effectiveBuildDir "libs") -Filter "vitrine-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "-sources|-dev" } |
        Select-Object -First 1
    if ($jar) {
        Write-Host "Gebautes Jar: $($jar.FullName)"
    } else {
        Write-Host "Hinweis: gebautes Jar nicht unter $effectiveBuildDir\libs gefunden (evtl. -Dvitrine.buildDir abweichend gesetzt) -- runClient startet trotzdem direkt aus der Dev-Umgebung, nicht aus diesem Jar (siehe docs/fresh-test.md)." -ForegroundColor Yellow
    }

    Write-Step "3/4 Start (gradlew runClient) -- echtes Fenster, Offline-Dev-Benutzer, kein Login"

    if ($SimulateNoNetwork) {
        Write-Host @"
Das Spiel startet jetzt in einem echten Fenster (kein Mojang-Login noetig --
die Dev-Umgebung nutzt einen Offline-Dummy-Benutzer). -SimulateNoNetwork ist
aktiv: der Zugriff auf $NoNetworkHost ist waehrend des Laufs per
Firewall-Regel blockiert. Bitte von Hand durchgehen, waehrend das Fenster
offen ist:

  [ ] Store oeffnen        -- Taste 'M' im Spiel, oder Knopf im Pausenmenue
  [ ] Kein Netz sichtbar   -- Store zeigt eine ehrliche "kein Netz"-Meldung
                               (oder den Cache, falls vorhanden) statt zu
                               haengen, einen Fehlerdialog zu zeigen oder
                               abzustuerzen
  [ ] Lokale Pakete gehen  -- bereits installierte/lokale Pakete bleiben
                               unabhaengig vom Katalog bedienbar
                               (an-/abschalten, deinstallieren)

Fenster danach schliessen (ESC -> Spiel verlassen, oder Alt+F4). Dieses
Skript wertet anschliessend mod/run/logs/latest.log aus.
"@
    } else {
        Write-Host @"
Das Spiel startet jetzt in einem echten Fenster (kein Mojang-Login noetig --
die Dev-Umgebung nutzt einen Offline-Dummy-Benutzer). Bitte von Hand
durchgehen, waehrend das Fenster offen ist:

  [ ] Store oeffnen        -- Taste 'M' im Spiel, oder Knopf im Pausenmenue
  [ ] Katalog laden        -- Store zeigt Pakete vom echten Registry-Index
                               (https://umgekocht.github.io/vitrine-registry/index/v1/index.json)
  [ ] Paket installieren   -- ein beliebiges Paket anklicken, installieren
  [ ] Hot sehen            -- Wirkung ist sofort sichtbar, KEIN "Neustart
                               noetig"-Hinweis fuer Pakete, die laut Manifest
                               hot anwendbar sind
  [ ] Deinstallieren       -- dasselbe Paket wieder entfernen

Fenster danach schliessen (ESC -> Spiel verlassen, oder Alt+F4). Dieses
Skript wertet anschliessend mod/run/logs/latest.log aus.
"@
    }

    if ($SimulateNoNetwork) {
        # Zuerst evtl. Regel(n) aus einem abgebrochenen Vorlauf entfernen,
        # dann DNS aufloesen (braucht noch Netz) und NUR diese IP(s) sperren
        # -- nicht das ganze Netz, sonst waere auch Gradle/Loom betroffen.
        Remove-NoNetworkFirewallRules

        $addresses = [System.Net.Dns]::GetHostAddresses($NoNetworkHost)
        $ipv4 = @($addresses | Where-Object { $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork } | ForEach-Object { $_.IPAddressToString })
        $ipv6 = @($addresses | Where-Object { $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetworkV6 } | ForEach-Object { $_.IPAddressToString })
        if ($ipv4.Count -eq 0 -and $ipv6.Count -eq 0) {
            throw "Konnte $NoNetworkHost nicht aufloesen -- -SimulateNoNetwork braucht kurz Netz, um die zu sperrende(n) IP-Adresse(n) zu ermitteln."
        }

        Write-Host "Sperre $NoNetworkHost fuer die Dauer von runClient (aufgeloest: $((@($ipv4) + @($ipv6)) -join ', '))" -ForegroundColor Yellow

        try {
            if ($ipv4.Count -gt 0) {
                New-NetFirewallRule -DisplayName "$NoNetworkFirewallRulePrefix-v4" -Direction Outbound -Action Block -Protocol TCP -RemotePort 443 -RemoteAddress $ipv4 -Profile Any | Out-Null
            }
            if ($ipv6.Count -gt 0) {
                New-NetFirewallRule -DisplayName "$NoNetworkFirewallRulePrefix-v6" -Direction Outbound -Action Block -Protocol TCP -RemotePort 443 -RemoteAddress $ipv6 -Profile Any | Out-Null
            }

            & ./gradlew.bat runClient --console=plain
            $runExitCode = $LASTEXITCODE
        } finally {
            Remove-NoNetworkFirewallRules
            Write-Host "Firewall-Regel(n) fuer $NoNetworkHost wieder entfernt." -ForegroundColor Yellow
        }
    } else {
        & ./gradlew.bat runClient --console=plain
        $runExitCode = $LASTEXITCODE
    }
} finally {
    Pop-Location
}

Write-Step "4/4 Auswertung von mod/run/logs/latest.log"
$logPath = Join-Path $RunDir "logs\latest.log"
if (-not (Test-Path $logPath)) {
    Write-Host "FAIL: kein Log gefunden unter $logPath" -ForegroundColor Red
    exit 1
}
$logText = Get-Content -Path $logPath -Raw

$checks = [ordered]@{
    "Mod initialisiert"                                      = "Vitrine startet\."
    "Einstellungen frisch angelegt (kein Handeinrichten)"     = "lege Standardwerte an"
    "Paket-Zustand frisch angelegt (kein Handeinrichten)"     = "lege leeren Zustand an"
}

$allAutomatedChecksOk = $true
foreach ($checkName in $checks.Keys) {
    $pattern = $checks[$checkName]
    if ($logText -match $pattern) {
        Write-Host "PASS: $checkName" -ForegroundColor Green
    } else {
        Write-Host "FAIL: $checkName (Muster '$pattern' nicht im Log gefunden)" -ForegroundColor Red
        $allAutomatedChecksOk = $false
    }
}

if ($SimulateNoNetwork) {
    # ROADMAP A3: kein Crash, keine unbehandelte Ausnahme trotz gesperrtem
    # Katalog-Host -- weder ein voller Crash-Report noch eine rohe
    # Java-Ausnahme im Client-Log.
    $checkName = "Kein Absturz trotz gesperrtem Katalog-Host"
    if ($logText -notmatch "Minecraft Crash Report" -and $logText -notmatch "Exception in thread") {
        Write-Host "PASS: $checkName" -ForegroundColor Green
    } else {
        Write-Host "FAIL: $checkName (Crash-Report oder unbehandelte Ausnahme im Log gefunden)" -ForegroundColor Red
        $allAutomatedChecksOk = $false
    }
}

Write-Host ""
if ($SimulateNoNetwork) {
    Write-Host "Hinweis: 'kein Netz'-Meldung im Store / lokale Pakete bleiben bedienbar" -ForegroundColor Yellow
    Write-Host "sind oben als Handschritte aufgefuehrt -- dieses Skript kann keine" -ForegroundColor Yellow
    Write-Host "Mausklicks im echten Fenster simulieren. Bestaetige sie selbst." -ForegroundColor Yellow
} else {
    Write-Host "Hinweis: Katalog laden / Paket installieren / hot sehen / deinstallieren" -ForegroundColor Yellow
    Write-Host "sind oben als Handschritte aufgefuehrt -- dieses Skript kann keine" -ForegroundColor Yellow
    Write-Host "Mausklicks im echten Fenster simulieren. Bestaetige sie selbst." -ForegroundColor Yellow
}

if ($runExitCode -ne 0) {
    Write-Host "Hinweis: gradlew runClient endete mit Exit $runExitCode (oft einfach durch Schliessen des Fensters -- kein automatischer Fehlschlag)." -ForegroundColor Yellow
}

if (-not $allAutomatedChecksOk) {
    exit 1
}
exit 0
