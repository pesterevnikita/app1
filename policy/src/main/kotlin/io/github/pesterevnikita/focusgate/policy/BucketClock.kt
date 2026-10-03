package io.github.pesterevnikita.focusgate.policy
import java.time.*
import java.time.temporal.ChronoUnit
object BucketClock {
    fun start(period: QuotaPeriod, clock: ClockSnapshot): Long {
        val local = clock.instant.atZone(ZoneId.of(clock.zoneId))
        return (if (period == QuotaPeriod.HOUR) local.truncatedTo(ChronoUnit.HOURS) else local.toLocalDate().atStartOfDay(local.zone)).toInstant().toEpochMilli()
    }
    fun next(period: QuotaPeriod, clock: ClockSnapshot): Instant {
        val local = clock.instant.atZone(ZoneId.of(clock.zoneId))
        return if (period == QuotaPeriod.HOUR) local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant() else local.toLocalDate().plusDays(1).atStartOfDay(local.zone).toInstant()
    }
}
