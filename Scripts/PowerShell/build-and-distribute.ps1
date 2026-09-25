[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Drive')]
    [string]$Destination,

    # Пересобирает APK с текущим versionCode для быстрой тестовой переустановки.
    [switch]$KeepVersion,

    # Конфигурация нативной и Android-сборки.
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release'
)

$ErrorActionPreference = 'Stop'
$utf8Encoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8Encoding
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bumpVersion = Join-Path $PSScriptRoot 'bump-version.ps1'
$buildAndroid = Join-Path $PSScriptRoot 'build-android.ps1'
$uploadToDrive = Join-Path $PSScriptRoot 'upload-apk-to-drive.ps1'
$configurationDirectory = $Configuration.ToLowerInvariant()
$apkSuffix = if ($Configuration -eq 'Release') { 'release' } else { 'debug' }
$sourceApk = Join-Path $projectRoot "Build\DocumentTranslator.Android\outputs\apk\$configurationDirectory\DocumentTranslator.Android-$apkSuffix.apk"
$distributionOutput = Join-Path $projectRoot 'Build\distribution'

$version = & $bumpVersion -KeepVersion:$KeepVersion
if ($KeepVersion) { Write-Host "==> Keeping Android version $($version.VERSION_CODE) / $($version.VERSION_NAME) for a test reinstall" }
else { Write-Host "==> Using next Android version $($version.VERSION_CODE) / $($version.VERSION_NAME)" }
& $buildAndroid -Configuration $Configuration -AppVersionCode $version.VERSION_CODE -AppVersionName $version.VERSION_NAME

$destinationApk = Join-Path $distributionOutput "DocumentTranslator-$($version.VERSION_NAME).apk"
New-Item -ItemType Directory -Path $distributionOutput -Force | Out-Null
Copy-Item -LiteralPath $sourceApk -Destination $destinationApk -Force

if ($Destination -eq 'Drive') {
    Write-Host '==> Uploading DocumentTranslator APK to Google Drive'
    # Имя содержит версию, поэтому Google Drive хранит историю релизов.
    & $uploadToDrive -ApkPath $destinationApk
    Write-Host "APK uploaded to Google Drive: $destinationApk"
}