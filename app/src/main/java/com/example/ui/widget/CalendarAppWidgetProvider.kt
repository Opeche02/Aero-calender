package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val goScope = CoroutineScope(Dispatchers.IO)
        goScope.launch {
            try {
                // Fetch the next 3 upcoming active events from database
                val database = AppDatabase.getDatabase(context)
                val nowMs = System.currentTimeMillis()
                val upcoming = database.eventDao().getUpcomingEventsSync(nowMs, 3)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_upcoming_events)
                    
                    // Bind Quick Add action Pending Intent
                    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_OPEN_QUICK_ADD", true)
                    }
                    val flagMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val quickAddPI = PendingIntent.getActivity(context, 100, quickAddIntent, flagMode)
                    views.setOnClickPendingIntent(R.id.widget_quick_add_btn, quickAddPI)

                    if (upcoming.isEmpty()) {
                        views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_items_container, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                        views.setViewVisibility(R.id.widget_items_container, View.VISIBLE)

                        // Bind Item 1
                        views.setViewVisibility(R.id.widget_item_1, View.VISIBLE)
                        bindEventRow(context, views, R.id.widget_item_1, R.id.widget_text_title_1, R.id.widget_text_desc_1, R.id.widget_color_bar_1, upcoming[0], flagMode)

                        // Bind Item 2
                        if (upcoming.size > 1) {
                            views.setViewVisibility(R.id.widget_item_2, View.VISIBLE)
                            bindEventRow(context, views, R.id.widget_item_2, R.id.widget_text_title_2, R.id.widget_text_desc_2, R.id.widget_color_bar_2, upcoming[1], flagMode)
                        } else {
                            views.setViewVisibility(R.id.widget_item_2, View.GONE)
                        }

                        // Bind Item 3
                        if (upcoming.size > 2) {
                            views.setViewVisibility(R.id.widget_item_3, View.VISIBLE)
                            bindEventRow(context, views, R.id.widget_item_3, R.id.widget_text_title_3, R.id.widget_text_desc_3, R.id.widget_color_bar_3, upcoming[2], flagMode)
                        } else {
                            views.setViewVisibility(R.id.widget_item_3, View.GONE)
                        }
                    }

                    // Update widget
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun bindEventRow(
        context: Context,
        views: RemoteViews,
        rowId: Int,
        titleId: Int,
        descId: Int,
        colorBarId: Int,
        event: Event,
        flagMode: Int
    ) {
        views.setTextViewText(titleId, event.title)

        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStartStr = formatter.format(Date(event.startTime))
        val dateLabel = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(event.startTime))
        val subtitle = if (event.location.isNotEmpty()) {
            "$dateLabel, $timeStartStr | ${event.location}"
        } else {
            "$dateLabel, $timeStartStr"
        }
        views.setTextViewText(descId, subtitle)

        // Set category vertical bar accent color
        val hexColor = when (event.categoryName) {
            "Work" -> "#F44336"
            "Personal" -> "#4CAF50"
            "Family" -> "#FF9800"
            "Fitness" -> "#9C27B0"
            else -> "#3F51B5"
        }
        try {
            views.setInt(colorBarId, "setBackgroundColor", Color.parseColor(hexColor))
        } catch (e: Exception) {
            views.setInt(colorBarId, "setBackgroundColor", Color.BLUE)
        }

        // Set click pending intent to load this specific event in details mode inside the app
        val rowClickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_EVENT_ID", event.id)
        }
        // Unique request code per row to prevent overriding intents
        val rowPI = PendingIntent.getActivity(context, event.id.toInt() + 101, rowClickIntent, flagMode)
        views.setOnClickPendingIntent(rowId, rowPI)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh triggers on database adjustments
        if (intent.action == "com.example.ui.widget.ACTION_REFRESH_WIDGET" ||
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CalendarAppWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val refreshIntent = Intent(context, CalendarAppWidgetProvider::class.java).apply {
                action = "com.example.ui.widget.ACTION_REFRESH_WIDGET"
            }
            context.sendBroadcast(refreshIntent)
        }
    }
}
