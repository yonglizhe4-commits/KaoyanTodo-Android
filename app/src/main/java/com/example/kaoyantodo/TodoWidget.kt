package com.example.kaoyantodo

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.dp
import androidx.glance.unit.sp

object TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val day = prefs.getInt("widget_day", 1)
            val date = prefs.getString("widget_date", "8月15日") ?: "8月15日"
            val done = prefs.getInt("widget_done", 0)
            val total = prefs.getInt("widget_total", 0)
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(android.graphics.Color.rgb(49, 87, 213), android.graphics.Color.rgb(49, 87, 213))).padding(16.dp)
            ) {
                Text("考研每日计划", style = TextStyle(color = ColorProvider(android.graphics.Color.WHITE, android.graphics.Color.WHITE), fontSize = 16.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text("Day $day · $date", style = TextStyle(color = ColorProvider(android.graphics.Color.WHITE, android.graphics.Color.WHITE), fontSize = 22.sp))
                Spacer(GlanceModifier.height(8.dp))
                Text("今日完成：$done / $total", style = TextStyle(color = ColorProvider(android.graphics.Color.WHITE, android.graphics.Color.WHITE), fontSize = 14.sp))
                Spacer(GlanceModifier.height(6.dp))
                Text("点击桌面组件进入 App", style = TextStyle(color = ColorProvider(android.graphics.Color.WHITE, android.graphics.Color.WHITE), fontSize = 12.sp))
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget
}
