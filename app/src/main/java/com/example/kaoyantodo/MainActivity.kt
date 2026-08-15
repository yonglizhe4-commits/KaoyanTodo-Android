package com.example.kaoyantodo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val Ink = Color(0xFF0A0A0F)
private val Paper = Color(0xFFF3EFE7)
private val Red = Color(0xFFE3192B)
private val Pink = Color(0xFFFF4D7D)
private val Yellow = Color(0xFFFFD43B)
private val Green = Color(0xFF18A96B)
private val Muted = Color(0xFF746F76)
private val Card = Color(0xFFFFFCF7)

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

        val explicitDates = mutableMapOf<Int, LocalDate>()
        val taskBuckets = mutableMapOf<Int, MutableList<TodoTask>>()
        var maxCols = 0

        for (r in 0 until matrix.length()) {
            maxCols = maxOf(maxCols, matrix.getJSONArray(r).length())
        }

        for (c in 1 until maxCols) {
            var currentDay = -1
            for (r in 0 until matrix.length()) {
                val row = matrix.getJSONArray(r)
                if (c >= row.length()) continue
                val value = row.optString(c, "").trim()
                if (value.isEmpty()) continue

                val marker = Regex("^Day\\s*(\\d+)\\s*[｜|]\\s*(\\d+)月(\\d+)日$").find(value)
                if (marker != null) {
                    currentDay = marker.groupValues[1].toInt()
                    val month = marker.groupValues[2].toInt()
                    val dayOfMonth = marker.groupValues[3].toInt()
                    explicitDates[currentDay] = LocalDate.of(2026, month, dayOfMonth)
                    taskBuckets.getOrPut(currentDay) { mutableListOf() }
                    continue
                }

                if (currentDay > 0 && !value.startsWith("Day ")) {
                    val time = when {
                        value.startsWith("英语") || value.contains("长难句") -> "10:30"
                        value.startsWith("315") || value.contains("化学") -> "14:00"
                        value.startsWith("415") || value.contains("生理") || value.contains("生化") -> "16:30"
                        value.startsWith("政治") -> "19:00"
                        value.contains("晚上") || value.contains("晚间") || value.contains("复盘") -> "22:00"
                        else -> "20:00"
                    }
                    taskBuckets.getOrPut(currentDay) { mutableListOf() }
                        .add(TodoTask("$currentDay-$c-$r", time, value))
                }
            }
        }

        val maxDay = explicitDates.keys.maxOrNull() ?: 128
        val anchor = LocalDate.of(2026, 8, 15)

        cache = (1..maxDay).map { d ->
            val expectedDate = anchor.plusDays((d - 1).toLong())
            val fixed = listOf(
                TodoTask("$d-default-1", "07:30", "英语：背单词", "DAILY / START"),
                TodoTask("$d-default-2", "09:00", "全科：网课学习", "DAILY / CORE"),
                TodoTask("$d-default-3", "22:30", "晚间复盘 15–20 分钟", "DAILY / REVIEW")
            )
            StudyDay(
                day = d,
                date = expectedDate,
                tasks = (fixed + taskBuckets[d].orEmpty()).distinctBy { it.title }
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
private fun KaoyanApp(context: Context) {
    val days = remember { PlanRepository.load(context) }
    val today = LocalDate.now()
    val startIndex = remember {
        days.indexOfFirst { it.date == today }.takeIf { it >= 0 }
            ?: days.indexOfFirst { it.date.isAfter(today) }.takeIf { it >= 0 }
            ?: 0
    }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { days.size })
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }

    fun done(day: StudyDay, task: TodoTask) = prefs.getBoolean("${day.day}:${task.id}", false)

    val allTasks = remember(days, refresh) { days.flatMap { d -> d.tasks.map { d to it } } }
    val overall = remember(days, refresh) {
        if (allTasks.isEmpty()) 0f else allTasks.count { done(it.first, it.second) }.toFloat() / allTasks.size
    }
    val subjectProgress = remember(days, refresh) {
        listOf("英语", "315化学", "415生理生化", "政治").associateWith { subject ->
            val subset = allTasks.filter { subjectOf(it.second.title) == subject }
            if (subset.isEmpty()) 0f else subset.count { done(it.first, it.second) }.toFloat() / subset.size
        }
    }

    LaunchedEffect(pagerState.currentPage, refresh) {
        val d = days[pagerState.currentPage]
        prefs.edit()
            .putInt("widget_day", d.day)
            .putString("widget_date", d.date.toString())
            .apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Paper,
            surface = Paper,
            onBackground = Ink,
            onSurface = Ink,
            primary = Red
        )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = true,
            beyondViewportPageCount = 1
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val day = days[page]
            val completed = day.tasks.count { done(day, it) }
            val ordered = day.tasks.filterNot { done(day, it) } + day.tasks.filter { done(day, it) }

            DayScreen(
                day = day,
                today = today,
                pageOffset = pageOffset,
                completed = completed,
                overall = overall,
                subjectProgress = subjectProgress,
                orderedTasks = ordered,
                done = { t -> done(day, t) },
                onToggle = { task ->
                    val old = done(day, task)
                    prefs.edit().putBoolean("${day.day}:${task.id}", !old).apply()
                    refresh++
                },
                onPrev = {
                    if (pagerState.currentPage > 0) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage - 1,
                                animationSpec = tween(520, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                },
                onNext = {
                    if (pagerState.currentPage < days.lastIndex) {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = tween(520, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DayScreen(
    day: StudyDay,
    today: LocalDate,
    pageOffset: Float,
    completed: Int,
    overall: Float,
    subjectProgress: Map<String, Float>,
    orderedTasks: List<TodoTask>,
    done: (TodoTask) -> Boolean,
    onToggle: (TodoTask) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val dayProgress = if (day.tasks.isEmpty()) 0f else completed.toFloat() / day.tasks.size
    val distance = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(0.965f, 1f, 1f - distance)
    val alpha = lerp(0.72f, 1f, 1f - distance)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(Paper),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ArtHeader(day, overall, subjectProgress, pageOffset) }
        item { DateRibbon(day, today, day.day > 1, day.day < 128, onPrev, onNext) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TODAY / MISSION QUEUE",
                        color = Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        "DAY ${day.day}  ·  ${day.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text("${(dayProgress * 100).toInt()}%", color = Red, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        items(orderedTasks, key = { it.id }) { task ->
            TaskCard(task, done(task), onToggle = { onToggle(task) })
        }
        item { SubjectBoard(subjectProgress) }
    }
}

@Composable
private fun ArtHeader(
    day: StudyDay,
    overall: Float,
    subjectProgress: Map<String, Float>,
    pageOffset: Float
) {
    val parallax = pageOffset.coerceIn(-1f, 1f) * 40f
    Box(
        Modifier
            .fillMaxWidth()
            .height(235.dp)
            .background(Ink)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .offset(x = parallax.dp)
        ) {
            drawRect(
                color = Red,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.73f, -40f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 1.2f)
            )
            drawRect(
                color = Yellow,
                topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.12f, size.height * 0.72f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.56f, 22f)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("THE MASTER PLAN", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("DAY ${day.day}", color = Color.White, fontSize = 47.sp, fontWeight = FontWeight.Black, letterSpacing = (-2.5).sp)
                    Text(
                        "${day.date.monthValue}.${day.date.dayOfMonth}  /  ${day.date.dayOfWeek.name.take(3)}",
                        color = Color.White.copy(.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .size(88.dp)
                        .background(Red, CutCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(overall * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("MASTER", color = Color.White.copy(.72f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(17.dp))
            Text("EXAMINATION PROGRESS", color = Color.White.copy(.52f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(5.dp))
            ProgressBar(overall, Color.White.copy(.16f), Red, 8.dp)

            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                subjectProgress.forEach { (name, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontSize = 8.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        ProgressBar(value, Color.White.copy(.12f), if (name == "政治") Yellow else Pink, 5.dp)
                        Text("${(value * 100).toInt()}%", color = Color.White.copy(.68f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRibbon(
    day: StudyDay,
    today: LocalDate,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (day.date == today) "TODAY" else "PLAN DATE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text(day.date.format(DateTimeFormatter.ofPattern("yyyy / MM / dd")), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        IconButton(enabled = canPrev, onClick = onPrev) {
            Icon(Icons.Default.ArrowBack, null, tint = if (canPrev) Ink else Muted.copy(.25f))
        }
        Text("SWIPE", modifier = Modifier.padding(horizontal = 3.dp), color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        IconButton(enabled = canNext, onClick = onNext) {
            Icon(Icons.Default.ArrowForward, null, tint = if (canNext) Ink else Muted.copy(.25f))
        }
    }
}

@Composable
private fun ProgressBar(value: Float, track: Color, fill: Color, height: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(Modifier.fillMaxWidth().height(height).background(track, RoundedCornerShape(3.dp))) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fill, RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun TaskCard(task: TodoTask, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .background(
                if (done) Color(0xFFE2DED6) else Card,
                CutCornerShape(topStart = 2.dp, topEnd = 18.dp, bottomEnd = 2.dp, bottomStart = 18.dp)
            )
            .clickable { onToggle() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(58.dp)) {
            Text(task.time, color = Red, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(if (done) "DONE" else "TODO", color = if (done) Green else Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.width(4.dp).height(50.dp).background(if (done) Green else Red))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (done) .46f else 1f))
            if (task.detail.isNotEmpty()) Text(task.detail, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(36.dp).background(if (done) Green else Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SubjectBoard(progress: Map<String, Float>) {
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .background(Card, CutCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUBJECT STATUS", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("TRACK ALL", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        progress.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(94.dp), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ProgressBar(value, Color(0xFFE7E1D8), if (name == "政治") Yellow else Red, 6.dp)
                Spacer(Modifier.width(8.dp))
                Text("${(value * 100).toInt()}%", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
