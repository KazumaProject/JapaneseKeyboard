#include "zenz_model_compat.h"
#include <iostream>
#include <memory>
#include <stdexcept>

static void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}

int main() {
    try {
        std::unique_ptr<gguf_context, decltype(&gguf_free)> metadata(gguf_init_empty(), gguf_free);
        std::vector<llama_model_kv_override> overrides;
        gguf_set_val_str(metadata.get(), "general.architecture", "gpt2");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.model", "gpt2");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.pre", "gpt2-small-japanese-char");
        const char *tokens[] = {"[UNK]", "[PAD]", "<s>", "</s>", "a"};
        gguf_set_arr_str(metadata.get(), "tokenizer.ggml.tokens", tokens, 5);
        // These are the incorrect values in the bundled v3.2 xsmall GGUF.
        gguf_set_val_u32(metadata.get(), "tokenizer.ggml.bos_token_id", 1);
        gguf_set_val_u32(metadata.get(), "tokenizer.ggml.eos_token_id", 2);
        require(zenz_model_overrides(metadata.get(), overrides), "legacy model rejected");
        require(overrides.size() == 6, "missing overrides or terminator");
        require(overrides[0].tag == LLAMA_KV_OVERRIDE_TYPE_STR &&
                std::strcmp(overrides[0].key, "tokenizer.ggml.pre") == 0 &&
                std::strcmp(overrides[0].val_str, "gpt-2") == 0, "wrong BPE mapping");
        const char *keys[] = {
                "tokenizer.ggml.unknown_token_id", "tokenizer.ggml.padding_token_id",
                "tokenizer.ggml.bos_token_id", "tokenizer.ggml.eos_token_id",
        };
        for (int id = 0; id < 4; ++id) {
            require(overrides[id + 1].tag == LLAMA_KV_OVERRIDE_TYPE_INT &&
                    std::strcmp(overrides[id + 1].key, keys[id]) == 0 &&
                    overrides[id + 1].val_i64 == id, "wrong special-token ID");
        }
        require(overrides.back().key[0] == '\0', "unterminated overrides");
        require(gguf_get_val_u32(metadata.get(), gguf_find_key(metadata.get(),
                "tokenizer.ggml.eos_token_id")) == 2, "model metadata was modified");

        const char *reordered[] = {"a", "</s>", "<s>", "[PAD]", "[UNK]"};
        gguf_set_arr_str(metadata.get(), "tokenizer.ggml.tokens", reordered, 5);
        require(zenz_model_overrides(metadata.get(), overrides), "reordered vocabulary rejected");
        require(overrides[1].val_i64 == 4 && overrides[2].val_i64 == 3 &&
                overrides[3].val_i64 == 2 && overrides[4].val_i64 == 1,
                "special IDs must come from the vocabulary");

        gguf_set_val_str(metadata.get(), "tokenizer.ggml.pre", "gpt-2");
        require(zenz_model_overrides(metadata.get(), overrides) && overrides.empty(),
                "standard GPT-2 metadata must be preserved");
        gguf_remove_key(metadata.get(), "tokenizer.ggml.pre");
        require(zenz_model_overrides(metadata.get(), overrides) && overrides.empty(),
                "missing pre-tokenizer must not enable legacy mapping");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.pre", "gpt2-small-japanese-char");
        gguf_set_val_str(metadata.get(), "general.architecture", "llama");
        require(!zenz_model_overrides(metadata.get(), overrides) && overrides.empty(),
                "non-GPT-2 model must be rejected");
        gguf_set_val_str(metadata.get(), "general.architecture", "gpt2");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.model", "llama");
        require(!zenz_model_overrides(metadata.get(), overrides), "non-BPE tokenizer accepted");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.model", "gpt2");
        gguf_set_arr_str(metadata.get(), "tokenizer.ggml.tokens", tokens, 3);
        require(!zenz_model_overrides(metadata.get(), overrides) && overrides.empty(),
                "missing EOS must be rejected without partial overrides");
        gguf_set_val_str(metadata.get(), "tokenizer.ggml.tokens", "invalid");
        require(!zenz_model_overrides(metadata.get(), overrides), "wrong token type accepted");
        std::cout << "Zenz model compatibility tests passed\n";
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
    return 0;
}
