# Builds Need For Madness: LIT for Windows.
#
#   madness.jar  - the game, launched by madness64.exe (see madness64.ini)
#   runtime\     - a private Java runtime made with jlink, so no Java install is needed to play
#   madness.aot  - the runtime's ahead-of-time cache for the game, from a training run (see below)
#
# Needs a JDK 25 or newer to build. It is found via -Jdk, then JAVA_HOME, then %USERPROFILE%\.jdks.

param(
    [string]$Jdk,
    # Skip the AOT training run (about two minutes): for quick builds while working on the game.
    [switch]$Quick
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Find-Jdk {
    $candidates = @()
    if ($Jdk) { $candidates += $Jdk }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdks) {
        $candidates += Get-ChildItem $jdks -Directory | Sort-Object Name -Descending | ForEach-Object FullName
    }
    foreach ($c in $candidates) {
        if ((Test-Path "$c\bin\javac.exe") -and (Test-Path "$c\bin\jlink.exe")) { return $c }
    }
    throw 'No JDK 25+ found. Install one (e.g. winget install EclipseAdoptium.Temurin.25.JDK) or pass -Jdk <path>.'
}

$jdkHome = Find-Jdk
Write-Host "Using JDK at $jdkHome"

$build = Join-Path $PSScriptRoot 'bin'
$classes = Join-Path $build 'classes'
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory $classes | Out-Null

$libJars = Get-ChildItem lib\*.jar | ForEach-Object { "lib/$($_.Name)" }
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
$sources | Set-Content "$build\sources.txt" -Encoding ascii

& "$jdkHome\bin\javac.exe" -nowarn -encoding UTF-8 --release 25 -cp ($libJars -join ';') -d $classes "@$build\sources.txt"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

# Images loaded with getResource() live next to the classes
Copy-Item src\nfm\lit\aimg "$classes\nfm\lit\aimg" -Recurse

# The renderer's shaders, compiled now by Windows' shader compiler so the game
# does not compile them at every start (it still can, if one is missing)
& "$jdkHome\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp $classes nfm.lit.gpu.ShaderBaker $classes
if ($LASTEXITCODE -ne 0) { Write-Warning 'Baking the shaders failed: the game will compile them when it starts.' }

@(
    'Main-Class: nfm.lit.RunApp'
    "Class-Path: $($libJars -join ' ')"
    'Enable-Native-Access: ALL-UNNAMED'
    ''
) | Set-Content "$build\manifest.txt" -Encoding ascii
& "$jdkHome\bin\jar.exe" --create --file madness.jar --manifest "$build\manifest.txt" -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging madness.jar failed.' }

# The private runtime. Madness.drive and Control.preform (the car physics and
# the AI) are over HotSpot's 8000-byte limit for compiling a method, so without
# -XX:-DontCompileHugeMethods they stay interpreted: about 9% of a race frame's
# CPU time, 2% compiled. With the trap limits at 0 the compiler keeps the
# branches it has not seen taken yet: otherwise the first wreck, say, throws
# Plane.d back to the interpreter, and ticks take 15-40 ms instead of 3-5 for
# seconds while it is compiled again. Options given to jlink are stored in the
# runtime, so madness64.exe and java.exe both get them. Generational ZGC
# collects while the game runs: G1 stops every thread for about 4 ms every half
# minute of racing, which at 300 frames a second is a frame missed; ZGC's pauses
# are under 0.1 ms. Compact object headers (8 bytes instead of 12) leave a few
# percent less live heap after each collection; the game's data is mostly
# large arrays, so not more.
$jlinkArgs = @('--add-modules', 'java.base,java.desktop,java.logging', '--strip-debug', '--no-header-files',
    '--no-man-pages',
    '--add-options=-XX:+UseZGC -XX:+UseCompactObjectHeaders -XX:-DontCompileHugeMethods -XX:PerMethodTrapLimit=0 -XX:PerBytecodeTrapLimit=0')
# Rebuilt only when the JDK or these arguments change: a runtime just written
# is often held by the antivirus for a while, so it cannot be deleted at once.
$stampFile = 'runtime\nfm-runtime.txt'
$stamp = "$((Get-FileHash "$jdkHome\release").Hash) $($jlinkArgs -join ' ')"
if ((Test-Path $stampFile) -and (Get-Content $stampFile -Raw).Trim() -eq $stamp) {
    Write-Host 'Runtime is up to date.'
} else {
    foreach ($attempt in 1..15) {
        if (-not (Test-Path runtime)) { break }
        try { Remove-Item runtime -Recurse -Force -ErrorAction Stop } catch {
            if ($attempt -eq 15) { throw "Cannot remove the old runtime folder: $_" }
            Start-Sleep -Seconds 2
        }
    }
    & "$jdkHome\bin\jlink.exe" @jlinkArgs --output runtime
    if ($LASTEXITCODE -ne 0) { throw 'Creating the runtime failed.' }
    Set-Content $stampFile $stamp -Encoding ascii
}

Remove-Item $build -Recurse -Force

# The AOT cache (JDK 25+): a training run, the hidden smoke test, records the
# classes the game loads, already parsed and linked, and how often each method
# ran, so the game starts faster and its JIT compiles the hot code sooner on the
# first race. madness64.ini points the game at it. It belongs to this exact
# madness.jar and runtime: the JVM ignores it after any change, so a quick build
# deletes it rather than leave one that no longer fits.
if (Test-Path madness.aot) { Remove-Item madness.aot -Force }
if ($Quick) {
    Write-Host 'Quick build: no AOT cache.'
} else {
    Write-Host 'Training the AOT cache (the hidden smoke test, about two minutes)...'
    $training = Join-Path ([IO.Path]::GetTempPath()) "nfm-aot-training-$PID"
    New-Item -ItemType Directory $training -Force | Out-Null
    $trainingLog = Join-Path $training 'training.log'
    $p = Start-Process runtime\bin\java.exe -ArgumentList '--enable-native-access=ALL-UNNAMED',
        '-XX:AOTCacheOutput=madness.aot', '-Dnfm.debug=false', '-Dnfm.renderer=gpu', "-Dnfm.autotest=$training",
        '-jar', 'madness.jar' -WorkingDirectory $PSScriptRoot -RedirectStandardOutput $trainingLog -WindowStyle Hidden -PassThru -Wait
    if ($p.ExitCode -ne 0 -or -not (Test-Path madness.aot)) {
        Write-Warning "The AOT training run failed (exit $($p.ExitCode)); the game runs without the cache. Log: $trainingLog"
    } else {
        Remove-Item $training -Recurse -Force
        Write-Host "AOT cache: madness.aot, $([int]((Get-Item madness.aot).Length / 1MB)) MB."
    }
}
Write-Host 'Done. Start the game with madness64.exe.'
