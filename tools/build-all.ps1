param([switch]$Offline)
$ErrorActionPreference='Stop'
Push-Location "$PSScriptRoot/.."
try {
    $flags = @()
    if ($Offline) { $flags += '--offline' }
    foreach ($mc in '26.3','26.2','26.1.2') {
        & .\gradlew.bat @flags "-Pmc=$mc" build
        if ($LASTEXITCODE) { throw "Build failed for $mc" }
    }
} finally { Pop-Location }
