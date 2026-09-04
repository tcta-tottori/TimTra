package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.model.JrCalendar
import com.kazuya.timtra.core.model.JrLeg
import com.kazuya.timtra.core.model.JrService
import com.kazuya.timtra.core.model.JrTimetable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** app/src/main/assets/jr_timetable.json を読む。スキーマは CLAUDE.md 4-3。 */
object JrTimetableParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun parse(text: String): JrTimetable {
        val dto = json.decodeFromString<JrTimetableDto>(text)
        val legs =
            dto.legs.map { leg ->
                JrLeg(
                    id = leg.id,
                    from = leg.from,
                    to = leg.to,
                    line = leg.line,
                    services =
                        leg.services.map { s ->
                            JrService(
                                trainId = s.trainId,
                                calendar = JrCalendar.fromJsonValue(s.calendar),
                                departure = LocalTime.parse(s.departure, timeFormat),
                                arrival = LocalTime.parse(s.arrival, timeFormat),
                                platform = s.platform,
                                note = s.note,
                            )
                        },
                )
            }
        require(legs.map { it.id }.toSet().size == legs.size) { "legs[].id が重複しています" }
        val overrides =
            dto.overrides.associate { o ->
                LocalDate.parse(o.date, TimTraConstants.ISO_DATE) to JrCalendar.fromJsonValue(o.calendar).toDayType()
            }
        return JrTimetable(version = dto.version, note = dto.note, legs = legs, overrides = overrides)
    }

    @Serializable
    private data class JrTimetableDto(
        val version: String,
        val note: String = "",
        val legs: List<JrLegDto> = emptyList(),
        val overrides: List<JrOverrideDto> = emptyList(),
    )

    @Serializable
    private data class JrLegDto(
        val id: String,
        val from: String,
        val to: String,
        val line: String,
        val services: List<JrServiceDto> = emptyList(),
    )

    @Serializable
    private data class JrServiceDto(
        val trainId: String,
        val calendar: String,
        val departure: String,
        val arrival: String,
        val platform: String = "",
        val note: String = "",
    )

    @Serializable
    private data class JrOverrideDto(
        val date: String,
        val calendar: String,
    )
}
