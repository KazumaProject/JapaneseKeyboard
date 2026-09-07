#include <jni.h>
#include <algorithm>
#include <string>
#include <string_view>
#include <vector>
#include <mutex>
#include <atomic>
#include <cstdint>
#include <cmath>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "zenz-bridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "zenz-bridge", __VA_ARGS__)

// モデルと語彙のみグローバルで保持する。
// コンテキストはセッションで使い回す。
static llama_model *g_model = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ランタイム設定用パラメータ（Kotlin から変更可能）
static int g_param_n_ctx = 512;
static int g_param_n_threads = 4;
static int g_param_n_threads_batch = 4;
static int g_param_n_batch = 512;
static std::mutex g_param_mutex;   // 設定値の読み書き用
static std::atomic<uint64_t> g_request_seq{0};
static bool g_backend_initialized = false;

struct RuntimeConfig {
    int n_ctx;
    int n_threads;
    int n_threads_batch;
    int n_batch;
};

struct ZenzSession {
    llama_context *ctx = nullptr;
    RuntimeConfig config{0, 0, 0, 0};
    std::mutex mutex;
};

static ZenzSession g_session;

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

// Zenz v3.x reserves the private-use range U+EE00..U+EE0F for prompt,
// alignment, and future protocol markers.  These code points are not
// necessarily classified as llama control tokens, so they must be handled by
// the Zenz decoder itself.
constexpr uint32_t kZenzProtocolMarkerFirst = 0xEE00;
constexpr uint32_t kZenzProtocolMarkerLast = 0xEE0F;

enum class ZenzGenerationTermination {
    EOG,
    PROTOCOL_MARKER,
    EMPTY_OUTPUT,
    TOKEN_LIMIT,
    CONTEXT_LIMIT,
    INVALID_UTF8,
    DECODE_ERROR,
    CANCELLED,
    MODEL_UNAVAILABLE,
    INVALID_REQUEST,
};

struct ZenzGenerationResult {
    std::string text;
    ZenzGenerationTermination termination = ZenzGenerationTermination::INVALID_REQUEST;

    bool accepted() const {
        return !text.empty() &&
               (termination == ZenzGenerationTermination::EOG ||
                termination == ZenzGenerationTermination::PROTOCOL_MARKER);
    }
};

enum class Utf8DecodeResult {
    OK,
    INCOMPLETE,
    INVALID,
};

static Utf8DecodeResult decode_utf8_codepoint(
        std::string_view text,
        size_t offset,
        uint32_t *codepoint,
        size_t *width
) {
    if (!codepoint || !width || offset >= text.size()) {
        return Utf8DecodeResult::INVALID;
    }

    const auto byte = [&](size_t index) -> uint8_t {
        return static_cast<uint8_t>(text[index]);
    };

    const uint8_t first = byte(offset);
    size_t sequence_width = 0;
    if (first <= 0x7F) {
        *codepoint = first;
        *width = 1;
        return Utf8DecodeResult::OK;
    } else if (first >= 0xC2 && first <= 0xDF) {
        sequence_width = 2;
    } else if (first >= 0xE0 && first <= 0xEF) {
        sequence_width = 3;
    } else if (first >= 0xF0 && first <= 0xF4) {
        sequence_width = 4;
    } else {
        return Utf8DecodeResult::INVALID;
    }

    if (offset + sequence_width > text.size()) {
        return Utf8DecodeResult::INCOMPLETE;
    }

    for (size_t i = 1; i < sequence_width; ++i) {
        if ((byte(offset + i) & 0xC0) != 0x80) {
            return Utf8DecodeResult::INVALID;
        }
    }

    uint32_t value = first & ((1u << (8 - sequence_width - 1)) - 1u);
    for (size_t i = 1; i < sequence_width; ++i) {
        value = (value << 6) | (byte(offset + i) & 0x3F);
    }

    // Reject overlong encodings, UTF-16 surrogates, and values outside
    // Unicode.  The first-byte constraints above already reject most of
    // these, but keeping the checks here makes the helper self-contained.
    if ((sequence_width == 2 && value < 0x80) ||
        (sequence_width == 3 && value < 0x800) ||
        (sequence_width == 4 && value < 0x10000) ||
        (value >= 0xD800 && value <= 0xDFFF) ||
        value > 0x10FFFF) {
        return Utf8DecodeResult::INVALID;
    }

    *codepoint = value;
    *width = sequence_width;
    return Utf8DecodeResult::OK;
}

static bool is_zenz_protocol_marker(uint32_t codepoint) {
    return codepoint >= kZenzProtocolMarkerFirst &&
           codepoint <= kZenzProtocolMarkerLast;
}

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

__attribute__((used)) static const char inputTag[] = u8"\uEE00";
__attribute__((used)) static const char outputTag[] = u8"\uEE01";
__attribute__((used)) static const char leftContextTag[] = u8"\uEE02";
__attribute__((used)) static const char profileTag[] = u8"\uEE03";
__attribute__((used)) static const char topicTag[] = u8"\uEE04";
__attribute__((used)) static const char styleTag[] = u8"\uEE05";
__attribute__((used)) static const char preferenceTag[] = u8"\uEE06";
__attribute__((used)) static const char rightContextTag[] = u8"\uEE07";

static std::string sanitize_prompt_field(std::string_view text);

static std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) {
        return "";
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

static std::string build_conditions(
        const std::string &profile,
        const std::string &topic,
        const std::string &style,
        const std::string &preference
) {
    std::string conditions;
    if (!profile.empty()) {
        conditions += profileTag;
        conditions += profile;
    }
    if (!topic.empty()) {
        conditions += topicTag;
        conditions += topic;
    }
    if (!style.empty()) {
        conditions += styleTag;
        conditions += style;
    }
    if (!preference.empty()) {
        conditions += preferenceTag;
        conditions += preference;
    }
    return conditions;
}

static std::string build_zenz_prompt(
        const std::string &profile,
        const std::string &topic,
        const std::string &style,
        const std::string &preference,
        const std::string &leftContext,
        const std::string &rightContext,
        const std::string &input
) {
    const std::string safeProfile = sanitize_prompt_field(profile);
    const std::string safeTopic = sanitize_prompt_field(topic);
    const std::string safeStyle = sanitize_prompt_field(style);
    const std::string safePreference = sanitize_prompt_field(preference);
    const std::string safeLeftContext = sanitize_prompt_field(leftContext);
    const std::string safeRightContext = sanitize_prompt_field(rightContext);
    const std::string safeInput = sanitize_prompt_field(input);

    std::string prompt = build_conditions(
            safeProfile,
            safeTopic,
            safeStyle,
            safePreference);
    if (!safeLeftContext.empty()) {
        prompt += leftContextTag;
        prompt += safeLeftContext;
    }
    if (!safeRightContext.empty()) {
        prompt += rightContextTag;
        prompt += safeRightContext;
    }
    prompt += inputTag;
    prompt += safeInput;
    prompt += outputTag;
    return prompt;
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

class ZenzOutputDecoder {
public:
    enum class AppendResult {
        CONTINUE,
        PROTOCOL_MARKER,
        INVALID_UTF8,
    };

    AppendResult append_piece(std::string_view piece) {
        pending_utf8_.append(piece.data(), piece.size());

        size_t offset = 0;
        while (offset < pending_utf8_.size()) {
            uint32_t codepoint = 0;
            size_t width = 0;
            const Utf8DecodeResult status = decode_utf8_codepoint(
                    pending_utf8_, offset, &codepoint, &width);
            if (status == Utf8DecodeResult::INCOMPLETE) {
                break;
            }
            if (status == Utf8DecodeResult::INVALID) {
                return AppendResult::INVALID_UTF8;
            }

            if (is_zenz_protocol_marker(codepoint)) {
                pending_utf8_.clear();
                return AppendResult::PROTOCOL_MARKER;
            }

            // A conversion candidate is user-visible text.  Prompt/control
            // bytes, replacement characters, and C0/DEL controls must never
            // cross the native boundary as candidate text.
            if (codepoint == 0xFFFD || codepoint < 0x20 || codepoint == 0x7F) {
                return AppendResult::INVALID_UTF8;
            }

            output_.append(pending_utf8_, offset, width);
            offset += width;
        }

        if (offset > 0) {
            pending_utf8_.erase(0, offset);
        }
        return AppendResult::CONTINUE;
    }

    bool finish() const {
        return pending_utf8_.empty();
    }

    bool has_text() const {
        return !output_.empty();
    }

    const std::string &text() const {
        return output_;
    }

private:
    std::string output_;
    std::string pending_utf8_;
};

static bool is_safe_user_text(std::string_view text) {
    ZenzOutputDecoder decoder;
    return decoder.append_piece(text) == ZenzOutputDecoder::AppendResult::CONTINUE &&
           decoder.finish();
}

// Structured prompt fields are user-controlled, while the private-use range
// is the model protocol itself.  Strip protocol/control code points before
// attaching field tags so context/profile text cannot alter the prompt
// grammar. Invalid UTF-8 is dropped from the field; the generated output is
// handled fail-closed instead.
static std::string sanitize_prompt_field(std::string_view text) {
    std::string sanitized;
    sanitized.reserve(text.size());

    size_t offset = 0;
    while (offset < text.size()) {
        uint32_t codepoint = 0;
        size_t width = 0;
        const Utf8DecodeResult status = decode_utf8_codepoint(
                text, offset, &codepoint, &width);
        if (status != Utf8DecodeResult::OK) {
            break;
        }

        if (!is_zenz_protocol_marker(codepoint) &&
            codepoint >= 0x20 && codepoint != 0x7F) {
            sanitized.append(text, offset, width);
        }
        offset += width;
    }
    return sanitized;
}

static std::string decode_token_range(
        const std::vector<llama_token> &tokens,
        size_t begin,
        size_t end,
        bool *valid
) {
    ZenzOutputDecoder decoder;
    if (begin > end || end > tokens.size()) {
        if (valid) *valid = false;
        return "";
    }

    for (size_t index = begin; index < end; ++index) {
        const llama_token token = tokens[index];
        if (g_vocab && llama_vocab_is_eog(g_vocab, token)) {
            if (valid) *valid = false;
            return "";
        }

        const std::string piece = token_to_piece_str(token);
        const ZenzOutputDecoder::AppendResult append_result =
                decoder.append_piece(piece);
        if (append_result != ZenzOutputDecoder::AppendResult::CONTINUE) {
            if (valid) *valid = false;
            return "";
        }
    }

    const bool complete = decoder.finish();
    if (valid) *valid = complete;
    return complete ? decoder.text() : "";
}

static RuntimeConfig get_runtime_config() {
    std::lock_guard<std::mutex> lock(g_param_mutex);
    return RuntimeConfig{
            g_param_n_ctx,
            g_param_n_threads,
            g_param_n_threads_batch,
            g_param_n_batch
    };
}

static bool same_runtime_config(const RuntimeConfig &lhs, const RuntimeConfig &rhs) {
    return lhs.n_ctx == rhs.n_ctx &&
           lhs.n_threads == rhs.n_threads &&
           lhs.n_threads_batch == rhs.n_threads_batch &&
           lhs.n_batch == rhs.n_batch;
}

static bool is_request_stale(uint64_t request_seq) {
    return request_seq != g_request_seq.load(std::memory_order_relaxed);
}

struct AbortRequestState {
    uint64_t request_seq;
};

static bool abort_if_stale(void *data) {
    auto *state = static_cast<AbortRequestState *>(data);
    return state && is_request_stale(state->request_seq);
}

static bool never_abort(void * /*data*/) {
    return false;
}

static void destroy_session_context_locked() {
    if (!g_session.ctx) {
        return;
    }
    llama_set_abort_callback(g_session.ctx, never_abort, nullptr);
    llama_synchronize(g_session.ctx);
    llama_free(g_session.ctx);
    g_session.ctx = nullptr;
    g_session.config = RuntimeConfig{0, 0, 0, 0};
}

static llama_context *ensure_session_context_locked() {
    if (!g_model) {
        return nullptr;
    }

    const RuntimeConfig config = get_runtime_config();
    if (g_session.ctx && same_runtime_config(g_session.config, config)) {
        return g_session.ctx;
    }

    destroy_session_context_locked();

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = config.n_ctx;
    cparams.n_threads = config.n_threads;
    cparams.n_threads_batch = config.n_threads_batch;
    cparams.n_batch = config.n_batch;

    g_session.ctx = llama_init_from_model(g_model, cparams);
    if (!g_session.ctx) {
        LOGE("Failed to create llama_context");
        return nullptr;
    }

    g_session.config = config;
    LOGI("llama_context created: n_ctx=%d, n_threads=%d, n_batch=%d",
         cparams.n_ctx, cparams.n_threads, cparams.n_batch);
    return g_session.ctx;
}

static ZenzGenerationResult no_zenz_result(ZenzGenerationTermination termination) {
    return ZenzGenerationResult{"", termination};
}

static ZenzGenerationResult finish_zenz_generation(
        const ZenzOutputDecoder &decoder,
        ZenzGenerationTermination termination
) {
    if (!decoder.finish()) {
        return no_zenz_result(ZenzGenerationTermination::INVALID_UTF8);
    }
    if (!decoder.has_text()) {
        return no_zenz_result(ZenzGenerationTermination::EMPTY_OUTPUT);
    }
    return ZenzGenerationResult{decoder.text(), termination};
}

static const char *zenz_generation_termination_name(
        ZenzGenerationTermination termination
) {
    switch (termination) {
        case ZenzGenerationTermination::EOG: return "eog";
        case ZenzGenerationTermination::PROTOCOL_MARKER: return "protocol_marker";
        case ZenzGenerationTermination::EMPTY_OUTPUT: return "empty_output";
        case ZenzGenerationTermination::TOKEN_LIMIT: return "token_limit";
        case ZenzGenerationTermination::CONTEXT_LIMIT: return "context_limit";
        case ZenzGenerationTermination::INVALID_UTF8: return "invalid_utf8";
        case ZenzGenerationTermination::DECODE_ERROR: return "decode_error";
        case ZenzGenerationTermination::CANCELLED: return "cancelled";
        case ZenzGenerationTermination::MODEL_UNAVAILABLE: return "model_unavailable";
        case ZenzGenerationTermination::INVALID_REQUEST: return "invalid_request";
    }
    return "unknown";
}

static std::string accepted_zenz_text(const ZenzGenerationResult &result) {
    LOGI("Zenz generation termination=%s accepted=%s bytes=%zu",
         zenz_generation_termination_name(result.termination),
         result.accepted() ? "true" : "false",
         result.text.size());
    return result.accepted() ? result.text : "";
}

// Swift の pure_greedy_decoding 相当。ただし、候補として返せるのは
// EOG または Zenz プロトコルマーカーで正常終了した完全な出力だけにする。
static ZenzGenerationResult pure_greedy_decoding(
        const std::string &leftSideContext,
        int maxCount,
        uint64_t request_seq
) {
    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq)) {
        return no_zenz_result(ZenzGenerationTermination::CANCELLED);
    }
    if (!g_model || !g_vocab) {
        return no_zenz_result(ZenzGenerationTermination::MODEL_UNAVAILABLE);
    }

    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        return no_zenz_result(ZenzGenerationTermination::DECODE_ERROR);
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);
    const auto clear_abort_callback = [&]() {
        llama_set_abort_callback(ctx, never_abort, nullptr);
    };

    std::string pre = preprocess_text(leftSideContext);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/false, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        clear_abort_callback();
        return no_zenz_result(ZenzGenerationTermination::INVALID_REQUEST);
    }

    const RuntimeConfig config = get_runtime_config();
    if (config.n_ctx <= 0 || prompt_tokens.size() >= static_cast<size_t>(config.n_ctx)) {
        clear_abort_callback();
        LOGE("pure_greedy_decoding: prompt exceeds context window: prompt=%zu n_ctx=%d",
             prompt_tokens.size(), config.n_ctx);
        return no_zenz_result(ZenzGenerationTermination::CONTEXT_LIMIT);
    }

    const int requested_count = std::max(maxCount, 0);
    const int available_count = config.n_ctx - static_cast<int>(prompt_tokens.size());
    const int generation_limit = std::min(requested_count, available_count);
    if (generation_limit <= 0) {
        clear_abort_callback();
        return no_zenz_result(
                requested_count <= 0
                        ? ZenzGenerationTermination::INVALID_REQUEST
                        : ZenzGenerationTermination::CONTEXT_LIMIT);
    }

    {
        llama_batch batch = llama_batch_get_one(
                prompt_tokens.data(),
                (int32_t) prompt_tokens.size()
        );
        int rc = llama_decode(ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode(prompt) failed: %d", rc);
            if (is_request_stale(request_seq)) {
                LOGI("pure_greedy_decoding aborted while decoding prompt");
            }
            clear_abort_callback();
            return no_zenz_result(
                    is_request_stale(request_seq)
                            ? ZenzGenerationTermination::CANCELLED
                            : ZenzGenerationTermination::DECODE_ERROR);
        }
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    ZenzOutputDecoder decoder;

    for (int i = 0; i < generation_limit; ++i) {
        if (is_request_stale(request_seq)) {
            clear_abort_callback();
            return no_zenz_result(ZenzGenerationTermination::CANCELLED);
        }

        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            LOGE("logits is null");
            clear_abort_callback();
            return no_zenz_result(ZenzGenerationTermination::DECODE_ERROR);
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
        if (llama_vocab_is_eog(g_vocab, next)) {
            const ZenzGenerationResult result = finish_zenz_generation(
                    decoder,
                    ZenzGenerationTermination::EOG);
            clear_abort_callback();
            return result;
        }

        const std::string piece = token_to_piece_str(next);
        const ZenzOutputDecoder::AppendResult append_result =
                decoder.append_piece(piece);
        if (append_result == ZenzOutputDecoder::AppendResult::PROTOCOL_MARKER) {
            const ZenzGenerationResult result = finish_zenz_generation(
                    decoder,
                    ZenzGenerationTermination::PROTOCOL_MARKER);
            clear_abort_callback();
            return result;
        }
        if (append_result == ZenzOutputDecoder::AppendResult::INVALID_UTF8) {
            clear_abort_callback();
            return no_zenz_result(ZenzGenerationTermination::INVALID_UTF8);
        }

        // The last allowed token does not need to be decoded again.  Reaching
        // this branch means the model did not provide a clean terminator.
        if (i + 1 >= generation_limit) {
            break;
        }

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        int rc = llama_decode(ctx, next_batch);
        if (rc != 0) {
            if (is_request_stale(request_seq)) {
                LOGI("pure_greedy_decoding aborted during token generation");
                clear_abort_callback();
                return no_zenz_result(ZenzGenerationTermination::CANCELLED);
            } else {
                LOGE("llama_decode(step) failed: %d", rc);
            }
            clear_abort_callback();
            return no_zenz_result(ZenzGenerationTermination::DECODE_ERROR);
        }
    }

    clear_abort_callback();
    return no_zenz_result(ZenzGenerationTermination::TOKEN_LIMIT);
}

// Swift の evaluate_candidate 相当
static CandidateEvaluationResult candidate_evaluate(
        const std::string &prompt,
        const std::string &candidate_text,
        uint64_t request_seq
) {
    CandidateEvaluationResult result;
    result.type = CandidateEvaluationResultType::ERROR;
    result.score = 0.0f;

    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq)) {
        return result;
    }
    if (!g_model || !g_vocab) {
        LOGE("candidate_evaluate: model not initialized");
        return result;
    }

    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        LOGE("candidate_evaluate: failed to create context");
        return result;
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

    std::string pre_prompt = preprocess_text(prompt);
    std::string pre_candidate = preprocess_text(candidate_text);

    auto prompt_tokens = tokenize_text(pre_prompt, /*add_bos=*/false, /*add_eos=*/false);
    auto candidate_tokens = tokenize_text(pre_candidate, /*add_bos=*/false, /*add_eos=*/false);

    if (prompt_tokens.empty() || candidate_tokens.empty() ||
        pre_candidate.empty() || !is_safe_user_text(pre_candidate)) {
        LOGE("candidate_evaluate: prompt tokens empty");
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

    std::vector<llama_token> all_tokens = prompt_tokens;
    all_tokens.insert(all_tokens.end(), candidate_tokens.begin(), candidate_tokens.end());

    const RuntimeConfig config = get_runtime_config();
    if (config.n_ctx <= 0 || all_tokens.size() > static_cast<size_t>(config.n_ctx)) {
        LOGE("candidate_evaluate: prompt and candidate exceed context window: tokens=%zu n_ctx=%d",
             all_tokens.size(), config.n_ctx);
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

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
        if (is_request_stale(request_seq)) {
            LOGI("candidate_evaluate aborted");
        } else {
            LOGE("candidate_evaluate: llama_decode failed: %d", rc);
        }
        llama_batch_free(batch);
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    float *all_logits = llama_get_logits(ctx);
    if (!all_logits) {
        LOGE("candidate_evaluate: all_logits is null");
        llama_batch_free(batch);
        llama_set_abort_callback(ctx, never_abort, nullptr);
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
        const std::string max_piece = token_to_piece_str(max_token);
        ZenzOutputDecoder max_piece_decoder;
        const ZenzOutputDecoder::AppendResult max_piece_result =
                max_piece_decoder.append_piece(max_piece);
        const bool max_token_is_protocol_marker =
                max_piece_result == ZenzOutputDecoder::AppendResult::PROTOCOL_MARKER;

        float sum_exp = 0.0f;
        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            sum_exp += expf(logits[tid] - max_logit);
        }
        float log_prob = logits[expected_token] - max_logit - logf(sum_exp);
        total_score += log_prob;

        if (max_token != expected_token) {
            if (llama_vocab_is_eog(g_vocab, max_token) || max_token_is_protocol_marker) {
                bool valid = false;
                const std::string partial = decode_token_range(
                        all_tokens,
                        prompt_tokens.size(),
                        i,
                        &valid);
                if (!valid || partial.empty()) {
                    result.type = CandidateEvaluationResultType::ERROR;
                    llama_batch_free(batch);
                    llama_set_abort_callback(ctx, never_abort, nullptr);
                    return result;
                }
                result.type = CandidateEvaluationResultType::WHOLE_RESULT;
                result.whole_result = partial;
                LOGI("candidate_evaluate: WHOLE_RESULT at pos %zu, result=%s", i, partial.c_str());
                llama_batch_free(batch);
                llama_set_abort_callback(ctx, never_abort, nullptr);
                return result;
            } else {
                bool valid = false;
                const std::string previous_prefix = decode_token_range(
                        all_tokens,
                        prompt_tokens.size(),
                        i,
                        &valid);
                if (!valid) {
                    result.type = CandidateEvaluationResultType::ERROR;
                    llama_batch_free(batch);
                    llama_set_abort_callback(ctx, never_abort, nullptr);
                    return result;
                }

                ZenzOutputDecoder prefix_decoder;
                if (prefix_decoder.append_piece(previous_prefix) !=
                            ZenzOutputDecoder::AppendResult::CONTINUE) {
                    result.type = CandidateEvaluationResultType::ERROR;
                    llama_batch_free(batch);
                    llama_set_abort_callback(ctx, never_abort, nullptr);
                    return result;
                }
                if (!llama_vocab_is_control(g_vocab, max_token) || !max_piece.empty()) {
                    if (max_piece_result != ZenzOutputDecoder::AppendResult::CONTINUE) {
                        result.type = CandidateEvaluationResultType::ERROR;
                        llama_batch_free(batch);
                        llama_set_abort_callback(ctx, never_abort, nullptr);
                        return result;
                    }
                    if (prefix_decoder.append_piece(max_piece) !=
                                ZenzOutputDecoder::AppendResult::CONTINUE) {
                        result.type = CandidateEvaluationResultType::ERROR;
                        llama_batch_free(batch);
                        llama_set_abort_callback(ctx, never_abort, nullptr);
                        return result;
                    }
                }
                if (!prefix_decoder.finish() || !prefix_decoder.has_text()) {
                    result.type = CandidateEvaluationResultType::ERROR;
                    llama_batch_free(batch);
                    llama_set_abort_callback(ctx, never_abort, nullptr);
                    return result;
                }
                result.type = CandidateEvaluationResultType::FIX_REQUIRED;
                result.prefix = prefix_decoder.text();
                LOGI("candidate_evaluate: FIX_REQUIRED at pos %zu, prefix=%s", i, result.prefix.c_str());
                llama_batch_free(batch);
                llama_set_abort_callback(ctx, never_abort, nullptr);
                return result;
            }
        }
    }

    result.type = CandidateEvaluationResultType::PASS;
    result.score = total_score;
    LOGI("candidate_evaluate: PASS, score=%f", total_score);

    llama_batch_free(batch);
    llama_set_abort_callback(ctx, never_abort, nullptr);
    return result;
}

static bool prefill_prompt_prefix_locked(
        llama_context *ctx,
        const std::vector<llama_token> &prompt_tokens
) {
    llama_kv_cache_clear(ctx);

    if (prompt_tokens.size() <= 1) {
        return true;
    }

    const int32_t cap = (int32_t) (prompt_tokens.size() - 1);
    llama_batch batch = llama_batch_init(cap, 0, 1);
    for (size_t i = 0; i + 1 < prompt_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = prompt_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 0;
        batch.n_tokens++;
    }

    const int rc = llama_decode(ctx, batch);
    llama_batch_free(batch);
    return rc == 0;
}

static float score_candidate_avg_logprob_reuse_prompt_locked(
        llama_context *ctx,
        const std::vector<llama_token> &prompt_tokens,
        const std::vector<llama_token> &candidate_tokens,
        uint64_t request_seq
) {
    if (is_request_stale(request_seq)) {
        return -INFINITY;
    }
    if (prompt_tokens.empty() || candidate_tokens.empty()) {
        return -INFINITY;
    }

    const RuntimeConfig config = get_runtime_config();
    if (config.n_ctx <= 0 ||
        prompt_tokens.size() + candidate_tokens.size() > static_cast<size_t>(config.n_ctx)) {
        return -INFINITY;
    }

    const llama_pos suffix_start = (llama_pos) (prompt_tokens.size() - 1);
    llama_kv_cache_seq_rm(ctx, 0, suffix_start, -1);

    const int32_t cap = (int32_t) (1 + candidate_tokens.size());
    llama_batch batch = llama_batch_init(cap, 0, 1);

    batch.token[batch.n_tokens] = prompt_tokens.back();
    batch.pos[batch.n_tokens] = suffix_start;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = 0;
    batch.logits[batch.n_tokens] = 1;
    batch.n_tokens++;

    for (size_t i = 0; i < candidate_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = candidate_tokens[i];
        batch.pos[batch.n_tokens] = suffix_start + 1 + (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 1;
        batch.n_tokens++;
    }

    const int rc = llama_decode(ctx, batch);
    if (rc != 0) {
        if (!is_request_stale(request_seq)) {
            LOGE("score_candidate_avg_logprob_reuse_prompt_locked: llama_decode failed: %d", rc);
        }
        llama_batch_free(batch);
        return -INFINITY;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    float *all_logits = llama_get_logits(ctx);
    if (!all_logits) {
        LOGE("score_candidate_avg_logprob_reuse_prompt_locked: all_logits is null");
        llama_batch_free(batch);
        return -INFINITY;
    }

    float total_score = 0.0f;
    for (size_t i = 0; i < candidate_tokens.size(); ++i) {
        llama_token expected_token = candidate_tokens[i];
        float *logits = all_logits + ((size_t) i * (size_t) n_vocab);

        float max_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > max_logit) {
                max_logit = logits[tid];
            }
        }

        double sum_exp = 0.0;
        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            sum_exp += exp((double) logits[tid] - (double) max_logit);
        }
        total_score += logits[expected_token] - max_logit - (float) log(sum_exp);
    }

    llama_batch_free(batch);
    return total_score / (float) candidate_tokens.size();
}

// ------- JNI: モデル初期化・キャンセル・解放 -------
// package com.kazumaproject.zenz; class ZenzEngine

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath
) {
    if (!jModelPath) {
        LOGE("initModel: model path is null");
        return JNI_FALSE;
    }

    const char *c_model_path = env->GetStringUTFChars(jModelPath, nullptr);
    if (!c_model_path) {
        LOGE("initModel: failed to read model path");
        return JNI_FALSE;
    }
    LOGI("initModel: %s", c_model_path);

    // A reload must also stop a decode that currently owns the session mutex.
    g_request_seq.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_session.mutex);
    destroy_session_context_locked();

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("Failed to get vocab");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(jModelPath, c_model_path);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_cancelCurrent(
        JNIEnv * /*env*/,
        jobject /*thiz*/
) {
    // Do not take g_session.mutex here. This method must remain callable from a Binder thread
    // while the actor thread is blocked inside llama_decode with that mutex held.
    g_request_seq.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_closeModel(
        JNIEnv * /*env*/,
        jobject /*thiz*/
) {
    g_request_seq.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_session.mutex);
    destroy_session_context_locked();

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }
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
    int n_ctx = jNCtx > 0 ? jNCtx : 512;
    int n_threads = jNThreads > 0 ? jNThreads : 4;

    if (n_ctx < 128) n_ctx = 128;
    if (n_ctx > 4096) n_ctx = 4096;

    if (n_threads < 1) n_threads = 1;
    if (n_threads > 8) n_threads = 8;

    RuntimeConfig new_config{
            n_ctx,
            n_threads,
            n_threads,
            n_ctx
    };

    {
        std::lock_guard<std::mutex> lock(g_param_mutex);
        g_param_n_ctx = new_config.n_ctx;
        g_param_n_threads = new_config.n_threads;
        g_param_n_threads_batch = new_config.n_threads_batch;
        g_param_n_batch = new_config.n_batch;
    }

    {
        std::lock_guard<std::mutex> session_lock(g_session.mutex);
        if (g_session.ctx && !same_runtime_config(g_session.config, new_config)) {
            destroy_session_context_locked();
        }
    }

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
    const char *c_prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(c_prompt ? c_prompt : "");
    env->ReleaseStringUTFChars(jPrompt, c_prompt);

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    const ZenzGenerationResult result = pure_greedy_decoding(
            prompt,
            /*maxCount=*/maxTokens,
            request_seq);

    // ★ NewStringUTFは禁止（不正UTF-8の可能性）
    return toJString(env, accepted_zenz_text(result));
}

// ------- JNI: 文脈 + 読み で変換 -------

static jstring generate_with_context_and_conditions(
        JNIEnv *env,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jint maxTokens
) {
    std::string profile = jstring_to_string(env, jProfile);
    std::string topic = jstring_to_string(env, jTopic);
    std::string style = jstring_to_string(env, jStyle);
    std::string preference = jstring_to_string(env, jPreference);
    std::string left = jstring_to_string(env, jLeftContext);
    std::string right = jstring_to_string(env, jRightContext);
    std::string input = jstring_to_string(env, jInput);

    std::string prompt = build_zenz_prompt(
            profile,
            topic,
            style,
            preference,
            left,
            right,
            input
    );

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    const ZenzGenerationResult result = pure_greedy_decoding(
            prompt,
            /*maxCount=*/maxTokens,
            request_seq);
    return toJString(env, accepted_zenz_text(result));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jLeftContext,
        jstring jInput,
        jint maxTokens
) {
    return generate_with_context_and_conditions(
            env,
            nullptr,
            nullptr,
            nullptr,
            nullptr,
            jLeftContext,
            nullptr,
            jInput,
            maxTokens
    );
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
    return generate_with_context_and_conditions(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            nullptr,
            jInput,
            maxTokens
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContextAndConditionsV32(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jint maxTokens
) {
    return generate_with_context_and_conditions(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            jRightContext,
            jInput,
            maxTokens
    );
}

// ------- JNI: 投機的デコーディングによる候補評価 -------

static jstring candidate_evaluate_with_context(
        JNIEnv *env,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jstring jCandidate
) {
    std::string profile = jstring_to_string(env, jProfile);
    std::string topic = jstring_to_string(env, jTopic);
    std::string style = jstring_to_string(env, jStyle);
    std::string preference = jstring_to_string(env, jPreference);
    std::string left = jstring_to_string(env, jLeftContext);
    std::string right = jstring_to_string(env, jRightContext);
    std::string input = jstring_to_string(env, jInput);
    std::string candidate = jstring_to_string(env, jCandidate);

    if (candidate.empty()) {
        return toJString(env, "ERROR");
    }

    std::string prompt = build_zenz_prompt(
            profile,
            topic,
            style,
            preference,
            left,
            right,
            input
    );

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    CandidateEvaluationResult eval_result = candidate_evaluate(prompt, candidate, request_seq);

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
    return candidate_evaluate_with_context(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            nullptr,
            jInput,
            jCandidate
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_candidateEvaluateV32(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jstring jCandidate
) {
    return candidate_evaluate_with_context(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            jRightContext,
            jInput,
            jCandidate
    );
}

static jfloatArray score_candidates_with_context(
        JNIEnv *env,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jobjectArray jCandidates
) {
    const jsize candidate_count = jCandidates ? env->GetArrayLength(jCandidates) : 0;
    jfloatArray result_array = env->NewFloatArray(candidate_count);
    if (!result_array) {
        return nullptr;
    }

    std::vector<jfloat> scores((size_t) candidate_count, -INFINITY);
    if (candidate_count <= 0) {
        return result_array;
    }

    std::string profile = jstring_to_string(env, jProfile);
    std::string topic = jstring_to_string(env, jTopic);
    std::string style = jstring_to_string(env, jStyle);
    std::string preference = jstring_to_string(env, jPreference);
    std::string left = jstring_to_string(env, jLeftContext);
    std::string right = jstring_to_string(env, jRightContext);
    std::string input = jstring_to_string(env, jInput);

    std::string prompt = build_zenz_prompt(
            profile,
            topic,
            style,
            preference,
            left,
            right,
            input
    );

    const std::string pre_prompt = preprocess_text(prompt);
    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;

    std::vector<std::string> candidate_strings((size_t) candidate_count);
    for (jsize i = 0; i < candidate_count; ++i) {
        auto *j_candidate = (jstring) env->GetObjectArrayElement(jCandidates, i);
        if (!j_candidate) {
            continue;
        }
        candidate_strings[(size_t) i] = jstring_to_string(env, j_candidate);
        env->DeleteLocalRef(j_candidate);
    }

    {
        std::unique_lock<std::mutex> session_lock(g_session.mutex);
        if (!g_model || !g_vocab) {
            LOGE("scoreCandidates: model not initialized");
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        llama_context *ctx = ensure_session_context_locked();
        if (!ctx) {
            LOGE("scoreCandidates: failed to create context");
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        AbortRequestState abort_state{request_seq};
        llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

        auto prompt_tokens = tokenize_text(pre_prompt, /*add_bos=*/false, /*add_eos=*/false);
        if (prompt_tokens.empty()) {
            llama_set_abort_callback(ctx, never_abort, nullptr);
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        const RuntimeConfig config = get_runtime_config();
        if (config.n_ctx <= 0 || prompt_tokens.size() >= static_cast<size_t>(config.n_ctx)) {
            LOGE("scoreCandidates: prompt exceeds context window: prompt=%zu n_ctx=%d",
                 prompt_tokens.size(), config.n_ctx);
            llama_set_abort_callback(ctx, never_abort, nullptr);
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        if (!prefill_prompt_prefix_locked(ctx, prompt_tokens)) {
            LOGE("scoreCandidates: failed to prefill prompt prefix");
            llama_set_abort_callback(ctx, never_abort, nullptr);
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        std::vector<std::vector<llama_token>> candidate_tokens_list((size_t) candidate_count);
        for (jsize i = 0; i < candidate_count; ++i) {
            if (candidate_strings[(size_t) i].empty()) {
                continue;
            }
            if (!is_safe_user_text(candidate_strings[(size_t) i])) {
                scores[(size_t) i] = -INFINITY;
                continue;
            }
            candidate_tokens_list[(size_t) i] = tokenize_text(
                    preprocess_text(candidate_strings[(size_t) i]),
                    /*add_bos=*/false,
                    /*add_eos=*/false
            );
        }

        for (jsize i = 0; i < candidate_count; ++i) {
            if (is_request_stale(request_seq)) {
                break;
            }
            if (candidate_tokens_list[(size_t) i].empty()) {
                scores[(size_t) i] = -INFINITY;
                continue;
            }

            scores[(size_t) i] = score_candidate_avg_logprob_reuse_prompt_locked(
                    ctx,
                    prompt_tokens,
                    candidate_tokens_list[(size_t) i],
                    request_seq
            );
        }
        llama_set_abort_callback(ctx, never_abort, nullptr);
    }

    env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
    return result_array;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_scoreCandidates(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jInput,
        jobjectArray jCandidates
) {
    return score_candidates_with_context(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            nullptr,
            jInput,
            jCandidates
    );
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_scoreCandidatesV32(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jobjectArray jCandidates
) {
    return score_candidates_with_context(
            env,
            jProfile,
            jTopic,
            jStyle,
            jPreference,
            jLeftContext,
            jRightContext,
            jInput,
            jCandidates
    );
}
