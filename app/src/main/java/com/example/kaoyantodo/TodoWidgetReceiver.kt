package com.example.kaoyantodo

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.action.ActionParameters
import android.content.Intent

object TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val day = prefs.getInt("widget_day", 1)
            val date = prefs.getString("widget_date", "8月15日") ?: "8月15日"
            val done = prefs.getInt("widget_done", 0)
            val total = prefs.getInt("widget_total", 0)
            Column(
                modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(ColorProvider(android.graphics.Color.WHITE)).cornerRadius(22.dp).padding(16.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                Text("📚 考研计划", style = TextStyle(color = ColorProvider(android.graphics.Color.rgb(49,87,213))))
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("DAY $day", style = TextStyle(color = ColorProvider(android.graphics.Color.DKGRAY)))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(date, style = TextStyle(color = ColorProvider(android.graphics.Color.GRAY)))
                }
                Spacer(GlanceModifier.height(8.dp))
                Text("今日完成 $done / $total", style = TextStyle(color = ColorProvider(android.graphics.Color.DKGRAY)))
                Spacer(GlanceModifier.height(4.dp))
                Text("点击查看今日任务", style = TextStyle(color = ColorProvider(android.graphics.Color.rgb(49,87,213))))
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget
}
