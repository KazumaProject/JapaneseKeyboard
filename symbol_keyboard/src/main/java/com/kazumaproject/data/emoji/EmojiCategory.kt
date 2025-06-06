package com.kazumaproject.data.emoji

/**
 * Unicode の絵文字群を大まかに以下のように分類します。
 * 実際の Unicode 仕様ではさらに細かく分かれていますが、
 * アプリでよく使われる大分類として参考にしてください。
 */
enum class EmojiCategory {
    EMOTICONS,          // 😀〜🙏 のような「顔文字」系 (U+1F600–1F64F)
    GESTURES,           // 👍👎✊✌️など「手や指・ジェスチャー」系 (U+1F590–1F5FF、U+1F90C–1F91F、U+2700–27BF など)
    PEOPLE_BODY,        // 👦👧👮🧕 など「人・体のパーツ」系 (U+1F466–1F487、U+1F9D0–1F9FF など)
    ANIMALS_NATURE,     // 🐶🐱🌲🌻 など「動物・自然」系 (U+1F300–1F5FF の一部、U+1F900–1F9FF の動植物範囲)
    FOOD_DRINK,         // 🍎🍔☕️ など「食べ物・飲み物」系 (U+1F34F–1F37F、U+1F950–1F96F など)
    TRAVEL_PLACES,      // 🚗✈️🏝 など「乗り物・場所」系 (U+1F680–1F6FF、U+1F3E0–1F3FF など)
    ACTIVITIES,         // ⚽️🎮🎉 など「アクティビティ・イベント」系 (U+26F0–26FF、U+1F3A0–1F3FF など)
    OBJECTS,            // 💡📱🔑 など「モノ一般」系 (U+1F4A0–1F4FF、U+1F500–1F5FF の一部)
    SYMBOLS,            // ♻️✅🔣 など「記号・シンボル」系 (U+2600–26FF、U+1F300–1F5FF の一部、U+1F7E0–1F7EB など)
    FLAGS,              // 🇯🇵🇺🇸 など「旗」系 (U+1F1E6–1F1FF)
    UNKNOWN             // どの範囲にも当てはまらない場合
}
