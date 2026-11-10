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
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE) # 静的リンク

# Android 向け定義
add_definitions(-D__ANDROID__)

# ※重要: アーキテクチャ固有の最適化
# F-Droid は様々な端末で動くことを想定するため、あまり過激な命令セット(AVX512など)は
# Androidではそもそも使えませんが、ARM NEONなどは自動で有効になります。
# 基本的に -O3 があれば大丈夫です。

# The bundled zenz engine supplies all four Full Play ABIs.
# The llamafile kernel assumes ARM half-precision loads absent on ARMv7.
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(llama.cpp)

# The upstream CPU target enables SSE4.2 unconditionally on x86.
# The Android x86 ABI only guarantees SSSE3.
if(ANDROID_ABI STREQUAL "x86")
    get_target_property(ggml_cpu_options ggml-cpu COMPILE_OPTIONS)
    list(REMOVE_ITEM ggml_cpu_options "-msse4.2")
    set_property(TARGET ggml-cpu PROPERTY COMPILE_OPTIONS "${ggml_cpu_options}")
    get_target_property(ggml_cpu_definitions ggml-cpu COMPILE_DEFINITIONS)
    list(REMOVE_ITEM ggml_cpu_definitions "GGML_SSE42")
    set_property(TARGET ggml-cpu PROPERTY COMPILE_DEFINITIONS "${ggml_cpu_definitions}")
endif()

# -------------------------------------------------------------------
# zenz ブリッジ
# -------------------------------------------------------------------
add_library(zenz SHARED zenz_bridge.cpp)

target_include_directories(zenz PRIVATE
        ${CMAKE_SOURCE_DIR}
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/common
)

find_library(log-lib log)

target_link_libraries(zenz
        PRIVATE
        llama
        ggml
        ${log-lib}
)
