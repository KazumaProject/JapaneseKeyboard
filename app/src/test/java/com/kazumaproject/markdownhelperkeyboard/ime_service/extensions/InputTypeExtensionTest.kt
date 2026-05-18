package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTypeExtensionTest {

    @Test
    fun textClassPasswordLikeHintTextReturnsTextPassword() {
        listOf(
            "Password",
            "PASSWORD",
            "PassWord",
            "passwd",
            "パスワード",
            "暗証番号",
            "暗証",
        ).forEach { hintText ->
            assertEquals(
                hintText,
                InputTypeForIME.TextPassword,
                editorInfo(hintText = hintText).currentInputType()
            )
        }
    }

    @Test
    fun textClassPasswordLikeFieldNameReturnsTextPassword() {
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(fieldName = "password").currentInputType()
        )
    }

    @Test
    fun textClassPasswordLikePrivateImeOptionsReturnsTextPassword() {
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(privateImeOptions = "router.field=password").currentInputType()
        )
    }

    @Test
    fun typeNullReturnsTextPasswordOnlyWithPasswordLikeMetadata() {
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(
                inputType = InputType.TYPE_NULL,
                hintText = "Password"
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.Text,
            editorInfo(inputType = InputType.TYPE_NULL).currentInputType()
        )
    }

    @Test
    fun passwordLikeMetadataIsCheckedBeforeGoAndNoSuggestions() {
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(
                imeOptions = EditorInfo.IME_ACTION_GO,
                hintText = "Password"
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                hintText = "Password"
            ).currentInputType()
        )
    }

    @Test
    fun existingNoSuggestionsAndGoClassificationRemainWithoutPasswordLikeMetadata() {
        assertEquals(
            InputTypeForIME.TextNoSuggestion,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.TextUri,
            editorInfo(imeOptions = EditorInfo.IME_ACTION_GO).currentInputType()
        )
    }

    @Test
    fun searchHintClassificationRemainsUnlessPasswordLikeMetadataIsPresent() {
        assertEquals(
            InputTypeForIME.TextSearchView,
            editorInfo(hintText = "Search").currentInputType()
        )
        assertEquals(
            InputTypeForIME.TextSearchView,
            editorInfo(hintText = "検索").currentInputType()
        )
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(hintText = "Search Password").currentInputType()
        )
    }

    @Test
    fun broadPassAndPinWordsAreNotTreatedAsPassword() {
        listOf("passport", "passenger", "pin").forEach { hintText ->
            assertEquals(
                hintText,
                InputTypeForIME.Text,
                editorInfo(hintText = hintText).currentInputType()
            )
        }
    }

    @Test
    fun existingPasswordVariationsRemainPasswordClassifications() {
        assertEquals(
            InputTypeForIME.TextPassword,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ).currentInputType()
        )
        assertTrue(
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ).currentInputType().isPassword()
        )
        assertEquals(
            InputTypeForIME.TextVisiblePassword,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.NumberPassword,
            editorInfo(
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            ).currentInputType()
        )
    }

    @Test
    fun existingNonTextClassificationsRemainUnchanged() {
        assertEquals(
            InputTypeForIME.TextEmailAddress,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.TextUri,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.Number,
            editorInfo(inputType = InputType.TYPE_CLASS_NUMBER).currentInputType()
        )
        assertEquals(
            InputTypeForIME.Phone,
            editorInfo(inputType = InputType.TYPE_CLASS_PHONE).currentInputType()
        )
        assertEquals(
            InputTypeForIME.Date,
            editorInfo(
                inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
            ).currentInputType()
        )
        assertEquals(
            InputTypeForIME.Time,
            editorInfo(
                inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            ).currentInputType()
        )
    }

    @Test
    fun normalTextAndNoSuggestionsRemainUnchangedWithoutPasswordLikeMetadata() {
        assertEquals(InputTypeForIME.Text, editorInfo().currentInputType())
        assertEquals(
            InputTypeForIME.TextNoSuggestion,
            editorInfo(
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            ).currentInputType()
        )
    }

    private fun editorInfo(
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        imeOptions: Int = EditorInfo.IME_ACTION_NONE,
        hintText: CharSequence? = null,
        fieldName: String? = null,
        privateImeOptions: String? = null,
    ): EditorInfo {
        return EditorInfo().apply {
            this.inputType = inputType
            this.imeOptions = imeOptions
            this.hintText = hintText
            this.fieldName = fieldName
            this.privateImeOptions = privateImeOptions
        }
    }

    private fun EditorInfo.currentInputType(): InputTypeForIME {
        return getCurrentInputTypeForIME2(this)
    }
}
