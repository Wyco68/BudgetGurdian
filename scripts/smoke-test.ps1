<#
.SYNOPSIS
    Boot-smoke the packaged Budget Guardian application.

.DESCRIPTION
    Launches the fat jar with the CI smoke flag set, which forces local
    (SQLite) storage-agnostic startup: the full object graph is built, every
    view is registered, the window is shown, and the app then prints
    BUDGET_GUARDIAN_SMOKE_OK and exits by itself.

    The script fails (non-zero exit) if:
      - the app throws during startup (non-zero jar exit code), or
      - the success marker never appears, or
      - the app hangs past the timeout (killed, treated as failure).

    Runs the app in a throwaway data directory so CI never touches, and is
    never affected by, a developer's real config or database.
#>
[CmdletBinding()]
param(
    [string]$Jar,
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $Jar) {
    $Jar = Get-ChildItem -Path (Join-Path $repoRoot 'target') -Filter '*-app.jar' |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    Write-Error "Fat jar not found. Run 'mvn package' first."
    exit 1
}
Write-Host "Smoke-testing: $Jar"

# Isolated data dir so the smoke run defaults to a fresh local-mode config
# and its own SQLite file, independent of any real install on the machine.
$dataDir = Join-Path ([System.IO.Path]::GetTempPath()) ("bg-smoke-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $dataDir | Out-Null
$env:LOCALAPPDATA = $dataDir

# Raw ProcessStartInfo (not Start-Process): on PS 5.1 the Start-Process
# -PassThru object reads back a null ExitCode for a fast-exiting child, and
# `2>&1` on a native exe wraps each stderr line in an ErrorRecord. Driving the
# process directly with async stream reads avoids both, and captures stdout/
# stderr without the pipe-buffer deadlock a synchronous read can hit.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'java'
# prism.order=sw forces JavaFX's software render pipeline: CI runners have no
# GPU, and the hardware pipeline would otherwise fail to initialize there.
$psi.Arguments = "-Dbudgetguardian.smokeTest=true -Dprism.order=sw -jar `"$Jar`""
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$outTask = $proc.StandardOutput.ReadToEndAsync()
$errTask = $proc.StandardError.ReadToEndAsync()

if (-not $proc.WaitForExit($TimeoutSeconds * 1000)) {
    try { $proc.Kill() } catch { }
    Write-Host "--- stdout ---"; Write-Host $outTask.Result
    Write-Host "--- stderr ---"; Write-Host $errTask.Result
    Write-Error "App did not exit within $TimeoutSeconds s (hung during startup)."
    exit 1
}

$proc.WaitForExit()   # ensure async readers drained
$exitCode = $proc.ExitCode
$out = $outTask.Result
$err = $errTask.Result

Write-Host "--- stdout ---"; if ($out) { Write-Host $out }
Write-Host "--- stderr ---"; if ($err) { Write-Host $err }
Write-Host "exit code: $exitCode"

Remove-Item -Recurse -Force $dataDir -ErrorAction SilentlyContinue

if ($exitCode -ne 0) {
    Write-Error "App exited non-zero ($exitCode) — startup error."
    exit 1
}
if ($out -notmatch 'BUDGET_GUARDIAN_SMOKE_OK') {
    Write-Error "Success marker not found — app never reached a shown window."
    exit 1
}

Write-Host "SMOKE PASS: app launched, wired every view, showed the window, exited clean."
exit 0
