package com.eventroute.app.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "student", // "student", "organizer", "admin"
    val collegeId: String? = null,
    val collegeName: String? = null,
    val interests: List<String> = emptyList(),
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val homeLabel: String? = null,
    val defaultStart: String = "college", // "college", "home", "custom"
    val isEmailVerified: Boolean = false
)
