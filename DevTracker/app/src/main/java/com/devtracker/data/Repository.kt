package com.devtracker.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Single entry point to storage, preferences and reminder scheduling. */
class Repository(private val ctx: Context, private val db: Db) {
    val dao = db.dao()
    val prefs = Prefs(ctx)
    val goals = dao.goals(); val milestones = dao.milestones(); val courses = dao.courses(); val lessons = dao.lessons()
    val sessions = dao.sessions(); val ratings = dao.ratings(); val notes = dao.notes()
    val events = dao.events(); val results = dao.results(); val problems = dao.problems()

    suspend fun seedIfNeeded() {
        if (!prefs.seeded.first() && dao.goalCount() == 0) Seed.run(dao)
        prefs.setSeeded()
    }

    suspend fun addGoal(title: String, type: String, priority: Int) { dao.insertGoal(Goal(title = title, type = type, priority = priority)) }
    suspend fun saveGoal(g: Goal) {
        dao.updateGoal(when {
            g.status == "Completed" && g.completedAt == null -> g.copy(completedAt = System.currentTimeMillis())
            g.status != "Completed" -> g.copy(completedAt = null)
            else -> g
        })
    }
    suspend fun deleteGoal(g: Goal) { dao.delMilestones("GOAL", g.id); dao.delNotes("GOAL", g.id); dao.delResults(g.id); dao.deleteGoal(g) }

    suspend fun addMilestone(type: String, id: Long, title: String) { dao.insertMilestone(Milestone(parentType = type, parentId = id, title = title)) }
    suspend fun toggleMilestone(m: Milestone) { dao.updateMilestone(m.copy(done = !m.done, doneAt = if (!m.done) System.currentTimeMillis() else null)) }
    suspend fun setMilestoneDue(m: Milestone, d: Long?) { dao.updateMilestone(m.copy(dueDate = d)) }
    suspend fun deleteMilestone(m: Milestone) { dao.deleteMilestone(m) }
    suspend fun addNote(type: String, id: Long, text: String) { dao.insertNote(NoteEntity(parentType = type, parentId = id, text = text, createdAt = System.currentTimeMillis())) }
    suspend fun deleteNote(n: NoteEntity) { dao.deleteNote(n) }

    suspend fun addCourse(title: String, domain: Int) { dao.insertCourse(Course(title = title, domain = domain)) }
    suspend fun saveCourse(c: Course) { dao.updateCourse(c) }
    suspend fun deleteCourse(c: Course) { dao.delLessons(c.id); dao.delMilestones("COURSE", c.id); dao.delNotes("COURSE", c.id); dao.deleteCourse(c) }
    suspend fun addLesson(courseId: Long, title: String) { dao.insertLesson(Lesson(courseId = courseId, title = title)); recalc(courseId) }
    suspend fun toggleLesson(l: Lesson) { dao.updateLesson(l.copy(done = !l.done)); recalc(l.courseId) }
    suspend fun deleteLesson(l: Lesson) { dao.deleteLesson(l); recalc(l.courseId) }
    suspend fun setCourseCompleted(c: Course, done: Boolean) {
        val now = System.currentTimeMillis()
        val ls = dao.lessonsOf(c.id)
        if (ls.isNotEmpty()) { ls.forEach { dao.updateLesson(it.copy(done = done)) }; recalc(c.id) }
        else dao.updateCourse(c.copy(status = if (done) "Completed" else "In Progress", completedAt = if (done) now else null, startDate = c.startDate ?: now))
    }
    private suspend fun recalc(courseId: Long) {
        val c = dao.course(courseId) ?: return
        val ls = dao.lessonsOf(courseId)
        if (ls.isEmpty()) return
        val done = ls.count { it.done }; val now = System.currentTimeMillis()
        dao.updateCourse(when {
            done == ls.size -> c.copy(status = "Completed", completedAt = c.completedAt ?: now, startDate = c.startDate ?: now)
            done > 0 -> c.copy(status = "In Progress", completedAt = null, startDate = c.startDate ?: now)
            else -> c.copy(status = "Not Started", completedAt = null)
        })
    }

    suspend fun logSession(minutes: Int, domain: Int, refType: String, refId: Long) {
        if (minutes > 0) dao.insertSession(Session(date = System.currentTimeMillis(), minutes = minutes, domain = domain, refType = refType, refId = refId))
    }
    suspend fun deleteSession(s: Session) { dao.deleteSession(s) }

    suspend fun addRating(rating: Int, contest: String) { dao.insertRating(RatingEntry(date = System.currentTimeMillis(), rating = rating, contest = contest)) }
    suspend fun deleteRating(r: RatingEntry) { dao.deleteRating(r) }
    suspend fun addResult(goalId: Long, title: String, result: String) { dao.insertResult(CompResult(goalId = goalId, date = System.currentTimeMillis(), title = title, result = result)) }
    suspend fun deleteResult(r: CompResult) { dao.deleteResult(r) }
    suspend fun addProblems(count: Int, rating: Int) { dao.insertProblems(ProblemLog(date = System.currentTimeMillis(), rating = rating, count = count)) }
    suspend fun deleteProblems(p: ProblemLog) { dao.deleteProblems(p) }

    suspend fun addEvent(e: Event) { val id = dao.insertEvent(e); Reminders.schedule(ctx, e.copy(id = id)) }
    suspend fun deleteEvent(e: Event) { Reminders.cancel(ctx, e.id); dao.deleteEvent(e) }
    suspend fun toggleEvent(e: Event) {
        if (e.kind == "STUDY" && !e.done) {
            dao.insertSession(Session(date = System.currentTimeMillis(), minutes = e.minutes, domain = e.domain))
            dao.updateEvent(e.copy(done = true)); Reminders.cancel(ctx, e.id)
        } else if (e.kind == "REMINDER") {
            dao.updateEvent(e.copy(done = !e.done))
            if (!e.done) Reminders.cancel(ctx, e.id) else Reminders.schedule(ctx, e)
        }
    }

    /** Imports rating history from Codeforces, skipping entries already stored. Returns the number added. */
    suspend fun syncCodeforces(handle: String): Int {
        require(handle.isNotBlank()) { "Enter your Codeforces handle first" }
        val remote = CodeforcesApi.fetchRatings(handle.trim())
        val have = dao.ratings().first()
        var added = 0
        remote.forEach { r ->
            if (have.none { it.contest == r.contest && it.rating == r.rating }) {
                dao.insertRating(RatingEntry(date = r.time, rating = r.rating, contest = r.contest)); added++
            }
        }
        return added
    }

    fun backupTo(uri: Uri) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        ctx.contentResolver.openOutputStream(uri)?.use { out -> ctx.getDatabasePath(Db.NAME).inputStream().use { it.copyTo(out) } }
    }
    /** Replaces the database file. The caller must restart the process afterwards. */
    fun restoreFrom(uri: Uri) {
        val tmp = File(ctx.cacheDir, "restore.db")
        ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val header = tmp.inputStream().use { s -> ByteArray(15).also { s.read(it) } }
        require(String(header) == "SQLite format 3") { "Not a valid backup file" }
        db.close()
        val f = ctx.getDatabasePath(Db.NAME)
        tmp.copyTo(f, overwrite = true)
        File(f.path + "-wal").delete(); File(f.path + "-shm").delete()
    }
    suspend fun exportCsv(uri: Uri) {
        val rows = dao.sessions().first()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
            w.write("date,minutes,domain_index,ref_type,ref_id\n")
            rows.forEach { w.write("${df.format(Date(it.date))},${it.minutes},${it.domain},${it.refType},${it.refId}\n") }
        }
    }
}
