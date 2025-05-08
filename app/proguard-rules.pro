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

# ------------------- LOGGING -------------------
# Keep SLF4J classes and interfaces
-keep class org.slf4j.** { *; }
-keep interface org.slf4j.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ------------------- PDF GENERATION -------------------
# iText rules para generación de PDF
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ------------------- NETWORKING -------------------
# OkHttp & Okio
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit y Gson
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn com.google.gson.**

# Evitar optimización agresiva de ProGuard en respuestas API
-keepattributes Exceptions

# Reglas Retrofit específicas para arreglar el error ClassCastException
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Preservar la firma genérica de Call y Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Preservar la interfaz AuthApi y modelos relacionados
-keep class com.timetracking.app.core.network.AuthApi { *; }
-keep class com.timetracking.app.core.network.AuthResponse { *; }
-keep class com.timetracking.app.core.network.User { *; }

# ------------------- KOTLIN & COROUTINES -------------------
# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keepclassmembernames class kotlin.coroutines.** { volatile <fields>; }

# ------------------- ROOM DATABASE -------------------
# Room Database
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ------------------- ARCHITECTURE COMPONENTS -------------------
# LiveData y ViewModel
-keep class androidx.lifecycle.** { *; }

# ------------------- JSON SERIALIZATION -------------------
# Mantener clases de modelos JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ------------------- FIREBASE & GOOGLE -------------------
# Firebase Auth completo
-keep class com.google.firebase.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keepclassmembers class com.google.firebase.auth.** { *; }

# Mantener las clases de la biblioteca de autenticación de Google
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.internal.** { *; }

# Para Google Sign-In
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ------------------- ANDROID SPECIFICS -------------------
# Para el manejo de credenciales de Android
-keep class androidx.credentials.** { *; }
-keep interface androidx.credentials.** { *; }

# Proteger BuildConfig para acceso a API_KEY
-keep class com.timetracking.app.BuildConfig { *; }

# Mantener Parcelables
-keepnames class * implements android.os.Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Mantener Serializables
-keepnames class * implements java.io.Serializable

# ------------------- SECURITY -------------------
# Reglas para OAuth/OpenID Connect
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# ------------------- PLAY STORE COMPATIBILITY -------------------
# Para evitar advertencias de seguridad en la Play Store
-dontwarn android.security.NetworkSecurityPolicy
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn java.awt.**