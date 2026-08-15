package com.example.kaoyantodo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FixedTodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    companion object {
        fun updateAll(context: Context) {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val days = FixedPlanRepository.load(context)
            val selected = days.firstOrNull { it.day == prefs.getInt("widget_day", 1) } ?: days.first()
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            fun isDone(day: FixedStudyDay, task: FixedTodoTask) = prefs.getBoolean("${day.day}:${task.id}", false)

            val all = days.flatMap { d -> d.tasks.map { d to it } }
            val overallDone = all.count { isDone(it.first, it.second) }
            val overallPct = if (all.isEmpty()) 0 else overallDone * 100 / all.size
            val todayDone = selected.tasks.count { isDone(selected, it) }
            val todayPct = if (selected.tasks.isEmpty()) 0 else todayDone * 100 / selected.tasks.size

            views.setTextViewText(R.id.widget_title, "考研作战计划  /  MASTER MODE")
            views.setTextViewText(R.id.widget_day, "DAY ${selected.day} · ${selected.date.monthValue}.${selected.date.dayOfMonth}")
            views.setTextViewText(R.id.widget_progress, "考研总进度  $overallPct%")
            views.setProgressBar(R.id.widget_bar, 100, overallPct, false)
            views.setTextViewText(R.id.widget_today, "今日完成  $todayPct%  ·  $todayDone / ${selected.tasks.size}")

            val subjectRows = listOf(
                "英语" to R.id.widget_subject1,
                "315化学" to R.id.widget_subject2,
                "415生理生化" to R.id.widget_subject3,
                "政治" to R.id.widget_subject4
            )
            subjectRows.forEach { (subject, id) ->
                val subset = all.filter { it.second.subject == subject }
                val pct = if (subset.isEmpty()) 0 else subset.count { isDone(it.first, it.second) } * 100 / subset.size
                views.setTextViewText(id, "$subject  $pct%")
            }

            val visible = selected.tasks.filterNot { isDone(selected, it) } + selected.tasks.filter { isDone(selected, it) }
            val taskIds = listOf(R.id.widget_task1, R.id.widget_task2, R.id.widget_task3, R.id.widget_task4, R.id.widget_task5)
            taskIds.forEachIndexed { i, viewId ->
                if (i < visible.size) {
                    val t = visible[i]
                    views.setTextViewText(viewId, "${if (isDone(selected, t)) "✓" else "○"} ${t.time}  ${t.title}")
                } else views.setTextViewText(viewId, "")
            }

            val intent = Intent(context, FixedMainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, FixedTodoWidgetProvider::class.java), views)
        }
    }
}
