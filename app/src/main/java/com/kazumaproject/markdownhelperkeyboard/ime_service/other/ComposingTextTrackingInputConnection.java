package com.kazumaproject.markdownhelperkeyboard.ime_service.other;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class ComposingTextTrackingInputConnection implements InputConnection {
    private final InputConnection baseConnection;
    private String composingText = "";
    private Integer composingTextInsertPosition = 0;

    public ComposingTextTrackingInputConnection(InputConnection baseConnection) {
        if (baseConnection == null) {
            throw new NullPointerException();
        }
        this.baseConnection = baseConnection;
    }
    public String getComposingText() {
        return composingText;
    }

    public void resetComposingText(){
        this.composingText = "";
        this.composingTextInsertPosition = 0;
    }

    public synchronized Integer getComposingTextInsertPosition() { return composingTextInsertPosition;}
    @Override
    public boolean beginBatchEdit() {
        return baseConnection.beginBatchEdit();
    }
    @Override
    public boolean clearMetaKeyStates(int states) {
        return baseConnection.clearMetaKeyStates(states);
    }
    @Override
    public boolean commitCompletion(CompletionInfo text) {
        return baseConnection.commitCompletion(text);
    }
    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        return baseConnection.commitCorrection(correctionInfo);
    }
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        return baseConnection.commitText(text, newCursorPosition);
    }
    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        return baseConnection.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return false;
    }

    @Override
    public boolean endBatchEdit() {
        return baseConnection.endBatchEdit();
    }
    @Override
    public boolean finishComposingText() {
        composingText = "";
        composingTextInsertPosition = 0;
        return baseConnection.finishComposingText();
    }
    @Override
    public int getCursorCapsMode(int reqModes) {
        return baseConnection.getCursorCapsMode(reqModes);
    }
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        return baseConnection.getExtractedText(request, flags);
    }
    @Override
    public CharSequence getSelectedText(int flags) {
        return baseConnection.getSelectedText(flags);
    }
    @Override
    public CharSequence getTextAfterCursor(int n, int flags) {
        return baseConnection.getTextAfterCursor(n, flags);
    }
    @Override
    public CharSequence getTextBeforeCursor(int n, int flags) {
        return baseConnection.getTextBeforeCursor(n, flags);
    }
    @Override
    public boolean performContextMenuAction(int id) {
        return baseConnection.performContextMenuAction(id);
    }
    @Override
    public boolean performEditorAction(int editorAction) {
        return baseConnection.performEditorAction(editorAction);
    }
    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        return baseConnection.performPrivateCommand(action, data);
    }
    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        return baseConnection.reportFullscreenMode(enabled);
    }
    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        return baseConnection.sendKeyEvent(event);
    }
    @Override
    public boolean setComposingRegion(int start, int end) {
        // Note: This method is introduced since API level 9. Mozc supports API level 7,
        // so we don't need to track the composing text by the invocation of this method.
        return baseConnection.setComposingRegion(start, end);
    }
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        composingText = text == null ? "" : text.toString();
        if (text == null){
            composingTextInsertPosition = 0;
        }else {
            composingTextInsertPosition = text.length();
        }
        return baseConnection.setComposingText(text, newCursorPosition);
    }
    @Override
    public boolean setSelection(int start, int end) {
        return baseConnection.setSelection(start, end);
    }

    public void moveLeftComposingTextPosition(){
        this.composingTextInsertPosition -= 1;
        if (this.composingTextInsertPosition < 0) this.composingTextInsertPosition = 0;
    }

    public void moveRightComposingTextPosition(){
        this.composingTextInsertPosition += 1;
        if (this.composingTextInsertPosition > this.composingText.length()) this.composingTextInsertPosition = this.composingText.length();
    }

    public void setComposingTextInsertPosition(Integer num){
        this.composingTextInsertPosition = num;
    }

    /**
     * Returns the instance of ComposingTextTrackingInputConnection based on the given baseConnection.
     * This method will return:
     * - {@code null}, if the given connection is {@code null}.
     * - the given connection instance as is, if it is the instance of
     *   ComposingTextTrackingInputConnection.
     * - the new instance of ComposingTextTrackingInputConnection wrapping baseConnection, otherwise.
     */
    public static ComposingTextTrackingInputConnection newInstance(InputConnection baseConnection) {
        if (baseConnection == null) {
            return null;
        }
        if (baseConnection instanceof ComposingTextTrackingInputConnection) {
            // The InputConnection is already wrapped by ComposingTextTrackingInputConnection,
            // so we don't need to re-wrap it.
            return (ComposingTextTrackingInputConnection) baseConnection;
        }
        return new ComposingTextTrackingInputConnection(baseConnection);
    }
    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        return baseConnection.requestCursorUpdates(cursorUpdateMode);
    }

    @Nullable
    @Override
    public Handler getHandler() {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void closeConnection() {
        baseConnection.closeConnection();
    }

    @Override
    public boolean commitContent(@NonNull InputContentInfo inputContentInfo, int flags, @Nullable Bundle opts) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            return baseConnection.commitContent(inputContentInfo, flags, opts);
        }else {
            return false;
        }
    }

}
