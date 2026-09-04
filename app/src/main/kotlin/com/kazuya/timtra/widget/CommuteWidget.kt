package com.kazuya.timtra.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kazuya.timtra.MainActivity
import com.kazuya.timtra.R
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.theme.StatusColors
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.LocalDateTime

/**
 * ホーム画面ウィジェット（CLAUDE.md 12-7）: 家を出る時刻、残り時間、ステータス色、バス/JR の発時刻。
 * 常駐やポーリングはしない。更新は 30 分ごとのシステム更新に加えて、通知が鳴る時刻・前夜の再計算・アプリ起動時に行う。
 */
class CommuteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val provider = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).snapshotProvider()
        val snapshot = provider.snapshot()
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }
}

class CommuteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommuteWidget()
}

/** 「更新」タップ。 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        CommuteWidget().update(context, glanceId)
    }
}

/** 通知受信・再計算のあとに全ウィジェットを更新する入口。 */
object CommuteWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { CommuteWidget().updateAll(context) }
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val journey = snapshot.journey
        if (journey == null) {
            Text(
                text = context.getString(if (snapshot.dayOff) R.string.widget_day_off else R.string.widget_no_journey),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp),
            )
        } else {
            Text(
                text = context.getString(if (journey.bound == Bound.OUTBOUND) R.string.home_leave_home else R.string.home_leave_work),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Text(
                text = journey.leaveAt.hhmm(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = countdown(context, snapshot.now, journey.leaveAt),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            StatusBadge(journey)
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text =
                    when (journey.bound) {
                        Bound.OUTBOUND ->
                            context.getString(
                                R.string.widget_bus_jr_outbound,
                                journey.bus.departureAt.hhmm(),
                                journey.train.departureAt.hhmm(),
                            )
                        Bound.INBOUND ->
                            context.getString(
                                R.string.widget_bus_jr_inbound,
                                journey.train.departureAt.hhmm(),
                                journey.bus.departureAt.hhmm(),
                            )
                    },
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_updated_at, snapshot.now.hhmm()),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = context.getString(R.string.widget_refresh),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
            )
        }
    }
}

@Composable
private fun StatusBadge(journey: Journey) {
    val context = LocalContext.current
    val label =
        context.getString(
            when (journey.status) {
                JourneyStatus.OK -> R.string.status_ok
                JourneyStatus.TIGHT -> R.string.status_tight
                JourneyStatus.RISK -> R.string.status_risk
                JourneyStatus.MISSED -> R.string.status_missed
            },
        )
    val margin = journey.transferMargin.toMinutes()
    val marginText =
        if (margin >=
            0
        ) {
            context.getString(R.string.home_transfer_margin, margin)
        } else {
            context.getString(R.string.home_transfer_margin_negative, -margin)
        }
    Text(
        text = "$label・$marginText",
        style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier =
            GlanceModifier
                .background(ColorProvider(StatusColors.of(journey.status)))
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** ウィジェットは自動でカウントダウンできないので、更新時点の残り時間を出す。 */
private fun countdown(
    context: Context,
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val remaining = Duration.between(now, target)
    val minutes = remaining.toMinutes()
    return when {
        remaining.isNegative && minutes <= -1 -> context.getString(R.string.home_countdown_passed)
        minutes < 1 -> context.getString(R.string.home_countdown_now)
        minutes < MINUTES_PER_HOUR -> context.getString(R.string.home_countdown_minutes, minutes)
        else -> context.getString(R.string.home_countdown_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}

private const val MINUTES_PER_HOUR = 60L
