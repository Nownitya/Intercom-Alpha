# ProGuard & R8 Optimization Rules — Intercom-Alpha Production v2.0.0

# 1. General Optimization & Stack Trace Preservation
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 2. Kotlinx Serialization Preservation (Critical for MeshPacket, NavKey, GPS, Text, Hazard, Radar)
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
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-dontwarn kotlinx.serialization.**

# 3. Koin Dependency Injection
-keep class * extends org.koin.core.module.Module { *; }
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# 4. Android Jetpack Compose & Navigation 3
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.navigation3.** { *; }

# 5. Ktor Networking & WebSocket Protocols
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# 6. Intercom-Alpha v2.0 Platform Services, Bridges, BLE Mesh & Subsystems
-keep class org.nowni.intercom_alpha.MainActivity { *; }
-keep class org.nowni.intercom_alpha.IntercomAlphaApplication { *; }
-keep class org.nowni.intercom_alpha.service.IntercomForegroundService { *; }
-keep class org.nowni.intercom_alpha.power.PowerManagerHelper { *; }
-keep class org.nowni.intercom_alpha.headset.HeadsetManager { *; }
-keep class org.nowni.intercom_alpha.bridge.** { *; }
-keep class org.nowni.intercom_alpha.mesh.** { *; }
-keep class org.nowni.intercom_alpha.audio.** { *; }
-keep class org.nowni.intercom_alpha.channel.** { *; }
-keep class org.nowni.intercom_alpha.radar.** { *; }
-keep class org.nowni.intercom_alpha.gps.** { *; }
-keep class org.nowni.intercom_alpha.text.** { *; }
-keep class org.nowni.intercom_alpha.priority.** { *; }
-keep class org.nowni.intercom_alpha.blackbox.** { *; }
-keep class org.nowni.intercom_alpha.bitrate.** { *; }
-keep class org.nowni.intercom_alpha.spatial.** { *; }
-keep class org.nowni.intercom_alpha.cellular.** { *; }
-keep class org.nowni.intercom_alpha.crypto.** { *; }
-keep class org.nowni.intercom_alpha.protocol.** { *; }
-keep class org.nowni.intercom_alpha.model.** { *; }
-keep class org.nowni.intercom_alpha.di.** { *; }
-keep class org.nowni.intercom_alpha.feature.** { *; }

# 7. Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**