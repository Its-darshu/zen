# Zen Mode ProGuard/R8 rules.
# Room, Hilt and Compose ship their own consumer rules; only project-specific
# rules belong here.

# Keep the AccessibilityService and other manifest-declared components: they are
# instantiated reflectively by the framework.
-keep class com.zenmode.app.service.** { *; }

# Keep enum names used as Room TypeConverter values.
-keepclassmembers enum com.zenmode.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
