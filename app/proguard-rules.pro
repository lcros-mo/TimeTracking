# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep SLF4J classes and interfaces
-keep class org.slf4j.** { *; }
-keep interface org.slf4j.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# iText rules (necesarias si usas iText para PDF)
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# OkHttp rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keepclassmembernames class kotlin.coroutines.** { volatile <fields>; }

# Room (Base de datos)
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }

# Retrofit y Gson (para evitar eliminación de clases en respuestas API)
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn com.google.gson.**

# Mantener clases de modelos JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Evitar optimización agresiva de ProGuard en respuestas API
-keepattributes Exceptions

# Reglas Retrofit específicas para arreglar el error ClassCastException
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Preservar la firma genérica de Call y Response (R8 en modo completo elimina firmas de elementos no preservados)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Preservar la interfaz AuthApi y modelos relacionados
-keep class com.timetracking.app.core.network.AuthApi { *; }
-keep class com.timetracking.app.core.network.AuthResponse { *; }
-keep class com.timetracking.app.core.network.User { *; }

# Proteger BuildConfig para acceso a API_KEY
-keep class com.timetracking.app.BuildConfig { *; }
