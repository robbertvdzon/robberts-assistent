# flutter_local_notifications gebruikt Gson om geplande notificaties te (de)serialiseren.
# Zonder deze regels strippt R8 de generieke type-info → "Missing type parameter" bij
# pendingNotificationRequests/zonedSchedule. (Minification staat nu uit, maar deze regels zijn
# een vangnet mocht 'ie ooit weer aangezet worden.)
-keep class com.dexterous.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson: behoud generieke signatures en de TypeToken-subklassen.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
