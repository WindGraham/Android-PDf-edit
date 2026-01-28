# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep PDF related classes
-keep class com.pdfcore.** { *; }
-keep class com.pdfrender.** { *; }
-keep class com.pdfeditor.** { *; }

# Keep data classes for serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
