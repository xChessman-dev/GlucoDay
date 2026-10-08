package dev.chessman.glucoday.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import dev.chessman.glucoday.R

/** Entry shortcut only: personal measurements are never exposed on the home/lock screen. */
class QuickGlucoseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = update(context, appWidgetManager, appWidgetId)

    private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val launch = PendingIntent.getActivity(
            context,
            0,
            PlatformActions.mainActivityIntent(context, PlatformActions.QUICK_ADD_GLUCOSE, "widget:$widgetId"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_quick_glucose).apply {
            setTextViewText(R.id.widget_title, "GlucoDay")
            setTextViewText(R.id.widget_subtitle, "Быстрая запись глюкозы")
            setOnClickPendingIntent(R.id.widget_add, launch)
            setOnClickPendingIntent(R.id.widget_title, launch)
            setOnClickPendingIntent(R.id.widget_subtitle, launch)
        }
        manager.updateAppWidget(widgetId, views)
    }

    companion object {
        fun requestPin(context: Context): Boolean {
            val manager = context.getSystemService(AppWidgetManager::class.java)
            return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
                ComponentName(context, QuickGlucoseWidgetProvider::class.java), null, null,
            )
        }
    }
}
