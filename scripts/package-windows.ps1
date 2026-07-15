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

# Stage ONLY the fat jar in a dedicated input directory. jpackage copies the
# entire --input tree into the app image, so --input must never point at a
# directory that contains the --dest output (doing so nests the installer
# inside itself on every rebuild).
$input = "target/app-input"
if (Test-Path $input) { Remove-Item $input -Recurse -Force }
New-Item -ItemType Directory -Force $input | Out-Null
Copy-Item "target/$jar" (Join-Path $input $jar)

$out = "target/dist"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

Write-Host "Running jpackage (type: $Type) ..."
$jpackageArgs = @(
    "--type", $Type,
    "--name", "Budget Guardian",
    "--app-version", $version,
    "--input", $input,
    "--main-jar", $jar,
    "--main-class", "com.budgetguardian.app.Launcher",
    "--dest", $out,
    "--vendor", "Budget Guardian",
    "--description", "Personal finance data-structures showcase"
)
if ($Type -ne "app-image") {
    # These installer-behavior flags are only valid for msi/exe, not app-image.
    $jpackageArgs += @("--win-dir-chooser", "--win-menu", "--win-shortcut")
}
& jpackage @jpackageArgs

Write-Host "Done. Output in $out"
