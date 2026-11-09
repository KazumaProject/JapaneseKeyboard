cmake_minimum_required(VERSION 3.22.1)
project(keyboard_ai LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

# Use pinned headers with the NDK's host shader compiler and Android Vulkan loader.
# The NDK's own Vulkan headers are older than this llama.cpp backend requires.
include(FetchContent)
FetchContent_Declare(ai_vulkan_headers
    URL https://codeload.github.com/KhronosGroup/Vulkan-Headers/tar.gz/0d3f509e57041fbd073a1ee84cd9ccd36d148446
    URL_HASH SHA256=e82005a6bd3289ce213232ef41e0f8722d1365ed72bf5cc4be56911c56bc30e0)
FetchContent_Declare(ai_spirv_headers
    URL https://codeload.github.com/KhronosGroup/SPIRV-Headers/tar.gz/04f10f650d514df88b76d25e83db360142c7b174
    URL_HASH SHA256=1b220e3eec1714f0451b0e3652979bd280edf10893f617837b88e6359a804ded)
FetchContent_MakeAvailable(ai_vulkan_headers)
FetchContent_GetProperties(ai_spirv_headers)
if (NOT ai_spirv_headers_POPULATED)
    FetchContent_Populate(ai_spirv_headers)
endif()
# Provide a build-tree package for the header-only dependency on SDK CMake 3.22.
configure_file(SPIRV-HeadersConfig.cmake.in
    "${ai_spirv_headers_BINARY_DIR}/SPIRV-HeadersConfig.cmake" @ONLY)
set(SPIRV-Headers_DIR "${ai_spirv_headers_BINARY_DIR}" CACHE PATH "" FORCE)
set(Vulkan_INCLUDE_DIR "${ai_vulkan_headers_SOURCE_DIR}/include" CACHE PATH "" FORCE)
if (CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
    set(shader_host darwin-x86_64)
elseif (CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
    set(shader_host windows-x86_64)
else()
    set(shader_host linux-x86_64)
endif()
find_program(Vulkan_GLSLC_EXECUTABLE NAMES glslc
    HINTS "${CMAKE_ANDROID_NDK}/shader-tools/${shader_host}" REQUIRED)
add_subdirectory(llama.cpp)
target_link_libraries(ggml-vulkan PRIVATE SPIRV-Headers::SPIRV-Headers)
add_library(keyboard_ai SHARED ai_bridge.cpp android_vulkan.cpp)
target_link_libraries(keyboard_ai PRIVATE llama-common mtmd Vulkan::Headers log)
# This Vulkan 1.1 entry point is absent from Android's API 24 loader. Resolve it
# at runtime so older devices can still load the library and use the CPU backend.
target_link_options(keyboard_ai PRIVATE "-Wl,--wrap=vkGetPhysicalDeviceFeatures2")
# Keep this llama/ggml version private; Zenz and LiteRT-LM use their own native libraries.
target_link_options(keyboard_ai PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-z,max-page-size=16384")

# The scorer's tokenizer is supplied with the model. Keep SentencePiece private to this library.
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
FetchContent_Declare(ai_sentencepiece
    URL https://codeload.github.com/google/sentencepiece/tar.gz/refs/tags/v0.2.1
    URL_HASH SHA256=c1a59e9259c9653ad0ade653dadff074cd31f0a6ff2a11316f67bee4189a8f1b)
FetchContent_MakeAvailable(ai_sentencepiece)
target_sources(keyboard_ai PRIVATE sentencepiece_bridge.cpp)
target_include_directories(keyboard_ai PRIVATE "${ai_sentencepiece_SOURCE_DIR}/src")
target_link_libraries(keyboard_ai PRIVATE sentencepiece-static)
