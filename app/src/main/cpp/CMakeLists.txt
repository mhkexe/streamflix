cmake_minimum_required(VERSION 3.22.1)

project("nextvision-keys")

add_library(
        nextvision-keys
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        nextvision-keys
        ${log-lib})
