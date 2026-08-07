cmake_minimum_required(VERSION 3.22.1)

project(showdown_vulkan)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(showdown_vulkan SHARED native_vulkan.cpp)

target_link_libraries(showdown_vulkan PRIVATE android log vulkan)
