include("${CMAKE_CURRENT_LIST_DIR}/NuGetSource.cmake")

function(fn_androidappkit_install_android_app_previewer_plugin_sdk project_name)
    set(androidappkit_plugin_sdk_package_name AndroidAppPreviewer.PluginSDK)
    set(androidappkit_plugin_sdk_packages_root "${CMAKE_SOURCE_DIR}/Build/Packages/${project_name}")
    # До введения versioned package layout архив распаковывался в этот каталог.
    # Он может содержать header по старому include-пути и не должен участвовать
    # в выборе SDK.
    set(androidappkit_plugin_sdk_legacy_directory
        "${androidappkit_plugin_sdk_packages_root}/AndroidAppPreviewer")
    if (EXISTS "${androidappkit_plugin_sdk_legacy_directory}")
        file(REMOVE_RECURSE "${androidappkit_plugin_sdk_legacy_directory}")
    endif()
    if (IS_DIRECTORY "${ANDROIDAPPKIT_NUGET_SOURCE}")
        # Native CMake-проекты не выполняют NuGet restore. Поэтому перед
        # find_package выбираем максимальный доступный пакет из локального feed,
        # как это сделал бы floating PackageReference в WPF-проекте.
        file(GLOB androidappkit_plugin_sdk_archives "${ANDROIDAPPKIT_NUGET_SOURCE}/${androidappkit_plugin_sdk_package_name}.*.nupkg")
        list(SORT androidappkit_plugin_sdk_archives COMPARE NATURAL ORDER DESCENDING)
        list(LENGTH androidappkit_plugin_sdk_archives androidappkit_plugin_sdk_archive_count)
        if (androidappkit_plugin_sdk_archive_count GREATER 0)
            list(GET androidappkit_plugin_sdk_archives 0 androidappkit_plugin_sdk_archive)
            # NAME_WE для имени с несколькими точками отбрасывает всю version
            # часть. Удаляем исключительно суффикс .nupkg, чтобы сохранить
            # каталог AndroidAppPreviewer.PluginSDK.<version>.
            get_filename_component(androidappkit_plugin_sdk_archive_name "${androidappkit_plugin_sdk_archive}" NAME)
            string(REGEX REPLACE "\\.nupkg$" "" androidappkit_plugin_sdk_archive_name
                "${androidappkit_plugin_sdk_archive_name}")
            set(androidappkit_plugin_sdk_extract_directory
                "${androidappkit_plugin_sdk_packages_root}/${androidappkit_plugin_sdk_archive_name}")
            set(androidappkit_plugin_sdk_config_directory
                "${androidappkit_plugin_sdk_extract_directory}/build/native/cmake")
            if (NOT EXISTS "${androidappkit_plugin_sdk_config_directory}/AndroidAppPreviewerPluginConfig.cmake")
                # Уже распакованную версию повторно не извлекаем: наличие config
                # означает, что пакет подготовлен для find_package.
                file(MAKE_DIRECTORY "${androidappkit_plugin_sdk_extract_directory}")
                execute_process(
                    COMMAND "${CMAKE_COMMAND}" -E tar xvf "${androidappkit_plugin_sdk_archive}"
                    WORKING_DIRECTORY "${androidappkit_plugin_sdk_extract_directory}"
                    COMMAND_ERROR_IS_FATAL ANY)
            endif()
            set(androidappkit_plugin_sdk_config_directory
                "${androidappkit_plugin_sdk_extract_directory}/build/native/cmake")
            if (NOT EXISTS "${androidappkit_plugin_sdk_config_directory}/AndroidAppPreviewerPluginConfig.cmake")
                message(FATAL_ERROR "Extracted plugin SDK does not contain its CMake config.")
            endif()
            unset(AndroidAppPreviewerPlugin_DIR CACHE)
            find_package(AndroidAppPreviewerPlugin CONFIG REQUIRED
                PATHS "${androidappkit_plugin_sdk_config_directory}"
                NO_DEFAULT_PATH
                NO_CMAKE_FIND_ROOT_PATH)
            return()
        endif()
    endif()

    # Offline fallback: если локальный feed недоступен, используем наиболее
    # свежую ранее распакованную корректную версию SDK.
    file(GLOB androidappkit_plugin_sdk_package_candidates LIST_DIRECTORIES true
        "${androidappkit_plugin_sdk_packages_root}/${androidappkit_plugin_sdk_package_name}.*")
    list(SORT androidappkit_plugin_sdk_package_candidates COMPARE NATURAL ORDER DESCENDING)
    foreach (androidappkit_plugin_sdk_package_candidate IN LISTS androidappkit_plugin_sdk_package_candidates)
        set(androidappkit_plugin_sdk_config_directory "${androidappkit_plugin_sdk_package_candidate}/build/native/cmake")
        if (EXISTS "${androidappkit_plugin_sdk_config_directory}/AndroidAppPreviewerPluginConfig.cmake")
            unset(AndroidAppPreviewerPlugin_DIR CACHE)
            find_package(AndroidAppPreviewerPlugin CONFIG REQUIRED
                PATHS "${androidappkit_plugin_sdk_config_directory}"
                NO_DEFAULT_PATH
                NO_CMAKE_FIND_ROOT_PATH)
            return()
        endif()
    endforeach()

    # Последний fallback нужен для окружений без локального feed и кэша.
    # После nuget install повторяем выбор уже распакованного package layout.
    find_program(androidappkit_plugin_sdk_nuget_executable NAMES nuget.exe REQUIRED)
    execute_process(
        COMMAND "${androidappkit_plugin_sdk_nuget_executable}" install "${androidappkit_plugin_sdk_package_name}"
            -Source "${ANDROIDAPPKIT_NUGET_SOURCE}"
            -OutputDirectory "${androidappkit_plugin_sdk_packages_root}"
            -NonInteractive
        COMMAND_ERROR_IS_FATAL ANY)
    fn_androidappkit_install_android_app_previewer_plugin_sdk("${project_name}")
endfunction()