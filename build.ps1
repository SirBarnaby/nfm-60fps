# Builds Need For Madness: LIT for Windows.
#
#   madness.jar  - the game, launched by madness64.exe (see madness64.ini)
#   runtime\     - a private Java runtime made with jlink, so no Java install is needed to play
#
# Needs a JDK 21 or newer to build. It is found via -Jdk, then JAVA_HOME, then %USERPROFILE%\.jdks.

param(
    [string]$Jdk
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
    throw 'No JDK 21+ found. Install one (e.g. winget install EclipseAdoptium.Temurin.21.JDK) or pass -Jdk <path>.'
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

& "$jdkHome\bin\javac.exe" -nowarn -encoding UTF-8 --release 21 -cp ($libJars -join ';') -d $classes "@$build\sources.txt"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

# Images loaded with getResource() live next to the classes
Copy-Item src\nfm\lit\aimg "$classes\nfm\lit\aimg" -Recurse

@(
    'Main-Class: nfm.lit.RunApp'
    "Class-Path: $($libJars -join ' ')"
    'Enable-Native-Access: ALL-UNNAMED'
    ''
) | Set-Content "$build\manifest.txt" -Encoding ascii
& "$jdkHome\bin\jar.exe" --create --file madness.jar --manifest "$build\manifest.txt" -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging madness.jar failed.' }

if (Test-Path runtime) { Remove-Item runtime -Recurse -Force }
& "$jdkHome\bin\jlink.exe" --add-modules java.base,java.desktop,java.logging --strip-debug --no-header-files --no-man-pages --output runtime
if ($LASTEXITCODE -ne 0) { throw 'Creating the runtime failed.' }

Remove-Item $build -Recurse -Force
Write-Host 'Done. Start the game with madness64.exe.'
