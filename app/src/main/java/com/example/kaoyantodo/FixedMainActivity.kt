package com.example.kaoyantodo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val Ink = Color(0xFF090A0F)
private val Paper = Color(0xFFF3EFE7)
private val Card = Color(0xFFFFFCF7)
private val Red = Color(0xFFE3192B)
private val Pink = Color(0xFFFF4D7D)
private val Yellow = Color(0xFFFFD43B)
private val Green = Color(0xFF18A96B)
private val Muted = Color(0xFF746F76)

class FixedMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FixedTodoApp(this) }
    }
}

@Composable
private fun FixedTodoApp(context: Context) {
    val days = remember { FixedPlanRepository.load(context) }
    val today = LocalDate.now()
    val start = days.indexOfFirst { it.date == today }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = start, pageCount = { days.size })
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("todo_state", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }

    fun done(day: FixedStudyDay, task: FixedTodoTask) = prefs.getBoolean("${day.day}:${task.id}", false)

    val allTasks = remember(days, refresh) { days.flatMap { d -> d.tasks.map { d to it } } }
    val overall = remember(days, refresh) {
        if (allTasks.isEmpty()) 0f else allTasks.count { done(it.first, it.second) }.toFloat() / allTasks.size
    }
    val subjects = listOf("英语", "315化学", "415生理生化", "政治")
    val subjectProgress = remember(days, refresh) {
        subjects.associateWith { subject ->
            val subset = allTasks.filter { it.second.subject == subject }
            if (subset.isEmpty()) 0f else subset.count { done(it.first, it.second) }.toFloat() / subset.size
        }
    }

    LaunchedEffect(pagerState.currentPage, refresh) {
        val d = days[pagerState.currentPage]
        prefs.edit().putInt("widget_day", d.day).putString("widget_date", d.date.toString()).apply()
        TodoWidgetProvider.updateAll(context)
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink, primary = Red)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            pageSpacing = 0.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1)
            )
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val day = days[page]
            val doneCount = day.tasks.count { done(day, it) }
            val ordered = day.tasks.filterNot { done(day, it) } + day.tasks.filter { done(day, it) }
            DayPage(
                day = day,
                today = today,
                pageOffset = pageOffset,
                doneCount = doneCount,
                overall = overall,
                subjectProgress = subjectProgress,
                tasks = ordered,
                isDone = { done(day, it) },
                onToggle = { task ->
                    val old = done(day, task)
                    prefs.edit().putBoolean("${day.day}:${task.id}", !old).apply()
                    refresh++
                },
                onPrev = {
                    if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1, tween(520, easing = FastOutSlowInEasing)) }
                },
                onNext = {
                    if (pagerState.currentPage < days.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1, tween(520, easing = FastOutSlowInEasing)) }
                }
            )
        }
    }
}

@Composable
private fun DayPage(day: FixedStudyDay, today: LocalDate, pageOffset: Float, doneCount: Int, overall: Float, subjectProgress: Map<String, Float>, tasks: List<FixedTodoTask>, isDone: (FixedTodoTask) -> Boolean, onToggle: (FixedTodoTask) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    val dayProgress = if (day.tasks.isEmpty()) 0f else doneCount.toFloat() / day.tasks.size
    val distance = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(0.965f, 1f, 1f - distance)
    val alpha = lerp(0.72f, 1f, 1f - distance)

    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.background(Paper),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ArtHeader(day, overall, subjectProgress, pageOffset) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (day.date == today) "TODAY" else "PLAN DATE", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(day.date.format(DateTimeFormatter.ofPattern("yyyy / MM / dd")), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                IconButton(enabled = day.day > 1, onClick = onPrev) { Icon(Icons.Default.ArrowBack, null, tint = if (day.day > 1) Ink else Muted.copy(alpha = .2f)) }
                Text("DAY ${day.day}", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                IconButton(enabled = day.day < 128, onClick = onNext) { Icon(Icons.Default.ArrowForward, null, tint = if (day.day < 128) Ink else Muted.copy(alpha = .2f)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY / MISSION QUEUE", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Text("${doneCount}/${day.tasks.size} tasks complete", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("${(dayProgress * 100).toInt()}%", color = Red, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        items(tasks, key = { it.id }) { task ->
            TaskCard(task, isDone(task)) { onToggle(task) }
        }
        item { SubjectBoard(subjectProgress) }
    }
}

@Composable
private fun ArtHeader(day: FixedStudyDay, overall: Float, subjectProgress: Map<String, Float>, pageOffset: Float) {
    val parallax = pageOffset.coerceIn(-1f, 1f)
    Box(Modifier.fillMaxWidth().height(238.dp).background(Ink)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = parallax * 55f; rotationZ = parallax * 1.8f }.background(Red.copy(alpha = .95f)).padding(start = 250.dp, top = 0.dp))
        Box(Modifier.fillMaxWidth().height(18.dp).align(Alignment.BottomCenter).graphicsLayer { translationX = -parallax * 30f }.background(Yellow))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("THE MASTER PLAN", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("DAY ${day.day}", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-2.7).sp)
                    Text("${day.date.monthValue}.${day.date.dayOfMonth}  /  ${day.date.dayOfWeek.name.take(3)}", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(88.dp).background(Red, CutCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("${(overall * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("EXAMINATION MASTER PROGRESS", color = Color.White.copy(alpha = .55f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(5.dp))
            ProgressBar(overall, Color.White.copy(alpha = .14f), Red, 8.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                subjectProgress.forEach { (name, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontSize = 8.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        ProgressBar(value, Color.White.copy(alpha = .12f), if (name == "政治") Yellow else Pink, 5.dp)
                        Text("${(value * 100).toInt()}%", color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: FixedTodoTask, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .background(if (done) Color(0xFFE2DED6) else Card, CutCornerShape(topStart = 2.dp, topEnd = 20.dp, bottomEnd = 2.dp, bottomStart = 20.dp))
            .clickable { onToggle() }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(55.dp)) {
            Text(task.time, color = Red, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(if (done) "DONE" else "TODO", color = if (done) Green else Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.width(4.dp).height(50.dp).background(if (done) Green else Red))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(if (done) .46f else 1f))
            Text(task.subject, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(36.dp).background(if (done) Green else Ink, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SubjectBoard(progress: Map<String, Float>) {
    Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().background(Color.White, CutCornerShape(16.dp)).padding(16.dp)) {
        Text("SUBJECT STATUS", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(10.dp))
        progress.forEach { (name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.width(86.dp), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                ProgressBar(value, Color(0xFFE8E3D9), if (name == "政治") Yellow else Pink, 6.dp, Modifier.weight(1f))
                Text("${(value * 100).toInt()}%", modifier = Modifier.width(36.dp), color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun ProgressBar(value: Float, track: Color, fill: Color, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.height(height).background(track, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(fill, RoundedCornerShape(3.dp)))
    }
}
