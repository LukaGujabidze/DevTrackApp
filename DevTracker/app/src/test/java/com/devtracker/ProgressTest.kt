package com.devtracker

import com.devtracker.data.*
import com.devtracker.ui.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {
    private val goal = Goal(id = 1, title = "g", type = "CERT")
    private fun ms(vararg done: Boolean) = done.map { Milestone(parentType = "GOAL", parentId = 1, title = "m", done = it) }

    @Test fun goalProgressUsesMilestones() = assertEquals(0.5f, goalProgress(goal, ms(true, false)), 0.001f)
    @Test fun goalWithoutMilestonesUsesManualProgress() = assertEquals(0.4f, goalProgress(goal.copy(manualProgress = 40), emptyList()), 0.001f)
    @Test fun completedGoalIsFull() = assertEquals(1f, goalProgress(goal.copy(status = "Completed"), ms(false)), 0.001f)
    @Test fun otherGoalsMilestonesAreIgnored() {
        val other = Milestone(parentType = "GOAL", parentId = 2, title = "x", done = true)
        assertEquals(0f, goalProgress(goal, ms(false) + other), 0.001f)
    }
    @Test fun courseProgressFromLessons() {
        val c = Course(id = 5, title = "c", domain = 0)
        val ls = listOf(Lesson(courseId = 5, title = "a", done = true), Lesson(courseId = 5, title = "b"), Lesson(courseId = 5, title = "c"), Lesson(courseId = 5, title = "d", done = true))
        assertEquals(0.5f, courseProgress(c, ls), 0.001f)
    }
    @Test fun streakCountsConsecutiveDays() {
        val now = System.currentTimeMillis()
        val s = listOf(0L, 1L, 2L).map { Session(date = now - it * DAY, minutes = 10, domain = 0) }
        assertEquals(3, streak(s)); assertEquals(3, longestStreak(s))
    }
    @Test fun streakBreaksOnGap() {
        val now = System.currentTimeMillis()
        val s = listOf(0L, 3L, 4L, 5L).map { Session(date = now - it * DAY, minutes = 10, domain = 0) }
        assertEquals(1, streak(s)); assertEquals(3, longestStreak(s))
    }
    @Test fun weekValuesSumsHours() {
        val s = listOf(Session(date = System.currentTimeMillis(), minutes = 90, domain = 0))
        assertEquals(1.5f, weekValues(s, 2).last(), 0.001f)
    }
}
