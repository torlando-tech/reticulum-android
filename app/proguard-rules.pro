# Chaquopy - keep Python bridge classes
-keep class com.chaquo.python.** { *; }
-keepclassmembers class * {
    @com.chaquo.python.PyMethod *;
}

# Keep bridge classes called from Python
-keep class io.reticulum.transport.bridges.** { *; }
