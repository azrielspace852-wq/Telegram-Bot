# Keep llama.cpp / native classes if you add them later
-keep class com.offlineai.app.inference.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Keep all app model / data classes
-keep class com.offlineai.app.data.** { *; }
-keep class com.offlineai.app.ui.** { *; }
-keep class com.offlineai.app.util.** { *; }
-keep class com.offlineai.app.server.** { *; }

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class io.ktor.server.** { *; }
-keep class io.ktor.utils.io.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
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

# PDFBox-Android
-dontwarn com.gemalto.jp2.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# Apache Commons Compress
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }

# Coil
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }

# Compose / Material – avoid R8 stripping
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# General Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
