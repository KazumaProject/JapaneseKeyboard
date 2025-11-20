#include <jni.h>
#include <string>
#include <vector>
#include <mutex>        // ★ 追加
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "zenz-bridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "zenz-bridge", __VA_ARGS__)

// モデルと語彙のみグローバルで保持する。
// コンテキストは毎回生成・破棄する。
static llama_model *g_model = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ランタイム設定用パラメータ（Kotlin から変更可能）
static int g_param_n_ctx           = 512;
static int g_param_n_threads       = 4;
static int g_param_n_threads_batch = 4;
static int g_param_n_batch         = 512;
static std::mutex g_param_mutex;   // 設定値の読み書き用

// ------- 共通ヘルパー -------

// Swift の preprocessText とほぼ同じ:
// - 半角スペース -> 全角スペース (\u3000)
// - 改行は削除
static std::string preprocess_text(const std::string &text) {
    std::string out;
    out.reserve(text.size());

    for (unsigned char c: text) {
        if (c == ' ') {
            // UTF-8 の全角スペース U+3000
            out.append(u8"\u3000");
        } else if (c == '\n' || c == '\r') {
            // 捨てる
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
        // -n_tokens が必要な長さ
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

// 1トークン -> UTF-8 文字列
static std::string token_to_piece_str(llama_token token) {
    std::string out;
    if (!g_vocab) return out;

    // 最初は 8 バイト確保
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
        // 設定値の読み取り時のみロック（ごく短時間）
        std::lock_guard<std::mutex> lock(g_param_mutex);
        cparams.n_ctx           = g_param_n_ctx;
        cparams.n_threads       = g_param_n_threads;
        cparams.n_threads_batch = g_param_n_threads_batch;
        cparams.n_batch         = g_param_n_batch;
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
// ※ここで毎回コンテキストを生成・破棄する
static std::string pure_greedy_decoding(const std::string &leftSideContext, int maxCount) {
    if (!g_model || !g_vocab) {
        return "[error] model not initialized";
    }

    llama_context *ctx = create_context();
    if (!ctx) {
        return "[error] failed to create context";
    }

    // 1. 前処理 & トークナイズ (BOS なし, EOS なし)
    std::string pre = preprocess_text(leftSideContext);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/false, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        llama_free(ctx);
        return "";
    }

    // 2. プロンプトを一度まとめて decode
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

    // 3. greedy で maxCount トークンまで生成
    std::vector<llama_token> generated;
    generated.reserve(maxCount);

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    for (int i = 0; i < maxCount; ++i) {
        // 直近トークンの logits
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            LOGE("logits is null");
            break;
        }

        // argmax を取る (softmax 不要：logits の大小だけ見れば OK)
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

        // 次トークンを decode に食わせて、状態を進める
        llama_batch next_batch = llama_batch_get_one(&next, 1);
        int rc = llama_decode(ctx, next_batch);
        if (rc != 0) {
            LOGE("llama_decode(step) failed: %d", rc);
            break;
        }
    }

    // 4. 生成トークン列を detokenize
    std::string out;
    for (auto t: generated) {
        if (llama_vocab_is_control(g_vocab, t)) {
            // 制御トークンは表示しない
            continue;
        }
        out += token_to_piece_str(t);
    }

    // ★最後にコンテキストを破棄
    llama_free(ctx);

    return out;
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

    // 再 init 時のクリーンアップ
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // Android は CPU 前提
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
// Kotlin: external fun setRuntimeConfig(nCtx: Int, nThreads: Int)

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_setRuntimeConfig(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jint jNCtx,
        jint jNThreads
) {
    std::lock_guard<std::mutex> lock(g_param_mutex);

    int n_ctx     = jNCtx     > 0 ? jNCtx     : 512;
    int n_threads = jNThreads > 0 ? jNThreads : 4;

    // 適当に下限・上限
    if (n_ctx < 128)   n_ctx = 128;
    if (n_ctx > 4096)  n_ctx = 4096;

    if (n_threads < 1) n_threads = 1;
    if (n_threads > 8) n_threads = 8;

    g_param_n_ctx           = n_ctx;
    g_param_n_threads       = n_threads;
    g_param_n_threads_batch = n_threads;
    g_param_n_batch         = n_ctx;   // シンプルに n_ctx に合わせる

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
        return env->NewStringUTF("Model not initialized");
    }

    const char *c_prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(c_prompt);
    env->ReleaseStringUTFChars(jPrompt, c_prompt);

    // prompt は Kotlin 側で \uEE00 + 入力 + \uEE01 の形で組み立てたものを想定
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);

    return env->NewStringUTF(result.c_str());
}

// ------- JNI: 文脈 + 読み で変換（v3 っぽい） -------

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
        return env->NewStringUTF("Model not initialized");
    }

    // JNI 文字列を取得
    const char *c_left = env->GetStringUTFChars(jLeftContext, nullptr);
    const char *c_input = env->GetStringUTFChars(jInput, nullptr);

    std::string left = c_left ? c_left : "";
    std::string input = c_input ? c_input : "";

    env->ReleaseStringUTFChars(jLeftContext, c_left);
    env->ReleaseStringUTFChars(jInput, c_input);

    // Zenz v3 っぽい形:
    //   conditions(今回はなし) + contextTag + left + inputTag + input + outputTag
    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string outputTag = u8"\uEE01";

    std::string prompt;
    if (!left.empty()) {
        // 文脈あり
        prompt = contextTag + left + inputTag + input + outputTag;
    } else {
        // 文脈なし（従来の v1 と同じ形）
        prompt = inputTag + input + outputTag;
    }

    // ここで pure_greedy_decoding が毎回コンテキストを作って破棄してくれる
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);

    return env->NewStringUTF(result.c_str());
}

// ------- JNI: 条件 + 文脈 + 読み で変換（v3 条件つき）-------

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
        return env->NewStringUTF("Model not initialized");
    }

    // JNI 文字列を取得（null 許容）
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

    // Zenz v3 で使われるタグ
    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string profileTag = u8"\uEE03";
    const std::string topicTag = u8"\uEE04";
    const std::string styleTag = u8"\uEE05";
    const std::string preferenceTag = u8"\uEE06";
    const std::string outputTag = u8"\uEE01";

    // conditions を連結
    std::string conditions;
    if (!profile.empty()) {
        conditions += profileTag + profile;
    }
    if (!topic.empty()) {
        conditions += topicTag + topic;
    }
    if (!style.empty()) {
        conditions += styleTag + style;
    }
    if (!preference.empty()) {
        conditions += preferenceTag + preference;
    }

    // プロンプト構築
    std::string prompt;
    if (!left.empty()) {
        // 文脈あり: conditions + contextTag + left + inputTag + input + outputTag
        prompt = conditions + contextTag + left + inputTag + input + outputTag;
    } else {
        // 文脈なし: conditions + inputTag + input + outputTag
        prompt = conditions + inputTag + input + outputTag;
    }

    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens);

    return env->NewStringUTF(result.c_str());
}
