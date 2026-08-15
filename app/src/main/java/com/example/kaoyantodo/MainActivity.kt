package com.example.kaoyantodo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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

private val Ink = Color(0xFF0B0B10)
private val Paper = Color(0xFFF2EEE6)
private val Red = Color(0xFFE51F2A)
private val Pink = Color(0xFFFF477E)
private val Yellow = Color(0xFFFFD83D)
private val Green = Color(0xFF13A866)
private val Muted = Color(0xFF777078)

fun subjectOf(title: String): String = when {
    title.startsWith("英语") || title.contains("单词") || title.contains("阅读") || title.contains("写作") -> "英语"
    title.startsWith("315") || title.contains("化学") -> "315化学"
    title.startsWith("415") || title.contains("生理") || title.contains("生化") -> "415生理生化"
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
        var maxCols = 0
        for (r in 0 until matrix.length()) {
            maxCols = maxOf(maxCols, matrix.getJSONArray(r).length())
        }

        // Walk every populated Excel column. Every "Day X｜M月D日" cell is the authoritative
        // boundary for the following tasks in that column, so no days are skipped or fabricated.
        for (c in 1 until maxCols) {
            var currentDay = -1
            for (r in 0 until matrix.length()) {
                val row = matrix.getJSONArray(r)
                if (c >= row.length()) continue
                val value = row.optString(c, "").trim()
                if (value.isEmpty()) continue

                val dayMatch = Regex("Day\\s*(\\d+)\\s*[｜|]\\s*(\\d+)月(\\d+)日").find(value)
                if (dayMatch != null) {
                    currentDay = dayMatch.groupValues[1].toInt()
                    dates[currentDay] = LocalDate.of(2026, dayMatch.groupValues[2].toInt(), dayMatch.groupValues[3].toInt())
                    days.getOrPut(currentDay) { mutableListOf() }
                    continue
                }

                if (currentDay > 0) {
                    val time = when {
                        value.startsWith("英语") -> "10:30"
                        value.startsWith("315") -> "14:00"
                        value.startsWith("415") -> "16:00"
                        value.startsWith("政治") -> "19:00"
                        value.contains("晚上") || value.contains("晚间") -> "22:00"
                        else -> "20:00"
                    }
                    days.getOrPut(currentDay) { mutableListOf() }
                        .add(TodoTask("$currentDay-$c-$r", time, value))
                }
            }
        }

        val sortedDays = days.keys.sorted()
        cache = sortedDays.map { d ->
            val fixed = listOf(
                TodoTask("$d-fixed-1", "07:30", "英语：背单词", "DAILY / START"),
                TodoTask("$d-fixed-2", "09:00", "全科：网课学习", "DAILY / CORE"),
                TodoTask("$d-fixed-3", "22:30", "晚间复盘 15–20 分钟", "DAILY / REVIEW")
            )
            StudyDay(
                day = d,
                date = dates.getValue(d),
                tasks = fixed + days.getValue(d).distinctBy { it.title }
            )
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
        days.indexOfFirst { it.date == today }.takeIf { it >= 0 }
            ?: days.indexOfFirst { it.date.isAfter(today) }.takeIf { it >= 0 }
            ?: days.lastIndex.coerceAtLeast(0)
    }
    var selectedIndex by remember { mutableIntStateOf(initial) }
    var navigationDirection by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    val selected = days[selectedIndex]
    val isDone: (StudyDay, TodoTask) -> Boolean = { day, task -> prefs.getBoolean("${day.day}:${task.id}", false) }
    val doneTasks = selected.tasks.filter { isDone(selected, it) }
    val pendingTasks = selected.tasks.filterNot { isDone(selected, it) }
    val orderedTasks = pendingTasks + doneTasks
    val allTasks = days.flatMap { d -> d.tasks.map { d to it } }
    val overall = if (allTasks.isEmpty()) 0f else allTasks.count { isDone(it.first, it.second) }.toFloat() / allTasks.size
    val subjectProgress = listOf("英语", "315化学", "415生理生化", "政治").associateWith { subject ->
        val subset = allTasks.filter { subjectOf(it.second.title) == subject }
        if (subset.isEmpty()) 0f else subset.count { isDone(it.first, it.second) }.toFloat() / subset.size
    }

    LaunchedEffect(selected.day, doneTasks.size, refresh) {
        prefs.edit()
            .putInt("widget_day", selected.day)
            .putString("widget_date", "${selected.date.monthValue}月${selected.date.dayOfMonth}日")
            .apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink, primary = Red)) {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                val enter = if (navigationDirection > 0) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(420, easing = FastOutSlowInEasing)) + fadeIn(tween(220))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(420, easing = FastOutSlowInEasing)) + fadeIn(tween(220))
                }
                val exit = if (navigationDirection > 0) {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(420, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                } else {
                    slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(420, easing = FastOutSlowInEasing)) + fadeOut(tween(180))
                }
                ContentTransform(enter, exit)
            },
            label = "studyDayTransition"
        ) { animatedIndex ->
            val day = days[animatedIndex]
            StudyDayPage(
                context = context,
                day = day,
                today = today,
                index = animatedIndex,
                lastIndex = days.lastIndex,
                allDays = days,
                isDone = isDone,
                overall = overall,
                subjectProgress = subjectProgress,
                orderedTasks = if (animatedIndex == selectedIndex) orderedTasks else day.tasks,
                onPrev = {
                    if (selectedIndex > 0) {
                        navigationDirection = -1
                        selectedIndex--
                    }
                },
                onNext = {
                    if (selectedIndex < days.lastIndex) {
                        navigationDirection = 1
                        selectedIndex++
                    }
                },
                onToggle = { task, done ->
                    prefs.edit().putBoolean("${day.day}:${task.id}", !done).apply()
                    refresh++
                }
            )
        }
    }
}

@Composable
private fun StudyDayPage(
    context: Context,
    day: StudyDay,
    today: LocalDate,
    index: Int,
    lastIndex: Int,
    allDays: List<StudyDay>,
    isDone: (StudyDay, TodoTask) -> Boolean,
    overall: Float,
    subjectProgress: Map<String, Float>,
    orderedTasks: List<TodoTask>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggle: (TodoTask, Boolean) -> Unit
) {
    val completed = day.tasks.count { isDone(day, it) }
    val dayProgress = if (day.tasks.isEmpty()) 0f else completed.toFloat() / day.tasks.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroHeader(day, overall, subjectProgress) }
        item {
            DayNavigator(day, today, index, lastIndex, onPrev, onNext)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY / 任务队列", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("${day.date.year} · ${day.date.monthValue}月${day.date.dayOfMonth}日 · DAY ${day.day}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("${(dayProgress * 100).toInt()}%", color = Red, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
        items(orderedTasks, key = { it.id }) { task ->
            val done = isDone(day, task)
            TaskCard(task, done) { onToggle(task, done) }
        }
        item { SubjectBoard(subjectProgress) }
        item {
            Text("✓ 完成后自动沉底 · 页面切换采用连贯滑动转场", modifier = Modifier.padding(horizontal = 18.dp), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun DayNavigator(selected: StudyDay, today: LocalDate, index: Int, lastIndex: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (selected.date == today) "TODAY" else "PLAN", color = Red, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text(DateTimeFormatter.ofPattern("yyyy / MM / dd").format(selected.date), color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        TextButton(enabled = index > 0, onClick = onPrev) { Icon(Icons.Default.ArrowBack, null); Text(" PREV") }
        TextButton(enabled = index < lastIndex, onClick = onNext) { Text("NEXT "); Icon(Icons.Default.ArrowForward, null) }
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
                Text(name, Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                ProgressBar(value, Color(0xFFE6E2DA), if (name == "政治") Yellow else Red)
                Spacer(Modifier.width(8.dp))
                Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
