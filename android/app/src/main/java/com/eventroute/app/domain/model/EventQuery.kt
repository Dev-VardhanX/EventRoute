package com.eventroute.app.domain.model

enum class SortOrder {
    SOONEST,
    NEAREST,
    SHORTEST_TRAVEL_TIME
}

enum class ParticipationFilter {
    ALL,
    SOLO,
    TEAM,
    EITHER
}

enum class FeeFilter {
    ALL,
    FREE,
    PAID
}

data class EventQuery(
    val text: String = "",
    val category: String = "All Events",
    val participation: ParticipationFilter = ParticipationFilter.ALL,
    val feeFilter: FeeFilter = FeeFilter.ALL,
    val startDate: String? = null,
    val endDate: String? = null,
    val maxDistanceKm: Double? = null,
    val maxTravelMin: Int? = null,
    val sort: SortOrder = SortOrder.SOONEST,
    val userLat: Double? = null,
    val userLng: Double? = null
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (category != "All Events" && category != "All" && category.isNotBlank()) count++
            if (participation != ParticipationFilter.ALL) count++
            if (feeFilter != FeeFilter.ALL) count++
            if (startDate != null || endDate != null) count++
            if (maxDistanceKm != null) count++
            if (maxTravelMin != null) count++
            if (sort != SortOrder.SOONEST) count++
            return count
        }
}
