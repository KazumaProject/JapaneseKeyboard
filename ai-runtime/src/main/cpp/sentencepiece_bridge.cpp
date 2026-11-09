#include <jni.h>
#include "sentencepiece_processor.h"
#include <codecvt>
#include <locale>
#include <memory>
#include <string>
#include <vector>

namespace {
std::string utf8(JNIEnv * env, jstring value) {
    const auto * chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::u16string utf16(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(utf16);
}

void error(JNIEnv * env, const char * message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), message);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kazumaproject_ai_SentencePieceTokenizer_open(JNIEnv * env, jobject, jstring path) {
    auto model = std::make_unique<sentencepiece::SentencePieceProcessor>();
    if (!model->Load(utf8(env, path)).ok() || model->bos_id() < 0 || model->eos_id() < 0) {
        error(env, "Invalid SentencePiece tokenizer or missing BOS/EOS tokens.");
        return 0;
    }
    return reinterpret_cast<jlong>(model.release());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_kazumaproject_ai_SentencePieceTokenizer_encode(JNIEnv * env, jobject, jlong handle, jstring text, jboolean bos) {
    const auto * model = reinterpret_cast<const sentencepiece::SentencePieceProcessor *>(handle);
    std::vector<int> tokens;
    if (!model->Encode(utf8(env, text), &tokens).ok()) {
        error(env, "Could not tokenize candidate text.");
        return nullptr;
    }
    if (bos) tokens.insert(tokens.begin(), model->bos_id());
    auto result = env->NewIntArray(tokens.size());
    if (result && !tokens.empty()) env->SetIntArrayRegion(result, 0, tokens.size(), tokens.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kazumaproject_ai_SentencePieceTokenizer_close(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
}
