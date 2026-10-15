# Builds the signed QPipePipe release APKs.
#
#   powershell -ExecutionPolicy Bypass -File tools\release\build-release.ps1
#
# The keystore password is asked here, kept only in this process and never written to
# disk. The APKs end up in app\build\outputs\apk\release\.
param(
    [string]$KeyPath = "$env:USERPROFILE\keystores\qpipepipe-release.jks",
    [string]$KeyAlias = "qpipepipe",
    [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
if (-not (Test-Path $KeyPath)) { throw "Keystore not found: $KeyPath" }

$branch = git -C $root rev-parse --abbrev-ref HEAD
$commit = git -C $root rev-parse --short HEAD
$dirty = git -C $root status --porcelain --untracked-files=no
Write-Host "Building $branch at $commit"
if ($dirty) { Write-Warning "The working tree has uncommitted changes; they end up in the APKs." }

$secure = Read-Host "Keystore password" -AsSecureString
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

Write-Host "Signed APKs ($branch at $commit):"
Get-ChildItem "$outputs\*.apk" | ForEach-Object { Write-Host "  $($_.FullName)" }
