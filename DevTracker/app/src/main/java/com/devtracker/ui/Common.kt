package com.devtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

val DOMAINS = listOf("Programming & CS", "Backend & DevOps", "Architecture", "Math & ML", "AI Engineering", "Certs & Contests")
val STATUSES = listOf("Not Started", "Preparing", "In Progress", "Exam Scheduled", "Completed")
const val DAY = 86_400_000L

fun fmt(ms: Long?): String = ms?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "—"
fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
fun hours(min: Int) = "%.1f h".format(min / 60f)

@Composable fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    if (onClick != null) ElevatedCard(onClick = onClick, modifier = m) { Column(Modifier.padding(16.dp), content = content) }
    else ElevatedCard(m) { Column(Modifier.padding(16.dp), content = content) }
}
@Composable fun Progress(p: Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
@Composable fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
@Composable fun Stat(label: String, value: String, modifier: Modifier = Modifier) = ElevatedCard(modifier) {
    Column(Modifier.padding(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun TextDialog(title: String, hint: String, initial: String = "", onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ if (t.isNotBlank()) { onDone(t.trim()); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
@Composable fun NumberDialog(title: String, hint: String, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it.filter(Char::isDigit) }, label = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton({ t.toIntOrNull()?.let { onDone(it); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateButton(label: String, value: Long?, onPick: (Long?) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }) { Text("$label: ${fmt(value)}") }
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = value)
        DatePickerDialog(onDismissRequest = { show = false },
            confirmButton = { TextButton({ onPick(st.selectedDateMillis); show = false }) { Text("OK") } },
            dismissButton = { TextButton({ onPick(null); show = false }) { Text("Clear") } }) { DatePicker(st) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Dropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(options[selected], {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false }) }
        }
    }
}
@Composable fun LogSessionDialog(defaultDomain: Int, lockDomain: Boolean, onDone: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var mins by remember { mutableStateOf("") }; var dom by remember { mutableIntStateOf(defaultDomain) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Log study time") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(mins, { mins = it.filter(Char::isDigit) }, label = { Text("Minutes") }, singleLine = true)
            if (!lockDomain) Dropdown("Domain", DOMAINS, dom) { dom = it }
        } },
        confirmButton = { TextButton({ mins.toIntOrNull()?.let { onDone(it, dom); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable fun BarChart(values: List<Float>, labels: List<String>, unit: String = "h") {
    if (values.isEmpty()) return
    var sel by remember { mutableStateOf<Int?>(null) }
    val c1 = MaterialTheme.colorScheme.primary; val c2 = MaterialTheme.colorScheme.tertiary
    Column {
        Text(sel?.let { "${labels[it]}: ${"%.1f".format(values[it])} $unit" } ?: "Tap a bar for details", style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().height(150.dp).pointerInput(values) {
            detectTapGestures { o -> sel = (o.x / (size.width / values.size)).toInt().coerceIn(0, values.size - 1) }
        }) {
            val max = (values.maxOrNull() ?: 1f).coerceAtLeast(0.1f); val w = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRoundRect(if (i == sel) c2 else c1, Offset(i * w + w * .15f, size.height - h), Size(w * .7f, h), CornerRadius(8f))
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) { labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall) } }
    }
}
@Composable fun LineChart(values: List<Int>, target: Int) {
    if (values.isEmpty()) return
    val c1 = MaterialTheme.colorScheme.primary; val c2 = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val lo = (minOf(values.min(), target) - 100).toFloat(); val hi = (maxOf(values.max(), target) + 100).toFloat()
        fun y(v: Float) = size.height * (1 - (v - lo) / (hi - lo))
        drawLine(c2, Offset(0f, y(target.toFloat())), Offset(size.width, y(target.toFloat())), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(if (values.size > 1) i * step else size.width / 2, y(v.toFloat()))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            drawCircle(c1, 7f, p)
        }
        drawPath(path, c1, style = Stroke(5f))
    }
}

@Composable fun FieldsDialog(title: String, labels: List<String>, numeric: List<Boolean> = labels.map { false }, required: Int = labels.size,
                             onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val vals = remember { mutableStateListOf(*Array(labels.size) { "" }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> OutlinedTextField(vals[i], { v -> vals[i] = if (numeric[i]) v.filter(Char::isDigit) else v }, label = { Text(l) }, singleLine = true) }
        } },
        confirmButton = { TextButton({ if (vals.take(required).all { it.isNotBlank() }) { onDone(vals.map { it.trim() }); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
