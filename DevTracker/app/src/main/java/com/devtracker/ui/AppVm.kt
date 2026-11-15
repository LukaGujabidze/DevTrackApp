package com.devtracker.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devtracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class AppVm(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app, Db.get(app))
    private fun <T> st(f: Flow<List<T>>) = f.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals = st(repo.goals); val milestones = st(repo.milestones)
    val courses = st(repo.courses); val lessons = st(repo.lessons)
    val sessions = st(repo.sessions); val ratings = st(repo.ratings); val notes = st(repo.notes)
    val events = st(repo.events); val results = st(repo.results); val problems = st(repo.problems)

    var themeMode by mutableIntStateOf(0)
    var dailyGoal by mutableIntStateOf(60)
    var hidden by mutableStateOf<Set<String>>(emptySet())
    var timerStart by mutableStateOf<Long?>(null)
    var message by mutableStateOf<String?>(null)
    var timerDomain by mutableIntStateOf(0)
    var cfHandle by mutableStateOf("")
    var cfStatus by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch { val t = repo.prefs.timer.first(); if (t > 0) timerStart = t; timerDomain = repo.prefs.timerDomain.first(); cfHandle = repo.prefs.cfHandle.first() }
        viewModelScope.launch { repo.prefs.theme.collect { themeMode = it } }
        viewModelScope.launch { repo.prefs.daily.collect { dailyGoal = it } }
        viewModelScope.launch { repo.prefs.hidden.collect { hidden = it } }
        viewModelScope.launch(Dispatchers.IO) { repo.seedIfNeeded() }
    }
    fun run(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun setTheme(m: Int) { themeMode = m; run { repo.prefs.setTheme(m) } }
    fun setDaily(m: Int) { dailyGoal = m; run { repo.prefs.setDaily(m) } }
    fun toggleSection(id: String) { val n = if (id in hidden) hidden - id else hidden + id; hidden = n; run { repo.prefs.setHidden(n) } }

    fun addGoal(title: String, type: String, priority: Int) = run { repo.addGoal(title, type, priority) }
    fun saveGoal(g: Goal) = run { repo.saveGoal(g) }
    fun deleteGoal(g: Goal) = run { repo.deleteGoal(g) }
    fun addMilestone(type: String, id: Long, title: String) = run { repo.addMilestone(type, id, title) }
    fun toggleMilestone(m: Milestone) = run { repo.toggleMilestone(m) }
    fun setMilestoneDue(m: Milestone, d: Long?) = run { repo.setMilestoneDue(m, d) }
    fun deleteMilestone(m: Milestone) = run { repo.deleteMilestone(m) }
    fun addNote(type: String, id: Long, text: String) = run { repo.addNote(type, id, text) }
    fun deleteNote(n: NoteEntity) = run { repo.deleteNote(n) }

    fun addCourse(title: String, domain: Int) = run { repo.addCourse(title, domain) }
    fun saveCourse(c: Course) = run { repo.saveCourse(c) }
    fun deleteCourse(c: Course) = run { repo.deleteCourse(c) }
    fun setCourseCompleted(c: Course, done: Boolean) = run { repo.setCourseCompleted(c, done) }
    fun addLesson(courseId: Long, title: String) = run { repo.addLesson(courseId, title) }
    fun toggleLesson(l: Lesson) = run { repo.toggleLesson(l) }
    fun deleteLesson(l: Lesson) = run { repo.deleteLesson(l) }

    fun logSession(minutes: Int, domain: Int, refType: String = "NONE", refId: Long = 0) = run { repo.logSession(minutes, domain, refType, refId) }
    fun deleteSession(s: Session) = run { repo.deleteSession(s) }
    fun startTimer(domain: Int) { val t = System.currentTimeMillis(); timerDomain = domain; timerStart = t; run { repo.prefs.setTimer(t, domain) } }
    fun stopTimer(domain: Int, refType: String = "NONE", refId: Long = 0) {
        val s = timerStart ?: return
        timerStart = null; run { repo.prefs.setTimer(0L, timerDomain) }
        logSession(((System.currentTimeMillis() - s) / 60000).toInt().coerceAtLeast(1), domain, refType, refId)
    }

    fun setCfHandle(h: String) { cfHandle = h; run { repo.prefs.setCfHandle(h) } }
    fun syncCodeforces(handle: String) {
        cfStatus = "Syncing…"
        run { cfStatus = try { "Imported ${repo.syncCodeforces(handle)} new rating entries" } catch (e: Exception) { "Sync failed: ${e.message}" } }
    }
    fun addRating(rating: Int, contest: String) = run { repo.addRating(rating, contest) }
    fun deleteRating(r: RatingEntry) = run { repo.deleteRating(r) }
    fun addResult(goalId: Long, title: String, result: String) = run { repo.addResult(goalId, title, result) }
    fun deleteResult(r: CompResult) = run { repo.deleteResult(r) }
    fun addProblems(count: Int, rating: Int) = run { repo.addProblems(count, rating) }
    fun deleteProblems(p: ProblemLog) = run { repo.deleteProblems(p) }

    fun addEvent(e: Event) = run { repo.addEvent(e) }
    fun deleteEvent(e: Event) = run { repo.deleteEvent(e) }
    fun toggleEvent(e: Event) = run { repo.toggleEvent(e) }

    fun backup(uri: Uri) = run {
        message = try { repo.backupTo(uri); "Backup saved" } catch (e: Exception) { "Backup failed: ${e.message}" }
    }
    fun restore(uri: Uri) = run {
        try { repo.restoreFrom(uri); exitProcess(0) } catch (e: Exception) { message = "Restore failed: ${e.message}" }
    }
    fun exportCsv(uri: Uri) = run {
        message = try { repo.exportCsv(uri); "CSV exported" } catch (e: Exception) { "Export failed: ${e.message}" }
    }
}

fun goalProgress(g: Goal, ms: List<Milestone>): Float {
    if (g.status == "Completed") return 1f
    val m = ms.filter { it.parentType == "GOAL" && it.parentId == g.id }
    return if (m.isEmpty()) g.manualProgress / 100f else m.count { it.done }.toFloat() / m.size
}
fun courseProgress(c: Course, ls: List<Lesson>): Float {
    val l = ls.filter { it.courseId == c.id }
    return if (l.isEmpty()) (if (c.status == "Completed") 1f else 0f) else l.count { it.done }.toFloat() / l.size
}
