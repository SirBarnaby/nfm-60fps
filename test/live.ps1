# The live test: plays the game the way you do, with your saved settings, and
# reports how fast and how evenly each part of it went on screen.
#
#   .\test\live.ps1                                  one run with your settings, at 1920x1080 fullscreen
#   .\test\live.ps1 -AsSaved                         at your saved window or fullscreen resolution instead
#   .\test\live.ps1 -Split                           split screen instead
#   .\test\live.ps1 -Set 'nfm.overlay=off'           one run more for each entry,
#   .\test\live.ps1 -Set 'nfm.overlay=off','nfm.smootheffects=false nfm.runahead=false'
#                                                    each entry's -D settings on top of yours
#   .\test\live.ps1 -Out C:\somewhere                where the runs go (default: %TEMP%\nfm-live\<time>)
#   .\test\live.ps1 -NoShots                         no screenshots: each one stops the frames for a moment
#   .\test\live.ps1 -Limits 'unlimited','120'        the frame limits played too, each on top of your settings
#                                                    (the default; your run counts for its own limit), and
#                                                    compared; -Limits none plays your settings alone
#   .\test\live.ps1 -MinFps 300                      the frame rate a race part must reach, with no allowance
#                                                    under it (default 250, the owner's target:
#                                                    openspec\specs\performance); 0 turns it off. Not
#                                                    judged when your settings hold frames under it (a
#                                                    lower frame limit, Match display, Variable refresh,
#                                                    vsync on: then the limit is the target). On battery
#                                                    or with Battery saver on, or not at 1920x1080
#                                                    fullscreen (-AsSaved), the run is inconclusive
#
# The specs ask for the floor at 1920x1080 fullscreen, so by default each run is put there on top of your saved
# settings: the game has no command-line setting for it (it reads fullscreen and its resolution from
# data\cookies.radq, where it saves them), so the run plays in a folder in %TEMP% of hard links to the files the
# game only reads where it starts (data, mods, replays, lib\dlls, madness.aot: no copies, unless a link cannot be
# made), a copy of cache (the game updates its files, which must stay yours) and its own data\cookies.radq: yours,
# with fullscreen on at 1920x1080 in place of your fullscreen and its resolution. The folder goes when the run
# ends; the game's log from it is kept in the run's folder (logs\).
# report.md says which resolution each run had. A run that is not at 1920x1080 fullscreen (-AsSaved, or a monitor
# that cannot show it) cannot prove the floor: it is inconclusive, its frame rates under the floor are notes.
#
# What it plays: the main menu, fullscreen switched off and on again (a new
# window and the graphics card started again, as F11 does), the settings
# screen, car and stage select, a race, the pause menu, the instant replay, the
# race again and the post-race highlight; with -Split, a split screen race, the
# instant replay, the race again, the results and a race again from them (split
# screen has no highlight). It reads your settings as the game
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
# stalls (the fullscreen switch may pause up to 500 ms as the player sees it,
# as the owner allows; elsewhere
# told apart from frames the card held up: the game was ready, the
# card or Windows took that long to take the frame), the card near its time
# per frame, and the race moving unevenly as the player sees it, however evenly the frames come: each frame
# shows the race at a moment (frames.json's motion), and a race part whose frames stand the race still and then
# jump it on is flagged 'motion uneven' or 'motion hitch' (Get-MotionFlags). The owner found the game smooth at
# a 120 fps limit and stuttering with none, which frame times did not show: so it plays your settings with no
# frame limit and at 120 fps too (-Limits), and flags a race part that stutters with no limit but not at 120
# (openspec\specs\done-and-testing). What reaches the monitor (Windows' own record of when each frame was shown,
# PresentMon) needs a window on screen and admin rights: not measured here (docs\live-test.md).
# A run is flagged as a whole when the game's test did not go the
# whole way, Java did not load the startup cache madness64.ini names (build.ps1's
# madness.aot: a -Quick build has none; Java's log of it goes to aot.log in the
# run's folder), a race part it plays is missing or too short to judge (each time
# it comes up is graded on its own: 'race (2nd time)' is the race resumed after
# the instant replay, never added to the first), or it
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
    [switch]$AsSaved,
    [switch]$NoShots,
    [string[]]$Set = @(),
    [string]$Out,
    [int]$MinFps = 250,
    [string[]]$Limits = @('unlimited', '120')
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

# The resolution the specs' floor is for, as the game's settings.txt words it, and the saved settings that put a
# run there (GameSparker.loadFullscreen, loadFullscreenResolution); the files and folders the game only reads
# relative to where it starts (GameFiles, GameSparker's lib\dlls, Replays, the -XX:AOTCache in madness64.ini),
# which a run's game folder links to, and those it also writes (SteamAcoustics' cache: it touches a probe file it
# reads and rewrites one it bakes), which it copies (New-GameFolder)
$floorWindow = 'fullscreen 1920x1080'
$override = [ordered]@{ 'fullscreen.dat' = 1; 'fullresw.dat' = 1920; 'fullresh.dat' = 1080 }
$cookies = Join-Path $root 'data\cookies.radq'
$gameReads = @('data', 'mods', 'replays', 'lib\dlls', 'madness.aot')
$gameWrites = @('cache')
# the startup cache Java is told to load (madness64.ini's -XX:AOTCache, build.ps1's madness.aot), '' for none
$aotCache = [string]($launcher | Where-Object { $_ -like '-XX:AOTCache=*' } | Select-Object -First 1) -replace '^-XX:AOTCache=', ''
Add-Type -AssemblyName System.IO.Compression

# parts of a race, which should go at the frame limit; the rest are menus, drawn once a tick
$raceParts = @('countdown', 'race', 'split countdown', 'split race', 'instantreplay', 'gamehighlight')
# the race parts the test plays, in order (GameSparker.runAutoTest, runSplitTest): one player races, pauses for the
# instant replay, races on and ends in the post-race highlight; split screen races, pauses for the instant replay,
# races on, then races again from the results (it has no highlight). Each time a part comes up it is graded on
# its own, never added to another: a run missing one, or with one too short to judge (the 100 frames over 2
# seconds that unevenness needs), did not show what it was meant to.
$expectedParts = if ($Split) { @('split countdown', 'split race', 'instantreplay', 'split race', 'split countdown', 'split race') }
else { @('countdown', 'race', 'instantreplay', 'race', 'gamehighlight') }
$enoughFrames = 100
$enoughSeconds = 2
# the race standing still or jumping about for longer than this is a hitch, as a frame this long is (Get-Stall)
$motionHitchMs = 100

# The frame limits to compare (-Limits): 'unlimited' or a number of frames a second; 'none' for none.
$limitWanted = @($Limits | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().ToLowerInvariant() } |
    Where-Object { $_ -and $_ -ne 'none' } | Select-Object -Unique)
foreach ($limit in $limitWanted) {
    if ($limit -notmatch '^(unlimited|[1-9]\d*)$') { throw "-Limits takes 'unlimited', a number or 'none', not '$limit'" }
}

# A frame limit as the game's settings.txt words it (GameSparker.describeFpsLimit).
function Get-LimitWords([string]$limit) {
    if ($limit -eq 'unlimited') { return 'unlimited' }
    return "$limit fps"
}

# A race part's name the nth time it comes up in a run: 'race', then 'race (2nd time)', and so on. Menus keep theirs.
function Get-PartLabel([string]$name, [int]$nth) {
    if ($nth -le 1 -or $raceParts -notcontains $name) { return $name }
    $suffix = if ($nth % 100 -in 11..13) { 'th' } else { @('th', 'st', 'nd', 'rd', 'th', 'th', 'th', 'th', 'th', 'th')[$nth % 10] }
    return "$name ($nth$suffix time)"
}

# The labels of a run's parts, in order (Get-PartLabel).
function Get-PartLabels($parts) {
    $seen = @{}
    foreach ($part in $parts) {
        $seen[$part.name] = 1 + [int]$seen[$part.name]
        Get-PartLabel $part.name $seen[$part.name]
    }
}

function Invoke-Run([string]$name, [string]$extra) {
    $dir = Join-Path $Out $name
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    New-Item -ItemType Directory -Force $dir | Out-Null
    $arguments = @($launcher) + @('-Dnfm.debug=false', '-Dnfm.live=true', "-Dnfm.autotest=$dir",
        "-Dnfm.sound.render=$dir\sound.wav")
    # Java's own account of the startup cache, as it starts (Get-StartupCacheProblem)
    if ($aotCache) { $arguments += "-Xlog:aot=info,cds=info:file=$dir\aot.log" }
    if ($Split) { $arguments += '-Dnfm.autotest.split=saved' }
    if ($NoShots) { $arguments += '-Dnfm.autotest.shots=false' }
    $arguments += @($extra -split ' ' | Where-Object { $_ } | ForEach-Object { "-D$_" })
    $arguments += @('-jar', $jar)
    $manifest = Write-Manifest $dir $extra
    if ($manifest.jarOlderThan) {
        Write-Host "[live] note: madness.jar is older than $($manifest.jarOlderThan): this tests the last build, not the files as they are (run build.ps1 first)"
    }
    if ($aotCache -and -not (Test-Path (Join-Path $root $aotCache))) {
        Write-Host "[live] note: there is no $aotCache, so Java starts without its startup cache and the run is flagged (build.ps1 without -Quick makes it)"
    }
    # the game plays in its own folder with -AsSaved, otherwise in one at 1920x1080 fullscreen (New-GameFolder)
    $game = if ($AsSaved) { $root } else { New-GameFolder $name }
    try {
        # Windows is asked about power before, every 2 seconds during and after the run, so a plug pulled for a
        # moment counts
        $power = @(Get-PowerSaving)
        $p = Start-Process $java -ArgumentList $arguments -WorkingDirectory $game -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "$dir\out.log" -RedirectStandardError "$dir\err.log"
        $stopAt = (Get-Date).AddMinutes(7)
        while (-not $p.WaitForExit(2000)) {
            $power += @(Get-PowerSaving)
            if ((Get-Date) -gt $stopAt) {
                $p.Kill()
                $p.WaitForExit(10000) | Out-Null
                Write-Host "[live] $name ran for 7 minutes and was stopped"
                break
            }
        }
        $power = @($power + @(Get-PowerSaving) | Select-Object -Unique)
    } finally {
        if ($game -ne $root) { Remove-GameFolder $game $dir }
    }
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
    $settings['resolution'] = $manifest.resolution
    $noCache = Get-StartupCacheProblem $dir
    $settings['startup cache'] = if (-not $aotCache) { 'none named in madness64.ini' }
    elseif ($noCache) { "$aotCache, not loaded" } else { "$aotCache, loaded" }
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
        noCache = $noCache
    }
}

# Why Java did not load the startup cache madness64.ini names (-XX:AOTCache: build.ps1's madness.aot), '' when it
# did or none is named. A run without it starts and warms up slower than the game does with it, so it does not show
# the game as it ships. From Java's own log of the cache (aot.log, -Xlog:aot) and what it printed (out.log): an
# error or warning about it, or the cache not found, says why; with neither, a log that never says the cache's
# classes are in use ("Using AOT-linked classes") means it was not loaded.
function Get-StartupCacheProblem([string]$dir) {
    if (-not $aotCache) { return '' }
    $lines = @(foreach ($file in 'aot.log', 'out.log', 'err.log') {
            $path = Join-Path $dir $file
            if (Test-Path $path) { Get-Content $path | Where-Object { $_ -match '\]\[(aot|cds)\b' } }
        })
    $why = @($lines | Where-Object { $_ -match '\]\[(error|warning) *\]\[(aot|cds)|cache not found' } |
        ForEach-Object { ($_ -replace '^(\[[^\]]*\])+\s*', '').Trim() } | Select-Object -Unique)
    if ($why.Count -gt 0) { return ($why | Select-Object -First 3) -join '; ' }
    if (@($lines | Where-Object { $_ -match 'Using AOT-linked classes:' }).Count -eq 0) {
        return "Java's log of it (aot.log) never says the cache is in use"
    }
    return ''
}

# What a run is made from, written to manifest.json in its folder before the game starts, so its evidence can be
# matched to a build (test\blind-review.ps1 does): the commit checked out, whether the tree had changes and which
# (git status --porcelain lines), madness.jar's SHA-256 and when it was built, the newest source file (src\,
# lib\) if one is newer than the jar, and build.ps1's build-info.json (what the jar was built from) as buildInfo.
# commit is null outside git.
function Write-Manifest([string]$dir, [string]$extra) {
    $ErrorActionPreference = 'Continue'   # git's own messages, if any, are not the test's
    $commit = [string](& git -C $root rev-parse --verify --quiet HEAD 2>$null)
    $changed = @(& git -C $root status --porcelain 2>$null)
    $ErrorActionPreference = 'Stop'
    $jarFile = Get-Item $jar
    $newest = @(Get-ChildItem (Join-Path $root 'src') -Recurse -File -Filter '*.java') +
    @(Get-ChildItem (Join-Path $root 'lib') -File -Filter '*.jar' -ErrorAction SilentlyContinue) |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    # build.ps1's own record of what it built madness.jar from (null when there is none: an older build)
    $infoFile = Join-Path $root 'build-info.json'
    $buildInfo = if (Test-Path $infoFile) { Get-Content $infoFile -Raw | ConvertFrom-Json } else { $null }
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
        buildInfo = $buildInfo
        split = [bool]$Split
        set = $extra
        resolution = Get-ResolutionText
    }
    [IO.File]::WriteAllText((Join-Path $dir 'manifest.json'), ($manifest | ConvertTo-Json -Depth 4),
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
    $info = $manifest.buildInfo
    if (-not $info) {
        $text += ', no build-info.json (build.ps1 writes one)'
    } elseif ($info.jarSha256 -ne $manifest.jarSha256) {
        $text += ', not the jar build-info.json describes'
    } else {
        $text += ', built from {0}{1}' -f $(if ($info.commit) { ([string]$info.commit).Substring(0, 10) } else { 'no commit' }),
        $(if ($info.dirty) { " with $(@($info.changed).Count) uncommitted changes" } else { '' })
    }
    return $text
}

# Saved settings read as the game reads them (GameSparker.readcookie: the last entry whose name holds the key, its
# lines run together as a number), -1 for one never saved.
function Read-Cookies([string]$path, [string[]]$keys) {
    $values = @{}
    foreach ($k in $keys) { $values[$k] = -1 }
    if (-not (Test-Path $path)) { return $values }
    $zip = [IO.Compression.ZipArchive]::new([IO.File]::OpenRead($path), [IO.Compression.ZipArchiveMode]::Read)
    try {
        foreach ($e in $zip.Entries) {
            foreach ($k in $keys | Where-Object { $e.FullName.Contains($_) }) {
                $reader = [IO.StreamReader]::new($e.Open())
                try { $text = $reader.ReadToEnd() -replace '[\r\n]', '' } finally { $reader.Dispose() }
                $n = 0
                $values[$k] = if ([int]::TryParse($text, [ref]$n)) { $n } else { -1 }
            }
        }
    } finally { $zip.Dispose() }
    return $values
}

# The resolution a run plays at, in a line for report.md and manifest.json: yours as saved with -AsSaved, otherwise
# 1920x1080 fullscreen on top of your settings.
function Get-ResolutionText {
    $saved = Read-Cookies $cookies @('fullscreen', 'fullresw', 'fullresh')
    $yours = if ($saved['fullscreen'] -ne 1) { 'in a window' }
    elseif ($saved['fullresw'] -gt 0 -and $saved['fullresh'] -gt 0) { 'fullscreen at {0}x{1}' -f $saved['fullresw'], $saved['fullresh'] }
    else { "fullscreen at the monitor's own resolution" }
    if ($AsSaved) { return "as saved, $yours (-AsSaved)" }
    return "$floorWindow, put on top of your saved settings for the run (saved: $yours; -AsSaved runs at that)"
}

# A copy of the saved settings with some put in place (name.dat = value), each written as the game writes it, a
# line; the rest as they were. Those put in place go last, so they are the ones the game reads (Read-Cookies).
function Write-Cookies([string]$from, [string]$to, $set) {
    $out = [IO.Compression.ZipArchive]::new([IO.File]::Create($to), [IO.Compression.ZipArchiveMode]::Create)
    try {
        if (Test-Path $from) {
            $in = [IO.Compression.ZipArchive]::new([IO.File]::OpenRead($from), [IO.Compression.ZipArchiveMode]::Read)
            try {
                foreach ($e in $in.Entries | Where-Object { -not $set.Contains($_.FullName) }) {
                    $source = $e.Open()
                    $copy = $out.CreateEntry($e.FullName).Open()
                    try { $source.CopyTo($copy) } finally { $copy.Dispose(); $source.Dispose() }
                }
            } finally { $in.Dispose() }
        }
        foreach ($k in $set.Keys) {
            $writer = [IO.StreamWriter]::new($out.CreateEntry($k).Open(), [Text.UTF8Encoding]::new($false))
            try { $writer.Write("$($set[$k])`n") } finally { $writer.Dispose() }
        }
    } finally { $out.Dispose() }
}

# The folder a run plays in at 1920x1080 fullscreen (see the top): hard links to what the game only reads where it
# starts ($gameReads), so the game's own files are what it reads, copies of what it also writes ($gameWrites), so
# nothing the run does reaches yours through a link, and its own data\cookies.radq ($override on top of yours).
# A file to link is copied where a link cannot be made (another drive), and so is a read-only one, whose flag
# deleting the link would clear on the game's own file. The game's music temporaries (data\music\temp_*, which it
# writes and deletes) are left out.
function New-GameFolder([string]$name) {
    if (-not ('NfmLive.Links' -as [type])) {
        Add-Type -Namespace NfmLive -Name Links -MemberDefinition @'
[DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
public static extern bool CreateHardLink(string lpFileName, string lpExistingFileName, IntPtr lpSecurityAttributes);
'@
    }
    $game = Join-Path ([IO.Path]::GetTempPath()) "nfm-live-game-$PID-$name"
    if (Test-Path $game) { Remove-GameFolder $game '' }
    New-Item -ItemType Directory $game | Out-Null
    $musicTemp = Join-Path $root 'data\music\temp_*'
    foreach ($rel in $gameReads + $gameWrites) {
        $from = Join-Path $root $rel
        if (-not (Test-Path -LiteralPath $from)) { continue }
        $copy = $gameWrites -contains $rel
        $top = Get-Item -LiteralPath $from -Force
        # a folder comes before what is in it
        $items = @($top)
        if ($top.PSIsContainer) { $items += @(Get-ChildItem -LiteralPath $from -Recurse -Force) }
        foreach ($item in $items) {
            $to = Join-Path $game $item.FullName.Substring($root.Length).TrimStart('\')
            if ($item.PSIsContainer) {
                New-Item -ItemType Directory -Force $to | Out-Null
            } elseif ($item.FullName -eq $cookies -or $item.FullName -like $musicTemp) {
                continue
            } elseif ($copy -or $item.IsReadOnly -or
                -not [NfmLive.Links]::CreateHardLink($to, $item.FullName, [IntPtr]::Zero)) {
                Copy-Item -LiteralPath $item.FullName $to
            }
        }
    }
    New-Item -ItemType Directory -Force (Join-Path $game 'data') | Out-Null
    Write-Cookies $cookies (Join-Path $game 'data\cookies.radq') $override
    return $game
}

# Deletes a run's game folder: links and its own files, never the game's. The game's log from it (logs\) is kept
# in the run's folder first. Tried again for a few seconds while the antivirus or the game, closing, holds a file.
function Remove-GameFolder([string]$game, [string]$dir) {
    $logs = Join-Path $game 'logs'
    if ($dir -and (Test-Path $logs)) { Copy-Item -LiteralPath $logs (Join-Path $dir 'logs') -Recurse -Force }
    foreach ($attempt in 1..10) {
        try {
            Remove-Item -LiteralPath $game -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq 10) { Write-Host "[live] note: could not delete $game ($_)"; return }
            Start-Sleep -Seconds 1
        }
    }
}

# Flags for a run as a whole: the test did not go the whole way, or a race part it plays is missing or too short
# to judge, so its numbers cannot say the game is fine.
function Get-RunFlags($run) {
    $flags = @()
    if (-not $run.finished) { $flags += "the test did not finish: $($run.ending)" }
    if ($run.noCache) { $flags += "no startup cache: Java did not load $aotCache ($($run.noCache)), rebuild with build.ps1" }
    $wanted = @{}
    foreach ($name in $expectedParts) {
        # the nth time this part should come up, matched with the nth time it did
        $wanted[$name] = 1 + [int]$wanted[$name]
        $label = Get-PartLabel $name $wanted[$name]
        $came = @($run.parts | Where-Object { $_.name -eq $name })
        if ($came.Count -lt $wanted[$name]) {
            $flags += "no $label"
            continue
        }
        $part = $came[$wanted[$name] - 1]
        if ($part.frames -lt $enoughFrames -or $part.seconds -lt $enoughSeconds) {
            $flags += ('{0}: {1:N0} frames over {2:N1} s, too few to judge (needs {3} over {4} s)' -f $label, $part.frames,
                $part.seconds, $enoughFrames, $enoughSeconds)
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
# hold frames under it. why: the note saying so, once a run. offWindow: the window the run had when it was not the
# floor's 1920x1080 fullscreen, which leaves the floor unproven (Get-Inconclusive). (On battery see there too.)
function Get-FloorRule($run) {
    $cap = Get-Cap $run
    if ($MinFps -le 0) { return [pscustomobject]@{ judged = $false; why = ''; offWindow = '' } }
    if (-not $cap.known) {
        return [pscustomobject]@{ judged = $false; offWindow = ''
            why = "the frame limit follows the monitor's refresh rate, which the run could not read: the $MinFps fps floor is not judged" }
    }
    if ($cap.fps -gt 0 -and $cap.fps -lt $MinFps) {
        return [pscustomobject]@{ judged = $false; offWindow = ''
            why = ('frames are held to {0:N0} a second by {1}, under the {2} fps floor: race parts are judged against {0:N0}' -f
                $cap.fps, $cap.why, $MinFps) }
    }
    $window = [string]$run.settings['window']
    if ($window -ne $floorWindow) {
        return [pscustomobject]@{ judged = $false; why = ''; offWindow = $(if ($window) { $window } else { 'unknown' }) }
    }
    return [pscustomobject]@{ judged = $true; why = ''; offWindow = '' }
}

# Why a run cannot prove what it is meant to, one line each, none when it can. Windows saved power during it in a
# way the owner did not choose (on battery it slows the processor and the card; Battery saver too; the game itself
# never holds a test run to its battery cap, so the slowing is Windows' alone): its frame rates are notes, not
# passes or flags. Or it was not at 1920x1080 fullscreen, where the specs ask for the floor: its frame rates under
# the floor are notes (a frame limit is still judged).
function Get-Inconclusive($run) {
    if (@($run.power).Count -gt 0) {
        ('Windows was saving power during the run ({0}), when it slows the processor and the graphics card, so ' +
            'the run cannot prove {1} fps or any frame rate: plug in, turn Battery saver off and run again') -f
        ($run.power -join ' and '), $MinFps
    }
    $rule = Get-FloorRule $run
    if ($rule.offWindow) {
        $how = if ($AsSaved) { 'run without -AsSaved to put it there' }
        else { 'the game did not take the 1920x1080 fullscreen this test put in its settings (a monitor that cannot show it?)' }
        ('the run was at {0}, not the {1} the specs ask the {2} fps floor at, so it cannot prove the floor: {3}' -f
            $rule.offWindow, $floorWindow, $MinFps, $how)
    }
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

# A race part's frames the processor drew with the GPU renderer on (FrameLog): 'kept scene' and 'recorded scene' are
# drawn by the card; 'picture' by the processor, the card only putting it on screen; 'software' by the processor,
# put on screen by Windows.
function Get-DrawnFlags($run, $part) {
    if ($run.settings['renderer'] -notlike 'gpu*') { return }
    $drawn = $part.drawn.PSObject.Properties.Name
    if ($drawn -contains 'software') { 'drawn in software' }
    if ($drawn -contains 'picture') { '{0} frames drawn by the processor, shown by the card' -f $part.drawn.picture }
}

# Flags, and notes (starting "note: ") for what this window, off every monitor, cannot show.
function Get-Flags($run, $part) {
    $flags = @()
    $race = $raceParts -contains $part.name
    if ($part.frames -lt 10) {
        # too few frames for a frame rate or pacing, which fails a race part (one the test plays is a whole-run flag
        # too, Get-RunFlags); how its frames were drawn and a long pause still show, and a long pause in the
        # fullscreen switch is one however few frames it showed
        if ($race) {
            $flags += 'only {0} frames, too few to judge' -f $part.frames
            $flags += @(Get-DrawnFlags $run $part)
            $flags += @(Get-Stall $part $true)
        } elseif ($part.name -eq 'fullscreen switch') {
            $flags += @(Get-Stall $part $false)
        }
        return $flags
    }
    $target = Get-Target $run
    $floor = 0
    $rule = Get-FloorRule $run
    if ($race -and $rule.judged) { $target = [math]::Max($target, $MinFps); $floor = $MinFps }
    if ($race) { $flags += @(Get-DrawnFlags $run $part) }
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
    if ($slow -and @($run.power).Count -gt 0) { $flags += "note: $slow, not judged: $($run.power -join ' and ')" }
    elseif ($slow) { $pacing += $slow }
    # nor can one at another resolution prove the floor
    if ($race -and $rule.offWindow -and $part.fps -lt $MinFps) {
        $flags += 'note: {0:N0} fps, under {1}, not judged at {2}' -f $part.fps, $MinFps, $rule.offWindow
    }
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
    if ($race) { $flags += @(Get-MotionFlags $part) }
    $flags += @(Get-Stall $part $race)
    if ($target -gt 0 -and $part.gpuMs -gt 900 / $target) {
        $flags += ('card takes {0:N1} ms of {1:N1}' -f $part.gpuMs, (1000 / $target))
    }
    return $flags
}

# Whether a race part showed the race moving evenly, as the player sees it (frames.json's motion, FrameLog.Motion):
# frames can come evenly while what they show stands still and then jumps, which frame times cannot tell. Each
# frame shows the race at a moment; moving evenly, that moment keeps the same distance behind the time the frame
# went on screen. A frame off that distance (the median around it) by over 4 ms shows the race stopped or jumped.
# 'motion uneven': over 1% of the frames are (99% are not within 4 ms of even). 'motion hitch': frames in a row are
# for over 100 ms, the race standing still or jumping about that long, the race hitch limit (Get-Stall). Frames near
# a part's start and end are not judged (FrameLog.Motion); a part with too few judged frames is a note.
function Get-MotionFlags($part) {
    $m = $part.motion
    if (-not $m) { return 'note: no motion in frames.json (a build from before it was measured)' }
    if ([int]$m.judged -lt $enoughFrames) {
        return 'note: the motion was measured on {0} frames, too few to judge' -f [int]$m.judged
    }
    if ([double]$m.p99Ms -gt [double]$m.strayMs) {
        'motion uneven: 99% of frames within {0:N1} ms of even, {1} of {2} off by over {3} ms' -f $m.p99Ms, $m.strays,
        $m.judged, $m.strayMs
    }
    if ([double]$m.stretchMs -gt $motionHitchMs) {
        'motion hitch: the race stood still or jumped for {0:N0} ms at {1:N1} s' -f $m.stretchMs, $m.stretchAt
    }
}

# A part's longest frame as a hitch (race) or a stall (menu), or nothing: over 100 ms in a race, 250 in a menu, and
# 500 in the fullscreen switch, which the owner allows a pause of up to half a second as the player sees it while
# the window and the card restart (openspec\specs\performance, "No stuttering, ever"), so there the time the card
# held a frame counts too. The game's start and loading are not judged.
function Get-Stall($part, [bool]$race) {
    $switch = $part.name -eq 'fullscreen switch'
    $limit = if ($race) { 100 } elseif ($switch) { 500 } else { 250 }
    if ($part.ms.max -le $limit -or (-not $race -and $part.name -in @('start', 'loading'))) { return }
    # elsewhere a stall the card held up (handing it the frame took that long) is the card's, not the game's drawing
    $held = if ($part.cardHeld) { [double]$part.cardHeld.longestMs } else { 0 }
    if (-not $switch -and $held -gt 0 -and $held -ge $part.ms.max - 100) {
        # Windows does this now and then to a window off every monitor: the test cannot tell whether a screen would
        return ('note: the card held a frame up {0:N0} ms' -f $held)
    }
    $text = '{0} of {1:N0} ms' -f $(if ($race) { 'hitch' } else { 'stall' }), $part.ms.max
    if ($switch -and $held -gt 0) { $text += ' (the card held a frame up {0:N0} ms, counted too)' -f $held }
    return $text
}

$runs = @()
$lost = @()
# Plays one run of the plan and keeps it ($runs), or says it wrote no frames ($lost).
function Invoke-Planned($r) {
    Write-Host "[live] $($r.name)$(if ($r.extra) { ": $($r.extra)" })..."
    $run = Invoke-Run $r.name $r.extra
    if ($null -eq $run) {
        $script:lost += "$($r.name) wrote no frames.json: see $(Join-Path $Out $r.name)\out.log"
        Write-Host "[live] $($script:lost[-1])"
        return
    }
    $run | Add-Member title $r.title
    $script:runs += $run
}
Invoke-Planned @{ name = 'yours'; extra = ''; title = 'Your settings' }
$n = 1
foreach ($s in $Set) { Invoke-Planned @{ name = "set$n"; extra = $s; title = "set${n}: $s" }; $n++ }
# each frame limit to compare (-Limits) on top of your settings, unless your own run already had it
$yours = $runs | Where-Object { $_.name -eq 'yours' } | Select-Object -First 1
$limitRuns = @()
foreach ($limit in $limitWanted) {
    $words = Get-LimitWords $limit
    if ($yours -and $yours.settings['fps limit'] -eq $words) {
        $limitRuns += [pscustomobject]@{ words = $words; run = $yours }
        continue
    }
    $name = "limit-$limit"
    Invoke-Planned @{ name = $name; extra = "nfm.fps=$limit"; title = "Your settings at the frame limit: $words" }
    $run = $runs | Where-Object { $_.name -eq $name } | Select-Object -First 1
    if ($run) { $limitRuns += [pscustomobject]@{ words = $words; run = $run } }
}
if ($runs.Count -eq 0) { exit 2 }

# a run that never wrote its frames is a flag, not a run left out
$flagged = $lost.Count -gt 0
$inconclusive = @()
$md = @('# Live test', '', "$(Get-Date -Format 'yyyy-MM-dd HH:mm'), $env:COMPUTERNAME", '')
if ($lost.Count -gt 0) { $md += @($lost | ForEach-Object { "**Flagged:** $_  " }) + '' }
foreach ($run in $runs) {
    $title = $run.title
    $md += @("## $title", '')
    $md += @($run.settings.GetEnumerator() | Sort-Object Name | ForEach-Object { "- $($_.Name): $($_.Value)" })
    $runFlags = @(Get-RunFlags $run)
    if (@($runFlags | Where-Object { $_ -notlike 'note: *' }).Count -gt 0) { $flagged = $true }
    $md += @('', "**Resolution:** $($run.settings['resolution']); the game reported $($run.settings['window']).")
    $why = @(Get-Inconclusive $run)
    if ($why.Count -gt 0) { $inconclusive += $why; $md += @($why | ForEach-Object { '', "**Inconclusive:** $_." }) }
    if ($runFlags.Count -gt 0) { $md += @('', "**Whole run:** $($runFlags -join '; ')") }
    $md += @('', ('| Part | Seconds | Frames/s | Median ms | 99% ms | Longest ms | Late | Card ms | Motion 99% ms |' +
            ' Motion off in a row ms | Drawn | Flags |'), '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|')
    Write-Host ''
    Write-Host "== $title"
    Write-Host "resolution: $($run.settings['resolution'])"
    foreach ($line in $why) { Write-Host "inconclusive: $line" }
    if ($runFlags.Count -gt 0) { Write-Host "whole run: $($runFlags -join '; ')" }
    Write-Host ('{0,-26} {1,6} {2,8} {3,7} {4,7} {5,8} {6,5} {7,6} {8,7} {9,6}  {10}' -f 'part', 's', 'fps',
        'median', '99%', 'longest', 'late', 'card', 'motion', 'in row', 'flags')
    # a race part that comes up again is named for the time it is (Get-PartLabel), in the table and the lists below
    $labels = @(Get-PartLabels $run.parts)
    for ($i = 0; $i -lt $run.parts.Count; $i++) {
        $part = $run.parts[$i]
        $flags = @(Get-Flags $run $part)
        if (@($flags | Where-Object { $_ -notlike 'note: *' }).Count -gt 0) { $flagged = $true }
        $drawn = ($part.drawn.PSObject.Properties | ForEach-Object { "$($_.Name) $($_.Value)" }) -join ', '
        if ($part.screenshotFrames) { $drawn += "; $($part.screenshotFrames) screenshot frames left out" }
        # the motion's columns are blank for a part that showed no race moving
        $moved = $part.motion -and [int]$part.motion.judged -gt 0
        $motionP99 = if ($moved) { '{0:N1}' -f $part.motion.p99Ms } else { '' }
        $motionRow = if ($moved) { '{0:N0}' -f $part.motion.stretchMs } else { '' }
        $md += ('| {0} | {1:N1} | {2:N1} | {3:N2} | {4:N2} | {5:N1} | {6} | {7:N2} | {8} | {9} | {10} | {11} |' -f
            $labels[$i], $part.seconds, $part.fps, $part.ms.median, $part.ms.p99, $part.ms.max, $part.late,
            $part.gpuMs, $motionP99, $motionRow, $drawn, ($flags -join '; '))
        Write-Host ('{0,-26} {1,6:N1} {2,8:N1} {3,7:N2} {4,7:N2} {5,8:N1} {6,5} {7,6:N2} {8,7} {9,6}  {10}' -f
            $labels[$i], $part.seconds, $part.fps, $part.ms.median, $part.ms.p99, $part.ms.max, $part.late,
            $part.gpuMs, $motionP99, $motionRow, ($flags -join '; '))
    }
    $md += ''
    for ($i = 0; $i -lt $run.parts.Count; $i++) {
        $part = $run.parts[$i]
        if ($raceParts -notcontains $part.name -or -not $part.slowest) { continue }
        $md += ("Slowest frames in $($labels[$i]): " + (($part.slowest | ForEach-Object {
                        '{0:N1} ms at {1:N1} s' -f $_.ms, $_.at }) -join ', ') + '  ')
    }
    $holds = @(for ($i = 0; $i -lt $run.parts.Count; $i++) {
            foreach ($h in @($run.parts[$i].cardHeld.holds)) {
                if ($null -eq $h) { continue }
                '{0:N0} ms in {1} at {2:N1} s{3}' -f $h.ms, $labels[$i], $h.at, $(if ($h.after) { " ($($h.after))" })
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
    $md += @('## Races compared', '',
        '| Run | Frames/s | Median ms | 99% ms | Longest ms | Motion 99% ms | Motion off in a row ms |',
        '|---|---:|---:|---:|---:|---:|---:|')
    foreach ($run in $runs) {
        $race = $run.parts | Where-Object { $_.name -eq 'race' } | Sort-Object frames -Descending | Select-Object -First 1
        if ($race) {
            $md += ('| {0} | {1:N1} | {2:N2} | {3:N2} | {4:N1} | {5} | {6} |' -f $run.title, $race.fps, $race.ms.median,
                $race.ms.p99, $race.ms.max, $(if ($race.motion) { '{0:N1}' -f $race.motion.p99Ms }),
                $(if ($race.motion) { '{0:N0}' -f $race.motion.stretchMs }))
        }
    }
    $md += ''
}
# The owner found the game smooth at a 120 fps limit and stuttering with none (openspec\specs\done-and-testing,
# "Stutter with no frame limit is caught"): each race part with no limit next to the same part at each limit
# (-Limits), flagged where it showed the race moving unevenly with no limit but evenly at the limit.
$open = $limitRuns | Where-Object { $_.words -eq 'unlimited' } | Select-Object -First 1
foreach ($capped in @($limitRuns | Where-Object { $_.words -ne 'unlimited' })) {
    if (-not $open) { break }
    $md += @("## No frame limit and $($capped.words) compared", '',
        ('| Part | Frames/s, none | Frames/s, {0} | Motion 99% ms, none | Motion 99% ms, {0} |' +
            ' Off in a row ms, none | Off in a row ms, {0} | Flags |') -f $capped.words,
        '|---|---:|---:|---:|---:|---:|---:|---|')
    Write-Host ''
    Write-Host "== No frame limit and $($capped.words) compared (motion 99% ms / off in a row ms)"
    $theirs = @{}
    $cappedLabels = @(Get-PartLabels $capped.run.parts)
    for ($i = 0; $i -lt $capped.run.parts.Count; $i++) { $theirs[$cappedLabels[$i]] = $capped.run.parts[$i] }
    $openLabels = @(Get-PartLabels $open.run.parts)
    for ($i = 0; $i -lt $open.run.parts.Count; $i++) {
        $part = $open.run.parts[$i]
        $other = $theirs[$openLabels[$i]]
        if ($raceParts -notcontains $part.name -or -not $other -or -not $part.motion -or -not $other.motion) { continue }
        $openUneven = @(Get-MotionFlags $part | Where-Object { $_ -notlike 'note: *' }).Count -gt 0
        $cappedUneven = @(Get-MotionFlags $other | Where-Object { $_ -notlike 'note: *' }).Count -gt 0
        $flag = ''
        if ($openUneven -and -not $cappedUneven) {
            $flag = "stutters with no frame limit, not at $($capped.words)"
            $flagged = $true
        }
        $md += ('| {0} | {1:N1} | {2:N1} | {3:N1} | {4:N1} | {5:N0} | {6:N0} | {7} |' -f $openLabels[$i], $part.fps,
            $other.fps, $part.motion.p99Ms, $other.motion.p99Ms, $part.motion.stretchMs, $other.motion.stretchMs, $flag)
        Write-Host ('{0,-26} none {1,7:N1} / {2,5:N0}   {3} {4,7:N1} / {5,5:N0}  {6}' -f $openLabels[$i],
            $part.motion.p99Ms, $part.motion.stretchMs, $capped.words, $other.motion.p99Ms, $other.motion.stretchMs, $flag)
    }
    $md += ''
}
$report = Join-Path $Out 'report.md'
$md | Set-Content $report -Encoding utf8
Write-Host ''
Write-Host "[live] report: $report"
# a flag fails the run even when it is inconclusive: a stutter, a missing part or a menu the processor drew is
# one on any power
if ($flagged) { exit 1 }
if ($inconclusive.Count -gt 0) {
    $inconclusive | Select-Object -Unique | ForEach-Object { Write-Host "[live] inconclusive: $_" }
    exit 3
}
exit 0
