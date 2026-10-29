# JSch instantiates its algorithm implementations reflectively from class names in JSch.config.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# BouncyCastle is only used through direct references from JSch's com.jcraft.jsch.bc classes.
-dontwarn org.bouncycastle.**

# Optional integrations of JSch that do not exist on Android.
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Keep BouncyCastle class names (classes are still shrunk); its error messages and any
# name-based lookups stay intact.
-keepnames class org.bouncycastle.**
