# DocumentTranslator: настройки сборки

Общий flow, bootstrap нового проекта, контракт `android-build.psd1`, XAML,
CMake, Gradle plugins и команды описаны в
[AndroidBuildTools BUILD-PIPELINE](UtilityHelpersLib/NugetProjects/AndroidBuildTools/BUILD-PIPELINE.md).
Этот файл содержит только значения и особенности DocumentTranslator.

## Конфигурация проекта

[android-build.psd1](android-build.psd1) закрепляет `AndroidBuildTools 1.0.39`
и задаёт данные приложения:

| Значение | DocumentTranslator |
| --- | --- |
| ArtifactName | `DocumentTranslator` |
| Android module | `DocumentTranslator.Android` |
| Android host | `DocumentTranslator.AndroidHost` |
| Application ID | `com.isrepeat.documenttranslator` |
| Native library | `libmobileclock.so` |
| CMake version variable | `MOBILECLOCK_PACKAGE_VERSION` |
| Android ABI | `arm64-v8a` |

Каталоги NuGet-пакетов находятся в `PackageDirectories`:

```powershell
PackageDirectories = @{
    AndroidBuildTools = 'Build\Packages\DocumentTranslator'
    XamlRuntime = 'Build\Packages\DocumentTranslator.AndroidHost'
    AndroidAppPreviewerPluginSdk = 'Build\Packages\DocumentTranslator.PreviewPlugin'
}
```

Source native-пакетов:

```powershell
PackageSources = @{
    Native = 'C:\NugetFeed'
}
```

## Обычная сборка

```powershell
./build.ps1 build-android -Configuration Debug
```

Результаты:

```text
Build/DocumentTranslator.AndroidHost/android/jniLibs/arm64-v8a/libmobileclock.so
Build/DocumentTranslator.Android/outputs/apk/debug/DocumentTranslator.Android-debug.apk
```

## XAML

DocumentTranslator использует два каталога XAML:

```powershell
Application = 'DocumentTranslator.Application'
UI = 'DocumentTranslator.UI'
```

Generator использует namespace `urn:mobileclock:xaml`, C++ namespace
`mobileclock::ui::control` и создаёт файлы в `!Generated`. Готовый
`XamlCompiler.exe` поставляется пакетом XamlRuntime; исходники компилятора не
собираются в этом проекте.

## Previewer

Собрать plugin и WPF-host без запуска окна:

```powershell
./build.ps1 run-android-app-previewer -Configuration Debug -BuildOnly
```

Пути к AndroidAppPreviewer и output plugin заданы в секции `Preview`
`android-build.psd1`.

## Distribution и Drive

Локальный versioned APK:

```powershell
./build.ps1 build-and-distribute -Destination Local -Configuration Release
```

Готовый файл создаётся в `Build/distribution` с именем
`DocumentTranslator-<версия>.apk`.

Публикация в Google Drive:

```powershell
./build.ps1 build-and-distribute -Destination Drive -Configuration Release
```

Секция `Drive` задаёт путь `Android/DocumentTranslator` и расположение OAuth
credentials вне репозитория.

## Release signing

Release использует properties-файл, заданный в
[Tools/Gradle/gradle.properties](Tools/Gradle/gradle.properties) через
`androidSigningProperties`. Его также можно передать через
`-PandroidSigningProperties` или `ANDROID_SIGNING_PROPERTIES`. Сам keystore и его
пароли не хранятся в проекте.