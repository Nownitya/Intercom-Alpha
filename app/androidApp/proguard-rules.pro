# ProGuard & R8 Optimization Rules — Intercom-Alpha Production v1.0.0

# 1. General Optimization & Stack Trace Preservation
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 2. Kotlinx Serialization Preservation (Critical for MeshPacket & NavKey JSON)
-keepattributes *Annotation*
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    public static final *** INSTANCE;
}
-keepnames class kotlinx.serialization.PolymorphicSerializer
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }

# 3. Koin Dependency Injection
-keep class * extends org.koin.core.module.Module { *; }
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# 4. Android Jetpack Compose & Navigation 3
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keep class androidx.compose.runtime.** { *; }

# 5. Core BLE Mesh & Foreground Services
-keep class org.nowni.intercom_alpha.service.IntercomForegroundService { *; }
-keep class org.nowni.intercom_alpha.power.PowerManagerHelper { *; }
-keep class org.nowni.intercom_alpha.headset.HeadsetManager { *; }
-keep class org.nowni.intercom_alpha.mesh.** { *; }
-keep class org.nowni.intercom_alpha.audio.** { *; }

# 6. Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**