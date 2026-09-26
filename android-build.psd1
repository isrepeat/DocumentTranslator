@{
    BuildToolsVersion = '1.0.5'
    BuildToolsSource = 'C:\NugetFeed'
    ArtifactName = 'DocumentTranslator'
    AndroidModule = 'DocumentTranslator.Android'
    AndroidHost = 'DocumentTranslator.AndroidHost'
    Application = 'DocumentTranslator.Application'
    UI = 'DocumentTranslator.UI'
    NativeLibrary = 'libmobileclock.so'
    AndroidPresetPrefix = 'android-arm64'
    CMakeVersionVariable = 'MOBILECLOCK_PACKAGE_VERSION'
    GradleRoot = 'Tools\Gradle'
    VersionFile = 'version.properties'
    DistributionDirectory = 'Build\distribution'
    Xaml = @{
        CompilerSource = 'UtilityHelpersLib\NugetProjects\XamlRuntime\Nuget\XamlCompiler'
        CompilerBuild = 'Build\DocumentTranslator.Application\xaml-compiler'
        Namespace = 'urn:mobileclock:xaml'
        ControlNamespace = 'mobileclock::ui::control'
        ControlIncludePrefix = 'DocumentTranslator.UI/Control'
    }
    Preview = @{
        ArtifactDirectory = 'Build\DocumentTranslator.PreviewPlugin'
        Root = '..\AndroidAppPreviewer'
        ProjectFile = 'AndroidAppPreviewer.WPF\AndroidAppPreviewer.WPF.csproj'
        Executable = '!VS_TMP\Build\{Configuration}\x64\AndroidAppPreviewer.WPF\AndroidAppPreviewer.exe'
        Plugin = 'Build\{Configuration}\x64\DocumentTranslator.PreviewPlugin\DocumentTranslator.PreviewPlugin.dll'
        Target = 'mobileclock_preview_plugin'
    }
    Drive = @{
        Path = @('Android', 'DocumentTranslator')
        OAuthClientPath = 'C:\WORK\Secrets\apkupdater-drive-oauth.json'
        TokenPath = 'C:\WORK\Secrets\apkupdater-drive-token.json'
    }
}