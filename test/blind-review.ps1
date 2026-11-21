# The blind review: Codex rates a change with no word from whoever made it, only the owner's specs
# and the repo (openspec\specs\done-and-testing). It is the Codex adversarial review from OpenAI's own
# Codex plugin for Claude Code (github.com/openai/codex-plugin-cc, the same review as
# /codex:adversarial-review), with the owner's question fixed here so the agent under review cannot
# steer it.
#
#   .\test\blind-review.ps1                   reviews the last commit
#   .\test\blind-review.ps1 -Base 1ccfa7f     reviews every commit after 1ccfa7f (a whole night's work)
#   .\test\blind-review.ps1 -Model gpt-5.5    another Codex model than its default
#   .\test\blind-review.ps1 -Live C:\runs\a   the live test runs to give Codex (folders test\live.ps1
#                                             wrote; default: the newest one-player and the newest
#                                             split screen run in %TEMP%\nfm-live)
#
# Codex gets the live test's evidence in review-evidence\ inside its checkout: each run's report.md,
# manifest.json, frames.json, settings.txt and the *-gpu.png screenshots read back from the graphics card,
# to judge the frame rate, stutter and what is on screen against the specs. From each run's manifest.json
# (the commit, uncommitted changes and madness.jar it ran with) Codex is told whether the run came from the
# build under review: the reviewed commit, or one with the same game files (src, lib, data, build.ps1,
# madness64.ini, test\live.ps1), nothing of those uncommitted, and the jar built after its sources. The
# review file names the runs it got and says plainly which do not match, or that there were none.
#
# Needs Node.js 18.18 or later, the Codex CLI signed in (`codex login`), and the plugin:
#   claude plugin marketplace add openai/codex-plugin-cc
#   claude plugin install codex@openai-codex
# Codex runs read only and changes nothing. Only committed work is reviewed: Codex reads a clean checkout
# of the head commit (a detached git worktree under %TEMP%, removed afterwards), so uncommitted edits here
# cannot sway it.
# Writes docs\reviews\<date>-<commit>.md with the verdict and every finding, and prints it.
#
# Exit code 0: Codex approves. 1: findings to read. 2: the review could not run.

param(
    [string]$Base = 'HEAD~1',
    [string]$Model,
    [string[]]$Live
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

$focus = @'
Blind review for the game's owner, who alone decides what the game should be. You get no explanation from whoever made this change, on purpose. Judge it only against the owner's requirements in openspec/specs (read every spec.md the change touches, and openspec/config.yaml for the rules agents must follow) and against the repository itself. For each requirement the change touches, decide whether it is met, not met, or cannot be judged from what you can see, and make every unmet requirement a finding. Treat each of these as blocking: a requirement or its Check scenario changed, removed or weakened without the owner; a test, check or baseline loosened, skipped, deleted or updated so that it passes; a claim in docs, specs or commit messages that no test output backs; until 1.0, anything that could make a race play differently from the original (physics, AI, timing, random streams, race state); drawing moved from the graphics card to the processor; anything likely to drop the frame rate under 250 fps at 1080p fullscreen, or to add stutter or input lag.
'@.Trim()
# one of these follows the focus, as there is live test evidence or not
$withLive = 'The latest live test results are in review-evidence\ (a folder per live test, named by when it ran: its report.md, and for each run its manifest.json, frames.json, settings.txt and the *-gpu.png screenshots read back from the graphics card, which are what the screen showed). Judge frame rate and stutter from report.md and frames.json, and open the screenshots to judge whether what is on screen meets the specs.'
$withoutLive = 'There are no live test results for this review, so frame rate, stutter and what is on screen cannot be judged from a live run.'
# then, after $withLive, whether each run came from the build under review (Get-BuildMatch), and when one did not:
$buildIntro = 'Whether each live test run came from the build under review, from the manifest.json test\live.ps1 wrote in its folder when it started:'
$buildWarning = 'Evidence that does not match may not show the build under review: say so wherever you rely on it.'

# What the reviewed build is made from, and what a live test's figures depend on: the game's sources and libraries,
# its data, how it is built and started, and the live test that judged it
$buildPaths = @('src', 'lib', 'data', 'build.ps1', 'madness64.ini', 'test/live.ps1')

function Fail($message) {
    Write-Host $message
    Write-Host 'blind review: could not run'
    exit 2
}

$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { Fail 'Node.js is not installed (https://nodejs.org, 18.18 or later).' }
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
    Fail 'The Codex CLI is not installed: npm install -g @openai/codex, then codex login.'
}

# the plugin's own runner, from Claude Code's plugin cache (newest version), or CODEX_COMPANION
$companion = $env:CODEX_COMPANION
if (-not $companion) {
    $cache = Join-Path $env:USERPROFILE '.claude\plugins\cache'
    $found = @(Get-ChildItem -Path $cache -Filter 'codex-companion.mjs' -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\codex\\[^\\]+\\scripts\\codex-companion\.mjs$' } |
        Sort-Object LastWriteTime -Descending)
    if ($found.Count -gt 0) { $companion = $found[0].FullName }
}
if (-not $companion -or -not (Test-Path $companion)) {
    Fail ('The Codex plugin for Claude Code is not installed: claude plugin marketplace add openai/codex-plugin-cc, ' +
        'then claude plugin install codex@openai-codex.')
}

# The plugin's runner leaves a shared Codex broker running for the checkout it reviews; inside Claude Code
# the plugin's SessionEnd hook stops it. Started from here it would outlive the review and, on Windows, keep
# the caller's output pipe open, so anything capturing this script's output would wait forever.
# The plugin keeps a broker's state per checkout path (its resolveStateDir), and this review's checkout is its
# own, so no other review's broker can be found there. Even so only the broker that appeared during this review
# is stopped: 'peek' gives the pid the state names now (before the review: the pid to leave alone); 'stop'
# stops the broker only if the state still names $brokerPid, with the plugin's own teardown (the same steps as
# its SessionEnd hook), and removes the state folder with it.
function Invoke-Broker([string]$repo, [string]$action, [string]$brokerPid = '') {
    $lib = ([Uri](Join-Path (Split-Path $companion -Parent) 'lib\')).AbsoluteUri
    $js = @"
import fs from 'node:fs';
import { loadBrokerSession, sendBrokerShutdown, teardownBrokerSession, clearBrokerSession } from '${lib}broker-lifecycle.mjs';
import { terminateProcessTree } from '${lib}process.mjs';
import { resolveStateDir } from '${lib}state.mjs';
const root = process.env.BLIND_REVIEW_ROOT;
const session = loadBrokerSession(root);
const pid = String(session?.pid ?? '');
if (process.env.BLIND_REVIEW_BROKER === 'peek') {
  process.stdout.write(pid);
} else if (session && pid !== '' && pid === process.env.BLIND_REVIEW_PID.slice(4)) {
  if (session.endpoint) await sendBrokerShutdown(session.endpoint);
  teardownBrokerSession({ ...session, killProcess: terminateProcessTree });
  clearBrokerSession(root);
  fs.rmSync(resolveStateDir(root), { recursive: true, force: true });
}
"@
    $env:BLIND_REVIEW_ROOT = $repo
    $env:BLIND_REVIEW_BROKER = $action
    $env:BLIND_REVIEW_PID = "pid:$brokerPid"   # never empty: PowerShell drops an empty variable
    try { ($js | & $node.Source --no-deprecation --input-type=module -) -join '' }
    finally { Remove-Item Env:BLIND_REVIEW_ROOT, Env:BLIND_REVIEW_BROKER, Env:BLIND_REVIEW_PID -ErrorAction SilentlyContinue }
}

# A live test's folder (test\live.ps1 -Out, by default %TEMP%\nfm-live\<time>): its runs (each a folder with
# frames.json) and whether they raced split screen; a run folder given on its own counts as its live test with
# that one run. Null if there is no run in it.
function Get-LiveTest([string]$folder) {
    if (-not (Test-Path $folder -PathType Container)) { return $null }
    $folder = (Resolve-Path $folder).Path
    $runs = @(Get-ChildItem $folder -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'frames.json') })
    if ($runs.Count -eq 0 -and (Test-Path (Join-Path $folder 'frames.json'))) {
        $runs = @(Get-Item $folder)
        $folder = Split-Path $folder -Parent
    }
    if ($runs.Count -eq 0) { return $null }
    $split = @($runs | Where-Object { (Get-Content (Join-Path $_.FullName 'frames.json') -Raw) -match '"name":\s*"split ' }).Count -gt 0
    [pscustomobject]@{
        folder = $folder
        runs = $runs
        split = $split
        ranAt = (Get-Item (Join-Path $runs[0].FullName 'frames.json')).LastWriteTime
    }
}

# The live tests to give Codex: those named with -Live, else the newest one-player and the newest split screen
# test in %TEMP%\nfm-live (none when there are none).
function Find-LiveTests {
    if ($Live) {
        return @(foreach ($folder in $Live) {
                $test = Get-LiveTest $folder
                if (-not $test) { Fail "No live test run in $folder (a folder test\live.ps1 wrote, with frames.json)." }
                $test
            })
    }
    $onePlayer = $null
    $splitScreen = $null
    $folders = Get-ChildItem (Join-Path ([IO.Path]::GetTempPath()) 'nfm-live') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($folder in $folders) {
        $test = Get-LiveTest $folder.FullName
        if (-not $test) { continue }
        if ($test.split) { if (-not $splitScreen) { $splitScreen = $test } } elseif (-not $onePlayer) { $onePlayer = $test }
        if ($onePlayer -and $splitScreen) { break }
    }
    return @(@($onePlayer, $splitScreen) | Where-Object { $_ })
}

# Whether a live test run came from the reviewed build ($headFull), from the manifest.json test\live.ps1 wrote in
# the run's folder when it started: made at the reviewed commit or at one whose $buildPaths are the same, with
# none of them changed and not committed, and madness.jar built after its sources. text says which, plainly.
function Get-BuildMatch($run) {
    $file = Join-Path $run.FullName 'manifest.json'
    if (-not (Test-Path $file)) {
        return [pscustomobject]@{ matches = $false
            text = 'does not match the build under review: it has no manifest.json, so which build it came from is unknown' }
    }
    $manifest = Get-Content $file -Raw | ConvertFrom-Json
    $commit = [string]$manifest.commit
    $short = if ($commit.Length -gt 7) { $commit.Substring(0, 7) } else { $commit }
    $why = @()
    $same = ''
    if (-not $commit) {
        $why += 'its manifest names no commit (not run from a git checkout)'
    } elseif (-not (& git rev-parse --verify --quiet "$commit^{commit}")) {
        $why += "it was made at $short, a commit this repository does not have"
    } elseif ($commit -ne $headFull) {
        $differ = @(& git diff --name-only $commit $headFull -- $buildPaths)
        if ($differ.Count -gt 0) {
            $list = (($differ | Select-Object -First 5) -join ', ') + $(if ($differ.Count -gt 5) { ', ...' })
            $why += ('it was made at {0}, and {1} of the files the build and the live test come from differ from {2}: {3}' -f
                $short, $differ.Count, $headSha, $list)
        } else {
            $same = " (its $($buildPaths -join ', ') are the same as $headSha's)"
        }
    }
    # git status --porcelain lines: 'XY path', or 'XY old -> new' for a rename
    $changed = @(foreach ($line in @($manifest.changed)) {
            if (-not $line) { continue }
            $path = ([string]$line).Substring(3)
            if ($path -match ' -> (.+)$') { $path = $Matches[1] }
            $path = $path.Trim('"')
            if (@($buildPaths | Where-Object { $path -eq $_ -or $path.StartsWith("$_/") }).Count -gt 0) { $path }
        })
    if ($changed.Count -gt 0) {
        $why += 'it was made with uncommitted changes to ' + (($changed | Select-Object -First 5) -join ', ') +
        $(if ($changed.Count -gt 5) { ', ...' })
    }
    if ($manifest.jarOlderThan) {
        $why += "madness.jar was older than $($manifest.jarOlderThan), so it was an earlier build than the files"
    }
    $jarHash = if ($manifest.jarSha256) { ([string]$manifest.jarSha256).Substring(0, 12) } else { 'unknown' }
    if ($why.Count -gt 0) {
        return [pscustomobject]@{ matches = $false; text = 'does not match the build under review: ' + ($why -join '; ') }
    }
    return [pscustomobject]@{ matches = $true
        text = ("matches the build under review: made at $short$same, with no uncommitted changes to the files the build and the live test " +
            "come from, and madness.jar (sha256 $jarHash) built after its sources") }
}

# Copies a live test's evidence into the review's checkout: report.md, and each run's manifest.json, frames.json,
# settings.txt and the screenshots read back from the card.
function Copy-LiveEvidence($test, [string]$to) {
    New-Item -ItemType Directory -Force $to | Out-Null
    $report = Join-Path $test.folder 'report.md'
    if (Test-Path $report) { Copy-Item $report $to }
    foreach ($run in $test.runs) {
        $runTo = Join-Path $to $run.Name
        New-Item -ItemType Directory -Force $runTo | Out-Null
        foreach ($name in 'manifest.json', 'frames.json', 'settings.txt') {
            $file = Join-Path $run.FullName $name
            if (Test-Path $file) { Copy-Item $file $runTo }
        }
        Get-ChildItem $run.FullName -Filter '*-gpu.png' | Copy-Item -Destination $runTo
    }
}

# Removes the checkout made for the review; one that cannot go (a file still held) is named, never left silently.
function Remove-ReviewTree([string]$path) {
    & git -C $root worktree remove --force $path
    if (Test-Path $path) { Remove-Item -Recurse -Force $path -ErrorAction SilentlyContinue }
    & git -C $root worktree prune
    if (Test-Path $path) { Write-Host "note: the review checkout $path is still there; delete it and run git worktree prune." }
}

$brokerBefore = $null
$tree = $null
$inTree = $false
Push-Location $root
try {
    $baseSha = (& git rev-parse --verify --quiet "$Base^{commit}")
    if ($LASTEXITCODE -ne 0 -or -not $baseSha) { Fail "No commit called $Base." }
    $headFull = (& git rev-parse HEAD).Trim()
    $headSha = (& git rev-parse --short $headFull).Trim()
    $baseShort = (& git rev-parse --short $baseSha).Trim()
    if (& git status --porcelain) {
        Write-Host 'note: there are uncommitted changes; they are not reviewed.'
    }
    $liveTests = @(Find-LiveTests)

    # a clean checkout of the head commit for Codex to read, made for this review alone
    $tree = Join-Path ([IO.Path]::GetTempPath()) ('nfm-review-' + [guid]::NewGuid().ToString('N').Substring(0, 12))
    & git worktree add --detach --quiet $tree $headFull
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tree)) {
        $tree = $null
        Fail 'Could not make a clean checkout of HEAD to review (git worktree add).'
    }
    Push-Location $tree
    $inTree = $true
    $liveLines = @()
    $buildLines = @()
    $mismatch = $false
    foreach ($test in $liveTests) {
        $name = (Split-Path $test.folder -Leaf) + $(if ($test.split) { '-split' })
        Copy-LiveEvidence $test (Join-Path $tree "review-evidence\$name")
        $liveLines += ('- `{0}`: {1}, run {2:yyyy-MM-dd HH:mm} ({3}), in review-evidence\{4}' -f $test.folder,
            $(if ($test.split) { 'split screen' } else { 'one player' }), $test.ranAt, ($test.runs.Name -join ', '), $name)
        foreach ($run in $test.runs) {
            $match = Get-BuildMatch $run
            if (-not $match.matches) { $mismatch = $true }
            $liveLines += "  - $($run.Name): $($match.text)"
            $buildLines += "review-evidence\$name\$($run.Name) $($match.text)."
        }
    }
    $reviewFocus = $focus + ' ' + $(if ($liveTests.Count -gt 0) {
            (@($withLive, $buildIntro) + $buildLines + @($(if ($mismatch) { $buildWarning }))) -join ' '
        } else { $withoutLive })

    # --no-deprecation: Node 24 warns about the runner's own Windows spawn (DEP0190), noise for this report
    $arguments = @('--no-deprecation', $companion, 'adversarial-review', '--wait', '--json', '--base', $baseSha)
    if ($Model) { $arguments += @('--model', $Model) }
    $arguments += $reviewFocus
    Write-Host ('Codex is reviewing {0}..{1} (a few minutes)...' -f $baseShort, $headSha)
    $brokerBefore = Invoke-Broker $tree 'peek'
    # Node writes UTF-8, which PowerShell reads in the console's code page (850 here) unless told otherwise,
    # garbling every character of the review outside ASCII; a console that cannot be switched is left as it is
    $consoleEncoding = [Console]::OutputEncoding
    try { [Console]::OutputEncoding = New-Object Text.UTF8Encoding $false } catch { }
    try {
        $raw = (& $node.Source @arguments) -join "`n"
        $exit = $LASTEXITCODE
    } finally {
        try { [Console]::OutputEncoding = $consoleEncoding } catch { }
    }

    try { $payload = $raw | ConvertFrom-Json } catch { $payload = $null }
    if (-not $payload -or -not $payload.result) {
        $why = if ($payload -and $payload.parseError) { $payload.parseError } else { "exit code $exit" }
        if ($payload -and $payload.codex -and $payload.codex.stderr) { Write-Host $payload.codex.stderr }
        if (-not $payload) { Write-Host $raw }
        Fail "Codex gave no review ($why). Is it signed in? Run: codex login status"
    }
    $result = $payload.result

    $lines = @(
        ('# Blind review of {0}..{1}' -f $baseShort, $headSha),
        '',
        ('{0:yyyy-MM-dd HH:mm}, Codex adversarial review against openspec\specs, run by test\blind-review.ps1.' -f (Get-Date)),
        ('Commits after {0} up to {1}, read from a clean checkout of {1}.' -f $baseSha, $headFull),
        ''
    )
    if ($liveLines.Count -gt 0) {
        $lines += @('Live test evidence given to Codex:', '') + $liveLines + ''
        if ($mismatch) {
            $lines += @("**Some of this evidence is not from the build under review** (above), so what it shows may not be $headSha's. Run test\live.ps1 on $headSha and review again.", '')
        }
    } else {
        $lines += @('No live test run was found (test\live.ps1 writes them to %TEMP%\nfm-live), so Codex had no live test evidence.', '')
    }
    $lines += @(
        ('**Verdict: {0}**' -f $result.verdict),
        '',
        $result.summary,
        ''
    )
    $findings = @($result.findings)
    $lines += ('## Findings ({0})' -f $findings.Count)
    $lines += ''
    if ($findings.Count -eq 0) { $lines += 'None.'; $lines += '' }
    foreach ($f in $findings) {
        $lines += ('### {0}: {1}' -f $f.severity, $f.title)
        $lines += ''
        $lines += ('`{0}` lines {1}-{2}, confidence {3:N2}' -f $f.file, $f.line_start, $f.line_end, [double]$f.confidence)
        $lines += ''
        $lines += $f.body
        $lines += ''
        $lines += ('Recommendation: {0}' -f $f.recommendation)
        $lines += ''
    }
    $steps = @($result.next_steps)
    if ($steps.Count -gt 0) {
        $lines += '## Next steps'
        $lines += ''
        foreach ($s in $steps) { $lines += ('- {0}' -f $s) }
        $lines += ''
    }
    if ($payload.threadId) { $lines += ('Codex session: `codex resume {0}`' -f $payload.threadId); $lines += '' }

    $text = $lines -join "`n"
    $dir = Join-Path $root 'docs\reviews'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $file = Join-Path $dir ('{0:yyyy-MM-dd-HHmm}-{1}.md' -f (Get-Date), $headSha)
    [IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding $false))

    Write-Host $text
    Write-Host ('blind review: {0}, {1} findings (docs\reviews\{2})' -f $result.verdict, $findings.Count, (Split-Path $file -Leaf))
    if ($result.verdict -eq 'approve') { exit 0 }
    exit 1
} catch {
    # anything unexpected (a copy that failed, a finding Codex shaped oddly) is "could not run", never exit 1,
    # which means findings to read
    Fail "The review stopped: $($_.Exception.Message)"
} finally {
    # the broker this review started (its processes work in the checkout, so they go before it does); one
    # that was there before the review is not this review's
    if ($null -ne $brokerBefore) {
        $brokerOurs = Invoke-Broker $tree 'peek'
        if ($brokerOurs -and $brokerOurs -ne $brokerBefore) { Invoke-Broker $tree 'stop' $brokerOurs | Out-Null }
    }
    if ($inTree) { Pop-Location }
    if ($tree) { Remove-ReviewTree $tree }
    Pop-Location
}
