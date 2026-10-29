# Starts the installed Aqua Browser on the Windows build machine and screenshots the desktop, so the
# Windows title bar and look can be checked without a person.
#   pwsh test/screenshots-windows.ps1 "C:\path\to\firefox.exe" out-dir
param([string]$Exe, [string]$Out)
$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force -Path $Out | Out-Null
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

function Shot([string]$Name) {
  $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
  $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
  $bmp.Save((Join-Path $Out "$Name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
}

function Run([string]$Name, [string]$Prefs, [string[]]$Urls) {
  $p = Join-Path $env:RUNNER_TEMP ("aqua-" + $Name)
  New-Item -ItemType Directory -Force -Path $p | Out-Null
  @"
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.sessionstore.resume_from_crash", false);
user_pref("datareporting.policy.firstRunURL", "");
$Prefs
"@ | Set-Content -Encoding UTF8 (Join-Path $p 'user.js')
  $ini = Join-Path (Split-Path $Exe) 'browser\application.ini'
  $argList = @('-app', "`"$ini`"", '-no-remote', '-profile', $p) + $Urls
  $proc = Start-Process -FilePath $Exe -ArgumentList $argList -PassThru
  Start-Sleep -Seconds 30
  Shot $Name
  Write-Host "shot $Name"
  Get-Process -Name firefox -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $Exe } | Stop-Process -Force
  Start-Sleep -Seconds 3
}

Run 'win-01-welcome' '' @()
Run 'win-02-page' 'user_pref("aqua.welcomed", true); user_pref("aqua.color", "green");' @('https://www.wikipedia.org')
Get-ChildItem $Out
