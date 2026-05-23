# Add project specific ProGuard rules here.

# Keep Retrofit and OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }

# Keep Open-Meteo response models
-keep class com.uvtracker.app.data.** { *; }
-keep class com.uvtracker.app.model.** { *; }

# Keep Kotlin Parcelize
-keep class kotlin.Metadata { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Android Location
-keep class com.google.android.gms.location.** { *; }

# Gson rules
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
