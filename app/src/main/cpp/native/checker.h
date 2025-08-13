#pragma once
#include <string>
#include <vector>
#include <utility>
#include <android/asset_manager_jni.h>

std::vector<std::pair<std::string, std::string>> scanForViruses(const std::vector<std::string>& filePaths, AAssetManager* mgr); 