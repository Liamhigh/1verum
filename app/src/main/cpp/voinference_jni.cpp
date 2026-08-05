// JNI bridge to llama.cpp for the three on-device models (ON_DEVICE_LLM_ARCHITECTURE.md):
// Gemma 3 (report writer), Phi-3 and Gemma 4 (communicator). One model is loaded per
// handle; the app can hold multiple handles for multiple loaded models at once.
//
// Deterministic by design (Constitution DETERMINISM_REQUIRED): sampling is greedy
// (temperature=0, no randomness), matching "deterministic when temperature=0" in
// ON_DEVICE_LLM_ARCHITECTURE.md.

#include <jni.h>
#include <android/log.h>
#include <thread>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "VoInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LoadedModel {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    // Owned by llama_model's metadata; valid for the model's lifetime. Null if the GGUF
    // has no embedded chat template (falls back to raw-text completion in that case).
    const char* chatTemplate = nullptr;
};

// Instruct-tuned models (Gemma 3/4, Phi-3) are trained almost exclusively on their chat
// template's turn structure — fed raw, unformatted text they very often emit an end-of-turn
// token as the very first sampled token (0 output). Wrapping the prompt as a single "user"
// turn via the model's own embedded template fixes this; falls back to the raw prompt only
// if the GGUF has no template at all.
std::string applyChatTemplate(const LoadedModel& loaded, const std::string& prompt) {
    if (loaded.chatTemplate == nullptr) return prompt;

    llama_chat_message message{"user", prompt.c_str()};
    std::vector<char> buf(prompt.size() * 2 + 256);
    int32_t needed = llama_chat_apply_template(
            loaded.chatTemplate, &message, 1, /*add_ass=*/true, buf.data(), static_cast<int32_t>(buf.size()));
    if (needed < 0) return prompt;
    if (static_cast<size_t>(needed) > buf.size()) {
        buf.resize(needed);
        needed = llama_chat_apply_template(
                loaded.chatTemplate, &message, 1, /*add_ass=*/true, buf.data(), static_cast<int32_t>(buf.size()));
        if (needed < 0) return prompt;
    }
    return std::string(buf.data(), needed);
}

std::once_flag g_backendInitFlag;

void ensureBackendInit() {
    std::call_once(g_backendInitFlag, [] { llama_backend_init(); });
}

std::string jstringToStd(JNIEnv* env, jstring s) {
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(s, chars);
    return result;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_verumomnis_forensic_engine_llm_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject /*thiz*/, jstring jModelPath, jint nCtx, jint nGpuLayers) {
    ensureBackendInit();

    const std::string modelPath = jstringToStd(env, jModelPath);

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = nGpuLayers;

    llama_model* model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        LOGE("Failed to load model: %s", modelPath.c_str());
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx);
    ctxParams.n_batch = static_cast<uint32_t>(nCtx);
    // Use the performance cluster only, not every core.
    //
    // hardware_concurrency() reports all cores, and on the big.LITTLE layout every
    // Android phone uses, that mixes fast cores with slow ones. Token generation is
    // memory-bandwidth bound and runs in lockstep, so each step waits on the
    // slowest thread: adding little cores *lowers* throughput. Measured on an
    // SM-A366B (8 cores), all-8 gave ~2 tok/s — a chat reply took six minutes and
    // read to the user as a frozen app.
    //
    // Half the cores, capped at 4, targets the big cluster on the usual 4+4 and
    // 2+6 arrangements without needing to probe the specific SoC.
    const auto cores = std::max(1u, std::thread::hardware_concurrency());
    const auto nThreads = static_cast<int32_t>(std::min(4u, std::max(1u, cores / 2)));
    ctxParams.n_threads = nThreads;
    ctxParams.n_threads_batch = nThreads;

    llama_context* ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("Failed to create context for model: %s", modelPath.c_str());
        llama_model_free(model);
        return 0;
    }

    auto* loaded = new LoadedModel{model, ctx, llama_model_get_vocab(model), llama_model_chat_template(model, nullptr)};
    LOGI("Loaded model %s (n_ctx=%d, n_threads=%d)", modelPath.c_str(), nCtx, nThreads);
    return reinterpret_cast<jlong>(loaded);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_verumomnis_forensic_engine_llm_LlamaBridge_nativeGenerate(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jPrompt, jint maxTokens) {
    if (handle == 0) {
        return env->NewStringUTF("INSUFFICIENT: model not loaded.");
    }
    auto* loaded = reinterpret_cast<LoadedModel*>(handle);
    const std::string rawPrompt = jstringToStd(env, jPrompt);
    const std::string prompt = applyChatTemplate(*loaded, rawPrompt);

    // Tokenize. Buffer sized generously (char count is always >= token count for UTF-8 text).
    std::vector<llama_token> promptTokens(prompt.size() + 16);
    const int32_t nPromptTokens = llama_tokenize(
            loaded->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            promptTokens.data(), static_cast<int32_t>(promptTokens.size()),
            /*add_special=*/true, /*parse_special=*/true);
    if (nPromptTokens < 0) {
        return env->NewStringUTF("INSUFFICIENT: failed to tokenize prompt.");
    }
    promptTokens.resize(nPromptTokens);

    const uint32_t nCtx = llama_n_ctx(loaded->ctx);
    if (static_cast<uint32_t>(nPromptTokens) + static_cast<uint32_t>(maxTokens) >= nCtx) {
        return env->NewStringUTF("INSUFFICIENT: prompt + requested output exceeds the model's context window.");
    }

    // Greedy sampling only — deterministic, no temperature/top-k/top-p randomness
    // (Constitution.DETERMINISM_REQUIRED).
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    char pieceBuf[256];

    llama_batch batch = llama_batch_get_one(promptTokens.data(), static_cast<int32_t>(promptTokens.size()));
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(loaded->ctx, batch) != 0) {
            LOGE("llama_decode failed at step %d", i);
            break;
        }

        llama_token newToken = llama_sampler_sample(sampler, loaded->ctx, -1);
        llama_sampler_accept(sampler, newToken);

        if (llama_vocab_is_eog(loaded->vocab, newToken)) {
            break;
        }

        const int32_t pieceLen = llama_token_to_piece(
                loaded->vocab, newToken, pieceBuf, sizeof(pieceBuf), /*lstrip=*/0, /*special=*/false);
        if (pieceLen > 0) {
            output.append(pieceBuf, pieceLen);
        }

        batch = llama_batch_get_one(&newToken, 1);
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_verumomnis_forensic_engine_llm_LlamaBridge_nativeFree(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;
    auto* loaded = reinterpret_cast<LoadedModel*>(handle);
    if (loaded->ctx != nullptr) llama_free(loaded->ctx);
    if (loaded->model != nullptr) llama_model_free(loaded->model);
    delete loaded;
}
