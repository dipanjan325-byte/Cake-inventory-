# Add project specific ProGuard rules here.
# Minify is disabled in the debug and release build types configured in
# build.gradle.kts, so this file is currently unused. If you later turn on
# isMinifyEnabled = true for release, keep Room's generated code:
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
