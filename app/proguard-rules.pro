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
-keep class com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefEntry { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.NgramRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.TwoNodeRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.ThreeNodeRuleBackup { *; }
-keep class com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui.NodeFeatureInput { *; }

# Keep Gson generic type metadata and annotations used by backup import/export parsing.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Legacy data compatibility: Pair fields may still appear in old JSON payloads.
-keep class kotlin.Pair { *; }

# Gemma LiteRT-LM is loaded entirely via reflection from GemmaTranslationManager.
# Keep class names, members, and singleton fields such as NativeLibraryLoader.INSTANCE
# so release builds behave the same as debug builds.
-keep class com.google.ai.edge.litertlm.** { *; }

# LiteRT-LM references kotlin-reflect helper APIs from code paths we do not use.
-dontwarn kotlin.reflect.full.KClasses
