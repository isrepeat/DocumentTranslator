[CmdletBinding()]
param()

dynamicparam {
    $ErrorActionPreference = 'Stop'
    # CMake запускает Windows PowerShell в урезанном окружении, где cmdlet
    # Import-PowerShellDataFile может быть недоступен. Конфигурация проекта
    # контролируется репозиторием и обязана вернуть хеш-таблицу.
    $config = & ([scriptblock]::Create([System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'android-build.psd1'))))
    if ($config -isnot [hashtable]) {
        throw 'android-build.psd1 must return a hashtable.'
    }
    $version = $config.BuildToolsVersion
    if ($version -notmatch '^\d+\.\d+\.\d+$') {
        throw 'BuildToolsVersion must pin an exact major.minor.patch version.'
    }
    $packagesRoot = Join-Path $PSScriptRoot "Build\Packages\$($config.ArtifactName)"
    $packageRoot = Join-Path $packagesRoot "AndroidBuildTools.$version"
    $entryPoint = Join-Path $packageRoot 'tools\Invoke-Build.ps1'
    if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
        $nuget = (Get-Command nuget.exe -ErrorAction Stop).Source
        $source = if ($env:ANDROID_BUILD_TOOLS_SOURCE) { $env:ANDROID_BUILD_TOOLS_SOURCE } else { $config.BuildToolsSource }
        & $nuget install AndroidBuildTools -Version $version -Source $source -OutputDirectory $packagesRoot -NonInteractive -DirectDownload -NoCache -ForceEnglishOutput | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "AndroidBuildTools $version restore failed with exit code $LASTEXITCODE."
        }
    }

    # Получаем параметры из пакета: загрузчик не содержит списка команд и опций.
    $metadata = Get-Command -Name $entryPoint -ErrorAction Stop
    $common = [System.Management.Automation.PSCmdlet]::CommonParameters + [System.Management.Automation.PSCmdlet]::OptionalCommonParameters
    $parameters = [System.Management.Automation.RuntimeDefinedParameterDictionary]::new()
    foreach ($parameter in $metadata.Parameters.Values) {
        if ($parameter.Name -ne 'ProjectRoot' -and $parameter.Name -notin $common) {
            $parameters.Add($parameter.Name, [System.Management.Automation.RuntimeDefinedParameter]::new($parameter.Name, $parameter.ParameterType, $parameter.Attributes))
        }
    }
    $parameters
}

end {
    & $entryPoint -ProjectRoot $PSScriptRoot @PSBoundParameters
}