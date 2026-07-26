cmake_minimum_required(VERSION 3.22.1)
project(voinference)

# On-device LLM inference bridge (ON_DEVICE_LLM_ARCHITECTURE.md). Wraps llama.cpp's
# public C API for the three models the app loads by device tier (DeviceTier.kt):
# Gemma 3 (report writer), Phi-3 and Gemma 4 (communicator).
#
# llama.cpp is NOT vendored into this repo's git history — it's fetched at CMake
# configure time, pinned to a fixed commit for reproducible builds. See
# DEPENDENCIES.md for the pin. Override with -DVO_LLAMA_CPP_COMMIT=<sha> if needed.

include(FetchContent)

set(VO_LLAMA_CPP_COMMIT "ff067f76dd8e9e05f0528056f1274adf01a54d70" CACHE STRING
    "llama.cpp commit pinned for reproducible native builds")

# Keep the native build lean: only the inference library, none of llama.cpp's own
# examples/tests/tools/server, which roughly triples build time and isn't used here.
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp
    GIT_TAG ${VO_LLAMA_CPP_COMMIT}
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(voinference SHARED voinference_jni.cpp)

# Android 15+ runs 16 KB memory pages on some devices, and Google Play requires every
# native library to be 16 KB-aligned. NDK r27 still defaults to 4 KB max-page-size, so
# without these flags the loader reports "LOAD segment not aligned" for libvoinference.so.
target_link_options(voinference PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

target_link_libraries(voinference PRIVATE llama ggml log android)
target_include_directories(voinference PRIVATE ${llama_cpp_SOURCE_DIR}/include ${llama_cpp_SOURCE_DIR}/ggml/include)
