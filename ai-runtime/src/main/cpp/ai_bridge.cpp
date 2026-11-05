#include <jni.h>
#include "llama.h"
#include "llama-cpp.h"
#include "gguf.h"
#include "ggml-backend.h"
#include "chat.h"
#include "nlohmann/json.hpp"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <algorithm>
#include <atomic>
#include <codecvt>
#include <locale>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <unordered_map>

namespace {
std::string utf8(JNIEnv * env, jstring value) {
    if (!value) return {};
    const auto * chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("Could not read AI input.");
    std::u16string text(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(text);
}
jstring java_string(JNIEnv * env, const std::string & value) {
    auto text = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(value);
    return env->NewString(reinterpret_cast<const jchar *>(text.data()), static_cast<jsize>(text.size()));
}
void report(JNIEnv * env, const std::exception & error) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
std::string metadata_string(const gguf_context * ctx, const char * key) {
    auto index = gguf_find_key(ctx, key);
    return index >= 0 && gguf_get_kv_type(ctx, index) == GGUF_TYPE_STRING ? gguf_get_val_str(ctx, index) : "";
}
bool metadata_bool(const gguf_context * ctx, const char * key) {
    auto index = gguf_find_key(ctx, key);
    return index >= 0 && gguf_get_kv_type(ctx, index) == GGUF_TYPE_BOOL && gguf_get_val_bool(ctx, index);
}
struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    mtmd_context * media = nullptr;
    common_chat_templates_ptr templates;
    bool pfml_translation = false;
    std::atomic<bool> cancelled{false};
    int flags = 0;
    ~Engine() {
        templates.reset();
        if (media) mtmd_free(media);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> engines;
std::atomic<jlong> next_handle{1};
std::shared_ptr<Engine> get_engine(jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = engines.find(handle);
    if (it == engines.end()) throw std::runtime_error("AI model is not ready.");
    return it->second;
}
void check_cancelled(const Engine & engine) {
    if (engine.cancelled.load()) throw std::runtime_error("AI request was cancelled.");
}

std::string generate(Engine & engine, const std::string & input, const std::string & media_path, int media_type, const std::string & instruction, const std::string & target_language) {
    check_cancelled(engine);
    llama_memory_clear(llama_get_memory(engine.context), true);
    auto * vocab = llama_model_get_vocab(engine.model);
    const auto context_size = llama_n_ctx(engine.context);
    const int max_output = 512;
    std::string user_input = input;
    if (media_type != 0) {
        const int needed = media_type == 1 ? 1 : 2;
        if (!engine.media || !(engine.flags & needed)) throw std::runtime_error("The selected model does not support this media input.");
        const std::string marker = mtmd_get_marker(engine.media);
        user_input = media_type == 2 ? input + "\n" + marker : marker + "\n" + input;
    }
    std::unique_ptr<common_chat_session> session;
    const bool structured_translation = engine.pfml_translation && !target_language.empty();
    std::string prompt = instruction.empty() ? user_input :
        "Instruction:\n" + instruction + "\n\nInput:\n" + user_input + "\n\nOutput:";
    if (engine.templates) {
        common_chat_templates_inputs inputs;
        // PFML3 carries the task and target language in the template. A system instruction
        // would be folded into the source text by common_chat_session and translated literally.
        if (!instruction.empty() && !structured_translation) {
            common_chat_msg system;
            system.role = "system";
            system.content = instruction;
            inputs.messages.push_back(system);
        }
        common_chat_msg message;
        message.role = "user";
        message.content = user_input;
        inputs.messages.push_back(message);
        inputs.enable_thinking = false;
        if (!target_language.empty()) inputs.chat_template_kwargs["target_lang"] = nlohmann::json(target_language).dump();
        inputs.reasoning_format = COMMON_REASONING_FORMAT_DEEPSEEK;
        session = std::make_unique<common_chat_session>(engine.templates.get(), vocab, inputs);
        prompt = session->prompt().str();
    }
    llama_pos position = 0;
    if (media_type != 0) {
        const auto bitmap_wrapper = mtmd_helper_bitmap_init_from_file(engine.media, media_path.c_str(), false, mtmd_helper_init_opt_default());
        std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)> bitmap(bitmap_wrapper.bitmap, mtmd_bitmap_free);
        if (!bitmap) throw std::runtime_error("Could not decode the selected image or audio.");
        if (mtmd_bitmap_is_audio(bitmap.get()) != (media_type == 2)) throw std::runtime_error("Media content does not match the requested input type.");
        std::unique_ptr<mtmd_input_chunks, decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(), mtmd_input_chunks_free);
        mtmd_input_text text{prompt.c_str(), prompt.size(), true, true};
        const mtmd_bitmap * bitmaps[] = {bitmap.get()};
        if (mtmd_tokenize(engine.media, chunks.get(), &text, bitmaps, 1) != 0) throw std::runtime_error("Could not prepare media for this model.");
        if (mtmd_helper_get_n_pos(chunks.get()) + max_output > context_size) throw std::runtime_error("Media input exceeds the AI context limit.");
        check_cancelled(engine);
        if (mtmd_helper_eval_chunks(engine.media, engine.context, chunks.get(), 0, 0, 512, true, &position) != 0) throw std::runtime_error("AI media evaluation failed or was cancelled.");
    } else {
        const int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        if (count <= 0 || count + max_output > context_size) throw std::runtime_error("Text input exceeds the AI context limit.");
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true) < 0) throw std::runtime_error("AI tokenization failed.");
        for (size_t offset = 0; offset < tokens.size(); offset += 512) {
            check_cancelled(engine);
            const auto count_in_batch = static_cast<int32_t>(std::min<size_t>(512, tokens.size() - offset));
            llama_batch_ext_ptr batch(llama_batch_ext_init(engine.context));
            for (int32_t i = 0; i < count_in_batch; ++i) {
                const auto index = llama_batch_ext_add_token(batch.get(), 0, tokens[offset + i]);
                if (index < 0) throw std::runtime_error("Could not allocate the AI token batch.");
                llama_pos token_position = static_cast<llama_pos>(offset + i);
                llama_batch_ext_set_pos(batch.get(), index, &token_position);
                if (offset + i + 1 == tokens.size()) llama_batch_ext_set_output_logits(batch.get(), index, true);
            }
            if (llama_process(engine.context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) throw std::runtime_error("AI text evaluation failed or was cancelled.");
        }
        position = tokens.size();
    }
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
    common_chat_input generated;
    std::vector<std::string> stops = session ? session->additional_stops() : std::vector<std::string>{};
    if (engine.pfml_translation) {
        stops.emplace_back("<|plamo:tag|>");
        stops.emplace_back("<|plamo:bos|>");
    }
    for (int i = 0; i < max_output && position < static_cast<llama_pos>(context_size); ++i) {
        check_cancelled(engine);
        auto token = llama_sampler_sample(sampler.get(), engine.context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        auto length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        if (length < 0) {
            piece.resize(-length);
            length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        }
        if (length < 0) throw std::runtime_error("Could not decode AI output.");
        generated.append(std::string(piece.data(), length), token);
        bool stop = false;
        for (const auto & marker : stops) {
            if (!marker.empty() && generated.text.size() >= marker.size() &&
                generated.text.compare(generated.text.size() - marker.size(), marker.size(), marker) == 0) {
                generated.truncate(generated.text.size() - marker.size());
                stop = true;
                break;
            }
        }
        if (stop) break;
        llama_batch_ext_ptr batch(llama_batch_ext_init(engine.context));
        const auto index = llama_batch_ext_add_token(batch.get(), 0, token);
        if (index < 0) throw std::runtime_error("Could not allocate the AI output batch.");
        llama_batch_ext_set_pos(batch.get(), index, &position);
        llama_batch_ext_set_output_logits(batch.get(), index, true);
        if (llama_process(engine.context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) throw std::runtime_error("AI generation failed or was cancelled.");
        ++position;
    }
    check_cancelled(engine);
    return session ? session->finish(generated).content : generated.text;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL Java_com_kazumaproject_ai_GgufEngine_inspect(JNIEnv * env, jobject, jstring path) {
    try {
        gguf_init_params params{true, nullptr};
        std::unique_ptr<gguf_context, decltype(&gguf_free)> info(gguf_init_from_file(utf8(env, path).c_str(), params), gguf_free);
        if (!info) throw std::runtime_error("Invalid or incomplete GGUF model.");
        const bool projector = metadata_string(info.get(), "general.architecture") == "clip";
        const int flags = (metadata_bool(info.get(), "clip.has_vision_encoder") ? 1 : 0) |
                          (metadata_bool(info.get(), "clip.has_audio_encoder") ? 2 : 0);
        return java_string(env, common_json{{"name", metadata_string(info.get(), "general.name")}, {"flags", flags}, {"projector", projector}}.dump());
    } catch (const std::exception & error) { report(env, error); return nullptr; }
}
extern "C" JNIEXPORT jlong JNICALL Java_com_kazumaproject_ai_GgufEngine_initialize(JNIEnv * env, jobject, jstring path, jstring projector, jint threads, jboolean use_gpu) {
    try {
        static std::once_flag once;
        std::call_once(once, [] { llama_backend_init(); });
        auto engine = std::make_shared<Engine>();
        auto device = use_gpu ? ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU) : nullptr;
        if (use_gpu && !device) device = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_IGPU);
        if (use_gpu && !device) throw std::runtime_error("No compatible Vulkan GPU is available.");
        ggml_backend_dev_t devices[] = {device, nullptr};
        auto model_params = llama_model_default_params();
        // An empty device list prevents opportunistic GPU offload in CPU-only mode.
        model_params.devices = devices;
        model_params.n_gpu_layers = use_gpu ? -1 : 0;
        engine->model = llama_model_load_from_file(utf8(env, path).c_str(), model_params);
        if (!engine->model) throw std::runtime_error("Could not load this GGUF model. Its architecture, quantization, or memory requirements may be unsupported.");
        auto context_params = llama_context_default_params();
        context_params.n_ctx = std::min<uint32_t>(4096, llama_model_n_ctx_train(engine->model));
        context_params.n_batch = 512;
        context_params.n_ubatch = 256;
        context_params.n_threads = std::clamp<int>(threads, 1, 4);
        context_params.n_threads_batch = context_params.n_threads;
        context_params.offload_kqv = use_gpu;
        context_params.op_offload = use_gpu;
        context_params.abort_callback = [](void * data) { return static_cast<Engine *>(data)->cancelled.load(); };
        context_params.abort_callback_data = engine.get();
        engine->context = llama_init_from_model(engine->model, context_params);
        if (!engine->context) throw std::runtime_error("Could not allocate the AI context.");
        const auto projector_path = utf8(env, projector);
        if (!projector_path.empty()) {
            gguf_init_params info_params{true, nullptr};
            std::unique_ptr<gguf_context, decltype(&gguf_free)> info(
                gguf_init_from_file(projector_path.c_str(), info_params), gguf_free);
            if (!info) throw std::runtime_error("Could not inspect the multimodal projector.");
            auto params = mtmd_context_params_default();
            // Like LiteRT-LM, keep audio encoding on CPU while language generation
            // uses the selected backend.
            params.use_gpu = use_gpu && !metadata_bool(info.get(), "clip.has_audio_encoder");
            params.device = params.use_gpu ? device : nullptr;
            params.n_threads = context_params.n_threads;
            engine->media = mtmd_init_from_file(projector_path.c_str(), engine->model, params);
            if (!engine->media) throw std::runtime_error("The projector could not be loaded with this GGUF model.");
            engine->flags = (mtmd_support_vision(engine->media) ? 1 : 0) | (mtmd_support_audio(engine->media) ? 2 : 0);
        }
        if (!mtmd_helper_get_capabilities(engine->model, engine->media).text_gen) throw std::runtime_error("This model does not generate text.");
        const char * chat_template = llama_model_chat_template(engine->model, nullptr);
        if (chat_template && *chat_template) {
            engine->templates = common_chat_templates_init(engine->model, "");
            engine->pfml_translation = std::string(chat_template).find("<|plamo:tag|>dataset<|plamo:msg|>translation") != std::string::npos;
        }
        const auto handle = next_handle.fetch_add(1);
        std::lock_guard<std::mutex> lock(registry_mutex);
        engines.emplace(handle, engine);
        return handle;
    } catch (const std::exception & error) { report(env, error); return 0; }
}
extern "C" JNIEXPORT jint JNICALL Java_com_kazumaproject_ai_GgufEngine_capabilities(JNIEnv * env, jobject, jlong handle) {
    try { return get_engine(handle)->flags; } catch (const std::exception & error) { report(env, error); return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_com_kazumaproject_ai_GgufEngine_resetCancellation(JNIEnv * env, jobject, jlong handle) {
    try { get_engine(handle)->cancelled = false; } catch (const std::exception & error) { report(env, error); }
}
extern "C" JNIEXPORT jstring JNICALL Java_com_kazumaproject_ai_GgufEngine_generate(JNIEnv * env, jobject, jlong handle, jstring prompt, jstring media, jint type, jstring instruction, jstring target_language) {
    try { return java_string(env, generate(*get_engine(handle), utf8(env, prompt), utf8(env, media), type, utf8(env, instruction), utf8(env, target_language))); }
    catch (const std::exception & error) { report(env, error); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL Java_com_kazumaproject_ai_GgufEngine_cancel(JNIEnv *, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = engines.find(handle);
    if (it != engines.end()) it->second->cancelled = true;
}
extern "C" JNIEXPORT void JNICALL Java_com_kazumaproject_ai_GgufEngine_close(JNIEnv *, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    engines.erase(handle);
}
