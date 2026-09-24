#pragma once
#include <jni.h>

#include "DocumentTranslator.Application/Core/AppSessionController.h"

#include <string>

namespace mobileclock::android_host::renderer {
    class AndroidCommandDispatcher final {
    public:
        AndroidCommandDispatcher() = default;
        ~AndroidCommandDispatcher();

        AndroidCommandDispatcher(const AndroidCommandDispatcher&) = delete;
        AndroidCommandDispatcher& operator=(const AndroidCommandDispatcher&) = delete;

        void Dispatch(
            mobileclock::application::core::AppSessionSignal signal,
            const mobileclock::application::core::AppSessionSignalData& data) const;
        void Log(const std::string& message) const;
        void SetDispatcher(JNIEnv* env, jobject value);

    private:
        void ClearDispatcher();

    private:
        JavaVM* javaVm = nullptr;
        jobject dispatcher = nullptr;
        jmethodID dispatchMethod = nullptr;
    };
}