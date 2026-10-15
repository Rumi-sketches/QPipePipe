# Builds the signed QPipePipe release APKs.
#
#   powershell -ExecutionPolicy Bypass -File tools\release\build-release.ps1
#
# The keystore password is asked here, kept only in this process and never written to
# disk. The APKs end up in app\build\outputs\apk\release\. The script fails unless every
# APK is signed with the expected certificate: with a wrong or empty password Gradle
# silently builds unsigned APKs.
param(
    [string]$KeyPath = "$env:USERPROFILE\keystores\qpipepipe-release.jks",
    [string]$KeyAlias = "qpipepipe",
    [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot",
    # SHA-256 of the release certificate, as published in README.md.
    [string]$CertSha256 = "83a7ca6a819ee2d59079753ab5f18ff66c2bbfe6234015f8b17c554aac22cd36"
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
if (-not (Test-Path $KeyPath)) { throw "Keystore not found: $KeyPath" }

# apksigner comes with the Android SDK build tools; take the newest version.
$sdkLine = Get-Content (Join-Path $root "local.properties") | Where-Object { $_ -like "sdk.dir=*" }
$sdk = if ($sdkLine) { $sdkLine.Substring(8).Replace("\:", ":").Replace("\\", "\") } else { $env:ANDROID_HOME }
$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Sort-Object { [version]($_.Name -replace "[^0-9.].*$", "") } | Select-Object -Last 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
if (-not (Test-Path $apksigner)) { throw "apksigner not found in $sdk\build-tools" }

$branch = git -C $root rev-parse --abbrev-ref HEAD
$commit = git -C $root rev-parse --short HEAD
$dirty = git -C $root status --porcelain --untracked-files=no
Write-Host "Building $branch at $commit"
if ($dirty) { Write-Warning "The working tree has uncommitted changes; they end up in the APKs." }

do {
    $secure = Read-Host "Keystore password" -AsSecureString
    if ($secure.Length -eq 0) { Write-Warning "The password is empty, type it again." }
} while ($secure.Length -eq 0)
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$outputs = Join-Path $root "app\build\outputs\apk\release"
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    $env:JAVA_HOME = $JavaHome
    $env:KEY_PATH = $KeyPath
    $env:KEY_ALIAS = $KeyAlias
    $env:KEY_STORE_PASSWORD = $password
    $env:KEY_PASSWORD = $password
    # Only the old release APKs: "gradlew clean" would also delete the root build folder.
    if (Test-Path $outputs) { Remove-Item -Recurse -Force $outputs }
    Push-Location $root
    try {
        & .\gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    Remove-Item Env:KEY_STORE_PASSWORD, Env:KEY_PASSWORD -ErrorAction SilentlyContinue
}

$apks = @(Get-ChildItem "$outputs\*.apk")
if ($apks.Count -eq 0) { throw "No APKs in $outputs" }
foreach ($apk in $apks) {
    # Java prints warnings on stderr; with "Stop" PowerShell 5.1 would treat them as errors.
    $ErrorActionPreference = "Continue"
    $certs = & $apksigner verify --print-certs $apk.FullName 2>&1 | Out-String
    $verified = $LASTEXITCODE -eq 0 -and $certs -match "SHA-256 digest: $CertSha256"
    $ErrorActionPreference = "Stop"
    if (-not $verified) {
        throw "$($apk.Name) is not signed with the release certificate. Wrong password? Do not publish it."
    }
}

Write-Host "Signed APKs ($branch at $commit), certificate verified:"
$apks | ForEach-Object { Write-Host "  $($_.FullName)" }
