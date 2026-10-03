package io.github.pesterevnikita.focusgate.policy
import java.time.*
import java.time.temporal.ChronoUnit
/** Calendar-aligned quotas: an hourly allowance resets at the local clock hour, not after 60 minutes of use. */
object BucketClock {
    /** UTC epoch milliseconds identify the local hour/day bucket, including timezone/DST rules. */
    fun start(period: QuotaPeriod, clock: ClockSnapshot): Long {
        val local = clock.instant.atZone(ZoneId.of(clock.zoneId))
        return (if (period == QuotaPeriod.HOUR) local.truncatedTo(ChronoUnit.HOURS) else local.toLocalDate().atStartOfDay(local.zone)).toInstant().toEpochMilli()
    }
    /** Find the next real boundary; local days need not be exactly 24 hours around DST changes. */
    fun next(period: QuotaPeriod, clock: ClockSnapshot): Instant {
        val local = clock.instant.atZone(ZoneId.of(clock.zoneId))
        return if (period == QuotaPeriod.HOUR) local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant() else local.toLocalDate().plusDays(1).atStartOfDay(local.zone).toInstant()
    }
}
