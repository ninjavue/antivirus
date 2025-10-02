# Log
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Enum’s
-keepclassmembers class ** {
    **[] $VALUES;
    public static ** valueOf(java.lang.String);
}

# Parcelable (Android classes)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

######################
# AndroidX / Support
######################
-keep class androidx.** { *; }
-dontwarn androidx.**

###############
# Retrofit / OkHttp
###############
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

##############
# Gson / Moshi
##############
# Gson uchun model klasslar saqlanishi kerak:
-keep class uz.csec.zirhanalizator.model.** { *; }

# Moshi ishlatsa (faqat Java):
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

#########
# Glide
#########
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
# Asosiy Application yoki refleksiya orqali chaqiriladigan klasslarni saqlang
-keep class uz.csec.zirhanalizator.MainActivity { *; }

# API interfeyslarini saqlash
-keep public class com.example.app.api.** { public *; }


# DeviceUtils klassini va metodlarini obfuskatsiya qilinmasin
-keep class uz.csec.zirhanalizator.NativeLib { *; }

