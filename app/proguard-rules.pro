# kotlinx.serialization keeps generated serializers reachable via the
# companion; R8 cannot see those links without these rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.cartoonstudio.**$$serializer { *; }
-keepclassmembers class com.cartoonstudio.** {
    *** Companion;
}
-keepclasseswithmembers class com.cartoonstudio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
