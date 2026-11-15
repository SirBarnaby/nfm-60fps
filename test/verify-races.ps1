# Plays every recorded race again from its input alone and checks it tick for tick.
#
#   .\test\verify-races.ps1                 every race in test\races, in one hidden, silent game
#   .\test\verify-races.ps1 -Fresh          each race in a game of its own (slower; nothing carries over)
#   .\test\verify-races.ps1 -Races <path>   another folder, or a single .nfmr file
#
# Run it after any change that could touch gameplay. Exit code 0 means every
# race played out exactly as recorded, 1 that at least one differed (the first
# differing tick, car and fields are printed), 2 that one could not be run.
# Build first with build.ps1: this uses madness.jar and the runtime it makes.

param(
    [string]$Races = (Join-Path $PSScriptRoot 'races'),
    [switch]$Fresh,
    [switch]$CanonicalOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}
$Races = (Resolve-Path $Races).Path

function Invoke-Verify([string]$target) {
    $arguments = @('-Dnfm.debug=false', "-Dnfm.verify=$target")
    if ($CanonicalOnly) { $arguments += '-Dnfm.verify.canonical=true' }
    $arguments += @('-jar', $jar)
    Push-Location $root
    try {
        & $java @arguments | Where-Object { $_ -like '`[verify`]*' } | ForEach-Object { Write-Host $_ }
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

if ($Fresh -and (Test-Path $Races -PathType Container)) {
    $worst = 0
    foreach ($file in Get-ChildItem $Races -Filter *.nfmr | Sort-Object Name) {
        $code = Invoke-Verify $file.FullName
        if ($code -gt $worst) { $worst = $code }
    }
    exit $worst
}
exit (Invoke-Verify $Races)
