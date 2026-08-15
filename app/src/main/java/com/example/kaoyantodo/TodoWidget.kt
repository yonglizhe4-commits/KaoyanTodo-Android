package com.example.kaoyantodo

import android.content.Context
import androidx.compose.ui.graphics.Color
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
import androidx.glance.unit.Dp
import androidx.glance.unit.Sp

object TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("todo_state", Context.MODE_PRIVATE)
            val day = prefs.getInt("widget_day", 1)
            val date = prefs.getString("widget_date", "8月15日") ?: "8月15日"
            val done = prefs.getInt("widget_done", 0)
            val total = prefs.getInt("widget_total", 0)
            val white = ColorProvider(Color.White, Color.White)
            val blue = ColorProvider(Color(0xFF3157D5), Color(0xFF3157D5))
            Column(
                modifier = GlanceModifier.fillMaxSize().background(blue).padding(Dp(16f))
            ) {
                Text("考研每日计划", style = TextStyle(color = white, fontSize = Sp(16f)))
                Spacer(GlanceModifier.height(Dp(4f)))
                Text("Day $day · $date", style = TextStyle(color = white, fontSize = Sp(22f)))
                Spacer(GlanceModifier.height(Dp(8f)))
                Text("今日完成：$done / $total", style = TextStyle(color = white, fontSize = Sp(14f)))
                Spacer(GlanceModifier.height(Dp(6f)))
                Text("点击桌面组件进入 App", style = TextStyle(color = white, fontSize = Sp(12f)))
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget
}
