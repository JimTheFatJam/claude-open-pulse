# kotlinx.serialization ships its own consumer rules; keep our DTOs' serializers explicitly too.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.openpulse.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.openpulse.core.**$$serializer { *; }

# Room entities / DAOs are generated and referenced reflectively by name.
-keep class app.openpulse.data.db.** { *; }
