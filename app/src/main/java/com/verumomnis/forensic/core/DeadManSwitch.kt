package com.verumomnis.forensic.core

import java.time.Duration
import java.time.Instant

/** Alert produced when the Dead-Man Switch fires (build spec Section 25). */
data class DeadManSwitchAlert(
    val triggeredAt: String,
    val inactivityHours: Long,
    val action: String = "AUTO_RELEASE_TO_INTERPOL"
)

/**
 * Dead-Man Switch: 72h of inactivity auto-releases sealed evidence to INTERPOL.
 * Hard-coded timer, cannot be disabled (build spec Section 25 / Constitution).
 *
 * The core is deterministic and unit-testable (no Android Handler); an Android
 * wrapper calls [recordActivity] on user interaction and polls [checkAndBuildAlert]
 * hourly.
 */
class DeadManSwitch(private var lastActivity: Instant = Instant.now()) {

    private val thresholdHours: Long = Constitution.DEAD_MAN_SWITCH_HOURS.toLong()

    fun recordActivity(now: Instant = Instant.now()) { lastActivity = now }

    fun hoursInactive(now: Instant = Instant.now()): Long =
        Duration.between(lastActivity, now).toHours()

    fun isTriggered(now: Instant = Instant.now()): Boolean = hoursInactive(now) >= thresholdHours

    /** Returns an alert if (and only if) the switch has fired. */
    fun checkAndBuildAlert(now: Instant = Instant.now()): DeadManSwitchAlert? =
        if (isTriggered(now)) {
            DeadManSwitchAlert(triggeredAt = now.toString(), inactivityHours = hoursInactive(now))
        } else null
}
