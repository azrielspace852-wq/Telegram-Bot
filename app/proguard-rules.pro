# Keep llama.cpp / native classes if you add them later
-keep class com.offlineai.app.inference.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.offlineai.app.**$$serializer { *; }
-keepclassmembers class com.offlineai.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.offlineai.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
