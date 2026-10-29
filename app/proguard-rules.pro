# kotlinx.serialization: keep the generated serializers of the API DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ar.pharmatrace.mobile.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ar.pharmatrace.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
