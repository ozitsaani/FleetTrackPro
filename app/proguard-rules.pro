# 1. Room / entitas
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class com.industri.fleettrack.data.local.entity.** { *; }

# 2. DTO Retrofit & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.industri.fleettrack.data.remote.api.** { *; }
-keepclassmembers class com.industri.fleettrack.data.remote.api.** { *; }

# 3. Timber
-dontwarn timber.log.**
-keep class timber.log.** { *; }

# 4. Fallback anti NoSuchMethod pada data class
-keep class com.industri.fleettrack.data.** { *; }
