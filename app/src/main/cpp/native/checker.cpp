//
// Created by Umrzoq on 7/11/2025.
//
#include "md5.h"
#include <fstream>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <vector>
#include <string>
#include <set>
#include <sstream>

#define LOG_TAG "VirusScanner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Helper: Read virus hashes from assets/virus.txt using AAssetManager
std::set<std::string> loadVirusHashes(AAssetManager* mgr) {
    std::set<std::string> hashes;
    AAsset* asset = AAssetManager_open(mgr, "virus.txt", AASSET_MODE_BUFFER);
    if (!asset) return hashes;
    size_t size = AAsset_getLength(asset);
    const char* data = (const char*)AAsset_getBuffer(asset);
    if (data && size > 0) {
        std::string content(data, size);
        size_t pos = 0, end;
        while ((end = content.find('\n', pos)) != std::string::npos) {
            std::string hash = content.substr(pos, end - pos);
            if (!hash.empty()) hashes.insert(hash);
            pos = end + 1;
        }
        // Last line
        if (pos < content.size()) {
            std::string hash = content.substr(pos);
            if (!hash.empty()) hashes.insert(hash);
        }
    }
    AAsset_close(asset);
    return hashes;
}

// Main scan function: returns vector of infected file paths and hashes
std::vector<std::pair<std::string, std::string>> scanForViruses(const std::vector<std::string>& filePaths, AAssetManager* mgr) {
    std::vector<std::pair<std::string, std::string>> infected;
    std::set<std::string> virusHashes = loadVirusHashes(mgr);
    if (virusHashes.empty()) return infected;
    const long long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    for (const auto& file : filePaths) {
        std::ifstream f(file, std::ios::binary);
        if (!f.is_open()) {
            LOGI("Cannot open file: %s", file.c_str());
            continue;
        }
        f.seekg(0, std::ios::end);
        long long size = f.tellg();
        f.seekg(0, std::ios::beg);
        if (size > MAX_FILE_SIZE) {
            LOGI("File too large, skipping: %s", file.c_str());
            continue;
        }
        std::ostringstream ss;
        ss << f.rdbuf();
        std::string fileData = ss.str();
        std::string hash = md5(fileData);
        LOGI("Scanned file: %s, MD5: %s", file.c_str(), hash.c_str());
        if (virusHashes.count(hash)) {
            infected.emplace_back(file, hash);
            LOGI("Virus detected: %s [%s]", file.c_str(), hash.c_str());
        }
    }
    return infected;
}
