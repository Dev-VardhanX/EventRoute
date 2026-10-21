package com.eventroute.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null
)

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int
) : Exception(message)
