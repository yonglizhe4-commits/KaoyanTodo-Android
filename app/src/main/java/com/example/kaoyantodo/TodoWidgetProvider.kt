package com.example.kaoyantodo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    companion object {
        fun updateAll(context: Context) {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val days = PlanRepository.load(context)
            val selected = days.firstOrNull { it.day == prefs.getInt("widget_day", days.firstOrNull()?.day ?: 1) } ?: days.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            if (selected != null) {
                fun done(day: StudyDay, task: TodoTask) = prefs.getBoolean("${day.day}:${task.id}", false)
                val todayDone = selected.tasks.count { done(selected, it) }
                val todayTotal = selected.tasks.size
                val all = days.flatMap { d -> d.tasks.map { d to it } }
                val overallDone = all.count { done(it.first, it.second) }
                val overallPct = if (all.isEmpty()) 0 else overallDone * 100 / all.size
                val subjectRows = listOf(
                    "英语" to R.id.widget_subject1,
                    "315化学" to R.id.widget_subject2,
                    "415生理生化" to R.id.widget_subject3,
                    "政治" to R.id.widget_subject4
                )
                views.setTextViewText(R.id.widget_title, "考研作战计划  /  MASTER MODE")
                views.setTextViewText(R.id.widget_day, "DAY ${selected.day} · ${selected.date.monthValue}.${selected.date.dayOfMonth}")
                views.setTextViewText(R.id.widget_progress, "考研总进度  $overallPct%")
                views.setProgressBar(R.id.widget_bar, 100, overallPct, false)
                views.setTextViewText(R.id.widget_today, "今日完成  ${if (todayTotal == 0) 0 else todayDone * 100 / todayTotal}%  ·  $todayDone / $todayTotal")
                subjectRows.forEach { (subject, id) ->
                    val subset = all.filter { subjectOf(it.second.title) == subject }
                    val pct = if (subset.isEmpty()) 0 else subset.count { done(it.first, it.second) } * 100 / subset.size
                    views.setTextViewText(id, "$subject  $pct%")
                }
                val show = selected.tasks.sortedWith(compareBy<TodoTask> { done(selected, it) }.thenBy { it.time }).take(5)
                val taskIds = listOf(R.id.widget_task1, R.id.widget_task2, R.id.widget_task3, R.id.widget_task4, R.id.widget_task5)
                taskIds.forEachIndexed { i, viewId ->
                    if (i < show.size) {
                        val task = show[i]
                        val isDone = done(selected, task)
                        views.setTextViewText(viewId, "${if (isDone) "✓" else "○"} ${task.time}  ${task.title}")
                        views.setTextColor(viewId, if (isDone) 0xFF72D68C.toInt() else 0xFFFFFFFF.toInt())
                    } else views.setTextViewText(viewId, "")
                }
            }
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, TodoWidgetProvider::class.java), views)
        }
    }
}
