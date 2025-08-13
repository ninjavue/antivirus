//
// Created by Umrzoq on 7/10/2025.
//

#include <jni.h>
#include <string>

// Helper: get application context from activity
jobject getContextFromActivity(JNIEnv* env, jobject activity) {
    jclass activityClass = env->GetObjectClass(activity);
    jmethodID getAppContext = env->GetMethodID(activityClass, "getApplicationContext", "()Landroid/content/Context;");
    return env->CallObjectMethod(activity, getAppContext);
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_SecurityActivity_getDangerousApps(JNIEnv *env, jobject thiz) {
    jobject context = getContextFromActivity(env, thiz);
    jclass cls = env->FindClass("uz/csec/antivirus/SecurityActivity");
    jmethodID mid = env->GetStaticMethodID(cls, "getDangerousAppsList", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(cls, mid, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_SecurityActivity_getPermissionsControl(JNIEnv *env, jobject thiz) {
    jobject context = getContextFromActivity(env, thiz);
    jclass cls = env->FindClass("uz/csec/antivirus/SecurityActivity");
    jmethodID mid = env->GetStaticMethodID(cls, "getAppsWithSensitivePermissions", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(cls, mid, context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_SecurityActivity_getRootStatus(JNIEnv *env, jobject /* this */) {
    jclass cls = env->FindClass("uz/csec/antivirus/SecurityActivity");
    jmethodID mid = env->GetStaticMethodID(cls, "getRootStatusJava", "()Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(cls, mid);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_uz_csec_antivirus_SecurityActivity_getWifiSecurity(JNIEnv *env, jobject thiz) {
    jobject context = getContextFromActivity(env, thiz);
    jclass cls = env->FindClass("uz/csec/antivirus/SecurityActivity");
    jmethodID mid = env->GetStaticMethodID(cls, "getWifiSecurityJava", "(Landroid/content/Context;)Ljava/lang/String;");
    jstring result = (jstring)env->CallStaticObjectMethod(cls, mid, context);
    return result;
}
