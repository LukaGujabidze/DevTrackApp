package com.devtracker.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** type: CERT, COMP, CF (Codeforces), CUSTOM */
@Entity data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val provider: String = "", val type: String,
    val status: String = "Not Started", val priority: Int = 2,
    val targetDate: Long? = null, val examDate: Long? = null,
    val description: String = "", val url: String = "",
    val completedAt: Long? = null, val archived: Boolean = false,
    val manualProgress: Int = 0, val certificateUri: String = ""
)
/** parentType: GOAL or COURSE */
@Entity data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentType: String, val parentId: Long, val title: String,
    val done: Boolean = false, val doneAt: Long? = null, val dueDate: Long? = null
)
@Entity data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val domain: Int, val status: String = "Not Started",
    val url: String = "", val estHours: Int = 20,
    val startDate: Long? = null, val targetDate: Long? = null, val completedAt: Long? = null,
    val skills: String = ""
)
@Entity data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long, val title: String, val done: Boolean = false
)
/** refType: COURSE, GOAL or NONE */
@Entity data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, val minutes: Int, val domain: Int,
    val refType: String = "NONE", val refId: Long = 0
)
@Entity data class RatingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, val rating: Int, val contest: String = ""
)
@Entity data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentType: String, val parentId: Long, val text: String, val createdAt: Long
)

/** kind: STUDY (scheduled session) or REMINDER; notifyAt != null schedules a notification */
@Entity data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val date: Long, val kind: String,
    val minutes: Int = 0, val domain: Int = 5,
    val notifyAt: Long? = null, val repeatDaily: Boolean = false, val done: Boolean = false
)
@Entity data class CompResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long, val date: Long, val title: String, val result: String
)
@Entity data class ProblemLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, val rating: Int, val count: Int
)

@Dao interface AppDao {
    @Query("SELECT * FROM Goal ORDER BY priority, id") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM Milestone ORDER BY id") fun milestones(): Flow<List<Milestone>>
    @Query("SELECT * FROM Course ORDER BY id") fun courses(): Flow<List<Course>>
    @Query("SELECT * FROM Lesson ORDER BY id") fun lessons(): Flow<List<Lesson>>
    @Query("SELECT * FROM Session ORDER BY date DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM RatingEntry ORDER BY date") fun ratings(): Flow<List<RatingEntry>>
    @Query("SELECT * FROM NoteEntity ORDER BY createdAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM Lesson WHERE courseId=:id") suspend fun lessonsOf(id: Long): List<Lesson>
    @Query("SELECT * FROM Course WHERE id=:id") suspend fun course(id: Long): Course?
    @Query("SELECT COUNT(*) FROM Goal") suspend fun goalCount(): Int
    @Query("SELECT * FROM Event ORDER BY date") fun events(): Flow<List<Event>>
    @Query("SELECT * FROM CompResult ORDER BY date DESC") fun results(): Flow<List<CompResult>>
    @Query("SELECT * FROM ProblemLog ORDER BY date DESC") fun problems(): Flow<List<ProblemLog>>
    @Insert suspend fun insertEvent(e: Event): Long
    @Update suspend fun updateEvent(e: Event)
    @Delete suspend fun deleteEvent(e: Event)
    @Insert suspend fun insertResult(r: CompResult): Long
    @Delete suspend fun deleteResult(r: CompResult)
    @Insert suspend fun insertProblems(p: ProblemLog): Long
    @Delete suspend fun deleteProblems(p: ProblemLog)
    @Query("DELETE FROM CompResult WHERE goalId=:id") suspend fun delResults(id: Long)

    @Insert suspend fun insertGoal(g: Goal): Long
    @Update suspend fun updateGoal(g: Goal)
    @Delete suspend fun deleteGoal(g: Goal)
    @Insert suspend fun insertMilestone(m: Milestone): Long
    @Update suspend fun updateMilestone(m: Milestone)
    @Delete suspend fun deleteMilestone(m: Milestone)
    @Insert suspend fun insertCourse(c: Course): Long
    @Update suspend fun updateCourse(c: Course)
    @Delete suspend fun deleteCourse(c: Course)
    @Insert suspend fun insertLesson(l: Lesson): Long
    @Update suspend fun updateLesson(l: Lesson)
    @Delete suspend fun deleteLesson(l: Lesson)
    @Insert suspend fun insertSession(s: Session): Long
    @Delete suspend fun deleteSession(s: Session)
    @Insert suspend fun insertRating(r: RatingEntry): Long
    @Delete suspend fun deleteRating(r: RatingEntry)
    @Insert suspend fun insertNote(n: NoteEntity): Long
    @Delete suspend fun deleteNote(n: NoteEntity)
    @Query("DELETE FROM Milestone WHERE parentType=:t AND parentId=:id") suspend fun delMilestones(t: String, id: Long)
    @Query("DELETE FROM NoteEntity WHERE parentType=:t AND parentId=:id") suspend fun delNotes(t: String, id: Long)
    @Query("DELETE FROM Lesson WHERE courseId=:id") suspend fun delLessons(id: Long)
}

@Database(
    entities = [Goal::class, Milestone::class, Course::class, Lesson::class, Session::class, RatingEntry::class, NoteEntity::class,
        Event::class, CompResult::class, ProblemLog::class],
    version = 2, exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        const val NAME = "devtracker.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Milestone ADD COLUMN dueDate INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `date` INTEGER NOT NULL, `kind` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `domain` INTEGER NOT NULL, `notifyAt` INTEGER, `repeatDaily` INTEGER NOT NULL, `done` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `CompResult` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `title` TEXT NOT NULL, `result` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ProblemLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `count` INTEGER NOT NULL)")
            }
        }
        @Volatile private var inst: Db? = null
        fun get(c: android.content.Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, NAME).addMigrations(MIGRATION_1_2).build().also { inst = it }
        }
    }
}
