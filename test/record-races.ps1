# Records races for the verification corpus with the autopilot, in a hidden,
# silent game. The autopilot picks the stages and cars and drives with
# scripted input; the opponents are the game's own AI. Every third race
# watches an instant replay half way, and half the races skip the intro.
#
#   .\test\record-races.ps1 -Count 17                 one race per stage, as fast as the PC allows
#   .\test\record-races.ps1 -Count 1 -RealTime        at the real pace, with frames drawn in between
#   .\test\record-races.ps1 -Count 2 -Ticks 6000      long races, which may end on their own
#
# Races you play yourself can be added too: start the game with
# -Dnfm.record=test\races (for example in madness64.ini) and play.

param(
    [int]$Count = 17,
    [long]$Seed = 1,
    [int]$Ticks = 1500,
    [switch]$RealTime,
    [string]$Out = (Join-Path $PSScriptRoot 'races')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}
New-Item -ItemType Directory -Force $Out | Out-Null

$arguments = @(
    '-Dnfm.debug=false',
    "-Dnfm.record=$((Resolve-Path $Out).Path)",
    "-Dnfm.autopilot=$Count",
    "-Dnfm.autopilot.seed=$Seed",
    "-Dnfm.autopilot.ticks=$Ticks"
)
if ($RealTime) { $arguments += @('-Dnfm.unpaced=false', '-Dnfm.fps=unlimited') }
$arguments += @('-jar', $jar)

Push-Location $root
try {
    & $java @arguments | Where-Object { $_ -like '`[record`]*' } | ForEach-Object { Write-Host $_ }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
