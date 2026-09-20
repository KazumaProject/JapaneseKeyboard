package com.kazumaproject.core.ui.skin

import android.view.View

/** Supplies the window view whose frame is the coordinate origin for an anchored popup. */
interface SkinPopupWindowHost {
    var applicationWindowView: View?
}
