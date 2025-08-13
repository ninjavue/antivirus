#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <cstring>
#include <algorithm>
#include <regex>
#include <android/log.h>
#define LOG_TAG "FileScanner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct FileInfo {
    std::string name;
    std::string path;
    long long size;
    bool isHidden;
    bool isSuspicious;
    std::string fileType;
};

// Helper function to get file size
long long getFileSize(const std::string& path) {
    struct stat st;
    if (stat(path.c_str(), &st) == 0) {
        return st.st_size;
    }
    return 0;
}

// Helper function to check if file is hidden
bool isHiddenFile(const std::string& name) {
    return name[0] == '.';
}

// Helper function to check if file is suspicious
bool isSuspiciousFile(const std::string& name) {
    std::string lowerName = name;
    std::transform(lowerName.begin(), lowerName.end(), lowerName.begin(), ::tolower);
    
    std::vector<std::string> suspiciousExtensions = {
        ".exe", ".apk", ".jar", ".bat", ".cmd", ".com", ".scr", ".pif", ".vbs", ".js"
    };
    
    for (const auto& ext : suspiciousExtensions) {
        if (lowerName.size() >= ext.size() &&
            lowerName.compare(lowerName.size() - ext.size(), ext.size(), ext) == 0) {
            return true;
        }
    }
    return false;
}

// Helper function to get file extension
std::string getFileExtension(const std::string& name) {
    size_t pos = name.find_last_of('.');
    if (pos != std::string::npos) {
        return name.substr(pos);
    }
    return "";
}

// Helper function to format file size
std::string formatFileSize(long long size) {
    const char* units[] = {"B", "KB", "MB", "GB", "TB"};
    int unitIndex = 0;
    double fileSize = static_cast<double>(size);
    
    while (fileSize >= 1024.0 && unitIndex < 4) {
        fileSize /= 1024.0;
        unitIndex++;
    }
    
    std::stringstream ss;
    ss << std::fixed << std::setprecision(2) << fileSize << " " << units[unitIndex];
    return ss.str();
}

// Scan directory recursively
void scanDirectory(const std::string& path, std::vector<FileInfo>& files, bool includeHidden) {
    DIR* dir = opendir(path.c_str());
    if (!dir) return;
    
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        
        // Skip . and ..
        if (name == "." || name == "..") continue;
        
        // Skip hidden files if not requested
        if (!includeHidden && isHiddenFile(name)) continue;
        
        std::string fullPath = path + "/" + name;
        LOGI("Checking file: %s", fullPath.c_str());
        struct stat st;
        
        if (stat(fullPath.c_str(), &st) == 0) {
            if (S_ISDIR(st.st_mode)) {
                // Recursively scan subdirectories
                scanDirectory(fullPath, files, includeHidden);
            } else {
                // Add file to list
                FileInfo file;
                file.name = name;
                file.path = fullPath;
                file.size = st.st_size;
                file.isHidden = isHiddenFile(name);
                file.isSuspicious = isSuspiciousFile(name);
                file.fileType = getFileExtension(name);
                files.push_back(file);
            }
        }
    }
    closedir(dir);
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getLargeFiles(
        JNIEnv* env,
        jobject /* this */,
        jstring rootPath,
        jlong minSize) {
    
    const char* path = env->GetStringUTFChars(rootPath, nullptr);
    std::vector<FileInfo> allFiles;
    
    // Scan all files
    scanDirectory(path, allFiles, true);
    
    // Filter large files
    std::vector<FileInfo> largeFiles;
    for (const auto& file : allFiles) {
        if (file.size >= minSize) {
            largeFiles.push_back(file);
        }
    }
    
    // Sort by size (largest first)
    std::sort(largeFiles.begin(), largeFiles.end(), 
              [](const FileInfo& a, const FileInfo& b) {
                  return a.size > b.size;
              });
    
    // Build JSON result
    std::stringstream result;
    result << "[";
    for (size_t i = 0; i < largeFiles.size(); ++i) {
        if (i > 0) result << ",";
        result << "{";
        result << "\"name\":\"" << largeFiles[i].name << "\",";
        result << "\"path\":\"" << largeFiles[i].path << "\",";
        result << "\"size\":" << largeFiles[i].size << ",";
        result << "\"formattedSize\":\"" << formatFileSize(largeFiles[i].size) << "\",";
        result << "\"isHidden\":" << (largeFiles[i].isHidden ? "true" : "false") << ",";
        result << "\"fileType\":\"" << largeFiles[i].fileType << "\"";
        result << "}";
    }
    result << "]";
    
    env->ReleaseStringUTFChars(rootPath, path);
    return env->NewStringUTF(result.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getSuspiciousFiles(
        JNIEnv* env,
        jobject /* this */,
        jstring rootPath) {
    
    const char* path = env->GetStringUTFChars(rootPath, nullptr);
    std::vector<FileInfo> allFiles;
    
    // Scan all files
    scanDirectory(path, allFiles, true);
    
    // Filter suspicious files
    std::vector<FileInfo> suspiciousFiles;
    for (const auto& file : allFiles) {
        if (file.isSuspicious) {
            suspiciousFiles.push_back(file);
        }
    }
    
    // Build JSON result
    std::stringstream result;
    result << "[";
    for (size_t i = 0; i < suspiciousFiles.size(); ++i) {
        if (i > 0) result << ",";
        result << "{";
        result << "\"name\":\"" << suspiciousFiles[i].name << "\",";
        result << "\"path\":\"" << suspiciousFiles[i].path << "\",";
        result << "\"size\":" << suspiciousFiles[i].size << ",";
        result << "\"formattedSize\":\"" << formatFileSize(suspiciousFiles[i].size) << "\",";
        result << "\"isHidden\":" << (suspiciousFiles[i].isHidden ? "true" : "false") << ",";
        result << "\"fileType\":\"" << suspiciousFiles[i].fileType << "\",";
        result << "\"riskLevel\":\"high\"";
        result << "}";
    }
    result << "]";
    
    env->ReleaseStringUTFChars(rootPath, path);
    return env->NewStringUTF(result.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getHiddenFiles(
        JNIEnv* env,
        jobject /* this */,
        jstring rootPath) {
    
    const char* path = env->GetStringUTFChars(rootPath, nullptr);
    std::vector<FileInfo> allFiles;
    
    // Scan all files including hidden ones
    scanDirectory(path, allFiles, true);
    
    // Filter hidden files
    std::vector<FileInfo> hiddenFiles;
    for (const auto& file : allFiles) {
        if (file.isHidden) {
            hiddenFiles.push_back(file);
        }
    }
    
    // Build JSON result
    std::stringstream result;
    result << "[";
    for (size_t i = 0; i < hiddenFiles.size(); ++i) {
        if (i > 0) result << ",";
        result << "{";
        result << "\"name\":\"" << hiddenFiles[i].name << "\",";
        result << "\"path\":\"" << hiddenFiles[i].path << "\",";
        result << "\"size\":" << hiddenFiles[i].size << ",";
        result << "\"formattedSize\":\"" << formatFileSize(hiddenFiles[i].size) << "\",";
        result << "\"isSuspicious\":" << (hiddenFiles[i].isSuspicious ? "true" : "false") << ",";
        result << "\"fileType\":\"" << hiddenFiles[i].fileType << "\"";
        result << "}";
    }
    result << "]";
    
    env->ReleaseStringUTFChars(rootPath, path);
    return env->NewStringUTF(result.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_scanFileWithAntivirus(
        JNIEnv* env,
        jobject /* this */,
        jstring filePath) {
    
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    
    // Simple file scanning logic (in real implementation, this would connect to antivirus engine)
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        env->ReleaseStringUTFChars(filePath, path);
        return env->NewStringUTF("{\"status\":\"error\",\"message\":\"File not found\"}");
    }
    
    // Read first 1024 bytes for basic analysis
    char buffer[1024];
    file.read(buffer, sizeof(buffer));
    std::streamsize bytesRead = file.gcount();
    file.close();
    
    // Basic heuristic analysis
    bool suspicious = false;
    std::string reason = "";
    
    // Check for executable signatures
    if (bytesRead >= 2) {
        if (buffer[0] == 'M' && buffer[1] == 'Z') {
            suspicious = true;
            reason = "PE executable detected";
        }
    }
    
    // Check for APK signature
    if (bytesRead >= 4) {
        if (buffer[0] == 'P' && buffer[1] == 'K' && buffer[2] == 0x03 && buffer[3] == 0x04) {
            suspicious = true;
            reason = "APK file detected";
        }
    }
    
    // Check for JAR signature
    if (bytesRead >= 4) {
        if (buffer[0] == 'P' && buffer[1] == 'K' && buffer[2] == 0x03 && buffer[3] == 0x04) {
            suspicious = true;
            reason = "JAR file detected";
        }
    }
    
    // Build result
    std::stringstream result;
    result << "{";
    result << "\"status\":\"scanned\",";
    result << "\"suspicious\":" << (suspicious ? "true" : "false") << ",";
    result << "\"reason\":\"" << (suspicious ? reason : "No threats detected") << "\",";
    result << "\"bytesAnalyzed\":" << bytesRead;
    result << "}";
    
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(result.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getFileStatistics(
        JNIEnv* env,
        jobject /* this */,
        jstring rootPath) {
    
    const char* path = env->GetStringUTFChars(rootPath, nullptr);
    std::vector<FileInfo> allFiles;
    
    // Scan all files
    scanDirectory(path, allFiles, true);
    
    // Calculate statistics
    long long totalSize = 0;
    int totalFiles = allFiles.size();
    int hiddenFiles = 0;
    int suspiciousFiles = 0;
    int largeFiles = 0; // > 100MB
    
    for (const auto& file : allFiles) {
        totalSize += file.size;
        if (file.isHidden) hiddenFiles++;
        if (file.isSuspicious) suspiciousFiles++;
        if (file.size > 100 * 1024 * 1024) largeFiles++; // 100MB
    }
    
    // Build JSON result
    std::stringstream result;
    result << "{";
    result << "\"totalFiles\":" << totalFiles << ",";
    result << "\"totalSize\":" << totalSize << ",";
    result << "\"formattedTotalSize\":\"" << formatFileSize(totalSize) << "\",";
    result << "\"hiddenFiles\":" << hiddenFiles << ",";
    result << "\"suspiciousFiles\":" << suspiciousFiles << ",";
    result << "\"largeFiles\":" << largeFiles;
    result << "}";
    
    env->ReleaseStringUTFChars(rootPath, path);
    return env->NewStringUTF(result.str().c_str());
}
