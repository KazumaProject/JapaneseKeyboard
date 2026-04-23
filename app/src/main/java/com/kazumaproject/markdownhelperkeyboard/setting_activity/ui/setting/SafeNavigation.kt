package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import timber.log.Timber

internal fun Fragment.navigateSafely(@IdRes resId: Int): Boolean {
    return runCatching {
        findNavController().navigateSafely(resId)
    }.getOrElse { error ->
        Timber.w(error, "Failed to obtain NavController for navigation target: %s", resId)
        false
    }
}

internal fun NavController.navigateSafely(@IdRes resId: Int): Boolean {
    val destination = currentDestination ?: return false
    val hasAction = destination.getAction(resId) != null || graph.getAction(resId) != null
    val hasDestination = graph.findNode(resId) != null

    if (!hasAction && !hasDestination) {
        Timber.w(
            "Ignored navigation because action/destination was not available. current=%s target=%s",
            destination.displayName,
            resId
        )
        return false
    }

    if (!hasAction && destination.id == resId) {
        return false
    }

    return runCatching {
        navigate(resId)
        true
    }.getOrElse { error ->
        Timber.w(
            error,
            "Ignored duplicate or stale navigation. current=%s target=%s",
            destination.displayName,
            resId
        )
        false
    }
}
