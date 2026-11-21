# The spec check: proves the owner's specs in openspec\specs are well formed and that every
# requirement says how it is checked.
#
#   .\test\spec-check.ps1
#
# 1. Runs `openspec validate --specs --strict` (the OpenSpec CLI, installed per user). If the CLI is
#    missing it says so and goes on with step 2; install it with
#    irm https://openspec.dev/cli/install.ps1 | iex
# 2. Reads every openspec\specs\<area>\spec.md and fails if a requirement has no scenario whose name
#    starts with "Check:", or if a check names a test\*.ps1 file that does not exist.
#
# Exit code 0: every spec valid and every requirement checked. 1: something to fix.
# It only reads files: no window, no sound, nothing saved.

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$specs = Join-Path $root 'openspec\specs'
$failed = $false

if (-not (Test-Path $specs)) {
    Write-Host 'openspec\specs is missing.'
    exit 1
}

# 1. OpenSpec's own format check
$cli = Get-Command openspec -ErrorAction SilentlyContinue
if ($cli) {
    Push-Location $root
    try {
        $env:OPENSPEC_TELEMETRY = '0'
        & $cli.Source validate --specs --strict --no-interactive
        if ($LASTEXITCODE -ne 0) { $failed = $true; Write-Host 'openspec validate --specs --strict failed.' }
    } finally {
        Pop-Location
    }
} else {
    Write-Host 'note: the openspec CLI is not installed, so the format check was skipped.'
}

# 2. Every requirement has a "Check:" scenario, and the test files it names exist
$areas = 0
$requirements = 0
foreach ($file in Get-ChildItem -Path $specs -Filter 'spec.md' -Recurse) {
    $areas++
    $area = Split-Path (Split-Path $file.FullName -Parent) -Leaf
    $text = Get-Content -Raw -Encoding UTF8 $file.FullName
    $blocks = [regex]::Split($text, '(?m)^### Requirement:')
    for ($i = 1; $i -lt $blocks.Count; $i++) {
        $requirements++
        $block = $blocks[$i]
        $name = ($block -split "`n", 2)[0].Trim()
        if ($block -notmatch '(?m)^#### Scenario:\s*Check:') {
            $failed = $true
            Write-Host ("{0}: '{1}' has no 'Check:' scenario" -f $area, $name)
        }
        foreach ($m in [regex]::Matches($block, 'test\\[A-Za-z0-9_.-]+\.ps1')) {
            if (-not (Test-Path (Join-Path $root $m.Value))) {
                $failed = $true
                Write-Host ("{0}: '{1}' names {2}, which does not exist" -f $area, $name, $m.Value)
            }
        }
    }
}

if ($failed) {
    Write-Host ('spec check: FAILED ({0} areas, {1} requirements)' -f $areas, $requirements)
    exit 1
}
Write-Host ('spec check: {0} areas, {1} requirements, every one checked' -f $areas, $requirements)
exit 0
