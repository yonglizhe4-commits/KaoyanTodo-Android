package com.example.kaoyantodo

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

data class FixedTodoTask(val id: String, val time: String, val title: String, val subject: String)
data class FixedStudyDay(val day: Int, val date: LocalDate, val tasks: List<FixedTodoTask>)

object FixedPlanRepository {
    private var cache: List<FixedStudyDay>? = null
    private val anchor = LocalDate.of(2026, 8, 15)

    fun load(context: Context): List<FixedStudyDay> {
        cache?.let { return it }
        val raw = context.assets.open("plan.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONArray(raw)
        val matrix = root.getJSONObject(0).getJSONArray("tasks")
        val buckets = mutableMapOf<Int, MutableList<FixedTodoTask>>()
        var maxCols = 0
        for (r in 0 until matrix.length()) maxCols = maxOf(maxCols, matrix.getJSONArray(r).length())

        // The Excel export is compacted: empty cells are omitted, but the non-empty
        // phase columns remain in stable positions. Parse each compact column independently.
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
                    buckets.getOrPut(currentDay) { mutableListOf() }
                    continue
                }
                if (currentDay > 0 && !value.startsWith("Day ")) {
                    val title = normalize415(value)
                    buckets.getOrPut(currentDay) { mutableListOf() }
                        .add(
                            FixedTodoTask(
                                id = "$currentDay-$c-$r",
                                time = timeFor(title),
                                title = title,
                                subject = subjectFor(title)
                            )
                        )
                }
            }
        }

        // Always expose a strict continuous Day 1..128 sequence.
        val days = (1..128).map { day ->
            val fixed = listOf(
                FixedTodoTask("$day-default-word", "07:30", "英语：背单词", "英语"),
                FixedTodoTask("$day-default-course", "09:00", "全科：网课学习", "全科"),
                FixedTodoTask("$day-default-review", "22:30", "晚间复盘 15–20 分钟", "全科")
            )
            val tasks = (fixed + buckets[day].orEmpty())
                .distinctBy { it.title }
                .sortedWith(compareBy<FixedTodoTask> { it.time }.thenBy { it.id })
            FixedStudyDay(day, anchor.plusDays((day - 1).toLong()), tasks)
        }

        cache = days
        return days
    }

    private fun normalize415(text: String): String {
        return when {
            text.startsWith("415生理：") -> text.replaceFirst("415生理：", "415生理生化：")
            text.startsWith("415：") -> text.replaceFirst("415：", "415生理生化：")
            text.startsWith("415生化：") -> text
            else -> text
        }
    }

    private fun subjectFor(text: String): String = when {
        text.startsWith("英语") || text.contains("单词") || text.contains("阅读") || text.contains("写作") -> "英语"
        text.startsWith("315") || text.contains("化学") -> "315化学"
        text.startsWith("415") || text.contains("生理") || text.contains("生化") -> "415生理生化"
        text.startsWith("政治") || text.contains("政治") -> "政治"
        else -> "其他"
    }

    private fun timeFor(text: String): String = when {
        text.startsWith("英语") || text.contains("长难句") -> "10:30"
        text.startsWith("315") || text.contains("化学") -> "14:00"
        text.startsWith("415") || text.contains("生理") || text.contains("生化") -> "16:30"
        text.startsWith("政治") -> "19:00"
        text.contains("晚上") || text.contains("晚间") || text.contains("复盘") -> "22:00"
        else -> "20:00"
    }
}