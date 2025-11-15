cmake_minimum_required(VERSION 3.22.1)
project(zenz_core LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# CMAKE_SOURCE_DIR = zenz/src/main/cpp を想定
# jniLibs         = zenz/src/main/jniLibs/${ANDROID_ABI}
set(LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# 事前ビルド済みの .so を IMPORTED ライブラリとして扱う
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml.so)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml-cpu.so)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libllama.so)

# JNI ブリッジ本体（ライブラリ名: zenz）
add_library(zenz SHARED
        zenz_bridge.cpp
)

# llama.h / ggml*.h が置いてあるフォルダを include path に追加
# (ggml-backend.h, ggml-cpu.h, ggml.h, llama.h が src/main/cpp にある想定)
target_include_directories(zenz PRIVATE
        ${CMAKE_SOURCE_DIR}
)

# Android の log ライブラリ
find_library(log-lib log)

# Android の log ライブラリ + llama/ggml にリンク
target_link_libraries(zenz
        PRIVATE
        llama
        ggml
        ggml-base
        ggml-cpu
        ${log-lib}
)
