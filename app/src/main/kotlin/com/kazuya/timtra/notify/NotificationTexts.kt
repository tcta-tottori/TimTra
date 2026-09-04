package com.kazuya.timtra.notify

import android.content.Context
import com.kazuya.timtra.R
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.notify.NotificationKind
import com.kazuya.timtra.core.notify.NotificationTiming
import com.kazuya.timtra.core.notify.PlannedNotification
import com.kazuya.timtra.ui.common.hhmm

/** 通知の文面。AlarmManager に渡す時点で確定させ、受信側は表示するだけにする。 */
data class NotificationContent(
    val id: Int,
    val title: String,
    val text: String,
    val bigText: String,
)

fun Context.notificationContent(
    n: PlannedNotification,
    timing: NotificationTiming,
): NotificationContent {
    val j = n.journey
    val title =
        getString(if (j.bound == Bound.OUTBOUND) R.string.notify_title_outbound else R.string.notify_title_inbound)
    val text =
        when (n.kind) {
            NotificationKind.LEAVE_SOON ->
                when (j.bound) {
                    Bound.OUTBOUND ->
                        getString(
                            R.string.notify_leave_soon_outbound,
                            timing.beforeLeave.toMinutes(),
                            j.bus.departureAt.hhmm(),
                        )
                    Bound.INBOUND ->
                        getString(
                            R.string.notify_leave_soon_inbound,
                            timing.beforeLeave.toMinutes(),
                            j.train.departureAt.hhmm(),
                        )
                }
            NotificationKind.LEAVE_NOW -> getString(R.string.notify_leave_now)
            NotificationKind.FIRST_LEG_DEPARTING ->
                when (j.bound) {
                    Bound.OUTBOUND -> getString(R.string.notify_bus_departing, j.bus.trip.boardStop.name)
                    Bound.INBOUND -> getString(R.string.notify_jr_departing)
                }
            NotificationKind.APPROACHING_TRANSFER ->
                when (j.bound) {
                    Bound.OUTBOUND -> getString(R.string.notify_approaching_outbound, j.train.departureAt.hhmm())
                    Bound.INBOUND -> {
                        val platform = j.bus.trip.boardStop.platformCode
                        if (platform.isNullOrBlank()) {
                            getString(R.string.notify_approaching_inbound, j.bus.departureAt.hhmm())
                        } else {
                            getString(R.string.notify_approaching_inbound_platform, j.bus.departureAt.hhmm(), platform)
                        }
                    }
                }
        }
    val summary =
        when (j.bound) {
            Bound.OUTBOUND ->
                getString(
                    R.string.notify_summary_outbound,
                    j.leaveAt.hhmm(),
                    j.bus.departureAt.hhmm(),
                    j.bus.arrivalAt.hhmm(),
                    j.train.departureAt.hhmm(),
                    j.train.arrivalAt.hhmm(),
                    j.transferMargin.toMinutes(),
                )
            Bound.INBOUND ->
                getString(
                    R.string.notify_summary_inbound,
                    j.leaveAt.hhmm(),
                    j.train.departureAt.hhmm(),
                    j.train.arrivalAt.hhmm(),
                    j.bus.departureAt.hhmm(),
                    j.bus.arrivalAt.hhmm(),
                    j.transferMargin.toMinutes(),
                )
        }
    return NotificationContent(id = n.id, title = title, text = text, bigText = "$text\n$summary")
}
