package com.eventroute.app.data.remote.api

import com.eventroute.app.data.remote.dto.CollegeDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CollegesApi {
    @GET("colleges")
    suspend fun getColleges(@Query("search") search: String? = null): List<CollegeDto>
}
