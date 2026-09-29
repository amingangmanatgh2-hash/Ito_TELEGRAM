# TDLib is bound through reflection -> R8 must not touch it.
-keep class org.drinkless.** { *; }
-dontwarn org.drinkless.**

# ML Kit / CameraX
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep @androidx.annotation.Keep class * { *; }

-dontwarn javax.lang.model.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
