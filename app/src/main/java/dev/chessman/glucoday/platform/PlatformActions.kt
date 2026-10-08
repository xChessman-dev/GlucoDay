package dev.chessman.glucoday.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit app entry points. No health values or medication names are put in an intent. */
object PlatformActions {
    const val QUICK_ADD_GLUCOSE = "dev.chessman.glucoday.action.QUICK_ADD_GLUCOSE"
    const val OPEN_MEDICATIONS = "dev.chessman.glucoday.action.OPEN_MEDICATIONS"
    const val SHOW_PRIVACY = "dev.chessman.glucoday.action.SHOW_PRIVACY"

    fun mainActivityIntent(context: Context, action: String, identity: String): Intent =
        Intent(action).apply {
            setClassName(context.packageName, "dev.chessman.glucoday.MainActivity")
            data = Uri.Builder().scheme("glucoday").authority("local")
                .appendPath(action.substringAfterLast('.')).appendPath(identity).build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
