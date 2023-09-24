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
 * The interface of the text converter accessed from OpenWnn.
 * <br>
 * The realization class of this interface should be an singleton class.
 *
 * @author Copyright (C) 2009, OMRON SOFTWARE CO., LTD.  All Rights Reserved.
 */
public interface WnnEngine {
    /*
     * DEFINITION OF CONSTANTS
     */
    /** The identifier of the learning dictionary */
    public static final int DICTIONARY_TYPE_LEARN = 1;
    /** The identifier of the user dictionary */
    public static final int DICTIONARY_TYPE_USER  = 2;

    /*
     * DEFINITION OF METHODS
     */
    /**
     * Initialize parameters.
     */
    public void init();

    /**
     * Close the converter.
     * <br>
     *
     * OpenWnn calls this method when it is destroyed.
     */
    public void close();

    /**
     * Predict words/phrases.
     * <br>
     * @param text      The input string
     * @param minLen    The minimum length of a word to predict (0  : no limit)
     * @param maxLen    The maximum length of a word to predict (-1 : no limit)
     * @return          Plus value if there are candidates; 0 if there is no candidate; minus value if a error occurs.
     */
    public int predict(ComposingText text, int minLen, int maxLen);

    /**
     * Convert a string.
     * <br>
     * This method is used to consecutive/single clause convert in
     * Japanese, Pinyin to Kanji convert in Chinese, Hangul to Hanja
     * convert in Korean, etc.
     *
     * The result of conversion is set into the layer 2 in the {@link ComposingText}.
     * To get other candidates of each clause, call {@link #makeCandidateListOf(int)}.
     *
     * @param text      The input string
     * @return      Plus value if there are candidates; 0 if there is no candidate; minus value if a error occurs.
     */
    public int convert(ComposingText text);

    /**
     * Search words from the dictionaries.
     * <br>
     * @param key       The search key (stroke)
     * @return      Plus value if there are candidates; 0 if there is no candidate; minus value if a error occurs.
     */
    public int searchWords(String key);

    /**
     * Search words from the dictionaries.
     * <br>
     * @param word      A word to search
     * @return          Plus value if there are candidates; 0 if there is no candidate; minus value if a error occurs.
     */
    public int searchWords(WnnWord word);

    /**
     * Get a candidate.
     * <br>
     * After {@link #predict(ComposingText, int, int)} or {@link #makeCandidateListOf(int)} or
     * {@code searchWords()}, call this method to get the
     * results.  This method will return a candidate in decreasing
     * frequency order for {@link #predict(ComposingText, int, int)} and
     * {@link #makeCandidateListOf(int)}, in increasing character code order for
     * {@code searchWords()}.
     *
     * @return          The candidate; {@code null} if there is no more candidate.
     */
    public WnnWord getNextCandidate();

    /**
     * Retrieve the list of registered words.
     * <br>
     * @return          {@code null} if no word is registered; the array of {@link WnnWord} if some words is registered.
     */
    public WnnWord[] getUserDictionaryWords();

    /**
     * Learn a word. 
     * <br>
     * This method is used to register the word selected from
     * candidates to the learning dictionary or update the frequency
     * of the word.
     *
     * @param word      The selected word
     * @return          {@code true} if success; {@code false} if fail or not supported.
     */
    public boolean learn(WnnWord word);

    /**
     * Register a word to the user's dictionary.
     * <br>
     * @param word      A word to register
     * @return          Number of registered words in the user's dictionary after the operation; minus value if a error occurs.
     */
    public int addWord(WnnWord word);

    /**
     * Delete a word from the user's dictionary.
     * <br>
     * @param word      A word to delete
     * @return          {@code true} if success; {@code false} if fail or not supported.
     */
    public boolean deleteWord(WnnWord word);

    /**
     * Delete all words from the user's dictionary.
     * <br>
     * @param dictionary    {@code DICTIONARY_TYPE_LEARN} or {@code DICTIONARY_TYPE_USER}
     * @return              {@code true} if success; {@code false} if fail or not supported.
     */
    public boolean initializeDictionary(int dictionary);

    /**
     * Delete all words from the user's dictionary of the specified language.
     * <br>
     * @param dictionary        {@code DICTIONARY_TYPE_LEARN} or {@code DICTIONARY_TYPE_USER}
     * @param type              Dictionary type (language, etc...)
     * @return                  {@code true} if success; {@code false} if fail or not supported.
     */
    public boolean initializeDictionary(int dictionary, int type);

    /**
     * Reflect the preferences in the converter.
     *
     * @param pref  The preferences
     */
    public void setPreferences(SharedPreferences pref);

    /**
     * Break the sequence of words.
     * <br>
     * This method is used to notice breaking the sequence of input
     * words to the converter.  The converter will stop learning
     * collocation between previous input words and words which will
     * input after this break.
     */
    public void breakSequence();

    /**
     * Makes the candidate list.
     * <br>
     * This method is used when to make a list of other candidates of
     * the clause which is in the result of consecutive clause
     * conversion({@link #convert(ComposingText)}).
     * To get the elements of the list, call {@link #getNextCandidate()}.
     *
     * @param clausePosition  The position of a clause
     * @return                  Plus value if there are candidates; 0 if there is no candidate; minus value if a error occurs.
     */
    public int makeCandidateListOf(int clausePosition);
}
