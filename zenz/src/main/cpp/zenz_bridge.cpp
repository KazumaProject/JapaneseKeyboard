#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdint>
#include <cmath>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "zenz-bridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "zenz-bridge", __VA_ARGS__)

// モデルと語彙のみグローバルで保持する。
// コンテキストは毎回生成・破棄する。
static llama_model *g_model = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ランタイム設定用パラメータ（Kotlin から変更可能）
static int g_param_n_ctx = 512;
static int g_param_n_threads = 4;
static int g_param_n_threads_batch = 4;
static int g_param_n_batch = 512;
static std::mutex g_param_mutex;   // 設定値の読み書き用

// 候補評価の結果タイプ
enum class CandidateEvaluationResultType {
    ERROR,
    PASS,
    FIX_REQUIRED,
    WHOLE_RESULT
};

// 候補評価の結果
struct CandidateEvaluationResult {
    CandidateEvaluationResultType type;
    float score;                // PASS の場合のスコア
    std::string prefix;         // FIX_REQUIRED の場合の接頭辞
    std::string whole_result;   // WHOLE_RESULT の場合の結果
};

// ------- JNI文字列変換（重要） -------
// llama_token_to_piece() が返すバイト列は不正UTF-8になり得るため、NewStringUTFは禁止。
// UTF-8(不正あり得る) -> UTF-16(不正は U+FFFD 置換) -> NewString で返す。

static inline void append_u16(std::u16string &out, uint32_t cp) {
    if (cp <= 0xFFFF) {
        out.push_back(static_cast<char16_t>(cp));
    } else {
        cp -= 0x10000;
        out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
        out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
    }
}

static std::u16string utf8_to_utf16_lossy(const uint8_t *s, size_t n) {
    std::u16string out;
    out.reserve(n);

    size_t i = 0;
    while (i < n) {
        uint8_t b0 = s[i];

        // ASCII
        if (b0 <= 0x7F) {
            out.push_back(static_cast<char16_t>(b0));
            i += 1;
            continue;
        }

        int len = 0;
        uint32_t cp = 0;
        if ((b0 & 0xE0) == 0xC0) { len = 2; cp = b0 & 0x1F; }
        else if ((b0 & 0xF0) == 0xE0) { len = 3; cp = b0 & 0x0F; }
        else if ((b0 & 0xF8) == 0xF0) { len = 4; cp = b0 & 0x07; }
        else {
            out.push_back(u'\uFFFD');
            i += 1;
            continue;
        }

        if (i + static_cast<size_t>(len) > n) {
            out.push_back(u'\uFFFD');
            break;
        }

        bool ok = true;
        for (int k = 1; k < len; ++k) {
            uint8_t bx = s[i + k];
            if ((bx & 0xC0) != 0x80) { ok = false; break; }
            cp = (cp << 6) | (bx & 0x3F);
        }

        if (ok) {
            // overlong
            if (len == 2 && cp < 0x80) ok = false;
            if (len == 3 && cp < 0x800) ok = false;
            if (len == 4 && cp < 0x10000) ok = false;

            // surrogate / range
            if (cp >= 0xD800 && cp <= 0xDFFF) ok = false;
            if (cp > 0x10FFFF) ok = false;
        }

        if (!ok) {
            out.push_back(u'\uFFFD');
            i += 1; // resync
            continue;
        }

        append_u16(out, cp);
        i += static_cast<size_t>(len);
    }

    return out;
}

static jstring toJString(JNIEnv *env, const std::string &bytes) {
    const auto *p = reinterpret_cast<const uint8_t *>(bytes.data());
    std::u16string u16 = utf8_to_utf16_lossy(p, bytes.size());
    return env->NewString(reinterpret_cast<const jchar *>(u16.data()),
                          static_cast<jsize>(u16.size()));
}

static jstring toJString(JNIEnv *env, const char *cstr) {
    if (!cstr) return env->NewString(reinterpret_cast<const jchar *>(u""), 0);
    return toJString(env, std::string(cstr));
}

// ------- 共通ヘルパー -------

// Swift の preprocessText とほぼ同じ:
// - 半角スペース -> 全角スペース (\u3000)
// - 改行は削除
static std::string preprocess_text(const std::string &text) {
    std::string out;
    out.reserve(text.size());

    for (unsigned char c: text) {
        if (c == ' ') {
            out.append(u8"\u3000");
        } else if (c == '\n' || c == '\r') {
            continue;
        } else {
            out.push_back(static_cast<char>(c));
        }
    }
    return out;
}

// text を tokenize して llama_token の配列にする
static std::vector<llama_token> tokenize_text(const std::string &text, bool add_bos, bool add_eos) {
    std::vector<llama_token> tokens;

    if (!g_vocab) {
        return tokens;
    }

    const int32_t text_len = (int32_t) text.size();

    // 最初は適当に大きめ
    int32_t n_max = text_len + (add_bos ? 2 : 1);
    tokens.resize(n_max);

    int32_t n_tokens = llama_tokenize(
            g_vocab,
            text.c_str(),
            text_len,
            tokens.data(),
            n_max,
            add_bos,
            /*parse_special=*/false);

    if (n_tokens < 0) {
        n_max = -n_tokens;
        tokens.resize(n_max);
        n_tokens = llama_tokenize(
                g_vocab,
                text.c_str(),
                text_len,
                tokens.data(),
                n_max,
                add_bos,
                /*parse_special=*/false);
    }

    if (n_tokens <= 0) {
        tokens.clear();
        return tokens;
    }

    tokens.resize(n_tokens);

    if (add_eos) {
        tokens.push_back(llama_vocab_eos(g_vocab));
    }

    return tokens;
}

// 1トークン -> UTF-8 文字列（不正UTF-8が混ざり得る）
static std::string token_to_piece_str(llama_token token) {
    std::string out;
    if (!g_vocab) return out;

    int32_t buf_size = 8;
    std::vector<char> buf(buf_size);

    int32_t n = llama_token_to_piece(
            g_vocab,
            token,
            buf.data(),
            buf_size,
            /*lstrip=*/0,
            /*special=*/false);

    if (n < 0) {
        buf_size = -n;
        buf.resize(buf_size);
        n = llama_token_to_piece(
                g_vocab,
                token,
                buf.data(),
                buf_size,
                0,
                false);
    }

    if (n > 0) {
        out.assign(buf.data(), buf.data() + n);
    }
    return out;
}

// llama_context を毎回生成するヘルパー
static llama_context *create_context() {
    if (!g_model) {
        return nullptr;
    }

    llama_context_params cparams = llama_context_default_params();

    {
        std::lock_guard<std::mutex> lock(g_param_mutex);
        cparams.n_ctx = g_param_n_ctx;
        cparams.n_threads = g_param_n_threads;
        cparams.n_threads_batch = g_param_n_threads_batch;
        cparams.n_batch = g_param_n_batch;
    }

    llama_context *ctx = llama_init_from_model(g_model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama_context");
    } else {
        LOGI("llama_context created: n_ctx=%d, n_threads=%d, n_batch=%d",
             cparams.n_ctx, cparams.n_threads, cparams.n_batch);
    }
    return ctx;
}

// Swift の pure_greedy_decoding 相当
static std::string pure_greedy_decoding(const std::string &leftSideContext, int maxCount) {
    if (!g_model || !g_vocab) {
        return "[error] model not initialized";
    }

    llama_context *ctx = create_context();
    if (!ctx) {
        return "[error] failed to create context";
    }

    std::string pre = preprocess_text(leftSideContext);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/false, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        llama_free(ctx);
        return "";
    }

    {
        llama_batch batch = llama_batch_get_one(
                prompt_tokens.data(),
                (int32_t) prompt_tokens.size()
        );
        int rc = llama_decode(ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode(prompt) failed: %d", rc);
            llama_free(ctx);
            return "";
        }
    }

    std::vector<llama_token> generated;
    generated.reserve(maxCount);

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    for (int i = 0; i < maxCount; ++i) {
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            LOGE("logits is null");
            break;
        }

        int best_id = 0;
        float best_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > best_logit) {
                best_logit = logits[tid];
                best_id = tid;
            }
        }

        llama_token next = (llama_token) best_id;
        if (next == eos) {
            break;
        }

        generated.push_back(next);

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        int rc = llama_decode(ctx, next_batch);
        if (rc != 0) {
            LOGE("llama_decode(step) failed: %d", rc);
            break;
        }
    }

    std::string out;
    for (auto t: generated) {
        if (llama_vocab_is_control(g_vocab, t)) {
            continue;
        }
        out += token_to_piece_str(t);
    }

    llama_free(ctx);
    return out;
}

// Swift の evaluate_candidate 相当
static CandidateEvaluationResult
candidate_evaluate(const std::string &prompt, const std::string &candidate_text) {
    CandidateEvaluationResult result;
    result.type = CandidateEvaluationResultType::ERROR;
    result.score = 0.0f;

    if (!g_model || !g_vocab) {
        LOGE("candidate_evaluate: model not initialized");
        return result;
    }

    llama_context *ctx = create_context();
    if (!ctx) {
        LOGE("candidate_evaluate: failed to create context");
        return result;
    }

    std::string pre_prompt = preprocess_text(prompt);
    std::string pre_candidate = preprocess_text(candidate_text);

    auto prompt_tokens = tokenize_text(pre_prompt, /*add_bos=*/false, /*add_eos=*/false);
    auto candidate_tokens = tokenize_text(pre_candidate, /*add_bos=*/false, /*add_eos=*/false);

    if (prompt_tokens.empty()) {
        LOGE("candidate_evaluate: prompt tokens empty");
        llama_free(ctx);
        return result;
    }

    std::vector<llama_token> all_tokens = prompt_tokens;
    all_tokens.insert(all_tokens.end(), candidate_tokens.begin(), candidate_tokens.end());

    // ★ 512固定だと長文で overflow するので必要量で確保
    const int32_t cap = (int32_t) all_tokens.size();
    llama_batch batch = llama_batch_init(cap, 0, 1);

    // プロンプト部分: logits不要（最後のトークンを除く）
    for (size_t i = 0; i + 1 < prompt_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = prompt_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 0;
        batch.n_tokens++;
    }

    // プロンプトの最後のトークンから候補の最後のトークンまで: logits必要
    size_t logits_start_pos = prompt_tokens.size() - 1;
    for (size_t i = logits_start_pos; i < all_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = all_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 1;
        batch.n_tokens++;
    }

    int rc = llama_decode(ctx, batch);
    if (rc != 0) {
        LOGE("candidate_evaluate: llama_decode failed: %d", rc);
        llama_batch_free(batch);
        llama_free(ctx);
        return result;
    }

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    float *all_logits = llama_get_logits(ctx);
    if (!all_logits) {
        LOGE("candidate_evaluate: all_logits is null");
        llama_batch_free(batch);
        llama_free(ctx);
        return result;
    }

    float total_score = 0.0f;

    for (size_t i = prompt_tokens.size(); i < all_tokens.size(); ++i) {
        llama_token expected_token = all_tokens[i];

        size_t logits_offset = (i - 1 - logits_start_pos) * (size_t) n_vocab;
        float *logits = all_logits + logits_offset;

        int32_t max_id = 0;
        float max_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > max_logit) {
                max_logit = logits[tid];
                max_id = tid;
            }
        }

        llama_token max_token = (llama_token) max_id;

        float sum_exp = 0.0f;
        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            sum_exp += expf(logits[tid] - max_logit);
        }
        float log_prob = logits[expected_token] - max_logit - logf(sum_exp);
        total_score += log_prob;

        if (max_token != expected_token) {
            if (max_token == eos) {
                std::string partial;
                for (size_t j = prompt_tokens.size(); j < i; ++j) {
                    llama_token t = all_tokens[j];
                    if (!llama_vocab_is_control(g_vocab, t)) {
                        partial += token_to_piece_str(t);
                    }
                }
                result.type = CandidateEvaluationResultType::WHOLE_RESULT;
                result.whole_result = partial;
                LOGI("candidate_evaluate: WHOLE_RESULT at pos %zu, result=%s", i, partial.c_str());
                llama_batch_free(batch);
                llama_free(ctx);
                return result;
            } else {
                std::string prefix;
                for (size_t j = prompt_tokens.size(); j < i; ++j) {
                    llama_token t = all_tokens[j];
                    if (!llama_vocab_is_control(g_vocab, t)) {
                        prefix += token_to_piece_str(t);
                    }
                }
                if (!llama_vocab_is_control(g_vocab, max_token)) {
                    prefix += token_to_piece_str(max_token);
                }
                result.type = CandidateEvaluationResultType::FIX_REQUIRED;
                result.prefix = prefix;
                LOGI("candidate_evaluate: FIX_REQUIRED at pos %zu, prefix=%s", i, prefix.c_str());
                llama_batch_free(batch);
                llama_free(ctx);
                return result;
            }
        }
    }

    result.type = CandidateEvaluationResultType::PASS;
    result.score = total_score;
    LOGI("candidate_evaluate: PASS, score=%f", total_score);

    llama_batch_free(batch);
    llama_free(ctx);
    return result;
}

// ------- JNI: モデル初期化 -------
// package com.kazumaproject.zenz; class ZenzEngine

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath
) {
    const char *c_model_path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("initModel: %s", c_model_path);

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("Failed to get vocab");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    env->ReleaseStringUTFChars(jModelPath, c_model_path);
}

// ------- JNI: ランタイム設定 (n_ctx / n_threads) -------

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_setRuntimeConfig(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jint jNCtx,
        jint jNThreads
) {
    std::lock_guard<std::mutex> lock(g_param_mutex);

    int n_ctx = jNCtx > 0 ? jNCtx : 512;
    int n_threads = jNThreads > 0 ? jNThreads : 4;

    if (n_ctx < 128) n_ctx = 128;
    if (n_ctx > 4096) n_ctx = 4096;

    if (n_threads < 1) n_threads = 1;
    if (n_threads > 8) n_threads = 8;

    g_param_n_ctx = n_ctx;
    g_param_n_threads = n_threads;
    g_param_n_threads_batch = n_threads;
    g_param_n_batch = n_ctx;

    LOGI("setRuntimeConfig: n_ctx=%d, n_threads=%d", n_ctx, n_threads);
}

// ------- JNI: 「後半の変換結果」を返す（v1 型） -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jPrompt,
        jint maxTokens
) {
    if (!g_model || !g_vocab) {
        return toJString(env, "Model not initialized");
    }

    const char *c_prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(c_prompt ? c_prompt : "");
    env->ReleaseStringUTFChars(jPrompt, c_prompt);

    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);

    // ★ NewStringUTFは禁止（不正UTF-8の可能性）
    return toJString(env, result);
}

// ------- JNI: 文脈 + 読み で変換 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jLeftContext,
        jstring jInput,
        jint maxTokens
) {
    if (!g_model || !g_vocab) {
        return toJString(env, "Model not initialized");
    }

    const char *c_left = env->GetStringUTFChars(jLeftContext, nullptr);
    const char *c_input = env->GetStringUTFChars(jInput, nullptr);

    std::string left = c_left ? c_left : "";
    std::string input = c_input ? c_input : "";

    env->ReleaseStringUTFChars(jLeftContext, c_left);
    env->ReleaseStringUTFChars(jInput, c_input);

    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string outputTag = u8"\uEE01";

    std::string prompt;
    if (!left.empty()) {
        prompt = contextTag + left + inputTag + input + outputTag;
    } else {
        prompt = inputTag + input + outputTag;
    }

    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);
    return toJString(env, result);
}

// ------- JNI: 条件 + 文脈 + 読み で変換 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContextAndConditions(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jInput,
        jint maxTokens
) {
    if (!g_model || !g_vocab) {
        return toJString(env, "Model not initialized");
    }

    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string input = c_input ? c_input : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);

    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string profileTag = u8"\uEE03";
    const std::string topicTag = u8"\uEE04";
    const std::string styleTag = u8"\uEE05";
    const std::string preferenceTag = u8"\uEE06";
    const std::string outputTag = u8"\uEE01";

    std::string conditions;
    if (!profile.empty()) conditions += profileTag + profile;
    if (!topic.empty()) conditions += topicTag + topic;
    if (!style.empty()) conditions += styleTag + style;
    if (!preference.empty()) conditions += preferenceTag + preference;

    std::string prompt;
    if (!left.empty()) {
        prompt = conditions + contextTag + left + inputTag + input + outputTag;
    } else {
        prompt = conditions + inputTag + input + outputTag;
    }

    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);
    return toJString(env, result);
}

// ------- JNI: 投機的デコーディングによる候補評価 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_candidateEvaluate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jInput,
        jstring jCandidate
) {
    if (!g_model || !g_vocab) {
        return toJString(env, "ERROR");
    }

    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;
    const char *c_candidate = jCandidate ? env->GetStringUTFChars(jCandidate, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string input = c_input ? c_input : "";
    std::string candidate = c_candidate ? c_candidate : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);
    if (c_candidate) env->ReleaseStringUTFChars(jCandidate, c_candidate);

    if (candidate.empty()) {
        return toJString(env, "ERROR");
    }

    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string profileTag = u8"\uEE03";
    const std::string topicTag = u8"\uEE04";
    const std::string styleTag = u8"\uEE05";
    const std::string preferenceTag = u8"\uEE06";
    const std::string outputTag = u8"\uEE01";

    std::string conditions;
    if (!profile.empty()) conditions += profileTag + profile;
    if (!topic.empty()) conditions += topicTag + topic;
    if (!style.empty()) conditions += styleTag + style;
    if (!preference.empty()) conditions += preferenceTag + preference;

    std::string prompt;
    if (!left.empty()) {
        prompt = conditions + contextTag + left + inputTag + input + outputTag;
    } else {
        prompt = conditions + inputTag + input + outputTag;
    }

    CandidateEvaluationResult eval_result = candidate_evaluate(prompt, candidate);

    std::string result_str;
    switch (eval_result.type) {
        case CandidateEvaluationResultType::PASS:
            result_str = "PASS:" + std::to_string(eval_result.score);
            break;
        case CandidateEvaluationResultType::FIX_REQUIRED:
            result_str = "FIX:" + eval_result.prefix;          // ここも不正UTF-8が混ざり得るので toJString 必須
            break;
        case CandidateEvaluationResultType::WHOLE_RESULT:
            result_str = "WHOLE:" + eval_result.whole_result;  // 同上
            break;
        case CandidateEvaluationResultType::ERROR:
        default:
            result_str = "ERROR";
            break;
    }

    return toJString(env, result_str);
}
