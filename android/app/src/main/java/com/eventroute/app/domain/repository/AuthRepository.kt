package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.College
import com.eventroute.app.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, email: String, password: String, collegeId: String = ""): Result<User>
    suspend fun verifyEmail(email: String, code: String): Result<Boolean>
    suspend fun getCurrentUser(): Result<User?>
    suspend fun logout(): Result<Unit>
}

interface ProfileRepository {
    suspend fun getProfile(): Result<User>
    suspend fun updateStartPoint(defaultStart: String, lat: Double, lng: Double, label: String): Result<User>
    suspend fun registerDeviceToken(token: String): Result<Unit>
    suspend fun getColleges(search: String? = null): Result<List<College>>
}
