# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Keep WorkManager workers (instantiated by name)
-keep class com.tim.articlequotes.work.** { *; }

# Anthropic Java SDK (devotionals). It deserializes API responses with Jackson by reflection,
# so keep its model classes and Jackson's annotations; optional telemetry deps are absent.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn com.google.auto.value.**
-dontwarn io.opentelemetry.**
-dontwarn java.beans.**
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn kotlin.reflect.jvm.internal.**
# Desktop-JVM reflection types the SDK's schema generator references but never uses on Android.
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.Annotated*
-dontwarn com.github.victools.**
