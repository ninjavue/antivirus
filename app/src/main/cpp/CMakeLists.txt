
cmake_minimum_required(VERSION 3.22.1)

project("antivirus")


add_library(${CMAKE_PROJECT_NAME} SHARED
        native/md5.cpp
        native/main.cpp
        native/security.cpp
        native/files.cpp
        native/checker.cpp
)


target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)