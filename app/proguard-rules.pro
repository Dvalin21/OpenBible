# OpenBible ProGuard Rules (GPL-3.0)
# Compose and Room handle their own keep rules via consumer proguard files.
# No additional rules needed for core functionality.

# Data classes used by Room must not be obfuscated
-keep class com.openbible.data.db.entity.** { *; }

# Enum classes used by Room
-keepclassmembers enum com.openbible.data.model.** { *; }
-keepclassmembers enum com.openbible.data.db.converter.** { *; }

# Keep serialized pen stroke data classes
-keep class com.openbible.ui.notes.** { *; }
