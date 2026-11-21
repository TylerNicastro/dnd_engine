# Windows PowerShell version of web/build.sh (same output).
# Usage (from anywhere):  powershell -ExecutionPolicy Bypass -File web\build.ps1
# Preview locally:        cd web; python -m http.server 8000   then open http://localhost:8000/
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Classes = Join-Path $Root "build\web-classes"
$DesktopRoot = Join-Path $Root "build\desktop"
$Desktop = Join-Path $DesktopRoot "dnd_engine"
$Web = Join-Path $Root "web"

if (Test-Path $Classes) { Remove-Item -Recurse -Force $Classes }
New-Item -ItemType Directory -Force $Classes | Out-Null

Write-Host "Compiling src/ for Java 11..."
javac --release 11 -encoding UTF-8 -d $Classes (Get-ChildItem (Join-Path $Root "src\*.java")).FullName
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "Packaging web/dnd_engine.jar..."
jar --create --file (Join-Path $Web "dnd_engine.jar") --main-class App -C $Classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "Copying data files..."
Copy-Item (Join-Path $Root "weapons.json"), (Join-Path $Root "spells.json"), (Join-Path $Root "beasts.json") $Web
$Art = Join-Path $Root "art"
if (Test-Path $Art) {
    $WebArt = Join-Path $Web "art"
    if (Test-Path $WebArt) { Remove-Item -Recurse -Force $WebArt }
    Copy-Item -Recurse $Art $WebArt
}

Write-Host "Packaging web/dnd_engine-desktop.zip..."
if (Test-Path $DesktopRoot) { Remove-Item -Recurse -Force $DesktopRoot }
New-Item -ItemType Directory -Force $Desktop | Out-Null
Copy-Item (Join-Path $Web "dnd_engine.jar"), (Join-Path $Root "weapons.json"), (Join-Path $Root "spells.json"), (Join-Path $Root "beasts.json"),
          (Join-Path $Root "README.md"), (Join-Path $Root "LICENSE") $Desktop
if (Test-Path $Art) { Copy-Item -Recurse $Art (Join-Path $Desktop "art") }
$Zip = Join-Path $Web "dnd_engine-desktop.zip"
if (Test-Path $Zip) { Remove-Item -Force $Zip }
Compress-Archive -Path $Desktop -DestinationPath $Zip

Write-Host "Done. Output is in web/"
