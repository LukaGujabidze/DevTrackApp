package com.devtracker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devtracker.data.*
import java.util.Calendar

fun longestStreak(sessions: List<Session>): Int {
    val days = sessions.map { dayStart(it.date) }.toSortedSet().toList()
    var best = 0; var run = 0; var prev = -1L
    for (d in days) { run = if (prev >= 0 && d - prev == DAY) run + 1 else 1; best = maxOf(best, run); prev = d }
    return best
}
fun firstOfMonth(ms: Long): Long = dayStart(Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
fun addMonths(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, n) }.timeInMillis
fun dayOf(monthStart: Long, d: Int): Long = dayStart(Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.DAY_OF_MONTH, d - 1) }.timeInMillis)

@Composable fun TopBar(title: String, nav: NavController) = Row(Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MilestoneRow(m: Milestone, vm: AppVm) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(m.done, { vm.toggleMilestone(m) })
        Column(Modifier.weight(1f)) {
            Text(m.title)
            TextButton({ show = true }, contentPadding = PaddingValues(0.dp)) {
                Text(if (m.done) "Done ${fmt(m.doneAt)}" else m.dueDate?.let { "Due ${fmt(it)}" } ?: "Set due date", style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton({ vm.deleteMilestone(m) }) { Icon(Icons.Default.Close, "Delete") }
    }
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = m.dueDate)
        DatePickerDialog(onDismissRequest = { show = false },
            confirmButton = { TextButton({ vm.setMilestoneDue(m, st.selectedDateMillis); show = false }) { Text("OK") } },
            dismissButton = { TextButton({ vm.setMilestoneDue(m, null); show = false }) { Text("Clear") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddEventDialog(kind: String, day: Long, vm: AppVm, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }; var mins by remember { mutableStateOf("45") }; var dom by remember { mutableIntStateOf(0) }
    var notify by remember { mutableStateOf(true) }; var daily by remember { mutableStateOf(false) }
    val ts = rememberTimePickerState(9, 0, true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (kind == "STUDY") "Schedule study session" else "New reminder") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fmt(day))
            OutlinedTextField(title, { title = it }, label = { Text(if (kind == "STUDY") "What will you study?" else "Reminder") }, singleLine = true)
            if (kind == "STUDY") {
                OutlinedTextField(mins, { mins = it.filter(Char::isDigit) }, label = { Text("Minutes") }, singleLine = true)
                Dropdown("Domain", DOMAINS, dom) { dom = it }
            }
            TimeInput(ts)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (kind == "STUDY") { Checkbox(notify, { notify = it }); Text("Notify me at this time") }
                else { Checkbox(daily, { daily = it }); Text("Repeat daily") }
            }
        } },
        confirmButton = { TextButton({
            if (title.isNotBlank()) {
                val at = day + ts.hour * 3_600_000L + ts.minute * 60_000L
                vm.addEvent(Event(title = title.trim(), date = at, kind = kind, minutes = mins.toIntOrNull() ?: 0, domain = dom,
                    notifyAt = if (kind == "REMINDER" || notify) at else null, repeatDaily = kind == "REMINDER" && daily))
                onDismiss()
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

private data class CalItem(val day: Long, val text: String, val kind: String, val route: String? = null, val event: Event? = null)

@Composable fun CalendarScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val courses by vm.courses.collectAsState()
    val ms by vm.milestones.collectAsState(); val events by vm.events.collectAsState()
    val now = System.currentTimeMillis()
    var monthStart by remember { mutableLongStateOf(firstOfMonth(now)) }
    var selected by remember { mutableLongStateOf(dayStart(now)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val items = remember(goals, courses, ms, events) {
        buildList {
            goals.filter { !it.archived && it.status != "Completed" }.forEach { g ->
                g.examDate?.let { add(CalItem(dayStart(it), "Exam: ${g.title}", "exam", "goal/${g.id}")) }
                g.targetDate?.let { add(CalItem(dayStart(it), "Target: ${g.title}", "target", "goal/${g.id}")) }
            }
            courses.filter { it.status != "Completed" }.forEach { c -> c.targetDate?.let { add(CalItem(dayStart(it), "Course target: ${c.title}", "target", "course/${c.id}")) } }
            ms.filter { !it.done }.forEach { m -> m.dueDate?.let { add(CalItem(dayStart(it), "Milestone: ${m.title}", "milestone", if (m.parentType == "GOAL") "goal/${m.parentId}" else "course/${m.parentId}")) } }
            events.forEach { e -> add(CalItem(dayStart(e.date), (if (e.kind == "STUDY") "Study: " else "Reminder: ") + e.title, e.kind, null, e)) }
        }
    }
    val byDay = items.groupBy { it.day }
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val daysIn = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val rows = (offset + daysIn + 6) / 7
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TopBar("Calendar", nav) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ monthStart = firstOfMonth(addMonths(monthStart, -1)) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
            Text(java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(monthStart)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ monthStart = firstOfMonth(addMonths(monthStart, 1)) }) { Icon(Icons.Default.ChevronRight, "Next month") }
        } }
        item { Row { (0..6).forEach { Text(names[(cal.firstDayOfWeek - 1 + it) % 7], Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } } }
        items((0 until rows).toList()) { r -> Row {
            (0..6).forEach { c ->
                val d = r * 7 + c - offset + 1
                if (d in 1..daysIn) {
                    val day = dayOf(monthStart, d)
                    val isSel = day == selected
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { selected = day }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$d", fontWeight = if (day == dayStart(now)) FontWeight.Bold else FontWeight.Normal)
                            if (byDay.containsKey(day)) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                } else Spacer(Modifier.weight(1f))
            }
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(selected), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ dialog = "STUDY" }) { Text("+ Study") }
            TextButton({ dialog = "REMINDER" }) { Text("+ Reminder") }
        } }
        val list = byDay[selected].orEmpty()
        if (list.isEmpty()) item { Text("Nothing on this day.", style = MaterialTheme.typography.bodyMedium) }
        items(list) { it ->
            val e = it.event
            ListItem(headlineContent = { Text(it.text) },
                supportingContent = if (e != null) { { Text(buildString {
                    append(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.date)))
                    if (e.kind == "STUDY") append(" • ${e.minutes} min"); if (e.repeatDaily) append(" • daily") }) } } else null,
                leadingContent = { if (e != null) Checkbox(e.done, { vm.toggleEvent(e) }, enabled = !(e.kind == "STUDY" && e.done))
                    else Icon(when (it.kind) { "exam" -> Icons.Default.School; "milestone" -> Icons.Default.Flag; else -> Icons.Default.Event }, null) },
                trailingContent = if (e != null) { { IconButton({ vm.deleteEvent(e) }) { Icon(Icons.Default.Close, "Delete") } } } else null,
                modifier = Modifier.clickable(enabled = it.route != null) { it.route?.let { r -> nav.navigate(r) } })
        }
        item { Text("Completing a scheduled study session logs its minutes to your learning stats.", style = MaterialTheme.typography.bodySmall) }
    }
    dialog?.let { AddEventDialog(it, selected, vm) { dialog = null } }
}

private data class AchBadge(val title: String, val desc: String, val earned: Boolean)
private data class TL(val date: Long, val title: String, val sub: String)

@Composable fun AchievementsScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val ms by vm.milestones.collectAsState()
    val courses by vm.courses.collectAsState(); val sessions by vm.sessions.collectAsState()
    val ratings by vm.ratings.collectAsState(); val results by vm.results.collectAsState()
    val totalH = sessions.sumOf { it.minutes } / 60
    val certs = goals.count { it.type == "CERT" && it.status == "Completed" }
    val doneCourses = courses.count { it.status == "Completed" }
    val best = streak(sessions).coerceAtLeast(longestStreak(sessions))
    val maxCf = ratings.maxOfOrNull { it.rating } ?: 0
    val badges = listOf(
        AchBadge("First step", "Complete your first milestone", ms.any { it.done }),
        AchBadge("Milestone maker", "Complete 10 milestones", ms.count { it.done } >= 10),
        AchBadge("Milestone machine", "Complete 50 milestones", ms.count { it.done } >= 50),
        AchBadge("Certified", "Earn your first certification", certs >= 1),
        AchBadge("Collector", "Earn 3 certifications", certs >= 3),
        AchBadge("Course finisher", "Finish a course", doneCourses >= 1),
        AchBadge("Course master", "Finish 10 courses", doneCourses >= 10),
        AchBadge("Domain complete", "Finish every course in one domain", (0..4).any { d -> courses.filter { it.domain == d }.let { l -> l.isNotEmpty() && l.all { it.status == "Completed" } } }),
        AchBadge("10 hours", "Log 10 hours of learning", totalH >= 10),
        AchBadge("50 hours", "Log 50 hours of learning", totalH >= 50),
        AchBadge("100 hours", "Log 100 hours of learning", totalH >= 100),
        AchBadge("500 hours", "Log 500 hours of learning", totalH >= 500),
        AchBadge("3-day streak", "Learn 3 days in a row", best >= 3),
        AchBadge("Week warrior", "Learn 7 days in a row", best >= 7),
        AchBadge("Unstoppable", "Learn 30 days in a row", best >= 30),
        AchBadge("Competitor", "Record a competition result", results.isNotEmpty()),
        AchBadge("CF 1400", "Reach Codeforces 1400", maxCf >= 1400),
        AchBadge("CF 1600", "Reach Codeforces 1600", maxCf >= 1600),
        AchBadge("CF 1900", "Reach Codeforces 1900", maxCf >= 1900),
        AchBadge("CF 2100", "Reach your Codeforces target", maxCf >= 2100)
    )
    val timeline = buildList {
        ms.filter { it.done && it.doneAt != null }.forEach { m ->
            add(TL(m.doneAt!!, "Milestone: ${m.title}", (if (m.parentType == "GOAL") goals.firstOrNull { it.id == m.parentId }?.title else courses.firstOrNull { it.id == m.parentId }?.title) ?: ""))
        }
        goals.filter { it.completedAt != null }.forEach { add(TL(it.completedAt!!, "Completed: ${it.title}", it.provider)) }
        courses.filter { it.completedAt != null }.forEach { add(TL(it.completedAt!!, "Course finished: ${it.title}", DOMAINS[it.domain])) }
        results.forEach { r -> add(TL(r.date, "${r.title}: ${r.result}", goals.firstOrNull { it.id == r.goalId }?.title ?: "")) }
    }.sortedByDescending { it.date }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { TopBar("Achievements", nav) }
        item { Text("${badges.count { it.earned }} of ${badges.size} badges earned", Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium) }
        items(badges) { b ->
            ListItem(headlineContent = { Text(b.title) }, supportingContent = { Text(b.desc) },
                leadingContent = { Icon(if (b.earned) Icons.Default.EmojiEvents else Icons.Default.Lock, null,
                    tint = if (b.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) })
        }
        item { Text("Personal timeline", Modifier.padding(8.dp, 16.dp, 8.dp, 4.dp), style = MaterialTheme.typography.titleMedium) }
        if (timeline.isEmpty()) item { Text("Completed milestones, courses, goals and results will appear here.", Modifier.padding(8.dp)) }
        items(timeline) { ListItem(headlineContent = { Text(it.title) }, supportingContent = { Text("${fmt(it.date)}  ${it.sub}") }, leadingContent = { Icon(Icons.Default.CheckCircle, null) }) }
    }
}

private data class RoadChild(val title: String, val progress: Float, val route: String)
private data class RoadNode(val title: String, val progress: Float, val sub: String, val children: List<RoadChild>)

@Composable fun RoadmapScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val ms by vm.milestones.collectAsState()
    val courses by vm.courses.collectAsState(); val lessons by vm.lessons.collectAsState()
    var open by remember { mutableStateOf<Int?>(null) }
    val active = goals.filter { !it.archived }
    fun goalNode(title: String, l: List<Goal>) = RoadNode(title, if (l.isEmpty()) 0f else l.map { goalProgress(it, ms) }.average().toFloat(),
        "${l.count { it.status == "Completed" }}/${l.size} completed", l.map { RoadChild(it.title, goalProgress(it, ms), "goal/${it.id}") })
    val nodes = buildList {
        DOMAINS.take(5).forEachIndexed { i, d ->
            val cs = courses.filter { it.domain == i }
            add(RoadNode(d, if (cs.isEmpty()) 0f else cs.map { courseProgress(it, lessons) }.average().toFloat(),
                "${cs.count { it.status == "Completed" }}/${cs.size} courses", cs.map { RoadChild(it.title, courseProgress(it, lessons), "course/${it.id}") }))
        }
        add(goalNode("Certifications", active.filter { it.type == "CERT" }))
        add(goalNode("Competitions", active.filter { it.type == "COMP" || it.type == "CF" }))
        active.filter { it.type == "CUSTOM" }.let { if (it.isNotEmpty()) add(goalNode("Custom goals", it)) }
    }
    val current = nodes.indexOfFirst { it.progress < 1f }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { TopBar("Career roadmap", nav) }
        item { Text("Tap a stage to see what's inside. The highlighted stage is your first unfinished one.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        items(nodes.indices.toList()) { i ->
            val n = nodes[i]
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp).fillMaxHeight()) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { n.progress }, modifier = Modifier.size(44.dp))
                        Text("${(n.progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (i < nodes.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Column(Modifier.padding(start = 8.dp, bottom = 16.dp).weight(1f)) {
                    AppCard(onClick = { open = if (open == i) null else i }) {
                        Text(n.title, style = MaterialTheme.typography.titleMedium)
                        Text(n.sub + if (i == current) " • You are here" else "", style = MaterialTheme.typography.bodySmall,
                            color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (open == i) {
                            Spacer(Modifier.height(8.dp))
                            n.children.forEach { ch ->
                                Column(Modifier.fillMaxWidth().clickable { nav.navigate(ch.route) }.padding(vertical = 6.dp)) {
                                    Text(ch.title, style = MaterialTheme.typography.bodyMedium); Progress(ch.progress)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Hit(val title: String, val sub: String, val route: String, val group: Int)

@Composable fun SearchScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val courses by vm.courses.collectAsState()
    val ms by vm.milestones.collectAsState(); val notes by vm.notes.collectAsState()
    var q by remember { mutableStateOf("") }; var f by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Goals", "Courses", "Milestones", "Notes")
    val hits = if (q.trim().length < 2) emptyList() else buildList {
        goals.filter { it.title.contains(q, true) || it.description.contains(q, true) }.forEach { add(Hit(it.title, "Goal • ${it.status}", "goal/${it.id}", 1)) }
        courses.filter { it.title.contains(q, true) || it.skills.contains(q, true) }.forEach { add(Hit(it.title, "Course • ${it.status}", "course/${it.id}", 2)) }
        ms.filter { it.title.contains(q, true) }.forEach { add(Hit(it.title, "Milestone", if (it.parentType == "GOAL") "goal/${it.parentId}" else "course/${it.parentId}", 3)) }
        notes.filter { it.text.contains(q, true) }.forEach { add(Hit(it.text.take(120), "Note", if (it.parentType == "GOAL") "goal/${it.parentId}" else "course/${it.parentId}", 4)) }
    }.filter { f == 0 || it.group == f }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { TopBar("Search", nav) }
        item { OutlinedTextField(q, { q = it }, label = { Text("Search goals, courses, milestones, notes") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        item { ScrollableTabRow(f, edgePadding = 0.dp) { filters.forEachIndexed { i, t -> Tab(f == i, { f = i }, text = { Text(t) }) } } }
        if (q.trim().length >= 2 && hits.isEmpty()) item { Text("No results.", Modifier.padding(8.dp)) }
        items(hits) { h -> ListItem(headlineContent = { Text(h.title) }, supportingContent = { Text(h.sub) }, modifier = Modifier.clickable { nav.navigate(h.route) }) }
    }
}
