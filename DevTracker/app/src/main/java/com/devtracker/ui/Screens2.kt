package com.devtracker.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable fun LearningScreen(vm: AppVm, nav: NavController) {
    val courses by vm.courses.collectAsState(); val lessons by vm.lessons.collectAsState(); val sessions by vm.sessions.collectAsState()
    var filter by remember { mutableIntStateOf(-1) }; var add by remember { mutableStateOf(false) }; var log by remember { mutableStateOf(false) }
    var timerDomain by vm::timerDomain
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.timerStart) { while (vm.timerStart != null) { tick = System.currentTimeMillis(); delay(1000) } }
    val list = courses.filter { filter < 0 || it.domain == filter }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AppCard {
            Text("Study timer", style = MaterialTheme.typography.titleMedium)
            val st = vm.timerStart
            if (st == null) {
                Dropdown("Domain", DOMAINS, timerDomain) { timerDomain = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.startTimer(timerDomain) }) { Icon(Icons.Default.PlayArrow, null); Text("Start") }
                    OutlinedButton({ log = true }) { Text("Log manually") }
                }
            } else {
                val s = ((tick - st) / 1000).coerceAtLeast(0)
                Text("%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60), style = MaterialTheme.typography.displaySmall)
                Button({ vm.stopTimer(timerDomain) }) { Icon(Icons.Default.Stop, null); Text("Stop & save") }
            }
        } }
        item { AppCard {
            Text("Time by domain", style = MaterialTheme.typography.titleMedium)
            DOMAINS.forEachIndexed { i, d -> val m = sessions.filter { it.domain == i }.sumOf { it.minutes }; Text("$d: ${hours(m)}", style = MaterialTheme.typography.bodyMedium) }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Courses"); Spacer(Modifier.weight(1f)); IconButton({ add = true }) { Icon(Icons.Default.Add, "Add course") } } }
        item { ScrollableTabRow(filter + 1, edgePadding = 0.dp) {
            (listOf("All") + DOMAINS.take(5)).forEachIndexed { i, t -> Tab(filter + 1 == i, { filter = i - 1 }, text = { Text(t) }) } } }
        items(list, key = { it.id }) { c ->
            val p = courseProgress(c, lessons); val ls = lessons.filter { it.courseId == c.id }
            AppCard(onClick = { nav.navigate("course/${c.id}") }) {
                Text(c.title, style = MaterialTheme.typography.titleSmall)
                Text("${DOMAINS[c.domain]} • ${c.status} • ${ls.count { it.done }}/${ls.size} lessons", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp)); Progress(p)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (log) LogSessionDialog(timerDomain, false, { m, d -> vm.logSession(m, d) }) { log = false }
    if (add) {
        var t by remember { mutableStateOf("") }; var d by remember { mutableIntStateOf(0) }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New course") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") }); Dropdown("Domain", DOMAINS.take(5), d) { d = it } } },
            confirmButton = { TextButton({ if (t.isNotBlank()) { vm.addCourse(t.trim(), d); add = false } }) { Text("Add") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable fun CourseDetail(vm: AppVm, id: Long, nav: NavController) {
    val courses by vm.courses.collectAsState(); val allLessons by vm.lessons.collectAsState()
    val notes by vm.notes.collectAsState(); val sessions by vm.sessions.collectAsState(); val allMs by vm.milestones.collectAsState()
    val c = courses.firstOrNull { it.id == id } ?: return
    val ls = allLessons.filter { it.courseId == id }
    val ms = allMs.filter { it.parentType == "COURSE" && it.parentId == id }
    val myNotes = notes.filter { it.parentType == "COURSE" && it.parentId == id }
    val spent = sessions.filter { it.refType == "COURSE" && it.refId == id }.sumOf { it.minutes }
    val uri = LocalUriHandler.current
    var addLesson by remember { mutableStateOf(false) }; var addNote by remember { mutableStateOf(false) }; var log by remember { mutableStateOf(false) }
    var addMs by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    var url by remember(c.id) { mutableStateOf(c.url) }; var skills by remember(c.id) { mutableStateOf(c.skills) }; var est by remember(c.id) { mutableStateOf(c.estHours.toString()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton({ del = true }) { Icon(Icons.Default.Delete, "Delete") } } }
        item { AppCard {
            val p = courseProgress(c, allLessons)
            Text("${(p * 100).toInt()}% • ${c.status}  •  ${DOMAINS[c.domain]}"); Progress(p)
            Text("Hours: ${hours(spent)} actual / ${c.estHours} h estimated", style = MaterialTheme.typography.bodySmall)
            Text("Started ${fmt(c.startDate)} • Completed ${fmt(c.completedAt)}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(c.status == "Completed", { vm.setCourseCompleted(c, it) }); Text("Mark course as completed")
            }
            if (ls.isEmpty()) {
                Dropdown("Status", STATUSES, STATUSES.indexOf(c.status).coerceAtLeast(0)) {
                    val now = System.currentTimeMillis()
                    vm.saveCourse(c.copy(status = STATUSES[it], startDate = c.startDate ?: if (it > 0) now else null, completedAt = if (STATUSES[it] == "Completed") now else null))
                }
                Text("Add lessons to track progress automatically.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("Target", c.targetDate) { vm.saveCourse(c.copy(targetDate = it)) }
                Button({ log = true }) { Text("Log time") }
            }
        } }
        item { AppCard {
            OutlinedTextField(url, { url = it }, label = { Text("Course URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(skills, { skills = it }, label = { Text("Skills acquired") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(est, { est = it.filter(Char::isDigit) }, label = { Text("Estimated hours") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.saveCourse(c.copy(url = url, skills = skills, estHours = est.toIntOrNull() ?: c.estHours)) }) { Text("Save") }
                if (url.startsWith("http")) OutlinedButton({ uri.openUri(url) }) { Text("Open") }
            }
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Lessons"); Spacer(Modifier.weight(1f)); IconButton({ addLesson = true }) { Icon(Icons.Default.Add, "Add lesson") } } }
        items(ls, key = { "l${it.id}" }) { l -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(l.done, { vm.toggleLesson(l) }); Text(l.title, Modifier.weight(1f)); IconButton({ vm.deleteLesson(l) }) { Icon(Icons.Default.Close, "Delete") } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Milestones"); Spacer(Modifier.weight(1f)); IconButton({ addMs = true }) { Icon(Icons.Default.Add, "Add milestone") } } }
        items(ms, key = { "m${it.id}" }) { m -> MilestoneRow(m, vm) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Notes & resources"); Spacer(Modifier.weight(1f)); IconButton({ addNote = true }) { Icon(Icons.Default.Add, "Add note") } } }
        items(myNotes, key = { "n${it.id}" }) { n -> AppCard { Text(n.text); Row { Text(fmt(n.createdAt), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (n.text.startsWith("http")) TextButton({ uri.openUri(n.text.trim()) }) { Text("Open") }
            TextButton({ vm.deleteNote(n) }) { Text("Delete") } } } }
    }
    if (addLesson) TextDialog("New lesson", "Title", onDone = { vm.addLesson(id, it) }, onDismiss = { addLesson = false })
    if (addMs) TextDialog("New milestone", "Title", onDone = { vm.addMilestone("COURSE", id, it) }, onDismiss = { addMs = false })
    if (addNote) TextDialog("New note or link", "Text", onDone = { vm.addNote("COURSE", id, it) }, onDismiss = { addNote = false })
    if (log) LogSessionDialog(c.domain, true, { m, _ -> vm.logSession(m, c.domain, "COURSE", id) }) { log = false }
    if (del) AlertDialog(onDismissRequest = { del = false }, title = { Text("Delete course?") }, text = { Text("Its lessons, milestones and notes will be removed.") },
        confirmButton = { TextButton({ vm.deleteCourse(c); del = false; nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton({ del = false }) { Text("Cancel") } })
}

@Composable fun AnalyticsScreen(vm: AppVm) {
    val goals by vm.goals.collectAsState(); val ms by vm.milestones.collectAsState()
    val courses by vm.courses.collectAsState(); val lessons by vm.lessons.collectAsState()
    val sessions by vm.sessions.collectAsState(); val ratings by vm.ratings.collectAsState()
    val now = System.currentTimeMillis(); val active = goals.filter { !it.archived }
    fun avg(l: List<Float>) = if (l.isEmpty()) 0f else l.average().toFloat()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium) }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val wk = weekValues(sessions, 2); val wkStart = dayStart(now) - 6 * DAY
            val thisWeek = sessions.filter { it.date >= wkStart }
            val top = DOMAINS.indices.maxByOrNull { d -> thisWeek.filter { it.domain == d }.sumOf { it.minutes } }
            val report = buildString {
                appendLine("Weekly report")
                appendLine("Hours: ${"%.1f".format(wk[1])} this week, ${"%.1f".format(wk[0])} last week")
                appendLine("Sessions: ${thisWeek.size}, active days: ${thisWeek.map { dayStart(it.date) }.toSet().size}/7")
                appendLine("Milestones completed: ${ms.count { (it.doneAt ?: 0L) >= wkStart }}")
                if (thisWeek.isNotEmpty() && top != null) appendLine("Most time: ${DOMAINS[top]}")
            }
            AppCard { Text("Weekly report", style = MaterialTheme.typography.titleMedium); Text(report.lines().drop(1).joinToString("\n").trim())
                TextButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share report")) }) { Text("Share") } }
        }
        item { AppCard { Text("Learning hours per week (8 weeks)", style = MaterialTheme.typography.titleMedium)
            BarChart(weekValues(sessions, 8), (8 downTo 1).map { "W-${it - 1}".replace("W-0", "Now") }) } }
        item { AppCard { Text("Hours per month (6 months)", style = MaterialTheme.typography.titleMedium)
            val cal = java.util.Calendar.getInstance()
            val months = (5 downTo 0).map { k -> java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -k); set(java.util.Calendar.DAY_OF_MONTH, 1) }.let { it.get(java.util.Calendar.YEAR) * 12 + it.get(java.util.Calendar.MONTH) to it } }
            BarChart(months.map { (key, _) -> sessions.filter { s -> cal.timeInMillis = s.date; cal.get(java.util.Calendar.YEAR) * 12 + cal.get(java.util.Calendar.MONTH) == key }.sumOf { it.minutes } / 60f },
                months.map { (_, c) -> java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(c.time) }) } }
        item { AppCard { Text("Progress by category", style = MaterialTheme.typography.titleMedium)
            listOf("Certifications" to avg(active.filter { it.type == "CERT" }.map { goalProgress(it, ms) }),
                "Competitions" to avg(active.filter { it.type == "COMP" || it.type == "CF" }.map { goalProgress(it, ms) }),
                "Custom goals" to avg(active.filter { it.type == "CUSTOM" }.map { goalProgress(it, ms) })) .plus(
                DOMAINS.take(5).mapIndexed { i, d -> d to avg(courses.filter { it.domain == i }.map { courseProgress(it, lessons) }) })
                .forEach { (n, p) -> Text("$n — ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium); Progress(p); Spacer(Modifier.height(6.dp)) } } }
        item { AppCard { Text("Completion statistics", style = MaterialTheme.typography.titleMedium)
            Text("Certifications: ${active.count { it.type == "CERT" && it.status == "Completed" }}/${active.count { it.type == "CERT" }}")
            Text("Courses: ${courses.count { it.status == "Completed" }}/${courses.size}")
            Text("Goals completed: ${goals.count { it.status == "Completed" }}/${goals.size}")
            Text("Milestones done: ${ms.count { it.done }}/${ms.size}")
            Text("Estimated vs actual hours (courses): ${courses.sumOf { it.estHours }} h est / ${hours(sessions.filter { it.refType == "COURSE" }.sumOf { it.minutes })} actual") } }
        item { AppCard { Text("Activity — last 28 days", style = MaterialTheme.typography.titleMedium)
            val today = dayStart(now)
            (0 until 4).forEach { w -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d -> val s = today - (27 - (w * 7 + d)) * DAY; val m = sessions.filter { it.date in s until s + DAY }.sumOf { it.minutes }
                    val a = when { m == 0 -> 0.08f; m < 30 -> 0.35f; m < 90 -> 0.65f; else -> 1f }
                    Surface(Modifier.weight(1f).height(28.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = a)) {} } }
                Spacer(Modifier.height(4.dp)) }
            Text("Current streak: ${streak(sessions)} days", style = MaterialTheme.typography.bodySmall) } }
        item { AppCard { Text("Codeforces rating history", style = MaterialTheme.typography.titleMedium)
            if (ratings.isEmpty()) Text("Add ratings in the Codeforces goal to see the chart.") else LineChart(ratings.map { it.rating }, 2100) } }
    }
}

@Composable fun ProfileScreen(vm: AppVm, nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(vm::backup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    var confirmRestore by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profile & settings", style = MaterialTheme.typography.headlineMedium) }
        item { AppCard { Text("Theme", style = MaterialTheme.typography.titleMedium)
            Dropdown("Mode", listOf("System", "Light", "Dark"), vm.themeMode) { vm.setTheme(it) } } }
        item { AppCard { Text("Daily learning goal: ${vm.dailyGoal} min", style = MaterialTheme.typography.titleMedium)
            Slider(vm.dailyGoal / 15f, { vm.setDaily(((it.toInt()).coerceAtLeast(1)) * 15) }, valueRange = 1f..16f) } }
        item { AppCard { Text("Dashboard sections", style = MaterialTheme.typography.titleMedium)
            listOf("overall" to "Overall progress", "stats" to "Quick stats", "plan" to "Today's plan", "today" to "Daily activity",
                "codeforces" to "Codeforces", "competition" to "Competition preparation", "deadlines" to "Upcoming deadlines", "recent" to "Recent achievements")
                .forEach { (id, label) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(id !in vm.hidden, { vm.toggleSection(id) }) } } } }
        item { AppCard(onClick = { nav.navigate("achievements") }) { Text("Achievements & timeline", style = MaterialTheme.typography.titleMedium)
            Text("Badges you have earned and your personal timeline", style = MaterialTheme.typography.bodySmall) } }
        item { AppCard { Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
            Text("Saves your whole database to a file you choose. Restoring replaces all current data and restarts the app.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ backup.launch("devtracker-backup.db") }) { Text("Backup") }
                OutlinedButton({ confirmRestore = true }) { Text("Restore") } }
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) } } }
        item { AppCard { Text("Export progress summary", style = MaterialTheme.typography.titleMedium)
            Button({
                val g = vm.goals.value; val m = vm.milestones.value; val c = vm.courses.value; val l = vm.lessons.value
                val text = buildString {
                    appendLine("DevTracker progress report"); appendLine("Goals:")
                    g.forEach { appendLine("- ${it.title}: ${it.status}, ${(goalProgress(it, m) * 100).toInt()}%") }
                    appendLine("Courses:"); c.forEach { appendLine("- ${it.title}: ${(courseProgress(it, l) * 100).toInt()}%") }
                    appendLine("Total hours: ${hours(vm.sessions.value.sumOf { it.minutes })}")
                }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
            }) { Text("Share report") }
            OutlinedButton({ csv.launch("devtracker-sessions.csv") }) { Text("Export sessions (CSV)") } } }
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false }, title = { Text("Restore backup?") },
        text = { Text("All current data will be replaced and the app will close.") },
        confirmButton = { TextButton({ confirmRestore = false; restore.launch(arrayOf("*/*")) }) { Text("Choose file") } },
        dismissButton = { TextButton({ confirmRestore = false }) { Text("Cancel") } })
}
