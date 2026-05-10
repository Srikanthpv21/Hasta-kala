# ProGuard rules for Hasta-Kala Shop
# Fix #14: Comprehensive rules to ensure minified release builds work correctly

# ─── Room Database ──────────────────────────────────────────────────────────────
# Keep all Room entity and DAO classes so Room can access them at runtime
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao class * { *; }
-keep class androidx.room.** { *; }

# ─── Data Classes (Kotlin) ──────────────────────────────────────────────────────
# Kotlin data classes used with Room must not be obfuscated
-keepclassmembers class com.example.hastakalashop.data.** { *; }

# ─── ViewModel ──────────────────────────────────────────────────────────────────
-keep class androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ─── MPAndroidChart ─────────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ─── Facebook Shimmer ───────────────────────────────────────────────────────────
-keep class com.facebook.shimmer.** { *; }

# ─── Android Core ───────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ─── Kotlin Coroutines ──────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
