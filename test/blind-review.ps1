# The blind review: Codex rates a change with no word from whoever made it, only the owner's specs
# and the repo (openspec\specs\done-and-testing). It is the Codex adversarial review from OpenAI's own
# Codex plugin for Claude Code (github.com/openai/codex-plugin-cc, the same review as
# /codex:adversarial-review), with the owner's question fixed here so the agent under review cannot
# steer it.
#
#   .\test\blind-review.ps1                   reviews the last commit
#   .\test\blind-review.ps1 -Base 1ccfa7f     reviews every commit after 1ccfa7f (a whole night's work)
#   .\test\blind-review.ps1 -Model gpt-5.5    another Codex model than its default
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
    [string]$Model
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

$focus = @'
Blind review for the game's owner, who alone decides what the game should be. You get no explanation from whoever made this change, on purpose. Judge it only against the owner's requirements in openspec/specs (read every spec.md the change touches, and openspec/config.yaml for the rules agents must follow) and against the repository itself. For each requirement the change touches, decide whether it is met, not met, or cannot be judged from what you can see, and make every unmet requirement a finding. Treat each of these as blocking: a requirement or its Check scenario changed, removed or weakened without the owner; a test, check or baseline loosened, skipped, deleted or updated so that it passes; a claim in docs, specs or commit messages that no test output backs; until 1.0, anything that could make a race play differently from the original (physics, AI, timing, random streams, race state); drawing moved from the graphics card to the processor; anything likely to drop the frame rate under 250 fps at 1080p fullscreen, or to add stutter or input lag.
'@.Trim()

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

# The plugin's runner leaves a shared Codex broker running for the repo; inside Claude Code the plugin's
# SessionEnd hook stops it. Started from here it would outlive the review and, on Windows, keep the
# caller's output pipe open, so anything capturing this script's output would wait forever.
# 'peek' gives the pid of the broker serving the repo now; 'stop' stops the broker unless it is the one
# given to keep, with the plugin's own teardown (the same steps as its SessionEnd hook).
function Invoke-Broker([string]$repo, [string]$action, [string]$keepPid = '') {
    $lib = ([Uri](Join-Path (Split-Path $companion -Parent) 'lib\')).AbsoluteUri
    $js = @"
import { loadBrokerSession, sendBrokerShutdown, teardownBrokerSession, clearBrokerSession } from '${lib}broker-lifecycle.mjs';
import { terminateProcessTree } from '${lib}process.mjs';
const root = process.env.BLIND_REVIEW_ROOT;
const session = loadBrokerSession(root);
const pid = String(session?.pid ?? '');
if (process.env.BLIND_REVIEW_BROKER === 'peek') {
  process.stdout.write(pid);
} else if (session && pid !== process.env.BLIND_REVIEW_KEEP.slice(4)) {
  if (session.endpoint) await sendBrokerShutdown(session.endpoint);
  teardownBrokerSession({ ...session, killProcess: terminateProcessTree });
  clearBrokerSession(root);
}
"@
    $env:BLIND_REVIEW_ROOT = $repo
    $env:BLIND_REVIEW_BROKER = $action
    $env:BLIND_REVIEW_KEEP = "pid:$keepPid"   # never empty: PowerShell drops an empty variable
    try { ($js | & $node.Source --no-deprecation --input-type=module -) -join '' }
    finally { Remove-Item Env:BLIND_REVIEW_ROOT, Env:BLIND_REVIEW_BROKER, Env:BLIND_REVIEW_KEEP -ErrorAction SilentlyContinue }
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

    # a clean checkout of the head commit for Codex to read, made for this review alone
    $tree = Join-Path ([IO.Path]::GetTempPath()) ('nfm-review-' + [guid]::NewGuid().ToString('N').Substring(0, 12))
    & git worktree add --detach --quiet $tree $headFull
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tree)) {
        $tree = $null
        Fail 'Could not make a clean checkout of HEAD to review (git worktree add).'
    }
    Push-Location $tree
    $inTree = $true

    # --no-deprecation: Node 24 warns about the runner's own Windows spawn (DEP0190), noise for this report
    $arguments = @('--no-deprecation', $companion, 'adversarial-review', '--wait', '--json', '--base', $baseSha)
    if ($Model) { $arguments += @('--model', $Model) }
    $arguments += $focus
    Write-Host ('Codex is reviewing {0}..{1} (a few minutes)...' -f $baseShort, $headSha)
    $brokerBefore = Invoke-Broker $tree 'peek'
    $raw = (& $node.Source @arguments) -join "`n"
    $exit = $LASTEXITCODE

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
        '',
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
} finally {
    # the broker's processes work in the checkout, so they go before it does
    if ($null -ne $brokerBefore) { Invoke-Broker $tree 'stop' $brokerBefore | Out-Null }
    if ($inTree) { Pop-Location }
    if ($tree) { Remove-ReviewTree $tree }
    Pop-Location
}
