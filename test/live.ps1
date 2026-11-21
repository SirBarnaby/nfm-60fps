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
#   .\test\live.ps1 -MinFps 300                      the frame rate a race part must reach, with no allowance
#                                                    under it (default 250, the owner's target:
#                                                    openspec\specs\performance); 0 turns it off. Not
#                                                    judged when your settings hold frames under it (a
#                                                    lower frame limit, Match display, Variable refresh,
#                                                    vsync on: then the limit is the target). On battery
#                                                    or with Battery saver on, the run is inconclusive
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
# Each run writes manifest.json before the game starts (the commit, uncommitted
# changes, madness.jar's SHA-256: what was tested, for test\blind-review.ps1),
# frames.json (every part's frames a second, frame times, late
# frames, the card's time, how frames were drawn), settings.txt (what it ran
# with) and screenshots: <name>-gpu.png is read back from the card, so it is
# what your screen shows. report.md and the console list each part and flag
# what is wrong: a race or a menu drawn in software, or drawn by the processor
# and only put on screen by the card, frames a second under the frame
# limit (or the monitor's refresh rate), uneven or late frames, hitches and
# stalls (told apart from frames the card held up: the game was ready, the
# card or Windows took that long to take the frame), the card near its time
# per frame. A run is flagged as a whole when the game's test did not go the
# whole way, a race part it plays is missing or too short to judge, or it
# wrote no frames at all. A run during which the PC was on battery or had
# Battery saver on (Windows slows the processor and the card then, which the
# owner did not choose) cannot prove a frame rate: it is inconclusive, its
# frame rates are notes, and anything else it flags still fails it. Exit code 0
# means nothing was flagged, 1 something was, 2 the game could not be run, 3
# inconclusive (plug in and run again). What a window off every monitor cannot show is a note, not a flag: with
# vsync its frames can wait for Windows' own clock, not your monitor's refresh (then half of them
# come quicker than the refresh allows, which no screen could show), and Windows sometimes holds
# one of its frames up for half a second. Notes also name what the specs ask
# that this test does not judge: a frame cap under the floor.
# Build first with build.ps1: this uses madness.jar and the runtime it makes.

param(
    [switch]$Split,
    [switch]$NoShots,
    [string[]]$Set = @(),
    [string]$Out,
    [int]$MinFps = 250
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
# the race parts the test plays (GameSparker.runAutoTest, runSplitAutoTest): a run missing one, or with one too
# short to judge (the 100 frames over 2 seconds that unevenness needs), did not show what it was meant to
$expectedParts = if ($Split) { @('split countdown', 'split race', 'instantreplay') }
else { @('countdown', 'race', 'instantreplay', 'gamehighlight') }
$enoughFrames = 100
$enoughSeconds = 2

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
    $manifest = Write-Manifest $dir $extra
    if ($manifest.jarOlderThan) {
        Write-Host "[live] note: madness.jar is older than $($manifest.jarOlderThan): this tests the last build, not the files as they are (run build.ps1 -Quick first)"
    }
    # Windows is asked about power before, every 2 seconds during and after the run, so a plug pulled for a
    # moment counts
    $power = @(Get-PowerSaving)
    $p = Start-Process $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$dir\out.log" -RedirectStandardError "$dir\err.log"
    $stopAt = (Get-Date).AddMinutes(7)
    while (-not $p.WaitForExit(2000)) {
        $power += @(Get-PowerSaving)
        if ((Get-Date) -gt $stopAt) {
            $p.Kill()
            Write-Host "[live] $name ran for 7 minutes and was stopped"
            break
        }
    }
    $power = @($power + @(Get-PowerSaving) | Select-Object -Unique)
    $frames = Join-Path $dir 'frames.json'
    if (-not (Test-Path $frames)) { return $null }
    $settings = @{}
    Get-Content (Join-Path $dir 'settings.txt') | ForEach-Object {
        $i = $_.IndexOf(': ')
        if ($i -gt 0) { $settings[$_.Substring(0, $i)] = $_.Substring($i + 2) }
    }
    $settings['power'] = if ($power.Count -gt 0) { "$($power -join ' and ') during the run" } else { 'plugged in' }
    $plan = Get-PowerPlan
    if ($plan) { $settings['power'] += ", power plan $plan" }
    $settings['build'] = Get-BuildText $manifest
    # the game says when its test went the whole way; one that ran out of time writes frames.json all the same
    $log = @(Get-Content "$dir\out.log" -ErrorAction SilentlyContinue)
    $finished = @($log | Where-Object { $_ -match '^\[autotest\] (split test )?done' }).Count -gt 0
    $ending = @($log | Where-Object { $_ -match '^\[autotest\] .*ran out of time' } | Select-Object -Last 1)
    [pscustomobject]@{
        name = $name
        extra = $extra
        dir = $dir
        parts = @((Get-Content $frames -Raw | ConvertFrom-Json).parts)
        settings = $settings
        power = $power
        finished = $finished
        ending = if ($ending) { $ending[0] -replace '^\[autotest\] ', '' } else { 'out.log has no "[autotest] done" line' }
    }
}

# What a run is made from, written to manifest.json in its folder before the game starts, so its evidence can be
# matched to a build (test\blind-review.ps1 does): the commit checked out, whether the tree had changes and which
# (git status --porcelain lines), madness.jar's SHA-256 and when it was built, and the newest source file (src\,
# lib\) if one is newer than the jar, which is then an earlier build than the files. commit is null outside git.
function Write-Manifest([string]$dir, [string]$extra) {
    $ErrorActionPreference = 'Continue'   # git's own messages, if any, are not the test's
    $commit = [string](& git -C $root rev-parse --verify --quiet HEAD 2>$null)
    $changed = @(& git -C $root status --porcelain 2>$null)
    $ErrorActionPreference = 'Stop'
    $jarFile = Get-Item $jar
    $newest = @(Get-ChildItem (Join-Path $root 'src') -Recurse -File -Filter '*.java') +
    @(Get-ChildItem (Join-Path $root 'lib') -File -Filter '*.jar' -ErrorAction SilentlyContinue) |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $manifest = [ordered]@{
        time = (Get-Date).ToString('o')
        repo = $root
        commit = if ($commit) { $commit.Trim() } else { $null }
        dirty = $changed.Count -gt 0
        changed = $changed
        jarSha256 = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        jarBuilt = $jarFile.LastWriteTime.ToString('o')
        jarOlderThan = if ($newest -and $newest.LastWriteTime -gt $jarFile.LastWriteTime) {
            $newest.FullName.Substring($root.Length + 1)
        } else { $null }
        split = [bool]$Split
        set = $extra
    }
    [IO.File]::WriteAllText((Join-Path $dir 'manifest.json'), ($manifest | ConvertTo-Json -Depth 3),
        (New-Object Text.UTF8Encoding $false))
    return [pscustomobject]$manifest
}

# The manifest in a line, for the settings list in report.md.
function Get-BuildText($manifest) {
    $text = if ($manifest.commit) { 'commit ' + $manifest.commit.Substring(0, 10) } else { 'not a git checkout' }
    $text += if ($manifest.dirty) { ", $($manifest.changed.Count) uncommitted changes" } else { ', nothing uncommitted' }
    $text += ', madness.jar sha256 {0} built {1:yyyy-MM-dd HH:mm}' -f $manifest.jarSha256.Substring(0, 12),
    [datetime]$manifest.jarBuilt
    if ($manifest.jarOlderThan) { $text += ", older than $($manifest.jarOlderThan) (an earlier build than the files)" }
    return $text
}

# Flags for a run as a whole: the test did not go the whole way, or a race part it plays is missing or too short
# to judge, so its numbers cannot say the game is fine.
function Get-RunFlags($run) {
    $flags = @()
    if (-not $run.finished) { $flags += "the test did not finish: $($run.ending)" }
    foreach ($name in $expectedParts) {
        $parts = @($run.parts | Where-Object { $_.name -eq $name })
        $frames = [double](($parts | Measure-Object frames -Sum).Sum)
        $seconds = [double](($parts | Measure-Object seconds -Sum).Sum)
        if ($parts.Count -eq 0) {
            $flags += "no $name"
        } elseif ($frames -lt $enoughFrames -or $seconds -lt $enoughSeconds) {
            $flags += ('{0}: {1:N0} frames over {2:N1} s, too few to judge (needs {3} over {4} s)' -f $name, $frames,
                $seconds, $enoughFrames, $enoughSeconds)
        }
    }
    $rule = Get-FloorRule $run
    if ($rule.why) { $flags += "note: $($rule.why)" }
    # openspec\specs\performance: the card draws the game, menus and every race view. With the GPU renderer a menu
    # the processor drew ('picture': the card only put it on screen; 'software': Windows did) is a flag, as a race
    # part is (Get-Flags); its frames are counted over every time the menu came up, however short
    if ($run.settings['renderer'] -like 'gpu*') {
        $menus = [ordered]@{}
        foreach ($part in $run.parts | Where-Object { $raceParts -notcontains $_.name }) {
            $cpu = 0
            foreach ($way in 'picture', 'software') { if ($part.drawn.$way) { $cpu += [int]$part.drawn.$way } }
            if ($cpu -gt 0) { $menus[$part.name] = [int]$menus[$part.name] + $cpu }
        }
        if ($menus.Count -gt 0) {
            $flags += 'menus drawn by the processor, not the card: ' + (($menus.GetEnumerator() | ForEach-Object {
                        '{0} ({1} frames)' -f $_.Key, $_.Value }) -join ', ')
        }
    }
    return $flags
}

# The monitor's refresh rate the run read, 0 if it could not.
function Get-Refresh($run) {
    if ($run.settings['refresh'] -match '^(\d+)') { return [int]$Matches[1] }
    return 0
}

# What holds a run's frames down, from its settings as the game words them (GameSparker.describeFpsLimit and
# describeVsync): a limit of 'N fps', 'match display' (the monitor's refresh rate) or 'variable refresh (...)'
# (just under it, GameSparker.vrrRate), and vsync on (frames wait for the monitor). fps 0: nothing holds them;
# known false: a cap follows the refresh rate, which the run could not read. The game never holds a test run to
# its battery cap (GameSparker.batteryCap skips hidden runs), so that is not one.
function Get-Cap($run) {
    $refresh = Get-Refresh $run
    $limit = [string]$run.settings['fps limit']
    $caps = @()
    if ($limit -match '^(\d+)') {
        $caps += [pscustomobject]@{ fps = [double]$Matches[1]; why = "the $limit limit" }
    } elseif ($limit -like 'match display*') {
        $caps += [pscustomobject]@{ fps = [double]$refresh; why = 'the limit matching the display' }
    } elseif ($limit -like 'variable refresh*') {
        $caps += [pscustomobject]@{ fps = $refresh - $refresh * $refresh / 3600; why = 'the variable refresh limit' }
    }
    if ([string]$run.settings['vsync'] -match '^on') { $caps += [pscustomobject]@{ fps = [double]$refresh; why = 'vsync' } }
    $lowest = $caps | Where-Object { $_.fps -gt 0 } | Sort-Object fps | Select-Object -First 1
    [pscustomobject]@{
        fps = if ($lowest) { $lowest.fps } else { 0 }
        why = if ($lowest) { $lowest.why } else { '' }
        known = @($caps | Where-Object { $_.fps -le 0 }).Count -eq 0
    }
}

# The frames a second a race part should reach: what holds the frames down, or the monitor's refresh rate with
# nothing.
function Get-Target($run) {
    $cap = Get-Cap $run
    if ($cap.fps -gt 0) { return $cap.fps }
    return Get-Refresh $run
}

# Whether race parts are held to the owner's floor (-MinFps, openspec\specs\performance): not when the settings
# hold frames under it. why: the note saying so, once a run. (On battery see Get-Inconclusive.)
function Get-FloorRule($run) {
    $cap = Get-Cap $run
    if ($MinFps -le 0) { return [pscustomobject]@{ judged = $false; why = '' } }
    if (-not $cap.known) {
        return [pscustomobject]@{ judged = $false
            why = "the frame limit follows the monitor's refresh rate, which the run could not read: the $MinFps fps floor is not judged" }
    }
    if ($cap.fps -gt 0 -and $cap.fps -lt $MinFps) {
        return [pscustomobject]@{ judged = $false
            why = ('frames are held to {0:N0} a second by {1}, under the {2} fps floor: race parts are judged against {0:N0}' -f
                $cap.fps, $cap.why, $MinFps) }
    }
    return [pscustomobject]@{ judged = $true; why = '' }
}

# Why a run cannot prove a frame rate, '' when it can: Windows saved power during it in a way the owner did not
# choose (on battery it slows the processor and the card; Battery saver too). The game itself never holds a test
# run to its battery cap, so the slowing is Windows' alone, and its frame rates are notes, not passes or flags.
function Get-Inconclusive($run) {
    if (@($run.power).Count -eq 0) { return '' }
    return ('Windows was saving power during the run ({0}), when it slows the processor and the graphics card, so ' +
        'the run cannot prove {1} fps or any frame rate: plug in, turn Battery saver off and run again') -f
    ($run.power -join ' and '), $MinFps
}

# What Windows does to save power now that the owner did not choose: 'on battery', 'with Battery saver on', or nothing
# (GetSystemPowerStatus, as the game asks it).
function Get-PowerSaving {
    if (-not ('NfmLive.Power' -as [type])) {
        Add-Type -Namespace NfmLive -Name Power -MemberDefinition @'
[StructLayout(LayoutKind.Sequential)]
public struct Status {
    public byte ACLineStatus; public byte BatteryFlag; public byte BatteryLifePercent; public byte SystemStatusFlag;
    public int BatteryLifeTime; public int BatteryFullLifeTime;
}
[DllImport("kernel32.dll")]
public static extern bool GetSystemPowerStatus(out Status status);
'@
    }
    $status = New-Object NfmLive.Power+Status
    if (-not [NfmLive.Power]::GetSystemPowerStatus([ref]$status)) { return }
    if ($status.ACLineStatus -eq 0) { 'on battery' }
    if ($status.SystemStatusFlag -eq 1) { 'with Battery saver on' }
}

# The Windows power plan in use, by name ('' if powercfg cannot tell): the owner's own choice, so it is only shown.
function Get-PowerPlan {
    $line = [string](& powercfg /getactivescheme 2>$null)
    if ($line -match '\(([^)]+)\)\s*$') { return $Matches[1] }
    return ''
}

# Flags, and notes (starting "note: ") for what this window, off every monitor, cannot show.
function Get-Flags($run, $part) {
    $flags = @()
    if ($part.frames -lt 10) { return $flags }
    $race = $raceParts -contains $part.name
    $target = Get-Target $run
    $floor = 0
    $rule = Get-FloorRule $run
    if ($race -and $rule.judged) { $target = [math]::Max($target, $MinFps); $floor = $MinFps }
    # how frames were drawn (FrameLog): 'kept scene' and 'recorded scene' by the card; 'picture' by the processor,
    # the card only putting it on screen; 'software' by the processor, put on screen by Windows
    $drawn = $part.drawn.PSObject.Properties.Name
    if ($race -and $run.settings['renderer'] -like 'gpu*') {
        if ($drawn -contains 'software') { $flags += 'drawn in software' }
        if ($drawn -contains 'picture') { $flags += ('{0} frames drawn by the processor, shown by the card' -f $part.drawn.picture) }
    }
    $pacing = @()
    # frames the card held up are the card's (see below), so against a frame limit frames a second are counted
    # without them
    $heldMs = 0
    foreach ($h in @($part.cardHeld.holds)) { if ($null -ne $h) { $heldMs += [double]$h.ms } }
    $fps = $part.fps
    if ($heldMs -gt 0 -and $part.seconds * 1000 -gt $heldMs + 100) { $fps = $part.frames / ($part.seconds - $heldMs / 1000) }
    $slow = ''
    if ($race -and $floor -gt 0 -and $part.fps -lt $floor) {
        # the owner's floor, with no allowance, on the frames a second the report shows: a frame the card held up
        # kept the player waiting all the same
        $slow = '{0:N0} fps, under {1}' -f $part.fps, $floor
        if ($heldMs -gt 0) { $slow += ' ({0:N0} without the {1:N0} ms the card held frames up)' -f $fps, $heldMs }
    } elseif ($race -and $target -gt 0 -and $fps -lt $target * 0.9) {
        # 10% under a frame limit is allowed
        $slow = '{0:N0} fps, under {1}' -f $fps, $target
    }
    # a run Windows slowed to save power cannot prove a frame rate (Get-Inconclusive): too slow is only noted
    if ($slow -and (Get-Inconclusive $run)) { $flags += "note: $slow, not judged: $($run.power -join ' and ')" }
    elseif ($slow) { $pacing += $slow }
    # unevenness needs enough frames to tell: 100 over two seconds the card did not hold up (a menu's second
    # of 25 frames has one 99th-percentile frame); and frames that all come within the display's frame time
    # look even on it, however much quicker the rest are
    $enough = $part.frames -ge 100 -and $part.seconds - $heldMs / 1000 -ge 2
    $frameTime = if ($target -gt 0) { 1000 / $target } else { 0 }
    if ($enough -and $part.ms.p99 -gt $part.ms.median * 2.5 -and $part.ms.p99 - $part.ms.median -gt 4 -and
        $part.ms.p99 -gt $frameTime) {
        $pacing += ('uneven: 99% of frames within {0:N1} ms, half within {1:N1}' -f $part.ms.p99, $part.ms.median)
    }
    if ($enough -and $part.late -gt $part.frames * 0.05) {
        $pacing += ('{0} of {1} frames late' -f $part.late, $part.frames)
    }
    $refresh = Get-Refresh $run
    # off every monitor vsync can wait for Windows' clock instead of the monitor's refresh; then half the frames
    # come quicker than the refresh allows, which no screen shows, and its late frames are that clock's too.
    # Frames at the refresh or slower are the game's, and stay flags.
    $offClock = $refresh -gt 0 -and $part.ms.median -lt 0.98 * 1000 / $refresh
    if ($pacing.Count -gt 0 -and $run.settings['vsync'] -match '^on' -and $offClock) {
        $flags += ('note: paced by vsync off screen, on Windows'' clock (half the frames within {0:N1} ms, ' +
            'the monitor''s refresh {1:N1}): {2}') -f $part.ms.median, (1000 / $refresh), ($pacing -join ', ')
    } else {
        $flags += $pacing
    }
    # a stall the card held up (handing it the frame took that long) is the card's, not the game's drawing
    $held = if ($part.cardHeld) { [double]$part.cardHeld.longestMs } else { 0 }
    $limit = if ($race) { 100 } else { 250 }
    if ($part.ms.max -gt $limit -and ($race -or $part.name -notin @('start', 'loading', 'fullscreen switch'))) {
        if ($held -gt 0 -and $held -ge $part.ms.max - 100) {
            # Windows does this now and then to a window off every monitor: the test cannot tell whether a screen would
            $flags += ('note: the card held a frame up {0:N0} ms' -f $held)
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
$lost = @()
$plan = @(@{ name = 'yours'; extra = '' })
$n = 1
foreach ($s in $Set) { $plan += @{ name = "set$n"; extra = $s }; $n++ }
foreach ($r in $plan) {
    Write-Host "[live] $($r.name)$(if ($r.extra) { ": $($r.extra)" })..."
    $run = Invoke-Run $r.name $r.extra
    if ($null -eq $run) {
        $lost += "$($r.name) wrote no frames.json: see $(Join-Path $Out $r.name)\out.log"
        Write-Host "[live] $($lost[-1])"
        continue
    }
    $runs += $run
}
if ($runs.Count -eq 0) { exit 2 }

# a run that never wrote its frames is a flag, not a run left out
$flagged = $lost.Count -gt 0
$inconclusive = $false
$md = @('# Live test', '', "$(Get-Date -Format 'yyyy-MM-dd HH:mm'), $env:COMPUTERNAME", '')
if ($lost.Count -gt 0) { $md += @($lost | ForEach-Object { "**Flagged:** $_  " }) + '' }
foreach ($run in $runs) {
    $title = if ($run.extra) { "$($run.name): $($run.extra)" } else { 'Your settings' }
    $md += @("## $title", '')
    $md += @($run.settings.GetEnumerator() | Sort-Object Name | ForEach-Object { "- $($_.Name): $($_.Value)" })
    $runFlags = @(Get-RunFlags $run)
    if (@($runFlags | Where-Object { $_ -notlike 'note: *' }).Count -gt 0) { $flagged = $true }
    $why = Get-Inconclusive $run
    if ($why) { $inconclusive = $true; $md += @('', "**Inconclusive:** $why.") }
    if ($runFlags.Count -gt 0) { $md += @('', "**Whole run:** $($runFlags -join '; ')") }
    $md += @('', '| Part | Seconds | Frames/s | Median ms | 99% ms | Longest ms | Late | Card ms | Drawn | Flags |',
        '|---|---:|---:|---:|---:|---:|---:|---:|---|---|')
    Write-Host ''
    Write-Host "== $title"
    if ($why) { Write-Host "inconclusive: $why" }
    if ($runFlags.Count -gt 0) { Write-Host "whole run: $($runFlags -join '; ')" }
    Write-Host ('{0,-18} {1,6} {2,8} {3,7} {4,7} {5,8} {6,5} {7,6}  {8}' -f 'part', 's', 'fps', 'median', '99%',
        'longest', 'late', 'card', 'flags')
    foreach ($part in $run.parts) {
        $flags = @(Get-Flags $run $part)
        if (@($flags | Where-Object { $_ -notlike 'note: *' }).Count -gt 0) { $flagged = $true }
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
# a flag fails the run even when it is inconclusive: a stutter, a missing part or a menu the processor drew is
# one on any power
if ($flagged) { exit 1 }
if ($inconclusive) { Write-Host '[live] inconclusive: Windows saved power during the run; plug in and run again'; exit 3 }
exit 0
