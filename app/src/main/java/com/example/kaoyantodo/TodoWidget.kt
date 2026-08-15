package com.example.kaoyantodo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.dp
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.text.TextStyle
import androidx.glance.unit.sp
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxWidth
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.compose.ui.graphics.Color

object TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(context) }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val p = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
        val day = p.getInt("widget_day", todayDay())
        val date = p.getString("widget_date", "") ?: ""
        val done = p.getInt("widget_done", 0)
        val total = p.getInt("widget_total", 0)
        Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF3157D5))).cornerRadius(20.dp).padding(16.dp).clickable(actionStartActivity<MainActivity>())) {
            Text("考研计划", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp))
            Spacer(GlanceModifier.height(4.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Day $day", style = TextStyle(color = ColorProvider(Color.White), fontSize = 24.sp))
                    Text(date, style = TextStyle(color = ColorProvider(Color.White.copy(alpha = .8f)), fontSize = 12.sp))
                }
                Text("$done/$total", style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Text("点击查看今日任务", style = TextStyle(color = ColorProvider(Color.White.copy(alpha = .9f)), fontSize = 12.sp))
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget
}
