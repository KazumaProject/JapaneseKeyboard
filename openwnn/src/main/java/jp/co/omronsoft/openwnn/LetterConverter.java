/*
 * Copyright (C) 2008-2012  OMRON SOFTWARE Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.omronsoft.openwnn;

import android.content.SharedPreferences;

/**
 * The interface of pre-converter for input string used by OpenWnn.
 * <br>
 * This is a simple converter for Romaji-to-Kana input, Hangul input, etc.
 *
 * @author Copyright (C) 2009 OMRON SOFTWARE CO., LTD.  All Rights Reserved.
 */
public interface LetterConverter {
    /**
     * Convert the layer #0 text(pressed key sequence) to layer #1 text(pre converted string).
     * <br>
     * This conversion is used for converting some key input to a character.
     * For example, Latin capital letter conversion (ex: "'"+"a" to "&#x00E1;")",
     * Romaji-to-Kana conversion in Japanese,
     * Hangul conversion in Korean.
     *
     * @param text      The text data includes input sequence(layer #0) and output area(layer #1)
     * @return      {@code true} if conversion is completed; {@code false} if not.
     */
    public boolean convert(ComposingText text);

    /**
     * Reflect the preferences in the letter converter.
     *
     * @param pref      The preferences
     */
    public void setPreferences(SharedPreferences pref);
}
