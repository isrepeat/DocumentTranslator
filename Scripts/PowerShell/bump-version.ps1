[CmdletBinding()]
param(
# Возвращает последнюю опубликованную версию вместо следующей.
    [switch]$KeepVersion
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$versionFile = Join-Path $projectRoot 'version.properties'
$distributionDirectory = Join-Path $projectRoot 'Build\distribution'

if (-not (Test-Path -LiteralPath $versionFile -PathType Leaf)) {
    throw "Version file not found: $versionFile"
}

$properties = ConvertFrom-StringData ([System.IO.File]::ReadAllText($versionFile))
$baseCode = 0
if (-not [int]::TryParse($properties.VERSION_CODE_BASE, [ref]$baseCode)) {
    throw 'version.properties must define integer VERSION_CODE_BASE.'
}
if ($properties.VERSION_NAME_BASE -notmatch '^\d+\.\d+$') {
    throw 'version.properties must define VERSION_NAME_BASE in major.minor format.'
}

$publishedVersions = if (Test-Path -LiteralPath $distributionDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $distributionDirectory -Filter 'DocumentTranslator-*.apk' -File | ForEach-Object {
        $match = [regex]::Match($_.Name, "^DocumentTranslator-$([regex]::Escape($properties.VERSION_NAME_BASE))\.(\d+)\.apk$")
        if ($match.Success) {
            [pscustomobject]@{
                Patch = [int]$match.Groups[1].Value
            }
        }
    }
}
$latestVersion = $publishedVersions | Sort-Object Patch, Code -Descending | Select-Object -First 1
if ($KeepVersion) {
    $patch = if ($null -eq $latestVersion) { 0 } else { $latestVersion.Patch }
} else {
    $patch = if ($null -eq $latestVersion) { 1 } else { $latestVersion.Patch + 1 }
}
$versionCode = $baseCode + $patch
$versionName = "$($properties.VERSION_NAME_BASE).$patch"

[pscustomobject]@{
    VERSION_CODE = $versionCode
    VERSION_NAME = $versionName
}