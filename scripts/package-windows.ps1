# Builds a native Windows installer for Budget Guardian using jpackage.
#
# Prerequisites:
#   - JDK 21 (provides jpackage) on PATH or via JAVA_HOME
#   - Maven on PATH
#   - WiX Toolset v3 on PATH (only for the default "msi"/"exe" installer types;
#     use -Type app-image to skip WiX and produce a portable folder)
#
# Usage:
#   pwsh scripts/package-windows.ps1                 # builds an MSI installer
#   pwsh scripts/package-windows.ps1 -Type app-image # builds a portable app folder

param(
    [ValidateSet("msi", "exe", "app-image")]
    [string]$Type = "msi"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$version = (Select-String -Path pom.xml -Pattern '<version>(.*?)</version>' | Select-Object -First 1).Matches.Groups[1].Value
$jar = "budget-guardian-$version-app.jar"

Write-Host "Building fat jar ($jar) ..."
mvn -q -DskipTests clean package

if (-not (Test-Path "target/$jar")) {
    throw "Expected target/$jar was not produced."
}

$out = "target/installer"
New-Item -ItemType Directory -Force $out | Out-Null

Write-Host "Running jpackage (type: $Type) ..."
jpackage `
    --type $Type `
    --name "Budget Guardian" `
    --app-version $version `
    --input target `
    --main-jar $jar `
    --main-class com.budgetguardian.app.Launcher `
    --dest $out `
    --vendor "Budget Guardian" `
    --description "Personal finance data-structures showcase" `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut

Write-Host "Done. Output in $out"
