cmake_minimum_required(VERSION 3.22.1)
project(zenz_core LANGUAGES C CXX)

# -------------------------------------------------------------------
# ★重要: パフォーマンス設定
# -------------------------------------------------------------------
# 常に Release ビルドにする（これをしないと推論が劇的に遅くなります）
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

# 最適化オプションの強制
# -ffast-math を使いつつ、無限大(Infinity)の計算は許可する設定
add_compile_options(-O3 -ffast-math -fno-finite-math-only -DNDEBUG)

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

add_subdirectory(llama.cpp)

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
