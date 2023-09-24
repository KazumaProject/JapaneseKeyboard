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


/**
 * The interface of dictionary searcher
 *
 * @author Copyright (C) 2008-2009, OMRON SOFTWARE CO., LTD.  All Rights Reserved.
 */
public interface WnnDictionary {
    /*
     * DEFINITION OF CONSTANTS
     */
    /**
     * Predefined approximate pattern set (capital letters from small letters).
     * 
     * This pattern includes the rules for ambiguous searching capital letters from small letters.<br>
     * ex. "a" to "A", "b" to "B", ... , "z" to "Z"
     */
    public static final int APPROX_PATTERN_EN_TOUPPER               = 0;
    /**
     * Predefined approximate pattern set (small letters from capital letters).
     *
     * This pattern includes the rules for ambiguous searching small letters from capital letters.<br>
     * ex. "A" to "a", "B" to "b", ... , "Z" to "z"
     */
    public static final int APPROX_PATTERN_EN_TOLOWER               = 1;
    /**
     * Predefined approximate pattern set (QWERTY neighbor keys).
     *
     * This pattern includes the rules for ambiguous searching neighbor keys on QWERTY keyboard.
     * Only alphabet letters are defined; numerical or symbol letters are not defined as the rules.<br>
     * ex. "a" to "q"/"w"/"s"/"z", "b" to "v"/"g"/"h"/"n", ... ,"z" to "a"/"s"/"x"
     */
    public static final int APPROX_PATTERN_EN_QWERTY_NEAR           = 2;
    /**
     * Predefined approximate pattern set (QWERTY neighbor keys/capital letters).
     *
     * This pattern includes the rules for ambiguous searching capital letters of neighbor keys on QWERTY keyboard.
     * Only alphabet letters are defined; numerical or symbol letters are not defined as the rules.<br>
     * ex. "a" to "Q"/"W"/"S"/"Z", "b" to "V"/"G"/"H"/"N", ... ,"z" to "A"/"S"/"X"
     */
    public static final int APPROX_PATTERN_EN_QWERTY_NEAR_UPPER     = 3;
    /**
     * Predefined approximate pattern set (for Japanese 12-key keyboard).
     *
     * This pattern includes the standard rules for Japanese multi-tap 12-key keyboard.
     */
    public static final int APPROX_PATTERN_JAJP_12KEY_NORMAL        = 4;

    /** Search operation mode (exact matching). */
    public static final int SEARCH_EXACT                            = 0;
    /** Search operation mode (prefix matching). */
    public static final int SEARCH_PREFIX                           = 1;
    /** Search operation mode (link search). */
    public static final int SEARCH_LINK                             = 2;

    /** Sort order (frequency in descending). */
    public static final int ORDER_BY_FREQUENCY                      = 0;
    /** Sort order (character code of key string in ascending). */
    public static final int ORDER_BY_KEY                            = 1;

    /** Type of a part of speech (V1) */
    public static final int POS_TYPE_V1                             = 0;
    /** Type of a part of speech (V2) */
    public static final int POS_TYPE_V2                             = 1;
    /** Type of a part of speech (V3) */
    public static final int POS_TYPE_V3                             = 2;
    /** Type of a part of speech (Top of sentence) */
    public static final int POS_TYPE_BUNTOU                         = 3;
    /** Type of a part of speech (Single Chinese character) */
    public static final int POS_TYPE_TANKANJI                       = 4;
    /** Type of a part of speech (Numeric) */
    public static final int POS_TYPE_SUUJI                          = 5;
    /** Type of a part of speech (Noun) */
    public static final int POS_TYPE_MEISI                          = 6;
    /** Type of a part of speech (Person's name) */
    public static final int POS_TYPE_JINMEI                         = 7;
    /** Type of a part of speech (Place name) */
    public static final int POS_TYPE_CHIMEI                         = 8;
    /** Type of a part of speech (Symbol) */
    public static final int POS_TYPE_KIGOU                          = 9;

    /** Index of the user dictionary for {@link #setDictionary(int, int, int)} */
    public static final int INDEX_USER_DICTIONARY                   = -1;
    /** Index of the learn dictionary for {@link #setDictionary(int, int, int)} */
    public static final int INDEX_LEARN_DICTIONARY                  = -2;

    
    /**
     * Whether this dictionary module is active.
     * @return {@code true} if this dictionary module is active; {@code false} if not.
     */
    public boolean isActive();
    
    /**
     * Set "in use" state.
     *
     * When the flag set true, the user dictionary is locked.
     *
     * @param flag      {@code true} if the user dictionary is locked; {@code false} if the user dictionary is unlocked.
     */
    public void setInUseState(boolean flag);

    /**
     * Clear all dictionary settings.
     *
     * All the dictionaries are set to be unused.
     *
     * @return          0 if success; minus value(error code) if fail.
     */
    public int clearDictionary();

    /**
     * Sets a dictionary information for using specified dictionary.
     *
     * <p>
     * A dictionary information contains parameters:<br>
     * {@code base} is the bias of frequency for the dictionary.<br>
     * {@code high} is the upper limit of frequency for the dictionary.
     * </p>
     * Searched word's frequency in the dictionary is mapped to the range from {@code base} to {@code high}.
     * <br>
     * The maximum value of {@code base} and {@code high} is 1000.
     * To set a dictionary unused, specify -1 to {@code base} and {@code high}.
     *
     * @param index     A dictionary index
     * @param base      The base frequency for the dictionary
     * @param high      The maximum frequency for the dictionary
     * @return          0 if success; minus value(error code) if fail.
     */
    public int setDictionary(int index, int base, int high);

    /**
     * Clears approximate patterns.
     *
     * This clears all approximate search patterns in the search condition.
     */
    public void clearApproxPattern();

    /**
     * Sets a approximate pattern.
     *
     * This adds an approximate search pattern(replacement of character) to the search condition.
     * The pattern rule is defined as replacing a character({@code src}) to characters({@code dst}).
     * <br>
     * The length of {@code src} must be 1 and the length of {@code dst} must be lower than 4.<br>
     * The maximum count of approximate patterns is 255.
     *
     * @param src       A character replace from
     * @param dst       Characters replace to
     * @return          0 if success; minus value(error code) if fail.
     */
    public int setApproxPattern(String src, String dst);

    /**
     * Sets a predefined approximate pattern.
     *
     * The patterns included predefined approximate search pattern set specified by
     * {@code approxPattern} are added to the search condition.
     *
     * @param approxPattern     A predefined approximate pattern set
     * @see jp.co.omronsoft.openwnn.WnnDictionary#APPROX_PATTERN_EN_TOUPPER
     * @see jp.co.omronsoft.openwnn.WnnDictionary#APPROX_PATTERN_EN_TOLOWER
     * @see jp.co.omronsoft.openwnn.WnnDictionary#APPROX_PATTERN_EN_QWERTY_NEAR
     * @see jp.co.omronsoft.openwnn.WnnDictionary#APPROX_PATTERN_EN_QWERTY_NEAR_UPPER
     *
     * @return                  0 if success; minus value(error code) if fail.
     */
    public int setApproxPattern(int approxPattern);

    /**
     * Search words from dictionaries with specified conditions.
     * <p>
     * To get the searched word's information, use {@link #getNextWord()}.<br>
     * If a same word existed in the set of dictionary, the search result may contain some same words.<br>
     * <br>
     * If approximate patterns were set, the first word in search
     * results is the highest approximation word which contains best
     * matched character in the key string. <br>
     * For example, If a key string is "bbc", a approximate pattern
     * "b" to "a" is specified and the dictionary includes "abc
     * (frequency 10)" "bbcd (frequency 1)" "aac (frequency 5)"; the
     * result of prefix search is output by following order: "bbcd",
     * "abc", "aac".
     * </p>
     * <p>
     * The supported combination of parameters is:
     *
     * Search Mode      Sort        Order           Ambiguous Search
     * exact matching   frequency   descending      no
     * prefix matching  frequency   descending      no
     * prefix matching  frequency   descending      yes
     * prefix matching  character   code ascending  no
     * </p>
     *
     * @param operation     The search operation
     * @see jp.co.omronsoft.openwnn.WnnDictionary#SEARCH_EXACT
     * @see jp.co.omronsoft.openwnn.WnnDictionary#SEARCH_PREFIX
     * @param order         The sort order
     * @see jp.co.omronsoft.openwnn.WnnDictionary#ORDER_BY_FREQUENCY
     * @see jp.co.omronsoft.openwnn.WnnDictionary#ORDER_BY_KEY
     * @param keyString     The key string
     *
     * @see jp.co.omronsoft.openwnn.WnnDictionary#getNextWord
     *
     * @return              0 if no word is found; 1 if some words found; minus value if a error occurs.
     */
    public int searchWord(int operation, int order, String keyString);

    /**
     * Search words from dictionaries with specified conditions and previous word.
     * <p>
     * For using link search function, specify the {@code wnnWord} as previous word and
     * set {@code SEARCH_LINK} mode to {@code operation}. The other arguments are
     * the same as {@link #searchWord(int operation, int order, String keyString)}.
     * <p>
     * If the prediction dictionary for reading is set to use, the previous word must contain
     * the {@code stroke} and the {@code candidate} information. If the prediction dictionary
     * for part of speech is set to use, the previous word must contain the {@code partOfSpeech} information.
     *
     * @param operation     The search operation
     * @see jp.co.omronsoft.openwnn.WnnDictionary#SEARCH_EXACT
     * @see jp.co.omronsoft.openwnn.WnnDictionary#SEARCH_PREFIX
     * @param order         The sort order
     * @see jp.co.omronsoft.openwnn.WnnDictionary#ORDER_BY_FREQUENCY
     * @see jp.co.omronsoft.openwnn.WnnDictionary#ORDER_BY_KEY
     * @param keyString     The key string
     * @param wnnWord       The previous word
     * @see jp.co.omronsoft.openwnn.WnnDictionary#searchWord
     *
     * @return              0 if no word is found; 1 if some words found; minus value if a error occurs.
     */
    public int searchWord(int operation, int order, String keyString, WnnWord wnnWord);

    /**
     * Retrieve a searched word information.
     *
     * It returns a word information from top of the {@code searchWord()}'s result.
     * To get all word's information of the result, call this method repeatedly until it returns null.
     *
     * @return              An instance of WnnWord; null if no result or an error occurs.
     */
    public WnnWord getNextWord();

    /**
     * Retrieve a searched word information with condition of length.
     *
     * It returns a word information from top of the {@code searchWord()}'s result.
     * To get all word's information of the result, call this method repeatedly until it returns null.
     *
     * @param length    &#x3E;0 if only the result of specified length is retrieved; 0 if no condition exist
     * @return          An instance of WnnWord; null if no result or an error occurs.
     */
    public WnnWord getNextWord(int length);

    /**
     * Retrieve all word in the user dictionary.
     *
     * @return          The array of WnnWord objects.
     */
    public WnnWord[] getUserDictionaryWords();

    /**
     * Retrieve the connect matrix.
     *
     * @return          The array of the connect matrix; null if an error occurs.
     */
    public byte[][] getConnectMatrix();

    /**
     * Retrieve the part of speech information specified POS type.
     *
     * @param type      The type of a part of speech
     * @return          The part of speech information; null if invalid type is specified or  an error occurs.
     *
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_V1
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_V2
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_V3
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_BUNTOU
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_TANKANJI
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_SUUJI
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_MEISI
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_JINMEI
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_CHIMEI
     * @see jp.co.omronsoft.openwnn.WnnDictionary#POS_TYPE_KIGOU
    */
    public WnnPOS getPOS(int type);

    /**
     * Clear the user dictionary.
     * 
     * @return      0 if no error occur; &#x3C;0 if an error occur
     */
    public int clearUserDictionary();
    /**
     * Clear the learn dictionary.
     * 
     * @return      0 if no error occur; &#x3C;0 if an error occur
     */
    public int clearLearnDictionary();

    /**
     * Add the words to user dictionary.
     *
     * @param word      The array of word
     * @return          0 if no error occur; &#x3C;0 if an error occur
     */
    public int addWordToUserDictionary(WnnWord[] word);
    /**
     * Add the word to user dictionary.
     *
     * @param word      The word
     * @return          0 if no error occur; &#x3C;0 if an error occur
     */
    public int addWordToUserDictionary(WnnWord word);

    /**
     * Remove the words from user dictionary.
     *
     * @param word      The array of word
     * @return          0 if no error occur; &#x3C;0 if an error occur
     */
    public int removeWordFromUserDictionary(WnnWord[] word);
    /**
     * Remove the word from user dictionary.
     *
     * @param word      The word
     * @return          0 if no error occur; &#x3C;0 if an error occur
     */
    public int removeWordFromUserDictionary(WnnWord word);

    /**
     * Learn the word.
     *
     * @param word      The word for learning
     * @return          0 if no error occur; &#x3C;0 if an error occur
     */
    public int learnWord(WnnWord word);

    /**
     * Learn the word with connection.
     *
     * @param word              The word for learning
     * @param previousWord      The word for link learning
     * @return                  0 if no error occur; &#x3C;0 if an error occur
     */
    public int learnWord(WnnWord word, WnnWord previousWord);
}

