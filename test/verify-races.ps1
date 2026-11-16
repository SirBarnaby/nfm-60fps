# Plays every recorded race again from its input alone and checks it tick for tick.
#
#   .\test\verify-races.ps1                 every race in test\races, in one hidden, silent game
#   .\test\verify-races.ps1 -Fresh          each race in a game of its own (slower; nothing carries over)
#   .\test\verify-races.ps1 -Races <path>   another folder, or a single .nfmr file
#
# The races in test\races are also checked against their golden screenshots in
# test\golden: four frames per race, compared pixel by pixel. Frames that are
# missing are saved. -UpdateGolden saves every frame again, after a change that
# is meant to alter the picture; -NoGolden skips the frames. -Golden <folder>
# checks other races' frames against another folder. -Fresh checks the races
# only, because a few things on screen carry over from the races before.
#
# -Rollback <n> also rewinds every race every n ticks, after playing a few
# ticks ahead with the steering and throttle reversed. Every value the race
# can reach must be back as it was, and the race must still match: this proves
# the whole race state can be captured and put back.
#
# Run it after any change that could touch gameplay or drawing. Exit code 0
# means every race played out exactly as recorded and every frame looked the
# same, 1 that a race or a frame differed (the first differing tick, car and
# fields, or the changed pixels, are printed), 2 that a race could not be run.
# Build first with build.ps1: this uses madness.jar and the runtime it makes.

param(
    [string]$Races,
    [switch]$Fresh,
    [switch]$CanonicalOnly,
    [string]$Golden,
    [switch]$NoGolden,
    [switch]$UpdateGolden,
    [int]$Rollback = 0
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}
if (-not $Races) {
    $Races = Join-Path $PSScriptRoot 'races'
    # Golden frames are taken with every race in one game, in name order: what
    # the HUD hints at and some dust and sparks carry over from the races before.
    if (-not $Golden -and -not $Fresh) { $Golden = Join-Path $PSScriptRoot 'golden' }
}
$Races = (Resolve-Path $Races).Path
if ($NoGolden) { $Golden = $null }
if ($Golden) { $Golden = [IO.Path]::GetFullPath($Golden) }

function Invoke-Verify([string]$target) {
    $arguments = @('-Dnfm.debug=false', "-Dnfm.verify=$target")
    if ($CanonicalOnly) { $arguments += '-Dnfm.verify.canonical=true' }
    if ($Rollback -gt 0) { $arguments += "-Dnfm.verify.rollback=$Rollback" }
    if ($Golden) {
        $arguments += "-Dnfm.verify.golden=$Golden"
        if ($UpdateGolden) { $arguments += '-Dnfm.verify.golden.update=true' }
    }
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
