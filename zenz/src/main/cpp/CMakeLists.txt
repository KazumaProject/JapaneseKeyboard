cmake_minimum_required(VERSION 3.22.1)
project(zenz_core LANGUAGES C CXX)

# -------------------------------------------------------------------
# ★重要: パフォーマンス設定
# -------------------------------------------------------------------
option(ZENZ_NATIVE_OPTIMIZED "Build the Zenz native library with release optimization flags" ON)

if(ZENZ_NATIVE_OPTIMIZED)
    # Release builds need fast inference; debug builds can opt in via zenzDebugOptimizedNative.
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
    add_compile_options(-O3 -ffast-math -fno-finite-math-only -DNDEBUG)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -------------------------------------------------------------------
# llama.cpp の設定
# -------------------------------------------------------------------
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE) # 静的リンク
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Preserve the fork's OpenMP threading backend for CPU inference.
set(GGML_OPENMP ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

# ※重要: アーキテクチャ固有の最適化
# F-Droid は様々な端末で動くことを想定するため、あまり過激な命令セット(AVX512など)は
# Androidではそもそも使えませんが、ARM NEONなどは自動で有効になります。
# 基本的に -O3 があれば大丈夫です。

# Use the Android ABI baseline, without requiring AVX on Intel devices.
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # The llamafile kernels use ARM half-precision loads absent on ARMv7.
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()
if(ANDROID_ABI MATCHES "^x86")
    set(GGML_AVX OFF CACHE BOOL "" FORCE)
    set(GGML_AVX2 OFF CACHE BOOL "" FORCE)
    set(GGML_BMI2 OFF CACHE BOOL "" FORCE)
    set(GGML_FMA OFF CACHE BOOL "" FORCE)
    set(GGML_F16C OFF CACHE BOOL "" FORCE)
endif()
if(ANDROID_ABI STREQUAL "x86")
    set(GGML_SSE42 OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(llama.cpp)

# -------------------------------------------------------------------
# zenz ブリッジ
# -------------------------------------------------------------------
add_library(zenz SHARED zenz_bridge.cpp)

target_include_directories(zenz PRIVATE
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
)

find_library(log-lib log)

target_link_libraries(zenz
        PRIVATE
        llama
        ggml
        ${log-lib}
)

# Full also packages another llama.cpp version in keyboard_ai.
target_link_options(zenz PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-z,max-page-size=16384")
