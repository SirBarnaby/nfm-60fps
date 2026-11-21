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
# Codex runs read only and changes nothing. Only committed work is reviewed.
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

Push-Location $root
try {
    $baseSha = (& git rev-parse --verify --quiet "$Base^{commit}")
    if ($LASTEXITCODE -ne 0 -or -not $baseSha) { Fail "No commit called $Base." }
    $headSha = (& git rev-parse --short HEAD).Trim()
    $baseShort = (& git rev-parse --short $baseSha).Trim()
    if (& git status --porcelain --untracked-files=no) {
        Write-Host 'note: there are uncommitted changes; only committed work is reviewed.'
    }

    $arguments = @($companion, 'adversarial-review', '--wait', '--json', '--base', $baseSha)
    if ($Model) { $arguments += @('--model', $Model) }
    $arguments += $focus
    Write-Host ('Codex is reviewing {0}..{1} (a few minutes)...' -f $baseShort, $headSha)
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
    Write-Host ('blind review: {0}, {1} findings ({2})' -f $result.verdict, $findings.Count, (Resolve-Path -Relative $file))
    if ($result.verdict -eq 'approve') { exit 0 }
    exit 1
} finally {
    Pop-Location
}
