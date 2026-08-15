package com.example.kaoyantodo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val day = prefs.getInt("widget_day", 1)
            val date = prefs.getString("widget_date", "8月15日") ?: "8月15日"
            val done = prefs.getInt("widget_done", 0)
            val total = prefs.getInt("widget_total", 0)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_title, "考研每日计划")
            views.setTextViewText(R.id.widget_day, "Day $day · $date")
            views.setTextViewText(R.id.widget_progress, "今日完成：$done / $total")
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(ComponentName(context, TodoWidgetProvider::class.java), views)
        }
    }
}
