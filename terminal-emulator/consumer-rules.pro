# Native methods are looked up by name from libterminalpty.so.
-keepclasseswithmembernames class com.termux.terminal.JNI {
    native <methods>;
}
