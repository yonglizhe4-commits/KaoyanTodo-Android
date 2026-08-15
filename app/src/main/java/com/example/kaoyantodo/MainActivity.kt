package com.example.kaoyantodo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodoTask(val id: String, val time: String, val title: String, val detail: String = "")
data class StudyDay(val day: Int, val date: LocalDate, val tasks: List<TodoTask>)

object PlanRepository {
    private var cache: List<StudyDay>? = null
    fun load(context: Context): List<StudyDay> {
        cache?.let { return it }
        val raw = context.assets.open("plan.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONArray(raw)
        val matrix = root.getJSONObject(0).getJSONArray("tasks")
        val days = linkedMapOf<Int, MutableList<TodoTask>>()
        val dates = mutableMapOf<Int, LocalDate>()
        for (c in 1 until 15) {
            var current = -1
            for (r in 0 until matrix.length()) {
                val row = matrix.getJSONArray(r)
                if (c >= row.length()) continue
                val value = row.optString(c, "").trim()
                if (value.isEmpty()) continue
                val m = Regex("Day\\s*(\\d+)\\s*[｜|]\\s*(\\d+)月(\\d+)日").find(value)
                if (m != null) {
                    current = m.groupValues[1].toInt()
                    dates[current] = LocalDate.of(2026, m.groupValues[2].toInt(), m.groupValues[3].toInt())
                    days.getOrPut(current) { mutableListOf() }
                } else if (current > 0) {
                    val time = when {
                        value.startsWith("英语") -> "10:30"
                        value.startsWith("315") -> "14:00"
                        value.startsWith("415") -> "16:00"
                        value.startsWith("政治") -> "19:00"
                        value.contains("晚上") || value.contains("晚间") -> "22:00"
                        else -> "20:00"
                    }
                    days.getOrPut(current) { mutableListOf() }.add(TodoTask("$current-$c-$r", time, value))
                }
            }
        }
        cache = days.keys.sorted().map { d ->
            val fixed = listOf(
                TodoTask("$d-fixed-1", "07:30", "英语：背单词", "每日固定任务"),
                TodoTask("$d-fixed-2", "09:00", "所有：网课学习", "每日固定任务"),
                TodoTask("$d-fixed-3", "22:30", "晚间复盘 15–20 分钟", "记录当天错题、薄弱点和明日首要任务")
            )
            StudyDay(d, dates[d] ?: LocalDate.of(2026, 8, 15).plusDays((d - 1).toLong()), fixed + days[d]!!.distinctBy { it.title })
        }
        return cache!!
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaoyanApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaoyanApp(context: Context) {
    val days = remember { PlanRepository.load(context) }
    val today = LocalDate.now()
    val initial = remember {
        val exact = days.indexOfFirst { it.date == today }
        if (exact >= 0) exact else {
            val past = days.indexOfLast { it.date < today }
            if (past >= 0) past else 0
        }
    }
    var selectedIndex by remember { mutableIntStateOf(initial) }
    val selected = days[selectedIndex]
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    val completed = selected.tasks.count { prefs.getBoolean("${selected.day}:${it.id}", false) }
    val progress = if (selected.tasks.isEmpty()) 0f else completed.toFloat() / selected.tasks.size

    LaunchedEffect(selected.day, completed) {
        prefs.edit().putInt("widget_day", selected.day).putString("widget_date", "${selected.date.monthValue}月${selected.date.dayOfMonth}日").putInt("widget_done", completed).putInt("widget_total", selected.tasks.size).apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3157D5), surface = Color(0xFFF7F8FC), background = Color(0xFFF7F8FC))) {
        Scaffold(containerColor = Color(0xFFF7F8FC), topBar = { CenterAlignedTopAppBar(title = { Text("考研每日计划", fontWeight = FontWeight.Bold) }) }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3157D5)), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("DAY ${selected.day}", color = Color.White.copy(alpha = .75f), fontSize = 14.sp)
                                    Text("${selected.date.monthValue}月${selected.date.dayOfMonth}日", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color.White, trackColor = Color.White.copy(alpha = .25f))
                            Spacer(Modifier.height(8.dp))
                            Text("已完成 $completed / ${selected.tasks.size}", color = Color.White.copy(alpha = .9f))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = selectedIndex > 0, onClick = { selectedIndex-- }) { Icon(Icons.Default.ArrowBack, "上一天") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selected.date == today) "今天" else "计划日", fontWeight = FontWeight.Bold)
                            Text(DateTimeFormatter.ofPattern("yyyy/MM/dd").format(selected.date), color = Color.Gray, fontSize = 13.sp)
                        }
                        IconButton(enabled = selectedIndex < days.lastIndex, onClick = { selectedIndex++ }) { Icon(Icons.Default.ArrowForward, "下一天") }
                    }
                }
                item { Text("今日任务", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(selected.tasks, key = { it.id }) { task ->
                    val isDone = prefs.getBoolean("${selected.day}:${task.id}", false)
                    TaskCard(task, isDone) {
                        prefs.edit().putBoolean("${selected.day}:${task.id}", !isDone).apply()
                        TodoWidgetProvider.updateAll(context)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: TodoTask, done: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onToggle() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(task.time, color = Color(0xFF3157D5), fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
            Box(Modifier.size(4.dp, 42.dp).background(if (done) Color(0xFF22C55E) else Color(0xFFE5E7EB), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.alpha(if (done) .5f else 1f))
                if (task.detail.isNotBlank()) Text(task.detail, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (done) Color(0xFF22C55E) else Color(0xFF9CA3AF), modifier = Modifier.size(26.dp))
        }
    }
}
