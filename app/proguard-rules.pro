# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep data classes used in navigation
-keep class com.resqmesh.app.navigation.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, Signature, Exception
