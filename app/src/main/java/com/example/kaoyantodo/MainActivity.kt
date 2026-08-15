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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

private val Ink = Color(0xFF08090D)
private val Paper = Color(0xFFF3EEE4)
private val Red = Color(0xFFE51E2A)
private val Pink = Color(0xFFFF477E)
private val Yellow = Color(0xFFFFD84D)
private val Green = Color(0xFF10A968)
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
    private val scheduleStart = LocalDate.of(2026, 8, 15)

    fun load(context: Context): List<StudyDay> {
        cache?.let { return it }
        val raw = context.assets.open("plan.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONArray(raw)
        val matrix = root.getJSONObject(0).getJSONArray("tasks")
        val dayTasks = linkedMapOf<Int, MutableList<TodoTask>>()
        var maxDay = 1
        var maxCols = 0
        for (r in 0 until matrix.length()) maxCols = maxOf(maxCols, matrix.getJSONArray(r).length())

        for (c in 1 until maxCols) {
            var currentDay = -1
            for (r in 0 until matrix.length()) {
                val row = matrix.getJSONArray(r)
                if (c >= row.length()) continue
                val value = row.optString(c, "").trim()
                if (value.isEmpty()) continue
                val marker = Regex("Day\\s*(\\d+)\\s*[｜|]\\s*(\\d+)月(\\d+)日").find(value)
                if (marker != null) {
                    currentDay = marker.groupValues[1].toInt()
                    maxDay = maxOf(maxDay, currentDay)
                    dayTasks.getOrPut(currentDay) { mutableListOf() }
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
                    dayTasks.getOrPut(currentDay) { mutableListOf() }.add(TodoTask("$currentDay-$c-$r", time, value))
                }
            }
        }

        cache = (1..maxDay).map { d ->
            val fixed = listOf(
                TodoTask("$d-fixed-1", "07:30", "英语：背单词", "DAILY / START"),
                TodoTask("$d-fixed-2", "09:00", "全科：网课学习", "DAILY / CORE"),
                TodoTask("$d-fixed-3", "22:30", "晚间复盘 15–20 分钟", "DAILY / REVIEW")
            )
            StudyDay(d, scheduleStart.plusDays((d - 1).toLong()), fixed + dayTasks[d].orEmpty().distinctBy { it.title })
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
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    val initial = remember { days.indexOfFirst { it.date == today }.takeIf { it >= 0 } ?: 0 }
    var index by remember { mutableIntStateOf(initial) }
    var direction by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    val allTasks = days.flatMap { d -> d.tasks.map { d to it } }
    fun isDone(day: StudyDay, task: TodoTask) = prefs.getBoolean("${day.day}:${task.id}", false)
    val overall = if (allTasks.isEmpty()) 0f else allTasks.count { isDone(it.first, it.second) }.toFloat() / allTasks.size
    val subjects = listOf("英语", "315化学", "415生理生化", "政治")
    val subjectProgress = subjects.associateWith { s ->
        val subset = allTasks.filter { subjectOf(it.second.title) == s }
        if (subset.isEmpty()) 0f else subset.count { isDone(it.first, it.second) }.toFloat() / subset.size
    }

    LaunchedEffect(index, refresh) {
        val d = days[index]
        prefs.edit().putInt("widget_day", d.day).putString("widget_date", "${d.date.monthValue}月${d.date.dayOfMonth}日").apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink, primary = Red)) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val enter = if (direction > 0) {
                    slideInHorizontally({ it }, tween(560, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(300)) + scaleIn(initialScale = 0.965f, animationSpec = tween(560, easing = FastOutSlowInEasing))
                } else {
                    slideInHorizontally({ -it }, tween(560, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(300)) + scaleIn(initialScale = 0.965f, animationSpec = tween(560, easing = FastOutSlowInEasing))
                }
                val exit = if (direction > 0) {
                    slideOutHorizontally({ -it / 5 }, tween(560, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(240)) + scaleOut(targetScale = 0.985f, animationSpec = tween(560, easing = FastOutSlowInEasing))
                } else {
                    slideOutHorizontally({ it / 5 }, tween(560, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(240)) + scaleOut(targetScale = 0.985f, animationSpec = tween(560, easing = FastOutSlowInEasing))
                }
                ContentTransform(enter, exit)
            },
            label = "cinematicDayTransition"
        ) { animatedIndex ->
            val day = days[animatedIndex]
            val completed = day.tasks.count { isDone(day, it) }
            val dayTasks = day.tasks.sortedWith(compareBy<TodoTask> { isDone(day, it) }.thenBy { it.time })
            DayScreen(
                day = day,
                today = today,
                index = animatedIndex,
                lastIndex = days.lastIndex,
                completed = completed,
                tasks = dayTasks,
                overall = overall,
                subjectProgress = subjectProgress,
                isDone = { t -> isDone(day, t) },
                onPrev = { if (index > 0) { direction = -1; index-- } },
                onNext = { if (index < days.lastIndex) { direction = 1; index++ } },
                onToggle = { t, done -> prefs.edit().putBoolean("${day.day}:${t.id}", !done).apply(); refresh++ }
            )
        }
    }
}

@Composable
private fun DayScreen(day: StudyDay, today: LocalDate, index: Int, lastIndex: Int, completed: Int, tasks: List<TodoTask>, overall: Float, subjectProgress: Map<String, Float>, isDone: (TodoTask) -> Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToggle: (TodoTask, Boolean) -> Unit) {
    val dayProgress = if (day.tasks.isEmpty()) 0f else completed.toFloat() / day.tasks.size
    LazyColumn(Modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HeroHeader(day, overall, subjectProgress) }
        item { Navigator(day, today, index, lastIndex, onPrev, onNext) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY / 任务队列", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("${day.date.year} · ${day.date.monthValue}月${day.date.dayOfMonth}日 · DAY ${day.day}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("${(dayProgress * 100).toInt()}%", color = Red, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
        items(tasks, key = { it.id }) { task ->
            val done = isDone(task)
            TaskCard(task, done) { onToggle(task, done) }
        }
        item { SubjectBoard(subjectProgress) }
        item { Text("完成任务会自动沉底 · 左右切换采用电影式连续转场", Modifier.padding(horizontal = 18.dp), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HeroHeader(day: StudyDay, overall: Float, subjectProgress: Map<String, Float>) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("PERSONA / STUDY MODE", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("DAY ${day.day}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text("${day.date.monthValue}.${day.date.dayOfMonth}  //  今日作战计划", color = Color.White.copy(.72f), fontSize = 13.sp)
            }
            Box(Modifier.size(84.dp).background(Red, CutCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text("${(overall * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("EXAMINATION MASTER PROGRESS", color = Color.White.copy(.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        ProgressBar(overall, Color.White.copy(.16f), Red, 8.dp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            subjectProgress.forEach { (name, p) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color.White, fontSize = 8.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${(p * 100).toInt()}%", color = Color.White.copy(.7f), fontSize = 8.sp)
                    }
                    ProgressBar(p, Color.White.copy(.12f), if (name == "政治") Yellow else Pink, 5.dp)
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
private fun Navigator(day: StudyDay, today: LocalDate, index: Int, lastIndex: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (day.date == today) "TODAY" else "PLAN", color = Red, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text("DAY ${day.day}  ·  ${DateTimeFormatter.ofPattern("yyyy / MM / dd").format(day.date)}", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        TextButton(enabled = index > 0, onClick = onPrev) { Icon(Icons.Default.ArrowBack, null); Text(" PREV") }
        TextButton(enabled = index < lastIndex, onClick = onNext) { Text("NEXT "); Icon(Icons.Default.ArrowForward, null) }
    }
}

@Composable
private fun TaskCard(task: TodoTask, done: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).background(if (done) Color(0xFFE1DDD4) else Color.White, CutCornerShape(topStart = 2.dp, topEnd = 18.dp, bottomEnd = 2.dp, bottomStart = 18.dp)).clickable { onToggle() }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(55.dp)) {
            Text(task.time, color = Red, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(if (done) "COMPLETE" else "TODO", color = if (done) Green else Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.width(4.dp).height(49.dp).background(if (done) Green else Red))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (done) .48f else 1f))
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
                Text(name, Modifier.width(94.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                ProgressBar(value, Color(0xFFE6E2DA), if (name == "政治") Yellow else Red)
                Spacer(Modifier.width(8.dp))
                Text("${(value * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
