# PrimeGram: on-device voice transcription (whisper.cpp), built as its own
# libprimewhisper.so.
#
# Two reasons this is a separate library and a separate CMakeLists:
#
#  * Flags. ggml needs C++17 and does not tolerate the whole-program LTO and
#    --exclude-libs set the parent uses for libtmessages. Those settings are directory
#    scoped, so overriding them here leaves the messenger's own build untouched — doing it
#    in the parent file silently relinks libtmessages with the wrong flags, which fails in
#    ffmpeg rather than anywhere near here.
#
#  * Blast radius. ggml is large and moves fast. As a standalone .so loaded lazily by the
#    Java side, it can fail to build, load or run without taking the messenger with it.

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR})

set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -O3 -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "-w -std=c++17 -DANDROID -O3 -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_SHARED_LINKER_FLAGS "-Wl,--gc-sections")

# Only the CPU backend is vendored; the rest were removed from the tree entirely.
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml EXCLUDE_FROM_ALL)

add_library(primewhisper SHARED
        ${WHISPER_DIR}/src/whisper.cpp
        ${WHISPER_DIR}/prime_whisper_jni.cpp)

target_compile_definitions(primewhisper PRIVATE GGML_USE_CPU WHISPER_VERSION="1.8.4")
target_include_directories(primewhisper PRIVATE
        ${WHISPER_DIR}
        ${WHISPER_DIR}/src
        ${WHISPER_DIR}/include
        ${WHISPER_DIR}/ggml/include
        ${WHISPER_DIR}/ggml/src)

target_link_libraries(primewhisper ggml log android)
