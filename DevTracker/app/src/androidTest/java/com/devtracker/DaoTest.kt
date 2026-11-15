package com.devtracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devtracker.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: Db
    private lateinit var repo: Repository
    private val dao get() = db.dao()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, Db::class.java).allowMainThreadQueries().build()
        repo = Repository(ctx, db)
    }
    @After fun tearDown() = db.close()

    @Test fun seedCreatesAllGoalsAndCourses() = runBlocking {
        Seed.run(dao)
        val goals = dao.goals().first()
        assertEquals(8, goals.count { it.type == "CERT" }); assertEquals(4, goals.count { it.type == "COMP" }); assertEquals(1, goals.count { it.type == "CF" })
        val courses = dao.courses().first()
        assertEquals(32, courses.size)
        assertEquals(listOf(5, 8, 6, 8, 5), (0..4).map { d -> courses.count { it.domain == d } })
        assertTrue(dao.milestones().first().isNotEmpty())
    }

    @Test fun lessonsDriveCourseCompletion() = runBlocking {
        val id = dao.insertCourse(Course(title = "c", domain = 0))
        repo.addLesson(id, "a"); repo.addLesson(id, "b")
        repo.toggleLesson(dao.lessonsOf(id).first())
        assertEquals("In Progress", dao.course(id)!!.status)
        repo.toggleLesson(dao.lessonsOf(id).first { !it.done })
        val c = dao.course(id)!!
        assertEquals("Completed", c.status); assertNotNull(c.completedAt)
        repo.setCourseCompleted(c, false)
        assertEquals("Not Started", dao.course(id)!!.status)
    }

    @Test fun completingGoalStoresDateAndReopeningClearsIt() = runBlocking {
        val id = dao.insertGoal(Goal(title = "g", type = "CUSTOM"))
        repo.saveGoal(dao.goals().first().first { it.id == id }.copy(status = "Completed"))
        assertNotNull(dao.goals().first().first { it.id == id }.completedAt)
        repo.saveGoal(dao.goals().first().first { it.id == id }.copy(status = "In Progress"))
        assertNull(dao.goals().first().first { it.id == id }.completedAt)
    }

    @Test fun deletingGoalRemovesItsChildren() = runBlocking {
        val id = dao.insertGoal(Goal(title = "g", type = "COMP"))
        repo.addMilestone("GOAL", id, "m"); repo.addNote("GOAL", id, "n"); repo.addResult(id, "IOI 2026", "Bronze")
        repo.deleteGoal(dao.goals().first().first { it.id == id })
        assertTrue(dao.milestones().first().isEmpty()); assertTrue(dao.notes().first().isEmpty()); assertTrue(dao.results().first().isEmpty())
    }

    @Test fun completingScheduledStudyEventLogsSession() = runBlocking {
        repo.addEvent(Event(title = "DP", date = System.currentTimeMillis(), kind = "STUDY", minutes = 45, domain = 0))
        repo.toggleEvent(dao.events().first().first())
        assertEquals(45, dao.sessions().first().sumOf { it.minutes })
        assertTrue(dao.events().first().first().done)
        repo.toggleEvent(dao.events().first().first()) // second tap must not log twice
        assertEquals(45, dao.sessions().first().sumOf { it.minutes })
    }

    @Test fun milestoneToggleTracksCompletionDate() = runBlocking {
        val gid = dao.insertGoal(Goal(title = "g", type = "CERT"))
        repo.addMilestone("GOAL", gid, "m")
        repo.toggleMilestone(dao.milestones().first().first())
        assertNotNull(dao.milestones().first().first().doneAt)
        repo.toggleMilestone(dao.milestones().first().first())
        assertNull(dao.milestones().first().first().doneAt)
    }
}
