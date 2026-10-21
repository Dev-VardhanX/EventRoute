package com.eventroute.app.data.mapper

import com.eventroute.app.data.remote.dto.UserDto
import com.eventroute.app.domain.model.User

fun UserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    role = role,
    collegeId = collegeId,
    collegeName = collegeName,
    interests = interests,
    homeLat = homeLat,
    homeLng = homeLng,
    homeLabel = homeLabel,
    defaultStart = defaultStart,
    isEmailVerified = isEmailVerified
)
