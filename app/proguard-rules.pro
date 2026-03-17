# Add project specific ProGuard rules here.
-keep class com.voxtype.keyboard.** { *; }
-keepclassmembers class * extends android.inputmethodservice.InputMethodService { *; }
