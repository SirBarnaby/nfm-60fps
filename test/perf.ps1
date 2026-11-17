# The performance gate: plays the hidden smoke test with the GPU renderer and no
# frame limit, and measures how fast race frames are drawn.
#
#   .\test\perf.ps1                   measure, and compare with this machine's baseline
#   .\test\perf.ps1 -UpdateBaseline   measure, and save the result as the baseline
#   .\test\perf.ps1 -Runs 5           more runs (the best of them counts; default 3)
#
# Speed is defended the way gameplay is: a change that makes race frames more
# than 5% slower (median frames a second over the race, replays and highlights)
# or whose 99th-percentile frame gap grows by more than 20% fails. Baselines are
# kept per machine and power source in test\perf-baseline.json, as other
# machines are not comparable. On battery a laptop's speed wanders by more than
# 5% from run to run, so there the numbers are reported but never fail the gate.
# The window is never shown. Exit code 0 means as fast as the baseline (or a
# baseline was saved), 1 slower, 2 the test could not be run.
# Build first with build.ps1: this uses madness.jar and the runtime it makes.

param(
    [int]$Runs = 3,
    [switch]$UpdateBaseline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}

Add-Type -AssemblyName System.Windows.Forms
$power = [System.Windows.Forms.SystemInformation]::PowerStatus.PowerLineStatus
$onBattery = $power -eq 'Offline'
$key = "$env:COMPUTERNAME, " + $(if ($onBattery) { 'battery' } else { 'mains' })
if ($onBattery) { Write-Warning 'Running on battery: the numbers wander. Plug in for a fair measurement.' }

function Measure-Run([int]$n) {
    $dir = Join-Path ([IO.Path]::GetTempPath()) "nfm-perf-$PID-$n"
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    New-Item -ItemType Directory -Force $dir | Out-Null
    $log = "$dir.log"
    $arguments = @('--enable-native-access=ALL-UNNAMED', '-Dnfm.debug=false', '-Dnfm.renderer=gpu', '-Dnfm.profile=true',
        '-Dnfm.fps=unlimited', "-Dnfm.autotest=$dir", '-jar', $jar)
    $p = Start-Process $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru -Wait `
        -RedirectStandardOutput $log -RedirectStandardError "$dir.err"
    $lines = @(Get-Content $log | Where-Object { $_ -like '`[draw`] own thread:*' })
    Remove-Item -Recurse -Force $dir, $log, "$dir.err" -ErrorAction SilentlyContinue
    if ($p.ExitCode -ne 0 -or $lines.Count -lt 8) { return $null }
    # the first lines are the race starting up, while the JIT is still at work
    $lines = $lines | Select-Object -Skip 4
    $fps = @($lines | ForEach-Object { if ($_ -match 'own thread: ([0-9.]+) fps') { [double]$Matches[1] } }) | Sort-Object
    $gaps = @($lines | ForEach-Object { if ($_ -match '99% ([0-9.]+) ms') { [double]$Matches[1] } }) | Sort-Object
    [pscustomobject]@{
        fps = $fps[[int][math]::Floor($fps.Count / 2)]
        gap99 = $gaps[[int][math]::Floor($gaps.Count / 2)]
    }
}

$best = $null
foreach ($n in 1..$Runs) {
    $r = Measure-Run $n
    if ($null -eq $r) { Write-Host "[perf] run $n failed"; continue }
    Write-Host ("[perf] run {0}: median {1:N1} fps, 99% frame gap {2:N1} ms" -f $n, $r.fps, $r.gap99)
    if ($null -eq $best -or $r.fps -gt $best.fps) { $best = $r }
}
if ($null -eq $best) { Write-Host '[perf] no run finished'; exit 2 }

$baselineFile = Join-Path $PSScriptRoot 'perf-baseline.json'
$baselines = @{}
if (Test-Path $baselineFile) {
    (Get-Content $baselineFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $baselines[$_.Name] = $_.Value }
}
$baseline = $baselines[$key]
if ($UpdateBaseline -or $null -eq $baseline) {
    $baselines[$key] = [pscustomobject]@{ fps = $best.fps; gap99 = $best.gap99; date = (Get-Date -Format 'yyyy-MM-dd') }
    [pscustomobject]$baselines | ConvertTo-Json | Set-Content $baselineFile -Encoding utf8
    Write-Host ("[perf] baseline for {0} saved: {1:N1} fps, 99% gap {2:N1} ms" -f $key, $best.fps, $best.gap99)
    exit 0
}
$slower = $best.fps -lt $baseline.fps * 0.95
$jerkier = $best.gap99 -gt $baseline.gap99 * 1.2
Write-Host ("[perf] {0}: {1:N1} fps against {2:N1} ({3:+0.0;-0.0}%), 99% gap {4:N1} ms against {5:N1}" -f $key,
    $best.fps, $baseline.fps, (100 * ($best.fps / $baseline.fps - 1)), $best.gap99, $baseline.gap99)
if (($slower -or $jerkier) -and $onBattery) {
    Write-Host '[perf] slower than the baseline, but on battery that is not judged: plug in and run again'
    exit 0
}
if ($slower -or $jerkier) {
    Write-Host "[perf] SLOWER than the baseline$(if ($slower) { ': frames a second down more than 5%' })$(if ($jerkier) { ': frame gaps up more than 20%' })"
    exit 1
}
Write-Host '[perf] as fast as the baseline'
exit 0
