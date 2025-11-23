cmake_minimum_required(VERSION 3.22.1)
project(zenz_core LANGUAGES C CXX)

# -------------------------------------------------------------------
# ★ パフォーマンス設定
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
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE) # 静的リンク

# Android 向け定義
add_definitions(-D__ANDROID__)

# llama.cpp へのパスを絶対パスに解決
# (CMake をどこから呼んでも安定したパスになる)
set(LLAMA_DIR_RELATIVE "${CMAKE_CURRENT_LIST_DIR}/llama.cpp")
get_filename_component(LLAMA_DIR "${LLAMA_DIR_RELATIVE}" ABSOLUTE)

# ビルド用バイナリディレクトリも明示
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# -------------------------------------------------------------------
# zenz ブリッジ
# -------------------------------------------------------------------
add_library(zenz SHARED zenz_bridge.cpp)

target_include_directories(zenz PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}/ggml/include
        ${LLAMA_DIR}/common
)

find_library(log-lib log)

target_link_libraries(zenz
        PRIVATE
        llama
        ggml
        ${log-lib}
)

# -------------------------------------------------------------------
# 再現ビルド／リンクまわりの調整
#   -Wl,--gc-sections : 使われていないセクションの除去
#   -flto             : LTO を有効化
#   -Wl,--exclude-libs,ALL : すべての static lib を「外から見えない」ようにして
#                             リンク結果を安定させる
# -------------------------------------------------------------------
if (ANDROID)
    target_link_options(
            zenz
            PRIVATE
            -Wl,--gc-sections
            -flto
            -Wl,--exclude-libs,ALL
    )
endif()
