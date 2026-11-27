# The extras' own checks: each extra (skidmarks, the wing trail, ...) plays the situations its
# requirements in openspec\specs name on a made-up race and measures what it would show.
#
#   .\test\extras-check.ps1              every extra
#   .\test\extras-check.ps1 skidmarks    one
#
# Runs nfm.lit.features.ExtrasCheck from madness.jar (build first). It opens no window, plays no
# sound and leaves no file behind (the skid zones write into a temporary folder they delete).
#
# Exit code 0: every check passed. 1: a check failed (its line says FAILED and what it measured).

param([string]$Feature = 'all')

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $jar)) {
    Write-Host 'madness.jar is missing: run build.ps1 first.'
    exit 1
}
if (-not (Test-Path $java)) {
    Write-Host 'runtime\bin\java.exe is missing: run build.ps1 first.'
    exit 1
}
& $java '-Djava.awt.headless=true' '--enable-native-access=ALL-UNNAMED' -cp $jar nfm.lit.features.ExtrasCheck $Feature
exit $LASTEXITCODE
