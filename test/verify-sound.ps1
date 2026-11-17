# The sound check: renders recorded races through the 3D sound, in step with
# the race (no clock, no sound device, nothing heard), and compares each
# render with this machine's baseline.
#
#   .\test\verify-sound.ps1                   render, and compare with the baseline
#   .\test\verify-sound.ps1 -UpdateBaseline   render, and save the results as the baseline
#   .\test\verify-sound.ps1 -Keep             also keep the renders, to listen to (test\sound-renders)
#
# A race rendered this way always gives the same samples, however fast it was
# played, so any change to what the new sound does (an engine's mix, a
# crash's level, how a hill muffles a car) shows up as a different render;
# a change meant to change the sound is then listened to and saved with
# -UpdateBaseline. Each render's SHA-256 is kept per machine in
# test\sound-baseline.json, as OpenAL Soft and Steam Audio pick their code by
# processor and other machines may round differently. Every race is also
# checked tick for tick as verify-races does, so the sound can be seen not to
# touch it. Classic sound is not rendered here: it is the original code
# path, and verify-races checks its commands.
# Exit code 0 means every render matches (or a baseline was saved), 1 one
# differs, 2 the check could not be run. Build first with build.ps1.

param(
    [switch]$UpdateBaseline,
    [switch]$Keep,
    [string[]]$Races = @('race-20261116-045258-734-stage01.nfmr', 'race-20261116-045352-343-stage08.nfmr',
        'race-20261116-045315-492-stage14.nfmr')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}
$out = if ($Keep) { Join-Path $PSScriptRoot 'sound-renders' } else { Join-Path ([IO.Path]::GetTempPath()) "nfm-sound-$PID" }
New-Item -ItemType Directory -Force $out | Out-Null

$results = [ordered]@{}
$failed = $false
foreach ($race in $Races) {
    $recording = Join-Path $PSScriptRoot "races\$race"
    if (-not (Test-Path $recording)) { Write-Host "[sound] $race is not in test\races"; $failed = $true; continue }
    $wav = Join-Path $out ([IO.Path]::ChangeExtension($race, '.wav'))
    $log = "$wav.log"
    $arguments = @('--enable-native-access=ALL-UNNAMED', '-Dnfm.debug=false', "-Dnfm.verify=$recording",
        '-Dnfm.sound=3d', "-Dnfm.sound.render=$wav", '-jar', $jar)
    $p = Start-Process $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru -Wait `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $raceOk = @(Get-Content $log | Where-Object { $_ -like '*`[verify`]   OK*' }).Count -gt 0
    if ($p.ExitCode -ne 0 -or -not $raceOk -or -not (Test-Path $wav) -or (Get-Item $wav).Length -le 44) {
        Write-Host "[sound] $race could not be rendered, or the race did not match (see $log)"
        $failed = $true
        continue
    }
    $results[$race] = (Get-FileHash $wav -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $Keep) { Remove-Item $wav, $log, "$log.err" -ErrorAction SilentlyContinue }
}
if (-not $Keep) { Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue }
if ($failed) { exit 2 }

$baselineFile = Join-Path $PSScriptRoot 'sound-baseline.json'
$baselines = @{}
if (Test-Path $baselineFile) {
    (Get-Content $baselineFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $baselines[$_.Name] = $_.Value }
}
$key = $env:COMPUTERNAME
$baseline = $baselines[$key]
if ($UpdateBaseline -or $null -eq $baseline) {
    $baselines[$key] = [pscustomobject]$results
    [pscustomobject]$baselines | ConvertTo-Json -Depth 3 | Set-Content $baselineFile -Encoding utf8
    Write-Host "[sound] baseline for $key saved: $($results.Count) renders"
    exit 0
}
$changed = 0
foreach ($race in $results.Keys) {
    $was = $baseline.$race
    if ($null -eq $was) {
        Write-Host "[sound] $race`: no baseline yet (save one with -UpdateBaseline)"
        $changed++
    } elseif ($was -ne $results[$race]) {
        Write-Host "[sound] $race`: CHANGED"
        $changed++
    } else {
        Write-Host "[sound] $race`: same"
    }
}
if ($changed -gt 0) {
    Write-Host "[sound] $changed of $($results.Count) renders differ from the baseline: listen with -Keep, then save with -UpdateBaseline if meant"
    exit 1
}
Write-Host "[sound] all $($results.Count) renders match the baseline"
exit 0
