# The live test: plays the game the way you do, with your saved settings, and
# reports how fast and how evenly each part of it went on screen.
#
#   .\test\live.ps1                                  one run with your settings
#   .\test\live.ps1 -Split                           split screen instead
#   .\test\live.ps1 -Set 'nfm.overlay=off'           one run more for each entry,
#   .\test\live.ps1 -Set 'nfm.overlay=off','nfm.smootheffects=false nfm.runahead=false'
#                                                    each entry's -D settings on top of yours
#   .\test\live.ps1 -Out C:\somewhere                where the runs go (default: %TEMP%\nfm-live\<time>)
#   .\test\live.ps1 -NoShots                         no screenshots: each one stops the frames for a moment
#
# What it plays: the main menu, fullscreen switched off and on again (a new
# window and the graphics card started again, as F11 does), the settings
# screen, car and stage select, a race, the pause menu, the instant replay, the
# race again and the post-race highlight. It reads your settings as the game
# does (window or fullscreen and its resolution, the picture's shape, renderer,
# frame limit, vsync, MSAA, supersampling, dynamic resolution, HDR, smooth
# effects, run-ahead, the overlay, sound and its effects, mods, game
# controllers) and starts Java the way madness64.exe does (madness64.ini). The
# window is real, the card presents to it as it does to yours, but it is off
# every monitor, never takes the keyboard or the mouse, a game controller can
# neither drive it nor shake, sound goes to a file instead of the speakers, and
# nothing is saved.
#
# Each run writes frames.json (every part's frames a second, frame times, late
# frames, the card's time, how frames were drawn), settings.txt (what it ran
# with) and screenshots: <name>-gpu.png is read back from the card, so it is
# what your screen shows. report.md and the console list each part and flag
# what is wrong: a race drawn in software, frames a second under the frame
# limit (or the monitor's refresh rate), uneven or late frames, hitches and
# stalls (told apart from frames the card held up: the game was ready, the
# card or Windows took that long to take the frame), the card near its time
# per frame. Exit code 0 means nothing was flagged, 1 something was, 2 the
# game could not be run.
# Build first with build.ps1: this uses madness.jar and the runtime it makes.

param(
    [switch]$Split,
    [switch]$NoShots,
    [string[]]$Set = @(),
    [string]$Out
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'madness.jar'
if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
    throw 'madness.jar or runtime\ is missing. Run build.ps1 first.'
}
if (-not $Out) {
    $Out = Join-Path ([IO.Path]::GetTempPath()) ('nfm-live\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force $Out | Out-Null

# Java as madness64.exe starts it
$launcher = @(Get-Content (Join-Path $root 'madness64.ini') | Where-Object { $_ -match '^vm\.arg\.\d+=' } |
    ForEach-Object { $_ -replace '^vm\.arg\.\d+=', '' })

# parts of a race, which should go at the frame limit; the rest are menus, drawn once a tick
$raceParts = @('countdown', 'race', 'split countdown', 'split race', 'instantreplay', 'gamehighlight')

function Invoke-Run([string]$name, [string]$extra) {
    $dir = Join-Path $Out $name
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    New-Item -ItemType Directory -Force $dir | Out-Null
    $arguments = @($launcher) + @('-Dnfm.debug=false', '-Dnfm.live=true', "-Dnfm.autotest=$dir",
        "-Dnfm.sound.render=$dir\sound.wav")
    if ($Split) { $arguments += '-Dnfm.autotest.split=saved' }
    if ($NoShots) { $arguments += '-Dnfm.autotest.shots=false' }
    $arguments += @($extra -split ' ' | Where-Object { $_ } | ForEach-Object { "-D$_" })
    $arguments += @('-jar', $jar)
    $p = Start-Process $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$dir\out.log" -RedirectStandardError "$dir\err.log"
    if (-not $p.WaitForExit(420000)) {
        $p.Kill()
        Write-Host "[live] $name ran for 7 minutes and was stopped"
    }
    $frames = Join-Path $dir 'frames.json'
    if (-not (Test-Path $frames)) { return $null }
    $settings = @{}
    Get-Content (Join-Path $dir 'settings.txt') | ForEach-Object {
        $i = $_.IndexOf(': ')
        if ($i -gt 0) { $settings[$_.Substring(0, $i)] = $_.Substring($i + 2) }
    }
    [pscustomobject]@{
        name = $name
        extra = $extra
        dir = $dir
        parts = @((Get-Content $frames -Raw | ConvertFrom-Json).parts)
        settings = $settings
    }
}

# The frames a second a race part should reach: the frame limit, or the monitor's refresh rate with none.
function Get-Target($run) {
    $refresh = 0
    if ($run.settings['refresh'] -match '^(\d+)') { $refresh = [int]$Matches[1] }
    $limit = $run.settings['fps limit']
    if ($limit -match '^(\d+)') { return [int]$Matches[1] }
    if ($limit -match 'vrr') { return [math]::Max(1, $refresh - $refresh * $refresh / 3600) }
    return $refresh
}

function Get-Flags($run, $part) {
    $flags = @()
    if ($part.frames -lt 10) { return $flags }
    $race = $raceParts -contains $part.name
    $target = Get-Target $run
    $drawn = $part.drawn.PSObject.Properties.Name
    if ($race -and $drawn -contains 'software' -and $run.settings['renderer'] -like 'gpu*') {
        $flags += 'drawn in software'
    }
    if ($race -and $target -gt 0 -and $part.fps -lt $target * 0.9) {
        $flags += ('{0:N0} fps, under {1}' -f $part.fps, $target)
    }
    # unevenness needs enough frames to tell: a menu's second of 25 frames has one 99th-percentile frame
    $enough = $part.frames -ge 100
    if ($enough -and $part.ms.p99 -gt $part.ms.median * 2.5 -and $part.ms.p99 - $part.ms.median -gt 4) {
        $flags += ('uneven: 99% of frames within {0:N1} ms, half within {1:N1}' -f $part.ms.p99, $part.ms.median)
    }
    if ($enough -and $part.late -gt $part.frames * 0.05) {
        $flags += ('{0} of {1} frames late' -f $part.late, $part.frames)
    }
    # a stall the card held up (handing it the frame took that long) is the card's, not the game's drawing
    $held = if ($part.cardHeld) { [double]$part.cardHeld.longestMs } else { 0 }
    $limit = if ($race) { 100 } else { 250 }
    if ($part.ms.max -gt $limit -and ($race -or $part.name -notin @('start', 'loading', 'fullscreen switch'))) {
        if ($held -gt 0 -and $held -ge $part.ms.max - 100) {
            $flags += ('the card held a frame up {0:N0} ms' -f $held)
        } else {
            $flags += ('{0} of {1:N0} ms' -f $(if ($race) { 'hitch' } else { 'stall' }), $part.ms.max)
        }
    }
    if ($target -gt 0 -and $part.gpuMs -gt 900 / $target) {
        $flags += ('card takes {0:N1} ms of {1:N1}' -f $part.gpuMs, (1000 / $target))
    }
    return $flags
}

$runs = @()
$plan = @(@{ name = 'yours'; extra = '' })
$n = 1
foreach ($s in $Set) { $plan += @{ name = "set$n"; extra = $s }; $n++ }
foreach ($r in $plan) {
    Write-Host "[live] $($r.name)$(if ($r.extra) { ": $($r.extra)" })..."
    $run = Invoke-Run $r.name $r.extra
    if ($null -eq $run) { Write-Host "[live] $($r.name) did not finish: see $(Join-Path $Out $r.name)\out.log"; continue }
    $runs += $run
}
if ($runs.Count -eq 0) { exit 2 }

$flagged = $false
$md = @('# Live test', '', "$(Get-Date -Format 'yyyy-MM-dd HH:mm'), $env:COMPUTERNAME", '')
foreach ($run in $runs) {
    $title = if ($run.extra) { "$($run.name): $($run.extra)" } else { 'Your settings' }
    $md += @("## $title", '')
    $md += @($run.settings.GetEnumerator() | Sort-Object Name | ForEach-Object { "- $($_.Name): $($_.Value)" })
    $md += @('', '| Part | Seconds | Frames/s | Median ms | 99% ms | Longest ms | Late | Card ms | Drawn | Flags |',
        '|---|---:|---:|---:|---:|---:|---:|---:|---|---|')
    Write-Host ''
    Write-Host "== $title"
    Write-Host ('{0,-18} {1,6} {2,8} {3,7} {4,7} {5,8} {6,5} {7,6}  {8}' -f 'part', 's', 'fps', 'median', '99%',
        'longest', 'late', 'card', 'flags')
    foreach ($part in $run.parts) {
        $flags = @(Get-Flags $run $part)
        if ($flags.Count -gt 0) { $flagged = $true }
        $drawn = ($part.drawn.PSObject.Properties | ForEach-Object { "$($_.Name) $($_.Value)" }) -join ', '
        if ($part.screenshotFrames) { $drawn += "; $($part.screenshotFrames) screenshot frames left out" }
        $md += ('| {0} | {1:N1} | {2:N1} | {3:N2} | {4:N2} | {5:N1} | {6} | {7:N2} | {8} | {9} |' -f $part.name,
            $part.seconds, $part.fps, $part.ms.median, $part.ms.p99, $part.ms.max, $part.late, $part.gpuMs, $drawn,
            ($flags -join '; '))
        Write-Host ('{0,-18} {1,6:N1} {2,8:N1} {3,7:N2} {4,7:N2} {5,8:N1} {6,5} {7,6:N2}  {8}' -f $part.name,
            $part.seconds, $part.fps, $part.ms.median, $part.ms.p99, $part.ms.max, $part.late, $part.gpuMs,
            ($flags -join '; '))
    }
    $md += ''
    foreach ($part in $run.parts | Where-Object { $raceParts -contains $_.name -and $_.slowest }) {
        $md += ("Slowest frames in $($part.name): " + (($part.slowest | ForEach-Object {
                        '{0:N1} ms at {1:N1} s' -f $_.ms, $_.at }) -join ', ') + '  ')
    }
    $holds = @(foreach ($part in $run.parts) {
            foreach ($h in @($part.cardHeld.holds)) {
                if ($null -eq $h) { continue }
                '{0:N0} ms in {1} at {2:N1} s{3}' -f $h.ms, $part.name, $h.at, $(if ($h.after) { " ($($h.after))" })
            }
        })
    if ($holds.Count -gt 0) {
        $md += @('', 'The card held frames up (handing it the frame took this long; what changed on the card just' +
            ' before is in brackets):', '')
        $md += @($holds | ForEach-Object { "- $_" })
    }
    $shot = Get-ChildItem $run.dir -Filter '*-ingame-*-gpu.png' | Select-Object -First 1
    if ($shot) { $md += @('', "A race frame as the card showed it: $($shot.FullName)") }
    $md += ''
}
if ($runs.Count -gt 1) {
    $md += @('## Races compared', '', '| Run | Frames/s | Median ms | 99% ms | Longest ms |', '|---|---:|---:|---:|---:|')
    foreach ($run in $runs) {
        $race = $run.parts | Where-Object { $_.name -eq 'race' } | Sort-Object frames -Descending | Select-Object -First 1
        if ($race) {
            $md += ('| {0} | {1:N1} | {2:N2} | {3:N2} | {4:N1} |' -f $(if ($run.extra) { $run.extra } else { 'yours' }),
                $race.fps, $race.ms.median, $race.ms.p99, $race.ms.max)
        }
    }
}
$report = Join-Path $Out 'report.md'
$md | Set-Content $report -Encoding utf8
Write-Host ''
Write-Host "[live] report: $report"
if ($flagged) { exit 1 }
exit 0
