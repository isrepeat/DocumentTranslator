#include "AnimationShaders.h"

#include "DocumentTranslator.Presentation/Effects/Shaders.h"

namespace mobileclock::android_host::renderer {
    es_renderer::OpenGlRenderer::ShaderProgramSources CreateShaderPrograms() {
        return mobileclock::presentation::effects::CreateShaderPrograms();
    }
}