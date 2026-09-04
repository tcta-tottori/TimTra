package com.kazuya.timtra.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** GTFS calendar.txt の 1 行。 */
data class CalendarRule(
    val serviceId: String,
    val days: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    fun matches(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate) && date.dayOfWeek in days
}

/** GTFS calendar_dates.txt の 1 行。 */
data class CalendarException(
    val serviceId: String,
    val date: LocalDate,
    val type: ExceptionType,
)

enum class ExceptionType(
    val gtfsValue: Int,
) {
    /** その日だけ追加運行 */
    ADDED(1),

    /** その日は運休 */
    REMOVED(2),
    ;

    companion object {
        fun fromGtfsValue(value: Int): ExceptionType =
            entries.firstOrNull { it.gtfsValue == value }
                ?: throw IllegalArgumentException("未知の exception_type: $value")
    }
}

/**
 * calendar.txt と calendar_dates.txt から、ある日に有効な service_id を求める。
 * calendar_dates の指定は calendar の規則より優先する（GTFS 仕様どおり）。
 */
class ServiceCalendar(
    rules: Collection<CalendarRule>,
    exceptions: Collection<CalendarException>,
) {
    private val rulesById: Map<String, CalendarRule> = rules.associateBy { it.serviceId }
    private val exceptionsByDate: Map<LocalDate, Map<String, ExceptionType>> =
        exceptions.groupBy { it.date }.mapValues { (_, list) -> list.associate { it.serviceId to it.type } }
    private val allServiceIds: Set<String> = rulesById.keys + exceptions.map { it.serviceId }

    fun isActive(
        serviceId: String,
        date: LocalDate,
    ): Boolean {
        exceptionsByDate[date]?.get(serviceId)?.let { return it == ExceptionType.ADDED }
        return rulesById[serviceId]?.matches(date) ?: false
    }

    fun activeServiceIds(date: LocalDate): Set<String> = allServiceIds.filterTo(LinkedHashSet()) { isActive(it, date) }
}
