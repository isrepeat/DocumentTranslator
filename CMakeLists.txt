cmake_minimum_required(VERSION 3.22.1)
project(AndroidAppKit LANGUAGES C CXX)

# Android preset восстанавливает пакет через cmake/AndroidToolchain.cmake.
# Desktop-конфигурация приходит сюда напрямую, поэтому восстанавливаем его здесь.
if (NOT ANDROID_BUILD_TOOLS_ROOT)
    find_program(android_build_tools_powershell NAMES powershell.exe REQUIRED)
    execute_process(
        COMMAND "${android_build_tools_powershell}" -NoProfile -ExecutionPolicy Bypass
            -File "${CMAKE_CURRENT_SOURCE_DIR}/build.ps1" -Command restore
        RESULT_VARIABLE android_build_tools_result
        OUTPUT_VARIABLE android_build_tools_output
        ERROR_VARIABLE android_build_tools_error
        OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    if (NOT android_build_tools_result EQUAL 0)
        message(FATAL_ERROR "AndroidBuildTools restore failed: ${android_build_tools_output}\n${android_build_tools_error}")
    endif()
    string(REGEX MATCH "[^\r\n]+$" ANDROID_BUILD_TOOLS_ROOT "${android_build_tools_output}")
    file(TO_CMAKE_PATH "${ANDROID_BUILD_TOOLS_ROOT}" ANDROID_BUILD_TOOLS_ROOT)
endif()

include("${ANDROID_BUILD_TOOLS_ROOT}/tools/cmake/InstallXamlRuntime.cmake")
fn_androidappkit_install_xaml_runtime("DocumentTranslator.AndroidHost")
include("${ANDROID_BUILD_TOOLS_ROOT}/tools/cmake/InstallAndroidAppPreviewerPluginSdk.cmake")
fn_androidappkit_install_android_app_previewer_plugin_sdk("DocumentTranslator.PreviewPlugin")

# Короткое имя сохраняет пути к промежуточным объектам ниже MAX_PATH, не
# меняя имя исходной папки DocumentTranslator.AndroidHost.
add_subdirectory(DocumentTranslator.AndroidHost MC.AHost)

# Desktop preview-plugin относится к AndroidAppKit, а не к решению WPF-хоста.
# На Android он не нужен: там используется androidappkit_android_host.
if (NOT ANDROID)
    add_subdirectory(DocumentTranslator.PreviewPlugin)
endif()

if (NOT ANDROID AND CMAKE_BUILD_TYPE STREQUAL "Debug")
    enable_testing()
    file(GLOB_RECURSE androidappkit_runtime_markup_test_files CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/DocumentTranslator.AndroidHost/Tests/RuntimeMarkup/*.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/DocumentTranslator.AndroidHost/Tests/RuntimeMarkup/*.h"
        "${CMAKE_CURRENT_SOURCE_DIR}/DocumentTranslator.AndroidHost/Tests/RuntimeMarkup/*.xaml"
    )
    # Тесты собираются явно при работе с ними, но не входят в обычную desktop
    # сборку: F5 Previewer-а не должен зависеть от их отдельного состояния.
    add_executable(RuntimeMarkupTests EXCLUDE_FROM_ALL ${androidappkit_runtime_markup_test_files})
    target_link_libraries(RuntimeMarkupTests PRIVATE androidappkit_application)
    target_compile_features(RuntimeMarkupTests PRIVATE cxx_std_20)
    add_test(NAME RuntimeMarkup COMMAND RuntimeMarkupTests "${CMAKE_CURRENT_SOURCE_DIR}")
endif()