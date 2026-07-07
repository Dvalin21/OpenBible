# OpenBible ProGuard Rules (GPL-3.0)

# ── Compose Navigation ───────────────────────────────────────────────
# R8 full mode cannot trace through Compose-transformed bytecode into
# navigation-compose lambdas. Keep the entire navigation library.
# Do NOT allow shrinking or obfuscation — navigation uses runtime lookups.
-keep class androidx.navigation.** { *; }

# ── All UI Screens ───────────────────────────────────────────────────
# Screen composables are only reachable through composable() lambdas in
# NavHost. R8 can't trace through these, so keep them explicitly.
# Obfuscation allowed (no reflection), but members must survive.
-keep,allowobfuscation class com.openbible.ui.** { *; }
-keep,allowobfuscation class com.openbible.navigation.** { *; }

# ── Hilt/Dagger ─────────────────────────────────────────────────────
# Hilt uses reflection for component creation. Keep exact names.
-keep class com.openbible.Hilt_** { *; }
-keep class com.openbible.DaggerOpenBibleApp_** { *; }
-keep class com.openbible.OpenBibleApp_HiltComponents { *; }
-keep class com.openbible.OpenBibleApp_HiltComponents$* { *; }
-keep class com.openbible.OpenBibleApp_MembersInjector { *; }
-keep class com.openbible.*_MembersInjector { *; }
-keep class com.openbible.*_GeneratedInjector { *; }
-keep class com.openbible.*_ComponentTreeDeps { *; }
-keep class com.openbible.di.** { *; }
-keepclassmembers class com.openbible.OpenBibleApp {
    @javax.inject.Inject *;
}
-keepclassmembers class com.openbible.MainActivity {
    @javax.inject.Inject *;
}

# ── Room Entities ────────────────────────────────────────────────────
-keep class com.openbible.data.db.entity.** { *; }
-keepclassmembers enum com.openbible.data.model.** { *; }
-keepclassmembers enum com.openbible.data.db.converter.** { *; }

# ── osmdroid Map ─────────────────────────────────────────────────────
# osmdroid uses reflection for tile source providers and HTTP connections.
# R8 cannot trace through these initialization paths.
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
