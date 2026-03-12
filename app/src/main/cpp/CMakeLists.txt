# CMakeLists.txt - Oboe音频引擎配置
cmake_minimum_required(VERSION 3.22.1)
project("echo_audio")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找Oboe库
find_package(oboe REQUIRED CONFIG)

# 添加日志库
find_library(log-lib log)

# 创建共享库
add_library(
    echo_audio
    SHARED
    audio_engine.cpp
    audio_capture.cpp
    audio_playback.cpp
    volume_limiter.cpp
    jni_bridge.cpp
)

# 链接库
target_link_libraries(
    echo_audio
    oboe::oboe
    ${log-lib}
    android
)

# 编译选项
target_compile_options(echo_audio PRIVATE
    -O3
    -ffast-math
    -fomit-frame-pointer
    -Wall
    -Wextra
)
