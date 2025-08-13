//
// Created by Umrzoq on 7/8/2025.
//
#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <unistd.h>
#include <ctime>
#include <android/asset_manager_jni.h>
#include "checker.h"

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getRunningApps(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    jclass nativeLibClass = env->FindClass("uz/csec/antivirus/NativeLib");
    jmethodID method = env->GetStaticMethodID(nativeLibClass, "getRunningAppsJava", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(nativeLibClass, method, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getUnusedApps(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    jclass nativeLibClass = env->FindClass("uz/csec/antivirus/NativeLib");
    jmethodID method = env->GetStaticMethodID(nativeLibClass, "getUnusedAppsJava", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(nativeLibClass, method, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getAppCpuUsage(
        JNIEnv* env,
        jobject /* this */) {
    pid_t pid = getpid();
    std::stringstream path;
    path << "/proc/" << pid << "/stat";
    std::ifstream statFile(path.str());
    std::string statLine;
    if (std::getline(statFile, statLine)) {
        std::istringstream iss(statLine);
        std::string token;
        int i = 0;
        long utime = 0, stime = 0;
        while (iss >> token) {
            i++;
            if (i == 14) utime = std::stol(token);
            if (i == 15) { stime = std::stol(token); break; }
        }
        std::stringstream result;
        result << "App CPU: " << (utime + stime);
        return env->NewStringUTF(result.str().c_str());
    }
    return env->NewStringUTF("CPU info not found");
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getDeviceUptime(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    jclass nativeLibClass = env->FindClass("uz/csec/antivirus/NativeLib");
    jmethodID method = env->GetStaticMethodID(nativeLibClass, "getDeviceUptimeJava", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(nativeLibClass, method, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_getAppBatteryUsage(
        JNIEnv* env,
        jobject /* this */,
        jobject context) {
    jclass nativeLibClass = env->FindClass("uz/csec/antivirus/NativeLib");
    jmethodID method = env->GetStaticMethodID(nativeLibClass, "getAppBatteryUsageJava", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(nativeLibClass, method, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_NativeLib_quickScanFiles(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray filePaths,
        jobject assetManager) {
    jsize len = env->GetArrayLength(filePaths);
    std::vector<std::string> files;
    for (jsize i = 0; i < len; ++i) {
        jstring jpath = (jstring)env->GetObjectArrayElement(filePaths, i);
        const char* cpath = env->GetStringUTFChars(jpath, nullptr);
        files.emplace_back(cpath);
        env->ReleaseStringUTFChars(jpath, cpath);
        env->DeleteLocalRef(jpath);
    }
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    auto infected = scanForViruses(files, mgr);
    // Build JSON result
    std::stringstream result;
    result << "[";
    for (size_t i = 0; i < infected.size(); ++i) {
        if (i > 0) result << ",";
        result << "{\"path\":\"" << infected[i].first << "\",\"hash\":\"" << infected[i].second << "\"}";
    }
    result << "]";
    return env->NewStringUTF(result.str().c_str());
}