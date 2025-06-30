# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# com.kazumaproject.core.domain.extensions.hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.** { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem { *; }
