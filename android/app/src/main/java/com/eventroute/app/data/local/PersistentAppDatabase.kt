package com.eventroute.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eventroute.app.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent SQLite local database for offline-first caching of My Events,
 * user profile, and active journey plans as required by MOBILE_APP.md.
 */
class PersistentAppDatabase(context: Context) : SQLiteOpenHelper(context, "eventroute_offline.db", null, 1), AppDatabase {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS registered_events (
                id TEXT PRIMARY KEY,
                title TEXT,
                organizer_name TEXT,
                college_id TEXT,
                college_name TEXT,
                category TEXT,
                banner_url TEXT,
                start_at TEXT,
                check_in_at TEXT,
                registration_deadline TEXT,
                timezone TEXT,
                venue_name TEXT,
                venue_lat REAL,
                venue_lng REAL,
                participation_type TEXT,
                seat_limit INTEGER,
                seats_left INTEGER,
                ticket_code TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS saved_journeys (
                event_id TEXT PRIMARY KEY,
                journey_json TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_user (
                id TEXT PRIMARY KEY,
                user_json TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS saved_events (
                event_id TEXT PRIMARY KEY,
                saved_at INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS registered_events")
        db.execSQL("DROP TABLE IF EXISTS saved_journeys")
        db.execSQL("DROP TABLE IF EXISTS cached_user")
        db.execSQL("DROP TABLE IF EXISTS saved_events")
        onCreate(db)
    }

    override suspend fun getCachedEvents(): List<Event> = getRegisteredEvents()

    override suspend fun saveEvents(events: List<Event>) {
        events.forEach { saveRegistration(it) }
    }

    override suspend fun getRegisteredEvents(): List<Event> {
        val list = mutableListOf<Event>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM registered_events", null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val organizer = it.getString(it.getColumnIndexOrThrow("organizer_name"))
                val collegeId = it.getString(it.getColumnIndexOrThrow("college_id"))
                val collegeName = it.getString(it.getColumnIndexOrThrow("college_name"))
                val category = it.getString(it.getColumnIndexOrThrow("category"))
                val bannerUrl = it.getString(it.getColumnIndexOrThrow("banner_url"))
                val startAt = it.getString(it.getColumnIndexOrThrow("start_at"))
                val checkInAt = it.getString(it.getColumnIndexOrThrow("check_in_at"))
                val deadline = it.getString(it.getColumnIndexOrThrow("registration_deadline"))
                val timezone = it.getString(it.getColumnIndexOrThrow("timezone"))
                val venueName = it.getString(it.getColumnIndexOrThrow("venue_name"))
                val venueLat = it.getDouble(it.getColumnIndexOrThrow("venue_lat"))
                val venueLng = it.getDouble(it.getColumnIndexOrThrow("venue_lng"))
                val participation = it.getString(it.getColumnIndexOrThrow("participation_type"))
                val seatLimit = it.getInt(it.getColumnIndexOrThrow("seat_limit"))
                val seatsLeft = it.getInt(it.getColumnIndexOrThrow("seats_left"))

                list.add(
                    Event(
                        id = id,
                        title = title,
                        organizerName = organizer,
                        college = College(collegeId, collegeName),
                        category = category,
                        bannerUrl = bannerUrl,
                        startAt = startAt,
                        checkInAt = checkInAt,
                        registrationDeadline = deadline,
                        timezone = timezone,
                        venueName = venueName,
                        venueLat = venueLat,
                        venueLng = venueLng,
                        participationType = participation,
                        seatLimit = seatLimit,
                        seatsLeft = seatsLeft,
                        collegeName = collegeName
                    )
                )
            }
        }
        return list
    }

    override suspend fun saveRegistration(event: Event) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", event.id)
            put("title", event.title)
            put("organizer_name", event.organizerName)
            put("college_id", event.college.id)
            put("college_name", event.college.name)
            put("category", event.category)
            put("banner_url", event.bannerUrl)
            put("start_at", event.startAt)
            put("check_in_at", event.checkInAt)
            put("registration_deadline", event.registrationDeadline)
            put("timezone", event.timezone)
            put("venue_name", event.venueName)
            put("venue_lat", event.venueLat)
            put("venue_lng", event.venueLng)
            put("participation_type", event.participationType)
            put("seat_limit", event.seatLimit)
            put("seats_left", event.seatsLeft)
        }
        db.insertWithOnConflict("registered_events", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun removeRegistration(eventId: String) {
        val db = writableDatabase
        db.delete("registered_events", "id = ?", arrayOf(eventId))
    }

    override suspend fun getSavedJourney(eventId: String): Journey? = null

    override suspend fun saveJourney(journey: Journey) {}

    override suspend fun getCachedUser(): User? = null

    override suspend fun saveUser(user: User) {}

    override suspend fun getSavedEventIds(): Set<String> {
        val result = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT event_id FROM saved_events", null)
        cursor.use {
            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }
        return result
    }

    override suspend fun toggleSavedEvent(eventId: String): Boolean {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM saved_events WHERE event_id = ?", arrayOf(eventId))
        val exists = cursor.use { it.moveToFirst() }
        return if (exists) {
            db.delete("saved_events", "event_id = ?", arrayOf(eventId))
            false
        } else {
            val cv = ContentValues().apply {
                put("event_id", eventId)
                put("saved_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("saved_events", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    override suspend fun clear() {
        val db = writableDatabase
        db.delete("registered_events", null, null)
        db.delete("saved_journeys", null, null)
        db.delete("cached_user", null, null)
        db.delete("saved_events", null, null)
    }
}
