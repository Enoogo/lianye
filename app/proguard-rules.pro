# Lianye ProGuard & R8 Optimization Rules

# 1. Keep Accessibility Service & Foreground Capture Service
-keep public class org.lianye.service.LianyeAccessibilityService { *; }
-keep public class org.lianye.service.capture.LianyeMediaProjectionService { *; }

# 2. Keep Domain and Storage Models
-keep class org.lianye.domain.model.** { *; }
-keep class org.lianye.engine.model.** { *; }

# 3. Kotlin Coroutines Internal Mechanics
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
# 4. Keep Floating Overlay UI & Lifecycles
-keep class org.lianye.ui.floating.** { *; }
