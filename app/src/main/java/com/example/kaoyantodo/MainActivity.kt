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

private val Ink = Color(0xFF101014)
private val Paper = Color(0xFFF4F0E8)
private val Red = Color(0xFFE51F2A)
private val Pink = Color(0xFFFF4F73)
private val Yellow = Color(0xFFFFD83D)
private val Muted = Color(0xFF6D6870)

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
                TodoTask("$d-fixed-2", "09:00", "所有：网课学习", "DAILY / CORE"),
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
    val completion: (TodoTask) -> Boolean = { task -> prefs.getBoolean("${selected.day}:${task.id}", false) }
    val doneTasks = selected.tasks.filter(completion)
    val pendingTasks = selected.tasks.filterNot(completion)
    val orderedTasks = pendingTasks + doneTasks
    val overall = remember(refresh) {
        val all = days.flatMap { it.tasks }
        if (all.isEmpty()) 0f else all.count { prefs.getBoolean("${days.firstOrNull { d -> d.tasks.contains(it) }?.day}:${it.id}", false) }.toFloat() / all.size
    }
    val subjectProgress = remember(refresh) {
        val all = days.flatMap { it.tasks }
        listOf("英语", "315化学", "415生理", "政治").associateWith { subject ->
            val subset = all.filter { subjectOf(it.title) == subject }
            if (subset.isEmpty()) 0f else subset.count { task ->
                days.any { d -> d.tasks.any { it.id == task.id } && prefs.getBoolean("${d.day}:${task.id}", false) }
            }.toFloat() / subset.size
        }
    }
    val completed = doneTasks.size
    val progress = if (selected.tasks.isEmpty()) 0f else completed.toFloat() / selected.tasks.size

    LaunchedEffect(selected.day, completed, refresh) {
        prefs.edit().putInt("widget_day", selected.day).putString("widget_date", "${selected.date.monthValue}月${selected.date.dayOfMonth}日").apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink, primary = Red)) {
        Box(Modifier.fillMaxSize().background(Paper)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { HeroHeader(selected, overall, subjectProgress) }
                item {
                    DayNavigator(selected, today, selectedIndex, days.lastIndex,
                        onPrev = { if (selectedIndex > 0) selectedIndex-- },
                        onNext = { if (selectedIndex < days.lastIndex) selectedIndex++ })
                }
                item {
                    Text("TODAY / 任务队列", Modifier.padding(horizontal = 18.dp), fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink)
                }
                items(orderedTasks, key = { it.id }) { task ->
                    val done = completion(task)
                    TaskCard(task, done) {
                        prefs.edit().putBoolean("${selected.day}:${task.id}", !done).apply()
                        refresh++
                    }
                }
                item { SubjectBoard(subjectProgress) }
            }
        }
    }
}

@Composable
private fun HeroHeader(selected: StudyDay, overall: Float, subjectProgress: Map<String, Float>) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("PERSONA / STUDY MODE", color = Yellow, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("DAY ${selected.day}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text("${selected.date.monthValue}.${selected.date.dayOfMonth}  //  今日作战计划", color = Color.White.copy(.72f), fontSize = 13.sp)
            }
            Box(Modifier.size(78.dp).background(Red, CutCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("${(overall * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("OVERALL PROGRESS", color = Color.White.copy(.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        ProgressBar(overall, Color.White, Red)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            subjectProgress.forEach { (name, p) ->
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    ProgressBar(p, Color.White.copy(.18f), if (name == "政治") Yellow else Pink)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Float, track: Color, fill: Color) {
    Box(Modifier.fillMaxWidth().height(7.dp).background(track, RoundedCornerShape(2.dp))) {
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
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).background(if (done) Color(0xFFE1DDD4) else Color.White, CutCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomEnd = 2.dp, bottomStart = 16.dp)).clickable { onToggle() }.padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(52.dp)) {
            Text(task.time, color = Red, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(if (done) "DONE" else "TODO", color = if (done) Color(0xFF16804A) else Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.width(4.dp).height(45.dp).background(if (done) Color(0xFF16804A) else Red))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (done) .48f else 1f))
            Text(task.detail, fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(34.dp).background(if (done) Color(0xFF16804A) else Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun SubjectBoard(progress: Map<String, Float>) {
    Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().background(Color.White, CutCornerShape(14.dp)).padding(16.dp)) {
        Text("SUBJECT STATUS", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        progress.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(68.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                ProgressBar(value, Color(0xFFE6E2DA), Red)
                Spacer(Modifier.width(8.dp))
                Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
