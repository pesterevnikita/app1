package io.github.pesterevnikita.focusgate.policy
import java.time.*
object ScheduleEvaluator {
    private fun eligible(schedule: Schedule, date: LocalDate): Boolean =
        date.dayOfWeek.value in schedule.days && (schedule.startDate == null || date >= LocalDate.parse(schedule.startDate)) &&
            (schedule.endDateExclusive == null || date < LocalDate.parse(schedule.endDateExclusive))
    /**
     * Windows include their start and exclude their end. An overnight window belongs to its
     * starting weekday: Monday 22:00–02:00 also covers early Tuesday, even if Tuesday is disabled.
     */
    fun isActive(schedule: Schedule, instant: Instant, zoneId: ZoneId): Boolean {
        val local = instant.atZone(zoneId)
        val date = local.toLocalDate(); val time = local.toLocalTime()
        if (schedule.windows.isEmpty()) return eligible(schedule, date)
        return schedule.windows.any { window ->
            val start = LocalTime.parse(window.start); val end = LocalTime.parse(window.end)
            if (start < end) eligible(schedule, date) && time >= start && time < end
            else (eligible(schedule, date) && time >= start) || (eligible(schedule, date.minusDays(1)) && time < end)
        }
    }
    /** Search one weekly cycle plus explicit date boundaries for the next actual active/inactive change. */
    fun nextTransition(schedule: Schedule, instant: Instant, zone: ZoneId): Instant? {
        val date = instant.atZone(zone).toLocalDate()
        val dates = ((0L..8L).map { date.plusDays(it) } + listOfNotNull(schedule.startDate?.let(LocalDate::parse),schedule.endDateExclusive?.let(LocalDate::parse))).distinct()
        val times = (listOf(LocalTime.MIDNIGHT) + schedule.windows.flatMap { listOf(LocalTime.parse(it.start), LocalTime.parse(it.end)) }).distinct()
        return dates.flatMap { day -> times.map { day.atTime(it).atZone(zone).toInstant() } }.filter { it > instant }.sorted()
            .firstOrNull { isActive(schedule, it.minusNanos(1), zone) != isActive(schedule, it, zone) }
    }
}
