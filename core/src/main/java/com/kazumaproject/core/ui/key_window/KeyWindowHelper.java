package com.kazumaproject.core.ui.key_window;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.kazumaproject.core.R;

/**
 * Created by sudamasayuki on 16/05/02.
 */
public class KeyWindowHelper {

    public static PopupWindow create(@NonNull Context context, @NonNull KeyWindowLayout keyWindowLayout) {

        PopupWindow popupWindow = new PopupWindow(context);

        popupWindow.setContentView(keyWindowLayout);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        // change background color to transparent
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.popup_window_transparent));

        return popupWindow;
    }

}
