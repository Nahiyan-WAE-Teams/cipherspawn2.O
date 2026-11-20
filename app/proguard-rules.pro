# Add project specific ProGuard rules here.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId *;
    @com.google.firebase.firestore.ServerTimestamp *;
    @com.google.firebase.firestore.PropertyName *;
}
