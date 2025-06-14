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

-keepnames class com.kazumaproject.markdownhelperkeyboard.setting_activity.database.** { *; }
-dontwarn com.kazumaproject.core.data.clicked_symbol.SymbolMode
-dontwarn com.kazumaproject.core.domain.extensions.MaterilColorKt
-dontwarn com.kazumaproject.core.domain.listener.FlickListener
-dontwarn com.kazumaproject.core.domain.listener.LongPressListener
-dontwarn com.kazumaproject.core.domain.listener.QWERTYKeyListener
-dontwarn com.kazumaproject.core.domain.physical_shift_key.PhysicalShiftKeyCodeMap
-dontwarn com.kazumaproject.core.domain.state.GestureType$Down
-dontwarn com.kazumaproject.core.domain.state.GestureType$FlickBottom
-dontwarn com.kazumaproject.core.domain.state.GestureType$FlickLeft
-dontwarn com.kazumaproject.core.domain.state.GestureType$FlickRight
-dontwarn com.kazumaproject.core.domain.state.GestureType$FlickTop
-dontwarn com.kazumaproject.core.domain.state.GestureType$Null
-dontwarn com.kazumaproject.core.domain.state.GestureType$Tap
-dontwarn com.kazumaproject.core.domain.state.GestureType
-dontwarn com.kazumaproject.core.domain.state.InputMode$ModeEnglish
-dontwarn com.kazumaproject.core.domain.state.InputMode$ModeJapanese
-dontwarn com.kazumaproject.core.domain.state.InputMode$ModeNumber
-dontwarn com.kazumaproject.core.domain.state.InputMode
-dontwarn com.kazumaproject.core.domain.state.TenKeyQWERTYMode$Default
-dontwarn com.kazumaproject.core.domain.state.TenKeyQWERTYMode$TenKeyQWERTY
-dontwarn com.kazumaproject.core.domain.state.TenKeyQWERTYMode
-dontwarn com.kazumaproject.custom_keyboard.data.KeyboardInputMode
-dontwarn com.kazumaproject.custom_keyboard.data.KeyboardLayout
-dontwarn com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
-dontwarn com.kazumaproject.custom_keyboard.view.FlickKeyboardView$OnKeyboardActionListener
-dontwarn com.kazumaproject.custom_keyboard.view.FlickKeyboardView
-dontwarn com.kazumaproject.data.clicked_symbol.ClickedSymbol
-dontwarn com.kazumaproject.data.emoji.Emoji
-dontwarn com.kazumaproject.data.emoji.EmojiCategory
-dontwarn com.kazumaproject.domain.EmojiKt
-dontwarn com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
-dontwarn com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
-dontwarn com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
-dontwarn com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
-dontwarn com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
-dontwarn com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
-dontwarn com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
-dontwarn com.kazumaproject.tabletkey.TabletKeyboardView
-dontwarn com.kazumaproject.tenkey.TenKey
-dontwarn com.kazumaproject.tenkey.extensions.CharExtensionKt
