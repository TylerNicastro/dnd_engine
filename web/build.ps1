# Windows PowerShell version of web/build.sh (same output).
# Usage (from anywhere):  powershell -ExecutionPolicy Bypass -File web\build.ps1
# Preview locally:        cd web; python -m http.server 8000   then open http://localhost:8000/
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Classes = Join-Path $Root "build\web-classes"
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
Copy-Item (Join-Path $Root "weapons.json"), (Join-Path $Root "spells.json") $Web
$Art = Join-Path $Root "art"
if (Test-Path $Art) {
    $WebArt = Join-Path $Web "art"
    if (Test-Path $WebArt) { Remove-Item -Recurse -Force $WebArt }
    Copy-Item -Recurse $Art $WebArt
}

Write-Host "Exporting search index..."
Push-Location $Root
try {
    java "-Djava.awt.headless=true" -cp $Classes SearchIndexExport (Join-Path $Web "search-index.json")
    if ($LASTEXITCODE -ne 0) { throw "search index export failed" }
} finally {
    Pop-Location
}

Write-Host "Done. Output is in web/"
