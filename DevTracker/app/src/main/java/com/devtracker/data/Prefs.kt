package com.devtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App preferences in DataStore. Falls back to values from the earlier SharedPreferences version. */
class Prefs(private val c: Context) {
    private val legacy = c.getSharedPreferences("prefs", 0)
    private val THEME = intPreferencesKey("theme"); private val DAILY = intPreferencesKey("daily")
    private val TIMER = longPreferencesKey("timer"); private val TDOM = intPreferencesKey("timerDomain"); private val CF = stringPreferencesKey("cfHandle")
    private val HIDDEN = stringSetPreferencesKey("hidden"); private val SEEDED = booleanPreferencesKey("seeded")

    val theme: Flow<Int> = c.store.data.map { it[THEME] ?: legacy.getInt("theme", 0) }
    val daily: Flow<Int> = c.store.data.map { it[DAILY] ?: legacy.getInt("daily", 60) }
    val hidden: Flow<Set<String>> = c.store.data.map { it[HIDDEN] ?: emptySet() }
    val seeded: Flow<Boolean> = c.store.data.map { it[SEEDED] ?: legacy.getBoolean("seeded", false) }

    val timer: Flow<Long> = c.store.data.map { it[TIMER] ?: 0L }
    val timerDomain: Flow<Int> = c.store.data.map { it[TDOM] ?: 0 }
    val cfHandle: Flow<String> = c.store.data.map { it[CF] ?: "" }
    suspend fun setTimer(start: Long, domain: Int) { c.store.edit { it[TIMER] = start; it[TDOM] = domain } }
    suspend fun setCfHandle(h: String) { c.store.edit { it[CF] = h } }
    suspend fun setTheme(v: Int) { c.store.edit { it[THEME] = v } }
    suspend fun setDaily(v: Int) { c.store.edit { it[DAILY] = v } }
    suspend fun setHidden(v: Set<String>) { c.store.edit { it[HIDDEN] = v } }
    suspend fun setSeeded() { c.store.edit { it[SEEDED] = true } }
}
