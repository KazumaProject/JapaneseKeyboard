#pragma once

#include <cstring>
#include <vector>
#include <llama.h>
#include <gguf.h>

// The azooKey fork uses the GPT-2 BPE rules for this pre-tokenizer, but
// ignores GGUF special-token IDs. Older Zenz GGUFs contain incorrect IDs.
// Apply the compatibility mapping at load time, without modifying the model
// file or upstream llama.cpp. Other tokenizers keep their own metadata.
inline bool zenz_model_overrides(
        const gguf_context *metadata,
        std::vector<llama_model_kv_override> &overrides
) {
    overrides.clear();
    const auto string_is = [&](const char *key, const char *value) {
        const int64_t index = gguf_find_key(metadata, key);
        return index >= 0 && gguf_get_kv_type(metadata, index) == GGUF_TYPE_STRING &&
               std::strcmp(gguf_get_val_str(metadata, index), value) == 0;
    };
    if (!string_is("tokenizer.ggml.pre", "gpt2-small-japanese-char")) {
        return true;
    }
    if (!string_is("general.architecture", "gpt2") ||
        !string_is("tokenizer.ggml.model", "gpt2")) {
        return false;
    }

    const int64_t tokens = gguf_find_key(metadata, "tokenizer.ggml.tokens");
    if (tokens < 0 || gguf_get_kv_type(metadata, tokens) != GGUF_TYPE_ARRAY ||
        gguf_get_arr_type(metadata, tokens) != GGUF_TYPE_STRING) {
        return false;
    }

    llama_model_kv_override pre{};
    pre.tag = LLAMA_KV_OVERRIDE_TYPE_STR;
    std::strcpy(pre.key, "tokenizer.ggml.pre");
    std::strcpy(pre.val_str, "gpt-2");
    overrides.push_back(pre);

    const struct { const char *key; const char *piece; } special_tokens[] = {
            {"tokenizer.ggml.unknown_token_id", "[UNK]"},
            {"tokenizer.ggml.padding_token_id", "[PAD]"},
            {"tokenizer.ggml.bos_token_id", "<s>"},
            {"tokenizer.ggml.eos_token_id", "</s>"},
    };
    for (const auto &special : special_tokens) {
        size_t id = 0;
        const size_t count = gguf_get_arr_n(metadata, tokens);
        while (id < count && std::strcmp(gguf_get_arr_str(metadata, tokens, id), special.piece) != 0) {
            ++id;
        }
        if (id == count) {
            overrides.clear();
            return false;
        }
        llama_model_kv_override value{};
        value.tag = LLAMA_KV_OVERRIDE_TYPE_INT;
        std::strcpy(value.key, special.key);
        value.val_i64 = static_cast<int64_t>(id);
        overrides.push_back(value);
    }
    overrides.emplace_back(); // llama.cpp terminates the list at an empty key.
    return true;
}
