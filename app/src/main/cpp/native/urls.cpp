//
// Created by jsmx on 23/09/2025.
//
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_zirhanalizator_NativeLib_FirstMethod(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL25pbmphdnVlL21lc3NhZ2UvcmVmcy9oZWFkcy9tYWluL3ZpcnVzZXMudHh0";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_zirhanalizator_NativeLib_SecondMethod(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "MGVjM2Y0MTcxOTU5MGVkZWZhNmM1YjVkNmMyZDAyYThiODE0NTQyMGE0YmJkNmEyYzE0YjcxYWNiZTBmOTE2Yg==";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_zirhanalizator_NativeLib_LastMethod(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "aHR0cHM6Ly93d3cudmlydXN0b3RhbC5jb20vYXBpL3YzL2ZpbGVzLw==";
    return env->NewStringUTF(hello.c_str());
}