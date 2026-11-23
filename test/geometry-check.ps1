# The card's band check: polygons that cross themselves, cut in bands once a
# tick for the graphics card (SceneGeometry), checked away from the game.
# Each is built as a kept scene is, its corners are moved as they move
# between ticks, its vertices are placed as the projected vertex shader
# places them, and what its triangles cover is compared with what the
# even-odd rule fills (see test\geometry\nfm\lit\gpu\BandCheck.java).
#
#   .\test\geometry-check.ps1                  random polygons, corners moved in several ways
#   .\test\geometry-check.ps1 -Motion <dir>[,<dir>]  also the game's own polygons, from the scenes
#                                              -Dnfm.motiontest=<dir> writes (motion-*.bin),
#                                              and what the check costs a scene
#   .\test\geometry-check.ps1 -NoProbe         as the card cut bands before the check (for comparing)
#
# Exit code 0 means no sample is wrong away from the outline (one on it is a
# tie between the triangles' edges and the even-odd rule's, not geometry),
# 1 one is, 2 the check could not be run. Needs a JDK 25 or newer, found as
# build.ps1 finds it; it compiles the three geometry classes from src
# itself, so no build is needed first.

param(
    [string[]]$Motion,
    [switch]$NoProbe,
    [string]$Jdk
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

function Find-Jdk {
    $candidates = @()
    if ($Jdk) { $candidates += $Jdk }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdks) {
        $candidates += Get-ChildItem $jdks -Directory | Sort-Object Name -Descending | ForEach-Object FullName
    }
    foreach ($c in $candidates) {
        if (Test-Path "$c\bin\javac.exe") { return $c }
    }
    return $null
}

$jdkHome = Find-Jdk
if (-not $jdkHome) {
    Write-Host 'No JDK 25+ found (see build.ps1).'
    exit 2
}
$classes = Join-Path ([System.IO.Path]::GetTempPath()) "nfm-geometry-check-$PID"
New-Item -ItemType Directory -Force $classes | Out-Null
try {
    $sources = @('SceneGeometry', 'SceneList', 'ScenePoints' | ForEach-Object { "$root\src\nfm\lit\gpu\$_.java" }) +
        "$PSScriptRoot\geometry\nfm\lit\gpu\BandCheck.java"
    & "$jdkHome\bin\javac.exe" -nowarn -encoding UTF-8 --release 25 -d $classes @sources
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'The check did not compile.'
        exit 2
    }
    $java = "$jdkHome\bin\java.exe"
    $props = @("-Dnoprobe=$($NoProbe.IsPresent.ToString().ToLower())")
    $commit = (git -C $root rev-parse --short HEAD) + $(if (git -C $root status --porcelain -- src test) { ' with changes' })
    Write-Host "Band check of $commit (src\nfm\lit\gpu\SceneGeometry.java), probe $(-not $NoProbe)"
    $failed = $false
    # the blind review's case first, then a corner, every corner and a tilt, from a tick's half-pixel to far beyond
    $cases = @('2 edge', '0.5 one', '2 one', '0.5 random', '2 random', '5 random', '1 perspective')
    foreach ($case in $cases) {
        $move, $mode = $case -split ' '
        & $java @props -cp $classes nfm.lit.gpu.BandCheck random $move $mode 2000 1
        if ($LASTEXITCODE -ne 0) { $failed = $true }
    }
    if ($Motion) {
        $files = @($Motion | ForEach-Object { Get-ChildItem $_ -Filter 'motion-*.bin' } | ForEach-Object FullName)
        if ($files.Count -eq 0) {
            Write-Host "No motion-*.bin in $($Motion -join ', ') (run the game with -Dnfm.motiontest=<dir>)."
            exit 2
        }
        & $java @props '-Dreach=1.5' -cp $classes nfm.lit.gpu.BandCheck motion @files
        if ($LASTEXITCODE -ne 0) { $failed = $true }
        & $java -cp $classes nfm.lit.gpu.BandCheck time @files
    }
    if ($failed) {
        Write-Host 'Band check: FAILED (samples wrong away from the outline, above)'
        exit 1
    }
    Write-Host 'Band check: passed'
    exit 0
} finally {
    Remove-Item -Recurse -Force $classes -ErrorAction SilentlyContinue
}
