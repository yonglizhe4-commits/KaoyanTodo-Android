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
            val days = PlanRepository.load(context)
            val savedDay = prefs.getInt("widget_day", days.firstOrNull()?.day ?: 1)
            val selected = days.firstOrNull { it.day == savedDay } ?: days.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            if (selected != null) {
                val done = selected.tasks.count { prefs.getBoolean("${selected.day}:${it.id}", false) }
                val total = selected.tasks.size
                val pct = if (total == 0) 0 else done * 100 / total
                views.setTextViewText(R.id.widget_title, "考研作战计划")
                views.setTextViewText(R.id.widget_day, "DAY ${selected.day}  ·  ${selected.date.monthValue}.${selected.date.dayOfMonth}")
                views.setTextViewText(R.id.widget_progress, "TODAY  $pct%   ·   $done / $total")
                views.setProgressBar(R.id.widget_bar, 100, pct, false)
                val show = selected.tasks.take(5)
                val ids = listOf(R.id.widget_task1, R.id.widget_task2, R.id.widget_task3, R.id.widget_task4, R.id.widget_task5)
                ids.forEachIndexed { i, viewId ->
                    if (i < show.size) {
                        val task = show[i]
                        val isDone = prefs.getBoolean("${selected.day}:${task.id}", false)
                        views.setTextViewText(viewId, "${if (isDone) "✓" else "○"} ${task.time}  ${task.title}")
                        views.setTextColor(viewId, if (isDone) 0xFF77DD77.toInt() else 0xFFFFFFFF.toInt())
                    } else {
                        views.setTextViewText(viewId, "")
                    }
                }
            }
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, TodoWidgetProvider::class.java), views)
        }
    }
}
