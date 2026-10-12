# GeckoView calls Play Services for passkeys. That library stays out of the
# app, so R8 must ignore the references instead of failing the release build.
-dontwarn com.google.android.gms.fido.**
-dontwarn com.google.android.gms.tasks.**

# Nullness annotations compiled into GeckoView's media code. They are not
# present at runtime.
-dontwarn org.checkerframework.checker.nullness.qual.**
