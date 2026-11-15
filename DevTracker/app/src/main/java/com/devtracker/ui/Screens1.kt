package com.devtracker.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devtracker.data.*

fun streak(sessions: List<Session>): Int {
    val days = sessions.map { dayStart(it.date) }.toSet()
    var d = dayStart(System.currentTimeMillis()); if (d !in days) d -= DAY
    var n = 0; while (d in days) { n++; d -= DAY }; return n
}
fun weekValues(sessions: List<Session>, weeks: Int): List<Float> {
    val end = dayStart(System.currentTimeMillis()) + DAY
    return (weeks - 1 downTo 0).map { w ->
        val from = end - (w + 1) * 7 * DAY; val to = end - w * 7 * DAY
        sessions.filter { it.date in from until to }.sumOf { it.minutes } / 60f
    }
}

@Composable fun HomeScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val ms by vm.milestones.collectAsState()
    val courses by vm.courses.collectAsState(); val lessons by vm.lessons.collectAsState()
    val sessions by vm.sessions.collectAsState(); val ratings by vm.ratings.collectAsState()
    val events by vm.events.collectAsState()
    var menu by remember { mutableStateOf(false) }; var dialog by remember { mutableStateOf("") }
    fun show(id: String) = id !in vm.hidden
    val now = System.currentTimeMillis(); val todayS = dayStart(now)
    val active = goals.filter { !it.archived }
    val certs = active.filter { it.type == "CERT" }; val comps = active.filter { it.type == "COMP" || it.type == "CF" }
    val all = active.map { goalProgress(it, ms) } + courses.map { courseProgress(it, lessons) }
    val overall = if (all.isEmpty()) 0f else all.average().toFloat()
    val today = sessions.filter { it.date >= todayS }.sumOf { it.minutes }
    val cf = ratings.maxByOrNull { it.date }?.rating
    val deadlines = active.filter { it.status != "Completed" }
        .flatMap { g -> listOfNotNull(g.examDate?.let { Triple(it, "${g.title} (exam)", g.id) }, g.targetDate?.let { Triple(it, g.title, g.id) }) }
        .filter { it.first >= todayS }.sortedBy { it.first }.take(4)
    val recent = ms.filter { it.done && it.doneAt != null }.sortedByDescending { it.doneAt }.take(5)
    val todayEvents = events.filter { dayStart(it.date) == todayS && !it.done }
    val dueMs = ms.filter { !it.done && (it.dueDate ?: Long.MAX_VALUE) < todayS + DAY }
    val dueIds = dueMs.map { it.id }.toSet()
    val nextUp = active.filter { it.status != "Completed" }.sortedBy { it.priority }.take(3).mapNotNull { g ->
        ms.firstOrNull { it.parentType == "GOAL" && it.parentId == g.id && !it.done && it.id !in dueIds }?.let { g to it }
    }
    fun parentOf(m: Milestone) = (if (m.parentType == "GOAL") goals.firstOrNull { it.id == m.parentId }?.title else courses.firstOrNull { it.id == m.parentId }?.title) ?: ""
    val days7 = (6 downTo 0).map { todayS - it * DAY }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your journey", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton({ nav.navigate("search") }) { Icon(Icons.Default.Search, "Search") }
                IconButton({ nav.navigate("calendar") }) { Icon(Icons.Default.CalendarMonth, "Calendar") }
                IconButton({ nav.navigate("roadmap") }) { Icon(Icons.Default.Timeline, "Roadmap") }
                IconButton({ nav.navigate("achievements") }) { Icon(Icons.Default.EmojiEvents, "Achievements") }
            } }
            if (show("overall")) item { AppCard {
                Text("Overall progress", style = MaterialTheme.typography.titleMedium)
                Text("${(overall * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
                Progress(overall)
                Text("Across ${active.size} goals and ${courses.size} courses", style = MaterialTheme.typography.bodySmall)
            } }
            if (show("stats")) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Certs done", "${certs.count { it.status == "Completed" }}/${certs.size}", Modifier.weight(1f))
                    Stat("Preparing", "${certs.count { it.status in listOf("Preparing", "In Progress", "Exam Scheduled") }}", Modifier.weight(1f))
                } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Courses done", "${courses.count { it.status == "Completed" }}/${courses.size}", Modifier.weight(1f))
                    Stat("Total hours", hours(sessions.sumOf { it.minutes }), Modifier.weight(1f))
                } }
                item { Stat("Active goals", "${active.count { it.status != "Completed" }}", Modifier.fillMaxWidth()) }
            }
            if (show("plan")) {
                item { SectionTitle("Today's plan") }
                if (todayEvents.isEmpty() && dueMs.isEmpty() && nextUp.isEmpty()) item { Text("Nothing planned. Schedule a session or set milestone due dates.", style = MaterialTheme.typography.bodyMedium) }
                items(todayEvents, key = { "e${it.id}" }) { e ->
                    ListItem(headlineContent = { Text(e.title) }, supportingContent = { Text(if (e.kind == "STUDY") "Study • ${e.minutes} min" else "Reminder") },
                        leadingContent = { Checkbox(false, { vm.toggleEvent(e) }) })
                }
                items(dueMs, key = { "d${it.id}" }) { m ->
                    ListItem(headlineContent = { Text(m.title) }, supportingContent = { Text("${parentOf(m)} • due ${fmt(m.dueDate)}") },
                        leadingContent = { Checkbox(false, { vm.toggleMilestone(m) }) })
                }
                items(nextUp, key = { "n${it.second.id}" }) { (g, m) ->
                    ListItem(headlineContent = { Text(m.title) }, supportingContent = { Text("Next up • ${g.title}") },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { nav.navigate("goal/${g.id}") })
                }
            }
            if (show("today")) item { AppCard {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Text("$today / ${vm.dailyGoal} min  •  ${streak(sessions)}-day streak")
                Progress(today / vm.dailyGoal.toFloat())
                Spacer(Modifier.height(8.dp))
                BarChart(days7.map { s -> sessions.filter { it.date in s until s + DAY }.sumOf { it.minutes } / 60f },
                    days7.map { java.text.SimpleDateFormat("E", java.util.Locale.getDefault()).format(java.util.Date(it)).take(2) })
            } }
            if (show("codeforces")) item { AppCard(onClick = { goals.firstOrNull { it.type == "CF" }?.let { nav.navigate("goal/${it.id}") } }) {
                Text("Codeforces", style = MaterialTheme.typography.titleMedium)
                Text(if (cf == null) "No rating recorded yet — tap to add one" else "$cf → 2100", style = MaterialTheme.typography.headlineSmall)
                if (cf != null) Progress(cf / 2100f)
            } }
            if (show("competition")) item { AppCard {
                Text("Competition preparation", style = MaterialTheme.typography.titleMedium)
                val p = if (comps.isEmpty()) 0f else comps.map { goalProgress(it, ms) }.average().toFloat()
                Text("${(p * 100).toInt()}% across ${comps.size} contests"); Progress(p)
            } }
            if (show("deadlines")) {
                item { SectionTitle("Upcoming deadlines") }
                if (deadlines.isEmpty()) item { Text("Nothing scheduled. Set target or exam dates on your goals.", style = MaterialTheme.typography.bodyMedium) }
                items(deadlines) { (d, t, id) ->
                    AppCard(onClick = { nav.navigate("goal/$id") }) {
                        Text(t); Text("${fmt(d)} • ${(d - todayS) / DAY} days left", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (show("recent")) {
                item { SectionTitle("Recent achievements") }
                if (recent.isEmpty()) item { Text("Complete a milestone and it will appear here.") }
                items(recent) { m -> ListItem(headlineContent = { Text(m.title) }, supportingContent = { Text("${fmt(m.doneAt)} • ${parentOf(m)}") },
                    leadingContent = { Icon(Icons.Default.EmojiEvents, null) }) }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                    onClick = { menu = true },
                    icon = {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    },
                    text = {
                        Text("Quick add")
                    }
                )
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Log study time") }, onClick = { menu = false; dialog = "log" })
                DropdownMenuItem(text = { Text("Schedule study session") }, onClick = { menu = false; dialog = "STUDY" })
                DropdownMenuItem(text = { Text("Add reminder") }, onClick = { menu = false; dialog = "REMINDER" })
            }
        }
    }
    if (dialog == "log") LogSessionDialog(5, false, { m, d -> vm.logSession(m, d) }) { dialog = "" }
    if (dialog == "STUDY" || dialog == "REMINDER") AddEventDialog(dialog, todayS, vm) { dialog = "" }
}

@Composable fun GoalsScreen(vm: AppVm, nav: NavController) {
    val goals by vm.goals.collectAsState(); val ms by vm.milestones.collectAsState()
    var tab by remember { mutableIntStateOf(0) }; var q by remember { mutableStateOf("") }; var add by remember { mutableStateOf(false) }
    val tabs = listOf("Certs", "Contests", "Custom", "Archived")
    val list = goals.filter { g ->
        (if (tab == 3) g.archived else !g.archived && when (tab) { 0 -> g.type == "CERT"; 1 -> g.type == "COMP" || g.type == "CF"; else -> g.type == "CUSTOM" }) &&
            g.title.contains(q, true)
    }
    Box(Modifier.fillMaxSize()) {
        Column {
            ScrollableTabRow(tab, edgePadding = 8.dp) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            OutlinedTextField(q, { q = it }, label = { Text("Search goals") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { Text("No goals here yet.") }
                items(list, key = { it.id }) { g ->
                    val p = goalProgress(g, ms)
                    AppCard(onClick = { nav.navigate("goal/${g.id}") }) {
                        Text(g.title, style = MaterialTheme.typography.titleMedium)
                        Text("${g.status} • ${listOf("High", "Medium", "Low")[g.priority.coerceIn(1, 3) - 1]} priority" + (g.targetDate?.let { " • due ${fmt(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp)); Progress(p)
                    }
                }
            }
        }
        FloatingActionButton({ add = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add goal") }
    }
    if (add) {
        var t by remember { mutableStateOf("") }; var type by remember { mutableIntStateOf(2) }; var pr by remember { mutableIntStateOf(1) }
        val types = listOf("Certification" to "CERT", "Competition" to "COMP", "Custom goal" to "CUSTOM")
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New goal") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") })
                Dropdown("Category", types.map { it.first }, type) { type = it }
                Dropdown("Priority", listOf("High", "Medium", "Low"), pr) { pr = it }
            } },
            confirmButton = { TextButton({ if (t.isNotBlank()) { vm.addGoal(t.trim(), types[type].second, pr + 1); add = false } }) { Text("Add") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable fun GoalDetail(vm: AppVm, id: Long, nav: NavController) {
    val goals by vm.goals.collectAsState(); val allMs by vm.milestones.collectAsState()
    val notes by vm.notes.collectAsState(); val sessions by vm.sessions.collectAsState(); val ratings by vm.ratings.collectAsState()
    val results by vm.results.collectAsState(); val problems by vm.problems.collectAsState()
    val ctx = LocalContext.current
    val g = goals.firstOrNull { it.id == id } ?: return
    val ms = allMs.filter { it.parentType == "GOAL" && it.parentId == id }
    val myNotes = notes.filter { it.parentType == "GOAL" && it.parentId == id }
    val mySessions = sessions.filter { it.refType == "GOAL" && it.refId == id }
    val myResults = results.filter { it.goalId == id }
    val uri = LocalUriHandler.current
    val now = System.currentTimeMillis()
    var addMs by remember { mutableStateOf(false) }; var addNote by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf(false) }; var rate by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    var addResult by remember { mutableStateOf(false) }; var addProblems by remember { mutableStateOf(false) }; var reminded by remember { mutableStateOf(false) }
    var handle by remember(vm.cfHandle) { mutableStateOf(vm.cfHandle) }
    var desc by remember(g.id) { mutableStateOf(g.description) }; var url by remember(g.id) { mutableStateOf(g.url) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let {
            try { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            vm.saveGoal(g.copy(certificateUri = it.toString()))
        }
    }
    val nextDate = listOfNotNull(g.examDate, g.targetDate).filter { it > now }.minOrNull()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(g.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton({ vm.saveGoal(g.copy(archived = !g.archived)) }) { Icon(Icons.Default.Archive, "Archive") }
            IconButton({ del = true }) { Icon(Icons.Default.Delete, "Delete") }
        } }
        item { AppCard {
            val p = goalProgress(g, ms)
            Text("${(p * 100).toInt()}% complete  •  ${hours(mySessions.sumOf { it.minutes })} studied")
            Progress(p)
            Text("Milestones ${ms.count { it.done }}/${ms.size} • ${mySessions.size} sessions" +
                (g.examDate?.let { if (it >= dayStart(now)) " • exam in ${(it - dayStart(now)) / DAY} days" else "" } ?: ""), style = MaterialTheme.typography.bodySmall)
            if (g.completedAt != null && g.targetDate != null) {
                val diff = (g.completedAt - g.targetDate) / DAY
                Text(if (diff <= 0) "Finished ${-diff} days before target" else "Finished $diff days after target", style = MaterialTheme.typography.bodySmall)
            }
            if (ms.isEmpty()) { Slider(g.manualProgress / 100f, { vm.saveGoal(g.copy(manualProgress = (it * 100).toInt())) }); Text("No milestones: drag to set progress", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Dropdown("Status", STATUSES, STATUSES.indexOf(g.status).coerceAtLeast(0)) { vm.saveGoal(g.copy(status = STATUSES[it])) }
            Spacer(Modifier.height(8.dp))
            Dropdown("Priority", listOf("High", "Medium", "Low"), g.priority.coerceIn(1, 3) - 1) { vm.saveGoal(g.copy(priority = it + 1)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("Target", g.targetDate) { vm.saveGoal(g.copy(targetDate = it)) }
                if (g.type != "CUSTOM") DateButton("Exam", g.examDate) { vm.saveGoal(g.copy(examDate = it)) }
            }
            if (g.completedAt != null) Text("Completed on ${fmt(g.completedAt)}", style = MaterialTheme.typography.bodySmall)
            if (nextDate != null) OutlinedButton({
                val at = maxOf(dayStart(nextDate) - DAY + 9 * 3_600_000L, now + 60_000)
                vm.addEvent(Event(title = "Tomorrow: ${g.title}", date = at, kind = "REMINDER", notifyAt = at)); reminded = true
            }, enabled = !reminded) { Text(if (reminded) "Reminder set" else "Remind me the day before") }
        } }
        if (g.type == "CF") {
            item { AppCard {
                val cur = ratings.maxByOrNull { it.date }
                Text("Rating: ${cur?.rating ?: "—"} / 2100 • best ${ratings.maxOfOrNull { it.rating } ?: "—"} • ${ratings.size} contests", style = MaterialTheme.typography.titleMedium)
                LineChart(ratings.map { it.rating }, 2100)
                Button({ rate = true }) { Text("Add rating") }
                OutlinedTextField(handle, { handle = it }, label = { Text("Codeforces handle (optional sync)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton({ vm.updateCfHandle(handle.trim()); vm.syncCodeforces(handle) }) { Text("Sync ratings from Codeforces") }
                vm.cfStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ratings.reversed().take(5).forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt(r.date)}  ${r.rating}  ${r.contest}", Modifier.weight(1f)); IconButton({ vm.deleteRating(r) }) { Icon(Icons.Default.Close, "Delete") }
                } }
            } }
            item { AppCard {
                fun bucket(r: Int) = when { r < 1200 -> 0; r < 1600 -> 1; r < 2000 -> 2; else -> 3 }
                Text("Problem-solving: ${problems.sumOf { it.count }} solved", style = MaterialTheme.typography.titleMedium)
                BarChart((0..3).map { b -> problems.filter { bucket(it.rating) == b }.sumOf { it.count }.toFloat() }, listOf("<1200", "1200+", "1600+", "2000+"), "problems")
                Button({ addProblems = true }) { Text("Log solved problems") }
                problems.take(5).forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt(p.date)}  ${p.count} problems (~${p.rating})", Modifier.weight(1f)); IconButton({ vm.deleteProblems(p) }) { Icon(Icons.Default.Close, "Delete") }
                } }
            } }
        }
        if (g.type == "COMP") {
            item { AppCard {
                Text("Competition history & results", style = MaterialTheme.typography.titleMedium)
                if (myResults.isEmpty()) Text("No results yet.", style = MaterialTheme.typography.bodySmall)
                myResults.forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(r.title); Text("${r.result} • ${fmt(r.date)}", style = MaterialTheme.typography.bodySmall) }
                    IconButton({ vm.deleteResult(r) }) { Icon(Icons.Default.Close, "Delete") }
                } }
                Button({ addResult = true }) { Text("Add result") }
            } }
        }
        item { AppCard {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("Official resource URL") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.saveGoal(g.copy(description = desc, url = url)) }) { Text("Save") }
                if (url.startsWith("http")) OutlinedButton({ uri.openUri(url) }) { Text("Open link") }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (g.certificateUri.isBlank()) "No certificate attached" else "Certificate attached", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pick.launch(arrayOf("application/pdf", "image/*")) }) { Text(if (g.certificateUri.isBlank()) "Attach certificate" else "Replace") }
                if (g.certificateUri.isNotBlank()) {
                    OutlinedButton({
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(g.certificateUri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { }
                    }) { Text("Open") }
                    TextButton({ vm.saveGoal(g.copy(certificateUri = "")) }) { Text("Remove") }
                }
            }
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Milestones"); Spacer(Modifier.weight(1f)); IconButton({ addMs = true }) { Icon(Icons.Default.Add, "Add milestone") } } }
        items(ms, key = { "m${it.id}" }) { m -> MilestoneRow(m, vm) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Training sessions"); Spacer(Modifier.weight(1f)); IconButton({ log = true }) { Icon(Icons.Default.Add, "Log time") } } }
        items(mySessions.take(5), key = { "s${it.id}" }) { s -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(s.date)} • ${s.minutes} min", Modifier.weight(1f)); IconButton({ vm.deleteSession(s) }) { Icon(Icons.Default.Close, "Delete") } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Notes & resources"); Spacer(Modifier.weight(1f)); IconButton({ addNote = true }) { Icon(Icons.Default.Add, "Add note") } } }
        items(myNotes, key = { "n${it.id}" }) { n -> AppCard { Text(n.text); Row { Text(fmt(n.createdAt), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (n.text.startsWith("http")) TextButton({ uri.openUri(n.text.trim()) }) { Text("Open") }
            TextButton({ vm.deleteNote(n) }) { Text("Delete") } } } }
    }
    if (addMs) TextDialog("New milestone", "Title", onDone = { vm.addMilestone("GOAL", id, it) }, onDismiss = { addMs = false })
    if (addNote) TextDialog("New note or link", "Text", onDone = { vm.addNote("GOAL", id, it) }, onDismiss = { addNote = false })
    if (log) LogSessionDialog(5, true, { m, _ -> vm.logSession(m, 5, "GOAL", id) }) { log = false }
    if (rate) FieldsDialog("Add rating", listOf("Rating after contest", "Contest name (optional)"), listOf(true, false), 1,
        { v -> v[0].toIntOrNull()?.let { vm.addRating(it, v[1]) } }) { rate = false }
    if (addProblems) FieldsDialog("Log solved problems", listOf("Problems solved", "Typical difficulty rating"), listOf(true, true), 2,
        { v -> vm.addProblems(v[0].toIntOrNull() ?: 0, v[1].toIntOrNull() ?: 0) }) { addProblems = false }
    if (addResult) FieldsDialog("Add result", listOf("Event / year", "Result or ranking"), onDone = { v -> vm.addResult(id, v[0], v[1]) }) { addResult = false }
    if (del) AlertDialog(onDismissRequest = { del = false }, title = { Text("Delete goal?") }, text = { Text("Its milestones, notes and results will be removed too.") },
        confirmButton = { TextButton({ vm.deleteGoal(g); del = false; nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton({ del = false }) { Text("Cancel") } })
}
