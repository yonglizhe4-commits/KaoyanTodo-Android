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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
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

private val Ink = Color(0xFF0B0B10)
private val Paper = Color(0xFFF2EEE6)
private val Red = Color(0xFFE51F2A)
private val Pink = Color(0xFFFF477E)
private val Yellow = Color(0xFFFFD83D)
private val Green = Color(0xFF13A866)
private val Muted = Color(0xFF777078)

private fun subjectOf(title: String): String = when {
    title.startsWith("英语") || title.contains("单词") || title.contains("阅读") || title.contains("写作") -> "英语"
    title.startsWith("315") || title.contains("化学") -> "315化学"
    title.startsWith("415") || title.contains("生理") -> "415生理"
    title.startsWith("政治") || title.contains("政治") -> "政治"
    else -> "其他"
}

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
                TodoTask("$d-fixed-1", "07:30", "英语：背单词", "DAILY / START"),
                TodoTask("$d-fixed-2", "09:00", "全科：网课学习", "DAILY / CORE"),
                TodoTask("$d-fixed-3", "22:30", "晚间复盘 15–20 分钟", "DAILY / REVIEW")
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

@Composable
fun KaoyanApp(context: Context) {
    val days = remember { PlanRepository.load(context) }
    val today = LocalDate.now()
    val initial = remember {
        val exact = days.indexOfFirst { it.date == today }
        if (exact >= 0) exact else (days.indexOfLast { it.date < today }.takeIf { it >= 0 } ?: 0)
    }
    var selectedIndex by remember { mutableIntStateOf(initial) }
    var refresh by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    val selected = days[selectedIndex]
    val isDone: (StudyDay, TodoTask) -> Boolean = { day, task -> prefs.getBoolean("${day.day}:${task.id}", false) }
    val doneTasks = selected.tasks.filter { isDone(selected, it) }
    val pendingTasks = selected.tasks.filterNot { isDone(selected, it) }
    val orderedTasks = pendingTasks + doneTasks
    val allTasks = days.flatMap { d -> d.tasks.map { d to it } }
    val overall = if (allTasks.isEmpty()) 0f else allTasks.count { isDone(it.first, it.second) }.toFloat() / allTasks.size
    val subjectProgress = listOf("英语", "315化学", "415生理", "政治").associateWith { subject ->
        val subset = allTasks.filter { subjectOf(it.second.title) == subject }
        if (subset.isEmpty()) 0f else subset.count { isDone(it.first, it.second) }.toFloat() / subset.size
    }
    val listState = rememberLazyListState()

    LaunchedEffect(selected.day, doneTasks.size, refresh) {
        prefs.edit().putInt("widget_day", selected.day).putString("widget_date", "${selected.date.monthValue}月${selected.date.dayOfMonth}日").apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink, primary = Red)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Paper),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeroHeader(selected, overall, subjectProgress) }
            item { DayNavigator(selected, today, selectedIndex, days.lastIndex,
                onPrev = { if (selectedIndex > 0) selectedIndex-- },
                onNext = { if (selectedIndex < days.lastIndex) selectedIndex++ }) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                    Text("TODAY / 任务队列", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.weight(1f))
                    Text("${doneTasks.size} / ${selected.tasks.size}", color = Red, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            items(orderedTasks, key = { it.id }) { task ->
                val done = isDone(selected, task)
                TaskCard(task, done) {
                    prefs.edit().putBoolean("${selected.day}:${task.id}", !done).apply()
                    refresh++
                }
            }
            item { SubjectBoard(subjectProgress) }
            item { Text("✓ 完成后自动沉底 · 再次点击可撤销", modifier = Modifier.padding(horizontal = 18.dp), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun HeroHeader(selected: StudyDay, overall: Float, subjectProgress: Map<String, Float>) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("PERSONA / STUDY MODE", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("DAY ${selected.day}", color = Color.White, fontSize = 43.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text("${selected.date.monthValue}.${selected.date.dayOfMonth}  //  今日作战计划", color = Color.White.copy(.72f), fontSize = 13.sp)
            }
            Box(Modifier.size(82.dp).background(Red, CutCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("${(overall * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("EXAMINATION MASTER PROGRESS", color = Color.White.copy(.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ProgressBar(overall, Color.White.copy(.18f), Red, 8.dp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            subjectProgress.forEach { (name, p) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color.White, fontSize = 9.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${(p * 100).toInt()}%", color = Color.White.copy(.7f), fontSize = 8.sp)
                    }
                    ProgressBar(p, Color.White.copy(.16f), if (name == "政治") Yellow else Pink, 5.dp)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Float, track: Color, fill: Color, height: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(Modifier.fillMaxWidth().height(height).background(track, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(fill, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun DayNavigator(selected: StudyDay, today: LocalDate, index: Int, last: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (selected.date == today) "TODAY" else "PLAN"}  ${DateTimeFormatter.ofPattern("MM / dd").format(selected.date)}", Modifier.weight(1f), fontWeight = FontWeight.Black, color = Red)
        TextButton(enabled = index > 0, onClick = onPrev) { Icon(Icons.Default.ArrowBack, null); Text(" PREV") }
        TextButton(enabled = index < last, onClick = onNext) { Text("NEXT "); Icon(Icons.Default.ArrowForward, null) }
    }
}

@Composable
private fun TaskCard(task: TodoTask, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .background(if (done) Color(0xFFE1DDD4) else Color.White, CutCornerShape(topStart = 2.dp, topEnd = 18.dp, bottomEnd = 2.dp, bottomStart = 18.dp))
            .clickable { onToggle() }.padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(55.dp)) {
            Text(task.time, color = Red, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(if (done) "COMPLETE" else "TODO", color = if (done) Green else Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.width(4.dp).height(49.dp).background(if (done) Green else Red))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (done) .52f else 1f))
            Text(task.detail, fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(36.dp).background(if (done) Green else Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun SubjectBoard(progress: Map<String, Float>) {
    Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().background(Color.White, CutCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUBJECT STATUS", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("MASTER PLAN", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        progress.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(68.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                ProgressBar(value, Color(0xFFE6E2DA), if (name == "政治") Yellow else Red)
                Spacer(Modifier.width(8.dp))
                Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
