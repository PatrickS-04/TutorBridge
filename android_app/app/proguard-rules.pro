# Gson fills these classes by reflection using their field names, and they have no
# @SerializedName, so R8 must not rename or strip them or the release build loads
# empty data. data.model holds the API models; data.local holds the outbox payloads
# and the Room entities that are stored as JSON.
-keep class com.tutorbridge.app.data.model.** { *; }
-keep class com.tutorbridge.app.data.local.** { *; }

# TypeToken and Retrofit read generic types and annotations at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
