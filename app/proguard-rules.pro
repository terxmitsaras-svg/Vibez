# Vortext Keyboard ProGuard Rules

# Keep IME service
-keep class com.vibez.keyboard.ime.** { *; }

# Keep all model classes
-keep class com.vibez.keyboard.language.** { *; }
-keep class com.vibez.keyboard.theme.** { *; }
-keep class com.vibez.keyboard.subscription.** { *; }
-keep class com.vibez.keyboard.voice.** { *; }
-keep class com.vibez.keyboard.utils.** { *; }

# Billing library
-keep class com.android.billingclient.** { *; }
-keepattributes *Annotation*

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Parcelable/Serializable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.inputmethodservice.InputMethodService
-keep public class * extends android.content.BroadcastReceiver

# EmojiCompat
-keep class androidx.emoji2.** { *; }
